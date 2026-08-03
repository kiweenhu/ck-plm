/*
 * Copyright (c) 2025 深圳乘恺科技有限公司
 * All rights reserved.
 *
 * @author Kiween.Hu; Roney.Liu
 */

package cn.ck.plm.iam.mapper.impl.postgresql;

import cn.ck.plm.iam.entity.Role;
import cn.ck.plm.iam.mapper.RoleMapper;
import org.apache.ibatis.annotations.*;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;

import java.util.List;

/**
 * {@link RoleMapper} 的 PostgreSQL 实现（MyBatis 注解模式）。
 */
@Mapper
@ConditionalOnProperty(name = "plm.database.type", havingValue = "postgresql", matchIfMissing = true)
public interface PostgreSqlRoleMapper extends RoleMapper {

    @Override
    @Insert("INSERT INTO ck_role (oid, code, name, description, role_type, creator, created_at, updater, updated_at) " +
            "VALUES (#{oid}, #{code}, #{name}, #{description}, #{roleType}, #{creator}, #{createdAt}, #{updater}, #{updatedAt})")
    int insert(Role role);

    @Override
    @Update("UPDATE ck_role SET name = #{name}, description = #{description}, " +
            "updater = #{updater}, updated_at = #{updatedAt} WHERE oid = #{oid}")
    int update(Role role);

    @Override
    @Delete("DELETE FROM ck_role WHERE oid = #{oid}")
    int deleteByOid(@Param("oid") String oid);

    // ==================== 查询映射定义 ====================

    @Select("SELECT oid, code, name, description, role_type, creator, created_at, updater, updated_at " +
            "FROM ck_role WHERE oid = #{oid}")
    @Results(id = "roleResult", value = {
            @Result(property = "oid",         column = "oid"),
            @Result(property = "code",        column = "code"),
            @Result(property = "name",        column = "name"),
            @Result(property = "description", column = "description"),
            @Result(property = "roleType",    column = "role_type"),
            @Result(property = "creator",     column = "creator"),
            @Result(property = "createdAt",   column = "created_at"),
            @Result(property = "updater",     column = "updater"),
            @Result(property = "updatedAt",   column = "updated_at")
    })
    @Override
    Role selectByOid(@Param("oid") String oid);

    @Select("SELECT oid, code, name, description, role_type, creator, created_at, updater, updated_at " +
            "FROM ck_role WHERE code = #{code}")
    @ResultMap("roleResult")
    @Override
    Role selectByCode(@Param("code") String code);

    @Select("SELECT oid, code, name, description, role_type, creator, created_at, updater, updated_at " +
            "FROM ck_role ORDER BY code ASC")
    @ResultMap("roleResult")
    @Override
    List<Role> selectAll();

    @Select("SELECT r.oid, r.code, r.name, r.description, r.role_type, r.creator, r.created_at, r.updater, r.updated_at " +
            "FROM ck_role r INNER JOIN ck_role_member rm ON r.oid = rm.role_oid " +
            "WHERE rm.user_oid = #{userOid} ORDER BY r.code ASC")
    @ResultMap("roleResult")
    @Override
    List<Role> selectByUserOid(@Param("userOid") String userOid);

    @Select("SELECT oid, code, name, description, role_type, creator, created_at, updater, updated_at " +
            "FROM ck_role " +
            "WHERE LOWER(code) LIKE LOWER('%' || #{keyword} || '%') " +
            "OR LOWER(name) LIKE LOWER('%' || #{keyword} || '%') " +
            "ORDER BY code ASC")
    @ResultMap("roleResult")
    @Override
    List<Role> search(@Param("keyword") String keyword);

    @Select("SELECT COUNT(*) FROM ck_role WHERE code = #{code}")
    @Override
    int existsByCode(@Param("code") String code);
}
