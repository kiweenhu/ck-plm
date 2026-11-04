/*
 * Copyright (c) 2026~2028 深圳乘恺科技有限公司
 * All rights reserved.
 */

package cn.ck.plm.change.service.api;

import cn.ck.plm.change.dto.ChangeIssueRef;
import cn.ck.plm.change.entity.ChangeActivity;
import cn.ck.plm.change.entity.ChangeNotice;
import cn.ck.plm.change.entity.ChangeRequest;
import cn.ck.plm.change.entity.ChangeRequestAffectedObject;

import java.util.List;
import java.util.Map;

/**
 * 变更请求（ECR）服务 —— 回答"为什么要改"。
 *
 * <h3>写接口语义（与既有模块一致）</h3>
 * <b>字段缺席（null）= 本次不改；空串（""）= 显式清空</b>。
 *
 * <h3>状态口径</h3>
 * 新建恒为 {@code DRAFT}，入参里的 status 一律忽略；流转只经 {@link #transition}，
 * 规则表见 {@link cn.ck.plm.change.flow.ChangeRequestFlow}。
 */
public interface ChangeRequestService {

    /** 新建（编号自动生成 ECR-####；提出人 = 当前用户；状态 = DRAFT；写 CREATE 记录） */
    ChangeRequest create(ChangeRequest request);

    /** 编辑（patch 语义）；已关闭 / 已取消不可编辑 */
    ChangeRequest update(ChangeRequest patch);

    /**
     * 状态流转。
     *
     * @param oid     变更请求 oid
     * @param action  SUBMIT / ACCEPT / REJECT / REOPEN / CLOSE / CANCEL
     *                （SUBMIT 即「发起评审」，草稿一步进入评审；存量 SUBMITTED 也走它收口）
     * @param comment 说明（REJECT / REOPEN / CANCEL 必填；SUBMIT 时可作为评审备注）
     */
    ChangeRequest transition(String oid, String action, String comment);

    ChangeRequest detail(String oid);

    /** 时间线（按时间正序） */
    List<ChangeActivity> activities(String oid);

    /** 关联问题（编号 / 标题 / 状态），按问题编号排序 */
    List<ChangeIssueRef> issues(String oid);

    /**
     * 预估受影响对象（评审输入）。
     *
     * <p>与 ECN 的执行清单 {@code ChangeAffectedObject} 分表：这里没有版本 / 锁 / 修订字段，
     * 只表达"大概会动到哪些对象"。
     */
    List<ChangeRequestAffectedObject> affectedObjects(String oid);

    /**
     * 添加预估受影响对象。
     *
     * <p>只需给 {@code entityOid}：类型 / 编码 / 名称 / 所属容器一律由服务端回查对象主表，
     * 不接受前端传值（防篡改、防漏填）。
     */
    ChangeRequestAffectedObject addAffected(String requestOid, ChangeRequestAffectedObject affected);

    /** 移除预估受影响对象 */
    boolean removeAffected(String requestOid, String affectedOid);

    /**
     * 对象候选（对象选择器用）：按关键字在零件 / 文档 / 工程文档里各取 {@code limit} 条。
     *
     * <p>返回字段统一为 {@code oid / entityType / code / name / containerOid}。
     *
     * <p>另带 {@code changeCode}：该对象已被一张<b>在途变更通告</b>收录时为那张单的编号
     * （口径同 {@code ChangeNoticeFlow.ACTIVE}），选择器据此置灰 ——
     * 同一对象同时只能被一份执行清单持有（见 {@code ck_change_affected_object.locked}）。
     *
     * <p>{@code containerOid} 非空时只在"该系列/型号范围内"检索：选系列则含其子系列与
     * 其下所有型号，选型号则只有该型号自己的对象 —— 预估受影响对象必须落在这次变更
     * 覆盖的产品范围内，否则等于把别的产品拖进来改。
     */
    List<Map<String, Object>> objectCandidates(String keyword, int limit, String containerOid);

    /**
     * 收集所选对象关联的其他对象（变更任务挑对象时的"收集"动作）。
     *
     * <p>当前口径（2026-11-03 与业务确认）：只对<b>零件</b>生效，收集
     * <b>关联文档</b>（{@code ck_doc_part_link} 的 DESCRIBES + REFERENCE）与
     * <b>替代件</b>（{@code ck_part_alternate_link}，双向）。BOM 结构不在收集范围内。
     *
     * <p>返回字段在候选的基础上多一个 {@code relation}（DOCUMENT / ALTERNATE），
     * 替代件另带 {@code alternateType}；按 oid 去重，入参里的对象自身不会出现在结果中。
     */
    List<Map<String, Object>> relatedObjects(List<String> entityOids);

    /**
     * 该请求派生出的变更通告（子流程，按未关闭优先、创建时间倒序）。
     *
     * <p>只用于详情展示与"关闭前还差什么"的说明；关闭门槛由 {@link #transition} 拦截。
     */
    List<ChangeNotice> childNotices(String oid);

    /** 列表（筛选均可空；分页）—— 返回 { rows, total, page, pageSize } */
    Map<String, Object> list(String status, String keyword, int page, int pageSize);

    /** 统计：各状态计数 + 总数 —— 返回 { total, counts }（页头的状态概览用） */
    Map<String, Object> stats();

    /** 删除（仅 DRAFT 可删；连带时间线） */
    boolean delete(String oid);
}
