/*
 * Copyright (c) 2025 深圳乘恺科技有限公司
 * All rights reserved.
 *
 * @author Kiween.Hu; Roney.Liu
 */

package cn.ck.plm.softtype.controller;

import cn.ck.plm.iam.dto.ApiResponse;
import cn.ck.plm.softtype.entity.AttributeDefinition;
import cn.ck.plm.softtype.service.api.AttributeDefinitionService;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 实体属性定义 REST 控制器。
 */
@RestController
@RequestMapping("/api/attribute-definitions")
public class AttributeDefinitionController {

    private final AttributeDefinitionService service;

    public AttributeDefinitionController(AttributeDefinitionService service) {
        this.service = service;
    }

    /** 获取某实体的属性定义列表（按 sort_order 排序），支持动态合并 IBA 属性 */
    @GetMapping
    public ApiResponse<List<AttributeDefinition>> listByEntity(
            @RequestParam String entityName,
            @RequestParam(required = false) String entityOid,
            @RequestParam(required = false) String entityType) {
        if (entityOid != null && !entityOid.isEmpty()
                && entityType != null && !entityType.isEmpty()) {
            return ApiResponse.ok(service.findByEntity(entityName, entityOid, entityType));
        }
        return ApiResponse.ok(service.findByEntityName(entityName));
    }

    /** 更新单个属性定义布局配置 */
    @PutMapping("/{oid}")
    public ApiResponse<AttributeDefinition> update(@PathVariable String oid,
                                                    @RequestBody AttributeDefinition def) {
        def.setOid(oid);
        AttributeDefinition updated = service.update(def);
        if (updated == null) return ApiResponse.fail("属性定义不存在");
        return ApiResponse.ok(updated);
    }

    /** 批量更新布局配置 */
    @PutMapping("/batch-layout")
    public ApiResponse<Integer> batchUpdateLayout(@RequestBody List<AttributeDefinition> defs) {
        int count = service.batchUpdateLayout(defs);
        return ApiResponse.ok(count);
    }
}
