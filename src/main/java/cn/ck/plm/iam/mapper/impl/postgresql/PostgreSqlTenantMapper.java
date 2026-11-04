/*
 * Copyright (c) 2026~2028 深圳乘恺科技有限公司
 * All rights reserved.
 *
 * @author Kiween.Hu; Roney.Liu
 */

package cn.ck.plm.iam.mapper.impl.postgresql;

import cn.ck.plm.iam.entity.Tenant;
import cn.ck.plm.iam.mapper.TenantMapper;
import org.apache.ibatis.annotations.*;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;

import java.util.List;

/**
 * 租户表（{@code ck_tenant}）PostgreSQL 实现。
 *
 * <p><b>列清单统一在 {@link #COLUMNS} / {@link #RESULTS} 里维护</b>：租户表有 4 处 SELECT
 * （按 oid / 按 tenant_id / 待审核 / 已激活），早前每处各写一遍列名 —— 加一组字段要改 4 遍，
 * 漏一处就出现"列表查得到、详情查不到"的鬼影。行业引导的 7 个字段（industry_code /
 * dev_method_code / industry_pack_code / onboarding_state / onboarded_at / onboarded_by /
 * profile_json）按同一口径补齐。
 */
@Mapper
@ConditionalOnProperty(name = "plm.database.type", havingValue = "postgresql", matchIfMissing = true)
public interface PostgreSqlTenantMapper extends TenantMapper {

    /** 全列清单（含行业引导字段） */
    String COLUMNS = "oid, tenant_id, name, status, contact_name, contact_email, "
            + "admin_username, admin_display_name, "
            + "approved_at, approved_by, reject_reason, "
            + "industry_code, dev_method_code, industry_pack_code, "
            + "onboarding_state, onboarded_at, onboarded_by, profile_json, "
            + "creator, created_at, updater, updated_at";

    @Override
    @Insert("INSERT INTO ck_tenant (oid, tenant_id, name, status, contact_name, contact_email, "
            + "admin_username, admin_password, admin_display_name, "
            + "industry_code, dev_method_code, industry_pack_code, "
            + "onboarding_state, onboarded_at, onboarded_by, profile_json, "
            + "creator, created_at, updater, updated_at) "
            + "VALUES (#{oid}, #{tenantId}, #{name}, #{status}, #{contactName}, #{contactEmail}, "
            + "#{adminUsername}, #{adminPassword}, #{adminDisplayName}, "
            + "#{industryCode}, #{devMethodCode}, #{industryPackCode}, "
            + "#{onboardingState}, #{onboardedAt}, #{onboardedBy}, #{profileJson}, "
            + "#{creator}, #{createdAt}, #{updater}, #{updatedAt})")
    int insert(Tenant tenant);

    @Override
    @Update("UPDATE ck_tenant SET status = #{status}, approved_at = #{approvedAt}, approved_by = #{approvedBy}, "
            + "reject_reason = #{rejectReason}, "
            + "industry_code = #{industryCode}, dev_method_code = #{devMethodCode}, "
            + "industry_pack_code = #{industryPackCode}, onboarding_state = #{onboardingState}, "
            + "onboarded_at = #{onboardedAt}, onboarded_by = #{onboardedBy}, profile_json = #{profileJson}, "
            + "updater = #{updater}, updated_at = #{updatedAt} "
            + "WHERE oid = #{oid}")
    int update(Tenant tenant);

    @Override
    @Select("SELECT " + COLUMNS + " FROM ck_tenant WHERE oid = #{oid}")
    @Results(id = "tenantResult", value = {
            @Result(property = "oid",            column = "oid"),
            @Result(property = "tenantId",       column = "tenant_id"),
            @Result(property = "name",           column = "name"),
            @Result(property = "status",         column = "status"),
            @Result(property = "contactName",    column = "contact_name"),
            @Result(property = "contactEmail",   column = "contact_email"),
            @Result(property = "adminUsername",  column = "admin_username"),
            @Result(property = "adminDisplayName", column = "admin_display_name"),
            @Result(property = "approvedAt",     column = "approved_at"),
            @Result(property = "approvedBy",     column = "approved_by"),
            @Result(property = "rejectReason",   column = "reject_reason"),
            @Result(property = "industryCode",   column = "industry_code"),
            @Result(property = "devMethodCode",  column = "dev_method_code"),
            @Result(property = "industryPackCode", column = "industry_pack_code"),
            @Result(property = "onboardingState", column = "onboarding_state"),
            @Result(property = "onboardedAt",    column = "onboarded_at"),
            @Result(property = "onboardedBy",    column = "onboarded_by"),
            @Result(property = "profileJson",    column = "profile_json"),
            @Result(property = "creator",        column = "creator"),
            @Result(property = "createdAt",      column = "created_at"),
            @Result(property = "updater",        column = "updater"),
            @Result(property = "updatedAt",      column = "updated_at")
    })
    Tenant selectByOid(@Param("oid") String oid);

    @Override
    @Select("SELECT " + COLUMNS + " FROM ck_tenant WHERE tenant_id = #{tenantId}")
    @ResultMap("tenantResult")
    Tenant selectByTenantId(@Param("tenantId") String tenantId);

    @Override
    @Select("SELECT COUNT(*) > 0 FROM ck_tenant WHERE tenant_id = #{tenantId}")
    boolean existsByTenantId(@Param("tenantId") String tenantId);

    @Override
    @Select("SELECT " + COLUMNS + " FROM ck_tenant WHERE status = 'PENDING' ORDER BY created_at ASC")
    @ResultMap("tenantResult")
    List<Tenant> selectPending();

    @Override
    @Select("SELECT COUNT(*) FROM ck_tenant WHERE status = 'PENDING'")
    int countPending();

    @Override
    @Select("SELECT " + COLUMNS + " FROM ck_tenant WHERE status = 'ACTIVE' ORDER BY created_at ASC")
    @ResultMap("tenantResult")
    List<Tenant> selectActive();
}
