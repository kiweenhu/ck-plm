/*
 * Copyright (c) 2025 深圳乘恺科技有限公司
 * All rights reserved.
 */

package cn.ck.plm.base.mapper.impl.postgresql;

import cn.ck.plm.base.entity.VersionRule;
import cn.ck.plm.base.mapper.VersionRuleMapper;
import org.apache.ibatis.annotations.*;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;

import java.util.List;

/**
 * {@link VersionRuleMapper} 的 PostgreSQL 实现（MyBatis 注解模式）。
 */
@Mapper
@ConditionalOnProperty(name = "plm.database.type", havingValue = "postgresql", matchIfMissing = true)
public interface PostgreSqlVersionRuleMapper extends VersionRuleMapper {

    String TABLE = "ck_version_rule";

    @Override
    @Insert("INSERT INTO " + TABLE + " (oid, name, code, rule_definition, description, " +
            "applicable_type, sequence_value, enabled, tenant_oid, creator, created_at, updater, updated_at) " +
            "VALUES (#{oid}, #{name}, #{code}, #{ruleDefinition}, #{description}, " +
            "#{applicableType}, #{sequenceValue}, #{enabled}, #{tenantOid}, #{creator}, #{createdAt}, #{updater}, #{updatedAt})")
    int insert(VersionRule rule);

    @Override
    @Update("UPDATE " + TABLE + " SET name = #{name}, code = #{code}, " +
            "rule_definition = #{ruleDefinition}, description = #{description}, " +
            "applicable_type = #{applicableType}, sequence_value = #{sequenceValue}, " +
            "enabled = #{enabled}, tenant_oid = #{tenantOid}, updater = #{updater}, updated_at = CURRENT_TIMESTAMP " +
            "WHERE oid = #{oid}")
    int update(VersionRule rule);

    @Override
    @Delete("DELETE FROM " + TABLE + " WHERE oid = #{oid}")
    int deleteByOid(@Param("oid") String oid);

    @Override
    @Select("SELECT oid, name, code, rule_definition, description, applicable_type, " +
            "sequence_value, enabled, tenant_oid, creator, created_at, updater, updated_at " +
            "FROM " + TABLE + " WHERE oid = #{oid}")
    @Results({
        @Result(property = "ruleDefinition", column = "rule_definition"),
        @Result(property = "applicableType", column = "applicable_type"),
        @Result(property = "sequenceValue", column = "sequence_value"),
        @Result(property = "createdAt", column = "created_at"),
        @Result(property = "updatedAt", column = "updated_at")
    })
    VersionRule selectByOid(@Param("oid") String oid);

    @Override
    @Select("SELECT oid, name, code, rule_definition, description, applicable_type, " +
            "sequence_value, enabled, tenant_oid, creator, created_at, updater, updated_at " +
            "FROM " + TABLE + " WHERE code = #{code}")
    @Results({
        @Result(property = "ruleDefinition", column = "rule_definition"),
        @Result(property = "applicableType", column = "applicable_type"),
        @Result(property = "sequenceValue", column = "sequence_value"),
        @Result(property = "createdAt", column = "created_at"),
        @Result(property = "updatedAt", column = "updated_at")
    })
    VersionRule selectByCode(@Param("code") String code);

    @Override
    @Select("SELECT oid, name, code, rule_definition, description, applicable_type, " +
            "sequence_value, enabled, tenant_oid, creator, created_at, updater, updated_at " +
            "FROM " + TABLE + " ORDER BY created_at")
    @Results({
        @Result(property = "ruleDefinition", column = "rule_definition"),
        @Result(property = "applicableType", column = "applicable_type"),
        @Result(property = "sequenceValue", column = "sequence_value"),
        @Result(property = "createdAt", column = "created_at"),
        @Result(property = "updatedAt", column = "updated_at")
    })
    List<VersionRule> selectAll();

    @Override
    @Select("SELECT COUNT(*) FROM " + TABLE)
    int count();

    @Override
    @Update("UPDATE " + TABLE + " SET sequence_value = sequence_value + 1, " +
            "updated_at = CURRENT_TIMESTAMP WHERE code = #{code} RETURNING sequence_value")
    Long incrementAndGetSequence(@Param("code") String code);

    @Override
    @Select("SELECT COUNT(*) FROM " + TABLE + " WHERE code = #{code}")
    int existsByCode(@Param("code") String code);
}
