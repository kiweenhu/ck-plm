/*
 * Copyright (c) 2025 深圳乘恺科技有限公司
 * All rights reserved.
 *
 * @author Kiween.Hu; Roney.Liu
 */

package cn.ck.plm.base.entity;

/**
 * 图片空间实体（Media），用于存储和复用图片资源。
 *
 * <p>产品线缩略图、企业资源图等可通过 media_oid 引用此实体，
 * 实现图片的统一管理和复用。
 *
 * <h3>继承链条</h3>
 * <pre>
 * BaseEntity → Media(this)
 * </pre>
 */
public class Media extends BaseEntity implements TenantEntity {

    /** 原始文件名（含扩展名） */
    private String originalName;

    /** 存储文件名（{oid}.{ext}） */
    private String fileName;

    /** 文件大小（字节） */
    private Long fileSize;

    /** MIME 类型 */
    private String mimeType;

    /** 存储路径（相对路径） */
    private String storagePath;

    /** 图片描述/用途说明 */
    private String description;

    /** 图片宽度（像素） */
    private Integer width;

    /** 图片高度（像素） */
    private Integer height;

    /** 租户 oid（引用 ck_tenant.oid） */
    private String tenantOid;

    // ==================== 构造方法 ====================

    public Media() {
        super();
    }

    // ==================== Getter / Setter ====================

    public String getOriginalName() { return originalName; }
    public void setOriginalName(String originalName) { this.originalName = originalName; }

    public String getFileName() { return fileName; }
    public void setFileName(String fileName) { this.fileName = fileName; }

    public Long getFileSize() { return fileSize; }
    public void setFileSize(Long fileSize) { this.fileSize = fileSize; }

    public String getMimeType() { return mimeType; }
    public void setMimeType(String mimeType) { this.mimeType = mimeType; }

    public String getStoragePath() { return storagePath; }
    public void setStoragePath(String storagePath) { this.storagePath = storagePath; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public Integer getWidth() { return width; }
    public void setWidth(Integer width) { this.width = width; }

    public Integer getHeight() { return height; }
    public void setHeight(Integer height) { this.height = height; }

    @Override
    public String getTenantOid() { return tenantOid; }

    @Override
    public void setTenantOid(String tenantOid) { this.tenantOid = tenantOid; }

    @Override
    public String toString() {
        return "Media{originalName='" + originalName + "', fileSize=" + fileSize
                + ", mimeType='" + mimeType + "'}";
    }
}
