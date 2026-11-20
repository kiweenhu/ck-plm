/*
 * Copyright (c) 2026~2028 深圳乘恺科技有限公司
 * All rights reserved.
 *
 * @author Kiween.Hu
 */

package cn.ck.plm.ecad.mapper.impl.postgresql;

import cn.ck.plm.ecad.entity.EcadBomSyncLog;
import cn.ck.plm.ecad.mapper.EcadBomSyncLogMapper;
import org.apache.ibatis.annotations.*;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;

import java.util.List;

/**
 * {@link EcadBomSyncLogMapper} 的 PostgreSQL 实现（MyBatis 注解模式）。
 */
@Mapper
@ConditionalOnProperty(name = "plm.database.type", havingValue = "postgresql", matchIfMissing = true)
public interface PostgreSqlEcadBomSyncLogMapper extends EcadBomSyncLogMapper {

    @Override
    @Insert("INSERT INTO ck_ecad_bom_sync_log (oid, schematic_oid, iteration_oid, " +
            "added_count, removed_count, changed_count, diff_json, tenant_oid, " +
            "creator, created_at, updater, updated_at) " +
            "VALUES (#{oid}, #{schematicOid}, #{iterationOid}, " +
            "#{addedCount}, #{removedCount}, #{changedCount}, #{diffJson}::jsonb, #{tenantOid}, " +
            "#{creator}, #{createdAt}, #{updater}, #{updatedAt})")
    int insert(EcadBomSyncLog syncLog);

    // ==================== 查询映射 ====================

    String SELECT_COLUMNS = "SELECT oid, schematic_oid, iteration_oid, added_count, removed_count, " +
            "changed_count, diff_json::text AS diff_json, tenant_oid, " +
            "creator, created_at, updater, updated_at FROM ck_ecad_bom_sync_log ";

    @Select(SELECT_COLUMNS + "WHERE schematic_oid = #{schematicOid} ORDER BY created_at DESC LIMIT 50")
    @Results(id = "ecadBomSyncLogResult", value = {
            @Result(property = "oid",           column = "oid"),
            @Result(property = "schematicOid",  column = "schematic_oid"),
            @Result(property = "iterationOid",  column = "iteration_oid"),
            @Result(property = "addedCount",    column = "added_count"),
            @Result(property = "removedCount",  column = "removed_count"),
            @Result(property = "changedCount",  column = "changed_count"),
            @Result(property = "diffJson",      column = "diff_json"),
            @Result(property = "tenantOid",     column = "tenant_oid"),
            @Result(property = "creator",       column = "creator"),
            @Result(property = "createdAt",     column = "created_at"),
            @Result(property = "updater",       column = "updater"),
            @Result(property = "updatedAt",     column = "updated_at")
    })
    @Override
    List<EcadBomSyncLog> selectBySchematicOid(@Param("schematicOid") String schematicOid);
}
