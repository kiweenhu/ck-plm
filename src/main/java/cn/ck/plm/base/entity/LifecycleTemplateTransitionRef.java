/*
 * Copyright (c) 2025 深圳乘恺科技有限公司
 * All rights reserved.
 *
 * @author Kiween.Hu; Roney.Liu
 */

package cn.ck.plm.base.entity;

/**
 * 生命周期模板 → 状态流转规则 的关联对象。
 *
 * <p>记录模板中状态间的升版（PROMOTE）和驳回（REJECT）流转规则。
 * 对应表 ck_lifecycle_template_transition。
 */
public class LifecycleTemplateTransitionRef {

    /** 关联 oid（UUID） */
    private String oid;

    /** 子版本 oid（关联 ck_lifecycle_template_iteration.oid） */
    private String iterationOid;

    /** 来源状态编码 */
    private String fromStatusCode;

    /** 目标状态编码 */
    private String toStatusCode;

    /** 流转类型：PROMOTE（升版）/ REJECT（驳回） */
    private String transitionType;

    public LifecycleTemplateTransitionRef() {
    }

    public LifecycleTemplateTransitionRef(String fromStatusCode, String toStatusCode, String transitionType) {
        this.fromStatusCode = fromStatusCode;
        this.toStatusCode = toStatusCode;
        this.transitionType = transitionType;
    }

    public String getOid() { return oid; }
    public void setOid(String oid) { this.oid = oid; }
    public String getIterationOid() { return iterationOid; }
    public void setIterationOid(String iterationOid) { this.iterationOid = iterationOid; }
    public String getFromStatusCode() { return fromStatusCode; }
    public void setFromStatusCode(String fromStatusCode) { this.fromStatusCode = fromStatusCode; }
    public String getToStatusCode() { return toStatusCode; }
    public void setToStatusCode(String toStatusCode) { this.toStatusCode = toStatusCode; }
    public String getTransitionType() { return transitionType; }
    public void setTransitionType(String transitionType) { this.transitionType = transitionType; }
}
