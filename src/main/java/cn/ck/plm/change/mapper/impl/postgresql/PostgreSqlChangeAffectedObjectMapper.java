/*
 * Copyright (c) 2026~2028 深圳乘恺科技有限公司
 * All rights reserved.
 */

package cn.ck.plm.change.mapper.impl.postgresql;

import cn.ck.plm.change.entity.ChangeAffectedObject;
import cn.ck.plm.change.mapper.ChangeAffectedObjectMapper;
import org.apache.ibatis.annotations.*;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;

import java.util.List;
import java.util.Map;

/**
 * {@link ChangeAffectedObjectMapper} 的 PostgreSQL 实现。
 *
 * <p>{@link #lockByNotice} 依赖部分唯一索引 {@code uk_change_affected_lock}：
 * 同一 {@code (entity_type, entity_oid)} 只能有一行 {@code locked = TRUE}。
 * 冲突时 PostgreSQL 抛唯一约束异常，由服务层翻译成"已被 ECN-xxxx 占用"。
 */
@Mapper
@ConditionalOnProperty(name = "plm.database.type", havingValue = "postgresql", matchIfMissing = true)
public interface PostgreSqlChangeAffectedObjectMapper extends ChangeAffectedObjectMapper {

    String COLUMNS = "oid, notice_oid, task_oid, entity_type, entity_oid, entity_code, entity_name, "
            + "container_oid, base_iteration_oid, base_version, result_iteration_oid, result_version, "
            + "executed_at, executed_by, locked, note, "
            + "tenant_oid, creator, created_at, updater, updated_at";

    @Override
    @Insert("INSERT INTO ck_change_affected_object (" + COLUMNS + ") VALUES (" +
            "#{oid}, #{noticeOid}, #{taskOid}, #{entityType}, #{entityOid}, #{entityCode}, #{entityName}, " +
            "#{containerOid}, #{baseIterationOid}, #{baseVersion}, #{resultIterationOid}, #{resultVersion}, " +
            "#{executedAt}, #{executedBy}, #{locked}, #{note}, " +
            "#{tenantOid}, #{creator}, #{createdAt}, #{updater}, #{updatedAt})")
    int insert(ChangeAffectedObject affected);

    @Override
    @Update("UPDATE ck_change_affected_object SET task_oid = #{taskOid}, " +
            "entity_code = #{entityCode}, entity_name = #{entityName}, container_oid = #{containerOid}, " +
            "base_iteration_oid = #{baseIterationOid}, base_version = #{baseVersion}, " +
            "result_iteration_oid = #{resultIterationOid}, result_version = #{resultVersion}, " +
            "executed_at = #{executedAt}, executed_by = #{executedBy}, locked = #{locked}, note = #{note}, " +
            "updater = #{updater}, updated_at = #{updatedAt} " +
            "WHERE oid = #{oid}")
    int update(ChangeAffectedObject affected);

    @Override
    @Delete("DELETE FROM ck_change_affected_object WHERE oid = #{oid}")
    int deleteByOid(@Param("oid") String oid);

    @Override
    @Select("SELECT " + COLUMNS + " FROM ck_change_affected_object WHERE oid = #{oid}")
    @Results(id = "changeAffectedResult", value = {
            @Result(property = "oid",                column = "oid"),
            @Result(property = "noticeOid",          column = "notice_oid"),
            @Result(property = "taskOid",            column = "task_oid"),
            @Result(property = "entityType",         column = "entity_type"),
            @Result(property = "entityOid",          column = "entity_oid"),
            @Result(property = "entityCode",         column = "entity_code"),
            @Result(property = "entityName",         column = "entity_name"),
            @Result(property = "containerOid",       column = "container_oid"),
            @Result(property = "containerName",      column = "container_name"),
            @Result(property = "baseIterationOid",   column = "base_iteration_oid"),
            @Result(property = "baseVersion",        column = "base_version"),
            @Result(property = "resultIterationOid", column = "result_iteration_oid"),
            @Result(property = "resultVersion",      column = "result_version"),
            @Result(property = "executedAt",         column = "executed_at"),
            @Result(property = "executedBy",         column = "executed_by"),
            @Result(property = "locked",             column = "locked"),
            @Result(property = "note",               column = "note"),
            @Result(property = "tenantOid",          column = "tenant_oid"),
            @Result(property = "creator",            column = "creator"),
            @Result(property = "createdAt",          column = "created_at"),
            @Result(property = "updater",            column = "updater"),
            @Result(property = "updatedAt",          column = "updated_at")
    })
    ChangeAffectedObject selectByOid(@Param("oid") String oid);

    /**
     * 通告下的受影响对象（带容器名）。
     *
     * <p>容器名不落库：三个 LEFT JOIN + COALESCE 取出，容器被删时自然为 NULL。
     */
    @Override
    @Select("SELECT a.oid, a.notice_oid, a.task_oid, a.entity_type, a.entity_oid, a.entity_code, a.entity_name, "
            + "a.container_oid, a.base_iteration_oid, a.base_version, a.result_iteration_oid, a.result_version, "
            + "a.executed_at, a.executed_by, a.locked, a.note, a.tenant_oid, a.creator, a.created_at, "
            + "a.updater, a.updated_at, "
            + "COALESCE(pl.name, pm.name, rc.name) AS container_name "
            + "FROM ck_change_affected_object a "
            + "LEFT JOIN ck_product_line pl ON pl.oid = a.container_oid "
            + "LEFT JOIN ck_product_model pm ON pm.oid = a.container_oid "
            + "LEFT JOIN ck_resource_container rc ON rc.oid = a.container_oid "
            + "WHERE a.notice_oid = #{noticeOid} ORDER BY a.created_at ASC")
    @ResultMap("changeAffectedResult")
    List<ChangeAffectedObject> selectByNotice(@Param("noticeOid") String noticeOid);

    @Override
    @Select("SELECT " + COLUMNS + " FROM ck_change_affected_object WHERE task_oid = #{taskOid} " +
            "ORDER BY created_at ASC")
    @ResultMap("changeAffectedResult")
    List<ChangeAffectedObject> selectByTask(@Param("taskOid") String taskOid);

    /**
     * 对象反查变更通告（对象详情页「变更情况」）。
     *
     * <p>主表写成 {@code ck_change_notice}：租户拦截器把条件加在**主表别名**上，
     * 这样一张通告下的受影响对象天然同租户，不必再手工拼 tenant_oid。
     * 容器名照旧三表 COALESCE（容器被删时为 NULL，前端显示 "—"）。
     */
    @Override
    @Select("SELECT n.oid AS \"noticeOid\", n.code AS \"noticeCode\", n.name AS \"noticeName\", "
            + "n.status AS \"status\", n.owner AS \"owner\", n.product_oid AS \"productOid\", "
            + "n.plan_start AS \"planStart\", n.plan_end AS \"planEnd\", "
            + "n.approved_at AS \"approvedAt\", n.completed_at AS \"completedAt\", n.closed_at AS \"closedAt\", "
            + "n.created_at AS \"noticeCreatedAt\", n.creator AS \"noticeCreator\", "
            + "a.oid AS \"affectedOid\", a.entity_type AS \"entityType\", a.entity_oid AS \"entityOid\", "
            + "a.entity_code AS \"entityCode\", a.entity_name AS \"entityName\", "
            + "a.base_iteration_oid AS \"baseIterationOid\", a.base_version AS \"baseVersion\", "
            + "a.result_iteration_oid AS \"resultIterationOid\", a.result_version AS \"resultVersion\", "
            + "a.task_oid AS \"taskOid\", a.executed_at AS \"executedAt\", a.executed_by AS \"executedBy\", "
            + "a.locked AS \"locked\", a.note AS \"note\", "
            + "COALESCE(pl.name, pm.name, rc.name) AS \"containerName\" "
            + "FROM ck_change_notice n "
            + "JOIN ck_change_affected_object a ON a.notice_oid = n.oid "
            + "LEFT JOIN ck_product_line pl ON pl.oid = a.container_oid "
            + "LEFT JOIN ck_product_model pm ON pm.oid = a.container_oid "
            + "LEFT JOIN ck_resource_container rc ON rc.oid = a.container_oid "
            + "WHERE a.entity_type = #{entityType} AND a.entity_oid = #{entityOid} "
            + "ORDER BY n.created_at DESC")
    List<Map<String, Object>> selectNoticesByEntity(@Param("entityType") String entityType,
                                                    @Param("entityOid") String entityOid);

    @Override
    @Select("SELECT COUNT(*) FROM ck_change_affected_object " +
            "WHERE notice_oid = #{noticeOid} AND entity_type = #{entityType} AND entity_oid = #{entityOid}")
    int countByNoticeAndEntity(@Param("noticeOid") String noticeOid,
                               @Param("entityType") String entityType,
                               @Param("entityOid") String entityOid);

    @Override
    @Select("SELECT " + COLUMNS + " FROM ck_change_affected_object " +
            "WHERE entity_type = #{entityType} AND entity_oid = #{entityOid} AND locked = TRUE LIMIT 1")
    @ResultMap("changeAffectedResult")
    ChangeAffectedObject selectActiveLock(@Param("entityType") String entityType,
                                          @Param("entityOid") String entityOid);

    @Override
    @Select("SELECT COUNT(*) FROM ck_change_affected_object " +
            "WHERE notice_oid = #{noticeOid} AND executed_at IS NULL")
    int countPending(@Param("noticeOid") String noticeOid);

    @Override
    @Update("UPDATE ck_change_affected_object SET locked = FALSE " +
            "WHERE notice_oid = #{noticeOid} AND locked = TRUE")
    int releaseByNotice(@Param("noticeOid") String noticeOid);

    @Override
    @Update("UPDATE ck_change_affected_object SET locked = TRUE WHERE notice_oid = #{noticeOid}")
    int lockByNotice(@Param("noticeOid") String noticeOid);
}
