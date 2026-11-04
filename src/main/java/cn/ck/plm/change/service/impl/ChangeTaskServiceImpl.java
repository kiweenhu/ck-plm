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
import cn.ck.plm.change.entity.ChangeNotice;
import cn.ck.plm.change.entity.ChangeTask;
import cn.ck.plm.change.flow.ChangeNoticeFlow;
import cn.ck.plm.change.flow.ChangeTaskFlow;
import cn.ck.plm.change.mapper.ChangeActivityMapper;
import cn.ck.plm.change.mapper.ChangeNoticeMapper;
import cn.ck.plm.change.mapper.ChangeTaskMapper;
import cn.ck.plm.change.service.api.ChangeTaskService;
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

    private static final int DEFAULT_PAGE_SIZE = 20;
    private static final int MAX_PAGE_SIZE = 200;

    private final ChangeTaskMapper mapper;
    private final ChangeNoticeMapper noticeMapper;
    private final ChangeActivityMapper activityMapper;
    /** 编号生成：按 CHANGE_TASK 类型绑定的编码规则（随 platform-base 包安装） */
    private final NumberService numberService;

    public ChangeTaskServiceImpl(ChangeTaskMapper mapper,
                                 ChangeNoticeMapper noticeMapper,
                                 ChangeActivityMapper activityMapper,
                                 NumberService numberService) {
        this.mapper = mapper;
        this.noticeMapper = noticeMapper;
        this.activityMapper = activityMapper;
        this.numberService = numberService;
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

    // ==================== 查询 ====================

    @Override
    public ChangeTask detail(String oid) {
        return isBlank(oid) ? null : mapper.selectByOid(oid);
    }

    @Override
    public List<ChangeTask> byNotice(String noticeOid) {
        return isBlank(noticeOid) ? List.of() : mapper.selectByNotice(noticeOid);
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
}
