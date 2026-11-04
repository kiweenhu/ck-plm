/*
 * Copyright (c) 2026~2028 深圳乘恺科技有限公司
 * All rights reserved.
 */

package cn.ck.plm.product.mapper.impl.postgresql;

import cn.ck.plm.product.entity.Gate;
import cn.ck.plm.product.mapper.GateMapper;
import org.apache.ibatis.annotations.*;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;

import java.util.List;

/**
 * {@link GateMapper} 的 PostgreSQL 实现。
 *
 * <p>{@code tenant_oid} 不入 INSERT 列（租户拦截器注入），也不入 SELECT 列（与 ck_stage 同口径）。
 */
@Mapper
@ConditionalOnProperty(name = "plm.database.type", havingValue = "postgresql", matchIfMissing = true)
public interface PostgreSqlGateMapper extends GateMapper {

    String COLS = "oid, owner_oid, owner_type, code, name, description, stage_key, type, process_key, "
            + "decision_options, auto_checks, required_participants, record_fields, evidence_required, "
            + "industry_note, template_code, status, decision, decided_by, decided_at, decision_note, "
            + "disabled, sort_order, creator, created_at, updater, updated_at";

    @Override
    @Insert("INSERT INTO ck_gate (" + COLS + ") VALUES ("
            + "#{oid}, #{ownerOid}, #{ownerType}, #{code}, #{name}, #{description}, #{stageKey}, #{type}, "
            + "#{processKey}, #{decisionOptions}, #{autoChecks}, #{requiredParticipants}, #{recordFields}, "
            + "#{evidenceRequired}, #{industryNote}, #{templateCode}, #{status}, #{decision}, #{decidedBy}, "
            + "#{decidedAt}, #{decisionNote}, #{disabled}, #{sortOrder}, "
            + "#{creator}, #{createdAt}, #{updater}, #{updatedAt})")
    int insert(Gate gate);

    @Override
    @Select("SELECT " + COLS + " FROM ck_gate WHERE oid = #{oid}")
    Gate selectByOid(@Param("oid") String oid);

    @Override
    @Select("SELECT " + COLS + " FROM ck_gate WHERE owner_oid = #{ownerOid} "
            + "ORDER BY stage_key ASC, sort_order ASC, code ASC")
    List<Gate> selectByOwnerOid(@Param("ownerOid") String ownerOid);

    @Override
    @Select("SELECT COUNT(*) FROM ck_gate WHERE owner_oid = #{ownerOid}")
    int countByOwnerOid(@Param("ownerOid") String ownerOid);

    @Override
    @Delete("DELETE FROM ck_gate WHERE owner_oid = #{ownerOid}")
    int deleteByOwnerOid(@Param("ownerOid") String ownerOid);

    @Override
    @Update("UPDATE ck_gate SET disabled = #{disabled}, updated_at = CURRENT_TIMESTAMP WHERE oid = #{oid}")
    int updateDisabled(@Param("oid") String oid, @Param("disabled") boolean disabled);
}
