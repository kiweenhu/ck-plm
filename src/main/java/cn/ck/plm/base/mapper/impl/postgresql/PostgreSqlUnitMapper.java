/*
 * Copyright (c) 2025 深圳乘恺科技有限公司
 * All rights reserved.
 */

package cn.ck.plm.base.mapper.impl.postgresql;

import cn.ck.plm.base.entity.Unit;
import cn.ck.plm.base.mapper.UnitMapper;
import org.apache.ibatis.annotations.*;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;

import java.util.List;

@Mapper
@ConditionalOnProperty(name = "plm.database.type", havingValue = "postgresql", matchIfMissing = true)
public interface PostgreSqlUnitMapper extends UnitMapper {

    String TABLE = "ck_unit";
    String COLS = "oid, name, display, quantity_type, is_si, base_unit_name, " +
                  "factor, unit_shift, sort_order, description, " +
                  "creator, created_at, updater, updated_at";

    @Override
    @Insert("INSERT INTO " + TABLE + " (oid, name, display, quantity_type, is_si, base_unit_name, " +
            "factor, unit_shift, sort_order, description, creator, created_at, updater, updated_at) " +
            "VALUES (#{oid}, #{name}, #{display}, #{quantityType}, #{isSI}, #{baseUnitName}, " +
            "#{factor}, #{offset}, #{sortOrder}, #{description}, #{creator}, #{createdAt}, #{updater}, #{updatedAt})")
    int insert(Unit unit);

    @Override
    @Update("UPDATE " + TABLE + " SET display = #{display}, quantity_type = #{quantityType}, " +
            "is_si = #{isSI}, base_unit_name = #{baseUnitName}, factor = #{factor}, " +
            "unit_shift = #{offset}, sort_order = #{sortOrder}, description = #{description}, " +
            "updater = #{updater}, updated_at = CURRENT_TIMESTAMP WHERE oid = #{oid}")
    int update(Unit unit);

    @Override
    @Delete("DELETE FROM " + TABLE + " WHERE oid = #{oid}")
    int deleteByOid(@Param("oid") String oid);

    @Override
    @Select("SELECT " + COLS + " FROM " + TABLE + " WHERE name = #{name}")
    @Results(id = "unitResult", value = {
            @Result(property = "quantityType", column = "quantity_type"),
            @Result(property = "isSI", column = "is_si"),
            @Result(property = "baseUnitName", column = "base_unit_name"),
            @Result(property = "offset", column = "unit_shift"),
            @Result(property = "sortOrder", column = "sort_order"),
            @Result(property = "createdAt", column = "created_at"),
            @Result(property = "updatedAt", column = "updated_at"),
    })
    Unit selectByName(@Param("name") String name);

    @Override
    @Select("SELECT " + COLS + " FROM " + TABLE + " WHERE oid = #{oid}")
    @ResultMap("unitResult")
    Unit selectByOid(@Param("oid") String oid);

    @Override
    @Select("SELECT " + COLS + " FROM " + TABLE + " WHERE quantity_type = #{quantityType} ORDER BY sort_order, name")
    @ResultMap("unitResult")
    List<Unit> selectByQuantityType(@Param("quantityType") String quantityType);

    @Override
    @Select("SELECT " + COLS + " FROM " + TABLE + " ORDER BY quantity_type, sort_order, name")
    @ResultMap("unitResult")
    List<Unit> selectAll();

    @Override
    @Select("SELECT COUNT(*) FROM " + TABLE + " WHERE name = #{name}")
    int existsByName(@Param("name") String name);

    @Override
    @Select("SELECT " + COLS + " FROM " + TABLE + " WHERE base_unit_name = #{baseUnitName}")
    @ResultMap("unitResult")
    List<Unit> selectByBaseUnitName(@Param("baseUnitName") String baseUnitName);
}
