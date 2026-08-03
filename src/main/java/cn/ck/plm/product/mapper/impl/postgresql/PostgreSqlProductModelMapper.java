/*
 * Copyright (c) 2025 深圳乘恺科技有限公司
 * All rights reserved.
 *
 * @author Kiween.Hu; Roney.Liu
 */

package cn.ck.plm.product.mapper.impl.postgresql;

import cn.ck.plm.product.entity.ProductModel;
import cn.ck.plm.product.mapper.ProductModelMapper;
import org.apache.ibatis.annotations.*;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;

import java.util.List;

/**
 * {@link ProductModelMapper} 的 PostgreSQL 实现（MyBatis 注解模式）。
 */
@Mapper
@ConditionalOnProperty(name = "plm.database.type", havingValue = "postgresql", matchIfMissing = true)
public interface PostgreSqlProductModelMapper extends ProductModelMapper {

    @Override
    @Insert("INSERT INTO ck_product_model (oid, code, name, description, thumbnail, team_oid, parent_oid, " +
            " creator, created_at, updater, updated_at) " +
            "VALUES (#{oid}, #{code}, #{name}, #{description}, #{thumbnail}, #{teamOid}, #{parentOid}, " +
            "#{creator}, #{createdAt}, #{updater}, #{updatedAt})")
    int insert(ProductModel model);

    @Override
    @Update("UPDATE ck_product_model SET name = #{name}, description = #{description}, " +
            "thumbnail = #{thumbnail}, team_oid = #{teamOid}, parent_oid = #{parentOid}, " +
            "updater = #{updater}, updated_at = #{updatedAt} " +
            "WHERE oid = #{oid}")
    int update(ProductModel model);

    @Override
    @Delete("DELETE FROM ck_product_model WHERE oid = #{oid}")
    int deleteByOid(@Param("oid") String oid);

    // ==================== 查询映射 ====================
    String SELECT_COLUMNS = "SELECT oid, code, name, description, thumbnail, team_oid, parent_oid, " +
            " creator, created_at, updater, updated_at FROM ck_product_model ";

    @Select(SELECT_COLUMNS + "WHERE oid = #{oid}")
    @Results(id = "productModelResult", value = {
            @Result(property = "oid",            column = "oid"),
            @Result(property = "code",           column = "code"),
            @Result(property = "name",           column = "name"),
            @Result(property = "description",    column = "description"),
            @Result(property = "thumbnail",      column = "thumbnail"),
            @Result(property = "teamOid",        column = "team_oid"),
            @Result(property = "parentOid",      column = "parent_oid"),
            @Result(property = "creator",        column = "creator"),
            @Result(property = "createdAt",      column = "created_at"),
            @Result(property = "updater",        column = "updater"),
            @Result(property = "updatedAt",      column = "updated_at")
    })
    @Override
    ProductModel selectByOid(@Param("oid") String oid);

    @Select(SELECT_COLUMNS + "WHERE code = #{code}")
    @ResultMap("productModelResult")
    @Override
    ProductModel selectByCode(@Param("code") String code);

    @Select(SELECT_COLUMNS + "ORDER BY code ASC")
    @ResultMap("productModelResult")
    @Override
    List<ProductModel> selectAll();

    @Select(SELECT_COLUMNS + "WHERE parent_oid = #{parentOid} ORDER BY code ASC")
    @ResultMap("productModelResult")
    @Override
    List<ProductModel> selectByProductLineOid(@Param("parentOid") String parentOid);

    @Select(SELECT_COLUMNS +
            "WHERE LOWER(code) LIKE LOWER('%' || #{keyword} || '%') " +
            "OR LOWER(name) LIKE LOWER('%' || #{keyword} || '%') " +
            "ORDER BY code ASC")
    @ResultMap("productModelResult")
    @Override
    List<ProductModel> search(@Param("keyword") String keyword);

    @Select("SELECT COUNT(*) FROM ck_product_model WHERE code = #{code}")
    @Override
    int existsByCode(@Param("code") String code);

    @Override
    @Select("SELECT parent_oid, COUNT(*) AS cnt FROM ck_product_model GROUP BY parent_oid")
    List<java.util.Map<String, Object>> countGroupByProductLineOid();
}
