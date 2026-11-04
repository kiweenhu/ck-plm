/*
 * Copyright (c) 2026~2028 深圳乘恺科技有限公司
 * All rights reserved.
 *
 * @author Kiween.Hu; Roney.Liu
 */

package cn.ck.plm.product.service.impl;

import cn.ck.plm.base.util.TenantContext;
import cn.ck.plm.process.service.api.ProcessEntitySetService;
import cn.ck.plm.product.entity.Deliverable;
import cn.ck.plm.product.mapper.DeliverableArtifactLinkMapper;
import cn.ck.plm.product.mapper.DeliverableMapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * 交付件状态求值器（P3 判定 · 2026-10-11 首版；2026-10-25 补 {@code ISSUE_CLOSED}、按模板顺序判生命周期、逐条依据）。
 *
 * <h3>用户口径</h3>
 * "如果绑定了对象修改更新状态；如果绑定的对象进入流程中也需要更新状态" —— 即交付件状态要
 * 真实反映产出物的进展，而不是快照时写死的 PENDING。
 *
 * <h3>判定规则</h3>
 * <ol>
 *   <li>{@code NA}（不适用）是人工决定，<b>永不覆盖</b>；</li>
 *   <li><b>无产出物关联</b> → {@code PENDING}（未开始）；</li>
 *   <li><b>有产出物关联</b> → 至少 {@code IN_PROGRESS}（进行中）—— 规则③同时覆盖
 *       "绑定了对象"与"绑定对象进入流程"（在流程中同样是进行中，且明确阻止 DONE）；</li>
 *   <li><b>全部完成条件可判定且满足</b> → {@code DONE}（已完成）：
 *     <ul>
 *       <li>{@code BOUND}：有产出物关联即满足（第 2 条已排除无关联的情况）；</li>
 *       <li>{@code LIFECYCLE(minState)}：全部关联对象的<b>最新迭代</b>状态 ≥ minState。
 *           "≥"按<b>该对象所用生命周期模板的 {@code sort_order}</b>比，不是写死的高低表 ——
 *           各租户的状态码是自己定义的（同一套 IPD 交付件写「已评审」，本租户文档模板里是
 *           {@code DRAFT / APPROVING 审核中 / PUBLISHED 已发布 / …}），写死的表里根本没有这些码；
 *           模板里连"同档位"都没有时（要求「已批准」而模板是 草稿→审核中→已发布，整档跳过），
 *           退到规范阶梯比档位，而不是判不可判定 —— 否则"已发布 + 流程已走完"永远停在进行中
 *           （2026-10-29 用户反馈）；</li>
 *       <li>{@code ISSUE_CLOSED(severity)}：该交付件<b>关联的产出物实例</b>上没有未闭环问题
 *           （关联靠 {@code ck_issue.related_type + related_code} 与产出物编码对齐；
 *           严重度按"≥ 规则给的值"算，{@code CLOSED} / {@code CANCELLED} 视为已闭环）；</li>
 *       <li>{@code REVIEW_PASSED / FIELD_CHECK / COVERAGE}：<b>平台尚未实现</b> → 视为"不可判定"；</li>
 *     </ul>
 *   </li>
 *   <li><b>存在不可判定子项，或任一子项不满足，或关联对象正在流程中</b> → 停在
 *       {@code IN_PROGRESS}。<b>宁缺勿滥</b>：不假装完成（与 PlatformCapabilities 同一口径），
 *       也不把"未完成"错报成"未开始"。</li>
 * </ol>
 *
 * <h3>为什么要给出逐条依据</h3>
 * 只有一枚「进行中」时，用户没法知道是"产出物还在流程里"、"状态没到已评审"还是
 * "有个阻断问题没关"（2026-10-25 用户就问过：产出物已发布、流程已走完，为什么还是进行中）。
 * {@link #judge} 因此返回 {@link Evaluation}：总状态 + 每条子项的
 * ✓ 满足 / ✗ 不满足 / ？不可判定 + 一句人话原因，界面直接展示，不必再猜。
 *
 * <h3>何时求值</h3>
 * 惰性：{@code GET /api/instances}（交付件面板数据源）返回前重算并写回；
 * 产出物关联的创建 / 解除后也立即重算（用户刚操作完，列表一刷新就该是新的）。
 * 不做定时任务、不在对象状态变更处挂钩 —— 状态是"看的时候对"，而不是"到处都可能写"。
 */
@Component
public class DeliverableStatusEvaluator {

    private static final Logger log = LoggerFactory.getLogger(DeliverableStatusEvaluator.class);

    private static final String PENDING = "PENDING";
    private static final String IN_PROGRESS = "IN_PROGRESS";
    private static final String DONE = "DONE";
    private static final String NA = "NA";

    /**
     * 规范生命周期阶梯 —— 只用于<b>把模板里写的 minState 翻译成"模板里的哪一档"</b>。
     * 真正的先后关系仍以该对象所用模板的 {@code sort_order} 为准（见 {@link TemplateStates}）。
     */
    private static final List<String> CANONICAL_LADDER =
            List.of("DRAFT", "INWORK", "REVIEW", "APPROVED", "RELEASED");

    /** 常见状态码 / 中文显示名 → 规范阶梯（覆盖各租户并行使用的几套词） */
    private static final Map<String, String> CANONICAL_ALIAS = new HashMap<>();

    static {
        for (String v : List.of("DRAFT", "草稿", "新建", "待开始", "OPEN", "NEW")) {
            CANONICAL_ALIAS.put(v, "DRAFT");
        }
        for (String v : List.of("INWORK", "IN_WORK", "IN_PROGRESS", "WORKING", "工作中", "进行中", "实施中")) {
            CANONICAL_ALIAS.put(v, "INWORK");
        }
        for (String v : List.of("REVIEW", "REVIEWING", "APPROVING", "评审中", "已评审", "审核中",
                "SUBMITTED", "待验收")) {
            CANONICAL_ALIAS.put(v, "REVIEW");
        }
        for (String v : List.of("APPROVED", "ACCEPTED", "已批准", "已审批", "已受理")) {
            CANONICAL_ALIAS.put(v, "APPROVED");
        }
        for (String v : List.of("RELEASED", "PUBLISHED", "COMPLETED", "CLOSED", "FROZEN",
                "已发布", "已完成", "已关闭", "已冻结")) {
            CANONICAL_ALIAS.put(v, "RELEASED");
        }
    }

    /** 问题严重度排序（与 ck_issue.severity 同一套码）；规则给 BLOCK 就只算 BLOCK，给 WARN 则 WARN+BLOCK */
    private static final Map<String, Integer> SEVERITY_RANK = Map.of("INFO", 1, "WARN", 2, "BLOCK", 3);

    /** 完成条件类型的显示名（界面上的"为什么还没完成"用这套词） */
    private static final Map<String, String> RULE_LABEL = Map.of(
            "BOUND", "已绑定产出物",
            "LIFECYCLE", "生命周期达标",
            "REVIEW_PASSED", "评审通过",
            "ISSUE_CLOSED", "阻断问题清零",
            "FIELD_CHECK", "字段校验",
            "COVERAGE", "覆盖率");

    private final DeliverableMapper deliverableMapper;
    private final DeliverableArtifactLinkMapper linkMapper;
    private final ProcessEntitySetService processEntitySetService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public DeliverableStatusEvaluator(DeliverableMapper deliverableMapper,
                                      DeliverableArtifactLinkMapper linkMapper,
                                      ProcessEntitySetService processEntitySetService) {
        this.deliverableMapper = deliverableMapper;
        this.linkMapper = linkMapper;
        this.processEntitySetService = processEntitySetService;
    }

    // ==================== 判定结果（给界面） ====================

    /**
     * 一条判定依据。
     *
     * <p>{@code satisfied} 三态：{@code true} 满足 / {@code false} 不满足 / {@code null} 不可判定
     * （平台尚未实现，或数据读不到）—— 后两种都不允许判已完成。
     */
    public static class RuleResult {

        private final String type;
        private final String label;
        private final Boolean satisfied;
        private final String detail;

        public RuleResult(String type, String label, Boolean satisfied, String detail) {
            this.type = type;
            this.label = label;
            this.satisfied = satisfied;
            this.detail = detail;
        }

        public String getType() { return type; }
        public String getLabel() { return label; }
        public Boolean getSatisfied() { return satisfied; }
        public String getDetail() { return detail; }

        public Map<String, Object> toMap() {
            Map<String, Object> map = new LinkedHashMap<>();
            map.put("type", type);
            map.put("label", label);
            map.put("satisfied", satisfied);
            map.put("detail", detail);
            return map;
        }
    }

    /** 一次判定的完整结果：总状态 + 逐条依据 */
    public static class Evaluation {

        private final String status;
        private final List<RuleResult> rules;

        public Evaluation(String status, List<RuleResult> rules) {
            this.status = status;
            this.rules = rules;
        }

        public String getStatus() { return status; }
        public List<RuleResult> getRules() { return rules; }

        public Map<String, Object> toMap() {
            Map<String, Object> map = new LinkedHashMap<>();
            map.put("status", status);
            List<Map<String, Object>> items = new ArrayList<>();
            for (RuleResult rule : rules) {
                items.add(rule.toMap());
            }
            map.put("rules", items);
            return map;
        }
    }

    /**
     * 判定所需的事实（由调用方一次备齐，判定本身不查库 —— 批量重算与浮层共用同一份输入）。
     *
     * <p>{@code openIssues} 为 <b>null</b> 表示"问题数据读不到"（{@code ISSUE_CLOSED} 因此不可判定），
     * 空表才表示"确实没有问题"。
     */
    public static class Facts {

        public List<Map<String, Object>> links = List.of();
        /** entityOid → 最新迭代状态 code */
        public Map<String, String> latestStatus = Map.of();
        /** entityOid → 该对象所用生命周期模板子版本 oid */
        public Map<String, String> templateOf = Map.of();
        /** 模板子版本 oid → 状态序列 */
        public Map<String, TemplateStates> templates = Map.of();
        /** 当前在流程中的产出物 oid */
        public Set<String> runningOids = Set.of();
        /** 该交付件产出物上未闭环的问题；null = 读不到 */
        public List<Map<String, Object>> openIssues = List.of();
    }

    /**
     * 某生命周期模板子版本的状态序列 —— "≥ 某状态"按这张表里的 {@code sort_order} 比。
     *
     * <p>为什么要按模板而不是写死的顺序：状态码由各租户模板定义，写死必然漏。
     */
    public static class TemplateStates {

        private final Map<String, Integer> orderByCode = new HashMap<>();
        private final Map<String, Integer> orderByName = new HashMap<>();
        private final Map<String, String> nameByCode = new HashMap<>();
        /** 规范阶梯下标 → 该模板里最早达到该档的 sort_order */
        private final Map<Integer, Integer> orderByLevel = new HashMap<>();

        private TemplateStates() {
        }

        /** 由 [{ statusCode, statusDisplayName, sortOrder }] 构造（批量查询与单测共用同一套装配） */
        public static TemplateStates of(List<Map<String, Object>> rows) {
            TemplateStates states = new TemplateStates();
            for (Map<String, Object> row : rows == null ? List.<Map<String, Object>>of() : rows) {
                Object sortOrder = row.get("sortOrder");
                Integer order;
                if (sortOrder instanceof Number n) {
                    order = n.intValue();
                } else if (sortOrder == null) {
                    order = null;
                } else {
                    order = Integer.valueOf(String.valueOf(sortOrder));
                }
                states.add(str(row.get("statusCode")), str(row.get("statusDisplayName")), order);
            }
            return states;
        }

        private void add(String code, String displayName, Integer sortOrder) {
            if (code == null || sortOrder == null) {
                return;
            }
            String upper = code.trim().toUpperCase(Locale.ROOT);
            orderByCode.putIfAbsent(upper, sortOrder);
            String name = displayName == null ? null : displayName.trim();
            if (name != null && !name.isEmpty()) {
                orderByName.putIfAbsent(name, sortOrder);
            }
            nameByCode.putIfAbsent(upper, name == null || name.isEmpty() ? code : name);
            String canonical = canonicalOf(upper);
            if (canonical != null) {
                orderByLevel.merge(CANONICAL_LADDER.indexOf(canonical), sortOrder, Math::min);
            }
        }

        /** 状态（code 或显示名）在该模板里的 sort_order；不认识返回 null */
        Integer orderOf(String raw) {
            if (raw == null) {
                return null;
            }
            String v = raw.trim();
            if (v.isEmpty()) {
                return null;
            }
            Integer byCode = orderByCode.get(v.toUpperCase(Locale.ROOT));
            return byCode != null ? byCode : orderByName.get(v);
        }

        /** 该模板里"最早达到规范阶梯第 level 档"的 sort_order；模板里没有这一档返回 null */
        Integer orderOfLevel(int level) {
            return orderByLevel.get(level);
        }

        /** 某 sort_order 对应的状态显示名（说明文案用） */
        String nameOfOrder(int order) {
            for (Map.Entry<String, Integer> entry : orderByCode.entrySet()) {
                if (entry.getValue() != null && entry.getValue() == order) {
                    return nameByCode.getOrDefault(entry.getKey(), entry.getKey());
                }
            }
            return String.valueOf(order);
        }
    }

    // ==================== 批量重算（写回） ====================

    /**
     * 重算某归属单元（系列 / 型号）下全部交付件的状态，写回与现值不同的那些。
     *
     * @return 实际更新条数
     */
    @Transactional
    public int reevaluate(String ownerOid) {
        if (ownerOid == null || ownerOid.trim().isEmpty()) {
            return 0;
        }
        if (TenantContext.getOrNull() == null) {
            // 无租户上下文（如启动期调用）不做判定：读不到关联与对象状态，算了也是错的
            return 0;
        }

        List<Deliverable> deliverables = deliverableMapper.selectByOwnerOid(ownerOid);
        if (deliverables == null || deliverables.isEmpty()) {
            return 0;
        }
        List<Map<String, Object>> links = linkMapper.selectByOwnerOid(ownerOid);

        // 按交付件分组关联
        Map<String, List<Map<String, Object>>> linksByDeliverable = new HashMap<>();
        for (Map<String, Object> link : links == null ? List.<Map<String, Object>>of() : links) {
            String did = str(link.get("deliverableOid"));
            if (did == null) continue;
            linksByDeliverable.computeIfAbsent(did, k -> new ArrayList<>()).add(link);
        }

        // 关联对象 → 最新迭代状态 + 所用模板（三类各查一次）
        Map<String, String> latestStatus = new HashMap<>();
        Map<String, String> templateOf = new HashMap<>();
        collectStatuses(links, latestStatus, templateOf);

        // 关联对象里哪些"当前大版本正在流程中"（按 typeCode 分组问流程侧，口径与发起闸门一致）
        Set<String> runningOids = collectRunningOids(links);

        // 挂在产出物上的未闭环问题（ISSUE_CLOSED 判定用；读不到 = 不可判定）
        Map<String, List<Map<String, Object>>> issuesByDeliverable = openIssuesByDeliverable(ownerOid);

        Map<String, TemplateStates> templates = templateStatesOf(templateOf);

        int updated = 0;
        for (Deliverable d : deliverables) {
            String current = d.getStatus();
            if (NA.equals(current)) {
                continue; // 人工决定，永不覆盖
            }
            Facts facts = new Facts();
            facts.links = linksByDeliverable.getOrDefault(d.getOid(), List.of());
            facts.latestStatus = latestStatus;
            facts.templateOf = templateOf;
            facts.templates = templates;
            facts.runningOids = runningOids;
            facts.openIssues = issuesByDeliverable == null ? null
                    : issuesByDeliverable.getOrDefault(d.getOid(), List.of());

            Evaluation evaluation = judge(d, facts);
            if (evaluation.getStatus() != null && !evaluation.getStatus().equals(current)) {
                deliverableMapper.updateStatus(d.getOid(), evaluation.getStatus());
                updated++;
            }
        }
        if (updated > 0) {
            log.info("交付件状态判定: owner={} 更新 {} 条", ownerOid, updated);
        }
        return updated;
    }

    /** 按"entityOid → 模板子版本 oid"批量取状态序列（一次查询；模板缺失时返回空表，判定退化为规范阶梯） */
    public Map<String, TemplateStates> templateStatesOf(Map<String, String> templateOf) {
        if (templateOf == null || templateOf.isEmpty()) {
            return Map.of();
        }
        List<String> oids = new ArrayList<>(new LinkedHashSet<>(templateOf.values()));
        for (int i = oids.size() - 1; i >= 0; i--) {
            if (oids.get(i) == null || oids.get(i).trim().isEmpty()) {
                oids.remove(i);
            }
        }
        if (oids.isEmpty()) {
            return Map.of();
        }
        Map<String, TemplateStates> result = new HashMap<>();
        try {
            Map<String, List<Map<String, Object>>> rowsByTemplate = new HashMap<>();
            for (Map<String, Object> row : linkMapper.selectTemplateStates(oids)) {
                String templateOid = str(row.get("templateIterationOid"));
                if (templateOid == null) continue;
                rowsByTemplate.computeIfAbsent(templateOid, k -> new ArrayList<>()).add(row);
            }
            for (Map.Entry<String, List<Map<String, Object>>> entry : rowsByTemplate.entrySet()) {
                result.put(entry.getKey(), TemplateStates.of(entry.getValue()));
            }
        } catch (Exception e) {
            // 拿不到模板顺序就退化为规范阶梯比较（能判的照判，判不了的自然成"不可判定"）
            log.warn("查询生命周期模板状态失败（LIFECYCLE 退化为规范阶梯比较）: {}", e.getMessage());
            return Map.of();
        }
        return result;
    }

    // ==================== 判定 ====================

    /**
     * 判定一个交付件（<b>纯函数，不写库</b>）—— 批量重算与「状态」列浮层共用同一套规则，
     * 保证"列表上的状态"和"浮层里解释的原因"永远一致。
     */
    public Evaluation judge(Deliverable d, Facts facts) {
        List<Map<String, Object>> links = facts.links == null ? List.<Map<String, Object>>of() : facts.links;
        Set<String> running = facts.runningOids == null ? Set.<String>of() : facts.runningOids;
        List<RuleResult> rules = new ArrayList<>();

        // 规则②：没有产出物 → 未开始（后面的条件无从谈起）
        if (links.isEmpty()) {
            rules.add(new RuleResult("BOUND", label("BOUND"), false, "尚未关联产出物"));
            return new Evaluation(PENDING, rules);
        }
        rules.add(new RuleResult("BOUND", label("BOUND"), true, "产出物 " + links.size() + " 项"));

        // 规则③之一：产出物还在流程中 → 活儿没干完，任何完成条件都不作数
        List<String> inProcess = new ArrayList<>();
        for (Map<String, Object> link : links) {
            String oid = str(link.get("entityOid"));
            if (oid != null && running.contains(oid)) {
                inProcess.add(describeLink(link));
            }
        }
        if (!inProcess.isEmpty()) {
            rules.add(new RuleResult("PROCESS", "产出物在流程中", false,
                    String.join("、", inProcess) + " 正在走流程"));
        }

        // 规则③之二：完成条件逐条（BOUND 已由上面那条覆盖，不重复列）
        JsonNode all = parseRules(d.getCompletionRule());
        if (all == null || all.isEmpty()) {
            rules.add(new RuleResult("RULE_MISSING", "完成条件", null,
                    "完成条件为空或无法解析 —— 按口径不判完成"));
        } else {
            for (JsonNode item : all) {
                String type = item.path("type").asText("");
                if ("BOUND".equals(type)) {
                    continue;
                }
                rules.add(judgeRule(item, facts));
            }
        }

        boolean allSatisfied = true;
        for (RuleResult rule : rules) {
            if (!Boolean.TRUE.equals(rule.getSatisfied())) {
                allSatisfied = false;
                break;
            }
        }
        return new Evaluation(allSatisfied ? DONE : IN_PROGRESS, rules);
    }

    /** 单条完成条件的判定 */
    private RuleResult judgeRule(JsonNode item, Facts facts) {
        String type = item.path("type").asText("");
        switch (type) {
            case "LIFECYCLE":
                return judgeLifecycle(item.path("minState").asText(""), facts);

            case "ISSUE_CLOSED": {
                if (facts.openIssues == null) {
                    // 问题数据读不到：既不能判满足（可能有问题没关），也不能说"有问题"—— 如实标不可判定
                    return new RuleResult(type, label(type), null,
                            "问题数据读取失败 —— 按口径不判完成");
                }
                String severity = item.path("severity").asText("").trim().toUpperCase(Locale.ROOT);
                int need = SEVERITY_RANK.getOrDefault(severity, 0);
                List<Map<String, Object>> hits = new ArrayList<>();
                for (Map<String, Object> issue : facts.openIssues) {
                    int rank = SEVERITY_RANK.getOrDefault(
                            String.valueOf(issue.get("severity")).trim().toUpperCase(Locale.ROOT), 0);
                    if (rank >= need) {
                        hits.add(issue);
                    }
                }
                String scope = need > 0 ? "「" + severity + "」" : "";
                if (hits.isEmpty()) {
                    return new RuleResult(type, label(type), true, "产出物上无未闭环" + scope + "问题");
                }
                return new RuleResult(type, label(type), false, describeIssues(hits) + " 未闭环");
            }

            case "REVIEW_PASSED":
            case "FIELD_CHECK":
            case "COVERAGE":
                // 平台尚未实现（无评审-交付件关联 / 无字段校验规则 / 无覆盖率口径）→ 不可判定
                return new RuleResult(type, label(type), null, "平台尚未实现该判定 —— 按口径不判完成");

            default:
                return new RuleResult(type, label(type), null,
                        "未知的完成条件类型 —— 按口径不判完成");
        }
    }

    /** LIFECYCLE：每个产出物的最新状态是否达到 minState（按各自模板的 sort_order 比） */
    private RuleResult judgeLifecycle(String minState, Facts facts) {
        String type = "LIFECYCLE";
        String requiredCanonical = canonicalOf(minState);
        if (requiredCanonical == null) {
            return new RuleResult(type, label(type), null,
                    "状态要求「" + minState + "」认不出来 —— 按口径不判完成");
        }
        int requiredLevel = CANONICAL_LADDER.indexOf(requiredCanonical);

        List<String> failing = new ArrayList<>();
        List<String> undecidable = new ArrayList<>();
        int passed = 0;
        for (Map<String, Object> link : facts.links) {
            String oid = str(link.get("entityOid"));
            String code = oid == null ? null : facts.latestStatus.get(oid);
            String templateOid = oid == null || facts.templateOf == null ? null : facts.templateOf.get(oid);
            TemplateStates states = templateOid == null || facts.templates == null
                    ? null : facts.templates.get(templateOid);

            Boolean ok = meetsLifecycle(link, code, minState, requiredLevel, states,
                    failing, undecidable);
            if (Boolean.TRUE.equals(ok)) {
                passed++;
            }
        }

        if (!undecidable.isEmpty()) {
            return new RuleResult(type, label(type), null,
                    String.join("、", undecidable) + " —— 无法判定（按口径不判完成）");
        }
        if (!failing.isEmpty()) {
            return new RuleResult(type, label(type), false,
                    String.join("、", failing) + " 未达「" + minState + "」");
        }
        return new RuleResult(type, label(type), true,
                passed + "/" + facts.links.size() + " 项达标（要求 ≥ " + minState + "）");
    }

    /**
     * 单个产出物是否达到 minState。
     *
     * @return true 达标 / false 不达标 / null 不可判定（已把原因写进 undecidable）
     */
    private Boolean meetsLifecycle(Map<String, Object> link, String code, String minState,
                                   int requiredLevel, TemplateStates states,
                                   List<String> failing, List<String> undecidable) {
        String who = describeLink(link);
        if (code == null) {
            undecidable.add(who + "：查不到迭代状态");
            return null;
        }
        if (states != null) {
            Integer requiredOrder = states.orderOf(minState);
            String note = "";
            if (requiredOrder == null) {
                // 该模板里没有这个状态（例：模板写「已评审」，租户模板只有 审核中 / 已发布）
                // → 退到"规范阶梯"的同档位（REVIEW 档 = 审核中），仍拿该模板的 sort_order 比大小
                Integer viaLevel = states.orderOfLevel(requiredLevel);
                if (viaLevel == null) {
                    // 连同档位都没有（2026-10-29 用户反馈：模板要求「已批准」，
                    // 而文档模板是 草稿 → 审核中 → 已发布，整条阶梯跳过了 APPROVED 档）。
                    // 这时不能判"不可判定"——那会让"已发布 + 流程已走完"的交付件永远停在进行中。
                    // 退到规范阶梯直接比档位：当前状态档 ≥ 要求档即达标（已发布 RELEASED ≥ 已批准 APPROVED）。
                    String currentCanonical = canonicalOf(code);
                    if (currentCanonical == null) {
                        undecidable.add(who + "：该对象所用生命周期模板里没有「" + minState
                                + "」这一档，当前状态 " + code + " 也认不出来");
                        return null;
                    }
                    if (CANONICAL_LADDER.indexOf(currentCanonical) >= requiredLevel) {
                        return true;
                    }
                    failing.add(who + "（" + stateName(states, code) + "，模板无「" + minState + "」档）");
                    return false;
                }
                requiredOrder = viaLevel;
                note = "（模板无「" + minState + "」，按「" + states.nameOfOrder(viaLevel) + "」档计）";
            }
            Integer currentOrder = states.orderOf(code);
            if (currentOrder == null) {
                undecidable.add(who + "：" + code + " 不在该对象所用生命周期模板的状态表里");
                return null;
            }
            if (currentOrder >= requiredOrder) {
                return true;
            }
            failing.add(who + "（" + stateName(states, code) + note + "）");
            return false;
        }

        // 模板缺失（存量数据 / 查询失败）→ 退化为规范阶梯比较，至少不会把"已发布"判成不达标
        String currentCanonical = canonicalOf(code);
        if (currentCanonical == null) {
            undecidable.add(who + "：" + code + " 认不出来");
            return null;
        }
        if (CANONICAL_LADDER.indexOf(currentCanonical) >= requiredLevel) {
            return true;
        }
        failing.add(who + "（" + code + "）");
        return false;
    }

    private static String stateName(TemplateStates states, String code) {
        Integer order = states.orderOf(code);
        return order == null ? code : states.nameOfOrder(order);
    }

    private static String label(String type) {
        return RULE_LABEL.getOrDefault(type, type);
    }

    /** 规范阶梯：状态码 / 中文名 → DRAFT / INWORK / REVIEW / APPROVED / RELEASED；认不出返回 null */
    private static String canonicalOf(String raw) {
        if (raw == null) {
            return null;
        }
        String v = raw.trim();
        if (v.isEmpty()) {
            return null;
        }
        String alias = CANONICAL_ALIAS.get(v);
        if (alias != null) {
            return alias;
        }
        return CANONICAL_ALIAS.get(v.toUpperCase(Locale.ROOT));
    }

    /** 产出物在一句话里的标识：编码优先，其次名称，最后 oid */
    private static String describeLink(Map<String, Object> link) {
        String code = str(link.get("entityCode"));
        if (code != null) return code;
        String name = str(link.get("entityName"));
        return name != null ? name : String.valueOf(link.get("entityOid"));
    }

    /** 未闭环问题的摘要（最多列 3 条，其余给条数） */
    private static String describeIssues(List<Map<String, Object>> issues) {
        List<String> parts = new ArrayList<>();
        int limit = Math.min(issues.size(), 3);
        for (int i = 0; i < limit; i++) {
            Map<String, Object> issue = issues.get(i);
            StringBuilder sb = new StringBuilder();
            String code = str(issue.get("issueCode"));
            sb.append(code == null ? "（无编号）" : code);
            String name = str(issue.get("issueName"));
            if (name != null) sb.append(" ").append(name);
            String severity = str(issue.get("severity"));
            String status = str(issue.get("issueStatus"));
            if (severity != null || status != null) {
                sb.append("（");
                sb.append(severity == null ? "—" : severity);
                sb.append(" · ").append(status == null ? "—" : status);
                sb.append("）");
            }
            parts.add(sb.toString());
        }
        String text = String.join("、", parts);
        return issues.size() > limit ? text + " 等 " + issues.size() + " 条" : text;
    }

    // ==================== 数据收集 ====================

    /** 关联对象的 minState 解析：{@code {"all":[...]}} → all 数组节点；解析失败返回 null */
    private JsonNode parseRules(String completionRule) {
        if (completionRule == null || completionRule.trim().isEmpty()) {
            return null;
        }
        try {
            JsonNode root = objectMapper.readTree(completionRule);
            JsonNode all = root.path("all");
            return all.isArray() ? all : null;
        } catch (Exception e) {
            log.warn("交付件完成条件解析失败（按不可判定处理）: {}", e.getMessage());
            return null;
        }
    }

    /** 批量取三类产出物的最新迭代状态与所用模板子版本 */
    private void collectStatuses(List<Map<String, Object>> links,
                                 Map<String, String> statusInto,
                                 Map<String, String> templateInto) {
        if (links == null || links.isEmpty()) {
            return;
        }
        List<String> docOids = new ArrayList<>();
        List<String> engOids = new ArrayList<>();
        List<String> partOids = new ArrayList<>();
        for (Map<String, Object> link : links) {
            String oid = str(link.get("entityOid"));
            if (oid == null) continue;
            switch (String.valueOf(link.get("entityType"))) {
                case "DOCUMENT" -> docOids.add(oid);
                case "ENG_DOCUMENT" -> engOids.add(oid);
                case "PART" -> partOids.add(oid);
                default -> { /* 未知类型：不参与判定（查不到状态自然不算达标） */ }
            }
        }
        putStatuses(docOids.isEmpty() ? null : linkMapper.selectLatestDocStatus(docOids), statusInto, templateInto);
        putStatuses(engOids.isEmpty() ? null : linkMapper.selectLatestEngDocStatus(engOids), statusInto, templateInto);
        putStatuses(partOids.isEmpty() ? null : linkMapper.selectLatestPartStatus(partOids), statusInto, templateInto);
    }

    private void putStatuses(List<Map<String, Object>> rows,
                             Map<String, String> statusInto,
                             Map<String, String> templateInto) {
        for (Map<String, Object> row : rows == null ? List.<Map<String, Object>>of() : rows) {
            String oid = str(row.get("entityOid"));
            if (oid == null) continue;
            String status = str(row.get("status"));
            if (status != null) {
                statusInto.put(oid, status);
            }
            String templateOid = str(row.get("lifecycleTemplateIterationOid"));
            if (templateOid != null) {
                templateInto.put(oid, templateOid);
            }
        }
    }

    /**
     * 某归属单元下挂在产出物上的未闭环问题，按交付件分组。
     *
     * @return 交付件 oid → 未闭环问题；<b>查询失败返回 null</b>（调用方据此把 ISSUE_CLOSED 标成不可判定，
     *         而不是当成"没有问题"—— 那会把没关的阻断问题判成已完成）
     */
    private Map<String, List<Map<String, Object>>> openIssuesByDeliverable(String ownerOid) {
        try {
            List<Map<String, Object>> rows = linkMapper.selectOpenIssuesOfOwnerArtifacts(ownerOid);
            Map<String, List<Map<String, Object>>> out = new HashMap<>();
            for (Map<String, Object> row : rows == null ? List.<Map<String, Object>>of() : rows) {
                String did = str(row.get("deliverableOid"));
                if (did == null) continue;
                out.computeIfAbsent(did, k -> new ArrayList<>()).add(row);
            }
            return out;
        } catch (Exception e) {
            log.warn("查询产出物未闭环问题失败（ISSUE_CLOSED 按不可判定处理）: owner={} err={}",
                    ownerOid, e.getMessage());
            return null;
        }
    }

    /** 关联对象里哪些"当前大版本正在流程中"（按 typeCode 分组调用流程侧，口径与发起闸门一致） */
    private Set<String> collectRunningOids(List<Map<String, Object>> links) {
        Set<String> result = new LinkedHashSet<>();
        if (links == null || links.isEmpty()) {
            return result;
        }
        Map<String, List<String>> byType = new LinkedHashMap<>();
        for (Map<String, Object> link : links) {
            String oid = str(link.get("entityOid"));
            if (oid == null) continue;
            String typeCode = str(link.get("typeCode"));
            byType.computeIfAbsent(typeCode == null ? "" : typeCode, k -> new ArrayList<>()).add(oid);
        }
        for (Map.Entry<String, List<String>> entry : byType.entrySet()) {
            try {
                List<String> running = processEntitySetService.findRunningEntityOids(
                        entry.getValue(), entry.getKey().isEmpty() ? null : entry.getKey());
                if (running != null) {
                    result.addAll(running);
                }
            } catch (Exception e) {
                // 流程侧查询失败不阻断判定：宁可只按"有无关联"给进行中，也不要把整个面板拖垮
                log.warn("查询对象流程状态失败（该组按未在流程处理）: type={} err={}", entry.getKey(), e.getMessage());
            }
        }
        return result;
    }

    private static String str(Object o) {
        if (o == null) return null;
        String s = String.valueOf(o).trim();
        return s.isEmpty() ? null : s;
    }
}
