/*
 * Copyright (c) 2026~2028 深圳乘恺科技有限公司
 * All rights reserved.
 *
 * @author Kiween.Hu; Roney.Liu
 */

package cn.ck.plm.change.controller;

import cn.ck.plm.base.util.UserContext;
import cn.ck.plm.change.entity.ChangeAffectedObject;
import cn.ck.plm.change.entity.ChangeTask;
import cn.ck.plm.change.service.api.ChangeTaskService;
import cn.ck.plm.iam.dto.ApiResponse;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 变更任务 REST 控制器。
 *
 * <pre>
 * GET    /api/plm/change-tasks/my                我的任务（默认当前用户；assignee 可覆盖）
 * GET    /api/plm/change-tasks/stats             各状态计数（assignee 为空 = 全部）
 * GET    /api/plm/change-tasks/by-notice/{oid}   某张变更通告下的任务
 * GET    /api/plm/change-tasks/{oid}             详情（任务本体 + 时间线）
 * POST   /api/plm/change-tasks                   新建（编号 ECT-####；审批前建的是 DRAFT 清单，审批后直接 OPEN）
 * PUT    /api/plm/change-tasks/{oid}             编辑（patch 语义；已完成不可编辑）
 * POST   /api/plm/change-tasks/{oid}/transition  状态流转 { action, comment }
 * POST   /api/plm/change-tasks/{oid}/execute     执行修订（受影响对象批量出新大版本）{ affectedOids }
 * POST   /api/plm/change-tasks/by-notice/{oid}/dispatch  派发该通告下全部草稿任务
 * DELETE /api/plm/change-tasks/{oid}             删除（仅草稿 / 未开始）
 * </pre>
 *
 * <p>{@code START} / {@code SUBMIT} 只允许任务责任人本人执行；{@code DISPATCH} 由通告审批通过
 * 自动触发（2026-11-03 用户口径「不需要派发操作」后，界面不再提供手工派发入口，
 * 这个端点保留给运维/脚本兜底）。
 */
@RestController
@RequestMapping("/api/plm/change-tasks")
public class ChangeTaskController {

    private final ChangeTaskService service;

    public ChangeTaskController(ChangeTaskService service) {
        this.service = service;
    }

    @GetMapping("/my")
    public ApiResponse<Map<String, Object>> my(
            @RequestParam(required = false) String assignee,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int pageSize) {
        String who = (assignee == null || assignee.trim().isEmpty()) ? UserContext.get() : assignee;
        try {
            return ApiResponse.ok(service.mine(who, status, keyword, page, pageSize));
        } catch (IllegalArgumentException e) {
            return ApiResponse.fail(400, e.getMessage());
        }
    }

    /**
     * 各状态计数（页头状态概览）：{@code { total, counts }}。
     *
     * <p>{@code assignee} 为空 = 租户下全部任务；给了就只数该责任人的 —— 与「只看我的」同一口径。
     * 注意这里<b>不</b>照搬 {@code /my} 的"空 = 当前用户"默认值，否则"全部"永远拿不到。
     */
    @GetMapping("/stats")
    public ApiResponse<Map<String, Object>> stats(@RequestParam(required = false) String assignee) {
        return ApiResponse.ok(service.stats(assignee));
    }

    @GetMapping("/by-notice/{noticeOid}")
    public ApiResponse<?> byNotice(@PathVariable String noticeOid) {
        return ApiResponse.ok(service.byNotice(noticeOid));
    }

    /**
     * 派发该通告下的全部草稿任务（审批通过时后端已自动做过一次，这里只作兜底）。
     *
     * <p>界面自 2026-11-03 起不再有「派发」按钮：用户在更改事项上看到的是
     * 「待生效 → 通告审批通过后自动可执行」，不必手工补派。
     *
     * <p>返回派发条数：0 表示"没有待派发的清单"，不是错误（幂等）。
     */
    @PostMapping("/by-notice/{noticeOid}/dispatch")
    public ApiResponse<Integer> dispatch(@PathVariable String noticeOid) {
        try {
            return ApiResponse.ok(service.dispatchByNotice(noticeOid));
        } catch (IllegalStateException e) {
            return ApiResponse.fail(403, e.getMessage());
        }
    }

    @GetMapping("/{oid}")
    public ApiResponse<Map<String, Object>> detail(@PathVariable String oid) {
        ChangeTask task = service.detail(oid);
        if (task == null) return ApiResponse.fail(404, "变更任务不存在");
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("task", task);
        result.put("activities", service.activities(oid));
        return ApiResponse.ok(result);
    }

    @PostMapping
    public ApiResponse<ChangeTask> create(@RequestBody ChangeTask task) {
        try {
            return ApiResponse.ok(service.create(task));
        } catch (IllegalArgumentException e) {
            return ApiResponse.fail(400, e.getMessage());
        } catch (IllegalStateException e) {
            return ApiResponse.fail(403, e.getMessage());
        }
    }

    @PutMapping("/{oid}")
    public ApiResponse<ChangeTask> update(@PathVariable String oid, @RequestBody ChangeTask patch) {
        patch.setOid(oid);
        try {
            return ApiResponse.ok(service.update(patch));
        } catch (IllegalArgumentException e) {
            return ApiResponse.fail(400, e.getMessage());
        } catch (IllegalStateException e) {
            return ApiResponse.fail(403, e.getMessage());
        }
    }

    @PostMapping("/{oid}/transition")
    public ApiResponse<ChangeTask> transition(@PathVariable String oid,
                                              @RequestBody(required = false) Map<String, String> body) {
        Map<String, String> params = body == null ? Map.of() : body;
        try {
            return ApiResponse.ok(service.transition(oid, params.get("action"), params.get("comment")));
        } catch (IllegalArgumentException e) {
            return ApiResponse.fail(400, e.getMessage());
        } catch (IllegalStateException e) {
            return ApiResponse.fail(403, e.getMessage());
        }
    }

    /**
     * 执行变更任务：把任务下的受影响对象自动修订出新大版本，并回填修订产物。
     *
     * <p>body：{@code { "affectedOids": ["..."] }}，省略 = 任务下全部未执行的行。
     * 「执行变更任务」节点表单里那一下「执行变更」就是它 —— 与「提交办理」分开：
     * 先让执行人看清"修订后变成哪一版"，再提交流程。
     */
    @PostMapping("/{oid}/execute")
    public ApiResponse<List<ChangeAffectedObject>> execute(@PathVariable String oid,
                                                           @RequestBody(required = false) Map<String, Object> body) {
        Map<String, Object> params = body == null ? Map.of() : body;
        List<String> affectedOids = new java.util.ArrayList<>();
        Object raw = params.get("affectedOids");
        if (raw instanceof List<?> list) {
            for (Object item : list) {
                if (item != null && !item.toString().trim().isEmpty()) {
                    affectedOids.add(item.toString().trim());
                }
            }
        }
        try {
            return ApiResponse.ok(service.execute(oid, affectedOids));
        } catch (IllegalArgumentException e) {
            return ApiResponse.fail(400, e.getMessage());
        } catch (IllegalStateException e) {
            return ApiResponse.fail(403, e.getMessage());
        }
    }

    @DeleteMapping("/{oid}")
    public ApiResponse<Boolean> delete(@PathVariable String oid) {
        try {
            return ApiResponse.ok(service.delete(oid));
        } catch (IllegalArgumentException e) {
            return ApiResponse.fail(400, e.getMessage());
        } catch (IllegalStateException e) {
            return ApiResponse.fail(403, e.getMessage());
        }
    }
}
