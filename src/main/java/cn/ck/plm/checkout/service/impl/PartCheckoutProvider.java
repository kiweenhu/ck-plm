/*
 * Copyright (c) 2025 深圳乘恺科技有限公司
 * All rights reserved.
 */
package cn.ck.plm.checkout.service.impl;

import cn.ck.plm.part.entity.PartIteration;
import cn.ck.plm.part.mapper.PartIterationMapper;
import cn.ck.plm.part.mapper.PartMapper;
import cn.ck.plm.part.entity.Part;
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
 * 部件检出提供者 —— 统一处理部件的检出查询和检出操作。
 */
@Component
public class PartCheckoutProvider implements CheckoutProvider {

    private static final Logger log = LoggerFactory.getLogger(PartCheckoutProvider.class);

    private final PartMapper partMapper;
    private final PartIterationMapper iterationMapper;
    private final UserActivityMapper activityMapper;

    public PartCheckoutProvider(PartMapper partMapper, PartIterationMapper iterationMapper,
                                 UserActivityMapper activityMapper) {
        this.partMapper = partMapper;
        this.iterationMapper = iterationMapper;
        this.activityMapper = activityMapper;
    }

    @Override
    public String getEntityType() {
        return "PART";
    }

    @Override
    public String getEntityTypeName() {
        return "部件";
    }

    @Override
    public List<CheckoutVO> findCheckedOutByUser(String userOid) {
        List<CheckoutVO> result = new ArrayList<>();
        List<PartIteration> checkedOutIters = iterationMapper.selectCheckedOutByUser(userOid);
        for (PartIteration iter : checkedOutIters) {
            Part part = partMapper.selectByOid(iter.getMasterOid());
            if (part == null) continue;
            CheckoutVO vo = new CheckoutVO();
            vo.setOid(part.getOid());
            vo.setName(part.getName());
            vo.setCode(part.getNumber());
            vo.setEntityType("PART");
            vo.setEntityTypeName("部件");
            vo.setDisplayVersion(iter.getRevision() + "." + iter.getIteration());
            vo.setCheckedOutBy(iter.getCheckedOutBy());
            vo.setCheckedOutComment(iter.getCheckedOutComment());
            vo.setCheckedOutAt(iter.getUpdatedAt() != null ? iter.getUpdatedAt().toString() : null);
            if (iter.getStatus() != null) {
                vo.setStatusCode(iter.getStatus().getCode());
                vo.setStatusName(iter.getStatus().getDisplayName());
            }
            vo.setLinkPath("/part");
            result.add(vo);
        }
        return result;
    }

    @Override
    @Transactional
    public void checkout(String entityOid, String comment, String user) {
        Part part = partMapper.selectByOid(entityOid);
        if (part == null) {
            throw new IllegalArgumentException("部件不存在: " + entityOid);
        }
        PartIteration currentIter = iterationMapper.selectLatestByMasterOid(entityOid);
        if (currentIter == null) {
            throw new IllegalArgumentException("部件没有可用版本: " + entityOid);
        }
        if (currentIter.isCheckedOut()) {
            throw new IllegalStateException(
                "部件已被 " + currentIter.getCheckedOutBy() + " 检出，检出注释: " +
                (currentIter.getCheckedOutComment() != null ? currentIter.getCheckedOutComment() : "无")
            );
        }

        // 1. 源版本 latest → false
        currentIter.setLatest(false);
        iterationMapper.update(currentIter);

        // 2. 创建同大版本的新小版本（iteration+1）
        PartIteration copy = new PartIteration();
        copy.setMasterOid(currentIter.getMasterOid());
        copy.setRevision(currentIter.getRevision());
        copy.setIteration(currentIter.getIteration() + 1);
        copy.setLatest(true);                              // 标记为最新
        copy.setCheckedOut(true);                          // 标记为已检出
        copy.setCheckedOutBy(user);
        copy.setCheckedOutComment(comment);
        copy.setDerivedFromOid(currentIter.getOid());      // 记录来源版本
        copy.setDerivedAt(LocalDateTime.now());
        copy.setView(currentIter.getView());
        copy.setStatus(currentIter.getStatus());
        copy.setLifecycleTemplateIterationOid(currentIter.getLifecycleTemplateIterationOid());
        copy.setUnit(currentIter.getUnit());
        copy.setSource(currentIter.getSource());
        iterationMapper.insert(copy);

        log.info("检出成功: partOid={}, {}.{} -> {}.{}, user={}", entityOid,
                currentIter.getRevision(), currentIter.getIteration(),
                copy.getRevision(), copy.getIteration(), user);

        recordActivity(user, "检出部件", part);
    }

    @Override
    @Transactional
    public void undoCheckout(String entityOid, String user) {
        Part part = partMapper.selectByOid(entityOid);
        if (part == null) {
            throw new IllegalArgumentException("部件不存在: " + entityOid);
        }

        // 找到检出时创建的新版本（checkedOut=true, latest=true, derivedFromOid 指向源版本）
        List<PartIteration> allIters = iterationMapper.selectByMasterOid(entityOid);
        PartIteration checkedOutIter = null;
        PartIteration sourceIter = null;

        for (PartIteration iter : allIters) {
            if (iter.isCheckedOut() && iter.isLatest() && iter.getDerivedFromOid() != null) {
                checkedOutIter = iter;
                sourceIter = iterationMapper.selectByOid(iter.getDerivedFromOid());
            }
        }

        if (checkedOutIter == null) {
            throw new IllegalStateException("该部件未被检出: " + entityOid);
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

        log.info("取消检出成功: partOid={}, user={}", entityOid, user);

        recordActivity(user, "取消检出", part);
    }

    private void recordActivity(String user, String actionDesc, Part part) {
        try {
            UserActivity activity = new UserActivity();
            activity.setOid(UUID.randomUUID().toString());
            activity.setUserOid(user);
            activity.setActivityType("OPERATION");
            activity.setActionDesc(actionDesc);
            activity.setTargetName((part.getNumber() != null ? part.getNumber() + " " : "") + part.getName());
            activity.setTargetType("部件");
            activity.setTargetPath("/part");
            activity.setResult("SUCCESS");
            activity.setCreator(user);
            activity.setUpdater(user);
            activityMapper.insert(activity);
        } catch (Exception e) {
            log.warn("记录操作日志失败[{}]: {}", actionDesc, e.getMessage());
        }
    }
}
