/*
 * Copyright (c) 2025 深圳乘恺科技有限公司
 * All rights reserved.
 *
 * @author Kiween.Hu; Roney.Liu
 */

package cn.ck.plm.softtype.mapper.impl.postgresql;

import cn.ck.plm.softtype.entity.TypeDefinition;
import cn.ck.plm.softtype.mapper.TypeDefinitionMapper;
import org.apache.ibatis.annotations.*;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;

import java.util.List;

/**
 * {@link TypeDefinitionMapper} 的 PostgreSQL 实现。
 */
@Mapper
@ConditionalOnProperty(name = "plm.database.type", havingValue = "postgresql", matchIfMissing = true)
public interface PostgreSqlTypeDefinitionMapper extends TypeDefinitionMapper {

    @Override
    @Insert("INSERT INTO ck_type_definition (oid, code, name, icon, source, type_kind, parent_oid, root_type_code, " +
            "description, sort_order, enabled, tenant_oid, creator, created_at, updater, updated_at) " +
            "VALUES (#{oid}, #{code}, #{name}, #{icon}, #{source}, #{typeKind}, #{parentOid}, #{rootTypeCode}, " +
            "#{description}, #{sortOrder}, #{enabled}, #{tenantOid}, #{creator}, #{createdAt}, #{updater}, #{updatedAt})")
    int insert(TypeDefinition td);

    @Override
    @Update("UPDATE ck_type_definition SET name = #{name}, icon = #{icon}, description = #{description}, " +
            "sort_order = #{sortOrder}, enabled = #{enabled}, tenant_oid = #{tenantOid}, " +
            "updater = #{updater}, updated_at = #{updatedAt} WHERE oid = #{oid}")
    int update(TypeDefinition td);

    @Override
    @Delete("DELETE FROM ck_type_definition WHERE oid = #{oid}")
    int deleteByOid(@Param("oid") String oid);

    // ==================== 查询映射 ====================

    @Select("SELECT oid, code, name, icon, source, type_kind, parent_oid, root_type_code, " +
            "description, sort_order, enabled, tenant_oid, creator, created_at, updater, updated_at " +
            "FROM ck_type_definition WHERE oid = #{oid}")
    @Results(id = "tdResult", value = {
            @Result(property = "oid",         column = "oid"),
            @Result(property = "code",        column = "code"),
            @Result(property = "name",        column = "name"),
            @Result(property = "icon",        column = "icon"),
            @Result(property = "source",      column = "source"),
            @Result(property = "typeKind",    column = "type_kind"),
            @Result(property = "parentOid",   column = "parent_oid"),
            @Result(property = "rootTypeCode",column = "root_type_code"),
            @Result(property = "description", column = "description"),
            @Result(property = "sortOrder",   column = "sort_order"),
            @Result(property = "enabled",     column = "enabled"),
            @Result(property = "tenantOid",   column = "tenant_oid"),
            @Result(property = "creator",     column = "creator"),
            @Result(property = "createdAt",   column = "created_at"),
            @Result(property = "updater",     column = "updater"),
            @Result(property = "updatedAt",   column = "updated_at")
    })
    @Override
    TypeDefinition selectByOid(@Param("oid") String oid);

    @Select("SELECT oid, code, name, icon, source, type_kind, parent_oid, root_type_code, " +
            "description, sort_order, enabled, tenant_oid, creator, created_at, updater, updated_at " +
            "FROM ck_type_definition WHERE code = #{code} " +
            "AND tenant_oid IN (#{tenantOid}, #{platformOid}) " +
            "ORDER BY CASE WHEN tenant_oid = #{tenantOid} THEN 0 ELSE 1 END LIMIT 1")
    @ResultMap("tdResult")
    @Override
    TypeDefinition selectByCode(@Param("code") String code,
                                @Param("tenantOid") String tenantOid,
                                @Param("platformOid") String platformOid);

    /**
     * 查询全部类型（平台 + 本租户），优先本租户
     */
    @Select("SELECT DISTINCT ON (code) oid, code, name, icon, source, type_kind, parent_oid, root_type_code, " +
            "description, sort_order, enabled, tenant_oid, creator, created_at, updater, updated_at " +
            "FROM ck_type_definition " +
            "WHERE tenant_oid IN (#{tenantOid}, #{platformOid}) " +
            "ORDER BY code, CASE WHEN tenant_oid = #{tenantOid} THEN 0 ELSE 1 END")
    @ResultMap("tdResult")
    @Override
    List<TypeDefinition> selectAll(@Param("tenantOid") String tenantOid,
                                   @Param("platformOid") String platformOid);

    /**
     * 查询已启用的类型（平台 + 本租户），优先本租户
     */
    @Select("SELECT DISTINCT ON (code) oid, code, name, icon, source, type_kind, parent_oid, root_type_code, " +
            "description, sort_order, enabled, tenant_oid, creator, created_at, updater, updated_at " +
            "FROM ck_type_definition " +
            "WHERE enabled = true AND tenant_oid IN (#{tenantOid}, #{platformOid}) " +
            "ORDER BY code, CASE WHEN tenant_oid = #{tenantOid} THEN 0 ELSE 1 END")
    @ResultMap("tdResult")
    @Override
    List<TypeDefinition> selectEnabled(@Param("tenantOid") String tenantOid,
                                        @Param("platformOid") String platformOid);

    @Select("SELECT DISTINCT ON (code) oid, code, name, icon, source, type_kind, parent_oid, root_type_code, " +
            "description, sort_order, enabled, tenant_oid, creator, created_at, updater, updated_at " +
            "FROM ck_type_definition " +
            "WHERE type_kind = #{typeKind} AND tenant_oid IN (#{tenantOid}, #{platformOid}) " +
            "ORDER BY code, CASE WHEN tenant_oid = #{tenantOid} THEN 0 ELSE 1 END")
    @ResultMap("tdResult")
    @Override
    List<TypeDefinition> selectByTypeKind(@Param("typeKind") String typeKind,
                                           @Param("tenantOid") String tenantOid,
                                           @Param("platformOid") String platformOid);

    @Select("SELECT DISTINCT ON (code) oid, code, name, icon, source, type_kind, parent_oid, root_type_code, " +
            "description, sort_order, enabled, tenant_oid, creator, created_at, updater, updated_at " +
            "FROM ck_type_definition " +
            "WHERE parent_oid = #{parentOid} AND tenant_oid IN (#{tenantOid}, #{platformOid}) " +
            "ORDER BY code, CASE WHEN tenant_oid = #{tenantOid} THEN 0 ELSE 1 END")
    @ResultMap("tdResult")
    @Override
    List<TypeDefinition> selectByParentOid(@Param("parentOid") String parentOid,
                                            @Param("tenantOid") String tenantOid,
                                            @Param("platformOid") String platformOid);

    @Select("SELECT DISTINCT ON (code) oid, code, name, icon, source, type_kind, parent_oid, root_type_code, " +
            "description, sort_order, enabled, tenant_oid, creator, created_at, updater, updated_at " +
            "FROM ck_type_definition " +
            "WHERE parent_oid IS NULL AND tenant_oid IN (#{tenantOid}, #{platformOid}) " +
            "ORDER BY code, CASE WHEN tenant_oid = #{tenantOid} THEN 0 ELSE 1 END")
    @ResultMap("tdResult")
    @Override
    List<TypeDefinition> selectRoots(@Param("tenantOid") String tenantOid,
                                      @Param("platformOid") String platformOid);

    @Select("SELECT COUNT(*) FROM ck_type_definition WHERE code = #{code} " +
            "AND tenant_oid IN (#{tenantOid}, #{platformOid})")
    @Override
    int existsByCode(@Param("code") String code,
                     @Param("tenantOid") String tenantOid,
                     @Param("platformOid") String platformOid);

    @Select("SELECT COUNT(*) FROM ck_type_definition WHERE parent_oid = #{oid}")
    @Override
    int countChildren(@Param("oid") String oid);

    @Select("SELECT COUNT(*) FROM ck_type_iba WHERE type_oid = #{oid}")
    @Override
    int countIbaMappings(@Param("oid") String oid);

    @Override
    @Update("DO $$ BEGIN " +
            "  IF NOT EXISTS (SELECT 1 FROM information_schema.columns " +
            "    WHERE table_name='ck_type_definition' AND column_name='root_type_code') THEN " +
            "    ALTER TABLE ck_type_definition ADD COLUMN root_type_code VARCHAR(50); " +
            "  END IF; " +
            "END $$")
    void addRootTypeCodeColumn();

    @Override
    @Update("UPDATE ck_type_definition SET root_type_code = #{rootTypeCode} WHERE oid = #{oid}")
    int updateRootTypeCode(@Param("oid") String oid, @Param("rootTypeCode") String rootTypeCode);

    @Override
    @Update("UPDATE ck_type_definition SET root_type_code = code " +
            "WHERE type_kind = 'OOTB' AND root_type_code IS NULL")
    int patchRootTypeCodeForOotb();
}
