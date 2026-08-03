/*
 * Copyright (c) 2025 深圳乘恺科技有限公司
 * All rights reserved.
 *
 * @author Kiween.Hu; Roney.Liu
 */

package cn.ck.plm.base.mapper.impl.postgresql;

import cn.ck.plm.base.entity.LifecycleTemplateMaster;
import cn.ck.plm.base.entity.LifecycleTemplateStatusRef;
import cn.ck.plm.base.entity.LifecycleTemplateTransitionRef;
import cn.ck.plm.base.mapper.LifecycleTemplateMapper;
import org.apache.ibatis.annotations.*;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;

import java.util.List;

/**
 * {@link LifecycleTemplateMapper} 的 PostgreSQL 实现（MyBatis 注解模式）。
 */
@Mapper
@ConditionalOnProperty(name = "plm.database.type", havingValue = "postgresql", matchIfMissing = true)
public interface PostgreSqlLifecycleTemplateMapper extends LifecycleTemplateMapper {

    // ==================== 模板主表 ====================

    @Override
    @Insert("INSERT INTO ck_lifecycle_template (oid, code, name, description, is_active, initial_state_code, tenant_oid, creator, created_at, updater, updated_at) " +
            "VALUES (#{oid}, #{code}, #{name}, #{description}, #{active,jdbcType=BOOLEAN}, #{initialStateCode}, #{tenantOid}, #{creator}, #{createdAt,jdbcType=TIMESTAMP}, #{updater}, #{updatedAt,jdbcType=TIMESTAMP})")
    int insert(LifecycleTemplateMaster template);

    @Override
    @Update("UPDATE ck_lifecycle_template SET name = #{name}, description = #{description}, is_active = #{active,jdbcType=BOOLEAN}, " +
            "initial_state_code = #{initialStateCode}, tenant_oid = #{tenantOid}, updater = #{updater}, updated_at = #{updatedAt,jdbcType=TIMESTAMP} WHERE code = #{code}")
    int update(LifecycleTemplateMaster template);

    @Override
    @Delete("DELETE FROM ck_lifecycle_template WHERE code = #{code}")
    int deleteByCode(@Param("code") String code);

    @Override
    @Select("SELECT oid, code, name, description, is_active, initial_state_code, tenant_oid, creator, created_at, updater, updated_at " +
            "FROM ck_lifecycle_template WHERE code = #{code}")
    @Results(id = "templateResult", value = {
            @Result(property = "oid",              column = "oid"),
            @Result(property = "code",             column = "code"),
            @Result(property = "name",             column = "name"),
            @Result(property = "description",      column = "description"),
            @Result(property = "active",           column = "is_active"),
            @Result(property = "initialStateCode", column = "initial_state_code"),
            @Result(property = "tenantOid",        column = "tenant_oid"),
            @Result(property = "creator",          column = "creator"),
            @Result(property = "createdAt",        column = "created_at"),
            @Result(property = "updater",          column = "updater"),
            @Result(property = "updatedAt",        column = "updated_at")
    })
    LifecycleTemplateMaster selectByCode(@Param("code") String code);

    @Override
    @Select("SELECT oid, code, name, description, is_active, initial_state_code, tenant_oid, creator, created_at, updater, updated_at " +
            "FROM ck_lifecycle_template ORDER BY name ASC")
    @ResultMap("templateResult")
    List<LifecycleTemplateMaster> selectAll();

    @Override
    @Select("SELECT oid, code, name, description, is_active, initial_state_code, tenant_oid, creator, created_at, updater, updated_at " +
            "FROM ck_lifecycle_template " +
            "WHERE LOWER(code) LIKE LOWER('%' || #{keyword} || '%') " +
            "OR LOWER(name) LIKE LOWER('%' || #{keyword} || '%') " +
            "ORDER BY name ASC")
    @ResultMap("templateResult")
    List<LifecycleTemplateMaster> search(@Param("keyword") String keyword);

    @Override
    @Select("SELECT COUNT(*) FROM ck_lifecycle_template WHERE code = #{code}")
    int existsByCode(@Param("code") String code);

    // ==================== 模板状态关联 ====================

    @Override
    @Insert("INSERT INTO ck_lifecycle_template_state (oid, iteration_oid, status_code, status_display_name, sort_order) " +
            "VALUES (#{oid}, #{iterationOid}, #{statusCode}, #{statusDisplayName}, #{sortOrder,jdbcType=INTEGER})")
    int insertStateRef(LifecycleTemplateStatusRef ref);

    @Override
    @Delete("DELETE FROM ck_lifecycle_template_state WHERE iteration_oid = #{iterationOid}")
    int deleteStateRefsByIterationOid(@Param("iterationOid") String iterationOid);

    @Override
    @Select("SELECT oid, iteration_oid, status_code, status_display_name, sort_order " +
            "FROM ck_lifecycle_template_state WHERE iteration_oid = #{iterationOid} ORDER BY sort_order ASC")
    @Results(id = "stateRefResult", value = {
            @Result(property = "oid",               column = "oid"),
            @Result(property = "iterationOid",      column = "iteration_oid"),
            @Result(property = "statusCode",        column = "status_code"),
            @Result(property = "statusDisplayName", column = "status_display_name"),
            @Result(property = "sortOrder",         column = "sort_order")
    })
    List<LifecycleTemplateStatusRef> selectStateRefsByIterationOid(@Param("iterationOid") String iterationOid);

    // ==================== 模板流转规则 ====================

    @Override
    @Insert("INSERT INTO ck_lifecycle_template_transition (oid, iteration_oid, from_status_code, to_status_code, transition_type) " +
            "VALUES (#{oid}, #{iterationOid}, #{fromStatusCode}, #{toStatusCode}, #{transitionType})")
    int insertTransitionRef(LifecycleTemplateTransitionRef ref);

    @Override
    @Delete("DELETE FROM ck_lifecycle_template_transition WHERE iteration_oid = #{iterationOid}")
    int deleteTransitionRefsByIterationOid(@Param("iterationOid") String iterationOid);

    @Override
    @Select("SELECT oid, iteration_oid, from_status_code, to_status_code, transition_type " +
            "FROM ck_lifecycle_template_transition WHERE iteration_oid = #{iterationOid}")
    @Results(id = "transitionRefResult", value = {
            @Result(property = "oid",             column = "oid"),
            @Result(property = "iterationOid",    column = "iteration_oid"),
            @Result(property = "fromStatusCode",  column = "from_status_code"),
            @Result(property = "toStatusCode",    column = "to_status_code"),
            @Result(property = "transitionType",  column = "transition_type")
    })
    List<LifecycleTemplateTransitionRef> selectTransitionRefsByIterationOid(@Param("iterationOid") String iterationOid);
}
