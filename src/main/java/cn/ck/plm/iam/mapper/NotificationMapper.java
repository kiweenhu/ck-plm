/*
 * Copyright (c) 2025 深圳乘恺科技有限公司
 * All rights reserved.
 *
 * @author Kiween.Hu; Roney.Liu
 */

package cn.ck.plm.iam.mapper;

import cn.ck.plm.iam.entity.Notification;
import java.util.List;

/**
 * 通知数据访问接口。
 */
public interface NotificationMapper {

    int insert(Notification notification);

    int markRead(String oid);

    int markAllRead(String userOid);

    int countUnread(String userOid);

    List<Notification> selectByUserOid(String userOid, int limit);

    /** 只看未读（通知中心"未读"页签用；在库里过滤，避免"取 20 条再筛"导致列表看着少 */
    List<Notification> selectUnreadByUserOid(String userOid, int limit);

    /** 只看某类（如"企业公告"页只看 ANNOUNCEMENT）—— 同样在库里过滤 */
    List<Notification> selectByUserOidAndType(String userOid, String type, int limit);
}
