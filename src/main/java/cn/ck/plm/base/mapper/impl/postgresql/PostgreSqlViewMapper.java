/*
 * Copyright (c) 2025 深圳乘恺科技有限公司
 * All rights reserved.
 *
 * @author Kiween.Hu; Roney.Liu
 */

package cn.ck.plm.base.mapper.impl.postgresql;

import cn.ck.plm.base.entity.View;
import cn.ck.plm.base.mapper.ViewMapper;
import org.apache.ibatis.annotations.*;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;

import java.util.List;

/**
 * {@link ViewMapper} 的 PostgreSQL 实现（MyBatis 注解模式）。
 *
 * <p>通过 {@code plm.database.type=postgresql} 配置激活此实现。
 */
@Mapper
@ConditionalOnProperty(name = "plm.database.type", havingValue = "postgresql", matchIfMissing = true)
public interface PostgreSqlViewMapper extends ViewMapper {

    @Override
    @Insert("INSERT INTO ck_view (code, oid, name, description, sort_order, enabled, tenant_oid, creator, created_at, updater, updated_at) " +
            "VALUES (#{code}, #{oid}, #{name}, #{description}, #{sortOrder}, #{enabled}, #{tenantOid}, #{creator}, #{createdAt}, #{updater}, #{updatedAt})")
    int insert(View view);

    @Override
    @Update("UPDATE ck_view SET name = #{name}, description = #{description}, sort_order = #{sortOrder}, " +
            "enabled = #{enabled}, tenant_oid = #{tenantOid}, updater = #{updater}, updated_at = #{updatedAt} WHERE code = #{code}")
    int update(View view);

    @Override
    @Delete("DELETE FROM ck_view WHERE code = #{code}")
    int deleteByCode(@Param("code") String code);

    // ==================== 查询映射定义 ====================

    @Select("SELECT code, oid, name, description, sort_order, enabled, tenant_oid, creator, created_at, updater, updated_at " +
            "FROM ck_view WHERE code = #{code}")
    @Results(id = "viewResult", value = {
            @Result(property = "code",        column = "code"),
            @Result(property = "oid",         column = "oid"),
            @Result(property = "name",        column = "name"),
            @Result(property = "description", column = "description"),
            @Result(property = "sortOrder",   column = "sort_order"),
            @Result(property = "enabled",     column = "enabled"),
            @Result(property = "tenantOid",   column = "tenant_oid"),
            @Result(property = "creator",     column = "creator"),
            @Result(property = "createdAt",   column = "created_at"),
            @Result(property = "updater",     column = "updater"),
            @Result(property = "updatedAt",   column = "updated_at")
    })
    @Override
    View selectByCode(@Param("code") String code);

    @Select("SELECT code, oid, name, description, sort_order, enabled, tenant_oid, creator, created_at, updater, updated_at " +
            "FROM ck_view WHERE enabled = TRUE ORDER BY sort_order ASC")
    @ResultMap("viewResult")
    @Override
    List<View> selectAllEnabled();

    @Select("SELECT code, oid, name, description, sort_order, enabled, tenant_oid, creator, created_at, updater, updated_at " +
            "FROM ck_view ORDER BY sort_order ASC")
    @ResultMap("viewResult")
    @Override
    List<View> selectAll();

    @Select("SELECT code, oid, name, description, sort_order, enabled, tenant_oid, creator, created_at, updater, updated_at " +
            "FROM ck_view " +
            "WHERE LOWER(code) LIKE LOWER('%' || #{keyword} || '%') " +
            "OR LOWER(name) LIKE LOWER('%' || #{keyword} || '%') " +
            "ORDER BY sort_order ASC")
    @ResultMap("viewResult")
    @Override
    List<View> search(@Param("keyword") String keyword);

    @Select("SELECT COUNT(*) FROM ck_view WHERE code = #{code}")
    @Override
    int existsByCode(@Param("code") String code);
}
