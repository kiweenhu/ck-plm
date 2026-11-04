/*
 * Copyright (c) 2026~2028 深圳乘恺科技有限公司
 * All rights reserved.
 *
 * @author Kiween.Hu; Roney.Liu
 */

package cn.ck.plm.process.support;

import org.flowable.bpmn.model.BpmnModel;
import org.flowable.bpmn.model.ExtensionAttribute;
import org.flowable.bpmn.model.FlowElement;
import org.flowable.bpmn.model.FlowNode;
import org.flowable.bpmn.model.InclusiveGateway;
import org.flowable.bpmn.model.ParallelGateway;
import org.flowable.bpmn.model.Process;
import org.flowable.bpmn.model.SequenceFlow;
import org.flowable.engine.RepositoryService;
import org.flowable.engine.RuntimeService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 「终止同层分支」的运行期实现。
 *
 * <h3>语义</h3>
 * <p>并行 / 包容分支里，某一条支路先走到收口边时，其它还在办的支路<b>就地结束</b>，
 * 流程从汇聚点继续。设计器上这个开关挂在连线上（{@code terminateSiblings}），
 * 编译期会在<b>源节点</b>上汇总成 {@code ckplm:terminateOn="flow_a,flow_b"}。
 *
 * <h3>为什么判据在节点上</h3>
 * <p>实测（{@code BranchTerminateEngineTest}）：引擎只保留<b>节点</b>上的自定义命名空间属性，
 * {@code sequenceFlow} 上的会被丢掉 —— 写在连线上的配置运行期根本读不到。
 * 连线上的那份只服务于前端导入（前端自己解析 XML 文本）。
 *
 * <h3>为什么是"移动"而不是"删除"</h3>
 * <p>同样实测过：删掉兄弟执行后，并行汇聚永远等不到那一支（按"到达次数"计数），流程卡死；
 * 把兄弟支路<b>移动到汇聚点</b>则是一次正常到达，计数对得上，语义也更贴切 ——
 * 终止分支是"这一支到这儿为止"，不是"这一支从未存在过"。
 *
 * <h3>失败不阻断</h3>
 * <p>收口失败（找不到汇聚点、引擎拒绝迁移）只留 WARN，绝不把已完成的办理回滚：
 * 用户点了提交，流程至少要继续走，多一条并行分支总比"提交失败"轻。
 */
@Component
public class BranchTerminator {

    private static final Logger log = LoggerFactory.getLogger(BranchTerminator.class);

    /** 编译期写在源节点上的出边汇总（逗号分隔的连线 id），见前端 namespaces.ts 的 TERMINATE_ON */
    private static final String ATTR_TERMINATE_ON = "terminateOn";

    private final RepositoryService repositoryService;
    private final RuntimeService runtimeService;

    public BranchTerminator(RepositoryService repositoryService, RuntimeService runtimeService) {
        this.repositoryService = repositoryService;
        this.runtimeService = runtimeService;
    }

    /**
     * 任务办完后调用：若该节点声明了「终止同层分支」，收掉其它还在走的分支。
     *
     * @param processInstanceId  流程实例
     * @param processDefinitionId 流程定义（读 BPMN 模型用）
     * @param taskDefinitionKey  刚办完的活动 id（节点上才有 terminateOn 声明）
     */
    public void terminateSiblings(String processInstanceId, String processDefinitionId, String taskDefinitionKey) {
        if (processInstanceId == null || processDefinitionId == null || taskDefinitionKey == null) {
            return;
        }
        try {
            doTerminate(processInstanceId, processDefinitionId, taskDefinitionKey);
        } catch (Exception e) {
            // 收口失败不能让"已经办完的这一步"跟着失败 —— 见类注释「失败不阻断」
            log.warn("终止同层分支失败（已忽略）: instance={}, activity={} — {}",
                    processInstanceId, taskDefinitionKey, e.getMessage());
        }
    }

    private void doTerminate(String processInstanceId, String processDefinitionId, String taskDefinitionKey) {
        BpmnModel model = repositoryService.getBpmnModel(processDefinitionId);
        if (model == null) {
            return;
        }
        Process process = model.getMainProcess();
        FlowElement activity = process.getFlowElement(taskDefinitionKey, true);
        String declared = attributeOf(activity, ATTR_TERMINATE_ON);
        if (declared == null || declared.isBlank()) {
            return;
        }

        List<String> activeIds = runtimeService.getActiveActivityIds(processInstanceId);
        if (activeIds.isEmpty()) {
            return;
        }

        // 1) 这次走的是哪条收口边：沿边目标向下走，命中当前活跃活动就是它
        SequenceFlow takenFlow = null;
        Set<String> keptActivities = Set.of();
        for (String flowId : declared.split(",")) {
            FlowElement element = process.getFlowElement(flowId.trim(), true);
            if (!(element instanceof SequenceFlow flow)) {
                continue;
            }
            Set<String> downstream = reachableFrom(flow.getTargetRef(), process);
            if (downstream.stream().noneMatch(activeIds::contains)) {
                continue;
            }
            takenFlow = flow;
            keptActivities = downstream;
            break;
        }
        if (takenFlow == null) {
            // 收口边没被走到（走了其它出边）：这是正常情况，不是错误
            return;
        }

        // 2) 汇聚点：没有它就无法安全收口（移到半路会让分支永远悬空）
        String joinId = nearestJoin(takenFlow.getTargetRef(), process);
        if (joinId == null) {
            log.warn("「终止同层分支」找不到并行汇聚点，跳过收口: instance={}, flow={}",
                    processInstanceId, takenFlow.getId());
            return;
        }

        // 3) 要终止的：活跃活动里，既不是汇聚点、也不属于刚走过的那条支路
        List<String> toTerminate = new ArrayList<>();
        for (String activeId : activeIds) {
            if (activeId.equals(joinId) || keptActivities.contains(activeId)) {
                continue;
            }
            toTerminate.add(activeId);
        }
        if (toTerminate.isEmpty()) {
            return;
        }

        runtimeService.createChangeActivityStateBuilder()
                .processInstanceId(processInstanceId)
                .moveActivityIdsToSingleActivityId(toTerminate, joinId)
                .changeState();
        log.info("终止同层分支: instance={}, 收口边={}, 终止的活动={}, 汇聚点={}",
                processInstanceId, takenFlow.getId(), toTerminate, joinId);
    }

    // ==================== 图遍历 ====================

    /** 从某个节点出发能到达的所有节点（含自身），用于区分"收口那条支路"和"其它支路" */
    private static Set<String> reachableFrom(String startId, Process process) {
        Set<String> visited = new LinkedHashSet<>();
        Deque<String> queue = new ArrayDeque<>();
        queue.add(startId);
        while (!queue.isEmpty()) {
            String id = queue.poll();
            if (!visited.add(id)) {
                continue;
            }
            FlowElement element = process.getFlowElement(id, true);
            if (!(element instanceof FlowNode node)) {
                continue;
            }
            for (SequenceFlow flow : node.getOutgoingFlows()) {
                queue.add(flow.getTargetRef());
            }
        }
        return visited;
    }

    /**
     * 最近的并行 / 包容汇聚网关（入边 ≥ 2）。
     *
     * <p>按广度优先找"第一个"，所以是离收口点最近的那层汇聚 —— 嵌套并行块时，
     * 先收内层（内层收完外层自然继续），不会一步跳到最外层。
     */
    private static String nearestJoin(String startId, Process process) {
        Set<String> visited = new LinkedHashSet<>();
        Deque<String> queue = new ArrayDeque<>();
        queue.add(startId);
        while (!queue.isEmpty()) {
            String id = queue.poll();
            if (!visited.add(id)) {
                continue;
            }
            FlowElement element = process.getFlowElement(id, true);
            if (!(element instanceof FlowNode node)) {
                continue;
            }
            if (isJoinGateway(node)) {
                return id;
            }
            for (SequenceFlow flow : node.getOutgoingFlows()) {
                queue.add(flow.getTargetRef());
            }
        }
        return null;
    }

    /**
     * 并行 / 包容网关且入边 ≥ 2 —— 只有这两种网关在"等齐所有分支"，也才有可收的分支。
     * 排他网关的多入边是"择一而来"，不存在等齐，不能当汇聚点。
     */
    private static boolean isJoinGateway(FlowNode node) {
        boolean branchGateway = node instanceof ParallelGateway || node instanceof InclusiveGateway;
        return branchGateway && node.getIncomingFlows().size() >= 2;
    }

    /** 读扩展属性：按属性名找、跨命名空间（与 ProcessServiceImpl / RejectRouter 同口径） */
    private static String attributeOf(FlowElement element, String name) {
        if (element == null) {
            return null;
        }
        Map<String, List<ExtensionAttribute>> attributes = element.getAttributes();
        if (attributes == null) {
            return null;
        }
        for (List<ExtensionAttribute> group : attributes.values()) {
            for (ExtensionAttribute attribute : group) {
                if (name.equals(attribute.getName())) {
                    return attribute.getValue();
                }
            }
        }
        return null;
    }
}
