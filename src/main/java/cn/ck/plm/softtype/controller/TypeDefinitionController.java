/*
 * Copyright (c) 2025 深圳乘恺科技有限公司
 * All rights reserved.
 *
 * @author Kiween.Hu; Roney.Liu
 */

package cn.ck.plm.softtype.controller;

import cn.ck.plm.iam.dto.ApiResponse;
import cn.ck.plm.softtype.entity.TypeDefinition;
import cn.ck.plm.softtype.service.api.TypeDefinitionService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 类型定义 REST 控制器 —— 统一管理 OOTB 实体和 SOFT_TYPE 子类型。
 */
@RestController
@RequestMapping("/api/type-definitions")
public class TypeDefinitionController {

    private static final Logger log = LoggerFactory.getLogger(TypeDefinitionController.class);

    private final TypeDefinitionService service;

    public TypeDefinitionController(TypeDefinitionService service) {
        this.service = service;
    }

    /** 获取全部类型定义（平铺） */
    @GetMapping
    public ApiResponse<List<TypeDefinition>> list(@RequestParam(defaultValue = "false") boolean enabledOnly) {
        if (enabledOnly) {
            return ApiResponse.ok(service.findEnabled());
        }
        return ApiResponse.ok(service.findAll());
    }

    /** 获取类型树（OOTB 根 + 递归子节点） */
    @GetMapping("/tree")
    public ApiResponse<List<TypeDefinition>> tree() {
        return ApiResponse.ok(service.findTree());
    }

    /** 获取 OOTB 根类型 */
    @GetMapping("/roots")
    public ApiResponse<List<TypeDefinition>> roots() {
        return ApiResponse.ok(service.findRoots());
    }

    /** 获取某类型的子类型 */
    @GetMapping("/children")
    public ApiResponse<List<TypeDefinition>> children(@RequestParam String parentOid) {
        return ApiResponse.ok(service.findChildren(parentOid));
    }

    /** 按 typeKind 筛选 */
    @GetMapping("/by-kind")
    public ApiResponse<List<TypeDefinition>> listByKind(@RequestParam String typeKind) {
        return ApiResponse.ok(service.findByTypeKind(typeKind));
    }

    /** 获取单个类型详情 */
    @GetMapping("/{oid}")
    public ApiResponse<TypeDefinition> get(@PathVariable String oid) {
        try {
            TypeDefinition td = service.findByOid(oid);
            if (td == null) return ApiResponse.fail("类型定义不存在");
            return ApiResponse.ok(td);
        } catch (Exception e) {
            log.error("获取类型详情失败: oid={}", oid, e);
            return ApiResponse.fail(500, "服务器内部错误: " + e.getMessage());
        }
    }

    /** 创建类型定义 */
    @PostMapping
    public ApiResponse<TypeDefinition> create(@RequestBody TypeDefinition td) {
        try {
            TypeDefinition created = service.create(td);
            return ApiResponse.ok(created);
        } catch (IllegalArgumentException e) {
            return ApiResponse.fail(e.getMessage());
        }
    }

    /** 更新类型定义 */
    @PutMapping("/{oid}")
    public ApiResponse<TypeDefinition> update(@PathVariable String oid, @RequestBody TypeDefinition td) {
        td.setOid(oid);
        try {
            TypeDefinition updated = service.update(td);
            return ApiResponse.ok(updated);
        } catch (IllegalArgumentException e) {
            return ApiResponse.fail(e.getMessage());
        }
    }

    /** 删除类型定义 */
    @DeleteMapping("/{oid}")
    public ApiResponse<Void> delete(@PathVariable String oid) {
        try {
            if (service.delete(oid)) {
                return ApiResponse.ok(null);
            }
            return ApiResponse.fail("类型定义不存在");
        } catch (IllegalArgumentException e) {
            return ApiResponse.fail(e.getMessage());
        }
    }
}
