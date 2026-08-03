/*
 * Copyright (c) 2025 深圳乘恺科技有限公司
 * All rights reserved.
 *
 * @author Kiween.Hu; Roney.Liu
 */

package cn.ck.plm.iam.mapper.impl.postgresql;

import cn.ck.plm.iam.entity.Organization;
import cn.ck.plm.iam.mapper.OrganizationMapper;
import org.apache.ibatis.annotations.*;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;

import java.util.List;

/**
 * {@link OrganizationMapper} 的 PostgreSQL 实现（MyBatis 注解模式）。
 */
@Mapper
@ConditionalOnProperty(name = "plm.database.type", havingValue = "postgresql", matchIfMissing = true)
public interface PostgreSqlOrganizationMapper extends OrganizationMapper {

    @Override
    @Insert("INSERT INTO ck_organization (oid, code, name, parent_oid, description, enabled, " +
            " creator, created_at, updater, updated_at) " +
            "VALUES (#{oid}, #{code}, #{name}, #{parentOid}, #{description}, #{enabled}, " +
            "#{creator}, #{createdAt}, #{updater}, #{updatedAt})")
    int insert(Organization org);

    @Override
    @Update("UPDATE ck_organization SET name = #{name}, parent_oid = #{parentOid}, " +
            "description = #{description}, enabled = #{enabled}, " +
            "updater = #{updater}, updated_at = #{updatedAt} WHERE oid = #{oid}")
    int update(Organization org);

    @Override
    @Delete("DELETE FROM ck_organization WHERE oid = #{oid}")
    int deleteByOid(@Param("oid") String oid);

    // ==================== 查询映射定义 ====================

    @Select("SELECT oid, code, name, parent_oid, description, enabled, " +
            " creator, created_at, updater, updated_at " +
            "FROM ck_organization WHERE oid = #{oid}")
    @Results(id = "orgResult", value = {
            @Result(property = "oid",         column = "oid"),
            @Result(property = "code",        column = "code"),
            @Result(property = "name",        column = "name"),
            @Result(property = "parentOid",   column = "parent_oid"),
            @Result(property = "description", column = "description"),
            @Result(property = "enabled",     column = "enabled"),
            @Result(property = "creator",     column = "creator"),
            @Result(property = "createdAt",   column = "created_at"),
            @Result(property = "updater",     column = "updater"),
            @Result(property = "updatedAt",   column = "updated_at")
    })
    @Override
    Organization selectByOid(@Param("oid") String oid);

    @Select("SELECT oid, code, name, parent_oid, description, enabled, " +
            " creator, created_at, updater, updated_at " +
            "FROM ck_organization WHERE code = #{code}")
    @ResultMap("orgResult")
    @Override
    Organization selectByCode(@Param("code") String code);

    @Select("SELECT oid, code, name, parent_oid, description, enabled, " +
            " creator, created_at, updater, updated_at " +
            "FROM ck_organization ORDER BY code ASC")
    @ResultMap("orgResult")
    @Override
    List<Organization> selectAll();

    @Select("SELECT oid, code, name, parent_oid, description, enabled, " +
            " creator, created_at, updater, updated_at " +
            "FROM ck_organization WHERE parent_oid = #{parentOid} ORDER BY code ASC")
    @ResultMap("orgResult")
    @Override
    List<Organization> selectByParentOid(@Param("parentOid") String parentOid);

    @Select("SELECT oid, code, name, parent_oid, description, enabled, " +
            " creator, created_at, updater, updated_at " +
            "FROM ck_organization WHERE parent_oid IS NULL ORDER BY code ASC")
    @ResultMap("orgResult")
    @Override
    List<Organization> selectRoots();

    @Select("SELECT oid, code, name, parent_oid, description, enabled, " +
            " creator, created_at, updater, updated_at " +
            "FROM ck_organization " +
            "WHERE LOWER(code) LIKE LOWER('%' || #{keyword} || '%') " +
            "OR LOWER(name) LIKE LOWER('%' || #{keyword} || '%') " +
            "ORDER BY code ASC")
    @ResultMap("orgResult")
    @Override
    List<Organization> search(@Param("keyword") String keyword);

    @Select("SELECT COUNT(*) FROM ck_organization WHERE code = #{code}")
    @Override
    int existsByCode(@Param("code") String code);
}
