/*
 * Copyright (c) 2025 深圳乘恺科技有限公司
 * All rights reserved.
 *
 * @author Kiween.Hu; Roney.Liu
 */

package cn.ck.plm.base.mapper.impl.postgresql;

import cn.ck.plm.base.entity.NumberSegment;
import cn.ck.plm.base.mapper.NumberSegmentMapper;
import org.apache.ibatis.annotations.*;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;

import java.util.List;

/**
 * {@link NumberSegmentMapper} 的 PostgreSQL 实现（MyBatis 注解模式）。
 */
@Mapper
@ConditionalOnProperty(name = "plm.database.type", havingValue = "postgresql", matchIfMissing = true)
public interface PostgreSqlNumberSegmentMapper extends NumberSegmentMapper {

    @Override
    @Insert("INSERT INTO ck_number_segment (oid, rule_code, sort_order, segment_type, " +
            "fixed_value, date_format, serial_length, serial_start, current_value, description, config, " +
            " creator, created_at, updater, updated_at) " +
            "VALUES (#{oid}, #{ruleCode}, #{sortOrder}, #{segmentType}, " +
            "#{fixedValue}, #{dateFormat}, #{serialLength}, #{serialStart}, #{currentValue}, #{description}, #{config}, " +
            "#{creator}, #{createdAt}, #{updater}, #{updatedAt})")
    int insert(NumberSegment segment);

    @Override
    @Insert("<script>" +
            "INSERT INTO ck_number_segment (oid, rule_code, sort_order, segment_type, " +
            "fixed_value, date_format, serial_length, serial_start, current_value, description, config, " +
            " creator, created_at, updater, updated_at) VALUES " +
            "<foreach collection='list' item='seg' separator=','>" +
            "(#{seg.oid}, #{seg.ruleCode}, #{seg.sortOrder}, #{seg.segmentType}, " +
            "#{seg.fixedValue}, #{seg.dateFormat}, #{seg.serialLength}, " +
            "#{seg.serialStart}, #{seg.currentValue}, #{seg.description}, #{seg.config}, " +
            "#{seg.creator}, #{seg.createdAt}, #{seg.updater}, #{seg.updatedAt})" +
            "</foreach>" +
            "</script>")
    int batchInsert(List<NumberSegment> segments);

    @Override
    @Update("UPDATE ck_number_segment SET sort_order = #{sortOrder}, segment_type = #{segmentType}, " +
            "fixed_value = #{fixedValue}, date_format = #{dateFormat}, serial_length = #{serialLength}, " +
            "serial_start = #{serialStart}, description = #{description}, config = #{config}, " +
            "updater = #{updater}, updated_at = #{updatedAt} WHERE oid = #{oid}")
    int update(NumberSegment segment);

    @Override
    @Delete("DELETE FROM ck_number_segment WHERE rule_code = #{ruleCode}")
    int deleteByRuleCode(@Param("ruleCode") String ruleCode);

    @Override
    @Delete("DELETE FROM ck_number_segment WHERE oid = #{oid}")
    int deleteByOid(@Param("oid") String oid);

    // ==================== 查询映射 ====================

    @Select("SELECT oid, rule_code, sort_order, segment_type, fixed_value, " +
            "date_format, serial_length, serial_start, current_value, description, config, " +
            " creator, created_at, updater, updated_at " +
            "FROM ck_number_segment WHERE oid = #{oid}")
    @Results(id = "numberSegmentResult", value = {
            @Result(property = "oid",           column = "oid"),
            @Result(property = "ruleCode",      column = "rule_code"),
            @Result(property = "sortOrder",     column = "sort_order"),
            @Result(property = "segmentType",   column = "segment_type"),
            @Result(property = "fixedValue",    column = "fixed_value"),
            @Result(property = "dateFormat",    column = "date_format"),
            @Result(property = "serialLength",  column = "serial_length"),
            @Result(property = "serialStart",   column = "serial_start"),
            @Result(property = "currentValue",  column = "current_value"),
            @Result(property = "description",   column = "description"),
            @Result(property = "config",        column = "config"),
            @Result(property = "creator",       column = "creator"),
            @Result(property = "createdAt",     column = "created_at"),
            @Result(property = "updater",       column = "updater"),
            @Result(property = "updatedAt",     column = "updated_at")
    })
    @Override
    NumberSegment selectByOid(@Param("oid") String oid);

    @Select("SELECT oid, rule_code, sort_order, segment_type, fixed_value, " +
            "date_format, serial_length, serial_start, current_value, description, config, " +
            " creator, created_at, updater, updated_at " +
            "FROM ck_number_segment WHERE rule_code = #{ruleCode} ORDER BY sort_order ASC")
    @ResultMap("numberSegmentResult")
    @Override
    List<NumberSegment> selectByRuleCode(@Param("ruleCode") String ruleCode);

    @Override
    @Update("UPDATE ck_number_segment SET current_value = current_value + 1 WHERE oid = #{oid}")
    int incrementCurrentValue(@Param("oid") String oid);

    @Override
    @Update("UPDATE ck_number_segment SET current_value = #{startValue} WHERE oid = #{oid}")
    int resetCurrentValue(@Param("oid") String oid, @Param("startValue") int startValue);
}
