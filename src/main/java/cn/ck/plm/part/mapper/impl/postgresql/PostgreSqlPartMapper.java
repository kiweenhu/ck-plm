package cn.ck.plm.part.mapper.impl.postgresql;

import cn.ck.plm.part.entity.Part;
import cn.ck.plm.part.mapper.PartMapper;
import org.apache.ibatis.annotations.*;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;

import java.util.List;

@Mapper
@ConditionalOnProperty(name = "plm.database.type", havingValue = "postgresql", matchIfMissing = true)
public interface PostgreSqlPartMapper extends PartMapper {

    @Override
    @Insert("INSERT INTO ck_part (oid, name, number, description, type_definition_code, " +
            "container_oid, container_type, folder_oid, stage_oid, classification_oid, " +
            "creator, created_at, updater, updated_at) " +
            "VALUES (#{oid}, #{name}, #{number}, #{description}, #{typeDefinitionCode}, " +
            "#{containerOid}, #{containerType}, #{folderOid}, #{stageOid}, #{classificationOid}, " +
            "#{creator}, #{createdAt}, #{updater}, #{updatedAt})")
    int insert(Part part);

    @Override
    @Update("UPDATE ck_part SET name = #{name}, number = #{number}, description = #{description}, " +
            "type_definition_code = #{typeDefinitionCode}, " +
            "container_oid = #{containerOid}, container_type = #{containerType}, folder_oid = #{folderOid}, stage_oid = #{stageOid}, " +
            "classification_oid = #{classificationOid}, " +
            "updater = #{updater}, updated_at = #{updatedAt} " +
            "WHERE oid = #{oid}")
    int update(Part part);

    @Override
    @Delete("DELETE FROM ck_part WHERE oid = #{oid}")
    int deleteByOid(@Param("oid") String oid);

    String SELECT_COLUMNS = "SELECT oid, name, number, description, type_definition_code, " +
            "container_oid, container_type, folder_oid, stage_oid, classification_oid, " +
            "creator, created_at, updater, updated_at FROM ck_part ";

    @Select(SELECT_COLUMNS + "WHERE oid = #{oid}")
    @Results(id = "partResult", value = {
            @Result(property = "oid",               column = "oid"),
            @Result(property = "name",              column = "name"),
            @Result(property = "number",            column = "number"),
            @Result(property = "description",       column = "description"),
            @Result(property = "typeDefinitionCode", column = "type_definition_code"),
            @Result(property = "containerOid",      column = "container_oid"),
            @Result(property = "containerType",     column = "container_type"),
            @Result(property = "folderOid",         column = "folder_oid"),
            @Result(property = "stageOid",          column = "stage_oid"),
            @Result(property = "classificationOid", column = "classification_oid"),
            @Result(property = "creator",           column = "creator"),
            @Result(property = "createdAt",         column = "created_at"),
            @Result(property = "updater",           column = "updater"),
            @Result(property = "updatedAt",         column = "updated_at")
    })
    @Override
    Part selectByOid(@Param("oid") String oid);

    @Override
    @Select(SELECT_COLUMNS + "WHERE container_oid = #{containerOid} ORDER BY created_at DESC")
    @ResultMap("partResult")
    List<Part> selectByContainerOid(@Param("containerOid") String containerOid);

    @Override
    @Select(SELECT_COLUMNS + "WHERE container_oid = #{containerOid} AND stage_oid = #{stageOid} ORDER BY created_at DESC")
    @ResultMap("partResult")
    List<Part> selectByContainerAndStage(@Param("containerOid") String containerOid,
                                      @Param("stageOid") String stageOid);

    @Override
    @Select(SELECT_COLUMNS + "WHERE folder_oid = #{folderOid} ORDER BY created_at DESC")
    @ResultMap("partResult")
    List<Part> selectByFolderOid(@Param("folderOid") String folderOid);

    @Override
    @Select(SELECT_COLUMNS + "WHERE classification_oid = #{classificationOid} ORDER BY created_at DESC")
    @ResultMap("partResult")
    List<Part> selectByClassificationOid(@Param("classificationOid") String classificationOid);

    @Override
    @Select(SELECT_COLUMNS + "ORDER BY created_at DESC")
    @ResultMap("partResult")
    List<Part> selectAll();
}
