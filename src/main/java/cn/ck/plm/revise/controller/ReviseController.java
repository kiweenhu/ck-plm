/*
 * Copyright (c) 2026~2028 深圳乘恺科技有限公司
 * All rights reserved.
 */
package cn.ck.plm.revise.controller;

import cn.ck.plm.base.util.UserContext;
import cn.ck.plm.iam.dto.ApiResponse;
import cn.ck.plm.revise.service.impl.ReviseOperationServiceImpl;
import org.springframework.web.bind.annotation.*;

import java.util.Collection;
import java.util.Map;

/**
 * 修订 API —— 统一入口，按 {@code entityType} 路由到对应 {@code ReviseProvider}。
 *
 * <p>与检出/检入的 {@code /api/checkout/*} 同构：调用方只需要知道对象 oid 与实体类型，
 * 不需要为部件 / 文档 / 工程数据各记一条端点。
 */
@RestController
@RequestMapping("/api/revise")
public class ReviseController {

    private final ReviseOperationServiceImpl operationService;

    public ReviseController(ReviseOperationServiceImpl operationService) {
        this.operationService = operationService;
    }

    /**
     * 修订对象：基于当前最新版本创建新的大版本（revision+1，iteration=1）。
     *
     * <pre>
     * POST /api/revise
     * { "entityType": "DOCUMENT", "entityOid": "uuid" }
     * </pre>
     */
    @PostMapping
    public ApiResponse<Void> revise(@RequestBody Map<String, Object> body) {
        try {
            String entityType = (String) body.get("entityType");
            String entityOid = (String) body.get("entityOid");
            if (entityType == null || entityOid == null) {
                return ApiResponse.fail(400, "entityType 和 entityOid 不能为空");
            }
            operationService.revise(entityType, entityOid, UserContext.get());
            return ApiResponse.ok();
        } catch (IllegalArgumentException e) {
            return ApiResponse.fail(404, e.getMessage());
        } catch (IllegalStateException e) {
            return ApiResponse.fail(409, e.getMessage());
        } catch (Exception e) {
            return ApiResponse.fail(500, "修订失败: " + e.getMessage());
        }
    }

    /** 已注册的修订实体类型（诊断 / 能力探询） */
    @GetMapping("/supported")
    public ApiResponse<Collection<String>> supported() {
        return ApiResponse.ok(operationService.supportedEntityTypes());
    }
}
