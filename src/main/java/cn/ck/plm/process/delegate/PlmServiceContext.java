/*
 * Copyright (c) 2025 深圳乘恺科技有限公司
 * All rights reserved.
 *
 * @author Kiween.Hu; Roney.Liu
 */

package cn.ck.plm.process.delegate;

import org.flowable.engine.delegate.DelegateExecution;

import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * 自动服务节点的执行上下文 —— 处理器只从它取输入，不直接碰引擎 API。
 *
 * <p>三样东西：<b>配置</b>（{@code flowable:field} 传来的参数）、
 * <b>作用对象</b>（流程实例关联的业务实体）、<b>执行位置</b>（哪个节点、哪个实例、谁发起的）。
 */
public class PlmServiceContext {

    /** 发起人变量名 —— Flowable 的 {@code flowable:initiator}，值是用户名 */
    private static final String VAR_INITIATOR = "initiator";

    private final DelegateExecution execution;
    /** 节点配置参数（{@code flowable:field}，已去掉 serviceId） */
    private final Map<String, String> params;
    /** 作用对象（实例关联的业务实体；可能为空） */
    private final List<PlmServiceTarget> targets;
    /** 节点名（出错时要说清是哪个节点） */
    private final String nodeName;

    public PlmServiceContext(DelegateExecution execution, Map<String, String> params,
                             List<PlmServiceTarget> targets, String nodeName) {
        this.execution = execution;
        this.params = params != null ? params : Collections.emptyMap();
        this.targets = targets != null ? targets : Collections.emptyList();
        this.nodeName = nodeName;
    }

    /** 取参数（{@code flowable:field}）；缺省返回 null，空串按 null 处理 */
    public String param(String name) {
        String value = params.get(name);
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    public Map<String, String> params() {
        return params;
    }

    public List<PlmServiceTarget> targets() {
        return targets;
    }

    public String nodeName() {
        return nodeName != null ? nodeName : "(未命名节点)";
    }

    public String processInstanceId() {
        return execution.getProcessInstanceId();
    }

    /**
     * 执行者（用户名）—— 流程发起人。
     *
     * <p>自动服务的"谁在做"就是发起人：检出/检入等操作要记操作者，
     * 检入还要求与检出人是同一人（见 CheckoutProvider）。
     */
    public String actor() {
        Object value = execution.getVariable(VAR_INITIATOR);
        return value == null ? null : value.toString();
    }

    /** 取流程变量（处理器读业务上下文时用） */
    public Object variable(String name) {
        return execution.getVariable(name);
    }

    /**
     * 流程实例所属租户（引擎的 tenantId）。
     *
     * <p>为什么要显式取它：出站调用要按"哪个租户的目标系统"去注册表里找，
     * 而异步作业线程里的 {@code TenantContext} 未必等于流程实例的租户 ——
     * 拿错租户的后果是"调到了别人家的系统"，或莫名其妙找不到系统。
     * 可能为空（历史实例没设租户），调用方需自行兜底。
     */
    public String tenantOid() {
        String tenantId = execution.getTenantId();
        if (tenantId == null) {
            return null;
        }
        String trimmed = tenantId.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    /** 节点标识：出错信息里带上，便于在流程图里定位 */
    public String nodeRef() {
        return nodeName() + "（" + processInstanceId() + "）";
    }
}
