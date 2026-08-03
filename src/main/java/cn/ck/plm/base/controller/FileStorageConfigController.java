/*
 * Copyright (c) 2025 深圳乘恺科技有限公司
 * All rights reserved.
 */
package cn.ck.plm.base.controller;

import cn.ck.plm.base.dto.FileStorageSummaryVO;
import cn.ck.plm.base.entity.FileStorageConfig;
import cn.ck.plm.base.service.FileStorageConfigService;
import cn.ck.plm.iam.dto.ApiResponse;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 文件存储配置 API。
 */
@RestController
@RequestMapping("/api/file-storage")
public class FileStorageConfigController {

    private final FileStorageConfigService service;

    public FileStorageConfigController(FileStorageConfigService service) {
        this.service = service;
    }

    @GetMapping
    public ApiResponse<List<FileStorageConfig>> list() {
        return ApiResponse.ok(service.listAll());
    }

    @GetMapping("/{oid}")
    public ApiResponse<FileStorageConfig> get(@PathVariable String oid) {
        FileStorageConfig cfg = service.getByOid(oid);
        if (cfg == null) return ApiResponse.fail(404, "配置不存在");
        return ApiResponse.ok(cfg);
    }

    @PostMapping
    public ApiResponse<FileStorageConfig> create(@RequestBody FileStorageConfig config) {
        return ApiResponse.ok(service.create(config));
    }

    @PutMapping("/{oid}")
    public ApiResponse<FileStorageConfig> update(@PathVariable String oid, @RequestBody FileStorageConfig config) {
        config.setOid(oid);
        return ApiResponse.ok(service.update(config));
    }

    @DeleteMapping("/{oid}")
    public ApiResponse<Void> delete(@PathVariable String oid) {
        service.delete(oid);
        return ApiResponse.ok();
    }

    /** 存储统计汇总 */
    @GetMapping("/summary")
    public ApiResponse<List<FileStorageSummaryVO>> summary() {
        return ApiResponse.ok(service.getSummary());
    }

    /** 检查存储告警 */
    @GetMapping("/alerts")
    public ApiResponse<List<String>> alerts() {
        return ApiResponse.ok(service.checkStorageAlerts());
    }
}
