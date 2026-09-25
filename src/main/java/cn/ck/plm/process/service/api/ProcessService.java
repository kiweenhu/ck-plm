/*
 * Copyright (c) 2025 深圳乘恺科技有限公司
 * All rights reserved.
 *
 * @author Kiween.Hu; Roney.Liu
 */

package cn.ck.plm.process.service.api;

import cn.ck.plm.process.dto.ProcessActivityVO;
import cn.ck.plm.process.dto.ProcessCommentVO;
import cn.ck.plm.process.dto.ProcessTaskContextVO;
import cn.ck.plm.process.dto.ProcessInstanceVO;
import cn.ck.plm.process.dto.ProcessTaskFormVO;
import cn.ck.plm.process.dto.ProcessTaskStatsVO;
import cn.ck.plm.process.dto.ProcessTaskVO;

import java.util.List;
import java.util.Map;

/**
 * 工作流服务契约 —— 基于 Flowable 7 流程引擎，服务于前端「任务中心」与「流程监控」。
 *
 * <h3>多租户</h3>
 * <p>所有查询与写操作都以<b>当前请求的租户</b>（{@code TenantContext}）为 Flowable 的 {@code tenantId}，
 * 当前用户取 {@code UserContext}（username）。跨租户访问一律拒绝。
 *
 * <h3>身份映射</h3>
 * <p>userId = {@code ck_user.username}；候选组 = {@code ck_role.code}
 * （详见 {@link cn.ck.plm.process.support.ProcessIdentitySupport}）。
 */
public interface ProcessService {

    // ==================== 任务中心 ====================

    /** 待办任务：已指派给当前用户且未完成 */
    List<ProcessTaskVO> findTodoTasks(int page, int size);

    /** 可认领任务：候选人为当前用户或其角色组，且尚未签收 */
    List<ProcessTaskVO> findClaimableTasks(int page, int size);

    /** 已办任务：当前用户已完成的历史任务 */
    List<ProcessTaskVO> findDoneTasks(int page, int size);

    /**
     * 按 id 取单个<b>在办</b>任务 —— 办理页（独立窗口）自己拿上下文用。
     *
     * <p>为什么要单独开一个方法：办理动作现在在新窗口打开，那个页面是<b>冷启动</b>的，
     * 手里只有任务 id，没有列表行对象可借。列表接口带分页，按 id 去翻页找是不可靠的
     * （并发签收/分页边界都会让它找不到）。
     *
     * <p>任务已被办理或不存在时返回 {@code null} —— 由调用方给出"可能已办理"的说明，
     * 而不是抛异常（两个人同时打开同一个任务是很正常的）。
     */
    ProcessTaskVO findTaskById(String taskId);

    /**
     * 任务办理页的<b>渲染上下文</b>：通用信息（业务实体 / 流程 / 当前任务 / 完整进度）+ 任务表单 + 表单模板上下文。
     *
     * <p>办理页是冷启动的独立窗口，只有任务 id；让前端自己拼 4~5 个请求会引入串行依赖与口径漂移
     * （详见 {@link cn.ck.plm.process.dto.ProcessTaskContextVO}）。所以在这里一次给齐。
     *
     * <p>流程信息与进度<b>各自降级</b>：单个查询失败只留空，不让整页失败（实体/任务信息照常渲染）。
     * 任务已被办理或不存在时返回 {@code null}。
     */
    ProcessTaskContextVO findTaskContext(String taskId);

    /** 任务统计（待办 / 可认领 / 逾期 / 已办） */
    ProcessTaskStatsVO taskStats();

    /** 认领任务（签收后 assignee 变为当前用户） */
    void claimTask(String taskId);

    /**
     * 办理任务。
     *
     * <p>会把 {@code approved}（action == approve 时为 true）写入流程变量，
     * 供 BPMN 中的排他网关条件（{@code ${approved}} / {@code ${!approved}}）判断走审批通过还是驳回分支。
     *
     * @param action       approve=同意 / reject=驳回
     * @param comment      审批意见（写入 act_hi_comment）
     * @param formVariables 节点表单填的流程变量（如「设置审批人」写
     *                      {@code ckplmSetupAssignees_<活动id>}）；可空。
     *                      <b>系统口径变量（approved / lastAction / lastComment）会覆盖同名表单值</b>
     *                      —— 表单不该能改写"这次审批是什么结论"
     */
    void completeTask(String taskId, String action, String comment, Map<String, Object> formVariables);

    /**
     * 任务办理表单上下文：该节点配的是什么表单（{@code formKey}）、是哪个活动
     * （{@code taskDefinitionKey}）、以及<b>该实例所用那一版</b>流程模板的 DSL。
     *
     * <p>供「任务中心」办理弹框按节点表单渲染，取代写死的"同意/驳回 + 意见"。
     *
     * @throws IllegalArgumentException 任务不存在或不属于当前租户
     */
    ProcessTaskFormVO taskForm(String taskId);

    /** 委派任务（处理后任务回到委派人，assignee 变为受托人） */
    void delegateTask(String taskId, String targetAssignee);

    /** 转办任务（把任务彻底交给他人） */
    void transferTask(String taskId, String targetAssignee);

    /** 查询任务评论 */
    List<ProcessCommentVO> findTaskComments(String taskId);

    /** 添加任务评论 */
    void addTaskComment(String taskId, String comment);

    // ==================== 流程监控 ====================

    /**
     * 查询流程实例。
     *
     * @param scope {@code all-running}=运行中全部 / {@code my-running}=我发起的 /
     *              {@code my-involved}=我参与的（含已结束）
     */
    List<ProcessInstanceVO> findInstances(String scope, int page, int size);

    /** 流程实例详情（含流程变量） */
    ProcessInstanceVO findInstanceDetail(String instanceId);

    /**
     * 某个业务对象关联的流程实例 —— <b>对象详情页「关联流程」两栏的数据源</b>。
     *
     * <p>实例来源是关联表 {@code ck_process_entity_set}（"实例 ↔ 实体"的唯一来源），
     * 因此本方法回答的是"这个对象<b>参与过</b>哪些流程"，而不是"谁发起的"。
     *
     * <p>返回顺序刻意分组：<b>执行中的在前</b>（按开始时间倒序），<b>已执行的在后</b>
     * （按结束时间倒序）—— 调用方按 {@code status} 分两栏即可，不必自己排。
     * 执行中（{@code running} / {@code suspended}）的会带上当前节点与办理人；
     * 已结束的带结束时间与结果（{@code completed} / {@code terminated}）。
     *
     * <p>关联行还在、但实例历史已被清理的，直接跳过（不编造状态）。
     *
     * @param entityOid     业务对象主 oid
     * @param entityVersion 业务对象大版本（可空 = 该对象全部大版本）
     */
    List<ProcessInstanceVO> findInstancesByEntity(String entityOid, String entityVersion);

    /**
     * 某个流程实例的<b>节点经路</b> —— 回答"走到哪了"。
     *
     * <p>返回按时间顺序的节点列表，每项状态三选一：
     * <ul>
     *   <li>{@code completed} 已办：责任人和办理时间取自历史活动，意见取自 {@code act_hi_comment}，
     *       {@code outcome} 是该节点之后<b>实际走过</b>的那条连线名（如「同意」/「驳回」）；</li>
     *   <li>{@code running} 当前在办：责任人取当前任务办理人（可能为空 = 待认领）；</li>
     *   <li>{@code pending} 尚未到达：从当前节点沿连线往下可达的活动（不评估网关条件，
     *       所以是"可能要去"而不保证一定去）。</li>
     * </ul>
     *
     * <p>网关与连线本身<b>不</b>作为节点返回（它们是路由，不是活动）；已结束的实例不会返回 pending。
     *
     * @throws IllegalArgumentException 实例不存在或不属于当前租户
     */
    List<ProcessActivityVO> findInstanceActivities(String instanceId);

    /** 挂起流程实例 */
    void suspendInstance(String instanceId);

    /** 激活流程实例 */
    void activateInstance(String instanceId);

    /** 终止流程实例（运行中实例，记录终止原因） */
    void terminateInstance(String instanceId, String reason);

    /** 删除流程实例（运行中先终止，再清理历史） */
    void deleteInstance(String instanceId);

    // ==================== 业务发起 ====================

    /**
     * 发起流程实例。
     *
     * <p>调用前会自动确保当前租户已部署该内置流程（惰性补齐新注册租户）。
     * 会自动注入流程变量 {@code initiator}（发起人）与 {@code tenantOid}（租户）。
     *
     * @param processKey  流程定义 key，如 {@code plm-change-review}
     * @param businessKey 业务标识（建议传业务对象 oid）
     * @param variables   业务变量（可空）
     * @return 流程实例 id
     */
    String startProcess(String processKey, String businessKey, Map<String, Object> variables);
}
