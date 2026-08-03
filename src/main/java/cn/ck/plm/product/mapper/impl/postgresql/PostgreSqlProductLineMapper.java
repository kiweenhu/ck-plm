/*
 * Copyright (c) 2025 深圳乘恺科技有限公司
 * All rights reserved.
 *
 * @author Kiween.Hu; Roney.Liu
 */

package cn.ck.plm.product.mapper.impl.postgresql;

import cn.ck.plm.product.entity.ProductLine;
import cn.ck.plm.product.mapper.ProductLineMapper;
import org.apache.ibatis.annotations.*;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;

import java.util.List;

/**
 * {@link ProductLineMapper} 的 PostgreSQL 实现（MyBatis 注解模式）。
 */
@Mapper
@ConditionalOnProperty(name = "plm.database.type", havingValue = "postgresql", matchIfMissing = true)
public interface PostgreSqlProductLineMapper extends ProductLineMapper {

    @Override
    @Insert("INSERT INTO ck_product_line (oid, code, name, description, thumbnail, team_oid, parent_oid, " +
            " creator, created_at, updater, updated_at) " +
            "VALUES (#{oid}, #{code}, #{name}, #{description}, #{thumbnail}, #{teamOid}, #{parentOid}, " +
            "#{creator}, #{createdAt}, #{updater}, #{updatedAt})")
    int insert(ProductLine productLine);

    @Override
    @Update("UPDATE ck_product_line SET name = #{name}, description = #{description}, " +
            "thumbnail = #{thumbnail}, team_oid = #{teamOid}, parent_oid = #{parentOid}, " +
            "updater = #{updater}, updated_at = #{updatedAt} " +
            "WHERE oid = #{oid}")
    int update(ProductLine productLine);

    @Override
    @Delete("DELETE FROM ck_product_line WHERE oid = #{oid}")
    int deleteByOid(@Param("oid") String oid);

    // ==================== 查询映射 ====================
    // 公共查询列（所有 SELECT 共享）
    // 注：ext_attrs 列已废弃，IBA 动态属性值由 IBADataService 从 ck_type_iba_data 表统一管理
    String SELECT_COLUMNS = "SELECT oid, code, name, description, thumbnail, team_oid, parent_oid, " +
            " creator, created_at, updater, updated_at FROM ck_product_line ";

    @Select(SELECT_COLUMNS + "WHERE oid = #{oid}")
    @Results(id = "productLineResult", value = {
            @Result(property = "oid",              column = "oid"),
            @Result(property = "code",             column = "code"),
            @Result(property = "name",             column = "name"),
            @Result(property = "description",      column = "description"),
            @Result(property = "thumbnail",        column = "thumbnail"),
            @Result(property = "teamOid",          column = "team_oid"),
            @Result(property = "parentOid",        column = "parent_oid"),
            @Result(property = "creator",          column = "creator"),
            @Result(property = "createdAt",        column = "created_at"),
            @Result(property = "updater",          column = "updater"),
            @Result(property = "updatedAt",        column = "updated_at")
    })
    @Override
    ProductLine selectByOid(@Param("oid") String oid);

    @Select(SELECT_COLUMNS + "WHERE code = #{code}")
    @ResultMap("productLineResult")
    @Override
    ProductLine selectByCode(@Param("code") String code);

    @Select(SELECT_COLUMNS + "ORDER BY code ASC")
    @ResultMap("productLineResult")
    @Override
    List<ProductLine> selectAll();

    @Select(SELECT_COLUMNS +
            "WHERE LOWER(code) LIKE LOWER('%' || #{keyword} || '%') " +
            "OR LOWER(name) LIKE LOWER('%' || #{keyword} || '%') " +
            "ORDER BY code ASC")
    @ResultMap("productLineResult")
    @Override
    List<ProductLine> search(@Param("keyword") String keyword);

    @Select("SELECT COUNT(*) FROM ck_product_line WHERE code = #{code}")
    @Override
    int existsByCode(@Param("code") String code);

    // ==================== 树形查询 ====================

    @Override
    @Select(SELECT_COLUMNS + "WHERE parent_oid IS NULL ORDER BY code ASC")
    @ResultMap("productLineResult")
    List<ProductLine> selectRoots();

    @Override
    @Select(SELECT_COLUMNS + "WHERE parent_oid = #{parentOid} ORDER BY code ASC")
    @ResultMap("productLineResult")
    List<ProductLine> selectByParentOid(@Param("parentOid") String parentOid);

    @Override
    @Select("SELECT COUNT(*) FROM ck_product_line WHERE parent_oid = #{parentOid}")
    int countByParentOid(@Param("parentOid") String parentOid);

    @Override
    @Select("SELECT parent_oid, COUNT(*) AS cnt FROM ck_product_line WHERE parent_oid IS NOT NULL GROUP BY parent_oid")
    java.util.List<java.util.Map<String, Object>> countChildrenGroupByParentOid();

    @Override
    @Select("SELECT parent_oid AS oid, COUNT(*) AS cnt FROM ck_product_model GROUP BY parent_oid")
    java.util.List<java.util.Map<String, Object>> countModelsGroupByProductLineOid();
}
