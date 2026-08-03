/*
 * Copyright (c) 2025 深圳乘恺科技有限公司
 * All rights reserved.
 *
 * @author Kiween.Hu; Roney.Liu
 */

package cn.ck.plm.base.mapper.impl.postgresql;

import cn.ck.plm.base.entity.Number;
import cn.ck.plm.base.mapper.NumberMapper;
import org.apache.ibatis.annotations.*;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;

import java.util.List;

/**
 * {@link NumberMapper} 的 PostgreSQL 实现（MyBatis 注解模式）。
 */
@Mapper
@ConditionalOnProperty(name = "plm.database.type", havingValue = "postgresql", matchIfMissing = true)
public interface PostgreSqlNumberMapper extends NumberMapper {

    @Override
    @Insert("INSERT INTO ck_number (code, oid, name, description, enabled, tenant_oid, creator, created_at, updater, updated_at) " +
            "VALUES (#{code}, #{oid}, #{name}, #{description}, #{enabled}, #{tenantOid}, #{creator}, #{createdAt}, #{updater}, #{updatedAt})")
    int insert(Number number);

    @Override
    @Update("UPDATE ck_number SET name = #{name}, description = #{description}, enabled = #{enabled}, " +
            "tenant_oid = #{tenantOid}, updater = #{updater}, updated_at = #{updatedAt} WHERE code = #{code}")
    int update(Number number);

    @Override
    @Delete("DELETE FROM ck_number WHERE code = #{code}")
    int deleteByCode(@Param("code") String code);

    // ==================== 查询映射 ====================

    @Select("SELECT code, oid, name, description, enabled, tenant_oid, creator, created_at, updater, updated_at FROM ck_number WHERE code = #{code}")
    @Results(id = "numberResult", value = {
            @Result(property = "code",        column = "code"),
            @Result(property = "oid",         column = "oid"),
            @Result(property = "name",        column = "name"),
            @Result(property = "description", column = "description"),
            @Result(property = "enabled",     column = "enabled"),
            @Result(property = "tenantOid",   column = "tenant_oid"),
            @Result(property = "creator",     column = "creator"),
            @Result(property = "createdAt",   column = "created_at"),
            @Result(property = "updater",     column = "updater"),
            @Result(property = "updatedAt",   column = "updated_at")
    })
    @Override
    Number selectByCode(@Param("code") String code);

    @Select("SELECT code, oid, name, description, enabled, tenant_oid, creator, created_at, updater, updated_at FROM ck_number ORDER BY code ASC")
    @ResultMap("numberResult")
    @Override
    List<Number> selectAll();

    @Select("SELECT code, oid, name, description, enabled, tenant_oid, creator, created_at, updater, updated_at FROM ck_number " +
            "WHERE LOWER(code) LIKE LOWER('%' || #{keyword} || '%') " +
            "OR LOWER(name) LIKE LOWER('%' || #{keyword} || '%') " +
            "ORDER BY code ASC")
    @ResultMap("numberResult")
    @Override
    List<Number> search(@Param("keyword") String keyword);

    @Select("SELECT COUNT(*) FROM ck_number WHERE code = #{code}")
    @Override
    int existsByCode(@Param("code") String code);
}
