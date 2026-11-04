/*
 * Copyright (c) 2026~2028 深圳乘恺科技有限公司
 * All rights reserved.
 *
 * @author Kiween.Hu; Roney.Liu
 */

package cn.ck.plm.process.support;

import org.flowable.engine.ProcessEngine;
import org.flowable.engine.ProcessEngineConfiguration;
import org.flowable.engine.RepositoryService;
import org.flowable.engine.RuntimeService;
import org.flowable.engine.TaskService;
import org.flowable.engine.impl.cfg.StandaloneInMemProcessEngineConfiguration;
import org.flowable.engine.repository.Deployment;
import org.flowable.engine.runtime.ProcessInstance;
import org.flowable.task.api.Task;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * 引擎测试基建的冒烟用例：内存 H2 上起一个真 Flowable 引擎，部署并跑通一条最简流程。
 *
 * <h3>为什么需要它</h3>
 * <p>并行分支终止、加签这类特性，靠 mock 只能验"我调了哪个 API"，验不出"引擎是否按预期走"——
 * 并行汇聚的计数、会签多实例的执行树、被删分支的局部变量，这些都是引擎内部行为。
 * 本用例先把"能在本机跑真引擎"这件事固定下来；后续的引擎级用例都复用
 * {@link #newEngine(String)}，各自用独立的库名，互不干扰。
 *
 * <h3>不依赖 Spring 上下文</h3>
 * <p>用 {@code StandaloneInMem} 而不是 {@code @SpringBootTest}：测试只关心引擎语义，
 * 拉整个 Spring 容器只会更慢、更容易被无关配置影响。
 */
class FlowableEngineSmokeTest {

    private static ProcessEngine engine;

    @BeforeAll
    static void startEngine() {
        engine = newEngine("smoke");
    }

    @AfterAll
    static void stopEngine() {
        if (engine != null) {
            engine.close();
        }
    }

    /**
     * 每个用例一个独立的内存库（库名不同 → 表不冲突）。
     *
     * <p>{@code DB_CLOSE_DELAY=-1}：连接全关也不销毁库，否则引擎拿不到表。
     * 自建表 + 关异步执行器：测试里不需要定时器 / 异步任务跑起来。
     */
    static ProcessEngine newEngine(String name) {
        return new StandaloneInMemProcessEngineConfiguration()
                .setJdbcUrl("jdbc:h2:mem:ckplm_" + name + ";DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE")
                .setJdbcDriver("org.h2.Driver")
                .setJdbcUsername("sa")
                .setJdbcPassword("")
                .setDatabaseSchemaUpdate(ProcessEngineConfiguration.DB_SCHEMA_UPDATE_TRUE)
                .setAsyncExecutorActivate(false)
                .buildProcessEngine();
    }

    @Test
    void deploysAndRunsSimplestProcess() {
        RepositoryService repository = engine.getRepositoryService();
        RuntimeService runtime = engine.getRuntimeService();
        TaskService taskService = engine.getTaskService();

        String xml = """
                <?xml version="1.0" encoding="UTF-8"?>
                <definitions xmlns="http://www.omg.org/spec/BPMN/20100524/MODEL"
                             xmlns:flowable="http://flowable.org/bpmn"
                             xmlns:ckplm="http://ckplm.local/bpmn"
                             targetNamespace="http://ckplm.local/test">
                  <process id="smoke" name="冒烟" isExecutable="true" ckplm:variables="[]">
                    <startEvent id="start"/>
                    <sequenceFlow id="f1" sourceRef="start" targetRef="task1"/>
                    <userTask id="task1" name="审批" flowable:assignee="${initiator}"/>
                    <sequenceFlow id="f2" sourceRef="task1" targetRef="end"/>
                    <endEvent id="end"/>
                  </process>
                </definitions>
                """;

        Deployment deployment = repository.createDeployment()
                .addString("smoke.bpmn20.xml", xml)
                .deploy();
        assertNotNull(deployment.getId());

        // initiator 是 Flowable 的内置变量名，但"有这个名字"不等于"有值" ——
        // 引擎解析 ${initiator} 时找不到就抛 PropertyNotFoundException（任务建不出来）。
        // 真实业务里由发起环节写入；这里显式给一个，避免把"夹具没赋值"当成引擎问题。
        ProcessInstance instance = runtime.startProcessInstanceByKey("smoke", Map.of("initiator", "user_1"));
        Task task = taskService.createTaskQuery().processInstanceId(instance.getId()).singleResult();
        assertNotNull(task, "开始之后应当创建审批任务");
        assertEquals("task1", task.getTaskDefinitionKey());

        taskService.complete(task.getId());
        assertNull(
                runtime.createProcessInstanceQuery().processInstanceId(instance.getId()).singleResult(),
                "任务办完流程应当结束");
    }

    /**
     * 编译产物上的自定义命名空间属性（{@code ckplm:variables}）不能影响部署 ——
     * 前端新加的扩展属性必须被引擎当作"陌生属性"忽略，而不是拒绝。
     */
    @Test
    void ignoresUnknownCkplmExtensionAttributes() {
        RepositoryService repository = engine.getRepositoryService();

        String xml = """
                <?xml version="1.0" encoding="UTF-8"?>
                <definitions xmlns="http://www.omg.org/spec/BPMN/20100524/MODEL"
                             xmlns:flowable="http://flowable.org/bpmn"
                             xmlns:ckplm="http://ckplm.local/bpmn"
                             targetNamespace="http://ckplm.local/test">
                  <process id="ext_attr" isExecutable="true"
                           ckplm:variables="[{&quot;name&quot;:&quot;amount&quot;,&quot;type&quot;:&quot;NUMBER&quot;}]">
                    <startEvent id="start"/>
                    <sequenceFlow id="f1" sourceRef="start" targetRef="end" ckplm:condition="{&quot;mode&quot;:&quot;FIELD&quot;}"/>
                    <endEvent id="end"/>
                  </process>
                </definitions>
                """;

        Deployment deployment = repository.createDeployment()
                .addString("ext_attr.bpmn20.xml", xml)
                .deploy();
        assertNotNull(deployment.getId(), "自定义命名空间属性不应导致部署失败");
    }
}
