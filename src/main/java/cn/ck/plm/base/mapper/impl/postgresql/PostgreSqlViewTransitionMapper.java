/*
 * Copyright (c) 2025 深圳乘恺科技有限公司
 * All rights reserved.
 *
 * @author Kiween.Hu; Roney.Liu
 */

package cn.ck.plm.base.mapper.impl.postgresql;

import cn.ck.plm.base.entity.ViewTransition;
import cn.ck.plm.base.mapper.ViewTransitionMapper;
import org.apache.ibatis.annotations.*;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;

import java.util.List;

/**
 * {@link ViewTransitionMapper} 的 PostgreSQL 实现（MyBatis 注解模式）。
 *
 * <p>通过 {@code plm.database.type=postgresql} 配置激活此实现。
 */
@Mapper
@ConditionalOnProperty(name = "plm.database.type", havingValue = "postgresql", matchIfMissing = true)
public interface PostgreSqlViewTransitionMapper extends ViewTransitionMapper {

    @Override
    @Insert("INSERT INTO ck_view_transition (oid, from_view_code, to_view_code, condition_status, condition_view_latest, " +
            "description, sort_order, enabled, tenant_oid, creator, created_at, updater, updated_at) " +
            "VALUES (#{oid}, #{fromViewCode}, #{toViewCode}, #{conditionStatus}, #{conditionViewLatest}, " +
            "#{description}, #{sortOrder}, #{enabled}, #{tenantOid}, #{creator}, #{createdAt}, #{updater}, #{updatedAt})")
    int insert(ViewTransition transition);

    @Override
    @Update("UPDATE ck_view_transition SET from_view_code = #{fromViewCode}, to_view_code = #{toViewCode}, " +
            "condition_status = #{conditionStatus}, condition_view_latest = #{conditionViewLatest}, " +
            "description = #{description}, sort_order = #{sortOrder}, enabled = #{enabled}, " +
            "tenant_oid = #{tenantOid}, updater = #{updater}, updated_at = #{updatedAt} WHERE oid = #{oid}")
    int update(ViewTransition transition);

    @Override
    @Delete("DELETE FROM ck_view_transition WHERE oid = #{oid}")
    int deleteByOid(@Param("oid") String oid);

    // ==================== 查询映射定义 ====================

    @Select("SELECT oid, from_view_code, to_view_code, condition_status, condition_view_latest, " +
            "description, sort_order, enabled, tenant_oid, creator, created_at, updater, updated_at " +
            "FROM ck_view_transition WHERE oid = #{oid}")
    @Results(id = "viewTransitionResult", value = {
            @Result(property = "oid",                  column = "oid"),
            @Result(property = "fromViewCode",         column = "from_view_code"),
            @Result(property = "toViewCode",           column = "to_view_code"),
            @Result(property = "conditionStatus",      column = "condition_status"),
            @Result(property = "conditionViewLatest",  column = "condition_view_latest"),
            @Result(property = "description",          column = "description"),
            @Result(property = "sortOrder",            column = "sort_order"),
            @Result(property = "enabled",              column = "enabled"),
            @Result(property = "tenantOid",            column = "tenant_oid"),
            @Result(property = "creator",              column = "creator"),
            @Result(property = "createdAt",            column = "created_at"),
            @Result(property = "updater",              column = "updater"),
            @Result(property = "updatedAt",            column = "updated_at")
    })
    @Override
    ViewTransition selectByOid(@Param("oid") String oid);

    @Select("SELECT oid, from_view_code, to_view_code, condition_status, condition_view_latest, " +
            "description, sort_order, enabled, tenant_oid, creator, created_at, updater, updated_at " +
            "FROM ck_view_transition WHERE from_view_code = #{fromViewCode} ORDER BY sort_order ASC")
    @ResultMap("viewTransitionResult")
    @Override
    List<ViewTransition> selectByFromViewCode(@Param("fromViewCode") String fromViewCode);

    @Select("SELECT oid, from_view_code, to_view_code, condition_status, condition_view_latest, " +
            "description, sort_order, enabled, tenant_oid, creator, created_at, updater, updated_at " +
            "FROM ck_view_transition WHERE from_view_code = #{fromViewCode} AND enabled = TRUE ORDER BY sort_order ASC")
    @ResultMap("viewTransitionResult")
    @Override
    List<ViewTransition> selectEnabledByFromViewCode(@Param("fromViewCode") String fromViewCode);

    @Select("SELECT oid, from_view_code, to_view_code, condition_status, condition_view_latest, " +
            "description, sort_order, enabled, tenant_oid, creator, created_at, updater, updated_at " +
            "FROM ck_view_transition WHERE from_view_code = #{fromViewCode} AND to_view_code = #{toViewCode}")
    @ResultMap("viewTransitionResult")
    @Override
    ViewTransition selectByFromAndTo(@Param("fromViewCode") String fromViewCode,
                                     @Param("toViewCode") String toViewCode);

    @Select("SELECT oid, from_view_code, to_view_code, condition_status, condition_view_latest, " +
            "description, sort_order, enabled, tenant_oid, creator, created_at, updater, updated_at " +
            "FROM ck_view_transition ORDER BY sort_order ASC")
    @ResultMap("viewTransitionResult")
    @Override
    List<ViewTransition> selectAll();

    @Delete("DELETE FROM ck_view_transition WHERE from_view_code = #{fromViewCode}")
    @Override
    int deleteByFromViewCode(@Param("fromViewCode") String fromViewCode);

    @Select("SELECT COUNT(*) FROM ck_view_transition WHERE from_view_code = #{fromViewCode} AND to_view_code = #{toViewCode}")
    @Override
    int existsByFromAndTo(@Param("fromViewCode") String fromViewCode,
                          @Param("toViewCode") String toViewCode);
}
