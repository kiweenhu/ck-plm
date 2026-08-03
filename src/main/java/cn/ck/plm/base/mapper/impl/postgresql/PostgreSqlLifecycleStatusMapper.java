/*
 * Copyright (c) 2025 深圳乘恺科技有限公司
 * All rights reserved.
 *
 * @author Kiween.Hu; Roney.Liu
 */

package cn.ck.plm.base.mapper.impl.postgresql;

import cn.ck.plm.base.entity.LifecycleStatus;
import cn.ck.plm.base.mapper.LifecycleStatusMapper;
import org.apache.ibatis.annotations.*;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;

import java.util.List;

/**
 * {@link LifecycleStatusMapper} 的 PostgreSQL 实现。
 * 测试：移除所有 @Results 注解，完全依赖 MyBatis auto-mapping（下划线→驼峰 + full behavior）。
 */
@Mapper
@ConditionalOnProperty(name = "plm.database.type", havingValue = "postgresql", matchIfMissing = true)
public interface PostgreSqlLifecycleStatusMapper extends LifecycleStatusMapper {

    @Override
    @Insert("INSERT INTO ck_lifecycle_status (oid, code, name, display_name, " +
            " tenant_oid, creator, created_at, updater, updated_at) " +
            "VALUES (#{oid}, #{code}, #{name}, #{displayName}, " +
            "#{tenantOid}, #{creator}, #{createdAt}, #{updater}, #{updatedAt})")
    int insert(LifecycleStatus status);

    @Override
    @Update("UPDATE ck_lifecycle_status SET name = #{name}, display_name = #{displayName}, " +
            "tenant_oid = #{tenantOid}, updater = #{updater}, updated_at = #{updatedAt} " +
            "WHERE code = #{code}")
    int update(LifecycleStatus status);

    @Override
    @Delete("DELETE FROM ck_lifecycle_status WHERE code = #{code}")
    int deleteByCode(@Param("code") String code);

    // ==================== 查询（无 @Results，纯自动映射） ====================

    @Override
    @Select("SELECT oid, code, name, display_name, tenant_oid, creator, created_at, updater, updated_at " +
            "FROM ck_lifecycle_status WHERE code = #{code}")
    LifecycleStatus selectByCode(@Param("code") String code);

    @Override
    @Select("SELECT oid, code, name, display_name, tenant_oid, creator, created_at, updater, updated_at " +
            "FROM ck_lifecycle_status ORDER BY code ASC")
    List<LifecycleStatus> selectAll();

    @Override
    @Select("SELECT oid, code, name, display_name, tenant_oid, creator, created_at, updater, updated_at " +
            "FROM ck_lifecycle_status " +
            "WHERE LOWER(code) LIKE LOWER(CONCAT('%', #{keyword}, '%')) " +
            "OR LOWER(name) LIKE LOWER(CONCAT('%', #{keyword}, '%')) " +
            "OR LOWER(display_name) LIKE LOWER(CONCAT('%', #{keyword}, '%')) " +
            "ORDER BY code ASC")
    List<LifecycleStatus> search(@Param("keyword") String keyword);

    @Override
    @Select("SELECT COUNT(*) FROM ck_lifecycle_status WHERE code = #{code}")
    int existsByCode(@Param("code") String code);

    // ==================== 维护 ====================

    @Override
    @Update("UPDATE ck_lifecycle_status SET created_at = now(), updated_at = now() " +
            "WHERE created_at IS NULL OR updated_at IS NULL")
    int fixAllNullTimestamps();

    @Override
    @Update("UPDATE ck_lifecycle_status SET name = COALESCE(name, display_name, code), " +
            "display_name = COALESCE(display_name, name, code), updated_at = now() " +
            "WHERE name IS NULL OR display_name IS NULL")
    int fixMissingDisplayName();
}
