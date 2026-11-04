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
 * 变更通告（ECN）状态机 —— <b>状态与流转规则的唯一出处</b>。
 *
 * <h3>状态</h3>
 * <pre>
 *   DRAFT 草稿 ──SUBMIT──▶ APPROVING 审批中（需要审批：提交即提交审批）
 *        │                      │
 *        └─START_APPROVAL──▶ SUBMITTED 已提交（存量单据的中间态，补点用）
 *                               │
 *                               └──START_APPROVAL──▶ APPROVING
 *                                                       │
 *                                         APPROVE ▼     ▼ REJECT
 *                                            APPROVED   REJECTED ──REVISE──▶ DRAFT
 *                                                │
 *                                          START_WORK
 *                                                ▼
 *                                            IN_WORK 实施中 ──COMPLETE──▶ COMPLETED
 *                                                                           │
 *                                                                         CLOSE
 *                                                                           ▼
 *                                                                        CLOSED
 *   任意未完成状态 ──CANCEL──▶ CANCELLED
 * </pre>
 *
 * <h3>提交即提交审批（2026-11-03）</h3>
 * <p>租户配置为"需要审批"时，{@code SUBMIT} 直接把流程拉起来（状态直接落到 {@code APPROVING}），
 * 不再先停在 {@code SUBMITTED} 等人工点 {@code START_APPROVAL} —— 中间那个状态没有决策价值，
 * 只会让单据卡在"已提交"没人管。{@code SUBMITTED} 与 {@code START_APPROVAL} 保留，
 * 只为兼容存量单据（以及流程 key 后来才配好的租户）。
 *
 * <h3>免审不产生分叉</h3>
 * <p>免审不是"跳过状态"，而是提交后立刻再走一次 {@code APPROVE}：
 * 所有 ECN 都会经过 {@code APPROVED}，下游（锁对象、执行修订、完成判据）无需分支判断。
 * 差别只写在数据上（{@code approval_bypassed = true} + 免审原因）。
 *
 * <h3>状态与流程引擎的职责划分</h3>
 * <p>流程引擎只负责"谁点同意"，不负责状态机：{@code APPROVING → APPROVED/REJECTED}
 * 由流程结束回调驱动；其余流转由本表决定。
 */
public final class ChangeNoticeFlow {

    // ==================== 状态 ====================

    public static final String DRAFT = "DRAFT";
    public static final String SUBMITTED = "SUBMITTED";
    public static final String APPROVING = "APPROVING";
    public static final String APPROVED = "APPROVED";
    public static final String IN_WORK = "IN_WORK";
    public static final String COMPLETED = "COMPLETED";
    public static final String CLOSED = "CLOSED";
    public static final String REJECTED = "REJECTED";
    public static final String CANCELLED = "CANCELLED";

    /** 初始状态（新建） */
    public static final String INITIAL = DRAFT;

    private static final Set<String> STATUSES = new LinkedHashSet<>(java.util.List.of(
            DRAFT, SUBMITTED, APPROVING, APPROVED, IN_WORK, COMPLETED, CLOSED, REJECTED, CANCELLED));

    /** 进行中（未结束）的状态 —— 变更占用锁只在这些状态下成立 */
    private static final Set<String> ACTIVE = new LinkedHashSet<>(java.util.List.of(
            DRAFT, SUBMITTED, APPROVING, APPROVED, IN_WORK));

    // ==================== 动作 ====================

    /** 提交：DRAFT → SUBMITTED（要求至少一个受影响对象） */
    public static final String SUBMIT = "SUBMIT";
    /** 发起审批：SUBMITTED → APPROVING（启动流程实例） */
    public static final String START_APPROVAL = "START_APPROVAL";
    /** 审批通过：APPROVING → APPROVED（流程回调驱动；免审时由 submit 自动触发） */
    public static final String APPROVE = "APPROVE";
    /** 审批驳回：APPROVING → REJECTED（需 comment） */
    public static final String REJECT = "REJECT";
    /** 修改后重新提交：REJECTED → DRAFT（需 comment） */
    public static final String REVISE = "REVISE";
    /** 开始实施：APPROVED → IN_WORK */
    public static final String START_WORK = "START_WORK";
    /** 实施完成：IN_WORK → COMPLETED（要求所有受影响对象已执行） */
    public static final String COMPLETE = "COMPLETE";
    /** 关闭：COMPLETED → CLOSED（要求其下变更任务全部 COMPLETED —— 子流程先关） */
    public static final String CLOSE = "CLOSE";
    /** 取消：未完成状态 → CANCELLED（需 comment） */
    public static final String CANCEL = "CANCEL";
    /**
     * 管理员设置状态（运维纠错，不参与状态机校验）。
     *
     * <p>刻意<b>不</b>写进 {@link #RULES}：它不是一条流转，而是"跳过门槛直接落在某个状态"，
     * 由 {@code ChangeNoticeService#setStatusByAdmin} 自己校验（仅租户管理员、原因必填、
     * 不能设置为审批中）。放进 RULES 会让它被 {@link #resolve} 当成普通动作，
     * 谁都能顺着 {@code /transition} 调 —— 那就等于把状态机拆了。
     */
    public static final String ADMIN_SET_STATUS = "ADMIN_SET_STATUS";

    private static final Map<String, Map<String, String>> RULES = new LinkedHashMap<>();

    private static final Map<String, String> LABELS = new LinkedHashMap<>();

    static {
        LABELS.put("CREATE", "新建变更通告");
        LABELS.put("UPDATE", "更新变更通告");
        LABELS.put("ADD_AFFECTED", "添加受影响对象");
        LABELS.put("REMOVE_AFFECTED", "移除受影响对象");
        LABELS.put("ASSIGN_AFFECTED", "调整更改事项归属");
        LABELS.put(SUBMIT, "提交");
        LABELS.put(START_APPROVAL, "发起审批");
        LABELS.put(APPROVE, "审批通过");
        LABELS.put(REJECT, "审批驳回");
        LABELS.put(REVISE, "修改后重新提交");
        LABELS.put(START_WORK, "开始实施");
        LABELS.put(COMPLETE, "实施完成");
        LABELS.put(CLOSE, "关闭");
        LABELS.put(CANCEL, "取消");
        LABELS.put(ADMIN_SET_STATUS, "管理员设置状态");

        rule(SUBMIT, DRAFT, SUBMITTED);
        rule(START_APPROVAL, SUBMITTED, APPROVING);
        rule(APPROVE, APPROVING, APPROVED);
        rule(REJECT, APPROVING, REJECTED);
        rule(REVISE, REJECTED, DRAFT);
        rule(START_WORK, APPROVED, IN_WORK);
        rule(COMPLETE, IN_WORK, COMPLETED);
        rule(CLOSE, COMPLETED, CLOSED);
        rule(CANCEL, DRAFT, CANCELLED);
        rule(CANCEL, SUBMITTED, CANCELLED);
        rule(CANCEL, APPROVING, CANCELLED);
        rule(CANCEL, APPROVED, CANCELLED);
        rule(CANCEL, IN_WORK, CANCELLED);
    }

    private ChangeNoticeFlow() {
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
        return CLOSED.equals(status) || CANCELLED.equals(status);
    }

    /** 是否处于进行中（持有变更占用） */
    public static boolean isActive(String status) {
        return status != null && ACTIVE.contains(status);
    }

    /** 该状态是否允许编辑基本信息 */
    public static boolean isEditable(String status) {
        return DRAFT.equals(status) || REJECTED.equals(status);
    }

    /** 该状态是否允许增删受影响对象 */
    public static boolean isAffectedEditable(String status) {
        return DRAFT.equals(status) || REJECTED.equals(status);
    }

    /**
     * 该状态是否允许规划变更任务。
     *
     * <p>计划阶段（草稿 / 已驳回）与实施阶段（已批准 / 实施中）都可以追加任务：
     * 前者是排工，后者是执行中发现漏项 —— 唯独审批中不允许改分工。
     */
    public static boolean isTaskPlannable(String status) {
        return DRAFT.equals(status) || REJECTED.equals(status)
                || APPROVED.equals(status) || IN_WORK.equals(status);
    }

    /**
     * 该状态是否属于"审批前"（草稿 / 已驳回）。
     *
     * <p>更改事项（= 变更任务）在审批前只是清单，状态 DRAFT；审批通过时统一派发。
     * 审批后补录的事项没有"待派发"这一步，创建即 OPEN。
     */
    public static boolean isPlannedBeforeApproval(String status) {
        return DRAFT.equals(status) || REJECTED.equals(status);
    }

    /** 该状态是否已过审批（允许派发任务） */
    public static boolean isDispatchable(String status) {
        return APPROVED.equals(status) || IN_WORK.equals(status)
                || COMPLETED.equals(status);
    }

    /**
     * 解析流转：给定动作与当前状态，返回目标状态。
     *
     * @throws IllegalArgumentException 动作未知 / 当前状态不允许该动作（消息面向用户）
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

    /** 该动作是否要求填说明 */
    public static boolean requiresComment(String action) {
        return REJECT.equals(action) || REVISE.equals(action) || CANCEL.equals(action);
    }

    /**
     * 该动作是否只能由<b>责任人</b>执行。
     *
     * <p>提交、发起审批、修改后重提、开始实施、实施完成、关闭、取消 —— 都是责任人推进单据的动作。
     *
     * <p>{@code APPROVE} / {@code REJECT} 不在其列：这两个是<b>审批人</b>的结论，
     * 且实际由流程回调直接写库（{@code ChangeNoticeApprovalOutcomeHandler}），
     * 若按责任人拦住，等于让被审人自己决定审批结果。
     */
    public static boolean requiresOwner(String action) {
        return SUBMIT.equals(action) || START_APPROVAL.equals(action) || REVISE.equals(action)
                || START_WORK.equals(action) || COMPLETE.equals(action)
                || CLOSE.equals(action) || CANCEL.equals(action);
    }

    public static Set<String> statuses() {
        return Collections.unmodifiableSet(STATUSES);
    }

    public static String label(String action) {
        return LABELS.getOrDefault(action, action);
    }
}
