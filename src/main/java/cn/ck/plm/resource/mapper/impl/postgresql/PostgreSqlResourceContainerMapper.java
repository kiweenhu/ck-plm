/*
 * Copyright (c) 2026~2028 深圳乘恺科技有限公司
 * All rights reserved.
 *
 * @author Kiween.Hu; Roney.Liu
 */

package cn.ck.plm.resource.mapper.impl.postgresql;

import cn.ck.plm.resource.entity.ResourceContainer;
import cn.ck.plm.resource.mapper.ResourceContainerMapper;
import org.apache.ibatis.annotations.*;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;

import java.util.List;

/**
 * {@link ResourceContainerMapper} 的 PostgreSQL 实现。
 *
 * <p>资源库**按租户隔离**（2026-10-11 由"硬编码平台条件"的半共享改为隔离）：
 * 容器随 {@code platform-base} 包按租户各装一份，读写一律**显式**带 {@code tenant_oid} ——
 * 与 {@code PostgreSqlClassificationMapper} / {@code PostgreSqlUnitMapper} 同款口径，
 * 不依赖 {@code TenantStatementInterceptor} 注入（SQL 显式含 tenant_oid 时它会跳过注入，正是本意）。
 *
 * <p>改前的写法（所有 SQL 硬编码平台 oid）让"租户装包"写不进本租户、"租户读取"也全靠这几个
 * 显式 SQL 撑着；一旦有别的路径按业务表过滤（拦截器注入 {@code tenant_oid = 本租户}），
 * 同一张表就是两套口径（谁查谁说了算）。详见 CHANGELOG「企业资源库改为按租户隔离」。
 */
@Mapper
@ConditionalOnProperty(name = "plm.database.type", havingValue = "postgresql", matchIfMissing = true)
public interface PostgreSqlResourceContainerMapper extends ResourceContainerMapper {

    String SELECT_COLUMNS = "SELECT oid, code, name, description, container_type, thumbnail, " +
            "parent_oid, team_oid, sort_order, tenant_oid, delete_mark, " +
            "creator, created_at, updater, updated_at FROM ck_resource_container ";

    @Override
    @Insert("INSERT INTO ck_resource_container (oid, code, name, description, container_type, parent_oid, sort_order, " +
            "tenant_oid, delete_mark, creator, created_at, updater, updated_at) " +
            "VALUES (#{oid}, #{code}, #{name}, #{description}, #{containerType}, #{parentOid}, #{sortOrder}, " +
            "#{tenantOid}, #{deleteMark}, #{creator}, #{createdAt}, #{updater}, #{updatedAt})")
    int insert(ResourceContainer node);

    @Override
    @Select(SELECT_COLUMNS + "WHERE code = #{code} AND tenant_oid = #{tenantOid} " +
            " AND COALESCE(delete_mark, false) = false LIMIT 1")
    @Results(id = "resourceContainerResult", value = {
            @Result(property = "oid",            column = "oid"),
            @Result(property = "code",           column = "code"),
            @Result(property = "name",           column = "name"),
            @Result(property = "description",    column = "description"),
            @Result(property = "containerType",  column = "container_type"),
            @Result(property = "thumbnail",      column = "thumbnail"),
            @Result(property = "parentOid",      column = "parent_oid"),
            @Result(property = "teamOid",        column = "team_oid"),
            @Result(property = "sortOrder",      column = "sort_order"),
            @Result(property = "tenantOid",      column = "tenant_oid"),
            @Result(property = "deleteMark",     column = "delete_mark"),
            @Result(property = "creator",        column = "creator"),
            @Result(property = "createdAt",      column = "created_at"),
            @Result(property = "updater",        column = "updater"),
            @Result(property = "updatedAt",      column = "updated_at")
    })
    ResourceContainer selectByCode(@Param("code") String code, @Param("tenantOid") String tenantOid);

    @Override
    @Select(SELECT_COLUMNS + "WHERE parent_oid = #{parentOid} AND tenant_oid = #{tenantOid} " +
            " AND COALESCE(delete_mark, false) = false ORDER BY sort_order ASC, code ASC")
    @ResultMap("resourceContainerResult")
    List<ResourceContainer> selectChildren(@Param("parentOid") String parentOid, @Param("tenantOid") String tenantOid);

    @Override
    @Select(SELECT_COLUMNS + "WHERE code = 'CORP_RESOURCE' AND tenant_oid = #{tenantOid} " +
            " AND COALESCE(delete_mark, false) = false LIMIT 1")
    @ResultMap("resourceContainerResult")
    ResourceContainer selectRoot(@Param("tenantOid") String tenantOid);
}
