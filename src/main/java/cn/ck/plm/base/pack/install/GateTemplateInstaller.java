/*
 * Copyright (c) 2026~2028 深圳乘恺科技有限公司
 * All rights reserved.
 *
 * @author Kiween.Hu; Roney.Liu
 */

package cn.ck.plm.base.pack.install;

import cn.ck.plm.base.entity.GateTemplate;
import cn.ck.plm.base.mapper.GateTemplateMapper;
import cn.ck.plm.base.pack.loader.IndustryPackLoader;
import cn.ck.plm.base.pack.model.PackManifest;
import cn.ck.plm.base.pack.model.PackSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * 阶段门（评审门）模板安装器（{@code GATE}）—— 读包内门清单的 {@code items[]}。
 *
 * <h3>只装「门」</h3>
 * <p>自 2026-10-29 扁平化后，门只剩决策门一类（{@code decision-gates.json}）：技术评审不再挂在
 * 技术门下，而是由 {@link ReviewTemplateInstaller} 按阶段归属（{@code technical-reviews.json}）。
 * 早前"技术门 + 内嵌 reviews[]"的写法已随技术门层一起移除 —— 一条从属关系只保留一种写法，
 * 免得同一份数据两条安装路径。
 *
 * <h3>四个口径</h3>
 * <ol>
 *   <li><b>行业/方法论与阶段模板同源</b>：清单没有 industry 字段，取<b>包清单的 {@code devMethod}</b>
 *       （如 IPD）—— 与同一包装出的 {@code ck_stage_template.industry} 逐字一致，
 *       门的 {@code stage_key} 与其 {@code autoChecks} 里的 {@code DELIVERABLE_RATE + stage} 才对得上；</li>
 *   <li><b>幂等键 {@code code + 目标租户}</b>：已存在即 SKIP —— 企业裁剪（改决策选项 / 改参与者 / 停用）
 *       不会被重装冲掉；<b>安装器永不 UPDATE 已有行</b>；</li>
 *   <li><b>流程引用不允许悬空</b>：{@code process} 只允许空或一个在<b>包链</b>（本包 → basePack → …）
 *       里真实存在 {@code processes/<key>.bpmn20.xml} 的 key。找不到记 FAILED（不装该条），
 *       并写明"查了哪些包"—— 悬空引用会让门发起时才发现流程不存在，那时代价已经在用户那儿了；
 *       内嵌评审的 {@code process} 同规则逐条校验；</li>
 *   <li><b>{@code inherit} 不消费</b>：它是"本文件是某包的增量"的声明，父包由引擎先装。</li>
 * </ol>
 */
@Component
public class GateTemplateInstaller implements ResourceInstaller {

    private static final Logger log = LoggerFactory.getLogger(GateTemplateInstaller.class);

    private final GateTemplateMapper mapper;
    private final IndustryPackLoader loader;

    public GateTemplateInstaller(GateTemplateMapper mapper, IndustryPackLoader loader) {
        this.mapper = mapper;
        this.loader = loader;
    }

    @Override
    public String resourceType() {
        return "GATE";
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
                    "清单里没有 items 段（本包不含阶段门模板）"));
            return results;
        }

        String tenant = context.getTenantOid();
        String industry = resolveIndustry(context, json);
        log.debug("阶段门模板安装：industry={}（取包的方法论），items={} 条",
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
                // 流程引用逐条校验（包自己的规矩：不允许悬空引用）——先校验再判重，
                // 否则"已存在"的 SKIP 会把一条悬空引用悄悄放过（老数据里有的话也不会被发现）
                String processKey = text(row.get("process"));
                String processOwner = null;
                if (processKey != null) {
                    processOwner = resolveProcessOwner(context.getSource(), processKey, new HashSet<>());
                    if (processOwner == null) {
                        results.add(InstallResult.failed(resourceType(), code,
                                "流程引用悬空：包链里找不到 processes/" + processKey
                                        + ".bpmn20.xml（查过 " + context.getSource().getId() + " 及其 basePack）"));
                        continue;
                    }
                }

                GateTemplate template = build(row, industry, tenant, order);
                String typeLabel = typeLabel(template.getType());
                String stageKey = template.getStageKey() == null ? "-" : template.getStageKey();
                boolean exists = mapper.existsByCode(code, tenant, tenant) > 0;
                if (exists) {
                    results.add(InstallResult.skipped(resourceType(), code,
                            "阶段门模板已存在（企业裁剪值不被覆盖）"));
                } else if (context.isDryRun()) {
                    results.add(InstallResult.inserted(resourceType(), code)
                            .why("预演：将新增阶段门「" + template.getName() + "」（" + stageKey + "/"
                                    + typeLabel + "，" + participantCount(template) + " 位参与者，"
                                    + checkCount(template) + " 项自动检查）"));
                } else {
                    mapper.insert(template);
                    results.add(InstallResult.inserted(resourceType(), code)
                            .why("阶段门模板：" + template.getName() + "（" + stageKey + "/" + typeLabel
                                    + "，" + participantCount(template) + " 位参与者，"
                                    + checkCount(template) + " 项自动检查"
                                    + (processKey == null ? "，无流程"
                                            : "，流程 " + processKey + "@" + shortId(processOwner)) + "）")
                            .on("ck_gate_template", template.getOid()));
                }

            } catch (Exception e) {
                results.add(InstallResult.failed(resourceType(), code,
                        e.getClass().getSimpleName() + ": " + e.getMessage()));
            }
        }
        log.debug("阶段门模板安装完成: {} 条明细", results.size());
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

    /**
     * 沿包链（本包 → basePack → …）找 {@code processes/<key>.bpmn20.xml}，返回拥有它的包 id。
     *
     * <p>为什么必须跨包找：门/评审机制是方法论无关的公共资产，放在基础包（electronics-core）里
     * 供各行业包按 key 复用；只看本包会把 10 条全部误判为悬空。
     */
    private String resolveProcessOwner(PackSource source, String processKey, Set<String> visited) {
        if (source == null || !visited.add(source.getId())) {
            return null;
        }
        if (source.exists("processes/" + processKey + ".bpmn20.xml")) {
            return source.getId();
        }
        String basePack = source.getManifest().getBasePackId();
        if (basePack == null) {
            return null;
        }
        return resolveProcessOwner(loader.find(basePack), processKey, visited);
    }

    private GateTemplate build(Map<?, ?> row, String industry, String tenant, int order) throws Exception {
        GateTemplate template = new GateTemplate(text(row.get("code")), text(row.get("name")));
        template.setOid(UUID.randomUUID().toString());
        template.setIndustry(industry);
        template.setStageKey(text(row.get("stage")));
        template.setType(text(row.get("type")));
        template.setProcessKey(text(row.get("process")));
        template.setDecisionOptions(toJson(row.get("decisionOptions")));
        template.setAutoChecks(toJson(row.get("autoChecks")));
        template.setRequiredParticipants(toJson(row.get("requiredParticipants")));
        template.setRecordFields(toJson(row.get("recordFields")));
        template.setEvidenceRequired(boolOr(row.get("evidenceRequired"), true));
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

    private String typeLabel(String type) {
        if ("TECHNICAL_REVIEW".equals(type)) return "技术评审";
        if ("DECISION_REVIEW".equals(type)) return "决策评审";
        return type == null ? "-" : type;
    }

    private String shortId(String packId) {
        if (packId == null) return "-";
        int at = packId.indexOf('@');
        return at < 0 ? packId : packId.substring(0, at);
    }

    /** JSON 数组元素个数（数 '"' 折半；数组里都是字符串或对象，都成对出现） */
    private int countArrayItems(String json) {
        if (json == null || json.isEmpty()) {
            return 0;
        }
        int quotes = 0;
        for (int i = 0; i < json.length(); i++) {
            if (json.charAt(i) == '"') {
                quotes++;
            }
        }
        return quotes / 2;
    }

    private int participantCount(GateTemplate template) {
        return countArrayItems(template.getRequiredParticipants());
    }

    /** 自动检查项条数（数 {@code "type"} 出现次数） */
    private int checkCount(GateTemplate template) {
        String checks = template.getAutoChecks();
        if (checks == null || checks.isEmpty()) {
            return 0;
        }
        int count = 0;
        int from = 0;
        String needle = "\"type\"";
        while (true) {
            int at = checks.indexOf(needle, from);
            if (at < 0) {
                break;
            }
            count++;
            from = at + needle.length();
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
