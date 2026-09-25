/*
 * Copyright (c) 2025 深圳乘恺科技有限公司
 * All rights reserved.
 *
 * @author Kiween.Hu; Roney.Liu
 */

package cn.ck.plm.process.delegate;

import org.junit.jupiter.api.Test;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 「REST 接口调用」的地址拼装：斜杠不能双写、中文与空格必须编码。
 *
 * <p>这些是最容易"看起来对、实际 404/乱码"的地方，所以单独钉住。
 */
class RestCallHandlerUrlTest {

    @Test
    void 系统地址与接口路径的斜杠不会双写() {
        assertThat(RestCallHandler.joinUrl("https://erp.example.com", "/api/order"))
                .isEqualTo("https://erp.example.com/api/order");
        assertThat(RestCallHandler.joinUrl("https://erp.example.com/", "api/order"))
                .isEqualTo("https://erp.example.com/api/order");
    }

    @Test
    void GET_参数拼成查询串并做编码() {
        Map<String, String> params = new LinkedHashMap<>();
        params.put("orderNo", "PO 001");
        params.put("name", "中文");
        String url = RestCallHandler.withQuery("https://erp.example.com/api/order", params);
        assertThat(url).startsWith("https://erp.example.com/api/order?orderNo=PO+001&name=");
        assertThat(url).contains(URLEncoder.encode("中文", StandardCharsets.UTF_8));
    }

    @Test
    void 原地址已带查询串时用与号连接() {
        Map<String, String> params = new LinkedHashMap<>();
        params.put("page", "1");
        assertThat(RestCallHandler.withQuery("https://erp.example.com/api?type=A", params))
                .isEqualTo("https://erp.example.com/api?type=A&page=1");
    }

    @Test
    void 没有参数时地址原样返回() {
        assertThat(RestCallHandler.withQuery("https://erp.example.com/api", Map.of()))
                .isEqualTo("https://erp.example.com/api");
    }
}
