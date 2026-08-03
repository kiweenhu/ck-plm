/*
 * Copyright (c) 2025 深圳乘恺科技有限公司
 * All rights reserved.
 *
 * @author Kiween.Hu; Roney.Liu
 */

package cn.ck.plm.iam.mapper.impl.postgresql;

import cn.ck.plm.iam.entity.User;
import cn.ck.plm.iam.mapper.UserMapper;
import org.apache.ibatis.annotations.*;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;

import java.util.List;

/**
 * {@link UserMapper} 的 PostgreSQL 实现（MyBatis 注解模式）。
 */
@Mapper
@ConditionalOnProperty(name = "plm.database.type", havingValue = "postgresql", matchIfMissing = true)
public interface PostgreSqlUserMapper extends UserMapper {

    @Override
    @Insert("INSERT INTO ck_user (oid, username, password, display_name, email, phone, " +
            "org_oid, enabled, locked, tenant_oid, creator, created_at, updater, updated_at) " +
            "VALUES (#{oid}, #{username}, #{password}, #{displayName}, #{email}, #{phone}, " +
            "#{orgOid}, #{enabled}, #{locked}, #{tenantOid}, #{creator}, #{createdAt}, #{updater}, #{updatedAt})")
    int insert(User user);

    @Override
    @Update("UPDATE ck_user SET display_name = #{displayName}, email = #{email}, " +
            "phone = #{phone}, org_oid = #{orgOid}, enabled = #{enabled}, locked = #{locked}, " +
            "tenant_oid = #{tenantOid}, updater = #{updater}, updated_at = #{updatedAt} WHERE oid = #{oid}")
    int update(User user);

    @Override
    @Update("UPDATE ck_user SET display_name = #{displayName}, email = #{email}, " +
            "phone = #{phone}, updater = #{updater}, updated_at = #{updatedAt} WHERE oid = #{oid}")
    int updateProfile(User user);

    @Override
    @Update("UPDATE ck_user SET password = #{password}, updater = #{updater}, " +
            "updated_at = #{updatedAt} WHERE oid = #{oid}")
    int updatePassword(User user);

    @Override
    @Delete("DELETE FROM ck_user WHERE oid = #{oid}")
    int deleteByOid(@Param("oid") String oid);

    // ==================== 查询映射定义 ====================

    @Select("SELECT oid, username, password, display_name, email, phone, " +
            "org_oid, enabled, locked, tenant_oid, creator, created_at, updater, updated_at " +
            "FROM ck_user WHERE oid = #{oid}")
    @Results(id = "userResult", value = {
            @Result(property = "oid",         column = "oid"),
            @Result(property = "username",    column = "username"),
            @Result(property = "password",    column = "password"),
            @Result(property = "displayName", column = "display_name"),
            @Result(property = "email",       column = "email"),
            @Result(property = "phone",       column = "phone"),
            @Result(property = "orgOid",      column = "org_oid"),
            @Result(property = "enabled",     column = "enabled"),
            @Result(property = "locked",      column = "locked"),
            @Result(property = "tenantOid",   column = "tenant_oid"),
            @Result(property = "creator",     column = "creator"),
            @Result(property = "createdAt",   column = "created_at"),
            @Result(property = "updater",     column = "updater"),
            @Result(property = "updatedAt",   column = "updated_at")
    })
    @Override
    User selectByOid(@Param("oid") String oid);

    @Select("SELECT oid, username, password, display_name, email, phone, " +
            "org_oid, enabled, locked, tenant_oid, creator, created_at, updater, updated_at " +
            "FROM ck_user WHERE username = #{username}")
    @ResultMap("userResult")
    @Override
    User selectByUsername(@Param("username") String username);

    @Select("SELECT oid, username, password, display_name, email, phone, " +
            "org_oid, enabled, locked, tenant_oid, creator, created_at, updater, updated_at " +
            "FROM ck_user WHERE tenant_oid = #{tenantOid} ORDER BY username ASC")
    @ResultMap("userResult")
    @Override
    List<User> selectAll(@Param("tenantOid") String tenantOid);

    @Select("SELECT oid, username, password, display_name, email, phone, " +
            "org_oid, enabled, locked, tenant_oid, creator, created_at, updater, updated_at " +
            "FROM ck_user WHERE org_oid = #{orgOid} AND tenant_oid = #{tenantOid} ORDER BY username ASC")
    @ResultMap("userResult")
    @Override
    List<User> selectByOrgOid(@Param("orgOid") String orgOid, @Param("tenantOid") String tenantOid);

    @Select("SELECT oid, username, password, display_name, email, phone, " +
            "org_oid, enabled, locked, tenant_oid, creator, created_at, updater, updated_at " +
            "FROM ck_user " +
            "WHERE tenant_oid = #{tenantOid} AND (" +
            "LOWER(username) LIKE LOWER('%' || #{keyword} || '%') " +
            "OR LOWER(display_name) LIKE LOWER('%' || #{keyword} || '%')) " +
            "ORDER BY username ASC")
    @ResultMap("userResult")
    @Override
    List<User> search(@Param("keyword") String keyword, @Param("tenantOid") String tenantOid);

    @Select("SELECT COUNT(*) FROM ck_user WHERE username = #{username}")
    @Override
    int existsByUsername(@Param("username") String username);

    @Select("SELECT u.oid, u.username, u.password, u.display_name, u.email, u.phone, "
            + "u.org_oid, u.enabled, u.locked, u.tenant_oid, u.creator, u.created_at, u.updater, u.updated_at "
            + "FROM ck_user u INNER JOIN ck_role_member rm ON u.oid = rm.user_oid "
            + "WHERE rm.role_oid = #{roleOid} ORDER BY u.username ASC")
    @ResultMap("userResult")
    @Override
    List<User> selectByRoleOid(@Param("roleOid") String roleOid);
}
