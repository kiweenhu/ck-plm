/*
 * Copyright (c) 2026~2028 深圳乘恺科技有限公司
 * All rights reserved.
 *
 * @author Kiween.Hu; Roney.Liu
 */

package cn.ck.plm.base.service.impl;

import cn.ck.plm.base.entity.DeliverableTemplate;
import cn.ck.plm.base.mapper.DeliverableTemplateMapper;
import cn.ck.plm.base.service.api.DeliverableTemplateService;
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
 * 交付件模板服务实现 —— 与 {@link StageTemplateServiceImpl} 同口径。
 *
 * <h3>三层裁剪里的"企业层"就落在这里</h3>
 * 行业包把 46 条装进企业一份（见 {@code DeliverableInstaller}）；企业在这份上改强制、改完成条件、
 * 改责任人、<b>停用</b>。停用而不删除：保留行与幂等键（{@code code + tenant_oid}），
 * 重装行业包只会 SKIP —— 既不会把企业改过的值冲掉，也不会把停用的条目改回启用。
 *
 * <h3>通用条目</h3>
 * {@code industry} 为空 = 不限定方法论（任何行业都适用）。克隆与筛选都把它算进来。
 *
 * <h3>审计字段</h3>
 * 由 {@code AuditInterceptor} 自动填充（INSERT 填 creator/createdAt/tenantOid，
 * UPDATE 填 updater/updatedAt），本类不手工设置。
 */
@Service
public class DeliverableTemplateServiceImpl implements DeliverableTemplateService {

    private static final Logger log = LoggerFactory.getLogger(DeliverableTemplateServiceImpl.class);

    private final DeliverableTemplateMapper mapper;

    public DeliverableTemplateServiceImpl(DeliverableTemplateMapper mapper) {
        this.mapper = mapper;
    }

    private String tenantOid() { return TenantContext.get(); }
    private String platformOid() { return TenantContext.PLATFORM_TENANT_OID; }

    @Override
    @Transactional
    public DeliverableTemplate create(DeliverableTemplate template) {
        if (template == null || isBlank(template.getCode())) {
            throw new IllegalArgumentException("交付件编码不能为空");
        }
        if (isBlank(template.getName())) {
            throw new IllegalArgumentException("交付件名称不能为空");
        }
        if (isBlank(template.getStageKey())) {
            throw new IllegalArgumentException("所属研发阶段不能为空（交付件与阶段的关系靠它建立）");
        }
        template.setCode(template.getCode().trim());
        // 与阶段模板同款：平台已有同码时不允许在本租户再造一个（要差异化请克隆后再改）
        if (mapper.existsByCode(template.getCode().trim(), tenantOid(), platformOid()) > 0) {
            throw new IllegalArgumentException("编码 '" + template.getCode() + "' 已存在");
        }
        if (template.getTenantOid() == null) template.setTenantOid(tenantOid());
        if (template.getMandatory() == null) template.setMandatory(false);
        if (template.getDisabled() == null) template.setDisabled(false);
        if (template.getSortOrder() == null) template.setSortOrder(0);
        normalizeBlanks(template);
        mapper.insert(template);
        return template;
    }

    @Override
    @Transactional
    public DeliverableTemplate update(DeliverableTemplate template) {
        if (template == null || isBlank(template.getOid())) {
            throw new IllegalArgumentException("oid 不能为空");
        }
        DeliverableTemplate existing = mapper.selectByOid(template.getOid());
        if (existing == null) {
            throw new IllegalArgumentException("交付件模板不存在");
        }
        TenantContext.requireEditPermission(existing.getTenantOid(), "交付件模板");
        // code 是幂等键、tenant 是归属 —— 两者不可改：UPDATE 语句里也没有这两列
        template.setCode(existing.getCode());
        template.setTenantOid(existing.getTenantOid());
        // 本接口是 patch 语义，而 UPDATE 语句是整行写法 —— 逐字段兜齐：
        //   字段缺席（null）= 本次不改（沿用原值）；空串（""）= 显式清空（normalizeBlanks 归一为 NULL）。
        // 血泪教训：最初只兜底了 5 个字段，一次真实的裁剪测试就把没提到的
        // gate / completion_rule / owner_role / due_offset_days 全写空了 —— 漏一个字段就是一次数据损坏。
        if (template.getName() == null) template.setName(existing.getName());
        if (template.getDescription() == null) template.setDescription(existing.getDescription());
        if (template.getIndustry() == null) template.setIndustry(existing.getIndustry());
        if (template.getStageKey() == null) template.setStageKey(existing.getStageKey());
        if (template.getDiscipline() == null) template.setDiscipline(existing.getDiscipline());
        if (template.getMandatory() == null) template.setMandatory(existing.getMandatory());
        if (template.getOwnerRole() == null) template.setOwnerRole(existing.getOwnerRole());
        if (template.getObjectType() == null) template.setObjectType(existing.getObjectType());
        if (template.getBindMode() == null) template.setBindMode(existing.getBindMode());
        if (template.getCompletionRule() == null) template.setCompletionRule(existing.getCompletionRule());
        if (template.getDueOffsetDays() == null) template.setDueOffsetDays(existing.getDueOffsetDays());
        if (template.getGate() == null) template.setGate(existing.getGate());
        if (template.getIndustryNote() == null) template.setIndustryNote(existing.getIndustryNote());
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
        DeliverableTemplate existing = mapper.selectByOid(oid);
        if (existing == null) return false;
        TenantContext.requireEditPermission(existing.getTenantOid(), "交付件模板");
        mapper.deleteByOid(oid);
        log.info("交付件模板已删除: {}（{}）—— 提醒：包重装会把它加回来，日常请用「停用」",
                existing.getCode(), existing.getName());
        return true;
    }

    @Override
    @Transactional
    public boolean setDisabled(String oid, boolean disabled) {
        if (isBlank(oid)) return false;
        DeliverableTemplate existing = mapper.selectByOid(oid);
        if (existing == null) return false;
        TenantContext.requireEditPermission(existing.getTenantOid(), "交付件模板");
        existing.setDisabled(disabled);
        mapper.update(existing);
        return true;
    }

    @Override
    public List<DeliverableTemplate> findAll() {
        return mapper.selectByTenant(tenantOid());
    }

    @Override
    public List<DeliverableTemplate> findAll(String industry) {
        return filterByIndustry(mapper.selectByTenant(tenantOid()), industry);
    }

    @Override
    public DeliverableTemplate findByCode(String code) {
        if (isBlank(code)) return null;
        return mapper.selectByCode(code.trim(), tenantOid(), platformOid());
    }

    @Override
    public List<String> listPlatformIndustries() {
        Set<String> industries = new LinkedHashSet<>();
        for (DeliverableTemplate t : mapper.selectByTenant(platformOid())) {
            if (!isBlank(t.getIndustry())) {
                industries.add(t.getIndustry());
            }
        }
        return new ArrayList<>(industries);
    }

    @Override
    public List<DeliverableTemplate> findPlatformTemplates(String industry) {
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
        List<DeliverableTemplate> platformTemplates = findPlatformTemplates(industry);
        if (platformTemplates.isEmpty()) {
            log.warn("平台级交付件模板为空（industry={}），无法克隆", industry);
            return 0;
        }
        int cloned = 0;
        int skipped = 0;
        for (DeliverableTemplate source : platformTemplates) {
            // 幂等：本租户已有同码（含企业已停用的）→ 跳过，绝不覆盖企业裁剪
            if (mapper.existsByCode(source.getCode(), currentTenant, currentTenant) > 0) {
                skipped++;
                continue;
            }
            DeliverableTemplate clone = new DeliverableTemplate(source.getCode(), source.getName());
            clone.setOid(UUID.randomUUID().toString());
            clone.setDescription(source.getDescription());
            clone.setIndustry(source.getIndustry());
            clone.setStageKey(source.getStageKey());
            clone.setDiscipline(source.getDiscipline());
            clone.setMandatory(source.getMandatory());
            clone.setOwnerRole(source.getOwnerRole());
            clone.setObjectType(source.getObjectType());
            clone.setBindMode(source.getBindMode());
            clone.setCompletionRule(source.getCompletionRule());
            clone.setDueOffsetDays(source.getDueOffsetDays());
            clone.setGate(source.getGate());
            clone.setIndustryNote(source.getIndustryNote());
            clone.setDisabled(false);
            clone.setSortOrder(source.getSortOrder());
            clone.setTenantOid(currentTenant);
            mapper.insert(clone);
            cloned++;
        }
        log.info("租户交付件模板克隆完成（industry={}）: 新增 {}，跳过（已存在）{}", industry, cloned, skipped);
        return cloned;
    }

    /**
     * 按行业/方法论过滤：空参数 = 不过滤；
     * 非空时**通用条目（industry 为空）也算命中** —— 它的语义就是"不限定方法论"。
     */
    private List<DeliverableTemplate> filterByIndustry(List<DeliverableTemplate> all, String industry) {
        if (isBlank(industry)) {
            return all;
        }
        String wanted = industry.trim();
        List<DeliverableTemplate> filtered = new ArrayList<>();
        for (DeliverableTemplate t : all) {
            if (wanted.equals(t.getIndustry()) || isBlank(t.getIndustry())) {
                filtered.add(t);
            }
        }
        return filtered;
    }

    /**
     * 把"空串"归一为 NULL —— 前端用空串表达"清空这个字段"，库里不该同时存在 '' 与 NULL 两种"空"。
     *
     * <p>必须在"按原值兜底"<b>之后</b>调用：先兜底再归一，才能区分
     * "缺席（不改）"与"空串（清空）"两件事。
     */
    private void normalizeBlanks(DeliverableTemplate template) {
        template.setDescription(blankToNull(template.getDescription()));
        template.setIndustry(blankToNull(template.getIndustry()));
        template.setGate(blankToNull(template.getGate()));
        template.setDiscipline(blankToNull(template.getDiscipline()));
        template.setOwnerRole(blankToNull(template.getOwnerRole()));
        template.setObjectType(blankToNull(template.getObjectType()));
        template.setBindMode(blankToNull(template.getBindMode()));
        template.setCompletionRule(blankToNull(template.getCompletionRule()));
        template.setIndustryNote(blankToNull(template.getIndustryNote()));
    }

    private static String blankToNull(String value) {
        return (value != null && value.trim().isEmpty()) ? null : value;
    }

    private static boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }
}
