/*
 * Copyright (c) 2025 深圳乘恺科技有限公司
 * All rights reserved.
 *
 * @author Kiween.Hu; Roney.Liu
 */

package cn.ck.plm.document.mapper.impl.postgresql;

import cn.ck.plm.base.typehandler.LifecycleStatusTypeHandler;
import cn.ck.plm.document.entity.EngineeringDocumentIteration;
import cn.ck.plm.document.mapper.EngineeringDocumentIterationMapper;
import org.apache.ibatis.annotations.*;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;

import java.util.List;

/**
 * {@link EngineeringDocumentIterationMapper} 的 PostgreSQL 实现（MyBatis 注解模式）。
 *
 * <p>注意：{@code ck_eng_document_iteration} <b>无 view 列</b>（与 {@code ck_part_iteration} 不同），
 * 故不映射 {@code view} 字段。
 */
@Mapper
@ConditionalOnProperty(name = "plm.database.type", havingValue = "postgresql", matchIfMissing = true)
public interface PostgreSqlEngineeringDocumentIterationMapper extends EngineeringDocumentIterationMapper {

    @Override
    @Insert("INSERT INTO ck_eng_document_iteration (oid, master_oid, revision, iteration, display_version, " +
            "checked_out, checked_out_by, checked_out_comment, latest, derived_from_oid, derived_at, " +
            "status, lifecycle_template_iteration_oid, version_sort, branch_id, delete_mark, " +
            "ckfile_oid, cad_name, cad_type, cad_tool, sheet_size, scale, sheet_number, sheet_count, " +
            "projection, author, material, weight, tenant_oid, " +
            "creator, created_at, updater, updated_at) " +
            "VALUES (#{oid}, #{masterOid}, #{revision}, #{iteration}, #{displayVersion}, " +
            "#{checkedOut}, #{checkedOutBy}, #{checkedOutComment}, #{latest}, #{derivedFromOid}, #{derivedAt}, " +
            "#{status, typeHandler=cn.ck.plm.base.typehandler.LifecycleStatusTypeHandler}, " +
            "#{lifecycleTemplateIterationOid}, #{versionSort}, #{branchId}, #{deleteMark}, " +
            "#{ckfileOid}, #{cadName}, #{cadType}, #{cadTool}, #{sheetSize}, #{scale}, #{sheetNumber}, #{sheetCount}, " +
            "#{projection}, #{author}, #{material}, #{weight}, #{tenantOid}, " +
            "#{creator}, #{createdAt}, #{updater}, #{updatedAt})")
    int insert(EngineeringDocumentIteration iteration);

    @Override
    @Update("UPDATE ck_eng_document_iteration SET revision = #{revision}, iteration = #{iteration}, " +
            "display_version = #{displayVersion}, " +
            "checked_out = #{checkedOut}, checked_out_by = #{checkedOutBy}, " +
            "checked_out_comment = #{checkedOutComment}, latest = #{latest}, " +
            "derived_from_oid = #{derivedFromOid}, derived_at = #{derivedAt}, " +
            "status = #{status, typeHandler=cn.ck.plm.base.typehandler.LifecycleStatusTypeHandler}, " +
            "lifecycle_template_iteration_oid = #{lifecycleTemplateIterationOid}, " +
            "version_sort = #{versionSort}, branch_id = #{branchId}, delete_mark = #{deleteMark}, " +
            "ckfile_oid = #{ckfileOid}, cad_name = #{cadName}, cad_type = #{cadType}, cad_tool = #{cadTool}, " +
            "sheet_size = #{sheetSize}, scale = #{scale}, sheet_number = #{sheetNumber}, sheet_count = #{sheetCount}, " +
            "projection = #{projection}, author = #{author}, material = #{material}, weight = #{weight}, " +
            "updater = #{updater}, updated_at = #{updatedAt} " +
            "WHERE oid = #{oid}")
    int update(EngineeringDocumentIteration iteration);

    @Override
    @Delete("DELETE FROM ck_eng_document_iteration WHERE oid = #{oid}")
    int deleteByOid(@Param("oid") String oid);

    // ==================== 查询映射 ====================

    String SELECT_COLUMNS = "SELECT oid, master_oid, revision, iteration, display_version, " +
            "checked_out, checked_out_by, checked_out_comment, latest, derived_from_oid, derived_at, " +
            "status, lifecycle_template_iteration_oid, version_sort, branch_id, delete_mark, " +
            "ckfile_oid, cad_name, cad_type, cad_tool, sheet_size, scale, sheet_number, sheet_count, " +
            "projection, author, material, weight, tenant_oid, " +
            "creator, created_at, updater, updated_at FROM ck_eng_document_iteration ";

    @Select(SELECT_COLUMNS + "WHERE oid = #{oid}")
    @Results(id = "engDocIterationResult", value = {
            @Result(property = "oid",                            column = "oid"),
            @Result(property = "masterOid",                      column = "master_oid"),
            @Result(property = "revision",                       column = "revision"),
            @Result(property = "iteration",                      column = "iteration"),
            @Result(property = "displayVersion",                 column = "display_version"),
            @Result(property = "checkedOut",                     column = "checked_out"),
            @Result(property = "checkedOutBy",                   column = "checked_out_by"),
            @Result(property = "checkedOutComment",              column = "checked_out_comment"),
            @Result(property = "latest",                         column = "latest"),
            @Result(property = "derivedFromOid",                 column = "derived_from_oid"),
            @Result(property = "derivedAt",                      column = "derived_at"),
            @Result(property = "status",                         column = "status", typeHandler = LifecycleStatusTypeHandler.class),
            @Result(property = "lifecycleTemplateIterationOid",  column = "lifecycle_template_iteration_oid"),
            @Result(property = "versionSort",                    column = "version_sort"),
            @Result(property = "branchId",                       column = "branch_id"),
            @Result(property = "deleteMark",                     column = "delete_mark"),
            @Result(property = "ckfileOid",                      column = "ckfile_oid"),
            @Result(property = "cadName",                        column = "cad_name"),
            @Result(property = "cadType",                        column = "cad_type"),
            @Result(property = "cadTool",                        column = "cad_tool"),
            @Result(property = "sheetSize",                      column = "sheet_size"),
            @Result(property = "scale",                          column = "scale"),
            @Result(property = "sheetNumber",                    column = "sheet_number"),
            @Result(property = "sheetCount",                     column = "sheet_count"),
            @Result(property = "projection",                     column = "projection"),
            @Result(property = "author",                         column = "author"),
            @Result(property = "material",                       column = "material"),
            @Result(property = "weight",                         column = "weight"),
            @Result(property = "tenantOid",                      column = "tenant_oid"),
            @Result(property = "creator",                        column = "creator"),
            @Result(property = "createdAt",                      column = "created_at"),
            @Result(property = "updater",                        column = "updater"),
            @Result(property = "updatedAt",                      column = "updated_at")
    })
    @Override
    EngineeringDocumentIteration selectByOid(@Param("oid") String oid);

    @Override
    @Select(SELECT_COLUMNS + "WHERE master_oid = #{masterOid} AND latest = TRUE")
    @ResultMap("engDocIterationResult")
    EngineeringDocumentIteration selectLatestByMasterOid(@Param("masterOid") String masterOid);

    @Override
    @Select(SELECT_COLUMNS + "WHERE master_oid = #{masterOid} ORDER BY revision DESC, iteration DESC")
    @ResultMap("engDocIterationResult")
    List<EngineeringDocumentIteration> selectByMasterOid(@Param("masterOid") String masterOid);

    @Override
    @Select(SELECT_COLUMNS + "WHERE checked_out = TRUE AND checked_out_by = #{checkedOutBy} ORDER BY updated_at DESC")
    @ResultMap("engDocIterationResult")
    List<EngineeringDocumentIteration> selectCheckedOutByUser(@Param("checkedOutBy") String checkedOutBy);
}
