/*
 * Copyright (c) 2025 深圳乘恺科技有限公司
 * All rights reserved.
 *
 * @author Kiween.Hu; Roney.Liu
 */

package cn.ck.plm.cls.mapper.impl;

import cn.ck.plm.cls.entity.ClsPageLayout;
import cn.ck.plm.cls.mapper.ClsPageLayoutMapper;
import org.apache.ibatis.annotations.*;

import java.util.List;

/**
 * PostgreSQL 方言的 ClsPageLayout Mapper 实现。
 */
@Mapper
public interface PostgreSqlClsPageLayoutMapper extends ClsPageLayoutMapper {

    @Override
    @Select("SELECT oid, cls_oid, operation_code, operation_name, " +
            "layout_json::text, tenant_oid, creator, created_at, updater, updated_at " +
            "FROM ck_cls_page_layout " +
            "WHERE cls_oid=#{clsOid} AND tenant_oid=#{tenantOid} " +
            "ORDER BY operation_code")
    List<ClsPageLayout> selectAllByClsOid(@Param("clsOid") String clsOid,
                                           @Param("tenantOid") String tenantOid);

    @Override
    @Select("SELECT oid, cls_oid, operation_code, operation_name, " +
            "layout_json::text, tenant_oid, creator, created_at, updater, updated_at " +
            "FROM ck_cls_page_layout " +
            "WHERE cls_oid=#{clsOid} AND operation_code=#{operationCode} " +
            "AND tenant_oid=#{tenantOid} " +
            "LIMIT 1")
    ClsPageLayout selectByClsAndOperation(@Param("clsOid") String clsOid,
                                           @Param("operationCode") String operationCode,
                                           @Param("tenantOid") String tenantOid);

    @Override
    @Insert("INSERT INTO ck_cls_page_layout (oid, cls_oid, operation_code, operation_name, " +
            "layout_json, tenant_oid, creator, created_at) " +
            "VALUES (#{oid}, #{clsOid}, #{operationCode}, #{operationName}, " +
            "#{layoutJson}::jsonb, #{tenantOid}, #{creator}, NOW())")
    int insert(ClsPageLayout layout);

    @Override
    @Update("UPDATE ck_cls_page_layout SET cls_oid=#{clsOid}, " +
            "operation_code=#{operationCode}, operation_name=#{operationName}, " +
            "layout_json=#{layoutJson}::jsonb, tenant_oid=#{tenantOid}, " +
            "updater=#{updater}, updated_at=NOW() WHERE oid=#{oid}")
    int update(ClsPageLayout layout);

    @Override
    @Update("UPDATE ck_cls_page_layout SET layout_json=#{layoutJson}::jsonb, " +
            "operation_name=#{operationName}, updater=#{updater}, updated_at=NOW() " +
            "WHERE oid=#{oid}")
    int updateLayout(@Param("oid") String oid,
                     @Param("layoutJson") String layoutJson,
                     @Param("operationName") String operationName);

    @Override
    @Delete("DELETE FROM ck_cls_page_layout " +
            "WHERE cls_oid=#{clsOid} AND operation_code=#{operationCode} " +
            "AND tenant_oid=#{tenantOid}")
    int deleteByClsAndOperation(@Param("clsOid") String clsOid,
                                 @Param("operationCode") String operationCode,
                                 @Param("tenantOid") String tenantOid);

    @Override
    @Delete("DELETE FROM ck_cls_page_layout WHERE cls_oid=#{clsOid}")
    int deleteAllByClsOid(@Param("clsOid") String clsOid);
}
