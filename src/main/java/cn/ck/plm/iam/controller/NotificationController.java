/*
 * Copyright (c) 2025 深圳乘恺科技有限公司
 * All rights reserved.
 *
 * @author Kiween.Hu; Roney.Liu
 */

package cn.ck.plm.iam.controller;

import cn.ck.plm.iam.dto.ApiResponse;
import cn.ck.plm.iam.dto.NotificationChannelVO;
import cn.ck.plm.iam.entity.Notification;
import cn.ck.plm.iam.service.api.NotificationChannelService;
import cn.ck.plm.iam.service.api.NotificationService;
import cn.ck.plm.iam.entity.User;
import cn.ck.plm.iam.service.api.UserService;
import cn.ck.plm.iam.security.TokenInfo;
import cn.ck.plm.iam.security.TokenStore;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 通知控制器 —— 铃铛 Badge 及通知列表。
 */
@RestController
@RequestMapping("/api/notifications")
public class NotificationController {

    private final NotificationService notificationService;
    private final UserService userService;
    private final TokenStore tokenStore;
    /** 系统通知模式（plm.notification）：渠道与"能不能用"由它给出，见 /channels */
    private final NotificationChannelService channelService;

    public NotificationController(NotificationService notificationService,
                                  UserService userService, TokenStore tokenStore,
                                  NotificationChannelService channelService) {
        this.notificationService = notificationService;
        this.userService = userService;
        this.tokenStore = tokenStore;
        this.channelService = channelService;
    }

    /** 获取当前用户未读通知数 */
    @GetMapping("/unread-count")
    public ApiResponse<Integer> unreadCount(@RequestHeader("Authorization") String authHeader) {
        String userOid = resolveUserOid(authHeader);
        if (userOid == null) return ApiResponse.fail(401, "未登录");
        return ApiResponse.ok(notificationService.countUnread(userOid));
    }

    /**
     * 系统当前的通知模式 —— 支持哪些渠道、哪些真的能用、不能用的缺什么。
     *
     * <pre>
     * GET /api/notifications/channels
     * </pre>
     *
     * <p>渠道与凭据是<b>系统级配置</b>（{@code plm.notification}，见 application.yml），
     * 但配置的结果得让界面看得见：流程设计器只能从能用的渠道里挑，
     * 管理员也要能一眼看到"邮件为什么没发出去"（缺哪个配置项）。
     */
    @GetMapping("/channels")
    public ApiResponse<List<NotificationChannelVO>> channels(@RequestHeader("Authorization") String authHeader) {
        if (resolveUser(authHeader) == null) return ApiResponse.fail(401, "未登录");
        return ApiResponse.ok(channelService.channelViews());
    }

    /**
     * 获取通知列表。
     *
     * <pre>
     * GET /api/notifications?limit=20&amp;unreadOnly=false&amp;type=ANNOUNCEMENT
     * </pre>
     *
     * @param unreadOnly 只看未读（通知中心"未读"页签）
     * @param type       只看某一类（如「企业公告」页要 ANNOUNCEMENT）；不传则全部类型
     */
    @GetMapping
    public ApiResponse<List<Notification>> list(
            @RequestHeader("Authorization") String authHeader,
            @RequestParam(defaultValue = "20") int limit,
            @RequestParam(defaultValue = "false") boolean unreadOnly,
            @RequestParam(required = false) String type) {
        User user = resolveUser(authHeader);
        if (user == null) return ApiResponse.fail(401, "未登录");
        int size = Math.min(Math.max(limit, 1), 100);
        if (type != null && !type.trim().isEmpty()) {
            return ApiResponse.ok(notificationService.getByType(user.getOid(), type.trim(), size));
        }
        return ApiResponse.ok(notificationService.getNotifications(user.getOid(), size, unreadOnly));
    }

    /**
     * 发布公告 —— 发给<b>发布人所在租户的全部启用用户</b>。
     *
     * <pre>
     * POST /api/notifications/announcement  { "title": "...", "content": "..." }
     * </pre>
     *
     * <p>权限在<b>服务端</b>校验（只有管理员能发）：前端隐藏按钮只是体验，不是权限。
     * 按租户发而不是全局发 —— 公告是"这家的通知"，平台级公告由平台租户的管理员发布。
     *
     * @return 实际送达人数
     */
    @PostMapping("/announcement")
    public ApiResponse<Integer> publishAnnouncement(@RequestHeader("Authorization") String authHeader,
                                                    @RequestBody Map<String, Object> body) {
        User user = resolveUser(authHeader);
        if (user == null) return ApiResponse.fail(401, "未登录");
        if (!notificationService.isAdmin(user.getOid())) {
            return ApiResponse.fail(403, "只有租户管理员 / 平台管理员可以发布公告");
        }
        String title = str(body, "title");
        if (title == null || title.isEmpty()) {
            return ApiResponse.fail(400, "公告标题不能为空");
        }
        String content = str(body, "content");
        int sent = notificationService.sendToTenantUsers(user.getTenantOid(),
                title, content == null ? "" : content, "ANNOUNCEMENT");
        return ApiResponse.ok(sent);
    }

    /** 标记已读 */
    @PutMapping("/{oid}/read")
    public ApiResponse<Void> markRead(@PathVariable String oid) {
        notificationService.markRead(oid);
        return ApiResponse.ok();
    }

    /** 全部已读 */
    @PutMapping("/read-all")
    public ApiResponse<Void> markAllRead(@RequestHeader("Authorization") String authHeader) {
        String userOid = resolveUserOid(authHeader);
        if (userOid != null) {
            notificationService.markAllRead(userOid);
        }
        return ApiResponse.ok();
    }

    private String resolveUserOid(String authHeader) {
        User user = resolveUser(authHeader);
        return user != null ? user.getOid() : null;
    }

    /**
     * 解析当前用户（不只是 oid —— 发公告要拿他的租户）。
     *
     * <p>通知表是<b>共享表</b>（不按租户隔离），所以本控制器历来自己解 token，
     * 不依赖请求上下文里的租户；这里保持同一套路，避免"发公告时租户上下文缺席"的坑。
     */
    private User resolveUser(String authHeader) {
        if (authHeader == null || !authHeader.startsWith("Bearer ")) return null;
        String token = authHeader.substring(7).trim();
        TokenInfo tokenInfo = tokenStore.validate(token);
        if (tokenInfo == null) return null;
        return userService.findByUsername(tokenInfo.getUsername());
    }

    private String str(Map<String, Object> body, String key) {
        if (body == null) return null;
        Object value = body.get(key);
        return value == null ? null : value.toString().trim();
    }
}
