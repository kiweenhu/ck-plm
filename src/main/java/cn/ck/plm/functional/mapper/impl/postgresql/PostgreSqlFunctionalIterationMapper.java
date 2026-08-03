/*
 * Copyright (c) 2025 深圳乘恺科技有限公司
 * All rights reserved.
 */

package cn.ck.plm.functional.mapper.impl.postgresql;

import cn.ck.plm.base.typehandler.LifecycleStatusTypeHandler;
import cn.ck.plm.base.typehandler.ViewTypeHandler;
import cn.ck.plm.functional.entity.FunctionalIteration;
import cn.ck.plm.functional.mapper.FunctionalIterationMapper;
import org.apache.ibatis.annotations.*;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;

import java.util.List;

@Mapper
@ConditionalOnProperty(name = "plm.database.type", havingValue = "postgresql", matchIfMissing = true)
public interface PostgreSqlFunctionalIterationMapper extends FunctionalIterationMapper {

    String TABLE = "ck_functional_iteration";
    String SELECT_COLS = "oid, master_oid, revision, iteration, display_version, " +
            "view, status, lifecycle_template_iteration_oid, " +
            "checked_out, checked_out_by, checked_out_comment, latest, " +
            "derived_from_oid, derived_at, version_sort, branch_id, delete_mark, " +
            "tenant_oid, creator, created_at, updater, updated_at";

    @Override
    @Insert("INSERT INTO " + TABLE + " (oid, master_oid, revision, iteration, display_version, " +
            "view, status, lifecycle_template_iteration_oid, " +
            "checked_out, checked_out_by, checked_out_comment, latest, " +
            "derived_from_oid, derived_at, version_sort, branch_id, delete_mark, " +
            "tenant_oid, creator, created_at, updater, updated_at) " +
            "VALUES (#{oid}, #{masterOid}, #{revision}, #{iteration}, #{displayVersion}, " +
            "#{view, typeHandler=cn.ck.plm.base.typehandler.ViewTypeHandler}, " +
            "#{status, typeHandler=cn.ck.plm.base.typehandler.LifecycleStatusTypeHandler}, " +
            "#{lifecycleTemplateIterationOid}, " +
            "#{checkedOut}, #{checkedOutBy}, #{checkedOutComment}, #{latest}, " +
            "#{derivedFromOid}, #{derivedAt}, #{versionSort}, #{branchId}, #{deleteMark}, " +
            "#{tenantOid}, #{creator}, #{createdAt}, #{updater}, #{updatedAt})")
    int insert(FunctionalIteration iteration);

    @Override
    @Update("UPDATE " + TABLE + " SET revision = #{revision}, iteration = #{iteration}, " +
            "display_version = #{displayVersion}, " +
            "view = #{view, typeHandler=cn.ck.plm.base.typehandler.ViewTypeHandler}, " +
            "status = #{status, typeHandler=cn.ck.plm.base.typehandler.LifecycleStatusTypeHandler}, " +
            "lifecycle_template_iteration_oid = #{lifecycleTemplateIterationOid}, " +
            "checked_out = #{checkedOut}, checked_out_by = #{checkedOutBy}, " +
            "checked_out_comment = #{checkedOutComment}, latest = #{latest}, " +
            "derived_from_oid = #{derivedFromOid}, derived_at = #{derivedAt}, " +
            "version_sort = #{versionSort}, branch_id = #{branchId}, delete_mark = #{deleteMark}, " +
            "updater = #{updater}, updated_at = CURRENT_TIMESTAMP WHERE oid = #{oid}")
    int update(FunctionalIteration iteration);

    @Override
    @Delete("DELETE FROM " + TABLE + " WHERE oid = #{oid}")
    int deleteByOid(@Param("oid") String oid);

    @Override
    @Select("SELECT " + SELECT_COLS + " FROM " + TABLE + " WHERE oid = #{oid}")
    @Results(id = "FunctionalIterationResult", value = {
            @Result(property = "masterOid", column = "master_oid"),
            @Result(property = "displayVersion", column = "display_version"),
            @Result(property = "lifecycleTemplateIterationOid", column = "lifecycle_template_iteration_oid"),
            @Result(property = "view", column = "view", typeHandler = ViewTypeHandler.class),
            @Result(property = "status", column = "status", typeHandler = LifecycleStatusTypeHandler.class),
            @Result(property = "checkedOut", column = "checked_out"),
            @Result(property = "checkedOutBy", column = "checked_out_by"),
            @Result(property = "checkedOutComment", column = "checked_out_comment"),
            @Result(property = "derivedFromOid", column = "derived_from_oid"),
            @Result(property = "derivedAt", column = "derived_at"),
            @Result(property = "versionSort", column = "version_sort"),
            @Result(property = "branchId", column = "branch_id"),
            @Result(property = "deleteMark", column = "delete_mark"),
            @Result(property = "tenantOid", column = "tenant_oid"),
            @Result(property = "createdAt", column = "created_at"),
            @Result(property = "updatedAt", column = "updated_at"),
    })
    FunctionalIteration selectByOid(@Param("oid") String oid);

    @Override
    @Select("SELECT " + SELECT_COLS + " FROM " + TABLE + " WHERE master_oid = #{masterOid} AND latest = true")
    @ResultMap("FunctionalIterationResult")
    FunctionalIteration selectLatestByMasterOid(@Param("masterOid") String masterOid);

    @Override
    @Select("SELECT " + SELECT_COLS + " FROM " + TABLE + " WHERE master_oid = #{masterOid} ORDER BY version_sort DESC, iteration DESC")
    @ResultMap("FunctionalIterationResult")
    List<FunctionalIteration> selectByMasterOid(@Param("masterOid") String masterOid);

    @Override
    @Select("SELECT " + SELECT_COLS + " FROM " + TABLE + " ORDER BY created_at")
    @ResultMap("FunctionalIterationResult")
    List<FunctionalIteration> selectAll();
}
