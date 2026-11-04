/*
 * Copyright (c) 2026~2028 深圳乘恺科技有限公司
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

    // 平台级默认阶段模板的播种已移入行业包（platform-base 三套通用方法论 + smart-hardware-ipd 的 IPD 六阶段），
    // 由 IndustryPackBootstrap 在启动时安装；原先的静态 map 与 initPlatformDefaults() 一并退场。
    // 本服务保留「读模板 + 克隆到租户」的职责。

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

}
