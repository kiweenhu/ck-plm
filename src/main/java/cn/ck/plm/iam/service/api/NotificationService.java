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

    /**
     * 向指定用户发送通知（系统自动产生的通知走这里：待办 / 转办 / 流程结束 …）。
     *
     * <p>受系统通知模式控制：{@code plm.notification} 里没启用站内渠道时静默跳过
     * （只记一条 debug）—— 调用方都是主链路，不该因为通知发不出去而失败。
     */
    void send(String userOid, String title, String content, String type, String targetType, String targetOid);

    /** 向所有 ADMIN 角色用户发送通知 */
    void sendToAdmins(String title, String content, String type, String targetType, String targetOid);

    /** 获取用户未读通知数 */
    int countUnread(String userOid);

    /** 获取用户最近通知列表 */
    List<Notification> getNotifications(String userOid, int limit);

    /**
     * 通知列表。
     *
     * @param unreadOnly 只看未读（在库里过滤，不是取一批再筛 —— 否则"未读"页签条数会忽多忽少）
     */
    List<Notification> getNotifications(String userOid, int limit, boolean unreadOnly);

    /**
     * 给某租户的<b>全部启用用户</b>发同一条通知（公告即走这条路）。
     *
     * <p>按用户逐行落库（与已有的"每用户一行 + is_read"模型一致），因此已读/未读、
     * 铃铛计数、单条标记已读这些既有机能全部沿用，不必再为公告另立一套已读表。
     * 代价是用户数 × 公告数 的行数 —— 当前量级完全够用。
     *
     * <p><b>不受</b>通知总开关（{@code plm.notification.enabled}）影响：公告是管理员亲手点的
     * 显式广播，点了"发布"却一条都不发（却照样报"送达 N 人"）比不发更糟。
     *
     * @return 实际送达人数
     */
    int sendToTenantUsers(String tenantOid, String title, String content, String type);

    /** 当前用户是否有资格发布公告（租户管理员 / 平台管理员） */
    boolean isAdmin(String userOid);

    /**
     * 只看某一类通知（如「企业公告」页只要 ANNOUNCEMENT）。
     *
     * <p>在库里按 type 过滤，不在 Java 里筛 —— 否则"取 50 条再挑公告"会漏掉更早的公告。
     */
    List<Notification> getByType(String userOid, String type, int limit);

    /** 标记单条通知已读 */
    void markRead(String oid);

    /** 标记全部已读 */
    void markAllRead(String userOid);
}
