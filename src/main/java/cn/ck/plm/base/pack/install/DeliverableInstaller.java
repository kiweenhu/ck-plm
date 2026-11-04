/*
 * Copyright (c) 2026~2028 深圳乘恺科技有限公司
 * All rights reserved.
 *
 * @author Kiween.Hu; Roney.Liu
 */

package cn.ck.plm.base.pack.install;

import cn.ck.plm.base.entity.DeliverableTemplate;
import cn.ck.plm.base.mapper.DeliverableTemplateMapper;
import cn.ck.plm.base.pack.model.PackManifest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 交付件（交付物）模板安装器（{@code DELIVERABLE}）—— 读包内 {@code deliverables.json} 的 {@code items[]}。
 *
 * <h3>三个口径</h3>
 * <ol>
 *   <li><b>行业/方法论与阶段模板同源</b>：清单里没有 industry 字段，安装器取<b>包清单的
 *       {@code devMethod}</b>（如 IPD）—— 与同一包装出来的 {@code ck_stage_template.industry} 逐字一致。
 *       这样交付件的 {@code stage_key} / {@code gate} 与阶段、阶段门才对得上；
 *       两边各取各的（或靠"有没有 TRADITIONAL 模板"去猜）会造出永远算不清的判定。</li>
 *   <li><b>幂等键 {@code code + 目标租户}</b>：已存在即 SKIP —— 企业在模板层的裁剪
 *       （改强制 / 改完成条件 / 停用）不会被重装冲掉；<b>安装器永不 UPDATE 已有行</b>。</li>
 *   <li><b>{@code inherit} 不消费</b>：它是"本文件是某包的增量"的声明，与 {@code pack.json} 的
 *       {@code basePack} 呼应；父包由引擎先装（{@code installRecursive}），此处只读来记日志。</li>
 * </ol>
 *
 * <p>落库的 {@code completionRule} 是 JSON 字符串（包里是真对象）—— 与 {@code defaultFolders}
 * 同款：包里可读可审，库里按字符串存，P3 的判定器再解析求值。
 */
@Component
public class DeliverableInstaller implements ResourceInstaller {

    private static final Logger log = LoggerFactory.getLogger(DeliverableInstaller.class);

    private final DeliverableTemplateMapper mapper;

    public DeliverableInstaller(DeliverableTemplateMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public String resourceType() {
        return "DELIVERABLE";
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
                    "清单里没有 items 段（本包不含交付件模板）"));
            return results;
        }

        String tenant = context.getTenantOid();
        String industry = resolveIndustry(context, json);
        Object inherit = json.get("inherit");
        log.debug("交付件模板安装：industry={}（取包的方法论），inherit={}，items={} 条",
                industry, inherit, ((List<?>) items).size());

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
                if (mapper.existsByCode(code, tenant, tenant) > 0) {
                    results.add(InstallResult.skipped(resourceType(), code,
                            "交付件模板已存在（企业裁剪值不被覆盖）"));
                    continue;
                }
                DeliverableTemplate template = build(row, industry, tenant, order, context);
                String stageKey = template.getStageKey() == null ? "-" : template.getStageKey();
                String gate = template.getGate() == null ? "-" : template.getGate();
                if (context.isDryRun()) {
                    results.add(InstallResult.inserted(resourceType(), code)
                            .why("预演：将新增交付件「" + template.getName() + "」（" + stageKey
                                    + "/" + gate + "，" + mandatoryLabel(template) + "）"));
                    continue;
                }
                mapper.insert(template);
                results.add(InstallResult.inserted(resourceType(), code)
                        .why("交付件模板：" + template.getName() + "（" + stageKey + "/" + gate
                                + "，" + mandatoryLabel(template)
                                + "，" + ruleCount(template) + " 条完成条件）")
                        .on("ck_deliverable_template", template.getOid()));
            } catch (Exception e) {
                results.add(InstallResult.failed(resourceType(), code,
                        e.getClass().getSimpleName() + ": " + e.getMessage()));
            }
        }
        log.debug("交付件模板安装完成: {} 条明细", results.size());
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

    private DeliverableTemplate build(Map<?, ?> row, String industry, String tenant, int order,
                                      InstallContext context) throws Exception {
        DeliverableTemplate template = new DeliverableTemplate(text(row.get("code")), text(row.get("name")));
        template.setOid(UUID.randomUUID().toString());
        template.setIndustry(industry);
        template.setStageKey(text(row.get("stage")));
        template.setDiscipline(text(row.get("discipline")));
        template.setMandatory(boolOr(row.get("mandatory"), false));
        template.setOwnerRole(text(row.get("ownerRole")));
        template.setObjectType(text(row.get("objectType")));
        template.setBindMode(text(row.get("bindMode")));
        // 包里是可读对象，库里存 JSON 字符串（Jackson 默认无空格，与 StageTemplateInstaller 同款）
        template.setCompletionRule(toJson(row.get("completionRule")));
        template.setDueOffsetDays(row.get("dueOffsetDays") == null ? null : intOr(row.get("dueOffsetDays"), 0));
        template.setGate(text(row.get("gate")));
        template.setIndustryNote(text(row.get("industryNote")));
        template.setDisabled(false);
        // 清单是按阶段成组书写的：文件顺序即阶段内顺序（有余量时以显式 sortOrder 为准）
        template.setSortOrder(intOr(row.get("sortOrder"), order));
        template.setTenantOid(tenant);
        // 装包是系统动作（与引擎落能力账本时 creator="system" 同口径）；后续人工修改由审计拦截器改写
        template.setCreator("system");
        template.setUpdater("system");
        return template;
    }

    /** 对象/数组 → 无空格 JSON 字符串；其它类型原样转字符串 */
    private String toJson(Object value) throws Exception {
        if (value == null) {
            return null;
        }
        if (value instanceof Map || value instanceof List) {
            return com.fasterxml.jackson.databind.json.JsonMapper.builder().build()
                    .writeValueAsString(value);
        }
        return String.valueOf(value);
    }

    private String mandatoryLabel(DeliverableTemplate template) {
        return Boolean.TRUE.equals(template.getMandatory()) ? "强制" : "可选";
    }

    /** 完成条件条数（数 {@code "type"} 出现次数：形如 {"all":[{"type":…},…]}） */
    private int ruleCount(DeliverableTemplate template) {
        String rule = template.getCompletionRule();
        if (rule == null || rule.isEmpty()) {
            return 0;
        }
        int count = 0;
        int from = 0;
        while (true) {
            int at = rule.indexOf("\"type\"", from);
            if (at < 0) {
                break;
            }
            count++;
            from = at + 6;
        }
        return count;
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
