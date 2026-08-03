/*
 * Copyright (c) 2025 深圳乘恺科技有限公司
 * All rights reserved.
 *
 * @author Kiween.Hu; Roney.Liu
 */

package cn.ck.plm.softtype.mapper.impl.postgresql;

import cn.ck.plm.softtype.mapper.IBADataMapper;
import org.apache.ibatis.annotations.*;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;

import java.util.List;
import java.util.Map;

/**
 * {@link IBADataMapper} 的 PostgreSQL 实现。
 */
@Mapper
@ConditionalOnProperty(name = "plm.database.type", havingValue = "postgresql", matchIfMissing = true)
public interface PostgreSqlIBADataMapper extends IBADataMapper {

    @Override
    @Delete("DELETE FROM ck_type_iba_data WHERE entity_type = #{entityType} AND entity_oid = #{entityOid}")
    int deleteByEntity(@Param("entityType") String entityType,
                       @Param("entityOid") String entityOid);

    @Override
    @Insert("INSERT INTO ck_type_iba_data " +
            "(entity_type, entity_oid, attr_code, attr_value, tenant_oid, creator, created_at, updater, updated_at) " +
            "VALUES (#{entityType}, #{entityOid}, #{attrCode}, #{attrValue}::jsonb, " +
            "#{tenantOid}, #{creator}, CURRENT_TIMESTAMP, #{updater}, CURRENT_TIMESTAMP)")
    int insert(@Param("entityType") String entityType,
               @Param("entityOid") String entityOid,
               @Param("attrCode") String attrCode,
               @Param("attrValue") String attrValue,
               @Param("tenantOid") String tenantOid,
               @Param("creator") String creator,
               @Param("updater") String updater);

    @Override
    @Select("SELECT attr_code, attr_value::text AS attr_value " +
            "FROM ck_type_iba_data " +
            "WHERE entity_type = #{entityType} AND entity_oid = #{entityOid} " +
            "ORDER BY attr_code")
    List<Map<String, Object>> selectByEntity(@Param("entityType") String entityType,
                                              @Param("entityOid") String entityOid);
}
