/*
 * Copyright (c) 2026~2028 深圳乘恺科技有限公司
 * All rights reserved.
 *
 * @author Kiween.Hu; Roney.Liu
 */

package cn.ck.plm.cad.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * CAD 轻量化转换器（CK-CADForge）对接配置的<b>运行时快照</b>。
 *
 * <h3>权威来源已改为数据库</h3>
 * 真实值在 {@code ck_cad_forge_config}（单行），由 {@code CadForgeConfigService}
 * 装载后写进本对象；页面「业务配置 → 轻量化转换」改完立即刷新，见那张表的说明。
 *
 * <p>这里的 {@code @ConfigurationProperties} 只剩两个作用：
 * <ol>
 *   <li><b>首次启动的种子</b> —— 库里没有那一行时，把 yml 的值落库，运维不必手工 insert；</li>
 *   <li><b>兜底</b> —— 库不可用 / 配置表还没建（老库首次升级）时，服务仍按 yml 的值工作。</li>
 * </ol>
 * 一旦库里有行，<b>以库为准</b>：两边都能改，迟早出现"改了不生效、不知道听谁的"。
 *
 * <h3>为什么字段是 volatile</h3>
 * 提交、回调、看门狗在各自线程里读这些值，而刷新发生在保存配置的请求线程。
 * 值本身是不可变类型（String / 基本类型），加 volatile 保证<b>可见性</b>即可 ——
 * 少了它，改完配置后可能几分钟内仍按旧地址发请求（而且日志里看不出任何异常）。
 *
 * <h3>内网部署注意</h3>
 * 转换器通常跑在内网，而 {@code OutboundUrlGuard} 默认会拦内网/回环地址（防 SSRF）。
 * 所以要么把转换器主机加进 {@code plm.integration.allowed-hosts}，
 * 要么设 {@code plm.integration.allow-private-hosts=true} —— 显式放行，不要靠"恰好没拦住"。
 */
@Component
@ConfigurationProperties(prefix = "plm.cad.forge")
public class CadForgeProperties {

    /** 是否启用 CAD 可视化。关掉时触发转换直接返回 SKIPPED，不打扰没部署转换器的用户 */
    private volatile boolean enabled = false;

    /** 转换器基地址，如 http://cadforge:8090 */
    private volatile String baseUrl = "http://localhost:8090";

    /** 服务间调用令牌：转换器回调 CK-PLM 时需要带回，防止内网任意程序伪造转换结果 */
    private volatile String callbackToken = "";

    /** 结果回调地址（转换器要能访问到 CK-PLM，容器部署时填服务名而非 localhost） */
    private volatile String callbackUrl = "http://localhost:8082/api/internal/cad/convert/callback";

    /**
     * 源件下载地址的有效期（分钟）。
     *
     * <p>没有共享存储时，转换器要靠 {@code sourceUrl} 回拉源件。这个地址会落进转换器的
     * 任务文件与日志里，长期有效等于发了一张永久通行证 —— 所以签名里带过期时间。
     * 取得太短会让慢任务（排队 + 几百 MB 下载）取不到文件，
     * 默认 60 分钟对"提交后排队再转换"的节奏够用。
     */
    private volatile int sourceUrlTtlMinutes = 60;

    /** 连接超时（毫秒） */
    private volatile int connectTimeoutMs = 3000;
    /** 读取超时（毫秒）—— 提交任务本身应当很快，慢说明转换器有问题 */
    private volatile int readTimeoutMs = 10000;

    /**
     * 产物下载的读取超时（毫秒）—— 与提交任务分开，且必须更长。
     *
     * <p>{@code readTimeoutMs} 那 10 秒是给"提交/查状态"用的：一次调用该在毫秒级返回。
     * 产物下载是另一回事 —— 几百 MB 的 GLB 在慢网络上，两次 read 之间轻易超过 10 秒，
     * 于是下载会在中途被掐断，而症状只是"产物为空，跳过登记"，极难联想到超时。
     * 没有共享卷时<b>每次回传都走它</b>，所以单独给一个宽松的超时。
     */
    private volatile int artifactReadTimeoutMs = 120_000;

    /** 单次源文件大小上限（字节），超过直接 SKIPPED，不送进流水线 */
    private volatile long maxSourceSizeBytes = 200L * 1024 * 1024;

    /** 支持的扩展名（小写，含点）。空表示不限制 */
    private volatile List<String> supportedExtensions = List.of(".step", ".stp", ".iges", ".igs", ".stl");

    /** 同一内容指纹最多重试几次；超过则转 SKIPPED（熔断，避免畸形文件反复烧 CPU） */
    private volatile int maxAttempts = 3;

    /** 看门狗：是否扫描卡住的任务 */
    private volatile boolean watchdogEnabled = true;
    /** 看门狗：提交后超过多少分钟仍无结果，视为超时 */
    private volatile int staleAfterMinutes = 15;
    /** 看门狗：单轮最多检查多少条 */
    private volatile int watchdogBatchSize = 50;
    /** 看门狗：扫描间隔（毫秒）—— 在页面上可改，见 CadConvertServiceImpl 的节拍说明 */
    private volatile long watchdogIntervalMs = 120_000L;

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getBaseUrl() {
        return baseUrl;
    }

    public void setBaseUrl(String baseUrl) {
        this.baseUrl = baseUrl;
    }

    public String getCallbackToken() {
        return callbackToken;
    }

    public void setCallbackToken(String callbackToken) {
        this.callbackToken = callbackToken;
    }

    public String getCallbackUrl() {
        return callbackUrl;
    }

    public void setCallbackUrl(String callbackUrl) {
        this.callbackUrl = callbackUrl;
    }

    public int getSourceUrlTtlMinutes() {
        return sourceUrlTtlMinutes;
    }

    public void setSourceUrlTtlMinutes(int sourceUrlTtlMinutes) {
        this.sourceUrlTtlMinutes = sourceUrlTtlMinutes;
    }

    public int getConnectTimeoutMs() {
        return connectTimeoutMs;
    }

    public void setConnectTimeoutMs(int connectTimeoutMs) {
        this.connectTimeoutMs = connectTimeoutMs;
    }

    public int getReadTimeoutMs() {
        return readTimeoutMs;
    }

    public void setReadTimeoutMs(int readTimeoutMs) {
        this.readTimeoutMs = readTimeoutMs;
    }

    public int getArtifactReadTimeoutMs() {
        return artifactReadTimeoutMs;
    }

    public void setArtifactReadTimeoutMs(int artifactReadTimeoutMs) {
        this.artifactReadTimeoutMs = artifactReadTimeoutMs;
    }

    public long getMaxSourceSizeBytes() {
        return maxSourceSizeBytes;
    }

    public void setMaxSourceSizeBytes(long maxSourceSizeBytes) {
        this.maxSourceSizeBytes = maxSourceSizeBytes;
    }

    public List<String> getSupportedExtensions() {
        return supportedExtensions;
    }

    public void setSupportedExtensions(List<String> supportedExtensions) {
        this.supportedExtensions = supportedExtensions;
    }

    public int getMaxAttempts() {
        return maxAttempts;
    }

    public void setMaxAttempts(int maxAttempts) {
        this.maxAttempts = maxAttempts;
    }

    public boolean isWatchdogEnabled() {
        return watchdogEnabled;
    }

    public void setWatchdogEnabled(boolean watchdogEnabled) {
        this.watchdogEnabled = watchdogEnabled;
    }

    public int getStaleAfterMinutes() {
        return staleAfterMinutes;
    }

    public void setStaleAfterMinutes(int staleAfterMinutes) {
        this.staleAfterMinutes = staleAfterMinutes;
    }

    public int getWatchdogBatchSize() {
        return watchdogBatchSize;
    }

    public void setWatchdogBatchSize(int watchdogBatchSize) {
        this.watchdogBatchSize = watchdogBatchSize;
    }

    public long getWatchdogIntervalMs() {
        return watchdogIntervalMs;
    }

    public void setWatchdogIntervalMs(long watchdogIntervalMs) {
        this.watchdogIntervalMs = watchdogIntervalMs;
    }
}
