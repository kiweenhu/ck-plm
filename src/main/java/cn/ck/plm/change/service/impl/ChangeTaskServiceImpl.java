/*
 * Copyright (c) 2026~2028 深圳乘恺科技有限公司
 * All rights reserved.
 *
 * @author Kiween.Hu; Roney.Liu
 */

package cn.ck.plm.change.service.impl;

import cn.ck.plm.base.service.api.NumberService;
import cn.ck.plm.base.util.TenantContext;
import cn.ck.plm.base.util.UserContext;
import cn.ck.plm.change.entity.ChangeActivity;
import cn.ck.plm.change.entity.ChangeAffectedObject;
import cn.ck.plm.change.entity.ChangeNotice;
import cn.ck.plm.change.entity.ChangeResultObject;
import cn.ck.plm.change.entity.ChangeTask;
import cn.ck.plm.change.flow.ChangeNoticeFlow;
import cn.ck.plm.change.flow.ChangeTaskFlow;
import cn.ck.plm.change.mapper.ChangeActivityMapper;
import cn.ck.plm.change.mapper.ChangeAffectedObjectMapper;
import cn.ck.plm.change.mapper.ChangeNoticeMapper;
import cn.ck.plm.change.mapper.ChangeObjectLookupMapper;
import cn.ck.plm.change.mapper.ChangeResultObjectMapper;
import cn.ck.plm.change.mapper.ChangeTaskMapper;
import cn.ck.plm.change.service.api.ChangeTaskService;
import cn.ck.plm.process.entity.ProcessEntitySet;
import cn.ck.plm.process.mapper.ProcessEntitySetMapper;
import cn.ck.plm.process.service.api.ProcessService;
import cn.ck.plm.revise.service.impl.ReviseOperationServiceImpl;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * 变更任务服务实现。
 *
 * <h3>责任人锁定</h3>
 * <p>{@code START} / {@code SUBMIT} 只允许任务责任人执行。这不是权限系统的替代品，
 * 而是"这件事谁认"的表达：变更被退回时，必须能一眼看出是哪个环节的处理人返工的。
 */
@Service
public class ChangeTaskServiceImpl implements ChangeTaskService {

    private static final Logger log = LoggerFactory.getLogger(ChangeTaskServiceImpl.class);

    /** 任务类型白名单 */
    private static final Set<String> TASK_TYPES = new LinkedHashSet<>(List.of(
            "DESIGN", "DOCUMENT", "BOM", "PROCESS", "VERIFY"));

    /** 结果类型：修订（当前唯一在用；SPLIT / NEW 留给拆件与新建，表已预留） */
    private static final String RESULT_KIND_REVISE = "REVISE";

    private static final int DEFAULT_PAGE_SIZE = 20;
    private static final int MAX_PAGE_SIZE = 200;

    private final ChangeTaskMapper mapper;
    private final ChangeNoticeMapper noticeMapper;
    private final ChangeActivityMapper activityMapper;
    /** 任务下的受影响对象（执行修订的作用范围）—— 变更前 */
    private final ChangeAffectedObjectMapper affectedMapper;
    /** 执行产出的版本 —— 变更后（2026-11-09 拆表：事实只存这里） */
    private final ChangeResultObjectMapper resultMapper;
    /** 对象快照：修订后回查新版本号（A.2 → B.1），只读三张主表 */
    private final ChangeObjectLookupMapper objectLookupMapper;
    /** 修订统一入口：与行操作「修订」同一段代码，不在这里复制版本逻辑 */
    private final ReviseOperationServiceImpl reviseOperationService;
    /** 编号生成：按 CHANGE_TASK 类型绑定的编码规则（随 platform-base 包安装） */
    private final NumberService numberService;
    /** 子流程实例按 businessKey = 任务 oid 关联，删除 / 手工流转前要问它"流程起来没有" */
    private final ProcessService processService;
    /** 流程实例 ↔ 业务实体关联：修订后把新大版本挂到变更任务子流程上，供 object.* 服务节点作用 */
    private final ProcessEntitySetMapper entitySetMapper;

    public ChangeTaskServiceImpl(ChangeTaskMapper mapper,
                                 ChangeNoticeMapper noticeMapper,
                                 ChangeActivityMapper activityMapper,
                                 ChangeAffectedObjectMapper affectedMapper,
                                 ChangeResultObjectMapper resultMapper,
                                 ChangeObjectLookupMapper objectLookupMapper,
                                 ReviseOperationServiceImpl reviseOperationService,
                                 NumberService numberService,
                                 ProcessService processService,
                                 ProcessEntitySetMapper entitySetMapper) {
        this.mapper = mapper;
        this.noticeMapper = noticeMapper;
        this.activityMapper = activityMapper;
        this.affectedMapper = affectedMapper;
        this.resultMapper = resultMapper;
        this.objectLookupMapper = objectLookupMapper;
        this.reviseOperationService = reviseOperationService;
        this.numberService = numberService;
        this.processService = processService;
        this.entitySetMapper = entitySetMapper;
    }

    private String tenantOid() { return TenantContext.get(); }

    // ==================== 创建 / 编辑 ====================

    @Override
    @Transactional
    public ChangeTask create(ChangeTask task) {
        if (task == null || isBlank(task.getName())) {
            throw new IllegalArgumentException("变更任务标题不能为空");
        }
        if (isBlank(task.getNoticeOid())) {
            throw new IllegalArgumentException("必须指定所属变更通告");
        }
        ChangeNotice notice = noticeMapper.selectByOid(task.getNoticeOid());
        if (notice == null) throw new IllegalArgumentException("变更通告不存在");
        if (!ChangeNoticeFlow.isTaskPlannable(notice.getStatus())) {
            throw new IllegalStateException("变更通告当前状态（" + notice.getStatus() + "）不允许规划任务");
        }

        task.setName(task.getName().trim());
        task.setTaskType(normalizeTaskType(task.getTaskType()));
        validatePlan(task.getPlanStart(), task.getPlanEnd());

        // 审批前建的是"事项清单"，等审批通过统一派发；审批后补的项直接派发，
        // 否则它永远停在 DRAFT —— 派发节点已经过去了
        boolean planned = ChangeNoticeFlow.isPlannedBeforeApproval(notice.getStatus());
        String initial = planned ? ChangeTaskFlow.DRAFT : ChangeTaskFlow.OPEN;

        task.setOid(UUID.randomUUID().toString());
        task.setCode(nextCode());
        task.setStatus(initial);                      // 入参 status 一律忽略
        task.setCompletedAt(null);
        if (task.getTenantOid() == null) task.setTenantOid(tenantOid());
        normalizeBlanks(task);

        mapper.insert(task);
        record(task.getOid(), "CREATE", null, initial, planned
                ? "更改事项已加入清单（审批通过后自动生效）"
                : "变更任务已创建并生效");
        log.info("变更任务已创建: {}（{}）初始状态 {}", task.getCode(), task.getName(), initial);
        return mapper.selectByOid(task.getOid());
    }

    @Override
    @Transactional
    public int dispatchByNotice(String noticeOid) {
        if (isBlank(noticeOid)) return 0;
        // 状态门槛照查；责任人门槛只在有登录用户时生效（流程回调跑在系统线程上）
        assertDispatchAllowed(noticeMapper.selectByOid(noticeOid));
        List<ChangeTask> drafts = mapper.selectByNotice(noticeOid).stream()
                .filter((task) -> ChangeTaskFlow.isDraft(task.getStatus()))
                .toList();
        if (drafts.isEmpty()) return 0;

        for (ChangeTask task : drafts) {
            ChangeTask target = copyOf(task);
            target.setStatus(ChangeTaskFlow.OPEN);
            target.setUpdater(UserContext.get());
            target.setUpdatedAt(LocalDateTime.now());
            mapper.update(target);
            record(task.getOid(), ChangeTaskFlow.DISPATCH, ChangeTaskFlow.DRAFT, ChangeTaskFlow.OPEN,
                    "变更通告审批通过，任务已派发" + (isBlank(task.getAssignee())
                            ? "（未指派责任人）" : "给 " + task.getAssignee()));
        }
        log.info("变更通告 {} 派发变更任务 {} 条", noticeOid, drafts.size());
        return drafts.size();
    }

    @Override
    @Transactional
    public ChangeTask update(ChangeTask patch) {
        if (patch == null || isBlank(patch.getOid())) {
            throw new IllegalArgumentException("oid 不能为空");
        }
        ChangeTask existing = require(patch.getOid());
        if (ChangeTaskFlow.isTerminal(existing.getStatus())) {
            throw new IllegalStateException("已完成的变更任务不可编辑");
        }
        if (patch.getTaskType() != null) patch.setTaskType(normalizeTaskType(patch.getTaskType()));

        ChangeTask target = copyOf(existing);
        if (patch.getName() != null) {
            if (patch.getName().trim().isEmpty()) throw new IllegalArgumentException("标题不能为空");
            target.setName(patch.getName().trim());
        }
        if (patch.getDescription() != null) target.setDescription(patch.getDescription());
        if (patch.getTaskType() != null) target.setTaskType(patch.getTaskType());
        if (patch.getAssignee() != null) target.setAssignee(patch.getAssignee());
        if (patch.getPlanStart() != null) target.setPlanStart(patch.getPlanStart());
        if (patch.getPlanEnd() != null) target.setPlanEnd(patch.getPlanEnd());
        validatePlan(target.getPlanStart(), target.getPlanEnd());
        normalizeBlanks(target);

        mapper.update(target);
        record(patch.getOid(), "UPDATE", null, null, "变更任务信息已更新");
        return mapper.selectByOid(patch.getOid());
    }

    // ==================== 状态流转 ====================

    @Override
    @Transactional
    public ChangeTask transition(String oid, String action, String comment) {
        if (isBlank(oid)) throw new IllegalArgumentException("oid 不能为空");
        ChangeTask existing = require(oid);

        String normalized = isBlank(action) ? "" : action.trim().toUpperCase();
        String to = ChangeTaskFlow.resolve(normalized, existing.getStatus());   // 非法流转在这里拦下

        // 任务一旦拉起子流程实例，状态就由流程里的办理推进：再手工「开始处理」，
        // 流程里还挂着待办、业务侧已显示进行中，两边对不上
        if (ChangeTaskFlow.START.equals(normalized) && hasProcessInstance(oid)) {
            throw new IllegalStateException(
                    "该任务的变更流程已启动，请到「任务中心」办理，不能直接开始处理");
        }

        // 派发 = 让责任人开始干活，只能在通告审批通过之后；审批前清单还可以随便改
        if (ChangeTaskFlow.DISPATCH.equals(normalized)) {
            assertDispatchAllowed(noticeMapper.selectByOid(existing.getNoticeOid()));
        }

        if (ChangeTaskFlow.requiresOwner(normalized)) {
            if (isBlank(existing.getAssignee())) {
                throw new IllegalStateException("任务尚未指派责任人，无法「"
                        + ChangeTaskFlow.label(normalized) + "」");
            }
            if (!existing.getAssignee().equals(UserContext.get())) {
                throw new IllegalStateException("只有任务责任人「" + existing.getAssignee()
                        + "」才能执行「" + ChangeTaskFlow.label(normalized) + "」");
            }
        }
        if (ChangeTaskFlow.requiresComment(normalized) && isBlank(comment)) {
            throw new IllegalArgumentException("「" + ChangeTaskFlow.label(normalized) + "」必须填写说明");
        }

        ChangeTask target = copyOf(existing);
        target.setStatus(to);
        target.setCompletedAt(ChangeTaskFlow.PASS.equals(normalized)
                ? LocalDateTime.now() : existing.getCompletedAt());

        mapper.update(target);
        record(oid, normalized, existing.getStatus(), to, isBlank(comment) ? null : comment.trim());
        log.info("变更任务 {} 流转: {} -> {}（{}）", existing.getCode(), existing.getStatus(), to, normalized);
        return mapper.selectByOid(oid);
    }

    @Override
    @Transactional
    public ChangeTask completeByProcess(String oid, String comment) {
        if (isBlank(oid)) throw new IllegalArgumentException("变更任务 oid 不能为空");
        ChangeTask existing = mapper.selectByOid(oid);
        if (existing == null) throw new IllegalArgumentException("变更任务不存在: " + oid);

        // 幂等：流程重试 / 重复回调时不再多留一条时间线
        if (ChangeTaskFlow.COMPLETED.equals(existing.getStatus())) {
            log.debug("变更任务 {} 已是已完成，流程收尾跳过", existing.getCode());
            return existing;
        }

        String from = existing.getStatus();
        ChangeTask target = copyOf(existing);
        target.setStatus(ChangeTaskFlow.COMPLETED);
        target.setCompletedAt(LocalDateTime.now());
        mapper.update(target);

        record(oid, ChangeTaskFlow.PASS, from, ChangeTaskFlow.COMPLETED,
                isBlank(comment) ? "变更任务流程完成，任务自动结项" : comment.trim());
        log.info("变更任务 {} 流程收尾: {} -> {}", existing.getCode(), from, ChangeTaskFlow.COMPLETED);
        return mapper.selectByOid(oid);
    }

    // ==================== 执行修订 ====================

    /**
     * 执行变更任务：把任务下的受影响对象自动修订出新大版本。
     *
     * <p>为什么由系统代劳：受影响对象上写死了"被修订的那一版"（{@code base_iteration_oid}），
     * 变更要表达的就是"这一版要出新大版本"。让执行人自己去对象列表里一个个点「修订」，
     * 结果一定是有改有漏，事后没人说得清这次变更到底落地了没有 ——
     * 执行范围与修订产物必须落在同一张表上（{@code result_iteration_oid / result_version}）。
     *
     * <p>幂等：已执行（{@code executedAt != null}）的行跳过，重复点「执行」不会出第二个 B 版。
     * 整批同事务：中途某个对象修订失败（如已检出），前面已修订的一并回滚 ——
     * 留半批 B 版比一次失败更难收拾。
     */
    @Override
    @Transactional
    public List<ChangeAffectedObject> execute(String taskOid, List<String> affectedOids) {
        if (isBlank(taskOid)) throw new IllegalArgumentException("任务 oid 不能为空");
        ChangeTask task = require(taskOid);
        if (ChangeTaskFlow.isTerminal(task.getStatus()) || ChangeTaskFlow.SUBMITTED.equals(task.getStatus())) {
            throw new IllegalStateException("任务当前状态（" + task.getStatus()
                    + "）不能再执行修订：已经提交验收，此时改对象等于验收的不是同一批东西");
        }
        String user = UserContext.get();
        // 与 START 同一口径：谁执行谁留名，别人代点会让"谁改的"落不到人头上
        if (!isBlank(task.getAssignee()) && !task.getAssignee().equals(user)) {
            throw new IllegalStateException("只有任务责任人「" + task.getAssignee() + "」才能执行本任务的修订");
        }

        int revised = doExecute(task, affectedOids, user);
        if (revised == 0) {
            throw new IllegalStateException("没有需要执行的对象：勾选的行都已修订过");
        }
        return affectedMapper.selectByTask(taskOid);
    }

    @Override
    @Transactional
    public List<ChangeAffectedObject> executeByProcess(String taskOid) {
        if (isBlank(taskOid)) throw new IllegalArgumentException("变更任务 oid 不能为空");
        ChangeTask task = require(taskOid);
        if (ChangeTaskFlow.isTerminal(task.getStatus())) {
            // 流程重试、回退后重跑：任务已经结束，什么都不做（幂等）
            return affectedMapper.selectByTask(taskOid);
        }
        String user = UserContext.get();
        if (isBlank(user)) {
            // 引擎线程拿不到登录人时退回任务责任人：留痕宁可记责任人，也不要空着
            user = task.getAssignee();
        }
        doExecute(task, null, user);
        return affectedMapper.selectByTask(taskOid);
    }

    /**
     * 修订任务下所有未出过结果的对象，返回实际修订条数。
     *
     * <p>调用方决定"一条都没修"算不算错：人工执行按错误处理（说明勾选的都是已完成的行），
     * 流程收尾则静默通过（重跑很正常）。
     */
    private int doExecute(ChangeTask task, List<String> affectedOids, String user) {
        String taskOid = task.getOid();
        List<ChangeAffectedObject> rows = affectedMapper.selectByTask(taskOid);
        if (rows.isEmpty()) {
            throw new IllegalStateException("该任务下还没有变更对象，无法执行修订；"
                    + "请先在变更通告上把对象归入这条更改事项");
        }
        Set<String> wanted = affectedOids == null || affectedOids.isEmpty()
                ? null
                : new LinkedHashSet<>(affectedOids.stream()
                        .filter((oid) -> !isBlank(oid)).map(String::trim).toList());

        int revised = 0;
        List<String> failed = new java.util.ArrayList<>();
        for (ChangeAffectedObject row : rows) {
            if (wanted != null && !wanted.contains(row.getOid())) continue;
            // 幂等：已有结果行（JOIN 出来的 executedAt 非空）不再出第二个大版本
            if (row.getExecutedAt() != null) continue;
            try {
                reviseOne(row, user, task.getTenantOid());
                revised++;
            } catch (RuntimeException e) {
                // 收集完再抛：一次告诉用户哪些对象没修成，别让他改一个试一次
                failed.add(nullToEmpty(row.getEntityCode()) + "：" + e.getMessage());
            }
        }
        if (!failed.isEmpty()) {
            throw new IllegalStateException("有 " + failed.size() + " 个对象修订失败（已全部回滚）："
                    + String.join("；", failed));
        }
        if (revised > 0) {
            // 执行 = 开工：任务状态跟着从「待开始」推进到「进行中」
            String from = task.getStatus();
            String to = ChangeTaskFlow.IN_WORK.equals(from) ? from : ChangeTaskFlow.IN_WORK;
            ChangeTask patch = copyOf(task);
            patch.setStatus(to);
            patch.setUpdater(user);
            patch.setUpdatedAt(LocalDateTime.now());
            mapper.update(patch);
            record(taskOid, ChangeTaskFlow.EXECUTE, from, to,
                    "执行修订：" + revised + " 个变更对象出新版本");
        }
        log.info("变更任务 {} 执行修订 {} 个对象（操作人 {}）", task.getCode(), revised, user);
        // 「获取变更权限」后把修订产物挂到本任务子流程实例：下游 object.* 服务节点才有作用对象
        recordResultEntityLinks(taskOid, user, task.getTenantOid());
        return revised;
    }

    /**
     * 修订单个受影响对象，<b>产物写进结果表</b>（变更后）。
     *
     * <p>修订后重新查一次快照拿新版本号：{@code ReviseProvider} 只负责建版本、不返回产物
     * （契约是 {@code void}），版本信息只能从对象的 {@code latest} 迭代读。
     *
     * <p>2026-11-09 拆表后这里不再回填 {@code ck_change_affected_object} 的 result 列 ——
     * 那是历史字段，已随迁移删除。一条变更前对象产出一条结果，一对一时就是插入一行。
     */
    private void reviseOne(ChangeAffectedObject row, String user, String tenantOid) {
        if (isBlank(row.getEntityType()) || isBlank(row.getEntityOid())) {
            throw new IllegalStateException("受影响对象缺少类型或 oid");
        }
        try {
            reviseOperationService.revise(row.getEntityType(), row.getEntityOid(), user);
        } catch (IllegalArgumentException e) {
            throw new IllegalStateException(e.getMessage());
        } catch (IllegalStateException e) {
            throw new IllegalStateException(e.getMessage());
        }
        Map<String, Object> snapshot = snapshotOf(row.getEntityType(), row.getEntityOid(), tenantOid);
        String iterationOid = snapshot == null ? null : stringOf(snapshot.get("iterationOid"));
        String version = snapshot == null ? null : stringOf(snapshot.get("version"));
        if (isBlank(iterationOid) || isBlank(version)) {
            throw new IllegalStateException("修订后查不到新版本（对象可能已被删除）");
        }
        ChangeResultObject result = new ChangeResultObject();
        result.setOid(UUID.randomUUID().toString());
        result.setAffectedOid(row.getOid());
        result.setNoticeOid(row.getNoticeOid());
        result.setTaskOid(row.getTaskOid());
        result.setEntityType(row.getEntityType());
        result.setEntityOid(row.getEntityOid());
        result.setEntityCode(row.getEntityCode());
        result.setEntityName(row.getEntityName());
        result.setSourceIterationOid(row.getBaseIterationOid());
        result.setSourceVersion(row.getBaseVersion());
        result.setResultIterationOid(iterationOid);
        result.setResultVersion(version);
        result.setChangeKind(RESULT_KIND_REVISE);
        result.setExecutedAt(LocalDateTime.now());
        result.setExecutedBy(user);
        result.setTenantOid(isBlank(tenantOid) ? tenantOid() : tenantOid);
        result.setCreator(user);
        result.setUpdater(user);
        resultMapper.insert(result);
    }

    /** 指定类型取当前 latest 快照；类型不在白名单返回 null */
    private Map<String, Object> snapshotOf(String entityType, String entityOid, String tenantOid) {
        String tenant = isBlank(tenantOid) ? tenantOid() : tenantOid;
        return switch (entityType == null ? "" : entityType.trim().toUpperCase()) {
            case "PART" -> objectLookupMapper.selectPartSnapshot(entityOid, tenant);
            case "DOCUMENT" -> objectLookupMapper.selectDocumentSnapshot(entityOid, tenant);
            case "ENG_DOCUMENT" -> objectLookupMapper.selectEngDocumentSnapshot(entityOid, tenant);
            default -> null;
        };
    }

    /**
     * 「获取变更权限」修订完成后，把变更后的对象写进变更任务子流程的 {@code ck_process_entity_set}。
     *
     * <p>ECT 子流程由 ECN 图里的 callActivity 在引擎内部拉起（businessKey = 任务 oid），
     * 不走 {@code /workflow/instance/start}，因此发起时没有像普通流程那样写入实体关联；
     * 下游 {@code object.*} 自动服务节点按 {@code process_instance_id} 解析作用对象，这里补上才有 targets。
     *
     * <p>按「本任务全部结果行」补齐而不是只补本次新增：重复点击、首次写失败、流程节点执行路径
     * 都能自愈；{@code insertIfAbsent} 靠唯一键去重，不会堆重复行。
     *
     * <p>按 businessKey 取该任务最新的子流程实例（在跑或已结束都写：这张表本身就是
     * 「实例 ↔ 对象」的历史事实，流程结束后仍要能反查对象参与过哪些流程）；
     * 免审单 / 子流程还没拉起时静默跳过。关联写失败只告警，
     * 绝不回滚已经产出的新版本 —— 所以这里直接走 mapper，不调
     * {@code ProcessEntitySetService.record()}：后者带 {@code @Transactional}，
     * 在外层修订事务里抛异常会把整个事务标成 rollback-only，catch 也救不回来。
     */
    private void recordResultEntityLinks(String taskOid, String user, String tenantOid) {
        String instanceId = processService.instanceIdsByBusinessKey(List.of(taskOid)).get(taskOid);
        if (isBlank(instanceId)) {
            log.debug("变更任务 {} 没有子流程实例（免审单 / 尚未派发），跳过流程实体关联", taskOid);
            return;
        }
        List<ChangeResultObject> results = resultMapper.selectByTask(taskOid);
        if (results.isEmpty()) return;
        String tenant = isBlank(tenantOid) ? tenantOid() : tenantOid;
        LocalDateTime now = LocalDateTime.now();
        for (ChangeResultObject result : results) {
            if (isBlank(result.getEntityOid())) continue;
            String type = String.valueOf(result.getEntityType()).trim().toUpperCase();
            String revision = revisionOfIteration(type, result.getResultIterationOid(), tenant);
            if (isBlank(revision)) revision = majorVersionOf(result.getResultVersion());
            if (isBlank(revision)) {
                log.warn("变更任务 {} 的结果对象 {} 查不到大版本，未写入流程实体关联",
                        taskOid, result.getEntityCode());
                continue;
            }
            ProcessEntitySet link = new ProcessEntitySet();
            link.setOid(UUID.randomUUID().toString());
            link.setProcessInstanceId(instanceId);
            link.setBusinessKey(taskOid);
            link.setEntityOid(result.getEntityOid());
            link.setEntityVersion(revision);
            link.setTypeCode(type);
            link.setRootTypeCode(type);
            link.setTenantOid(tenant);
            link.setCreator(user);
            link.setCreatedAt(now);
            link.setUpdater(user);
            link.setUpdatedAt(now);
            try {
                entitySetMapper.insertIfAbsent(link);
            } catch (RuntimeException e) {
                log.warn("变更任务 {} 的结果对象 {}@{} 写入流程实体关联失败：{}",
                        taskOid, result.getEntityCode(), revision, e.getMessage());
            }
        }
    }

    /** 结果迭代 oid → 所属大版本（B.1 那行 → B）；类型不在白名单返回 null */
    private String revisionOfIteration(String entityType, String iterationOid, String tenant) {
        if (isBlank(iterationOid)) return null;
        return switch (entityType) {
            case "PART" -> objectLookupMapper.selectPartRevisionOfIteration(iterationOid, tenant);
            case "DOCUMENT" -> objectLookupMapper.selectDocumentRevisionOfIteration(iterationOid, tenant);
            case "ENG_DOCUMENT" -> objectLookupMapper.selectEngDocumentRevisionOfIteration(iterationOid, tenant);
            default -> null;
        };
    }

    /** 迭代行被清理时的兜底：display_version 去掉小版本段（B.1 → B，A-Z.2 → A-Z） */
    private static String majorVersionOf(String displayVersion) {
        if (isBlank(displayVersion)) return null;
        String v = displayVersion.trim();
        int dot = v.lastIndexOf('.');
        return dot > 0 ? v.substring(0, dot) : v;
    }

    // ==================== 查询 ====================

    @Override
    public ChangeTask detail(String oid) {
        return isBlank(oid) ? null : mapper.selectByOid(oid);
    }

    @Override
    public List<ChangeTask> byNotice(String noticeOid) {
        if (isBlank(noticeOid)) return List.of();
        List<ChangeTask> tasks = mapper.selectByNotice(noticeOid);
        markTaskProcesses(tasks);
        return tasks;
    }

    @Override
    public Map<String, Object> mine(String assignee, String status, String keyword, int page, int pageSize) {
        String normalizedAssignee = isBlank(assignee) ? null : assignee.trim();
        String normalizedStatus = normalizeStatusFilter(status);
        String normalizedKeyword = isBlank(keyword) ? null : keyword.trim();
        int size = normalizePageSize(pageSize);
        int current = Math.max(page, 1);
        int offset = (current - 1) * size;

        List<ChangeTask> rows = mapper.selectMine(tenantOid(), normalizedAssignee, normalizedStatus,
                normalizedKeyword, size, offset);
        // 列表里要能看出"这条任务已经交给流程了"：手工流转按钮换成「去办理 / 流程」
        markTaskProcesses(rows);
        int total = mapper.countMine(tenantOid(), normalizedAssignee, normalizedStatus, normalizedKeyword);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("rows", rows);
        result.put("total", total);
        result.put("page", current);
        result.put("pageSize", size);
        return result;
    }

    @Override
    public Map<String, Object> stats(String assignee) {
        String normalizedAssignee = isBlank(assignee) ? null : assignee.trim();
        // 先把流程里定义过的状态铺成 0：前端要的是"每一项都在"，不是"有单的才出现"。
        // 草稿除外 —— 那是通告上的计划清单，不是任何人的待办（见 selectMine 的过滤）
        Map<String, Object> counts = new LinkedHashMap<>();
        for (String status : ChangeTaskFlow.statuses()) {
            if (!ChangeTaskFlow.isDraft(status)) counts.put(status, 0);
        }
        int total = 0;
        for (Map<String, Object> row : mapper.countByStatus(tenantOid(), normalizedAssignee)) {
            Object status = row.get("status");
            Object cnt = row.get("cnt");
            int value = cnt instanceof Number ? ((Number) cnt).intValue() : 0;
            if (status != null) counts.put(status.toString(), value);
            total += value;
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("total", total);
        result.put("counts", counts);
        return result;
    }

    @Override
    public List<ChangeActivity> activities(String oid) {
        return activityMapper.selectByOwner("TASK", oid);
    }

    // ==================== 删除 ====================

    @Override
    @Transactional
    public boolean delete(String oid) {
        if (isBlank(oid)) throw new IllegalArgumentException("oid 不能为空");
        ChangeTask existing = mapper.selectByOid(oid);
        if (existing == null) return false;
        if (!ChangeTaskFlow.isDraft(existing.getStatus())
                && !ChangeTaskFlow.OPEN.equals(existing.getStatus())) {
            throw new IllegalStateException(
                    "只有草稿或未开始的变更任务可以删除（当前 " + existing.getStatus() + "）");
        }
        // 子流程已拉起：删掉任务，流程实例的 businessKey 就指向一条不存在的记录，
        // 待办还在、单据没了。要停先到「流程监控」终止实例
        if (hasProcessInstance(oid)) {
            throw new IllegalStateException(
                    "该任务的变更流程已启动，不能删除；如需终止请先在「流程监控」里处理该流程实例");
        }
        activityMapper.deleteByOwner("TASK", oid);
        return mapper.deleteByOid(oid) > 0;
    }

    // ==================== 内部 ====================

    private ChangeTask require(String oid) {
        ChangeTask task = mapper.selectByOid(oid);
        if (task == null) throw new IllegalArgumentException("变更任务不存在");
        return task;
    }

    /**
     * 该任务是否已经拉起子流程实例（businessKey = 任务 oid）。
     *
     * <p>有实例 = 这条任务的执行由流程驱动：删不得（流程还在跑 / 已有执行痕迹），
     * 也不能手工「开始处理」（状态该由流程里的办理推进）。
     */
    private boolean hasProcessInstance(String taskOid) {
        return !processService.instanceIdsByBusinessKey(List.of(taskOid)).isEmpty();
    }

    /**
     * 批量回填每条任务对应的子流程实例（businessKey = 任务 oid）。
     *
     * <p>一次查询，别在任务列表里逐行打引擎。
     */
    private void markTaskProcesses(List<ChangeTask> tasks) {
        if (tasks == null || tasks.isEmpty()) {
            return;
        }
        Map<String, String> byBusinessKey = processService.instanceIdsByBusinessKey(
                tasks.stream().map(ChangeTask::getOid).toList());
        for (ChangeTask task : tasks) {
            task.setProcessInstanceId(byBusinessKey.get(task.getOid()));
        }
    }

    /**
     * 派发门槛：通告必须已过审批。
     *
     * <p>刻意不校验责任人：审批通过后的自动派发跑在流程回调里，那里未必有登录用户，
     * 按责任人硬拦会让整批清单卡在草稿态、而且失败是静默的（回调只记日志）。
     * 界面已不提供手工派发入口（2026-11-03），这里只剩回调这条路径。
     */
    private void assertDispatchAllowed(ChangeNotice notice) {
        if (notice == null) throw new IllegalArgumentException("变更通告不存在");
        if (!ChangeNoticeFlow.isDispatchable(notice.getStatus())) {
            throw new IllegalStateException("变更通告尚未审批通过（当前 " + notice.getStatus()
                    + "），不能派发变更任务");
        }
    }

    private ChangeTask copyOf(ChangeTask source) {
        ChangeTask target = new ChangeTask();
        target.setOid(source.getOid());
        target.setCode(source.getCode());
        target.setName(source.getName());
        target.setDescription(source.getDescription());
        target.setNoticeOid(source.getNoticeOid());
        target.setTaskType(source.getTaskType());
        target.setAssignee(source.getAssignee());
        target.setStatus(source.getStatus());
        target.setPlanStart(source.getPlanStart());
        target.setPlanEnd(source.getPlanEnd());
        target.setCompletedAt(source.getCompletedAt());
        target.setTenantOid(source.getTenantOid());
        return target;
    }

    /**
     * 取下一个编码：优先走类型绑定的编码规则（{@code CHANGE_TASK} → ECT-0001），
     * 规则未安装或未绑定时回退本地序列（格式同构，可无缝切换）。
     */
    private String nextCode() {
        try {
            String generated = numberService.generateNumberForType("CHANGE_TASK");
            if (generated != null && !generated.trim().isEmpty()) {
                return generated;
            }
        } catch (Exception e) {
            log.warn("按类型生成变更任务编号失败，回退本地序列: {}", e.getMessage());
        }
        return String.format("ECT-%04d", mapper.maxCodeSeq(tenantOid()) + 1);
    }

    private String normalizeTaskType(String value) {
        if (isBlank(value)) return null;
        String normalized = value.trim().toUpperCase();
        if (!TASK_TYPES.contains(normalized)) {
            throw new IllegalArgumentException("任务类型只能是 " + TASK_TYPES);
        }
        return normalized;
    }

    private String normalizeStatusFilter(String value) {
        if (isBlank(value)) return null;
        String normalized = value.trim().toUpperCase();
        if (!ChangeTaskFlow.isValidStatus(normalized)) {
            throw new IllegalArgumentException("未知状态 '" + value + "'，取值只能是 " + ChangeTaskFlow.statuses());
        }
        return normalized;
    }

    private static void validatePlan(java.time.LocalDate start, java.time.LocalDate end) {
        if (start != null && end != null && end.isBefore(start)) {
            throw new IllegalArgumentException("计划完成日期不能早于开始日期");
        }
    }

    private static int normalizePageSize(int pageSize) {
        if (pageSize <= 0) return DEFAULT_PAGE_SIZE;
        return Math.min(pageSize, MAX_PAGE_SIZE);
    }

    private void record(String oid, String action, String from, String to, String comment) {
        ChangeActivity activity = new ChangeActivity();
        activity.setOid(UUID.randomUUID().toString());
        activity.setChangeType("TASK");
        activity.setChangeOid(oid);
        activity.setAction(action);
        activity.setFromStatus(from);
        activity.setToStatus(to);
        activity.setComment(comment);
        activity.setOperator(UserContext.get());
        activity.setTenantOid(tenantOid());
        activityMapper.insert(activity);
    }

    private void normalizeBlanks(ChangeTask task) {
        task.setDescription(blankToNull(task.getDescription()));
        task.setTaskType(blankToNull(task.getTaskType()));
        task.setAssignee(blankToNull(task.getAssignee()));
    }

    private static String blankToNull(String value) {
        return (value != null && value.trim().isEmpty()) ? null : value;
    }

    private static boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

    /** 快照 Map 里的值统一转字符串（null 安全） */
    private static String stringOf(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }
}
