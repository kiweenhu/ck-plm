/*
 * Copyright (c) 2026~2028 深圳乘恺科技有限公司
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

    /**
     * 该状态下是否允许修订（生成新的大版本）。
     *
     * <p>同时是「能否发起变更」的判据：只有对象当前所处的状态可修订，才具备修订权限。
     * 按状态配置而不是按类型配置——同一个模板会被多个类型复用，而"已发布能不能改"
     * 是该状态的业务语义，不该由调用方各自解释。
     */
    private boolean revisable = true;

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

    public boolean isRevisable() { return revisable; }
    public void setRevisable(boolean revisable) { this.revisable = revisable; }
}
