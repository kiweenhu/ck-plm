/*
 * Copyright (c) 2025 深圳乘恺科技有限公司
 * All rights reserved.
 *
 * @author Kiween.Hu; Roney.Liu
 */

package cn.ck.plm.iam.mapper.impl.postgresql;

import cn.ck.plm.iam.entity.Notification;
import cn.ck.plm.iam.mapper.NotificationMapper;
import org.apache.ibatis.annotations.*;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;

import java.util.List;

@Mapper
@ConditionalOnProperty(name = "plm.database.type", havingValue = "postgresql", matchIfMissing = true)
public interface PostgreSqlNotificationMapper extends NotificationMapper {

    @Override
    @Insert("INSERT INTO ck_notification (oid, user_oid, title, content, type, target_type, target_oid, is_read, "
            + "creator, created_at, updater, updated_at) "
            + "VALUES (#{oid}, #{userOid}, #{title}, #{content}, #{type}, #{targetType}, #{targetOid}, #{isRead}, "
            + "#{creator}, #{createdAt}, #{updater}, #{updatedAt})")
    int insert(Notification notification);

    @Override
    @Update("UPDATE ck_notification SET is_read = true, updated_at = CURRENT_TIMESTAMP WHERE oid = #{oid}")
    int markRead(@Param("oid") String oid);

    @Override
    @Update("UPDATE ck_notification SET is_read = true, updated_at = CURRENT_TIMESTAMP WHERE user_oid = #{userOid} AND is_read = false")
    int markAllRead(@Param("userOid") String userOid);

    @Override
    @Select("SELECT COUNT(*) FROM ck_notification WHERE user_oid = #{userOid} AND is_read = false")
    int countUnread(@Param("userOid") String userOid);

    @Override
    @Select("SELECT oid, user_oid, title, content, type, target_type, target_oid, is_read, "
            + "creator, created_at, updater, updated_at "
            + "FROM ck_notification WHERE user_oid = #{userOid} "
            + "ORDER BY created_at DESC LIMIT #{limit}")
    @Results(id = "notificationResult", value = {
            @Result(property = "oid",        column = "oid"),
            @Result(property = "userOid",    column = "user_oid"),
            @Result(property = "title",      column = "title"),
            @Result(property = "content",    column = "content"),
            @Result(property = "type",       column = "type"),
            @Result(property = "targetType", column = "target_type"),
            @Result(property = "targetOid",  column = "target_oid"),
            @Result(property = "isRead",     column = "is_read"),
            @Result(property = "creator",    column = "creator"),
            @Result(property = "createdAt",  column = "created_at"),
            @Result(property = "updater",    column = "updater"),
            @Result(property = "updatedAt",  column = "updated_at")
    })
    List<Notification> selectByUserOid(@Param("userOid") String userOid, @Param("limit") int limit);
}
