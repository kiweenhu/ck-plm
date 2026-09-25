/*
 * Copyright (c) 2025 深圳乘恺科技有限公司
 * All rights reserved.
 *
 * @author Kiween.Hu; Roney.Liu
 */

package cn.ck.plm.softtype.mapper.impl.postgresql;

import cn.ck.plm.softtype.entity.TypeLifecycleStateProcessLink;
import cn.ck.plm.softtype.mapper.TypeLifecycleStateProcessMapper;
import org.apache.ibatis.annotations.*;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;

import java.time.LocalDateTime;
import java.util.List;

/**
 * {@link TypeLifecycleStateProcessMapper} 的 PostgreSQL 实现（MyBatis 注解模式）。
 *
 * <p>与同族表一致：{@code tenant_oid} 显式列出（平台共享表，查询侧由
 * {@code TenantStatementInterceptor} 注入租户条件）。
 */
@Mapper
@ConditionalOnProperty(name = "plm.database.type", havingValue = "postgresql", matchIfMissing = true)
public interface PostgreSqlTypeLifecycleStateProcessMapper extends TypeLifecycleStateProcessMapper {

    String TABLE = "ck_type_lifecycle_state_process_link";

    @Override
    @Insert("INSERT INTO " + TABLE + " (oid, type_oid, lifecycle_template_iteration_oid, status_code, "
            + "process_template_oid, tenant_oid, creator, created_at, updater, updated_at) "
            + "VALUES (#{oid}, #{typeOid}, #{lifecycleTemplateIterationOid}, #{statusCode}, "
            + "#{processTemplateOid}, #{tenantOid}, #{creator}, #{createdAt,jdbcType=TIMESTAMP}, "
            + "#{updater}, #{updatedAt,jdbcType=TIMESTAMP})")
    int insert(TypeLifecycleStateProcessLink link);

    @Override
    @Update("UPDATE " + TABLE + " SET process_template_oid = #{processTemplateOid}, "
            + "updater = #{updater}, updated_at = #{updatedAt,jdbcType=TIMESTAMP} WHERE oid = #{oid}")
    int updateProcessTemplateOid(@Param("oid") String oid,
                                 @Param("processTemplateOid") String processTemplateOid,
                                 @Param("updater") String updater,
                                 @Param("updatedAt") LocalDateTime updatedAt);

    @Override
    @Delete("DELETE FROM " + TABLE + " WHERE oid = #{oid}")
    int deleteByOid(@Param("oid") String oid);

    @Override
    @Select("SELECT * FROM " + TABLE
            + " WHERE type_oid = #{typeOid} AND lifecycle_template_iteration_oid = #{lifecycleTemplateIterationOid}")
    List<TypeLifecycleStateProcessLink> selectByTypeAndIteration(
            @Param("typeOid") String typeOid,
            @Param("lifecycleTemplateIterationOid") String lifecycleTemplateIterationOid);

    @Override
    @Select("SELECT * FROM " + TABLE
            + " WHERE type_oid = #{typeOid} AND lifecycle_template_iteration_oid = #{lifecycleTemplateIterationOid}"
            + " AND status_code = #{statusCode}")
    List<TypeLifecycleStateProcessLink> selectByTypeIterationStatus(
            @Param("typeOid") String typeOid,
            @Param("lifecycleTemplateIterationOid") String lifecycleTemplateIterationOid,
            @Param("statusCode") String statusCode);

    @Override
    @Select("SELECT * FROM " + TABLE + " WHERE lifecycle_template_iteration_oid = #{lifecycleTemplateIterationOid}")
    List<TypeLifecycleStateProcessLink> selectByIterationOid(
            @Param("lifecycleTemplateIterationOid") String lifecycleTemplateIterationOid);

    @Override
    @Delete("DELETE FROM " + TABLE + " WHERE type_oid = #{typeOid}")
    int deleteByTypeOid(@Param("typeOid") String typeOid);

    @Override
    @Delete("<script>DELETE FROM " + TABLE + " WHERE lifecycle_template_iteration_oid IN "
            + "<foreach item='oid' collection='iterationOids' open='(' separator=',' close=')'>#{oid}</foreach>"
            + "</script>")
    int deleteByIterations(@Param("iterationOids") List<String> iterationOids);

    @Override
    @Select("SELECT * FROM " + TABLE + " WHERE process_template_oid = #{processTemplateOid}")
    List<TypeLifecycleStateProcessLink> selectByProcessTemplateOid(
            @Param("processTemplateOid") String processTemplateOid);
}
