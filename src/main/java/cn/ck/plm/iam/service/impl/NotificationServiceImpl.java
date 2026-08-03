/*
 * Copyright (c) 2025 深圳乘恺科技有限公司
 * All rights reserved.
 *
 * @author Kiween.Hu; Roney.Liu
 */

package cn.ck.plm.iam.service.impl;

import cn.ck.plm.iam.entity.Notification;
import cn.ck.plm.iam.entity.User;
import cn.ck.plm.iam.mapper.NotificationMapper;
import cn.ck.plm.iam.mapper.RoleMapper;
import cn.ck.plm.iam.service.api.NotificationService;
import cn.ck.plm.iam.service.api.UserService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 通知服务实现。
 */
@Service
public class NotificationServiceImpl implements NotificationService {

    private static final Logger log = LoggerFactory.getLogger(NotificationServiceImpl.class);

    /** 平台管理员角色 — 负责接收租户注册等平台级通知 */
    private static final String ADMIN_ROLE_CODE = "PLATFORM_ADMIN";

    private final NotificationMapper notificationMapper;
    private final UserService userService;
    private final RoleMapper roleMapper;

    public NotificationServiceImpl(NotificationMapper notificationMapper,
                                    UserService userService, RoleMapper roleMapper) {
        this.notificationMapper = notificationMapper;
        this.userService = userService;
        this.roleMapper = roleMapper;
    }

    @Override
    public void send(String userOid, String title, String content, String type,
                      String targetType, String targetOid) {
        Notification notif = new Notification();
        notif.setUserOid(userOid);
        notif.setTitle(title);
        notif.setContent(content);
        notif.setType(type);
        notif.setTargetType(targetType);
        notif.setTargetOid(targetOid);
        notificationMapper.insert(notif);
        log.debug("通知已发送: userOid={}, title={}", userOid, title);
    }

    @Override
    public void sendToAdmins(String title, String content, String type,
                              String targetType, String targetOid) {
        cn.ck.plm.iam.entity.Role adminRole = roleMapper.selectByCode(ADMIN_ROLE_CODE);
        if (adminRole == null) {
            log.warn("未找到 PLATFORM_ADMIN 角色，无法发送管理员通知");
            return;
        }
        List<User> admins = userService.findUsersByRoleOid(adminRole.getOid());
        if (admins == null || admins.isEmpty()) {
            log.info("没有 ADMIN 用户，跳过通知发送");
            return;
        }
        for (User admin : admins) {
            send(admin.getOid(), title, content, type, targetType, targetOid);
        }
        log.info("已向 {} 位管理员发送通知: {}", admins.size(), title);
    }

    @Override
    public int countUnread(String userOid) {
        if (userOid == null) return 0;
        return notificationMapper.countUnread(userOid);
    }

    @Override
    public List<Notification> getNotifications(String userOid, int limit) {
        if (userOid == null) return java.util.Collections.emptyList();
        return notificationMapper.selectByUserOid(userOid, limit);
    }

    @Override
    public void markRead(String oid) {
        notificationMapper.markRead(oid);
    }

    @Override
    public void markAllRead(String userOid) {
        if (userOid != null) {
            notificationMapper.markAllRead(userOid);
        }
    }
}
