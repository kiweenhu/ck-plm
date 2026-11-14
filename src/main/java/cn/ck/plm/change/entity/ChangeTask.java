/*
 * Copyright (c) 2026~2028 深圳乘恺科技有限公司
 * All rights reserved.
 *
 * @author Kiween.Hu; Roney.Liu
 */

package cn.ck.plm.change.entity;

import cn.ck.plm.base.entity.TenantEntity;
import cn.ck.plm.base.entity.WithoutVersionEntity;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 变更任务 —— 一次变更的<b>执行单元</b>。
 *
 * <h3>为什么要有它</h3>
 * <p>受影响对象可能横跨设计、文档、BOM 几拨人。若整张 ECN 一起执行，失败时只能整批重来；
 * 拆成任务后：谁的任务谁负责、失败只重跑该任务下 {@code executedAt} 为空的行 ——
 * 这就是"按任务分工、失败可局部重试"的落点。
 *
 * <p>状态取值见 {@link cn.ck.plm.change.flow.ChangeTaskFlow}。
 */
public class ChangeTask extends WithoutVersionEntity implements TenantEntity {

    /** 所属变更通告（ck_change_notice.oid） */
    private String noticeOid;

    /** 任务类型：DESIGN / DOCUMENT / BOM / PROCESS / VERIFY；空 = 未分类 */
    private String taskType;

    /** 责任人（用户名） */
    private String assignee;

    /** 状态（见 ChangeTaskFlow） */
    private String status;

    /**
     * 对应的变更任务子流程实例 id（非持久化，详情查询时按 businessKey = 任务 oid 回填）。
     *
     * <p>任务表上没有这一列：实例由「变更任务自动分发」按 {@code businessKey=${task.oid}}
     * 拉起，关联信息在流程侧。界面上要据此回答两件事 —— 能不能删、能不能手工改状态，
     * 以及「流程跑到哪了」从哪进。
     */
    private transient String processInstanceId;

    /** 计划开始日期 */
    private LocalDate planStart;

    /** 计划完成日期 */
    private LocalDate planEnd;

    /** 完成时间 */
    private LocalDateTime completedAt;

    /**
     * 所属变更通告编号 —— <b>响应装配字段</b>，不入库。
     *
     * <p>任务中心是跨通告的列表，只有 {@code noticeOid} 的话用户看不懂"这条任务属于哪张单"。
     * 由 {@code selectMine} 的 JOIN 带出（与 ck_issue 的 gateLabel 同一做法）。
     */
    private String noticeCode;

    /**
     * 所属变更通告状态 —— <b>响应装配字段</b>，不入库。
     *
     * <p>任务能不能动手由通告决定：审批配置为「需要审批」的单，任务由流程派发并由
     * 任务子流程推进（业务列表上不该再给直接改状态的按钮）；免审单没有流程，
     * 要等通告进入「实施中」才由责任人手工推进。列表上只有 noticeCode 说明不了这件事，
     * 由 {@code selectMine} 的 JOIN 带出。
     */
    private String noticeStatus;

    /** 租户 oid */
    private String tenantOid;

    public ChangeTask() {
    }

    public String getNoticeOid() { return noticeOid; }
    public void setNoticeOid(String noticeOid) { this.noticeOid = noticeOid; }

    public String getTaskType() { return taskType; }
    public void setTaskType(String taskType) { this.taskType = taskType; }

    public String getAssignee() { return assignee; }
    public void setAssignee(String assignee) { this.assignee = assignee; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public String getProcessInstanceId() { return processInstanceId; }
    public void setProcessInstanceId(String processInstanceId) { this.processInstanceId = processInstanceId; }

    public LocalDate getPlanStart() { return planStart; }
    public void setPlanStart(LocalDate planStart) { this.planStart = planStart; }

    public LocalDate getPlanEnd() { return planEnd; }
    public void setPlanEnd(LocalDate planEnd) { this.planEnd = planEnd; }

    public LocalDateTime getCompletedAt() { return completedAt; }
    public void setCompletedAt(LocalDateTime completedAt) { this.completedAt = completedAt; }

    public String getNoticeCode() { return noticeCode; }
    public void setNoticeCode(String noticeCode) { this.noticeCode = noticeCode; }

    public String getNoticeStatus() { return noticeStatus; }
    public void setNoticeStatus(String noticeStatus) { this.noticeStatus = noticeStatus; }

    @Override
    public String getTenantOid() { return tenantOid; }

    @Override
    public void setTenantOid(String tenantOid) { this.tenantOid = tenantOid; }

    @Override
    public String toString() {
        return "ChangeTask{code='" + getCode() + "', status='" + status + "'}";
    }
}
