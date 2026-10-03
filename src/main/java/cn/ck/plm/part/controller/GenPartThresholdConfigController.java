/*
 * Copyright (c) 2026~2028 深圳乘恺科技有限公司
 * All rights reserved.
 *
 * @author Kiween.Hu; Roney.Liu
 */

package cn.ck.plm.part.controller;

import cn.ck.plm.iam.dto.ApiResponse;
import cn.ck.plm.part.entity.GenPartThresholdConfig;
import cn.ck.plm.part.service.GenPartThresholdConfigService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 通用件阈值配置 REST 控制器 —— 「业务配置中心 → 通用件阈值配置」对应的后端。
 *
 * <p>一个租户一份（没有就返回默认口径）；流程模板以 oid 绑定，流程结束回调里据此发起认定。
 */
@RestController
@RequestMapping("/api/gen-part-threshold-config")
public class GenPartThresholdConfigController {

    @Autowired
    private GenPartThresholdConfigService service;

    /** 读取当前租户的阈值配置（未配置时返回默认值） */
    @GetMapping
    public ApiResponse<GenPartThresholdConfig> get() {
        try {
            return ApiResponse.ok(service.get());
        } catch (Exception e) {
            return ApiResponse.fail(500, "读取通用件阈值配置失败: " + e.getMessage());
        }
    }

    /** 保存当前租户的阈值配置 */
    @PutMapping
    public ApiResponse<GenPartThresholdConfig> save(@RequestBody GenPartThresholdConfig config) {
        try {
            return ApiResponse.ok(service.save(config));
        } catch (IllegalArgumentException e) {
            return ApiResponse.fail(400, e.getMessage());
        } catch (Exception e) {
            return ApiResponse.fail(500, "保存通用件阈值配置失败: " + e.getMessage());
        }
    }
}
