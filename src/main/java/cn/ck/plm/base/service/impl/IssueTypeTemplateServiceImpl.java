/*
 * Copyright (c) 2026~2028 深圳乘恺科技有限公司
 * All rights reserved.
 *
 * @author Kiween.Hu; Roney.Liu
 */

package cn.ck.plm.base.service.impl;

import cn.ck.plm.base.entity.IssueTypeTemplate;
import cn.ck.plm.base.mapper.IssueTypeTemplateMapper;
import cn.ck.plm.base.service.api.IssueTypeTemplateService;
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
 * 问题类型模板服务实现 —— 与前面五类模板服务同口径。
 *
 * <h3>写接口语义（与前端约定，勿改）</h3>
 * <b>字段缺席（null）= 本次不改；空串（""）= 显式清空</b>。UPDATE 是整行写法，
 * update 必须把没提交的字段逐个用原值兜底。</p>
 *
 * <p>严重度只有 {@code BLOCK} / {@code WARN} 两种 —— 与检查规则、阶段门同一口径，
 * 服务层把关，不让第三个值混进来。
 */
@Service
public class IssueTypeTemplateServiceImpl implements IssueTypeTemplateService {

    private static final Logger log = LoggerFactory.getLogger(IssueTypeTemplateServiceImpl.class);

    /** 默认严重度（与检查规则、阶段门同一口径） */
    private static final Set<String> SEVERITIES = new LinkedHashSet<>(List.of("BLOCK", "WARN"));

    private final IssueTypeTemplateMapper mapper;

    public IssueTypeTemplateServiceImpl(IssueTypeTemplateMapper mapper) {
        this.mapper = mapper;
    }

    private String tenantOid() { return TenantContext.get(); }
    private String platformOid() { return TenantContext.PLATFORM_TENANT_OID; }

    @Override
    @Transactional
    public IssueTypeTemplate create(IssueTypeTemplate template) {
        if (template == null || isBlank(template.getCode())) {
            throw new IllegalArgumentException("问题类型编码不能为空");
        }
        if (isBlank(template.getName())) {
            throw new IllegalArgumentException("问题类型名称不能为空");
        }
        template.setDefaultSeverity(normalizeSeverity(template.getDefaultSeverity()));
        template.setCode(template.getCode().trim());
        if (mapper.existsByCode(template.getCode(), tenantOid(), platformOid()) > 0) {
            throw new IllegalArgumentException("编码 '" + template.getCode() + "' 已存在");
        }
        if (template.getTenantOid() == null) template.setTenantOid(tenantOid());
        if (template.getRequireRootCause() == null) template.setRequireRootCause(false);
        if (template.getRequireRetest() == null) template.setRequireRetest(false);
        if (template.getDisabled() == null) template.setDisabled(false);
        if (template.getSortOrder() == null) template.setSortOrder(0);
        normalizeBlanks(template);
        mapper.insert(template);
        return template;
    }

    @Override
    @Transactional
    public IssueTypeTemplate update(IssueTypeTemplate template) {
        if (template == null || isBlank(template.getOid())) {
            throw new IllegalArgumentException("oid 不能为空");
        }
        IssueTypeTemplate existing = mapper.selectByOid(template.getOid());
        if (existing == null) {
            throw new IllegalArgumentException("问题类型不存在");
        }
        TenantContext.requireEditPermission(existing.getTenantOid(), "问题类型");
        // code 是幂等键、tenant 是归属 —— 两者不可改：UPDATE 语句里也没有这两列
        template.setCode(existing.getCode());
        template.setTenantOid(existing.getTenantOid());
        // patch 语义：逐个字段兜底
        if (template.getName() == null) template.setName(existing.getName());
        if (template.getDescription() == null) template.setDescription(existing.getDescription());
        if (template.getIndustry() == null) template.setIndustry(existing.getIndustry());
        if (template.getDefaultSeverity() == null) template.setDefaultSeverity(existing.getDefaultSeverity());
        if (template.getRequireRootCause() == null) template.setRequireRootCause(existing.getRequireRootCause());
        if (template.getRequireRetest() == null) template.setRequireRetest(existing.getRequireRetest());
        if (template.getIndustryNote() == null) template.setIndustryNote(existing.getIndustryNote());
        if (template.getDisabled() == null) template.setDisabled(existing.getDisabled());
        if (template.getSortOrder() == null) template.setSortOrder(existing.getSortOrder());
        template.setDefaultSeverity(normalizeSeverity(template.getDefaultSeverity()));
        normalizeBlanks(template);
        mapper.update(template);
        return mapper.selectByOid(template.getOid());
    }

    @Override
    @Transactional
    public boolean delete(String oid) {
        if (isBlank(oid)) return false;
        IssueTypeTemplate existing = mapper.selectByOid(oid);
        if (existing == null) return false;
        TenantContext.requireEditPermission(existing.getTenantOid(), "问题类型");
        mapper.deleteByOid(oid);
        log.info("问题类型已删除: {}（{}）—— 提醒：包重装会把它加回来，日常请用「停用」",
                existing.getCode(), existing.getName());
        return true;
    }

    @Override
    @Transactional
    public boolean setDisabled(String oid, boolean disabled) {
        if (isBlank(oid)) return false;
        IssueTypeTemplate existing = mapper.selectByOid(oid);
        if (existing == null) return false;
        TenantContext.requireEditPermission(existing.getTenantOid(), "问题类型");
        existing.setDisabled(disabled);
        mapper.update(existing);
        return true;
    }

    @Override
    public List<IssueTypeTemplate> findAll() {
        return mapper.selectByTenant(tenantOid());
    }

    @Override
    public List<IssueTypeTemplate> findAll(String industry) {
        return filterByIndustry(mapper.selectByTenant(tenantOid()), industry);
    }

    @Override
    public IssueTypeTemplate findByCode(String code) {
        if (isBlank(code)) return null;
        return mapper.selectByCode(code.trim(), tenantOid(), platformOid());
    }

    @Override
    public List<String> listPlatformIndustries() {
        Set<String> industries = new LinkedHashSet<>();
        for (IssueTypeTemplate t : mapper.selectByTenant(platformOid())) {
            if (!isBlank(t.getIndustry())) {
                industries.add(t.getIndustry());
            }
        }
        return new ArrayList<>(industries);
    }

    @Override
    public List<IssueTypeTemplate> findPlatformTemplates(String industry) {
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
        List<IssueTypeTemplate> platformTemplates = findPlatformTemplates(industry);
        if (platformTemplates.isEmpty()) {
            log.warn("平台级问题类型为空（industry={}），无法克隆", industry);
            return 0;
        }
        int cloned = 0;
        int skipped = 0;
        for (IssueTypeTemplate source : platformTemplates) {
            // 幂等：本租户已有同码（含企业已停用的）→ 跳过，绝不覆盖企业裁剪
            if (mapper.existsByCode(source.getCode(), currentTenant, currentTenant) > 0) {
                skipped++;
                continue;
            }
            IssueTypeTemplate clone = new IssueTypeTemplate(source.getCode(), source.getName());
            clone.setOid(UUID.randomUUID().toString());
            clone.setDescription(source.getDescription());
            clone.setIndustry(source.getIndustry());
            clone.setDefaultSeverity(source.getDefaultSeverity());
            clone.setRequireRootCause(source.getRequireRootCause());
            clone.setRequireRetest(source.getRequireRetest());
            clone.setIndustryNote(source.getIndustryNote());
            clone.setDisabled(false);
            clone.setSortOrder(source.getSortOrder());
            clone.setTenantOid(currentTenant);
            mapper.insert(clone);
            cloned++;
        }
        log.info("租户问题类型克隆完成（industry={}）: 新增 {}，跳过（已存在）{}", industry, cloned, skipped);
        return cloned;
    }

    /**
     * 按行业/方法论过滤：空参数 = 不过滤；
     * 非空时**通用条目（industry 为空）也算命中** —— 它的语义就是"不限定方法论"。
     */
    private List<IssueTypeTemplate> filterByIndustry(List<IssueTypeTemplate> all, String industry) {
        if (isBlank(industry)) {
            return all;
        }
        String wanted = industry.trim();
        List<IssueTypeTemplate> filtered = new ArrayList<>();
        for (IssueTypeTemplate t : all) {
            if (wanted.equals(t.getIndustry()) || isBlank(t.getIndustry())) {
                filtered.add(t);
            }
        }
        return filtered;
    }

    /** 严重度归一 + 越界即拒（错误消息里带上允许取值） */
    private String normalizeSeverity(String value) {
        if (isBlank(value)) {
            throw new IllegalArgumentException("默认严重度不能为空，取值只能是 " + SEVERITIES);
        }
        String normalized = value.trim().toUpperCase();
        if (!SEVERITIES.contains(normalized)) {
            throw new IllegalArgumentException("默认严重度 '" + value + "' 非法，取值只能是 " + SEVERITIES);
        }
        return normalized;
    }

    /**
     * 把"空串"归一为 NULL（前端用空串表达"清空这个字段"）。
     * 必须在"按原值兜底"<b>之后</b>调用，才能区分"缺席（不改）"与"空串（清空）"。
     */
    private void normalizeBlanks(IssueTypeTemplate template) {
        template.setDescription(blankToNull(template.getDescription()));
        template.setIndustry(blankToNull(template.getIndustry()));
        template.setIndustryNote(blankToNull(template.getIndustryNote()));
    }

    private static String blankToNull(String value) {
        return (value != null && value.trim().isEmpty()) ? null : value;
    }

    private static boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }
}
