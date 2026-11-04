/*
 * Copyright (c) 2026~2028 深圳乘恺科技有限公司
 * All rights reserved.
 *
 * @author Kiween.Hu; Roney.Liu
 */

package cn.ck.plm.resource.controller;

import cn.ck.plm.iam.dto.ApiResponse;
import cn.ck.plm.resource.service.api.TechDocService;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

/**
 * 企业资源库-技术文档知识库 REST 控制器。
 *
 * <p>技术文档是<b>通用文档（Document）</b>，创建 / 编辑走 {@code /api/softtype-instances}
 * （root_type_code = DOCUMENT → 落 {@code ck_document}）。本控制器只做两件事：
 * 子库归属上下文、按文件夹列文档。
 *
 * <p>与封装·图符库（{@code /api/package-symbols}）刻意分开：
 * 那边的对象是 EPMDocument（{@code ck_eng_document}），表与属性都不同，
 * 合并成一个接口只会让两边的 SQL 都长出 if 分支。
 */
@RestController
@RequestMapping("/api/tech-docs")
public class TechDocController {

    private final TechDocService techDocService;

    public TechDocController(TechDocService techDocService) {
        this.techDocService = techDocService;
    }

    /** 技术文档库归属上下文（containerOid / containerType / containerCode / containerName / stageOid） */
    @GetMapping("/context")
    public ApiResponse<Map<String, Object>> resourceContext() {
        return ApiResponse.ok(techDocService.getResourceContext());
    }

    /**
     * 技术文档清单。
     *
     * @param folderOid 文件夹 oid（可空 = 该库全部）
     * @param keyword   名称 / 编码 / 主文件名关键字（可空）
     */
    @GetMapping
    public ApiResponse<Map<String, Object>> listItems(
            @RequestParam(required = false) String folderOid,
            @RequestParam(required = false) String keyword) {
        Map<String, Object> result = new HashMap<>();
        result.put("items", techDocService.findItems(folderOid, keyword));
        result.put("context", techDocService.getResourceContext());
        return ApiResponse.ok(result);
    }
}
