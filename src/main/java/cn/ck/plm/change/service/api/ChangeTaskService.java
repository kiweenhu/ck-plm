/*
 * Copyright (c) 2026~2028 深圳乘恺科技有限公司
 * All rights reserved.
 */

package cn.ck.plm.change.service.api;

import cn.ck.plm.change.entity.ChangeActivity;
import cn.ck.plm.change.entity.ChangeAffectedObject;
import cn.ck.plm.change.entity.ChangeTask;

import java.util.List;
import java.util.Map;

/**
 * 变更任务服务 —— 一次变更的<b>执行单元</b>。
 *
 * <h3>为什么按任务拆</h3>
 * <p>受影响对象可能横跨设计、文档、BOM 几拨人。整张 ECN 一起执行，失败只能整批重来；
 * 拆成任务后，失败只重跑该任务下未执行的行。
 *
 * <h3>谁的责任谁点</h3>
 * <p>{@code START} / {@code SUBMIT} 只允许任务责任人本人执行 —— 别人代点，
 * "谁在处理、谁认这个结果"就落不到人头上。
 */
public interface ChangeTaskService {

    /**
     * 新建（编号自动生成 ECT-####；所属通告必须在可计划状态）。
     *
     * <p>初始状态看通告：<b>审批前</b>（草稿 / 已驳回）建的是清单，状态 DRAFT；
     * <b>审批后</b>（已批准 / 实施中）建的是补项，直接 OPEN —— 派发节点已经过去了，
     * 再给个草稿态没人会来点派发。
     */
    ChangeTask create(ChangeTask task);

    /** 编辑（patch 语义）；已完成不可编辑 */
    ChangeTask update(ChangeTask patch);

    /**
     * 状态流转。
     *
     * @param oid     任务 oid
     * @param action  DISPATCH / START / SUBMIT / PASS / RETURN
     * @param comment 说明（RETURN 必填）
     */
    ChangeTask transition(String oid, String action, String comment);

    /**
     * 派发某张通告下的全部草稿任务（DRAFT → OPEN），返回派发条数。
     *
     * <p>审批通过（含免审通过）时自动调用；界面不再提供手工派发入口
     * （2026-11-03 用户口径「不需要派发操作」），只剩接口兜底。
     * 幂等：没有草稿任务时返回 0，不报错 —— 回调可能重复投递。
     */
    int dispatchByNotice(String noticeOid);

    /**
     * 执行变更任务：把任务下的受影响对象<b>自动修订</b>出新大版本，并回填修订产物
     * （{@code resultIterationOid / resultVersion / executedAt / executedBy}）。
     *
     * <p>这是「执行变更任务」节点表单里那一下「执行变更」：点下去就等于承诺"这些对象要出新版本"，
     * 所以幂等（已执行的行跳过）且整批同事务 —— 中途失败不留半批 B 版。
     *
     * <p>任务状态顺带 OPEN → IN_WORK（开始处理）；已提交验收 / 已完成的不能再执行。
     *
     * @param taskOid      变更任务 oid
     * @param affectedOids 本次要修订的受影响对象 oid；空 = 该任务下全部未执行的行
     * @return 执行后的受影响对象清单（含修订后的版本）
     */
    List<ChangeAffectedObject> execute(String taskOid, List<String> affectedOids);

    /**
     * <b>流程收尾时的自动修订</b>：变更任务流程走到「执行修订」节点时由引擎调用。
     *
     * <p>与 {@link #execute} 的差别：
     * <ul>
     *   <li>不做「只有任务责任人能执行」的校验 —— 引擎线程里的 {@code UserContext} 不保证是责任人；</li>
     *   <li>全部对象都已出过结果时<b>静默返回</b>，不当作错误 —— 流程重试、回退重跑都会走到这里；</li>
     *   <li>范围是任务下全部未执行的对象（不接受勾选）。</li>
     * </ul>
     *
     * <p>每修订一个对象就往 {@code ck_change_result_object} 写一条结果（变更后），
     * 「变更前」那一行只更新归属与快照，不再承载结果。
     *
     * @return 执行后的受影响对象清单（含 JOIN 出来的最新结果，供节点回显）
     */
    List<ChangeAffectedObject> executeByProcess(String taskOid);

    ChangeTask detail(String oid);

    /** 某张变更通告下的全部任务 */
    List<ChangeTask> byNotice(String noticeOid);

    /** 我的任务（assignee 为空时返回租户下全部；keyword 匹配编号/标题）；返回 { rows, total, page, pageSize } */
    Map<String, Object> mine(String assignee, String status, String keyword, int page, int pageSize);

    /**
     * 统计：各状态计数 + 总数 —— 返回 { total, counts }（页头的状态概览用）。
     *
     * <p>{@code assignee} 为空 = 租户下全部任务；给了就只数该责任人的（"只看我的"勾选时的口径）。
     */
    Map<String, Object> stats(String assignee);

    List<ChangeActivity> activities(String oid);

    /**
     * <b>流程收尾</b>：变更任务子流程走完时，把任务推到「已完成」（COMPLETED）。
     *
     * <p>为什么不复用 {@link #transition}：{@code PASS} 只接受 {@code SUBMITTED}，
     * 且要求操作人就是任务责任人 —— 而这一步由流程引擎在最后一个审批节点之后同步执行，
     * 当前登录人往往是审批人而非责任人；任务也可能压根没走过「提交验收」（流程里没这个节点）。
     * 硬套 transition 只会让收尾节点必然失败。
     *
     * <p>不给它加额外授权闸门是刻意的：子流程里有领导审批与会签，那就是验收本身，
     * 流程走完即代表这批对象改完并被认可。
     *
     * <p>幂等：已是 COMPLETED 时直接返回，不重复留痕 —— 流程重试、重复回调都不会多一条时间线。
     *
     * @param oid     任务 oid（子流程的 businessKey）
     * @param comment 说明；为空时记一条默认文案
     */
    ChangeTask completeByProcess(String oid, String comment);

    /** 删除（仅草稿 / 待开始可删；连带时间线） */
    boolean delete(String oid);
}
