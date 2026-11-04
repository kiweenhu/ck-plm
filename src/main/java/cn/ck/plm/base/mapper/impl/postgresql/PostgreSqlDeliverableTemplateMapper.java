/*
 * Copyright (c) 2026~2028 深圳乘恺科技有限公司
 * All rights reserved.
 */

package cn.ck.plm.base.mapper.impl.postgresql;

import cn.ck.plm.base.entity.DeliverableTemplate;
import cn.ck.plm.base.mapper.DeliverableTemplateMapper;
import org.apache.ibatis.annotations.*;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;

import java.util.List;

/**
 * {@link DeliverableTemplateMapper} 的 PostgreSQL 实现。
 *
 * <p>每条语句都<b>显式</b>写明 {@code tenant_oid}（INSERT 写列、SELECT 写条件）：
 * 显式书写使语义<b>不依赖拦截器注入</b>（拦截器见 SQL 已含 tenant_oid 会跳过改写），固定为"只本租户"。
 * 2026-10-10 起由"本租户一份 + 平台一份"改为按租户隔离 —— 模板随包按租户各装一份。
 */
@Mapper
@ConditionalOnProperty(name = "plm.database.type", havingValue = "postgresql", matchIfMissing = true)
public interface PostgreSqlDeliverableTemplateMapper extends DeliverableTemplateMapper {

    /** 查询列（与实体字段一一对应） */
    String COLUMNS = "oid, code, name, description, industry, stage_key, discipline, mandatory, "
            + "owner_role, object_type, bind_mode, completion_rule, due_offset_days, gate, "
            + "industry_note, disabled, sort_order, tenant_oid, creator, created_at, updater, updated_at";

    @Override
    @Insert("INSERT INTO ck_deliverable_template (" + COLUMNS + ") " +
            "VALUES (#{oid}, #{code}, #{name}, #{description}, #{industry}, #{stageKey}, #{discipline}, " +
            "#{mandatory}, #{ownerRole}, #{objectType}, #{bindMode}, #{completionRule}, #{dueOffsetDays}, " +
            "#{gate}, #{industryNote}, #{disabled}, #{sortOrder}, #{tenantOid}, " +
            "#{creator}, #{createdAt}, #{updater}, #{updatedAt})")
    int insert(DeliverableTemplate template);

    @Override
    @Update("UPDATE ck_deliverable_template SET name = #{name}, description = #{description}, " +
            "industry = #{industry}, stage_key = #{stageKey}, discipline = #{discipline}, " +
            "mandatory = #{mandatory}, owner_role = #{ownerRole}, object_type = #{objectType}, " +
            "bind_mode = #{bindMode}, completion_rule = #{completionRule}, " +
            "due_offset_days = #{dueOffsetDays}, gate = #{gate}, industry_note = #{industryNote}, " +
            "disabled = #{disabled}, sort_order = #{sortOrder}, " +
            "updater = #{updater}, updated_at = #{updatedAt} WHERE oid = #{oid}")
    int update(DeliverableTemplate template);

    @Override
    @Delete("DELETE FROM ck_deliverable_template WHERE oid = #{oid}")
    int deleteByOid(@Param("oid") String oid);

    @Override
    @Select("SELECT " + COLUMNS + " FROM ck_deliverable_template WHERE oid = #{oid}")
    @Results(id = "deliverableTemplateResult", value = {
            @Result(property = "oid",            column = "oid"),
            @Result(property = "code",           column = "code"),
            @Result(property = "name",           column = "name"),
            @Result(property = "description",    column = "description"),
            @Result(property = "industry",       column = "industry"),
            @Result(property = "stageKey",       column = "stage_key"),
            @Result(property = "discipline",     column = "discipline"),
            @Result(property = "mandatory",      column = "mandatory"),
            @Result(property = "ownerRole",      column = "owner_role"),
            @Result(property = "objectType",     column = "object_type"),
            @Result(property = "bindMode",       column = "bind_mode"),
            @Result(property = "completionRule", column = "completion_rule"),
            @Result(property = "dueOffsetDays",  column = "due_offset_days"),
            @Result(property = "gate",           column = "gate"),
            @Result(property = "industryNote",   column = "industry_note"),
            @Result(property = "disabled",       column = "disabled"),
            @Result(property = "sortOrder",      column = "sort_order"),
            @Result(property = "tenantOid",      column = "tenant_oid"),
            @Result(property = "creator",        column = "creator"),
            @Result(property = "createdAt",      column = "created_at"),
            @Result(property = "updater",        column = "updater"),
            @Result(property = "updatedAt",      column = "updated_at")
    })
    DeliverableTemplate selectByOid(@Param("oid") String oid);

    @Override
    @Select("SELECT " + COLUMNS + " FROM ck_deliverable_template WHERE code = #{code} " +
            "AND tenant_oid = #{tenantOid} LIMIT 1")
    @ResultMap("deliverableTemplateResult")
    DeliverableTemplate selectByCode(@Param("code") String code,
                                     @Param("tenantOid") String tenantOid,
                                     @Param("platformOid") String platformOid);

    @Override
    @Select("SELECT " + COLUMNS + " FROM ck_deliverable_template " +
            "WHERE tenant_oid = #{tenantOid} " +
            "ORDER BY stage_key, sort_order ASC")
    @ResultMap("deliverableTemplateResult")
    List<DeliverableTemplate> selectAll(@Param("tenantOid") String tenantOid,
                                        @Param("platformOid") String platformOid);

    @Override
    @Select("SELECT " + COLUMNS + " FROM ck_deliverable_template " +
            "WHERE tenant_oid = #{tenantOid} " +
            "ORDER BY industry, stage_key, sort_order ASC")
    @ResultMap("deliverableTemplateResult")
    List<DeliverableTemplate> selectByTenant(@Param("tenantOid") String tenantOid);

    @Override
    @Select("SELECT COUNT(*) FROM ck_deliverable_template WHERE code = #{code} " +
            "AND tenant_oid = #{tenantOid}")
    int existsByCode(@Param("code") String code,
                     @Param("tenantOid") String tenantOid,
                     @Param("platformOid") String platformOid);
}
