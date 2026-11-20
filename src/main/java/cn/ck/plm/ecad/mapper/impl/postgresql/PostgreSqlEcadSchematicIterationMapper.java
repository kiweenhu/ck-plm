/*
 * Copyright (c) 2026~2028 深圳乘恺科技有限公司
 * All rights reserved.
 *
 * @author Kiween.Hu
 */

package cn.ck.plm.ecad.mapper.impl.postgresql;

import cn.ck.plm.base.typehandler.LifecycleStatusTypeHandler;
import cn.ck.plm.ecad.entity.EcadSchematicIteration;
import cn.ck.plm.ecad.mapper.EcadSchematicIterationMapper;
import org.apache.ibatis.annotations.*;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;

import java.util.List;

/**
 * {@link EcadSchematicIterationMapper} 的 PostgreSQL 实现（MyBatis 注解模式）。
 */
@Mapper
@ConditionalOnProperty(name = "plm.database.type", havingValue = "postgresql", matchIfMissing = true)
public interface PostgreSqlEcadSchematicIterationMapper extends EcadSchematicIterationMapper {

    @Override
    @Insert("INSERT INTO ck_ecad_schematic_iteration (oid, master_oid, revision, iteration, " +
            "display_version, latest, checked_out, checked_out_by, checked_out_comment, " +
            "derived_from_oid, derived_at, status, ckfile_oid, sheet_count, component_count, " +
            "bom_json, tenant_oid, creator, created_at, updater, updated_at, delete_mark) " +
            "VALUES (#{oid}, #{masterOid}, #{revision}, #{iteration}, " +
            "#{displayVersion}, #{latest}, #{checkedOut}, #{checkedOutBy}, #{checkedOutComment}, " +
            "#{derivedFromOid}, #{derivedAt}, " +
            "#{status, typeHandler=cn.ck.plm.base.typehandler.LifecycleStatusTypeHandler}, " +
            "#{ckfileOid}, #{sheetCount}, #{componentCount}, " +
            "#{bomJson}::jsonb, #{tenantOid}, #{creator}, #{createdAt}, #{updater}, #{updatedAt}, #{deleteMark})")
    int insert(EcadSchematicIteration iteration);

    @Override
    @Update("UPDATE ck_ecad_schematic_iteration SET revision = #{revision}, iteration = #{iteration}, " +
            "display_version = #{displayVersion}, latest = #{latest}, checked_out = #{checkedOut}, " +
            "checked_out_by = #{checkedOutBy}, checked_out_comment = #{checkedOutComment}, " +
            "derived_from_oid = #{derivedFromOid}, derived_at = #{derivedAt}, " +
            "status = #{status, typeHandler=cn.ck.plm.base.typehandler.LifecycleStatusTypeHandler}, " +
            "ckfile_oid = #{ckfileOid}, sheet_count = #{sheetCount}, component_count = #{componentCount}, " +
            "bom_json = #{bomJson}::jsonb, updater = #{updater}, updated_at = #{updatedAt} " +
            "WHERE oid = #{oid}")
    int update(EcadSchematicIteration iteration);

    @Override
    @Delete("DELETE FROM ck_ecad_schematic_iteration WHERE oid = #{oid}")
    int deleteByOid(@Param("oid") String oid);

    // ==================== 查询映射 ====================

    String SELECT_COLUMNS = "SELECT oid, master_oid, revision, iteration, display_version, " +
            "latest, checked_out, checked_out_by, checked_out_comment, derived_from_oid, derived_at, " +
            "status, ckfile_oid, sheet_count, component_count, " +
            "bom_json::text AS bom_json, tenant_oid, " +
            "creator, created_at, updater, updated_at, delete_mark FROM ck_ecad_schematic_iteration ";

    @Select(SELECT_COLUMNS + "WHERE oid = #{oid}")
    @Results(id = "ecadSchematicIterationResult", value = {
            @Result(property = "oid",               column = "oid"),
            @Result(property = "masterOid",         column = "master_oid"),
            @Result(property = "revision",          column = "revision"),
            @Result(property = "iteration",         column = "iteration"),
            @Result(property = "displayVersion",    column = "display_version"),
            @Result(property = "latest",            column = "latest"),
            @Result(property = "checkedOut",        column = "checked_out"),
            @Result(property = "checkedOutBy",      column = "checked_out_by"),
            @Result(property = "checkedOutComment", column = "checked_out_comment"),
            @Result(property = "derivedFromOid",    column = "derived_from_oid"),
            @Result(property = "derivedAt",         column = "derived_at"),
            @Result(property = "status",            column = "status", typeHandler = LifecycleStatusTypeHandler.class),
            @Result(property = "ckfileOid",         column = "ckfile_oid"),
            @Result(property = "sheetCount",        column = "sheet_count"),
            @Result(property = "componentCount",    column = "component_count"),
            @Result(property = "bomJson",           column = "bom_json"),
            @Result(property = "tenantOid",         column = "tenant_oid"),
            @Result(property = "creator",           column = "creator"),
            @Result(property = "createdAt",         column = "created_at"),
            @Result(property = "updater",           column = "updater"),
            @Result(property = "updatedAt",         column = "updated_at"),
            @Result(property = "deleteMark",        column = "delete_mark")
    })
    @Override
    EcadSchematicIteration selectByOid(@Param("oid") String oid);

    @Override
    @Select(SELECT_COLUMNS + "WHERE master_oid = #{masterOid} AND delete_mark = FALSE " +
            "ORDER BY revision DESC, iteration DESC")
    @ResultMap("ecadSchematicIterationResult")
    List<EcadSchematicIteration> selectByMasterOid(@Param("masterOid") String masterOid);

    @Override
    @Select(SELECT_COLUMNS + "WHERE master_oid = #{masterOid} AND latest = TRUE AND delete_mark = FALSE")
    @ResultMap("ecadSchematicIterationResult")
    EcadSchematicIteration selectLatestByMasterOid(@Param("masterOid") String masterOid);

    @Override
    @Select(SELECT_COLUMNS + "WHERE checked_out = TRUE AND checked_out_by = #{user} " +
            "AND delete_mark = FALSE ORDER BY updated_at DESC")
    @ResultMap("ecadSchematicIterationResult")
    List<EcadSchematicIteration> selectCheckedOutByUser(@Param("user") String user);
}
