/*
 * Copyright (c) 2025 深圳乘恺科技有限公司
 * All rights reserved.
 *
 * @author Kiween.Hu; Roney.Liu
 */

package cn.ck.plm.process.dto;

import java.util.Date;
import java.util.List;

/**
 * 流程任务视图对象 —— 对应前端「任务中心」表格字段。
 *
 * <pre>
 * 前端取值：record.id / record.name / record.processDefinitionName
 *          record.assignee / record.createTime / record.dueDate
 * </pre>
 */
public class ProcessTaskVO {

    /** 任务 id（act_ru_task.id_ / act_hi_taskinst.id_） */
    private String id;

    /** 任务名称（BPMN userTask 的 name） */
    private String name;

    /** 流程定义名称（如「PLM变更审批流程」） */
    private String processDefinitionName;

    /** 流程定义 key */
    private String processDefinitionKey;

    /** 流程实例 id */
    private String processInstanceId;

    /** 流程实例业务标识（如业务对象 oid） */
    private String businessKey;

    /**
     * 该任务所属实例关联的业务实体引用（{@code ck_process_entity_set}）。
     *
     * <p><b>为什么列表要它</b>：任务中心原来只能显示任务名 + 流程名，而同一个流程（如「元部件引入」）
     * 会挂在几十个对象上——不看一眼"办的是哪一条"根本分不清两行有什么区别。
     *
     * <p>只给引用（typeCode / oid / 大版本），编码 / 名称 / 生命周期状态由前端回查：
     * 流程模块不回查业务表，否则等于反向依赖各业务模块（见 {@code ProcessServiceImpl#entityRefsOf}）。
     */
    private List<ProcessTaskContextVO.EntityRef> entities;

    /**
     * 节点表单 code（BPMN userTask 的 {@code flowable:formKey} = DSL 的 {@code formRef}）。
     *
     * <p>办理弹框据此<b>派发到该节点配的表单</b>；为空表示该节点没配表单（走通用表单）。
     */
    private String formKey;

    /** BPMN 活动 id（= DSL 节点 id）：表单需要知道"是哪个节点"，如「设置审批人」写变量要用它拼名 */
    private String taskDefinitionKey;

    /**
     * 负责人 —— Flowable 的<b>原样标识</b>：可能是用户名，也可能是人员 oid
     * （「设置审批人」按 oid 指派下游）。界面显示请用 {@link #assigneeName}。
     */
    private String assignee;

    /** 负责人的显示名（由 assignee 解析出来，两种标识都能解）；界面直接显示这个 */
    private String assigneeName;

    /** 候选组（逗号分隔，仅可认领任务返回） */
    private String candidateGroups;

    /** 创建时间 */
    private Date createTime;

    /** 截止时间（BPMN dueDate，可空） */
    private Date dueDate;

    /** 结束时间（已办任务返回） */
    private Date endTime;

    /** 是否逾期（截止时间早于当前时间） */
    private Boolean overdue;

    /** 租户 oid（Flowable tenantId） */
    private String tenantId;

    // ==================== Getter / Setter ====================

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getProcessDefinitionName() { return processDefinitionName; }
    public void setProcessDefinitionName(String processDefinitionName) { this.processDefinitionName = processDefinitionName; }

    public String getProcessDefinitionKey() { return processDefinitionKey; }
    public void setProcessDefinitionKey(String processDefinitionKey) { this.processDefinitionKey = processDefinitionKey; }

    public String getProcessInstanceId() { return processInstanceId; }
    public void setProcessInstanceId(String processInstanceId) { this.processInstanceId = processInstanceId; }

    public String getBusinessKey() { return businessKey; }
    public void setBusinessKey(String businessKey) { this.businessKey = businessKey; }

    public List<ProcessTaskContextVO.EntityRef> getEntities() { return entities; }
    public void setEntities(List<ProcessTaskContextVO.EntityRef> entities) { this.entities = entities; }

    public String getFormKey() { return formKey; }
    public void setFormKey(String formKey) { this.formKey = formKey; }

    public String getTaskDefinitionKey() { return taskDefinitionKey; }
    public void setTaskDefinitionKey(String taskDefinitionKey) { this.taskDefinitionKey = taskDefinitionKey; }

    public String getAssignee() { return assignee; }
    public void setAssignee(String assignee) { this.assignee = assignee; }

    public String getAssigneeName() { return assigneeName; }
    public void setAssigneeName(String assigneeName) { this.assigneeName = assigneeName; }

    public String getCandidateGroups() { return candidateGroups; }
    public void setCandidateGroups(String candidateGroups) { this.candidateGroups = candidateGroups; }

    public Date getCreateTime() { return createTime; }
    public void setCreateTime(Date createTime) { this.createTime = createTime; }

    public Date getDueDate() { return dueDate; }
    public void setDueDate(Date dueDate) { this.dueDate = dueDate; }

    public Date getEndTime() { return endTime; }
    public void setEndTime(Date endTime) { this.endTime = endTime; }

    public Boolean getOverdue() { return overdue; }
    public void setOverdue(Boolean overdue) { this.overdue = overdue; }

    public String getTenantId() { return tenantId; }
    public void setTenantId(String tenantId) { this.tenantId = tenantId; }
}
