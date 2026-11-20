/*
 * Copyright (c) 2026~2028 深圳乘恺科技有限公司
 * All rights reserved.
 *
 * @author Kiween.Hu
 */

package cn.ck.plm.checkout.service.impl;

import cn.ck.plm.base.entity.UserActivity;
import cn.ck.plm.base.mapper.UserActivityMapper;
import cn.ck.plm.checkout.dto.CheckoutVO;
import cn.ck.plm.checkout.service.api.CheckoutProvider;
import cn.ck.plm.ecad.entity.EcadSchematic;
import cn.ck.plm.ecad.entity.EcadSchematicIteration;
import cn.ck.plm.ecad.mapper.EcadSchematicIterationMapper;
import cn.ck.plm.ecad.mapper.EcadSchematicMapper;
import cn.ck.plm.ecad.service.api.EcadSchematicService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * ECAD 原理图检出提供者 —— 将原理图（.DSN）接入统一检出体系。
 *
 * <p>声明为 Spring Bean 后由 CheckoutService / CheckoutOperationServiceImpl 自动发现，
 * entityType 为 {@code ECAD_SCHEMATIC}。检出/检入/取消的业务规则在
 * {@link EcadSchematicService} 中实现，本类做路由适配与"我的检出"查询。
 */
@Component
public class EcadSchematicCheckoutProvider implements CheckoutProvider {

    private static final Logger log = LoggerFactory.getLogger(EcadSchematicCheckoutProvider.class);

    private final EcadSchematicMapper schematicMapper;
    private final EcadSchematicIterationMapper iterationMapper;
    private final EcadSchematicService schematicService;
    private final UserActivityMapper activityMapper;

    public EcadSchematicCheckoutProvider(EcadSchematicMapper schematicMapper,
                                         EcadSchematicIterationMapper iterationMapper,
                                         EcadSchematicService schematicService,
                                         UserActivityMapper activityMapper) {
        this.schematicMapper = schematicMapper;
        this.iterationMapper = iterationMapper;
        this.schematicService = schematicService;
        this.activityMapper = activityMapper;
    }

    @Override
    public String getEntityType() {
        return "ECAD_SCHEMATIC";
    }

    @Override
    public String getEntityTypeName() {
        return "原理图";
    }

    @Override
    public List<CheckoutVO> findCheckedOutByUser(String userOid) {
        List<CheckoutVO> result = new ArrayList<>();
        for (EcadSchematicIteration iter : iterationMapper.selectCheckedOutByUser(userOid)) {
            EcadSchematic schematic = schematicMapper.selectByOid(iter.getMasterOid());
            if (schematic == null) {
                continue;
            }
            CheckoutVO vo = new CheckoutVO();
            vo.setOid(schematic.getOid());
            vo.setName(schematic.getName());
            vo.setCode(schematic.getNumber());
            vo.setEntityType(getEntityType());
            vo.setEntityTypeName(getEntityTypeName());
            vo.setDisplayVersion(iter.getRevision() + "." + iter.getIteration());
            vo.setCheckedOutBy(iter.getCheckedOutBy());
            vo.setCheckedOutComment(iter.getCheckedOutComment());
            vo.setCheckedOutAt(iter.getUpdatedAt() != null ? iter.getUpdatedAt().toString() : null);
            vo.setLinkPath("/ecad");
            result.add(vo);
        }
        return result;
    }

    @Override
    @Transactional
    public void checkout(String entityOid, String comment, String user) {
        schematicService.checkout(entityOid, comment, user);
        recordActivity(user, "检出原理图", schematicMapper.selectByOid(entityOid));
    }

    @Override
    @Transactional
    public void checkin(String entityOid, String user) {
        schematicService.checkin(entityOid, user);
        recordActivity(user, "检入原理图", schematicMapper.selectByOid(entityOid));
    }

    @Override
    @Transactional
    public void undoCheckout(String entityOid, String user) {
        schematicService.undoCheckout(entityOid, user);
        recordActivity(user, "取消检出原理图", schematicMapper.selectByOid(entityOid));
    }

    /** 记录操作日志（失败不阻断业务） */
    private void recordActivity(String user, String actionDesc, EcadSchematic schematic) {
        if (schematic == null) {
            return;
        }
        try {
            UserActivity activity = new UserActivity();
            activity.setOid(UUID.randomUUID().toString());
            activity.setUserOid(user);
            activity.setActivityType("OPERATION");
            activity.setActionDesc(actionDesc);
            activity.setTargetName((schematic.getNumber() != null ? schematic.getNumber() + " " : "")
                    + schematic.getName());
            activity.setTargetType("原理图");
            activity.setTargetPath("/ecad");
            activity.setResult("SUCCESS");
            activity.setCreator(user);
            activity.setUpdater(user);
            activityMapper.insert(activity);
        } catch (Exception e) {
            log.warn("记录操作日志失败[{}]: {}", actionDesc, e.getMessage());
        }
    }
}
