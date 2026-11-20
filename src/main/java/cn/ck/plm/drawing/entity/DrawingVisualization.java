/*
 * Copyright (c) 2026~2028 深圳乘恺科技有限公司
 * All rights reserved.
 *
 * @author Kiween.Hu
 */

package cn.ck.plm.drawing.entity;

import java.time.LocalDateTime;

/**
 * 二维图纸转换编排记录，对应 ck_drawing_visualization。
 *
 * <p>与 CadVisualization 同构：本表只记编排与状态；产物（SVG/缩略图/结构 JSON/DXF）
 * 各自作为独立 ck_file 记录落盘，复用既有下载/权限/清理通道。
 */
public class DrawingVisualization {

    public static final String STATUS_QUEUED = "QUEUED";
    public static final String STATUS_RUNNING = "RUNNING";
    public static final String STATUS_SUCCESS = "SUCCESS";
    public static final String STATUS_FAILED = "FAILED";
    public static final String STATUS_SKIPPED = "SKIPPED";
    public static final String STATUS_TIMEOUT = "TIMEOUT";
    public static final String STATUS_CANCELLED = "CANCELLED";

    private String oid;
    private String tenantOid;
    private String engDocIterationOid;
    private String sourceCkfileOid;
    private String sourceSha1;
    private String sourceFileName;
    private Long sourceSize;
    private String status;
    private Integer progress;
    private String stage;
    private String forgeJobId;

    private String svgCkfileOid;
    private String thumbnailCkfileOid;
    private String structureCkfileOid;
    private String dxfCkfileOid;

    private String dwgVersion;
    private Integer entityCount;
    private Integer layerCount;
    private String skippedEntityTypes;
    private String fidelityWarnings;
    private String degradedKinds;
    private Long outputSize;
    private Long durationMs;
    private String converterVersion;
    private String errorCode;
    private String errorMessage;
    private Integer attemptCount;
    private LocalDateTime submittedAt;
    private LocalDateTime convertedAt;
    private String creator;
    private LocalDateTime createdAt;
    private String updater;
    private LocalDateTime updatedAt;

    public String getOid() { return oid; }
    public void setOid(String oid) { this.oid = oid; }
    public String getTenantOid() { return tenantOid; }
    public void setTenantOid(String tenantOid) { this.tenantOid = tenantOid; }
    public String getEngDocIterationOid() { return engDocIterationOid; }
    public void setEngDocIterationOid(String engDocIterationOid) { this.engDocIterationOid = engDocIterationOid; }
    public String getSourceCkfileOid() { return sourceCkfileOid; }
    public void setSourceCkfileOid(String sourceCkfileOid) { this.sourceCkfileOid = sourceCkfileOid; }
    public String getSourceSha1() { return sourceSha1; }
    public void setSourceSha1(String sourceSha1) { this.sourceSha1 = sourceSha1; }
    public String getSourceFileName() { return sourceFileName; }
    public void setSourceFileName(String sourceFileName) { this.sourceFileName = sourceFileName; }
    public Long getSourceSize() { return sourceSize; }
    public void setSourceSize(Long sourceSize) { this.sourceSize = sourceSize; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public Integer getProgress() { return progress; }
    public void setProgress(Integer progress) { this.progress = progress; }
    public String getStage() { return stage; }
    public void setStage(String stage) { this.stage = stage; }
    public String getForgeJobId() { return forgeJobId; }
    public void setForgeJobId(String forgeJobId) { this.forgeJobId = forgeJobId; }
    public String getSvgCkfileOid() { return svgCkfileOid; }
    public void setSvgCkfileOid(String svgCkfileOid) { this.svgCkfileOid = svgCkfileOid; }
    public String getThumbnailCkfileOid() { return thumbnailCkfileOid; }
    public void setThumbnailCkfileOid(String thumbnailCkfileOid) { this.thumbnailCkfileOid = thumbnailCkfileOid; }
    public String getStructureCkfileOid() { return structureCkfileOid; }
    public void setStructureCkfileOid(String structureCkfileOid) { this.structureCkfileOid = structureCkfileOid; }
    public String getDxfCkfileOid() { return dxfCkfileOid; }
    public void setDxfCkfileOid(String dxfCkfileOid) { this.dxfCkfileOid = dxfCkfileOid; }
    public String getDwgVersion() { return dwgVersion; }
    public void setDwgVersion(String dwgVersion) { this.dwgVersion = dwgVersion; }
    public Integer getEntityCount() { return entityCount; }
    public void setEntityCount(Integer entityCount) { this.entityCount = entityCount; }
    public Integer getLayerCount() { return layerCount; }
    public void setLayerCount(Integer layerCount) { this.layerCount = layerCount; }
    public String getSkippedEntityTypes() { return skippedEntityTypes; }
    public void setSkippedEntityTypes(String skippedEntityTypes) { this.skippedEntityTypes = skippedEntityTypes; }
    public String getFidelityWarnings() { return fidelityWarnings; }
    public void setFidelityWarnings(String fidelityWarnings) { this.fidelityWarnings = fidelityWarnings; }
    public String getDegradedKinds() { return degradedKinds; }
    public void setDegradedKinds(String degradedKinds) { this.degradedKinds = degradedKinds; }
    public Long getOutputSize() { return outputSize; }
    public void setOutputSize(Long outputSize) { this.outputSize = outputSize; }
    public Long getDurationMs() { return durationMs; }
    public void setDurationMs(Long durationMs) { this.durationMs = durationMs; }
    public String getConverterVersion() { return converterVersion; }
    public void setConverterVersion(String converterVersion) { this.converterVersion = converterVersion; }
    public String getErrorCode() { return errorCode; }
    public void setErrorCode(String errorCode) { this.errorCode = errorCode; }
    public String getErrorMessage() { return errorMessage; }
    public void setErrorMessage(String errorMessage) { this.errorMessage = errorMessage; }
    public Integer getAttemptCount() { return attemptCount; }
    public void setAttemptCount(Integer attemptCount) { this.attemptCount = attemptCount; }
    public LocalDateTime getSubmittedAt() { return submittedAt; }
    public void setSubmittedAt(LocalDateTime submittedAt) { this.submittedAt = submittedAt; }
    public LocalDateTime getConvertedAt() { return convertedAt; }
    public void setConvertedAt(LocalDateTime convertedAt) { this.convertedAt = convertedAt; }
    public String getCreator() { return creator; }
    public void setCreator(String creator) { this.creator = creator; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public String getUpdater() { return updater; }
    public void setUpdater(String updater) { this.updater = updater; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
