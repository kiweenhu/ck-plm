/*
 * Copyright (c) 2025 深圳乘恺科技有限公司
 * All rights reserved.
 *
 * @author Kiween.Hu; Roney.Liu
 */

package cn.ck.plm.base.service;

import cn.ck.plm.base.util.TenantContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.HashMap;
import java.util.Map;

/**
 * 统一文件存储服务 —— 按租户隔离磁盘目录，提供上传/下载/流式输出能力。
 *
 * <h3>目录结构</h3>
 * <pre>
 * {basePath}/
 *   {tenantOid}/
 *     media/         ← 图册
 *     files/         ← 主文档
 *     cad/           ← 数模
 *     attachments/   ← 附件
 * </pre>
 *
 * <h3>使用方式</h3>
 * <pre>{@code
 * // 上传
 * FileStorageService.StoredFile stored = fileStorage.store(inputStream, originalName, FileCategory.MEDIA);
 * media.setStoragePath(stored.getStoragePath());
 * media.setTenantOid(stored.getTenantOid());
 *
 * // 下载/预览
 * Path filePath = fileStorage.resolvePath(storagePath, tenantOid);
 * byte[] content = Files.readAllBytes(filePath);
 * }</pre>
 */
@Component
public class FileStorageService {

    private static final Logger log = LoggerFactory.getLogger(FileStorageService.class);

    /** 文件分类 */
    public enum FileCategory {
        MEDIA("media"),
        FILES("files"),
        CAD("cad"),
        ATTACHMENTS("attachments");

        private final String dirName;
        FileCategory(String dirName) { this.dirName = dirName; }
        public String getDirName() { return dirName; }
    }

    /** 存储的文件信息 */
    public static class StoredFile {
        private final String storagePath;   // 相对路径，如 /media/{oid}.pdf
        private final String tenantOid;
        private final long fileSize;

        public StoredFile(String storagePath, String tenantOid, long fileSize) {
            this.storagePath = storagePath;
            this.tenantOid = tenantOid;
            this.fileSize = fileSize;
        }
        public String getStoragePath() { return storagePath; }
        public String getTenantOid() { return tenantOid; }
        public long getFileSize() { return fileSize; }
    }

    @Value("${plm.storage.base-path}")
    private String rawBasePath;

    private Path basePath;

    @PostConstruct
    void init() {
        this.basePath = Paths.get(rawBasePath).toAbsolutePath().normalize();
        log.info("文件存储根目录: {}", basePath);
    }

    /**
     * 存储文件到租户隔离目录。
     *
     * @param inputStream 文件输入流
     * @param originalName 原始文件名（含扩展名）
     * @param category 文件分类
     * @param tenantOid 租户 oid（由调用方传入，确保正确性）
     * @return 存储后的文件信息
     */
    public StoredFile store(InputStream inputStream, String originalName, FileCategory category, String tenantOid) throws IOException {

        // 提取扩展名
        String ext = "";
        if (originalName != null) {
            int dot = originalName.lastIndexOf('.');
            if (dot > 0) ext = originalName.substring(dot);
        }

        // 生成存储文件名
        String oid = java.util.UUID.randomUUID().toString();
        String storedName = oid + ext;

        // 确保租户分类目录存在
        Path dir = basePath.resolve(tenantOid).resolve(category.getDirName());
        if (!Files.exists(dir)) {
            Files.createDirectories(dir);
        }

        // 写入文件
        Path dest = dir.resolve(storedName);
        long fileSize = Files.copy(inputStream, dest, StandardCopyOption.REPLACE_EXISTING);

        // 相对路径：/{tenantOid}/{category}/{storedName}，与磁盘目录结构一致
        String storagePath = "/" + tenantOid + "/" + category.getDirName() + "/" + storedName;

        log.debug("文件已存储: tenant={}, category={}, path={}, size={}",
                tenantOid, category, dest, fileSize);

        return new StoredFile(storagePath, tenantOid, fileSize);
    }

    /**
     * 获取租户指定分类的磁盘绝对路径（运行时动态生成，兼容 Windows/Linux）。
     */
    public Path getCategoryPath(String tenantOid, String categoryCode) {
        String dirName = categoryToDirName(categoryCode);
        return basePath.resolve(tenantOid).resolve(dirName).normalize();
    }

    /**
     * 获取租户指定分类的磁盘绝对路径字符串。
     */
    public String getCategoryPathString(String tenantOid, String categoryCode) {
        return getCategoryPath(tenantOid, categoryCode).toString();
    }

    /** 类别编码 → 目录名 */
    public static String categoryToDirName(String categoryCode) {
        switch (categoryCode) {
            case "GALLERY":    return "media";
            case "MAIN_DOC":   return "files";
            case "CAD_MODEL":  return "cad";
            case "ATTACHMENT": return "attachments";
            default:           return categoryCode.toLowerCase();
        }
    }

    /**
     * 根据相对存储路径解析为磁盘绝对路径。
     * <p>storagePath 格式：/{tenantOid}/{category}/{fileName}，与磁盘目录结构一致。
     *
     * @param storagePath 数据库存储的相对路径，如 /{tenantOid}/media/xxx.png
     * @param tenantOid   租户 oid（备用）
     * @return 磁盘绝对路径
     */
    public Path resolvePath(String storagePath, String tenantOid) {
        if (storagePath == null) return null;
        // 去掉开头的 /，然后直接在 basePath 下拼接
        String relative = storagePath.startsWith("/") ? storagePath.substring(1) : storagePath;
        return basePath.resolve(relative).normalize();
    }

    /**
     * 根据文件扩展名解析 MIME 类型。
     * 优先使用上传时浏览器提供的 MIME，若不可靠则按扩展名查表。
     *
     * @param browserMime 浏览器提供的 MIME（可为 null）
     * @param fileName    文件名（用于提取扩展名）
     * @return MIME 类型字符串
     */
    public String resolveContentType(String browserMime, String fileName) {
        // 数据库有明确的非通用 MIME 直接用
        if (browserMime != null && !browserMime.isEmpty()
                && !"application/octet-stream".equals(browserMime)
                && !"application/zip".equals(browserMime)) {
            return browserMime;
        }
        // 按扩展名查表
        if (fileName != null) {
            int dot = fileName.lastIndexOf('.');
            if (dot > -1) {
                String ext = fileName.substring(dot).toLowerCase();
                String mapped = EXT_MIME_MAP.get(ext);
                if (mapped != null) return mapped;
            }
        }
        return "application/octet-stream";
    }

    /** MIME 类型扩展名映射 */
    private static final Map<String, String> EXT_MIME_MAP = new HashMap<>();
    static {
        EXT_MIME_MAP.put(".doc", "application/msword");
        EXT_MIME_MAP.put(".docx", "application/vnd.openxmlformats-officedocument.wordprocessingml.document");
        EXT_MIME_MAP.put(".xls", "application/vnd.ms-excel");
        EXT_MIME_MAP.put(".xlsx", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
        EXT_MIME_MAP.put(".ppt", "application/vnd.ms-powerpoint");
        EXT_MIME_MAP.put(".pptx", "application/vnd.openxmlformats-officedocument.presentationml.presentation");
        EXT_MIME_MAP.put(".pdf", "application/pdf");
        EXT_MIME_MAP.put(".jpg", "image/jpeg");
        EXT_MIME_MAP.put(".jpeg", "image/jpeg");
        EXT_MIME_MAP.put(".png", "image/png");
        EXT_MIME_MAP.put(".gif", "image/gif");
        EXT_MIME_MAP.put(".bmp", "image/bmp");
        EXT_MIME_MAP.put(".webp", "image/webp");
        EXT_MIME_MAP.put(".svg", "image/svg+xml");
        EXT_MIME_MAP.put(".txt", "text/plain");
        EXT_MIME_MAP.put(".csv", "text/csv");
        EXT_MIME_MAP.put(".json", "application/json");
        EXT_MIME_MAP.put(".xml", "application/xml");
        EXT_MIME_MAP.put(".zip", "application/zip");
        EXT_MIME_MAP.put(".rar", "application/x-rar-compressed");
        EXT_MIME_MAP.put(".stp", "application/step");
        EXT_MIME_MAP.put(".step", "application/step");
        EXT_MIME_MAP.put(".stl", "application/sla");
        EXT_MIME_MAP.put(".igs", "application/iges");
        EXT_MIME_MAP.put(".iges", "application/iges");
        EXT_MIME_MAP.put(".dwg", "application/acad");
        EXT_MIME_MAP.put(".dxf", "application/dxf");
        EXT_MIME_MAP.put(".prt", "application/x-ug-part");
        EXT_MIME_MAP.put(".asm", "application/x-ug-assembly");
        EXT_MIME_MAP.put(".catpart", "application/x-catia");
        EXT_MIME_MAP.put(".catproduct", "application/x-catia");
        EXT_MIME_MAP.put(".sldprt", "application/x-solidworks");
        EXT_MIME_MAP.put(".sldasm", "application/x-solidworks");
    }
}
