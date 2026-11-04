/*
 * Copyright (c) 2026~2028 深圳乘恺科技有限公司
 * All rights reserved.
 *
 * @author Kiween.Hu; Roney.Liu
 */

package cn.ck.plm.cad.dto;

import java.time.LocalDateTime;

/**
 * 「数模转换中心」的一行 —— 一条转换记录 + 它挂在哪份工程数据 / 哪个产品下。
 *
 * <h3>为什么不复用 {@link CadVisualization}</h3>
 * 实体只有转换本身（源文件、状态、进度、产物），而转换中心是<b>跨容器、跨人</b>的队列视图：
 * 用户要知道"这是哪个产品的哪份图纸、谁提交的、前面还排着几个"。
 * 那些信息要 join 工程数据与产品表才能拿到，塞进实体就成了只为某个页面服务的脏字段
 * （与 {@code ContainerCadModelVO} 同一口径：**页面要什么，VO 就给什么**）。
 *
 * <h3>queuePosition 的口径</h3>
 * 排队位次 = 同租户下比它更早提交、且仍在 {@code QUEUED} 的记录数。
 * 为什么不问转换器：那会让"看一眼队列"依赖 CADForge 的版本，
 * 而 {@code submitted_at} 本来就在 CK-PLM 手里 —— 多一次远程调用换不来更准的数。
 */
public class CadConvertJobVO {

    // ---- 转换记录本身 ----

    private String visOid;
    /** QUEUED / RUNNING / SUCCESS / FAILED / SKIPPED / TIMEOUT */
    private String status;
    private Integer progress;
    /** 转换器上报的当前阶段（如「网格化零件 42/118」） */
    private String stage;

    /** 排队位次：前面还有几条 QUEUED；非排队状态为 null */
    private Integer queuePosition;

    private String errorCode;
    private String errorMessage;

    private String sourceFileName;
    private Long sourceSize;

    // ---- 归属：工程数据 / 产品 ----

    /** 工程数据迭代 oid —— 点「三维浏览」跳转用 */
    private String iterationOid;
    private String iterationVersion;
    private String engDocOid;
    private String engDocNumber;
    private String engDocName;

    /** PRODUCT_LINE / PRODUCT_MODEL */
    private String containerType;
    private String containerOid;
    private String containerName;

    // ---- 提交与结果 ----

    /** 提交人（用户名） */
    private String creator;
    private LocalDateTime submittedAt;
    private LocalDateTime convertedAt;
    /** 最后更新时间：判断"进度是否还在动"用 */
    private LocalDateTime updatedAt;
    private Long durationMs;
    private Long triangleCount;
    private Integer partCount;

    public String getVisOid() { return visOid; }
    public void setVisOid(String visOid) { this.visOid = visOid; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public Integer getProgress() { return progress; }
    public void setProgress(Integer progress) { this.progress = progress; }

    public String getStage() { return stage; }
    public void setStage(String stage) { this.stage = stage; }

    public Integer getQueuePosition() { return queuePosition; }
    public void setQueuePosition(Integer queuePosition) { this.queuePosition = queuePosition; }

    public String getErrorCode() { return errorCode; }
    public void setErrorCode(String errorCode) { this.errorCode = errorCode; }

    public String getErrorMessage() { return errorMessage; }
    public void setErrorMessage(String errorMessage) { this.errorMessage = errorMessage; }

    public String getSourceFileName() { return sourceFileName; }
    public void setSourceFileName(String sourceFileName) { this.sourceFileName = sourceFileName; }

    public Long getSourceSize() { return sourceSize; }
    public void setSourceSize(Long sourceSize) { this.sourceSize = sourceSize; }

    public String getIterationOid() { return iterationOid; }
    public void setIterationOid(String iterationOid) { this.iterationOid = iterationOid; }

    public String getIterationVersion() { return iterationVersion; }
    public void setIterationVersion(String iterationVersion) { this.iterationVersion = iterationVersion; }

    public String getEngDocOid() { return engDocOid; }
    public void setEngDocOid(String engDocOid) { this.engDocOid = engDocOid; }

    public String getEngDocNumber() { return engDocNumber; }
    public void setEngDocNumber(String engDocNumber) { this.engDocNumber = engDocNumber; }

    public String getEngDocName() { return engDocName; }
    public void setEngDocName(String engDocName) { this.engDocName = engDocName; }

    public String getContainerType() { return containerType; }
    public void setContainerType(String containerType) { this.containerType = containerType; }

    public String getContainerOid() { return containerOid; }
    public void setContainerOid(String containerOid) { this.containerOid = containerOid; }

    public String getContainerName() { return containerName; }
    public void setContainerName(String containerName) { this.containerName = containerName; }

    public String getCreator() { return creator; }
    public void setCreator(String creator) { this.creator = creator; }

    public LocalDateTime getSubmittedAt() { return submittedAt; }
    public void setSubmittedAt(LocalDateTime submittedAt) { this.submittedAt = submittedAt; }

    public LocalDateTime getConvertedAt() { return convertedAt; }
    public void setConvertedAt(LocalDateTime convertedAt) { this.convertedAt = convertedAt; }

    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }

    public Long getDurationMs() { return durationMs; }
    public void setDurationMs(Long durationMs) { this.durationMs = durationMs; }

    public Long getTriangleCount() { return triangleCount; }
    public void setTriangleCount(Long triangleCount) { this.triangleCount = triangleCount; }

    public Integer getPartCount() { return partCount; }
    public void setPartCount(Integer partCount) { this.partCount = partCount; }
}
