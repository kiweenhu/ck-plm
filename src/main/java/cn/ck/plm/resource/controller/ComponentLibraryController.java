/*
 * Copyright (c) 2025 深圳乘恺科技有限公司
 * All rights reserved.
 *
 * @author Kiween.Hu; Roney.Liu
 */

package cn.ck.plm.resource.controller;

import cn.ck.plm.iam.dto.ApiResponse;
import cn.ck.plm.resource.entity.ComponentCategory;
import cn.ck.plm.resource.entity.ElectronicComponent;
import cn.ck.plm.resource.service.api.ComponentLibraryService;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 企业资源库-电子元器件库 REST 控制器：分类树 + 元器件主数据。
 */
@RestController
public class ComponentLibraryController {

    private final ComponentLibraryService componentLibraryService;

    public ComponentLibraryController(ComponentLibraryService componentLibraryService) {
        this.componentLibraryService = componentLibraryService;
    }

    // ==================== 分类 ====================

    @PostMapping("/api/component-categories")
    public ApiResponse<ComponentCategory> createCategory(@RequestBody ComponentCategory category) {
        try {
            return ApiResponse.ok(componentLibraryService.createCategory(category));
        } catch (IllegalArgumentException e) {
            return ApiResponse.fail(400, e.getMessage());
        } catch (Exception e) {
            return ApiResponse.fail(500, "创建分类失败: " + e.getMessage());
        }
    }

    @PutMapping("/api/component-categories/{oid}")
    public ApiResponse<ComponentCategory> updateCategory(@PathVariable String oid,
                                                         @RequestBody ComponentCategory category) {
        try {
            category.setOid(oid);
            return ApiResponse.ok(componentLibraryService.updateCategory(category));
        } catch (IllegalArgumentException e) {
            return ApiResponse.fail(400, e.getMessage());
        } catch (Exception e) {
            return ApiResponse.fail(500, "更新分类失败: " + e.getMessage());
        }
    }

    @DeleteMapping("/api/component-categories/{oid}")
    public ApiResponse<Void> deleteCategory(@PathVariable String oid) {
        try {
            componentLibraryService.deleteCategory(oid);
            return ApiResponse.ok();
        } catch (IllegalArgumentException e) {
            return ApiResponse.fail(400, e.getMessage());
        } catch (Exception e) {
            return ApiResponse.fail(500, "删除分类失败: " + e.getMessage());
        }
    }

    /** 查询完整分类树 */
    @GetMapping("/api/component-categories/tree")
    public ApiResponse<List<ComponentCategory>> categoryTree() {
        return ApiResponse.ok(componentLibraryService.findCategoryTree());
    }

    // ==================== 元器件 ====================

    @PostMapping("/api/electronic-components")
    public ApiResponse<ElectronicComponent> createComponent(@RequestBody ElectronicComponent component) {
        try {
            return ApiResponse.ok(componentLibraryService.createComponent(component));
        } catch (IllegalArgumentException e) {
            return ApiResponse.fail(400, e.getMessage());
        } catch (Exception e) {
            return ApiResponse.fail(500, "创建元器件失败: " + e.getMessage());
        }
    }

    @PutMapping("/api/electronic-components/{oid}")
    public ApiResponse<ElectronicComponent> updateComponent(@PathVariable String oid,
                                                            @RequestBody ElectronicComponent component) {
        try {
            component.setOid(oid);
            return ApiResponse.ok(componentLibraryService.updateComponent(component));
        } catch (IllegalArgumentException e) {
            return ApiResponse.fail(400, e.getMessage());
        } catch (Exception e) {
            return ApiResponse.fail(500, "更新元器件失败: " + e.getMessage());
        }
    }

    @DeleteMapping("/api/electronic-components/{oid}")
    public ApiResponse<Void> deleteComponent(@PathVariable String oid) {
        try {
            componentLibraryService.deleteComponent(oid);
            return ApiResponse.ok();
        } catch (Exception e) {
            return ApiResponse.fail(500, "删除元器件失败: " + e.getMessage());
        }
    }

    @GetMapping("/api/electronic-components/{oid}")
    public ApiResponse<ElectronicComponent> getComponent(@PathVariable String oid) {
        return ApiResponse.ok(componentLibraryService.findComponentByOid(oid));
    }

    /** 按分类 + 关键字查询元器件（categoryOid 可空 = 全部分类） */
    @GetMapping("/api/electronic-components")
    public ApiResponse<List<ElectronicComponent>> listComponents(
            @RequestParam(required = false) String categoryOid,
            @RequestParam(required = false) String keyword) {
        return ApiResponse.ok(componentLibraryService.findComponents(categoryOid, keyword));
    }

    /** 统计信息（元器件总数 / 库存总数），用于列表页统计栏 */
    @GetMapping("/api/electronic-components/stats")
    public ApiResponse<Map<String, Long>> stats() {
        Map<String, Long> stats = new HashMap<>();
        stats.put("componentCount", componentLibraryService.countComponents());
        stats.put("stockTotal", componentLibraryService.sumStockQty());
        return ApiResponse.ok(stats);
    }
}
