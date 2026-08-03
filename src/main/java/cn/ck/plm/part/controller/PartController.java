/*
 * Copyright (c) 2025 深圳乘恺科技有限公司
 * All rights reserved.
 *
 * @author Kiween.Hu; Roney.Liu
 */

package cn.ck.plm.part.controller;

import cn.ck.plm.iam.dto.ApiResponse;
import cn.ck.plm.part.dto.PartVO;
import cn.ck.plm.part.entity.Part;
import cn.ck.plm.part.service.api.PartService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * Part 零组件 REST API 控制器。
 */
@RestController
@RequestMapping("/api/parts")
public class PartController {

    private final PartService partService;
    private final ObjectMapper objectMapper;

    public PartController(PartService partService, ObjectMapper objectMapper) {
        this.partService = partService;
        this.objectMapper = objectMapper;
    }

    /** 创建零组件 */
    @PostMapping
    public ApiResponse<Part> create(@RequestBody Map<String, Object> body) {
        try {
            Part part = objectMapper.convertValue(body, Part.class);
            String ckfileOid = getString(body, "ckfileOid");
            String attachmentOid = getString(body, "attachmentOid");
            Part created = partService.create(part, ckfileOid, attachmentOid);
            return ApiResponse.ok(created);
        } catch (IllegalArgumentException e) {
            return ApiResponse.fail(400, e.getMessage());
        } catch (Exception e) {
            return ApiResponse.fail(500, "创建零组件失败: " + e.getMessage());
        }
    }

    private String getString(Map<String, Object> body, String key) {
        Object val = body.get(key);
        return val != null && !"".equals(val) ? val.toString() : null;
    }

    /** 更新零组件 */
    @PutMapping("/{oid}")
    public ApiResponse<Part> update(@PathVariable String oid, @RequestBody Part part) {
        part.setOid(oid);
        return ApiResponse.ok(partService.update(part));
    }

    /** 删除零组件 */
    @DeleteMapping("/{oid}")
    public ApiResponse<Void> delete(@PathVariable String oid) {
        partService.delete(oid);
        return ApiResponse.ok();
    }

    /** 按 OID 查询 */
    @GetMapping("/{oid}")
    public ApiResponse<Part> getByOid(@PathVariable String oid) {
        return ApiResponse.ok(partService.findByOid(oid));
    }

    /** 按文件夹查询 VO */
    @GetMapping("/by-folder")
    public ApiResponse<List<PartVO>> listByFolder(@RequestParam String folderOid) {
        return ApiResponse.ok(partService.findVOsByFolder(folderOid));
    }

    /** 按容器查询 */
    @GetMapping("/by-container")
    public ApiResponse<List<Part>> listByContainer(@RequestParam String containerOid) {
        return ApiResponse.ok(partService.findByContainerOid(containerOid));
    }
}
