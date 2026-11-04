/*
 * Copyright (c) 2026~2028 深圳乘恺科技有限公司
 * All rights reserved.
 *
 * @author Kiween.Hu; Roney.Liu
 */

package cn.ck.plm.base.pack.install;

import cn.ck.plm.base.entity.ReviewTemplate;
import cn.ck.plm.base.mapper.GateTemplateMapper;
import cn.ck.plm.base.mapper.ReviewTemplateMapper;
import cn.ck.plm.base.pack.loader.IndustryPackLoader;
import cn.ck.plm.base.pack.model.PackManifest;
import cn.ck.plm.base.pack.model.PackSource;
import cn.ck.plm.base.util.TenantContext;
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
 * 评审模板安装器（{@code REVIEW}）—— 读包内 {@code reviews.json} 的 {@code items[]}。
 *
 * <p><b>注（2026-10-29 扁平化）</b>：技术门这一层已取消 —— smart-hardware-ipd 的技术评审现在
 * 由本安装器读 {@code technical-reviews.json}，条目带 {@code stage} 直接挂阶段（写
 * {@code ck_review_template.stage_key}），不再需要"先装门再装评审"的依赖顺序。
 * {@code gate} 仍保留解析：老包/老库里的评审是挂门的（gate 非空、stage 空），
 * 两条通道写同一张表、幂等键同为 {@code code + 目标租户}，不会重复也不会互相覆盖。
 *
 * <h3>五个口径</h3>
 * <ol>
 *   <li><b>行业/方法论与阶段模板同源</b>：清单没有 industry 字段，取包清单的 {@code devMethod}；</li>
 *   <li><b>幂等键 {@code code + 目标租户}</b>：已存在即 SKIP（企业裁剪不被覆盖），安装器永不 UPDATE；</li>
 *   <li><b>流程引用不许悬空</b>（包自己的硬规矩，与阶段门同款）：{@code process} 必须能在包链
 *       （本包 → basePack → …）里找到 {@code processes/<key>.bpmn20.xml}，否则记 <b>FAILED</b>；</li>
 *   <li><b>归属门引用</b>：{@code gate} 应指向已装入的 {@code ck_gate_template.code}。门还没装出来时
 *       记 <b>SKIP + 原因</b>（而不是 FAILED）—— 那是依赖顺序问题，不是数据缺陷：
 *       等门模板就绪后重装一次即可补齐，不该让整包跟着变成 PARTIAL；</li>
 *   <li><b>{@code inherit} 不消费</b>：父包由引擎先装。</li>
 * </ol>
 */
@Component
public class ReviewTemplateInstaller implements ResourceInstaller {

    private static final Logger log = LoggerFactory.getLogger(ReviewTemplateInstaller.class);

    private final ReviewTemplateMapper mapper;
    private final GateTemplateMapper gateMapper;
    private final IndustryPackLoader loader;

    public ReviewTemplateInstaller(ReviewTemplateMapper mapper,
                                   GateTemplateMapper gateMapper,
                                   IndustryPackLoader loader) {
        this.mapper = mapper;
        this.gateMapper = gateMapper;
        this.loader = loader;
    }

    @Override
    public String resourceType() {
        return "REVIEW";
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
                    "清单里没有 items 段（本包不含评审模板）"));
            return results;
        }

        String tenant = context.getTenantOid();
        String industry = resolveIndustry(context, json);
        log.debug("评审模板安装：industry={}（取包的方法论），items={} 条",
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
                // ① 流程引用（包自己的硬规矩）：悬空即 FAILED
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

                // ② 归属：优先 stage（技术评审直接挂阶段），其次 gate（老包/老库的挂门写法）
                String stage = text(row.get("stage"));
                String gate = text(row.get("gate"));
                if (stage == null && gate == null) {
                    results.add(InstallResult.failed(resourceType(), code,
                            "缺归属：评审必须写 stage（挂阶段）或 gate（挂门，兼容老写法）"));
                    continue;
                }
                if (stage != null) {
                    // 只做"能查就查"的校验：预演时链上包声明的阶段码是权威清单，
                    // 真装时不去查库（阶段模板可能来自父包、也可能被企业裁掉，不该因此阻断评审）
                    Set<String> declaredStages = context.isDryRun()
                            ? context.getChainDeclaredCodes("STAGE") : Set.of();
                    if (!declaredStages.isEmpty() && !declaredStages.contains(stage)) {
                        results.add(InstallResult.failed(resourceType(), code,
                                "归属阶段 '" + stage + "' 不在包链的 stages 清单里（拼错会让这份评审在界面上找不到落点）"));
                        continue;
                    }
                }
                // 门模板"就绪"的判据只对老写法（挂门）生效：挂阶段的评审不依赖任何门，
                // 也就不该因为"门还没装出来"被挡在外面 —— 那正是扁平化要消掉的依赖。
                // 门模板就绪以**本租户**为准；预演时链上包声明过也算。不传平台 oid（废弃的第三参）。
                if (stage == null && gateMapper.existsByCode(gate, tenant, tenant) <= 0
                        && !(context.isDryRun() && context.getChainDeclaredCodes("GATE").contains(gate))) {
                    results.add(InstallResult.skippedPending(resourceType(), code,
                            "归属阶段门 '" + gate + "' 尚未装入（待门模板就绪后重装即可补齐）"));
                    continue;
                }

                if (mapper.existsByCode(code, tenant, tenant) > 0) {
                    results.add(InstallResult.skipped(resourceType(), code,
                            "评审模板已存在（企业裁剪值不被覆盖）"));
                    continue;
                }
                ReviewTemplate template = build(row, industry, tenant, order);
                String ownerText = stage != null ? "挂阶段 " + stage : "挂门 " + gate;
                if (context.isDryRun()) {
                    results.add(InstallResult.inserted(resourceType(), code)
                            .why("预演：将新增评审「" + template.getName() + "」（" + ownerText
                                    + "，" + itemCount(template) + " 项检查，"
                                    + blockCount(template) + " 项 BLOCK）"));
                    continue;
                }
                mapper.insert(template);
                results.add(InstallResult.inserted(resourceType(), code)
                        .why("评审模板：" + template.getName() + "（" + ownerText
                                + "，" + itemCount(template) + " 项检查，"
                                + blockCount(template) + " 项 BLOCK，结论 "
                                + countArrayItems(template.getVerdictOptions()) + " 选"
                                + (processOwner == null ? "，无流程"
                                        : "，流程 " + processKey + "@" + shortId(processOwner)) + "）")
                        .on("ck_review_template", template.getOid()));
            } catch (Exception e) {
                results.add(InstallResult.failed(resourceType(), code,
                        e.getClass().getSimpleName() + ": " + e.getMessage()));
            }
        }
        log.debug("评审模板安装完成: {} 条明细", results.size());
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
     * 只查本包会把 8 条全部误判为悬空（流程在基础包 electronics-core 里）。
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

    private ReviewTemplate build(Map<?, ?> row, String industry, String tenant, int order) throws Exception {
        ReviewTemplate template = new ReviewTemplate(text(row.get("code")), text(row.get("name")));
        template.setOid(UUID.randomUUID().toString());
        template.setIndustry(industry);
        template.setStageKey(text(row.get("stage")));
        template.setGate(text(row.get("gate")));
        String type = text(row.get("type"));
        template.setType(type == null ? "TECHNICAL" : type.toUpperCase());
        template.setProcessKey(text(row.get("process")));
        template.setCheckItems(toJson(row.get("checkItems")));
        template.setOutputTypes(toJson(row.get("outputTypes")));
        template.setVerdictOptions(toJson(row.get("verdictOptions")));
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

    private String shortId(String packId) {
        if (packId == null) return "-";
        int at = packId.indexOf('@');
        return at < 0 ? packId : packId.substring(0, at);
    }

    /** JSON 数组元素个数（数 '"' 折半；数组里都是字符串，成对出现） */
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

    /** 检查项条数（数 {@code "seq"} 出现次数） */
    private int itemCount(ReviewTemplate template) {
        return occurrences(template.getCheckItems(), "\"seq\"");
    }

    /** BLOCK 级检查项条数 */
    private int blockCount(ReviewTemplate template) {
        return occurrences(template.getCheckItems(), "\"BLOCK\"");
    }

    private int occurrences(String text, String needle) {
        if (text == null || text.isEmpty()) {
            return 0;
        }
        int count = 0;
        int from = 0;
        while (true) {
            int at = text.indexOf(needle, from);
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
