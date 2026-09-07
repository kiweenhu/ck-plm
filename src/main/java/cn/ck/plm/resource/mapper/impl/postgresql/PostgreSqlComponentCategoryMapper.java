/*
 * Copyright (c) 2025 深圳乘恺科技有限公司
 * All rights reserved.
 *
 * @author Kiween.Hu; Roney.Liu
 */

package cn.ck.plm.resource.mapper.impl.postgresql;

import cn.ck.plm.resource.entity.ComponentCategory;
import cn.ck.plm.resource.mapper.ComponentCategoryMapper;
import org.apache.ibatis.annotations.*;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;

import java.util.List;

/**
 * {@link ComponentCategoryMapper} 的 PostgreSQL 实现（MyBatis 注解模式）。
 */
@Mapper
@ConditionalOnProperty(name = "plm.database.type", havingValue = "postgresql", matchIfMissing = true)
public interface PostgreSqlComponentCategoryMapper extends ComponentCategoryMapper {

    @Override
    @Insert("INSERT INTO ck_component_category (oid, name, parent_category_oid, sort_order, tenant_oid, " +
            "creator, created_at, updater, updated_at) " +
            "VALUES (#{oid}, #{name}, #{parentCategoryOid}, #{sortOrder}, #{tenantOid}, " +
            "#{creator}, #{createdAt}, #{updater}, #{updatedAt})")
    int insert(ComponentCategory category);

    @Override
    @Update("UPDATE ck_component_category SET name = #{name}, sort_order = #{sortOrder}, " +
            "updater = #{updater}, updated_at = #{updatedAt} WHERE oid = #{oid}")
    int update(ComponentCategory category);

    @Override
    @Delete("DELETE FROM ck_component_category WHERE oid = #{oid}")
    int deleteByOid(@Param("oid") String oid);

    String SELECT_COLUMNS = "SELECT oid, name, parent_category_oid, sort_order, tenant_oid, " +
            "creator, created_at, updater, updated_at FROM ck_component_category ";

    @Select(SELECT_COLUMNS + "WHERE oid = #{oid}")
    @Results(id = "componentCategoryResult", value = {
            @Result(property = "oid",                 column = "oid"),
            @Result(property = "name",                column = "name"),
            @Result(property = "parentCategoryOid",   column = "parent_category_oid"),
            @Result(property = "sortOrder",           column = "sort_order"),
            @Result(property = "tenantOid",           column = "tenant_oid"),
            @Result(property = "creator",             column = "creator"),
            @Result(property = "createdAt",           column = "created_at"),
            @Result(property = "updater",             column = "updater"),
            @Result(property = "updatedAt",           column = "updated_at")
    })
    @Override
    ComponentCategory selectByOid(@Param("oid") String oid);

    @Override
    @Select(SELECT_COLUMNS + "WHERE parent_category_oid IS NULL ORDER BY sort_order ASC, name ASC")
    @ResultMap("componentCategoryResult")
    List<ComponentCategory> selectRoots();

    @Override
    @Select(SELECT_COLUMNS + "WHERE parent_category_oid = #{parentCategoryOid} ORDER BY sort_order ASC, name ASC")
    @ResultMap("componentCategoryResult")
    List<ComponentCategory> selectByParentOid(@Param("parentCategoryOid") String parentCategoryOid);

    @Override
    @Select(SELECT_COLUMNS + "ORDER BY sort_order ASC, name ASC")
    @ResultMap("componentCategoryResult")
    List<ComponentCategory> selectAll();

    @Override
    @Select("<script>" +
            "SELECT COUNT(*) FROM ck_component_category WHERE name = #{name} " +
            "AND parent_category_oid " +
            "<if test='parentCategoryOid != null'> = #{parentCategoryOid} </if>" +
            "<if test='parentCategoryOid == null'> IS NULL </if>" +
            "<if test='excludeOid != null'> AND oid != #{excludeOid} </if>" +
            "</script>")
    int existsByName(@Param("parentCategoryOid") String parentCategoryOid,
                     @Param("name") String name,
                     @Param("excludeOid") String excludeOid);

    @Override
    @Select("SELECT COUNT(*) FROM ck_component_category WHERE parent_category_oid = #{parentCategoryOid}")
    int countByParentOid(@Param("parentCategoryOid") String parentCategoryOid);
}
