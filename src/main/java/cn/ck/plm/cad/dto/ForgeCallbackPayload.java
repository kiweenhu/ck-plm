/*
 * Copyright (c) 2026~2028 深圳乘恺科技有限公司
 * All rights reserved.
 *
 * @author Kiween.Hu; Roney.Liu
 */

package cn.ck.plm.cad.dto;

import java.util.List;

/**
 * 转换器完成后的回调体 —— 「等待结果反馈」的落地形态。
 *
 * <h3>两条结果回流通道</h3>
 * <ol>
 *   <li><b>回调（主）</b>：转换完成即打回本接口，延迟最低；</li>
 *   <li><b>看门狗补偿（备）</b>：回调丢了 / 转换器重启了，由定时任务主动拉状态。
 *       只靠回调是不够的 —— 回调丢一次，前端就永远转圈。</li>
 * </ol>
 *
 * <h3>产物怎么交回来</h3>
 * 优先给磁盘路径（与 CK-PLM 共享文件卷，零拷贝）；
 * 路径不可读时 CK-PLM 会退回 HTTP 下载，两种部署形态都能工作。
 */
public class ForgeCallbackPayload {

    /** CK-PLM 侧记录 oid（提交时带过去的 externalId） */
    private String externalId;
    /** 转换器侧任务号 */
    private String jobId;
    /** SUCCESS / FAILED / RUNNING（RUNNING 用于进度上报） */
    private String status;
    /** 进度百分比 0-100 */
    private Integer progress;
    /**
     * 当前阶段（转换器自己的一句话，如「网格化零件 42/118」）。
     * 转换器不认识本系统文案，CK-PLM 原样展示 —— 它只是一个"在干什么"的说明。
     */
    private String stage;

    /** 产物清单 */
    private List<ArtifactItem> artifacts;

    /** 指标 */
    private Metrics metrics;

    /** 失败信息 */
    private String errorCode;
    private String errorMessage;

    public static class ArtifactItem {
        /** glb / structure / thumbnail / lod */
        private String kind;
        /** 共享卷上的磁盘绝对路径（优先使用） */
        private String path;
        /** 产物文件名（登记 CKFile 时用） */
        private String fileName;
        /** 字节数 */
        private Long size;

        public String getKind() {
            return kind;
        }

        public void setKind(String kind) {
            this.kind = kind;
        }

        public String getPath() {
            return path;
        }

        public void setPath(String path) {
            this.path = path;
        }

        public String getFileName() {
            return fileName;
        }

        public void setFileName(String fileName) {
            this.fileName = fileName;
        }

        public Long getSize() {
            return size;
        }

        public void setSize(Long size) {
            this.size = size;
        }
    }

    public static class Metrics {
        private String units;
        private Long triangleCount;
        private Integer partCount;
        private Long outputSize;
        private Long durationMs;
        private String converterVersion;

        public String getUnits() {
            return units;
        }

        public void setUnits(String units) {
            this.units = units;
        }

        public Long getTriangleCount() {
            return triangleCount;
        }

        public void setTriangleCount(Long triangleCount) {
            this.triangleCount = triangleCount;
        }

        public Integer getPartCount() {
            return partCount;
        }

        public void setPartCount(Integer partCount) {
            this.partCount = partCount;
        }

        public Long getOutputSize() {
            return outputSize;
        }

        public void setOutputSize(Long outputSize) {
            this.outputSize = outputSize;
        }

        public Long getDurationMs() {
            return durationMs;
        }

        public void setDurationMs(Long durationMs) {
            this.durationMs = durationMs;
        }

        public String getConverterVersion() {
            return converterVersion;
        }

        public void setConverterVersion(String converterVersion) {
            this.converterVersion = converterVersion;
        }
    }

    public String getExternalId() {
        return externalId;
    }

    public void setExternalId(String externalId) {
        this.externalId = externalId;
    }

    public String getJobId() {
        return jobId;
    }

    public void setJobId(String jobId) {
        this.jobId = jobId;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public Integer getProgress() {
        return progress;
    }

    public void setProgress(Integer progress) {
        this.progress = progress;
    }

    public String getStage() {
        return stage;
    }

    public void setStage(String stage) {
        this.stage = stage;
    }

    public List<ArtifactItem> getArtifacts() {
        return artifacts;
    }

    public void setArtifacts(List<ArtifactItem> artifacts) {
        this.artifacts = artifacts;
    }

    public Metrics getMetrics() {
        return metrics;
    }

    public void setMetrics(Metrics metrics) {
        this.metrics = metrics;
    }

    public String getErrorCode() {
        return errorCode;
    }

    public void setErrorCode(String errorCode) {
        this.errorCode = errorCode;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public void setErrorMessage(String errorMessage) {
        this.errorMessage = errorMessage;
    }
}
