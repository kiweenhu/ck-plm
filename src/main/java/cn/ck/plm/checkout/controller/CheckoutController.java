/*
 * Copyright (c) 2025 深圳乘恺科技有限公司
 * All rights reserved.
 */
package cn.ck.plm.checkout.controller;

import cn.ck.plm.base.util.UserContext;
import cn.ck.plm.checkout.dto.CheckoutVO;
import cn.ck.plm.checkout.service.api.CheckoutService;
import cn.ck.plm.checkout.service.impl.CheckoutOperationServiceImpl;
import cn.ck.plm.iam.dto.ApiResponse;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 检出 API —— 统一入口，支持查询和操作。
 */
@RestController
@RequestMapping("/api/checkout")
public class CheckoutController {

    private final CheckoutService checkoutService;
    private final CheckoutOperationServiceImpl operationService;

    public CheckoutController(CheckoutService checkoutService,
                              CheckoutOperationServiceImpl operationService) {
        this.checkoutService = checkoutService;
        this.operationService = operationService;
    }

    /** 我的检出列表 */
    @GetMapping("/mine")
    public ApiResponse<List<CheckoutVO>> myCheckouts() {
        String user = UserContext.get();
        return ApiResponse.ok(checkoutService.findMyCheckouts(user));
    }

    /**
     * 执行检出操作（通用入口，按 entityType 路由到对应 Provider）。
     *
     * <pre>
     * POST /api/checkout/checkout
     * { "entityType": "DOCUMENT", "entityOid": "uuid", "comment": "修改原因" }
     * </pre>
     */
    @PostMapping("/checkout")
    public ApiResponse<Void> doCheckout(@RequestBody Map<String, Object> body) {
        try {
            String entityType = (String) body.get("entityType");
            String entityOid = (String) body.get("entityOid");
            String comment = body.get("comment") != null ? body.get("comment").toString() : "";
            String user = UserContext.get();

            if (entityType == null || entityOid == null) {
                return ApiResponse.fail(400, "entityType 和 entityOid 不能为空");
            }
            operationService.checkout(entityType, entityOid, comment, user);
            return ApiResponse.ok();
        } catch (IllegalArgumentException e) {
            return ApiResponse.fail(404, e.getMessage());
        } catch (IllegalStateException e) {
            return ApiResponse.fail(409, e.getMessage());
        } catch (Exception e) {
            return ApiResponse.fail(500, "检出失败: " + e.getMessage());
        }
    }

    /**
     * 取消检出（撤销检出，不保留修改）。
     *
     * <pre>
     * POST /api/checkout/undo-checkout
     * { "entityType": "DOCUMENT", "entityOid": "uuid" }
     * </pre>
     */
    @PostMapping("/undo-checkout")
    public ApiResponse<Void> undoCheckout(@RequestBody Map<String, Object> body) {
        try {
            String entityType = (String) body.get("entityType");
            String entityOid = (String) body.get("entityOid");
            String user = UserContext.get();

            if (entityType == null || entityOid == null) {
                return ApiResponse.fail(400, "entityType 和 entityOid 不能为空");
            }
            operationService.undoCheckout(entityType, entityOid, user);
            return ApiResponse.ok();
        } catch (IllegalArgumentException e) {
            return ApiResponse.fail(404, e.getMessage());
        } catch (IllegalStateException e) {
            return ApiResponse.fail(409, e.getMessage());
        } catch (Exception e) {
            return ApiResponse.fail(500, "取消检出失败: " + e.getMessage());
        }
    }
}
