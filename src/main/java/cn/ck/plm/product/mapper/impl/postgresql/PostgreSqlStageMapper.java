/*
 * Copyright (c) 2025 深圳乘恺科技有限公司
 * All rights reserved.
 *
 * @author Kiween.Hu; Roney.Liu
 */

package cn.ck.plm.product.mapper.impl.postgresql;

import cn.ck.plm.product.entity.Stage;
import cn.ck.plm.product.mapper.StageMapper;
import org.apache.ibatis.annotations.*;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;

import java.util.List;

/**
 * {@link StageMapper} 的 PostgreSQL 实现（MyBatis 注解模式）。
 */
@Mapper
@ConditionalOnProperty(name = "plm.database.type", havingValue = "postgresql", matchIfMissing = true)
public interface PostgreSqlStageMapper extends StageMapper {

    @Override
    @Insert("INSERT INTO ck_stage (oid, code, name, description, icon, color, sort_order, "
            + "owner_oid, owner_type, show_on_dashboard, default_folders, creator, created_at, updater, updated_at) "
            + "VALUES (#{oid}, #{code}, #{name}, #{description}, #{icon}, #{color}, #{sortOrder}, "
            + "#{ownerOid}, #{ownerType}, #{showOnDashboard}, #{defaultFolders}, #{creator}, #{createdAt}, #{updater}, #{updatedAt})")
    int insert(Stage stage);

    @Override
    @Update("UPDATE ck_stage SET name = #{name}, description = #{description}, "
            + "icon = #{icon}, color = #{color}, sort_order = #{sortOrder}, "
            + "show_on_dashboard = #{showOnDashboard}, default_folders = #{defaultFolders}, "
            + "updater = #{updater}, updated_at = #{updatedAt} WHERE oid = #{oid}")
    int update(Stage stage);

    @Override
    @Delete("DELETE FROM ck_stage WHERE oid = #{oid}")
    int deleteByOid(@Param("oid") String oid);

    // ==================== 查询列 ====================
    String SELECT_COLUMNS = "SELECT oid, code, name, description, icon, color, sort_order, "
            + "owner_oid, owner_type, show_on_dashboard, default_folders, "
            + "creator, created_at, updater, updated_at FROM ck_stage ";

    @Override
    @Select(SELECT_COLUMNS + "WHERE oid = #{oid}")
    @Results(id = "stageResult", value = {
            @Result(property = "oid",              column = "oid"),
            @Result(property = "code",             column = "code"),
            @Result(property = "name",             column = "name"),
            @Result(property = "description",      column = "description"),
            @Result(property = "icon",             column = "icon"),
            @Result(property = "color",            column = "color"),
            @Result(property = "sortOrder",        column = "sort_order"),
            @Result(property = "ownerOid",         column = "owner_oid"),
            @Result(property = "ownerType",        column = "owner_type"),
            @Result(property = "showOnDashboard",  column = "show_on_dashboard"),
            @Result(property = "defaultFolders",   column = "default_folders"),
            @Result(property = "creator",          column = "creator"),
            @Result(property = "createdAt",        column = "created_at"),
            @Result(property = "updater",          column = "updater"),
            @Result(property = "updatedAt",        column = "updated_at")
    })
    Stage selectByOid(@Param("oid") String oid);

    @Override
    @Select(SELECT_COLUMNS + "WHERE owner_oid = #{ownerOid} AND owner_type = #{ownerType} AND code = #{code}")
    @ResultMap("stageResult")
    Stage selectByOwnerAndCode(@Param("ownerOid") String ownerOid,
                               @Param("ownerType") String ownerType,
                               @Param("code") String code);

    @Override
    @Select(SELECT_COLUMNS + "WHERE owner_oid = #{ownerOid} ORDER BY sort_order ASC, code ASC")
    @ResultMap("stageResult")
    List<Stage> selectByOwnerOid(@Param("ownerOid") String ownerOid);

    @Override
    @Delete("DELETE FROM ck_stage WHERE owner_oid = #{ownerOid}")
    int deleteByOwnerOid(@Param("ownerOid") String ownerOid);

    @Override
    @Select("SELECT COUNT(*) FROM ck_stage WHERE owner_oid = #{ownerOid}")
    int countByOwnerOid(@Param("ownerOid") String ownerOid);
}
