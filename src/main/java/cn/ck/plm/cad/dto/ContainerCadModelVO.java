/*
 * Copyright (c) 2026~2028 深圳乘恺科技有限公司
 * All rights reserved.
 *
 * @author Kiween.Hu; Roney.Liu
 */

package cn.ck.plm.cad.dto;

import java.time.LocalDateTime;

/**
 * 「产品数模」一行：某容器（产品系列 / 型号）下配套的一份三维数模文件。
 *
 * <p>数模的载体是<b>工程数据（ENG_DOCUMENT）</b>，不是通用文档（DOCUMENT）：
 * 一行 = 一个 {@code ck_eng_document} 最新迭代上的一个 CAD 主文件
 * （{@code ck_eng_document_iteration.ckfile_oid}，后缀 .step/.stp/.iges/.igs/.stl）
 * + 它最近一次的轻量化转换结果。EPMDocument 才具备 CAD 专有属性
 * （cadName / cadType / cadTool），通用文档没有。
 *
 * <p>{@code status} 为空表示<b>还没转换过</b>（不是失败）。前端据此显示"未转换"并给出生成入口，
 * 与 {@code SKIPPED}（转换器判定不支持的格式，重试也没用）必须区分开。
 */
public class ContainerCadModelVO {

    // ---- 工程数据（数模的载体）----

    private String engDocOid;

    /** 工程数据编码 */
    private String engDocNumber;

    /** 工程数据名称 */
    private String engDocName;

    /** 归属容器类型：PRODUCT_LINE（系列）/ PRODUCT_MODEL（型号） */
    private String containerType;

    /** 归属容器 oid —— 系列页看到的是系列，型号页可能是型号本身或父系列 */
    private String containerOid;

    /** 最新迭代 oid —— 触发转换时的必需参数（就是 {@code engDocIterationOid}） */
    private String iterationOid;

    /** 最新迭代的显示版本（如 A.1） */
    private String iterationVersion;

    /** 最新迭代的生命周期状态 code */
    private String iterationStatus;

    /**
     * 最新迭代是否已被检出。
     *
     * <p>数模的检出状态挂在<b>工程数据迭代</b>上（{@code ck_eng_document_iteration.checked_out}），
     * 与物料清单里零件迭代的检出是同一套语义：检出 = 独占编辑锁，
     * 别人既改不了也检入不了。列表里必须能看到，否则用户会去点一个注定失败的编辑。
     */
    private Boolean checkedOut;

    /** 检出人（账号） */
    private String checkedOutBy;

    /** 检出时填的备注 */
    private String checkedOutComment;

    // ---- CAD 专有属性（仅 EPMDocument 有）----

    /** CAD 名称（数模在 CAD 工具里的名称，可与 PLM 编号不同） */
    private String cadName;

    /** CAD 类型（如 prt / asm），仅作展示 */
    private String cadType;

    /** CAD 工具名（如 Creo / NX / SolidWorks），仅作展示 */
    private String cadTool;

    // ---- 源文件 ----

    /** 源文件 oid（ck_file.oid）—— 触发转换时的必需参数 */
    private String sourceCkfileOid;

    /** 源文件名（含扩展名，前端据此显示 STEP / IGES / STL 标签） */
    private String fileName;

    /** 源文件字节数 */
    private Long fileSize;

    // ---- 转换记录（最近一次）----

    /** 转换记录 oid；为空表示还没转换过 */
    private String cadVisOid;

    /** QUEUED / RUNNING / SUCCESS / FAILED / SKIPPED / TIMEOUT；为空表示未转换 */
    private String status;

    /** 转换进度 0-100 */
    private Integer progress;

    /** 当前阶段（转换器上报，如「网格化零件 42/118」）；成功/未转换时为空 */
    private String stage;

    /** 失败原因（FAILED / SKIPPED / TIMEOUT 时有值） */
    private String errorMessage;

    /** 轻量化产物：GLB 模型文件 oid，走 /api/ckfiles/{oid}/stream 取 */
    private String glbCkfileOid;

    /** 轻量化产物：装配结构树 JSON 的 oid */
    private String structureCkfileOid;

    /** 轻量化产物：缩略图 oid */
    private String thumbnailCkfileOid;

    /** 轻量化产物：LOD 文件的 oid */
    private String lodCkfileOid;

    /** 三角面片数（成功后有值，用于列表里直观比较模型复杂度） */
    private Long triangleCount;

    /** 零件/子件数（装配体才有意义） */
    private Integer partCount;

    /** 模型单位（mm / m 等） */
    private String units;

    /** 转换完成时间 */
    private LocalDateTime convertedAt;

    /** 转换记录最后更新时间 —— 轮询时可按它判断状态是否有推进 */
    private LocalDateTime statusUpdatedAt;

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

    public String getIterationOid() { return iterationOid; }
    public void setIterationOid(String iterationOid) { this.iterationOid = iterationOid; }

    public String getIterationVersion() { return iterationVersion; }
    public void setIterationVersion(String iterationVersion) { this.iterationVersion = iterationVersion; }

    public String getIterationStatus() { return iterationStatus; }
    public void setIterationStatus(String iterationStatus) { this.iterationStatus = iterationStatus; }

    public Boolean getCheckedOut() { return checkedOut; }
    public void setCheckedOut(Boolean checkedOut) { this.checkedOut = checkedOut; }

    public String getCheckedOutBy() { return checkedOutBy; }
    public void setCheckedOutBy(String checkedOutBy) { this.checkedOutBy = checkedOutBy; }

    public String getCheckedOutComment() { return checkedOutComment; }
    public void setCheckedOutComment(String checkedOutComment) { this.checkedOutComment = checkedOutComment; }

    public String getCadName() { return cadName; }
    public void setCadName(String cadName) { this.cadName = cadName; }

    public String getCadType() { return cadType; }
    public void setCadType(String cadType) { this.cadType = cadType; }

    public String getCadTool() { return cadTool; }
    public void setCadTool(String cadTool) { this.cadTool = cadTool; }

    public String getSourceCkfileOid() { return sourceCkfileOid; }
    public void setSourceCkfileOid(String sourceCkfileOid) { this.sourceCkfileOid = sourceCkfileOid; }

    public String getFileName() { return fileName; }
    public void setFileName(String fileName) { this.fileName = fileName; }

    public Long getFileSize() { return fileSize; }
    public void setFileSize(Long fileSize) { this.fileSize = fileSize; }

    public String getCadVisOid() { return cadVisOid; }
    public void setCadVisOid(String cadVisOid) { this.cadVisOid = cadVisOid; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public Integer getProgress() { return progress; }
    public void setProgress(Integer progress) { this.progress = progress; }

    public String getStage() { return stage; }
    public void setStage(String stage) { this.stage = stage; }

    public String getErrorMessage() { return errorMessage; }
    public void setErrorMessage(String errorMessage) { this.errorMessage = errorMessage; }

    public String getGlbCkfileOid() { return glbCkfileOid; }
    public void setGlbCkfileOid(String glbCkfileOid) { this.glbCkfileOid = glbCkfileOid; }

    public String getStructureCkfileOid() { return structureCkfileOid; }
    public void setStructureCkfileOid(String structureCkfileOid) { this.structureCkfileOid = structureCkfileOid; }

    public String getThumbnailCkfileOid() { return thumbnailCkfileOid; }
    public void setThumbnailCkfileOid(String thumbnailCkfileOid) { this.thumbnailCkfileOid = thumbnailCkfileOid; }

    public String getLodCkfileOid() { return lodCkfileOid; }
    public void setLodCkfileOid(String lodCkfileOid) { this.lodCkfileOid = lodCkfileOid; }

    public Long getTriangleCount() { return triangleCount; }
    public void setTriangleCount(Long triangleCount) { this.triangleCount = triangleCount; }

    public Integer getPartCount() { return partCount; }
    public void setPartCount(Integer partCount) { this.partCount = partCount; }

    public String getUnits() { return units; }
    public void setUnits(String units) { this.units = units; }

    public LocalDateTime getConvertedAt() { return convertedAt; }
    public void setConvertedAt(LocalDateTime convertedAt) { this.convertedAt = convertedAt; }

    public LocalDateTime getStatusUpdatedAt() { return statusUpdatedAt; }
    public void setStatusUpdatedAt(LocalDateTime statusUpdatedAt) { this.statusUpdatedAt = statusUpdatedAt; }
}
