/*
 * Copyright (c) 2025 深圳乘恺科技有限公司
 * All rights reserved.
 *
 * @author Kiween.Hu; Roney.Liu
 */

package cn.ck.plm.process.controller;

import cn.ck.plm.iam.dto.ApiResponse;
import cn.ck.plm.process.dto.ProcessCommentVO;
import cn.ck.plm.process.dto.ProcessTaskContextVO;
import cn.ck.plm.process.dto.ProcessTaskFormVO;
import cn.ck.plm.process.dto.ProcessTaskStatsVO;
import cn.ck.plm.process.dto.ProcessTaskVO;
import cn.ck.plm.process.service.api.ProcessService;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 流程任务中心 REST API —— 对应前端「任务中心」页面。
 *
 * <pre>
 * GET    /api/workflow/task/{id}           单个在办任务（办理页冷启动用；已办/不存在 → 404）
 * GET    /api/workflow/task/todo           待办（指派给我且未完成）
 * GET    /api/workflow/task/claimable      可认领（候选人为我或我的角色组，未签收）
 * GET    /api/workflow/task/done           已办（我已完成的历史任务）
 * GET    /api/workflow/task/stats          统计（待办/可认领/逾期/已办）
 * GET    /api/workflow/task/{id}/form      办理表单上下文（节点 formKey + 活动 id + 该版 DSL）
 * POST   /api/workflow/task/{id}/claim     认领
 * POST   /api/workflow/task/{id}/complete  办理 {"action":"approve|reject","comment":"...",
 *                                                "variables":{ 节点表单填的流程变量 }}
 * POST   /api/workflow/task/{id}/delegate  委派 {"targetAssignee":"username"}
 * POST   /api/workflow/task/{id}/transfer  转办 {"targetAssignee":"username"}
 * GET    /api/workflow/task/{id}/comment   评论列表
 * POST   /api/workflow/task/{id}/comment   添加评论 {"comment":"..."}
 * </pre>
 *
 * <p>租户与用户身份由 {@code AuthInterceptor} 注入的上下文提供，此处无需显式传参。
 */
@RestController
@RequestMapping("/api/workflow/task")
public class ProcessTaskController {

    private final ProcessService workflowService;

    public ProcessTaskController(ProcessService workflowService) {
        this.workflowService = workflowService;
    }

    /**
     * 单个在办任务 —— 办理页（独立窗口）冷启动时用它拿上下文。
     *
     * <p>任务已被处理或不存在时返回 404 + 一句可读说明：两个人同时打开同一任务是正常的，
     * 后到的人应该看到"这个任务已经办过了"，而不是一堆报错。
     */
    @GetMapping("/{id}")
    public ApiResponse<ProcessTaskVO> detail(@PathVariable String id) {
        try {
            ProcessTaskVO vo = workflowService.findTaskById(id);
            return vo == null ? ApiResponse.fail(404, "任务不存在或已被办理") : ApiResponse.ok(vo);
        } catch (Exception e) {
            return ApiResponse.fail(500, "查询任务失败: " + e.getMessage());
        }
    }

    /**
     * 任务办理页的渲染上下文 —— 「通用信息 + 任务表单 + 完整进度」一次给齐。
     *
     * <pre>
     * GET /api/workflow/task/{id}/context
     * → { task, process, entities[], form, activities[] }
     * </pre>
     *
     * <p>为什么聚合：办理页是冷启动的独立窗口，只有任务 id；让前端自己串行拼 4~5 个请求，
     * 每跳都可能失败，页面就得处理各种"半加载"，同一个"当前节点"还可能两处算法不一致。
     */
    @GetMapping("/{id}/context")
    public ApiResponse<ProcessTaskContextVO> context(@PathVariable String id) {
        try {
            ProcessTaskContextVO context = workflowService.findTaskContext(id);
            return context == null ? ApiResponse.fail(404, "任务不存在或已被办理") : ApiResponse.ok(context);
        } catch (Exception e) {
            return ApiResponse.fail(500, "查询任务上下文失败: " + e.getMessage());
        }
    }

    /** 待办任务 */
    @GetMapping("/todo")
    public ApiResponse<List<ProcessTaskVO>> todo(@RequestParam(defaultValue = "1") int page,
                                                  @RequestParam(defaultValue = "10") int size) {
        try {
            return ApiResponse.ok(workflowService.findTodoTasks(page, size));
        } catch (Exception e) {
            return ApiResponse.fail(500, "查询待办任务失败: " + e.getMessage());
        }
    }

    /** 可认领任务 */
    @GetMapping("/claimable")
    public ApiResponse<List<ProcessTaskVO>> claimable(@RequestParam(defaultValue = "1") int page,
                                                       @RequestParam(defaultValue = "10") int size) {
        try {
            return ApiResponse.ok(workflowService.findClaimableTasks(page, size));
        } catch (Exception e) {
            return ApiResponse.fail(500, "查询可认领任务失败: " + e.getMessage());
        }
    }

    /** 已办任务 */
    @GetMapping("/done")
    public ApiResponse<List<ProcessTaskVO>> done(@RequestParam(defaultValue = "1") int page,
                                                  @RequestParam(defaultValue = "10") int size) {
        try {
            return ApiResponse.ok(workflowService.findDoneTasks(page, size));
        } catch (Exception e) {
            return ApiResponse.fail(500, "查询已办任务失败: " + e.getMessage());
        }
    }

    /** 任务统计 */
    @GetMapping("/stats")
    public ApiResponse<ProcessTaskStatsVO> stats() {
        try {
            return ApiResponse.ok(workflowService.taskStats());
        } catch (Exception e) {
            return ApiResponse.fail(500, "查询任务统计失败: " + e.getMessage());
        }
    }

    /** 认领任务 */
    @PostMapping("/{id}/claim")
    public ApiResponse<Void> claim(@PathVariable String id) {
        try {
            workflowService.claimTask(id);
            return ApiResponse.ok();
        } catch (IllegalStateException | IllegalArgumentException e) {
            return ApiResponse.fail(400, e.getMessage());
        } catch (Exception e) {
            return ApiResponse.fail(500, "认领失败: " + e.getMessage());
        }
    }

    /** 办理任务（同意 / 驳回 + 审批意见；{@code variables} 为节点表单填的流程变量） */
    @PostMapping("/{id}/complete")
    public ApiResponse<Void> complete(@PathVariable String id, @RequestBody(required = false) Map<String, Object> body) {
        try {
            String action = str(body, "action");
            String comment = str(body, "comment");
            Object raw = body == null ? null : body.get("variables");
            @SuppressWarnings("unchecked")
            Map<String, Object> variables = (raw instanceof Map) ? (Map<String, Object>) raw : null;
            workflowService.completeTask(id, action, comment, variables);
            return ApiResponse.ok();
        } catch (IllegalStateException | IllegalArgumentException e) {
            return ApiResponse.fail(400, e.getMessage());
        } catch (Exception e) {
            return ApiResponse.fail(500, "办理失败: " + e.getMessage());
        }
    }

    /**
     * 任务办理表单上下文 —— 该节点配的表单是什么（{@code formKey}）、活动 id，以及
     * <b>该实例所用那一版</b>流程模板的 DSL（表单内容由流程派生时需要）。
     *
     * <p>办理弹框据此渲染节点表单；{@code formKey} 为空时退化为通用表单。
     */
    @GetMapping("/{id}/form")
    public ApiResponse<ProcessTaskFormVO> form(@PathVariable String id) {
        try {
            return ApiResponse.ok(workflowService.taskForm(id));
        } catch (IllegalArgumentException e) {
            return ApiResponse.fail(404, e.getMessage());
        } catch (Exception e) {
            return ApiResponse.fail(500, "查询任务表单失败: " + e.getMessage());
        }
    }

    /** 委派任务 */
    @PostMapping("/{id}/delegate")
    public ApiResponse<Void> delegate(@PathVariable String id, @RequestBody Map<String, Object> body) {
        try {
            workflowService.delegateTask(id, str(body, "targetAssignee"));
            return ApiResponse.ok();
        } catch (IllegalStateException | IllegalArgumentException e) {
            return ApiResponse.fail(400, e.getMessage());
        } catch (Exception e) {
            return ApiResponse.fail(500, "委派失败: " + e.getMessage());
        }
    }

    /** 转办任务 */
    @PostMapping("/{id}/transfer")
    public ApiResponse<Void> transfer(@PathVariable String id, @RequestBody Map<String, Object> body) {
        try {
            workflowService.transferTask(id, str(body, "targetAssignee"));
            return ApiResponse.ok();
        } catch (IllegalStateException | IllegalArgumentException e) {
            return ApiResponse.fail(400, e.getMessage());
        } catch (Exception e) {
            return ApiResponse.fail(500, "转办失败: " + e.getMessage());
        }
    }

    /** 任务评论列表 */
    @GetMapping("/{id}/comment")
    public ApiResponse<List<ProcessCommentVO>> comments(@PathVariable String id) {
        try {
            return ApiResponse.ok(workflowService.findTaskComments(id));
        } catch (IllegalArgumentException e) {
            return ApiResponse.fail(404, e.getMessage());
        } catch (Exception e) {
            return ApiResponse.fail(500, "查询评论失败: " + e.getMessage());
        }
    }

    /** 添加评论 */
    @PostMapping("/{id}/comment")
    public ApiResponse<Void> addComment(@PathVariable String id, @RequestBody Map<String, Object> body) {
        try {
            workflowService.addTaskComment(id, str(body, "comment"));
            return ApiResponse.ok();
        } catch (IllegalArgumentException e) {
            return ApiResponse.fail(400, e.getMessage());
        } catch (Exception e) {
            return ApiResponse.fail(500, "添加评论失败: " + e.getMessage());
        }
    }

    private String str(Map<String, Object> body, String key) {
        if (body == null) return null;
        Object v = body.get(key);
        return v == null ? null : v.toString();
    }
}
