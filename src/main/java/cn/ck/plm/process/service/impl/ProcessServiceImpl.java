/*
 * Copyright (c) 2025 深圳乘恺科技有限公司
 * All rights reserved.
 *
 * @author Kiween.Hu; Roney.Liu
 */

package cn.ck.plm.process.service.impl;

import cn.ck.plm.iam.entity.User;
import cn.ck.plm.iam.service.api.NotificationService;
import cn.ck.plm.iam.service.api.UserService;
import cn.ck.plm.process.dto.ProcessActivityVO;
import cn.ck.plm.process.dto.ProcessCommentVO;
import cn.ck.plm.process.dto.ProcessTaskContextVO;
import cn.ck.plm.process.dto.ProcessInstanceVO;
import cn.ck.plm.process.dto.ProcessTaskFormVO;
import cn.ck.plm.process.dto.ProcessTaskStatsVO;
import cn.ck.plm.process.dto.ProcessTaskVO;
import cn.ck.plm.process.entity.ProcessEntitySet;
import cn.ck.plm.process.entity.ProcessTemplate;
import cn.ck.plm.process.entity.ProcessTemplateVersion;
import cn.ck.plm.process.mapper.ProcessTemplateMapper;
import cn.ck.plm.process.mapper.ProcessTemplateVersionMapper;
import cn.ck.plm.process.reject.RejectRouter;
import cn.ck.plm.process.service.api.ProcessNodeLogService;
import cn.ck.plm.process.service.api.ProcessEntitySetService;
import cn.ck.plm.process.service.api.ProcessService;
import cn.ck.plm.process.support.ProcessDeploymentSupport;
import cn.ck.plm.process.support.ProcessIdentitySupport;
import org.flowable.bpmn.model.BpmnModel;
import org.flowable.bpmn.model.CallActivity;
import org.flowable.bpmn.model.FlowElement;
import org.flowable.bpmn.model.FlowNode;
import org.flowable.bpmn.model.Gateway;
import org.flowable.bpmn.model.ManualTask;
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
import org.flowable.engine.runtime.ProcessInstance;
import org.flowable.engine.task.Comment;
import org.flowable.task.api.Task;
import org.flowable.task.api.TaskQuery;
import org.flowable.task.api.history.HistoricTaskInstance;
import org.flowable.task.api.history.HistoricTaskInstanceQuery;
import org.flowable.variable.api.history.HistoricVariableInstance;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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
    /** 站内通知：任务到达 / 委派 / 转办 / 流程结束 都要落到收件人的铃铛上 */
    private final NotificationService notificationService;
    /** 节点执行日志：给节点经路带"有几条日志、几条错误"，供详情页点开查看 */
    private final ProcessNodeLogService nodeLogService;
    /** 驳回路由：按节点的 ckplm:reject 把流程退回到目标节点（运行期跳转） */
    private final RejectRouter rejectRouter;

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
                               NotificationService notificationService,
                               ProcessNodeLogService nodeLogService,
                               RejectRouter rejectRouter) {
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
        this.notificationService = notificationService;
        this.nodeLogService = nodeLogService;
        this.rejectRouter = rejectRouter;
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
            notifyTodoTasks(task.getProcessInstanceId(), user);
            notifyIfFinished(task.getProcessInstanceId(), user);
            return;
        }

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
            variables.put("approved", approved);
            variables.put(VAR_LAST_ACTION, action);
            variables.put(VAR_LAST_COMMENT, comment);
            taskService.complete(taskId, variables);
        });
        log.info("任务办理: taskId={}, action={}, approved={}, user={}, 表单变量数={}",
                taskId, action, approved, user, formVariables == null ? 0 : formVariables.size());
        // 办理会推进流程：下游任务可能要落到别人头上，或者整条流程就此结束
        notifyTodoTasks(task.getProcessInstanceId(), user);
        notifyIfFinished(task.getProcessInstanceId(), user);
    }

    @Override
    public ProcessTaskFormVO taskForm(String taskId) {
        Task task = requireTask(taskId, identity.currentTenantId());
        ProcessTaskFormVO vo = new ProcessTaskFormVO();
        vo.setFormKey(task.getFormKey());
        vo.setTaskDefinitionKey(task.getTaskDefinitionKey());
        vo.setTaskName(task.getName());
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
    public void delegateTask(String taskId, String targetAssignee) {
        String target = identity.requireExistingUser(targetAssignee);
        String user = identity.currentUserId();
        Task task = requireTask(taskId, identity.currentTenantId());
        withAuthenticatedUser(user, () -> taskService.delegateTask(taskId, target));
        log.info("任务委派: taskId={}, from={}, to={}", taskId, user, target);
        notifyAssignee(target, "任务委派", "TASK_DELEGATE", task, user);
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

    /** 流程结束（完成 / 终止）后通知发起人；自己发起又自己办完的不打扰 */
    private void notifyIfFinished(String instanceId, String actorUsername) {
        if (instanceId == null) return;
        try {
            if (runtimeService.createProcessInstanceQuery().processInstanceId(instanceId).count() > 0) {
                return;
            }
            HistoricProcessInstance hpi = historyService.createHistoricProcessInstanceQuery()
                    .processInstanceId(instanceId).singleResult();
            if (hpi == null || hpi.getStartUserId() == null
                    || hpi.getStartUserId().equals(actorUsername)) {
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
