/*
 * Copyright (c) 2025 深圳乘恺科技有限公司
 * All rights reserved.
 *
 * @author Kiween.Hu; Roney.Liu
 */

package cn.ck.plm.base.mapper.impl.postgresql;

import cn.ck.plm.base.entity.LifecycleTemplateIteration;
import cn.ck.plm.base.mapper.LifecycleTemplateIterationMapper;
import cn.ck.plm.base.typehandler.LifecycleStatusTypeHandler;
import cn.ck.plm.base.typehandler.ViewTypeHandler;
import org.apache.ibatis.annotations.*;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;

import java.util.List;

/**
 * {@link LifecycleTemplateIterationMapper} 的 PostgreSQL 实现（MyBatis 注解模式）。
 */
@Mapper
@ConditionalOnProperty(name = "plm.database.type", havingValue = "postgresql", matchIfMissing = true)
public interface PostgreSqlLifecycleTemplateIterationMapper extends LifecycleTemplateIterationMapper {

    @Override
    @Insert("INSERT INTO ck_lifecycle_template_iteration (oid, master_oid, revision, iteration, " +
            "display_version, checked_out, checked_out_by, checked_out_comment, latest, " +
            "derived_from_oid, derived_at, view, status, " +
            "creator, created_at, updater, updated_at) " +
            "VALUES (#{oid}, #{masterOid}, #{revision}, #{iteration}, " +
            "#{displayVersion}, " +
            "#{checkedOut}, #{checkedOutBy}, #{checkedOutComment}, #{latest}, " +
            "#{derivedFromOid}, #{derivedAt}, " +
            "#{view, typeHandler=cn.ck.plm.base.typehandler.ViewTypeHandler}, " +
            "#{status, typeHandler=cn.ck.plm.base.typehandler.LifecycleStatusTypeHandler}, " +
            "#{creator}, #{createdAt}, #{updater}, #{updatedAt})")
    int insert(LifecycleTemplateIteration iteration);

    @Override
    @Update("UPDATE ck_lifecycle_template_iteration SET revision = #{revision}, iteration = #{iteration}, " +
            "display_version = #{displayVersion}, " +
            "checked_out = #{checkedOut}, checked_out_by = #{checkedOutBy}, " +
            "checked_out_comment = #{checkedOutComment}, latest = #{latest}, " +
            "derived_from_oid = #{derivedFromOid}, derived_at = #{derivedAt}, " +
            "view = #{view, typeHandler=cn.ck.plm.base.typehandler.ViewTypeHandler}, " +
            "status = #{status, typeHandler=cn.ck.plm.base.typehandler.LifecycleStatusTypeHandler}, " +
            "updater = #{updater}, updated_at = #{updatedAt} " +
            "WHERE oid = #{oid}")
    int update(LifecycleTemplateIteration iteration);

    @Override
    @Delete("DELETE FROM ck_lifecycle_template_iteration WHERE oid = #{oid}")
    int deleteByOid(@Param("oid") String oid);

    // ==================== 查询映射 ====================
    String SELECT_COLUMNS = "SELECT oid, master_oid, revision, iteration, " +
            "display_version, checked_out, checked_out_by, checked_out_comment, latest, " +
            "derived_from_oid, derived_at, view, status, " +
            "creator, created_at, updater, updated_at FROM ck_lifecycle_template_iteration ";

    @Select(SELECT_COLUMNS + "WHERE oid = #{oid}")
    @Results(id = "ltIterationResult", value = {
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
            @Result(property = "creator",           column = "creator"),
            @Result(property = "createdAt",         column = "created_at"),
            @Result(property = "updater",           column = "updater"),
            @Result(property = "updatedAt",         column = "updated_at")
    })
    @Override
    LifecycleTemplateIteration selectByOid(@Param("oid") String oid);

    @Override
    @Select(SELECT_COLUMNS + "WHERE master_oid = #{masterOid} AND latest = TRUE")
    @ResultMap("ltIterationResult")
    LifecycleTemplateIteration selectLatestByMasterOid(@Param("masterOid") String masterOid);

    @Override
    @Select(SELECT_COLUMNS + "WHERE master_oid = #{masterOid} ORDER BY revision DESC, iteration DESC")
    @ResultMap("ltIterationResult")
    List<LifecycleTemplateIteration> selectByMasterOid(@Param("masterOid") String masterOid);
}
