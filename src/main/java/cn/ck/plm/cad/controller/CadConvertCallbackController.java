/*
 * Copyright (c) 2026~2028 深圳乘恺科技有限公司
 * All rights reserved.
 *
 * @author Kiween.Hu; Roney.Liu
 */

package cn.ck.plm.cad.controller;

import cn.ck.plm.cad.config.CadForgeProperties;
import cn.ck.plm.cad.dto.ForgeCallbackPayload;
import cn.ck.plm.cad.service.api.CadConvertService;
import cn.ck.plm.iam.dto.ApiResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.*;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

/**
 * 转换结果回调 —— 「等待结果反馈」的主通道。
 *
 * <h3>为什么它不在登录拦截器里</h3>
 * 调用方是转换器进程，没有用户会话，走不了 {@code AuthInterceptor} 的 token 校验，
 * 所以这个路径被加入了拦截器白名单（见 {@code AuthConfig}）。
 *
 * <h3>那靠什么防伪造</h3>
 * 服务间共享令牌 {@code plm.cad.forge.callback-token}，用<b>常量时间比较</b>校验 ——
 * 用 {@code equals} 会因提前返回而泄漏匹配前缀长度，令牌比较必须常量时间。
 * 令牌为空时拒绝全部回调：宁可回调全部失败（还有看门狗兜底），
 * 也不接受"任何人都能声称转换成功"。
 */
@RestController
@RequestMapping("/api/internal/cad")
public class CadConvertCallbackController {

    private static final Logger log = LoggerFactory.getLogger(CadConvertCallbackController.class);

    private final CadConvertService convertService;
    private final CadForgeProperties properties;

    public CadConvertCallbackController(CadConvertService convertService,
                                        CadForgeProperties properties) {
        this.convertService = convertService;
        this.properties = properties;
    }

    @PostMapping("/convert/callback")
    public ApiResponse<Void> callback(@RequestBody(required = false) ForgeCallbackPayload payload,
                                      @RequestHeader(value = "X-Cad-Forge-Token", required = false) String token) {
        String expected = properties.getCallbackToken();
        if (expected == null || expected.isBlank() || !constantTimeEquals(expected, token)) {
            log.warn("拒绝未授权的转换回调: 令牌为空或不匹配");
            return ApiResponse.fail(401, "未授权的回调");
        }
        if (payload == null) {
            return ApiResponse.fail(400, "回调内容为空");
        }
        try {
            convertService.handleCallback(payload);
            return ApiResponse.ok();
        } catch (Exception e) {
            log.error("处理转换回调失败: externalId={}", payload.getExternalId(), e);
            return ApiResponse.fail(500, "处理回调失败: " + e.getMessage());
        }
    }

    /** 常量时间字符串比较，避免通过响应时间侧信道泄漏令牌前缀 */
    private static boolean constantTimeEquals(String a, String b) {
        if (a == null || b == null) return false;
        return MessageDigest.isEqual(a.getBytes(StandardCharsets.UTF_8), b.getBytes(StandardCharsets.UTF_8));
    }
}
