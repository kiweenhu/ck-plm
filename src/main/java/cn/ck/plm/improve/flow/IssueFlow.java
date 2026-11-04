/*
 * Copyright (c) 2026~2028 深圳乘恺科技有限公司
 * All rights reserved.
 */

package cn.ck.plm.improve.flow;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

/**
 * 问题闭环状态机 —— <b>状态与流转规则的唯一出处</b>（服务端校验、前端按钮都用这一套口径）。
 *
 * <h3>五态</h3>
 * <pre>
 *   OPEN 待处理 ──START──▶ IN_PROGRESS 处理中 ──SUBMIT_VERIFY──▶ VERIFYING 待验证
 *        │                       │                                  │
 *        │                       │                        VERIFY_PASS│VERIFY_FAIL（退回）
 *        │                       │                                  ▼
 *        │                       │                              CLOSED 已关闭 ──REOPEN──▶ OPEN
 *        └─────────────CANCEL────┴──────────CANCEL────────────┐
 *                                                             ▼
 *                                                       CANCELLED 已取消（终态）
 * </pre>
 *
 * <h3>闭环的设计意图</h3>
 * <ul>
 *   <li><b>不许一步关闭</b>：必须经 VERIFYING（提交验证）再由 VERIFY_PASS 关闭 ——
 *       处理人自己说"好了"不算数，要有人验证。这正是"闭环"与"标记完成"的区别；</li>
 *   <li><b>验证不通过要退回到 IN_PROGRESS</b>（不是新开一条）—— 退回来的原因留在时间线上；</li>
 *   <li><b>取消也是正道</b>：确认不做的问题应 CANCELLED（留原因），而不是留着永远不关；
 *       CANCELLED 不计入闭环率分母口径见 Issue 类注释；</li>
 *   <li><b>CLOSED 可重开</b>：问题复现时 REOPEN 回 OPEN（原时间线保留），不另起一张单。</li>
 * </ul>
 *
 * <h3>必填约束（服务端把关）</h3>
 * <ul>
 *   <li>{@link #SUBMIT_VERIFY}：必须给根因分析（rootCause）与处理措施（resolution）——
 *       处理人自己说"改好了"不算数，得先交代"为什么会发生""怎么处理的"；</li>
 *   <li>{@link #VERIFY_PASS}：必须给验证结论（verifyResult）；</li>
 *   <li>{@link #VERIFY_FAIL} / {@link #CANCEL} / {@link #REOPEN}：必须给说明（comment）。</li>
 * </ul>
 */
public final class IssueFlow {

    // ==================== 状态 ====================

    public static final String OPEN = "OPEN";
    public static final String IN_PROGRESS = "IN_PROGRESS";
    public static final String VERIFYING = "VERIFYING";
    public static final String CLOSED = "CLOSED";
    public static final String CANCELLED = "CANCELLED";

    /** 初始状态（新建问题） */
    public static final String INITIAL = OPEN;

    private static final Set<String> STATUSES = new LinkedHashSet<>(java.util.List.of(
            OPEN, IN_PROGRESS, VERIFYING, CLOSED, CANCELLED));

    // ==================== 动作 ====================

    /** 开始处理：OPEN → IN_PROGRESS */
    public static final String START = "START";
    /** 提交验证：IN_PROGRESS → VERIFYING */
    public static final String SUBMIT_VERIFY = "SUBMIT_VERIFY";
    /** 验证通过：VERIFYING → CLOSED（需给验证结论） */
    public static final String VERIFY_PASS = "VERIFY_PASS";
    /** 验证退回：VERIFYING → IN_PROGRESS（需给说明） */
    public static final String VERIFY_FAIL = "VERIFY_FAIL";
    /** 取消：OPEN / IN_PROGRESS / VERIFYING → CANCELLED（需给说明） */
    public static final String CANCEL = "CANCEL";
    /** 重开：CLOSED → OPEN（需给说明） */
    public static final String REOPEN = "REOPEN";

    /** 流转类动作（动作 → 目标状态按"当前状态"查规则表） */
    private static final Map<String, Map<String, String>> RULES = new LinkedHashMap<>();

    /** 动作中文名（与前端 ISSUE_ACTION_LABEL 同一口径，只用于消息文案） */
    private static final Map<String, String> LABELS = new LinkedHashMap<>();

    static {
        LABELS.put("CREATE", "提出");
        LABELS.put("UPDATE", "更新");
        LABELS.put("ASSIGN", "指派");
        LABELS.put(START, "开始处理");
        LABELS.put(SUBMIT_VERIFY, "提交验证");
        LABELS.put(VERIFY_PASS, "验证通过");
        LABELS.put(VERIFY_FAIL, "验证退回");
        LABELS.put(CANCEL, "取消问题");
        LABELS.put(REOPEN, "重开问题");
        LABELS.put("COMMENT", "备注");

        rule(START, OPEN, IN_PROGRESS);
        rule(SUBMIT_VERIFY, IN_PROGRESS, VERIFYING);
        rule(VERIFY_PASS, VERIFYING, CLOSED);
        rule(VERIFY_FAIL, VERIFYING, IN_PROGRESS);
        rule(CANCEL, OPEN, CANCELLED);
        rule(CANCEL, IN_PROGRESS, CANCELLED);
        rule(CANCEL, VERIFYING, CANCELLED);
        rule(REOPEN, CLOSED, OPEN);
    }

    private IssueFlow() {
    }

    private static void rule(String action, String from, String to) {
        RULES.computeIfAbsent(action, k -> new LinkedHashMap<>()).put(from, to);
    }

    // ==================== 查询 ====================

    /** 状态是否合法 */
    public static boolean isValidStatus(String status) {
        return status != null && STATUSES.contains(status);
    }

    /** 动作码是否已知 */
    public static boolean isKnownAction(String action) {
        return action != null && RULES.containsKey(action);
    }

    /** 终态（不可再流转，除 REOPEN） */
    public static boolean isTerminal(String status) {
        return CANCELLED.equals(status);
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

    /** 该动作是否要求填说明（comment） */
    public static boolean requiresComment(String action) {
        return VERIFY_FAIL.equals(action) || CANCEL.equals(action) || REOPEN.equals(action);
    }

    /** 该动作是否要求填验证结论（verifyResult） */
    public static boolean requiresVerifyResult(String action) {
        return VERIFY_PASS.equals(action);
    }

    /** 该动作是否要求填根因分析（rootCause）—— 提交验证必须回答"为什么会发生" */
    public static boolean requiresRootCause(String action) {
        return SUBMIT_VERIFY.equals(action);
    }

    /** 该动作是否要求填处理措施（resolution）—— 提交验证必须交代"怎么处理的" */
    public static boolean requiresResolution(String action) {
        return SUBMIT_VERIFY.equals(action);
    }

    /**
     * 该动作是否只能由<b>责任人</b>（{@code owner}）执行。
     *
     * <p>{@link #START}（开始处理）与 {@link #SUBMIT_VERIFY}（提交验证）都是"处理人自己的活"：
     * 别人代点会让"谁在处理、谁认这个结果"无处落地；验证类（VERIFY_PASS / VERIFY_FAIL）
     * 归提出人，取消 / 重开不限。
     *
     * <p>服务端在 {@code IssueServiceImpl.transition} 用当前登录用户与 {@code owner} 比对，
     * 前端按钮按同一口径渲染 —— 两边必须同步修改。
     */
    public static boolean requiresOwner(String action) {
        return START.equals(action) || SUBMIT_VERIFY.equals(action);
    }

    /** 全部状态（只读，按展示顺序） */
    public static Set<String> statuses() {
        return Collections.unmodifiableSet(STATUSES);
    }

    /** 动作的中文名（面向用户的消息里不要把动作码直接抛出去） */
    public static String label(String action) {
        return LABELS.getOrDefault(action, action);
    }
}
