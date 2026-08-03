/*
 * Copyright (c) 2025 深圳乘恺科技有限公司
 * All rights reserved.
 */
package cn.ck.plm.checkout.dto;

/**
 * 检出对象视图 —— 通用 DTO，可扩展支持多种实体类型。
 */
public class CheckoutVO {

    /** 实体 oid */
    private String oid;
    /** 实体名称 */
    private String name;
    /** 实体编码 */
    private String code;
    /** 实体类型（DOCUMENT / PART / ...） */
    private String entityType;
    /** 实体类型中文名 */
    private String entityTypeName;
    /** 显示版本，如 A.1 */
    private String displayVersion;
    /** 检出人 */
    private String checkedOutBy;
    /** 检出注释 */
    private String checkedOutComment;
    /** 检出时间 */
    private String checkedOutAt;
    /** 状态编码 */
    private String statusCode;
    /** 状态名称 */
    private String statusName;
    /** 跳转路径 */
    private String linkPath;

    // ==================== Getters & Setters ====================

    public String getOid() { return oid; }
    public void setOid(String oid) { this.oid = oid; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getCode() { return code; }
    public void setCode(String code) { this.code = code; }
    public String getEntityType() { return entityType; }
    public void setEntityType(String entityType) { this.entityType = entityType; }
    public String getEntityTypeName() { return entityTypeName; }
    public void setEntityTypeName(String entityTypeName) { this.entityTypeName = entityTypeName; }
    public String getDisplayVersion() { return displayVersion; }
    public void setDisplayVersion(String displayVersion) { this.displayVersion = displayVersion; }
    public String getCheckedOutBy() { return checkedOutBy; }
    public void setCheckedOutBy(String checkedOutBy) { this.checkedOutBy = checkedOutBy; }
    public String getCheckedOutComment() { return checkedOutComment; }
    public void setCheckedOutComment(String checkedOutComment) { this.checkedOutComment = checkedOutComment; }
    public String getCheckedOutAt() { return checkedOutAt; }
    public void setCheckedOutAt(String checkedOutAt) { this.checkedOutAt = checkedOutAt; }
    public String getStatusCode() { return statusCode; }
    public void setStatusCode(String statusCode) { this.statusCode = statusCode; }
    public String getStatusName() { return statusName; }
    public void setStatusName(String statusName) { this.statusName = statusName; }
    public String getLinkPath() { return linkPath; }
    public void setLinkPath(String linkPath) { this.linkPath = linkPath; }
}
