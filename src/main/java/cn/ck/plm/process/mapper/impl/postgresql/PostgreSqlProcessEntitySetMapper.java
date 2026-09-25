/*
 * Copyright (c) 2025 深圳乘恺科技有限公司
 * All rights reserved.
 *
 * @author Kiween.Hu; Roney.Liu
 */

package cn.ck.plm.process.mapper.impl.postgresql;

import cn.ck.plm.process.entity.ProcessEntitySet;
import cn.ck.plm.process.mapper.ProcessEntitySetMapper;
import org.apache.ibatis.annotations.*;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;

import java.util.List;

/**
 * {@link ProcessEntitySetMapper} 的 PostgreSQL 实现（MyBatis 注解模式）。
 *
 * <p>{@code tenant_oid} 显式列出（业务表，查询侧由 {@code TenantStatementInterceptor} 注入租户条件）。
 */
@Mapper
@ConditionalOnProperty(name = "plm.database.type", havingValue = "postgresql", matchIfMissing = true)
public interface PostgreSqlProcessEntitySetMapper extends ProcessEntitySetMapper {

    String TABLE = "ck_process_entity_set";

    @Override
    @Insert("INSERT INTO " + TABLE + " (oid, business_key, process_instance_id, entity_oid, "
            + "entity_version, type_code, root_type_code, tenant_oid, creator, created_at, "
            + "updater, updated_at) "
            + "VALUES (#{oid}, #{businessKey}, #{processInstanceId}, #{entityOid}, "
            + "#{entityVersion}, #{typeCode}, #{rootTypeCode}, #{tenantOid}, #{creator}, "
            + "#{createdAt,jdbcType=TIMESTAMP}, #{updater}, #{updatedAt,jdbcType=TIMESTAMP}) "
            // 同一实例重复记录同一实体时静默跳过（唯一键若命中则不入行、不报错）
            + "ON CONFLICT DO NOTHING")
    int insertIfAbsent(ProcessEntitySet row);

    @Override
    @Select("SELECT * FROM " + TABLE + " WHERE process_instance_id = #{processInstanceId}")
    List<ProcessEntitySet> selectByProcessInstanceId(@Param("processInstanceId") String processInstanceId);

    @Override
    @Select("SELECT * FROM " + TABLE + " WHERE entity_oid = #{entityOid}")
    List<ProcessEntitySet> selectByEntityOid(@Param("entityOid") String entityOid);

    @Override
    @Select("SELECT * FROM " + TABLE + " WHERE entity_oid = #{entityOid} AND entity_version = #{entityVersion}")
    List<ProcessEntitySet> selectByEntityOidAndEntityVersion(@Param("entityOid") String entityOid,
                                                             @Param("entityVersion") String entityVersion);

    @Override
    @Select("SELECT * FROM " + TABLE + " WHERE business_key = #{businessKey}")
    List<ProcessEntitySet> selectByBusinessKey(@Param("businessKey") String businessKey);

    @Override
    @Select("<script>SELECT * FROM " + TABLE + " WHERE entity_oid IN "
            + "<foreach item='oid' collection='entityOids' open='(' separator=',' close=')'>#{oid}</foreach>"
            + "</script>")
    List<ProcessEntitySet> selectByEntityOids(@Param("entityOids") List<String> entityOids);

    @Override
    @Delete("DELETE FROM " + TABLE + " WHERE process_instance_id = #{processInstanceId}")
    int deleteByProcessInstanceId(@Param("processInstanceId") String processInstanceId);
}
