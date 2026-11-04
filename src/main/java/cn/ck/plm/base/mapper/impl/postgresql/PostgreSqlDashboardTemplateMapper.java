/*
 * Copyright (c) 2026~2028 深圳乘恺科技有限公司
 * All rights reserved.
 */

package cn.ck.plm.base.mapper.impl.postgresql;

import cn.ck.plm.base.entity.DashboardTemplate;
import cn.ck.plm.base.mapper.DashboardTemplateMapper;
import org.apache.ibatis.annotations.*;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;

import java.util.List;

/**
 * {@link DashboardTemplateMapper} 的 PostgreSQL 实现。
 *
 * <p>每条语句都<b>显式</b>写明 {@code tenant_oid}（INSERT 写列、SELECT 写条件）：
 * 显式书写使语义<b>不依赖拦截器注入</b>（拦截器见 SQL 已含 tenant_oid 会跳过改写），固定为"只本租户"。
 * 2026-10-10 起由"本租户一份 + 平台一份"改为按租户隔离 —— 模板随包按租户各装一份。
 */
@Mapper
@ConditionalOnProperty(name = "plm.database.type", havingValue = "postgresql", matchIfMissing = true)
public interface PostgreSqlDashboardTemplateMapper extends DashboardTemplateMapper {

    /** 查询列（与实体字段一一对应） */
    String COLUMNS = "oid, code, name, description, industry, audience, page_count, widgets, "
            + "generate_policy, primary_scope, disabled, sort_order, tenant_oid, "
            + "creator, created_at, updater, updated_at";

    @Override
    @Insert("INSERT INTO ck_dashboard_template (" + COLUMNS + ") " +
            "VALUES (#{oid}, #{code}, #{name}, #{description}, #{industry}, #{audience}, #{pageCount}, " +
            "#{widgets}, #{generatePolicy}, #{primaryScope}, #{disabled}, #{sortOrder}, #{tenantOid}, " +
            "#{creator}, #{createdAt}, #{updater}, #{updatedAt})")
    int insert(DashboardTemplate template);

    @Override
    @Update("UPDATE ck_dashboard_template SET name = #{name}, description = #{description}, " +
            "industry = #{industry}, audience = #{audience}, page_count = #{pageCount}, " +
            "widgets = #{widgets}, generate_policy = #{generatePolicy}, " +
            "primary_scope = #{primaryScope}, disabled = #{disabled}, sort_order = #{sortOrder}, " +
            "updater = #{updater}, updated_at = #{updatedAt} WHERE oid = #{oid}")
    int update(DashboardTemplate template);

    @Override
    @Delete("DELETE FROM ck_dashboard_template WHERE oid = #{oid}")
    int deleteByOid(@Param("oid") String oid);

    @Override
    @Select("SELECT " + COLUMNS + " FROM ck_dashboard_template WHERE oid = #{oid}")
    @Results(id = "dashboardTemplateResult", value = {
            @Result(property = "oid",            column = "oid"),
            @Result(property = "code",           column = "code"),
            @Result(property = "name",           column = "name"),
            @Result(property = "description",    column = "description"),
            @Result(property = "industry",       column = "industry"),
            @Result(property = "audience",       column = "audience"),
            @Result(property = "pageCount",      column = "page_count"),
            @Result(property = "widgets",        column = "widgets"),
            @Result(property = "generatePolicy", column = "generate_policy"),
            @Result(property = "primaryScope",   column = "primary_scope"),
            @Result(property = "disabled",       column = "disabled"),
            @Result(property = "sortOrder",      column = "sort_order"),
            @Result(property = "tenantOid",      column = "tenant_oid"),
            @Result(property = "creator",        column = "creator"),
            @Result(property = "createdAt",      column = "created_at"),
            @Result(property = "updater",        column = "updater"),
            @Result(property = "updatedAt",      column = "updated_at")
    })
    DashboardTemplate selectByOid(@Param("oid") String oid);

    @Override
    @Select("SELECT " + COLUMNS + " FROM ck_dashboard_template WHERE code = #{code} " +
            "AND tenant_oid = #{tenantOid} LIMIT 1")
    @ResultMap("dashboardTemplateResult")
    DashboardTemplate selectByCode(@Param("code") String code,
                                   @Param("tenantOid") String tenantOid,
                                   @Param("platformOid") String platformOid);

    @Override
    @Select("SELECT " + COLUMNS + " FROM ck_dashboard_template " +
            "WHERE tenant_oid = #{tenantOid} " +
            "ORDER BY sort_order ASC")
    @ResultMap("dashboardTemplateResult")
    List<DashboardTemplate> selectAll(@Param("tenantOid") String tenantOid,
                                      @Param("platformOid") String platformOid);

    @Override
    @Select("SELECT " + COLUMNS + " FROM ck_dashboard_template " +
            "WHERE tenant_oid = #{tenantOid} " +
            "ORDER BY industry, sort_order ASC")
    @ResultMap("dashboardTemplateResult")
    List<DashboardTemplate> selectByTenant(@Param("tenantOid") String tenantOid);

    @Override
    @Select("SELECT COUNT(*) FROM ck_dashboard_template WHERE code = #{code} " +
            "AND tenant_oid = #{tenantOid}")
    int existsByCode(@Param("code") String code,
                     @Param("tenantOid") String tenantOid,
                     @Param("platformOid") String platformOid);
}
