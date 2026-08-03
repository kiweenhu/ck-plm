/*
 * Copyright (c) 2025 深圳乘恺科技有限公司
 * All rights reserved.
 *
 * @author Kiween.Hu; Roney.Liu
 */

package cn.ck.plm.product.service.impl;

import cn.ck.plm.base.entity.StageTemplate;
import cn.ck.plm.base.service.api.StageTemplateService;
import cn.ck.plm.product.entity.Stage;
import cn.ck.plm.product.mapper.StageMapper;
import cn.ck.plm.product.service.api.StageService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

/**
 * {@link StageService} 的数据库实现。
 *
 * <p>创建产品系列/型号时，根据本租户的研发阶段模板（ck_stage_template）初始化阶段实例。
 * 如果租户尚未配置模板，则 fallback 到平台级默认模板。
 */
@Service
public class StageServiceImpl implements StageService {

    private static final Logger log = LoggerFactory.getLogger(StageServiceImpl.class);

    private final StageMapper stageMapper;
    private final StageTemplateService stageTemplateService;

    public StageServiceImpl(StageMapper stageMapper, StageTemplateService stageTemplateService) {
        this.stageMapper = stageMapper;
        this.stageTemplateService = stageTemplateService;
    }

    @Override
    @Transactional
    public List<Stage> initDefaultStages(String ownerOid, String ownerType) {
        List<Stage> stages = new ArrayList<>();

        // 如果已存在阶段，返回已有的阶段列表
        List<Stage> existingStages = stageMapper.selectByOwnerOid(ownerOid);
        if (!existingStages.isEmpty()) {
            log.debug("归属单元 {} 的阶段已存在，跳过初始化", ownerOid);
            return existingStages;
        }

        // 从数据库获取本租户的研发阶段模板
        List<StageTemplate> templates = stageTemplateService.findAll();

        // 如果租户没有配置模板，fallback 到平台级模板（通过 selectByTenant 查平台租户）
        if (templates.isEmpty()) {
            log.info("租户尚未配置研发阶段模板，使用平台级默认模板初始化");
            templates = stageTemplateService.findPlatformTemplates();
        }

        if (templates.isEmpty()) {
            log.warn("没有任何研发阶段模板可用，阶段初始化跳过");
            return stages;
        }

        for (StageTemplate tmpl : templates) {
            Stage stage = new Stage();
            stage.setCode(tmpl.getCode());
            stage.setName(tmpl.getName());
            stage.setIcon(tmpl.getIcon());
            stage.setColor(tmpl.getColor());
            stage.setDescription(tmpl.getDescription());
            stage.setSortOrder(tmpl.getSortOrder() != null ? tmpl.getSortOrder() : 0);
            stage.setShowOnDashboard(true);
            stage.setDefaultFolders(tmpl.getDefaultFolders());
            stage.setOwnerOid(ownerOid);
            stage.setOwnerType(ownerType);

            stageMapper.insert(stage);
            stages.add(stage);
        }

        log.info("为归属单元 {} (type={}) 初始化了 {} 个研发阶段", ownerOid, ownerType, stages.size());
        return stages;
    }

    @Override
    public List<Stage> findByOwnerOid(String ownerOid) {
        return stageMapper.selectByOwnerOid(ownerOid);
    }

    @Override
    public Stage findByOid(String oid) {
        return stageMapper.selectByOid(oid);
    }

    @Override
    @Transactional
    public Stage update(Stage stage) {
        Stage existing = stageMapper.selectByOid(stage.getOid());
        if (existing == null) {
            throw new IllegalArgumentException("阶段不存在");
        }
        // 只合并前端可编辑的字段，其余保持原值
        if (stage.getName() != null) existing.setName(stage.getName());
        if (stage.getDescription() != null) existing.setDescription(stage.getDescription());
        if (stage.getColor() != null) existing.setColor(stage.getColor());
        if (stage.getSortOrder() != null) existing.setSortOrder(stage.getSortOrder());
        existing.setUpdatedAt(java.time.LocalDateTime.now());
        stageMapper.update(existing);
        return existing;
    }

    @Override
    @Transactional
    public int deleteByOwnerOid(String ownerOid) {
        int count = stageMapper.deleteByOwnerOid(ownerOid);
        if (count > 0) {
            log.info("删除了归属单元 {} 的 {} 个阶段", ownerOid, count);
        }
        return count;
    }
}
