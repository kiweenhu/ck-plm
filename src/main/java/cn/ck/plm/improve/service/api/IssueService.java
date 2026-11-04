/*
 * Copyright (c) 2026~2028 深圳乘恺科技有限公司
 * All rights reserved.
 */

package cn.ck.plm.improve.service.api;

import cn.ck.plm.improve.entity.Issue;
import cn.ck.plm.improve.entity.IssueActivity;

import java.util.List;
import java.util.Map;

/**
 * 问题服务 —— 持续改进的"跟踪与闭环"。
 *
 * <h3>写接口语义（与前几类一致）</h3>
 * <b>字段缺席（null）= 本次不改；空串（""）= 显式清空</b>。
 *
 * <h3>闭环口径（见 {@link cn.ck.plm.improve.flow.IssueFlow}）</h3>
 * 状态流转只经 {@link #transition}：新建恒为 {@code OPEN}，关闭必须走
 * "提交验证 → 验证通过"两步 —— 处理人自己说"好了"不算数。
 */
public interface IssueService {

    /** 新建（编号自动生成 ISS-####；提出人 = 当前用户；状态 = OPEN；写 CREATE 记录） */
    Issue create(Issue issue);

    /**
     * 编辑（patch 语义）：标题 / 描述 / 类型 / 严重度 / 来源 / 责任人 / 计划日期 /
     * 关联对象 / 根因 / 措施 —— 已关闭或已取消的问题不可编辑（请先重开）。
     */
    Issue update(Issue patch);

    /**
     * 状态流转（闭环的核心动作）。
     *
     * @param oid          问题 oid
     * @param action       动作：START / SUBMIT_VERIFY / VERIFY_PASS / VERIFY_FAIL / CANCEL / REOPEN
     * @param comment      说明（VERIFY_FAIL / CANCEL / REOPEN 必填）
     * @param verifyResult 验证结论（VERIFY_PASS 必填）
     * @param rootCause    根因分析（{@code SUBMIT_VERIFY} 时可随动作一起填；类型要求根因时必填）
     * @param resolution   处理措施（{@code SUBMIT_VERIFY} 时可随动作一起填）
     */
    Issue transition(String oid, String action, String comment, String verifyResult,
                     String rootCause, String resolution);

    /** 追加跟踪备注（不改变状态，只往时间线加一条 COMMENT） */
    IssueActivity addComment(String oid, String comment);

    /** 详情（问题本体） */
    Issue detail(String oid);

    /** 处理历程（时间线，按时间正序） */
    List<IssueActivity> activities(String oid);

    /**
     * 列表（筛选均可空；分页）—— 返回 { rows, total, page, pageSize }。
     *
     * <p>{@code relatedType / relatedCode}：按问题的「关联对象」过滤 —— 对象详情页
     * （零组件 / 文档…）用它列出挂在自己身上的问题。两个条件各自独立判空。
     */
    Map<String, Object> list(String status, String severity, String owner, String keyword,
                             String relatedType, String relatedCode, int page, int pageSize);

    /**
     * 统计：各状态计数 + 闭环率。
     *
     * <p>闭环率 = CLOSED /（总数 - CANCELLED）：确认不做的问题不该拉低闭环率。
     */
    Map<String, Object> stats();

    /**
     * 门的 <b>ISSUE_OPEN 自动检查</b>（"开门前还有几个未闭环问题"）。
     *
     * <p>作用域 = 该产品（系列 / 型号）下的未闭环问题（OPEN / IN_PROGRESS / VERIFYING）；
     * 但<b>逐门判定只算挂在该门上</b>的问题（{@code issue.gate_oid == gate.oid}）——
     * 登记时没选阶段门的问题不参与任何门，那道门并没有被它选中（2026-10-29 用户口径）。
     * 逐门读取包随门快照来的 {@code auto_checks} 里的 ISSUE_OPEN 声明（如
     * {@code {"type":"ISSUE_OPEN","severity":"BLOCK","maxCount":0}}），
     * 以 {@code 本门未闭环数 <= maxCount} 判过与不过 —— <b>现算不落库</b>（与"不编造数值"同口径）。
     *
     * <p>返回 { ownerOid, openTotal, blockOpen, warnOpen, gates: [逐门判定], items: [未闭环明细] }，
     * 其中 openTotal / blockOpen / warnOpen 是<b>产品级汇总</b>（不分门），供系列页列与门决策申请单带出；
     * 未声明 ISSUE_OPEN 的门不出现在 gates 里。
     */
    Map<String, Object> gateCheck(String ownerOid);

    /**
     * 关联对象候选（「关联对象编码」选择器用，2026-10-25）。
     *
     * <p>问题的关联对象只存类型 + 编码，手打编码既易错又搜不了 —— 这里按关键字在
     * 零组件 / 文档 / 工程数据 / 产品系列 / 产品型号里模糊查，返回
     * {@code oid / entityType / code / name}，选中即把类型与编码一起带回表单。
     *
     * @param keyword 编码或名称关键字（空 = 各类给最近更新的若干条）
     * @param type    只看某一类（{@code PART / DOCUMENT / ENG_DOCUMENT / PRODUCT_LINE / PRODUCT_MODEL}）；
     *                空 = 五类都查
     * @param limit   每类上限（1~50）
     */
    List<Map<String, Object>> objectCandidates(String keyword, String type, int limit);

    /** 删除（连带处理记录级联删除；谨慎用 —— 一般问题应关闭而不是删除） */
    boolean delete(String oid);
}
