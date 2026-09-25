/*
 * Copyright (c) 2025 深圳乘恺科技有限公司
 * All rights reserved.
 *
 * @author Kiween.Hu; Roney.Liu
 */

package cn.ck.plm.resource.controller;

import cn.ck.plm.iam.dto.ApiResponse;
import cn.ck.plm.resource.service.api.PackageSymbolService;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

/**
 * 企业资源库-封装·图符库 REST 控制器。
 *
 * <p>封装（FOOTPRINT）与图符（SYMBOL）为 PART 的软类型（创建 / 编辑走 /api/parts），
 * 本控制器负责：封装·图符库归属上下文、按文件夹查询封装 / 图符清单。
 */
@RestController
@RequestMapping("/api/package-symbols")
public class PackageSymbolController {

    private final PackageSymbolService packageSymbolService;

    public PackageSymbolController(PackageSymbolService packageSymbolService) {
        this.packageSymbolService = packageSymbolService;
    }

    /** 封装·图符归属上下文（containerOid/containerType/containerCode/containerName/stageOid） */
    @GetMapping("/context")
    public ApiResponse<Map<String, Object>> resourceContext() {
        return ApiResponse.ok(packageSymbolService.getResourceContext());
    }

    /**
     * 封装 / 图符清单：按文件夹查询 FOOTPRINT / SYMBOL 软类型的 Part。
     *
     * @param folderOid 文件夹 oid（可空 = 全部）
     * @param typeCode  FOOTPRINT / SYMBOL（可空 = 两者都查）
     * @param keyword   名称或编码关键字（可空）
     */
    @GetMapping
    public ApiResponse<Map<String, Object>> listItems(
            @RequestParam(required = false) String folderOid,
            @RequestParam(required = false) String typeCode,
            @RequestParam(required = false) String keyword) {
        Map<String, Object> result = new HashMap<>();
        result.put("items", packageSymbolService.findItems(folderOid, typeCode, keyword));
        result.put("context", packageSymbolService.getResourceContext());
        return ApiResponse.ok(result);
    }
}
