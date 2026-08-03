/*
 * Copyright (c) 2025 深圳乘恺科技有限公司
 * All rights reserved.
 */

package cn.ck.plm.softtype.mapper.impl.postgresql;

import cn.ck.plm.softtype.entity.TypeLifecycleTemplateLink;
import cn.ck.plm.softtype.mapper.TypeLifecycleTemplateLinkMapper;
import org.apache.ibatis.annotations.*;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;

import java.util.List;

@Mapper
@ConditionalOnProperty(name = "plm.database.type", havingValue = "postgresql", matchIfMissing = true)
public interface PostgreSqlTypeLifecycleTemplateLinkMapper extends TypeLifecycleTemplateLinkMapper {

    String TABLE = "ck_type_lifecycle_template_link";
    String COLS = "oid, type_oid, lifecycle_template_code, tenant_oid, creator, created_at, updater, updated_at";

    @Override
    @Insert("INSERT INTO " + TABLE + " (oid, type_oid, lifecycle_template_code, tenant_oid, creator, created_at, updater, updated_at) "
            + "VALUES (#{oid}, #{typeOid}, #{lifecycleTemplateCode}, #{tenantOid}, #{creator}, #{createdAt}, #{updater}, #{updatedAt})")
    int insert(TypeLifecycleTemplateLink link);

    @Override
    @Update("UPDATE " + TABLE + " SET lifecycle_template_code = #{lifecycleTemplateCode}, tenant_oid = #{tenantOid}, "
            + "updater = #{updater}, updated_at = CURRENT_TIMESTAMP WHERE oid = #{oid}")
    int update(TypeLifecycleTemplateLink link);

    @Override
    @Delete("DELETE FROM " + TABLE + " WHERE oid = #{oid}")
    int deleteByOid(@Param("oid") String oid);

    @Override
    @Delete("DELETE FROM " + TABLE + " WHERE type_oid = #{typeOid}")
    int deleteByTypeOid(@Param("typeOid") String typeOid);

    @Override
    @Select("SELECT " + COLS + " FROM " + TABLE + " WHERE type_oid = #{typeOid}")
    TypeLifecycleTemplateLink selectByTypeOid(@Param("typeOid") String typeOid);

    @Override
    @Select("SELECT " + COLS + " FROM " + TABLE + " WHERE oid = #{oid}")
    TypeLifecycleTemplateLink selectByOid(@Param("oid") String oid);

    @Override
    @Select("SELECT " + COLS + " FROM " + TABLE + " ORDER BY created_at")
    List<TypeLifecycleTemplateLink> selectAll();

    @Override
    @Select("SELECT " + COLS + " FROM " + TABLE + " WHERE lifecycle_template_code = #{lifecycleTemplateCode}")
    List<TypeLifecycleTemplateLink> selectByTemplateCode(@Param("lifecycleTemplateCode") String lifecycleTemplateCode);

    @Override
    @Select("SELECT COUNT(*) FROM " + TABLE + " WHERE type_oid = #{typeOid}")
    int existsByTypeOid(@Param("typeOid") String typeOid);
}
