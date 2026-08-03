/*
 * Copyright (c) 2025 深圳乘恺科技有限公司
 * All rights reserved.
 *
 * @author Kiween.Hu; Roney.Liu
 */

package cn.ck.plm.softtype.mapper.impl.postgresql;

import cn.ck.plm.softtype.entity.PageLayout;
import cn.ck.plm.softtype.mapper.PageLayoutMapper;
import org.apache.ibatis.annotations.*;

import java.util.List;

/**
 * PostgreSQL 方言的 PageLayout Mapper 实现。
 */
@Mapper
public interface PostgreSqlPageLayoutMapper extends PageLayoutMapper {

    @Override
    @Select("SELECT DISTINCT ON (operation_code) oid, entity_oid, entity_code, operation_code, operation_name, " +
            "layout_json::text, tenant_oid, creator, created_at, updater, updated_at " +
            "FROM ck_type_page_layout " +
            "WHERE entity_oid=#{entityOid} " +
            "AND tenant_oid IN (#{tenantOid}, #{platformOid}) " +
            "ORDER BY operation_code, CASE WHEN tenant_oid = #{tenantOid} THEN 0 ELSE 1 END")
    List<PageLayout> selectAllByEntity(@Param("entityOid") String entityOid,
                                       @Param("entityCode") String entityCode,
                                       @Param("tenantOid") String tenantOid,
                                       @Param("platformOid") String platformOid);

    @Override
    @Select("SELECT oid, entity_oid, entity_code, operation_code, operation_name, " +
            "layout_json::text, tenant_oid, creator, created_at, updater, updated_at " +
            "FROM ck_type_page_layout " +
            "WHERE entity_oid=#{entityOid} AND operation_code=#{operationCode} " +
            "AND tenant_oid IN (#{tenantOid}, #{platformOid}) " +
            "ORDER BY CASE WHEN tenant_oid = #{tenantOid} THEN 0 ELSE 1 END " +
            "LIMIT 1")
    PageLayout selectByEntityAndOperation(@Param("entityOid") String entityOid,
                                          @Param("operationCode") String operationCode,
                                          @Param("tenantOid") String tenantOid,
                                          @Param("platformOid") String platformOid);

    @Override
    @Select("SELECT oid, entity_oid, entity_code, operation_code, operation_name, " +
            "layout_json::text, tenant_oid, creator, created_at, updater, updated_at " +
            "FROM ck_type_page_layout " +
            "WHERE entity_code=#{entityCode} AND operation_code=#{operationCode} " +
            "AND tenant_oid IN (#{tenantOid}, #{platformOid}) " +
            "ORDER BY CASE WHEN tenant_oid = #{tenantOid} THEN 0 ELSE 1 END " +
            "LIMIT 1")
    PageLayout selectByEntityCodeAndOperation(@Param("entityCode") String entityCode,
                                              @Param("operationCode") String operationCode,
                                              @Param("tenantOid") String tenantOid,
                                              @Param("platformOid") String platformOid);

    @Override
    @Select("SELECT oid, entity_oid, entity_code, operation_code, operation_name, " +
            "layout_json::text, tenant_oid, creator, created_at, updater, updated_at " +
            "FROM ck_type_page_layout " +
            "WHERE entity_oid=#{entityOid} " +
            "AND operation_code=#{operationCode} AND tenant_oid=#{tenantOid}")
    PageLayout selectByTenant(@Param("entityOid") String entityOid,
                              @Param("entityCode") String entityCode,
                              @Param("operationCode") String operationCode,
                              @Param("tenantOid") String tenantOid);

    @Override
    @Insert("INSERT INTO ck_type_page_layout (oid, entity_oid, entity_code, operation_code, operation_name, " +
            "layout_json, tenant_oid, creator, created_at) " +
            "VALUES (#{oid}, #{entityOid}, #{entityCode}, #{operationCode}, #{operationName}, " +
            "#{layoutJson}::jsonb, #{tenantOid}, #{creator}, NOW())")
    int insert(PageLayout layout);

    @Override
    @Update("UPDATE ck_type_page_layout SET entity_oid=#{entityOid}, entity_code=#{entityCode}, " +
            "operation_code=#{operationCode}, operation_name=#{operationName}, " +
            "layout_json=#{layoutJson}::jsonb, tenant_oid=#{tenantOid}, " +
            "updater=#{updater}, updated_at=NOW() WHERE oid=#{oid}")
    int update(PageLayout layout);

    @Override
    @Update("UPDATE ck_type_page_layout SET layout_json=#{layoutJson}::jsonb, operation_name=#{operationName}, " +
            "updater=#{updater}, updated_at=NOW() WHERE oid=#{oid}")
    int updateLayout(@Param("oid") String oid, @Param("layoutJson") String layoutJson,
                     @Param("operationName") String operationName);

    @Override
    @Delete("DELETE FROM ck_type_page_layout WHERE entity_oid=#{entityOid} AND operation_code=#{operationCode} " +
            "AND tenant_oid=#{tenantOid}")
    int deleteByEntityAndOperation(@Param("entityOid") String entityOid,
                                    @Param("operationCode") String operationCode,
                                    @Param("tenantOid") String tenantOid);

    @Override
    @Delete("DELETE FROM ck_type_page_layout WHERE entity_oid=#{entityOid}")
    int deleteAllByEntity(@Param("entityOid") String entityOid);
}
