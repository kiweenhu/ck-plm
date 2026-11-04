/*
 * Copyright (c) 2026~2028 深圳乘恺科技有限公司
 * All rights reserved.
 */

package cn.ck.plm.base.controller;

import cn.ck.plm.base.entity.CKAttachment;
import cn.ck.plm.base.service.FileStorageService;
import cn.ck.plm.base.service.api.CKAttachmentService;
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
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;

/**
 * CKAttachment 附件 REST 控制器。
 * <p>通过 {@link FileStorageService} 统一管理文件存储，支持租户隔离。
 * <p>附件通过 ownerOid 关联 DocumentIteration 等业务对象（1:N）。
 */
@RestController
@RequestMapping("/api/attachments")
public class CKAttachmentController {

    private static final Logger log = LoggerFactory.getLogger(CKAttachmentController.class);

    private final CKAttachmentService attachmentService;
    private final FileStorageService fileStorage;
    private final UserService userService;

    public CKAttachmentController(CKAttachmentService attachmentService, FileStorageService fileStorage, UserService userService) {
        this.attachmentService = attachmentService;
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

    /** 上传附件 */
    @PostMapping("/upload")
    public ApiResponse<CKAttachment> upload(@RequestParam("file") MultipartFile file) {
        if (file.isEmpty()) {
            return ApiResponse.fail(400, "文件不能为空");
        }
        try {
            String originalName = file.getOriginalFilename();
            String tenantOid = getCurrentTenantOid();

            // 统一存储服务：按租户隔离目录
            FileStorageService.StoredFile stored = fileStorage.store(
                    file.getInputStream(), originalName, FileStorageService.FileCategory.ATTACHMENTS, tenantOid);

            CKAttachment att = new CKAttachment();
            att.setOid(UUID.randomUUID().toString());
            att.setFileName(originalName);
            att.setFileSize(stored.getFileSize());
            att.setMimeType(file.getContentType());
            att.setStoragePath(stored.getStoragePath());
            att.setTenantOid(tenantOid);

            attachmentService.create(att);
            log.info("附件上传成功: {} -> {}", originalName, stored.getStoragePath());
            return ApiResponse.ok(att);
        } catch (IOException e) {
            return ApiResponse.fail(500, "文件保存失败: " + e.getMessage());
        }
    }

    /** 查询附件信息 */
    @GetMapping("/{oid}")
    public ApiResponse<CKAttachment> getByOid(@PathVariable String oid) {
        CKAttachment att = attachmentService.findByOid(oid);
        if (att == null) return ApiResponse.fail(404, "附件不存在");
        return ApiResponse.ok(att);
    }

    /** 查询某业务对象的所有附件 */
    @GetMapping("/by-owner/{ownerOid}")
    public ApiResponse<List<CKAttachment>> listByOwner(@PathVariable String ownerOid) {
        return ApiResponse.ok(attachmentService.findByOwner(ownerOid));
    }

    /** 删除附件 */
    @DeleteMapping("/{oid}")
    public ApiResponse<Boolean> delete(@PathVariable String oid) {
        return ApiResponse.ok(attachmentService.delete(oid));
    }

    /**
     * 下载附件。
     *
     * <p>此前只有上传与查询元信息，没有读取通道 —— 变更单上传的补充材料存了 oid 却取不回来。
     * 与 {@link CKFileController#downloadFile} 同一条路：按 storagePath 落到磁盘读字节。
     *
     * <p>文件名按 RFC 5987 编码（{@code filename*=UTF-8''…}）：附件名几乎都是中文，
     * 直接塞进 {@code filename="…"} 会被浏览器截断成乱码。
     */
    @GetMapping("/{oid}/download")
    public ResponseEntity<byte[]> download(@PathVariable String oid) {
        CKAttachment att = attachmentService.findByOid(oid);
        if (att == null) return ResponseEntity.notFound().build();
        try {
            Path filePath = fileStorage.resolvePath(att.getStoragePath(), att.getTenantOid());
            if (filePath == null || !Files.exists(filePath) || !Files.isReadable(filePath)) {
                return ResponseEntity.notFound().build();
            }
            byte[] content = Files.readAllBytes(filePath);
            String contentType = fileStorage.resolveContentType(att.getMimeType(), att.getFileName());
            String fileName = att.getFileName() != null ? att.getFileName() : att.getOid();
            String encoded = URLEncoder.encode(fileName, StandardCharsets.UTF_8).replace("+", "%20");
            return ResponseEntity.ok()
                    .header("Content-Type", contentType)
                    .header("Content-Disposition", "attachment; filename=\"" + encoded + "\"; filename*=UTF-8''" + encoded)
                    .header("Content-Length", String.valueOf(content.length))
                    .header("Cache-Control", "no-cache, no-store, must-revalidate")
                    .body(content);
        } catch (IOException e) {
            log.error("读取附件失败: oid={}", oid, e);
            return ResponseEntity.internalServerError().build();
        }
    }
}
