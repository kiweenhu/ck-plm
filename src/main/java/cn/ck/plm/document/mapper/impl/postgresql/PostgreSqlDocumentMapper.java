/*
 * Copyright (c) 2025 深圳乘恺科技有限公司
 * All rights reserved.
 *
 * @author Kiween.Hu; Roney.Liu
 */

package cn.ck.plm.document.mapper.impl.postgresql;

import cn.ck.plm.document.entity.Document;
import cn.ck.plm.document.mapper.DocumentMapper;
import org.apache.ibatis.annotations.*;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;

import java.util.List;

/**
 * {@link DocumentMapper} 的 PostgreSQL 实现（MyBatis 注解模式）。
 */
@Mapper
@ConditionalOnProperty(name = "plm.database.type", havingValue = "postgresql", matchIfMissing = true)
public interface PostgreSqlDocumentMapper extends DocumentMapper {

    @Override
    @Insert("INSERT INTO ck_document (oid, name, number, description, type_definition_code, " +
            "container_oid, container_type, folder_oid, stage_oid, " +
            "creator, created_at, updater, updated_at) " +
            "VALUES (#{oid}, #{name}, #{number}, #{description}, #{typeDefinitionCode}, " +
            "#{containerOid}, #{containerType}, #{folderOid}, #{stageOid}, " +
            "#{creator}, #{createdAt}, #{updater}, #{updatedAt})")
    int insert(Document document);

    @Override
    @Update("UPDATE ck_document SET name = #{name}, number = #{number}, description = #{description}, " +
            "type_definition_code = #{typeDefinitionCode}, " +
            "container_oid = #{containerOid}, container_type = #{containerType}, folder_oid = #{folderOid}, stage_oid = #{stageOid}, " +
            "updater = #{updater}, updated_at = #{updatedAt} " +
            "WHERE oid = #{oid}")
    int update(Document document);

    @Override
    @Delete("DELETE FROM ck_document WHERE oid = #{oid}")
    int deleteByOid(@Param("oid") String oid);

    // ==================== 查询映射 ====================
    String SELECT_COLUMNS = "SELECT oid, name, number, description, type_definition_code, " +
            "container_oid, container_type, folder_oid, stage_oid, " +
            "creator, created_at, updater, updated_at FROM ck_document ";

    @Select(SELECT_COLUMNS + "WHERE oid = #{oid}")
    @Results(id = "documentResult", value = {
            @Result(property = "oid",               column = "oid"),
            @Result(property = "name",              column = "name"),
            @Result(property = "number",            column = "number"),
            @Result(property = "description",       column = "description"),
            @Result(property = "typeDefinitionCode", column = "type_definition_code"),
            @Result(property = "containerOid",      column = "container_oid"),
            @Result(property = "containerType",     column = "container_type"),
            @Result(property = "folderOid",         column = "folder_oid"),
            @Result(property = "stageOid",          column = "stage_oid"),
            @Result(property = "creator",           column = "creator"),
            @Result(property = "createdAt",         column = "created_at"),
            @Result(property = "updater",           column = "updater"),
            @Result(property = "updatedAt",         column = "updated_at")
    })
    @Override
    Document selectByOid(@Param("oid") String oid);

    @Override
    @Select(SELECT_COLUMNS + "WHERE container_oid = #{containerOid} ORDER BY created_at DESC")
    @ResultMap("documentResult")
    List<Document> selectByContainerOid(@Param("containerOid") String containerOid);

    @Override
    @Select(SELECT_COLUMNS + "WHERE container_oid = #{containerOid} AND stage_oid = #{stageOid} ORDER BY created_at DESC")
    @ResultMap("documentResult")
    List<Document> selectByContainerAndStage(@Param("containerOid") String containerOid,
                                          @Param("stageOid") String stageOid);

    @Override
    @Select(SELECT_COLUMNS + "WHERE folder_oid = #{folderOid} ORDER BY created_at DESC")
    @ResultMap("documentResult")
    List<Document> selectByFolderOid(@Param("folderOid") String folderOid);

    @Override
    @Select(SELECT_COLUMNS + "ORDER BY created_at DESC")
    @ResultMap("documentResult")
    List<Document> selectAll();
}
