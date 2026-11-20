/*
 * Copyright (c) 2026~2028 深圳乘恺科技有限公司
 * All rights reserved.
 *
 * @author Kiween.Hu
 */

package cn.ck.plm.drawing.dto;

import java.util.Map;

/**
 * 提交给 CK-DrawingForge 的任务请求体。字段与 ForgeJobRequest 完全一致，
 * 差异仅在 options 的 key（2D 用 targetKinds / generateDxf）。
 *
 * <p>源件定位三字段都发，由转换器按自己的部署形态挑一条能用的（同机直读 /
 * 共享卷相对路径 / 签名 URL），换部署形态时两边都不用改。
 */
public class DrawingForgeJobRequest {

    private String externalId;
    private String sourcePath;
    private String sourceRelativePath;
    private String sourceUrl;
    private String sourceFileName;
    private String sourceSha1;
    private long sourceSize;
    private String callbackUrl;
    private Map<String, Object> options;

    public String getExternalId() { return externalId; }
    public void setExternalId(String externalId) { this.externalId = externalId; }
    public String getSourcePath() { return sourcePath; }
    public void setSourcePath(String sourcePath) { this.sourcePath = sourcePath; }
    public String getSourceRelativePath() { return sourceRelativePath; }
    public void setSourceRelativePath(String sourceRelativePath) { this.sourceRelativePath = sourceRelativePath; }
    public String getSourceUrl() { return sourceUrl; }
    public void setSourceUrl(String sourceUrl) { this.sourceUrl = sourceUrl; }
    public String getSourceFileName() { return sourceFileName; }
    public void setSourceFileName(String sourceFileName) { this.sourceFileName = sourceFileName; }
    public String getSourceSha1() { return sourceSha1; }
    public void setSourceSha1(String sourceSha1) { this.sourceSha1 = sourceSha1; }
    public long getSourceSize() { return sourceSize; }
    public void setSourceSize(long sourceSize) { this.sourceSize = sourceSize; }
    public String getCallbackUrl() { return callbackUrl; }
    public void setCallbackUrl(String callbackUrl) { this.callbackUrl = callbackUrl; }
    public Map<String, Object> getOptions() { return options; }
    public void setOptions(Map<String, Object> options) { this.options = options; }
}
