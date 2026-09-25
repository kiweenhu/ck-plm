/*
 * Copyright (c) 2025 深圳乘恺科技有限公司
 * All rights reserved.
 *
 * @author Kiween.Hu; Roney.Liu
 */

package cn.ck.plm.integration.util;

import cn.ck.plm.integration.config.IntegrationProperties;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 出站地址守卫 —— 这些断言就是"设计器里填的地址不能拿来打内网"这条承诺的凭据。
 *
 * <p>不依赖真实网络：本机/私网/链路本地地址都是字面量；白名单命中的主机在解析之前就返回，
 * 所以下面这些用例在离线环境也稳定。
 */
class OutboundUrlGuardTest {

    private static IntegrationProperties props(String... allowedHosts) {
        IntegrationProperties properties = new IntegrationProperties();
        properties.setAllowedHosts(List.of(allowedHosts));
        return properties;
    }

    @Test
    void 只放行_http_与_https() {
        assertThatThrownBy(() -> OutboundUrlGuard.assertAllowed("ftp://example.com/a", props()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("http/https");
        assertThatThrownBy(() -> OutboundUrlGuard.assertAllowed("file:///etc/passwd", props()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void 私网与本机地址一律拦下() {
        assertThatThrownBy(() -> OutboundUrlGuard.assertAllowed("http://10.0.0.5/api", props()))
                .hasMessageContaining("内网");
        assertThatThrownBy(() -> OutboundUrlGuard.assertAllowed("http://192.168.1.10:8080/api", props()))
                .hasMessageContaining("内网");
        assertThatThrownBy(() -> OutboundUrlGuard.assertAllowed("http://127.0.0.1:8080/api", props()))
                .hasMessageContaining("内网");
        // 云元数据服务：SSRF 最典型的目标（169.254.169.254 上挂着实例凭据）
        assertThatThrownBy(() -> OutboundUrlGuard.assertAllowed(
                "http://169.254.169.254/latest/meta-data/", props()))
                .hasMessageContaining("内网");
        // IPv6 回环
        assertThatThrownBy(() -> OutboundUrlGuard.assertAllowed("http://[::1]:8080/api", props()))
                .hasMessageContaining("内网");
    }

    @Test
    void 白名单里的内网地址放行() {
        assertThatCode(() -> OutboundUrlGuard.assertAllowed("http://10.20.30.40:8080/api",
                props("10.20.30.40:8080"))).doesNotThrowAnyException();
        // 白名单精确到端口：同主机不同端口不命中
        assertThatThrownBy(() -> OutboundUrlGuard.assertAllowed("http://10.20.30.40:9090/api",
                props("10.20.30.40:8080"))).hasMessageContaining("内网");
    }

    @Test
    void 后缀通配白名单只放子域不放父域() {
        assertThat(OutboundUrlGuard.isWhitelisted(
                "erp.internal.example.com", -1, List.of("*.example.com"))).isTrue();
        // *.example.com 不该顺带放行 example.com 本身
        assertThat(OutboundUrlGuard.isWhitelisted(
                "example.com", -1, List.of("*.example.com"))).isFalse();
        // 后缀必须落在点边界上，不能拿 notexample.com 冒充
        assertThat(OutboundUrlGuard.isWhitelisted(
                "notexample.com", -1, List.of("*.example.com"))).isFalse();
    }

    @Test
    void 打开允许私网开关后不再拦截() {
        IntegrationProperties properties = props();
        properties.setAllowPrivateHosts(true);
        assertThatCode(() -> OutboundUrlGuard.assertAllowed("http://10.0.0.5/api", properties))
                .doesNotThrowAnyException();
    }

    @Test
    void 缺少主机名的地址报清楚() {
        assertThatThrownBy(() -> OutboundUrlGuard.assertAllowed("http:///api", props()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("主机名");
    }
}
