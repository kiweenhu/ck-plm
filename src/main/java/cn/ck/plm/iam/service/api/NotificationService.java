/*
 * Copyright (c) 2025 深圳乘恺科技有限公司
 * All rights reserved.
 *
 * @author Kiween.Hu; Roney.Liu
 */

package cn.ck.plm.iam.service.api;

import cn.ck.plm.iam.entity.Notification;
import java.util.List;

/**
 * 通知服务接口。
 */
public interface NotificationService {

    /** 向指定用户发送通知 */
    void send(String userOid, String title, String content, String type, String targetType, String targetOid);

    /** 向所有 ADMIN 角色用户发送通知 */
    void sendToAdmins(String title, String content, String type, String targetType, String targetOid);

    /** 获取用户未读通知数 */
    int countUnread(String userOid);

    /** 获取用户最近通知列表 */
    List<Notification> getNotifications(String userOid, int limit);

    /** 标记单条通知已读 */
    void markRead(String oid);

    /** 标记全部已读 */
    void markAllRead(String userOid);
}
