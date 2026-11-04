/*
 * Copyright (c) 2026~2028 深圳乘恺科技有限公司
 * All rights reserved.
 *
 * @author Kiween.Hu; Roney.Liu
 */

package cn.ck.plm.change.flow;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

/**
 * 变更任务状态机。
 *
 * <h3>状态</h3>
 * <pre>
 *   DRAFT 草稿（待派发） ──DISPATCH──▶ OPEN 待开始 ──START──▶ IN_WORK 进行中
 *                                                                │
 *                                                          SUBMIT ▼
 *                                                          SUBMITTED 待验收
 *                                                       ▲      │
 *                                        RETURN ────────┘      │ PASS
 *                                                              ▼
 *                                                         COMPLETED 已完成
 * </pre>
 *
 * <h3>为什么要有"草稿"</h3>
 * <p>更改事项 = 变更任务：通告还在草稿 / 审批中时，分工与计划只是<b>提案</b>。
 * 审批通过（含免审通过）那一刻才把整张清单一次性派发（{@code DRAFT → OPEN}），
 * 责任人这时才在任务中心看到自己的活。否则审批中被驳回、分工还会再改，
 * 每个候选人都先收到一条待办，等于让人对着废案干活。
 *
 * <h3>为什么要"待验收"</h3>
 * <p>与问题闭环同一口径：处理人自己说"改完了"不算完，要有人确认。
 * 否则执行处置（修订出 B 版）与"这次变更达到目的"会被混为一谈。
 *
 * <h3>谁能操作</h3>
 * <p>{@code START} / {@code SUBMIT} 只能由任务责任人执行 —— 别人代点会让
 * "谁在处理、谁认这个结果"无处落地。{@code PASS} / {@code RETURN} 不限（验收方可能是变更负责人）。
 * {@code DISPATCH} 是系统动作（审批通过时批量派发），不按责任人拦；手工补派由通告侧负责。
 */
public final class ChangeTaskFlow {

    // ==================== 状态 ====================

    /** 草稿：通告审批通过前只是计划，尚未派发 */
    public static final String DRAFT = "DRAFT";
    public static final String OPEN = "OPEN";
    public static final String IN_WORK = "IN_WORK";
    public static final String SUBMITTED = "SUBMITTED";
    public static final String COMPLETED = "COMPLETED";

    /** 初始状态 */
    public static final String INITIAL = DRAFT;

    private static final Set<String> STATUSES = new LinkedHashSet<>(java.util.List.of(
            DRAFT, OPEN, IN_WORK, SUBMITTED, COMPLETED));

    // ==================== 动作 ====================

    /** 派发：DRAFT → OPEN（通告审批通过时批量、或责任人手工补派） */
    public static final String DISPATCH = "DISPATCH";
    /** 开始处理：OPEN → IN_WORK */
    public static final String START = "START";
    /** 提交验收：IN_WORK → SUBMITTED */
    public static final String SUBMIT = "SUBMIT";
    /** 验收通过：SUBMITTED → COMPLETED */
    public static final String PASS = "PASS";
    /** 验收退回：SUBMITTED → IN_WORK（需 comment） */
    public static final String RETURN = "RETURN";

    private static final Map<String, Map<String, String>> RULES = new LinkedHashMap<>();

    private static final Map<String, String> LABELS = new LinkedHashMap<>();

    static {
        LABELS.put("CREATE", "新建变更任务");
        LABELS.put("UPDATE", "更新变更任务");
        LABELS.put(DISPATCH, "派发任务");
        LABELS.put(START, "开始处理");
        LABELS.put(SUBMIT, "提交验收");
        LABELS.put(PASS, "验收通过");
        LABELS.put(RETURN, "验收退回");

        rule(DISPATCH, DRAFT, OPEN);
        rule(START, OPEN, IN_WORK);
        rule(SUBMIT, IN_WORK, SUBMITTED);
        rule(PASS, SUBMITTED, COMPLETED);
        rule(RETURN, SUBMITTED, IN_WORK);
    }

    private ChangeTaskFlow() {
    }

    private static void rule(String action, String from, String to) {
        RULES.computeIfAbsent(action, k -> new LinkedHashMap<>()).put(from, to);
    }

    // ==================== 查询 ====================

    public static boolean isValidStatus(String status) {
        return status != null && STATUSES.contains(status);
    }

    /** 终态 */
    public static boolean isTerminal(String status) {
        return COMPLETED.equals(status);
    }

    /** 草稿：计划阶段，尚未派发给责任人（不出现在任务中心待办里） */
    public static boolean isDraft(String status) {
        return DRAFT.equals(status);
    }

    /** 已派发：责任人可见、可开工 */
    public static boolean isDispatched(String status) {
        return OPEN.equals(status) || IN_WORK.equals(status)
                || SUBMITTED.equals(status) || COMPLETED.equals(status);
    }

    /**
     * 该动作是否只能由任务责任人执行。
     *
     * <p>{@code START} / {@code SUBMIT} 是"处理人自己的活"；验收类归验收方，不在此列。
     */
    public static boolean requiresOwner(String action) {
        return START.equals(action) || SUBMIT.equals(action);
    }

    /** 该动作是否要求填说明 */
    public static boolean requiresComment(String action) {
        return RETURN.equals(action);
    }

    /**
     * 解析流转。
     *
     * @throws IllegalArgumentException 动作未知 / 当前状态不允许该动作
     */
    public static String resolve(String action, String fromStatus) {
        String normalized = action == null ? "" : action.trim().toUpperCase();
        Map<String, String> byFrom = RULES.get(normalized);
        if (byFrom == null) {
            throw new IllegalArgumentException("未知动作 '" + action + "'，取值只能是 " + RULES.keySet());
        }
        String to = byFrom.get(fromStatus);
        if (to == null) {
            throw new IllegalArgumentException(
                    "当前状态 " + fromStatus + " 不允许动作 " + normalized
                            + "（允许的当前状态：" + byFrom.keySet() + "）");
        }
        return to;
    }

    public static Set<String> statuses() {
        return Collections.unmodifiableSet(STATUSES);
    }

    public static String label(String action) {
        return LABELS.getOrDefault(action, action);
    }
}
