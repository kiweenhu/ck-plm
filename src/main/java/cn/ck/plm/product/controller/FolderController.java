/*
 * Copyright (c) 2025 深圳乘恺科技有限公司
 * All rights reserved.
 */

package cn.ck.plm.product.controller;

import cn.ck.plm.iam.dto.ApiResponse;
import cn.ck.plm.product.entity.Folder;
import cn.ck.plm.product.service.api.FolderService;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 文件夹管理 REST 控制器。
 */
@RestController
@RequestMapping("/api/folders")
public class FolderController {

    private final FolderService folderService;

    public FolderController(FolderService folderService) {
        this.folderService = folderService;
    }

    /** 创建文件夹 */
    @PostMapping
    public ApiResponse<Folder> create(@RequestBody Folder folder) {
        try {
            return ApiResponse.ok(folderService.create(folder));
        } catch (IllegalArgumentException e) {
            return ApiResponse.fail(400, e.getMessage());
        }
    }

    /** 更新文件夹 */
    @PutMapping("/{oid}")
    public ApiResponse<Folder> update(@PathVariable String oid, @RequestBody Folder folder) {
        try {
            folder.setOid(oid);
            return ApiResponse.ok(folderService.update(folder));
        } catch (IllegalArgumentException e) {
            return ApiResponse.fail(400, e.getMessage());
        }
    }

    /** 删除文件夹 */
    @DeleteMapping("/{oid}")
    public ApiResponse<Boolean> delete(@PathVariable String oid) {
        return ApiResponse.ok(folderService.delete(oid));
    }

    /** 查询文件夹详情 */
    @GetMapping("/{oid}")
    public ApiResponse<Folder> getByOid(@PathVariable String oid) {
        Folder folder = folderService.findByOid(oid);
        if (folder == null) {
            return ApiResponse.fail(404, "文件夹不存在");
        }
        return ApiResponse.ok(folder);
    }

    /** 查询指定业务对象 + 阶段下的文件夹树 */
    @GetMapping("/tree")
    public ApiResponse<List<Folder>> tree(@RequestParam String ownerOid,
                                            @RequestParam String stageOid) {
        return ApiResponse.ok(folderService.findTree(ownerOid, stageOid));
    }

    /** 查询所有文件夹树（不限定业务对象和阶段） */
    @GetMapping("/all-tree")
    public ApiResponse<List<Folder>> allTree() {
        return ApiResponse.ok(folderService.findAllTree());
    }

    /** 查询指定业务对象 + 阶段下的所有文件夹（扁平列表） */
    @GetMapping
    public ApiResponse<List<Folder>> list(@RequestParam String ownerOid,
                                            @RequestParam String stageOid) {
        return ApiResponse.ok(folderService.findByOwnerAndStage(ownerOid, stageOid));
    }
}
