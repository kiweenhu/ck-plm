/*
 * Copyright (c) 2026~2028 深圳乘恺科技有限公司
 * All rights reserved.
 *
 * @author Kiween.Hu; Roney.Liu
 */

package cn.ck.plm.base.pack.install;

import cn.ck.plm.base.entity.RuleTemplate;
import cn.ck.plm.base.mapper.DeliverableTemplateMapper;
import cn.ck.plm.base.mapper.GateTemplateMapper;
import cn.ck.plm.base.mapper.RuleTemplateMapper;
import cn.ck.plm.base.mapper.StageTemplateMapper;
import cn.ck.plm.base.pack.model.PackManifest;
import cn.ck.plm.base.util.TenantContext;
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
 * 检查规则模板安装器（{@code RULE}）—— 读包内 {@code rules.json} 的 {@code items[]}。
 *
 * <p><b>声明式 JSON，不是脚本</b>：一条规则 = 时机 + 对象 + 条件 + 严重度 + 提示，
 * 另可带分类、所属阶段/门、企业画像联动、修复动作。规则能被审、能被企业改。
 *
 * <h3>安装器做四件事</h3>
 * <ol>
 *   <li><b>行业/方法论与阶段模板同源</b>：清单没有 industry 字段，取包清单的 {@code devMethod}。
 *       基础包（electronics-core）不声明方法论 → 落成"通用"条目，任何方法论都适用 —— 这正是
 *       "共用侧 7 条 + 变体侧 4 条 = 11 条"里那 7 条的语义；</li>
 *   <li><b>四组枚举逐条校验</b>（时机 / 对象 / 条件类型 / 严重度，与包内 note 同口径）——
 *       越界即如实记 FAILED，并把允许取值写进原因。越界值平台根本解释不了，装进去只会变成静默失效；</li>
 *   <li><b>三处引用校验</b>：{@code stage} → 阶段模板、{@code gate} → 门模板、
 *       {@code fixAction.code} → 交付件模板。引用对象尚未就绪时记 <b>SKIP + 原因</b>
 *       （依赖顺序问题，重装即可补齐），不是 FAILED —— 不该让整包跟着变 PARTIAL；</li>
 *   <li><b>幂等键 {@code code + 目标租户}</b>：已存在即 SKIP（企业裁剪不被覆盖），安装器永不 UPDATE。</li>
 * </ol>
 */
@Component
public class RuleTemplateInstaller implements ResourceInstaller {

    private static final Logger log = LoggerFactory.getLogger(RuleTemplateInstaller.class);

    /** 检查时机（与包内 note 同口径） */
    private static final Set<String> TRIGGERS = new LinkedHashSet<>(List.of(
            "STAGE_ENTRY", "PART_SAVE", "BOM_SAVE", "GATE_CHECK", "RELEASE"));
    /** 检查对象 */
    private static final Set<String> SCOPES = new LinkedHashSet<>(List.of(
            "PRODUCT_MODEL", "PART", "BOM"));
    /** 条件类型 */
    private static final Set<String> CONDITION_TYPES = new LinkedHashSet<>(List.of(
            "FIELD_CHECK", "FIELD_EMPTY", "ISSUE_OPEN", "OBJECT_MISSING"));
    /** 严重度 */
    private static final Set<String> SEVERITIES = new LinkedHashSet<>(List.of("BLOCK", "WARN"));

    private final RuleTemplateMapper mapper;
    private final StageTemplateMapper stageMapper;
    private final GateTemplateMapper gateMapper;
    private final DeliverableTemplateMapper deliverableMapper;

    public RuleTemplateInstaller(RuleTemplateMapper mapper,
                                 StageTemplateMapper stageMapper,
                                 GateTemplateMapper gateMapper,
                                 DeliverableTemplateMapper deliverableMapper) {
        this.mapper = mapper;
        this.stageMapper = stageMapper;
        this.gateMapper = gateMapper;
        this.deliverableMapper = deliverableMapper;
    }

    @Override
    public String resourceType() {
        return "RULE";
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
                    "清单里没有 items 段（本包不含检查规则）"));
            return results;
        }

        String tenant = context.getTenantOid();
        String industry = resolveIndustry(context, json);
        log.debug("检查规则安装：industry={}（空=通用，基础包共用规则即如此），items={} 条",
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
                // ① 四组枚举（越界平台解释不了，如实记 FAILED）
                String trigger = enumOrNull(row.get("trigger"), TRIGGERS, "trigger");
                if (trigger == null) {
                    results.add(InstallResult.failed(resourceType(), code,
                            "trigger 取值非法（只能是 " + TRIGGERS + "）：" + row.get("trigger")));
                    continue;
                }
                String scope = enumOrNull(row.get("scope"), SCOPES, "scope");
                if (scope == null) {
                    results.add(InstallResult.failed(resourceType(), code,
                            "scope 取值非法（只能是 " + SCOPES + "）：" + row.get("scope")));
                    continue;
                }
                String severity = enumOrNull(row.get("severity"), SEVERITIES, "severity");
                if (severity == null) {
                    results.add(InstallResult.failed(resourceType(), code,
                            "severity 取值非法（只能是 " + SEVERITIES + "）：" + row.get("severity")));
                    continue;
                }
                Map<?, ?> conditionMap = (row.get("condition") instanceof Map) ? (Map<?, ?>) row.get("condition") : null;
                if (conditionMap == null) {
                    results.add(InstallResult.failed(resourceType(), code, "缺 condition：规则没有判定条件"));
                    continue;
                }
                String conditionType = enumOrNull(conditionMap.get("type"), CONDITION_TYPES, "condition.type");
                if (conditionType == null) {
                    results.add(InstallResult.failed(resourceType(), code,
                            "condition.type 取值非法（只能是 " + CONDITION_TYPES + "）：" + conditionMap.get("type")));
                    continue;
                }

                // ② 三处引用（未就绪 → 跳过，重装即可补齐）
                //    "就绪"的判据：真装以**本租户**为准；预演时**链上包声明过**也算 ——
                //    与软类型判父类型同一套逻辑（包元数据层面的事实，不看平台租户那份）。
                //    传入 (tenant, tenant) 是因为 Mapper 的第三参是历史遗留、SQL 只认本租户。
                String stageKey = text(row.get("stage"));
                if (stageKey != null && stageMapper.existsByCode(stageKey, tenant, tenant) <= 0
                        && !(context.isDryRun() && context.getChainDeclaredCodes("STAGE").contains(stageKey))) {
                    results.add(InstallResult.skippedPending(resourceType(), code,
                            "引用的阶段 '" + stageKey + "' 尚未装入（阶段模板就绪后重装即可补齐）"));
                    continue;
                }
                String gate = text(row.get("gate"));
                if (gate != null && gateMapper.existsByCode(gate, tenant, tenant) <= 0
                        && !(context.isDryRun() && context.getChainDeclaredCodes("GATE").contains(gate))) {
                    results.add(InstallResult.skippedPending(resourceType(), code,
                            "引用的阶段门 '" + gate + "' 尚未装入（门模板就绪后重装即可补齐）"));
                    continue;
                }
                String fixTarget = null;
                if (row.get("fixAction") instanceof Map) {
                    Map<?, ?> fix = (Map<?, ?>) row.get("fixAction");
                    fixTarget = text(fix.get("code"));
                    String target = text(fix.get("target"));
                    if (fixTarget != null && "deliverable".equalsIgnoreCase(target == null ? "" : target)
                            && deliverableMapper.existsByCode(fixTarget, tenant, tenant) <= 0
                            && !(context.isDryRun() && context.getChainDeclaredCodes("DELIVERABLE").contains(fixTarget))) {
                        results.add(InstallResult.skippedPending(resourceType(), code,
                                "修复动作指向的交付件 '" + fixTarget + "' 尚未装入（交付件模板就绪后重装即可补齐）"));
                        continue;
                    }
                }

                // ③ 幂等
                if (mapper.existsByCode(code, tenant, tenant) > 0) {
                    results.add(InstallResult.skipped(resourceType(), code,
                            "检查规则已存在（企业裁剪值不被覆盖）"));
                    continue;
                }
                RuleTemplate template = build(row, industry, tenant, order, trigger, scope, severity);
                if (context.isDryRun()) {
                    results.add(InstallResult.inserted(resourceType(), code)
                            .why("预演：将新增规则「" + template.getName() + "」（" + trigger + "/" + scope
                                    + "，" + severity + "）"));
                    continue;
                }
                mapper.insert(template);
                results.add(InstallResult.inserted(resourceType(), code)
                        .why("检查规则：" + template.getName() + "（" + trigger + "/" + scope
                                + "，" + severity + "，条件 " + conditionType
                                + (template.getGate() == null ? "" : "，门 " + template.getGate())
                                + (template.getStageKey() == null ? "" : "，阶段 " + template.getStageKey())
                                + (fixTarget == null ? "" : "，修复 → " + fixTarget) + "）")
                        .on("ck_rule_template", template.getOid()));
            } catch (Exception e) {
                results.add(InstallResult.failed(resourceType(), code,
                        e.getClass().getSimpleName() + ": " + e.getMessage()));
            }
        }
        log.debug("检查规则安装完成: {} 条明细", results.size());
        return results;
    }

    /**
     * 行业/方法论：优先清单自己声明的 {@code industry}，其次包清单的 {@code devMethod}，都没有则留空
     * （空 = 通用。基础包 electronics-core 不声明方法论，其共用规则就落成通用条目 —— 这是有意的语义）。
     */
    private String resolveIndustry(InstallContext context, Map<String, Object> json) {
        String fromFile = text(json.get("industry"));
        if (fromFile != null) {
            return fromFile;
        }
        return text(context.getSource().getManifest().getDevMethod());
    }

    /** 枚举取值归一；不在允许集内返回 null（调用方据此记 FAILED） */
    private String enumOrNull(Object value, Set<String> allowed, String fieldLabel) {
        String s = text(value);
        if (s == null) {
            log.debug("{} 为空值（将记为 FAILED）", fieldLabel);
            return null;
        }
        String normalized = s.toUpperCase();
        return allowed.contains(normalized) ? normalized : null;
    }

    private RuleTemplate build(Map<?, ?> row, String industry, String tenant, int order,
                               String trigger, String scope, String severity) throws Exception {
        RuleTemplate template = new RuleTemplate(text(row.get("code")), text(row.get("name")));
        template.setOid(UUID.randomUUID().toString());
        template.setIndustry(industry);
        template.setTriggerType(trigger);
        template.setScope(scope);
        template.setCategory(text(row.get("category")));
        template.setStageKey(text(row.get("stage")));
        template.setGate(text(row.get("gate")));
        template.setCondition(toJson(row.get("condition")));
        template.setSeverity(severity);
        template.setMessage(text(row.get("message")));
        template.setDefaultOverride(toJson(row.get("defaultOverride")));
        template.setFixAction(toJson(row.get("fixAction")));
        template.setIndustryNote(text(row.get("industryNote")));
        template.setDisabled(false);
        template.setSortOrder(intOr(row.get("sortOrder"), order));
        template.setTenantOid(tenant);
        // 装包是系统动作（与引擎落能力账本同口径）；后续人工修改由审计拦截器改写
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

    private static String text(Object value) {
        if (value == null) {
            return null;
        }
        String s = String.valueOf(value).trim();
        return s.isEmpty() ? null : s;
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
