/*
 * Copyright (c) 2026~2028 深圳乘恺科技有限公司
 * All rights reserved.
 *
 * @author Kiween.Hu; Roney.Liu
 */

package cn.ck.plm.cad.entity;

import java.time.LocalDateTime;

/**
 * CAD 轻量化可视化记录 —— 一次「STEP 源数模 → Web 可渲染资产」的转换任务。
 *
 * <h3>它管什么</h3>
 * 只管<b>编排与状态</b>，不管文件内容：
 * <ul>
 *   <li>源文件是 {@code ck_eng_document_iteration.ckfile_oid} 指向的那份 STEP；</li>
 *   <li>各类产物（GLB / 结构树 JSON / 缩略图）作为独立的 {@code ck_file} 记录落库，
 *       复用 {@code FileStorageService.FileCategory.CAD} 的 {@code cad/} 目录 ——
 *       这样下载、权限、清理逻辑全部走既有通道，不必为产物再开一套。</li>
 * </ul>
 *
 * <h3>为什么按内容指纹去重</h3>
 * 用户反复上传同一份 STEP（试版本、误操作、网络重试）是常态。
 * 表上有 {@code (eng_doc_iteration_oid, source_sha1)} 唯一索引：
 * 内容没变就不重复烧 CPU —— 转换是这里最贵的资源，没有之一。
 */
public class CadVisualization {

    /** 转换状态：已提交给转换器，等待 worker 拾取 */
    public static final String STATUS_QUEUED = "QUEUED";
    /** 转换状态：worker 正在跑流水线 */
    public static final String STATUS_RUNNING = "RUNNING";
    /** 转换状态：成功，产物已就绪 */
    public static final String STATUS_SUCCESS = "SUCCESS";
    /** 转换状态：失败（可重试） */
    public static final String STATUS_FAILED = "FAILED";
    /**
     * 转换状态：跳过（不重试）。
     * 用于"注定不会成功"的情况：非 STEP 格式、超过大小上限、同一指纹连续失败触发熔断。
     * 与 FAILED 的区别很重要 —— FAILED 可以重试，SKIPPED 不该再碰，否则熔断形同虚设。
     */
    public static final String STATUS_SKIPPED = "SKIPPED";
    /** 转换状态：超时（回调迟迟不来，看门狗判定） */
    public static final String STATUS_TIMEOUT = "TIMEOUT";
    /**
     * 转换状态：用户取消。
     *
     * <p>为什么不复用 SKIPPED：SKIPPED 是"转换器判定这份文件不该转"，重试无意义；
     * CANCELLED 是"人主动叫停"，重新转换是完全合理的动作。两者混在一起，
     * 用户就分不清"我的点击生效了吗"和"系统拒绝了这个文件"。
     */
    public static final String STATUS_CANCELLED = "CANCELLED";

    private String oid;
    private String tenantOid;

    /** 所属工程文档迭代（CAD 数模的载体） */
    private String engDocIterationOid;
    /** 源 STEP 文件（ck_file.oid） */
    private String sourceCkfileOid;
    /** 源文件内容 SHA-1 —— 去重与缓存命中的依据 */
    private String sourceSha1;
    /** 源文件名（冗余存一份，排查问题时不用再 join ck_file） */
    private String sourceFileName;

    /** 转换状态，取值见本类 STATUS_* 常量 */
    private String status;
    /** 转换器侧的任务号 */
    private String forgeJobId;
    /** 进度百分比（0-100，转换器上报） */
    private Integer progress;
    /**
     * 当前阶段（转换器上报的一句话，如「网格化零件 42/118」）。
     * 只有百分比时，卡在 85% 的用户不知道在等什么；成功收口后置空。
     */
    private String stage;

    /** 产物：GLB 主模型（ck_file.oid） */
    private String glbCkfileOid;
    /** 产物：装配结构树 JSON（ck_file.oid） */
    private String structureCkfileOid;
    /** 产物：缩略图（ck_file.oid） */
    private String thumbnailCkfileOid;
    /** 产物：简化 LOD 模型（ck_file.oid，大装配用） */
    private String lodCkfileOid;

    /** 模型单位（mm / inch），glTF 内部已统一为米，这里记源单位备查 */
    private String units;
    /** 三角面数 */
    private Long triangleCount;
    /** 零件数（装配树节点数） */
    private Integer partCount;
    /** 源文件字节数 */
    private Long sourceSize;
    /** GLB 产物字节数（用于统计压缩比） */
    private Long outputSize;
    /** 转换耗时（毫秒） */
    private Long durationMs;
    /** 转换器版本（排查"结果突然变了"时先问它） */
    private String converterVersion;

    /** 错误码 */
    private String errorCode;
    /** 错误详情 */
    private String errorMessage;
    /** 已尝试次数 */
    private Integer attemptCount;

    private LocalDateTime submittedAt;
    private LocalDateTime convertedAt;
    private String creator;
    private LocalDateTime createdAt;
    private String updater;
    private LocalDateTime updatedAt;

    public String getOid() {
        return oid;
    }

    public void setOid(String oid) {
        this.oid = oid;
    }

    public String getTenantOid() {
        return tenantOid;
    }

    public void setTenantOid(String tenantOid) {
        this.tenantOid = tenantOid;
    }

    public String getEngDocIterationOid() {
        return engDocIterationOid;
    }

    public void setEngDocIterationOid(String engDocIterationOid) {
        this.engDocIterationOid = engDocIterationOid;
    }

    public String getSourceCkfileOid() {
        return sourceCkfileOid;
    }

    public void setSourceCkfileOid(String sourceCkfileOid) {
        this.sourceCkfileOid = sourceCkfileOid;
    }

    public String getSourceSha1() {
        return sourceSha1;
    }

    public void setSourceSha1(String sourceSha1) {
        this.sourceSha1 = sourceSha1;
    }

    public String getSourceFileName() {
        return sourceFileName;
    }

    public void setSourceFileName(String sourceFileName) {
        this.sourceFileName = sourceFileName;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getForgeJobId() {
        return forgeJobId;
    }

    public void setForgeJobId(String forgeJobId) {
        this.forgeJobId = forgeJobId;
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

    public String getGlbCkfileOid() {
        return glbCkfileOid;
    }

    public void setGlbCkfileOid(String glbCkfileOid) {
        this.glbCkfileOid = glbCkfileOid;
    }

    public String getStructureCkfileOid() {
        return structureCkfileOid;
    }

    public void setStructureCkfileOid(String structureCkfileOid) {
        this.structureCkfileOid = structureCkfileOid;
    }

    public String getThumbnailCkfileOid() {
        return thumbnailCkfileOid;
    }

    public void setThumbnailCkfileOid(String thumbnailCkfileOid) {
        this.thumbnailCkfileOid = thumbnailCkfileOid;
    }

    public String getLodCkfileOid() {
        return lodCkfileOid;
    }

    public void setLodCkfileOid(String lodCkfileOid) {
        this.lodCkfileOid = lodCkfileOid;
    }

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

    public Long getSourceSize() {
        return sourceSize;
    }

    public void setSourceSize(Long sourceSize) {
        this.sourceSize = sourceSize;
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

    public Integer getAttemptCount() {
        return attemptCount;
    }

    public void setAttemptCount(Integer attemptCount) {
        this.attemptCount = attemptCount;
    }

    public LocalDateTime getSubmittedAt() {
        return submittedAt;
    }

    public void setSubmittedAt(LocalDateTime submittedAt) {
        this.submittedAt = submittedAt;
    }

    public LocalDateTime getConvertedAt() {
        return convertedAt;
    }

    public void setConvertedAt(LocalDateTime convertedAt) {
        this.convertedAt = convertedAt;
    }

    public String getCreator() {
        return creator;
    }

    public void setCreator(String creator) {
        this.creator = creator;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public String getUpdater() {
        return updater;
    }

    public void setUpdater(String updater) {
        this.updater = updater;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }
}
