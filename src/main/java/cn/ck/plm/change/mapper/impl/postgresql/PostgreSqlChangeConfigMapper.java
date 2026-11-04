/*
 * Copyright (c) 2026~2028 深圳乘恺科技有限公司
 * All rights reserved.
 */

package cn.ck.plm.change.mapper.impl.postgresql;

import cn.ck.plm.change.entity.ChangeConfig;
import cn.ck.plm.change.mapper.ChangeConfigMapper;
import org.apache.ibatis.annotations.*;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;

/**
 * {@link ChangeConfigMapper} 的 PostgreSQL 实现。
 */
@Mapper
@ConditionalOnProperty(name = "plm.database.type", havingValue = "postgresql", matchIfMissing = true)
public interface PostgreSqlChangeConfigMapper extends ChangeConfigMapper {

    String COLUMNS = "tenant_oid, approval_required, request_process_key, approval_process_key, task_process_key, "
            + "bypass_for_released, request_enabled, updater, updated_at";

    @Override
    @Insert("INSERT INTO ck_change_config (" + COLUMNS + ") VALUES (" +
            "#{tenantOid}, #{approvalRequired}, #{requestProcessKey}, #{approvalProcessKey}, #{taskProcessKey}, " +
            "#{bypassForReleased}, #{requestEnabled}, #{updater}, #{updatedAt})")
    int insert(ChangeConfig config);

    @Override
    @Update("UPDATE ck_change_config SET approval_required = #{approvalRequired}, " +
            "request_process_key = #{requestProcessKey}, approval_process_key = #{approvalProcessKey}, " +
            "task_process_key = #{taskProcessKey}, bypass_for_released = #{bypassForReleased}, " +
            "request_enabled = #{requestEnabled}, " +
            "updater = #{updater}, updated_at = #{updatedAt} " +
            "WHERE tenant_oid = #{tenantOid}")
    int update(ChangeConfig config);

    @Override
    @Select("SELECT " + COLUMNS + " FROM ck_change_config WHERE tenant_oid = #{tenantOid}")
    @Results(id = "changeConfigResult", value = {
            @Result(property = "tenantOid",          column = "tenant_oid"),
            @Result(property = "approvalRequired",   column = "approval_required"),
            @Result(property = "requestProcessKey",  column = "request_process_key"),
            @Result(property = "approvalProcessKey", column = "approval_process_key"),
            @Result(property = "taskProcessKey",     column = "task_process_key"),
            @Result(property = "bypassForReleased",  column = "bypass_for_released"),
            @Result(property = "requestEnabled",     column = "request_enabled"),
            @Result(property = "updater",            column = "updater"),
            @Result(property = "updatedAt",          column = "updated_at")
    })
    ChangeConfig selectByTenant(@Param("tenantOid") String tenantOid);
}
