/*
 * Copyright (c) 2026~2028 深圳乘恺科技有限公司
 * All rights reserved.
 */

package cn.ck.plm.change.service.api;

import cn.ck.plm.change.entity.ChangeActivity;
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

    /** 删除（仅草稿 / 待开始可删；连带时间线） */
    boolean delete(String oid);
}
