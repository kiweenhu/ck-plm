/*
 * Copyright (c) 2025 深圳乘恺科技有限公司
 * All rights reserved.
 *
 * @author Kiween.Hu; Roney.Liu
 */

package cn.ck.plm.checkout.service.impl;

import cn.ck.plm.base.entity.UserActivity;
import cn.ck.plm.base.mapper.UserActivityMapper;
import cn.ck.plm.base.service.api.LifecycleStatusService;
import cn.ck.plm.checkout.dto.CheckoutVO;
import cn.ck.plm.checkout.service.api.CheckoutProvider;
import cn.ck.plm.document.entity.EngineeringDocument;
import cn.ck.plm.document.entity.EngineeringDocumentIteration;
import cn.ck.plm.document.mapper.EngineeringDocumentIterationMapper;
import cn.ck.plm.document.mapper.EngineeringDocumentMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * 工程数据检出提供者 —— 处理工程数据（CAD 设计数据 / 封装 / 图符）的检出、检入与取消检出。
 *
 * <p>实现 {@link CheckoutProvider} 后由 {@code CheckoutService} 与
 * {@code CheckoutOperationServiceImpl} 自动发现并按 {@code entityType=ENG_DOCUMENT} 路由，
 * 无需修改任何聚合类。
 *
 * <p>行为与 {@code DocumentCheckoutProvider} 一致：检出时复制出同大版本的新小版本
 * （iteration+1，checkedOut=true，derivedFromOid 指向源版本），检入解除锁定，取消检出删除该副本。
 * 差异：{@code ck_eng_document_iteration} 无 view 列，故不继承视图；
 * 改为一并继承 CAD 专有属性（cadName / cadType / cadTool）与主文件。
 */
@Component
public class EngineeringDocumentCheckoutProvider implements CheckoutProvider {

    private static final Logger log = LoggerFactory.getLogger(EngineeringDocumentCheckoutProvider.class);

    /** 实体类型标识（与 /api/checkout/* 的 entityType 取值一致） */
    private static final String ENTITY_TYPE = "ENG_DOCUMENT";

    /** 实体类型中文名 */
    private static final String ENTITY_TYPE_NAME = "工程数据";

    /** 前端「我的检出」跳转路径（企业资源库；工程数据详情页尚未建立时退化为该列表页） */
    private static final String LINK_PATH = "/resource";

    private final EngineeringDocumentMapper engDocumentMapper;
    private final EngineeringDocumentIterationMapper iterationMapper;
    private final UserActivityMapper activityMapper;
    /** 状态 code → 显示名（见 LifecycleStatusService#displayName） */
    private final LifecycleStatusService lifecycleStatusService;

    public EngineeringDocumentCheckoutProvider(EngineeringDocumentMapper engDocumentMapper,
                                               EngineeringDocumentIterationMapper iterationMapper,
                                               UserActivityMapper activityMapper,
                                               LifecycleStatusService lifecycleStatusService) {
        this.engDocumentMapper = engDocumentMapper;
        this.iterationMapper = iterationMapper;
        this.activityMapper = activityMapper;
        this.lifecycleStatusService = lifecycleStatusService;
    }

    @Override
    public String getEntityType() {
        return ENTITY_TYPE;
    }

    @Override
    public String getEntityTypeName() {
        return ENTITY_TYPE_NAME;
    }

    @Override
    public List<CheckoutVO> findCheckedOutByUser(String userOid) {
        List<CheckoutVO> result = new ArrayList<>();
        List<EngineeringDocumentIteration> checkedOutIters = iterationMapper.selectCheckedOutByUser(userOid);
        for (EngineeringDocumentIteration iter : checkedOutIters) {
            EngineeringDocument doc = engDocumentMapper.selectByOid(iter.getMasterOid());
            if (doc == null) continue;
            CheckoutVO vo = new CheckoutVO();
            vo.setOid(doc.getOid());
            vo.setName(doc.getName());
            vo.setCode(doc.getNumber());
            vo.setEntityType(ENTITY_TYPE);
            vo.setEntityTypeName(ENTITY_TYPE_NAME);
            vo.setDisplayVersion(iter.getRevision() + "." + iter.getIteration());
            vo.setCheckedOutBy(iter.getCheckedOutBy());
            vo.setCheckedOutComment(iter.getCheckedOutComment());
            vo.setCheckedOutAt(iter.getUpdatedAt() != null ? iter.getUpdatedAt().toString() : null);
            if (iter.getStatus() != null) {
                vo.setStatusCode(iter.getStatus().getCode());
                // 不能取 getDisplayName()：迭代上的 status 只带 code，那样列表只会显示 code
                vo.setStatusName(lifecycleStatusService.displayName(
                        iter.getLifecycleTemplateIterationOid(), iter.getStatus().getCode()));
            }
            vo.setLinkPath(LINK_PATH);
            result.add(vo);
        }
        return result;
    }

    @Override
    @Transactional
    public void checkout(String entityOid, String comment, String user) {
        EngineeringDocument doc = engDocumentMapper.selectByOid(entityOid);
        if (doc == null) {
            throw new IllegalArgumentException("工程数据不存在: " + entityOid);
        }
        EngineeringDocumentIteration currentIter = iterationMapper.selectLatestByMasterOid(entityOid);
        if (currentIter == null) {
            throw new IllegalArgumentException("工程数据没有可用版本: " + entityOid);
        }
        if (currentIter.isCheckedOut()) {
            throw new IllegalStateException(
                "工程数据已被 " + currentIter.getCheckedOutBy() + " 检出，检出注释: " +
                (currentIter.getCheckedOutComment() != null ? currentIter.getCheckedOutComment() : "无")
            );
        }

        // 1. 源版本 latest → false
        currentIter.setLatest(false);
        currentIter.setUpdatedAt(LocalDateTime.now());
        iterationMapper.update(currentIter);

        // 2. 创建同大版本的新小版本（iteration+1）
        EngineeringDocumentIteration copy = new EngineeringDocumentIteration();
        copy.setOid(UUID.randomUUID().toString());
        copy.setMasterOid(currentIter.getMasterOid());
        copy.setRevision(currentIter.getRevision());
        copy.setIteration(currentIter.getIteration() + 1);
        copy.setLatest(true);                              // 标记为最新
        copy.setCheckedOut(true);                          // 标记为已检出
        copy.setCheckedOutBy(user);
        copy.setCheckedOutComment(comment);
        copy.setDerivedFromOid(currentIter.getOid());      // 记录来源版本
        copy.setDerivedAt(LocalDateTime.now());
        copy.setBranchId(currentIter.getBranchId() != null ? currentIter.getBranchId() : "master");
        copy.setCreatedAt(LocalDateTime.now());
        copy.setUpdatedAt(LocalDateTime.now());
        copy.setCreator(currentIter.getCreator());
        copy.setTenantOid(currentIter.getTenantOid());
        copy.setStatus(currentIter.getStatus());
        copy.setLifecycleTemplateIterationOid(currentIter.getLifecycleTemplateIterationOid());
        // 继承主文件与 CAD / 2D 制图属性（工程数据的迭代级真实列）
        copy.setCkfileOid(currentIter.getCkfileOid());
        copy.setCadName(currentIter.getCadName());
        copy.setCadType(currentIter.getCadType());
        copy.setCadTool(currentIter.getCadTool());
        copy.setSheetSize(currentIter.getSheetSize());
        copy.setScale(currentIter.getScale());
        copy.setSheetNumber(currentIter.getSheetNumber());
        copy.setSheetCount(currentIter.getSheetCount());
        copy.setProjection(currentIter.getProjection());
        copy.setAuthor(currentIter.getAuthor());
        copy.setMaterial(currentIter.getMaterial());
        copy.setWeight(currentIter.getWeight());
        iterationMapper.insert(copy);

        log.info("工程数据检出成功: oid={}, {}.{} -> {}.{}, user={}", entityOid,
                currentIter.getRevision(), currentIter.getIteration(),
                copy.getRevision(), copy.getIteration(), user);

        recordActivity(user, "检出工程数据", doc);
    }

    @Override
    @Transactional
    public void undoCheckout(String entityOid, String user) {
        EngineeringDocument doc = engDocumentMapper.selectByOid(entityOid);
        if (doc == null) {
            throw new IllegalArgumentException("工程数据不存在: " + entityOid);
        }

        // 找到检出时创建的新版本（checkedOut=true, latest=true, derivedFromOid 指向源版本）
        List<EngineeringDocumentIteration> allIters = iterationMapper.selectByMasterOid(entityOid);
        EngineeringDocumentIteration checkedOutIter = null;
        EngineeringDocumentIteration sourceIter = null;
        for (EngineeringDocumentIteration iter : allIters) {
            if (iter.isCheckedOut() && iter.isLatest() && iter.getDerivedFromOid() != null) {
                checkedOutIter = iter;
                sourceIter = iterationMapper.selectByOid(iter.getDerivedFromOid());
            }
        }

        if (checkedOutIter == null) {
            throw new IllegalStateException("该工程数据未被检出: " + entityOid);
        }
        if (!user.equals(checkedOutIter.getCheckedOutBy())) {
            throw new IllegalStateException("只有检出人 " + checkedOutIter.getCheckedOutBy() + " 才能取消检出");
        }

        // 1. 删除检出时创建的新版本
        iterationMapper.deleteByOid(checkedOutIter.getOid());

        // 2. 恢复源版本 latest = true
        if (sourceIter != null) {
            sourceIter.setLatest(true);
            sourceIter.setUpdatedAt(LocalDateTime.now());
            iterationMapper.update(sourceIter);
        }

        log.info("工程数据取消检出成功: oid={}, user={}", entityOid, user);

        recordActivity(user, "取消检出", doc);
    }

    @Override
    @Transactional
    public void checkin(String entityOid, String user) {
        EngineeringDocument doc = engDocumentMapper.selectByOid(entityOid);
        if (doc == null) {
            throw new IllegalArgumentException("工程数据不存在: " + entityOid);
        }

        // 找到检出版本（checkedOut=true, latest=true, derivedFromOid 指向源版本）
        List<EngineeringDocumentIteration> allIters = iterationMapper.selectByMasterOid(entityOid);
        EngineeringDocumentIteration checkedOutIter = null;
        for (EngineeringDocumentIteration iter : allIters) {
            if (iter.isCheckedOut() && iter.isLatest() && iter.getDerivedFromOid() != null) {
                checkedOutIter = iter;
            }
        }

        if (checkedOutIter == null) {
            throw new IllegalStateException("该工程数据未被检出: " + entityOid);
        }
        if (!user.equals(checkedOutIter.getCheckedOutBy())) {
            throw new IllegalStateException("只有检出人 " + checkedOutIter.getCheckedOutBy() + " 才能检入");
        }

        // 解除检出锁定，将工作副本保存为正式的新版本
        checkedOutIter.setCheckedOut(false);
        checkedOutIter.setCheckedOutBy(null);
        checkedOutIter.setCheckedOutComment(null);
        checkedOutIter.setUpdatedAt(LocalDateTime.now());
        iterationMapper.update(checkedOutIter);

        log.info("工程数据检入成功: oid={}, version={}.{}, user={}", entityOid,
                checkedOutIter.getRevision(), checkedOutIter.getIteration(), user);

        recordActivity(user, "检入工程数据", doc);
    }

    /** 记录操作日志 */
    private void recordActivity(String user, String actionDesc, EngineeringDocument doc) {
        try {
            UserActivity activity = new UserActivity();
            activity.setOid(UUID.randomUUID().toString());
            activity.setUserOid(user);
            activity.setActivityType("OPERATION");
            activity.setActionDesc(actionDesc);
            activity.setTargetName((doc.getNumber() != null ? doc.getNumber() + " " : "") + doc.getName());
            activity.setTargetType(ENTITY_TYPE_NAME);
            activity.setTargetPath(LINK_PATH);
            activity.setResult("SUCCESS");
            activity.setCreator(user);
            activity.setUpdater(user);
            activityMapper.insert(activity);
        } catch (Exception e) {
            log.warn("记录操作日志失败[{}]: {}", actionDesc, e.getMessage());
        }
    }
}
