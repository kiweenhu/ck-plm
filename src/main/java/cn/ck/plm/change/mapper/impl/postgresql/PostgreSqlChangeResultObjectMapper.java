/*
 * Copyright (c) 2026~2028 深圳乘恺科技有限公司
 * All rights reserved.
 *
 * @author Kiween.Hu; Roney.Liu
 */

package cn.ck.plm.change.mapper.impl.postgresql;

import cn.ck.plm.change.entity.ChangeResultObject;
import cn.ck.plm.change.mapper.ChangeResultObjectMapper;
import org.apache.ibatis.annotations.*;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;

import java.util.List;

/**
 * {@link ChangeResultObjectMapper} 的 PostgreSQL 实现。
 *
 * <p>唯一索引 {@code uk_change_result (affected_oid, result_iteration_oid)} 是幂等的最后一道闸：
 * 同一变更前对象的同一版重复写入直接冲突，服务层不用先查后写。
 */
@Mapper
@ConditionalOnProperty(name = "plm.database.type", havingValue = "postgresql", matchIfMissing = true)
public interface PostgreSqlChangeResultObjectMapper extends ChangeResultObjectMapper {

    String COLUMNS = "oid, affected_oid, notice_oid, task_oid, entity_type, entity_oid, entity_code, "
            + "entity_name, source_iteration_oid, source_version, result_iteration_oid, result_version, "
            + "change_kind, executed_at, executed_by, tenant_oid, creator, created_at, updater, updated_at";

    @Override
    @Insert("INSERT INTO ck_change_result_object (" + COLUMNS + ") VALUES (" +
            "#{oid}, #{affectedOid}, #{noticeOid}, #{taskOid}, #{entityType}, #{entityOid}, #{entityCode}, " +
            "#{entityName}, #{sourceIterationOid}, #{sourceVersion}, #{resultIterationOid}, #{resultVersion}, " +
            "#{changeKind}, #{executedAt}, #{executedBy}, #{tenantOid}, #{creator}, #{createdAt}, " +
            "#{updater}, #{updatedAt})")
    int insert(ChangeResultObject result);

    @Override
    @Select("SELECT " + COLUMNS + " FROM ck_change_result_object WHERE affected_oid = #{affectedOid} " +
            "ORDER BY created_at ASC")
    @Results(id = "changeResultResult", value = {
            @Result(property = "oid",                column = "oid"),
            @Result(property = "affectedOid",        column = "affected_oid"),
            @Result(property = "noticeOid",          column = "notice_oid"),
            @Result(property = "taskOid",            column = "task_oid"),
            @Result(property = "entityType",         column = "entity_type"),
            @Result(property = "entityOid",          column = "entity_oid"),
            @Result(property = "entityCode",         column = "entity_code"),
            @Result(property = "entityName",         column = "entity_name"),
            @Result(property = "sourceIterationOid", column = "source_iteration_oid"),
            @Result(property = "sourceVersion",      column = "source_version"),
            @Result(property = "resultIterationOid", column = "result_iteration_oid"),
            @Result(property = "resultVersion",      column = "result_version"),
            @Result(property = "changeKind",         column = "change_kind"),
            @Result(property = "executedAt",         column = "executed_at"),
            @Result(property = "executedBy",         column = "executed_by"),
            @Result(property = "tenantOid",          column = "tenant_oid"),
            @Result(property = "creator",            column = "creator"),
            @Result(property = "createdAt",          column = "created_at"),
            @Result(property = "updater",            column = "updater"),
            @Result(property = "updatedAt",          column = "updated_at")
    })
    List<ChangeResultObject> selectByAffected(@Param("affectedOid") String affectedOid);

    @Override
    @Select("SELECT " + COLUMNS + " FROM ck_change_result_object WHERE notice_oid = #{noticeOid} " +
            "ORDER BY created_at ASC")
    @ResultMap("changeResultResult")
    List<ChangeResultObject> selectByNotice(@Param("noticeOid") String noticeOid);

    @Override
    @Select("SELECT " + COLUMNS + " FROM ck_change_result_object WHERE task_oid = #{taskOid} " +
            "ORDER BY created_at ASC")
    @ResultMap("changeResultResult")
    List<ChangeResultObject> selectByTask(@Param("taskOid") String taskOid);

    @Override
    @Select("SELECT COUNT(*) FROM ck_change_result_object WHERE affected_oid = #{affectedOid}")
    int countByAffected(@Param("affectedOid") String affectedOid);

    @Override
    @Select("SELECT COUNT(*) FROM ck_change_result_object WHERE notice_oid = #{noticeOid}")
    int countByNotice(@Param("noticeOid") String noticeOid);

    @Override
    @Delete("DELETE FROM ck_change_result_object WHERE notice_oid = #{noticeOid}")
    int deleteByNotice(@Param("noticeOid") String noticeOid);
}
