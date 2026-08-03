/*
 * Copyright (c) 2025 深圳乘恺科技有限公司
 * All rights reserved.
 *
 * @author Kiween.Hu; Roney.Liu
 */

package cn.ck.plm.base.service.impl;

import cn.ck.plm.base.entity.StageTemplate;
import cn.ck.plm.base.mapper.StageTemplateMapper;
import cn.ck.plm.base.service.api.StageTemplateService;
import cn.ck.plm.base.util.TenantContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

/**
 * {@link StageTemplateService} 实现。
 */
@Service
public class StageTemplateServiceImpl implements StageTemplateService {

    private static final Logger log = LoggerFactory.getLogger(StageTemplateServiceImpl.class);

    private final StageTemplateMapper mapper;

    public StageTemplateServiceImpl(StageTemplateMapper mapper) {
        this.mapper = mapper;
    }

    private String tenantOid() { return TenantContext.get(); }
    private String platformOid() { return TenantContext.PLATFORM_TENANT_OID; }

    @Override
    @Transactional
    public StageTemplate create(StageTemplate template) {
        if (template.getCode() == null || template.getCode().trim().isEmpty()) {
            throw new IllegalArgumentException("编码不能为空");
        }
        template.setCode(template.getCode().trim());
        if (mapper.existsByCode(template.getCode(), tenantOid(), platformOid()) > 0) {
            throw new IllegalArgumentException("编码 '" + template.getCode() + "' 已存在");
        }
        if (template.getTenantOid() == null) {
            template.setTenantOid(tenantOid());
        }
        mapper.insert(template);
        return template;
    }

    @Override
    @Transactional
    public StageTemplate update(StageTemplate template) {
        if (template.getOid() == null) {
            throw new IllegalArgumentException("oid 不能为空");
        }
        StageTemplate existing = mapper.selectByOid(template.getOid());
        if (existing == null) {
            throw new IllegalArgumentException("阶段模板不存在");
        }
        TenantContext.requireEditPermission(existing.getTenantOid(), "研发阶段模板");
        if (template.getTenantOid() == null) {
            template.setTenantOid(existing.getTenantOid());
        }
        mapper.update(template);
        return template;
    }

    @Override
    @Transactional
    public boolean delete(String oid) {
        if (oid == null || oid.trim().isEmpty()) return false;
        StageTemplate existing = mapper.selectByOid(oid);
        if (existing == null) return false;
        TenantContext.requireEditPermission(existing.getTenantOid(), "研发阶段模板");
        mapper.deleteByOid(oid);
        return true;
    }

    @Override
    public StageTemplate findByOid(String oid) {
        return oid != null ? mapper.selectByOid(oid) : null;
    }

    @Override
    public StageTemplate findByCode(String code) {
        return code != null ? mapper.selectByCode(code, tenantOid(), platformOid()) : null;
    }

    @Override
    public List<StageTemplate> findAll() {
        // 只返回本租户的模板，不展示平台级模板
        return mapper.selectByTenant(tenantOid());
    }

    @Override
    @Transactional
    public int initPlatformDefaults() {
        int inserted = 0;
        for (Map.Entry<String, StageTemplate> entry : DEFAULT_STAGES.entrySet()) {
            if (mapper.existsByCode(entry.getKey(), platformOid(), platformOid()) == 0) {
                StageTemplate tmpl = entry.getValue();
                tmpl.setTenantOid(platformOid());
                mapper.insert(tmpl);
                inserted++;
                log.info("  平台阶段模板已创建: code={}, name={}", tmpl.getCode(), tmpl.getName());
            }
        }
        if (inserted > 0) {
            log.info("平台阶段模板初始化完成: 新增 {} 个", inserted);
        }
        return inserted;
    }

    // ==================== 6 个默认阶段定义 ====================

    private static final Map<String, StageTemplate> DEFAULT_STAGES = new LinkedHashMap<>();

    static {
        add("MARKET_VALIDATION", "市场验证", "ShoppingCartOutlined", "#eb2f96", 1,
                "[\"市场调研分析\",\"目标用户验证\",\"竞品对标\",\"市场可行性评估\"]");
        add("REQUIREMENTS", "需求论证", "AuditOutlined", "#1677ff", 2,
                "[\"需求分析\",\"需求评审\",\"技术可行性论证\"]");
        add("SOLUTION", "方案设计", "BulbOutlined", "#722ed1", 3,
                "[\"系统架构设计\",\"方案评审\",\"关键技术选型验证\"]");
        add("DETAILED", "详细设计", "FundProjectionScreenOutlined", "#13c2c2", 4,
                "[\"软件详细设计\",\"硬件原理图\",\"结构设计\",\"DFMEA分析\"]");
        add("PROCESS", "工艺规划", "ToolOutlined", "#fa8c16", 5,
                "[\"生产工艺设计\",\"工装夹具设计\",\"BOM编制\",\"试产计划\"]");
        add("TRIAL", "试产", "RocketOutlined", "#52c41a", 6,
                "[\"小批量试产验证\",\"问题追踪\",\"试产评审\",\"转量产决策\"]");
    }

    @Override
    @Transactional
    public int cloneFromPlatform() {
        String currentTenant = tenantOid();
        if (platformOid().equals(currentTenant)) {
            throw new IllegalArgumentException("平台租户无需克隆，本身就是平台模板");
        }

        // 获取平台级模板
        List<StageTemplate> platformTemplates = mapper.selectByTenant(platformOid());
        if (platformTemplates.isEmpty()) {
            log.warn("平台级阶段模板为空，无法克隆");
            return 0;
        }

        int cloned = 0;
        for (StageTemplate platformTmpl : platformTemplates) {
            // 幂等：如果本租户已有同code的模板则跳过
            if (mapper.existsByCode(platformTmpl.getCode(), currentTenant, currentTenant) > 0) {
                log.info("租户模板已存在 code={}，跳过克隆", platformTmpl.getCode());
                continue;
            }

            StageTemplate clone = new StageTemplate(platformTmpl.getCode(), platformTmpl.getName());
            clone.setOid(UUID.randomUUID().toString());
            clone.setDescription(platformTmpl.getDescription());
            clone.setIcon(platformTmpl.getIcon());
            clone.setColor(platformTmpl.getColor());
            clone.setSortOrder(platformTmpl.getSortOrder());
            clone.setDefaultFolders(platformTmpl.getDefaultFolders());
            clone.setTenantOid(currentTenant);
            mapper.insert(clone);
            cloned++;
            log.info("租户阶段模板克隆成功: code={}, name={}", clone.getCode(), clone.getName());
        }

        log.info("租户阶段模板克隆完成: 新增 {} 个", cloned);
        return cloned;
    }

    @Override
    public List<StageTemplate> findPlatformTemplates() {
        return mapper.selectByTenant(platformOid());
    }

    private static void add(String code, String name, String icon, String color, int sortOrder, String defaultFolders) {
        StageTemplate tmpl = new StageTemplate(code, name);
        tmpl.setOid(UUID.randomUUID().toString());
        tmpl.setIcon(icon);
        tmpl.setColor(color);
        tmpl.setSortOrder(sortOrder);
        tmpl.setDefaultFolders(defaultFolders);
        DEFAULT_STAGES.put(code, tmpl);
    }
}
