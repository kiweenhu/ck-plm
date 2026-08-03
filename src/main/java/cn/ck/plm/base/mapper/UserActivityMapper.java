/*
 * Copyright (c) 2025 深圳乘恺科技有限公司
 * All rights reserved.
 */
package cn.ck.plm.base.mapper;

import cn.ck.plm.base.entity.UserActivity;
import org.apache.ibatis.annotations.*;

import java.util.List;

/**
 * 用户活动 Mapper —— PostgreSQL 实现。
 */
@Mapper
public interface UserActivityMapper {

    @Insert("INSERT INTO ck_user_activity (oid, user_oid, activity_type, target_name, target_type, target_path, action_desc, operator_ip, user_agent, result, duration_ms, error_message, detail_json, tenant_oid, creator, created_at, updater, updated_at) " +
            "VALUES (#{oid}, #{userOid}, #{activityType}, #{targetName}, #{targetType}, #{targetPath}, #{actionDesc}, #{operatorIp}, #{userAgent}, #{result}, #{durationMs}, #{errorMessage}, #{detailJson}, #{tenantOid}, #{creator}, #{createdAt}, #{updater}, #{updatedAt})")
    void insert(UserActivity activity);

    /**
     * 查指定用户最近 N 条访问记录，去重同一路径只保留最新
     */
    @Select("SELECT DISTINCT ON (target_path) * FROM ck_user_activity " +
            "WHERE user_oid = #{userOid} AND activity_type = 'ACCESS' " +
            "ORDER BY target_path, created_at DESC LIMIT #{limit}")
    @Results(id = "activityResultMap", value = {
            @Result(property = "userOid", column = "user_oid"),
            @Result(property = "activityType", column = "activity_type"),
            @Result(property = "targetName", column = "target_name"),
            @Result(property = "targetType", column = "target_type"),
            @Result(property = "targetPath", column = "target_path"),
            @Result(property = "actionDesc", column = "action_desc"),
            @Result(property = "operatorIp", column = "operator_ip"),
            @Result(property = "userAgent", column = "user_agent"),
            @Result(property = "result", column = "result"),
            @Result(property = "durationMs", column = "duration_ms"),
            @Result(property = "errorMessage", column = "error_message"),
            @Result(property = "detailJson", column = "detail_json"),
            @Result(property = "createdAt", column = "created_at"),
            @Result(property = "updatedAt", column = "updated_at"),
    })
    List<UserActivity> selectRecentAccess(@Param("userOid") String userOid, @Param("limit") int limit);

    /**
     * 查指定用户最近 N 条操作记录（含 LOGIN / LOGOUT / OPERATION）
     */
    @Select("SELECT * FROM ck_user_activity " +
            "WHERE user_oid = #{userOid} AND activity_type IN ('OPERATION','LOGIN','LOGOUT') " +
            "ORDER BY created_at DESC LIMIT #{limit}")
    @ResultMap("activityResultMap")
    List<UserActivity> selectRecentOperations(@Param("userOid") String userOid, @Param("limit") int limit);

    /**
     * 分页查询操作日志（按租户隔离，支持类型、时间范围筛选）
     */
    @Select("<script>" +
            "SELECT * FROM ck_user_activity " +
            "WHERE tenant_oid = #{tenantOid} " +
            "<if test='activityType != null and activityType != \"\"'>" +
            "  AND activity_type = #{activityType} " +
            "</if>" +
            "<if test='startDate != null'>" +
            "  AND created_at &gt;= #{startDate} " +
            "</if>" +
            "<if test='endDate != null'>" +
            "  AND created_at &lt;= #{endDate} " +
            "</if>" +
            "ORDER BY created_at DESC " +
            "LIMIT #{limit} OFFSET #{offset}" +
            "</script>")
    @ResultMap("activityResultMap")
    List<UserActivity> selectActivityLogs(@Param("tenantOid") String tenantOid,
                                          @Param("activityType") String activityType,
                                          @Param("startDate") String startDate,
                                          @Param("endDate") String endDate,
                                          @Param("limit") int limit,
                                          @Param("offset") int offset);

    /**
     * 统计操作日志总数（按租户隔离）
     */
    @Select("<script>" +
            "SELECT COUNT(*) FROM ck_user_activity " +
            "WHERE tenant_oid = #{tenantOid} " +
            "<if test='activityType != null and activityType != \"\"'>" +
            "  AND activity_type = #{activityType} " +
            "</if>" +
            "<if test='startDate != null'>" +
            "  AND created_at &gt;= #{startDate} " +
            "</if>" +
            "<if test='endDate != null'>" +
            "  AND created_at &lt;= #{endDate} " +
            "</if>" +
            "</script>")
    int countActivityLogs(@Param("tenantOid") String tenantOid,
                          @Param("activityType") String activityType,
                          @Param("startDate") String startDate,
                          @Param("endDate") String endDate);
}
