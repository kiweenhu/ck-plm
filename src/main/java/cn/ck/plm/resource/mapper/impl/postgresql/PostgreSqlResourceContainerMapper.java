/*
 * Copyright (c) 2025 深圳乘恺科技有限公司
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
 * <p>资源库属于平台级共享数据：所有 SQL 显式带平台租户条件，
 * 使 TenantStatementInterceptor 跳过自动注入，保证任意租户均可读取。
 */
@Mapper
@ConditionalOnProperty(name = "plm.database.type", havingValue = "postgresql", matchIfMissing = true)
public interface PostgreSqlResourceContainerMapper extends ResourceContainerMapper {

    String PLATFORM_OID = "'00000000-0000-0000-0000-000000000000'";

    String SELECT_COLUMNS = "SELECT oid, code, name, description, container_type, thumbnail, " +
            "parent_oid, team_oid, sort_order, tenant_oid, delete_mark, " +
            "creator, created_at, updater, updated_at FROM ck_resource_container ";

    @Override
    @Insert("INSERT INTO ck_resource_container (oid, code, name, description, container_type, parent_oid, sort_order, " +
            "tenant_oid, delete_mark, creator, created_at, updater, updated_at) " +
            "VALUES (#{oid}, #{code}, #{name}, #{description}, #{containerType}, #{parentOid}, #{sortOrder}, " +
            PLATFORM_OID + ", #{deleteMark}, #{creator}, #{createdAt}, #{updater}, #{updatedAt})")
    int insert(ResourceContainer node);

    @Override
    @Select(SELECT_COLUMNS + "WHERE code = #{code} AND tenant_oid = " + PLATFORM_OID +
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
    ResourceContainer selectByCode(@Param("code") String code);

    @Override
    @Select(SELECT_COLUMNS + "WHERE parent_oid = #{parentOid} AND tenant_oid = " + PLATFORM_OID +
            " AND COALESCE(delete_mark, false) = false ORDER BY sort_order ASC, code ASC")
    @ResultMap("resourceContainerResult")
    List<ResourceContainer> selectChildren(@Param("parentOid") String parentOid);

    @Override
    @Select(SELECT_COLUMNS + "WHERE code = 'CORP_RESOURCE' AND tenant_oid = " + PLATFORM_OID +
            " AND COALESCE(delete_mark, false) = false LIMIT 1")
    @ResultMap("resourceContainerResult")
    ResourceContainer selectRoot();
}
