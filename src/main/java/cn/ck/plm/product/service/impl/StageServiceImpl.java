/*
 * Copyright (c) 2026~2028 深圳乘恺科技有限公司
 * All rights reserved.
 *
 * @author Kiween.Hu; Roney.Liu
 */

package cn.ck.plm.product.service.impl;

import cn.ck.plm.base.entity.StageTemplate;
import cn.ck.plm.base.service.api.StageTemplateService;
import cn.ck.plm.base.util.TenantContext;
import cn.ck.plm.iam.entity.Tenant;
import cn.ck.plm.iam.mapper.TenantMapper;
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
    private final TenantMapper tenantMapper;

    public StageServiceImpl(StageMapper stageMapper, StageTemplateService stageTemplateService,
                            TenantMapper tenantMapper) {
        this.stageMapper = stageMapper;
        this.stageTemplateService = stageTemplateService;
        this.tenantMapper = tenantMapper;
    }

    @Override
    @Transactional
    public List<Stage> initDefaultStages(String ownerOid, String ownerType, String industry) {
        List<Stage> stages = new ArrayList<>();

        // 如果已存在阶段，返回已有的阶段列表
        List<Stage> existingStages = stageMapper.selectByOwnerOid(ownerOid);
        if (!existingStages.isEmpty()) {
            log.debug("归属单元 {} 的阶段已存在，跳过初始化", ownerOid);
            return existingStages;
        }

        // 从数据库获取本租户指定行业的研发阶段模板
        String ind = (industry == null || industry.trim().isEmpty()) ? resolveDefaultIndustry() : industry.trim();
        List<StageTemplate> templates = stageTemplateService.findAll(ind);

        // 如果租户没有该行业模板，fallback 到平台级模板
        if (templates.isEmpty()) {
            log.info("租户尚未配置 {} 行业阶段模板，使用平台级默认模板初始化", ind);
            templates = stageTemplateService.findPlatformTemplates(ind);
        }

        if (templates.isEmpty()) {
            log.warn("没有任何 {} 行业研发阶段模板可用，阶段初始化跳过", ind);
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
            stage.setManagedObjectTypes(tmpl.getManagedObjectTypes());
            stage.setOwnerOid(ownerOid);
            stage.setOwnerType(ownerType);

            stageMapper.insert(stage);
            stages.add(stage);
        }

        log.info("为归属单元 {} (type={}) 初始化了 {} 个研发阶段", ownerOid, ownerType, stages.size());
        return stages;
    }

    /**
     * 本租户的方法论（行业）—— 与六类实例同源：读 {@code ck_tenant.dev_method_code}（引导页选定、平台权威）。
     *
     * <p><b>为什么不再"猜"</b>：旧口径是"本租户有 TRADITIONAL 模板就用 TRADITIONAL，否则取第一条"，
     * 于是出现"阶段按传统六阶段建、交付件按 IPD 建"的错位 —— 阶段页签下永远过滤不出交付件
     * （2026-10-09 真实踩过：一个 10-02 用传统模板引导、之后又装了 IPD 包的租户）。
     * 六类实例（MethodologyInstanceServiceImpl）一开始就用的 dev_method_code，阶段这里必须对齐。
     *
     * <p>读不到（未引导 / 老租户 / 异常）才退回旧口径：优先 TRADITIONAL（向后兼容），否则取第一条模板的行业。
     */
    private String resolveDefaultIndustry() {
        try {
            Tenant tenant = tenantMapper.selectByOid(TenantContext.get());
            if (tenant != null && tenant.getDevMethodCode() != null
                    && !tenant.getDevMethodCode().trim().isEmpty()) {
                return tenant.getDevMethodCode().trim();
            }
        } catch (Exception e) {
            log.warn("读取租户方法论失败，退回模板推断: {}", e.getMessage());
        }
        // 退回：优先 TRADITIONAL（向后兼容），否则取第一个模板的行业
        List<StageTemplate> all = stageTemplateService.findAll();
        for (StageTemplate t : all) {
            if ("TRADITIONAL".equals(t.getIndustry())) return "TRADITIONAL";
        }
        if (all.isEmpty()) return "TRADITIONAL";
        return all.get(0).getIndustry() != null ? all.get(0).getIndustry() : "TRADITIONAL";
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
