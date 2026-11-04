/*
 * Copyright (c) 2026~2028 深圳乘恺科技有限公司
 * All rights reserved.
 *
 * @author Kiween.Hu; Roney.Liu
 */

package cn.ck.plm.base.mapper.impl.postgresql;

import cn.ck.plm.base.entity.TenantCapability;
import cn.ck.plm.base.mapper.TenantCapabilityMapper;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Result;
import org.apache.ibatis.annotations.Results;
import org.apache.ibatis.annotations.Select;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;

import java.util.List;

/**
 * 租户能力 Mapper 的 PostgreSQL 实现。
 *
 * <p>INSERT 显式带 {@code tenant_oid}：租户拦截器检测到列已存在会原样放行
 * （见 {@code TenantStatementInterceptor#rewriteInsert}），不必依赖自动注入，
 * 也避免"当前上下文租户"与"目标租户"不一致时的意外归属。
 */
@Mapper
@ConditionalOnProperty(name = "plm.database.type", havingValue = "postgresql", matchIfMissing = true)
public interface PostgreSqlTenantCapabilityMapper extends TenantCapabilityMapper {

    String TABLE = "ck_tenant_capability";
    String COLS = "oid, tenant_oid, capability_code, capability_name, enabled, " +
                  "source_pack, note, creator, created_at, updater, updated_at";

    @Override
    @Insert("INSERT INTO " + TABLE + " (" + COLS + ") " +
            "VALUES (#{oid}, #{tenantOid}, #{capabilityCode}, #{capabilityName}, #{enabled}, " +
            "#{sourcePack}, #{note}, #{creator}, #{createdAt}, #{updater}, #{updatedAt})")
    int insert(TenantCapability capability);

    @Override
    @Select("SELECT COUNT(*) FROM " + TABLE +
            " WHERE tenant_oid = #{tenantOid} AND capability_code = #{capabilityCode}")
    int exists(@Param("tenantOid") String tenantOid, @Param("capabilityCode") String capabilityCode);

    @Override
    @Select("SELECT " + COLS + " FROM " + TABLE +
            " WHERE tenant_oid = #{tenantOid} ORDER BY capability_code")
    @Results({
            @Result(property = "oid", column = "oid"),
            @Result(property = "tenantOid", column = "tenant_oid"),
            @Result(property = "capabilityCode", column = "capability_code"),
            @Result(property = "capabilityName", column = "capability_name"),
            @Result(property = "enabled", column = "enabled"),
            @Result(property = "sourcePack", column = "source_pack"),
            @Result(property = "note", column = "note"),
            @Result(property = "creator", column = "creator"),
            @Result(property = "createdAt", column = "created_at"),
            @Result(property = "updater", column = "updater"),
            @Result(property = "updatedAt", column = "updated_at")
    })
    List<TenantCapability> selectByTenant(@Param("tenantOid") String tenantOid);
}
