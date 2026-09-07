/*
 * Copyright (c) 2025 深圳乘恺科技有限公司
 * All rights reserved.
 */

package cn.ck.plm.product.mapper.impl.postgresql;

import cn.ck.plm.product.entity.ProductModel;
import cn.ck.plm.product.mapper.ProductModelMapper;
import org.apache.ibatis.annotations.*;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;

import java.util.List;

/**
 * {@link ProductModelMapper} 的 PostgreSQL 实现（MyBatis 注解模式）。
 *
 * <p>删除采用逻辑删除（delete_mark=true），已删除对象进入回收站可恢复。
 */
@Mapper
@ConditionalOnProperty(name = "plm.database.type", havingValue = "postgresql", matchIfMissing = true)
public interface PostgreSqlProductModelMapper extends ProductModelMapper {

    @Override
    @Insert("INSERT INTO ck_product_model (oid, code, name, description, thumbnail, team_oid, parent_oid, " +
            "delete_mark, creator, created_at, updater, updated_at) " +
            "VALUES (#{oid}, #{code}, #{name}, #{description}, #{thumbnail}, #{teamOid}, #{parentOid}, " +
            "#{deleteMark}, #{creator}, #{createdAt}, #{updater}, #{updatedAt})")
    int insert(ProductModel model);

    @Override
    @Update("UPDATE ck_product_model SET name = #{name}, description = #{description}, " +
            "thumbnail = #{thumbnail}, team_oid = #{teamOid}, parent_oid = #{parentOid}, " +
            "updater = #{updater}, updated_at = #{updatedAt} " +
            "WHERE oid = #{oid}")
    int update(ProductModel model);

    @Override
    @Update("UPDATE ck_product_model SET delete_mark = true, updated_at = CURRENT_TIMESTAMP WHERE oid = #{oid}")
    int softDeleteByOid(@Param("oid") String oid);

    @Override
    @Update("UPDATE ck_product_model SET delete_mark = false, updated_at = CURRENT_TIMESTAMP WHERE oid = #{oid}")
    int restoreByOid(@Param("oid") String oid);

    @Override
    @Delete("DELETE FROM ck_product_model WHERE oid = #{oid}")
    int deleteByOid(@Param("oid") String oid);

    // ==================== 查询映射 ====================
    String SELECT_COLUMNS = "SELECT oid, code, name, description, thumbnail, team_oid, parent_oid, " +
            "delete_mark, creator, created_at, updater, updated_at FROM ck_product_model ";

    @Select(SELECT_COLUMNS + "WHERE oid = #{oid} AND COALESCE(delete_mark, false) = false")
    @Results(id = "productModelResult", value = {
            @Result(property = "oid",            column = "oid"),
            @Result(property = "code",           column = "code"),
            @Result(property = "name",           column = "name"),
            @Result(property = "description",    column = "description"),
            @Result(property = "thumbnail",      column = "thumbnail"),
            @Result(property = "teamOid",        column = "team_oid"),
            @Result(property = "parentOid",      column = "parent_oid"),
            @Result(property = "deleteMark",     column = "delete_mark"),
            @Result(property = "creator",        column = "creator"),
            @Result(property = "createdAt",      column = "created_at"),
            @Result(property = "updater",        column = "updater"),
            @Result(property = "updatedAt",      column = "updated_at")
    })
    @Override
    ProductModel selectByOid(@Param("oid") String oid);

    @Select(SELECT_COLUMNS + "WHERE code = #{code} AND COALESCE(delete_mark, false) = false")
    @ResultMap("productModelResult")
    @Override
    ProductModel selectByCode(@Param("code") String code);

    @Select(SELECT_COLUMNS + "WHERE COALESCE(delete_mark, false) = false ORDER BY code ASC")
    @ResultMap("productModelResult")
    @Override
    List<ProductModel> selectAll();

    @Select(SELECT_COLUMNS + "WHERE parent_oid = #{parentOid} AND COALESCE(delete_mark, false) = false ORDER BY code ASC")
    @ResultMap("productModelResult")
    @Override
    List<ProductModel> selectByProductLineOid(@Param("parentOid") String parentOid);

    @Select(SELECT_COLUMNS +
            "WHERE COALESCE(delete_mark, false) = false AND (" +
            "LOWER(code) LIKE LOWER('%' || #{keyword} || '%') " +
            "OR LOWER(name) LIKE LOWER('%' || #{keyword} || '%')) " +
            "ORDER BY code ASC")
    @ResultMap("productModelResult")
    @Override
    List<ProductModel> search(@Param("keyword") String keyword);

    @Select("SELECT COUNT(*) FROM ck_product_model WHERE code = #{code} AND COALESCE(delete_mark, false) = false")
    @Override
    int existsByCode(@Param("code") String code);

    @Override
    @Select("SELECT parent_oid, COUNT(*) AS cnt FROM ck_product_model WHERE COALESCE(delete_mark, false) = false GROUP BY parent_oid")
    List<java.util.Map<String, Object>> countGroupByProductLineOid();

    // ==================== 回收站 ====================

    @Override
    @Select(SELECT_COLUMNS + "WHERE COALESCE(delete_mark, false) = true ORDER BY updated_at DESC")
    @ResultMap("productModelResult")
    List<ProductModel> selectDeleted();
}