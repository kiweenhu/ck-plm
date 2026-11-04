/*
 * Copyright (c) 2026~2028 深圳乘恺科技有限公司
 * All rights reserved.
 *
 * @author Kiween.Hu; Roney.Liu
 */

package cn.ck.plm.base.pack.install;

import cn.ck.plm.base.entity.IssueTypeTemplate;
import cn.ck.plm.base.mapper.IssueTypeTemplateMapper;
import cn.ck.plm.base.pack.model.PackManifest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * 问题类型模板安装器（{@code ISSUE_TYPE}）—— 读包内 {@code issueTypes.json} 的 {@code items[]}。
 *
 * <h3>四个口径</h3>
 * <ol>
 *   <li><b>行业/方法论与阶段模板同源</b>：清单没有 industry 字段，取包清单的 {@code devMethod}；</li>
 *   <li><b>严重度枚举把关</b>：{@code defaultSeverity} 只能是 {@code BLOCK} / {@code WARN}
 *       （与检查规则、阶段门同一口径）—— 越界即如实记 FAILED，并把允许取值写进原因；</li>
 *   <li><b>字段名映射</b>：包内的行业注解字段叫 {@code note}，库里叫 {@code industry_note}
 *       （与其它五类模板的 {@code industryNote} 同义），安装器负责搬过来；两个键都认，便于以后统一；</li>
 *   <li><b>幂等键 {@code code + 目标租户}</b>：已存在即 SKIP（企业裁剪不被覆盖），安装器永不 UPDATE。</li>
 * </ol>
 *
 * <p>本文件不含对外引用（问题类型是公共词汇，被别人引用而不是引用别人）——
 * 检查规则的 {@code condition.issueType}（如 R-SH-03 只认 {@code TEST_FAIL}）、
 * 门的 {@code ISSUE_OPEN} 检查都按这里的编码统计。
 */
@Component
public class IssueTypeInstaller implements ResourceInstaller {

    private static final Logger log = LoggerFactory.getLogger(IssueTypeInstaller.class);

    /** 默认严重度（与检查规则、阶段门同一口径） */
    private static final Set<String> SEVERITIES = new LinkedHashSet<>(List.of("BLOCK", "WARN"));

    private final IssueTypeTemplateMapper mapper;

    public IssueTypeInstaller(IssueTypeTemplateMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public String resourceType() {
        return "ISSUE_TYPE";
    }

    @Override
    public List<InstallResult> install(InstallContext context, PackManifest.Resource resource) {
        List<InstallResult> results = new ArrayList<>();
        Map<String, Object> json;
        try {
            json = context.getSource().readJsonMap(context.getObjectMapper(), resource.getFile());
        } catch (Exception e) {
            results.add(InstallResult.failed(resourceType(), resource.getFile(),
                    "读取清单文件失败: " + e.getMessage()));
            return results;
        }

        Object items = json.get("items");
        if (!(items instanceof List)) {
            results.add(InstallResult.skippedPending(resourceType(), resource.getFile(),
                    "清单里没有 items 段（本包不含问题类型）"));
            return results;
        }

        String tenant = context.getTenantOid();
        String industry = resolveIndustry(context, json);
        log.debug("问题类型安装：industry={}（取包的方法论），items={} 条",
                industry, ((List<?>) items).size());

        int order = 0;
        for (Object element : (List<?>) items) {
            if (!(element instanceof Map)) {
                continue;
            }
            Map<?, ?> row = (Map<?, ?>) element;
            String code = text(row.get("code"));
            if (code == null) {
                continue;
            }
            order++;
            try {
                String severity = text(row.get("defaultSeverity"));
                if (severity == null || !SEVERITIES.contains(severity.toUpperCase())) {
                    results.add(InstallResult.failed(resourceType(), code,
                            "defaultSeverity 取值非法（只能是 " + SEVERITIES + "）：" + row.get("defaultSeverity")));
                    continue;
                }
                severity = severity.toUpperCase();

                if (mapper.existsByCode(code, tenant, tenant) > 0) {
                    results.add(InstallResult.skipped(resourceType(), code,
                            "问题类型已存在（企业裁剪值不被覆盖）"));
                    continue;
                }
                IssueTypeTemplate template = build(row, industry, tenant, order, severity);
                if (context.isDryRun()) {
                    results.add(InstallResult.inserted(resourceType(), code)
                            .why("预演：将新增问题类型「" + template.getName() + "」（默认 " + severity
                                    + "，" + flagLabel(template) + "）"));
                    continue;
                }
                mapper.insert(template);
                results.add(InstallResult.inserted(resourceType(), code)
                        .why("问题类型：" + template.getName() + "（默认 " + severity + "，"
                                + flagLabel(template) + "）")
                        .on("ck_issue_type_template", template.getOid()));
            } catch (Exception e) {
                results.add(InstallResult.failed(resourceType(), code,
                        e.getClass().getSimpleName() + ": " + e.getMessage()));
            }
        }
        log.debug("问题类型安装完成: {} 条明细", results.size());
        return results;
    }

    /**
     * 行业/方法论：优先清单自己声明的 {@code industry}，其次包清单的 {@code devMethod}，都没有则留空
     * （空 = 通用，任何方法论都适用）。不猜、不硬编码。
     */
    private String resolveIndustry(InstallContext context, Map<String, Object> json) {
        String fromFile = text(json.get("industry"));
        if (fromFile != null) {
            return fromFile;
        }
        return text(context.getSource().getManifest().getDevMethod());
    }

    private IssueTypeTemplate build(Map<?, ?> row, String industry, String tenant, int order,
                                    String severity) {
        IssueTypeTemplate template = new IssueTypeTemplate(text(row.get("code")), text(row.get("name")));
        template.setOid(UUID.randomUUID().toString());
        template.setIndustry(industry);
        template.setDefaultSeverity(severity);
        template.setRequireRootCause(boolOr(row.get("requireRootCause"), false));
        template.setRequireRetest(boolOr(row.get("requireRetest"), false));
        // 包内字段名是 note；也认 industryNote（便于将来六类模板统一命名）
        String note = text(row.get("industryNote"));
        if (note == null) {
            note = text(row.get("note"));
        }
        template.setIndustryNote(note);
        template.setDisabled(false);
        template.setSortOrder(intOr(row.get("sortOrder"), order));
        template.setTenantOid(tenant);
        // 装包是系统动作（与引擎落能力账本同口径）；后续人工修改由审计拦截器改写
        template.setCreator("system");
        template.setUpdater("system");
        return template;
    }

    /** 两个布尔标记的人话摘要（报告里看"这一类问题要不要做根因/要不要复测"） */
    private String flagLabel(IssueTypeTemplate template) {
        String rootCause = Boolean.TRUE.equals(template.getRequireRootCause()) ? "需根因" : "免根因";
        String retest = Boolean.TRUE.equals(template.getRequireRetest()) ? "需复测" : "免复测";
        return rootCause + "，" + retest;
    }

    private static String text(Object value) {
        if (value == null) {
            return null;
        }
        String s = String.valueOf(value).trim();
        return s.isEmpty() ? null : s;
    }

    private static boolean boolOr(Object value, boolean defaultValue) {
        if (value instanceof Boolean) {
            return (Boolean) value;
        }
        if (value instanceof String) {
            return Boolean.parseBoolean(((String) value).trim());
        }
        return defaultValue;
    }

    private static int intOr(Object value, int defaultValue) {
        if (value instanceof Number) {
            return ((Number) value).intValue();
        }
        if (value instanceof String) {
            try {
                return Integer.parseInt(((String) value).trim());
            } catch (NumberFormatException ignored) {
                return defaultValue;
            }
        }
        return defaultValue;
    }
}
