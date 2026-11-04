/*
 * Copyright (c) 2026~2028 深圳乘恺科技有限公司
 * All rights reserved.
 *
 * @author Kiween.Hu; Roney.Liu
 */

package cn.ck.plm.change.entity;

import cn.ck.plm.base.entity.TenantEntity;

/**
 * 变更通告 ↔ 问题 的关联行（{@code ck_change_notice_issue}）。
 *
 * <p>与 {@link ChangeRequestIssue} 同构：通告也常常一次解决多个问题
 * （一张 ECN 覆盖同批次的结构与工艺改进），单个字段存不下。
 */
public class ChangeNoticeIssue implements TenantEntity {

    private String oid;

    /** 变更通告 oid */
    private String noticeOid;

    /** 问题 oid */
    private String issueOid;

    private String tenantOid;

    private String creator;

    public ChangeNoticeIssue() {
    }

    public ChangeNoticeIssue(String oid, String noticeOid, String issueOid, String tenantOid, String creator) {
        this.oid = oid;
        this.noticeOid = noticeOid;
        this.issueOid = issueOid;
        this.tenantOid = tenantOid;
        this.creator = creator;
    }

    public String getOid() { return oid; }
    public void setOid(String oid) { this.oid = oid; }

    public String getNoticeOid() { return noticeOid; }
    public void setNoticeOid(String noticeOid) { this.noticeOid = noticeOid; }

    public String getIssueOid() { return issueOid; }
    public void setIssueOid(String issueOid) { this.issueOid = issueOid; }

    @Override
    public String getTenantOid() { return tenantOid; }

    @Override
    public void setTenantOid(String tenantOid) { this.tenantOid = tenantOid; }

    public String getCreator() { return creator; }
    public void setCreator(String creator) { this.creator = creator; }
}
