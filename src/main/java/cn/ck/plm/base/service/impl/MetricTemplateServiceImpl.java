/*
 * Copyright (c) 2026~2028 深圳乘恺科技有限公司
 * All rights reserved.
 *
 * @author Kiween.Hu; Roney.Liu
 */

package cn.ck.plm.base.service.impl;

import cn.ck.plm.base.entity.MetricTemplate;
import cn.ck.plm.base.mapper.MetricTemplateMapper;
import cn.ck.plm.base.service.api.MetricTemplateService;
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
 * 指标模板服务实现 —— 与前面六类模板服务同口径。
 *
 * <h3>写接口语义（与前端约定，勿改）</h3>
 * <b>字段缺席（null）= 本次不改；空串（""）= 显式清空</b>。UPDATE 是整行写法，
 * update 必须把没提交的字段逐个用原值兜底。
 *
 * <h3>两组枚举把关</h3>
 * <ul>
 *   <li>{@code layer} ∈ {businessLayer, executiveLayer}（分层口径：总监看 / 老板看）；</li>
 *   <li>{@code level} ∈ {A, B, C}（<b>数据就绪度</b>）。C 级按文档硬规则只显示"暂未评估"，
 *       禁止编造数值 —— 故这里也不允许把 C 写成"看起来更漂亮"的 A/B。</li>
 * </ul>
 */
@Service
public class MetricTemplateServiceImpl implements MetricTemplateService {

    private static final Logger log = LoggerFactory.getLogger(MetricTemplateServiceImpl.class);

    /** 分层（与包内 metrics.json 的两个数组一一对应） */
    private static final Set<String> LAYERS = new LinkedHashSet<>(List.of(
            "businessLayer", "executiveLayer"));
    /** 数据就绪度：A/B/C */
    private static final Set<String> LEVELS = new LinkedHashSet<>(List.of("A", "B", "C"));

    private final MetricTemplateMapper mapper;

    public MetricTemplateServiceImpl(MetricTemplateMapper mapper) {
        this.mapper = mapper;
    }

    private String tenantOid() { return TenantContext.get(); }
    private String platformOid() { return TenantContext.PLATFORM_TENANT_OID; }

    @Override
    @Transactional
    public MetricTemplate create(MetricTemplate template) {
        if (template == null || isBlank(template.getCode())) {
            throw new IllegalArgumentException("指标编码不能为空");
        }
        if (isBlank(template.getName())) {
            throw new IllegalArgumentException("指标名称不能为空");
        }
        template.setLayer(normalizeEnum(template.getLayer(), LAYERS, "分层"));
        template.setLevel(normalizeEnum(template.getLevel(), LEVELS, "数据就绪度"));
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
    public MetricTemplate update(MetricTemplate template) {
        if (template == null || isBlank(template.getOid())) {
            throw new IllegalArgumentException("oid 不能为空");
        }
        MetricTemplate existing = mapper.selectByOid(template.getOid());
        if (existing == null) {
            throw new IllegalArgumentException("指标不存在");
        }
        TenantContext.requireEditPermission(existing.getTenantOid(), "指标");
        // code 是幂等键、tenant 是归属 —— 两者不可改：UPDATE 语句里也没有这两列
        template.setCode(existing.getCode());
        template.setTenantOid(existing.getTenantOid());
        // patch 语义：逐个字段兜底
        if (template.getName() == null) template.setName(existing.getName());
        if (template.getDescription() == null) template.setDescription(existing.getDescription());
        if (template.getIndustry() == null) template.setIndustry(existing.getIndustry());
        if (template.getLayer() == null) template.setLayer(existing.getLayer());
        if (template.getLevel() == null) template.setLevel(existing.getLevel());
        if (template.getSource() == null) template.setSource(existing.getSource());
        if (template.getIndustryNote() == null) template.setIndustryNote(existing.getIndustryNote());
        if (template.getDisabled() == null) template.setDisabled(existing.getDisabled());
        if (template.getSortOrder() == null) template.setSortOrder(existing.getSortOrder());
        // 枚举越界宁可拒绝，也不写脏数据
        template.setLayer(normalizeEnum(template.getLayer(), LAYERS, "分层"));
        template.setLevel(normalizeEnum(template.getLevel(), LEVELS, "数据就绪度"));
        normalizeBlanks(template);
        mapper.update(template);
        return mapper.selectByOid(template.getOid());
    }

    @Override
    @Transactional
    public boolean delete(String oid) {
        if (isBlank(oid)) return false;
        MetricTemplate existing = mapper.selectByOid(oid);
        if (existing == null) return false;
        TenantContext.requireEditPermission(existing.getTenantOid(), "指标");
        mapper.deleteByOid(oid);
        log.info("指标已删除: {}（{}）—— 提醒：包重装会把它加回来，日常请用「停用」",
                existing.getCode(), existing.getName());
        return true;
    }

    @Override
    @Transactional
    public boolean setDisabled(String oid, boolean disabled) {
        if (isBlank(oid)) return false;
        MetricTemplate existing = mapper.selectByOid(oid);
        if (existing == null) return false;
        TenantContext.requireEditPermission(existing.getTenantOid(), "指标");
        existing.setDisabled(disabled);
        mapper.update(existing);
        return true;
    }

    @Override
    public List<MetricTemplate> findAll() {
        return mapper.selectByTenant(tenantOid());
    }

    @Override
    public List<MetricTemplate> findAll(String industry) {
        return filterByIndustry(mapper.selectByTenant(tenantOid()), industry);
    }

    @Override
    public MetricTemplate findByCode(String code) {
        if (isBlank(code)) return null;
        return mapper.selectByCode(code.trim(), tenantOid(), platformOid());
    }

    @Override
    public List<String> listPlatformIndustries() {
        Set<String> industries = new LinkedHashSet<>();
        for (MetricTemplate t : mapper.selectByTenant(platformOid())) {
            if (!isBlank(t.getIndustry())) {
                industries.add(t.getIndustry());
            }
        }
        return new ArrayList<>(industries);
    }

    @Override
    public List<MetricTemplate> findPlatformTemplates(String industry) {
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
        List<MetricTemplate> platformTemplates = findPlatformTemplates(industry);
        if (platformTemplates.isEmpty()) {
            log.warn("平台级指标为空（industry={}），无法克隆", industry);
            return 0;
        }
        int cloned = 0;
        int skipped = 0;
        for (MetricTemplate source : platformTemplates) {
            // 幂等：本租户已有同码（含企业已停用的）→ 跳过，绝不覆盖企业裁剪
            if (mapper.existsByCode(source.getCode(), currentTenant, currentTenant) > 0) {
                skipped++;
                continue;
            }
            MetricTemplate clone = new MetricTemplate(source.getCode(), source.getName());
            clone.setOid(UUID.randomUUID().toString());
            clone.setDescription(source.getDescription());
            clone.setIndustry(source.getIndustry());
            clone.setLayer(source.getLayer());
            clone.setLevel(source.getLevel());
            clone.setSource(source.getSource());
            clone.setIndustryNote(source.getIndustryNote());
            clone.setDisabled(false);
            clone.setSortOrder(source.getSortOrder());
            clone.setTenantOid(currentTenant);
            mapper.insert(clone);
            cloned++;
        }
        log.info("租户指标克隆完成（industry={}）: 新增 {}，跳过（已存在）{}", industry, cloned, skipped);
        return cloned;
    }

    /**
     * 按行业/方法论过滤：空参数 = 不过滤；
     * 非空时**通用条目（industry 为空）也算命中** —— 基础包的共用指标就是通用条目。
     */
    private List<MetricTemplate> filterByIndustry(List<MetricTemplate> all, String industry) {
        if (isBlank(industry)) {
            return all;
        }
        String wanted = industry.trim();
        List<MetricTemplate> filtered = new ArrayList<>();
        for (MetricTemplate t : all) {
            if (wanted.equals(t.getIndustry()) || isBlank(t.getIndustry())) {
                filtered.add(t);
            }
        }
        return filtered;
    }

    /** 枚举归一 + 越界即拒（错误消息里带上允许取值） */
    private String normalizeEnum(String value, Set<String> allowed, String fieldLabel) {
        if (isBlank(value)) {
            throw new IllegalArgumentException(fieldLabel + "不能为空，取值只能是 " + allowed);
        }
        for (String candidate : allowed) {
            if (candidate.equalsIgnoreCase(value.trim())) {
                return candidate;
            }
        }
        throw new IllegalArgumentException(fieldLabel + " '" + value + "' 非法，取值只能是 " + allowed);
    }

    /**
     * 把"空串"归一为 NULL（前端用空串表达"清空这个字段"）。
     * 必须在"按原值兜底"<b>之后</b>调用，才能区分"缺席（不改）"与"空串（清空）"。
     */
    private void normalizeBlanks(MetricTemplate template) {
        template.setDescription(blankToNull(template.getDescription()));
        template.setIndustry(blankToNull(template.getIndustry()));
        template.setSource(blankToNull(template.getSource()));
        template.setIndustryNote(blankToNull(template.getIndustryNote()));
    }

    private static String blankToNull(String value) {
        return (value != null && value.trim().isEmpty()) ? null : value;
    }

    private static boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }
}
