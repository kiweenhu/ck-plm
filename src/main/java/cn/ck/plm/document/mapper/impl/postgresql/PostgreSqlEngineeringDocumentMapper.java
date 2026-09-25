/*
 * Copyright (c) 2025 深圳乘恺科技有限公司
 * All rights reserved.
 *
 * @author Kiween.Hu; Roney.Liu
 */

package cn.ck.plm.document.mapper.impl.postgresql;

import cn.ck.plm.document.entity.EngineeringDocument;
import cn.ck.plm.document.mapper.EngineeringDocumentMapper;
import org.apache.ibatis.annotations.*;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;

import java.util.List;

/**
 * {@link EngineeringDocumentMapper} 的 PostgreSQL 实现（MyBatis 注解模式）。
 *
 * <p>表 {@code ck_eng_document} 由 {@code EngineeringDocumentSchemaInitializer} 建表/迁移。
 */
@Mapper
@ConditionalOnProperty(name = "plm.database.type", havingValue = "postgresql", matchIfMissing = true)
public interface PostgreSqlEngineeringDocumentMapper extends EngineeringDocumentMapper {

    @Override
    @Insert("INSERT INTO ck_eng_document (oid, number, name, description, " +
            "container_oid, container_type, type_definition_code, folder_oid, stage_oid, cls_oid, " +
            "tenant_oid, creator, created_at, updater, updated_at) " +
            "VALUES (#{oid}, #{number}, #{name}, #{description}, " +
            "#{containerOid}, #{containerType}, #{typeDefinitionCode}, #{folderOid}, #{stageOid}, #{clsOid}, " +
            "#{tenantOid}, #{creator}, #{createdAt}, #{updater}, #{updatedAt})")
    int insert(EngineeringDocument engDocument);

    @Override
    @Update("UPDATE ck_eng_document SET number = #{number}, name = #{name}, description = #{description}, " +
            "container_oid = #{containerOid}, container_type = #{containerType}, " +
            "type_definition_code = #{typeDefinitionCode}, folder_oid = #{folderOid}, " +
            "stage_oid = #{stageOid}, cls_oid = #{clsOid}, " +
            "tenant_oid = #{tenantOid}, " +
            "updater = #{updater}, updated_at = #{updatedAt} " +
            "WHERE oid = #{oid}")
    int update(EngineeringDocument engDocument);

    @Override
    @Delete("DELETE FROM ck_eng_document WHERE oid = #{oid}")
    int deleteByOid(@Param("oid") String oid);

    // ==================== 查询映射 ====================

    String SELECT_COLUMNS = "SELECT oid, number, name, description, " +
            "container_oid, container_type, type_definition_code, folder_oid, stage_oid, cls_oid, " +
            "tenant_oid, creator, created_at, updater, updated_at FROM ck_eng_document ";

    @Select(SELECT_COLUMNS + "WHERE oid = #{oid}")
    @Results(id = "engDocumentResult", value = {
            @Result(property = "oid",                column = "oid"),
            @Result(property = "number",             column = "number"),
            @Result(property = "name",               column = "name"),
            @Result(property = "description",        column = "description"),
            @Result(property = "containerOid",       column = "container_oid"),
            @Result(property = "containerType",      column = "container_type"),
            @Result(property = "typeDefinitionCode", column = "type_definition_code"),
            @Result(property = "folderOid",          column = "folder_oid"),
            @Result(property = "stageOid",           column = "stage_oid"),
            @Result(property = "clsOid",             column = "cls_oid"),
            @Result(property = "tenantOid",          column = "tenant_oid"),
            @Result(property = "creator",            column = "creator"),
            @Result(property = "createdAt",          column = "created_at"),
            @Result(property = "updater",            column = "updater"),
            @Result(property = "updatedAt",          column = "updated_at")
    })
    @Override
    EngineeringDocument selectByOid(@Param("oid") String oid);

    @Override
    @Select(SELECT_COLUMNS + "WHERE container_oid = #{containerOid} ORDER BY created_at DESC")
    @ResultMap("engDocumentResult")
    List<EngineeringDocument> selectByContainerOid(@Param("containerOid") String containerOid);

    @Override
    @Select(SELECT_COLUMNS + "WHERE container_oid = #{containerOid} AND stage_oid = #{stageOid} ORDER BY created_at DESC")
    @ResultMap("engDocumentResult")
    List<EngineeringDocument> selectByContainerAndStage(@Param("containerOid") String containerOid,
                                                        @Param("stageOid") String stageOid);

    @Override
    @Select(SELECT_COLUMNS + "WHERE folder_oid = #{folderOid} ORDER BY created_at DESC")
    @ResultMap("engDocumentResult")
    List<EngineeringDocument> selectByFolderOid(@Param("folderOid") String folderOid);

    @Override
    @Select(SELECT_COLUMNS + "WHERE type_definition_code = #{typeDefinitionCode} ORDER BY created_at DESC")
    @ResultMap("engDocumentResult")
    List<EngineeringDocument> selectByTypeDefinitionCode(@Param("typeDefinitionCode") String typeDefinitionCode);

    @Override
    @Select("SELECT COUNT(*) FROM ck_eng_document WHERE cls_oid = #{classificationOid}")
    int countByClassificationOid(@Param("classificationOid") String classificationOid);
}
