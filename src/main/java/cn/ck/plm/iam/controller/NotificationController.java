/*
 * Copyright (c) 2025 深圳乘恺科技有限公司
 * All rights reserved.
 *
 * @author Kiween.Hu; Roney.Liu
 */

package cn.ck.plm.iam.controller;

import cn.ck.plm.iam.dto.ApiResponse;
import cn.ck.plm.iam.entity.Notification;
import cn.ck.plm.iam.service.api.NotificationService;
import cn.ck.plm.iam.entity.User;
import cn.ck.plm.iam.service.api.UserService;
import cn.ck.plm.iam.security.TokenInfo;
import cn.ck.plm.iam.security.TokenStore;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 通知控制器 —— 铃铛 Badge 及通知列表。
 */
@RestController
@RequestMapping("/api/notifications")
public class NotificationController {

    private final NotificationService notificationService;
    private final UserService userService;
    private final TokenStore tokenStore;

    public NotificationController(NotificationService notificationService,
                                   UserService userService, TokenStore tokenStore) {
        this.notificationService = notificationService;
        this.userService = userService;
        this.tokenStore = tokenStore;
    }

    /** 获取当前用户未读通知数 */
    @GetMapping("/unread-count")
    public ApiResponse<Integer> unreadCount(@RequestHeader("Authorization") String authHeader) {
        String userOid = resolveUserOid(authHeader);
        if (userOid == null) return ApiResponse.fail(401, "未登录");
        return ApiResponse.ok(notificationService.countUnread(userOid));
    }

    /** 获取最近通知列表 */
    @GetMapping
    public ApiResponse<List<Notification>> list(
            @RequestHeader("Authorization") String authHeader,
            @RequestParam(defaultValue = "20") int limit) {
        String userOid = resolveUserOid(authHeader);
        if (userOid == null) return ApiResponse.fail(401, "未登录");
        return ApiResponse.ok(notificationService.getNotifications(userOid, Math.min(limit, 50)));
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
        if (authHeader == null || !authHeader.startsWith("Bearer ")) return null;
        String token = authHeader.substring(7).trim();
        TokenInfo tokenInfo = tokenStore.validate(token);
        if (tokenInfo == null) return null;
        User user = userService.findByUsername(tokenInfo.getUsername());
        return user != null ? user.getOid() : null;
    }
}
