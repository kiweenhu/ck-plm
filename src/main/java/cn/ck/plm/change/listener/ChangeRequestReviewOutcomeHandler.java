/*
 * Copyright (c) 2026~2028 深圳乘恺科技有限公司
 * All rights reserved.
 *
 * @author Kiween.Hu; Roney.Liu
 */

package cn.ck.plm.change.listener;

import cn.ck.plm.change.entity.ChangeActivity;
import cn.ck.plm.change.entity.ChangeRequest;
import cn.ck.plm.change.flow.ChangeRequestFlow;
import cn.ck.plm.change.mapper.ChangeActivityMapper;
import cn.ck.plm.change.mapper.ChangeRequestMapper;
import cn.ck.plm.process.support.ProcessOutcome;
import cn.ck.plm.process.support.ProcessOutcomeHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

/**
 * 变更请求评审流程结束 → 回写 ECR 状态（{@code REVIEWING → ACCEPTED / REJECTED}）。
 *
 * <h3>为什么由流程回调，而不是评审人在单据上点"受理"</h3>
 * <p>审批动作发生在「任务中心」，办理人办的是流程任务，不是这张单上的按钮。
 * 两边各写一次就会出现"任务中心点了同意、单据还停在评审中"；
 * 流程结束是唯一可信的结论来源，单据只负责接收它。
 *
 * <h3>判定口径</h3>
 * <ul>
 *   <li>流程必须<b>正常走完</b>（{@code endedNormally}）—— 被终止的实例不算评审过；</li>
 *   <li>结论取流程变量 {@code approved}：本项目的流程把"通过"和"驳回"都画到结束节点，
 *       只看"实例结束"会把驳回也当成通过。</li>
 * </ul>
 *
 * <h3>幂等</h3>
 * <p>只处理 {@code REVIEWING} 状态的单：重复分发、或单据已被人工推进过，一律不动。
 * 免审单据没有流程实例，永远不会走到这里。
 */
@Component
public class ChangeRequestReviewOutcomeHandler implements ProcessOutcomeHandler {

    private static final Logger log = LoggerFactory.getLogger(ChangeRequestReviewOutcomeHandler.class);

    /** 当前流程 key（随 platform-base 包安装，配置项 ck_change_config.request_process_key） */
    private static final String PROCESS_KEY = "change-request";

    private final ChangeRequestMapper requestMapper;
    private final ChangeActivityMapper activityMapper;

    public ChangeRequestReviewOutcomeHandler(ChangeRequestMapper requestMapper,
                                             ChangeActivityMapper activityMapper) {
        this.requestMapper = requestMapper;
        this.activityMapper = activityMapper;
    }

    @Override
    public boolean supports(String processKey) {
        return processKey != null && PROCESS_KEY.equals(processKey);
    }

    @Override
    public void onFinished(ProcessOutcome outcome) {
        String oid = outcome.getBusinessKey();
        if (oid == null || oid.isEmpty()) {
            log.warn("变更请求评审回调缺少业务 key: instance={}", outcome.getProcessInstanceId());
            return;
        }
        ChangeRequest request = requestMapper.selectByOid(oid);
        if (request == null) {
            log.warn("变更请求评审回调找不到单据: oid={} instance={}", oid, outcome.getProcessInstanceId());
            return;
        }
        if (!ChangeRequestFlow.REVIEWING.equals(request.getStatus())) {
            // 评审还没开始就被终止（最典型：卡在「影响分析中」时人工终止实例）。
            // 这时单据上的 process_instance_id 会变成一个永远不会有任务回来的死引用 ——
            // 既查不到待办，又被前端当成"评审已发起"而不再给重新发起的入口，单据彻底推不动。
            // 解除关联，让它能重新走一遍评审；正常走完的实例不会进这里（状态已不是 ANALYZING）。
            if (!outcome.isEndedNormally() && request.getProcessInstanceId() != null) {
                recordTerminated(request, outcome);
                requestMapper.updateProcessInstanceId(request.getOid(), null, outcome.getCompletedBy());
                log.info("变更请求 {} 评审流程被终止，已解除流程实例关联（原实例 {}）",
                        request.getCode(), request.getProcessInstanceId());
            }
            // 幂等：可能已经回调过，或单据被人为推进 —— 不覆盖现状
            log.info("变更请求 {} 当前状态 {}，跳过评审回调", request.getCode(), request.getStatus());
            return;
        }

        boolean approved = outcome.isEndedNormally() && outcome.boolVar("approved");
        String from = request.getStatus();
        request.setStatus(approved ? ChangeRequestFlow.ACCEPTED : ChangeRequestFlow.REJECTED);
        if (approved) {
            request.setAcceptedAt(LocalDateTime.now());
        }
        requestMapper.update(request);
        record(request, approved, from, outcome);
        log.info("变更请求 {} 评审{}（instance={}）",
                request.getCode(), approved ? "通过" : "驳回", outcome.getProcessInstanceId());
    }

    /** 终止留痕：解除关联是个"状态变化"，用户要能在时间线上看到为什么单子又回到了可发起状态 */
    private void recordTerminated(ChangeRequest request, ProcessOutcome outcome) {
        ChangeActivity activity = new ChangeActivity();
        activity.setChangeType("REQUEST");
        activity.setChangeOid(request.getOid());
        activity.setAction("TERMINATE");
        activity.setFromStatus(request.getStatus());
        activity.setToStatus(request.getStatus());
        activity.setComment("评审流程已终止，已解除流程实例关联，可重新发起评审");
        activity.setOperator(outcome.getCompletedBy());
        activity.setTenantOid(request.getTenantOid());
        activityMapper.insert(activity);
    }

    /** 时间线留痕：评审结论必须能在单据上看见，而不是只留在流程历史里 */
    private void record(ChangeRequest request, boolean approved, String from, ProcessOutcome outcome) {
        ChangeActivity activity = new ChangeActivity();
        activity.setChangeType("REQUEST");
        activity.setChangeOid(request.getOid());
        activity.setAction(approved ? ChangeRequestFlow.ACCEPT : ChangeRequestFlow.REJECT);
        activity.setFromStatus(from);
        activity.setToStatus(request.getStatus());
        activity.setComment(approved ? "流程评审通过" : "流程评审驳回");
        activity.setOperator(outcome.getCompletedBy());
        activity.setTenantOid(request.getTenantOid());
        activityMapper.insert(activity);
    }
}
