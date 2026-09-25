/*
 * Copyright (c) 2025 深圳乘恺科技有限公司
 * All rights reserved.
 *
 * @author Kiween.Hu; Roney.Liu
 */

package cn.ck.plm.resource.controller;

import cn.ck.plm.cls.entity.Classification;
import cn.ck.plm.iam.dto.ApiResponse;
import cn.ck.plm.resource.service.api.ResourceLibraryService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 企业资源库 REST 控制器。
 *
 * <p>各资源子库管理的对象是 Part 主数据的软类型（创建/编辑走 /api/parts），本控制器负责：
 * 资源库分类绑定（按资源库节点：COMPONENT/STD_PART/GEN_PART）、
 * 资源库上下文、按分类查询资源库清单。
 */
@RestController
@RequestMapping("/api/resource-libraries")
public class ResourceLibraryController {

    private static final Logger log = LoggerFactory.getLogger(ResourceLibraryController.class);

    private final ResourceLibraryService resourceLibraryService;

    public ResourceLibraryController(ResourceLibraryService resourceLibraryService) {
        this.resourceLibraryService = resourceLibraryService;
    }

    /** 查询指定资源库绑定的分类根节点及其子树（未绑定时 data 为 null） */
    @GetMapping("/category-tree")
    public ApiResponse<Classification> categoryTree(@RequestParam(required = false) String resourceCode) {
        return ApiResponse.ok(resourceLibraryService.getCategoryTree(resourceCode));
    }

    /** 查询当前租户指定资源库的绑定根分类 oid（供业务配置回显） */
    @GetMapping("/category-binding")
    public ApiResponse<Map<String, Object>> getCategoryBinding(@RequestParam(required = false) String resourceCode) {
        Map<String, Object> result = new HashMap<>();
        result.put("resourceCode", resourceCode == null || resourceCode.isEmpty() ? "COMPONENT" : resourceCode);
        result.put("rootClassificationOid", resourceLibraryService.getBindingRootOid(resourceCode));
        return ApiResponse.ok(result);
    }

    /**
     * 一次性返回当前租户所有资源库（COMPONENT / STD_PART / GEN_PART）的分类根节点绑定。
     * 含平台租户回退。返回 Map<resourceCode, rootClassificationOid>，缺失的 key 表示未绑定。
     * 用于「业务配置 → 资源库分类」页面加载（替代 N 次单条调用）。
     */
    @GetMapping("/category-bindings")
    public ApiResponse<Map<String, String>> getAllCategoryBindings() {
        return ApiResponse.ok(resourceLibraryService.getAllBindings());
    }

    /** 绑定资源库分类根节点（body: { resourceCode, rootClassificationOid }） */
    @PostMapping("/category-binding")
    public ApiResponse<Void> bindCategory(@RequestBody Map<String, String> body) {
        String resourceCode = body.get("resourceCode");
        String rootOid = body.get("rootClassificationOid");
        log.info("收到资源库分类绑定请求: resourceCode={}, rootClassificationOid={}", resourceCode, rootOid);
        try {
            resourceLibraryService.bindCategory(resourceCode, rootOid);
            log.info("资源库分类绑定成功: resourceCode={}", resourceCode);
            return ApiResponse.ok();
        } catch (IllegalArgumentException e) {
            log.warn("资源库分类绑定被拒绝: resourceCode={}, 原因={}", resourceCode, e.getMessage());
            return ApiResponse.fail(400, e.getMessage());
        } catch (Exception e) {
            log.error("资源库分类绑定失败: resourceCode={}", resourceCode, e);
            return ApiResponse.fail(500, "绑定分类失败: " + e.getMessage());
        }
    }

    /**
     * 资源库归属上下文（containerOid/containerType/containerCode/containerName/stageOid/typeCode）。
     * resourceCode 可空（默认 COMPONENT）。
     */
    @GetMapping("/context")
    public ApiResponse<Map<String, Object>> resourceContext(
            @RequestParam(required = false) String resourceCode) {
        return ApiResponse.ok(resourceLibraryService.getResourceContext(resourceCode));
    }

    /**
     * 资源库清单：按分类集合查询该库对应软类型的 Part。
     *
     * @param resourceCode COMPONENT→ELECTRONIC / STD_PART→STD_PART / GEN_PART→GEN_PART（可空=COMPONENT）
     * @param categoryOids 分类 oid 集合（逗号分隔，可空 = 全部分类）
     */
    @GetMapping
    public ApiResponse<Map<String, Object>> listItems(
            @RequestParam(required = false) String resourceCode,
            @RequestParam(required = false) String categoryOids,
            @RequestParam(required = false) String keyword) {
        List<String> oids = null;
        if (categoryOids != null && !categoryOids.trim().isEmpty()) {
            oids = List.of(categoryOids.split(","));
        }
        Map<String, Object> result = new HashMap<>();
        result.put("items", resourceLibraryService.findItems(resourceCode, oids, keyword));
        result.put("context", resourceLibraryService.getResourceContext(resourceCode));
        return ApiResponse.ok(result);
    }
}
