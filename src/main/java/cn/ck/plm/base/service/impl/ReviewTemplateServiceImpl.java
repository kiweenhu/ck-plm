/*
 * Copyright (c) 2026~2028 深圳乘恺科技有限公司
 * All rights reserved.
 *
 * @author Kiween.Hu; Roney.Liu
 */

package cn.ck.plm.base.service.impl;

import cn.ck.plm.base.entity.ReviewTemplate;
import cn.ck.plm.base.mapper.ReviewTemplateMapper;
import cn.ck.plm.base.service.api.ReviewTemplateService;
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
 * 评审模板服务实现 —— 与交付件 / 门模板服务同口径。
 *
 * <h3>写接口语义（与前端约定，勿改）</h3>
 * <b>字段缺席（null）= 本次不改；空串（""）= 显式清空</b>。
 * UPDATE 是整行写法，update 必须把调用方没提交的字段<b>逐个</b>用原值兜底 ——
 * 交付件那一轮曾因只兜底 5 个字段而把其余列写空，这里一次兜齐。
 *
 * <h3>两处不变量</h3>
 * <ol>
 *   <li>{@code gate} 必填：一份评审必须挂在某道门上（这是"评审属于哪道门"的关系）；</li>
 *   <li>{@code type} 必填且只允许 {@code TECHNICAL} / {@code DECISION}（与门的类型口径对齐）。</li>
 * </ol>
 */
@Service
public class ReviewTemplateServiceImpl implements ReviewTemplateService {

    private static final Logger log = LoggerFactory.getLogger(ReviewTemplateServiceImpl.class);

    /** 评审类型：技术评审 */
    private static final String TYPE_TECHNICAL = "TECHNICAL";
    /** 评审类型：决策类评审 */
    private static final String TYPE_DECISION = "DECISION";

    private final ReviewTemplateMapper mapper;

    public ReviewTemplateServiceImpl(ReviewTemplateMapper mapper) {
        this.mapper = mapper;
    }

    private String tenantOid() { return TenantContext.get(); }
    private String platformOid() { return TenantContext.PLATFORM_TENANT_OID; }

    @Override
    @Transactional
    public ReviewTemplate create(ReviewTemplate template) {
        if (template == null || isBlank(template.getCode())) {
            throw new IllegalArgumentException("评审模板编码不能为空");
        }
        if (isBlank(template.getName())) {
            throw new IllegalArgumentException("评审模板名称不能为空");
        }
        if (isBlank(template.getGate())) {
            throw new IllegalArgumentException("归属阶段门不能为空（评审挂在哪道门上靠它建立）");
        }
        if (isBlank(template.getType())) {
            template.setType(TYPE_TECHNICAL);
        }
        template.setType(template.getType().trim().toUpperCase());
        if (!TYPE_TECHNICAL.equals(template.getType()) && !TYPE_DECISION.equals(template.getType())) {
            throw new IllegalArgumentException("评审类型只能是 " + TYPE_TECHNICAL + " 或 " + TYPE_DECISION);
        }
        template.setCode(template.getCode().trim());
        if (mapper.existsByCode(template.getCode(), tenantOid(), platformOid()) > 0) {
            throw new IllegalArgumentException("编码 '" + template.getCode() + "' 已存在");
        }
        if (template.getTenantOid() == null) template.setTenantOid(tenantOid());
        if (template.getDisabled() == null) template.setDisabled(false);
        if (template.getSortOrder() == null) template.setSortOrder(0);
        normalizeBlanks(template);
        mapper.insert(template);
        return template;
    }

    @Override
    @Transactional
    public ReviewTemplate update(ReviewTemplate template) {
        if (template == null || isBlank(template.getOid())) {
            throw new IllegalArgumentException("oid 不能为空");
        }
        ReviewTemplate existing = mapper.selectByOid(template.getOid());
        if (existing == null) {
            throw new IllegalArgumentException("评审模板不存在");
        }
        TenantContext.requireEditPermission(existing.getTenantOid(), "评审模板");
        // code 是幂等键、tenant 是归属 —— 两者不可改：UPDATE 语句里也没有这两列
        template.setCode(existing.getCode());
        template.setTenantOid(existing.getTenantOid());
        // patch 语义：逐个字段兜底
        if (template.getName() == null) template.setName(existing.getName());
        if (template.getDescription() == null) template.setDescription(existing.getDescription());
        if (template.getIndustry() == null) template.setIndustry(existing.getIndustry());
        if (template.getGate() == null) template.setGate(existing.getGate());
        if (template.getType() == null) template.setType(existing.getType());
        if (template.getProcessKey() == null) template.setProcessKey(existing.getProcessKey());
        if (template.getCheckItems() == null) template.setCheckItems(existing.getCheckItems());
        if (template.getOutputTypes() == null) template.setOutputTypes(existing.getOutputTypes());
        if (template.getVerdictOptions() == null) template.setVerdictOptions(existing.getVerdictOptions());
        if (template.getIndustryNote() == null) template.setIndustryNote(existing.getIndustryNote());
        if (template.getDisabled() == null) template.setDisabled(existing.getDisabled());
        if (template.getSortOrder() == null) template.setSortOrder(existing.getSortOrder());
        // 类型若被改成非法值，宁可拒绝也不写脏数据
        if (template.getType() != null) {
            template.setType(template.getType().trim().toUpperCase());
            if (!TYPE_TECHNICAL.equals(template.getType()) && !TYPE_DECISION.equals(template.getType())) {
                throw new IllegalArgumentException("评审类型只能是 " + TYPE_TECHNICAL + " 或 " + TYPE_DECISION);
            }
        }
        normalizeBlanks(template);
        mapper.update(template);
        return mapper.selectByOid(template.getOid());
    }

    @Override
    @Transactional
    public boolean delete(String oid) {
        if (isBlank(oid)) return false;
        ReviewTemplate existing = mapper.selectByOid(oid);
        if (existing == null) return false;
        TenantContext.requireEditPermission(existing.getTenantOid(), "评审模板");
        mapper.deleteByOid(oid);
        log.info("评审模板已删除: {}（{}）—— 提醒：包重装会把它加回来，日常请用「停用」",
                existing.getCode(), existing.getName());
        return true;
    }

    @Override
    @Transactional
    public boolean setDisabled(String oid, boolean disabled) {
        if (isBlank(oid)) return false;
        ReviewTemplate existing = mapper.selectByOid(oid);
        if (existing == null) return false;
        TenantContext.requireEditPermission(existing.getTenantOid(), "评审模板");
        existing.setDisabled(disabled);
        mapper.update(existing);
        return true;
    }

    @Override
    public List<ReviewTemplate> findAll() {
        return mapper.selectByTenant(tenantOid());
    }

    @Override
    public List<ReviewTemplate> findAll(String industry) {
        return filterByIndustry(mapper.selectByTenant(tenantOid()), industry);
    }

    @Override
    public ReviewTemplate findByCode(String code) {
        if (isBlank(code)) return null;
        return mapper.selectByCode(code.trim(), tenantOid(), platformOid());
    }

    @Override
    public List<String> listPlatformIndustries() {
        Set<String> industries = new LinkedHashSet<>();
        for (ReviewTemplate t : mapper.selectByTenant(platformOid())) {
            if (!isBlank(t.getIndustry())) {
                industries.add(t.getIndustry());
            }
        }
        return new ArrayList<>(industries);
    }

    @Override
    public List<ReviewTemplate> findPlatformTemplates(String industry) {
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
        List<ReviewTemplate> platformTemplates = findPlatformTemplates(industry);
        if (platformTemplates.isEmpty()) {
            log.warn("平台级评审模板为空（industry={}），无法克隆", industry);
            return 0;
        }
        int cloned = 0;
        int skipped = 0;
        for (ReviewTemplate source : platformTemplates) {
            // 幂等：本租户已有同码（含企业已停用的）→ 跳过，绝不覆盖企业裁剪
            if (mapper.existsByCode(source.getCode(), currentTenant, currentTenant) > 0) {
                skipped++;
                continue;
            }
            ReviewTemplate clone = new ReviewTemplate(source.getCode(), source.getName());
            clone.setOid(UUID.randomUUID().toString());
            clone.setDescription(source.getDescription());
            clone.setIndustry(source.getIndustry());
            clone.setGate(source.getGate());
            clone.setType(source.getType());
            clone.setProcessKey(source.getProcessKey());
            clone.setCheckItems(source.getCheckItems());
            clone.setOutputTypes(source.getOutputTypes());
            clone.setVerdictOptions(source.getVerdictOptions());
            clone.setIndustryNote(source.getIndustryNote());
            clone.setDisabled(false);
            clone.setSortOrder(source.getSortOrder());
            clone.setTenantOid(currentTenant);
            mapper.insert(clone);
            cloned++;
        }
        log.info("租户评审模板克隆完成（industry={}）: 新增 {}，跳过（已存在）{}", industry, cloned, skipped);
        return cloned;
    }

    /**
     * 按行业/方法论过滤：空参数 = 不过滤；
     * 非空时**通用条目（industry 为空）也算命中** —— 它的语义就是"不限定方法论"。
     */
    private List<ReviewTemplate> filterByIndustry(List<ReviewTemplate> all, String industry) {
        if (isBlank(industry)) {
            return all;
        }
        String wanted = industry.trim();
        List<ReviewTemplate> filtered = new ArrayList<>();
        for (ReviewTemplate t : all) {
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
    private void normalizeBlanks(ReviewTemplate template) {
        template.setDescription(blankToNull(template.getDescription()));
        template.setIndustry(blankToNull(template.getIndustry()));
        template.setProcessKey(blankToNull(template.getProcessKey()));
        template.setCheckItems(blankToNull(template.getCheckItems()));
        template.setOutputTypes(blankToNull(template.getOutputTypes()));
        template.setVerdictOptions(blankToNull(template.getVerdictOptions()));
        template.setIndustryNote(blankToNull(template.getIndustryNote()));
    }

    private static String blankToNull(String value) {
        return (value != null && value.trim().isEmpty()) ? null : value;
    }

    private static boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }
}
