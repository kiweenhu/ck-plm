/*
 * Copyright (c) 2026~2028 深圳乘恺科技有限公司
 * All rights reserved.
 *
 * @author Kiween.Hu; Roney.Liu
 */

package cn.ck.plm.cad.dto;

import java.util.Map;

/**
 * 提交给转换器的任务请求体。
 *
 * <h3>源件定位：三个字段都发，由转换器按优先级自己挑</h3>
 * <ol>
 *   <li>{@code sourcePath} —— 磁盘绝对路径。转换器<b>与主应用同机</b>、或共享卷且挂载路径一致时直接读；</li>
 *   <li>{@code sourceRelativePath} —— 相对路径，转换器用 {@code CADFORGE_SOURCE_ROOT} 拼。
 *       用于"挂了同一份存储、但<b>两边挂载点不同</b>"；</li>
 *   <li>{@code sourceUrl} —— 带短期签名的下载地址。用于<b>完全没有共享存储</b>的分布式部署。</li>
 * </ol>
 *
 * <p>为什么不在这里判断部署形态、只发一个：判断需要 CK-PLM 知道转换器的挂载情况，
 * 而那是运维信息，不该进业务代码；三个都发，转换器按自己的环境挑一条能用的，
 * 换部署形态时<b>两边都不用改</b>。
 *
 * <p>注意都不传文件内容：几百 MB 的 STEP 塞进 HTTP body 再序列化一遍太浪费，
 * 能读到本地就让转换器自己读，读不到才走第 3 条的网络拉取。
 */
public class ForgeJobRequest {

    /** CK-PLM 侧记录 oid，转换器回调时原样带回，用于路由 */
    private String externalId;
    /** 源文件的磁盘绝对路径（转换器与 CK-PLM 共享文件卷时可读） */
    private String sourcePath;
    /** 源文件相对路径（挂载点不同时，转换器用自己的根目录拼） */
    private String sourceRelativePath;
    /** 带短期签名的源件下载地址（完全无共享存储时，转换器走这条网络拉取） */
    private String sourceUrl;
    private String sourceFileName;
    private String sourceSha1;
    private long sourceSize;
    /** 结果回调地址 */
    private String callbackUrl;

    /** 转换选项：精度、压缩、LOD 等，由调用方按需下发 */
    private Map<String, Object> options;

    public String getExternalId() {
        return externalId;
    }

    public void setExternalId(String externalId) {
        this.externalId = externalId;
    }

    public String getSourcePath() {
        return sourcePath;
    }

    public void setSourcePath(String sourcePath) {
        this.sourcePath = sourcePath;
    }

    public String getSourceRelativePath() {
        return sourceRelativePath;
    }

    public void setSourceRelativePath(String sourceRelativePath) {
        this.sourceRelativePath = sourceRelativePath;
    }

    public String getSourceUrl() {
        return sourceUrl;
    }

    public void setSourceUrl(String sourceUrl) {
        this.sourceUrl = sourceUrl;
    }

    public String getSourceFileName() {
        return sourceFileName;
    }

    public void setSourceFileName(String sourceFileName) {
        this.sourceFileName = sourceFileName;
    }

    public String getSourceSha1() {
        return sourceSha1;
    }

    public void setSourceSha1(String sourceSha1) {
        this.sourceSha1 = sourceSha1;
    }

    public long getSourceSize() {
        return sourceSize;
    }

    public void setSourceSize(long sourceSize) {
        this.sourceSize = sourceSize;
    }

    public String getCallbackUrl() {
        return callbackUrl;
    }

    public void setCallbackUrl(String callbackUrl) {
        this.callbackUrl = callbackUrl;
    }

    public Map<String, Object> getOptions() {
        return options;
    }

    public void setOptions(Map<String, Object> options) {
        this.options = options;
    }
}
