/*
 * Copyright (c) 2025 深圳乘恺科技有限公司
 * All rights reserved.
 *
 * @author Kiween.Hu; Roney.Liu
 */

package cn.ck.plm.ecad.mapper.impl.postgresql;

import cn.ck.plm.ecad.entity.EcadProject;
import cn.ck.plm.ecad.mapper.EcadProjectMapper;
import org.apache.ibatis.annotations.*;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;

import java.util.List;

/**
 * {@link EcadProjectMapper} 的 PostgreSQL 实现（MyBatis 注解模式）。
 *
 * <p>表 {@code ck_ecad_project} 由 {@code EcadSchemaInitializer#createProjectTable} 建表。
 */
@Mapper
@ConditionalOnProperty(name = "plm.database.type", havingValue = "postgresql", matchIfMissing = true)
public interface PostgreSqlEcadProjectMapper extends EcadProjectMapper {

    @Override
    @Insert("INSERT INTO ck_ecad_project (oid, code, name, description, container_type, " +
            "parent_oid, domain_oid, related_product, project_phase, owner, tenant_oid, " +
            "creator, created_at, updater, updated_at) " +
            "VALUES (#{oid}, #{code}, #{name}, #{description}, #{containerType}, " +
            "#{parentOid}, #{domainOid}, #{relatedProduct}, #{projectPhase}, #{owner}, #{tenantOid}, " +
            "#{creator}, #{createdAt}, #{updater}, #{updatedAt})")
    int insert(EcadProject project);

    @Override
    @Update("UPDATE ck_ecad_project SET code = #{code}, name = #{name}, description = #{description}, " +
            "container_type = #{containerType}, parent_oid = #{parentOid}, domain_oid = #{domainOid}, " +
            "related_product = #{relatedProduct}, project_phase = #{projectPhase}, owner = #{owner}, " +
            "updater = #{updater}, updated_at = #{updatedAt} " +
            "WHERE oid = #{oid}")
    int update(EcadProject project);

    @Override
    @Delete("DELETE FROM ck_ecad_project WHERE oid = #{oid}")
    int deleteByOid(@Param("oid") String oid);

    // ==================== 查询映射 ====================

    String SELECT_COLUMNS = "SELECT oid, code, name, description, container_type, " +
            "parent_oid, domain_oid, related_product, project_phase, owner, tenant_oid, " +
            "creator, created_at, updater, updated_at FROM ck_ecad_project ";

    @Select(SELECT_COLUMNS + "WHERE oid = #{oid}")
    @Results(id = "ecadProjectResult", value = {
            @Result(property = "oid",            column = "oid"),
            @Result(property = "code",           column = "code"),
            @Result(property = "name",           column = "name"),
            @Result(property = "description",    column = "description"),
            @Result(property = "containerType",  column = "container_type"),
            @Result(property = "parentOid",      column = "parent_oid"),
            @Result(property = "domainOid",      column = "domain_oid"),
            @Result(property = "relatedProduct", column = "related_product"),
            @Result(property = "projectPhase",   column = "project_phase"),
            @Result(property = "owner",          column = "owner"),
            @Result(property = "tenantOid",      column = "tenant_oid"),
            @Result(property = "creator",        column = "creator"),
            @Result(property = "createdAt",      column = "created_at"),
            @Result(property = "updater",        column = "updater"),
            @Result(property = "updatedAt",      column = "updated_at")
    })
    @Override
    EcadProject selectByOid(@Param("oid") String oid);

    @Override
    @Select(SELECT_COLUMNS + "WHERE code = #{code}")
    @ResultMap("ecadProjectResult")
    EcadProject selectByCode(@Param("code") String code);

    @Override
    @Select(SELECT_COLUMNS + "WHERE domain_oid = #{domainOid} ORDER BY created_at DESC")
    @ResultMap("ecadProjectResult")
    List<EcadProject> selectByDomainOid(@Param("domainOid") String domainOid);

    @Override
    @Select(SELECT_COLUMNS + "WHERE parent_oid = #{parentOid} ORDER BY created_at DESC")
    @ResultMap("ecadProjectResult")
    List<EcadProject> selectByParentOid(@Param("parentOid") String parentOid);

    @Override
    @Select(SELECT_COLUMNS + "WHERE related_product = #{relatedProduct} ORDER BY created_at DESC")
    @ResultMap("ecadProjectResult")
    List<EcadProject> selectByRelatedProduct(@Param("relatedProduct") String relatedProduct);
}
