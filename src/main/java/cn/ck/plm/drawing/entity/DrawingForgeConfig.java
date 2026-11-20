/*
 * Copyright (c) 2026~2028 深圳乘恺科技有限公司
 * All rights reserved.
 *
 * @author Kiween.Hu
 */

package cn.ck.plm.drawing.entity;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;

/** CK-DrawingForge 服务配置，对应 ck_drawing_forge_config（单行表）。 */
public class DrawingForgeConfig {

    /** 单行表的固定主键 —— 整个部署共享一份配置 */
    public static final String SINGLETON_OID = "drawing-forge-config-singleton";

    private String oid;
    private boolean enabled;
    private String baseUrl;
    private String callbackToken;
    private String callbackUrl;
    private String engine;
    private Integer connectTimeoutMs;
    private Integer readTimeoutMs;
    private Long artifactReadTimeoutMs;
    private Integer maxSourceSizeMb;
    private String supportedExtensions;
    private Integer sourceUrlTtlMinutes;
    private Integer maxAttempts;
    private boolean watchdogEnabled;
    private Integer staleAfterMinutes;
    private Integer watchdogBatchSize;
    private Long watchdogIntervalMs;
    private String creator;
    private LocalDateTime createdAt;
    private String updater;
    private LocalDateTime updatedAt;

    /** 视图专用：令牌是否已配置（不回传明文）。不落库。 */
    private boolean callbackTokenSet;

    public List<String> supportedExtensionList() {
        if (supportedExtensions == null || supportedExtensions.isBlank()) {
            return List.of(".dwg", ".dxf");
        }
        return Arrays.stream(supportedExtensions.split(","))
                .map(String::trim).filter(s -> !s.isEmpty()).toList();
    }

    public String getOid() { return oid; }
    public void setOid(String oid) { this.oid = oid; }
    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }
    public String getBaseUrl() { return baseUrl; }
    public void setBaseUrl(String baseUrl) { this.baseUrl = baseUrl; }
    public String getCallbackToken() { return callbackToken; }
    public void setCallbackToken(String callbackToken) { this.callbackToken = callbackToken; }
    public String getCallbackUrl() { return callbackUrl; }
    public void setCallbackUrl(String callbackUrl) { this.callbackUrl = callbackUrl; }
    public String getEngine() { return engine; }
    public void setEngine(String engine) { this.engine = engine; }
    public Integer getConnectTimeoutMs() { return connectTimeoutMs; }
    public void setConnectTimeoutMs(Integer connectTimeoutMs) { this.connectTimeoutMs = connectTimeoutMs; }
    public Integer getReadTimeoutMs() { return readTimeoutMs; }
    public void setReadTimeoutMs(Integer readTimeoutMs) { this.readTimeoutMs = readTimeoutMs; }
    public Long getArtifactReadTimeoutMs() { return artifactReadTimeoutMs; }
    public void setArtifactReadTimeoutMs(Long artifactReadTimeoutMs) { this.artifactReadTimeoutMs = artifactReadTimeoutMs; }
    public Integer getMaxSourceSizeMb() { return maxSourceSizeMb; }
    public void setMaxSourceSizeMb(Integer maxSourceSizeMb) { this.maxSourceSizeMb = maxSourceSizeMb; }
    public String getSupportedExtensions() { return supportedExtensions; }
    public void setSupportedExtensions(String supportedExtensions) { this.supportedExtensions = supportedExtensions; }
    public Integer getSourceUrlTtlMinutes() { return sourceUrlTtlMinutes; }
    public void setSourceUrlTtlMinutes(Integer sourceUrlTtlMinutes) { this.sourceUrlTtlMinutes = sourceUrlTtlMinutes; }
    public Integer getMaxAttempts() { return maxAttempts; }
    public void setMaxAttempts(Integer maxAttempts) { this.maxAttempts = maxAttempts; }
    public boolean isWatchdogEnabled() { return watchdogEnabled; }
    public void setWatchdogEnabled(boolean watchdogEnabled) { this.watchdogEnabled = watchdogEnabled; }
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
    public boolean isCallbackTokenSet() { return callbackTokenSet; }
    public void setCallbackTokenSet(boolean callbackTokenSet) { this.callbackTokenSet = callbackTokenSet; }
}
