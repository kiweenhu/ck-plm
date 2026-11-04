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
import org.flowable.engine.ProcessEngine;
import org.flowable.engine.RepositoryService;
import org.flowable.engine.RuntimeService;
import org.flowable.engine.TaskService;
import org.flowable.engine.runtime.ProcessInstance;
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
 * 「终止同层分支」的引擎行为实验：先弄清 Flowable 在并行结构里怎么算汇聚，再谈实现。
 *
 * <h3>要回答的问题</h3>
 * <p>设计器的语义是"这一支先到，收掉同层其它还在走的支路"。落到引擎上只有两条路：
 * <ol>
 *   <li><b>删掉</b>兄弟分支的执行（{@code ExecutionEntity#deleteCascade}）；</li>
 *   <li>把兄弟分支<b>移动</b>到汇聚点（{@code ChangeActivityStateBuilder}）。</li>
 * </ol>
 * <p>第一条最直观，但并行汇聚的计数如果按"曾有几个分支"算，删掉一个就会永远等不齐 ——
 * 那正是最典型的"配了之后流程卡死"。本用例先在真引擎上把这件事问清楚。
 */
class BranchTerminateEngineTest {

    private static ProcessEngine engine;

    @BeforeAll
    static void startEngine() {
        engine = FlowableEngineSmokeTest.newEngine("branch_terminate");
    }

    @AfterAll
    static void stopEngine() {
        if (engine != null) {
            engine.close();
        }
    }

    /** start → 并行分叉 → task_a / task_b → 并行汇聚 → end */
    private static String parallelXml() {
        return """
                <?xml version="1.0" encoding="UTF-8"?>
                <definitions xmlns="http://www.omg.org/spec/BPMN/20100524/MODEL"
                             xmlns:flowable="http://flowable.org/bpmn"
                             xmlns:ckplm="http://ckplm.local/bpmn"
                             targetNamespace="http://ckplm.local/test">
                  <process id="branch_par" name="并行" isExecutable="true">
                    <startEvent id="start"/>
                    <sequenceFlow id="f1" sourceRef="start" targetRef="fork"/>
                    <parallelGateway id="fork"/>
                    <sequenceFlow id="f_a" sourceRef="fork" targetRef="task_a"/>
                    <sequenceFlow id="f_b" sourceRef="fork" targetRef="task_b"/>
                    <userTask id="task_a" name="分支A" flowable:assignee="${initiator}"/>
                    <userTask id="task_b" name="分支B" flowable:assignee="${initiator}"/>
                    <sequenceFlow id="f_a2" sourceRef="task_a" targetRef="join"/>
                    <sequenceFlow id="f_b2" sourceRef="task_b" targetRef="join"/>
                    <parallelGateway id="join"/>
                    <sequenceFlow id="f3" sourceRef="join" targetRef="end"/>
                    <endEvent id="end"/>
                  </process>
                </definitions>
                """;
    }

    /**
     * @param deploymentName 仅用于区分两次部署的资源名 —— 流程的 id 始终是 {@code branch_par}，
     *                       启动时按它取最新版本（同名再部署只是版本递增，不会冲突）
     */
    private static ProcessInstance start(String deploymentName) {
        engine.getRepositoryService().createDeployment()
                .addString(deploymentName + ".bpmn20.xml", parallelXml())
                .deploy();
        return engine.getRuntimeService().startProcessInstanceByKey("branch_par", Map.of("initiator", "user_1"));
    }

    private static Task taskOf(String processInstanceId, String taskDefinitionKey) {
        return engine.getTaskService().createTaskQuery()
                .processInstanceId(processInstanceId)
                .taskDefinitionKey(taskDefinitionKey)
                .singleResult();
    }

    @Test
    void baselineBothBranchesRunToJoin() {
        ProcessInstance instance = start("branch_par_baseline");
        String pid = instance.getId();
        RuntimeService runtime = engine.getRuntimeService();
        TaskService taskService = engine.getTaskService();

        assertEquals(2, taskService.createTaskQuery().processInstanceId(pid).count(), "并行两支各一个任务");

        taskService.complete(taskOf(pid, "task_a").getId());
        assertNotNull(
                runtime.createProcessInstanceQuery().processInstanceId(pid).singleResult(),
                "只完成一支时流程应停在汇聚点等另一支");
        assertEquals(1, taskService.createTaskQuery().processInstanceId(pid).count());

        taskService.complete(taskOf(pid, "task_b").getId());
        assertNull(
                runtime.createProcessInstanceQuery().processInstanceId(pid).singleResult(),
                "两支都完成后流程应结束");
    }

    /**
     * 结论性实验：把兄弟分支"移动到汇聚点"（而不是删掉它）。
     *
     * <p><b>为什么不是删执行</b>：同一份流程上实测过 {@code deleteExecutionAndRelatedData}，
     * 兄弟执行消失后并行汇聚仍在等那一支（实例卡在 join）—— 汇聚按"到达次数"计数，
     * 删掉一个等于永远少一次到达。移动则是一次正常到达：计数对得上，流程能往下走。
     *
     * <p>语义上也更贴切：终止分支 = "这一支就到这儿了"，而不是"这一支从未存在过"。
     */
    /**
     * 关键前提：运行期到底读不读得到自定义命名空间属性。
     *
     * <p>后端读 {@code ckplm:route}（写在 sequenceFlow 上）时踩过"XML 里明明有、运行期读不到"，
     * 所以"终止同层分支"的信息<b>额外写了一份在源节点上</b>（{@code ckplm:terminateOn}）。
     * 本用例把两条通道都验一遍：节点上的能读到、连线上的不一定 —— 结论决定实现读哪一份。
     */
    @Test
    void readsCkplmExtensionAttributesFromDeployedModel() {
        RepositoryService repository = engine.getRepositoryService();
        String xml = """
                <?xml version="1.0" encoding="UTF-8"?>
                <definitions xmlns="http://www.omg.org/spec/BPMN/20100524/MODEL"
                             xmlns:flowable="http://flowable.org/bpmn"
                             xmlns:ckplm="http://ckplm.local/bpmn"
                             targetNamespace="http://ckplm.local/test">
                  <process id="attr_probe" isExecutable="true" ckplm:variables="[]">
                    <startEvent id="start"/>
                    <sequenceFlow id="f1" sourceRef="start" targetRef="task_a"/>
                    <userTask id="task_a" name="A" ckplm:terminateOn="f_a2" ckplm:nodeType="TASK"/>
                    <sequenceFlow id="f_a2" sourceRef="task_a" targetRef="end"
                                  ckplm:condition="{&quot;mode&quot;:&quot;FIELD&quot;}"
                                  ckplm:terminateSiblings="true"/>
                    <endEvent id="end"/>
                  </process>
                </definitions>
                """;
        repository.createDeployment().addString("attr_probe.bpmn20.xml", xml).deploy();

        String processDefinitionId = repository.createProcessDefinitionQuery()
                .processDefinitionKey("attr_probe")
                .latestVersion()
                .singleResult()
                .getId();
        BpmnModel model = repository.getBpmnModel(processDefinitionId);

        FlowElement task = model.getMainProcess().getFlowElement("task_a");
        assertEquals("f_a2", attributeOf(task, "terminateOn"), "节点上的 ckplm 属性必须可读（运行期的判据）");
        assertEquals("TASK", attributeOf(task, "nodeType"));

        // 连线上的自定义属性会被引擎丢掉（实测）—— 所以：
        //   · 运行期判据只能放节点上（terminateOn 就是这么来的）；
        //   · 连线上的 ckplm:condition / ckplm:terminateSiblings 仅供<b>前端导入</b>使用
        //     （前端自己解析 XML 文本，不经过 Flowable），不影响任何运行期行为。
        FlowElement flow = model.getMainProcess().getFlowElement("f_a2");
        assertNull(attributeOf(flow, "condition"), "sequenceFlow 上的自定义属性运行时读不到（已知且可接受）");
    }

    /** 与 ProcessServiceImpl / RejectRouter 同口径：按属性名找，跨命名空间 */
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

    /**
     * 正式实现（{@link BranchTerminator}）跑真引擎：收口后兄弟支路消失、流程能走完。
     *
     * <p>流程：start → 并行分叉 → [task_a → task_a2 → 汇聚] / [task_b → 汇聚] → end；
     * {@code task_a} 上声明 {@code ckplm:terminateOn="f_a2"}（即 task_a 办完就往收口边走）。
     */
    @Test
    void terminatorClosesSiblingBranchAfterTaskCompleted() {
        String xml = """
                <?xml version="1.0" encoding="UTF-8"?>
                <definitions xmlns="http://www.omg.org/spec/BPMN/20100524/MODEL"
                             xmlns:flowable="http://flowable.org/bpmn"
                             xmlns:ckplm="http://ckplm.local/bpmn"
                             targetNamespace="http://ckplm.local/test">
                  <process id="branch_close" isExecutable="true">
                    <startEvent id="start"/>
                    <sequenceFlow id="f1" sourceRef="start" targetRef="fork"/>
                    <parallelGateway id="fork"/>
                    <sequenceFlow id="f_a" sourceRef="fork" targetRef="task_a"/>
                    <sequenceFlow id="f_b" sourceRef="fork" targetRef="task_b"/>
                    <userTask id="task_a" name="分支A" flowable:assignee="${initiator}" ckplm:terminateOn="f_a2"/>
                    <userTask id="task_b" name="分支B" flowable:assignee="${initiator}"/>
                    <sequenceFlow id="f_a2" sourceRef="task_a" targetRef="task_a2" ckplm:terminateSiblings="true"/>
                    <sequenceFlow id="f_b2" sourceRef="task_b" targetRef="join"/>
                    <userTask id="task_a2" name="分支A收尾" flowable:assignee="${initiator}"/>
                    <sequenceFlow id="f_a3" sourceRef="task_a2" targetRef="join"/>
                    <parallelGateway id="join"/>
                    <sequenceFlow id="f3" sourceRef="join" targetRef="end"/>
                    <endEvent id="end"/>
                  </process>
                </definitions>
                """;
        RepositoryService repository = engine.getRepositoryService();
        repository.createDeployment().addString("branch_close.bpmn20.xml", xml).deploy();

        RuntimeService runtime = engine.getRuntimeService();
        TaskService taskService = engine.getTaskService();
        ProcessInstance instance = runtime.startProcessInstanceByKey("branch_close", Map.of("initiator", "user_1"));
        String pid = instance.getId();

        Task taskA = taskOf(pid, "task_a");
        taskService.complete(taskA.getId());

        BranchTerminator terminator = new BranchTerminator(repository, runtime);
        terminator.terminateSiblings(pid, taskA.getProcessDefinitionId(), "task_a");

        List<Task> afterClose = taskService.createTaskQuery().processInstanceId(pid).list();
        assertEquals(1, afterClose.size(), "兄弟分支的待办应被收掉，只剩收口支路的下一步");
        assertEquals("task_a2", afterClose.get(0).getTaskDefinitionKey());

        taskService.complete(afterClose.get(0).getId());
        assertNull(runtime.createProcessInstanceQuery().processInstanceId(pid).singleResult(),
                "收口支路办完后流程应当结束");
    }

    /** 没有声明 terminateOn 的节点：办理后不应动任何分支 */
    @Test
    void terminatorDoesNothingWithoutDeclaration() {
        ProcessInstance instance = start("branch_par_noop");
        String pid = instance.getId();
        RuntimeService runtime = engine.getRuntimeService();
        TaskService taskService = engine.getTaskService();

        Task taskA = taskOf(pid, "task_a");
        taskService.complete(taskA.getId());

        new BranchTerminator(engine.getRepositoryService(), runtime)
                .terminateSiblings(pid, taskA.getProcessDefinitionId(), "task_a");

        assertEquals(1, taskService.createTaskQuery().processInstanceId(pid).count(),
                "未声明的节点不应影响兄弟分支");
        assertNotNull(runtime.createProcessInstanceQuery().processInstanceId(pid).singleResult());
    }

    @Test
    void movingSiblingToJoinKeepsJoinConsistent() {
        ProcessInstance instance = start("branch_par_move");
        String pid = instance.getId();
        RuntimeService runtime = engine.getRuntimeService();
        TaskService taskService = engine.getTaskService();

        runtime.createChangeActivityStateBuilder()
                .processInstanceId(pid)
                .moveActivityIdTo("task_b", "join")
                .changeState();

        List<Task> remaining = taskService.createTaskQuery().processInstanceId(pid).list();
        assertEquals(1, remaining.size(), "被终止分支上的任务应当消失");
        assertEquals("task_a", remaining.get(0).getTaskDefinitionKey());

        taskService.complete(remaining.get(0).getId());
        assertNull(
                runtime.createProcessInstanceQuery().processInstanceId(pid).singleResult(),
                "移动到达汇聚点后，剩下一支办完流程应当结束");
    }
}
