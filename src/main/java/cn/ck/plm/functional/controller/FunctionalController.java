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
import cn.ck.plm.softtype.dto.SoftTypeInstanceResult;
import cn.ck.plm.softtype.service.api.SoftTypeInstanceService;
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
    private final SoftTypeInstanceService softTypeInstanceService;

    /** 能力宿主 code（与 TypeDefinition.rootTypeCode 一致） */
    private static final String HOST = "FUNCTIONAL";

    public FunctionalController(FunctionalService FunctionalService,
                                SoftTypeInstanceService softTypeInstanceService) {
        this.FunctionalService = FunctionalService;
        this.softTypeInstanceService = softTypeInstanceService;
    }

    /**
     * 创建功能架构（系统）。
     *
     * <p>创建编排已收敛到 {@code FunctionalInstanceCreator}，本方法复用同一实现。
     */
    @PostMapping
    public ApiResponse<FunctionalEntity> create(@RequestBody Map<String, Object> body) {
        try {
            Object typeCode = body.get("typeDefinitionCode");
            SoftTypeInstanceResult result = softTypeInstanceService.createForHost(
                    HOST, typeCode != null ? typeCode.toString() : HOST, body);
            return ApiResponse.ok((FunctionalEntity) result.getEntity());
        } catch (IllegalArgumentException e) {
            return ApiResponse.fail(400, e.getMessage());
        } catch (Exception e) {
            return ApiResponse.fail(500, "创建系统失败: " + e.getMessage());
        }
    }

    /**
     * 更新功能架构（系统）。
     *
     * <p>更新编排已收敛到 {@code FunctionalServiceImpl#updateInstance}，与统一入口共用同一实现。
     */
    @PutMapping("/{oid}")
    public ApiResponse<FunctionalEntity> update(@PathVariable String oid, @RequestBody Map<String, Object> body) {
        try {
            Object entity = softTypeInstanceService.updateForHost(HOST, oid, body);
            return ApiResponse.ok((FunctionalEntity) entity);
        } catch (IllegalArgumentException e) {
            return ApiResponse.fail(404, e.getMessage());
        } catch (UnsupportedOperationException e) {
            return ApiResponse.fail(501, e.getMessage());
        } catch (Exception e) {
            return ApiResponse.fail(500, "更新系统失败: " + e.getMessage());
        }
    }

    @DeleteMapping("/{oid}")
    public ApiResponse<Void> delete(@PathVariable String oid) {
        FunctionalService.delete(oid);
        return ApiResponse.ok();
    }

    /**
     * 按 OID 查询功能架构（系统）。
     *
     * <p>读取编排已收敛到 {@code FunctionalInstanceCreator#get}，与统一入口共用同一实现。
     */
    @GetMapping("/{oid}")
    public ApiResponse<FunctionalEntity> getByOid(@PathVariable String oid) {
        Object entity = softTypeInstanceService.getForHost(HOST, oid, null);
        if (entity == null) {
            return ApiResponse.fail(404, "系统不存在: " + oid);
        }
        return ApiResponse.ok((FunctionalEntity) entity);
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
