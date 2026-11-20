/*
 * Copyright (c) 2026~2028 深圳乘恺科技有限公司
 * All rights reserved.
 *
 * @author Kiween.Hu
 */

package cn.ck.plm.drawing.controller;

import cn.ck.plm.drawing.config.DrawingForgeProperties;
import cn.ck.plm.drawing.dto.DrawingForgeCallbackPayload;
import cn.ck.plm.drawing.service.api.DrawingConvertService;
import cn.ck.plm.iam.dto.ApiResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.*;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

/**
 * 二维转换结果回调 —— 主通道。
 *
 * <p>调用方是转换器进程，没有用户会话，走不了 AuthInterceptor，故本路径须加入拦截器白名单
 * （见 AuthConfig，与 /api/internal/cad/convert/callback 并列）。
 *
 * <p>靠服务间共享令牌 plm.drawing.forge.callback-token 校验，用常量时间比较 —— equals
 * 会因提前返回而泄漏匹配前缀长度。令牌为空时拒绝全部回调（还有看门狗兜底）。
 */
@RestController
@RequestMapping("/api/internal/drawing")
public class DrawingConvertCallbackController {

    private static final Logger log = LoggerFactory.getLogger(DrawingConvertCallbackController.class);

    private final DrawingConvertService convertService;
    private final DrawingForgeProperties properties;

    public DrawingConvertCallbackController(DrawingConvertService convertService,
                                            DrawingForgeProperties properties) {
        this.convertService = convertService;
        this.properties = properties;
    }

    @PostMapping("/convert/callback")
    public ApiResponse<Void> callback(@RequestBody(required = false) DrawingForgeCallbackPayload payload,
                                      @RequestHeader(value = "X-Cad-Forge-Token", required = false) String token) {
        String expected = properties.getCallbackToken();
        if (expected == null || expected.isBlank() || !constantTimeEquals(expected, token)) {
            log.warn("拒绝未授权的二维转换回调: 令牌为空或不匹配");
            return ApiResponse.fail(401, "未授权的回调");
        }
        if (payload == null) {
            return ApiResponse.fail(400, "回调内容为空");
        }
        try {
            convertService.handleCallback(payload);
            return ApiResponse.ok();
        } catch (Exception e) {
            log.error("处理二维转换回调失败: externalId={}", payload.getExternalId(), e);
            return ApiResponse.fail(500, "处理回调失败: " + e.getMessage());
        }
    }

    /** 常量时间字符串比较，避免通过响应时间侧信道泄漏令牌前缀。 */
    private static boolean constantTimeEquals(String a, String b) {
        if (a == null || b == null) return false;
        return MessageDigest.isEqual(a.getBytes(StandardCharsets.UTF_8), b.getBytes(StandardCharsets.UTF_8));
    }
}
