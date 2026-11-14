/*
 * Copyright (c) 2026~2028 深圳乘恺科技有限公司
 * All rights reserved.
 *
 * @author Kiween.Hu; Roney.Liu
 */

package cn.ck.plm.change.delegate;

import cn.ck.plm.change.service.api.ChangeNoticeService;
import cn.ck.plm.process.delegate.PlmServiceContext;
import cn.ck.plm.process.delegate.PlmServiceException;
import cn.ck.plm.process.delegate.PlmServiceHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * 内建服务 {@code change.completeNotice} —— <b>汇总确认通过</b>，把变更通告推到「已完成」。
 *
 * <h3>它补的是流程尾部的那个空档</h3>
 * <p>{@code change.setRequestStatus} 只写「审批中 / 已批准 / 已驳回」三个状态，理由是
 * 实施进度由人推进、流程不该替人宣布"实施完成"。但流程一旦有了<b>汇总确认</b>节点，
 * 那个节点就是人对实施结果的正式确认 —— 此时由流程落「已完成」才符合事实，
 * 否则通告会永远停在「已批准」。
 *
 * <h3>为什么要单独的 id 而不是扩白名单</h3>
 * <p>扩 {@code NOTICE_ALLOWED} 会同时打开"任何流程节点都能把通告写成已完成"，
 * 而且那条路径是直接 update status，绕过「受影响对象全部执行完」的门槛。
 * 这里走 {@link ChangeNoticeService#completeByProcess(String, String)}，
 * 门槛、状态机、时间线一律复用，拦不住就抛异常、流程停在汇总节点。
 */
@Component
public class ChangeNoticeCompletionHandler implements PlmServiceHandler {

    /** 服务 id：与前端 {@code BUILTIN_SERVICES}、编译器写进 BPMN 的 {@code serviceId} 三处一致 */
    public static final String SERVICE_ID = "change.completeNotice";

    private static final Logger log = LoggerFactory.getLogger(ChangeNoticeCompletionHandler.class);

    private final ChangeNoticeService noticeService;

    public ChangeNoticeCompletionHandler(ChangeNoticeService noticeService) {
        this.noticeService = noticeService;
    }

    @Override
    public String serviceId() {
        return SERVICE_ID;
    }

    @Override
    public void execute(PlmServiceContext context) {
        String oid = context.businessKey();
        if (oid == null || oid.trim().isEmpty()) {
            throw new PlmServiceException("节点「" + context.nodeName() + "」找不到要收尾的变更通告："
                    + "流程实例没有 businessKey（变更通告发起时应传通告 oid）");
        }

        String comment = "流程节点「" + context.nodeName() + "」执行：全部变更任务已执行完毕，汇总确认通过";
        try {
            noticeService.completeByProcess(oid.trim(), comment);
        } catch (IllegalArgumentException | IllegalStateException e) {
            // 最常见的就是"还有受影响对象没执行完"：消息原样给办理人，他知道该去催谁
            throw new PlmServiceException("节点「" + context.nodeName() + "」标记通告实施完成失败："
                    + e.getMessage());
        }
        log.info("节点「{}」完成变更通告 {} 的汇总确认", context.nodeName(), oid);
    }
}
