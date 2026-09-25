/*
 * Copyright (c) 2025 深圳乘恺科技有限公司
 * All rights reserved.
 *
 * @author Kiween.Hu; Roney.Liu
 */

package cn.ck.plm.process.delegate;

import cn.ck.plm.iam.service.api.NotificationChannelService;
import cn.ck.plm.process.entity.ProcessNodeLog;
import cn.ck.plm.process.service.api.ProcessNodeLogService;
import org.flowable.bpmn.model.FlowElement;
import org.flowable.engine.delegate.DelegateExecution;
import org.flowable.engine.delegate.JavaDelegate;
import org.springframework.stereotype.Component;

/**
 * 「通知」节点的运行期委托 —— BPMN 里的 {@code flowable:delegateExpression="${plmNotify}"}。
 *
 * <p><b>这个 Bean 存在的意义不是"能发通知了"，而是"失败要说得清楚"</b>：
 * 编译层给通知节点写了 {@code ${plmNotify}}，后端却没有这个 Bean，于是引擎抛
 * {@code Unknown property used in expression: ${plmNotify}} —— 这行字读起来像
 * "流程画错了"，而实际原因是"通知的运行期还没实现"，排查方向被彻底带偏。
 *
 * <p>现在它明确失败并说清缺什么：通知的<b>渠道与凭据已收归系统级配置</b>
 * （{@code plm.notification}，见 application.yml；流程侧只读，不再各自配 SMTP / OA 地址），
 * 但流程侧仍缺两样：按 {@code templateCode} 渲染标题/正文的通知模板，以及
 * "角色 → 收件人"的解析。补上这两样之前，通知节点<b>必须显式失败</b>：
 * 静默跳过等于让人以为通知发了 —— 而"以为发了"比"确定没发"危险得多
 * （该知道的人没收到，却没有任何痕迹）。
 *
 * <p>失败信息里带上"系统当前可用的通知方式"：这样办理人一眼能分清
 * "是系统没配渠道"还是"流程侧还没实现"，而不是对着一句"未实现"猜。
 */
@Component("plmNotify")
public class PlmNotifyDelegate implements JavaDelegate {

    private final ProcessNodeLogService nodeLogService;
    /** 系统通知模式（plm.notification）：失败信息要报"系统当前能用什么"，而不是笼统说"未实现" */
    private final NotificationChannelService channelService;

    public PlmNotifyDelegate(ProcessNodeLogService nodeLogService,
                             NotificationChannelService channelService) {
        this.nodeLogService = nodeLogService;
        this.channelService = channelService;
    }

    @Override
    public void execute(DelegateExecution execution) {
        String node = nodeName(execution);
        // 通知方式（渠道 + 凭据）已是系统级配置，这里读出来如实报给办理人：
        // "系统能用什么"与"本节点还缺什么"是两件事，混成一句"未实现"会让人以为连渠道都没配
        String reason = "通知节点「" + node + "」暂不能执行。系统当前可用的通知方式：「"
                + channelService.describeUsable()
                + "」；流程侧还缺两样：按 templateCode 渲染标题/正文的通知模板，"
                + "以及「收件人规则 → 具体用户」的解析。"
                + "渠道与凭据无需在流程里配（见服务端 application.yml 的 plm.notification）";
        // 落一条 ERROR 到节点日志：流程详情里点开这个节点就能看到原因，不必去翻服务器日志
        nodeLogService.record(errorRow(execution, node, reason));
        throw new PlmServiceException(reason);
    }

    private ProcessNodeLog errorRow(DelegateExecution execution, String node, String reason) {
        ProcessNodeLog row = new ProcessNodeLog();
        row.setTenantOid(execution.getTenantId());
        row.setProcessInstanceId(execution.getProcessInstanceId());
        row.setActivityId(execution.getCurrentActivityId());
        row.setActivityName(node);
        row.setLevel(ProcessNodeLog.LEVEL_ERROR);
        row.setSource(ProcessNodeLog.SOURCE_NOTIFY);
        row.setMessage("通知节点执行失败（运行期未实现）");
        row.setDetail(reason);
        return row;
    }

    private String nodeName(DelegateExecution execution) {
        FlowElement element = execution.getCurrentFlowElement();
        if (element == null) {
            return "(未知节点)";
        }
        String name = element.getName();
        return name != null && !name.trim().isEmpty() ? name : element.getId();
    }
}
