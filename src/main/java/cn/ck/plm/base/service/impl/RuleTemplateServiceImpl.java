/*
 * Copyright (c) 2026~2028 深圳乘恺科技有限公司
 * All rights reserved.
 *
 * @author Kiween.Hu; Roney.Liu
 */

package cn.ck.plm.base.service.impl;

import cn.ck.plm.base.entity.RuleTemplate;
import cn.ck.plm.base.mapper.RuleTemplateMapper;
import cn.ck.plm.base.service.api.RuleTemplateService;
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
 * 检查规则模板服务实现 —— 与前面四类模板服务同口径。
 *
 * <h3>写接口语义（与前端约定，勿改）</h3>
 * <b>字段缺席（null）= 本次不改；空串（""）= 显式清空</b>。UPDATE 是整行写法，
 * update 必须把没提交的字段逐个用原值兜底（交付件那一轮的血泪教训）。
 *
 * <h3>四组枚举在服务层同样把关</h3>
 * 时机 / 对象 / 严重度三组是结构性取值，界面与接口都不该写进越界值；
 * {@code condition.type} 属 JSON 内容，由前端做 JSON 合法性校验、运行期引擎解释。
 */
@Service
public class RuleTemplateServiceImpl implements RuleTemplateService {

    private static final Logger log = LoggerFactory.getLogger(RuleTemplateServiceImpl.class);

    /** 检查时机（与包内 note 同口径） */
    private static final Set<String> TRIGGERS = new LinkedHashSet<>(List.of(
            "STAGE_ENTRY", "PART_SAVE", "BOM_SAVE", "GATE_CHECK", "RELEASE"));
    /** 检查对象 */
    private static final Set<String> SCOPES = new LinkedHashSet<>(List.of(
            "PRODUCT_MODEL", "PART", "BOM"));
    /** 严重度 */
    private static final Set<String> SEVERITIES = new LinkedHashSet<>(List.of("BLOCK", "WARN"));

    private final RuleTemplateMapper mapper;

    public RuleTemplateServiceImpl(RuleTemplateMapper mapper) {
        this.mapper = mapper;
    }

    private String tenantOid() { return TenantContext.get(); }
    private String platformOid() { return TenantContext.PLATFORM_TENANT_OID; }

    @Override
    @Transactional
    public RuleTemplate create(RuleTemplate template) {
        if (template == null || isBlank(template.getCode())) {
            throw new IllegalArgumentException("规则编码不能为空");
        }
        if (isBlank(template.getName())) {
            throw new IllegalArgumentException("规则名称不能为空");
        }
        template.setTriggerType(normalizeEnum(template.getTriggerType(), TRIGGERS, "检查时机"));
        template.setScope(normalizeEnum(template.getScope(), SCOPES, "检查对象"));
        template.setSeverity(normalizeEnum(template.getSeverity(), SEVERITIES, "严重度"));
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
    public RuleTemplate update(RuleTemplate template) {
        if (template == null || isBlank(template.getOid())) {
            throw new IllegalArgumentException("oid 不能为空");
        }
        RuleTemplate existing = mapper.selectByOid(template.getOid());
        if (existing == null) {
            throw new IllegalArgumentException("检查规则不存在");
        }
        TenantContext.requireEditPermission(existing.getTenantOid(), "检查规则");
        // code 是幂等键、tenant 是归属 —— 两者不可改：UPDATE 语句里也没有这两列
        template.setCode(existing.getCode());
        template.setTenantOid(existing.getTenantOid());
        // patch 语义：逐个字段兜底
        if (template.getName() == null) template.setName(existing.getName());
        if (template.getDescription() == null) template.setDescription(existing.getDescription());
        if (template.getIndustry() == null) template.setIndustry(existing.getIndustry());
        if (template.getTriggerType() == null) template.setTriggerType(existing.getTriggerType());
        if (template.getScope() == null) template.setScope(existing.getScope());
        if (template.getCategory() == null) template.setCategory(existing.getCategory());
        if (template.getStageKey() == null) template.setStageKey(existing.getStageKey());
        if (template.getGate() == null) template.setGate(existing.getGate());
        if (template.getCondition() == null) template.setCondition(existing.getCondition());
        if (template.getSeverity() == null) template.setSeverity(existing.getSeverity());
        if (template.getMessage() == null) template.setMessage(existing.getMessage());
        if (template.getDefaultOverride() == null) template.setDefaultOverride(existing.getDefaultOverride());
        if (template.getFixAction() == null) template.setFixAction(existing.getFixAction());
        if (template.getIndustryNote() == null) template.setIndustryNote(existing.getIndustryNote());
        if (template.getDisabled() == null) template.setDisabled(existing.getDisabled());
        if (template.getSortOrder() == null) template.setSortOrder(existing.getSortOrder());
        // 枚举越界宁可拒绝，也不写脏数据
        template.setTriggerType(normalizeEnum(template.getTriggerType(), TRIGGERS, "检查时机"));
        template.setScope(normalizeEnum(template.getScope(), SCOPES, "检查对象"));
        template.setSeverity(normalizeEnum(template.getSeverity(), SEVERITIES, "严重度"));
        normalizeBlanks(template);
        mapper.update(template);
        return mapper.selectByOid(template.getOid());
    }

    @Override
    @Transactional
    public boolean delete(String oid) {
        if (isBlank(oid)) return false;
        RuleTemplate existing = mapper.selectByOid(oid);
        if (existing == null) return false;
        TenantContext.requireEditPermission(existing.getTenantOid(), "检查规则");
        mapper.deleteByOid(oid);
        log.info("检查规则已删除: {}（{}）—— 提醒：包重装会把它加回来，日常请用「停用」",
                existing.getCode(), existing.getName());
        return true;
    }

    @Override
    @Transactional
    public boolean setDisabled(String oid, boolean disabled) {
        if (isBlank(oid)) return false;
        RuleTemplate existing = mapper.selectByOid(oid);
        if (existing == null) return false;
        TenantContext.requireEditPermission(existing.getTenantOid(), "检查规则");
        existing.setDisabled(disabled);
        mapper.update(existing);
        return true;
    }

    @Override
    public List<RuleTemplate> findAll() {
        return mapper.selectByTenant(tenantOid());
    }

    @Override
    public List<RuleTemplate> findAll(String industry) {
        return filterByIndustry(mapper.selectByTenant(tenantOid()), industry);
    }

    @Override
    public RuleTemplate findByCode(String code) {
        if (isBlank(code)) return null;
        return mapper.selectByCode(code.trim(), tenantOid(), platformOid());
    }

    @Override
    public List<String> listPlatformIndustries() {
        Set<String> industries = new LinkedHashSet<>();
        for (RuleTemplate t : mapper.selectByTenant(platformOid())) {
            if (!isBlank(t.getIndustry())) {
                industries.add(t.getIndustry());
            }
        }
        return new ArrayList<>(industries);
    }

    @Override
    public List<RuleTemplate> findPlatformTemplates(String industry) {
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
        List<RuleTemplate> platformTemplates = findPlatformTemplates(industry);
        if (platformTemplates.isEmpty()) {
            log.warn("平台级检查规则为空（industry={}），无法克隆", industry);
            return 0;
        }
        int cloned = 0;
        int skipped = 0;
        for (RuleTemplate source : platformTemplates) {
            // 幂等：本租户已有同码（含企业已停用的）→ 跳过，绝不覆盖企业裁剪
            if (mapper.existsByCode(source.getCode(), currentTenant, currentTenant) > 0) {
                skipped++;
                continue;
            }
            RuleTemplate clone = new RuleTemplate(source.getCode(), source.getName());
            clone.setOid(UUID.randomUUID().toString());
            clone.setDescription(source.getDescription());
            clone.setIndustry(source.getIndustry());
            clone.setTriggerType(source.getTriggerType());
            clone.setScope(source.getScope());
            clone.setCategory(source.getCategory());
            clone.setStageKey(source.getStageKey());
            clone.setGate(source.getGate());
            clone.setCondition(source.getCondition());
            clone.setSeverity(source.getSeverity());
            clone.setMessage(source.getMessage());
            clone.setDefaultOverride(source.getDefaultOverride());
            clone.setFixAction(source.getFixAction());
            clone.setIndustryNote(source.getIndustryNote());
            clone.setDisabled(false);
            clone.setSortOrder(source.getSortOrder());
            clone.setTenantOid(currentTenant);
            mapper.insert(clone);
            cloned++;
        }
        log.info("租户检查规则克隆完成（industry={}）: 新增 {}，跳过（已存在）{}", industry, cloned, skipped);
        return cloned;
    }

    /**
     * 按行业/方法论过滤：空参数 = 不过滤；
     * 非空时**通用条目（industry 为空）也算命中** —— 基础包的共用规则就是通用条目。
     */
    private List<RuleTemplate> filterByIndustry(List<RuleTemplate> all, String industry) {
        if (isBlank(industry)) {
            return all;
        }
        String wanted = industry.trim();
        List<RuleTemplate> filtered = new ArrayList<>();
        for (RuleTemplate t : all) {
            if (wanted.equals(t.getIndustry()) || isBlank(t.getIndustry())) {
                filtered.add(t);
            }
        }
        return filtered;
    }

    /** 枚举归一 + 越界即拒（错误消息里带上允许取值，便于包作者定位） */
    private String normalizeEnum(String value, Set<String> allowed, String fieldLabel) {
        if (isBlank(value)) {
            throw new IllegalArgumentException(fieldLabel + "不能为空，取值只能是 " + allowed);
        }
        String normalized = value.trim().toUpperCase();
        if (!allowed.contains(normalized)) {
            throw new IllegalArgumentException(fieldLabel + " '" + value + "' 非法，取值只能是 " + allowed);
        }
        return normalized;
    }

    /**
     * 把"空串"归一为 NULL（前端用空串表达"清空这个字段"）。
     * 必须在"按原值兜底"<b>之后</b>调用，才能区分"缺席（不改）"与"空串（清空）"。
     */
    private void normalizeBlanks(RuleTemplate template) {
        template.setDescription(blankToNull(template.getDescription()));
        template.setIndustry(blankToNull(template.getIndustry()));
        template.setCategory(blankToNull(template.getCategory()));
        template.setStageKey(blankToNull(template.getStageKey()));
        template.setGate(blankToNull(template.getGate()));
        template.setCondition(blankToNull(template.getCondition()));
        template.setMessage(blankToNull(template.getMessage()));
        template.setDefaultOverride(blankToNull(template.getDefaultOverride()));
        template.setFixAction(blankToNull(template.getFixAction()));
        template.setIndustryNote(blankToNull(template.getIndustryNote()));
    }

    private static String blankToNull(String value) {
        return (value != null && value.trim().isEmpty()) ? null : value;
    }

    private static boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }
}
