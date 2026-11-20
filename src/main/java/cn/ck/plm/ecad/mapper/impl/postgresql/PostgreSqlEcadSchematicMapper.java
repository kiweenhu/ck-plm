/*
 * Copyright (c) 2026~2028 深圳乘恺科技有限公司
 * All rights reserved.
 *
 * @author Kiween.Hu
 */

package cn.ck.plm.ecad.mapper.impl.postgresql;

import cn.ck.plm.ecad.entity.EcadSchematic;
import cn.ck.plm.ecad.mapper.EcadSchematicMapper;
import org.apache.ibatis.annotations.*;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;

import java.util.List;

/**
 * {@link EcadSchematicMapper} 的 PostgreSQL 实现（MyBatis 注解模式）。
 */
@Mapper
@ConditionalOnProperty(name = "plm.database.type", havingValue = "postgresql", matchIfMissing = true)
public interface PostgreSqlEcadSchematicMapper extends EcadSchematicMapper {

    @Override
    @Insert("INSERT INTO ck_ecad_schematic (oid, number, name, description, project_oid, " +
            "eda_tool, file_format, latest_iteration_oid, tenant_oid, " +
            "creator, created_at, updater, updated_at, delete_mark) " +
            "VALUES (#{oid}, #{number}, #{name}, #{description}, #{projectOid}, " +
            "#{edaTool}, #{fileFormat}, #{latestIterationOid}, #{tenantOid}, " +
            "#{creator}, #{createdAt}, #{updater}, #{updatedAt}, #{deleteMark})")
    int insert(EcadSchematic schematic);

    @Override
    @Update("UPDATE ck_ecad_schematic SET name = #{name}, description = #{description}, " +
            "project_oid = #{projectOid}, eda_tool = #{edaTool}, file_format = #{fileFormat}, " +
            "latest_iteration_oid = #{latestIterationOid}, " +
            "updater = #{updater}, updated_at = #{updatedAt} " +
            "WHERE oid = #{oid}")
    int update(EcadSchematic schematic);

    @Override
    @Update("UPDATE ck_ecad_schematic SET delete_mark = TRUE, " +
            "updated_at = CURRENT_TIMESTAMP WHERE oid = #{oid}")
    int markDeleted(@Param("oid") String oid);

    // ==================== 查询映射 ====================

    String SELECT_COLUMNS = "SELECT oid, number, name, description, project_oid, " +
            "eda_tool, file_format, latest_iteration_oid, tenant_oid, " +
            "creator, created_at, updater, updated_at, delete_mark FROM ck_ecad_schematic ";

    @Select(SELECT_COLUMNS + "WHERE oid = #{oid} AND delete_mark = FALSE")
    @Results(id = "ecadSchematicResult", value = {
            @Result(property = "oid",                 column = "oid"),
            @Result(property = "number",              column = "number"),
            @Result(property = "name",                column = "name"),
            @Result(property = "description",         column = "description"),
            @Result(property = "projectOid",          column = "project_oid"),
            @Result(property = "edaTool",             column = "eda_tool"),
            @Result(property = "fileFormat",          column = "file_format"),
            @Result(property = "latestIterationOid",  column = "latest_iteration_oid"),
            @Result(property = "tenantOid",           column = "tenant_oid"),
            @Result(property = "creator",             column = "creator"),
            @Result(property = "createdAt",           column = "created_at"),
            @Result(property = "updater",             column = "updater"),
            @Result(property = "updatedAt",           column = "updated_at"),
            @Result(property = "deleteMark",          column = "delete_mark")
    })
    @Override
    EcadSchematic selectByOid(@Param("oid") String oid);

    @Override
    @Select(SELECT_COLUMNS + "WHERE number = #{number} AND delete_mark = FALSE")
    @ResultMap("ecadSchematicResult")
    EcadSchematic selectByNumber(@Param("number") String number);

    @Override
    @Select(SELECT_COLUMNS + "WHERE project_oid = #{projectOid} AND delete_mark = FALSE " +
            "ORDER BY created_at DESC")
    @ResultMap("ecadSchematicResult")
    List<EcadSchematic> selectByProjectOid(@Param("projectOid") String projectOid);
}
