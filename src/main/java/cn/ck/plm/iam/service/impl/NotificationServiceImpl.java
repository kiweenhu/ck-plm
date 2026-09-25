/*
 * Copyright (c) 2025 深圳乘恺科技有限公司
 * All rights reserved.
 *
 * @author Kiween.Hu; Roney.Liu
 */

package cn.ck.plm.iam.service.impl;

import cn.ck.plm.iam.config.NotificationProperties;
import cn.ck.plm.iam.entity.Notification;
import cn.ck.plm.iam.entity.User;
import cn.ck.plm.iam.mapper.NotificationMapper;
import cn.ck.plm.iam.mapper.RoleMapper;
import cn.ck.plm.iam.service.api.NotificationChannelService;
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
    /** 系统通知模式（plm.notification）：站内信是否启用由它决定，见 {@link #send} */
    private final NotificationChannelService channelService;

    public NotificationServiceImpl(NotificationMapper notificationMapper,
                                   UserService userService, RoleMapper roleMapper,
                                   NotificationChannelService channelService) {
        this.notificationMapper = notificationMapper;
        this.userService = userService;
        this.roleMapper = roleMapper;
        this.channelService = channelService;
    }

    /**
     * 发一条站内通知。<b>系统自动产生的通知走这里</b>（待办 / 转办 / 流程结束 / 管理员提醒）。
     *
     * <p>"站内信是否启用"在<b>这一处</b>判断，而不是各调用方各判一次：通知模式是全系统统一的
     * 配置（{@code plm.notification}），调用方漏判一处就会出现"有人收到、有人收不到"——
     * 这种不一致极难被发现。
     *
     * <p>关掉时静默跳过（只记 debug）：调用方都是流程推进、任务转办这类主链路，
     * 通知不是它们的目的，更不该因为"通知没发出去"把业务动作弄失败。
     *
     * <p>管理员手动"发布公告"不走这里（见 {@link #sendToTenantUsers}）：那是人点的显式动作。
     */
    @Override
    public void send(String userOid, String title, String content, String type,
                     String targetType, String targetOid) {
        if (!channelService.isUsable(NotificationProperties.CHANNEL_CK_PLM)) {
            log.debug("站内通知未启用，跳过发送: userId={}, title={}", userOid, title);
            return;
        }
        insertOne(userOid, title, content, type, targetType, targetOid);
    }

    /**
     * 落库一条通知 —— 唯一的写入点。
     *
     * <p>与 {@link #send} 分开，是为了让"总开关"与"人手动发公告"这两种语义能各自成立
     * （见 {@link #sendToTenantUsers}），而不是靠调用方传一个 boolean 开关进来。
     */
    private void insertOne(String userOid, String title, String content, String type,
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
        return getNotifications(userOid, limit, false);
    }

    @Override
    public List<Notification> getNotifications(String userOid, int limit, boolean unreadOnly) {
        if (userOid == null) return java.util.Collections.emptyList();
        return unreadOnly
                ? notificationMapper.selectUnreadByUserOid(userOid, limit)
                : notificationMapper.selectByUserOid(userOid, limit);
    }

    @Override
    public List<Notification> getByType(String userOid, String type, int limit) {
        if (userOid == null || type == null || type.trim().isEmpty()) {
            return java.util.Collections.emptyList();
        }
        return notificationMapper.selectByUserOidAndType(userOid, type.trim(), limit);
    }

    @Override
    public int sendToTenantUsers(String tenantOid, String title, String content, String type) {
        if (tenantOid == null) return 0;
        List<User> users = userService.findByTenantOid(tenantOid);
        if (users == null || users.isEmpty()) {
            log.info("租户 {} 没有可取的用户，公告未发送: {}", tenantOid, title);
            return 0;
        }
        int sent = 0;
        for (User user : users) {
            // 停用/锁定账号不打扰（消息对他们也没有意义）
            if (user.isEnabled() == false || user.isLocked()) {
                continue;
            }
            // 直接落库，不走 send 的"通知总开关"：公告是管理员亲手发的显式广播，
            // 点了"发布"却一条都不发（还照样报"送达 N 人"）比不发更糟
            insertOne(user.getOid(), title, content, type, null, null);
            sent++;
        }
        log.info("公告已发送: tenant={} 送达 {} 人, title={}", tenantOid, sent, title);
        return sent;
    }

    @Override
    public boolean isAdmin(String userOid) {
        if (userOid == null) return false;
        // ADMIN 是历史角色编码（库里不一定有），一起认下来，避免老账号发不了公告
        for (String code : new String[]{"TENANT_ADMIN", "PLATFORM_ADMIN", "ADMIN"}) {
            cn.ck.plm.iam.entity.Role role = roleMapper.selectByCode(code);
            if (role == null) continue;
            List<User> members = userService.findUsersByRoleOid(role.getOid());
            if (members == null) continue;
            for (User member : members) {
                if (userOid.equals(member.getOid())) return true;
            }
        }
        return false;
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
