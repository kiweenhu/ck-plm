/*
 * Copyright (c) 2025 深圳乘恺科技有限公司
 * All rights reserved.
 *
 * @author Kiween.Hu; Roney.Liu
 */

package cn.ck.plm.integration.mapper;

import cn.ck.plm.integration.entity.TargetSystem;
import org.apache.ibatis.annotations.*;

import java.util.List;

/**
 * 目标系统注册表 Mapper（手写注解 SQL，与 {@code FileStorageConfigMapper} 同一写法）。
 *
 * <p>本表是<b>业务表</b>（含 {@code tenant_oid}），未列入 {@code TenantStatementInterceptor}
 * 的 SHARED / PLATFORM_SHARED 白名单 —— 查询会被自动追加 {@code tenant_oid = 当前租户}，
 * 插入会被自动补上租户列。因此下面按 oid / 全量的查询天然是租户内的。
 *
 * <p>运行期（流程实例回调线程）另有一份<b>显式带租户</b>的查询
 * （{@link #selectByCodeAndTenant}）：那一刻的租户上下文不由本 Mapper 决定，
 * 显式传参比"依赖全局拦截器"更好排查。
 */
@Mapper
public interface TargetSystemMapper {

    @Insert("INSERT INTO ck_target_system (oid, code, name, base_url, auth_type, username, secret, "
            + "enabled, sort_order, description, tenant_oid, creator, created_at, updater, updated_at) "
            + "VALUES (#{oid}, #{code}, #{name}, #{baseUrl}, #{authType}, #{username}, #{secret}, "
            + "#{enabled}, #{sortOrder}, #{description}, #{tenantOid}, #{creator}, #{createdAt}, "
            + "#{updater}, #{updatedAt})")
    void insert(TargetSystem system);

    @Update("UPDATE ck_target_system SET code=#{code}, name=#{name}, base_url=#{baseUrl}, "
            + "auth_type=#{authType}, username=#{username}, secret=#{secret}, enabled=#{enabled}, "
            + "sort_order=#{sortOrder}, description=#{description}, updater=#{updater}, updated_at=#{updatedAt} "
            + "WHERE oid=#{oid}")
    void update(TargetSystem system);

    @Delete("DELETE FROM ck_target_system WHERE oid=#{oid}")
    void deleteByOid(String oid);

    @Select("SELECT * FROM ck_target_system WHERE oid=#{oid}")
    @Results(id = "targetSystemMap", value = {
        @Result(property = "baseUrl", column = "base_url"),
        @Result(property = "authType", column = "auth_type"),
        @Result(property = "sortOrder", column = "sort_order"),
        @Result(property = "tenantOid", column = "tenant_oid"),
        @Result(property = "createdAt", column = "created_at"),
        @Result(property = "updatedAt", column = "updated_at"),
    })
    TargetSystem selectByOid(String oid);

    @Select("SELECT * FROM ck_target_system ORDER BY sort_order, code")
    @ResultMap("targetSystemMap")
    List<TargetSystem> selectAll();

    @Select("SELECT * FROM ck_target_system WHERE tenant_oid = #{tenantOid} ORDER BY sort_order, code")
    @ResultMap("targetSystemMap")
    List<TargetSystem> selectByTenant(@Param("tenantOid") String tenantOid);

    /**
     * 按编码取（运行期出站调用用）。
     *
     * <p>显式带租户：流程实例属于哪个租户，就只能取到那个租户注册的系统。
     */
    @Select("SELECT * FROM ck_target_system WHERE code = #{code} AND tenant_oid = #{tenantOid}")
    @ResultMap("targetSystemMap")
    TargetSystem selectByCodeAndTenant(@Param("code") String code, @Param("tenantOid") String tenantOid);
}
