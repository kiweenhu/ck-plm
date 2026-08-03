/*
 * Copyright (c) 2025 深圳乘恺科技有限公司
 * All rights reserved.
 *
 * @author Kiween.Hu; Roney.Liu
 */

package cn.ck.plm.functional.controller;

import cn.ck.plm.iam.dto.ApiResponse;
import cn.ck.plm.functional.dto.FunctionalVO;
import cn.ck.plm.functional.entity.FunctionalEntity;
import cn.ck.plm.functional.service.api.FunctionalService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * System（系统/功能系统）REST API 控制器。
 */
@RestController
@RequestMapping("/api/functionals")
public class FunctionalController {

    private final FunctionalService FunctionalService;
    private final ObjectMapper objectMapper;

    public FunctionalController(FunctionalService FunctionalService, ObjectMapper objectMapper) {
        this.FunctionalService = FunctionalService;
        this.objectMapper = objectMapper;
    }

    @PostMapping
    public ApiResponse<FunctionalEntity> create(@RequestBody Map<String, Object> body) {
        try {
            FunctionalEntity entity = objectMapper.convertValue(body, FunctionalEntity.class);
            String ckfileOid = getString(body, "ckfileOid");
            String attachmentOid = getString(body, "attachmentOid");
            FunctionalEntity created = FunctionalService.create(entity, ckfileOid, attachmentOid);
            return ApiResponse.ok(created);
        } catch (IllegalArgumentException e) {
            return ApiResponse.fail(400, e.getMessage());
        } catch (Exception e) {
            return ApiResponse.fail(500, "创建系统失败: " + e.getMessage());
        }
    }

    private String getString(Map<String, Object> body, String key) {
        Object val = body.get(key);
        return val != null && !"".equals(val) ? val.toString() : null;
    }

    @PutMapping("/{oid}")
    public ApiResponse<FunctionalEntity> update(@PathVariable String oid, @RequestBody FunctionalEntity entity) {
        entity.setOid(oid);
        return ApiResponse.ok(FunctionalService.update(entity));
    }

    @DeleteMapping("/{oid}")
    public ApiResponse<Void> delete(@PathVariable String oid) {
        FunctionalService.delete(oid);
        return ApiResponse.ok();
    }

    @GetMapping("/{oid}")
    public ApiResponse<FunctionalEntity> getByOid(@PathVariable String oid) {
        return ApiResponse.ok(FunctionalService.findByOid(oid));
    }

    @GetMapping("/by-folder")
    public ApiResponse<List<FunctionalVO>> listByFolder(@RequestParam String folderOid) {
        return ApiResponse.ok(FunctionalService.findVOsByFolder(folderOid));
    }

    @GetMapping("/by-container")
    public ApiResponse<List<FunctionalEntity>> listByContainer(@RequestParam String containerOid) {
        return ApiResponse.ok(FunctionalService.findByContainerOid(containerOid));
    }
}
