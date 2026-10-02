/*
 * Copyright (c) 2025 深圳乘恺科技有限公司
 * All rights reserved.
 *
 * @author Kiween.Hu; Roney.Liu
 */

package cn.ck.plm.part.controller;

import cn.ck.plm.iam.dto.ApiResponse;
import cn.ck.plm.part.entity.StdPartInboundConfig;
import cn.ck.plm.part.service.StdPartInboundConfigService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 标准件入库流程配置 REST 控制器 —— 「业务配置中心 → 资源库配置 → 标准件入库流程」对应的后端。
 */
@RestController
@RequestMapping("/api/std-part-inbound-config")
public class StdPartInboundConfigController {

    @Autowired
    private StdPartInboundConfigService service;

    /** 读取当前租户的配置（未配置时返回默认值：不启用流程） */
    @GetMapping
    public ApiResponse<StdPartInboundConfig> get() {
        try {
            return ApiResponse.ok(service.get());
        } catch (Exception e) {
            return ApiResponse.fail(500, "读取标准件入库流程配置失败: " + e.getMessage());
        }
    }

    /** 保存当前租户的配置 */
    @PutMapping
    public ApiResponse<StdPartInboundConfig> save(@RequestBody StdPartInboundConfig config) {
        try {
            return ApiResponse.ok(service.save(config));
        } catch (IllegalArgumentException e) {
            return ApiResponse.fail(400, e.getMessage());
        } catch (Exception e) {
            return ApiResponse.fail(500, "保存标准件入库流程配置失败: " + e.getMessage());
        }
    }
}
