/*
 * Copyright (c) 2025 深圳乘恺科技有限公司
 * All rights reserved.
 *
 * @author Kiween.Hu; Roney.Liu
 */

package cn.ck.plm.base.controller;

import cn.ck.plm.base.entity.CKFile;
import cn.ck.plm.base.service.FileStorageService;
import cn.ck.plm.base.service.api.CKFileService;
import cn.ck.plm.base.util.TenantContext;
import cn.ck.plm.base.util.UserContext;
import cn.ck.plm.iam.dto.ApiResponse;
import cn.ck.plm.iam.entity.User;
import cn.ck.plm.iam.service.api.UserService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.UUID;

/**
 * CKFile 主文档文件 REST 控制器。
 * <p>通过 {@link FileStorageService} 统一管理文件存储，支持租户隔离。
 * <p>支持 LOCAL 上传和 URL 录入两种来源类型。
 */
@RestController
@RequestMapping("/api/ckfiles")
public class CKFileController {

    private static final Logger log = LoggerFactory.getLogger(CKFileController.class);

    private final CKFileService ckFileService;
    private final FileStorageService fileStorage;
    private final UserService userService;

    public CKFileController(CKFileService ckFileService, FileStorageService fileStorage, UserService userService) {
        this.ckFileService = ckFileService;
        this.fileStorage = fileStorage;
        this.userService = userService;
    }

    /** 从当前用户获取租户 oid */
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

    /** 上传主文档文件（LOCAL 模式） */
    @PostMapping("/upload")
    public ApiResponse<CKFile> upload(@RequestParam("file") MultipartFile file) {
        if (file.isEmpty()) return ApiResponse.fail(400, "文件不能为空");
        try {
            String originalName = file.getOriginalFilename();
            String tenantOid = getCurrentTenantOid();

            // 统一存储服务：按租户隔离目录
            FileStorageService.StoredFile stored = fileStorage.store(
                    file.getInputStream(), originalName, FileStorageService.FileCategory.FILES, tenantOid);

            CKFile ckFile = new CKFile();
            ckFile.setOid(UUID.randomUUID().toString());
            ckFile.setSourceType(CKFile.SOURCE_LOCAL);
            ckFile.setFileName(originalName);
            ckFile.setFileSize(stored.getFileSize());
            ckFile.setMimeType(file.getContentType());
            ckFile.setStoragePath(stored.getStoragePath());
            ckFile.setTenantOid(tenantOid);

            ckFileService.create(ckFile);
            log.info("文档文件上传成功(LOCAL): {} -> {}", originalName, stored.getStoragePath());
            return ApiResponse.ok(ckFile);
        } catch (IOException e) {
            return ApiResponse.fail(500, "文件保存失败: " + e.getMessage());
        }
    }

    /** 创建网络资源主文档（URL 模式） */
    @PostMapping("/url")
    public ApiResponse<CKFile> createFromUrl(@RequestBody Map<String, String> body) {
        String url = body.get("url");
        if (url == null || url.trim().isEmpty()) {
            return ApiResponse.fail(400, "URL 不能为空");
        }
        CKFile ckFile = new CKFile();
        ckFile.setOid(UUID.randomUUID().toString());
        ckFile.setSourceType(CKFile.SOURCE_URL);
        ckFile.setSourceUrl(url.trim());
        ckFile.setFileName(body.getOrDefault("fileName", url.trim()));
        ckFile.setTenantOid(getCurrentTenantOid());

        ckFileService.create(ckFile);
        log.info("主文档 URL 创建成功: {}", url);
        return ApiResponse.ok(ckFile);
    }

    /** 查询文件信息 */
    @GetMapping("/{oid}")
    public ApiResponse<CKFile> getByOid(@PathVariable String oid) {
        CKFile file = ckFileService.findByOid(oid);
        if (file == null) return ApiResponse.fail(404, "文件记录不存在");
        return ApiResponse.ok(file);
    }

    /** 流式输出文件内容（用于在线预览） */
    @GetMapping("/{oid}/stream")
    public ResponseEntity<byte[]> streamFile(@PathVariable String oid) {
        CKFile file = ckFileService.findByOid(oid);
        if (file == null) return ResponseEntity.notFound().build();

        // URL 类型：重定向
        if (CKFile.SOURCE_URL.equals(file.getSourceType())) {
            if (file.getSourceUrl() != null && !file.getSourceUrl().isEmpty()) {
                return ResponseEntity.status(302)
                        .header("Location", file.getSourceUrl()).build();
            }
            return ResponseEntity.notFound().build();
        }

        return readAndRespond(file, true);
    }

    /** 下载文件（浏览器触发下载） */
    @GetMapping("/{oid}/download")
    public ResponseEntity<byte[]> downloadFile(@PathVariable String oid) {
        CKFile file = ckFileService.findByOid(oid);
        if (file == null) return ResponseEntity.notFound().build();

        // URL 类型：重定向
        if (CKFile.SOURCE_URL.equals(file.getSourceType())) {
            if (file.getSourceUrl() != null && !file.getSourceUrl().isEmpty()) {
                return ResponseEntity.status(302)
                        .header("Location", file.getSourceUrl()).build();
            }
            return ResponseEntity.notFound().build();
        }

        return readAndRespond(file, false);
    }

    private ResponseEntity<byte[]> readAndRespond(CKFile file, boolean inline) {
        try {
            Path filePath = fileStorage.resolvePath(file.getStoragePath(), file.getTenantOid());
            if (filePath == null || !Files.exists(filePath) || !Files.isReadable(filePath)) {
                return ResponseEntity.notFound().build();
            }

            byte[] content = Files.readAllBytes(filePath);
            String contentType = fileStorage.resolveContentType(file.getMimeType(), file.getFileName());
            String disposition = inline ? "inline" : "attachment";
            String fileName = file.getFileName() != null ? file.getFileName() : file.getOid();

            return ResponseEntity.ok()
                    .header("Content-Type", contentType)
                    .header("Content-Disposition", disposition + "; filename=\"" + fileName + "\"")
                    .header("Content-Length", String.valueOf(content.length))
                    .header("Cache-Control", "no-cache, no-store, must-revalidate")
                    .header("Pragma", "no-cache")
                    .header("Expires", "0")
                    .body(content);
        } catch (IOException e) {
            log.error("读取文件失败: oid={}", file.getOid(), e);
            return ResponseEntity.internalServerError().build();
        }
    }
}
