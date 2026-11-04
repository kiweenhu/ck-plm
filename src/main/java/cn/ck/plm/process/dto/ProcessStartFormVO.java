/*
 * Copyright (c) 2026~2028 深圳乘恺科技有限公司
 * All rights reserved.
 *
 * @author Kiween.Hu; Roney.Liu
 */

package cn.ck.plm.process.dto;

/**
 * 发起表单上下文 —— 「发起流程」弹框据此渲染<b>开始节点配的表单</b>，
 * 让"发起时先填申请信息"这一步在设计与运行两期是同一份契约。
 *
 * <p><b>为什么需要它</b>：开始节点的 {@code formRef} 一直会被编译进 BPMN
 * （{@code <startEvent flowable:formKey="...">}），但在本接口出现之前<b>没有任何消费方</b>
 * —— 用户在设计器里选了表单，发起页却永远是写死的"对象清单 + 版本信息"，
 * 既看不到、也填不了（详见 CHANGELOG「开始节点表单接通」）。
 *
 * <p>三件事与 {@link ProcessTaskFormVO} 同源，只是主体从"任务"换成"开始事件"：
 * <ol>
 *   <li>{@code formKey}：开始事件的 {@code flowable:formKey}（= DSL 里 start 节点的
 *       {@code formRef}）。为空 = 开始节点没配表单，发起弹框不渲染表单区（老流程零影响）；</li>
 *   <li>{@code startEventId}：BPMN 开始事件 id（= DSL 节点 id，通常 {@code start_1}）——
 *       与 {@code ProcessTaskFormVO.taskDefinitionKey} 同一角色，表单按它在 DSL 里定位节点
 *       （发起页把它映射成 {@code taskDefinitionKey} 传给表单组件，组件因此不必区分两种上下文）；</li>
 *   <li>{@code dslJson}：<b>已部署那一版</b>的 DSL —— 与发起后实例实际跑的版本一致
 *       （发起总是启动最新部署版）。用模板主档的"最新版"可能与部署落后一版。</li>
 * </ol>
 *
 * <p>取值来源是<b>部署产物</b>（BPMN 原文）而非模板主档：未部署时各字段为空，
 * 发起弹框此时本来也不会给"发起"按钮。
 */
public class ProcessStartFormVO {

    /** 开始节点表单 code；null = 开始节点未配表单（发起弹框不渲染表单区） */
    private String formKey;

    /** BPMN 开始事件 id（= DSL 节点 id），表单据此在 DSL 里定位节点 */
    private String startEventId;

    /** 流程定义 key */
    private String processDefinitionKey;

    /** 流程定义版本（本次发起将会跑的部署版本） */
    private Integer processDefinitionVersion;

    /** 该部署版本的流程 DSL（JSON 字符串）；解析不到为 null */
    private String dslJson;

    // ==================== Getter / Setter ====================

    public String getFormKey() { return formKey; }
    public void setFormKey(String formKey) { this.formKey = formKey; }

    public String getStartEventId() { return startEventId; }
    public void setStartEventId(String startEventId) { this.startEventId = startEventId; }

    public String getProcessDefinitionKey() { return processDefinitionKey; }
    public void setProcessDefinitionKey(String processDefinitionKey) { this.processDefinitionKey = processDefinitionKey; }

    public Integer getProcessDefinitionVersion() { return processDefinitionVersion; }
    public void setProcessDefinitionVersion(Integer processDefinitionVersion) { this.processDefinitionVersion = processDefinitionVersion; }

    public String getDslJson() { return dslJson; }
    public void setDslJson(String dslJson) { this.dslJson = dslJson; }
}
