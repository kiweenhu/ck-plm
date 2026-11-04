/*
 * Copyright (c) 2026~2028 深圳乘恺科技有限公司
 * All rights reserved.
 *
 * @author Kiween.Hu; Roney.Liu
 */

package cn.ck.plm.process.support;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * 会签（多实例审批）的通过规则 —— BPMN 上的 {@code ckplm:passRule}（编译层由 DSL 的
 * {@code node.passRule} 写入，见 {@code flow-designer/bpmn-compiler/compile.ts}）。
 *
 * <pre>
 * {"mode":"PERCENT","percent":80,"abstain":"IGNORE"}
 * {"mode":"COUNT","count":3}
 * {"mode":"VETO","veto":true}
 * </pre>
 *
 * <h3>为什么运行期要自己算，而不是让 {@code approved} 直接取最后一票</h3>
 * <p>{@code approved} 是<b>实例级</b>变量，会签的每个并行实例完成任务时都会写一次 ——
 * 后一个人的「同意」会把前一个人的「驳回」抹掉，最终路由变成「最后一个人说了算」：
 * 3 人评审里 1 人驳回、2 人同意，只要最后投的是同意，流程照样走"通过 → 发布"。
 *
 * <p>所以运行期按<b>本轮所有票</b>汇总（见 {@code ProcessServiceImpl#tallyCountersign}），
 * 汇总口径与编译层写进 {@code completionCondition} 的口径保持一致：
 * <ul>
 *   <li>{@code PERCENT}：同意票 * 100 >= 应到人数 * 比例（应到人数 = 本轮 {@code nrOfInstances}）；</li>
 *   <li>{@code COUNT}：同意票 >= 必须票数；</li>
 *   <li>{@code VETO} / {@code veto=true}：一票驳回即不通过；</li>
 *   <li>没配规则（{@link Mode#ALL}）：全部同意才通过。</li>
 * </ul>
 */
public final class CountersignRule {

    /** 汇总方式 —— 与前端 dsl-core 的 {@code PassRuleMode} 同值 */
    public enum Mode {
        /** 按通过比例 */
        PERCENT,
        /** 按必须票数 */
        COUNT,
        /** 一票否决 */
        VETO,
        /** 没配规则（或无法识别）：全部同意才通过 */
        ALL,
    }

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private static final int DEFAULT_PERCENT = 100;
    private static final int DEFAULT_COUNT = 1;

    private final Mode mode;
    private final int percent;
    private final int count;
    /** 一票否决开关：{@code mode=PERCENT/COUNT} 上也可以带（DSL 的 {@code passRule.veto}） */
    private final boolean veto;

    private CountersignRule(Mode mode, int percent, int count, boolean veto) {
        this.mode = mode;
        this.percent = percent;
        this.count = count;
        this.veto = veto;
    }

    /** 没配规则时的默认口径：全部同意才通过 */
    public static CountersignRule all() {
        return new CountersignRule(Mode.ALL, DEFAULT_PERCENT, DEFAULT_COUNT, false);
    }

    /**
     * 解析 {@code ckplm:passRule} 的值；空值/坏值返回 {@link #all()}。
     *
     * <p>坏值不抛给办理人：这类脏数据只会来自手工改过的 BPMN，
     * 按"没配规则"（保守：全部同意才通过）处理比让办理失败更合理。
     */
    public static CountersignRule parse(String json) {
        if (json == null || json.trim().isEmpty()) {
            return all();
        }
        try {
            JsonNode node = MAPPER.readTree(json);
            if (node == null || !node.isObject()) {
                return all();
            }
            Mode mode = modeOf(text(node, "mode"));
            int percent = clamp(node.path("percent").asInt(DEFAULT_PERCENT), 1, 100);
            int count = Math.max(node.path("count").asInt(DEFAULT_COUNT), 1);
            boolean veto = node.path("veto").asBoolean(false) || mode == Mode.VETO;
            return new CountersignRule(mode, percent, count, veto);
        } catch (Exception e) {
            return all();
        }
    }

    private static Mode modeOf(String text) {
        if (text == null) {
            return Mode.ALL;
        }
        try {
            return Mode.valueOf(text.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            return Mode.ALL;
        }
    }

    /**
     * 本轮会签的结论。
     *
     * @param approveCount 本轮已投「同意」的票数（含刚刚这一票）
     * @param rejectCount  本轮已投「驳回」的票数（含刚刚这一票）
     * @param total        本轮应到人数（多实例的 {@code nrOfInstances}；未知传 0）
     * @return true ＝ 这一轮会签通过
     */
    public boolean passed(int approveCount, int rejectCount, int total) {
        // 一票否决优先于任何比例/票数口径
        if (veto && rejectCount > 0) {
            return false;
        }
        return switch (mode) {
            case VETO -> approveCount > 0 && rejectCount == 0;
            case PERCENT -> total > 0 && approveCount * 100L >= (long) total * percent;
            case COUNT -> approveCount >= count;
            case ALL -> approveCount > 0 && rejectCount == 0;
        };
    }

    /**
     * 本轮是否已经<b>不可能</b>通过 —— 用于提前结束多实例（否则一人驳回后，
     * 其余评审人还要把注定失败的票办一遍，任务列表里全是"白办"的待办）。
     *
     * <p>判据是"剩下的票全投同意也不够"：会签的通过规则一旦不可能满足，
     * 再多票也改变不了结论，此时中断比等完更诚实。
     *
     * <p>拿不准一律返回 false（例如 {@code total} 未知）：宁可多等一票，也不能把还能通过的会签掐掉。
     */
    public boolean doomed(int approveCount, int rejectCount, int total) {
        if (veto && rejectCount > 0) {
            return true;
        }
        return switch (mode) {
            case VETO, ALL -> rejectCount > 0;
            case PERCENT -> {
                if (total <= 0) {
                    yield false;   // 应到人数未知：算不出"还差多少票"，不猜
                }
                int remaining = Math.max(total - approveCount - rejectCount, 0);
                yield (approveCount + remaining) * 100L < (long) total * percent;
            }
            case COUNT -> {
                if (total <= 0) {
                    yield false;   // 应到人数未知：算不出"还差几票"，不猜
                }
                int remaining = Math.max(total - approveCount - rejectCount, 0);
                yield approveCount + remaining < count;
            }
        };
    }

    public Mode getMode() {
        return mode;
    }

    public boolean isVeto() {
        return veto;
    }

    private static String text(JsonNode node, String field) {
        JsonNode value = node.get(field);
        if (value == null || value.isNull()) {
            return null;
        }
        String text = value.asText();
        return text == null || text.trim().isEmpty() ? null : text.trim();
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }
}
