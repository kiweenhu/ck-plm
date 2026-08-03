/*
 * Copyright (c) 2025 深圳乘恺科技有限公司
 * All rights reserved.
 *
 * @author Kiween.Hu; Roney.Liu
 */

package cn.ck.plm.base.entity;

/**
 * 生命周期模板 → 生命周期状态 的关联对象。
 *
 * <p>记录模板中包含哪些状态及其排序顺序。对应表 ck_lifecycle_template_state。
 */
public class LifecycleTemplateStatusRef {

    /** 关联 oid（UUID） */
    private String oid;

    /** 子版本 oid（关联 ck_lifecycle_template_iteration.oid） */
    private String iterationOid;

    /** 状态编码 */
    private String statusCode;

    /** 状态显示名称（冗余，方便前端展示） */
    private String statusDisplayName;

    /** 排序序号 */
    private Integer sortOrder;

    public LifecycleTemplateStatusRef() {
    }

    public LifecycleTemplateStatusRef(String statusCode, String statusDisplayName, Integer sortOrder) {
        this.statusCode = statusCode;
        this.statusDisplayName = statusDisplayName;
        this.sortOrder = sortOrder;
    }

    public String getOid() { return oid; }
    public void setOid(String oid) { this.oid = oid; }
    public String getIterationOid() { return iterationOid; }
    public void setIterationOid(String iterationOid) { this.iterationOid = iterationOid; }
    public String getStatusCode() { return statusCode; }
    public void setStatusCode(String statusCode) { this.statusCode = statusCode; }
    public String getStatusDisplayName() { return statusDisplayName; }
    public void setStatusDisplayName(String statusDisplayName) { this.statusDisplayName = statusDisplayName; }
    public Integer getSortOrder() { return sortOrder; }
    public void setSortOrder(Integer sortOrder) { this.sortOrder = sortOrder; }
}
