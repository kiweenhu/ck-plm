/*
 * Copyright (c) 2025 深圳乘恺科技有限公司
 * All rights reserved.
 *
 * @author Kiween.Hu; Roney.Liu
 */

package cn.ck.plm.resource.mapper.impl.postgresql;

import cn.ck.plm.resource.entity.ElectronicComponent;
import cn.ck.plm.resource.mapper.ElectronicComponentMapper;
import org.apache.ibatis.annotations.*;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;

import java.util.List;

/**
 * {@link ElectronicComponentMapper} 的 PostgreSQL 实现（MyBatis 注解模式）。
 */
@Mapper
@ConditionalOnProperty(name = "plm.database.type", havingValue = "postgresql", matchIfMissing = true)
public interface PostgreSqlElectronicComponentMapper extends ElectronicComponentMapper {

    @Override
    @Insert("INSERT INTO ck_electronic_component (oid, code, name, category_oid, model, package_type, value_spec, " +
            "manufacturer, stock_qty, safe_stock_qty, unit, unit_cost, description, tenant_oid, " +
            "creator, created_at, updater, updated_at) " +
            "VALUES (#{oid}, #{code}, #{name}, #{categoryOid}, #{model}, #{packageType}, #{valueSpec}, " +
            "#{manufacturer}, #{stockQty}, #{safeStockQty}, #{unit}, #{unitCost}, #{description}, #{tenantOid}, " +
            "#{creator}, #{createdAt}, #{updater}, #{updatedAt})")
    int insert(ElectronicComponent component);

    @Override
    @Update("UPDATE ck_electronic_component SET code = #{code}, name = #{name}, category_oid = #{categoryOid}, " +
            "model = #{model}, package_type = #{packageType}, value_spec = #{valueSpec}, manufacturer = #{manufacturer}, " +
            "stock_qty = #{stockQty}, safe_stock_qty = #{safeStockQty}, unit = #{unit}, unit_cost = #{unitCost}, " +
            "description = #{description}, updater = #{updater}, updated_at = #{updatedAt} " +
            "WHERE oid = #{oid}")
    int update(ElectronicComponent component);

    @Override
    @Delete("DELETE FROM ck_electronic_component WHERE oid = #{oid}")
    int deleteByOid(@Param("oid") String oid);

    String SELECT_COLUMNS = "SELECT oid, code, name, category_oid, model, package_type, value_spec, " +
            "manufacturer, stock_qty, safe_stock_qty, unit, unit_cost, description, tenant_oid, " +
            "creator, created_at, updater, updated_at FROM ck_electronic_component ";

    @Select(SELECT_COLUMNS + "WHERE oid = #{oid}")
    @Results(id = "electronicComponentResult", value = {
            @Result(property = "oid",            column = "oid"),
            @Result(property = "code",           column = "code"),
            @Result(property = "name",           column = "name"),
            @Result(property = "categoryOid",    column = "category_oid"),
            @Result(property = "model",          column = "model"),
            @Result(property = "packageType",    column = "package_type"),
            @Result(property = "valueSpec",      column = "value_spec"),
            @Result(property = "manufacturer",   column = "manufacturer"),
            @Result(property = "stockQty",       column = "stock_qty"),
            @Result(property = "safeStockQty",   column = "safe_stock_qty"),
            @Result(property = "unit",           column = "unit"),
            @Result(property = "unitCost",       column = "unit_cost"),
            @Result(property = "description",    column = "description"),
            @Result(property = "tenantOid",      column = "tenant_oid"),
            @Result(property = "creator",        column = "creator"),
            @Result(property = "createdAt",      column = "created_at"),
            @Result(property = "updater",        column = "updater"),
            @Result(property = "updatedAt",      column = "updated_at")
    })
    @Override
    ElectronicComponent selectByOid(@Param("oid") String oid);

    @Override
    @Select("<script>" + SELECT_COLUMNS +
            "<where>" +
            "<if test='categoryOid != null and categoryOid != \"\"'> category_oid = #{categoryOid} </if>" +
            "<if test='keyword != null and keyword != \"\"'>" +
            " AND (code ILIKE CONCAT('%',#{keyword},'%') OR name ILIKE CONCAT('%',#{keyword},'%')" +
            " OR model ILIKE CONCAT('%',#{keyword},'%') OR manufacturer ILIKE CONCAT('%',#{keyword},'%'))" +
            "</if>" +
            "</where>" +
            "ORDER BY code ASC" +
            "</script>")
    @ResultMap("electronicComponentResult")
    List<ElectronicComponent> selectByCondition(@Param("categoryOid") String categoryOid,
                                                @Param("keyword") String keyword);

    @Override
    @Select("<script>" +
            "SELECT COUNT(*) FROM ck_electronic_component WHERE code = #{code}" +
            "<if test='excludeOid != null'> AND oid != #{excludeOid} </if>" +
            "</script>")
    int existsByCode(@Param("code") String code, @Param("excludeOid") String excludeOid);

    @Override
    @Select("SELECT COUNT(*) FROM ck_electronic_component")
    long countAll();

    @Override
    @Select("SELECT COALESCE(SUM(stock_qty), 0) FROM ck_electronic_component")
    long sumStockQty();
}
