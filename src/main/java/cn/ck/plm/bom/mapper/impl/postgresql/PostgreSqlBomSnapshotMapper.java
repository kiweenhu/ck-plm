/*
 * Copyright (c) 2025 深圳乘恺科技有限公司
 * All rights reserved.
 *
 * @author Kiween.Hu; Roney.Liu
 */

package cn.ck.plm.bom.mapper.impl.postgresql;

import cn.ck.plm.bom.entity.BomSnapshot;
import cn.ck.plm.bom.mapper.BomSnapshotMapper;
import org.apache.ibatis.annotations.*;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;

/**
 * BomSnapshot PostgreSQL 持久化实现。
 * 使用 INSERT ... ON CONFLICT DO UPDATE 实现 upsert（iterationOid 唯一）。
 */
@Mapper
@ConditionalOnProperty(name = "plm.database.type", havingValue = "postgresql", matchIfMissing = true)
public interface PostgreSqlBomSnapshotMapper extends BomSnapshotMapper {

    @Override
    @Insert("INSERT INTO ck_bom_snapshot (oid, iteration_oid, snapshot_json, node_count, max_depth, " +
            "tenant_oid, creator, created_at, updater, updated_at) " +
            "VALUES (#{oid}, #{iterationOid}, #{snapshotJson}::jsonb, #{nodeCount}, #{maxDepth}, " +
            "#{tenantOid}, #{creator}, #{createdAt}, #{updater}, #{updatedAt}) " +
            "ON CONFLICT (iteration_oid) DO UPDATE SET " +
            "snapshot_json = EXCLUDED.snapshot_json, node_count = EXCLUDED.node_count, " +
            "max_depth = EXCLUDED.max_depth, " +
            "updater = EXCLUDED.updater, updated_at = EXCLUDED.updated_at")
    int upsert(BomSnapshot snapshot);

    @Override
    @Delete("DELETE FROM ck_bom_snapshot WHERE iteration_oid = #{iterationOid}")
    int deleteByIterationOid(@Param("iterationOid") String iterationOid);

    String SELECT_COLUMNS = "SELECT oid, iteration_oid, snapshot_json, node_count, max_depth, " +
            "tenant_oid, creator, created_at, updater, updated_at FROM ck_bom_snapshot ";

    @Override
    @Select(SELECT_COLUMNS + "WHERE iteration_oid = #{iterationOid}")
    @Results(id = "bomSnapshotResult", value = {
            @Result(property = "oid",           column = "oid"),
            @Result(property = "iterationOid",  column = "iteration_oid"),
            @Result(property = "snapshotJson",  column = "snapshot_json"),
            @Result(property = "nodeCount",     column = "node_count"),
            @Result(property = "maxDepth",      column = "max_depth"),
            @Result(property = "tenantOid",     column = "tenant_oid"),
            @Result(property = "creator",       column = "creator"),
            @Result(property = "createdAt",     column = "created_at"),
            @Result(property = "updater",       column = "updater"),
            @Result(property = "updatedAt",     column = "updated_at")
    })
    BomSnapshot selectByIterationOid(@Param("iterationOid") String iterationOid);
}
