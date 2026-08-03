/*
 * Copyright (c) 2025 深圳乘恺科技有限公司
 * All rights reserved.
 */

package cn.ck.plm.softtype.mapper.impl.postgresql;

import cn.ck.plm.softtype.entity.TypeNumberRuleLink;
import cn.ck.plm.softtype.mapper.TypeNumberRuleLinkMapper;
import org.apache.ibatis.annotations.*;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;

import java.util.List;

@Mapper
@ConditionalOnProperty(name = "plm.database.type", havingValue = "postgresql", matchIfMissing = true)
public interface PostgreSqlTypeNumberRuleLinkMapper extends TypeNumberRuleLinkMapper {

    String TABLE = "ck_type_number_rule_link";
    String COLS = "oid, type_oid, number_rule_code, tenant_oid, creator, created_at, updater, updated_at";

    @Override
    @Insert("INSERT INTO " + TABLE + " (oid, type_oid, number_rule_code, tenant_oid, creator, created_at, updater, updated_at) "
            + "VALUES (#{oid}, #{typeOid}, #{numberRuleCode}, #{tenantOid}, #{creator}, #{createdAt}, #{updater}, #{updatedAt})")
    int insert(TypeNumberRuleLink link);

    @Override
    @Update("UPDATE " + TABLE + " SET number_rule_code = #{numberRuleCode}, tenant_oid = #{tenantOid}, "
            + "updater = #{updater}, updated_at = CURRENT_TIMESTAMP WHERE oid = #{oid}")
    int update(TypeNumberRuleLink link);

    @Override
    @Delete("DELETE FROM " + TABLE + " WHERE oid = #{oid}")
    int deleteByOid(@Param("oid") String oid);

    @Override
    @Delete("DELETE FROM " + TABLE + " WHERE type_oid = #{typeOid}")
    int deleteByTypeOid(@Param("typeOid") String typeOid);

    @Override
    @Select("SELECT " + COLS + " FROM " + TABLE + " WHERE type_oid = #{typeOid}")
    TypeNumberRuleLink selectByTypeOid(@Param("typeOid") String typeOid);

    @Override
    @Select("SELECT " + COLS + " FROM " + TABLE + " WHERE oid = #{oid}")
    TypeNumberRuleLink selectByOid(@Param("oid") String oid);

    @Override
    @Select("SELECT " + COLS + " FROM " + TABLE + " ORDER BY created_at")
    List<TypeNumberRuleLink> selectAll();

    @Override
    @Select("SELECT " + COLS + " FROM " + TABLE + " WHERE number_rule_code = #{numberRuleCode}")
    List<TypeNumberRuleLink> selectByNumberRuleCode(@Param("numberRuleCode") String numberRuleCode);

    @Override
    @Select("SELECT COUNT(*) FROM " + TABLE + " WHERE type_oid = #{typeOid}")
    int existsByTypeOid(@Param("typeOid") String typeOid);
}
