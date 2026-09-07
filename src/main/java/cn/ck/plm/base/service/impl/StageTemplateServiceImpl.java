/*
 * Copyright (c) 2025 深圳乘恺科技有限公司
 * All rights reserved.
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
 *
 * <p>支持多套行业模板：TRADITIONAL（传统产品研发）/ IPD（电子高科）/ MILITARY（军工）/ AUTOMOTIVE（汽车）。
 * 每个阶段可声明管理的业务语义对象类型（Functional / Part / Document）。
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
        if (template.getTenantOid() == null) template.setTenantOid(tenantOid());
        if (template.getIndustry() == null) template.setIndustry("TRADITIONAL");
        mapper.insert(template);
        return template;
    }

    @Override
    @Transactional
    public StageTemplate update(StageTemplate template) {
        if (template.getOid() == null) throw new IllegalArgumentException("oid 不能为空");
        StageTemplate existing = mapper.selectByOid(template.getOid());
        if (existing == null) throw new IllegalArgumentException("阶段模板不存在");
        TenantContext.requireEditPermission(existing.getTenantOid(), "研发阶段模板");
        if (template.getTenantOid() == null) template.setTenantOid(existing.getTenantOid());
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
        return mapper.selectByTenant(tenantOid());
    }

    @Override
    public List<StageTemplate> findAll(String industry) {
        List<StageTemplate> all = mapper.selectByTenant(tenantOid());
        if (industry == null || industry.trim().isEmpty()) return all;
        List<StageTemplate> filtered = new ArrayList<>();
        for (StageTemplate t : all) {
            if (industry.trim().equals(t.getIndustry())) filtered.add(t);
        }
        return filtered;
    }

    @Override
    @Transactional
    public int initPlatformDefaults() {
        int inserted = 0;
        // 按 industry + code 唯一初始化
        for (Map.Entry<String, Map<String, StageTemplate>> industryEntry : DEFAULT_STAGES_BY_INDUSTRY.entrySet()) {
            String industry = industryEntry.getKey();
            for (Map.Entry<String, StageTemplate> entry : industryEntry.getValue().entrySet()) {
                if (mapper.existsByCode(entry.getKey(), platformOid(), platformOid()) == 0) {
                    StageTemplate tmpl = entry.getValue();
                    tmpl.setIndustry(industry);
                    tmpl.setTenantOid(platformOid());
                    mapper.insert(tmpl);
                    inserted++;
                    log.info("  平台阶段模板已创建: industry={}, code={}, name={}", industry, tmpl.getCode(), tmpl.getName());
                }
            }
        }
        if (inserted > 0) log.info("平台阶段模板初始化完成: 新增 {} 个", inserted);
        return inserted;
    }

    // ==================== 4 套行业默认模板 ====================

    private static final Map<String, Map<String, StageTemplate>> DEFAULT_STAGES_BY_INDUSTRY = new LinkedHashMap<>();

    static {
        // 1. TRADITIONAL（传统产品研发）
        Map<String, StageTemplate> traditional = new LinkedHashMap<>();
        add(traditional, "MARKET_VALIDATION", "市场验证", "ShoppingCartOutlined", "#eb2f96", 1,
                "[\"市场调研分析\",\"目标用户验证\",\"竞品对标\",\"市场可行性评估\"]", "[\"FUNCTIONAL\",\"DOCUMENT\"]");
        add(traditional, "REQUIREMENTS", "需求论证", "AuditOutlined", "#1677ff", 2,
                "[\"需求分析\",\"需求评审\",\"技术可行性论证\"]", "[\"FUNCTIONAL\",\"DOCUMENT\"]");
        add(traditional, "SOLUTION", "方案设计", "BulbOutlined", "#722ed1", 3,
                "[\"系统架构设计\",\"方案评审\",\"关键技术选型验证\"]", "[\"FUNCTIONAL\",\"DOCUMENT\"]");
        add(traditional, "DETAILED", "详细设计", "FundProjectionScreenOutlined", "#13c2c2", 4,
                "[\"软件详细设计\",\"硬件原理图\",\"结构设计\",\"DFMEA分析\"]", "[\"FUNCTIONAL\",\"PART\",\"DOCUMENT\"]");
        add(traditional, "PROCESS", "工艺规划", "ToolOutlined", "#fa8c16", 5,
                "[\"生产工艺设计\",\"工装夹具设计\",\"BOM编制\",\"试产计划\"]", "[\"PART\",\"DOCUMENT\"]");
        add(traditional, "TRIAL", "试产", "RocketOutlined", "#52c41a", 6,
                "[\"小批量试产验证\",\"问题追踪\",\"试产评审\",\"转量产决策\"]", "[\"PART\",\"DOCUMENT\"]");
        DEFAULT_STAGES_BY_INDUSTRY.put("TRADITIONAL", traditional);

        // 2. IPD（电子高科/IPD 集成产品开发）
        Map<String, StageTemplate> ipd = new LinkedHashMap<>();
        add(ipd, "IPD_CONCEPT", "概念阶段", "BulbOutlined", "#722ed1", 1,
                "[\"市场评估\",\"需求调研\",\"概念方案\"]", "[\"FUNCTIONAL\",\"DOCUMENT\"]");
        add(ipd, "IPD_PLAN", "计划阶段", "CalendarOutlined", "#1677ff", 2,
                "[\"项目计划\",\"资源分配\",\"风险评估\"]", "[\"FUNCTIONAL\",\"DOCUMENT\"]");
        add(ipd, "IPD_DEVELOP", "开发阶段", "ToolOutlined", "#13c2c2", 3,
                "[\"详细设计\",\"编码实现\",\"单元测试\"]", "[\"FUNCTIONAL\",\"PART\",\"DOCUMENT\"]");
        add(ipd, "IPD_VERIFY", "验证阶段", "SafetyCertificateOutlined", "#52c41a", 4,
                "[\"集成测试\",\"系统测试\",\"验收评审\"]", "[\"FUNCTIONAL\",\"PART\",\"DOCUMENT\"]");
        add(ipd, "IPD_RELEASE", "发布阶段", "RocketOutlined", "#eb2f96", 5,
                "[\"Beta测试\",\"正式发布\",\"市场投放\"]", "[\"PART\",\"DOCUMENT\"]");
        add(ipd, "IPD_LIFECYCLE", "生命周期管理", "ReloadOutlined", "#fa541c", 6,
                "[\"运营监控\",\"版本迭代\",\"退市管理\"]", "[\"FUNCTIONAL\",\"PART\",\"DOCUMENT\"]");
        DEFAULT_STAGES_BY_INDUSTRY.put("IPD", ipd);

        // 3. MILITARY（军工）
        Map<String, StageTemplate> military = new LinkedHashMap<>();
        add(military, "MIL_ARGUMENT", "论证阶段", "FileSearchOutlined", "#1677ff", 1,
                "[\"需求论证\",\"战术技术指标\",\"可行性论证\"]", "[\"FUNCTIONAL\",\"DOCUMENT\"]");
        add(military, "MIL_INITIATION", "立项阶段", "FileTextOutlined", "#722ed1", 2,
                "[\"立项申请\",\"方案评审\",\"合同签订\"]", "[\"DOCUMENT\"]");
        add(military, "MIL_SCHEME", "方案设计", "BulbOutlined", "#13c2c2", 3,
                "[\"总体方案\",\"分系统方案\",\"关键技术攻关\"]", "[\"FUNCTIONAL\",\"PART\",\"DOCUMENT\"]");
        add(military, "MIL_ENGINEERING", "工程研制", "ToolOutlined", "#fa8c16", 4,
                "[\"详细设计\",\"样机研制\",\"试验验证\"]", "[\"FUNCTIONAL\",\"PART\",\"DOCUMENT\"]");
        add(military, "MIL_DESIGN_FIX", "设计定型", "SafetyCertificateOutlined", "#52c41a", 5,
                "[\"设计定型试验\",\"技术鉴定\",\"定型审查\"]", "[\"FUNCTIONAL\",\"PART\",\"DOCUMENT\"]");
        add(military, "MIL_PROD_FIX", "生产定型", "RocketOutlined", "#eb2f96", 6,
                "[\"生产工艺定型\",\"批量生产\",\"交付验收\"]", "[\"PART\",\"DOCUMENT\"]");
        DEFAULT_STAGES_BY_INDUSTRY.put("MILITARY", military);

        // 4. AUTOMOTIVE（汽车）
        Map<String, StageTemplate> auto = new LinkedHashMap<>();
        add(auto, "AUTO_CONCEPT", "概念设计", "BulbOutlined", "#722ed1", 1,
                "[\"市场定位\",\"造型概念\",\"工程概念\"]", "[\"FUNCTIONAL\",\"DOCUMENT\"]");
        add(auto, "AUTO_DEV", "开发验证", "ToolOutlined", "#13c2c2", 2,
                "[\"详细设计\",\"样车制作\",\"性能验证\"]", "[\"FUNCTIONAL\",\"PART\",\"DOCUMENT\"]");
        add(auto, "AUTO_PP", "生产准备", "SettingOutlined", "#fa8c16", 3,
                "[\"工艺设计\",\"模具开发\",\"生产线建设\",\"试生产\"]", "[\"PART\",\"DOCUMENT\"]");
        add(auto, "AUTO_SOP", "量产", "RocketOutlined", "#52c41a", 4,
                "[\"批量生产\",\"质量控制\",\"供应链管理\"]", "[\"PART\",\"DOCUMENT\"]");
        add(auto, "AUTO_LAUNCH", "上市", "ThunderboltOutlined", "#eb2f96", 5,
                "[\"上市发布\",\"市场推广\",\"销售服务\"]", "[\"PART\",\"DOCUMENT\"]");
        DEFAULT_STAGES_BY_INDUSTRY.put("AUTOMOTIVE", auto);
    }

    @Override
    public List<String> listPlatformIndustries() {
        // 从平台已有模板中提取 distinct industry
        List<StageTemplate> platformAll = mapper.selectByTenant(platformOid());
        Set<String> industries = new LinkedHashSet<>();
        for (StageTemplate t : platformAll) {
            if (t.getIndustry() != null && !t.getIndustry().isEmpty()) {
                industries.add(t.getIndustry());
            }
        }
        return new ArrayList<>(industries);
    }

    @Override
    public List<StageTemplate> findPlatformTemplates(String industry) {
        List<StageTemplate> all = mapper.selectByTenant(platformOid());
        if (industry == null || industry.isEmpty()) return all;
        List<StageTemplate> filtered = new ArrayList<>();
        for (StageTemplate t : all) {
            if (industry.equals(t.getIndustry())) filtered.add(t);
        }
        return filtered;
    }

    @Override
    @Transactional
    public int cloneFromPlatform() {
        return cloneFromPlatform(null);
    }

    @Override
    @Transactional
    public int cloneFromPlatform(String industry) {
        String currentTenant = tenantOid();
        if (platformOid().equals(currentTenant)) {
            throw new IllegalArgumentException("平台租户无需克隆，本身就是平台模板");
        }
        List<StageTemplate> platformTemplates = findPlatformTemplates(industry);
        if (platformTemplates.isEmpty()) {
            log.warn("平台级阶段模板为空（industry={}），无法克隆", industry);
            return 0;
        }
        int cloned = 0;
        for (StageTemplate platformTmpl : platformTemplates) {
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
            clone.setIndustry(platformTmpl.getIndustry());
            clone.setManagedObjectTypes(platformTmpl.getManagedObjectTypes());
            clone.setTenantOid(currentTenant);
            mapper.insert(clone);
            cloned++;
        }
        log.info("租户阶段模板克隆完成（industry={}）: 新增 {} 个", industry, cloned);
        return cloned;
    }

    @Override
    public List<StageTemplate> findPlatformTemplates() {
        return mapper.selectByTenant(platformOid());
    }

    private static void add(Map<String, StageTemplate> map, String code, String name, String icon, String color,
                            int sortOrder, String defaultFolders, String managedObjectTypes) {
        StageTemplate tmpl = new StageTemplate(code, name);
        tmpl.setOid(UUID.randomUUID().toString());
        tmpl.setIcon(icon);
        tmpl.setColor(color);
        tmpl.setSortOrder(sortOrder);
        tmpl.setDefaultFolders(defaultFolders);
        tmpl.setManagedObjectTypes(managedObjectTypes);
        map.put(code, tmpl);
    }
}