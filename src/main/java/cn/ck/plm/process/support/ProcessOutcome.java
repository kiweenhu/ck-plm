/*
 * Copyright (c) 2026~2028 深圳乘恺科技有限公司
 * All rights reserved.
 *
 * @author Kiween.Hu; Roney.Liu
 */

package cn.ck.plm.process.support;

import java.util.Collections;
import java.util.Map;

/**
 * 一次流程实例结束时的结论 —— 业务侧回写状态的唯一输入。
 *
 * <h3>为什么由流程模块组装</h3>
 * <p>业务模块（变更 / 文档 / 通用件…）不该依赖 Flowable 的 API：一旦它们自己
 * 去 {@code HistoryService} 里翻变量，引擎版本升级、历史清理策略变化都会漏到业务代码里。
 * 这里把"实例 id、流程 key、业务 key、是否正常结束、结束时的变量快照"一次性装好，
 * 业务只做它自己的状态判断。
 *
 * <h3>为什么带变量快照</h3>
 * <p>流程"正常结束"不等于"审批通过"：本项目的流程把<b>通过和驳回都画到结束节点</b>
 * （两条出边都收口到 END），此时只能靠 {@code approved} 变量分辨结论。
 */
public class ProcessOutcome {

    private final String processInstanceId;

    /** 流程定义 key（BPMN 的 process id，如 change-notice） */
    private final String processKey;

    /** 发起时传的业务标识（业务对象 oid） */
    private final String businessKey;

    /** 发起人（用户名） */
    private final String startUserId;

    /** true = 走完整个流程；false = 被终止 / 删除 */
    private final boolean endedNormally;

    /** 结束时的流程变量快照（含 approved / lastAction / 业务带进去的变量） */
    private final Map<String, Object> variables;

    /** 触发结束的办理人（用户名）；与发起人不同 —— 活动记录要记"谁批的"，不是"谁提的" */
    private final String completedBy;

    public ProcessOutcome(String processInstanceId, String processKey, String businessKey,
                          String startUserId, boolean endedNormally, Map<String, Object> variables,
                          String completedBy) {
        this.processInstanceId = processInstanceId;
        this.processKey = processKey;
        this.businessKey = businessKey;
        this.startUserId = startUserId;
        this.endedNormally = endedNormally;
        this.variables = variables == null ? Collections.emptyMap() : Collections.unmodifiableMap(variables);
        this.completedBy = completedBy;
    }

    public String getProcessInstanceId() { return processInstanceId; }

    public String getProcessKey() { return processKey; }

    public String getBusinessKey() { return businessKey; }

    public String getStartUserId() { return startUserId; }

    public boolean isEndedNormally() { return endedNormally; }

    public Map<String, Object> getVariables() { return variables; }

    public String getCompletedBy() { return completedBy; }

    /** 取布尔变量（缺失 / 类型不符一律 false，不抛异常） */
    public boolean boolVar(String name) {
        Object value = variables.get(name);
        return value instanceof Boolean && (Boolean) value;
    }

    /** 取字符串变量 */
    public String stringVar(String name) {
        Object value = variables.get(name);
        return value == null ? null : String.valueOf(value);
    }

    @Override
    public String toString() {
        return "ProcessOutcome{" + processKey + ", instance=" + processInstanceId
                + ", businessKey=" + businessKey + ", endedNormally=" + endedNormally + "}";
    }
}
