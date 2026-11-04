/*
 * Copyright (c) 2026~2028 深圳乘恺科技有限公司
 * All rights reserved.
 *
 * @author Kiween.Hu; Roney.Liu
 */

package cn.ck.plm.product.service.impl;

import cn.ck.plm.product.entity.Deliverable;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 交付件完成判定 —— 这些断言就是"什么时候能显示已完成"这条承诺的凭据。
 *
 * <p>{@code judge} 是纯函数（不查库、不写库），这里直接给关联 / 状态 / 模板顺序 / 在流程中 / 未闭环问题，
 * 校验总状态与<b>逐条依据</b> —— 用户问的"产出物已发布、流程也走完了，为什么还是进行中"，
 * 答案必须能从 {@code rules} 里读出来。
 */
class DeliverableStatusEvaluatorTest {

    private final DeliverableStatusEvaluator evaluator = new DeliverableStatusEvaluator(null, null, null);

    private static Deliverable deliverable(String completionRule) {
        Deliverable d = new Deliverable();
        d.setOid("dlv-1");
        d.setOwnerOid("owner-1");
        d.setStatus("PENDING");
        d.setCompletionRule(completionRule);
        return d;
    }

    private static Map<String, Object> link(String oid, String type, String code) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("entityOid", oid);
        row.put("entityType", type);
        row.put("entityCode", code);
        return row;
    }

    private static Map<String, Object> issue(String code, String name, String severity, String status) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("issueCode", code);
        row.put("issueName", name);
        row.put("severity", severity);
        row.put("issueStatus", status);
        return row;
    }

    private static Map<String, Object> state(String code, String name, int sortOrder) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("statusCode", code);
        row.put("statusDisplayName", name);
        row.put("sortOrder", sortOrder);
        return row;
    }

    /** 本租户文档模板的真实形态：没有「已评审」，只有 审核中 / 已发布 */
    private static DeliverableStatusEvaluator.TemplateStates docTemplate() {
        return DeliverableStatusEvaluator.TemplateStates.of(List.of(
                state("DRAFT", "草稿", 1),
                state("APPROVING", "审核中", 2),
                state("PUBLISHED", "已发布", 3),
                state("OFFLINE", "已下线", 4),
                state("ARCHIVED", "已归档", 5)));
    }

    /** 事实装配（只填用例关心的部分） */
    private static DeliverableStatusEvaluator.Facts facts(List<Map<String, Object>> links,
                                                          Map<String, String> statuses,
                                                          DeliverableStatusEvaluator.TemplateStates template,
                                                          Set<String> running,
                                                          List<Map<String, Object>> issues) {
        DeliverableStatusEvaluator.Facts facts = new DeliverableStatusEvaluator.Facts();
        facts.links = links;
        facts.latestStatus = statuses;
        facts.runningOids = running;
        facts.openIssues = issues;
        if (template != null) {
            Map<String, String> templateOf = new LinkedHashMap<>();
            for (Map<String, Object> link : links) {
                templateOf.put(String.valueOf(link.get("entityOid")), "tpl-1");
            }
            facts.templateOf = templateOf;
            facts.templates = Map.of("tpl-1", template);
        }
        return facts;
    }

    private static Map<String, Object> ruleOf(DeliverableStatusEvaluator.Evaluation evaluation, String type) {
        for (DeliverableStatusEvaluator.RuleResult rule : evaluation.getRules()) {
            if (type.equals(rule.getType())) {
                return rule.toMap();
            }
        }
        return null;
    }

    @Test
    void 没有产出物就是未开始() {
        DeliverableStatusEvaluator.Evaluation evaluation = evaluator.judge(
                deliverable("{\"all\":[{\"type\":\"BOUND\"}]}"), facts(List.of(), Map.of(), null, Set.of(), List.of()));

        assertThat(evaluation.getStatus()).isEqualTo("PENDING");
        assertThat(evaluation.getRules()).hasSize(1);
        assertThat(evaluation.getRules().get(0).getSatisfied()).isFalse();
    }

    @Test
    void 绑定_生命周期达标_无阻断问题即为已完成() {
        String rule = "{\"all\":[{\"type\":\"BOUND\"},"
                + "{\"type\":\"LIFECYCLE\",\"minState\":\"已评审\"},"
                + "{\"type\":\"ISSUE_CLOSED\",\"severity\":\"BLOCK\"}]}";
        List<Map<String, Object>> links = List.of(link("doc-1", "DOCUMENT", "DOC-202610-0001"));

        DeliverableStatusEvaluator.Evaluation evaluation = evaluator.judge(deliverable(rule),
                facts(links, Map.of("doc-1", "PUBLISHED"), docTemplate(), Set.of(), List.of()));

        assertThat(evaluation.getStatus()).isEqualTo("DONE");
        assertThat(evaluation.getRules()).allMatch(r -> Boolean.TRUE.equals(r.getSatisfied()));
        // BOUND 只出现一次（模板里的 BOUND 不再重复成一条）
        assertThat(evaluation.getRules().stream().filter(r -> "BOUND".equals(r.getType())).count()).isEqualTo(1);
    }

    @Test
    void 模板里没有已评审_已发布仍算达标且说明按哪一档计() {
        String rule = "{\"all\":[{\"type\":\"LIFECYCLE\",\"minState\":\"已评审\"}]}";
        List<Map<String, Object>> links = List.of(link("doc-1", "DOCUMENT", "DOC-202610-0001"));

        DeliverableStatusEvaluator.Evaluation evaluation = evaluator.judge(deliverable(rule),
                facts(links, Map.of("doc-1", "PUBLISHED"), docTemplate(), Set.of(), List.of()));

        // 关键回归：写死顺序表的年代，PUBLISHED 认不出来 → 被误判成"未达已评审"
        assertThat(evaluation.getStatus()).isEqualTo("DONE");
        Map<String, Object> lifecycle = ruleOf(evaluation, "LIFECYCLE");
        assertThat(lifecycle.get("detail").toString()).contains("1/1 项达标");
    }

    @Test
    void 还在审核中时不算达到已评审() {
        String rule = "{\"all\":[{\"type\":\"LIFECYCLE\",\"minState\":\"已评审\"}]}";
        List<Map<String, Object>> links = List.of(link("doc-1", "DOCUMENT", "DOC-202610-0001"));

        // 审核中(sort=2) 与「已评审」同档（模板无该状态时按 REVIEW 档定位到 审核中）→ 达标；
        // 这里验证的是"草稿(1) 显然不达标"
        DeliverableStatusEvaluator.Evaluation draft = evaluator.judge(deliverable(rule),
                facts(links, Map.of("doc-1", "DRAFT"), docTemplate(), Set.of(), List.of()));

        assertThat(draft.getStatus()).isEqualTo("IN_PROGRESS");
        assertThat(ruleOf(draft, "LIFECYCLE").get("detail").toString()).contains("未达「已评审」");
    }

    @Test
    void 产出物上还有阻断问题就不算完成() {
        String rule = "{\"all\":[{\"type\":\"ISSUE_CLOSED\",\"severity\":\"BLOCK\"}]}";
        List<Map<String, Object>> links = List.of(link("doc-1", "DOCUMENT", "DOC-202610-0001"));
        List<Map<String, Object>> issues = List.of(issue("ISS-0002", "试制问题", "BLOCK", "VERIFYING"));

        DeliverableStatusEvaluator.Evaluation evaluation = evaluator.judge(deliverable(rule),
                facts(links, Map.of("doc-1", "PUBLISHED"), docTemplate(), Set.of(), issues));

        assertThat(evaluation.getStatus()).isEqualTo("IN_PROGRESS");
        Map<String, Object> closedRule = ruleOf(evaluation, "ISSUE_CLOSED");
        assertThat(closedRule.get("satisfied")).isEqualTo(false);
        assertThat(closedRule.get("detail").toString()).contains("ISS-0002").contains("VERIFYING");
    }

    @Test
    void 规则只要阻断问题_warn问题不挡完成() {
        String rule = "{\"all\":[{\"type\":\"ISSUE_CLOSED\",\"severity\":\"BLOCK\"}]}";
        List<Map<String, Object>> links = List.of(link("doc-1", "DOCUMENT", "DOC-202610-0001"));
        List<Map<String, Object>> issues = List.of(issue("ISS-0001", "轻微问题", "WARN", "OPEN"));

        DeliverableStatusEvaluator.Evaluation evaluation = evaluator.judge(deliverable(rule),
                facts(links, Map.of("doc-1", "PUBLISHED"), docTemplate(), Set.of(), issues));

        assertThat(evaluation.getStatus()).isEqualTo("DONE");
    }

    @Test
    void 问题数据读不到时按不可判定处理而不是当成没有() {
        String rule = "{\"all\":[{\"type\":\"ISSUE_CLOSED\",\"severity\":\"BLOCK\"}]}";
        List<Map<String, Object>> links = List.of(link("doc-1", "DOCUMENT", "DOC-202610-0001"));

        DeliverableStatusEvaluator.Evaluation evaluation = evaluator.judge(deliverable(rule),
                facts(links, Map.of("doc-1", "PUBLISHED"), docTemplate(), Set.of(), null));

        assertThat(evaluation.getStatus()).isEqualTo("IN_PROGRESS");
        Map<String, Object> closedRule = ruleOf(evaluation, "ISSUE_CLOSED");
        assertThat(closedRule.get("satisfied")).isNull();
        assertThat(closedRule.get("detail").toString()).contains("读取失败");
    }

    @Test
    void 产出物在流程中时不判完成() {
        String rule = "{\"all\":[{\"type\":\"BOUND\"}]}";
        List<Map<String, Object>> links = List.of(link("doc-1", "DOCUMENT", "DOC-202610-0001"));

        DeliverableStatusEvaluator.Evaluation evaluation = evaluator.judge(deliverable(rule),
                facts(links, Map.of("doc-1", "PUBLISHED"), docTemplate(), Set.of("doc-1"), List.of()));

        assertThat(evaluation.getStatus()).isEqualTo("IN_PROGRESS");
        Map<String, Object> processRule = ruleOf(evaluation, "PROCESS");
        assertThat(processRule.get("satisfied")).isEqualTo(false);
        assertThat(processRule.get("detail").toString()).contains("DOC-202610-0001");
    }

    @Test
    void 平台未实现的子项一律不判完成() {
        String rule = "{\"all\":[{\"type\":\"REVIEW_PASSED\"},{\"type\":\"FIELD_CHECK\"},"
                + "{\"type\":\"COVERAGE\"},{\"type\":\"SOMETHING_NEW\"}]}";
        List<Map<String, Object>> links = List.of(link("doc-1", "DOCUMENT", "DOC-202610-0001"));

        DeliverableStatusEvaluator.Evaluation evaluation = evaluator.judge(deliverable(rule),
                facts(links, Map.of("doc-1", "PUBLISHED"), docTemplate(), Set.of(), List.of()));

        assertThat(evaluation.getStatus()).isEqualTo("IN_PROGRESS");
        List<Boolean> undecided = new ArrayList<>();
        for (DeliverableStatusEvaluator.RuleResult ruleResult : evaluation.getRules()) {
            if (!"BOUND".equals(ruleResult.getType())) {
                undecided.add(ruleResult.getSatisfied());
            }
        }
        // 四个未实现/未知的类型都是"不可判定"（null），不是"不满足"
        assertThat(undecided).hasSize(4).allMatch(satisfied -> satisfied == null);
    }

    /**
     * 2026-10-29 用户反馈：产出物已发布、绑定它的流程也走完了，交付件还是「进行中」。
     *
     * <p>根因：交付件模板要求 minState「已批准」（规范阶梯的 APPROVED 档），
     * 而本租户文档生命周期是 草稿 → 审核中 → 已发布，**整条阶梯没有 APPROVED 这一档**，
     * 旧逻辑找不到同档位就判"不可判定"，于是永远不判完成。
     */
    @Test
    void 模板整档缺失时_已发布仍算达到已批准() {
        String rule = "{\"all\":[{\"type\":\"BOUND\"},{\"type\":\"LIFECYCLE\",\"minState\":\"已批准\"}]}";
        List<Map<String, Object>> links = List.of(link("doc-1", "DOCUMENT", "DOC-202610-0004"));

        DeliverableStatusEvaluator.Evaluation published = evaluator.judge(deliverable(rule),
                facts(links, Map.of("doc-1", "PUBLISHED"), docTemplate(), Set.of(), List.of()));
        assertThat(published.getStatus()).isEqualTo("DONE");
        assertThat(ruleOf(published, "LIFECYCLE").get("detail").toString()).contains("1/1 项达标");

        // 草稿显然不达标：这一档缺失不能变成"什么都算过"
        DeliverableStatusEvaluator.Evaluation draft = evaluator.judge(deliverable(rule),
                facts(links, Map.of("doc-1", "DRAFT"), docTemplate(), Set.of(), List.of()));
        assertThat(draft.getStatus()).isEqualTo("IN_PROGRESS");
        assertThat(ruleOf(draft, "LIFECYCLE").get("detail").toString()).contains("未达「已批准」");
    }
}
