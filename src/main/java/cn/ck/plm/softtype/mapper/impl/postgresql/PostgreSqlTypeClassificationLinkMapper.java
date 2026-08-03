/*
 * Copyright (c) 2025 深圳乘恺科技有限公司
 * All rights reserved.
 */

package cn.ck.plm.softtype.mapper.impl.postgresql;

import cn.ck.plm.softtype.entity.TypeClassificationLink;
import cn.ck.plm.softtype.mapper.TypeClassificationLinkMapper;
import org.apache.ibatis.annotations.*;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;

import java.util.List;

@Mapper
@ConditionalOnProperty(name = "plm.database.type", havingValue = "postgresql", matchIfMissing = true)
public interface PostgreSqlTypeClassificationLinkMapper extends TypeClassificationLinkMapper {

    String TABLE = "ck_type_classification_link";
    String COLS = "oid, type_oid, classification_oid, tenant_oid, creator, created_at, updater, updated_at";

    @Override
    @Insert("INSERT INTO " + TABLE + " (oid, type_oid, classification_oid, tenant_oid, creator, created_at, updater, updated_at) "
            + "VALUES (#{oid}, #{typeOid}, #{classificationOid}, #{tenantOid}, #{creator}, #{createdAt}, #{updater}, #{updatedAt})")
    int insert(TypeClassificationLink link);

    @Override
    @Update("UPDATE " + TABLE + " SET classification_oid = #{classificationOid}, tenant_oid = #{tenantOid}, "
            + "updater = #{updater}, updated_at = CURRENT_TIMESTAMP WHERE oid = #{oid}")
    int update(TypeClassificationLink link);

    @Override
    @Delete("DELETE FROM " + TABLE + " WHERE oid = #{oid}")
    int deleteByOid(@Param("oid") String oid);

    @Override
    @Delete("DELETE FROM " + TABLE + " WHERE type_oid = #{typeOid}")
    int deleteByTypeOid(@Param("typeOid") String typeOid);

    @Override
    @Select("SELECT " + COLS + " FROM " + TABLE + " WHERE type_oid = #{typeOid}")
    TypeClassificationLink selectByTypeOid(@Param("typeOid") String typeOid);

    @Override
    @Select("SELECT " + COLS + " FROM " + TABLE + " WHERE oid = #{oid}")
    TypeClassificationLink selectByOid(@Param("oid") String oid);

    @Override
    @Select("SELECT " + COLS + " FROM " + TABLE + " ORDER BY created_at")
    List<TypeClassificationLink> selectAll();

    @Override
    @Select("SELECT " + COLS + " FROM " + TABLE + " WHERE classification_oid = #{classificationOid}")
    List<TypeClassificationLink> selectByClassificationOid(@Param("classificationOid") String classificationOid);

    @Override
    @Select("SELECT COUNT(*) FROM " + TABLE + " WHERE type_oid = #{typeOid}")
    int existsByTypeOid(@Param("typeOid") String typeOid);
}
