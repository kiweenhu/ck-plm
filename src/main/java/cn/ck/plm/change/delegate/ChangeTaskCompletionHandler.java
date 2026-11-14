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
 * 内建服务 {@code change.completeTask} —— <b>变更任务子流程收尾</b>，把任务推到「已完成」。
 *
 * <h3>为什么需要它</h3>
 * <p>子流程走完（执行 → 审批 → 会签 → 结束）并不等于任务单状态变了：流程是流程，单据是单据。
 * 没有这一步，父流程的多实例结束后，通告那边看到的是"任务都跑完了，但状态还停在实施中"，
 * 汇总确认时无从确认。
 *
 * <h3>为什么不用「设置变更单据状态」那个服务</h3>
 * <p>{@code change.setRequestStatus} 按 businessKey 找的是 {@code ck_change_request} /
 * {@code ck_change_notice}，够不着独立主表 {@code ck_change_task}；而且任务不能靠直接改
 * status 列收尾 —— 要写 {@code completedAt}、要留一条时间线，那是服务层的事。
 *
 * <h3>幂等</h3>
 * <p>重复执行（流程重试、人工回退后再走一遍）不会多出时间线，见
 * {@link ChangeTaskService#completeByProcess(String, String)}。
 */
@Component
public class ChangeTaskCompletionHandler implements PlmServiceHandler {

    /** 服务 id：与前端 {@code BUILTIN_SERVICES}、编译器写进 BPMN 的 {@code serviceId} 三处一致 */
    public static final String SERVICE_ID = "change.completeTask";

    private static final Logger log = LoggerFactory.getLogger(ChangeTaskCompletionHandler.class);

    private final ChangeTaskService taskService;

    public ChangeTaskCompletionHandler(ChangeTaskService taskService) {
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
            throw new PlmServiceException("节点「" + context.nodeName() + "」找不到要收尾的变更任务："
                    + "子流程的 businessKey 必须传变更任务 oid");
        }

        String comment = "流程节点「" + context.nodeName() + "」执行：变更任务流程完成，任务结项";
        try {
            taskService.completeByProcess(oid.trim(), comment);
        } catch (IllegalArgumentException | IllegalStateException e) {
            throw new PlmServiceException("节点「" + context.nodeName() + "」收尾变更任务失败："
                    + e.getMessage());
        }
        log.info("节点「{}」完成变更任务 {} 的流程收尾", context.nodeName(), oid);
    }
}
