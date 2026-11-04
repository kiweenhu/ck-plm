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
import org.flowable.engine.runtime.ProcessInstance;
import org.flowable.identitylink.api.IdentityLink;
import org.flowable.task.api.Task;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 「加签（或签）」的引擎行为：追加候选人之后，原办理人与被加签人各自看到什么。
 *
 * <h3>要固定下来的约定</h3>
 * <ul>
 *   <li><b>必须释放 assignee</b>：带着 assignee 的任务既查不到候选、也认领不了（见下方断言），
 *       所以加签要把任务转成候选任务；原办理人一并转为候选人，才不至于自己也看不到；</li>
 *   <li><b>被加签人进「可认领」</b>：通过 candidate 身份看到任务，认领即接手；</li>
 *   <li><b>谁先办谁算过</b>：认领后 assignee 变成认领人，原办理人不再持有任务，流程正常往下走。</li>
 * </ul>
 *
 * <p>本用例固定的是<b>或签</b>那条路径（单实例 / 串行会签）：多实例在进入活动时实例数就定死了，
 * 这类节点无法扩容，只能转候选。并行会签的"每人都必须审"另有一条路 ——
 * 用 {@code RuntimeService#addMultiInstanceExecution} 追加实例，见
 * {@link CountersignAddSignerEngineTest}。
 */
class AddSignerEngineTest {

    private static final String PROCESS_KEY = "add_signer";

    private static ProcessEngine engine;

    @BeforeAll
    static void startEngine() {
        engine = FlowableEngineSmokeTest.newEngine("add_signer");
    }

    @AfterAll
    static void stopEngine() {
        if (engine != null) {
            engine.close();
        }
    }

    private static ProcessInstance start() {
        String xml = """
                <?xml version="1.0" encoding="UTF-8"?>
                <definitions xmlns="http://www.omg.org/spec/BPMN/20100524/MODEL"
                             xmlns:flowable="http://flowable.org/bpmn"
                             xmlns:ckplm="http://ckplm.local/bpmn"
                             targetNamespace="http://ckplm.local/test">
                  <process id="add_signer" isExecutable="true">
                    <startEvent id="start"/>
                    <sequenceFlow id="f1" sourceRef="start" targetRef="approval"/>
                    <userTask id="approval" name="评审" flowable:assignee="user_1"/>
                    <sequenceFlow id="f2" sourceRef="approval" targetRef="end"/>
                    <endEvent id="end"/>
                  </process>
                </definitions>
                """;
        engine.getRepositoryService().createDeployment()
                .addString("add_signer.bpmn20.xml", xml)
                .deploy();
        return engine.getRuntimeService().startProcessInstanceByKey(PROCESS_KEY, Map.of());
    }

    /** 与生产实现同口径：原办理人转候选人 → 加签人加候选人 → 释放 assignee */
    private static void addSigner(TaskService taskService, Task task, String signer) {
        String original = task.getAssignee();
        if (original != null && !original.isBlank()) {
            taskService.addCandidateUser(task.getId(), original);
        }
        taskService.addCandidateUser(task.getId(), signer);
        taskService.setAssignee(task.getId(), null);
    }

    @Test
    void addingCandidateTurnsTaskIntoClaimableForBothSides() {
        String pid = start().getId();
        TaskService taskService = engine.getTaskService();
        Task task = taskService.createTaskQuery().processInstanceId(pid).singleResult();
        assertNotNull(task);
        assertEquals("user_1", task.getAssignee());

        addSigner(taskService, task, "user_2");

        // 释放 assignee 是必须的：实测「有 assignee 时候选人既查不到（taskUnassigned 过滤）
        // 也 claim 不了（TaskAlreadyClaimed）」，所以加签必须把任务转成候选任务。
        assertEquals(0, taskService.createTaskQuery().processInstanceId(pid).taskAssignee("user_1").count());
        assertEquals(1, taskService.createTaskQuery().processInstanceId(pid)
                .taskUnassigned().taskCandidateUser("user_1").count(), "原办理人仍要能看到任务");
        assertEquals(1, taskService.createTaskQuery().processInstanceId(pid)
                .taskUnassigned().taskCandidateUser("user_2").count(), "加签人从「可认领」看到任务");
        // 无关的人看不到
        assertEquals(0, taskService.createTaskQuery().processInstanceId(pid).taskCandidateUser("user_3").count());

        List<IdentityLink> links = taskService.getIdentityLinksForTask(task.getId());
        assertTrue(links.stream().anyMatch(link ->
                        "candidate".equals(link.getType()) && "user_2".equals(link.getUserId())),
                "候选人关系应落库，供待办/可认领查询使用");
    }

    @Test
    void whoeverClaimsFirstOwnsTheTask() {
        String pid = start().getId();
        TaskService taskService = engine.getTaskService();
        Task task = taskService.createTaskQuery().processInstanceId(pid).singleResult();

        addSigner(taskService, task, "user_2");
        taskService.claim(task.getId(), "user_2");

        assertEquals(0, taskService.createTaskQuery().processInstanceId(pid).taskAssignee("user_1").count(),
                "认领之后原办理人不再持有任务 —— 这正是「谁先办谁算过」");
        assertEquals(1, taskService.createTaskQuery().processInstanceId(pid).taskAssignee("user_2").count());

        RuntimeService runtime = engine.getRuntimeService();
        taskService.complete(task.getId());
        assertNull(runtime.createProcessInstanceQuery().processInstanceId(pid).singleResult(),
                "被加签人办完后流程应继续到结束");
    }
}
