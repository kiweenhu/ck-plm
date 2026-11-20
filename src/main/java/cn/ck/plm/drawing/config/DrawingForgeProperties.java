/*
 * Copyright (c) 2026~2028 深圳乘恺科技有限公司
 * All rights reserved.
 *
 * @author Kiween.Hu
 */

package cn.ck.plm.drawing.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * CK-DrawingForge 对接配置的运行时快照（prefix=plm.drawing.forge）。
 *
 * <p>权威来源为数据库 ck_drawing_forge_config（单行），由 DrawingForgeConfigService
 * 装载后写进本对象；这里的 @ConfigurationProperties 只作首次启动种子 + 库不可用时兜底。
 * volatile 保证配置刷新线程写入后，提交/回调/看门狗各线程立即可见。
 */
@Component
@ConfigurationProperties(prefix = "plm.drawing.forge")
public class DrawingForgeProperties {

    private volatile boolean enabled = false;
    private volatile String baseUrl = "http://localhost:8091";
    private volatile String callbackToken = "";
    private volatile String callbackUrl = "http://localhost:8082/api/internal/drawing/convert/callback";
    private volatile String engine = "libredwg";
    private volatile int connectTimeoutMs = 3000;
    private volatile int readTimeoutMs = 10000;
    private volatile long artifactReadTimeoutMs = 120_000L;
    private volatile long maxSourceSizeBytes = 50L * 1024 * 1024;
    private volatile List<String> supportedExtensions = List.of(".dwg", ".dxf");
    private volatile int sourceUrlTtlMinutes = 60;
    private volatile int maxAttempts = 3;
    private volatile boolean watchdogEnabled = true;
    private volatile int staleAfterMinutes = 12;
    private volatile int watchdogBatchSize = 50;
    private volatile long watchdogIntervalMs = 120_000L;

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
    public int getConnectTimeoutMs() { return connectTimeoutMs; }
    public void setConnectTimeoutMs(int connectTimeoutMs) { this.connectTimeoutMs = connectTimeoutMs; }
    public int getReadTimeoutMs() { return readTimeoutMs; }
    public void setReadTimeoutMs(int readTimeoutMs) { this.readTimeoutMs = readTimeoutMs; }
    public long getArtifactReadTimeoutMs() { return artifactReadTimeoutMs; }
    public void setArtifactReadTimeoutMs(long artifactReadTimeoutMs) { this.artifactReadTimeoutMs = artifactReadTimeoutMs; }
    public long getMaxSourceSizeBytes() { return maxSourceSizeBytes; }
    public void setMaxSourceSizeBytes(long maxSourceSizeBytes) { this.maxSourceSizeBytes = maxSourceSizeBytes; }
    public List<String> getSupportedExtensions() { return supportedExtensions; }
    public void setSupportedExtensions(List<String> supportedExtensions) { this.supportedExtensions = supportedExtensions; }
    public int getSourceUrlTtlMinutes() { return sourceUrlTtlMinutes; }
    public void setSourceUrlTtlMinutes(int sourceUrlTtlMinutes) { this.sourceUrlTtlMinutes = sourceUrlTtlMinutes; }
    public int getMaxAttempts() { return maxAttempts; }
    public void setMaxAttempts(int maxAttempts) { this.maxAttempts = maxAttempts; }
    public boolean isWatchdogEnabled() { return watchdogEnabled; }
    public void setWatchdogEnabled(boolean watchdogEnabled) { this.watchdogEnabled = watchdogEnabled; }
    public int getStaleAfterMinutes() { return staleAfterMinutes; }
    public void setStaleAfterMinutes(int staleAfterMinutes) { this.staleAfterMinutes = staleAfterMinutes; }
    public int getWatchdogBatchSize() { return watchdogBatchSize; }
    public void setWatchdogBatchSize(int watchdogBatchSize) { this.watchdogBatchSize = watchdogBatchSize; }
    public long getWatchdogIntervalMs() { return watchdogIntervalMs; }
    public void setWatchdogIntervalMs(long watchdogIntervalMs) { this.watchdogIntervalMs = watchdogIntervalMs; }
}
