/*
 * Copyright (c) 2026~2028 深圳乘恺科技有限公司
 * All rights reserved.
 *
 * @author Kiween.Hu; Roney.Liu
 */

package cn.ck.plm.process.support;

import org.flowable.engine.ProcessEngine;
import org.flowable.engine.RuntimeService;
import org.flowable.engine.TaskService;
import org.flowable.engine.runtime.Execution;
import org.flowable.task.api.Task;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * 「会签式加签」的引擎行为：审批进行中往多实例里<b>再塞一个实例</b>。
 *
 * <h3>要固定下来的约定</h3>
 * <ul>
 *   <li>扩容之后 {@code nrOfInstances} 真的 +1 —— 否则新人不计入"应到人数"，
 *       比例/票数口径会把他当成"多余的票"；</li>
 *   <li>被加签人<b>直接拿到待办</b>（不是可认领）：加签的语义是"指定你也必须审"；</li>
 *   <li>原有实例一个不少：已经办完的票不受影响，活动不会因为扩容提前结束。</li>
 * </ul>
 *
 * <p>这条用例是 M4 的地基：生产实现（{@code ProcessServiceImpl#addSigner}）只在
 * "并行会签"上走这条路，其余仍走或签。
 */
class CountersignAddSignerEngineTest {

    private static final String PROCESS_KEY = "add_signer_countersign";
    private static final String ACTIVITY = "review";

    private static ProcessEngine engine;

    @BeforeAll
    static void startEngine() {
        engine = FlowableEngineSmokeTest.newEngine("add_signer_mi");
    }

    @AfterAll
    static void stopEngine() {
        if (engine != null) {
            engine.close();
        }
    }

    /**
     * 与编译产物同构：{@code flowable:collection="${ckplmApprovers}"}，
     * {@code elementVariable="ckplmApprover"}，任务 assignee 取元素变量。
     */
    private static String start() {
        String xml = """
                <?xml version="1.0" encoding="UTF-8"?>
                <definitions xmlns="http://www.omg.org/spec/BPMN/20100524/MODEL"
                             xmlns:flowable="http://flowable.org/bpmn"
                             xmlns:ckplm="http://ckplm.local/bpmn"
                             targetNamespace="http://ckplm.local/test">
                  <process id="add_signer_countersign" isExecutable="true">
                    <startEvent id="start"/>
                    <sequenceFlow id="f1" sourceRef="start" targetRef="review"/>
                    <userTask id="review" name="会签" flowable:assignee="${ckplmApprover}">
                      <multiInstanceLoopCharacteristics isSequential="false"
                          flowable:collection="${ckplmApprovers}"
                          flowable:elementVariable="ckplmApprover"/>
                    </userTask>
                    <sequenceFlow id="f2" sourceRef="review" targetRef="end"/>
                    <endEvent id="end"/>
                  </process>
                </definitions>
                """;
        engine.getRepositoryService().createDeployment()
                .addString("add_signer_countersign.bpmn20.xml", xml)
                .deploy();
        return engine.getRuntimeService()
                .startProcessInstanceByKey(PROCESS_KEY, Map.of("ckplmApprovers", List.of("user_1", "user_2")))
                .getId();
    }

    /** 与生产实现同口径：沿父链取「同活动的最上层执行」 */
    private static String rootExecutionOf(Task task) {
        RuntimeService runtime = engine.getRuntimeService();
        String id = task.getExecutionId();
        String root = null;
        for (int depth = 0; id != null && depth < 8; depth++) {
            Execution execution = runtime.createExecutionQuery().executionId(id).singleResult();
            if (execution == null) {
                break;
            }
            if (ACTIVITY.equals(execution.getActivityId())) {
                root = id;
            } else if (root != null) {
                break;
            }
            id = execution.getParentId();
        }
        return root;
    }

    @Test
    void addingInstanceGrowsOngoingCountersign() {
        String pid = start();
        TaskService taskService = engine.getTaskService();
        RuntimeService runtime = engine.getRuntimeService();

        assertEquals(2, taskService.createTaskQuery().processInstanceId(pid).count(), "初始应到两人");
        Task first = taskService.createTaskQuery().processInstanceId(pid).taskAssignee("user_1").singleResult();
        assertNotNull(first);
        String root = rootExecutionOf(first);
        assertNotNull(root, "应当能按结构定位到多实例根执行");

        // 传的是多实例根的<b>父执行</b>：Flowable 在"这个父执行的子执行"里找多实例根，
        // 直接把根自己传进去会报 "No multi instance execution found"（实测）。
        String parentOfRoot = runtime.createExecutionQuery().executionId(root).singleResult().getParentId();
        Execution added = runtime.addMultiInstanceExecution(
                ACTIVITY, parentOfRoot, Map.of("ckplmApprover", "user_3"));
        assertNotNull(added, "引擎应接受运行期扩容");

        assertEquals(3, taskService.createTaskQuery().processInstanceId(pid).count(), "新人应分到一个任务");
        Task newTask = taskService.createTaskQuery().processInstanceId(pid).taskAssignee("user_3").singleResult();
        assertNotNull(newTask, "被加签人应当直接拿到待办");
        assertEquals(ACTIVITY, newTask.getTaskDefinitionKey());

        Object instances = runtime.getVariable(root, "nrOfInstances");
        assertEquals(3, ((Number) instances).intValue(), "应到人数必须跟着 +1，否则比例口径会算错");
    }

    /**
     * 「每人都必须审」的判据：少一个人没办，多实例就不能离开 ——
     * 否则加签只是"多派了个任务"，流程照旧按原来的票数通过。
     */
    @Test
    void nodeLeavesOnlyAfterEveryInstanceCompletes() {
        String pid = start();
        TaskService taskService = engine.getTaskService();
        RuntimeService runtime = engine.getRuntimeService();

        String root = rootExecutionOf(
                taskService.createTaskQuery().processInstanceId(pid).taskAssignee("user_1").singleResult());
        String parentOfRoot = runtime.createExecutionQuery().executionId(root).singleResult().getParentId();
        runtime.addMultiInstanceExecution(ACTIVITY, parentOfRoot, Map.of("ckplmApprover", "user_3"));

        taskService.complete(taskService.createTaskQuery().processInstanceId(pid)
                .taskAssignee("user_1").singleResult().getId());
        taskService.complete(taskService.createTaskQuery().processInstanceId(pid)
                .taskAssignee("user_2").singleResult().getId());

        assertEquals(1, taskService.createTaskQuery().processInstanceId(pid).count(),
                "只该剩被加签人的那一票");
        assertNotNull(runtime.createProcessInstanceQuery().processInstanceId(pid).singleResult(),
                "还有人没办，节点不能结束 —— 这就是会签与或签的分界");

        taskService.complete(taskService.createTaskQuery().processInstanceId(pid)
                .taskAssignee("user_3").singleResult().getId());
        assertEquals(0, taskService.createTaskQuery().processInstanceId(pid).count());
    }

    /**
     * 编译产物走候选组时（节点审批人策略是角色），多实例的元素变量<b>不参与 assignee</b>，
     * 扩容出来的任务没人负责 —— 生产实现必须补一次 {@code setAssignee}，
     * 否则被加签人要去「可认领」里自己抢，与"指定你也必须审"不是一回事。
     */
    @Test
    void candidateGroupVariantNeedsExplicitAssign() {
        String xml = """
                <?xml version="1.0" encoding="UTF-8"?>
                <definitions xmlns="http://www.omg.org/spec/BPMN/20100524/MODEL"
                             xmlns:flowable="http://flowable.org/bpmn"
                             targetNamespace="http://ckplm.local/test">
                  <process id="add_signer_group" isExecutable="true">
                    <startEvent id="start"/>
                    <sequenceFlow id="f1" sourceRef="start" targetRef="review"/>
                    <userTask id="review" name="会签" flowable:candidateGroups="ROLE_REVIEWER">
                      <multiInstanceLoopCharacteristics isSequential="false"
                          flowable:collection="${ckplmApprovers}"
                          flowable:elementVariable="ckplmApprover"/>
                    </userTask>
                    <sequenceFlow id="f2" sourceRef="review" targetRef="end"/>
                    <endEvent id="end"/>
                  </process>
                </definitions>
                """;
        engine.getRepositoryService().createDeployment()
                .addString("add_signer_group.bpmn20.xml", xml)
                .deploy();
        String pid = engine.getRuntimeService()
                .startProcessInstanceByKey("add_signer_group",
                        Map.of("ckplmApprovers", List.of("user_1", "user_2")))
                .getId();

        TaskService taskService = engine.getTaskService();
        RuntimeService runtime = engine.getRuntimeService();
        Task first = taskService.createTaskQuery().processInstanceId(pid).list().get(0);
        String root = rootExecutionOf(first);
        String parentOfRoot = runtime.createExecutionQuery().executionId(root).singleResult().getParentId();

        Execution added = runtime.addMultiInstanceExecution(
                ACTIVITY, parentOfRoot, Map.of("ckplmApprover", "user_3"));
        Task created = taskService.createTaskQuery().executionId(added.getId()).singleResult();
        assertNotNull(created);
        assertNull(created.getAssignee(), "候选组产物不会按元素变量自动指派");

        taskService.setAssignee(created.getId(), "user_3");
        assertEquals(1, taskService.createTaskQuery().processInstanceId(pid)
                .taskAssignee("user_3").count(), "补指派后被加签人才进「待办」");
    }
}
