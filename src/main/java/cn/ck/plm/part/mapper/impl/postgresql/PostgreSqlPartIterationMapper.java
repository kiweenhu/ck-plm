package cn.ck.plm.part.mapper.impl.postgresql;

import cn.ck.plm.base.typehandler.LifecycleStatusTypeHandler;
import cn.ck.plm.base.typehandler.ViewTypeHandler;
import cn.ck.plm.part.entity.PartIteration;
import cn.ck.plm.part.mapper.PartIterationMapper;
import org.apache.ibatis.annotations.*;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;

import java.util.List;

@Mapper
@ConditionalOnProperty(name = "plm.database.type", havingValue = "postgresql", matchIfMissing = true)
public interface PostgreSqlPartIterationMapper extends PartIterationMapper {

    @Override
    @Insert("INSERT INTO ck_part_iteration (oid, master_oid, revision, iteration, " +
            "display_version, checked_out, checked_out_by, checked_out_comment, latest, " +
            "derived_from_oid, derived_at, view, status, " +
            "lifecycle_template_iteration_oid, unit, source, version_sort, branch_id, delete_mark, " +
            "creator, created_at, updater, updated_at) " +
            "VALUES (#{oid}, #{masterOid}, #{revision}, #{iteration}, " +
            "#{displayVersion}, " +
            "#{checkedOut}, #{checkedOutBy}, #{checkedOutComment}, #{latest}, " +
            "#{derivedFromOid}, #{derivedAt}, " +
            "#{view, typeHandler=cn.ck.plm.base.typehandler.ViewTypeHandler}, " +
            "#{status, typeHandler=cn.ck.plm.base.typehandler.LifecycleStatusTypeHandler}, " +
            "#{lifecycleTemplateIterationOid}, #{unit}, #{source}, #{versionSort}, #{branchId}, #{deleteMark}, " +
            "#{creator}, #{createdAt}, #{updater}, #{updatedAt})")
    int insert(PartIteration iteration);

    @Override
    @Update("UPDATE ck_part_iteration SET revision = #{revision}, iteration = #{iteration}, " +
            "display_version = #{displayVersion}, " +
            "checked_out = #{checkedOut}, checked_out_by = #{checkedOutBy}, " +
            "checked_out_comment = #{checkedOutComment}, latest = #{latest}, " +
            "derived_from_oid = #{derivedFromOid}, derived_at = #{derivedAt}, " +
            "view = #{view, typeHandler=cn.ck.plm.base.typehandler.ViewTypeHandler}, " +
            "status = #{status, typeHandler=cn.ck.plm.base.typehandler.LifecycleStatusTypeHandler}, " +
            "lifecycle_template_iteration_oid = #{lifecycleTemplateIterationOid}, " +
            "unit = #{unit}, source = #{source}, " +
            "updater = #{updater}, updated_at = #{updatedAt} " +
            "WHERE oid = #{oid}")
    int update(PartIteration iteration);

    @Override
    @Delete("DELETE FROM ck_part_iteration WHERE oid = #{oid}")
    int deleteByOid(@Param("oid") String oid);

    String SELECT_COLUMNS = "SELECT oid, master_oid, revision, iteration, " +
            "display_version, checked_out, checked_out_by, checked_out_comment, latest, " +
            "derived_from_oid, derived_at, view, status, " +
            "lifecycle_template_iteration_oid, unit, source, version_sort, branch_id, delete_mark, " +
            "creator, created_at, updater, updated_at FROM ck_part_iteration ";

    @Select(SELECT_COLUMNS + "WHERE oid = #{oid}")
    @Results(id = "partIterationResult", value = {
            @Result(property = "oid",               column = "oid"),
            @Result(property = "masterOid",         column = "master_oid"),
            @Result(property = "revision",          column = "revision"),
            @Result(property = "iteration",         column = "iteration"),
            @Result(property = "displayVersion",    column = "display_version"),
            @Result(property = "checkedOut",        column = "checked_out"),
            @Result(property = "checkedOutBy",      column = "checked_out_by"),
            @Result(property = "checkedOutComment", column = "checked_out_comment"),
            @Result(property = "latest",            column = "latest"),
            @Result(property = "derivedFromOid",    column = "derived_from_oid"),
            @Result(property = "derivedAt",         column = "derived_at"),
            @Result(property = "view",              column = "view", typeHandler = ViewTypeHandler.class),
            @Result(property = "status",            column = "status", typeHandler = LifecycleStatusTypeHandler.class),
            @Result(property = "lifecycleTemplateIterationOid", column = "lifecycle_template_iteration_oid"),
            @Result(property = "unit",              column = "unit"),
            @Result(property = "source",            column = "source"),
            @Result(property = "versionSort",       column = "version_sort"),
            @Result(property = "branchId",          column = "branch_id"),
            @Result(property = "deleteMark",        column = "delete_mark"),
            @Result(property = "creator",           column = "creator"),
            @Result(property = "createdAt",         column = "created_at"),
            @Result(property = "updater",           column = "updater"),
            @Result(property = "updatedAt",         column = "updated_at")
    })
    @Override
    PartIteration selectByOid(@Param("oid") String oid);

    @Override
    @Select(SELECT_COLUMNS + "WHERE master_oid = #{masterOid} AND latest = TRUE")
    @ResultMap("partIterationResult")
    PartIteration selectLatestByMasterOid(@Param("masterOid") String masterOid);

    @Override
    @Select(SELECT_COLUMNS + "WHERE master_oid = #{masterOid} ORDER BY revision DESC, iteration DESC")
    @ResultMap("partIterationResult")
    List<PartIteration> selectByMasterOid(@Param("masterOid") String masterOid);

    @Override
    @Select("SELECT * FROM ck_part_iteration WHERE checked_out = true AND checked_out_by = #{checkedOutBy} ORDER BY updated_at DESC")
    @Results(id = "partIterationMap", value = {
        @Result(property = "oid", column = "oid"),
        @Result(property = "masterOid", column = "master_oid"),
        @Result(property = "revision", column = "revision"),
        @Result(property = "iteration", column = "iteration"),
        @Result(property = "displayVersion", column = "display_version"),
        @Result(property = "checkedOut", column = "checked_out"),
        @Result(property = "checkedOutBy", column = "checked_out_by"),
        @Result(property = "checkedOutComment", column = "checked_out_comment"),
        @Result(property = "latest", column = "latest"),
        @Result(property = "derivedFromOid", column = "derived_from_oid"),
        @Result(property = "derivedAt", column = "derived_at"),
        @Result(property = "lifecycleTemplateIterationOid", column = "lifecycle_template_iteration_oid"),
        @Result(property = "unit", column = "unit"),
        @Result(property = "source", column = "source"),
        @Result(property = "versionSort", column = "version_sort"),
        @Result(property = "branchId", column = "branch_id"),
        @Result(property = "deleteMark", column = "delete_mark"),
        @Result(property = "view", column = "view", typeHandler = cn.ck.plm.base.typehandler.ViewTypeHandler.class),
        @Result(property = "status", column = "status", typeHandler = cn.ck.plm.base.typehandler.LifecycleStatusTypeHandler.class),
        @Result(property = "creator", column = "creator"),
        @Result(property = "createdAt", column = "created_at"),
        @Result(property = "updater", column = "updater"),
        @Result(property = "updatedAt", column = "updated_at"),
    })
    List<PartIteration> selectCheckedOutByUser(@Param("checkedOutBy") String checkedOutBy);
}
