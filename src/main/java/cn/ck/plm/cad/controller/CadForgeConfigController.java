/*
 * Copyright (c) 2026~2028 深圳乘恺科技有限公司
 * All rights reserved.
 *
 * @author Kiween.Hu; Roney.Liu
 */

package cn.ck.plm.cad.controller;

import cn.ck.plm.cad.entity.CadForgeConfig;
import cn.ck.plm.cad.service.CadForgeConfigService;
import cn.ck.plm.iam.dto.ApiResponse;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * 转换器对接配置 API —— 「业务配置 → 轻量化转换」页面。
 *
 * <p>路径没有挂在 {@code /api/cad-visualizations} 下：那里有一个 {@code GET /{oid}}
 * 的路径变量，配置既不是一条转换记录、也不该让路由去猜。
 *
 * <h3>权限</h3>
 * 读：所有登录用户（页面要能显示"是否已启用"）；写：平台管理员
 * （转换器是整个部署共享的一套基础设施，见 {@link CadForgeConfigService#update}）。
 */
@RestController
@RequestMapping("/api/cad-forge/config")
public class CadForgeConfigController {

    private final CadForgeConfigService service;

    public CadForgeConfigController(CadForgeConfigService service) {
        this.service = service;
    }

    /** 当前生效的配置（令牌只回传"是否已配置"） */
    @GetMapping
    public ApiResponse<CadForgeConfig> get() {
        return ApiResponse.ok(service.getForView());
    }

    /** 保存并立即生效（不需要重启） */
    @PutMapping
    public ApiResponse<Object> update(@RequestBody CadForgeConfig config) {
        try {
            return ApiResponse.ok(service.update(config));
        } catch (IllegalArgumentException e) {
            // 参数不合法：直说哪个字段、什么范围
            return ApiResponse.fail(400, e.getMessage());
        } catch (IllegalStateException e) {
            return ApiResponse.fail(403, e.getMessage());
        }
    }

    /**
     * 测试连接 —— 用<b>已生效</b>的配置探一次 {@code /healthz}。
     *
     * <p>返回转换器自报的 engine / 并发 / 车道：起了个没装 OCCT 的解释器时，
     * 这里会直接显示 {@code engine=mock}，比等用户发现"模型是演示齿轮"早得多。
     */
    @PostMapping("/test")
    public ApiResponse<Map<String, Object>> test() {
        return ApiResponse.ok(service.test());
    }
}
