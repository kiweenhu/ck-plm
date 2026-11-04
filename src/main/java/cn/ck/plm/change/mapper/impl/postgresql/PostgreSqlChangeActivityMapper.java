/*
 * Copyright (c) 2026~2028 深圳乘恺科技有限公司
 * All rights reserved.
 */

package cn.ck.plm.change.mapper.impl.postgresql;

import cn.ck.plm.change.entity.ChangeActivity;
import cn.ck.plm.change.mapper.ChangeActivityMapper;
import org.apache.ibatis.annotations.*;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;

import java.util.List;

/**
 * {@link ChangeActivityMapper} 的 PostgreSQL 实现。
 *
 * <p>{@code comment} 在 PostgreSQL 里是非保留关键字，可用作列名，但此处仍加引号，
 * 避免以后改库或换方言时踩坑。
 */
@Mapper
@ConditionalOnProperty(name = "plm.database.type", havingValue = "postgresql", matchIfMissing = true)
public interface PostgreSqlChangeActivityMapper extends ChangeActivityMapper {

    String COLUMNS = "oid, change_type, change_oid, action, from_status, to_status, \"comment\", operator, "
            + "tenant_oid, creator, created_at, updater, updated_at";

    @Override
    @Insert("INSERT INTO ck_change_activity (" + COLUMNS + ") VALUES (" +
            "#{oid}, #{changeType}, #{changeOid}, #{action}, #{fromStatus}, #{toStatus}, #{comment}, #{operator}, " +
            "#{tenantOid}, #{creator}, #{createdAt}, #{updater}, #{updatedAt})")
    int insert(ChangeActivity activity);

    @Override
    @Select("SELECT " + COLUMNS + " FROM ck_change_activity " +
            "WHERE change_type = #{changeType} AND change_oid = #{changeOid} " +
            "ORDER BY created_at ASC")
    @Results(id = "changeActivityResult", value = {
            @Result(property = "oid",        column = "oid"),
            @Result(property = "changeType", column = "change_type"),
            @Result(property = "changeOid",  column = "change_oid"),
            @Result(property = "action",     column = "action"),
            @Result(property = "fromStatus", column = "from_status"),
            @Result(property = "toStatus",   column = "to_status"),
            @Result(property = "comment",    column = "comment"),
            @Result(property = "operator",   column = "operator"),
            @Result(property = "tenantOid",  column = "tenant_oid"),
            @Result(property = "creator",    column = "creator"),
            @Result(property = "createdAt",  column = "created_at"),
            @Result(property = "updater",    column = "updater"),
            @Result(property = "updatedAt",  column = "updated_at")
    })
    List<ChangeActivity> selectByOwner(@Param("changeType") String changeType,
                                       @Param("changeOid") String changeOid);

    @Override
    @Delete("DELETE FROM ck_change_activity WHERE change_type = #{changeType} AND change_oid = #{changeOid}")
    int deleteByOwner(@Param("changeType") String changeType,
                      @Param("changeOid") String changeOid);
}
