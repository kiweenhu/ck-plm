/*
 * Copyright (c) 2025 深圳乘恺科技有限公司
 * All rights reserved.
 *
 * @author Kiween.Hu; Roney.Liu
 */

package cn.ck.plm.base.entity;

/**
 * 主文档文件实体，存储主文档的信息。
 *
 * <p>支持两种来源类型：
 * <ul>
 *   <li><b>LOCAL</b> — 本地上传文件（通过 FileUploader 上传，存储物理文件元信息）</li>
 *   <li><b>URL</b>   — 网络资源（用户录入 URL 地址，不存储物理文件）</li>
 * </ul>
 *
 * <p>与 {@code DocumentIteration}（文档子版本）关联，通过 {@code ckfileOid} 建立 1:1 关系。
 * 不同迭代版本可关联不同的主文档文件。
 *
 * <h3>实体关系</h3>
 * <pre>
 * DocumentIteration  1 ── 1  CKFile  (通过 ckfileOid 关联，该版本主文档文件)
 * </pre>
 */
public class CKFile extends BaseEntity implements TenantEntity {

    // ==================== 来源类型常量 ====================
    public static final String SOURCE_LOCAL = "LOCAL";
    public static final String SOURCE_URL   = "URL";

    /** 来源类型：LOCAL（本地上传）或 URL（网络资源） */
    private String sourceType;

    /** 网络资源 URL（sourceType = URL 时使用） */
    private String sourceUrl;

    /** 原始文件名（含扩展名，LOCAL 模式使用，如 BOM_A.1.xlsx） */
    private String fileName;

    /** 文件大小（字节，LOCAL 模式使用） */
    private Long fileSize;

    /** 文件存储路径（服务器相对路径，LOCAL 模式使用） */
    private String storagePath;

    /** 文件 MIME 类型（LOCAL 模式自动识别，URL 模式用户可选填） */
    private String mimeType;

    /** 租户 oid（引用 ck_tenant.oid） */
    private String tenantOid;

    // ==================== 构造方法 ====================

    public CKFile() {
        super();
        this.sourceType = SOURCE_LOCAL;
    }

    // ==================== 便捷方法 ====================

    public boolean isLocal() { return SOURCE_LOCAL.equals(sourceType); }
    public boolean isUrl()   { return SOURCE_URL.equals(sourceType); }

    /** 获取显示名称：LOCAL 取 fileName，URL 取 sourceUrl */
    public String getDisplayName() {
        if (isUrl() && sourceUrl != null) {
            // URL 模式显示域名 + 路径简写
            try {
                java.net.URI uri = new java.net.URI(sourceUrl);
                String host = uri.getHost();
                String path = uri.getPath();
                if (path != null && path.length() > 40) path = "…" + path.substring(path.length() - 35);
                return host != null ? host + (path != null ? path : "") : sourceUrl;
            } catch (Exception e) {
                return sourceUrl.length() > 60 ? sourceUrl.substring(0, 57) + "…" : sourceUrl;
            }
        }
        return fileName;
    }

    // ==================== Getter / Setter ====================

    public String getSourceType() { return sourceType; }
    public void setSourceType(String sourceType) { this.sourceType = sourceType; }

    public String getSourceUrl() { return sourceUrl; }
    public void setSourceUrl(String sourceUrl) { this.sourceUrl = sourceUrl; }

    public String getFileName() { return fileName; }
    public void setFileName(String fileName) { this.fileName = fileName; }

    public Long getFileSize() { return fileSize; }
    public void setFileSize(Long fileSize) { this.fileSize = fileSize; }

    public String getStoragePath() { return storagePath; }
    public void setStoragePath(String storagePath) { this.storagePath = storagePath; }

    public String getMimeType() { return mimeType; }
    public void setMimeType(String mimeType) { this.mimeType = mimeType; }

    @Override
    public String getTenantOid() { return tenantOid; }

    @Override
    public void setTenantOid(String tenantOid) { this.tenantOid = tenantOid; }

    @Override
    public String toString() {
        return "CKFile{oid='" + getOid() + "', sourceType=" + sourceType +
                ", displayName='" + getDisplayName() + "'}";
    }
}
