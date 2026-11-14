/*
 * Copyright (c) 2026~2028 深圳乘恺科技有限公司
 * All rights reserved.
 *
 * @author Kiween.Hu; Roney.Liu
 */

package cn.ck.plm.change.delegate;

import cn.ck.plm.change.entity.ChangeActivity;
import cn.ck.plm.change.entity.ChangeNotice;
import cn.ck.plm.change.entity.ChangeRequest;
import cn.ck.plm.change.entity.ChangeTask;
import cn.ck.plm.change.flow.ChangeNoticeFlow;
import cn.ck.plm.change.flow.ChangeRequestFlow;
import cn.ck.plm.change.flow.ChangeTaskFlow;
import cn.ck.plm.change.mapper.ChangeActivityMapper;
import cn.ck.plm.change.mapper.ChangeNoticeMapper;
import cn.ck.plm.change.mapper.ChangeRequestMapper;
import cn.ck.plm.change.service.api.ChangeTaskService;
import cn.ck.plm.process.delegate.PlmServiceContext;
import cn.ck.plm.process.delegate.PlmServiceException;
import cn.ck.plm.process.delegate.PlmServiceHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 自动服务 {@code change.setRequestStatus} —— 「设置变更单据状态」。
 *
 * <h3>为什么需要它</h3>
 * <p>变更请求、变更通告的主表是 {@code ck_change_request} / {@code ck_change_notice}，
 * <b>不是</b> {@code ck_softtype_instance} 的实例，所以 {@code object.setLifecycleState}
 * （作用于流程关联的软类型对象）够不着它们的 {@code status} 列。
 *
 * <h3>一张单据怎么定位</h3>
 * <p>按流程业务的 businessKey 定位，见 {@code ChangeRequestServiceImpl#startReviewProcess} /
 * {@code ChangeNoticeServiceImpl#startApproval}（发起时传的就是单据 oid）。两类单据 oid 都是
 * UUID，不会撞号，所以先查请求、再查通告即可分辨是哪种单据 —— 流程里同一个服务节点，
 * 不需要为两种单据配两个服务 id。
 *
 * <h3>谁能变状态</h3>
 * <p>配置了「变更需要审批」时，单据状态<b>由评审流程驱动</b>：本节点在流程的关键位置写入
 * 影响分析中 / 评审中 / 已受理 / 已否决（变更请求），或已批准 / 已驳回（变更通告）；
 * 单据上不再提供「开始影响分析」这类人工动作 —— 两条路都能改状态，
 * 账面上就分不清是流程批的，还是人点的。
 *
 * <h3>变更通告能写哪几个状态</h3>
 * <p>通告的审批期没有变更请求那样的分段（影响分析 / 评审），只有「审批中」这一个过程态，
 * 加上「已批准 / 已驳回」两个结论 —— 这三个都由流程节点写。草稿 / 实施中 / 已完成 /
 * 已关闭 / 取消都是人的动作，同样不让流程写。
 *
 * <h3>批准时为什么要顺手派发</h3>
 * <p>更改事项（= 变更任务）审批前只是清单（DRAFT），批了才让责任人看到待办。原先这一步
 * 只在流程结束回调里做；现在状态由流程节点先写成 {@code APPROVED}，回调会按幂等跳过，
 * 所以派发要跟着搬到这里。派发失败只记日志（审已批完是既成事实），
 * 流程结束时的回调还会再补一次。
 *
 * <p>派发的<b>另一个产物</b>是流程变量 {@code changeTaskList}：紧接着的派发节点是
 * 1:M 多实例子流程，集合就取这个变量（见 {@link #dispatchQuietly}）。
 */
@Component
public class ChangeRequestStatusHandler implements PlmServiceHandler {

    private static final Logger log = LoggerFactory.getLogger(ChangeRequestStatusHandler.class);

    /** 服务 id —— 与前端 {@code BUILTIN_SERVICES}、流程 DSL 的 serviceRef 三处一致 */
    public static final String SERVICE_ID = "change.setRequestStatus";

    /** 参数：目标状态 code（取自 {@link ChangeRequestFlow} / {@link ChangeNoticeFlow} 的状态常量） */
    private static final String PARAM_STATUS = "status";

    /**
     * 派发子流程要遍历的任务清单变量名。
     *
     * <p>三处必须一致：{@code change-notice} / {@code change-notice-derived} 的
     * {@code sub_task.multiInstance.collection}、子流程 {@code variableMap} 里
     * {@code ${task.oid}} / {@code ${task.assignee}} 的元素结构、以及这里写入的键名。
     */
    private static final String VAR_TASK_LIST = "changeTaskList";

    /**
     * 变更请求允许流程写入的状态。
     *
     * <p>刻意<b>不含</b> DRAFT / SUBMITTED / CLOSED / CANCELLED：退回草稿、关闭、取消
     * 都是人的决定（或另一个动作的结果），让流程节点随手把单打回草稿会把评审痕迹抹平。
     */
    private static final Set<String> REQUEST_ALLOWED = new LinkedHashSet<>(java.util.Arrays.asList(
            ChangeRequestFlow.ANALYZING,   // 影响分析中：进入影响分析会签
            ChangeRequestFlow.REVIEWING,   // 评审中：会签通过、进入决策
            ChangeRequestFlow.ACCEPTED,    // 已受理：评审通过
            ChangeRequestFlow.REJECTED     // 已否决：评审驳回
    ));

    /**
     * 变更通告允许流程写入的状态 —— 两个过程态（审批中 / 实施中）+ 两个结论（批准 / 驳回）。
     *
     * <p>{@code APPROVING} 本可以在拉起流程时由服务层直接写（原先就是这么做的），
     * 但流程图上"单据何时进入审批中"这件事要能看见 —— 与变更请求用例的
     * 「进入影响分析 / 进入评审」保持一致：状态由流程节点推进，服务层那次写入退化为兜底。
     */
    private static final Set<String> NOTICE_ALLOWED = new LinkedHashSet<>(java.util.Arrays.asList(
            ChangeNoticeFlow.APPROVING,    // 审批中：流程一开始就置上，审批人看到的是"审批中"
            ChangeNoticeFlow.APPROVED,     // 已批准：审批通过（并派发更改事项）
            // 实施中：审批通过、更改事项全部派发后由流程节点推进。
            // 人工「开始实施」在流程运行时是被拦的（进度归流程），这个状态只能由流程写；
            // COMPLETED 仍只走 change.completeNotice（它有"受影响对象全部执行完"的门槛）
            ChangeNoticeFlow.IN_WORK,
            ChangeNoticeFlow.REJECTED      // 已驳回：任一环节驳回
    ));

    private final ChangeRequestMapper requestMapper;
    private final ChangeNoticeMapper noticeMapper;
    private final ChangeActivityMapper activityMapper;
    private final ChangeTaskService taskService;

    public ChangeRequestStatusHandler(ChangeRequestMapper requestMapper,
                                      ChangeNoticeMapper noticeMapper,
                                      ChangeActivityMapper activityMapper,
                                      ChangeTaskService taskService) {
        this.requestMapper = requestMapper;
        this.noticeMapper = noticeMapper;
        this.activityMapper = activityMapper;
        this.taskService = taskService;
    }

    @Override
    public String serviceId() {
        return SERVICE_ID;
    }

    @Override
    public void execute(PlmServiceContext context) {
        String target = context.param(PARAM_STATUS);
        if (target == null) {
            throw new PlmServiceException("「设置变更单据状态」节点「" + context.nodeName()
                    + "」未配置目标状态（参数 status）");
        }
        String status = target.trim().toUpperCase();

        String oid = context.businessKey();
        if (oid == null) {
            throw new PlmServiceException("节点「" + context.nodeName() + "」找不到业务单据："
                    + "流程实例没有 businessKey（变更单据发起时应传单据 oid）");
        }

        ChangeRequest request = requestMapper.selectByOid(oid);
        if (request != null) {
            applyToRequest(request, status, context);
            return;
        }
        ChangeNotice notice = noticeMapper.selectByOid(oid);
        if (notice != null) {
            applyToNotice(notice, status, context);
            return;
        }
        throw new PlmServiceException("节点「" + context.nodeName() + "」按 businessKey 找不到变更请求"
                + "或变更通告：" + oid);
    }

    // ==================== 变更请求 ====================

    private void applyToRequest(ChangeRequest request, String status, PlmServiceContext context) {
        if (!REQUEST_ALLOWED.contains(status)) {
            throw new PlmServiceException("节点「" + context.nodeName() + "」的目标状态 " + status
                    + " 不合法；变更请求只能是 " + String.join(" / ", REQUEST_ALLOWED));
        }

        String from = request.getStatus();
        if (status.equals(from)) {
            // 幂等：重复进入（驳回后重新走一遍）不该在时间线上留两条一样的记录
            log.info("变更请求 {} 已是 {}，跳过（节点 {}）", request.getCode(), status, context.nodeName());
            return;
        }

        request.setStatus(status);
        if (ChangeRequestFlow.ACCEPTED.equals(status) && request.getAcceptedAt() == null) {
            request.setAcceptedAt(LocalDateTime.now());
        }
        requestMapper.update(request);
        recordRequest(request, from, status, context);
        log.info("变更请求 {} 状态 {} → {}（流程节点 {}，instance={}）",
                request.getCode(), from, status, context.nodeName(), context.processInstanceId());
    }

    /** 时间线留痕：流程改的状态也要能像人工动作一样在单据上看到 */
    private void recordRequest(ChangeRequest request, String from, String to, PlmServiceContext context) {
        record("REQUEST", request.getOid(), request.getTenantOid(), requestActionOf(to), from, to, context);
    }

    private static String requestActionOf(String status) {
        switch (status) {
            case ChangeRequestFlow.ANALYZING:
                return ChangeRequestFlow.ANALYZE;
            case ChangeRequestFlow.REVIEWING:
                return ChangeRequestFlow.SUBMIT;
            case ChangeRequestFlow.ACCEPTED:
                return ChangeRequestFlow.ACCEPT;
            default:
                return ChangeRequestFlow.REJECT;
        }
    }

    // ==================== 变更通告 ====================

    private void applyToNotice(ChangeNotice notice, String status, PlmServiceContext context) {
        if (!NOTICE_ALLOWED.contains(status)) {
            throw new PlmServiceException("节点「" + context.nodeName() + "」的目标状态 " + status
                    + " 不合法；变更通告只能由流程写 " + String.join(" / ", NOTICE_ALLOWED));
        }

        String from = notice.getStatus();
        if (status.equals(from)) {
            log.info("变更通告 {} 已是 {}，跳过（节点 {}）", notice.getCode(), status, context.nodeName());
            dispatchQuietly(notice, context);
            return;
        }

        notice.setStatus(status);
        if (ChangeNoticeFlow.APPROVED.equals(status) && notice.getApprovedAt() == null) {
            notice.setApprovedAt(LocalDateTime.now());
        }
        noticeMapper.update(notice);
        record("NOTICE", notice.getOid(), notice.getTenantOid(),
                ChangeNoticeFlow.APPROVED.equals(status) ? ChangeNoticeFlow.APPROVE : ChangeNoticeFlow.REJECT,
                from, status, context);
        log.info("变更通告 {} 状态 {} → {}（流程节点 {}，instance={}）",
                notice.getCode(), from, status, context.nodeName(), context.processInstanceId());

        dispatchQuietly(notice, context);
    }

    /**
     * 审批通过 → 派发更改事项清单（DRAFT → OPEN），并把清单写进流程变量。
     *
     * <p>派发本身失败只记日志、不抛：审批结论已经落库，不该因为它回滚；
     * 流程结束回调发现状态已是「已批准」时会再补一次派发（幂等）。
     *
     * <p><b>但清单变量无论成败都必须写</b>：下游 {@code sub_task} 是多实例子流程，
     * 集合表达式 {@code ${changeTaskList}} 解析不到会抛
     * {@code Unknown property used in expression}，把本节点刚落库的「已批准」一起回滚
     * —— 审批人就只看到一句报错，单据还停在审批中。空清单就写空列表：没有任务时
     * 多实例创建 0 个实例，流程直接往后走。
     */
    private void dispatchQuietly(ChangeNotice notice, PlmServiceContext context) {
        if (!ChangeNoticeFlow.APPROVED.equals(notice.getStatus())) {
            return;
        }
        List<Map<String, Object>> plan = List.of();
        try {
            int dispatched = taskService.dispatchByNotice(notice.getOid());
            if (dispatched > 0) {
                log.info("变更通告 {} 派发变更任务 {} 条（流程节点）", notice.getCode(), dispatched);
            }
            plan = dispatchedTasks(notice.getOid());
        } catch (Exception e) {
            log.error("变更通告 {} 审批通过后派发变更任务失败: {}", notice.getCode(), e.getMessage(), e);
        }
        context.setVariable(VAR_TASK_LIST, plan);
        log.info("变更通告 {} 派发子流程清单 {} 条（节点 {}）",
                notice.getCode(), plan.size(), context.nodeName());
    }

    /**
     * 已派发（OPEN）的任务 → 子流程清单。
     *
     * <p>元素结构由流程 DSL 规定：{@code oid} 作子流程 businessKey、{@code assignee} 作
     * {@code taskAssignee} 变量。只收 OPEN：DRAFT 是"还没派发的提案"，
     * 提前拉起子流程会让人对着未生效的任务干活。
     */
    private List<Map<String, Object>> dispatchedTasks(String noticeOid) {
        List<Map<String, Object>> plan = new ArrayList<>();
        for (ChangeTask task : taskService.byNotice(noticeOid)) {
            if (!ChangeTaskFlow.OPEN.equals(task.getStatus())) {
                continue;
            }
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("oid", task.getOid());
            // 允许为空（未指派责任人）：子流程照样起，任务中心里显示为"未指派"
            item.put("assignee", task.getAssignee() == null ? "" : task.getAssignee());
            plan.add(item);
        }
        return plan;
    }

    // ==================== 共用 ====================

    /** 时间线留痕：流程改的状态也要能像人工动作一样在单据上看到 */
    private void record(String changeType, String changeOid, String tenantOid, String action,
                        String from, String to, PlmServiceContext context) {
        ChangeActivity activity = new ChangeActivity();
        activity.setChangeType(changeType);
        activity.setChangeOid(changeOid);
        activity.setAction(action);
        activity.setFromStatus(from);
        activity.setToStatus(to);
        activity.setComment("流程节点「" + context.nodeName() + "」自动写入");
        activity.setOperator(context.actor());
        activity.setTenantOid(tenantOid);
        activityMapper.insert(activity);
    }
}
