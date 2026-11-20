/*
 * Copyright (c) 2026~2028 深圳乘恺科技有限公司
 * All rights reserved.
 *
 * @author Kiween.Hu
 */

package cn.ck.plm.drawing.dto;

import java.util.List;

/**
 * CK-DrawingForge 转换完成后的回调体。结构与 ForgeCallbackPayload 对齐，
 * 差异：artifacts 的 kind 为 svg/thumbnail/structure/dxf；Metrics 换成 2D 指标。
 *
 * <p>两条结果回流通道：回调（主，延迟最低）+ 看门狗补偿（备，回调丢了前端也不会永远转圈）。
 */
public class DrawingForgeCallbackPayload {

    private String externalId;
    private String jobId;
    private String status;          // SUCCESS / FAILED / RUNNING
    private Integer progress;
    private String stage;
    private List<ArtifactItem> artifacts;
    private Metrics metrics;
    private String errorCode;
    private String errorMessage;

    public static class ArtifactItem {
        /** svg / thumbnail / structure / dxf */
        private String kind;
        /** 共享卷上的磁盘绝对路径（优先使用） */
        private String path;
        private String fileName;
        private Long size;

        public String getKind() { return kind; }
        public void setKind(String kind) { this.kind = kind; }
        public String getPath() { return path; }
        public void setPath(String path) { this.path = path; }
        public String getFileName() { return fileName; }
        public void setFileName(String fileName) { this.fileName = fileName; }
        public Long getSize() { return size; }
        public void setSize(Long size) { this.size = size; }
    }

    public static class Metrics {
        private String dwgVersion;
        private Integer entityCount;
        private Integer layerCount;
        private List<String> layerNames;
        private List<String> skippedEntityTypes;
        private List<String> fidelityWarnings;
        private List<String> degradedKinds;
        private Long outputSize;
        private Long durationMs;
        private String converterVersion;

        public String getDwgVersion() { return dwgVersion; }
        public void setDwgVersion(String dwgVersion) { this.dwgVersion = dwgVersion; }
        public Integer getEntityCount() { return entityCount; }
        public void setEntityCount(Integer entityCount) { this.entityCount = entityCount; }
        public Integer getLayerCount() { return layerCount; }
        public void setLayerCount(Integer layerCount) { this.layerCount = layerCount; }
        public List<String> getLayerNames() { return layerNames; }
        public void setLayerNames(List<String> layerNames) { this.layerNames = layerNames; }
        public List<String> getSkippedEntityTypes() { return skippedEntityTypes; }
        public void setSkippedEntityTypes(List<String> skippedEntityTypes) { this.skippedEntityTypes = skippedEntityTypes; }
        public List<String> getFidelityWarnings() { return fidelityWarnings; }
        public void setFidelityWarnings(List<String> fidelityWarnings) { this.fidelityWarnings = fidelityWarnings; }
        public List<String> getDegradedKinds() { return degradedKinds; }
        public void setDegradedKinds(List<String> degradedKinds) { this.degradedKinds = degradedKinds; }
        public Long getOutputSize() { return outputSize; }
        public void setOutputSize(Long outputSize) { this.outputSize = outputSize; }
        public Long getDurationMs() { return durationMs; }
        public void setDurationMs(Long durationMs) { this.durationMs = durationMs; }
        public String getConverterVersion() { return converterVersion; }
        public void setConverterVersion(String converterVersion) { this.converterVersion = converterVersion; }
    }

    public String getExternalId() { return externalId; }
    public void setExternalId(String externalId) { this.externalId = externalId; }
    public String getJobId() { return jobId; }
    public void setJobId(String jobId) { this.jobId = jobId; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public Integer getProgress() { return progress; }
    public void setProgress(Integer progress) { this.progress = progress; }
    public String getStage() { return stage; }
    public void setStage(String stage) { this.stage = stage; }
    public List<ArtifactItem> getArtifacts() { return artifacts; }
    public void setArtifacts(List<ArtifactItem> artifacts) { this.artifacts = artifacts; }
    public Metrics getMetrics() { return metrics; }
    public void setMetrics(Metrics metrics) { this.metrics = metrics; }
    public String getErrorCode() { return errorCode; }
    public void setErrorCode(String errorCode) { this.errorCode = errorCode; }
    public String getErrorMessage() { return errorMessage; }
    public void setErrorMessage(String errorMessage) { this.errorMessage = errorMessage; }
}
