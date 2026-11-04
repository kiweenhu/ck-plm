/*
 * Copyright (c) 2026~2028 深圳乘恺科技有限公司
 * All rights reserved.
 *
 * @author Kiween.Hu; Roney.Liu
 */

package cn.ck.plm.process.service.impl;

import cn.ck.plm.iam.entity.User;
import cn.ck.plm.iam.service.api.NotificationService;
import cn.ck.plm.iam.entity.Organization;
import cn.ck.plm.iam.service.api.OrganizationService;
import cn.ck.plm.iam.service.api.UserService;
import cn.ck.plm.process.dto.ProcessActivityFormVO;
import cn.ck.plm.process.dto.ProcessActivityVO;
import cn.ck.plm.process.dto.ProcessAddSignerVO;
import cn.ck.plm.process.dto.ProcessCommentVO;
import cn.ck.plm.process.dto.ProcessStartFormVO;
import cn.ck.plm.process.dto.ProcessTaskContextVO;
import cn.ck.plm.process.dto.ProcessInstanceVO;
import cn.ck.plm.process.dto.ProcessTaskFormVO;
import cn.ck.plm.process.dto.ProcessTaskStatsVO;
import cn.ck.plm.process.dto.ProcessTaskVO;
import cn.ck.plm.process.entity.ProcessEntitySet;
import cn.ck.plm.process.entity.ProcessNodeLog;
import cn.ck.plm.process.entity.ProcessTemplate;
import cn.ck.plm.process.entity.ProcessTemplateVersion;
import cn.ck.plm.process.mapper.ProcessTemplateMapper;
import cn.ck.plm.process.mapper.ProcessTemplateVersionMapper;
import cn.ck.plm.process.reject.RejectRouter;
import cn.ck.plm.process.service.api.ProcessNodeLogService;
import cn.ck.plm.process.service.api.ProcessEntitySetService;
import cn.ck.plm.process.service.api.ProcessService;
import cn.ck.plm.process.support.CountersignRule;
import cn.ck.plm.process.support.BranchTerminator;
import cn.ck.plm.process.support.ProcessDeploymentSupport;
import cn.ck.plm.process.support.ProcessIdentitySupport;
import cn.ck.plm.process.support.ProcessOutcome;
import cn.ck.plm.process.support.ProcessOutcomeHandler;
import cn.ck.plm.softtype.service.api.IBADataService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.flowable.bpmn.model.BpmnModel;
import org.flowable.bpmn.model.CallActivity;
import org.flowable.bpmn.model.ExtensionAttribute;
import org.flowable.bpmn.model.FlowElement;
import org.flowable.bpmn.model.FlowNode;
import org.flowable.bpmn.model.Gateway;
import org.flowable.bpmn.model.ManualTask;
import org.flowable.bpmn.model.MultiInstanceLoopCharacteristics;
import org.flowable.bpmn.model.ReceiveTask;
import org.flowable.bpmn.model.SequenceFlow;
import org.flowable.bpmn.model.ServiceTask;
import org.flowable.bpmn.model.SubProcess;
import org.flowable.bpmn.model.UserTask;
import org.flowable.common.engine.impl.identity.Authentication;
import org.flowable.engine.HistoryService;
import org.flowable.engine.RepositoryService;
import org.flowable.engine.RuntimeService;
import org.flowable.engine.TaskService;
import org.flowable.engine.history.HistoricActivityInstance;
import org.flowable.engine.history.HistoricProcessInstance;
import org.flowable.engine.repository.ProcessDefinition;
import org.flowable.engine.repository.ProcessDefinitionQuery;
import org.flowable.engine.runtime.Execution;
import org.flowable.engine.runtime.ProcessInstance;
import org.flowable.engine.task.Comment;
import org.flowable.identitylink.api.IdentityLink;
import org.flowable.task.api.Task;
import org.flowable.task.api.TaskQuery;
import org.flowable.task.api.history.HistoricTaskInstance;
import org.flowable.task.api.history.HistoricTaskInstanceQuery;
import org.flowable.variable.api.history.HistoricVariableInstance;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Date;
import java.util.Deque;
import java.util.function.Function;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 工作流服务实现 —— 基于 Flowable 7。
 *
 * <h3>多租户</h3>
 * <p>每个方法都以当前请求的租户作为 Flowable {@code tenantId}。Flowable 的租户<b>只是标记 + 显式过滤条件</b>，
 * 不会自动隔离，因此所有查询都显式带 {@code tenantId}，所有写操作前都先按「id + tenantId」校验归属，
 * 防止跨租户越权操作他人实例。
 *
 * <h3>身份</h3>
 * <p>userId 取 {@code ck_user.username}，候选组取 {@code ck_role.code}；
 * 写操作前通过 {@code Authentication.setAuthenticatedUserId} 让 Flowable 把操作人记入
 * {@code act_hi_comment} / {@code act_hi_identitylink}。用后必须清理该 ThreadLocal。
 */
@Service
public class ProcessServiceImpl implements ProcessService {

    private static final Logger log = LoggerFactory.getLogger(ProcessServiceImpl.class);

    /** 逾期统计的有界扫描条数：Flowable 无「截止时间早于」条件，靠 dueDate 升序扫描覆盖逾期项 */
    private static final int OVERDUE_SCAN_LIMIT = 500;

    /**
     * 兜底标识：拿不到当前用户标识时用它做查询条件。
     *
     * <p>取一个不可能存在的值，<b>保证查不到任务</b> —— 绝不能退化成"不加条件"，那等于把全租户的任务
     * 都当成"我的"。
     */
    private static final String NOT_FOUND_USER = "__ckplm_no_such_user__";

    /** 流程变量：发起人 */
    private static final String VAR_INITIATOR = "initiator";

    /** 流程变量：租户 */
    private static final String VAR_TENANT = "tenantOid";

    /** 流程变量：最近一次审批动作 / 意见（便于历史追溯） */
    private static final String VAR_LAST_ACTION = "lastAction";
    private static final String VAR_LAST_COMMENT = "lastComment";
    /**
     * 每人这一票的结论（<b>任务级</b>局部变量，见 {@link #completeTask}）。
     *
     * <p>为什么必须按任务存：会签是多人多票，而 {@code approved / lastAction} 是<b>实例级</b>的
     * 中间/末态变量（每次办理覆盖）—— 谁投了什么在流程历史里留不下来。办理页的会签表单要展示
     * "他人的会签情况"（谁同意、谁驳回、各写什么意见），没有它就只剩"已办理"三个字。
     *
     * <p>取值 {@link #DECISION_APPROVE} / {@link #DECISION_REJECT}；存量数据没有这个变量，
     * 读取侧一律返回 null（前端显示"已办理"而不是编一个结论）。
     */
    private static final String VAR_DECISION = "ckplmDecision";
    private static final String DECISION_APPROVE = "APPROVE";
    private static final String DECISION_REJECT = "REJECT";

    /**
     * 会签本轮已投的同意 / 驳回票数（<b>多实例根执行</b>的局部变量，见 {@link #tallyCountersign}）。
     *
     * <p>为什么放在根执行的局部作用域：一个流程里会签可能被走多轮（驳回退回后重新提交），
     * 票数若存在实例级会带着上一轮的票继续累加；挂在根执行上，本轮活动一结束就随之消失，
     * 天然按轮清零，不必额外找"什么时候重置"的钩子。
     */
    private static final String VAR_VOTE_APPROVE = "ckplmVoteApprove";
    private static final String VAR_VOTE_REJECT = "ckplmVoteReject";
    /** Flowable 多实例的内部变量：本轮应到人数（局部在根执行上） */
    private static final String VAR_MI_INSTANCES = "nrOfInstances";
    /**
     * 会签「本轮已不可能通过」标记（<b>多实例根执行</b>的局部变量）。
     *
     * <p>编译层把它写进 {@code completionCondition}（{@code … || ckplmVetoed}），一旦置 true
     * 多实例立刻结束，剩余评审人的任务随之取消 —— 否则一人驳回后，其他人还要把注定失败的票
     * 再办一遍。变量名与 {@code bpmn-compiler/namespaces.ts} 的 {@code RUNTIME_VETO_VARIABLE} 一致。
     */
    private static final String VAR_VETOED = "ckplmVetoed";
    /** 会签通过规则（编译层写在 userTask 上的 {@code ckplm:passRule}） */
    private static final String ATTR_PASS_RULE = "passRule";
    /** 审批人声明（编译层写在 userTask 上的 {@code ckplm:assignee}，JSON，含 strategy） */
    private static final String ATTR_ASSIGNEE = "assignee";
    /** 「发起人上级」策略 —— 运行期解析：发起人 → 其所属组织 → 部门主管 */
    private static final String STRATEGY_INITIATOR_LEADER = "INITIATOR_LEADER";
    /** 「部门主管」策略 —— 运行期解析：业务对象上的部门字段 → 该组织 → 部门主管 */
    private static final String STRATEGY_DEPT_LEADER = "DEPT_LEADER";
    /** 业务对象集合变量名（与 ProcessInstanceController#VAR_BUSINESS_OBJECT_SET 同一契约） */
    private static final String VAR_BUSINESS_OBJECT_SET = "businessObjectSet";
    /** 节点日志里「任务无人可办」的标记前缀 —— 重复扫描时按它判重 */
    private static final String ORPHAN_TASK_MARK = "任务无人可办";
    /** 沿执行父链找多实例根执行的最大层数（防御坏数据造成的死循环） */
    private static final int MAX_EXECUTION_DEPTH = 8;
    /** 读 {@code ckplm:assignee} 这类 JSON 扩展属性用（只读、无状态） */
    private static final ObjectMapper MAPPER = new ObjectMapper();

    /** 「设置流程参与者」内置表单 code：与前端 dsl-core/forms.ts 的 SETUP_ASSIGNEE_FORM_CODE 一致 */
    private static final String SETUP_ASSIGNEE_FORM = "CKPLM_SETUP_ASSIGNEE";

    private final RuntimeService runtimeService;
    private final TaskService taskService;
    private final HistoryService historyService;
    private final RepositoryService repositoryService;
    private final ProcessIdentitySupport identity;
    private final ProcessDeploymentSupport deploymentSupport;
    /** 任务表单上下文：按流程定义 key + 版本取「该实例在跑那一版」的 DSL */
    private final ProcessTemplateMapper templateMapper;
    private final ProcessTemplateVersionMapper versionMapper;
    /** 反查"某业务对象关联了哪些实例"（ck_process_entity_set 是实例 ↔ 实体的唯一来源） */
    private final ProcessEntitySetService entitySetService;
    /** 办理人显示名解析：Flowable 的 assignee 可能是用户名、也可能是人员 oid */
    private final UserService userService;
    /** 组织架构：解析「发起人上级 / 部门主管」要读组织的 leader_oid */
    private final OrganizationService organizationService;
    /** 实体 IBA 值：解析「部门主管」要按节点的 deptFieldKey 读出业务对象上的部门字段 */
    private final IBADataService ibaDataService;
    /** 站内通知：任务到达 / 委派 / 转办 / 流程结束 都要落到收件人的铃铛上 */
    private final NotificationService notificationService;
    /** 节点执行日志：给节点经路带"有几条日志、几条错误"，供详情页点开查看 */
    private final ProcessNodeLogService nodeLogService;
    /** 驳回路由：按节点的 ckplm:reject 把流程退回到目标节点（运行期跳转） */
    private final RejectRouter rejectRouter;
    /** 「终止同层分支」的运行期收口（并行 / 包容分支的局部终止） */
    private final BranchTerminator branchTerminator;
    /**
     * 业务回调（流程结束后回写各自单据状态）。
     *
     * <p>用 {@link ObjectProvider} 延迟取：业务 handler 往往要注入业务流程服务，
     * 而业务流程服务又要注入本服务去发起流程 —— 构造器直接注入会形成循环依赖，
     * 启动即失败。延迟到"流程真的结束"那一刻再取，循环自然不存在。
     */
    private final ObjectProvider<ProcessOutcomeHandler> outcomeHandlers;

    public ProcessServiceImpl(RuntimeService runtimeService,
                               TaskService taskService,
                               HistoryService historyService,
                               RepositoryService repositoryService,
                               ProcessIdentitySupport identity,
                               ProcessDeploymentSupport deploymentSupport,
                               ProcessTemplateMapper templateMapper,
                               ProcessTemplateVersionMapper versionMapper,
                               ProcessEntitySetService entitySetService,
                               UserService userService,
                               OrganizationService organizationService,
                               IBADataService ibaDataService,
                               NotificationService notificationService,
                               ProcessNodeLogService nodeLogService,
                               RejectRouter rejectRouter,
                               BranchTerminator branchTerminator,
                               ObjectProvider<ProcessOutcomeHandler> outcomeHandlers) {
        this.runtimeService = runtimeService;
        this.taskService = taskService;
        this.historyService = historyService;
        this.repositoryService = repositoryService;
        this.identity = identity;
        this.deploymentSupport = deploymentSupport;
        this.templateMapper = templateMapper;
        this.versionMapper = versionMapper;
        this.entitySetService = entitySetService;
        this.userService = userService;
        this.organizationService = organizationService;
        this.ibaDataService = ibaDataService;
        this.notificationService = notificationService;
        this.nodeLogService = nodeLogService;
        this.rejectRouter = rejectRouter;
        this.branchTerminator = branchTerminator;
        this.outcomeHandlers = outcomeHandlers;
    }

    // ==================== 任务中心 ====================

    @Override
    public List<ProcessTaskVO> findTodoTasks(int page, int size) {
        String tenant = identity.currentTenantId();
        List<Task> tasks = listTasksByIdentifiers(
                identifier -> taskService.createTaskQuery()
                        .taskTenantId(tenant).active().taskAssignee(identifier),
                identity.currentUserIdentifiers(), first(page, size), size);
        Map<String, ProcessDefinition> cache = new HashMap<>();
        List<ProcessTaskVO> result = new ArrayList<>(tasks.size());
        Date now = new Date();
        for (Task task : tasks) {
            result.add(toTaskVO(task, cache, now));
        }
        return result;
    }

    @Override
    public ProcessTaskVO findTaskById(String taskId) {
        if (taskId == null || taskId.trim().isEmpty()) {
            return null;
        }
        String tenant = identity.currentTenantId();
        Task task = taskService.createTaskQuery()
                .taskId(taskId.trim())
                .taskTenantId(tenant)
                .singleResult();
        return task == null ? null : toTaskVO(task, new HashMap<>(), new Date());
    }

    @Override
    public ProcessTaskContextVO findTaskContext(String taskId) {
        ProcessTaskVO task = findTaskById(taskId);
        if (task == null) {
            return null;
        }
        ProcessTaskContextVO context = new ProcessTaskContextVO();
        context.setTask(task);
        context.setForm(taskForm(taskId));

        String instanceId = task.getProcessInstanceId();
        if (instanceId == null) {
            return context;
        }
        // 流程信息与进度各自降级：任一查询失败只留空，不把整页拖垮
        // （实体引用与任务本身不依赖它们，照常渲染）
        try {
            context.setProcess(findInstanceDetail(instanceId));
        } catch (Exception e) {
            log.warn("读取流程实例信息失败: instance={} error={}", instanceId, e.getMessage());
        }
        try {
            context.setActivities(findInstanceActivities(instanceId));
        } catch (Exception e) {
            log.warn("读取流程进度失败: instance={} error={}", instanceId, e.getMessage());
        }
        context.setEntities(entityRefsOf(instanceId));
        return context;
    }

    /**
     * 流程实例关联的业务实体 → 渲染用引用。
     *
     * <p>只给"引用 + 类型"（oid / 大版本 / typeCode / 宿主 / businessKey），不回查业务表：
     * 一是避免流程模块反向依赖各业务模块，二是实体名是锦上添花，
     * 不该因为它查不到就让整页失败。
     *
     * <p>两处使用：任务办理页的「通用信息」、流程监控列表的「业务标识」——
     * 后者原来只能显示 {@code businessKey} 那个 oid，看不出办的是哪个对象。
     */
    private List<ProcessTaskContextVO.EntityRef> entityRefsOf(String instanceId) {
        List<ProcessTaskContextVO.EntityRef> result = new ArrayList<>();
        try {
            for (ProcessEntitySet row : entitySetService.findByProcessInstanceId(instanceId)) {
                ProcessTaskContextVO.EntityRef ref = new ProcessTaskContextVO.EntityRef();
                ref.setEntityOid(row.getEntityOid());
                ref.setEntityVersion(row.getEntityVersion());
                ref.setTypeCode(row.getTypeCode());
                ref.setRootTypeCode(row.getRootTypeCode());
                ref.setBusinessKey(row.getBusinessKey());
                result.add(ref);
            }
        } catch (Exception e) {
            log.warn("读取流程关联的业务实体失败: instance={} error={}", instanceId, e.getMessage());
        }
        return result;
    }

    /**
     * 流程实例的 businessKey；运行中查运行时表，已结束查历史表。
     *
     * <p>两条都查是因为任务中心会列出"已办"，那些实例在运行时表里已经不存在了。
     */
    private String businessKeyOf(String instanceId) {
        try {
            ProcessInstance running = runtimeService.createProcessInstanceQuery()
                    .processInstanceId(instanceId).singleResult();
            if (running != null) {
                return running.getBusinessKey();
            }
        } catch (Exception e) {
            log.debug("读取运行中实例的 businessKey 失败: instance={} error={}", instanceId, e.getMessage());
        }
        try {
            org.flowable.engine.history.HistoricProcessInstance done = historyService
                    .createHistoricProcessInstanceQuery().processInstanceId(instanceId).singleResult();
            return done != null ? done.getBusinessKey() : null;
        } catch (Exception e) {
            log.debug("读取历史实例的 businessKey 失败: instance={} error={}", instanceId, e.getMessage());
            return null;
        }
    }

    @Override
    public List<ProcessTaskVO> findClaimableTasks(int page, int size) {
        String tenant = identity.currentTenantId();
        List<String> groups = identity.currentUserGroups();
        List<Task> tasks = listTasksByIdentifiers(
                claimableQueryFactory(tenant, groups),
                identity.currentUserIdentifiers(), first(page, size), size);

        Map<String, ProcessDefinition> cache = new HashMap<>();
        List<ProcessTaskVO> result = new ArrayList<>(tasks.size());
        Date now = new Date();
        for (Task task : tasks) {
            ProcessTaskVO vo = toTaskVO(task, cache, now);
            if (groups != null && !groups.isEmpty()) {
                vo.setCandidateGroups(String.join(",", groups));
            }
            result.add(vo);
        }
        return result;
    }

    @Override
    public List<ProcessTaskVO> findDoneTasks(int page, int size) {
        String tenant = identity.currentTenantId();
        List<HistoricTaskInstance> tasks = listHistoricTasksByIdentifiers(
                identifier -> historyService.createHistoricTaskInstanceQuery()
                        .taskTenantId(tenant).finished().taskAssignee(identifier),
                identity.currentUserIdentifiers(), first(page, size), size);

        Map<String, ProcessDefinition> cache = new HashMap<>();
        List<ProcessTaskVO> result = new ArrayList<>(tasks.size());
        Date now = new Date();
        for (HistoricTaskInstance task : tasks) {
            ProcessTaskVO vo = new ProcessTaskVO();
            vo.setId(task.getId());
            vo.setName(task.getName());
            vo.setAssignee(task.getAssignee());
            vo.setAssigneeName(displayNameOf(task.getAssignee()));
            vo.setEntities(entityRefsOf(task.getProcessInstanceId()));
            // 业务主键：变更请求 / 通告 / 任务这类单据不是软类型实例、没有实体关联行，
            // 只有这个值能说明"办的是哪一张单"，前端据此回查编号与标题（见 useEntityLabels）
            vo.setBusinessKey(businessKeyOf(task.getProcessInstanceId()));
            vo.setProcessInstanceId(task.getProcessInstanceId());
            vo.setCreateTime(task.getCreateTime());
            vo.setDueDate(task.getDueDate());
            vo.setEndTime(task.getEndTime());
            vo.setOverdue(isOverdue(task.getDueDate(), now));
            vo.setTenantId(tenant);
            vo.setFormKey(task.getFormKey());
            vo.setTaskDefinitionKey(task.getTaskDefinitionKey());
            ProcessDefinition pd = findProcessDefinition(task.getProcessDefinitionId(), cache);
            if (pd != null) {
                vo.setProcessDefinitionName(pd.getName());
                vo.setProcessDefinitionKey(pd.getKey());
            }
            result.add(vo);
        }
        return result;
    }

    @Override
    public ProcessTaskStatsVO taskStats() {
        String tenant = identity.currentTenantId();
        List<String> identifiers = identity.currentUserIdentifiers();
        List<String> groups = identity.currentUserGroups();
        Date now = new Date();

        int todo = countTasksByIdentifiers(
                identifier -> taskService.createTaskQuery()
                        .taskTenantId(tenant).active().taskAssignee(identifier),
                identifiers);

        int claimable = countTasksByIdentifiers(claimableQueryFactory(tenant, groups), identifiers);

        int overdue = countOverdueTasks(tenant, identifiers, now);

        int done = countHistoricTasksByIdentifiers(
                identifier -> historyService.createHistoricTaskInstanceQuery()
                        .taskTenantId(tenant).finished().taskAssignee(identifier),
                identifiers);

        return new ProcessTaskStatsVO(todo, claimable, overdue, done);
    }

    // ==================== 人员标识匹配（用户名 / 人员 oid 都认）====================
    //
    // Flowable 的 assignee / candidateUser 是原样字符串，历史上同时存在两种写法：
    // 用户名（认领、转办、${initiator}）与人员 oid（「设置审批人」按 oid 指派下游）。
    // 只按一种查，"另一种写法的任务"在待办里不出现、也不能认领 —— 任务就那么卡着。
    // 所以查询统一走下面这组方法，两种标识都算；标识来源见 ProcessIdentitySupport#currentUserIdentifiers。
    //
    // <h3>为什么按标识各查一遍，而不是在一个查询里拼 OR</h3>
    // Flowable 7 的 TaskQuery / HistoricTaskInstanceQuery 都<b>没有</b> taskAssigneeIn，
    // 而它的查询模型会把 or() 块里"同一属性出现多次"折叠成<b>只留最后一个取值</b>：
    // 原先写成 {@code or(){ taskAssignee(username); taskAssignee(oid); } endOr()}，
    // 实测只有 oid 那份生效 —— 于是按用户名指派的任务（恰恰包括 {@code ${initiator}} 指派给
    // 发起人的「设置审批人」）在任务中心压根不出现，现象就是"任务没派给我"。
    // 所以这里对每个标识各查一遍再合并：语义明确，也不依赖引擎内部行为。

    /**
     * 按标识逐个查"我的任务"，合并成一页（按创建时间倒序）。
     *
     * @param queryFactory 标识 → 查询（调用方补 tenant / active / unassigned 等条件）
     */
    private static List<Task> listTasksByIdentifiers(Function<String, TaskQuery> queryFactory,
                                                     List<String> identifiers, int first, int size) {
        List<Task> merged = new ArrayList<>();
        for (String identifier : identifiersOrNothing(identifiers)) {
            // 每个标识都取到第 first+size 条：合并后再切本页，等价于"一个 IN 查询"的分页
            merged.addAll(queryFactory.apply(identifier)
                    .orderByTaskCreateTime().desc().listPage(0, first + size));
        }
        merged.sort(Comparator.comparing(Task::getCreateTime, Comparator.nullsLast(Comparator.reverseOrder())));
        return slicePage(merged, first, size);
    }

    /** 历史任务：按标识逐个查、合并成一页（按结束时间倒序） */
    private static List<HistoricTaskInstance> listHistoricTasksByIdentifiers(
            Function<String, HistoricTaskInstanceQuery> queryFactory,
            List<String> identifiers, int first, int size) {
        List<HistoricTaskInstance> merged = new ArrayList<>();
        for (String identifier : identifiersOrNothing(identifiers)) {
            merged.addAll(queryFactory.apply(identifier)
                    .orderByHistoricTaskInstanceEndTime().desc().listPage(0, first + size));
        }
        merged.sort(Comparator.comparing(HistoricTaskInstance::getEndTime,
                Comparator.nullsLast(Comparator.reverseOrder())));
        return slicePage(merged, first, size);
    }

    /** 按标识逐个 count 后求和 */
    private static int countTasksByIdentifiers(Function<String, TaskQuery> queryFactory,
                                               List<String> identifiers) {
        int total = 0;
        for (String identifier : identifiersOrNothing(identifiers)) {
            total += (int) queryFactory.apply(identifier).count();
        }
        return total;
    }

    /** 按标识逐个 count 历史任务后求和 */
    private static int countHistoricTasksByIdentifiers(
            Function<String, HistoricTaskInstanceQuery> queryFactory, List<String> identifiers) {
        int total = 0;
        for (String identifier : identifiersOrNothing(identifiers)) {
            total += (int) queryFactory.apply(identifier).count();
        }
        return total;
    }

    /**
     * 候选任务查询：每个标识一遍。
     *
     * <p>带角色组时在<b>同一查询内</b>用 OR 表达「候选人为我 或 属于我某个角色组」——
     * 这两个条件分属不同属性，OR 块不会被折叠。注意不能把 taskCandidateUser 再放到 OR 块<b>外面</b>：
     * {@code user = me AND (user = me OR group IN gs)} 等价于 {@code user = me}，
     * 会把"按角色组可认领"的任务全滤掉。
     */
    private Function<String, TaskQuery> claimableQueryFactory(String tenant, List<String> groups) {
        boolean hasGroups = groups != null && !groups.isEmpty();
        return identifier -> {
            TaskQuery query = taskService.createTaskQuery()
                    .taskTenantId(tenant)
                    .active()
                    .taskUnassigned();
            if (hasGroups) {
                query.or().taskCandidateUser(identifier).taskCandidateGroupIn(groups).endOr();
            } else {
                query.taskCandidateUser(identifier);
            }
            return query;
        };
    }

    /**
     * 兜底标识：拿不到当前用户标识时用它当查询条件。
     *
     * <p>取一个不可能存在的值，保证查不到任何任务 —— 绝不能退化成"不加条件"，
     * 那等于把全租户的任务都当成"我的"。
     */
    private static List<String> identifiersOrNothing(List<String> identifiers) {
        return (identifiers == null || identifiers.isEmpty()) ? List.of(NOT_FOUND_USER) : identifiers;
    }

    /** 合并结果的第 first 条起的 size 条（越界返回空表） */
    private static <T> List<T> slicePage(List<T> merged, int first, int size) {
        if (first >= merged.size()) {
            return new ArrayList<>();
        }
        return new ArrayList<>(merged.subList(first, Math.min(merged.size(), first + size)));
    }

    /**
     * 统计待办中已逾期的任务数。
     *
     * <p>Flowable 7 的 TaskQuery 没有「截止时间早于」这类范围条件（只有 {@code taskDueDate} 精确匹配），
     * 故按截止时间升序做<b>有界扫描</b>：升序排列后逾期任务集中在最前面，
     * 扫描 {@value #OVERDUE_SCAN_LIMIT} 条足以覆盖真实场景。
     */
    private int countOverdueTasks(String tenant, List<String> identifiers, Date now) {
        int n = 0;
        try {
            // 每个标识各扫一段（升序扫描的额度按标识分开给，合并后不会互相挤掉）
            for (String identifier : identifiersOrNothing(identifiers)) {
                List<Task> tasks = taskService.createTaskQuery()
                        .taskTenantId(tenant).active().taskAssignee(identifier)
                        .orderByTaskDueDate().asc()
                        .listPage(0, OVERDUE_SCAN_LIMIT);
                for (Task t : tasks) {
                    if (isOverdue(t.getDueDate(), now)) {
                        n++;
                    }
                }
            }
        } catch (Exception e) {
            log.warn("统计逾期任务失败: {}", e.getMessage());
        }
        return n;
    }

    @Override
    @Transactional
    public void claimTask(String taskId) {
        String tenant = identity.currentTenantId();
        String user = identity.currentUserId();
        Task task = requireTask(taskId, tenant);
        if (task.getAssignee() != null && !task.getAssignee().isEmpty()) {
            throw new IllegalStateException("任务已被 " + task.getAssignee() + " 认领，无法重复认领");
        }
        withAuthenticatedUser(user, () -> taskService.claim(taskId, user));
        log.info("任务认领: taskId={}, user={}, tenant={}", taskId, user, tenant);
    }

    @Override
    @Transactional
    public void completeTask(String taskId, String action, String comment,
                             Map<String, Object> formVariables) {
        String user = identity.currentUserId();
        Task task = requireTask(taskId, identity.currentTenantId());
        boolean approved = !"reject".equalsIgnoreCase(action == null ? "" : action.trim());

        // 这一票的结论先按任务记下（放在驳回分支之前：驳回也是"投了一票"，同样要留痕）：
        // 会签表单靠它显示"他人已同意/已驳回"，实例级的 approved 只会互相覆盖。
        withAuthenticatedUser(user, () -> taskService.setVariableLocal(
                taskId, VAR_DECISION, approved ? DECISION_APPROVE : DECISION_REJECT));

        // 驳回：节点配了驳回目标（BPMN 的 ckplm:reject）就按目标<b>退还</b>回去（运行期跳转），
        // 不走下面那套"写 approved=false + 由图上条件分支决定去哪"。
        // 为什么必须分开：图上没有驳回出边的流程（大多数设计器产出的流程），
        // 老口径会让驳回悄悄走默认分支继续往前 —— 界面上写着"驳回后回到发起人"，
        // 流程实际继续往前走，这是最难被发现的一类错。
        // 没配的流程（手写 BPMN、老部署）返回 false，仍走老口径，行为不变。
        if (!approved && rejectRouter.reject(task, comment)) {
            log.info("任务驳回: taskId={}, user={}, 已按配置退回到目标节点", taskId, user);
            autoAssignSingleCandidate(task.getProcessInstanceId());
            notifyTodoTasks(task.getProcessInstanceId(), user);
            notifyIfFinished(task.getProcessInstanceId(), user);
            return;
        }

        // 会签（多实例）的结论按本轮<b>所有票</b>汇总，不是"最后一票覆盖"：
        // approved 是实例级变量，3 人评审里 1 人驳回、2 人同意时，谁最后办谁说了算 ——
        // 最后投同意就把驳回抹掉了。汇总口径见 {@link #tallyCountersign}。
        boolean outcome = tallyCountersign(task, approved);

        withAuthenticatedUser(user, () -> {
            if (comment != null && !comment.trim().isEmpty()) {
                taskService.addComment(taskId, task.getProcessInstanceId(), comment.trim());
            }
            Map<String, Object> variables = new HashMap<>();
            // 节点表单填的变量先放（如「设置审批人」的 ckplmSetupAssignees_<活动id>）：
            // 它必须在任务完成时就进入流程作用域 —— 下游任务的 assignee / collection
            // 是在这一步之后立刻求值的，晚一步（或事后补写）下游就已经是"无人可办"了。
            if (formVariables != null && !formVariables.isEmpty()) {
                variables.putAll(formVariables);
            }
            // BPMN 排他网关条件：${approved} / ${!approved}
            // 系统口径放在后面：表单不该能改写"这次审批是什么结论"
            variables.put("approved", outcome);
            variables.put(VAR_LAST_ACTION, action);
            variables.put(VAR_LAST_COMMENT, comment);
            taskService.complete(taskId, variables);
        });
        // ★ 收口「终止同层分支」必须紧跟办理之后：并行的兄弟支路此时还在等办，
        //   晚了（比如等通知发完）就可能在收口前被人抢办，出现"刚终止的任务又被提交"。
        //   必须在 autoAssignSingleCandidate 之前 —— 否则先给即将消失的任务指派了办理人。
        branchTerminator.terminateSiblings(
                task.getProcessInstanceId(), task.getProcessDefinitionId(), task.getTaskDefinitionKey());
        log.info("任务办理: taskId={}, action={}, 这一票={}, 本轮结论={}, user={}, 表单变量数={}",
                taskId, action, approved, outcome, user, formVariables == null ? 0 : formVariables.size());
        // ★ 先做"单人候选自动指派"，再发通知 —— 顺序不能反：
        //   「设置审批人」管理的办理节点在 BPMN 里只有 candidateUsers（设计意图：可能多人 → 认领），
        //   候选人恰好一人时（单人办理的常态）要直接指派，任务才会进他的「待办」；
        //   否则先通知，通知到的是"assignee 还空着"的任务（用户实测反馈即此：
        //   提交「设置审批人」后 Roney.Liu 收不到「申请填报与完善」任务、act_ru_task.assignee_ 为空）。
        autoAssignSingleCandidate(task.getProcessInstanceId());
        // 办理会推进流程：下游任务可能要落到别人头上，或者整条流程就此结束
        notifyTodoTasks(task.getProcessInstanceId(), user);
        notifyIfFinished(task.getProcessInstanceId(), user);
    }

    /**
     * 「任务无人可办」留痕 —— 既没有 assignee，也没有候选人和候选组。
     *
     * <p>这类任务不报错、也不出现在任何人的待办里：流程"正常"停在这一步，谁都不知道它在等谁。
     * 典型成因是活动配的角色眼下没有成员（{@code candidateGroups} 指向一个空角色），
     * 或参与人表达式取到了空集合。所以按节点落一条 WARN 到「节点执行日志」——
     * 流程详情里点开这个节点就能看到，不必等业务方来问"这单子怎么没人审"。
     */
    private void warnOrphanTask(Task task, String processInstanceId) {
        warnNoAssignee(task, processInstanceId,
                ORPHAN_TASK_MARK + "：既没有办理人，也没有候选人或候选组",
                "流程不会报错，但这一步不会出现在任何人的待办里。"
                        + "常见原因：活动配置的角色当前没有成员，或参与人表达式取到空集合"
                        + "（如「设置审批人」未选人）。"
                        + "处理：补上角色成员，或由管理员把该任务转办给具体的人。");
    }

    /**
     * 落一条「任务无人可办」的 WARN 到节点执行日志。
     *
     * <p>去重：同一个卡住的任务在后续每一步都会被重新扫到，按「实例 + 节点 + 同一条消息」判重，
     * 免得把日志刷成一屏。写日志失败只记 debug —— 可观测性问题不该升级成流程故障。
     */
    private void warnNoAssignee(Task task, String processInstanceId, String message, String detail) {
        recordNodeWarn(task, processInstanceId, message, detail);
    }

    /**
     * 记一条节点级 WARN（流程详情里点开该节点可见）。
     *
     * <p>去重口径同上：同一条消息在同一个节点上只记一次 —— 这类检查在每一步办理后都会重跑。
     */
    private void recordNodeWarn(Task task, String processInstanceId, String message, String detail) {
        try {
            String activityId = task.getTaskDefinitionKey();
            boolean already = nodeLogService.findByActivity(processInstanceId, activityId).stream()
                    .anyMatch(row -> message.equals(row.getMessage()));
            if (already) {
                return;
            }
            ProcessNodeLog row = new ProcessNodeLog();
            row.setProcessInstanceId(processInstanceId);
            row.setActivityId(activityId);
            row.setActivityName(task.getName());
            row.setLevel(ProcessNodeLog.LEVEL_WARN);
            row.setSource(ProcessNodeLog.SOURCE_SYSTEM);
            row.setMessage(message);
            row.setDetail(detail);
            nodeLogService.record(row);
            log.warn("节点告警: instance={} task={} node={} message={}",
                    processInstanceId, task.getId(), activityId, message);
        } catch (Exception e) {
            log.debug("记录节点告警失败: task={} error={}", task.getId(), e.getMessage());
        }
    }

    /** 声明式审批人的解析结果 */
    private enum AssigneeOutcome {
        /** 已解析并指派 */
        ASSIGNED,
        /** BPMN 声明了策略，但解析失败（已落节点日志） */
        FAILED,
        /** 该节点没有声明"由运行期解析"的策略 */
        NOT_DECLARED,
    }

    /**
     * 按 BPMN 上声明的审批人策略落人 —— 「发起人上级」与「部门主管」。
     *
     * <h3>为什么不在 BPMN 里写 {@code ${ckplmInitiatorLeader}}</h3>
     * <p>变量式写法只能"发起时快照"：流程跨周时主管换人、人员调部门都不生效，最后派给一个已经离任的人。
     * 声明式则是在<b>任务创建的这一步</b>按当时的数据解析，而且老流程不用重新部署
     * （判定只读 BPMN 上已有的 {@code ckplm:assignee} 声明，与驳回路由、会签规则同一套做法）。
     *
     * <p>两条解析链：
     * <ul>
     *   <li>发起人上级：{@code initiator} → 用户 → 所属组织 → {@code leader_oid}；</li>
     *   <li>部门主管：{@code businessObjectSet.primary} → 对象上的部门字段（{@code deptFieldKey}）
     *       → 组织（按 oid 或编码匹配）→ {@code leader_oid}。</li>
     * </ul>
     * 任何一环缺失都<b>不阻断</b>流程：留一条能直接动手的节点日志（补主管 / 补字段值 / 转办），任务仍在。
     */
    private AssigneeOutcome resolveDeclaredAssignee(Task task, String processInstanceId) {
        try {
            DeclaredAssignee declared = declaredAssigneeOf(taskElementOf(task));
            if (declared == null || !declared.isLeaderStrategy()) {
                return AssigneeOutcome.NOT_DECLARED;
            }
            Object raw = runtimeService.getVariable(processInstanceId, VAR_INITIATOR);
            String initiator = raw == null ? null : raw.toString().trim();
            // 解析失败时原因已经写进节点日志，这里拿到 null 就直接收工
            Organization org = declared.isDeptLeader()
                    ? deptOrgOf(task, processInstanceId, declared.deptFieldKey())
                    : initiatorOrgOf(task, processInstanceId, initiator);
            if (org == null) {
                return AssigneeOutcome.FAILED;
            }
            return assignToOrgLeader(task, processInstanceId, org, declared.label(), initiator);
        } catch (Exception e) {
            log.warn("解析声明式审批人失败: task={} error={}", task.getId(), e.getMessage());
            return AssigneeOutcome.FAILED;
        }
    }

    /** 「发起人上级」：发起人 → 用户 → 所属组织（失败时已落日志，返回 null） */
    private Organization initiatorOrgOf(Task task, String processInstanceId, String initiator) {
        String via = "发起人上级";
        if (!isNotBlank(initiator)) {
            failDeclaredAssignee(task, processInstanceId, via, "流程里没有记录发起人（initiator 变量为空）");
            return null;
        }
        User user = userService.findByUsername(initiator);
        if (user == null || !isNotBlank(user.getOrgOid())) {
            failDeclaredAssignee(task, processInstanceId, via, "发起人「" + initiator + "」未归属任何部门");
            return null;
        }
        Organization org = organizationService.findByOid(user.getOrgOid());
        if (org == null) {
            failDeclaredAssignee(task, processInstanceId, via,
                    "发起人「" + initiator + "」所属部门已不存在（orgOid=" + user.getOrgOid() + "）");
        }
        return org;
    }

    /**
     * 「部门主管」：业务对象上的部门字段 → 组织（失败时已落日志，返回 null）。
     *
     * <p>字段值按「组织 oid」或「组织编码」两种写法都认 —— 页面上选部门时存的是 oid，
     * 但导入的历史数据里也有存编码的；两种都对不上才算真的配错了。
     *
     * <p>取值要走两个 entityType：{@code rootTypeCode}（能力宿主，IBA 实际落库的键）
     * 与 {@code typeCode}（软类型自身）—— 两种约定在历史数据里都存在，按顺序取第一个非空值。
     */
    private Organization deptOrgOf(Task task, String processInstanceId, String deptFieldKey) {
        String via = "部门主管";
        if (!isNotBlank(deptFieldKey)) {
            failDeclaredAssignee(task, processInstanceId, via, "节点没有配置「部门字段」（deptFieldKey）");
            return null;
        }
        Map<String, Object> primary = primaryBusinessObject(processInstanceId);
        if (primary == null) {
            failDeclaredAssignee(task, processInstanceId, via,
                    "该流程没有关联业务对象（businessObjectSet.primary 为空），无处读取部门字段");
            return null;
        }
        String entityOid = textValue(primary.get("entityOid"));
        String entityCode = textValue(primary.get("code"));
        String value = attributeValue(entityOid, deptFieldKey,
                textValue(primary.get("rootTypeCode")), textValue(primary.get("typeCode")));
        if (!isNotBlank(value)) {
            failDeclaredAssignee(task, processInstanceId, via,
                    "业务对象「" + (isNotBlank(entityCode) ? entityCode : entityOid) + "」的字段「"
                            + deptFieldKey + "」为空");
            return null;
        }
        Organization org = organizationService.findByOid(value);
        if (org == null) {
            org = organizationService.findByCode(value);
        }
        if (org == null) {
            failDeclaredAssignee(task, processInstanceId, via,
                    "字段「" + deptFieldKey + "」的值「" + value + "」对不上任何部门（既不是组织 oid，也不是组织编码）");
        }
        return org;
    }

    /** 组织的主管道人：指派 + 留痕（含"主管就是发起人本人"的自审提示） */
    private AssigneeOutcome assignToOrgLeader(Task task, String processInstanceId,
                                              Organization org, String via, String initiator) {
        if (!isNotBlank(org.getLeaderOid())) {
            return failDeclaredAssignee(task, processInstanceId, via, "部门「" + org.getName() + "」还没有设置主管");
        }
        User leader = userService.findByOid(org.getLeaderOid());
        if (leader == null) {
            return failDeclaredAssignee(task, processInstanceId, via,
                    "部门「" + org.getName() + "」的主管账号不存在（leaderOid=" + org.getLeaderOid() + "）");
        }
        if (!leader.isEnabled()) {
            return failDeclaredAssignee(task, processInstanceId, via,
                    "部门「" + org.getName() + "」的主管「" + leader.getUsername() + "」已停用");
        }
        // 以主管自己的身份指派（与单人候选自动指派同一口径：Flowable 会记操作人）
        withAuthenticatedUser(leader.getUsername(),
                () -> taskService.setAssignee(task.getId(), leader.getUsername()));
        nodeLogService.info(processInstanceId, task.getTaskDefinitionKey(), task.getName(),
                ProcessNodeLog.SOURCE_SYSTEM,
                "按「" + via + "」指派给「" + displayNameOf(leader) + "」（部门「" + org.getName() + "」的主管）");
        log.info("按声明指派（{}）: task={} node={} leader={}",
                via, task.getId(), task.getTaskDefinitionKey(), leader.getUsername());
        if (isNotBlank(initiator) && initiator.equals(leader.getUsername())) {
            // 一个人部门 / 发起人自己就是主管：照常指派（跳过节点等于悄悄改流程语义，审计上也说不过去），
            // 但要把"存在自审"记下来 —— 这是审计要看的，不是系统要替业务决定的事
            recordNodeWarn(task, processInstanceId,
                    "「" + via + "」解析到的是发起人本人（存在自审）",
                    "发起人「" + initiator + "」正是部门「" + org.getName() + "」的主管。"
                            + "系统按配置照常指派（不擅自跳过审批），如需回避请调整组织主管或改用其他策略。");
        }
        return AssigneeOutcome.ASSIGNED;
    }

    private AssigneeOutcome failDeclaredAssignee(Task task, String processInstanceId,
                                                 String via, String reason) {
        warnNoAssignee(task, processInstanceId,
                ORPHAN_TASK_MARK + "：无法解析「" + via + "」",
                reason + "。流程不会报错，但这一步不会出现在任何人的待办里。"
                        + "处理：补齐上面缺的数据（组织主管 / 对象上的部门字段），"
                        + "再由管理员把任务转办给具体的人 —— 不必重跑流程。");
        return AssigneeOutcome.FAILED;
    }

    /** 节点声明的审批人（{@code ckplm:assignee} JSON 的只读视图） */
    private record DeclaredAssignee(String strategy, String deptFieldKey) {

        boolean isDeptLeader() {
            return STRATEGY_DEPT_LEADER.equals(strategy);
        }

        boolean isLeaderStrategy() {
            return isDeptLeader() || STRATEGY_INITIATOR_LEADER.equals(strategy);
        }

        String label() {
            return isDeptLeader() ? "部门主管" : "发起人上级";
        }
    }

    /** 读节点声明的审批人策略（{@code ckplm:assignee} 是 JSON） */
    private static DeclaredAssignee declaredAssigneeOf(FlowElement element) {
        String json = attributeOf(element, ATTR_ASSIGNEE);
        if (json == null || json.trim().isEmpty()) {
            return null;
        }
        try {
            JsonNode node = MAPPER.readTree(json);
            if (node == null || !node.isObject()) {
                return null;
            }
            String strategy = textOf(node.get("strategy"));
            return strategy == null ? null : new DeclaredAssignee(strategy, textOf(node.get("deptFieldKey")));
        } catch (Exception e) {
            return null;   // 脏 JSON 当作"没有声明"，走通用兜底
        }
    }

    /** 流程关联的<b>主</b>业务对象（{@code businessObjectSet.primary}） */
    @SuppressWarnings("unchecked")
    private Map<String, Object> primaryBusinessObject(String processInstanceId) {
        Object raw = runtimeService.getVariable(processInstanceId, VAR_BUSINESS_OBJECT_SET);
        if (!(raw instanceof Map<?, ?> set)) {
            return null;
        }
        Object primary = set.get("primary");
        return primary instanceof Map<?, ?> map ? (Map<String, Object>) map : null;
    }

    /** 读业务对象上某个 IBA 字段的值（按给定的 entityType 顺序取第一个非空值） */
    private String attributeValue(String entityOid, String attrCode, String... entityTypes) {
        if (!isNotBlank(entityOid)) {
            return null;
        }
        for (String entityType : entityTypes) {
            if (!isNotBlank(entityType)) {
                continue;
            }
            String value = firstValue(ibaDataService.getValues(entityType, entityOid).get(attrCode));
            if (isNotBlank(value)) {
                return value;
            }
        }
        return null;
    }

    /** IBA 值可能是标量、也可能是列表（多值属性）：统一取第一个 */
    private static String firstValue(Object raw) {
        if (raw instanceof List<?> list) {
            return list.isEmpty() ? null : textValue(list.get(0));
        }
        return textValue(raw);
    }

    private static String textValue(Object raw) {
        return raw == null ? null : raw.toString().trim();
    }

    private static String textOf(JsonNode node) {
        return node == null || node.isNull() ? null : node.asText();
    }

    /** 人员显示名：优先姓名，其次用户名（日志里要能认出是谁） */
    private static String displayNameOf(User user) {
        String display = user == null ? null : user.getDisplayName();
        return isNotBlank(display) ? display : (user == null ? "" : user.getUsername());
    }

    /**
     * 「单人候选人」自动指派 —— 任务创建后的兜底（2026-10-12）。
     *
     * <p><b>为什么需要</b>：流程设计器把「设置审批人」管理的<b>办理节点</b>编译成
     * {@code flowable:candidateUsers="${ckplmSetupAssignees_<节点id>}"}（见
     * {@code flow-designer/bpmn-compiler/compile.ts}，设计意图是"办理节点可能多人，由他们认领"）。
     * 但 Flowable 的语义里**候选人 ≠ 指派** —— 任务建出来 {@code assignee_} 为空，只会出现在
     * 「可认领」里，用户的感受就是"任务没派给我"（实测：提交「设置审批人」后
     * Roney.Liu 收不到「申请填报与完善」任务，`act_ru_task.assignee_` 为空、
     * `act_ru_identitylink` 里他是一条 candidate）。
     *
     * <p><b>口径</b>：候选人（userId 型）恰好一人、且没有候选组时 → 直接指派给这个人；
     * **多候选人 / 带候选组 → 保持候选**（那本来就是"谁合适谁认领"的场景，不替用户做主）。
     *
     * <p>候选人可能是用户名或人员 oid（两种写法历史上都存在），这里**原样**指派 ——
     * 待办查询侧两种都认（见 {@link ProcessIdentitySupport#currentUserIdentifiers()}）。
     *
     * <p>兜底不阻断办理：异常只记警告，任务仍在（可认领）。
     */
    private void autoAssignSingleCandidate(String processInstanceId) {
        if (!isNotBlank(processInstanceId)) {
            return;
        }
        try {
            for (Task t : taskService.createTaskQuery().processInstanceId(processInstanceId).list()) {
                if (isNotBlank(t.getAssignee())) {
                    continue;
                }
                List<IdentityLink> links = taskService.getIdentityLinksForTask(t.getId());
                if (links.isEmpty()) {
                    // 先看这步是不是"由运行期解析的人"（目前只有「发起人上级」）；
                    // 解析不出来时它自己会留一条带原因的日志，不再重复报通用的"无人可办"
                    AssigneeOutcome outcome = resolveDeclaredAssignee(t, processInstanceId);
                    if (outcome == AssigneeOutcome.NOT_DECLARED) {
                        // 连候选都没有 = 这任务在<b>任何人</b>的待办里都不存在，也不会报错 —— 只能靠日志留痕
                        warnOrphanTask(t, processInstanceId);
                    }
                    continue;
                }
                Set<String> candidates = new LinkedHashSet<>();
                boolean hasGroup = false;
                for (IdentityLink link : links) {
                    if (isNotBlank(link.getGroupId())) {
                        hasGroup = true;
                    }
                    if (isNotBlank(link.getUserId())) {
                        candidates.add(link.getUserId());
                    }
                }
                if (candidates.size() != 1 || hasGroup) {
                    continue;   // 多人 / 带候选组：保持候选，等待认领
                }
                String assignee = candidates.iterator().next();
                // 以候选人自己的身份指派（Flowable 的 setAssignee 会记操作人）
                withAuthenticatedUser(assignee, () -> taskService.setAssignee(t.getId(), assignee));
                log.info("任务自动指派（唯一候选人）: task={} node={} assignee={}",
                        t.getId(), t.getTaskDefinitionKey(), assignee);
            }
        } catch (Exception e) {
            log.warn("单人候选自动指派失败: instance={} error={}", processInstanceId, e.getMessage());
        }
    }

    /**
     * 会签（多实例审批）的本轮结论 —— 按票汇总，而不是"最后一票覆盖"。
     *
     * <h3>为什么必须汇总</h3>
     * <p>{@code approved} 是<b>实例级</b>变量，会签里每个并行实例完成任务都会写一次，
     * 后写的一定盖掉先写的。于是「一票否决」形同虚设：3 人评审里 1 人驳回、2 人同意，
     * 只要最后办的是同意，流程照样走"通过 → 发布"；{@code PERCENT / COUNT} 也同样
     * 变成"谁最后办谁代表整轮"。本方法把本轮每一票记在多实例<b>根执行</b>的局部变量里
     * （见 {@link #VAR_VOTE_APPROVE}），每次办理按累计票数重算结论，最后写出的
     * {@code approved} 就是整轮的结论 —— 引擎在活动结束后紧接着求值的那一次拿到的正是它。
     *
     * <h3>口径</h3>
     * <p>与编译层写进 {@code completionCondition} 的口径一致（见 {@link CountersignRule}）：
     * 规则取自 BPMN 上的 {@code ckplm:passRule}；没配规则 = 全部同意才通过。
     * 汇总口径取「应到人数」（{@code nrOfInstances}）而非"已投票数"——会签提前满足条件时，
     * 剩余实例本就不会再投，用已投票数当分母会让比例虚高。
     *
     * <p>兜底不阻断办理：非多实例、找不到根执行、应到人数取不到（比例口径算不出来）、
     * 汇总过程出异常 —— 一律退回"这一票就是结论"的老口径（宁可少一层语义，也不能让办理失败，
     * 更不能凭空多出一个否决）。但 {@code ckplmVetoed} <b>任何时候都要写</b>：
     * 编译层把它拼进了 {@code completionCondition}，变量缺失会让条件求值直接报错，
     * 那时任务根本办不掉 —— 比结论不准严重得多。
     *
     * @param approved 这一票的结论（true=同意）
     * @return 本轮会签的结论（true=通过）
     */
    private boolean tallyCountersign(Task task, boolean approved) {
        try {
            if (!isMultiInstanceActivity(task)) {
                return approved;
            }
            String root = multiInstanceRootId(task);
            if (root == null) {
                // 找不到本轮根执行（理论上不该发生）：挂到任务自己的执行上，至少保证变量存在
                markDoomed(task.getExecutionId(), false);
                log.warn("会签根执行未找到，本轮按单票口径: instance={} node={}",
                        task.getProcessInstanceId(), task.getTaskDefinitionKey());
                return approved;
            }
            int approve = voteCount(root, VAR_VOTE_APPROVE) + (approved ? 1 : 0);
            int reject = voteCount(root, VAR_VOTE_REJECT) + (approved ? 0 : 1);
            runtimeService.setVariableLocal(root, VAR_VOTE_APPROVE, approve);
            runtimeService.setVariableLocal(root, VAR_VOTE_REJECT, reject);

            CountersignRule rule = CountersignRule.parse(attributeOf(taskElementOf(task), ATTR_PASS_RULE));
            int total = instanceCount(root);
            if (rule.getMode() == CountersignRule.Mode.PERCENT && total <= 0) {
                markDoomed(root, false);
                log.warn("会签应到人数取不到，本轮按单票口径: instance={} node={}",
                        task.getProcessInstanceId(), task.getTaskDefinitionKey());
                return approved;
            }
            boolean passed = rule.passed(approve, reject, total);
            // 提前收口：本轮已不可能通过 → 置标记，编译层的 completionCondition 以 `|| ckplmVetoed`
            // 短路离开多实例（老部署的产物没有这段短路，只是会白办，结论不受影响）
            markDoomed(root, rule.doomed(approve, reject, total));
            log.info("会签汇总: instance={} node={} 同意={} 驳回={} 应到={} 规则={}{} → {}",
                    task.getProcessInstanceId(), task.getTaskDefinitionKey(),
                    approve, reject, total, rule.getMode(), rule.isVeto() ? "(含一票否决)" : "",
                    passed ? "通过" : "不通过");
            return passed;
        } catch (Exception e) {
            // 变量已写入一半时也要保证标记存在，否则下游 completionCondition 求值会报错
            markDoomed(task.getExecutionId(), false);
            log.warn("会签汇总失败，按单票口径处理: task={} error={}", task.getId(), e.getMessage());
            return approved;
        }
    }

    /** 写「本轮已不可能通过」标记；写不进去只记警告 —— 一个标记不该让办理失败 */
    private void markDoomed(String executionId, boolean doomed) {
        try {
            runtimeService.setVariableLocal(executionId, VAR_VETOED, doomed);
        } catch (Exception e) {
            log.warn("写会签收口标记失败: execution={} error={}", executionId, e.getMessage());
        }
    }

    /** 该任务所在活动是否多实例（会签 / 并行 / 或签）—— 只有它才需要按票汇总 */
    private boolean isMultiInstanceActivity(Task task) {
        FlowElement element = taskElementOf(task);
        return element instanceof UserTask userTask
                && userTask.getLoopCharacteristics() instanceof MultiInstanceLoopCharacteristics;
    }

    private FlowElement taskElementOf(Task task) {
        return flowElementOf(mainProcessOf(task.getProcessDefinitionId()), task.getTaskDefinitionKey());
    }

    /**
     * 本轮多实例的<b>根执行</b> id（找不到返回 null）。
     *
     * <p>判据是<b>结构</b>而不是引擎内部变量：并行多实例的任务挂在根的子执行上，串行多实例的任务
     * 直接挂在根上 —— 两种情况下"根"与子执行的活动 id 相同，所以沿父链取<b>同活动的最上层执行</b>
     * 就是根，再往上第一个活动 id 不同的执行就是它的边界。这样不依赖 {@code nrOfInstances}
     * 这类引擎内部细节（名字或存放方式一变，按变量找就会静默失效）。
     */
    private String multiInstanceRootId(Task task) {
        String activityId = task.getTaskDefinitionKey();
        String id = task.getExecutionId();
        String root = null;
        for (int depth = 0; id != null && depth < MAX_EXECUTION_DEPTH; depth++) {
            Execution execution = runtimeService.createExecutionQuery().executionId(id).singleResult();
            if (execution == null) {
                break;
            }
            if (activityId.equals(execution.getActivityId())) {
                root = id;
            } else if (root != null) {
                break;   // 已越过多实例根
            }
            id = execution.getParentId();
        }
        return root;
    }

    /** 本轮应到人数（{@code nrOfInstances}）；取不到一律 0，由调用方决定"算不出来怎么办" */
    private int instanceCount(String executionId) {
        try {
            Object value = runtimeService.getVariable(executionId, VAR_MI_INSTANCES);
            return value instanceof Number number ? number.intValue() : 0;
        } catch (Exception e) {
            return 0;
        }
    }

    /** 执行作用域上的整型变量（不存在 / 类型异常一律当 0） */
    private int voteCount(String executionId, String name) {
        Object value = runtimeService.getVariableLocal(executionId, name);
        return value instanceof Number number ? number.intValue() : 0;
    }

    /**
     * 读扩展属性（{@code ckplm:*}）。
     *
     * <p>按<b>属性名</b>找而不是按命名空间找：Flowable 把扩展属性按命名空间分组存放，
     * 而命名空间常量前后端各有一份，写错一个字符就会变成"配了却读不到"。
     * 与 {@code reject/RejectRouter} 的同类方法保持一致。
     */
    private static String attributeOf(FlowElement element, String name) {
        if (element == null) {
            return null;
        }
        Map<String, List<ExtensionAttribute>> attributes = element.getAttributes();
        if (attributes == null) {
            return null;
        }
        for (List<ExtensionAttribute> group : new ArrayList<>(attributes.values())) {
            for (ExtensionAttribute attribute : group) {
                if (name.equals(attribute.getName())) {
                    return attribute.getValue();
                }
            }
        }
        return null;
    }

    @Override
    public ProcessTaskFormVO taskForm(String taskId) {
        Task task = requireTask(taskId, identity.currentTenantId());
        ProcessTaskFormVO vo = new ProcessTaskFormVO();
        vo.setFormKey(task.getFormKey());
        vo.setTaskDefinitionKey(task.getTaskDefinitionKey());
        vo.setTaskName(task.getName());
        // 业务对象引用：表单按它现算数据（如门决策申请单的"未关闭阻断问题数"）——
        // 取第一条即可（本项目的发起口径是"一个实例一个主业务对象"，见 StartProcessModal）
        List<ProcessTaskContextVO.EntityRef> refs = entityRefsOf(task.getProcessInstanceId());
        if (!refs.isEmpty()) {
            vo.setBusinessOid(refs.get(0).getEntityOid());
            vo.setBusinessTypeCode(refs.get(0).getTypeCode());
        }
        vo.setVariables(formVariablesOf(task.getProcessInstanceId()));
        ProcessDefinition pd = findProcessDefinition(task.getProcessDefinitionId(), new HashMap<>());
        if (pd == null) {
            return vo;
        }
        vo.setProcessDefinitionKey(pd.getKey());
        vo.setProcessDefinitionVersion(pd.getVersion());
        vo.setDslJson(resolveDeployedDsl(pd));
        return vo;
    }

    /**
     * 本实例的**用户表单变量**（流程变量剔除 {@link #INTERNAL_VARIABLES}）。
     *
     * <p>为什么要给办理页：发起节点与办理节点常常配**同一张表单**（技术评审流程的
     * {@code technical-review-apply} 就是开始节点与「材料准备与确认」共用），发起时填的申请信息
     * 已经作为流程变量存下来了，办理页却没有任何途径拿到 —— 表现为"发起时填过，进来还是空白"。
     *
     * <p>读运行中实例的值；实例若已结束（刚办完最后一步、用户还开着页面）退回历史变量。
     * 读失败一律返回空表：回填是锦上添花，不能让它把办理本身弄挂。
     *
     * <p>逐项做一次 JSON 可序列化探测：Flowable 允许存任意 {@code Serializable}，
     * 某个实例若存了序列化 POJO，直接把整张表塞进响应会让<b>整个办理页</b> 500。
     * 序列化不了的项跳过（前端本来就渲染不了它），其余照常回填。
     */
    private Map<String, Object> formVariablesOf(String instanceId) {
        Map<String, Object> result = new LinkedHashMap<>();
        if (instanceId == null || instanceId.trim().isEmpty()) {
            return result;
        }
        Map<String, Object> raw = null;
        try {
            raw = runtimeService.getVariables(instanceId);
        } catch (Exception e) {
            log.warn("读取流程变量失败: instance={} error={}", instanceId, e.getMessage());
        }
        if (raw == null || raw.isEmpty()) {
            raw = historicVariables(instanceId);
        }
        for (Map.Entry<String, Object> entry : raw.entrySet()) {
            String name = entry.getKey();
            if (name == null || INTERNAL_VARIABLES.contains(name)) {
                continue;
            }
            try {
                MAPPER.writeValueAsString(entry.getValue());
                result.put(name, entry.getValue());
            } catch (Exception e) {
                log.debug("跳过无法序列化的流程变量: instance={} name={} type={}",
                        instanceId, name, entry.getValue() == null ? "null" : entry.getValue().getClass().getName());
            }
        }
        return result;
    }

    @Override
    public ProcessStartFormVO startForm(String processKey) {
        ProcessStartFormVO vo = new ProcessStartFormVO();
        if (processKey == null || processKey.trim().isEmpty()) {
            return vo;
        }
        ProcessDefinitionQuery query = repositoryService.createProcessDefinitionQuery()
                .processDefinitionKey(processKey.trim())
                .latestVersion();
        // 多租户：Flowable 的租户只是标记 + 显式过滤条件（不自动隔离）。漏掉这一步就会
        // 跨租户撞上同 key 的其它部署（实测 5 个租户各一份 → "Query return 5 results instead of max 1"）
        String tenant = identity.currentTenantId();
        query = (tenant == null || tenant.trim().isEmpty())
                ? query.processDefinitionWithoutTenantId()
                : query.processDefinitionTenantId(tenant);
        ProcessDefinition pd = query.singleResult();
        if (pd == null) {
            // 未部署：没有"将要跑的版本"，也就没有表单可言（发起弹框此时不给「发起」）
            return vo;
        }
        vo.setProcessDefinitionKey(pd.getKey());
        vo.setProcessDefinitionVersion(pd.getVersion());
        vo.setDslJson(resolveDeployedDsl(pd));
        String[] ref = readStartEventRef(pd.getId());
        vo.setStartEventId(ref[0]);
        vo.setFormKey(ref[1]);
        return vo;
    }

    /** BPMN 里 `<startEvent …>` 标签（BPMN 只允许一个开始事件，取第一个即可） */
    private static final Pattern START_EVENT_TAG = Pattern.compile("<startEvent\\b[^>]*>");

    /**
     * 从**部署的 BPMN 原文**里读开始事件的 id 与表单键，返回 {@code [id, formKey]}。
     *
     * <p>为什么读原文：{@code org.flowable.bpmn.model.StartEvent} 在 Flowable 模型里
     * <b>没有 formKey 字段</b>（只有 UserTask 有），本编译层写在 startEvent 上的
     * {@code flowable:formKey} 是扩展属性 —— {@code getBpmnModel()} 拿不到，读部署产物
     * （{@code getProcessModel()} 的 XML）是唯一与运行完全一致、又不丢信息的途径。
     *
     * <p>取不到时返回空串对：发起弹框按"没配表单"处理，不影响发起本身。
     */
    private String[] readStartEventRef(String processDefinitionId) {
        // Flowable 7 的 getProcessModel 给的是 InputStream（不是 String）
        try (InputStream in = repositoryService.getProcessModel(processDefinitionId)) {
            if (in == null) {
                return new String[] { null, null };
            }
            String xml = new String(in.readAllBytes(), StandardCharsets.UTF_8);
            Matcher tag = START_EVENT_TAG.matcher(xml);
            if (!tag.find()) {
                return new String[] { null, null };
            }
            return new String[] {
                    xmlAttrOf(tag.group(), "id"),
                    xmlAttrOf(tag.group(), "flowable:formKey"),
            };
        } catch (Exception e) {
            log.warn("解析开始节点表单失败: definitionId={} error={}", processDefinitionId, e.getMessage());
            return new String[] { null, null };
        }
    }

    /** 从一段 XML 标签里取属性值（双/单引号都认）；取不到返回 null */
    private static String xmlAttrOf(String tag, String name) {
        Matcher m = Pattern.compile("\\b" + Pattern.quote(name) + "\\s*=\\s*[\"']([^\"']*)[\"']").matcher(tag);
        return m.find() ? m.group(1) : null;
    }

    /**
     * 取「该流程定义所用那一版」的 DSL。
     *
     * <p>为什么不直接用模板主档的 {@code dslJson}：主档镜像的是<b>最新版</b>，而在途实例跑的是
     * 它发起时部署的那一版 —— 表单内容由流程派生（「设置审批人」要列出下游人工活动），
     * 用最新版会列出该实例根本没有（或名称已改）的活动。
     *
     * <p><b>定位方式是部署 id</b>，不是版本号：Flowable 的"定义版本"是<i>同 key 第几次部署</i>，
     * 与模板版本号不是同一序列（模板 v3 可能只部署过一次 → 定义版本是 1），按数字对会取到错的那一版。
     * 部署时写下的 {@code deployment_id} 才是"哪一版模板内容"的唯一对应关系。
     *
     * <p>取不到部署时（老数据）退回模板主档；都拿不到返回 {@code null} ——
     * 前端退化为通用表单，办理本身不依赖 DSL。
     */
    private String resolveDeployedDsl(ProcessDefinition pd) {
        try {
            if (pd.getDeploymentId() != null) {
                ProcessTemplateVersion byDeployment =
                        versionMapper.selectByDeploymentId(pd.getDeploymentId());
                if (byDeployment != null && isNotBlank(byDeployment.getDslJson())) {
                    return byDeployment.getDslJson();
                }
            }
            ProcessTemplate template = templateMapper.selectByKey(pd.getKey());
            return template == null ? null : template.getDslJson();
        } catch (Exception e) {
            log.warn("解析任务表单 DSL 失败: key={} deployment={} error={}",
                    pd.getKey(), pd.getDeploymentId(), e.getMessage());
            return null;
        }
    }

    private static boolean isNotBlank(String value) {
        return value != null && !value.trim().isEmpty();
    }

    @Override
    @Transactional
    public void transferTask(String taskId, String targetAssignee) {
        String target = identity.requireExistingUser(targetAssignee);
        String user = identity.currentUserId();
        Task task = requireTask(taskId, identity.currentTenantId());
        withAuthenticatedUser(user, () -> taskService.setAssignee(taskId, target));
        log.info("任务转办: taskId={}, from={}, to={}", taskId, user, target);
        notifyAssignee(target, "任务转办", "TASK_TRANSFER", task, user);
    }

    /**
     * 加签：按任务所在活动分派「会签式」或「或签式」。
     *
     * <h3>会签式（并行多实例）</h3>
     * <p>调用 {@code RuntimeService#addMultiInstanceExecution} 给多实例<b>追加一个执行</b>：
     * {@code nrOfInstances} 随之 +1，新执行立刻建出任务，所有人都办完这个节点才结束。
     * 这是"每人都必须审"的唯一正解 —— {@code ChangeActivityStateBuilder} 只能"移动"，
     * 往活动里"插入"一个节点它做不到（Flowable 7 无此能力）。
     *
     * <p>必须传多实例根的<b>父执行</b>：Flowable 在这个父执行的子执行里找多实例根，
     * 直接把根自己传进去会报 {@code No multi instance execution found}（实测，见
     * {@code CountersignAddSignerEngineTest}）。
     *
     * <p>新实例的元素变量（编译产物固定为 {@code ckplmApprover}）取加签人，任务直接落进
     * 对方的「待办」；若产物用的是候选组（节点策略是角色），元素变量不参与 assignee，
     * 这里补一次指派 —— 加签的语义是"指定你审"，不是"你也来抢"。
     *
     * <h3>或签式（M3 老口径）</h3>
     * <p>单实例 / 串行会签无法扩容：追加候选人并释放 assignee，双方都从「可认领」进入，
     * 谁先认领并提交谁算过。为什么必须释放 assignee（实测，见 {@code AddSignerEngineTest}）：
     * Flowable 的候选查询带 {@code taskUnassigned}、{@code claim} 也拒绝"已有办理人"的任务 ——
     * 只要 assignee 还在，被加签的人既看不到也接不了。
     *
     * <p>语义降级不静默：返回值里带 {@code mode}，前端据此给不同提示。
     */
    @Override
    @Transactional
    public ProcessAddSignerVO addSigner(String taskId, String targetAssignee) {
        String target = identity.requireExistingUser(targetAssignee);
        String user = identity.currentUserId();
        Task task = requireTask(taskId, identity.currentTenantId());

        MultiInstanceLoopCharacteristics loop = expandableCountersignOf(task);
        if (loop != null) {
            return addCountersignSigner(task, loop, target, user);
        }
        addOrSignCandidate(task, target, user);
        return new ProcessAddSignerVO(ProcessAddSignerVO.MODE_OR_SIGN, 0);
    }

    /**
     * 该任务所在活动能否在运行期扩容；返回 {@code null} ＝ 走或签。
     *
     * <p>串行多实例不扩容：只有一个活动执行在跑，追加执行会打乱 {@code loopCounter}
     * 驱动的队列语义，本期只做并行会签。没有元素变量（{@code loopCardinality} 画法）
     * 也不行 —— 那样无法把"这一票归谁"告诉引擎。
     */
    private MultiInstanceLoopCharacteristics expandableCountersignOf(Task task) {
        FlowElement element = taskElementOf(task);
        if (!(element instanceof UserTask userTask)) {
            return null;
        }
        // UserTask#getLoopCharacteristics 的返回类型本身就是 MultiInstanceLoopCharacteristics
        MultiInstanceLoopCharacteristics loop = userTask.getLoopCharacteristics();
        if (loop == null) {
            return null;
        }
        if (Boolean.TRUE.equals(loop.isSequential())) {
            return null;
        }
        String elementVariable = loop.getElementVariable();
        return elementVariable == null || elementVariable.isBlank() ? null : loop;
    }

    /** 会签式加签：往多实例里追加一个实例，新人也必须办完 */
    private ProcessAddSignerVO addCountersignSigner(
            Task task, MultiInstanceLoopCharacteristics loop, String target, String user) {
        if (target.equals(task.getAssignee())) {
            throw new IllegalArgumentException("加签对象已经是当前办理人，无需加签");
        }
        if (hasOpenTaskAt(task, target)) {
            throw new IllegalArgumentException("「" + target + "」在这个节点上已经有待办任务");
        }
        String root = multiInstanceRootId(task);
        Execution rootExecution = root == null ? null
                : runtimeService.createExecutionQuery().executionId(root).singleResult();
        String parentId = rootExecution == null ? null : rootExecution.getParentId();
        if (parentId == null) {
            // 结构异常（理论不该发生）：退回或签，至少让人进得来；返回的 mode 会如实告诉前端
            log.warn("会签式加签找不到多实例父执行，退回或签: task={} node={}",
                    task.getId(), task.getTaskDefinitionKey());
            addOrSignCandidate(task, target, user);
            return new ProcessAddSignerVO(ProcessAddSignerVO.MODE_OR_SIGN, 0);
        }

        String activityId = task.getTaskDefinitionKey();
        withAuthenticatedUser(user, () -> {
            Execution added = runtimeService.addMultiInstanceExecution(
                    activityId, parentId, Map.of(loop.getElementVariable(), target));
            assignIfNotOwned(added.getId(), target);
        });
        int total = instanceCount(root);
        log.info("会签式加签: taskId={}, by={}, signer={}, 应到人数={}", task.getId(), user, target, total);
        notifyAssignee(target, "任务加签", "TASK_ADD_SIGNER", task, user);
        return new ProcessAddSignerVO(ProcessAddSignerVO.MODE_COUNTERSIGN, total);
    }

    /**
     * 新实例的任务若没落到加签人头上（编译产物用候选组而非 {@code ${ckplmApprover}}），
     * 补一次指派 —— 加签是"指定你审"，不是"你也来抢"。
     *
     * <p>指派失败只记 WARN：候选人身份仍然在，管理员可以转办，别让"加签"整个失败。
     */
    private void assignIfNotOwned(String executionId, String target) {
        Task created;
        try {
            created = taskService.createTaskQuery().executionId(executionId).singleResult();
        } catch (Exception e) {
            log.warn("加签后查新任务失败: execution={} error={}", executionId, e.getMessage());
            return;
        }
        if (created == null || target.equals(created.getAssignee())) {
            return;
        }
        try {
            taskService.setAssignee(created.getId(), target);
        } catch (Exception e) {
            log.warn("加签后指派失败（候选人仍可认领）: task={} target={} error={}",
                    created.getId(), target, e.getMessage());
        }
    }

    /** 该节点上是否已有这个人的在办任务（办理人 / 候选人两种入口都算） */
    private boolean hasOpenTaskAt(Task task, String target) {
        boolean asAssignee = taskService.createTaskQuery()
                .processInstanceId(task.getProcessInstanceId())
                .taskDefinitionKey(task.getTaskDefinitionKey())
                .taskAssignee(target).count() > 0;
        if (asAssignee) {
            return true;
        }
        return taskService.createTaskQuery()
                .processInstanceId(task.getProcessInstanceId())
                .taskDefinitionKey(task.getTaskDefinitionKey())
                .taskCandidateUser(target).count() > 0;
    }

    /** 或签式加签（M3 老口径）：原办理人转候选人 → 加签人加候选人 → 释放 assignee */
    private void addOrSignCandidate(Task task, String target, String user) {
        String taskId = task.getId();
        String originalAssignee = task.getAssignee();
        if (target.equals(originalAssignee)) {
            throw new IllegalArgumentException("加签对象已经是当前办理人，无需加签");
        }
        // 重复加签显式拦一下：Flowable 的 identity link 会去重，但前端会以为"点了没反应"
        boolean already = taskService.getIdentityLinksForTask(taskId).stream()
                .anyMatch(link -> "candidate".equals(link.getType()) && target.equals(link.getUserId()));
        if (already) {
            throw new IllegalArgumentException("「" + target + "」已经是该任务的候选人");
        }
        withAuthenticatedUser(user, () -> {
            // 原办理人转候选人：否则释放 assignee 后他自己也看不到这个任务了
            if (originalAssignee != null && !originalAssignee.isBlank()) {
                taskService.addCandidateUser(taskId, originalAssignee);
            }
            taskService.addCandidateUser(taskId, target);
            taskService.setAssignee(taskId, null);
        });
        log.info("任务加签（或签）: taskId={}, by={}, signer={}, 原办理人(转候选人)={}",
                taskId, user, target, originalAssignee);
        notifyAssignee(target, "任务加签", "TASK_ADD_SIGNER", task, user);
    }

    @Override
    public List<ProcessCommentVO> findTaskComments(String taskId) {
        // 先校验任务归属当前租户，避免跨租户读取评论。
        // 注意：任务办理完成后会从 act_ru_task 移除，因此读场景必须允许历史任务
        //（前端「已办任务」行上也有「评论」按钮）。
        requireTaskOrHistoric(taskId, identity.currentTenantId());
        List<Comment> comments = taskService.getTaskComments(taskId);
        List<ProcessCommentVO> result = new ArrayList<>();
        if (comments == null) {
            return result;
        }
        for (Comment c : comments) {
            ProcessCommentVO vo = new ProcessCommentVO();
            vo.setId(c.getId());
            vo.setUserId(c.getUserId());
            vo.setTime(c.getTime());
            vo.setMessage(c.getFullMessage());
            vo.setType(c.getType());
            result.add(vo);
        }
        return result;
    }

    @Override
    @Transactional
    public void addTaskComment(String taskId, String comment) {
        if (comment == null || comment.trim().isEmpty()) {
            throw new IllegalArgumentException("评论内容不能为空");
        }
        String user = identity.currentUserId();
        Task task = requireTask(taskId, identity.currentTenantId());
        withAuthenticatedUser(user,
                () -> taskService.addComment(taskId, task.getProcessInstanceId(), comment.trim()));
    }

    // ==================== 流程监控 ====================

    @Override
    public List<ProcessInstanceVO> findInstances(String scope, int page, int size) {
        String tenant = identity.currentTenantId();
        String user = identity.currentUserId();
        String s = scope == null ? "all-running" : scope.trim();
        int first = first(page, size);
        List<ProcessInstanceVO> result = new ArrayList<>();

        if ("my-involved".equals(s)) {
            // 我参与的（含已结束）——只能查历史表
            List<HistoricProcessInstance> list = historyService.createHistoricProcessInstanceQuery()
                    .processInstanceTenantId(tenant)
                    .involvedUser(user)
                    .orderByProcessInstanceStartTime().desc()
                    .listPage(first, size);
            for (HistoricProcessInstance hpi : list) {
                result.add(toInstanceVO(hpi));
            }
            return result;
        }

        // 运行中：全部 / 我发起的
        org.flowable.engine.runtime.ProcessInstanceQuery query = runtimeService.createProcessInstanceQuery()
                .processInstanceTenantId(tenant);
        if ("my-running".equals(s)) {
            query.startedBy(user);
        }
        List<ProcessInstance> list = query.orderByStartTime().desc().listPage(first, size);
        for (ProcessInstance pi : list) {
            result.add(toInstanceVO(pi));
        }
        return result;
    }

    @Override
    public ProcessInstanceVO findInstanceDetail(String instanceId) {
        String tenant = identity.currentTenantId();
        ProcessInstance running = runtimeService.createProcessInstanceQuery()
                .processInstanceId(instanceId)
                .processInstanceTenantId(tenant)
                .singleResult();
        if (running != null) {
            ProcessInstanceVO vo = toInstanceVO(running);
            vo.setVariables(runtimeService.getVariables(instanceId));
            // 详情页要回答"卡在哪、等谁办"：补上当前节点与办理人（与对象详情页「正在执行中」同一口径）
            fillCurrentTasks(vo, instanceId);
            return vo;
        }
        HistoricProcessInstance historic = historyService.createHistoricProcessInstanceQuery()
                .processInstanceId(instanceId)
                .processInstanceTenantId(tenant)
                .singleResult();
        if (historic == null) {
            throw new IllegalArgumentException("流程实例不存在或不属于当前租户: " + instanceId);
        }
        ProcessInstanceVO vo = toInstanceVO(historic);
        vo.setVariables(historicVariables(instanceId));
        return vo;
    }

    @Override
    public List<ProcessInstanceVO> findInstancesByEntity(String entityOid, String entityVersion) {
        String oid = entityOid == null ? null : entityOid.trim();
        if (oid == null || oid.isEmpty()) {
            return List.of();
        }
        String ver = (entityVersion == null || entityVersion.trim().isEmpty())
                ? null : entityVersion.trim();
        // 关联表是"实例 ↔ 实体"的唯一来源；同一实例若关联了多个实体，这里按对象取、保序去重
        List<ProcessEntitySet> rows = ver == null
                ? entitySetService.findByEntityOid(oid)
                : entitySetService.findByEntityOidAndEntityVersion(oid, ver);
        Map<String, String> versionOf = new LinkedHashMap<>();
        for (ProcessEntitySet row : rows) {
            String id = row.getProcessInstanceId();
            if (id != null && !id.trim().isEmpty()) {
                versionOf.putIfAbsent(id.trim(), row.getEntityVersion());
            }
        }
        if (versionOf.isEmpty()) {
            return List.of();
        }
        String tenant = identity.currentTenantId();
        List<ProcessInstanceVO> running = new ArrayList<>();
        List<ProcessInstanceVO> finished = new ArrayList<>();
        for (Map.Entry<String, String> entry : versionOf.entrySet()) {
            String instanceId = entry.getKey();
            ProcessInstanceVO vo = findInstanceByTenant(instanceId, tenant);
            if (vo == null) {
                // 关联行还在、实例历史已被清理：跳过，不编造状态
                continue;
            }
            vo.setEntityVersion(entry.getValue());
            if ("running".equals(vo.getStatus()) || "suspended".equals(vo.getStatus())) {
                fillCurrentTasks(vo, instanceId);
                running.add(vo);
            } else {
                finished.add(vo);
            }
        }
        // 执行中的按开始时间倒序（最近发起的在最上面）；已执行的按结束时间倒序
        running.sort(Comparator.comparing(ProcessInstanceVO::getStartTime,
                Comparator.nullsLast(Comparator.<Date>reverseOrder())));
        finished.sort(Comparator.comparing(ProcessInstanceVO::getEndTime,
                Comparator.nullsLast(Comparator.<Date>reverseOrder())));
        List<ProcessInstanceVO> result = new ArrayList<>(running.size() + finished.size());
        result.addAll(running);
        result.addAll(finished);
        return result;
    }

    /** 按 id + 租户取实例视图（先运行时、再历史）；都没有（历史已清理）返回 {@code null} */
    private ProcessInstanceVO findInstanceByTenant(String instanceId, String tenant) {
        try {
            ProcessInstance pi = runtimeService.createProcessInstanceQuery()
                    .processInstanceId(instanceId)
                    .processInstanceTenantId(tenant)
                    .singleResult();
            if (pi != null) {
                return toInstanceVO(pi);
            }
            HistoricProcessInstance hpi = historyService.createHistoricProcessInstanceQuery()
                    .processInstanceId(instanceId)
                    .processInstanceTenantId(tenant)
                    .singleResult();
            return hpi == null ? null : toInstanceVO(hpi);
        } catch (Exception e) {
            log.warn("查询流程实例失败，跳过该实例: instance={} error={}", instanceId, e.getMessage());
            return null;
        }
    }

    /**
     * 补"当前节点 + 办理人"。
     *
     * <p>对象详情页的「正在执行中」一栏要一眼看出<b>卡在哪一步、等谁办</b>，这比一个状态标签有用得多。
     * 未指派（候选组待认领）时办理人为空串 —— 前端据此显示"未指派"，而不是把空当成"没查到"。
     */
    private void fillCurrentTasks(ProcessInstanceVO vo, String instanceId) {
        try {
            List<Task> tasks = taskService.createTaskQuery().processInstanceId(instanceId).list();
            if (tasks == null || tasks.isEmpty()) {
                return;
            }
            Set<String> names = new LinkedHashSet<>();
            Set<String> assignees = new LinkedHashSet<>();
            for (Task task : tasks) {
                if (task.getName() != null && !task.getName().trim().isEmpty()) {
                    names.add(task.getName().trim());
                }
                if (task.getAssignee() != null && !task.getAssignee().trim().isEmpty()) {
                    assignees.add(task.getAssignee().trim());
                }
            }
            vo.setCurrentActivityName(String.join("、", names));
            vo.setCurrentAssignees(String.join("、", assignees));
        } catch (Exception e) {
            log.warn("读取实例当前任务失败: instance={} error={}", instanceId, e.getMessage());
        }
    }

    // ==================== 节点经路（走到哪了） ====================

    @Override
    public List<ProcessActivityVO> findInstanceActivities(String instanceId) {
        String tenant = identity.currentTenantId();
        String processDefinitionId;
        String startUserId;
        ProcessInstance running = runtimeService.createProcessInstanceQuery()
                .processInstanceId(instanceId)
                .processInstanceTenantId(tenant)
                .singleResult();
        if (running != null) {
            processDefinitionId = running.getProcessDefinitionId();
            startUserId = running.getStartUserId();
        } else {
            HistoricProcessInstance hpi = historyService.createHistoricProcessInstanceQuery()
                    .processInstanceId(instanceId)
                    .processInstanceTenantId(tenant)
                    .singleResult();
            if (hpi == null) {
                throw new IllegalArgumentException("流程实例不存在或不属于当前租户: " + instanceId);
            }
            processDefinitionId = hpi.getProcessDefinitionId();
            startUserId = hpi.getStartUserId();
        }
        // 用实例自己跑的那一版 BPMN：在途实例可能比模板主档旧，节点名/表单要以它当时部署的那版为准
        org.flowable.bpmn.model.Process process = mainProcessOf(processDefinitionId);
        List<HistoricActivityInstance> historic = new ArrayList<>(
                historyService.createHistoricActivityInstanceQuery()
                        .processInstanceId(instanceId)
                        .orderByHistoricActivityInstanceStartTime().asc()
                        .list());
        // 毫秒时间戳会撞车：开始事件与紧随其后的第一个任务常常是同一个时间戳（如 40.496），
        // 只按开始时间排会给出"技术评审在发起变更之前"这种颠倒的顺序。再按结束时间兜底 ——
        // 撞车的两条里，先结束的那条事实上先走（未结束的排最后，它必然是最后开始的）。
        historic.sort(Comparator
                .comparing(HistoricActivityInstance::getStartTime,
                        Comparator.nullsLast(Comparator.<Date>naturalOrder()))
                .thenComparing(HistoricActivityInstance::getEndTime,
                        Comparator.nullsLast(Comparator.<Date>naturalOrder())));
        Map<String, List<String>> commentsByTask = commentsByTask(instanceId);
        // 逐人结论（会签 / 并行多实例）：同一活动每人一条任务、各有一票，按任务 id 归位
        Map<String, String> decisionByTask = taskDecisions(instanceId);
        // 该节点之后实际走过的那条连线名（如 同意 / 驳回）：连线在历史里也是"活动"，按源节点归位
        Map<String, String> outcomeOf = outcomeOfActivity(historic, process);

        List<ProcessActivityVO> result = new ArrayList<>();
        Set<String> runningIds = new LinkedHashSet<>();
        for (HistoricActivityInstance a : historic) {
            String bpmnType = a.getActivityType();
            if (isRouting(bpmnType, a.getActivityId(), process)) {
                continue;   // 网关与连线是路由，不是"活动节点"
            }
            FlowElement el = flowElementOf(process, a.getActivityId());
            ProcessActivityVO vo = new ProcessActivityVO();
            vo.setActivityId(a.getActivityId());
            vo.setType(activityTypeName(el, bpmnType));
            vo.setName(activityName(a.getActivityName(), el, bpmnType));
            boolean done = a.getEndTime() != null;
            vo.setStatus(done ? "completed" : "running");
            vo.setStartTime(a.getStartTime());
            vo.setEndTime(a.getEndTime());
            vo.setTaskId(a.getTaskId());
            vo.setDecision(decisionByTask.get(a.getTaskId()));
            List<String> raw = splitAssignees(a.getAssignee());
            if (raw.isEmpty() && "startEvent".equals(bpmnType) && startUserId != null) {
                raw = List.of(startUserId);   // 开始事件没有办理人：它的人就是发起人
            }
            vo.setAssignees(raw);
            vo.setAssigneeNames(resolveUserNames(raw));
            if (done) {
                vo.setComment(joinComments(commentsByTask.get(a.getTaskId())));
                vo.setOutcome(outcomeOf.get(a.getActivityId()));
            } else {
                runningIds.add(a.getActivityId());
            }
            result.add(vo);
        }
        result.addAll(pendingActivities(process, result, runningIds));
        // 节点执行日志条数（含错误数）：时间轴据此标出"哪个节点有后台执行痕迹/出过错"。
        // 一次分组查询覆盖全部节点，不按节点逐个查 —— 十几个节点逐个查就是十几次往返
        Map<String, int[]> logCounts = nodeLogService.countsByActivity(instanceId);
        for (ProcessActivityVO vo : result) {
            int[] pair = logCounts.get(vo.getActivityId());
            if (pair != null) {
                vo.setNodeLogCount(pair[0]);
                vo.setNodeErrorCount(pair[1]);
            }
        }
        return result;
    }

    // ==================== 节点表单填写（这一步填了什么） ====================

    /**
     * 平台内部变量：不是"用户填的表单"，不列 ——
     * 结论 / 意见 / 责任人等在节点头部已展示（ckplmDecision / lastAction / lastComment），
     * 其余是发起时写入的上下文（businessObjectSet / initiator / tenantOid / lifecycleStatus / approved）。
     */
    private static final Set<String> INTERNAL_VARIABLES = Set.of(
            "businessObjectSet", "initiator", "tenantOid", "lifecycleStatus",
            "approved", "lastAction", "lastComment", "ckplmDecision");

    @Override
    public List<ProcessActivityFormVO> findActivityForm(String instanceId, String activityId) {
        String tenant = identity.currentTenantId();
        String processDefinitionId;
        ProcessInstance running = runtimeService.createProcessInstanceQuery()
                .processInstanceId(instanceId)
                .processInstanceTenantId(tenant)
                .singleResult();
        if (running != null) {
            processDefinitionId = running.getProcessDefinitionId();
        } else {
            HistoricProcessInstance hpi = historyService.createHistoricProcessInstanceQuery()
                    .processInstanceId(instanceId)
                    .processInstanceTenantId(tenant)
                    .singleResult();
            if (hpi == null) {
                throw new IllegalArgumentException("流程实例不存在或不属于当前租户: " + instanceId);
            }
            processDefinitionId = hpi.getProcessDefinitionId();
        }

        // 该活动可能执行过多次（驳回后重走）：取全部历史任务，按"任务 id 归属 + 执行时间窗"两条线归因。
        // 为什么要有时间窗：像「设置审批人」这类节点，各环节审批人变量是在**流程作用域**一次性写入的
        // （act_hi_varinst.task_id_ 为空），只能靠"落在该节点的执行区间内"判断它是这一步填的。
        List<HistoricTaskInstance> tasks = historyService.createHistoricTaskInstanceQuery()
                .processInstanceId(instanceId)
                .taskDefinitionKey(activityId)
                .list();
        Set<String> taskIds = new LinkedHashSet<>();
        List<Date[]> windows = new ArrayList<>();
        for (HistoricTaskInstance t : tasks) {
            if (t.getId() != null) {
                taskIds.add(t.getId());
            }
            if (t.getStartTime() != null) {
                // 前后各放宽 5 秒：变量与任务的写入/完成常是同一毫秒级时刻，边界要对得上
                Date from = new Date(t.getStartTime().getTime() - 5000L);
                Date to = t.getEndTime() != null
                        ? new Date(t.getEndTime().getTime() + 5000L) : new Date();
                windows.add(new Date[]{from, to});
            }
        }

        // 用实例自己跑的那一版 BPMN 解析中文标签（与节点经路同一份模型，口径一致）
        org.flowable.bpmn.model.Process process = mainProcessOf(processDefinitionId);

        List<ProcessActivityFormVO> result = new ArrayList<>();
        for (HistoricVariableInstance v : historyService.createHistoricVariableInstanceQuery()
                .processInstanceId(instanceId)
                .list()) {
            String name = v.getVariableName();
            if (name == null || INTERNAL_VARIABLES.contains(name)) {
                continue;
            }
            if (!belongsToActivity(v, taskIds, windows)) {
                continue;
            }
            ProcessActivityFormVO vo = new ProcessActivityFormVO();
            vo.setKey(name);
            vo.setLabel(formLabelOf(name, process));
            vo.setValue(v.getValue() == null ? null : String.valueOf(v.getValue()));
            result.add(vo);
        }
        result.sort(Comparator.comparing(ProcessActivityFormVO::getKey));
        return result;
    }

    /** 变量是否属于该节点：任务 id 命中，或其写入 / 更新时间落在该节点的执行时间窗内 */
    private static boolean belongsToActivity(HistoricVariableInstance v, Set<String> taskIds,
                                             List<Date[]> windows) {
        if (v.getTaskId() != null && taskIds.contains(v.getTaskId())) {
            return true;
        }
        Date t = v.getCreateTime() != null ? v.getCreateTime() : v.getLastUpdatedTime();
        if (t == null) {
            return false;
        }
        for (Date[] w : windows) {
            if (!t.before(w[0]) && !t.after(w[1])) {
                return true;
            }
        }
        return false;
    }

    /**
     * 变量展示名：{@code ckplmSetupAssignees_<节点id>}（「设置审批人」节点写入的各环节审批人）
     * → 「审批人 · 样品来料检验（IQC）」（节点名取 BPMN 里的 {@code userTask/@name}）；
     * 其余一律原样返回键名 —— 能确定的才翻译，宁可不好看，也不猜语义。
     */
    private static String formLabelOf(String name, org.flowable.bpmn.model.Process process) {
        String prefix = "ckplmSetupAssignees_";
        if (!name.startsWith(prefix)) {
            return name;
        }
        String nodeId = name.substring(prefix.length());
        FlowElement el = flowElementOf(process, nodeId);
        String nodeName = el == null ? null : el.getName();
        return nodeName == null || nodeName.trim().isEmpty() ? name : "审批人 · " + nodeName;
    }

    /** 该实例所用流程定义的<b>主流程</b>（拿不到模型时返回 null，历史部分照样能展示） */
    private org.flowable.bpmn.model.Process mainProcessOf(String processDefinitionId) {
        if (processDefinitionId == null) {
            return null;
        }
        try {
            BpmnModel model = repositoryService.getBpmnModel(processDefinitionId);
            return model == null ? null : model.getMainProcess();
        } catch (Exception e) {
            log.warn("读取流程定义 BPMN 失败: pdId={} error={}", processDefinitionId, e.getMessage());
            return null;
        }
    }

    private static FlowElement flowElementOf(org.flowable.bpmn.model.Process process, String activityId) {
        if (process == null || activityId == null) {
            return null;
        }
        try {
            return process.getFlowElement(activityId, true);
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * 意见按<b>任务</b>归组（不是按活动）。
     *
     * <p>会签/并行多实例下同一个活动会有多条任务、每人一条意见；按活动归组会把它们混成一句，
     * 按任务归组才能"谁的意见贴谁的节点"。
     */
    private Map<String, List<String>> commentsByTask(String instanceId) {
        Map<String, List<String>> map = new LinkedHashMap<>();
        try {
            List<Comment> comments = taskService.getProcessInstanceComments(instanceId);
            if (comments == null || comments.isEmpty()) {
                return map;
            }
            comments.sort(Comparator.comparing(Comment::getTime,
                    Comparator.nullsLast(Comparator.<Date>naturalOrder())));
            for (Comment c : comments) {
                String message = c.getFullMessage() == null ? null : c.getFullMessage().trim();
                if (c.getTaskId() == null || message == null || message.isEmpty()) {
                    continue;
                }
                map.computeIfAbsent(c.getTaskId(), k -> new ArrayList<>()).add(message);
            }
        } catch (Exception e) {
            log.warn("读取流程意见失败: instance={} error={}", instanceId, e.getMessage());
        }
        return map;
    }

    /** 每个活动之后实际走出的"结论"（如 同意 / 驳回）；没有可读结论的活动不出现在结果里 */
    private Map<String, String> outcomeOfActivity(List<HistoricActivityInstance> historic,
                                                 org.flowable.bpmn.model.Process process) {
        Map<String, String> map = new LinkedHashMap<>();
        if (process == null) {
            return map;
        }
        // 实际走过的连线：源节点 → 连线 id（按历史顺序）
        Map<String, List<String>> takenFlowsOf = new LinkedHashMap<>();
        for (HistoricActivityInstance a : historic) {
            if (!"sequenceFlow".equals(a.getActivityType())) {
                continue;
            }
            FlowElement el = flowElementOf(process, a.getActivityId());
            if (el instanceof SequenceFlow sf && sf.getSourceRef() != null) {
                takenFlowsOf.computeIfAbsent(sf.getSourceRef(), k -> new ArrayList<>()).add(a.getActivityId());
            }
        }
        for (String sourceId : takenFlowsOf.keySet()) {
            String label = resolveOutcome(sourceId, takenFlowsOf, process, 0);
            if (label != null) {
                map.put(sourceId, label);
            }
        }
        return map;
    }

    /**
     * 该活动之后实际走出的结论。
     *
     * <p>审批活动的下一跳往往只是一条<b>无名连线</b>（真正的分支名与条件在网关出去的那条线上），
     * 所以本方法遇到"无名也无条件"的连线时会顺着网关继续往下找 —— 上限 5 层，防环。
     */
    private String resolveOutcome(String sourceId, Map<String, List<String>> takenFlowsOf,
                                  org.flowable.bpmn.model.Process process, int depth) {
        if (depth > 5) {
            return null;
        }
        for (String flowId : takenFlowsOf.getOrDefault(sourceId, List.of())) {
            FlowElement el = flowElementOf(process, flowId);
            if (!(el instanceof SequenceFlow sf)) {
                continue;
            }
            String name = sf.getName() == null ? null : sf.getName().trim();
            String label = (name != null && !name.isEmpty())
                    ? name : outcomeOfCondition(sf.getConditionExpression());
            if (label != null) {
                return label;
            }
            FlowElement target = flowElementOf(process, sf.getTargetRef());
            if (target instanceof Gateway) {
                String next = resolveOutcome(sf.getTargetRef(), takenFlowsOf, process, depth + 1);
                if (next != null) {
                    return next;
                }
            }
        }
        return null;
    }

    /**
     * 连线<b>没命名</b>时，从条件表达式反推一个可读结论。
     *
     * <p>平台把审批结论编译成排他网关上的 {@code ${approved}} / {@code ${!approved}}
     * （见 {@link #completeTask}），所以这里能可靠认出「同意 / 驳回」—— 比让用户看
     * {@code ${!approved}} 强得多。认不出的表达式返回 {@code null}：宁可不显示，也不猜。
     */
    private static String outcomeOfCondition(String condition) {
        if (condition == null || condition.trim().isEmpty()) {
            return null;
        }
        String expr = condition.replace(" ", "");
        if (expr.contains("!approved") || expr.contains("approved==false")) {
            return "驳回";
        }
        return expr.contains("approved") ? "同意" : null;
    }

    private static boolean isRouting(String bpmnType, String activityId,
                                     org.flowable.bpmn.model.Process process) {
        if ("sequenceFlow".equals(bpmnType)) {
            return true;
        }
        if (bpmnType != null && bpmnType.toLowerCase().contains("gateway")) {
            return true;
        }
        return flowElementOf(process, activityId) instanceof Gateway;
    }

    /** 活动类型展示名：先认 BPMN 元素本体，再退回 BPMN 类型串 */
    private static String activityTypeName(FlowElement el, String bpmnType) {
        if (el instanceof UserTask ut) {
            String formKey = ut.getFormKey();
            return (formKey != null && formKey.contains(SETUP_ASSIGNEE_FORM)) ? "设置流程参与者" : "审批活动";
        }
        if (el instanceof ServiceTask) return "自动活动";
        if (el instanceof CallActivity) return "调用子流程";
        if (el instanceof SubProcess) return "子流程";
        if (el instanceof ReceiveTask) return "等待接收";
        if (el instanceof ManualTask) return "人工任务";
        if ("startEvent".equals(bpmnType)) return "开始";
        if ("endEvent".equals(bpmnType)) return "结束";
        return "活动";
    }

    private static String activityName(String historicName, FlowElement el, String bpmnType) {
        if (el instanceof FlowNode fn && fn.getName() != null && !fn.getName().trim().isEmpty()) {
            return fn.getName().trim();
        }
        if (historicName != null && !historicName.trim().isEmpty()) {
            return historicName.trim();
        }
        if ("startEvent".equals(bpmnType)) return "开始";
        if ("endEvent".equals(bpmnType)) return "结束";
        return el == null ? "活动" : el.getId();
    }

    private static List<String> splitAssignees(String assignee) {
        if (assignee == null || assignee.trim().isEmpty()) {
            return List.of();
        }
        List<String> list = new ArrayList<>();
        for (String part : assignee.split(",")) {
            String value = part.trim();
            if (!value.isEmpty() && !list.contains(value)) {
                list.add(value);
            }
        }
        return list;
    }

    private static String joinComments(List<String> messages) {
        return (messages == null || messages.isEmpty()) ? null : String.join("；", messages);
    }

    private List<String> resolveUserNames(List<String> values) {
        if (values == null || values.isEmpty()) {
            return List.of();
        }
        List<String> names = new ArrayList<>(values.size());
        Map<String, String> cache = new HashMap<>();
        for (String value : values) {
            names.add(cache.computeIfAbsent(value, this::displayNameOf));
        }
        return names;
    }

    /**
     * assignee → 显示名。
     *
     * <p>Flowable 的 assignee 是<b>原样字符串</b>：可能是用户名（{@code ${initiator}} 的求值结果），
     * 也可能是人员 <b>oid</b>（「设置审批人」按 oid 指派下游）—— 两种都试一次。查不到就原样返回：
     * 宁可在界面上看到一个 id，也不要看到空白（那会让人以为"没办"）。
     */
    private String displayNameOf(String value) {
        try {
            User user = userService.findByOid(value);
            if (user == null) {
                user = userService.findByUsername(value);
            }
            if (user != null && user.getDisplayName() != null && !user.getDisplayName().trim().isEmpty()) {
                return user.getDisplayName().trim();
            }
        } catch (Exception e) {
            log.debug("解析流程办理人显示名失败: value={} error={}", value, e.getMessage());
        }
        return value;
    }

    /**
     * 还没走到的活动：从当前活动沿连线往下可达的人工/自动活动。
     *
     * <p>刻意<b>不评估</b>网关条件 —— 条件依赖运行期变量，这里求值既复杂、又会给出"一定走这条"
     * 的错误确定感；只做可达性，界面上标为「未开始」。已结束的实例没有 pending（无当前活动）。
     */
    private List<ProcessActivityVO> pendingActivities(org.flowable.bpmn.model.Process process,
                                                      List<ProcessActivityVO> done,
                                                      Set<String> runningIds) {
        List<ProcessActivityVO> list = new ArrayList<>();
        if (process == null || runningIds.isEmpty()) {
            return list;
        }
        Set<String> seen = new LinkedHashSet<>();
        for (ProcessActivityVO vo : done) {
            if (vo.getActivityId() != null) {
                seen.add(vo.getActivityId());
            }
        }
        Deque<FlowElement> queue = new ArrayDeque<>();
        for (String id : runningIds) {
            FlowElement el = flowElementOf(process, id);
            if (el instanceof FlowNode fn && fn.getOutgoingFlows() != null) {
                queue.addAll(fn.getOutgoingFlows());
            }
        }
        while (!queue.isEmpty()) {
            FlowElement el = queue.poll();
            if (el == null || el.getId() == null || !seen.add(el.getId())) {
                continue;
            }
            if (el instanceof SequenceFlow sf) {
                FlowElement target = flowElementOf(process, sf.getTargetRef());
                if (target != null) {
                    queue.add(target);
                }
                continue;
            }
            if (el instanceof FlowNode fn && fn.getOutgoingFlows() != null) {
                queue.addAll(fn.getOutgoingFlows());
            }
            if (isPendingActivity(el)) {
                list.add(pendingVo(el));
            }
        }
        return list;
    }

    /** 未到达的节点只列"人工/自动活动"：事件是流程边界、网关是路由，都不是待办 */
    private static boolean isPendingActivity(FlowElement el) {
        return el instanceof UserTask || el instanceof ServiceTask || el instanceof CallActivity
                || el instanceof SubProcess || el instanceof ReceiveTask || el instanceof ManualTask;
    }

    private ProcessActivityVO pendingVo(FlowElement el) {
        ProcessActivityVO vo = new ProcessActivityVO();
        vo.setActivityId(el.getId());
        vo.setName(activityName(null, el, null));
        vo.setType(activityTypeName(el, null));
        vo.setStatus("pending");
        // 写死的办理人可以直接显示；表达式（${...}）要运行期才知道，留给"未指派"
        List<String> raw = (el instanceof UserTask ut && ut.getAssignee() != null
                && !ut.getAssignee().contains("${")) ? splitAssignees(ut.getAssignee()) : List.of();
        vo.setAssignees(raw);
        vo.setAssigneeNames(resolveUserNames(raw));
        return vo;
    }

    @Override
    @Transactional
    public void suspendInstance(String instanceId) {
        requireRunningInstance(instanceId);
        runtimeService.suspendProcessInstanceById(instanceId);
        log.info("流程实例挂起: instanceId={}", instanceId);
    }

    @Override
    @Transactional
    public void activateInstance(String instanceId) {
        requireRunningInstance(instanceId);
        runtimeService.activateProcessInstanceById(instanceId);
        log.info("流程实例激活: instanceId={}", instanceId);
    }

    @Override
    @Transactional
    public void terminateInstance(String instanceId, String reason) {
        requireRunningInstance(instanceId);
        String why = (reason == null || reason.trim().isEmpty())
                ? "用户终止（" + identity.currentUserId() + "）" : reason.trim();
        runtimeService.deleteProcessInstance(instanceId, why);
        // 终止也是一次"结束"：业务侧必须收到，否则单据会永远停在"审批中"，
        // 而且没有任何人还能再推进它（实例已经没了）。
        notifyIfFinished(instanceId, identity.currentUserId());
        log.info("流程实例终止: instanceId={}, reason={}", instanceId, why);
    }

    @Override
    @Transactional
    public void deleteInstance(String instanceId) {
        String tenant = identity.currentTenantId();
        // 运行中：先终止
        ProcessInstance running = runtimeService.createProcessInstanceQuery()
                .processInstanceId(instanceId).processInstanceTenantId(tenant).singleResult();
        if (running != null) {
            runtimeService.deleteProcessInstance(instanceId,
                    "用户删除（" + identity.currentUserId() + "）");
        }
        // 清理历史（含已结束实例）
        try {
            historyService.deleteHistoricProcessInstance(instanceId);
        } catch (Exception e) {
            log.warn("删除流程实例历史失败 instanceId={}: {}", instanceId, e.getMessage());
        }
        // 关联行随实例一起清掉。留在库里就是"悬挂关联"：对象详情页的「关联流程」会读到它，
        // 闸门判定也要多绕一圈（引擎里已查不到该实例，等于白查）。见 ProcessEntitySetService 契约。
        try {
            entitySetService.removeByProcessInstanceId(instanceId);
        } catch (Exception e) {
            log.warn("清理流程实例的业务实体关联失败 instanceId={}: {}", instanceId, e.getMessage());
        }
        // 节点执行日志同理：实例没了，这些行就查不到出处了
        nodeLogService.deleteByInstance(instanceId);
        log.info("流程实例删除: instanceId={}", instanceId);
    }

    // ==================== 业务发起 ====================

    @Override
    @Transactional
    public String startProcess(String processKey, String businessKey, Map<String, Object> variables) {
        if (processKey == null || processKey.trim().isEmpty()) {
            throw new IllegalArgumentException("processKey 不能为空");
        }
        String key = processKey.trim();
        String tenant = identity.currentTenantId();
        String user = identity.currentUserId();

        // 新注册租户可能尚未部署内置流程 —— 惰性补齐
        deploymentSupport.ensureBuiltInDeployed(tenant);

        Map<String, Object> vars = variables != null ? new HashMap<>(variables) : new HashMap<>();
        vars.putIfAbsent(VAR_INITIATOR, user);
        vars.put(VAR_TENANT, tenant);

        String instanceId;
        try {
            instanceId = withAuthenticatedUser(user, () -> runtimeService
                    .startProcessInstanceByKeyAndTenantId(key, businessKey, vars, tenant)
                    .getId());
        } catch (Exception e) {
            throw new IllegalArgumentException("发起流程失败（key=" + key + "）: " + e.getMessage(), e);
        }
        log.info("流程发起: key={}, businessKey={}, instanceId={}, user={}, tenant={}",
                key, businessKey, instanceId, user, tenant);
        notifyTodoTasks(instanceId, user);
        return instanceId;
    }

    // ==================== 私有工具 ====================

    /** 分页起始行 */
    private int first(int page, int size) {
        int p = Math.max(1, page);
        int s = Math.max(1, size);
        return (p - 1) * s;
    }

    /** 按 id + 租户取任务，不存在或跨租户则拒绝 */
    private Task requireTask(String taskId, String tenant) {
        Task task = taskService.createTaskQuery()
                .taskId(taskId).taskTenantId(tenant).singleResult();
        if (task == null) {
            throw new IllegalArgumentException("任务不存在或不属于当前租户: " + taskId);
        }
        return task;
    }

    // ==================== 站内通知（流程事件） ====================

    /**
     * 通知"新冒出来的待办"。
     *
     * <p>发起 / 办理之后，实例上会出现新的当前任务，这些人才是"该动了"的人。两条刻意的取舍：
     * <ul>
     *   <li><b>排除刚动手的人</b>：自己办完自己的活儿，不该再收到一条"你有待办"；</li>
     *   <li><b>待认领（无 assignee）不发</b>：还不知道该通知谁，发出去只会打扰候选组里的所有人。</li>
     * </ul>
     * 通知失败绝不影响主链路（流程该推进还得推进），所以整体兜一层 try。
     */
    private void notifyTodoTasks(String instanceId, String actorUsername) {
        if (instanceId == null) return;
        try {
            List<Task> tasks = taskService.createTaskQuery().processInstanceId(instanceId).list();
            if (tasks == null || tasks.isEmpty()) return;
            Map<String, ProcessDefinition> cache = new HashMap<>();
            // 刚动手的人不该收到"你有待办"：自己可能以用户名出现，也可能是别人指派时写的 oid
            List<String> actorIds = identity.currentUserIdentifiers();
            for (Task task : tasks) {
                String assignee = task.getAssignee();
                if (assignee == null || assignee.trim().isEmpty() || actorIds.contains(assignee.trim())) {
                    continue;
                }
                // assignee 可能是用户名，也可能是人员 oid（「设置审批人」按 oid 指派）—— 两种都认
                User user = identity.findUserByFlowableId(assignee);
                if (user == null) continue;
                ProcessDefinition pd = findProcessDefinition(task.getProcessDefinitionId(), cache);
                String flow = pd == null || pd.getName() == null ? "流程" : pd.getName();
                String taskName = task.getName() == null ? "任务" : task.getName();
                notificationService.send(user.getOid(), "待办任务", flow + " · " + taskName,
                        "TASK", "TASK", task.getId());
            }
        } catch (Exception e) {
            log.warn("发送待办通知失败: instance={} error={}", instanceId, e.getMessage());
        }
    }

    /**
     * 流程结束（完成 / 终止）后：先分发业务回调，再通知发起人。
     *
     * <p>两件事的<b>提前返回条件不一样</b>，所以不能合成一段：
     * 通知在"自己发起又自己办完"时跳过，但业务状态（如 ECN 的 APPROVING → APPROVED）
     * 无论谁办的都必须回写。回调放在最前面、判空之后，正是为此。
     */
    private void notifyIfFinished(String instanceId, String actorUsername) {
        if (instanceId == null) return;
        try {
            if (runtimeService.createProcessInstanceQuery().processInstanceId(instanceId).count() > 0) {
                return;
            }
            HistoricProcessInstance hpi = historyService.createHistoricProcessInstanceQuery()
                    .processInstanceId(instanceId).singleResult();
            if (hpi == null) {
                return;
            }

            dispatchOutcome(instanceId, hpi, actorUsername);

            if (hpi.getStartUserId() == null || hpi.getStartUserId().equals(actorUsername)) {
                return;
            }
            User user = userService.findByUsername(hpi.getStartUserId());
            if (user == null) return;
            String flow = hpi.getProcessDefinitionName() == null ? "流程" : hpi.getProcessDefinitionName();
            // deleteReason 有值 = 被终止/删除；没有 = 正常走完
            notificationService.send(user.getOid(),
                    hpi.getDeleteReason() != null ? "流程已终止" : "流程已完成",
                    flow, "PROCESS_RESULT", "PROCESS_INSTANCE", instanceId);
        } catch (Exception e) {
            log.warn("发送流程结果通知失败: instance={} error={}", instanceId, e.getMessage());
        }
    }

    /**
     * 把「实例已结束」分发给业务 handler（ECN 审批结果回写等）。
     *
     * <p>变量取<b>历史变量</b>：实例一结束，运行时变量就没有了（{@code act_ru_variable} 无此行），
     * 只有 {@code act_hi_varinst} 还留着最后一份快照 —— {@code approved} 这类结论正在其中。
     *
     * <p>每个 handler 单独 try/catch：一个业务模块的 bug 不该连累其它模块，
     * 更不该让"办理成功"变成"办理报错"。
     */
    private void dispatchOutcome(String instanceId, HistoricProcessInstance hpi, String actorUsername) {
        String processKey = hpi.getProcessDefinitionKey();
        if (processKey == null) {
            return;
        }
        try {
            Map<String, Object> variables = new HashMap<>();
            historyService.createHistoricVariableInstanceQuery()
                    .processInstanceId(instanceId).list()
                    .forEach(v -> {
                        if (v.getVariableName() != null) {
                            variables.put(v.getVariableName(), v.getValue());
                        }
                    });
            ProcessOutcome outcome = new ProcessOutcome(instanceId, processKey, hpi.getBusinessKey(),
                    hpi.getStartUserId(), hpi.getDeleteReason() == null, variables, actorUsername);
            outcomeHandlers.orderedStream().forEach(handler -> {
                try {
                    if (handler.supports(processKey)) {
                        handler.onFinished(outcome);
                        log.info("流程结束回调完成: {} handler={}", outcome, handler.getClass().getSimpleName());
                    }
                } catch (Exception e) {
                    log.warn("流程结束回调失败: instance={} handler={} error={}",
                            instanceId, handler.getClass().getSimpleName(), e.getMessage());
                }
            });
        } catch (Exception e) {
            log.warn("读取流程结束变量失败: instance={} error={}", instanceId, e.getMessage());
        }
    }

    /** 通知被委派 / 转办的人："有件事到你手上了" */
    private void notifyAssignee(String targetUsername, String title, String type, Task task, String actor) {
        try {
            User user = userService.findByUsername(targetUsername);
            if (user == null) return;
            String taskName = task.getName() == null ? "任务" : task.getName();
            String content = (actor == null ? "" : actor + " 转给你：") + taskName;
            notificationService.send(user.getOid(), title, content, type, "TASK", task.getId());
        } catch (Exception e) {
            log.warn("发送任务转交通知失败: task={} target={} error={}",
                    task == null ? null : task.getId(), targetUsername, e.getMessage());
        }
    }

    /**
     * 读场景的任务归属校验：运行中任务或已办（历史）任务皆可。
     *
     * <p>任务一旦办理完成即从 {@code act_ru_task} 删除、仅保留在 {@code act_hi_taskinst}，
     * 若只查运行时表会导致「已办任务的评论」返回 404。
     */
    private void requireTaskOrHistoric(String taskId, String tenant) {
        long running = taskService.createTaskQuery().taskId(taskId).taskTenantId(tenant).count();
        if (running > 0) {
            return;
        }
        long historic = historyService.createHistoricTaskInstanceQuery()
                .taskId(taskId).taskTenantId(tenant).count();
        if (historic > 0) {
            return;
        }
        throw new IllegalArgumentException("任务不存在或不属于当前租户: " + taskId);
    }

    /** 按 id + 租户取运行中实例，不存在或跨租户则拒绝 */
    private ProcessInstance requireRunningInstance(String instanceId) {
        ProcessInstance pi = runtimeService.createProcessInstanceQuery()
                .processInstanceId(instanceId)
                .processInstanceTenantId(identity.currentTenantId())
                .singleResult();
        if (pi == null) {
            throw new IllegalArgumentException("运行中的流程实例不存在或不属于当前租户: " + instanceId);
        }
        return pi;
    }

    private ProcessDefinition findProcessDefinition(String pdId, Map<String, ProcessDefinition> cache) {
        if (pdId == null) {
            return null;
        }
        ProcessDefinition cached = cache.get(pdId);
        if (cached != null) {
            return cached;
        }
        try {
            ProcessDefinition pd = repositoryService.createProcessDefinitionQuery()
                    .processDefinitionId(pdId).singleResult();
            if (pd != null) {
                cache.put(pdId, pd);
            }
            return pd;
        } catch (Exception e) {
            return null;
        }
    }

    private ProcessTaskVO toTaskVO(Task task, Map<String, ProcessDefinition> cache, Date now) {
        ProcessTaskVO vo = new ProcessTaskVO();
        vo.setId(task.getId());
        vo.setName(task.getName());
        vo.setAssignee(task.getAssignee());
        // 界面显示用的人名：assignee 可能是用户名也可能是 oid（见 ProcessIdentitySupport）
        vo.setAssigneeName(displayNameOf(task.getAssignee()));
        // 列表要显示"办的是哪一条对象"：给实体引用（编码/名称/状态由前端回查）
        vo.setEntities(entityRefsOf(task.getProcessInstanceId()));
        // 没有实体关联行的单据（变更请求 / 通告 / 任务）靠 businessKey 辨认，与上面的实体引用并行
        vo.setBusinessKey(businessKeyOf(task.getProcessInstanceId()));
        vo.setProcessInstanceId(task.getProcessInstanceId());
        vo.setCreateTime(task.getCreateTime());
        vo.setDueDate(task.getDueDate());
        vo.setOverdue(isOverdue(task.getDueDate(), now));
        vo.setTenantId(task.getTenantId());
        // 节点表单与活动 id：办理弹框据此渲染"该节点的表单"，而不是写死的通用表单
        vo.setFormKey(task.getFormKey());
        vo.setTaskDefinitionKey(task.getTaskDefinitionKey());
        ProcessDefinition pd = findProcessDefinition(task.getProcessDefinitionId(), cache);
        if (pd != null) {
            vo.setProcessDefinitionName(pd.getName());
            vo.setProcessDefinitionKey(pd.getKey());
        }
        return vo;
    }

    private ProcessInstanceVO toInstanceVO(ProcessInstance pi) {
        ProcessInstanceVO vo = new ProcessInstanceVO();
        vo.setId(pi.getId());
        vo.setProcessDefinitionId(pi.getProcessDefinitionId());
        vo.setProcessDefinitionKey(pi.getProcessDefinitionKey());
        vo.setProcessDefinitionName(pi.getProcessDefinitionName());
        vo.setBusinessKey(pi.getBusinessKey());
        vo.setStartUserId(pi.getStartUserId());
        // 发起人同样给显示名：办理页「流程信息」里显示出账号（Kiween.Hu）而「负责人」是姓名，
        // 是同一页里两套口径（见 ProcessInstanceVO#startUserName）
        vo.setStartUserName(displayNameOf(pi.getStartUserId()));
        vo.setStartTime(pi.getStartTime());
        vo.setTenantId(pi.getTenantId());
        vo.setStatus(pi.isSuspended() ? "suspended" : "running");
        vo.setEntities(entityRefsOf(vo.getId()));
        return vo;
    }

    private ProcessInstanceVO toInstanceVO(HistoricProcessInstance hpi) {
        ProcessInstanceVO vo = new ProcessInstanceVO();
        vo.setId(hpi.getId());
        vo.setProcessDefinitionId(hpi.getProcessDefinitionId());
        vo.setProcessDefinitionKey(hpi.getProcessDefinitionKey());
        vo.setProcessDefinitionName(hpi.getProcessDefinitionName());
        vo.setBusinessKey(hpi.getBusinessKey());
        vo.setStartUserId(hpi.getStartUserId());
        vo.setStartUserName(displayNameOf(hpi.getStartUserId()));
        vo.setStartTime(hpi.getStartTime());
        vo.setEndTime(hpi.getEndTime());
        vo.setDeleteReason(hpi.getDeleteReason());
        vo.setTenantId(hpi.getTenantId());
        if (hpi.getEndTime() != null) {
            // 有结束时间：有删除原因 = 终止，否则 = 正常完成
            vo.setStatus(hpi.getDeleteReason() != null ? "terminated" : "completed");
        } else {
            vo.setStatus(isInstanceSuspended(hpi.getId()) ? "suspended" : "running");
        }
        vo.setEntities(entityRefsOf(vo.getId()));
        return vo;
    }

    /** 历史实例是否处于挂起（历史表无该标记，回查运行时） */
    private boolean isInstanceSuspended(String instanceId) {
        try {
            ProcessInstance pi = runtimeService.createProcessInstanceQuery()
                    .processInstanceId(instanceId).singleResult();
            return pi != null && pi.isSuspended();
        } catch (Exception e) {
            return false;
        }
    }

    /** 读取已结束实例的流程变量 */
    private Map<String, Object> historicVariables(String instanceId) {
        Map<String, Object> vars = new LinkedHashMap<>();
        try {
            List<HistoricVariableInstance> list = historyService.createHistoricVariableInstanceQuery()
                    .processInstanceId(instanceId).list();
            if (list != null) {
                for (HistoricVariableInstance v : list) {
                    vars.put(v.getVariableName(), v.getValue());
                }
            }
        } catch (Exception e) {
            log.warn("读取历史流程变量失败 instanceId={}: {}", instanceId, e.getMessage());
        }
        return vars;
    }

    /**
     * 逐人办理结论：任务 id → {@code APPROVE} / {@code REJECT}。
     *
     * <p>为什么按任务查、而不是复用 {@link #historicVariables}（按实例、按变量名归并）：
     * 会签同一活动有多条任务、每人一票，按名字归并会把多票压成一票（后写的覆盖先写的）——
     * 那正是"看不出谁同意了"的根因。这里只取带 TASK_ID_ 的行，逐票读出来。
     *
     * <p>读失败一律返回空表：结论展示属于锦上添花，不能因为它让进度接口报错。
     */
    private Map<String, String> taskDecisions(String instanceId) {
        Map<String, String> result = new HashMap<>();
        try {
            List<HistoricVariableInstance> list = historyService.createHistoricVariableInstanceQuery()
                    .processInstanceId(instanceId)
                    .variableName(VAR_DECISION)
                    .list();
            if (list != null) {
                for (HistoricVariableInstance v : list) {
                    if (v.getTaskId() != null && v.getValue() != null) {
                        result.put(v.getTaskId(), String.valueOf(v.getValue()));
                    }
                }
            }
        } catch (Exception e) {
            log.warn("读取逐人办理结论失败 instanceId={}: {}", instanceId, e.getMessage());
        }
        return result;
    }

    private boolean isOverdue(Date dueDate, Date now) {
        return dueDate != null && dueDate.before(now);
    }

    /**
     * 在「已认证用户」上下文中执行 Flowable 写操作。
     *
     * <p>Flowable 通过 {@code Authentication} 这个 ThreadLocal 记录操作人
     * （写入 {@code act_hi_comment.user_id_}、{@code act_hi_identitylink}）。
     * 必须在 finally 中清理，否则线程复用时会造成身份串号。
     */
    private void withAuthenticatedUser(String user, Runnable action) {
        Authentication.setAuthenticatedUserId(user);
        try {
            action.run();
        } finally {
            Authentication.setAuthenticatedUserId(null);
        }
    }

    /** 同上，带返回值版本 */
    private <T> T withAuthenticatedUser(String user, java.util.function.Supplier<T> action) {
        Authentication.setAuthenticatedUserId(user);
        try {
            return action.get();
        } finally {
            Authentication.setAuthenticatedUserId(null);
        }
    }
}
