/*
 * Copyright (c) 2025 深圳乘恺科技有限公司
 * All rights reserved.
 *
 * @author Kiween.Hu; Roney.Liu
 */

package cn.ck.plm.bom.mapper.impl.postgresql;

import cn.ck.plm.bom.entity.BomDiff;
import cn.ck.plm.bom.mapper.BomDiffMapper;
import org.apache.ibatis.annotations.*;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;

import java.util.List;

/**
 * BomDiff PostgreSQL 持久化实现。
 */
@Mapper
@ConditionalOnProperty(name = "plm.database.type", havingValue = "postgresql", matchIfMissing = true)
public interface PostgreSqlBomDiffMapper extends BomDiffMapper {

    @Override
    @Insert("INSERT INTO ck_bom_diff (oid, from_iteration_oid, to_iteration_oid, " +
            "diff_json, added_count, removed_count, changed_count, " +
            "tenant_oid, creator, created_at, updater, updated_at) " +
            "VALUES (#{oid}, #{fromIterationOid}, #{toIterationOid}, " +
            "#{diffJson}::jsonb, #{addedCount}, #{removedCount}, #{changedCount}, " +
            "#{tenantOid}, #{creator}, #{createdAt}, #{updater}, #{updatedAt})")
    int insert(BomDiff diff);

    @Override
    @Delete("DELETE FROM ck_bom_diff WHERE oid = #{oid}")
    int deleteByOid(@Param("oid") String oid);

    String SELECT_COLUMNS = "SELECT oid, from_iteration_oid, to_iteration_oid, " +
            "diff_json, added_count, removed_count, changed_count, " +
            "tenant_oid, creator, created_at, updater, updated_at FROM ck_bom_diff ";

    @Override
    @Select(SELECT_COLUMNS + "WHERE oid = #{oid}")
    @Results(id = "bomDiffResult", value = {
            @Result(property = "oid",               column = "oid"),
            @Result(property = "fromIterationOid",  column = "from_iteration_oid"),
            @Result(property = "toIterationOid",    column = "to_iteration_oid"),
            @Result(property = "diffJson",          column = "diff_json"),
            @Result(property = "addedCount",        column = "added_count"),
            @Result(property = "removedCount",      column = "removed_count"),
            @Result(property = "changedCount",      column = "changed_count"),
            @Result(property = "tenantOid",         column = "tenant_oid"),
            @Result(property = "creator",           column = "creator"),
            @Result(property = "createdAt",         column = "created_at"),
            @Result(property = "updater",           column = "updater"),
            @Result(property = "updatedAt",         column = "updated_at")
    })
    BomDiff selectByOid(@Param("oid") String oid);

    @Override
    @Select(SELECT_COLUMNS + "WHERE from_iteration_oid = #{fromIterationOid} ORDER BY created_at DESC")
    @ResultMap("bomDiffResult")
    List<BomDiff> selectByFromIterationOid(@Param("fromIterationOid") String fromIterationOid);

    @Override
    @Select(SELECT_COLUMNS + "WHERE to_iteration_oid = #{toIterationOid} ORDER BY created_at DESC")
    @ResultMap("bomDiffResult")
    List<BomDiff> selectByToIterationOid(@Param("toIterationOid") String toIterationOid);

    @Override
    @Select(SELECT_COLUMNS + "WHERE from_iteration_oid = #{fromIterationOid} AND to_iteration_oid = #{toIterationOid}")
    @ResultMap("bomDiffResult")
    BomDiff selectByFromAndTo(@Param("fromIterationOid") String fromIterationOid,
                              @Param("toIterationOid") String toIterationOid);
}
