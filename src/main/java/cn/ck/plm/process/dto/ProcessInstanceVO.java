/*
 * Copyright (c) 2025 深圳乘恺科技有限公司
 * All rights reserved.
 *
 * @author Kiween.Hu; Roney.Liu
 */

package cn.ck.plm.process.dto;

import java.util.Date;
import java.util.List;
import java.util.Map;

/**
 * 流程实例视图对象 —— 对应前端「流程监控」表格与详情弹窗字段。
 *
 * <pre>
 * 前端取值：record.id / record.processDefinitionName / record.processDefinitionKey
 *          record.businessKey / record.startUserId / record.status
 *          record.startTime / record.endTime / record.deleteReason / record.variables
 *          record.entities ← 业务实体引用（列表里"业务标识"列据此显示编码/名称/版本/状态）
 * status 取值：running | suspended | completed | terminated（前端据此渲染标签与操作按钮）
 * </pre>
 */
public class ProcessInstanceVO {

    /** 流程实例 id */
    private String id;

    /** 流程定义 id */
    private String processDefinitionId;

    /** 流程定义 key */
    private String processDefinitionKey;

    /** 流程定义名称 */
    private String processDefinitionName;

    /** 业务标识（业务对象 oid 等） */
    private String businessKey;

    /** 发起人（username） */
    private String startUserId;

    /** 状态：running / suspended / completed / terminated */
    private String status;

    /** 开始时间 */
    private Date startTime;

    /** 结束时间（未结束为 null） */
    private Date endTime;

    /** 结束原因（终止原因 / 删除原因） */
    private String deleteReason;

    /** 流程变量（详情返回） */
    private Map<String, Object> variables;

    /** 租户 oid（Flowable tenantId） */
    private String tenantId;

    /** 该实例关联的业务对象大版本（{@code ck_process_entity_set.entity_version}，如 A）；反查场景才有 */
    private String entityVersion;

    /** 运行中实例的当前节点名（多个用「、」连接）；已结束为 null */
    private String currentActivityName;

    /** 运行中实例当前节点的办理人（多个用「、」连接，全未指派为空串）；已结束为 null */
    private String currentAssignees;

    /**
     * 该实例关联的业务实体引用（{@code ck_process_entity_set}，一个实例可能关联多个）。
     *
     * <p><b>为什么给引用而不是显示名</b>：{@code businessKey} 只是个 oid，列表里显示它没有意义；
     * 但"编码 / 名称 / 版本 / 生命周期状态"要么回查业务表、要么由各宿主自行解释，
     * 让流程模块去读 Part / Document 就等于反向依赖各业务模块（见 {@code ProcessServiceImpl#entityRefsOf}）。
     * 所以这里只给<b>引用 + 类型</b>（oid / 大版本 / typeCode / 宿主），由前端按类型回查展示
     * —— 与「任务办理页」的通用信息同一口径。
     */
    private List<ProcessTaskContextVO.EntityRef> entities;

    // ==================== Getter / Setter ====================

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getProcessDefinitionId() { return processDefinitionId; }
    public void setProcessDefinitionId(String processDefinitionId) { this.processDefinitionId = processDefinitionId; }

    public String getProcessDefinitionKey() { return processDefinitionKey; }
    public void setProcessDefinitionKey(String processDefinitionKey) { this.processDefinitionKey = processDefinitionKey; }

    public String getProcessDefinitionName() { return processDefinitionName; }
    public void setProcessDefinitionName(String processDefinitionName) { this.processDefinitionName = processDefinitionName; }

    public String getBusinessKey() { return businessKey; }
    public void setBusinessKey(String businessKey) { this.businessKey = businessKey; }

    public String getStartUserId() { return startUserId; }
    public void setStartUserId(String startUserId) { this.startUserId = startUserId; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public Date getStartTime() { return startTime; }
    public void setStartTime(Date startTime) { this.startTime = startTime; }

    public Date getEndTime() { return endTime; }
    public void setEndTime(Date endTime) { this.endTime = endTime; }

    public String getDeleteReason() { return deleteReason; }
    public void setDeleteReason(String deleteReason) { this.deleteReason = deleteReason; }

    public Map<String, Object> getVariables() { return variables; }
    public void setVariables(Map<String, Object> variables) { this.variables = variables; }

    public String getTenantId() { return tenantId; }
    public void setTenantId(String tenantId) { this.tenantId = tenantId; }

    public String getEntityVersion() { return entityVersion; }
    public void setEntityVersion(String entityVersion) { this.entityVersion = entityVersion; }

    public String getCurrentActivityName() { return currentActivityName; }
    public void setCurrentActivityName(String currentActivityName) { this.currentActivityName = currentActivityName; }

    public String getCurrentAssignees() { return currentAssignees; }
    public void setCurrentAssignees(String currentAssignees) { this.currentAssignees = currentAssignees; }

    public List<ProcessTaskContextVO.EntityRef> getEntities() { return entities; }
    public void setEntities(List<ProcessTaskContextVO.EntityRef> entities) { this.entities = entities; }
}
