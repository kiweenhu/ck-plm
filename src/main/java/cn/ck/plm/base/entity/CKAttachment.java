/*
 * Copyright (c) 2025 深圳乘恺科技有限公司
 * All rights reserved.
 *
 * @author Kiween.Hu; Roney.Liu
 */

package cn.ck.plm.base.entity;

/**
 * 附件实体，存储各类业务对象的附属文件元信息，可被 Document、Part、CR 等实体复用。
 *
 * <p>通过 {@code ownerOid} 关联其所属的业务对象（如 {@code DocumentIteration} 等），1:N 关系。
 * 与 {@link CKFile}（主文档文件）分离，专注于附件场景。
 *
 * <h3>实体关系（示例：文档场景）</h3>
 * <pre>
 * Document  1 ── N  DocumentIteration  (通过 masterOid 关联)
 * DocumentIteration  1 ── 1  CKFile       (通过 ckfileOid，该版本主文档文件)
 * DocumentIteration  1 ── N  CKAttachment (通过 ownerOid，附件)
 * </pre>
 */
public class CKAttachment extends BaseEntity implements TenantEntity {

    /** 所属业务对象 oid（指向关联的实体，如 {@code DocumentIteration}，支持多种业务对象复用） */
    private String ownerOid;

    /** 原始文件名（含扩展名） */
    private String fileName;

    /** 文件大小（字节） */
    private Long fileSize;

    /** 文件存储路径（服务器相对路径） */
    private String storagePath;

    /** 文件 MIME 类型（如 application/pdf） */
    private String mimeType;

    /** 租户 oid（引用 ck_tenant.oid） */
    private String tenantOid;

    // ==================== 构造方法 ====================

    public CKAttachment() {
        super();
    }

    // ==================== Getter / Setter ====================

    public String getOwnerOid() { return ownerOid; }
    public void setOwnerOid(String ownerOid) { this.ownerOid = ownerOid; }

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
        return "CKAttachment{oid='" + getOid() + "', fileName='" + fileName
                + "', ownerOid='" + ownerOid + "'}";
    }
}
