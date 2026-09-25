/*
 * Copyright (c) 2025 深圳乘恺科技有限公司
 * All rights reserved.
 *
 * @author Kiween.Hu; Roney.Liu
 */

package cn.ck.plm.process.controller;

import cn.ck.plm.iam.dto.ApiResponse;
import cn.ck.plm.process.entity.ProcessCategory;
import cn.ck.plm.process.service.api.ProcessCategoryService;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 流程分组 API —— 流程清单页左侧「分组」维度的后端入口。
 *
 * <pre>
 * GET    /api/plm/process-categories          分组列表（按 sort_order、name）
 * POST   /api/plm/process-categories          新建分组（body: { name, sortOrder?, description? }）
 * PUT    /api/plm/process-categories/{oid}    修改（改名会同步组内模板）
 * DELETE /api/plm/process-categories/{oid}    删除（组内仍有流程时拒绝）
 * </pre>
 *
 * <p>使用方法（与清单页的交互一致）：<b>先建分组 → 选中分组 → 在组内新建并设计流程</b>。
 * 模板的 {@code category} 一律来自本接口，前端不再提供自由输入的分类输入框。
 *
 * <p>错误码约定：入参问题（名称为空 / 重名）→ 400；对象不存在 → 404；
 * 状态问题（组内仍有流程）→ 409。
 */
@RestController
@RequestMapping("/api/plm/process-categories")
public class ProcessCategoryController {

    private final ProcessCategoryService service;

    public ProcessCategoryController(ProcessCategoryService service) {
        this.service = service;
    }

    @GetMapping
    public ApiResponse<List<ProcessCategory>> list() {
        try {
            return ApiResponse.ok(service.list());
        } catch (Exception e) {
            return ApiResponse.fail(500, "查询流程分组失败: " + e.getMessage());
        }
    }

    @PostMapping
    public ApiResponse<ProcessCategory> create(@RequestBody Map<String, Object> body) {
        try {
            return ApiResponse.ok(service.create(
                    str(body, "name"), intOrNull(body, "sortOrder"), str(body, "description")));
        } catch (IllegalArgumentException e) {
            return ApiResponse.fail(400, e.getMessage());
        } catch (Exception e) {
            return ApiResponse.fail(500, "新建流程分组失败: " + e.getMessage());
        }
    }

    @PutMapping("/{oid}")
    public ApiResponse<ProcessCategory> update(@PathVariable String oid,
                                               @RequestBody Map<String, Object> body) {
        try {
            return ApiResponse.ok(service.update(
                    oid, str(body, "name"), intOrNull(body, "sortOrder"), str(body, "description")));
        } catch (IllegalArgumentException e) {
            return ApiResponse.fail(400, e.getMessage());
        } catch (Exception e) {
            return ApiResponse.fail(500, "修改流程分组失败: " + e.getMessage());
        }
    }

    @DeleteMapping("/{oid}")
    public ApiResponse<Void> delete(@PathVariable String oid) {
        try {
            service.delete(oid);
            return ApiResponse.ok();
        } catch (IllegalArgumentException e) {
            // 组内仍有流程属于"状态不允许"，与"对象不存在"区分开
            int code = e.getMessage() != null && e.getMessage().startsWith("分组不存在") ? 404 : 409;
            return ApiResponse.fail(code, e.getMessage());
        } catch (Exception e) {
            return ApiResponse.fail(500, "删除流程分组失败: " + e.getMessage());
        }
    }

    // ==================== 私有工具 ====================

    private static String str(Map<String, Object> body, String key) {
        if (body == null) {
            return null;
        }
        Object value = body.get(key);
        return value != null ? value.toString() : null;
    }

    private static Integer intOrNull(Map<String, Object> body, String key) {
        if (body == null) {
            return null;
        }
        Object value = body.get(key);
        if (value == null || value.toString().trim().isEmpty()) {
            return null;
        }
        try {
            return Integer.valueOf(value.toString().trim());
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException(key + " 必须是整数");
        }
    }
}
