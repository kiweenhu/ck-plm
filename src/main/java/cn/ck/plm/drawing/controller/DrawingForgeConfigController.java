/*
 * Copyright (c) 2026~2028 深圳乘恺科技有限公司
 * All rights reserved.
 *
 * @author Kiween.Hu
 */

package cn.ck.plm.drawing.controller;

import cn.ck.plm.drawing.entity.DrawingForgeConfig;
import cn.ck.plm.drawing.service.DrawingForgeConfigService;
import cn.ck.plm.iam.dto.ApiResponse;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * 二维转换器对接配置 API —— 「业务配置 → 二维图纸轻量化」。
 * 读：所有登录用户；写：平台管理员（整个部署共享一套转换器）。
 */
@RestController
@RequestMapping("/api/drawing-forge/config")
public class DrawingForgeConfigController {

    private final DrawingForgeConfigService service;

    public DrawingForgeConfigController(DrawingForgeConfigService service) {
        this.service = service;
    }

    /** 当前生效的配置（令牌只回传"是否已配置"） */
    @GetMapping
    public ApiResponse<DrawingForgeConfig> get() {
        return ApiResponse.ok(service.getForView());
    }

    /** 保存并立即生效（不需要重启） */
    @PutMapping
    public ApiResponse<Object> update(@RequestBody DrawingForgeConfig config) {
        try {
            return ApiResponse.ok(service.update(config));
        } catch (IllegalArgumentException e) {
            return ApiResponse.fail(400, e.getMessage());
        } catch (IllegalStateException e) {
            return ApiResponse.fail(403, e.getMessage());
        }
    }

    /** 测试连接 —— 用已生效的配置探 /healthz，返回引擎/并发/车道 */
    @PostMapping("/test")
    public ApiResponse<Map<String, Object>> test() {
        return ApiResponse.ok(service.test());
    }
}
