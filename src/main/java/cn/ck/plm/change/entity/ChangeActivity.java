/*
 * Copyright (c) 2026~2028 深圳乘恺科技有限公司
 * All rights reserved.
 *
 * @author Kiween.Hu; Roney.Liu
 */

package cn.ck.plm.change.entity;

import cn.ck.plm.base.entity.BaseEntity;
import cn.ck.plm.base.entity.TenantEntity;

/**
 * 变更操作记录 —— 三类变更对象（请求 / 通告 / 任务）共用的时间线。
 *
 * <p>与 {@code ck_issue_activity} 同构：每次创建 / 编辑 / 流转追加一行。
 * 没有外键（多态：{@link #changeType} 决定 {@link #changeOid} 指向哪张表），
 * 删除主单时由服务层一并清理。
 *
 * <p>为什么必须有它：变更的价值一半在"结果"，一半在"谁在什么时候批的"。
 * 只把结果写进主表，审计时就没法回答"这张单怎么走到这一步的"。
 */
public class ChangeActivity extends BaseEntity implements TenantEntity {

    /** 对象类型：REQUEST / NOTICE / TASK */
    private String changeType;

    /** 对应对象的 oid */
    private String changeOid;

    /** 动作（取值见各 Flow 的常量，如 SUBMIT / APPROVE / REVISE） */
    private String action;

    /** 原状态（流转类动作填；创建与编辑类留空） */
    private String fromStatus;

    /** 新状态 */
    private String toStatus;

    /** 说明 / 意见 */
    private String comment;

    /** 操作人（用户名；冗余，避免与审计列语义混淆） */
    private String operator;

    /** 租户 oid */
    private String tenantOid;

    public ChangeActivity() {
    }

    public String getChangeType() { return changeType; }
    public void setChangeType(String changeType) { this.changeType = changeType; }

    public String getChangeOid() { return changeOid; }
    public void setChangeOid(String changeOid) { this.changeOid = changeOid; }

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

    @Override
    public String toString() {
        return "ChangeActivity{" + changeType + ":" + changeOid
                + ", " + action + ", " + fromStatus + " -> " + toStatus + "}";
    }
}
