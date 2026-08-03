/*
 * Copyright (c) 2025 深圳乘恺科技有限公司
 * All rights reserved.
 */

package cn.ck.plm.functional.mapper.impl.postgresql;

import cn.ck.plm.functional.entity.FunctionalEntity;
import cn.ck.plm.functional.mapper.FunctionalMapper;
import org.apache.ibatis.annotations.*;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;

import java.util.List;

@Mapper
@ConditionalOnProperty(name = "plm.database.type", havingValue = "postgresql", matchIfMissing = true)
public interface PostgreSqlFunctionalMapper extends FunctionalMapper {

    String TABLE = "ck_functional";
    String COLS = "oid, name, number, description, type_definition_code, folder_oid, " +
                  "stage_oid, container_oid, container_type, " +
                  "tenant_oid, creator, created_at, updater, updated_at";

    @Override
    @Insert("INSERT INTO " + TABLE + " (oid, name, number, description, type_definition_code, " +
            "folder_oid, stage_oid, container_oid, container_type, " +
            "tenant_oid, creator, created_at, updater, updated_at) " +
            "VALUES (#{oid}, #{name}, #{number}, #{description}, #{typeDefinitionCode}, " +
            "#{folderOid}, #{stageOid}, #{containerOid}, #{containerType}, " +
            "#{tenantOid}, #{creator}, #{createdAt}, #{updater}, #{updatedAt})")
    int insert(FunctionalEntity entity);

    @Override
    @Update("UPDATE " + TABLE + " SET name = #{name}, number = #{number}, description = #{description}, " +
            "type_definition_code = #{typeDefinitionCode}, folder_oid = #{folderOid}, " +
            "stage_oid = #{stageOid}, " +
            "container_oid = #{containerOid}, container_type = #{containerType}, " +
            "updater = #{updater}, updated_at = CURRENT_TIMESTAMP WHERE oid = #{oid}")
    int update(FunctionalEntity entity);

    @Override
    @Delete("DELETE FROM " + TABLE + " WHERE oid = #{oid}")
    int deleteByOid(@Param("oid") String oid);

    @Override
    @Select("SELECT " + COLS + " FROM " + TABLE + " WHERE oid = #{oid}")
    @Results(id = "systemResult", value = {
            @Result(property = "typeDefinitionCode", column = "type_definition_code"),
            @Result(property = "folderOid", column = "folder_oid"),
            @Result(property = "stageOid", column = "stage_oid"),
            @Result(property = "containerOid", column = "container_oid"),
            @Result(property = "containerType", column = "container_type"),
            @Result(property = "tenantOid", column = "tenant_oid"),
            @Result(property = "createdAt", column = "created_at"),
            @Result(property = "updatedAt", column = "updated_at"),
    })
    FunctionalEntity selectByOid(@Param("oid") String oid);

    @Override
    @Select("SELECT " + COLS + " FROM " + TABLE + " WHERE container_oid = #{containerOid}")
    @ResultMap("systemResult")
    List<FunctionalEntity> selectByContainerOid(@Param("containerOid") String containerOid);

    @Override
    @Select("SELECT " + COLS + " FROM " + TABLE + " WHERE container_oid = #{containerOid} AND stage_oid = #{stageOid}")
    @ResultMap("systemResult")
    List<FunctionalEntity> selectByContainerAndStage(@Param("containerOid") String containerOid,
                                                  @Param("stageOid") String stageOid);

    @Override
    @Select("SELECT " + COLS + " FROM " + TABLE + " WHERE folder_oid = #{folderOid}")
    @ResultMap("systemResult")
    List<FunctionalEntity> selectByFolderOid(@Param("folderOid") String folderOid);

    @Override
    @Select("SELECT " + COLS + " FROM " + TABLE + " ORDER BY created_at")
    @ResultMap("systemResult")
    List<FunctionalEntity> selectAll();
}
