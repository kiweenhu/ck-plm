/*
 * Copyright (c) 2026~2028 深圳乘恺科技有限公司
 * All rights reserved.
 *
 * @author Kiween.Hu; Roney.Liu
 */

package cn.ck.plm.base.feedback.controller;

import cn.ck.plm.base.feedback.dto.FeedbackReplyRequest;
import cn.ck.plm.base.feedback.dto.FeedbackRequest;
import cn.ck.plm.base.feedback.entity.Feedback;
import cn.ck.plm.base.feedback.service.api.FeedbackService;
import cn.ck.plm.base.util.TenantContext;
import cn.ck.plm.base.util.UserContext;
import cn.ck.plm.iam.dto.ApiResponse;
import cn.ck.plm.iam.entity.User;
import cn.ck.plm.iam.service.api.UserService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 使用反馈接口。
 *
 * <pre>
 * POST /api/feedback                 提交反馈（登录用户）→ { oid }
 * GET  /api/feedback                 反馈列表（平台管理员，可按 status 过滤；不含正文）
 * GET  /api/feedback/unprocessed     未处理条数（平台管理员，角标用）
 * GET  /api/feedback/mine            我提交过的反馈（本人，不含正文）
 * GET  /api/feedback/{oid}           反馈详情（平台管理员 / 提交人本人，含正文）
 * POST /api/feedback/{oid}/reply     回复反馈并发通知给提交人（平台管理员）
 * </pre>
 */
@RestController
@RequestMapping("/api/feedback")
public class FeedbackController {

    private static final Logger log = LoggerFactory.getLogger(FeedbackController.class);

    private final FeedbackService feedbackService;
    private final UserService userService;

    public FeedbackController(FeedbackService feedbackService, UserService userService) {
        this.feedbackService = feedbackService;
        this.userService = userService;
    }

    @PostMapping
    public ApiResponse<Map<String, String>> submit(@RequestBody FeedbackRequest request) {
        try {
            String oid = feedbackService.submit(request);
            Map<String, String> data = new LinkedHashMap<>();
            data.put("oid", oid);
            return ApiResponse.ok(data);
        } catch (IllegalArgumentException e) {
            return ApiResponse.fail(400, e.getMessage());
        } catch (Exception e) {
            log.error("提交使用反馈失败", e);
            return ApiResponse.fail(500, "提交失败，请稍后重试");
        }
    }

    @GetMapping
    public ApiResponse<List<Feedback>> list(@RequestParam(defaultValue = "100") int limit,
                                            @RequestParam(required = false) String status) {
        if (!isPlatformTenant()) {
            return ApiResponse.fail(403, "只有平台管理员可以查看反馈");
        }
        try {
            return ApiResponse.ok(feedbackService.listRecent(limit, status));
        } catch (IllegalArgumentException e) {
            return ApiResponse.fail(400, e.getMessage());
        }
    }

    @GetMapping("/unprocessed")
    public ApiResponse<Integer> unprocessed() {
        if (!isPlatformTenant()) {
            return ApiResponse.fail(403, "只有平台管理员可以查看反馈");
        }
        return ApiResponse.ok(feedbackService.countUnprocessed());
    }

    /** 本人提交过的反馈（页脚「问题反馈 → 我的反馈」），任何登录用户只能看到自己的 */
    @GetMapping("/mine")
    public ApiResponse<List<Feedback>> mine(@RequestParam(defaultValue = "50") int limit) {
        try {
            return ApiResponse.ok(feedbackService.listMine(limit));
        } catch (Exception e) {
            log.error("查询我的反馈失败", e);
            return ApiResponse.fail(500, "查询失败，请稍后重试");
        }
    }

    @GetMapping("/{oid}")
    public ApiResponse<Feedback> detail(@PathVariable String oid) {
        try {
            Feedback feedback = feedbackService.detail(oid);
            // 平台管理员看全部；普通用户只能看自己提交的那条
            if (!isPlatformTenant() && !isReporter(feedback)) {
                return ApiResponse.fail(403, "无权查看该反馈");
            }
            return ApiResponse.ok(feedback);
        } catch (IllegalArgumentException e) {
            return ApiResponse.fail(400, e.getMessage());
        }
    }

    @PostMapping("/{oid}/reply")
    public ApiResponse<Map<String, Object>> reply(@PathVariable String oid,
                                                  @RequestBody FeedbackReplyRequest request) {
        if (!isPlatformTenant()) {
            return ApiResponse.fail(403, "只有平台管理员可以回复反馈");
        }
        try {
            boolean notified = feedbackService.reply(oid, request);
            Map<String, Object> data = new LinkedHashMap<>();
            data.put("oid", oid);
            data.put("notified", notified);
            return ApiResponse.ok(data);
        } catch (IllegalArgumentException e) {
            return ApiResponse.fail(400, e.getMessage());
        } catch (Exception e) {
            log.error("回复使用反馈失败: oid={}", oid, e);
            return ApiResponse.fail(500, "回复失败，请稍后重试");
        }
    }

    /** 反馈是跨租户数据，只有平台租户可见 */
    private boolean isPlatformTenant() {
        return TenantContext.PLATFORM_TENANT_OID.equals(TenantContext.get());
    }

    /** 当前登录用户是不是这条反馈的提交人 */
    private boolean isReporter(Feedback feedback) {
        String username = UserContext.get();
        if (username == null || username.trim().isEmpty()) {
            return false;
        }
        User user = userService.findByUsername(username);
        return user != null && user.getOid() != null && user.getOid().equals(feedback.getReporterOid());
    }
}
