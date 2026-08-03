/*
 * Copyright (c) 2025 深圳乘恺科技有限公司
 * All rights reserved.
 *
 * @author Kiween.Hu; Roney.Liu
 */

package cn.ck.plm.softtype.controller;

import cn.ck.plm.iam.dto.ApiResponse;
import cn.ck.plm.softtype.entity.IBA;
import cn.ck.plm.softtype.entity.TypeIBA;
import cn.ck.plm.softtype.service.api.IBADataService;
import cn.ck.plm.softtype.service.api.IBAService;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * IBA（可互换属性）管理 REST 控制器。
 *
 * <p>提供 IBA CRUD 以及类型与 IBA 的关联映射管理。
 */
@RestController
@RequestMapping("/api/ibas")
public class IBAController {

    private final IBAService service;
    private final IBADataService dataService;

    public IBAController(IBAService service, IBADataService dataService) {
        this.service = service;
        this.dataService = dataService;
    }

    // ==================== IBA CRUD ====================

    /** IBA 列表 / 搜索 */
    @GetMapping
    public ApiResponse<List<IBA>> list(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false, defaultValue = "false") boolean enabledOnly) {
        if (keyword != null && !keyword.trim().isEmpty()) {
            return ApiResponse.ok(service.search(keyword));
        }
        return ApiResponse.ok(enabledOnly ? service.findEnabled() : service.findAll());
    }

    /** IBA 详情 */
    @GetMapping("/{oid}")
    public ApiResponse<IBA> getByOid(@PathVariable String oid) {
        IBA iba = service.findByOid(oid);
        if (iba == null) return ApiResponse.fail(404, "IBA 不存在");
        return ApiResponse.ok(iba);
    }

    /** 创建 IBA */
    @PostMapping
    public ApiResponse<IBA> create(@RequestBody IBA iba) {
        try {
            return ApiResponse.ok(service.create(iba));
        } catch (IllegalArgumentException e) {
            return ApiResponse.fail(400, e.getMessage());
        }
    }

    /** 更新 IBA */
    @PutMapping("/{oid}")
    public ApiResponse<IBA> update(@PathVariable String oid, @RequestBody IBA iba) {
        try {
            iba.setOid(oid);
            return ApiResponse.ok(service.update(iba));
        } catch (IllegalArgumentException e) {
            return ApiResponse.fail(400, e.getMessage());
        }
    }

    /** 删除 IBA */
    @DeleteMapping("/{oid}")
    public ApiResponse<Boolean> delete(@PathVariable String oid) {
        return ApiResponse.ok(service.delete(oid));
    }

    // ==================== 类型-IBA 映射管理 ====================

    /** 查询某类型下的所有 IBA 映射（含 IBA 详情），支持 entityCode */
    @GetMapping("/mappings")
    public ApiResponse<List<TypeIBA>> listMappings(
            @RequestParam String typeOid,
            @RequestParam(required = false) String entityCode) {
        if (entityCode != null && !entityCode.isEmpty()) {
            return ApiResponse.ok(service.findMappingsByOwner(typeOid, entityCode));
        }
        return ApiResponse.ok(service.findMappingsByTypeOid(typeOid));
    }

    /** 查询某类型下直接关联的 IBA 列表 */
    @GetMapping("/types/{typeOid}")
    public ApiResponse<List<IBA>> listByType(@PathVariable String typeOid) {
        return ApiResponse.ok(service.findIbasByTypeOid(typeOid));
    }

    /** 查询尚未分配给某类型的可用 IBA，支持 entityCode */
    @GetMapping("/unassigned")
    public ApiResponse<List<IBA>> listUnassigned(
            @RequestParam String typeOid,
            @RequestParam(required = false) String entityCode,
            @RequestParam(required = false) String keyword) {
        if (entityCode != null && !entityCode.isEmpty()) {
            return ApiResponse.ok(service.findUnassignedIbas(typeOid, entityCode, keyword));
        }
        return ApiResponse.ok(service.findUnassignedIbas(typeOid, keyword));
    }

    /** 为类型分配 IBA */
    @PostMapping("/mappings")
    public ApiResponse<TypeIBA> assignIba(@RequestBody TypeIBA mapping) {
        try {
            return ApiResponse.ok(service.assignIba(mapping));
        } catch (IllegalArgumentException e) {
            return ApiResponse.fail(400, e.getMessage());
        }
    }

    /** 批量分配 IBA，支持 entityCode */
    @PostMapping("/batch-assign")
    public ApiResponse<List<TypeIBA>> batchAssign(@RequestBody Map<String, Object> body) {
        try {
            String typeOid = (String) body.get("typeOid");
            String entityCode = (String) body.get("entityCode");
            @SuppressWarnings("unchecked")
            List<String> ibaOids = (List<String>) body.get("ibaOids");
            if (entityCode != null && !entityCode.isEmpty()) {
                return ApiResponse.ok(service.batchAssign(typeOid, entityCode, ibaOids));
            }
            return ApiResponse.ok(service.batchAssign(typeOid, ibaOids));
        } catch (Exception e) {
            return ApiResponse.fail(400, e.getMessage());
        }
    }

    /** 更新映射（覆写 required / defaultValue） */
    @PutMapping("/mappings/{mappingOid}")
    public ApiResponse<TypeIBA> updateMapping(@PathVariable String mappingOid,
                                                   @RequestBody TypeIBA mapping) {
        try {
            mapping.setOid(mappingOid);
            return ApiResponse.ok(service.updateMapping(mapping));
        } catch (IllegalArgumentException e) {
            return ApiResponse.fail(400, e.getMessage());
        }
    }

    /** 移除类型上的某个 IBA 关联 */
    @DeleteMapping("/mappings/{mappingOid}")
    public ApiResponse<Boolean> removeMapping(@PathVariable String mappingOid) {
        return ApiResponse.ok(service.removeMapping(mappingOid));
    }

    /** 查询某 IBA 被哪些类型使用 */
    @GetMapping("/{ibaOid}/types")
    public ApiResponse<List<TypeIBA>> listTypesByIba(@PathVariable String ibaOid) {
        return ApiResponse.ok(service.findTypesByIbaOid(ibaOid));
    }

    /** 递归查询类型继承链上祖先类型的 IBA 映射（不含自身），支持 entityCode */
    @GetMapping("/inherited")
    public ApiResponse<List<TypeIBA>> listInherited(
            @RequestParam String typeOid,
            @RequestParam(required = false) String entityCode) {
        if (entityCode != null && !entityCode.isEmpty()) {
            return ApiResponse.ok(service.findInheritedMappingsByOwner(typeOid, entityCode));
        }
        return ApiResponse.ok(service.findInheritedMappings(typeOid));
    }

    /** 查询某实体的 IBA 属性值 */
    @GetMapping("/data")
    public ApiResponse<Map<String, Object>> getEntityIbaData(
            @RequestParam String entityType,
            @RequestParam String entityOid) {
        Map<String, Object> values = dataService.getValues(entityType, entityOid);
        return ApiResponse.ok(values);
    }
}
