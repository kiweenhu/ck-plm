/*
 * Copyright (c) 2026~2028 深圳乘恺科技有限公司
 * All rights reserved.
 *
 * @author Kiween.Hu; Roney.Liu
 */

package cn.ck.plm.base.service.impl;

import cn.ck.plm.base.entity.DashboardTemplate;
import cn.ck.plm.base.mapper.DashboardTemplateMapper;
import cn.ck.plm.base.service.api.DashboardTemplateService;
import cn.ck.plm.base.util.TenantContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * 驾驶舱模板服务实现 —— 与前面七类模板服务同口径。
 *
 * <h3>写接口语义（与前端约定，勿改）</h3>
 * <b>字段缺席（null）= 本次不改；空串（""）= 显式清空</b>。UPDATE 是整行写法，
 * update 必须把没提交的字段逐个用原值兜底（交付件那一轮的血泪教训）。
 *
 * <h3>一处不自作聪明</h3>
 * {@code primaryScope}（主视角）来自包级 {@code primaryScopeType} —— <b>不由本服务强制"只能有一个"</b>：
 * 多租户环境下，企业若真把两个视角都标成主视角，那是它自己的数据选择，硬拦反而让人改不动。
 * 安装器保证包内语义正确（恰一条命中），界面上只做展示。
 */
@Service
public class DashboardTemplateServiceImpl implements DashboardTemplateService {

    private static final Logger log = LoggerFactory.getLogger(DashboardTemplateServiceImpl.class);

    private final DashboardTemplateMapper mapper;

    public DashboardTemplateServiceImpl(DashboardTemplateMapper mapper) {
        this.mapper = mapper;
    }

    private String tenantOid() { return TenantContext.get(); }
    private String platformOid() { return TenantContext.PLATFORM_TENANT_OID; }

    @Override
    @Transactional
    public DashboardTemplate create(DashboardTemplate template) {
        if (template == null || isBlank(template.getCode())) {
            throw new IllegalArgumentException("驾驶舱编码（视角类型）不能为空");
        }
        if (isBlank(template.getName())) {
            throw new IllegalArgumentException("驾驶舱名称不能为空");
        }
        template.setCode(template.getCode().trim());
        if (mapper.existsByCode(template.getCode(), tenantOid(), platformOid()) > 0) {
            throw new IllegalArgumentException("编码 '" + template.getCode() + "' 已存在");
        }
        if (template.getTenantOid() == null) template.setTenantOid(tenantOid());
        if (template.getPrimaryScope() == null) template.setPrimaryScope(false);
        if (template.getDisabled() == null) template.setDisabled(false);
        if (template.getSortOrder() == null) template.setSortOrder(0);
        normalizeBlanks(template);
        mapper.insert(template);
        return template;
    }

    @Override
    @Transactional
    public DashboardTemplate update(DashboardTemplate template) {
        if (template == null || isBlank(template.getOid())) {
            throw new IllegalArgumentException("oid 不能为空");
        }
        DashboardTemplate existing = mapper.selectByOid(template.getOid());
        if (existing == null) {
            throw new IllegalArgumentException("驾驶舱布局不存在");
        }
        TenantContext.requireEditPermission(existing.getTenantOid(), "驾驶舱布局");
        // code 是幂等键、tenant 是归属 —— 两者不可改：UPDATE 语句里也没有这两列
        template.setCode(existing.getCode());
        template.setTenantOid(existing.getTenantOid());
        // patch 语义：逐个字段兜底
        if (template.getName() == null) template.setName(existing.getName());
        if (template.getDescription() == null) template.setDescription(existing.getDescription());
        if (template.getIndustry() == null) template.setIndustry(existing.getIndustry());
        if (template.getAudience() == null) template.setAudience(existing.getAudience());
        if (template.getPageCount() == null) template.setPageCount(existing.getPageCount());
        if (template.getWidgets() == null) template.setWidgets(existing.getWidgets());
        if (template.getGeneratePolicy() == null) template.setGeneratePolicy(existing.getGeneratePolicy());
        if (template.getPrimaryScope() == null) template.setPrimaryScope(existing.getPrimaryScope());
        if (template.getDisabled() == null) template.setDisabled(existing.getDisabled());
        if (template.getSortOrder() == null) template.setSortOrder(existing.getSortOrder());
        normalizeBlanks(template);
        mapper.update(template);
        return mapper.selectByOid(template.getOid());
    }

    @Override
    @Transactional
    public boolean delete(String oid) {
        if (isBlank(oid)) return false;
        DashboardTemplate existing = mapper.selectByOid(oid);
        if (existing == null) return false;
        TenantContext.requireEditPermission(existing.getTenantOid(), "驾驶舱布局");
        mapper.deleteByOid(oid);
        log.info("驾驶舱布局已删除: {}（{}）—— 提醒：包重装会把它加回来，日常请用「停用」",
                existing.getCode(), existing.getName());
        return true;
    }

    @Override
    @Transactional
    public boolean setDisabled(String oid, boolean disabled) {
        if (isBlank(oid)) return false;
        DashboardTemplate existing = mapper.selectByOid(oid);
        if (existing == null) return false;
        TenantContext.requireEditPermission(existing.getTenantOid(), "驾驶舱布局");
        existing.setDisabled(disabled);
        mapper.update(existing);
        return true;
    }

    @Override
    public List<DashboardTemplate> findAll() {
        return mapper.selectByTenant(tenantOid());
    }

    @Override
    public List<DashboardTemplate> findAll(String industry) {
        return filterByIndustry(mapper.selectByTenant(tenantOid()), industry);
    }

    @Override
    public DashboardTemplate findByCode(String code) {
        if (isBlank(code)) return null;
        return mapper.selectByCode(code.trim(), tenantOid(), platformOid());
    }

    @Override
    public List<String> listPlatformIndustries() {
        Set<String> industries = new LinkedHashSet<>();
        for (DashboardTemplate t : mapper.selectByTenant(platformOid())) {
            if (!isBlank(t.getIndustry())) {
                industries.add(t.getIndustry());
            }
        }
        return new ArrayList<>(industries);
    }

    @Override
    public List<DashboardTemplate> findPlatformTemplates(String industry) {
        return filterByIndustry(mapper.selectByTenant(platformOid()), industry);
    }

    @Override
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
        List<DashboardTemplate> platformTemplates = findPlatformTemplates(industry);
        if (platformTemplates.isEmpty()) {
            log.warn("平台级驾驶舱布局为空（industry={}），无法克隆", industry);
            return 0;
        }
        int cloned = 0;
        int skipped = 0;
        for (DashboardTemplate source : platformTemplates) {
            // 幂等：本租户已有同码（含企业已停用的）→ 跳过，绝不覆盖企业裁剪
            if (mapper.existsByCode(source.getCode(), currentTenant, currentTenant) > 0) {
                skipped++;
                continue;
            }
            DashboardTemplate clone = new DashboardTemplate(source.getCode(), source.getName());
            clone.setOid(UUID.randomUUID().toString());
            clone.setDescription(source.getDescription());
            clone.setIndustry(source.getIndustry());
            clone.setAudience(source.getAudience());
            clone.setPageCount(source.getPageCount());
            clone.setWidgets(source.getWidgets());
            clone.setGeneratePolicy(source.getGeneratePolicy());
            clone.setPrimaryScope(source.getPrimaryScope());
            clone.setDisabled(false);
            clone.setSortOrder(source.getSortOrder());
            clone.setTenantOid(currentTenant);
            mapper.insert(clone);
            cloned++;
        }
        log.info("租户驾驶舱布局克隆完成（industry={}）: 新增 {}，跳过（已存在）{}", industry, cloned, skipped);
        return cloned;
    }

    /**
     * 按行业/方法论过滤：空参数 = 不过滤；
     * 非空时**通用条目（industry 为空）也算命中** —— 它的语义就是"不限定方法论"。
     */
    private List<DashboardTemplate> filterByIndustry(List<DashboardTemplate> all, String industry) {
        if (isBlank(industry)) {
            return all;
        }
        String wanted = industry.trim();
        List<DashboardTemplate> filtered = new ArrayList<>();
        for (DashboardTemplate t : all) {
            if (wanted.equals(t.getIndustry()) || isBlank(t.getIndustry())) {
                filtered.add(t);
            }
        }
        return filtered;
    }

    /**
     * 把"空串"归一为 NULL（前端用空串表达"清空这个字段"）。
     * 必须在"按原值兜底"<b>之后</b>调用，才能区分"缺席（不改）"与"空串（清空）"。
     */
    private void normalizeBlanks(DashboardTemplate template) {
        template.setDescription(blankToNull(template.getDescription()));
        template.setIndustry(blankToNull(template.getIndustry()));
        template.setAudience(blankToNull(template.getAudience()));
        template.setWidgets(blankToNull(template.getWidgets()));
        template.setGeneratePolicy(blankToNull(template.getGeneratePolicy()));
    }

    private static String blankToNull(String value) {
        return (value != null && value.trim().isEmpty()) ? null : value;
    }

    private static boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }
}
