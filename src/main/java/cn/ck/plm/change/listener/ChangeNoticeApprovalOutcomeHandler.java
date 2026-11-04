/*
 * Copyright (c) 2026~2028 深圳乘恺科技有限公司
 * All rights reserved.
 *
 * @author Kiween.Hu; Roney.Liu
 */

package cn.ck.plm.change.listener;

import cn.ck.plm.change.entity.ChangeActivity;
import cn.ck.plm.change.entity.ChangeNotice;
import cn.ck.plm.change.flow.ChangeNoticeFlow;
import cn.ck.plm.change.mapper.ChangeActivityMapper;
import cn.ck.plm.change.mapper.ChangeNoticeMapper;
import cn.ck.plm.change.service.api.ChangeTaskService;
import cn.ck.plm.process.support.ProcessOutcome;
import cn.ck.plm.process.support.ProcessOutcomeHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

/**
 * 变更通告审批结束 → 回写 ECN 状态（{@code APPROVING → APPROVED / REJECTED}）。
 *
 * <h3>为什么由流程回调，而不是审批人在单据上点"通过"</h3>
 * <p>审批动作发生在「任务中心」，办理人办的是流程任务，不是这张单上的按钮。
 * 若两边各写一次状态，就会出现"任务中心点了同意、单据还停在审批中"；
 * 流程结束是唯一可信的结论来源，单据只负责接收它。
 *
 * <h3>判定口径</h3>
 * <ul>
 *   <li>流程必须<b>正常走完</b>（{@code endedNormally}）—— 被终止的实例不算审批过；</li>
 *   <li>结论取流程变量 {@code approved}：本项目的流程把"通过"和"驳回"都画到结束节点，
 *       只看"实例结束"会把驳回也当成通过。</li>
 * </ul>
 *
 * <p>审批通过还要顺手把<b>更改事项清单</b>派发出去（{@code DRAFT → OPEN}）：
 * 更改事项就是变更任务，审批前建的任务只是提案，批了才让责任人看到待办。
 *
 * <h3>幂等</h3>
 * <p>只处理 {@code APPROVING} 状态的单：重复分发、或单据已被人推进过，一律不动。
 * 派发本身也幂等（没有草稿任务时返回 0）。
 */
@Component
public class ChangeNoticeApprovalOutcomeHandler implements ProcessOutcomeHandler {

    private static final Logger log = LoggerFactory.getLogger(ChangeNoticeApprovalOutcomeHandler.class);

    /** 当前流程 key（随 platform-base 包安装） */
    private static final String PROCESS_KEY = "change-notice";

    /**
     * 派生模板的 key：启用变更请求（ECR）的租户走这张。
     *
     * <p>两张模板的审批结论口径相同（{@code approved} 变量 + 正常结束），
     * 回调不退化成"按流程 key 分支"，否则派生单会停在审批中没人回写。
     */
    private static final String DERIVED_PROCESS_KEY = "change-notice-derived";

    /** 改名前的存量 key：老环境里已部署的流程可能还叫这个 */
    private static final String LEGACY_PROCESS_KEY = "change-notice-approval";

    private final ChangeNoticeMapper noticeMapper;
    private final ChangeActivityMapper activityMapper;
    private final ChangeTaskService changeTaskService;

    public ChangeNoticeApprovalOutcomeHandler(ChangeNoticeMapper noticeMapper,
                                              ChangeActivityMapper activityMapper,
                                              ChangeTaskService changeTaskService) {
        this.noticeMapper = noticeMapper;
        this.activityMapper = activityMapper;
        this.changeTaskService = changeTaskService;
    }

    @Override
    public boolean supports(String processKey) {
        if (processKey == null) {
            return false;
        }
        return PROCESS_KEY.equals(processKey)
                || DERIVED_PROCESS_KEY.equals(processKey)
                || LEGACY_PROCESS_KEY.equals(processKey);
    }

    @Override
    public void onFinished(ProcessOutcome outcome) {
        String oid = outcome.getBusinessKey();
        if (oid == null || oid.isEmpty()) {
            log.warn("变更通告审批回调缺少业务 key: instance={}", outcome.getProcessInstanceId());
            return;
        }
        ChangeNotice notice = noticeMapper.selectByOid(oid);
        if (notice == null) {
            log.warn("变更通告审批回调找不到单据: oid={} instance={}", oid, outcome.getProcessInstanceId());
            return;
        }
        if (ChangeNoticeFlow.APPROVED.equals(notice.getStatus())) {
            // 流程里的「设置状态」节点已经把「已批准」写进单据了，这里不再回写结论，
            // 只补一次派发 —— 节点里派发失败是只记日志不抛的，这是最后一次补救机会（幂等）
            dispatchTasks(notice);
            log.info("变更通告 {} 已由流程节点置为已批准，回调仅补派发更改事项", notice.getCode());
            return;
        }
        if (!ChangeNoticeFlow.APPROVING.equals(notice.getStatus())) {
            // 幂等：可能已经回调过，或单据被人为推进 —— 不覆盖现状
            log.info("变更通告 {} 当前状态 {}，跳过审批回调", notice.getCode(), notice.getStatus());
            return;
        }

        boolean approved = outcome.isEndedNormally() && outcome.boolVar("approved");
        String from = notice.getStatus();
        notice.setStatus(approved ? ChangeNoticeFlow.APPROVED : ChangeNoticeFlow.REJECTED);
        if (approved) {
            notice.setApprovedAt(LocalDateTime.now());
        }
        noticeMapper.update(notice);
        record(notice, approved, from, outcome);
        if (approved) dispatchTasks(notice);
        log.info("变更通告 {} 审批{}（instance={}）",
                notice.getCode(), approved ? "通过" : "驳回", outcome.getProcessInstanceId());
    }

    /**
     * 审批通过 → 派发更改事项清单。
     *
     * <p>失败只记日志，不抛：单据已经批了，这是既成事实；任务没派出去可以事后重派，
     * 不能因为派发失败把审批结论回滚或让回调反复重试。
     */
    private void dispatchTasks(ChangeNotice notice) {
        try {
            int dispatched = changeTaskService.dispatchByNotice(notice.getOid());
            if (dispatched > 0) {
                log.info("变更通告 {} 派发变更任务 {} 条", notice.getCode(), dispatched);
            }
        } catch (Exception e) {
            log.error("变更通告 {} 审批通过后派发变更任务失败: {}", notice.getCode(), e.getMessage(), e);
        }
    }

    /** 时间线留痕：审批结论必须能在单据上看见，而不是只留在流程历史里 */
    private void record(ChangeNotice notice, boolean approved, String from, ProcessOutcome outcome) {
        ChangeActivity activity = new ChangeActivity();
        activity.setChangeType("NOTICE");
        activity.setChangeOid(notice.getOid());
        activity.setAction(approved ? ChangeNoticeFlow.APPROVE : ChangeNoticeFlow.REJECT);
        activity.setFromStatus(from);
        activity.setToStatus(notice.getStatus());
        activity.setComment(approved ? "流程审批通过" : "流程审批驳回");
        activity.setOperator(outcome.getCompletedBy());
        activity.setTenantOid(notice.getTenantOid());
        activityMapper.insert(activity);
    }
}
