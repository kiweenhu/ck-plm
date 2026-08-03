/*
 * Copyright (c) 2025 深圳乘恺科技有限公司
 * All rights reserved.
 *
 * @author Kiween.Hu; Roney.Liu
 */

package cn.ck.plm.iam.mapper.impl.postgresql;

import cn.ck.plm.iam.entity.RoleMember;
import cn.ck.plm.iam.mapper.RoleMemberMapper;
import org.apache.ibatis.annotations.*;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;

import java.util.List;

/**
 * {@link RoleMemberMapper} 的 PostgreSQL 实现（MyBatis 注解模式）。
 */
@Mapper
@ConditionalOnProperty(name = "plm.database.type", havingValue = "postgresql", matchIfMissing = true)
public interface PostgreSqlRoleMemberMapper extends RoleMemberMapper {

    @Override
    @Insert("INSERT INTO ck_role_member (oid, user_oid, role_oid, tenant_oid, creator, created_at, updater, updated_at) " +
            "VALUES (#{oid}, #{userOid}, #{roleOid}, #{tenantOid}, #{creator}, #{createdAt}, #{updater}, #{updatedAt})")
    int insert(RoleMember roleMember);

    @Override
    @Delete("DELETE FROM ck_role_member WHERE oid = #{oid}")
    int deleteByOid(@Param("oid") String oid);

    @Override
    @Delete("DELETE FROM ck_role_member WHERE user_oid = #{userOid} AND role_oid = #{roleOid}")
    int deleteByUserOidAndRoleOid(@Param("userOid") String userOid, @Param("roleOid") String roleOid);

    @Override
    @Delete("DELETE FROM ck_role_member WHERE user_oid = #{userOid}")
    int deleteByUserOid(@Param("userOid") String userOid);

    // ==================== 查询映射定义 ====================

    @Select("SELECT oid, user_oid, role_oid, creator, created_at, updater, updated_at " +
            "FROM ck_role_member WHERE user_oid = #{userOid}")
    @Results(id = "roleMemberResult", value = {
            @Result(property = "oid",       column = "oid"),
            @Result(property = "userOid",   column = "user_oid"),
            @Result(property = "roleOid",   column = "role_oid"),
            @Result(property = "creator",   column = "creator"),
            @Result(property = "createdAt", column = "created_at"),
            @Result(property = "updater",   column = "updater"),
            @Result(property = "updatedAt", column = "updated_at")
    })
    @Override
    List<RoleMember> selectByUserOid(@Param("userOid") String userOid);

    @Select("SELECT oid, user_oid, role_oid, creator, created_at, updater, updated_at " +
            "FROM ck_role_member WHERE role_oid = #{roleOid}")
    @ResultMap("roleMemberResult")
    @Override
    List<RoleMember> selectByRoleOid(@Param("roleOid") String roleOid);
}
