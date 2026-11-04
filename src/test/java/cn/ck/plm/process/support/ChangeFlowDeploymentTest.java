/*
 * Copyright (c) 2026~2028 深圳乘恺科技有限公司
 * All rights reserved.
 *
 * @author Kiween.Hu; Roney.Liu
 */

package cn.ck.plm.process.support;

import org.flowable.engine.ProcessEngine;
import org.flowable.engine.TaskService;
import org.flowable.engine.repository.Deployment;
import org.flowable.engine.runtime.ProcessInstance;
import org.flowable.task.api.Task;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 变更域三流程（change-request → change-notice → change-task）的<b>真实引擎</b>验收。
 *
 * <h3>为什么不能只测编译产物文本</h3>
 * <p>「多实例子流程 + 业务 key + 变量传入」这三件事在 XML 里"写得出来"不代表引擎认：
 * 集合表达式取不到会抛异常、{@code flowable:in} 的 source 若在元素作用域外求值就传不进去、
 * 业务 key 重复会让多个子实例指向同一张单。这些都只有跑一遍引擎才看得见。
 */
class ChangeFlowDeploymentTest {

    private static ProcessEngine engine;

    /** 包内流程定义在 classpath 的固定位置（随 platform-base 发布） */
    private static final String DIR = "industry-packs/platform-base/v1.0.0/processes/";

    private static final List<String> KEYS = List.of("change-request", "change-notice", "change-task");

    @BeforeAll
    static void startEngine() {
        engine = FlowableEngineSmokeTest.newEngine("change_flows");
    }

    @AfterAll
    static void stopEngine() {
        if (engine != null) {
            engine.close();
        }
    }

    @Test
    void deploysAllThreeChangeFlows() {
        for (String key : KEYS) {
            Deployment deployment = engine.getRepositoryService().createDeployment()
                    .addString(key + ".bpmn20.xml", readBpmn(key))
                    .deploy();
            assertNotNull(deployment.getId(), key + " 应能部署到引擎");
        }
    }

    /**
     * 跑通 ECN：设置审批人 → 审批通过 → 按任务清单拉起 2 个任务子流程 → 全部办完 → 通告流程结束。
     *
     * <p>断言的重点是"每个子实例各拿各的"：businessKey、传入的办理人都必须一一对应，
     * 否则就是"两个任务都落到同一个人 / 都指向同一张单"这类线上才发现的错。
     */
    @Test
    void runsNoticeWithOneToManyTaskSubProcesses() {
        deployAll();

        Map<String, Object> taskA = new HashMap<>();
        taskA.put("oid", "task-a");
        taskA.put("assignee", "alice");
        Map<String, Object> taskB = new HashMap<>();
        taskB.put("oid", "task-b");
        taskB.put("assignee", "bob");

        Map<String, Object> vars = new HashMap<>();
        vars.put("initiator", "starter");
        vars.put("changeTaskList", List.of(taskA, taskB));

        ProcessInstance notice = engine.getRuntimeService()
                .startProcessInstanceByKey("change-notice", "notice-1", vars);
        assertNotNull(notice, "通告流程应能发起");

        // ① 设置审批人：办完把审批人写进该活动的专属变量
        Task setup = singleTask(notice.getId(), "setup_1");
        assertEquals("starter", setup.getAssignee(), "「设置审批人」由发起人办理");
        engine.getTaskService().complete(setup.getId(),
                Map.of("ckplmSetupAssignees_approve_1", "approver_1"));

        // ② 审批通过
        Task approve = singleTask(notice.getId(), "approve_1");
        assertEquals("approver_1", approve.getAssignee(), "审批人应取「设置审批人」写入的变量");
        engine.getTaskService().complete(approve.getId(), Map.of("approved", true));

        // ③ 任务子流程 1:M：两个实例、两个任务、各自的办理人
        TaskService taskService = engine.getTaskService();
        List<Task> taskNodes = taskService.createTaskQuery().taskDefinitionKey("task_1").list();
        assertEquals(2, taskNodes.size(), "任务清单有 2 条，应拉起 2 个任务子流程");

        Map<String, String> assigneeByBusinessKey = new HashMap<>();
        for (Task task : taskNodes) {
            String businessKey = engine.getRuntimeService().createProcessInstanceQuery()
                    .processInstanceId(task.getProcessInstanceId())
                    .singleResult()
                    .getBusinessKey();
            assigneeByBusinessKey.put(businessKey, task.getAssignee());
        }
        assertEquals("alice", assigneeByBusinessKey.get("task-a"), "task-a 应传给 alice");
        assertEquals("bob", assigneeByBusinessKey.get("task-b"), "task-b 应传给 bob");

        // ④ 两个任务都办完，通告流程才结束
        taskNodes.forEach(task -> taskService.complete(task.getId()));
        assertNull(
                engine.getRuntimeService().createProcessInstanceQuery()
                        .processInstanceId(notice.getId()).singleResult(),
                "所有任务子流程结束后，通告流程才应结束");

        // ⑤ 结论可由历史变量读回（业务回调据此回写 ECN 状态）
        Object approved = engine.getHistoryService().createHistoricVariableInstanceQuery()
                .processInstanceId(notice.getId()).variableName("approved").singleResult().getValue();
        assertTrue(Boolean.TRUE.equals(approved), "审批通过后 approved 变量应为 true");
    }

    private void deployAll() {
        for (String key : KEYS) {
            engine.getRepositoryService().createDeployment()
                    .addString(key + ".bpmn20.xml", readBpmn(key))
                    .deploy();
        }
    }

    private Task singleTask(String instanceId, String taskDefinitionKey) {
        return engine.getTaskService().createTaskQuery()
                .processInstanceId(instanceId)
                .taskDefinitionKey(taskDefinitionKey)
                .singleResult();
    }

    private String readBpmn(String key) {
        String path = DIR + key + ".bpmn20.xml";
        try (InputStream in = getClass().getClassLoader().getResourceAsStream(path)) {
            assertNotNull(in, path + " 应随 platform-base 包发布");
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new IllegalStateException("读取流程定义失败: " + path, e);
        }
    }
}
