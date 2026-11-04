/*
 * Copyright (c) 2026~2028 深圳乘恺科技有限公司
 * All rights reserved.
 *
 * @author Kiween.Hu; Roney.Liu
 */

package cn.ck.plm.change.entity;

import cn.ck.plm.base.entity.TenantEntity;

/**
 * 变更请求 ↔ 问题 的关联行（{@code ck_change_request_issue}）。
 *
 * <p>为什么是独立表而不是在 {@code ck_change_request} 上存逗号串：
 * "这个变更由哪些问题驱动"要能被反向查询（某个问题引出了几张变更单），
 * 逗号串既建不了索引，也没法做外键与级联删除。
 *
 * <p>与 {@code ck_change_request} 一样，本表不在租户拦截器的共享名单里，
 * SQL 必须显式书写 {@code tenant_oid}。
 */
public class ChangeRequestIssue implements TenantEntity {

    private String oid;

    /** 变更请求 oid */
    private String requestOid;

    /** 问题 oid */
    private String issueOid;

    private String tenantOid;

    private String creator;

    public ChangeRequestIssue() {
    }

    public ChangeRequestIssue(String oid, String requestOid, String issueOid, String tenantOid, String creator) {
        this.oid = oid;
        this.requestOid = requestOid;
        this.issueOid = issueOid;
        this.tenantOid = tenantOid;
        this.creator = creator;
    }

    public String getOid() { return oid; }
    public void setOid(String oid) { this.oid = oid; }

    public String getRequestOid() { return requestOid; }
    public void setRequestOid(String requestOid) { this.requestOid = requestOid; }

    public String getIssueOid() { return issueOid; }
    public void setIssueOid(String issueOid) { this.issueOid = issueOid; }

    @Override
    public String getTenantOid() { return tenantOid; }

    @Override
    public void setTenantOid(String tenantOid) { this.tenantOid = tenantOid; }

    public String getCreator() { return creator; }
    public void setCreator(String creator) { this.creator = creator; }
}
