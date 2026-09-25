/*
 * Copyright (c) 2025 深圳乘恺科技有限公司
 * All rights reserved.
 *
 * @author Kiween.Hu; Roney.Liu
 */

package cn.ck.plm.process.reject;

import cn.ck.plm.process.entity.ProcessNodeLog;
import cn.ck.plm.process.service.api.ProcessNodeLogService;
import org.flowable.bpmn.model.BpmnModel;
import org.flowable.bpmn.model.ExtensionAttribute;
import org.flowable.bpmn.model.FlowElement;
import org.flowable.bpmn.model.SequenceFlow;
import org.flowable.bpmn.model.UserTask;
import org.flowable.engine.HistoryService;
import org.flowable.engine.RepositoryService;
import org.flowable.engine.RuntimeService;
import org.flowable.engine.TaskService;
import org.flowable.engine.history.HistoricActivityInstance;
import org.flowable.engine.runtime.ChangeActivityStateBuilder;
import org.flowable.task.api.Task;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 驳回路由 —— 把"驳回"从一句配置变成流程真的退回去（运行期跳转）。
 *
 * <h3>为什么统一用运行期跳转</h3>
 * <p>驳回目标有三种（发起人 / 上一步 / 指定节点），其中两种<b>不可能在编译期画成回退线</b>：
 * <ul>
 *   <li><b>上一步</b>＝"实际走过的上一个办理节点"：并行分支、会签、跳转过的流程里，
 *       图上的前驱节点不等于实际走过的那个 —— 只有运行历史知道；</li>
 *   <li><b>发起人</b>：发起本身不是流程里的节点（发起人在业务对象上点"发起"），
 *       直连开始事件的回退线在 BPMN 里非法。所以"退回发起人"落到<b>发起人办理节点</b>
 *       （审批人策略＝发起人的那个活动）；本流程没有这样的节点时明确报错，而不是悄悄当成通过。</li>
 * </ul>
 * <p>三种目标因此统一走 Flowable 的活动跳转（{@code changeActivityState}），
 * 编译层只保留 {@code ckplm:reject} 声明 —— 顺带一个好处：<b>已部署的老流程不必重新部署</b>，
 * 只要它的 BPMN 里带着这条属性（设计器编译的都有）就能生效。
 *
 * <h3>不配驳回目标的流程照旧</h3>
 * <p>属性缺失（老流程、手写 BPMN）→ 本类不介入，仍按"写 {@code approved=false} + 图上的条件分支"走。
 * 那些用 rejectEnd 之类分支手写的流程因此不受影响。
 */
@Component
public class RejectRouter {

    private static final Logger log = LoggerFactory.getLogger(RejectRouter.class);

    private static final String ATTR_REJECT = "reject";
    private static final String ATTR_ASSIGNEE = "assignee";
    /** 连线上的路由属性（编译层由 DSL 的 edge.route 写入） */
    private static final String ATTR_ROUTE = "route";
    /** 路由取值之一：驳回（见前端 dsl-core 的 EdgeRoute） */
    private static final String ROUTE_REJECT = "REJECT";

    /** 审批人策略：发起人（编译层把它写成 ${initiator}） */
    private static final String STRATEGY_INITIATOR = "INITIATOR";
    /** Flowable 内置的发起人变量名（编译层 flowable:initiator="initiator"） */
    private static final String VAR_INITIATOR = "initiator";

    private static final String BPMN_USER_TASK = "userTask";

    private final RepositoryService repositoryService;
    private final RuntimeService runtimeService;
    private final HistoryService historyService;
    private final TaskService taskService;
    private final ProcessNodeLogService nodeLogService;

    public RejectRouter(RepositoryService repositoryService,
                        RuntimeService runtimeService,
                        HistoryService historyService,
                        TaskService taskService,
                        ProcessNodeLogService nodeLogService) {
        this.repositoryService = repositoryService;
        this.runtimeService = runtimeService;
        this.historyService = historyService;
        this.taskService = taskService;
        this.nodeLogService = nodeLogService;
    }

    /**
     * 该活动的驳回配置（没配返回 null）。
     *
     * <p>注意 {@code enabled=false} 也算"配了"：编译层会把它写出来（{@code {"enabled":false}}），
     * 于是运行期能区分"明确关掉了驳回"与"老流程压根没这回事" —— 前者要拒绝，后者要走老口径，
     * 混为一谈会让老流程的驳回突然失效。
     */
    public RejectSpec specOf(String processDefinitionId, String activityId) {
        FlowElement element = elementOf(processDefinitionId, activityId);
        return element == null ? null : RejectSpec.parse(attributeOf(element, ATTR_REJECT));
    }

    /**
     * 执行一次驳回：按节点配置把流程从当前活动退回到目标活动。
     *
     * @return true ＝ 已按配置退回（调用方不要再 {@code complete} 这个任务）；
     *         false ＝ 该节点没有驳回配置，调用方按老口径处理
     * @throws IllegalArgumentException 配置不允许驳回 / 意见必填未填 / 目标无法定位
     *                                  —— 消息是给办理人看的，必须说清原因与怎么办
     */
    public boolean reject(Task task, String comment) {
        String processDefinitionId = task.getProcessDefinitionId();
        String activityId = task.getTaskDefinitionKey();
        String instanceId = task.getProcessInstanceId();

        // 设计者已经把「驳回」画成了一条出边 → 本类不介入：
        // 让引擎按 ${!approved} 走那条边，边上接着的自动化节点（设置状态、撤回电子签名…）
        // 才有机会执行。设计期也禁止"驳回边 + 驳回目标"同时存在（见 validate 的 EDGE_ROUTE_CONFLICT）
        if (hasRejectRoute(processDefinitionId, activityId)) {
            return false;
        }

        RejectSpec spec = specOf(processDefinitionId, activityId);
        if (spec == null) {
            return false;   // 老流程：交给调用方（approved 变量 + 图上条件）
        }

        String nodeName = nodeNameOf(processDefinitionId, activityId);
        if (!spec.isEnabled()) {
            throw new IllegalArgumentException("节点「" + nodeName + "」没有开启驳回"
                    + "（设计器里该节点的「允许驳回」未勾选）。请改用「同意」，或让流程设计者开启驳回");
        }
        if (spec.isCommentRequired() && (comment == null || comment.trim().isEmpty())) {
            throw new IllegalArgumentException("节点「" + nodeName + "」要求驳回时填写意见，"
                    + "请说明驳回理由后再提交");
        }

        // 节点上有 reject 声明、却没写"退回目标"，而上面也没读到「驳回」出边 →
        // **不拦，交给引擎按 ${!approved} 条件路由**：图上真有那条驳回边时（属性读不到也）照样走得通，
        // 边后面挂的自动化节点（设置状态、撤回电子签名…）也才有机会执行。
        //
        // 这里原先一律抛「驳回目标没设置或无法识别」，结果"驳回边 + 自定义属性读不到"的流程
        // 被彻底卡死在办理页 —— 经办人连"同意"以外什么都做不了。真要拦的形态
        //（开启了允许驳回、却既无目标也无驳回边）已由设计期校验 REJECT_NO_TARGET 拦在保存前，
        // 运行期再报一次只会挡住合法流程。
        if (spec.getTarget() == null) {
            log.warn("驳回未在节点上配目标，按图上的条件路由处理（未走运行期跳转）: instance={}, node={}",
                    instanceId, nodeName);
            return false;
        }

        String target = resolveTarget(instanceId, processDefinitionId, activityId, spec, nodeName);
        if (target.equals(activityId)) {
            throw new IllegalArgumentException("节点「" + nodeName + "」的驳回目标是它自己（"
                    + spec.targetLabel() + "），退回去等于原地不动。请让流程设计者改选其它目标");
        }

        // 驳回也是一次"结论"：写进流程变量，历史与界面才看得出这一步是被驳回的
        // （与老口径一致：老口径写的也是 approved=false）
        taskService.addComment(task.getId(), instanceId, comment);
        runtimeService.setVariables(instanceId, Map.of("approved", false));

        try {
            ChangeActivityStateBuilder builder = runtimeService.createChangeActivityStateBuilder()
                    .processInstanceId(instanceId);
            builder.moveActivityIdTo(activityId, target).changeState();
        } catch (RuntimeException e) {
            // 引擎拒绝跳转（如会签多实例的形态不支持）时，给办理人一句能读懂的话，
            // 原始异常留在应用日志里给运维 —— 直接把引擎异常抛到界面等于把排查工作丢给用户
            log.error("驳回跳转失败: instance={}, from={}, to={}, error={}",
                    instanceId, activityId, target, e.getMessage(), e);
            throw new IllegalArgumentException("驳回失败：流程引擎无法把「" + nodeName + "」退回到「"
                    + nameOf(elementOf(processDefinitionId, target)) + "」（" + spec.targetLabel()
                    + "）。该节点可能是并行会签/多实例，请联系管理员或改用「同意」");
        }

        nodeLogService.info(instanceId, activityId, nodeName, ProcessNodeLog.SOURCE_SYSTEM,
                "已驳回：退回到「" + nameOf(elementOf(processDefinitionId, target)) + "」（目标＝"
                        + spec.targetLabel() + "）");
        log.info("驳回跳转: instance={}, from={}, to={}, target={}",
                instanceId, activityId, target, spec.getTarget());
        return true;
    }

    // ==================== 目标解析 ====================

    private String resolveTarget(String instanceId, String processDefinitionId, String activityId,
                                 RejectSpec spec, String nodeName) {
        String target = spec.getTarget();
        if (RejectSpec.TARGET_NODE.equals(target)) {
            return resolveNodeTarget(processDefinitionId, spec, nodeName);
        }
        if (RejectSpec.TARGET_PREVIOUS.equals(target)) {
            return resolvePreviousTarget(instanceId, activityId, nodeName);
        }
        if (RejectSpec.TARGET_INITIATOR.equals(target)) {
            return resolveInitiatorTarget(processDefinitionId, nodeName);
        }
        throw new IllegalArgumentException("节点「" + nodeName + "」的驳回目标没设置或无法识别（"
                + spec.targetLabel() + "），请让流程设计者重新选择驳回目标");
    }

    /** 指定节点：必须存在，且是能产生任务的人工活动 */
    private String resolveNodeTarget(String processDefinitionId, RejectSpec spec, String nodeName) {
        String targetNodeId = spec.getTargetNodeId();
        if (targetNodeId == null) {
            throw new IllegalArgumentException("节点「" + nodeName + "」选了「指定节点」驳回，"
                    + "却没指定退回到哪个节点，请让流程设计者补上目标节点");
        }
        FlowElement element = elementOf(processDefinitionId, targetNodeId);
        if (element == null) {
            throw new IllegalArgumentException("节点「" + nodeName + "」指定的驳回目标「" + targetNodeId
                    + "」在当前流程版本里不存在（可能改版时删掉了该节点），请让流程设计者重新指定");
        }
        if (!(element instanceof UserTask)) {
            throw new IllegalArgumentException("节点「" + nodeName + "」指定的驳回目标「"
                    + nameOf(element) + "」不是人工活动，退回去没有人能办理；请让流程设计者改选审批/办理节点");
        }
        return targetNodeId;
    }

    /**
     * 上一步：该实例<b>实际走过</b>的上一个已完成人工活动。
     *
     * <p>从历史里按开始时间倒着找第一个"非当前活动、且已完成"的 userTask。
     * 不取图上的前驱节点：并行/会签/跳转过的流程里，"图上前面那个"不等于"实际办过那个"。
     * 必须按实例过滤 —— 否则会退到别人的流程里去。
     */
    private String resolvePreviousTarget(String instanceId, String activityId, String nodeName) {
        List<HistoricActivityInstance> history = historyService.createHistoricActivityInstanceQuery()
                .processInstanceId(instanceId)
                .activityType(BPMN_USER_TASK)
                .orderByHistoricActivityInstanceStartTime().desc()
                .list();
        for (HistoricActivityInstance instance : history) {
            if (activityId.equals(instance.getActivityId())) {
                continue;
            }
            if (instance.getEndTime() != null) {
                return instance.getActivityId();
            }
        }
        throw new IllegalArgumentException("节点「" + nodeName + "」要退回到「上一步」，"
                + "但它之前还没有办过任何人工节点（它是流程里的第一步），无处可退；"
                + "请让流程设计者把驳回目标改为「指定节点」");
    }

    /**
     * 发起人：定位流程里的"发起人办理节点"（审批人策略＝发起人，编译后即 {@code flowable:assignee="${initiator}"}）。
     *
     * <p>为什么不是"跳回开始事件"：开始事件不产生任务，退回去没有人能办事；
     * 而发起人在本平台是"在业务对象上点发起"的那个人，不是一个流程节点。
     * 因此"退回发起人"的落点只能是流程里那个"由发起人办理"的活动
     * （通常就是"发起人修订 / 重新提交"那一步）。<b>没有这样的节点时明确报错</b>：
     * 悄悄当成通过、或退到一个别人办不了的地方，都比报错更伤人。
     */
    private String resolveInitiatorTarget(String processDefinitionId, String nodeName) {
        BpmnModel model = bpmnModelOf(processDefinitionId);
        if (model == null || model.getMainProcess() == null) {
            throw new IllegalArgumentException("读不到流程定义，无法定位「发起人」节点");
        }
        for (FlowElement element : model.getMainProcess().getFlowElements()) {
            if (element instanceof UserTask && isInitiatorTask((UserTask) element)) {
                return element.getId();
            }
        }
        throw new IllegalArgumentException("节点「" + nodeName + "」要退回到「发起人」，"
                + "但本流程里没有由发起人办理的节点（没有审批人策略为「发起人」的活动），退回去没有人能办；"
                + "请让流程设计者改选「上一步」或「指定节点」，或在流程里加一个由发起人办理的节点");
    }

    /** 该人工活动是不是"由发起人办理"：优先看 ckplm:assignee 的策略，退化看 flowable:assignee 表达式 */
    private boolean isInitiatorTask(UserTask task) {
        String spec = attributeOf(task, ATTR_ASSIGNEE);
        if (spec != null && spec.contains("\"" + STRATEGY_INITIATOR + "\"")) {
            return true;
        }
        String assignee = task.getAssignee();
        return assignee != null && assignee.contains("${" + VAR_INITIATOR + "}");
    }

    // ==================== 模型读取 ====================

    /**
     * 该活动是否已经把「驳回」画成了一条出边。
     *
     * <p>判据是编译层写在 {@code <sequenceFlow>} 上的 {@code ckplm:route="REJECT"}
     * ——比去猜条件表达式（{@code ${!approved}}）稳：条件可以被别的工具改写，
     * 而路由声明是设计器的语义出口。
     *
     * <p><b>但自定义属性不一定读得到</b>：{@code ckplm:route} 是自定义命名空间属性，
     * 引擎解析 BPMN 时对 sequenceFlow 上的这类属性是否保留，与解析器实现有关 ——
     * 实测部署 XML 里明明写着 {@code ckplm:route="REJECT"}，运行期却取不到，
     * 于是这里返回 false、流程落到"节点上的驳回目标"那条路，报出
     * 「驳回目标没设置或无法识别」把经办人挡住（节点其实画了驳回边）。
     * 所以补两条不依赖自定义属性的判据（见 {@link #isRejectFlow}）。
     */
    private boolean hasRejectRoute(String processDefinitionId, String activityId) {
        BpmnModel model = bpmnModelOf(processDefinitionId);
        if (model == null || model.getMainProcess() == null) {
            return false;
        }
        for (FlowElement element : model.getMainProcess().getFlowElements()) {
            if (!(element instanceof SequenceFlow)) {
                continue;
            }
            SequenceFlow flow = (SequenceFlow) element;
            if (!activityId.equals(flow.getSourceRef())) {
                continue;
            }
            if (isRejectFlow(flow)) {
                return true;
            }
        }
        return false;
    }

    /** 条件表达式里的驳回判据：编译层写的是 ${!approved}（与通过的 ${approved} 互斥） */
    private static final String CONDITION_REJECT = "!approved";
    /** 连线名判据：设计器把驳回边命名为「驳回」，而 name 是引擎必定保留的属性 */
    private static final String NAME_REJECT = "驳回";
    /** 自定义属性可能以带前缀的形式存进模型（{@code ckplm:route}） */
    private static final String ATTR_ROUTE_PREFIXED = "ckplm:" + ATTR_ROUTE;

    /**
     * 一条出边是不是「驳回」那条 —— 三个判据任一成立即认。
     *
     * <p>为什么三个都认：属性读取受解析器实现影响（见 {@link #hasRejectRoute} 的说明），
     * 条件表达式可能被别的工具改写，而连线名是设计器的显式命名。任一成立都说明
     * "设计者把驳回画成了一条边"，此时本类不该介入 —— 让引擎按条件走那条边，
     * 边上挂的自动化节点（设置状态、撤回电子签名…）才有机会执行。
     *
     * <p>不会误判「通过」那条边：它的 route 是 PASS、条件是 {@code ${approved}}（不含 {@code !approved}）、
     * 名字是「通过」。
     */
    private boolean isRejectFlow(SequenceFlow flow) {
        if (ROUTE_REJECT.equals(attributeOf(flow, ATTR_ROUTE))
                || ROUTE_REJECT.equals(attributeOf(flow, ATTR_ROUTE_PREFIXED))) {
            return true;
        }
        String condition = flow.getConditionExpression();
        if (condition != null && condition.contains(CONDITION_REJECT)) {
            return true;
        }
        String name = flow.getName();
        return name != null && name.contains(NAME_REJECT);
    }

    private BpmnModel bpmnModelOf(String processDefinitionId) {
        if (processDefinitionId == null) {
            return null;
        }
        try {
            return repositoryService.getBpmnModel(processDefinitionId);
        } catch (Exception e) {
            log.warn("读取流程定义 BPMN 失败: pdId={} error={}", processDefinitionId, e.getMessage());
            return null;
        }
    }

    private FlowElement elementOf(String processDefinitionId, String activityId) {
        BpmnModel model = bpmnModelOf(processDefinitionId);
        if (model == null || model.getMainProcess() == null || activityId == null) {
            return null;
        }
        try {
            return model.getMainProcess().getFlowElement(activityId, true);
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * 读扩展属性。
     *
     * <p>按<b>属性名</b>找而不是按命名空间找：Flowable 把扩展属性按命名空间分组存放，
     * 而命名空间常量前后端各有一份，写错一个字符就会变成"配了却读不到"（这类静默失配最难查）。
     */
    private static String attributeOf(FlowElement element, String name) {
        Map<String, List<ExtensionAttribute>> attributes = element.getAttributes();
        if (attributes == null) {
            return null;
        }
        for (List<ExtensionAttribute> group : new ArrayList<>(attributes.values())) {
            for (ExtensionAttribute attribute : group) {
                if (name.equals(attribute.getName())) {
                    return attribute.getValue();
                }
            }
        }
        return null;
    }

    private String nodeNameOf(String processDefinitionId, String activityId) {
        return nameOf(elementOf(processDefinitionId, activityId));
    }

    private static String nameOf(FlowElement element) {
        if (element == null) {
            return "（未知节点）";
        }
        String name = element.getName();
        return name != null && !name.trim().isEmpty() ? name : element.getId();
    }
}
