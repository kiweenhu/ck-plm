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
 * 变更请求（ECR）状态机 —— <b>状态与流转规则的唯一出处</b>。
 *
 * <h3>状态</h3>
 * <pre>
 *   DRAFT 草稿 ──ANALYZE（开始影响分析）──▶ ANALYZING 影响分析中
 *      │   │                                   │
 *      │   └──────────── SUBMIT（发起评审）────┴──▶ REVIEWING 评审中
 *      │                                              │
 *      │                            ACCEPT ▼          ▼ REJECT
 *      │                             ACCEPTED 已受理  REJECTED 已否决
 *      │                                │                 │
 *      │                              CLOSE             REOPEN（回到 DRAFT）
 *      │                                ▼
 *      │                             CLOSED 已关闭
 *      └──────────────CANCEL──▶ CANCELLED 已取消（终态）
 * </pre>
 *
 * <p><b>ANALYZING 是"已经在评估、还没进评审"</b>（2026-11-01 按六段变更流程补）：跨职能影响
 * 分析（认证 / 客户 / 在制库存）通常在正式评审前就要做一轮，草稿与评审中之间没有落点，
 * 影响评估就只能挤在评审意见里。它<b>不是必经</b> —— 快速变更可以从草稿直接发起评审。
 *
 * <p><b>状态由谁写，按是否开启审批分两条支路</b>（2026-11-02）：
 * <ul>
 *   <li><b>开启审批</b>：{@code SUBMIT} 只负责拉起 change-request 流程，状态由流程里的
 *       「设置状态」服务（{@code change.setRequestStatus}，见 {@code ChangeRequestStatusHandler}）
 *       推进 —— 影响分析中（标准 / 重大）→ 评审中（影响分析会签通过）→ 已受理 / 已否决；
 *       单据上的 {@code ANALYZE} 会被服务层拒绝：影响分析是流程里的会签环节，
 *       两处都能改状态就分不清这张单到底评没评过；</li>
 *   <li><b>免审</b>：没有流程承载，状态只能由单据上的动作流转（ANALYZE / SUBMIT / ACCEPT / REJECT），
 *       此时重大变更发起评审前必须先填完认证 / 客户影响。</li>
 * </ul>
 *
 * <p><b>SUBMITTED 已提交是存量状态</b>：早期"提交"与"开始评审"分两步，2026-10-21 合并为
 * 一个动作「发起评审」后，新单据不再进入该状态；存量行仍允许 {@code SUBMIT} 直接收口到
 * REVIEWING，避免老数据卡在中间态无法推进。
 *
 * <h3>口径</h3>
 * <ul>
 *   <li><b>发起评审必须说清为什么</b>：{@code SUBMIT} 要求 {@code reason} 非空；</li>
 *   <li><b>否决与取消都要留话</b>：{@code REJECT} / {@code CANCEL} / {@code REOPEN} 必须有说明；</li>
 *   <li><b>关闭是人工动作，但要先派发再等子流程</b>：ECN 允许独立于 ECR 存在，故关闭仍由人触发；
 *       但本请求必须先派生<b>至少一个</b>变更通告（{@code ck_change_notice.request_oid}）——
 *       一个都没派生说明"怎么改"从未落到执行单上，直接关闭等于跳过整个实施过程；
 *       已派生的通告还须全部关闭（CLOSED / CANCELLED）后本请求才允许 CLOSE ——
 *       关账口径统一为「先派发、子先关、父后关」，见 {@code ChangeRequestServiceImpl#transition}。</li>
 * </ul>
 */
public final class ChangeRequestFlow {

    // ==================== 状态 ====================

    public static final String DRAFT = "DRAFT";
    /** 已提交 —— 仅存量数据（旧的"提交"与"开始评审"两步流程），新单据不再进入 */
    public static final String SUBMITTED = "SUBMITTED";
    /** 影响分析中：跨职能评估认证 / 客户 / 在制库存影响，评估完再发起评审 */
    public static final String ANALYZING = "ANALYZING";
    public static final String REVIEWING = "REVIEWING";
    public static final String ACCEPTED = "ACCEPTED";
    public static final String REJECTED = "REJECTED";
    public static final String CLOSED = "CLOSED";
    public static final String CANCELLED = "CANCELLED";

    /** 初始状态（新建） */
    public static final String INITIAL = DRAFT;

    private static final Set<String> STATUSES = new LinkedHashSet<>(java.util.List.of(
            DRAFT, SUBMITTED, ANALYZING, REVIEWING, ACCEPTED, REJECTED, CLOSED, CANCELLED));

    // ==================== 动作 ====================

    /**
     * 开始影响分析：DRAFT → ANALYZING。
     *
     * <p>对应六段流程的第②段：把"谁受影响、影响多大"从评审意见里拿出来，先在单据上评估完。
     * 非必经动作：快速变更可以直接 {@code SUBMIT}。
     */
    public static final String ANALYZE = "ANALYZE";

    /**
     * 发起评审：DRAFT / ANALYZING → REVIEWING（需 reason）。
     *
     * <p>原「提交」与「开始评审」两步合并而来 —— 提交的唯一去向就是评审，
     * 中间态 SUBMITTED 对用户没有决策价值，只是多点一次。
     * SUBMITTED 仅作为存量状态接受同一动作收口（见 {@link #SUBMITTED}）。
     */
    public static final String SUBMIT = "SUBMIT";
    /** 受理：REVIEWING → ACCEPTED */
    public static final String ACCEPT = "ACCEPT";
    /** 否决：REVIEWING → REJECTED（需 comment） */
    public static final String REJECT = "REJECT";
    /** 重新起草：REJECTED → DRAFT（需 comment） */
    public static final String REOPEN = "REOPEN";
    /** 关闭：ACCEPTED → CLOSED */
    public static final String CLOSE = "CLOSE";
    /** 取消：DRAFT / SUBMITTED / REVIEWING / REJECTED → CANCELLED（需 comment） */
    public static final String CANCEL = "CANCEL";

    private static final Map<String, Map<String, String>> RULES = new LinkedHashMap<>();

    private static final Map<String, String> LABELS = new LinkedHashMap<>();

    static {
        LABELS.put("CREATE", "提出变更请求");
        LABELS.put("UPDATE", "更新变更请求");
        LABELS.put(ANALYZE, "开始影响分析");
        LABELS.put(SUBMIT, "发起评审");
        LABELS.put(ACCEPT, "受理");
        LABELS.put(REJECT, "否决");
        LABELS.put(REOPEN, "重新起草");
        LABELS.put(CLOSE, "关闭");
        LABELS.put(CANCEL, "取消");

        // 第②段：草稿 → 影响分析（可跳过，直接发起评审）
        rule(ANALYZE, DRAFT, ANALYZING);

        // 一步到评审；第二/三行：存量 SUBMITTED、以及做过影响分析的单据同样一键收口
        rule(SUBMIT, DRAFT, REVIEWING);
        rule(SUBMIT, SUBMITTED, REVIEWING);
        rule(SUBMIT, ANALYZING, REVIEWING);
        rule(ACCEPT, REVIEWING, ACCEPTED);
        rule(REJECT, REVIEWING, REJECTED);
        rule(REOPEN, REJECTED, DRAFT);
        rule(CLOSE, ACCEPTED, CLOSED);
        rule(CANCEL, DRAFT, CANCELLED);
        rule(CANCEL, SUBMITTED, CANCELLED);
        rule(CANCEL, ANALYZING, CANCELLED);
        rule(CANCEL, REVIEWING, CANCELLED);
        rule(CANCEL, REJECTED, CANCELLED);
    }

    private ChangeRequestFlow() {
    }

    private static void rule(String action, String from, String to) {
        RULES.computeIfAbsent(action, k -> new LinkedHashMap<>()).put(from, to);
    }

    // ==================== 查询 ====================

    public static boolean isValidStatus(String status) {
        return status != null && STATUSES.contains(status);
    }

    /** 终态：不可再流转 */
    public static boolean isTerminal(String status) {
        return CLOSED.equals(status) || CANCELLED.equals(status);
    }

    /** 该状态是否允许编辑（关闭 / 取消后只能查看） */
    public static boolean isEditable(String status) {
        return !isTerminal(status);
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
        return REJECT.equals(action) || CANCEL.equals(action) || REOPEN.equals(action);
    }

    /**
     * 该动作是否只能由<b>责任人</b>执行。
     *
     * <p>变更请求是"一个人的活"：单据上能推进它的是责任人（{@code owner}），
     * 而不是"有没有变更控制菜单权限"。除「详情」外，其余动作一律归责任人。
     *
     * <p>评审结论（{@code ACCEPT} / {@code REJECT}）虽然也算动作，但走流程回调时
     * 直接改 mapper（见 {@code ChangeRequestReviewOutcomeHandler}），不经过本校验 ——
     * 审批人是评审人，不一定是责任人，不能混为一谈。
     */
    public static boolean requiresOwner(String action) {
        return ANALYZE.equals(action) || SUBMIT.equals(action) || ACCEPT.equals(action)
                || REJECT.equals(action) || REOPEN.equals(action) || CLOSE.equals(action) || CANCEL.equals(action);
    }

    /** 该动作是否要求填变更原因 */
    public static boolean requiresReason(String action) {
        return SUBMIT.equals(action);
    }

    public static Set<String> statuses() {
        return Collections.unmodifiableSet(STATUSES);
    }

    public static String label(String action) {
        return LABELS.getOrDefault(action, action);
    }
}
