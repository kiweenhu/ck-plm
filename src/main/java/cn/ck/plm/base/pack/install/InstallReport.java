/*
 * Copyright (c) 2026~2028 深圳乘恺科技有限公司
 * All rights reserved.
 *
 * @author Kiween.Hu; Roney.Liu
 */

package cn.ck.plm.base.pack.install;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 安装报告 —— 一次安装（或一次预演）的总账。
 *
 * <p>它同时是三种场景的返回值：
 * <ol>
 *   <li><b>平台启动自动装</b>：日志里打印一行汇总；</li>
 *   <li><b>引导页预演（dry-run）</b>：前端拿 {@code summary} 渲染"你将得到 6 阶段 / 46 交付物…"；</li>
 *   <li><b>引导页执行</b>：前端按 {@code summary} 的顺序把结果逐条"播放"成步骤条。</li>
 * </ol>
 *
 * <p>{@code summary} 用 {@code resourceType} 分组并保持声明顺序 —— 顺序稳定，界面才不会跳。
 */
public class InstallReport {

    /** 包坐标 code@version */
    private String packId;
    /** 包名（界面展示） */
    private String packName;
    /** 安装目标租户 */
    private String tenantOid;
    /** 是否预演 */
    private boolean dryRun;
    /** 父包（先装的）：code@version */
    private String basePackId;
    /** 安装状态：INSTALLED / PARTIAL / FAILED / SKIPPED（已装过且目标一致） */
    private String installState;
    /** 内容指纹 */
    private String contentHash;
    /** 逐条明细 */
    private List<InstallResult> results = new ArrayList<>();
    /** 分组汇总：resourceType → [insert, skip, update, failed] */
    private Map<String, int[]> summary = new LinkedHashMap<>();
    /** 整个包级的问题（如 engineRange 不满足、父包缺失），非空表示没装成 */
    private List<String> errors = new ArrayList<>();
    /** 平台版本（用于界面展示"包适用 >=0.2.0 <1.0.0，当前 0.3.0"） */
    private String platformVersion;

    // ==================== 便捷方法 ====================

    public void add(InstallResult result) {
        results.add(result);
        int[] counts = summary.computeIfAbsent(result.getResourceType(), k -> new int[4]);
        switch (result.getAction()) {
            case INSERT: counts[0]++; break;
            case SKIP:   counts[1]++; break;
            case UPDATE: counts[2]++; break;
            case FAILED: counts[3]++; break;
            default: break;
        }
    }

    public void addAll(List<InstallResult> list) {
        if (list != null) {
            list.forEach(this::add);
        }
    }

    public void error(String message) {
        errors.add(message);
    }

    /** 是否全部成功（没有包级错误，也没有失败条目） */
    public boolean isSuccess() {
        return errors.isEmpty() && InstallResult.failures(results).isEmpty();
    }

    /** 一行摘要，日志与界面都能用 */
    public String toSummaryLine() {
        StringBuilder sb = new StringBuilder();
        sb.append(packId).append(dryRun ? "（预演）" : "").append(" → ").append(installState);
        for (Map.Entry<String, int[]> entry : summary.entrySet()) {
            int[] c = entry.getValue();
            sb.append(" | ").append(entry.getKey())
              .append(" 新增").append(c[0])
              .append(" 跳过").append(c[1]);
            if (c[2] > 0) {
                sb.append(" 更新").append(c[2]);
            }
            if (c[3] > 0) {
                sb.append(" 失败").append(c[3]);
            }
        }
        if (!errors.isEmpty()) {
            sb.append(" | 错误: ").append(String.join("; ", errors));
        }
        return sb.toString();
    }

    // ==================== Getter / Setter ====================

    public String getPackId() { return packId; }
    public void setPackId(String packId) { this.packId = packId; }

    public String getPackName() { return packName; }
    public void setPackName(String packName) { this.packName = packName; }

    public String getTenantOid() { return tenantOid; }
    public void setTenantOid(String tenantOid) { this.tenantOid = tenantOid; }

    public boolean isDryRun() { return dryRun; }
    public void setDryRun(boolean dryRun) { this.dryRun = dryRun; }

    public String getBasePackId() { return basePackId; }
    public void setBasePackId(String basePackId) { this.basePackId = basePackId; }

    public String getInstallState() { return installState; }
    public void setInstallState(String installState) { this.installState = installState; }

    public String getContentHash() { return contentHash; }
    public void setContentHash(String contentHash) { this.contentHash = contentHash; }

    public List<InstallResult> getResults() { return results; }
    public void setResults(List<InstallResult> results) {
        this.results = results == null ? new ArrayList<>() : results;
    }

    public Map<String, int[]> getSummary() { return summary; }
    public void setSummary(Map<String, int[]> summary) {
        this.summary = summary == null ? new LinkedHashMap<>() : summary;
    }

    public List<String> getErrors() { return errors; }
    public void setErrors(List<String> errors) {
        this.errors = errors == null ? new ArrayList<>() : errors;
    }

    public String getPlatformVersion() { return platformVersion; }
    public void setPlatformVersion(String platformVersion) { this.platformVersion = platformVersion; }
}
