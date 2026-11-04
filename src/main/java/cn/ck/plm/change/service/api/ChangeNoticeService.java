/*
 * Copyright (c) 2026~2028 深圳乘恺科技有限公司
 * All rights reserved.
 */

package cn.ck.plm.change.service.api;

import cn.ck.plm.change.entity.ChangeActivity;
import cn.ck.plm.change.entity.ChangeAffectedObject;
import cn.ck.plm.change.entity.ChangeNotice;

import java.util.List;
import java.util.Map;

/**
 * 变更通告（ECN）服务 —— 回答"改什么、谁授权改"。
 *
 * <h3>三条不让步的口径</h3>
 * <ol>
 *   <li><b>没有受影响对象不许提交</b>：一张不指向任何对象的变更单，批了也无法执行；</li>
 *   <li><b>免审必须留痕</b>：{@code approvalRequired = false} 时提交会直接置 {@code APPROVED}，
 *       但必须给出免审原因，落在 {@code approval_bypassed / bypass_reason}；</li>
 *   <li><b>完成 = 全部执行完</b>：{@code COMPLETE} 要求该通告下没有
 *       {@code executedAt} 为空的受影响对象。</li>
 * </ol>
 *
 * <h3>M1 边界</h3>
 * <p>审批流程引擎接入属于 M2：当前 {@code START_APPROVAL} 会明确拒绝（不改状态、不假装通过）。
 * 需要立刻跑通全链路的租户可先开免审；审批路径待流程 SPI 落地后开放。
 */
public interface ChangeNoticeService {

    ChangeNotice create(ChangeNotice notice);

    /** 编辑（patch 语义）；仅 DRAFT / REJECTED 可编辑 */
    ChangeNotice update(ChangeNotice patch);

    /**
     * 状态流转。
     *
     * @param oid     变更通告 oid
     * @param action  SUBMIT / START_APPROVAL / APPROVE / REJECT / REVISE / START_WORK / COMPLETE / CLOSE / CANCEL
     * @param comment 说明（REJECT / REVISE / CANCEL 必填；免审提交时必须给出免审原因）
     */
    ChangeNotice transition(String oid, String action, String comment);

    /**
     * 管理员直接设置状态（运维纠错：单据卡在某个状态、正常流程推不动时用）。
     *
     * <p>与 {@link #transition} 的区别：不看流转规则、不校验门槛（受影响对象是否执行完、
     * 子任务是否关闭），但有两个硬约束 —— <b>只有租户管理员能调</b>（由控制器校验），
     * 且<b>不能设成审批中</b>（那需要流程实例，硬设会让单据永远等不到回调）。
     *
     * <p>副作用按目标状态补齐：目标不再是"进行中"时释放对象占用；流程实例还在跑时先终止，
     * 否则回调会把状态改回审批结论。每次操作都会写一条 {@code ADMIN_SET_STATUS} 处理记录。
     *
     * @param status 目标状态（{@code ChangeNoticeFlow} 全集，除 APPROVING）
     * @param reason 操作原因，必填（进处理记录，用于事后追溯"谁把单子改成了什么"）
     */
    ChangeNotice setStatusByAdmin(String oid, String status, String reason);

    ChangeNotice detail(String oid);

    /** 详情聚合：{@code { notice, request, affectedObjects, tasks, activities }} */
    Map<String, Object> detailBundle(String oid);

    List<ChangeActivity> activities(String oid);

    /** 列表（筛选均可空；分页）—— 返回 { rows, total, page, pageSize } */
    Map<String, Object> list(String status, String requestOid, String keyword, int page, int pageSize);

    /** 统计：各状态计数 + 总数 —— 返回 { total, counts } */
    Map<String, Object> stats();

    /** 受影响对象明细 */
    List<ChangeAffectedObject> affectedObjects(String oid);

    /**
     * 某个对象出现在哪些变更通告里（对象详情页「变更情况」）。
     *
     * <p>一行 = 一张通告上的这一条变更对象：通告编号 / 标题 / 状态 / 责任人 / 时间，
     * 以及 被修订版本 → 修订产物版本、执行时间。按通告创建时间倒序。
     *
     * @param entityType PART / DOCUMENT / ENG_DOCUMENT
     * @param entityOid  主对象 oid（不是迭代 oid —— 变更对象本来就记在主对象上）
     */
    List<Map<String, Object>> historyByEntity(String entityType, String entityOid);

    /**
     * 添加受影响对象。
     *
     * <p>必须同时给出 {@code entityOid} / {@code baseIterationOid} / {@code baseVersion}：
     * 变更作用在<b>具体版本</b>上，"这个零件的变更"没有意义 —— 必须说清是 A.2 还是 B.1。
     */
    ChangeAffectedObject addAffected(String noticeOid, ChangeAffectedObject affected);

    /**
     * 按对象 oid 添加受影响对象（受影响版本取对象当前 latest 迭代）。
     *
     * <p>给"变更任务上直接挑对象"用：任务只认对象 oid，版本由服务端回查 ——
     * 让前端先查版本再 POST 一遍，等于把同一次回查拆成两次、还留下"查到旧版本"的窗口。
     *
     * <p>幂等：该对象已在本通告里就返回既有那行，不报"重复添加" ——
     * 两张任务先后挑同一个零件是正常操作，前端拿 oid 去改派即可。
     */
    ChangeAffectedObject addAffectedByEntity(String noticeOid, String entityOid);

    /**
     * 把受影响对象改派到某个更改事项（= 变更任务）；{@code taskOid} 传空表示取消归属。
     *
     * <p>对象与事项的关系是"这个对象由哪件事来改"：派发之后仍可调整（实施中发现分工不对），
     * 但已执行修订的对象不再允许改派 —— 结果已经落库，再换人只会让责任无处追溯。
     */
    ChangeAffectedObject assignAffectedTask(String noticeOid, String affectedOid, String taskOid);

    /** 移除受影响对象（仅未提交前） */
    boolean removeAffected(String noticeOid, String affectedOid);

    /** 删除（仅 DRAFT 可删；连带受影响对象、任务与时间线） */
    boolean delete(String oid);
}
