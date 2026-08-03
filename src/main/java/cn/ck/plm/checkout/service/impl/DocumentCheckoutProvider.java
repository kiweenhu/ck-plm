/*
 * Copyright (c) 2025 深圳乘恺科技有限公司
 * All rights reserved.
 */
package cn.ck.plm.checkout.service.impl;

import cn.ck.plm.document.entity.DocumentIteration;
import cn.ck.plm.document.mapper.DocumentIterationMapper;
import cn.ck.plm.document.mapper.DocumentMapper;
import cn.ck.plm.document.entity.Document;
import cn.ck.plm.base.entity.UserActivity;
import cn.ck.plm.base.mapper.UserActivityMapper;
import cn.ck.plm.checkout.dto.CheckoutVO;
import cn.ck.plm.checkout.service.api.CheckoutProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * 文档检出提供者 —— 统一处理文档的检出查询和检出操作。
 */
@Component
public class DocumentCheckoutProvider implements CheckoutProvider {

    private static final Logger log = LoggerFactory.getLogger(DocumentCheckoutProvider.class);

    private final DocumentMapper documentMapper;
    private final DocumentIterationMapper iterationMapper;
    private final UserActivityMapper activityMapper;

    public DocumentCheckoutProvider(DocumentMapper documentMapper, DocumentIterationMapper iterationMapper,
                                     UserActivityMapper activityMapper) {
        this.documentMapper = documentMapper;
        this.iterationMapper = iterationMapper;
        this.activityMapper = activityMapper;
    }

    @Override
    public String getEntityType() {
        return "DOCUMENT";
    }

    @Override
    public String getEntityTypeName() {
        return "文档";
    }

    @Override
    public List<CheckoutVO> findCheckedOutByUser(String userOid) {
        List<CheckoutVO> result = new ArrayList<>();
        List<DocumentIteration> checkedOutIters = iterationMapper.selectCheckedOutByUser(userOid);
        for (DocumentIteration iter : checkedOutIters) {
            Document doc = documentMapper.selectByOid(iter.getMasterOid());
            if (doc == null) continue;
            CheckoutVO vo = new CheckoutVO();
            vo.setOid(doc.getOid());
            vo.setName(doc.getName());
            vo.setCode(doc.getNumber());
            vo.setEntityType("DOCUMENT");
            vo.setEntityTypeName("文档");
            vo.setDisplayVersion(iter.getRevision() + "." + iter.getIteration());
            vo.setCheckedOutBy(iter.getCheckedOutBy());
            vo.setCheckedOutComment(iter.getCheckedOutComment());
            vo.setCheckedOutAt(iter.getUpdatedAt() != null ? iter.getUpdatedAt().toString() : null);
            if (iter.getStatus() != null) {
                vo.setStatusCode(iter.getStatus().getCode());
                vo.setStatusName(iter.getStatus().getDisplayName());
            }
            vo.setLinkPath("/product");
            result.add(vo);
        }
        return result;
    }

    @Override
    @Transactional
    public void checkout(String entityOid, String comment, String user) {
        Document doc = documentMapper.selectByOid(entityOid);
        if (doc == null) {
            throw new IllegalArgumentException("文档不存在: " + entityOid);
        }
        DocumentIteration currentIter = iterationMapper.selectLatestByMasterOid(entityOid);
        if (currentIter == null) {
            throw new IllegalArgumentException("文档没有可用版本: " + entityOid);
        }
        if (currentIter.isCheckedOut()) {
            throw new IllegalStateException(
                "文档已被 " + currentIter.getCheckedOutBy() + " 检出，检出注释: " +
                (currentIter.getCheckedOutComment() != null ? currentIter.getCheckedOutComment() : "无")
            );
        }

        // 1. 源版本 latest → false
        currentIter.setLatest(false);
        iterationMapper.update(currentIter);

        // 2. 创建同大版本的新小版本（iteration+1）
        DocumentIteration copy = new DocumentIteration();
        copy.setMasterOid(currentIter.getMasterOid());
        copy.setRevision(currentIter.getRevision());
        copy.setIteration(currentIter.getIteration() + 1);
        copy.setLatest(true);                              // 标记为最新
        copy.setCheckedOut(true);                          // 标记为已检出
        copy.setCheckedOutBy(user);
        copy.setCheckedOutComment(comment);
        copy.setDerivedFromOid(currentIter.getOid());      // 记录来源版本
        copy.setDerivedAt(LocalDateTime.now());
        copy.setCkfileOid(currentIter.getCkfileOid());
        copy.setView(currentIter.getView());
        copy.setStatus(currentIter.getStatus());
        copy.setLifecycleTemplateIterationOid(currentIter.getLifecycleTemplateIterationOid());
        iterationMapper.insert(copy);

        log.info("检出成功: docOid={}, {}.{} -> {}.{}, user={}", entityOid,
                currentIter.getRevision(), currentIter.getIteration(),
                copy.getRevision(), copy.getIteration(), user);

        // 记录操作日志
        recordActivity(user, "检出文档", doc);
    }

    @Override
    @Transactional
    public void undoCheckout(String entityOid, String user) {
        Document doc = documentMapper.selectByOid(entityOid);
        if (doc == null) {
            throw new IllegalArgumentException("文档不存在: " + entityOid);
        }

        // 找到检出时创建的新版本（checkedOut=true, latest=true, derivedFromOid 指向源版本）
        List<DocumentIteration> allIters = iterationMapper.selectByMasterOid(entityOid);
        DocumentIteration checkedOutIter = null;
        DocumentIteration sourceIter = null;

        for (DocumentIteration iter : allIters) {
            if (iter.isCheckedOut() && iter.isLatest() && iter.getDerivedFromOid() != null) {
                checkedOutIter = iter;
                sourceIter = iterationMapper.selectByOid(iter.getDerivedFromOid());
            }
        }

        if (checkedOutIter == null) {
            throw new IllegalStateException("该文档未被检出: " + entityOid);
        }
        if (!user.equals(checkedOutIter.getCheckedOutBy())) {
            throw new IllegalStateException("只有检出人 " + checkedOutIter.getCheckedOutBy() + " 才能取消检出");
        }

        // 1. 删除检出时创建的新版本
        iterationMapper.deleteByOid(checkedOutIter.getOid());

        // 2. 恢复源版本 latest = true
        if (sourceIter != null) {
            sourceIter.setLatest(true);
            iterationMapper.update(sourceIter);
        }

        log.info("取消检出成功: docOid={}, user={}", entityOid, user);

        // 记录操作日志
        recordActivity(user, "取消检出", doc);
    }

    /** 记录操作日志 */
    private void recordActivity(String user, String actionDesc, Document doc) {
        try {
            UserActivity activity = new UserActivity();
            activity.setOid(UUID.randomUUID().toString());
            activity.setUserOid(user);
            activity.setActivityType("OPERATION");
            activity.setActionDesc(actionDesc);
            activity.setTargetName((doc.getNumber() != null ? doc.getNumber() + " " : "") + doc.getName());
            activity.setTargetType("文档");
            activity.setTargetPath("/product");
            activity.setResult("SUCCESS");
            activity.setCreator(user);
            activity.setUpdater(user);
            activityMapper.insert(activity);
        } catch (Exception e) {
            log.warn("记录操作日志失败[{}]: {}", actionDesc, e.getMessage());
        }
    }
}
