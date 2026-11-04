/*
 * Copyright (c) 2026~2028 深圳乘恺科技有限公司
 * All rights reserved.
 *
 * @author Kiween.Hu; Roney.Liu
 */

package cn.ck.plm.cad.service;

import cn.ck.plm.base.util.TenantContext;
import cn.ck.plm.base.util.UserContext;
import cn.ck.plm.cad.client.CadForgeClient;
import cn.ck.plm.cad.config.CadForgeProperties;
import cn.ck.plm.cad.entity.CadForgeConfig;
import cn.ck.plm.cad.mapper.CadForgeConfigMapper;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 转换器对接配置：库里存一份权威值，运行时刷进 {@link CadForgeProperties}。
 *
 * <h3>为什么还要留着 yml</h3>
 * yml 是<b>首次启动的种子 + 兜底</b>：第一次跑（库里没有那一行）时把 application 里的值
 * 落库，运维不用手工 insert；万一库不可用，服务照样能用 yml 的值起来，只是页面改不动。
 * 但一旦库里有行，<b>以库为准</b> —— 两边都能改才是灾难。
 *
 * <h3>为什么要"刷"而不是"每次查库"</h3>
 * 转换链路上每次回调、每次提交、看门狗每一拍都要读这些值。每次都查库既慢又多一次故障点；
 * 配置是低频变更，改成"改的时候刷一次内存"，读路径就是纯字段访问。
 */
@Service
public class CadForgeConfigService {

    private static final Logger log = LoggerFactory.getLogger(CadForgeConfigService.class);

    private final CadForgeConfigMapper mapper;
    private final CadForgeProperties props;
    private final CadForgeClient forgeClient;

    public CadForgeConfigService(CadForgeConfigMapper mapper,
                                 CadForgeProperties props,
                                 CadForgeClient forgeClient) {
        this.mapper = mapper;
        this.props = props;
        this.forgeClient = forgeClient;
    }

    /**
     * 启动时装载：库里有 → 刷进运行时；库里没有 → 把 yml 的值播种进去。
     *
     * <p>失败只告警不抛：配置表可能还没建（老库首次升级），
     * 这时应该"用 yml 正常跑"，而不是"整个服务起不来"。
     */
    @PostConstruct
    public void init() {
        try {
            CadForgeConfig row = mapper.selectOne();
            if (row == null) {
                row = seedFromApplication();
                mapper.insert(row);
                log.info("转换器配置为空，已用 application 配置初始化（ck_cad_forge_config.oid={}）",
                        CadForgeConfig.SINGLETON_OID);
            }
            applyToRuntime(row);
            log.info("转换器配置已装载: enabled={}, baseUrl={}", props.isEnabled(), props.getBaseUrl());
        } catch (Exception e) {
            log.warn("装载转换器配置失败，本次沿用 application 配置: {}", e.getMessage());
        }
    }

    /**
     * 页面读取。
     *
     * <p>令牌<b>不回传明文</b>，只给"是否已配置"。管理员改超时时不必重填令牌，
     * 也不至于让一个共享密钥在浏览器里裸奔。
     */
    public CadForgeConfig getForView() {
        CadForgeConfig row = mapper.selectOne();
        if (row == null) {
            row = seedFromApplication();
        }
        boolean hasToken = row.getCallbackToken() != null && !row.getCallbackToken().isBlank();
        row.setCallbackToken(null);
        row.setCallbackTokenSet(hasToken);
        return row;
    }

    /**
     * 保存并立即生效。
     *
     * <p>只有平台管理员能改：转换器是整个部署共享的一套基础设施，
     * 让某个租户管理员改地址等于让他影响别人的转换。
     */
    @Transactional
    public CadForgeConfig update(CadForgeConfig input) {
        if (!TenantContext.isCurrentPlatform()) {
            throw new IllegalStateException("转换器配置由整个部署共享，仅平台管理员可修改");
        }

        CadForgeConfig row = mapper.selectOne();
        boolean insert = row == null;
        if (insert) {
            row = seedFromApplication();
        }

        row.setEnabled(Boolean.TRUE.equals(input.getEnabled()));
        row.setBaseUrl(trimToNull(input.getBaseUrl()));
        row.setCallbackUrl(trimToNull(input.getCallbackUrl()));
        // 留空 = 不修改：页面拿不到明文，也就没法"原样提交回来"
        if (input.getCallbackToken() != null && !input.getCallbackToken().isBlank()) {
            row.setCallbackToken(input.getCallbackToken().trim());
        }
        row.setConnectTimeoutMs(input.getConnectTimeoutMs());
        row.setReadTimeoutMs(input.getReadTimeoutMs());
        row.setMaxSourceSizeMb(input.getMaxSourceSizeMb());
        row.setSupportedExtensions(normalizeExtensions(input.getSupportedExtensions()));
        row.setMaxAttempts(input.getMaxAttempts());
        row.setWatchdogEnabled(Boolean.TRUE.equals(input.getWatchdogEnabled()));
        row.setStaleAfterMinutes(input.getStaleAfterMinutes());
        row.setWatchdogBatchSize(input.getWatchdogBatchSize());
        row.setWatchdogIntervalMs(input.getWatchdogIntervalMs());

        validate(row);

        row.setUpdatedAt(LocalDateTime.now());
        row.setUpdater(UserContext.get());
        if (insert) {
            row.setOid(CadForgeConfig.SINGLETON_OID);
            row.setCreator(UserContext.get());
            row.setCreatedAt(LocalDateTime.now());
            mapper.insert(row);
        } else {
            mapper.update(row);
        }
        applyToRuntime(row);
        log.info("转换器配置已更新: enabled={}, baseUrl={}, 操作人={}",
                row.getEnabled(), row.getBaseUrl(), row.getUpdater());
        return getForView();
    }

    /** 连通性探测（页面「测试连接」）—— 用<b>已生效</b>的配置去探，不是用表单里的草稿 */
    public Map<String, Object> test() {
        Map<String, Object> result = forgeClient.health();
        result.put("baseUrl", props.getBaseUrl());
        result.put("enabled", props.isEnabled());
        return result;
    }

    // ==================== 内部 ====================

    /** 用 application 配置构造首行（也用于"库不可用时的回显"） */
    private CadForgeConfig seedFromApplication() {
        CadForgeConfig row = new CadForgeConfig();
        row.setOid(CadForgeConfig.SINGLETON_OID);
        row.setEnabled(props.isEnabled());
        row.setBaseUrl(props.getBaseUrl());
        row.setCallbackToken(props.getCallbackToken());
        row.setCallbackUrl(props.getCallbackUrl());
        row.setConnectTimeoutMs(props.getConnectTimeoutMs());
        row.setReadTimeoutMs(props.getReadTimeoutMs());
        // 字节 → MB（库里的单位是 MB：管理员填 200 比填 209715200 不容易错）
        row.setMaxSourceSizeMb((int) Math.max(1, props.getMaxSourceSizeBytes() / 1024 / 1024));
        row.setSupportedExtensions(props.getSupportedExtensions() == null
                ? "" : String.join(",", props.getSupportedExtensions()));
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

    /** 刷进运行时配置 —— 读路径（提交/回调/看门狗）全靠这里，之后不再查库 */
    private void applyToRuntime(CadForgeConfig row) {
        props.setEnabled(Boolean.TRUE.equals(row.getEnabled()));
        props.setBaseUrl(row.getBaseUrl());
        props.setCallbackToken(row.getCallbackToken() == null ? "" : row.getCallbackToken());
        props.setCallbackUrl(row.getCallbackUrl());
        props.setConnectTimeoutMs(intOr(row.getConnectTimeoutMs(), 3000));
        props.setReadTimeoutMs(intOr(row.getReadTimeoutMs(), 10000));
        int sizeMb = intOr(row.getMaxSourceSizeMb(), 200);
        props.setMaxSourceSizeBytes(sizeMb * 1024L * 1024L);
        props.setSupportedExtensions(parseExtensions(row.getSupportedExtensions()));
        props.setMaxAttempts(intOr(row.getMaxAttempts(), 3));
        props.setWatchdogEnabled(!Boolean.FALSE.equals(row.getWatchdogEnabled()));
        props.setStaleAfterMinutes(intOr(row.getStaleAfterMinutes(), 15));
        props.setWatchdogBatchSize(intOr(row.getWatchdogBatchSize(), 50));
        props.setWatchdogIntervalMs(longOr(row.getWatchdogIntervalMs(), 120_000L));
        // 地址/超时可能变了：不重建客户端，新地址不会生效（RestClient 里固化着 baseUrl）
        forgeClient.invalidate();
    }

    /**
     * 校验。范围不是拍脑袋：下限都是"低于它就没意义"，上限都是"高于它基本等于配错"。
     *
     * <p>校验在这里做而不是加 Bean Validation 注解：错误信息要中文、要能指出是哪个字段，
     * 而且这是"运维现场填表"，报错必须直白。
     */
    private void validate(CadForgeConfig row) {
        if (row.getBaseUrl() == null || row.getBaseUrl().isBlank()) {
            throw new IllegalArgumentException("转换器地址不能为空");
        }
        if (!row.getBaseUrl().startsWith("http://") && !row.getBaseUrl().startsWith("https://")) {
            throw new IllegalArgumentException("转换器地址必须以 http:// 或 https:// 开头");
        }
        if (row.getCallbackUrl() != null && !row.getCallbackUrl().isBlank()
                && !row.getCallbackUrl().startsWith("http")) {
            throw new IllegalArgumentException("回调地址必须以 http:// 或 https:// 开头");
        }
        requireRange("连接超时", row.getConnectTimeoutMs(), 100, 60_000);
        requireRange("读取超时", row.getReadTimeoutMs(), 100, 300_000);
        requireRange("源文件大小上限", row.getMaxSourceSizeMb(), 1, 4096);
        requireRange("最大尝试次数", row.getMaxAttempts(), 1, 10);
        requireRange("判定超时", row.getStaleAfterMinutes(), 1, 1440);
        requireRange("单轮扫描条数", row.getWatchdogBatchSize(), 1, 500);
        if (row.getWatchdogIntervalMs() == null) {
            row.setWatchdogIntervalMs(120_000L);
        }
        if (row.getWatchdogIntervalMs() < 10_000L || row.getWatchdogIntervalMs() > 3_600_000L) {
            throw new IllegalArgumentException("扫描间隔需在 10 秒 ~ 60 分钟之间");
        }
    }

    private void requireRange(String label, Integer value, int min, int max) {
        if (value == null) {
            throw new IllegalArgumentException(label + "不能为空");
        }
        if (value < min || value > max) {
            throw new IllegalArgumentException(label + "需在 " + min + " ~ " + max + " 之间");
        }
    }

    /** 扩展名规范化：逗号/分号/空格分隔都接受，统一小写带点 */
    private String normalizeExtensions(String raw) {
        if (raw == null || raw.isBlank()) {
            return "";
        }
        return Arrays.stream(raw.split("[,;\\s]+"))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .map(s -> s.toLowerCase().startsWith(".") ? s.toLowerCase() : "." + s.toLowerCase())
                .distinct()
                .collect(Collectors.joining(","));
    }

    private List<String> parseExtensions(String raw) {
        if (raw == null || raw.isBlank()) {
            return List.of();
        }
        return Arrays.stream(raw.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .toList();
    }

    private String trimToNull(String s) {
        if (s == null) return null;
        String t = s.trim();
        return t.isEmpty() ? null : t;
    }

    private int intOr(Integer value, int fallback) {
        return value == null ? fallback : value;
    }

    private long longOr(Long value, long fallback) {
        return value == null ? fallback : value;
    }
}
