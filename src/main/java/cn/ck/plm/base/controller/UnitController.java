/*
 * Copyright (c) 2025 深圳乘恺科技有限公司
 * All rights reserved.
 *
 * @author Kiween.Hu; Roney.Liu
 */

package cn.ck.plm.base.controller;

import cn.ck.plm.base.entity.QuantityType;
import cn.ck.plm.base.entity.Unit;
import cn.ck.plm.base.service.api.UnitService;
import cn.ck.plm.iam.dto.ApiResponse;
import org.springframework.web.bind.annotation.*;

import java.util.*;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/units")
public class UnitController {

    private final UnitService service;

    public UnitController(UnitService service) {
        this.service = service;
    }

    /** 获取所有量纲类型 */
    @GetMapping("/quantity-types")
    public ApiResponse<List<Map<String, Object>>> getQuantityTypes() {
        List<Map<String, Object>> list = Arrays.stream(QuantityType.values())
                .map(qt -> {
                    Map<String, Object> m = new HashMap<>();
                    m.put("name", qt.name());
                    m.put("displayName", qt.getDisplayName());
                    m.put("baseUnitName", qt.getBaseUnitName());
                    m.put("typicalUnits", qt.getTypicalUnits());
                    return m;
                })
                .collect(Collectors.toList());
        return ApiResponse.ok(list);
    }

    /** 查询所有单位（按量纲分组） */
    @GetMapping
    public ApiResponse<Map<String, List<Unit>>> listAll(@RequestParam(defaultValue = "true") boolean grouped) {
        if (grouped) {
            return ApiResponse.ok(service.listAllGrouped());
        }
        return ApiResponse.ok(service.listAll().stream()
                .collect(Collectors.groupingBy(u -> "all", LinkedHashMap::new, Collectors.toList())));
    }

    /** 按量纲查询 */
    @GetMapping("/by-quantity-type")
    public ApiResponse<List<Unit>> listByQuantityType(@RequestParam String quantityType) {
        return ApiResponse.ok(service.listByQuantityType(quantityType));
    }

    /** 创建单位 */
    @PostMapping
    public ApiResponse<Unit> create(@RequestBody Unit unit) {
        try {
            return ApiResponse.ok(service.create(unit));
        } catch (IllegalArgumentException e) {
            return ApiResponse.fail(400, e.getMessage());
        }
    }

    /** 更新单位 */
    @PutMapping("/{oid}")
    public ApiResponse<Unit> update(@PathVariable String oid, @RequestBody Unit unit) {
        try {
            return ApiResponse.ok(service.update(oid, unit));
        } catch (IllegalArgumentException e) {
            return ApiResponse.fail(400, e.getMessage());
        }
    }

    /** 删除单位 */
    @DeleteMapping("/{oid}")
    public ApiResponse<Void> delete(@PathVariable String oid) {
        try {
            service.delete(oid);
            return ApiResponse.ok();
        } catch (IllegalArgumentException e) {
            return ApiResponse.fail(400, e.getMessage());
        }
    }

    /** 换算因子 */
    @GetMapping("/convert")
    public ApiResponse<Double> convert(@RequestParam String from, @RequestParam String to) {
        try {
            return ApiResponse.ok(service.convertFactor(from, to));
        } catch (IllegalArgumentException e) {
            return ApiResponse.fail(400, e.getMessage());
        }
    }
}
