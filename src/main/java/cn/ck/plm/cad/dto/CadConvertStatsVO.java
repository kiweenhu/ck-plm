/*
 * Copyright (c) 2026~2028 深圳乘恺科技有限公司
 * All rights reserved.
 *
 * @author Kiween.Hu; Roney.Liu
 */

package cn.ck.plm.cad.dto;

/**
 * 转换中心的顶部计数（本租户口径）。
 *
 * <h3>为什么 failed 不含 SKIPPED</h3>
 * {@code FAILED / TIMEOUT} 是"这次没转成、可以重试"；{@code SKIPPED} 是"转换器判定
 * 这个文件不该转"（格式不支持、超限、熔断）—— 点一百次重试也不会变。
 * 混在一个数里，用户会对着一个永远不会减少的红色数字反复点重试。
 */
public class CadConvertStatsVO {

    /** 排队中 */
    private Integer queued;
    /** 转换中 */
    private Integer running;
    /** 今日成功（按 converted_at 落在今天） */
    private Integer successToday;
    /** 失败 / 超时（可重试） */
    private Integer failed;
    /** 已跳过（重试无意义） */
    private Integer skipped;
    /** 已取消（用户主动叫停） */
    private Integer cancelled;
    /** 本租户累计记录数 */
    private Integer total;

    public Integer getQueued() { return queued; }
    public void setQueued(Integer queued) { this.queued = queued; }

    public Integer getRunning() { return running; }
    public void setRunning(Integer running) { this.running = running; }

    public Integer getSuccessToday() { return successToday; }
    public void setSuccessToday(Integer successToday) { this.successToday = successToday; }

    public Integer getFailed() { return failed; }
    public void setFailed(Integer failed) { this.failed = failed; }

    public Integer getSkipped() { return skipped; }
    public void setSkipped(Integer skipped) { this.skipped = skipped; }

    public Integer getCancelled() { return cancelled; }
    public void setCancelled(Integer cancelled) { this.cancelled = cancelled; }

    public Integer getTotal() { return total; }
    public void setTotal(Integer total) { this.total = total; }
}
