/*
 * Copyright (c) 2025 深圳乘恺科技有限公司
 * All rights reserved.
 *
 * @author Kiween.Hu; Roney.Liu
 */

package cn.ck.plm.part.mapper.impl.postgresql;

import cn.ck.plm.part.entity.PartAlternateLink;
import cn.ck.plm.part.mapper.PartAlternateLinkMapper;
import org.apache.ibatis.annotations.*;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;

import java.util.List;

/**
 * PartAlternateLink PostgreSQL 持久化实现。
 */
@Mapper
@ConditionalOnProperty(name = "plm.database.type", havingValue = "postgresql", matchIfMissing = true)
public interface PostgreSqlPartAlternateLinkMapper extends PartAlternateLinkMapper {

    @Override
    @Insert("INSERT INTO ck_part_alternate_link (oid, code, name, description, " +
            "role_a_part_oid, role_b_part_oid, alternate_type, alternate_quantity, alternate_unit, " +
            "enabled, effectivity_json, tenant_oid, creator, created_at, updater, updated_at) " +
            "VALUES (#{oid}, #{code}, #{name}, #{description}, " +
            "#{roleAPartOid}, #{roleBPartOid}, #{alternateType}, #{alternateQuantity}, #{alternateUnit}, " +
            "#{enabled}, #{effectivityJson}::jsonb, #{tenantOid}, #{creator}, #{createdAt}, #{updater}, #{updatedAt})")
    int insert(PartAlternateLink link);

    @Override
    @Update("UPDATE ck_part_alternate_link SET " +
            "code = #{code}, name = #{name}, description = #{description}, " +
            "role_a_part_oid = #{roleAPartOid}, role_b_part_oid = #{roleBPartOid}, " +
            "alternate_type = #{alternateType}, alternate_quantity = #{alternateQuantity}, alternate_unit = #{alternateUnit}, " +
            "enabled = #{enabled}, effectivity_json = #{effectivityJson}::jsonb, " +
            "updater = #{updater}, updated_at = #{updatedAt} " +
            "WHERE oid = #{oid}")
    int update(PartAlternateLink link);

    @Override
    @Delete("DELETE FROM ck_part_alternate_link WHERE oid = #{oid}")
    int deleteByOid(@Param("oid") String oid);

    String SELECT_COLUMNS = "SELECT oid, code, name, description, " +
            "role_a_part_oid, role_b_part_oid, alternate_type, alternate_quantity, alternate_unit, " +
            "enabled, effectivity_json, tenant_oid, creator, created_at, updater, updated_at FROM ck_part_alternate_link ";

    @Override
    @Select(SELECT_COLUMNS + "WHERE oid = #{oid}")
    @Results(id = "partAlternateLinkResult", value = {
            @Result(property = "oid",                 column = "oid"),
            @Result(property = "code",                column = "code"),
            @Result(property = "name",                column = "name"),
            @Result(property = "description",         column = "description"),
            @Result(property = "roleAPartOid",        column = "role_a_part_oid"),
            @Result(property = "roleBPartOid",        column = "role_b_part_oid"),
            @Result(property = "alternateType",       column = "alternate_type"),
            @Result(property = "alternateQuantity",   column = "alternate_quantity"),
            @Result(property = "alternateUnit",       column = "alternate_unit"),
            @Result(property = "enabled",             column = "enabled"),
            @Result(property = "effectivityJson",     column = "effectivity_json"),
            @Result(property = "tenantOid",           column = "tenant_oid"),
            @Result(property = "creator",             column = "creator"),
            @Result(property = "createdAt",           column = "created_at"),
            @Result(property = "updater",             column = "updater"),
            @Result(property = "updatedAt",           column = "updated_at")
    })
    PartAlternateLink selectByOid(@Param("oid") String oid);

    @Override
    @Select(SELECT_COLUMNS + "WHERE role_a_part_oid = #{roleAPartOid} ORDER BY created_at DESC")
    @ResultMap("partAlternateLinkResult")
    List<PartAlternateLink> selectByRoleAPartOid(@Param("roleAPartOid") String roleAPartOid);

    @Override
    @Select(SELECT_COLUMNS + "WHERE role_b_part_oid = #{roleBPartOid} ORDER BY created_at DESC")
    @ResultMap("partAlternateLinkResult")
    List<PartAlternateLink> selectByRoleBPartOid(@Param("roleBPartOid") String roleBPartOid);

    @Override
    @Select(SELECT_COLUMNS + "WHERE role_a_part_oid = #{partOid} OR role_b_part_oid = #{partOid} ORDER BY created_at DESC")
    @ResultMap("partAlternateLinkResult")
    List<PartAlternateLink> selectByPartOid(@Param("partOid") String partOid);

    @Override
    @Select(SELECT_COLUMNS)
    @ResultMap("partAlternateLinkResult")
    List<PartAlternateLink> selectAll();
}
