/*
 * Copyright (c) 2025 深圳乘恺科技有限公司
 * All rights reserved.
 *
 * @author Kiween.Hu; Roney.Liu
 */

package cn.ck.plm.base.controller;

import cn.ck.plm.base.entity.Media;
import cn.ck.plm.base.service.FileStorageService;
import cn.ck.plm.base.service.api.MediaService;
import cn.ck.plm.base.util.TenantContext;
import cn.ck.plm.base.util.UserContext;
import cn.ck.plm.iam.dto.ApiResponse;
import cn.ck.plm.iam.entity.User;
import cn.ck.plm.iam.service.api.UserService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

/**
 * 图片空间 REST 控制器。
 * <p>通过 {@link FileStorageService} 统一管理文件存储，支持租户隔离。
 * <p>提供图片的上传、查看、搜索、更新描述、删除功能。
 */
@RestController
@RequestMapping("/api/media")
public class MediaController {

    private static final Logger log = LoggerFactory.getLogger(MediaController.class);

    private final MediaService mediaService;
    private final FileStorageService fileStorage;
    private final UserService userService;

    public MediaController(MediaService mediaService, FileStorageService fileStorage, UserService userService) {
        this.mediaService = mediaService;
        this.fileStorage = fileStorage;
        this.userService = userService;
    }

    private String getCurrentTenantOid() {
        String username = UserContext.get();
        if (username != null) {
            User user = userService.findByUsername(username);
            if (user != null && user.getTenantOid() != null) {
                return user.getTenantOid();
            }
        }
        return TenantContext.get();
    }

    /** 上传文件（支持所有格式） */
    @PostMapping("/upload")
    public ApiResponse<Media> upload(@RequestParam("file") MultipartFile file,
                                      @RequestParam(value = "description", required = false) String description) {
        if (file.isEmpty()) {
            return ApiResponse.fail(400, "文件不能为空");
        }
        try {
            String originalName = file.getOriginalFilename();
            String tenantOid = getCurrentTenantOid();

            // 统一存储服务：按租户隔离目录
            FileStorageService.StoredFile stored = fileStorage.store(
                    file.getInputStream(), originalName, FileStorageService.FileCategory.MEDIA, tenantOid);

            Media media = new Media();
            media.setOid(java.util.UUID.randomUUID().toString());
            media.setOriginalName(originalName);
            media.setFileName(stored.getStoragePath().substring(stored.getStoragePath().lastIndexOf('/') + 1));
            media.setFileSize(stored.getFileSize());
            media.setMimeType(file.getContentType());
            media.setStoragePath(stored.getStoragePath());
            media.setDescription(description != null ? description : "");
            media.setTenantOid(tenantOid);

            mediaService.create(media);
            log.info("文件上传成功: {} -> {}", originalName, stored.getStoragePath());
            return ApiResponse.ok(media);
        } catch (IOException e) {
            return ApiResponse.fail("文件保存失败: " + e.getMessage());
        }
    }

    /** 图片列表 / 搜索 */
    @GetMapping
    public ApiResponse<List<Media>> list(@RequestParam(required = false) String keyword) {
        if (keyword != null && !keyword.trim().isEmpty()) {
            return ApiResponse.ok(mediaService.search(keyword));
        }
        return ApiResponse.ok(mediaService.findAll());
    }

    /** 获取图片详情 */
    @GetMapping("/{oid}")
    public ApiResponse<Media> getByOid(@PathVariable String oid) {
        Media media = mediaService.findByOid(oid);
        if (media == null) {
            return ApiResponse.fail(404, "图片记录不存在");
        }
        return ApiResponse.ok(media);
    }

    /** 更新图片描述 */
    @PutMapping("/{oid}")
    public ApiResponse<Media> update(@PathVariable String oid, @RequestBody Media media) {
        try {
            media.setOid(oid);
            return ApiResponse.ok(mediaService.update(media));
        } catch (IllegalArgumentException e) {
            return ApiResponse.fail(400, e.getMessage());
        }
    }

    /** 删除图片（同时删除磁盘文件） */
    @DeleteMapping("/{oid}")
    public ApiResponse<Boolean> delete(@PathVariable String oid) {
        Media media = mediaService.findByOid(oid);
        if (media != null) {
            try {
                Path filePath = fileStorage.resolvePath(media.getStoragePath(), media.getTenantOid());
                if (filePath != null) {
                    Files.deleteIfExists(filePath);
                }
            } catch (IOException e) {
                log.warn("删除磁盘文件失败: oid={}", oid);
            }
        }
        return ApiResponse.ok(mediaService.delete(oid));
    }

    /** 批量检查图片引用状态 */
    @PostMapping("/check-usage")
    public ApiResponse<Map<String, Boolean>> checkUsage(@RequestBody List<String> oids) {
        if (oids == null || oids.isEmpty()) {
            return ApiResponse.ok(java.util.Collections.emptyMap());
        }
        return ApiResponse.ok(mediaService.checkUsage(oids));
    }
}
