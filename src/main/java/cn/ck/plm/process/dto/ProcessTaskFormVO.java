/*
 * Copyright (c) 2025 深圳乘恺科技有限公司
 * All rights reserved.
 *
 * @author Kiween.Hu; Roney.Liu
 */

package cn.ck.plm.process.dto;

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
}
