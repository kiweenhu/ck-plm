/*
 * Copyright (c) 2026~2028 深圳乘恺科技有限公司
 * All rights reserved.
 *
 * @author Kiween.Hu; Roney.Liu
 */

package cn.ck.plm.process.dto;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 任务办理表单上下文 —— 「任务中心」的办理弹框据此渲染<b>该节点配的表单</b>，而不是写死的通用表单。
 *
 * <p>三件事的来源链路：
 * <ol>
 *   <li>{@code formKey}：BPMN userTask 的 {@code flowable:formKey}，即流程设计器里 DSL 的
 *       {@code formRef}。前端按表单注册表（{@code dsl-core/forms.ts}）派发到对应表单 ——
 *       注册表是设计期、编译期、运行期<b>共用</b>的那一份契约；</li>
 *   <li>{@code taskDefinitionKey}：BPMN 活动 id（= DSL 节点 id）。表单要按节点区分内容，
 *       也要按节点拼运行期变量名（如 {@code ckplmSetupAssignees_<活动id>}）；</li>
 *   <li>{@code dslJson}：<b>该实例所用那一版</b>流程模板的 DSL。表单内容由流程派生
 *       （「设置审批人」要列出下游所有审批 / 会签 / 办理活动），所以必须给实例实际在跑的那一版
 *       —— 用模板主档的"最新版"会列出该实例根本不存在的活动。</li>
 * </ol>
 *
 * <p>{@code dslJson} 解析不到时为 {@code null}：前端退化为通用表单，不影响办理（办理不依赖它）。
 */
public class ProcessTaskFormVO {

    /** 节点表单 code；null = 该节点未配表单 */
    private String formKey;

    /** BPMN 活动 id（= DSL 节点 id） */
    private String taskDefinitionKey;

    /** 节点名称（BPMN userTask 的 name，弹框标题用） */
    private String taskName;

    /** 流程定义 key */
    private String processDefinitionKey;

    /** 流程定义版本（该实例在跑的那一版模板） */
    private Integer processDefinitionVersion;

    /** 该版本的流程 DSL（JSON 字符串）；解析不到为 null */
    private String dslJson;

    /**
     * 本实例关联的**业务对象** oid（来自 {@code ck_process_entity_set}；无关联为 null）。
     *
     * <p>用途：表单要按业务对象现算数据 —— 如「阶段门决策申请单」的"未关闭阻断问题数"
     * 自动带出（{@code /api/issues/gate-check?ownerOid=}）。与 {@code entityRefsOf} 同一口径：
     * 只给"引用 + 类型"，不回查业务表（实体名是锦上添花，查不到不该让办理失败）。
     */
    private String businessOid;

    /** 业务对象的类型编码（如 {@code PRODUCT_LINE} / {@code PRODUCT_MODEL}） */
    private String businessTypeCode;

    /**
     * 本实例的**用户表单变量**（流程变量剔除平台内部项，如 {@code businessObjectSet} /
     * {@code initiator} / {@code approved}）。
     *
     * <p>用途：**同模板的办理节点回填发起时填过的内容**。发起节点与办理节点配同一张表单时
     * （如技术评审流程的「开始：评审申请」与「材料准备与确认」都是 {@code technical-review-apply}），
     * 办理页原先呈现一张空表 —— 申请信息明明已经躺在流程变量里，用户却被要求重填一遍
     * （2026-10-26 用户反馈「发起时已经填过了，没有带过来」）。
     *
     * <p>服务端不解释变量语义：表单自己按 {@code ckplm*} 前缀认自己的字段
     * （变量名由表单的 {@code collect()} 定义）。空表 = 该实例还没有表单变量（老实例 / 纯审批流程）。
     */
    private Map<String, Object> variables = new LinkedHashMap<>();

    // ==================== Getter / Setter ====================

    public String getFormKey() { return formKey; }
    public void setFormKey(String formKey) { this.formKey = formKey; }

    public String getTaskDefinitionKey() { return taskDefinitionKey; }
    public void setTaskDefinitionKey(String taskDefinitionKey) { this.taskDefinitionKey = taskDefinitionKey; }

    public String getTaskName() { return taskName; }
    public void setTaskName(String taskName) { this.taskName = taskName; }

    public String getProcessDefinitionKey() { return processDefinitionKey; }
    public void setProcessDefinitionKey(String processDefinitionKey) { this.processDefinitionKey = processDefinitionKey; }

    public Integer getProcessDefinitionVersion() { return processDefinitionVersion; }
    public void setProcessDefinitionVersion(Integer processDefinitionVersion) { this.processDefinitionVersion = processDefinitionVersion; }

    public String getDslJson() { return dslJson; }
    public void setDslJson(String dslJson) { this.dslJson = dslJson; }

    public String getBusinessOid() { return businessOid; }
    public void setBusinessOid(String businessOid) { this.businessOid = businessOid; }

    public String getBusinessTypeCode() { return businessTypeCode; }
    public void setBusinessTypeCode(String businessTypeCode) { this.businessTypeCode = businessTypeCode; }

    public Map<String, Object> getVariables() { return variables; }
    public void setVariables(Map<String, Object> variables) { this.variables = variables; }
}
