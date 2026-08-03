/*
 * Copyright (c) 2025 深圳乘恺科技有限公司
 * All rights reserved.
 *
 * @author Kiween.Hu; Roney.Liu
 */

package cn.ck.plm.softtype.mapper.impl.postgresql;

import cn.ck.plm.softtype.entity.TypeIBA;
import cn.ck.plm.softtype.mapper.TypeIBAMapper;
import org.apache.ibatis.annotations.*;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;

import java.util.List;

/**
 * {@link TypeIBAMapper} 的 PostgreSQL 实现。
 */
@Mapper
@ConditionalOnProperty(name = "plm.database.type", havingValue = "postgresql", matchIfMissing = true)
public interface PostgreSqlTypeIBAMapper extends TypeIBAMapper {

    @Override
    @Insert("INSERT INTO ck_type_iba (oid, type_oid, entity_code, iba_oid, required, default_value, " +
            "sort_order, tenant_oid, creator, created_at, updater, updated_at) " +
            "VALUES (#{oid}, #{typeOid}, #{entityCode}, #{ibaOid}, #{required}, #{defaultValue}, " +
            "#{sortOrder}, #{tenantOid}, #{creator}, #{createdAt}, #{updater}, #{updatedAt})")
    int insert(TypeIBA mapping);

    @Override
    @Update("UPDATE ck_type_iba SET required = #{required}, default_value = #{defaultValue}, " +
            "sort_order = #{sortOrder}, updater = #{updater}, updated_at = #{updatedAt} " +
            "WHERE oid = #{oid}")
    int update(TypeIBA mapping);

    @Override
    @Delete("DELETE FROM ck_type_iba WHERE oid = #{oid}")
    int deleteByOid(@Param("oid") String oid);

    // ==================== 查询映射 ====================

    @Select("SELECT ti.oid, ti.type_oid, ti.entity_code, ti.iba_oid, ti.required, ti.default_value, " +
            "ti.sort_order, ti.tenant_oid, ti.creator, ti.created_at, ti.updater, ti.updated_at, " +
            "i.code AS iba_code, i.name AS iba_name, i.display_name AS iba_display_name, " +
            "i.data_type AS iba_data_type " +
            "FROM ck_type_iba ti " +
            "INNER JOIN ck_iba i ON ti.iba_oid = i.oid " +
            "WHERE ti.oid = #{oid}")
    @Results(id = "tiResult", value = {
            @Result(property = "oid",            column = "oid"),
            @Result(property = "typeOid",        column = "type_oid"),
            @Result(property = "entityCode",     column = "entity_code"),
            @Result(property = "ibaOid",         column = "iba_oid"),
            @Result(property = "required",       column = "required"),
            @Result(property = "defaultValue",   column = "default_value"),
            @Result(property = "sortOrder",      column = "sort_order"),
            @Result(property = "tenantOid",      column = "tenant_oid"),
            @Result(property = "creator",        column = "creator"),
            @Result(property = "createdAt",      column = "created_at"),
            @Result(property = "updater",        column = "updater"),
            @Result(property = "updatedAt",      column = "updated_at"),
            @Result(property = "ibaCode",        column = "iba_code"),
            @Result(property = "ibaName",        column = "iba_name"),
            @Result(property = "ibaDisplayName", column = "iba_display_name"),
            @Result(property = "ibaDataType",    column = "iba_data_type")
    })
    @Override
    TypeIBA selectByOid(@Param("oid") String oid);

    @Select("SELECT ti.oid, ti.type_oid, ti.entity_code, ti.iba_oid, ti.required, ti.default_value, " +
            "ti.sort_order, ti.tenant_oid, ti.creator, ti.created_at, ti.updater, ti.updated_at, " +
            "i.code AS iba_code, i.name AS iba_name, i.display_name AS iba_display_name, " +
            "i.data_type AS iba_data_type " +
            "FROM ck_type_iba ti " +
            "INNER JOIN ck_iba i ON ti.iba_oid = i.oid " +
            "WHERE ti.type_oid = #{typeOid} " +
            "ORDER BY ti.sort_order ASC, i.sort_order ASC")
    @ResultMap("tiResult")
    @Override
    List<TypeIBA> selectByTypeOid(@Param("typeOid") String typeOid);

    @Select("SELECT ti.oid, ti.type_oid, ti.entity_code, ti.iba_oid, ti.required, ti.default_value, " +
            "ti.sort_order, ti.tenant_oid, ti.creator, ti.created_at, ti.updater, ti.updated_at, " +
            "i.code AS iba_code, i.name AS iba_name, i.display_name AS iba_display_name, " +
            "i.data_type AS iba_data_type " +
            "FROM ck_type_iba ti " +
            "INNER JOIN ck_iba i ON ti.iba_oid = i.oid " +
            "WHERE ti.type_oid = #{ownerOid} AND ti.entity_code = #{entityCode} " +
            "ORDER BY ti.sort_order ASC, i.sort_order ASC")
    @ResultMap("tiResult")
    @Override
    List<TypeIBA> selectByOwnerOid(@Param("ownerOid") String ownerOid,
                                   @Param("entityCode") String entityCode);

    @Select("SELECT ti.oid, ti.type_oid, ti.entity_code, ti.iba_oid, ti.required, ti.default_value, " +
            "ti.sort_order, ti.tenant_oid, ti.creator, ti.created_at, ti.updater, ti.updated_at, " +
            "i.code AS iba_code, i.name AS iba_name, i.display_name AS iba_display_name, " +
            "i.data_type AS iba_data_type " +
            "FROM ck_type_iba ti " +
            "INNER JOIN ck_iba i ON ti.iba_oid = i.oid " +
            "WHERE ti.iba_oid = #{ibaOid} " +
            "ORDER BY ti.type_oid ASC")
    @ResultMap("tiResult")
    @Override
    List<TypeIBA> selectByIbaOid(@Param("ibaOid") String ibaOid);

    @Select("SELECT COUNT(*) FROM ck_type_iba WHERE type_oid = #{typeOid} AND iba_oid = #{ibaOid}")
    @Override
    int existsByTypeAndIba(@Param("typeOid") String typeOid,
                           @Param("ibaOid") String ibaOid);

    @Override
    @Delete("DELETE FROM ck_type_iba WHERE type_oid = #{typeOid}")
    int deleteByTypeOid(@Param("typeOid") String typeOid);

    // ==================== 继承属性查询 ====================

    @Select("WITH RECURSIVE type_chain AS (" +
            "  SELECT td.oid, td.parent_oid FROM ck_type_definition td WHERE td.oid = #{typeOid} " +
            "  UNION ALL " +
            "  SELECT td.oid, td.parent_oid FROM ck_type_definition td " +
            "  INNER JOIN type_chain tc ON td.oid = tc.parent_oid " +
            ") " +
            "SELECT ti.oid, ti.type_oid, ti.entity_code, ti.iba_oid, ti.required, ti.default_value, " +
            "ti.sort_order, ti.tenant_oid, ti.creator, ti.created_at, ti.updater, ti.updated_at, " +
            "i.code AS iba_code, i.name AS iba_name, i.display_name AS iba_display_name, " +
            "i.data_type AS iba_data_type, " +
            "td2.name AS parent_type_name, " +
            "ad.display_name AS ad_display_name, " +
            "ad.ui_component AS ui_component, " +
            "ad.searchable, ad.listable, ad.editable, " +
            "ad.field_name AS field_name " +
            "FROM ck_type_iba ti " +
            "INNER JOIN ck_iba i ON ti.iba_oid = i.oid " +
            "INNER JOIN ck_type_definition td2 ON ti.type_oid = td2.oid " +
            "LEFT JOIN ck_attribute_definition ad ON ad.entity_name = td2.code AND ad.iba_oid = ti.iba_oid AND ad.source = 'IBA' " +
            "WHERE ti.type_oid IN (SELECT oid FROM type_chain WHERE oid != #{typeOid}) " +
            "ORDER BY ti.sort_order ASC, i.sort_order ASC")
    @Results(id = "inheritedResult", value = {
            @Result(property = "oid",            column = "oid"),
            @Result(property = "typeOid",        column = "type_oid"),
            @Result(property = "entityCode",     column = "entity_code"),
            @Result(property = "ibaOid",         column = "iba_oid"),
            @Result(property = "required",       column = "required"),
            @Result(property = "defaultValue",   column = "default_value"),
            @Result(property = "sortOrder",      column = "sort_order"),
            @Result(property = "tenantOid",      column = "tenant_oid"),
            @Result(property = "creator",        column = "creator"),
            @Result(property = "createdAt",      column = "created_at"),
            @Result(property = "updater",        column = "updater"),
            @Result(property = "updatedAt",      column = "updated_at"),
            @Result(property = "ibaCode",        column = "iba_code"),
            @Result(property = "ibaName",        column = "iba_name"),
            @Result(property = "ibaDisplayName", column = "iba_display_name"),
            @Result(property = "ibaDataType",    column = "iba_data_type"),
            @Result(property = "parentTypeName", column = "parent_type_name"),
            @Result(property = "adDisplayName",  column = "ad_display_name"),
            @Result(property = "uiComponent",    column = "ui_component"),
            @Result(property = "searchable",     column = "searchable"),
            @Result(property = "listable",       column = "listable"),
            @Result(property = "editable",       column = "editable"),
            @Result(property = "fieldName",      column = "field_name")
    })
    @Override
    List<TypeIBA> selectInheritedMappings(@Param("typeOid") String typeOid);

    @Select("WITH RECURSIVE type_chain AS (" +
            "  SELECT td.oid, td.parent_oid FROM ck_type_definition td WHERE td.oid = #{ownerOid} " +
            "  UNION ALL " +
            "  SELECT td.oid, td.parent_oid FROM ck_type_definition td " +
            "  INNER JOIN type_chain tc ON td.oid = tc.parent_oid " +
            ") " +
            "SELECT ti.oid, ti.type_oid, ti.entity_code, ti.iba_oid, ti.required, ti.default_value, " +
            "ti.sort_order, ti.tenant_oid, ti.creator, ti.created_at, ti.updater, ti.updated_at, " +
            "i.code AS iba_code, i.name AS iba_name, i.display_name AS iba_display_name, " +
            "i.data_type AS iba_data_type, " +
            "td2.name AS parent_type_name, " +
            "ad.display_name AS ad_display_name, " +
            "ad.ui_component AS ui_component, " +
            "ad.searchable, ad.listable, ad.editable, " +
            "ad.field_name AS field_name " +
            "FROM ck_type_iba ti " +
            "INNER JOIN ck_iba i ON ti.iba_oid = i.oid " +
            "INNER JOIN ck_type_definition td2 ON ti.type_oid = td2.oid " +
            "LEFT JOIN ck_attribute_definition ad ON ad.entity_name = td2.code AND ad.iba_oid = ti.iba_oid AND ad.source = 'IBA' " +
            "WHERE ti.type_oid IN (SELECT oid FROM type_chain WHERE oid != #{ownerOid}) " +
            "ORDER BY ti.sort_order ASC, i.sort_order ASC")
    @ResultMap("inheritedResult")
    @Override
    List<TypeIBA> selectInheritedMappingsByOwner(@Param("ownerOid") String ownerOid,
                                                  @Param("entityCode") String entityCode);
}
