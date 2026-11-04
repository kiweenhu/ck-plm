/*
 * Copyright (c) 2026~2028 深圳乘恺科技有限公司
 * All rights reserved.
 */

package cn.ck.plm.change.mapper.impl.postgresql;

import cn.ck.plm.change.entity.ChangeTask;
import cn.ck.plm.change.mapper.ChangeTaskMapper;
import org.apache.ibatis.annotations.*;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;

import java.util.List;
import java.util.Map;

/**
 * {@link ChangeTaskMapper} 的 PostgreSQL 实现。
 */
@Mapper
@ConditionalOnProperty(name = "plm.database.type", havingValue = "postgresql", matchIfMissing = true)
public interface PostgreSqlChangeTaskMapper extends ChangeTaskMapper {

    String COLUMNS = "oid, code, name, description, notice_oid, task_type, assignee, status, "
            + "plan_start, plan_end, completed_at, tenant_oid, creator, created_at, updater, updated_at";

    @Override
    @Insert("INSERT INTO ck_change_task (" + COLUMNS + ") VALUES (" +
            "#{oid}, #{code}, #{name}, #{description}, #{noticeOid}, #{taskType}, #{assignee}, #{status}, " +
            "#{planStart}, #{planEnd}, #{completedAt}, " +
            "#{tenantOid}, #{creator}, #{createdAt}, #{updater}, #{updatedAt})")
    int insert(ChangeTask task);

    @Override
    @Update("UPDATE ck_change_task SET name = #{name}, description = #{description}, " +
            "task_type = #{taskType}, assignee = #{assignee}, status = #{status}, " +
            "plan_start = #{planStart}, plan_end = #{planEnd}, completed_at = #{completedAt}, " +
            "updater = #{updater}, updated_at = #{updatedAt} " +
            "WHERE oid = #{oid}")
    int update(ChangeTask task);

    @Override
    @Delete("DELETE FROM ck_change_task WHERE oid = #{oid}")
    int deleteByOid(@Param("oid") String oid);

    @Override
    @Select("SELECT " + COLUMNS + " FROM ck_change_task WHERE oid = #{oid}")
    @Results(id = "changeTaskResult", value = {
            @Result(property = "oid",         column = "oid"),
            @Result(property = "code",        column = "code"),
            @Result(property = "name",        column = "name"),
            @Result(property = "description", column = "description"),
            @Result(property = "noticeOid",   column = "notice_oid"),
            @Result(property = "taskType",    column = "task_type"),
            @Result(property = "assignee",    column = "assignee"),
            @Result(property = "status",      column = "status"),
            @Result(property = "planStart",   column = "plan_start"),
            @Result(property = "planEnd",     column = "plan_end"),
            @Result(property = "completedAt", column = "completed_at"),
            @Result(property = "tenantOid",   column = "tenant_oid"),
            @Result(property = "creator",     column = "creator"),
            @Result(property = "createdAt",   column = "created_at"),
            @Result(property = "updater",     column = "updater"),
            @Result(property = "updatedAt",   column = "updated_at")
    })
    ChangeTask selectByOid(@Param("oid") String oid);

    @Override
    @Select("SELECT " + COLUMNS + " FROM ck_change_task WHERE notice_oid = #{noticeOid} " +
            "ORDER BY created_at ASC")
    @ResultMap("changeTaskResult")
    List<ChangeTask> selectByNotice(@Param("noticeOid") String noticeOid);

    /** 任务中心列：额外带出所属通告编号（响应装配，不入库） */
    String MINE_COLUMNS = "t.oid, t.code, t.name, t.description, t.notice_oid, t.task_type, t.assignee, t.status, "
            + "t.plan_start, t.plan_end, t.completed_at, t.tenant_oid, t.creator, t.created_at, t.updater, "
            + "t.updated_at, n.code AS notice_code";

    @Override
    @Select("<script>" +
            "SELECT " + MINE_COLUMNS + " FROM ck_change_task t " +
            "LEFT JOIN ck_change_notice n ON n.oid = t.notice_oid AND n.tenant_oid = t.tenant_oid " +
            "WHERE t.tenant_oid = #{tenantOid} " +
            "<if test='assignee != null and assignee != \"\"'>  AND t.assignee = #{assignee} </if>" +
            "<if test='status != null and status != \"\"'>  AND t.status = #{status} </if>" +
            // 未派发的草稿不是待办：任务中心只列"已派给我的活"，清单去通告详情看
            "<if test='status == null or status == \"\"'>  AND t.status != 'DRAFT' </if>" +
            "<if test='keyword != null and keyword != \"\"'>" +
            "  AND (t.code ILIKE '%' || #{keyword} || '%' OR t.name ILIKE '%' || #{keyword} || '%') " +
            "</if>" +
            "ORDER BY CASE t.status WHEN 'OPEN' THEN 1 WHEN 'IN_WORK' THEN 2 WHEN 'SUBMITTED' THEN 3 " +
            "         ELSE 4 END, t.plan_end NULLS LAST, t.created_at DESC " +
            "LIMIT #{limit} OFFSET #{offset}" +
            "</script>")
    @Results(id = "changeTaskMineResult", value = {
            @Result(property = "oid",         column = "oid"),
            @Result(property = "code",        column = "code"),
            @Result(property = "name",        column = "name"),
            @Result(property = "description", column = "description"),
            @Result(property = "noticeOid",   column = "notice_oid"),
            @Result(property = "noticeCode",  column = "notice_code"),
            @Result(property = "taskType",    column = "task_type"),
            @Result(property = "assignee",    column = "assignee"),
            @Result(property = "status",      column = "status"),
            @Result(property = "planStart",   column = "plan_start"),
            @Result(property = "planEnd",     column = "plan_end"),
            @Result(property = "completedAt", column = "completed_at"),
            @Result(property = "tenantOid",   column = "tenant_oid"),
            @Result(property = "creator",     column = "creator"),
            @Result(property = "createdAt",   column = "created_at"),
            @Result(property = "updater",     column = "updater"),
            @Result(property = "updatedAt",   column = "updated_at")
    })
    List<ChangeTask> selectMine(@Param("tenantOid") String tenantOid,
                                @Param("assignee") String assignee,
                                @Param("status") String status,
                                @Param("keyword") String keyword,
                                @Param("limit") int limit,
                                @Param("offset") int offset);

    @Override
    @Select("<script>" +
            "SELECT COUNT(*) FROM ck_change_task WHERE tenant_oid = #{tenantOid} " +
            "<if test='assignee != null and assignee != \"\"'>  AND assignee = #{assignee} </if>" +
            "<if test='status != null and status != \"\"'>  AND status = #{status} </if>" +
            "<if test='status == null or status == \"\"'>  AND status != 'DRAFT' </if>" +
            "<if test='keyword != null and keyword != \"\"'>" +
            "  AND (code ILIKE '%' || #{keyword} || '%' OR name ILIKE '%' || #{keyword} || '%') " +
            "</if>" +
            "</script>")
    int countMine(@Param("tenantOid") String tenantOid,
                  @Param("assignee") String assignee,
                  @Param("status") String status,
                  @Param("keyword") String keyword);

    @Override
    @Select("<script>" +
            "SELECT status, COUNT(*) AS cnt FROM ck_change_task WHERE tenant_oid = #{tenantOid} " +
            "<if test='assignee != null and assignee != \"\"'>  AND assignee = #{assignee} </if>" +
            // 与列表同口径：草稿不参与"我的任务"的状态概览
            "  AND status != 'DRAFT' " +
            "GROUP BY status" +
            "</script>")
    List<Map<String, Object>> countByStatus(@Param("tenantOid") String tenantOid,
                                           @Param("assignee") String assignee);

    @Override
    @Select("SELECT COALESCE(MAX(SUBSTRING(code FROM 5)::INTEGER), 0) FROM ck_change_task " +
            "WHERE tenant_oid = #{tenantOid} AND code ~ '^ECT-[0-9]+$'")
    int maxCodeSeq(@Param("tenantOid") String tenantOid);

    /** 未完成的子任务。不设 LIMIT：截断会让"还有几个没关"的判断失真 */
    @Override
    @Select("SELECT " + COLUMNS + " FROM ck_change_task " +
            "WHERE notice_oid = #{noticeOid} AND status <> 'COMPLETED' " +
            "ORDER BY code")
    @ResultMap("changeTaskResult")
    List<ChangeTask> selectOpenByNotice(@Param("noticeOid") String noticeOid);
}
