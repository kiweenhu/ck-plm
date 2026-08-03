/*
 * Copyright (c) 2025 深圳乘恺科技有限公司
 * All rights reserved.
 *
 * @author Kiween.Hu; Roney.Liu
 */

package cn.ck.plm.base.mapper.impl.postgresql;

import cn.ck.plm.base.entity.StageTemplate;
import cn.ck.plm.base.mapper.StageTemplateMapper;
import org.apache.ibatis.annotations.*;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;

import java.util.List;

/**
 * {@link StageTemplateMapper} 的 PostgreSQL 实现。
 */
@Mapper
@ConditionalOnProperty(name = "plm.database.type", havingValue = "postgresql", matchIfMissing = true)
public interface PostgreSqlStageTemplateMapper extends StageTemplateMapper {

    @Override
    @Insert("INSERT INTO ck_stage_template (oid, code, name, description, icon, color, sort_order, " +
            "default_folders, tenant_oid, creator, created_at, updater, updated_at) " +
            "VALUES (#{oid}, #{code}, #{name}, #{description}, #{icon}, #{color}, #{sortOrder}, " +
            "#{defaultFolders}, #{tenantOid}, #{creator}, #{createdAt}, #{updater}, #{updatedAt})")
    int insert(StageTemplate template);

    @Override
    @Update("UPDATE ck_stage_template SET name = #{name}, description = #{description}, " +
            "icon = #{icon}, color = #{color}, sort_order = #{sortOrder}, " +
            "default_folders = #{defaultFolders}, tenant_oid = #{tenantOid}, " +
            "updater = #{updater}, updated_at = #{updatedAt} WHERE oid = #{oid}")
    int update(StageTemplate template);

    @Override
    @Delete("DELETE FROM ck_stage_template WHERE oid = #{oid}")
    int deleteByOid(@Param("oid") String oid);

    @Override
    @Select("SELECT oid, code, name, description, icon, color, sort_order, " +
            "default_folders, tenant_oid, creator, created_at, updater, updated_at " +
            "FROM ck_stage_template WHERE oid = #{oid}")
    @Results(id = "stageTemplateResult", value = {
            @Result(property = "oid",            column = "oid"),
            @Result(property = "code",           column = "code"),
            @Result(property = "name",           column = "name"),
            @Result(property = "description",    column = "description"),
            @Result(property = "icon",           column = "icon"),
            @Result(property = "color",          column = "color"),
            @Result(property = "sortOrder",      column = "sort_order"),
            @Result(property = "defaultFolders", column = "default_folders"),
            @Result(property = "tenantOid",      column = "tenant_oid"),
            @Result(property = "creator",        column = "creator"),
            @Result(property = "createdAt",      column = "created_at"),
            @Result(property = "updater",        column = "updater"),
            @Result(property = "updatedAt",      column = "updated_at")
    })
    StageTemplate selectByOid(@Param("oid") String oid);

    @Override
    @Select("SELECT oid, code, name, description, icon, color, sort_order, " +
            "default_folders, tenant_oid, creator, created_at, updater, updated_at " +
            "FROM ck_stage_template WHERE code = #{code} " +
            "AND tenant_oid IN (#{tenantOid}, #{platformOid}) " +
            "ORDER BY CASE WHEN tenant_oid = #{tenantOid} THEN 0 ELSE 1 END LIMIT 1")
    @ResultMap("stageTemplateResult")
    StageTemplate selectByCode(@Param("code") String code,
                               @Param("tenantOid") String tenantOid,
                               @Param("platformOid") String platformOid);

    @Override
    @Select("SELECT oid, code, name, description, icon, color, sort_order, " +
            "default_folders, tenant_oid, creator, created_at, updater, updated_at " +
            "FROM ck_stage_template " +
            "WHERE tenant_oid = #{tenantOid} " +
            "ORDER BY sort_order ASC")
    @ResultMap("stageTemplateResult")
    List<StageTemplate> selectAll(@Param("tenantOid") String tenantOid,
                                   @Param("platformOid") String platformOid);

    @Override
    @Select("SELECT oid, code, name, description, icon, color, sort_order, " +
            "default_folders, tenant_oid, creator, created_at, updater, updated_at " +
            "FROM ck_stage_template " +
            "WHERE tenant_oid = #{tenantOid} " +
            "ORDER BY sort_order ASC")
    @ResultMap("stageTemplateResult")
    List<StageTemplate> selectByTenant(@Param("tenantOid") String tenantOid);

    @Override
    @Select("SELECT COUNT(*) FROM ck_stage_template WHERE code = #{code} " +
            "AND tenant_oid IN (#{tenantOid}, #{platformOid})")
    int existsByCode(@Param("code") String code,
                     @Param("tenantOid") String tenantOid,
                     @Param("platformOid") String platformOid);
}
