/*
 * Copyright (c) 2026~2028 深圳乘恺科技有限公司
 * All rights reserved.
 */

package cn.ck.plm.improve.entity;

import cn.ck.plm.base.entity.BaseEntity;
import cn.ck.plm.base.entity.TenantEntity;

/**
 * 问题处理记录 —— "跟踪"的载体：每次创建 / 编辑 / 流转 / 备注追加一行，
 * 按时间正序拼出这条问题从提出到关闭的完整历程。
 *
 * <p>只增不改（没有 update 语义）：记录被改过就不叫记录了。
 */
public class IssueActivity extends BaseEntity implements TenantEntity {

    /** 所属问题 oid（→ ck_issue.oid；问题删除时级联删除） */
    private String issueOid;

    /** 动作：见 {@link cn.ck.plm.improve.flow.IssueFlow}（CREATE / UPDATE / ASSIGN / START / … / COMMENT） */
    private String action;

    /** 动作前状态（流转类动作填；编辑与备注类留空） */
    private String fromStatus;

    /** 动作后状态（流转类动作填） */
    private String toStatus;

    /** 说明 / 意见（取消、验证退回、重开时必填；其余可选） */
    private String comment;

    /** 操作人（用户名；冗余存一份，展示时不必回查审计列） */
    private String operator;

    private String tenantOid;

    public IssueActivity() {}

    public IssueActivity(String issueOid, String action) {
        this.issueOid = issueOid;
        this.action = action;
    }

    public String getIssueOid() { return issueOid; }
    public void setIssueOid(String issueOid) { this.issueOid = issueOid; }

    public String getAction() { return action; }
    public void setAction(String action) { this.action = action; }

    public String getFromStatus() { return fromStatus; }
    public void setFromStatus(String fromStatus) { this.fromStatus = fromStatus; }

    public String getToStatus() { return toStatus; }
    public void setToStatus(String toStatus) { this.toStatus = toStatus; }

    public String getComment() { return comment; }
    public void setComment(String comment) { this.comment = comment; }

    public String getOperator() { return operator; }
    public void setOperator(String operator) { this.operator = operator; }

    @Override
    public String getTenantOid() { return tenantOid; }

    @Override
    public void setTenantOid(String tenantOid) { this.tenantOid = tenantOid; }
}
