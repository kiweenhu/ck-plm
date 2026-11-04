/*
 * Copyright (c) 2026~2028 深圳乘恺科技有限公司
 * All rights reserved.
 *
 * @author Kiween.Hu; Roney.Liu
 */

package cn.ck.plm.base.controller;

import cn.ck.plm.base.entity.TenantCapability;
import cn.ck.plm.base.service.api.CapabilityService;
import cn.ck.plm.iam.dto.ApiResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 能力读侧接口 —— 前端据此决定"功能入口该不该显示"。
 *
 * <p>口径：只返回本租户账本里**已启用**的能力。包里声明但平台尚未实现的（如 CHANGE_MANAGEMENT）
 * 不会出现在这里 —— 所以界面不会出现"标着已启用、点进去什么都没有"的入口。
 */
@RestController
@RequestMapping("/api/capabilities")
public class CapabilityController {

    private final CapabilityService capabilityService;

    public CapabilityController(CapabilityService capabilityService) {
        this.capabilityService = capabilityService;
    }

    /** 本租户已启用的能力（含名称与来源包） */
    @GetMapping
    public ApiResponse<List<TenantCapability>> list() {
        return ApiResponse.ok(capabilityService.listEnabled());
    }
}
