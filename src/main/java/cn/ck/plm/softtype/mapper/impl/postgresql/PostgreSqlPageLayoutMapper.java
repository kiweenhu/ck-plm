/*
 * Copyright (c) 2026~2028 深圳乘恺科技有限公司
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

    /**
     * 某实体的全部操作布局 —— <b>只查本租户</b>（2026-10-10 起由"平台共享 + 租户可覆写"改为按租户隔离）。
     *
     * <p>原先 {@code IN (#{tenantOid}, #{platformOid})} + {@code DISTINCT ON} + "本租户优先"排序，
     * 是实现"平台一份布局 + 租户可覆写一份"的手段；隔离后每租户随包各装一份自己的布局。
     * 参数 {@code platformOid} <b>已废弃</b>（保留仅为不改动接口与服务层签名）。
     * 注意 {@link #selectByTenant} 是"按指定租户精确取一份"（参数即目标租户，与"当前租户可见性"不同义）——
     * 克隆路径（{@code PageLayoutServiceImpl.cloneFromRootType}）2026-10-11 起改为读【本租户的根类型】，
     * 不再读平台租户那份；{@code selectByTenant} 的租户参数由调用方按需传入。
     */
    @Override
    @Select("SELECT oid, entity_oid, entity_code, operation_code, operation_name, " +
            "layout_json::text, tenant_oid, creator, created_at, updater, updated_at " +
            "FROM ck_type_page_layout " +
            "WHERE entity_oid=#{entityOid} AND tenant_oid = #{tenantOid} " +
            "ORDER BY operation_code")
    List<PageLayout> selectAllByEntity(@Param("entityOid") String entityOid,
                                       @Param("entityCode") String entityCode,
                                       @Param("tenantOid") String tenantOid,
                                       @Param("platformOid") String platformOid);

    @Override
    @Select("SELECT oid, entity_oid, entity_code, operation_code, operation_name, " +
            "layout_json::text, tenant_oid, creator, created_at, updater, updated_at " +
            "FROM ck_type_page_layout " +
            "WHERE entity_oid=#{entityOid} AND operation_code=#{operationCode} " +
            "AND tenant_oid = #{tenantOid} LIMIT 1")
    PageLayout selectByEntityAndOperation(@Param("entityOid") String entityOid,
                                          @Param("operationCode") String operationCode,
                                          @Param("tenantOid") String tenantOid,
                                          @Param("platformOid") String platformOid);

    @Override
    @Select("SELECT oid, entity_oid, entity_code, operation_code, operation_name, " +
            "layout_json::text, tenant_oid, creator, created_at, updater, updated_at " +
            "FROM ck_type_page_layout " +
            "WHERE entity_code=#{entityCode} AND operation_code=#{operationCode} " +
            "AND tenant_oid = #{tenantOid} LIMIT 1")
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
