/*
 * Copyright (c) 2026~2028 深圳乘恺科技有限公司
 * All rights reserved.
 */

package cn.ck.plm.base.mapper.impl.postgresql;

import cn.ck.plm.base.entity.RuleTemplate;
import cn.ck.plm.base.mapper.RuleTemplateMapper;
import org.apache.ibatis.annotations.*;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;

import java.util.List;

/**
 * {@link RuleTemplateMapper} 的 PostgreSQL 实现。
 *
 * <p>每条语句都<b>显式</b>写明 {@code tenant_oid}（INSERT 写列、SELECT 写条件）：
 * 显式书写使语义<b>不依赖拦截器注入</b>（拦截器见 SQL 已含 tenant_oid 会跳过改写），固定为"只本租户"。
 * 2026-10-10 起由"本租户一份 + 平台一份"改为按租户隔离 —— 模板随包按租户各装一份。
 */
@Mapper
@ConditionalOnProperty(name = "plm.database.type", havingValue = "postgresql", matchIfMissing = true)
public interface PostgreSqlRuleTemplateMapper extends RuleTemplateMapper {

    /** 查询列（与实体字段一一对应） */
    String COLUMNS = "oid, code, name, description, industry, trigger_type, scope, category, "
            + "stage_key, gate, condition, severity, message, default_override, fix_action, "
            + "industry_note, disabled, sort_order, tenant_oid, creator, created_at, updater, updated_at";

    @Override
    @Insert("INSERT INTO ck_rule_template (" + COLUMNS + ") " +
            "VALUES (#{oid}, #{code}, #{name}, #{description}, #{industry}, #{triggerType}, #{scope}, " +
            "#{category}, #{stageKey}, #{gate}, #{condition}, #{severity}, #{message}, " +
            "#{defaultOverride}, #{fixAction}, #{industryNote}, #{disabled}, #{sortOrder}, #{tenantOid}, " +
            "#{creator}, #{createdAt}, #{updater}, #{updatedAt})")
    int insert(RuleTemplate template);

    @Override
    @Update("UPDATE ck_rule_template SET name = #{name}, description = #{description}, " +
            "industry = #{industry}, trigger_type = #{triggerType}, scope = #{scope}, " +
            "category = #{category}, stage_key = #{stageKey}, gate = #{gate}, " +
            "condition = #{condition}, severity = #{severity}, message = #{message}, " +
            "default_override = #{defaultOverride}, fix_action = #{fixAction}, " +
            "industry_note = #{industryNote}, disabled = #{disabled}, sort_order = #{sortOrder}, " +
            "updater = #{updater}, updated_at = #{updatedAt} WHERE oid = #{oid}")
    int update(RuleTemplate template);

    @Override
    @Delete("DELETE FROM ck_rule_template WHERE oid = #{oid}")
    int deleteByOid(@Param("oid") String oid);

    @Override
    @Select("SELECT " + COLUMNS + " FROM ck_rule_template WHERE oid = #{oid}")
    @Results(id = "ruleTemplateResult", value = {
            @Result(property = "oid",             column = "oid"),
            @Result(property = "code",            column = "code"),
            @Result(property = "name",            column = "name"),
            @Result(property = "description",     column = "description"),
            @Result(property = "industry",        column = "industry"),
            @Result(property = "triggerType",     column = "trigger_type"),
            @Result(property = "scope",           column = "scope"),
            @Result(property = "category",        column = "category"),
            @Result(property = "stageKey",        column = "stage_key"),
            @Result(property = "gate",            column = "gate"),
            @Result(property = "condition",       column = "condition"),
            @Result(property = "severity",        column = "severity"),
            @Result(property = "message",         column = "message"),
            @Result(property = "defaultOverride", column = "default_override"),
            @Result(property = "fixAction",       column = "fix_action"),
            @Result(property = "industryNote",    column = "industry_note"),
            @Result(property = "disabled",        column = "disabled"),
            @Result(property = "sortOrder",       column = "sort_order"),
            @Result(property = "tenantOid",       column = "tenant_oid"),
            @Result(property = "creator",         column = "creator"),
            @Result(property = "createdAt",       column = "created_at"),
            @Result(property = "updater",         column = "updater"),
            @Result(property = "updatedAt",       column = "updated_at")
    })
    RuleTemplate selectByOid(@Param("oid") String oid);

    @Override
    @Select("SELECT " + COLUMNS + " FROM ck_rule_template WHERE code = #{code} " +
            "AND tenant_oid = #{tenantOid} LIMIT 1")
    @ResultMap("ruleTemplateResult")
    RuleTemplate selectByCode(@Param("code") String code,
                              @Param("tenantOid") String tenantOid,
                              @Param("platformOid") String platformOid);

    @Override
    @Select("SELECT " + COLUMNS + " FROM ck_rule_template " +
            "WHERE tenant_oid = #{tenantOid} " +
            "ORDER BY sort_order ASC")
    @ResultMap("ruleTemplateResult")
    List<RuleTemplate> selectAll(@Param("tenantOid") String tenantOid,
                                 @Param("platformOid") String platformOid);

    @Override
    @Select("SELECT " + COLUMNS + " FROM ck_rule_template " +
            "WHERE tenant_oid = #{tenantOid} " +
            "ORDER BY industry, trigger_type, sort_order ASC")
    @ResultMap("ruleTemplateResult")
    List<RuleTemplate> selectByTenant(@Param("tenantOid") String tenantOid);

    @Override
    @Select("SELECT COUNT(*) FROM ck_rule_template WHERE code = #{code} " +
            "AND tenant_oid IN (#{tenantOid}, #{platformOid})")
    int existsByCode(@Param("code") String code,
                     @Param("tenantOid") String tenantOid,
                     @Param("platformOid") String platformOid);
}
