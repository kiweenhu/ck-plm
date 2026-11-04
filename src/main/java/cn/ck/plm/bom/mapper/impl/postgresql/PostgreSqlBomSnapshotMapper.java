/*
 * Copyright (c) 2026~2028 深圳乘恺科技有限公司
 * All rights reserved.
 *
 * @author Kiween.Hu; Roney.Liu
 */

package cn.ck.plm.bom.mapper.impl.postgresql;

import cn.ck.plm.bom.entity.BomSnapshot;
import cn.ck.plm.bom.mapper.BomSnapshotMapper;
import org.apache.ibatis.annotations.*;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;

import java.util.List;

/**
 * BomSnapshot PostgreSQL 持久化实现。
 */
@Mapper
@ConditionalOnProperty(name = "plm.database.type", havingValue = "postgresql", matchIfMissing = true)
public interface PostgreSqlBomSnapshotMapper extends BomSnapshotMapper {

    @Override
    @Insert("INSERT INTO ck_bom_snapshot (oid, iteration_oid, part_oid, name, remark, version_label, " +
            "snapshot_json, item_count, node_count, max_depth, " +
            "tenant_oid, creator, created_at, updater, updated_at) " +
            "VALUES (#{oid}, #{iterationOid}, #{partOid}, #{name}, #{remark}, #{versionLabel}, " +
            "#{snapshotJson}::jsonb, #{itemCount}, #{nodeCount}, #{maxDepth}, " +
            "#{tenantOid}, #{creator}, #{createdAt}, #{updater}, #{updatedAt})")
    int insert(BomSnapshot snapshot);

    @Override
    @Delete("DELETE FROM ck_bom_snapshot WHERE oid = #{oid}")
    int deleteByOid(@Param("oid") String oid);

    String SELECT_COLUMNS = "SELECT oid, iteration_oid, part_oid, name, remark, version_label, " +
            "snapshot_json, item_count, node_count, max_depth, " +
            "tenant_oid, creator, created_at, updater, updated_at FROM ck_bom_snapshot ";

    @Override
    @Select(SELECT_COLUMNS + "WHERE oid = #{oid}")
    @Results(id = "bomSnapshotResult", value = {
            @Result(property = "oid",           column = "oid"),
            @Result(property = "iterationOid",  column = "iteration_oid"),
            @Result(property = "partOid",       column = "part_oid"),
            @Result(property = "name",          column = "name"),
            @Result(property = "remark",        column = "remark"),
            @Result(property = "versionLabel",  column = "version_label"),
            @Result(property = "snapshotJson",  column = "snapshot_json"),
            @Result(property = "itemCount",     column = "item_count"),
            @Result(property = "nodeCount",     column = "node_count"),
            @Result(property = "maxDepth",      column = "max_depth"),
            @Result(property = "tenantOid",     column = "tenant_oid"),
            @Result(property = "creator",       column = "creator"),
            @Result(property = "createdAt",     column = "created_at"),
            @Result(property = "updater",       column = "updater"),
            @Result(property = "updatedAt",     column = "updated_at")
    })
    BomSnapshot selectByOid(@Param("oid") String oid);

    /**
     * 记录列表：按零件（跨版本）或按迭代过滤。
     *
     * <p>写成 {@code WHERE 1 = 1 + <if>}：租户拦截器会把 {@code tenant_oid = ?} 追加在收尾子句
     * （ORDER BY）之前 —— 没有 WHERE 时它无处可插，所以这里必须先把 WHERE 开出来。
     */
    @Override
    @Select("<script>" + SELECT_COLUMNS + "WHERE 1 = 1 " +
            "<if test='partOid != null and partOid != \"\"'> AND part_oid = #{partOid}</if>" +
            "<if test='iterationOid != null and iterationOid != \"\"'> AND iteration_oid = #{iterationOid}</if>" +
            " ORDER BY created_at DESC" +
            "</script>")
    @ResultMap("bomSnapshotResult")
    List<BomSnapshot> selectList(@Param("partOid") String partOid,
                                 @Param("iterationOid") String iterationOid);
}
