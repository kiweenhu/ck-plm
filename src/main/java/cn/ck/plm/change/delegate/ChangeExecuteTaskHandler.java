/*
 * Copyright (c) 2026~2028 深圳乘恺科技有限公司
 * All rights reserved.
 *
 * @author Kiween.Hu; Roney.Liu
 */

package cn.ck.plm.change.delegate;

import cn.ck.plm.change.service.api.ChangeTaskService;
import cn.ck.plm.process.delegate.PlmServiceContext;
import cn.ck.plm.process.delegate.PlmServiceException;
import cn.ck.plm.process.delegate.PlmServiceHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * 内建服务 {@code change.executeTask} —— <b>执行修订</b>：把任务下的变更前对象逐个修出新大版本。
 *
 * <h3>为什么放在流程节点里，而不是让人在表单上点按钮</h3>
 * <p>"执行变更任务"节点办完就该出结果 —— 修订是这一步的<b>业务结果</b>，不是可选的附加动作。
 * 让人再点一次按钮，就会出现"任务提交了但对象没改"的状态：下游审批人验收的是空壳，
 * 汇总确认时也看不到变更后的版本。放在节点之后由引擎执行，顺序上天然成立。
 *
 * <h3>产物写哪</h3>
 * <p>每个对象产出一条 {@code ck_change_result_object}（变更后），变更前那一行不动。
 * 幂等：已有结果的跳过 —— 流程重试、驳回重跑都不会出第二个 B 版。
 *
 * <h3>与表单上「执行变更」按钮的关系</h3>
 * <p>按钮保留（责任人想提前跑、或只想先改其中几个），走的是同一个
 * {@code ChangeTaskService#execute}；流程走到本节点时只会跳过已执行的行，不会重复修订。
 */
@Component
public class ChangeExecuteTaskHandler implements PlmServiceHandler {

    /** 服务 id：与前端 {@code BUILTIN_SERVICES}、编译器写进 BPMN 的 {@code serviceId} 三处一致 */
    public static final String SERVICE_ID = "change.executeTask";

    private static final Logger log = LoggerFactory.getLogger(ChangeExecuteTaskHandler.class);

    private final ChangeTaskService taskService;

    public ChangeExecuteTaskHandler(ChangeTaskService taskService) {
        this.taskService = taskService;
    }

    @Override
    public String serviceId() {
        return SERVICE_ID;
    }

    @Override
    public void execute(PlmServiceContext context) {
        String oid = context.businessKey();
        if (oid == null || oid.trim().isEmpty()) {
            throw new PlmServiceException("节点「" + context.nodeName() + "」找不到要执行修订的变更任务："
                    + "子流程的 businessKey 必须传变更任务 oid");
        }
        try {
            taskService.executeByProcess(oid.trim());
        } catch (IllegalArgumentException | IllegalStateException e) {
            // 最常见的是"任务下还没有变更对象"：消息原样给办理人，他知道该去通告上挂对象
            throw new PlmServiceException("节点「" + context.nodeName() + "」执行修订失败：" + e.getMessage());
        }
        log.info("节点「{}」完成变更任务 {} 的自动修订", context.nodeName(), oid);
    }
}
