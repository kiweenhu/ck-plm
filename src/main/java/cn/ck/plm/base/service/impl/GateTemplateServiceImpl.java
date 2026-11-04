/*
 * Copyright (c) 2026~2028 深圳乘恺科技有限公司
 * All rights reserved.
 *
 * @author Kiween.Hu; Roney.Liu
 */

package cn.ck.plm.base.service.impl;

import cn.ck.plm.base.entity.GateTemplate;
import cn.ck.plm.base.mapper.GateTemplateMapper;
import cn.ck.plm.base.service.api.GateTemplateService;
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
 * 阶段门模板服务实现 —— 与 {@link DeliverableTemplateServiceImpl} 同口径。
 *
 * <h3>写接口语义（与前端约定，勿改）</h3>
 * <b>字段缺席（null）= 本次不改；空串（""）= 显式清空</b>。
 * UPDATE 语句是整行写法，所以 update 必须把调用方没提交的字段<b>逐个</b>用原值兜底 ——
 * 交付件那一轮曾经只兜底了 5 个字段，一次真实裁剪就把没提到的列写空了：漏一个字段就是一次数据损坏。
 *
 * <h3>门的两处不变量</h3>
 * <ol>
 *   <li>{@code stageKey} 必填（门属于阶段，这是与研发阶段的关系）；</li>
 *   <li>{@code type} 必填，且只有 {@code TECHNICAL_REVIEW} / {@code DECISION_REVIEW} 两种 ——
 *       技术评审与决策评审分开建模是文档定死的口径，不让第三个值混进来。</li>
 * </ol>
 */
@Service
public class GateTemplateServiceImpl implements GateTemplateService {

    private static final Logger log = LoggerFactory.getLogger(GateTemplateServiceImpl.class);

    /** 门类型：技术评审（产出问题清单与整改项） */
    private static final String TYPE_TECHNICAL = "TECHNICAL_REVIEW";
    /** 门类型：决策评审（产出 Go/Hold/Terminate 与商业留痕） */
    private static final String TYPE_DECISION = "DECISION_REVIEW";

    private final GateTemplateMapper mapper;

    public GateTemplateServiceImpl(GateTemplateMapper mapper) {
        this.mapper = mapper;
    }

    private String tenantOid() { return TenantContext.get(); }
    private String platformOid() { return TenantContext.PLATFORM_TENANT_OID; }

    @Override
    @Transactional
    public GateTemplate create(GateTemplate template) {
        if (template == null || isBlank(template.getCode())) {
            throw new IllegalArgumentException("阶段门编码不能为空");
        }
        if (isBlank(template.getName())) {
            throw new IllegalArgumentException("阶段门名称不能为空");
        }
        if (isBlank(template.getStageKey())) {
            throw new IllegalArgumentException("所属研发阶段不能为空（门与阶段的关系靠它建立）");
        }
        if (isBlank(template.getType())) {
            throw new IllegalArgumentException("门类型不能为空（技术评审 / 决策评审）");
        }
        template.setType(template.getType().trim().toUpperCase());
        if (!TYPE_TECHNICAL.equals(template.getType()) && !TYPE_DECISION.equals(template.getType())) {
            throw new IllegalArgumentException("门类型只能是 " + TYPE_TECHNICAL + " 或 " + TYPE_DECISION);
        }
        template.setCode(template.getCode().trim());
        if (mapper.existsByCode(template.getCode(), tenantOid(), platformOid()) > 0) {
            throw new IllegalArgumentException("编码 '" + template.getCode() + "' 已存在");
        }
        if (template.getTenantOid() == null) template.setTenantOid(tenantOid());
        if (template.getEvidenceRequired() == null) template.setEvidenceRequired(true);
        if (template.getDisabled() == null) template.setDisabled(false);
        if (template.getSortOrder() == null) template.setSortOrder(0);
        normalizeBlanks(template);
        mapper.insert(template);
        return template;
    }

    @Override
    @Transactional
    public GateTemplate update(GateTemplate template) {
        if (template == null || isBlank(template.getOid())) {
            throw new IllegalArgumentException("oid 不能为空");
        }
        GateTemplate existing = mapper.selectByOid(template.getOid());
        if (existing == null) {
            throw new IllegalArgumentException("阶段门模板不存在");
        }
        TenantContext.requireEditPermission(existing.getTenantOid(), "阶段门模板");
        // code 是幂等键、tenant 是归属 —— 两者不可改：UPDATE 语句里也没有这两列
        template.setCode(existing.getCode());
        template.setTenantOid(existing.getTenantOid());
        // patch 语义：逐个字段兜底（详见类注释里那条教训）
        if (template.getName() == null) template.setName(existing.getName());
        if (template.getDescription() == null) template.setDescription(existing.getDescription());
        if (template.getIndustry() == null) template.setIndustry(existing.getIndustry());
        if (template.getStageKey() == null) template.setStageKey(existing.getStageKey());
        if (template.getType() == null) template.setType(existing.getType());
        if (template.getProcessKey() == null) template.setProcessKey(existing.getProcessKey());
        if (template.getDecisionOptions() == null) template.setDecisionOptions(existing.getDecisionOptions());
        if (template.getAutoChecks() == null) template.setAutoChecks(existing.getAutoChecks());
        if (template.getRequiredParticipants() == null) template.setRequiredParticipants(existing.getRequiredParticipants());
        if (template.getRecordFields() == null) template.setRecordFields(existing.getRecordFields());
        if (template.getEvidenceRequired() == null) template.setEvidenceRequired(existing.getEvidenceRequired());
        if (template.getIndustryNote() == null) template.setIndustryNote(existing.getIndustryNote());
        if (template.getDisabled() == null) template.setDisabled(existing.getDisabled());
        if (template.getSortOrder() == null) template.setSortOrder(existing.getSortOrder());
        // 门类型若被改成非法值，宁可拒绝也不写脏数据
        if (template.getType() != null) {
            template.setType(template.getType().trim().toUpperCase());
            if (!TYPE_TECHNICAL.equals(template.getType()) && !TYPE_DECISION.equals(template.getType())) {
                throw new IllegalArgumentException("门类型只能是 " + TYPE_TECHNICAL + " 或 " + TYPE_DECISION);
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
        GateTemplate existing = mapper.selectByOid(oid);
        if (existing == null) return false;
        TenantContext.requireEditPermission(existing.getTenantOid(), "阶段门模板");
        mapper.deleteByOid(oid);
        log.info("阶段门模板已删除: {}（{}）—— 提醒：包重装会把它加回来，日常请用「停用」",
                existing.getCode(), existing.getName());
        return true;
    }

    @Override
    @Transactional
    public boolean setDisabled(String oid, boolean disabled) {
        if (isBlank(oid)) return false;
        GateTemplate existing = mapper.selectByOid(oid);
        if (existing == null) return false;
        TenantContext.requireEditPermission(existing.getTenantOid(), "阶段门模板");
        existing.setDisabled(disabled);
        mapper.update(existing);
        return true;
    }

    @Override
    public List<GateTemplate> findAll() {
        return mapper.selectByTenant(tenantOid());
    }

    @Override
    public List<GateTemplate> findAll(String industry) {
        return filterByIndustry(mapper.selectByTenant(tenantOid()), industry);
    }

    @Override
    public GateTemplate findByCode(String code) {
        if (isBlank(code)) return null;
        return mapper.selectByCode(code.trim(), tenantOid(), platformOid());
    }

    @Override
    public List<String> listPlatformIndustries() {
        Set<String> industries = new LinkedHashSet<>();
        for (GateTemplate t : mapper.selectByTenant(platformOid())) {
            if (!isBlank(t.getIndustry())) {
                industries.add(t.getIndustry());
            }
        }
        return new ArrayList<>(industries);
    }

    @Override
    public List<GateTemplate> findPlatformTemplates(String industry) {
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
        List<GateTemplate> platformTemplates = findPlatformTemplates(industry);
        if (platformTemplates.isEmpty()) {
            log.warn("平台级阶段门模板为空（industry={}），无法克隆", industry);
            return 0;
        }
        int cloned = 0;
        int skipped = 0;
        for (GateTemplate source : platformTemplates) {
            // 幂等：本租户已有同码（含企业已停用的）→ 跳过，绝不覆盖企业裁剪
            if (mapper.existsByCode(source.getCode(), currentTenant, currentTenant) > 0) {
                skipped++;
                continue;
            }
            GateTemplate clone = new GateTemplate(source.getCode(), source.getName());
            clone.setOid(UUID.randomUUID().toString());
            clone.setDescription(source.getDescription());
            clone.setIndustry(source.getIndustry());
            clone.setStageKey(source.getStageKey());
            clone.setType(source.getType());
            clone.setProcessKey(source.getProcessKey());
            clone.setDecisionOptions(source.getDecisionOptions());
            clone.setAutoChecks(source.getAutoChecks());
            clone.setRequiredParticipants(source.getRequiredParticipants());
            clone.setRecordFields(source.getRecordFields());
            clone.setEvidenceRequired(source.getEvidenceRequired());
            clone.setIndustryNote(source.getIndustryNote());
            clone.setDisabled(false);
            clone.setSortOrder(source.getSortOrder());
            clone.setTenantOid(currentTenant);
            mapper.insert(clone);
            cloned++;
        }
        log.info("租户阶段门模板克隆完成（industry={}）: 新增 {}，跳过（已存在）{}", industry, cloned, skipped);
        return cloned;
    }

    /**
     * 按行业/方法论过滤：空参数 = 不过滤；
     * 非空时**通用条目（industry 为空）也算命中** —— 它的语义就是"不限定方法论"。
     */
    private List<GateTemplate> filterByIndustry(List<GateTemplate> all, String industry) {
        if (isBlank(industry)) {
            return all;
        }
        String wanted = industry.trim();
        List<GateTemplate> filtered = new ArrayList<>();
        for (GateTemplate t : all) {
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
    private void normalizeBlanks(GateTemplate template) {
        template.setDescription(blankToNull(template.getDescription()));
        template.setIndustry(blankToNull(template.getIndustry()));
        template.setProcessKey(blankToNull(template.getProcessKey()));
        template.setDecisionOptions(blankToNull(template.getDecisionOptions()));
        template.setAutoChecks(blankToNull(template.getAutoChecks()));
        template.setRequiredParticipants(blankToNull(template.getRequiredParticipants()));
        template.setRecordFields(blankToNull(template.getRecordFields()));
        template.setIndustryNote(blankToNull(template.getIndustryNote()));
    }

    private static String blankToNull(String value) {
        return (value != null && value.trim().isEmpty()) ? null : value;
    }

    private static boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }
}
