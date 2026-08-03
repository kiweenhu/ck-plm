/*
 * Copyright (c) 2025 深圳乘恺科技有限公司
 * All rights reserved.
 *
 * @author Kiween.Hu; Roney.Liu
 */

package cn.ck.plm.softtype.mapper.impl.postgresql;

import cn.ck.plm.softtype.entity.AttributeDefinition;
import cn.ck.plm.softtype.mapper.AttributeDefinitionMapper;
import org.apache.ibatis.annotations.*;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;

import java.util.List;

/**
 * {@link AttributeDefinitionMapper} 的 PostgreSQL 实现。
 */
@Mapper
@ConditionalOnProperty(name = "plm.database.type", havingValue = "postgresql", matchIfMissing = true)
public interface PostgreSqlAttributeDefinitionMapper extends AttributeDefinitionMapper {

    @Override
    @Insert("INSERT INTO ck_attribute_definition (oid, entity_name, field_name, display_name, " +
            "data_type, source, iba_oid, required, searchable, listable, editable, " +
            "ui_component, default_value, constraints_json, sort_order, enabled, " +
            " creator, created_at, updater, updated_at) " +
            "VALUES (#{oid}, #{entityName}, #{fieldName}, #{displayName}, " +
            "#{dataType}, #{source}, #{ibaOid}, #{required}, #{searchable}, #{listable}, #{editable}, " +
            "#{uiComponent}, #{defaultValue}, #{constraintsJson}, #{sortOrder}, #{enabled}, " +
            "#{creator}, #{createdAt}, #{updater}, #{updatedAt})")
    int insert(AttributeDefinition def);

    @Override
    @Update("UPDATE ck_attribute_definition SET display_name=#{displayName}, " +
            "data_type=#{dataType}, required=#{required}, " +
            "searchable=#{searchable}, listable=#{listable}, editable=#{editable}, " +
            "ui_component=#{uiComponent}, default_value=#{defaultValue}, " +
            "constraints_json=#{constraintsJson}, sort_order=#{sortOrder}, enabled=#{enabled}, " +
            "updater=#{updater}, updated_at=#{updatedAt} " +
            "WHERE oid=#{oid}")
    int update(AttributeDefinition def);

    @Override
    @Delete("DELETE FROM ck_attribute_definition WHERE oid=#{oid}")
    int deleteByOid(@Param("oid") String oid);

    @Override
    @Select("SELECT oid, entity_name, field_name, display_name, " +
            "data_type, source, iba_oid, required, searchable, listable, editable, " +
            "ui_component, default_value, constraints_json, sort_order, enabled, " +
            " creator, created_at, updater, updated_at " +
            "FROM ck_attribute_definition WHERE oid=#{oid}")
    @Results(id = "attrDefResult", value = {
            @Result(property = "oid",             column = "oid"),
            @Result(property = "entityName",      column = "entity_name"),
            @Result(property = "fieldName",       column = "field_name"),
            @Result(property = "displayName",     column = "display_name"),
            @Result(property = "dataType",        column = "data_type"),
            @Result(property = "source",          column = "source"),
            @Result(property = "ibaOid",          column = "iba_oid"),
            @Result(property = "required",        column = "required"),
            @Result(property = "searchable",      column = "searchable"),
            @Result(property = "listable",        column = "listable"),
            @Result(property = "editable",        column = "editable"),
            @Result(property = "uiComponent",     column = "ui_component"),
            @Result(property = "defaultValue",    column = "default_value"),
            @Result(property = "constraintsJson", column = "constraints_json"),
            @Result(property = "sortOrder",       column = "sort_order"),
            @Result(property = "enabled",         column = "enabled"),
            @Result(property = "creator",         column = "creator"),
            @Result(property = "createdAt",       column = "created_at"),
            @Result(property = "updater",         column = "updater"),
            @Result(property = "updatedAt",       column = "updated_at")
    })
    AttributeDefinition selectByOid(@Param("oid") String oid);

    @Override
    @Select("SELECT oid, entity_name, field_name, display_name, " +
            "data_type, source, iba_oid, required, searchable, listable, editable, " +
            "ui_component, default_value, constraints_json, sort_order, enabled, " +
            " creator, created_at, updater, updated_at " +
            "FROM ck_attribute_definition WHERE entity_name=#{entityName} " +
            "AND editable = true " +
            "ORDER BY sort_order ASC")
    @ResultMap("attrDefResult")
    List<AttributeDefinition> selectByEntityName(@Param("entityName") String entityName);

    @Override
    @Select("SELECT COUNT(*) FROM ck_attribute_definition " +
            "WHERE entity_name=#{entityName} AND field_name=#{fieldName}")
    int existsByEntityAndField(@Param("entityName") String entityName,
                               @Param("fieldName") String fieldName);

    @Override
    @Delete("DELETE FROM ck_attribute_definition WHERE iba_oid=#{ibaOid}")
    int deleteByIbaOid(@Param("ibaOid") String ibaOid);

    @Override
    @Delete("DELETE FROM ck_attribute_definition WHERE entity_name=#{entityName} AND source='SYSTEM'")
    int deleteByEntityName(@Param("entityName") String entityName);
}
