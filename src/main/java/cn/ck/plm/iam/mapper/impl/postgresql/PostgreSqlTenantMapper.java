/*
 * Copyright (c) 2025 深圳乘恺科技有限公司
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

@Mapper
@ConditionalOnProperty(name = "plm.database.type", havingValue = "postgresql", matchIfMissing = true)
public interface PostgreSqlTenantMapper extends TenantMapper {

    @Override
    @Insert("INSERT INTO ck_tenant (oid, tenant_id, name, status, contact_name, contact_email, "
            + "admin_username, admin_password, admin_display_name, "
            + "creator, created_at, updater, updated_at) "
            + "VALUES (#{oid}, #{tenantId}, #{name}, #{status}, #{contactName}, #{contactEmail}, "
            + "#{adminUsername}, #{adminPassword}, #{adminDisplayName}, "
            + "#{creator}, #{createdAt}, #{updater}, #{updatedAt})")
    int insert(Tenant tenant);

    @Override
    @Update("UPDATE ck_tenant SET status = #{status}, approved_at = #{approvedAt}, approved_by = #{approvedBy}, "
            + "reject_reason = #{rejectReason}, updater = #{updater}, updated_at = #{updatedAt} "
            + "WHERE oid = #{oid}")
    int update(Tenant tenant);

    @Override
    @Select("SELECT oid, tenant_id, name, status, contact_name, contact_email, "
            + "admin_username, admin_display_name, "
            + "approved_at, approved_by, reject_reason, "
            + "creator, created_at, updater, updated_at "
            + "FROM ck_tenant WHERE oid = #{oid}")
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
            @Result(property = "creator",        column = "creator"),
            @Result(property = "createdAt",      column = "created_at"),
            @Result(property = "updater",        column = "updater"),
            @Result(property = "updatedAt",      column = "updated_at")
    })
    Tenant selectByOid(@Param("oid") String oid);

    @Override
    @Select("SELECT oid, tenant_id, name, status, contact_name, contact_email, "
            + "admin_username, admin_display_name, "
            + "approved_at, approved_by, reject_reason, "
            + "creator, created_at, updater, updated_at "
            + "FROM ck_tenant WHERE tenant_id = #{tenantId}")
    @ResultMap("tenantResult")
    Tenant selectByTenantId(@Param("tenantId") String tenantId);

    @Override
    @Select("SELECT COUNT(*) > 0 FROM ck_tenant WHERE tenant_id = #{tenantId}")
    boolean existsByTenantId(@Param("tenantId") String tenantId);

    @Override
    @Select("SELECT oid, tenant_id, name, status, contact_name, contact_email, "
            + "admin_username, admin_display_name, "
            + "approved_at, approved_by, reject_reason, "
            + "creator, created_at, updater, updated_at "
            + "FROM ck_tenant WHERE status = 'PENDING' ORDER BY created_at ASC")
    @ResultMap("tenantResult")
    List<Tenant> selectPending();

    @Override
    @Select("SELECT COUNT(*) FROM ck_tenant WHERE status = 'PENDING'")
    int countPending();

    @Override
    @Select("SELECT oid, tenant_id, name, status, contact_name, contact_email, "
            + "admin_username, admin_display_name, "
            + "approved_at, approved_by, reject_reason, "
            + "creator, created_at, updater, updated_at "
            + "FROM ck_tenant WHERE status = 'ACTIVE' ORDER BY created_at ASC")
    @ResultMap("tenantResult")
    List<Tenant> selectActive();
}
