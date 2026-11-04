/*
 * Copyright (c) 2026~2028 深圳乘恺科技有限公司
 * All rights reserved.
 *
 * @author Kiween.Hu; Roney.Liu
 */

package cn.ck.plm.process.dto;

/**
 * 加签结果 —— 告诉前端这一次落到了哪种语义，两种模式的交互后果完全不同。
 *
 * <ul>
 *   <li>{@link #MODE_COUNTERSIGN}：<b>会签式</b>。任务所在的活动本来就是并行会签，
 *       加签是往多实例里<b>再塞一个实例</b>：原办理人的待办不动，新人也拿到待办，
 *       <b>所有人都办完</b>这个节点才结束；</li>
 *   <li>{@link #MODE_OR_SIGN}：<b>或签式</b>（M3 的老口径）。单实例 / 串行会签无法扩容，
 *       只能追加候选人并释放 assignee：原办理人<b>从待办移到可认领</b>，谁先认领并提交谁算过。</li>
 * </ul>
 *
 * <p>为什么必须在响应里区分：前端如果对会签式加签也提示"任务已转为候选"，
 * 用户会去「可认领」里找一个其实还在「待办」里的任务。
 */
public class ProcessAddSignerVO {

    /** 会签式：追加多实例，必须办完 */
    public static final String MODE_COUNTERSIGN = "COUNTERSIGN";
    /** 或签式：转候选，谁先办谁算过 */
    public static final String MODE_OR_SIGN = "OR_SIGN";

    /** 本次加签的模式（{@link #MODE_COUNTERSIGN} / {@link #MODE_OR_SIGN}） */
    private String mode;

    /**
     * 会签模式下的<b>应到人数</b>（扩容之后，含本轮已办完的票）；或签模式为 0。
     *
     * <p>前端用它把"还需要几个人办"说清楚 —— 会签加签最容易踩的误解是
     * "我加完人，我自己是不是就不用办了"。
     */
    private int participantCount;

    public ProcessAddSignerVO() {
    }

    public ProcessAddSignerVO(String mode, int participantCount) {
        this.mode = mode;
        this.participantCount = participantCount;
    }

    public String getMode() { return mode; }
    public void setMode(String mode) { this.mode = mode; }

    public int getParticipantCount() { return participantCount; }
    public void setParticipantCount(int participantCount) { this.participantCount = participantCount; }
}
