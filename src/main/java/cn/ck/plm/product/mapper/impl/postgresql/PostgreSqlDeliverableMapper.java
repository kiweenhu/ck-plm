/*
 * Copyright (c) 2026~2028 深圳乘恺科技有限公司
 * All rights reserved.
 */

package cn.ck.plm.product.mapper.impl.postgresql;

import cn.ck.plm.product.entity.Deliverable;
import cn.ck.plm.product.mapper.DeliverableMapper;
import org.apache.ibatis.annotations.*;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;

import java.util.List;

/**
 * {@link DeliverableMapper} 的 PostgreSQL 实现。
 *
 * <p>字段映射靠 MyBatis 的 {@code map-underscore-to-camel-case}（application.yml 已开），
 * 故不写 {@code @Results}。{@code tenant_oid} 不入 INSERT 列（由租户拦截器注入），
 * 也不入 SELECT 列（与 ck_stage 同口径）。
 */
@Mapper
@ConditionalOnProperty(name = "plm.database.type", havingValue = "postgresql", matchIfMissing = true)
public interface PostgreSqlDeliverableMapper extends DeliverableMapper {

    String COLS = "oid, owner_oid, owner_type, code, name, description, stage_key, discipline, "
            + "mandatory, owner_role, object_type, bind_mode, completion_rule, due_offset_days, "
            + "gate, industry_note, template_code, status, owner, due_date, evidence, artifact_refs, "
            + "disabled, sort_order, creator, created_at, updater, updated_at";

    @Override
    @Insert("INSERT INTO ck_deliverable (" + COLS + ") VALUES ("
            + "#{oid}, #{ownerOid}, #{ownerType}, #{code}, #{name}, #{description}, #{stageKey}, "
            + "#{discipline}, #{mandatory}, #{ownerRole}, #{objectType}, #{bindMode}, #{completionRule}, "
            + "#{dueOffsetDays}, #{gate}, #{industryNote}, #{templateCode}, #{status}, #{owner}, #{dueDate}, "
            + "#{evidence}, #{artifactRefs}, #{disabled}, #{sortOrder}, "
            + "#{creator}, #{createdAt}, #{updater}, #{updatedAt})")
    int insert(Deliverable deliverable);

    @Override
    @Select("SELECT " + COLS + " FROM ck_deliverable WHERE oid = #{oid}")
    Deliverable selectByOid(@Param("oid") String oid);

    @Override
    @Select("SELECT " + COLS + " FROM ck_deliverable WHERE owner_oid = #{ownerOid} "
            + "ORDER BY stage_key ASC, sort_order ASC, code ASC")
    List<Deliverable> selectByOwnerOid(@Param("ownerOid") String ownerOid);

    @Override
    @Select("SELECT COUNT(*) FROM ck_deliverable WHERE owner_oid = #{ownerOid}")
    int countByOwnerOid(@Param("ownerOid") String ownerOid);

    @Override
    @Delete("DELETE FROM ck_deliverable WHERE owner_oid = #{ownerOid}")
    int deleteByOwnerOid(@Param("ownerOid") String ownerOid);

    @Override
    @Update("UPDATE ck_deliverable SET status = #{status}, updated_at = CURRENT_TIMESTAMP WHERE oid = #{oid}")
    int updateStatus(@Param("oid") String oid, @Param("status") String status);

    @Override
    @Update("UPDATE ck_deliverable SET disabled = #{disabled}, updated_at = CURRENT_TIMESTAMP WHERE oid = #{oid}")
    int updateDisabled(@Param("oid") String oid, @Param("disabled") boolean disabled);

    @Override
    @Update("UPDATE ck_deliverable SET mandatory = #{mandatory}, updated_at = CURRENT_TIMESTAMP WHERE oid = #{oid}")
    int updateMandatory(@Param("oid") String oid, @Param("mandatory") boolean mandatory);

    @Override
    @Update("UPDATE ck_deliverable SET owner_role = #{ownerRole}, updated_at = CURRENT_TIMESTAMP WHERE oid = #{oid}")
    int updateOwnerRole(@Param("oid") String oid, @Param("ownerRole") String ownerRole);
}
