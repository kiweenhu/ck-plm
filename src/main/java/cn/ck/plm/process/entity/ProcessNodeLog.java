/*
 * Copyright (c) 2025 深圳乘恺科技有限公司
 * All rights reserved.
 *
 * @author Kiween.Hu; Roney.Liu
 */

package cn.ck.plm.process.entity;

import cn.ck.plm.base.entity.TenantEntity;

import java.time.LocalDateTime;

/**
 * 流程节点执行日志 —— 「这个节点后台到底跑了什么、报了什么错」的一条记录。
 *
 * <p>粒度是<b>流程实例 + 节点</b>：详情页点开某个节点，就是按这两列查它的执行痕迹。
 *
 * <p>为什么要落库而不是只写日志文件：自动服务（设置状态 / 自动服务 / 通知）由后台执行，
 * 失败时用户只看到"流程卡住了"，运维要登服务器翻日志。落库之后，
 * "哪个节点、哪一步、什么原因失败"在流程详情里直接可见 —— 这正是本次要实现的能力。
 */
public class ProcessNodeLog implements TenantEntity {

    /** 日志级别（与界面分色一致）：INFO 正常 / WARN 有隐患 / ERROR 失败 */
    public static final String LEVEL_INFO = "INFO";
    public static final String LEVEL_WARN = "WARN";
    public static final String LEVEL_ERROR = "ERROR";

    /** 来源：自动服务节点 / 通知节点 / 引擎与平台自身 */
    public static final String SOURCE_SERVICE = "SERVICE";
    public static final String SOURCE_NOTIFY = "NOTIFY";
    public static final String SOURCE_SYSTEM = "SYSTEM";

    private String oid;
    private String tenantOid;
    /** 流程实例 id */
    private String processInstanceId;
    /** 节点 id（BPMN activityId，与节点经路里的 activityId 同源） */
    private String activityId;
    /** 节点名（存一份：节点改名后旧日志仍能读懂当时是哪一步） */
    private String activityName;
    private String level;
    private String source;
    /** 一句话结论（如「自动服务完成：服务=object.setLifecycleState，对象 3 个」） */
    private String message;
    /** 明细：参数、异常类名与堆栈 —— 排查时真正要看的东西 */
    private String detail;
    private LocalDateTime createdAt;

    public String getOid() { return oid; }
    public void setOid(String oid) { this.oid = oid; }

    @Override
    public String getTenantOid() { return tenantOid; }

    @Override
    public void setTenantOid(String tenantOid) { this.tenantOid = tenantOid; }

    public String getProcessInstanceId() { return processInstanceId; }
    public void setProcessInstanceId(String processInstanceId) { this.processInstanceId = processInstanceId; }

    public String getActivityId() { return activityId; }
    public void setActivityId(String activityId) { this.activityId = activityId; }

    public String getActivityName() { return activityName; }
    public void setActivityName(String activityName) { this.activityName = activityName; }

    public String getLevel() { return level; }
    public void setLevel(String level) { this.level = level; }

    public String getSource() { return source; }
    public void setSource(String source) { this.source = source; }

    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }

    public String getDetail() { return detail; }
    public void setDetail(String detail) { this.detail = detail; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
