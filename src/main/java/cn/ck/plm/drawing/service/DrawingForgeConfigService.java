/*
 * Copyright (c) 2026~2028 深圳乘恺科技有限公司
 * All rights reserved.
 *
 * @author Kiween.Hu
 */

package cn.ck.plm.drawing.service;

import cn.ck.plm.base.util.TenantContext;
import cn.ck.plm.base.util.UserContext;
import cn.ck.plm.drawing.client.DrawingForgeClient;
import cn.ck.plm.drawing.config.DrawingForgeProperties;
import cn.ck.plm.drawing.entity.DrawingForgeConfig;
import cn.ck.plm.drawing.mapper.DrawingForgeConfigMapper;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 二维转换器对接配置：库里存权威值，运行时刷进 {@link DrawingForgeProperties}。
 * 与 CadForgeConfigService 同构：yml 是首次启动种子 + 兜底，库里有行则以库为准。
 */
@Service
public class DrawingForgeConfigService {

    private static final Logger log = LoggerFactory.getLogger(DrawingForgeConfigService.class);

    private final DrawingForgeConfigMapper mapper;
    private final DrawingForgeProperties props;
    private final DrawingForgeClient forgeClient;

    public DrawingForgeConfigService(DrawingForgeConfigMapper mapper,
                                     DrawingForgeProperties props,
                                     DrawingForgeClient forgeClient) {
        this.mapper = mapper;
        this.props = props;
        this.forgeClient = forgeClient;
    }

    @PostConstruct
    public void init() {
        try {
            DrawingForgeConfig row = mapper.selectOne();
            if (row == null) {
                row = seedFromApplication();
                mapper.insert(row);
                log.info("二维转换器配置为空，已用 application 配置初始化");
            }
            applyToRuntime(row);
            log.info("二维转换器配置已装载: enabled={}, baseUrl={}", props.isEnabled(), props.getBaseUrl());
        } catch (Exception e) {
            log.warn("装载二维转换器配置失败，沿用 application 配置: {}", e.getMessage());
        }
    }

    /** 页面读取：令牌不回传明文，只给是否已配置。 */
    public DrawingForgeConfig getForView() {
        DrawingForgeConfig row = mapper.selectOne();
        if (row == null) row = seedFromApplication();
        boolean hasToken = row.getCallbackToken() != null && !row.getCallbackToken().isBlank();
        row.setCallbackToken(null);
        row.setCallbackTokenSet(hasToken);
        return row;
    }

    /** 保存并立即生效（仅平台管理员可改：整个部署共享一套转换器）。 */
    @Transactional
    public DrawingForgeConfig update(DrawingForgeConfig input) {
        if (!TenantContext.isCurrentPlatform()) {
            throw new IllegalStateException("二维转换器配置由整个部署共享，仅平台管理员可修改");
        }
        DrawingForgeConfig row = mapper.selectOne();
        boolean insert = row == null;
        if (insert) row = seedFromApplication();

        row.setEnabled(Boolean.TRUE.equals(input.isEnabled()));
        row.setBaseUrl(trimToNull(input.getBaseUrl()));
        row.setCallbackUrl(trimToNull(input.getCallbackUrl()));
        row.setEngine(input.getEngine() == null ? "libredwg" : input.getEngine().trim());
        if (input.getCallbackToken() != null && !input.getCallbackToken().isBlank()) {
            row.setCallbackToken(input.getCallbackToken().trim());
        }
        row.setConnectTimeoutMs(input.getConnectTimeoutMs());
        row.setReadTimeoutMs(input.getReadTimeoutMs());
        row.setArtifactReadTimeoutMs(input.getArtifactReadTimeoutMs());
        row.setMaxSourceSizeMb(input.getMaxSourceSizeMb());
        row.setSupportedExtensions(normalizeExtensions(input.getSupportedExtensions()));
        row.setMaxAttempts(input.getMaxAttempts());
        row.setWatchdogEnabled(!Boolean.FALSE.equals(input.isWatchdogEnabled()));
        row.setStaleAfterMinutes(input.getStaleAfterMinutes());
        row.setWatchdogBatchSize(input.getWatchdogBatchSize());
        row.setWatchdogIntervalMs(input.getWatchdogIntervalMs());

        validate(row);
        row.setUpdatedAt(LocalDateTime.now());
        row.setUpdater(UserContext.get());
        if (insert) {
            row.setOid(DrawingForgeConfig.SINGLETON_OID);
            row.setCreator(UserContext.get());
            row.setCreatedAt(LocalDateTime.now());
            mapper.insert(row);
        } else {
            mapper.update(row);
        }
        applyToRuntime(row);
        log.info("二维转换器配置已更新: enabled={}, baseUrl={}, engine={}",
                row.isEnabled(), row.getBaseUrl(), row.getEngine());
        return getForView();
    }

    public Map<String, Object> test() {
        Map<String, Object> result = forgeClient.health();
        result.put("baseUrl", props.getBaseUrl());
        result.put("enabled", props.isEnabled());
        return result;
    }

    // ==================== 内部 ====================

    private DrawingForgeConfig seedFromApplication() {
        DrawingForgeConfig row = new DrawingForgeConfig();
        row.setOid(DrawingForgeConfig.SINGLETON_OID);
        row.setEnabled(props.isEnabled());
        row.setBaseUrl(props.getBaseUrl());
        row.setCallbackToken(props.getCallbackToken());
        row.setCallbackUrl(props.getCallbackUrl());
        row.setEngine(props.getEngine());
        row.setConnectTimeoutMs(props.getConnectTimeoutMs());
        row.setReadTimeoutMs(props.getReadTimeoutMs());
        row.setArtifactReadTimeoutMs(props.getArtifactReadTimeoutMs());
        row.setMaxSourceSizeMb((int) Math.max(1, props.getMaxSourceSizeBytes() / 1024 / 1024));
        row.setSupportedExtensions(props.getSupportedExtensions() == null
                ? "" : String.join(",", props.getSupportedExtensions()));
        row.setSourceUrlTtlMinutes(props.getSourceUrlTtlMinutes());
        row.setMaxAttempts(props.getMaxAttempts());
        row.setWatchdogEnabled(props.isWatchdogEnabled());
        row.setStaleAfterMinutes(props.getStaleAfterMinutes());
        row.setWatchdogBatchSize(props.getWatchdogBatchSize());
        row.setWatchdogIntervalMs(props.getWatchdogIntervalMs());
        row.setCreator("system");
        row.setCreatedAt(LocalDateTime.now());
        row.setUpdater("system");
        row.setUpdatedAt(LocalDateTime.now());
        return row;
    }

    private void applyToRuntime(DrawingForgeConfig row) {
        props.setEnabled(row.isEnabled());
        props.setBaseUrl(row.getBaseUrl());
        props.setCallbackToken(row.getCallbackToken() == null ? "" : row.getCallbackToken());
        props.setCallbackUrl(row.getCallbackUrl());
        props.setEngine(row.getEngine() == null ? "libredwg" : row.getEngine());
        props.setConnectTimeoutMs(intOr(row.getConnectTimeoutMs(), 3000));
        props.setReadTimeoutMs(intOr(row.getReadTimeoutMs(), 10000));
        props.setArtifactReadTimeoutMs(row.getArtifactReadTimeoutMs() == null
                ? 120_000L : row.getArtifactReadTimeoutMs());
        props.setMaxSourceSizeBytes(intOr(row.getMaxSourceSizeMb(), 50) * 1024L * 1024L);
        props.setSupportedExtensions(parseExtensions(row.getSupportedExtensions()));
        props.setMaxAttempts(intOr(row.getMaxAttempts(), 3));
        props.setWatchdogEnabled(!Boolean.FALSE.equals(row.isWatchdogEnabled()));
        props.setStaleAfterMinutes(intOr(row.getStaleAfterMinutes(), 12));
        props.setWatchdogBatchSize(intOr(row.getWatchdogBatchSize(), 50));
        props.setWatchdogIntervalMs(row.getWatchdogIntervalMs() == null
                ? 120_000L : row.getWatchdogIntervalMs());
        forgeClient.invalidate();
    }

    private void validate(DrawingForgeConfig row) {
        if (row.getBaseUrl() == null || row.getBaseUrl().isBlank()) {
            throw new IllegalArgumentException("转换器地址不能为空");
        }
        if (!row.getBaseUrl().startsWith("http://") && !row.getBaseUrl().startsWith("https://")) {
            throw new IllegalArgumentException("转换器地址必须以 http:// 或 https:// 开头");
        }
        requireRange("连接超时", row.getConnectTimeoutMs(), 100, 60_000);
        requireRange("读取超时", row.getReadTimeoutMs(), 100, 300_000);
        requireRange("源文件大小上限", row.getMaxSourceSizeMb(), 1, 4096);
        requireRange("最大尝试次数", row.getMaxAttempts(), 1, 10);
        requireRange("判定超时", row.getStaleAfterMinutes(), 1, 1440);
        requireRange("单轮扫描条数", row.getWatchdogBatchSize(), 1, 500);
    }

    private void requireRange(String label, Integer value, int min, int max) {
        if (value == null) throw new IllegalArgumentException(label + "不能为空");
        if (value < min || value > max) {
            throw new IllegalArgumentException(label + "需在 " + min + " ~ " + max + " 之间");
        }
    }

    private String normalizeExtensions(String raw) {
        if (raw == null || raw.isBlank()) return "";
        return java.util.Arrays.stream(raw.split("[,;\\s]+"))
                .map(String::trim).filter(s -> !s.isEmpty())
                .map(s -> s.toLowerCase().startsWith(".") ? s.toLowerCase() : "." + s.toLowerCase())
                .distinct().reduce((a, b) -> a + "," + b).orElse("");
    }

    private List<String> parseExtensions(String raw) {
        if (raw == null || raw.isBlank()) return List.of();
        return java.util.Arrays.stream(raw.split(","))
                .map(String::trim).filter(s -> !s.isEmpty()).toList();
    }

    private String trimToNull(String s) {
        if (s == null) return null;
        String t = s.trim();
        return t.isEmpty() ? null : t;
    }

    private int intOr(Integer value, int fallback) { return value == null ? fallback : value; }
}
