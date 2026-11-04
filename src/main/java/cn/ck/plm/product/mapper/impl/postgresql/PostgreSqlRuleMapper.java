/*
 * Copyright (c) 2026~2028 深圳乘恺科技有限公司
 * All rights reserved.
 */

package cn.ck.plm.product.mapper.impl.postgresql;

import cn.ck.plm.product.entity.Rule;
import cn.ck.plm.product.mapper.RuleMapper;
import org.apache.ibatis.annotations.*;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;

import java.util.List;

/**
 * {@link RuleMapper} 的 PostgreSQL 实现。
 *
 * <p>注意 {@code condition} 是 PostgreSQL 的非保留关键字，可直接作列名使用（模板表已在用）。
 */
@Mapper
@ConditionalOnProperty(name = "plm.database.type", havingValue = "postgresql", matchIfMissing = true)
public interface PostgreSqlRuleMapper extends RuleMapper {

    String COLS = "oid, owner_oid, owner_type, code, name, description, trigger_type, scope, category, "
            + "stage_key, gate, condition, severity, message, default_override, fix_action, industry_note, "
            + "template_code, status, last_checked_at, findings, disabled, sort_order, "
            + "creator, created_at, updater, updated_at";

    @Override
    @Insert("INSERT INTO ck_rule (" + COLS + ") VALUES ("
            + "#{oid}, #{ownerOid}, #{ownerType}, #{code}, #{name}, #{description}, #{triggerType}, "
            + "#{scope}, #{category}, #{stageKey}, #{gate}, #{condition}, #{severity}, #{message}, "
            + "#{defaultOverride}, #{fixAction}, #{industryNote}, #{templateCode}, #{status}, "
            + "#{lastCheckedAt}, #{findings}, #{disabled}, #{sortOrder}, "
            + "#{creator}, #{createdAt}, #{updater}, #{updatedAt})")
    int insert(Rule rule);

    @Override
    @Select("SELECT " + COLS + " FROM ck_rule WHERE oid = #{oid}")
    Rule selectByOid(@Param("oid") String oid);

    @Override
    @Select("SELECT " + COLS + " FROM ck_rule WHERE owner_oid = #{ownerOid} "
            + "ORDER BY stage_key ASC NULLS FIRST, sort_order ASC, code ASC")
    List<Rule> selectByOwnerOid(@Param("ownerOid") String ownerOid);

    @Override
    @Select("SELECT COUNT(*) FROM ck_rule WHERE owner_oid = #{ownerOid}")
    int countByOwnerOid(@Param("ownerOid") String ownerOid);

    @Override
    @Delete("DELETE FROM ck_rule WHERE owner_oid = #{ownerOid}")
    int deleteByOwnerOid(@Param("ownerOid") String ownerOid);

    @Override
    @Update("UPDATE ck_rule SET disabled = #{disabled}, updated_at = CURRENT_TIMESTAMP WHERE oid = #{oid}")
    int updateDisabled(@Param("oid") String oid, @Param("disabled") boolean disabled);
}
