/*
 * Copyright (c) 2026~2028 深圳乘恺科技有限公司
 * All rights reserved.
 *
 * @author Kiween.Hu; Roney.Liu
 */

package cn.ck.plm.cad.entity;

import java.time.LocalDateTime;

/**
 * 转换器（CK-CADForge）对接配置 —— 单行表 {@code ck_cad_forge_config}。
 *
 * <h3>为什么从 yml 挪到库</h3>
 * 这些值原本只写在 {@code application*.yml}（{@code plm.cad.forge.*}）。改转换器地址要
 * 改文件 + 重启服务，而"转换器装到哪台机器、并发压不压得住"是运维现场随时会变的事。
 * 下沉到库以后，页面改完立即生效，重启只用于升级代码。
 *
 * <h3>为什么没有 tenant_oid</h3>
 * 转换器是<b>整个部署共享的一套基础设施</b>，不是某个租户的资产：一套 CK-PLM 只对接
 * 一个转换服务，所有租户的文件都送它转。按租户存会出现"两家企业各配一个地址、
 * 到底听谁的"这种无解状态。
 *
 * <p>因此本表在 {@code TenantStatementInterceptor} 的完全共享表名单里，
 * 且只有平台管理员能改（其余角色只读）。
 */
public class CadForgeConfig {

    /** 单行表的固定主键：不生成 oid，避免"改着改着多出一行" */
    public static final String SINGLETON_OID = "default";

    private String oid;

    /** 是否启用轻量化转换。关掉时触发转换直接 SKIPPED，不打扰没部署转换器的部署环境 */
    private Boolean enabled;

    /** 转换器基地址，如 http://cadforge:8090 */
    private String baseUrl;

    /** 服务间调用令牌：转换器回调 CK-PLM 时带回，防止内网任意程序伪造转换结果 */
    private String callbackToken;

    /** 结果回调地址（转换器要能访问到 CK-PLM，容器部署时填服务名而非 localhost） */
    private String callbackUrl;

    private Integer connectTimeoutMs;
    private Integer readTimeoutMs;

    /** 单次源文件大小上限（MB）—— 库里存 MB，运行期换算成字节 */
    private Integer maxSourceSizeMb;

    /** 支持的扩展名（逗号分隔，小写带点），如 {@code .step,.stp,.iges} */
    private String supportedExtensions;

    /** 同一内容指纹最多尝试几次，超过转 SKIPPED（熔断，避免畸形文件反复烧 CPU） */
    private Integer maxAttempts;

    private Boolean watchdogEnabled;
    private Integer staleAfterMinutes;
    private Integer watchdogBatchSize;
    /** 看门狗扫描间隔（毫秒） */
    private Long watchdogIntervalMs;

    private String creator;
    private LocalDateTime createdAt;
    private String updater;
    private LocalDateTime updatedAt;

    /**
     * 令牌是否已配置 —— <b>不回传给前端</b>的字段（VO 语义）。
     *
     * <p>接口返回时把 {@code callbackToken} 清空、只给出这个布尔值：
     * 页面能显示"已配置/未配置"，但拿不到明文。留空提交表示"不修改"，
     * 这样管理员改超时不必重填令牌。
     */
    private Boolean callbackTokenSet;

    public String getOid() { return oid; }
    public void setOid(String oid) { this.oid = oid; }

    public Boolean getEnabled() { return enabled; }
    public void setEnabled(Boolean enabled) { this.enabled = enabled; }

    public String getBaseUrl() { return baseUrl; }
    public void setBaseUrl(String baseUrl) { this.baseUrl = baseUrl; }

    public String getCallbackToken() { return callbackToken; }
    public void setCallbackToken(String callbackToken) { this.callbackToken = callbackToken; }

    public String getCallbackUrl() { return callbackUrl; }
    public void setCallbackUrl(String callbackUrl) { this.callbackUrl = callbackUrl; }

    public Integer getConnectTimeoutMs() { return connectTimeoutMs; }
    public void setConnectTimeoutMs(Integer connectTimeoutMs) { this.connectTimeoutMs = connectTimeoutMs; }

    public Integer getReadTimeoutMs() { return readTimeoutMs; }
    public void setReadTimeoutMs(Integer readTimeoutMs) { this.readTimeoutMs = readTimeoutMs; }

    public Integer getMaxSourceSizeMb() { return maxSourceSizeMb; }
    public void setMaxSourceSizeMb(Integer maxSourceSizeMb) { this.maxSourceSizeMb = maxSourceSizeMb; }

    public String getSupportedExtensions() { return supportedExtensions; }
    public void setSupportedExtensions(String supportedExtensions) { this.supportedExtensions = supportedExtensions; }

    public Integer getMaxAttempts() { return maxAttempts; }
    public void setMaxAttempts(Integer maxAttempts) { this.maxAttempts = maxAttempts; }

    public Boolean getWatchdogEnabled() { return watchdogEnabled; }
    public void setWatchdogEnabled(Boolean watchdogEnabled) { this.watchdogEnabled = watchdogEnabled; }

    public Integer getStaleAfterMinutes() { return staleAfterMinutes; }
    public void setStaleAfterMinutes(Integer staleAfterMinutes) { this.staleAfterMinutes = staleAfterMinutes; }

    public Integer getWatchdogBatchSize() { return watchdogBatchSize; }
    public void setWatchdogBatchSize(Integer watchdogBatchSize) { this.watchdogBatchSize = watchdogBatchSize; }

    public Long getWatchdogIntervalMs() { return watchdogIntervalMs; }
    public void setWatchdogIntervalMs(Long watchdogIntervalMs) { this.watchdogIntervalMs = watchdogIntervalMs; }

    public String getCreator() { return creator; }
    public void setCreator(String creator) { this.creator = creator; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    public String getUpdater() { return updater; }
    public void setUpdater(String updater) { this.updater = updater; }

    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }

    public Boolean getCallbackTokenSet() { return callbackTokenSet; }
    public void setCallbackTokenSet(Boolean callbackTokenSet) { this.callbackTokenSet = callbackTokenSet; }
}
