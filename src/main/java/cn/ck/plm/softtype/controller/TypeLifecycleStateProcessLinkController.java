/*
 * Copyright (c) 2025 深圳乘恺科技有限公司
 * All rights reserved.
 *
 * @author Kiween.Hu; Roney.Liu
 */

package cn.ck.plm.softtype.controller;

import cn.ck.plm.iam.dto.ApiResponse;
import cn.ck.plm.softtype.service.api.TypeLifecycleStateProcessService;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * 类型-生命周期状态-流程模板 关联 REST 控制器。
 *
 * <p>接口路径与同族 {@link TypeLifecycleTemplateLinkController} 对称；主语一律是<b>类型</b>：
 * 同一个生命周期模板（如 STANDARD）会被多个类型复用，各自可绑不同流程。
 * 该类型用的是哪个生命周期模板、归属哪个租户，由服务侧自行解析（调用方不必传）。
 *
 * <p>本期只做配置与展示 —— 发起流程仍由人工手动发起，运行期自动触发未接入。
 */
@RestController
@RequestMapping("/api/type-lifecycle-state-process-links")
public class TypeLifecycleStateProcessLinkController {

    private final TypeLifecycleStateProcessService service;

    public TypeLifecycleStateProcessLinkController(TypeLifecycleStateProcessService service) {
        this.service = service;
    }

    /**
     * 该类型（按其当前所绑生命周期模板）的「状态 → 流程模板」映射。
     *
     * <p>返回 {@code { 状态 code: 流程模板 oid }}；未绑定的状态不在其中，
     * 该类型还没绑生命周期模板时返回空映射（前端据此提示"先绑模板"）。
     */
    @GetMapping("/type/{typeOid}")
    public ApiResponse<Map<String, String>> mapByType(@PathVariable String typeOid) {
        try {
            return ApiResponse.ok(service.mapByType(typeOid));
        } catch (Exception e) {
            return ApiResponse.fail(500, "查询状态流程关联失败: " + e.getMessage());
        }
    }

    /**
     * 绑定 / 改绑 / 解绑某状态的流程模板。
     *
     * <pre>{ "processTemplateOid": "…" }</pre>
     *
     * <p>{@code processTemplateOid} 传空（null 或 ""）即<b>解绑</b>。返回更新后的整张映射，
     * 供前端一次刷新。
     */
    @PutMapping("/type/{typeOid}/{statusCode}")
    public ApiResponse<Map<String, String>> bind(@PathVariable String typeOid,
                                                 @PathVariable String statusCode,
                                                 @RequestBody Map<String, Object> body) {
        try {
            Object value = body == null ? null : body.get("processTemplateOid");
            return ApiResponse.ok(service.bind(typeOid, statusCode,
                    value == null ? null : value.toString()));
        } catch (IllegalArgumentException e) {
            return ApiResponse.fail(400, e.getMessage());
        } catch (Exception e) {
            return ApiResponse.fail(500, "绑定流程模板失败: " + e.getMessage());
        }
    }
}
