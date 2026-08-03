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
}
