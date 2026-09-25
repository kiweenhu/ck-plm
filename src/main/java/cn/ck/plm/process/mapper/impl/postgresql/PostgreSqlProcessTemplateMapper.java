/*
 * Copyright (c) 2025 深圳乘恺科技有限公司
 * All rights reserved.
 *
 * @author Kiween.Hu; Roney.Liu
 */

package cn.ck.plm.process.mapper.impl.postgresql;

import cn.ck.plm.process.entity.ProcessTemplate;
import cn.ck.plm.process.mapper.ProcessTemplateMapper;
import org.apache.ibatis.annotations.*;

import java.util.List;

/**
 * {@link ProcessTemplateMapper} 的 PostgreSQL 实现。
 *
 * <p>说明：SQL 中<b>不出现 tenant_oid</b> —— 查询条件与 INSERT 的租户列
 * 都由 {@code TenantStatementInterceptor} 注入（本表为业务表）。
 */
@Mapper
public interface PostgreSqlProcessTemplateMapper extends ProcessTemplateMapper {

    String TABLE = "ck_process_template";

    @Override
    @Insert("INSERT INTO " + TABLE + " (oid, key, name, display_name, category_oid, description, "
            + "dsl_json, latest_version, enabled, deployed_version, "
            + "deployment_id, process_definition_id, deployed_at, tenant_oid, creator, created_at, "
            + "updater, updated_at) VALUES (#{oid}, #{key}, #{name}, #{displayName}, #{categoryOid}, "
            + "#{description}, #{dslJson}, #{latestVersion}, #{enabled}, "
            + "#{deployedVersion}, #{deploymentId}, #{processDefinitionId}, #{deployedAt}, "
            + "#{tenantOid}, #{creator}, #{createdAt}, #{updater}, #{updatedAt})")
    int insert(ProcessTemplate template);

    @Override
    @Update("UPDATE " + TABLE + " SET name = #{name}, display_name = #{displayName}, "
            + "category_oid = #{categoryOid}, description = #{description}, "
            + "dsl_json = #{dslJson}, "
            + "latest_version = #{latestVersion}, enabled = #{enabled}, "
            + "deployed_version = #{deployedVersion}, deployment_id = #{deploymentId}, "
            + "process_definition_id = #{processDefinitionId}, deployed_at = #{deployedAt}, "
            + "updater = #{updater}, updated_at = #{updatedAt} WHERE oid = #{oid}")
    int update(ProcessTemplate template);

    @Override
    @Delete("DELETE FROM " + TABLE + " WHERE oid = #{oid}")
    int deleteByOid(@Param("oid") String oid);

    @Override
    @Select("SELECT * FROM " + TABLE + " WHERE oid = #{oid}")
    ProcessTemplate selectByOid(@Param("oid") String oid);

    @Override
    @Select("SELECT * FROM " + TABLE + " WHERE key = #{key}")
    ProcessTemplate selectByKey(@Param("key") String key);

    @Override
    @Select("<script>SELECT * FROM " + TABLE
            + " WHERE 1 = 1"
            + "<if test='keyword != null and keyword != \"\"'>"
            + " AND (name ILIKE '%' || #{keyword} || '%'"
            + " OR key ILIKE '%' || #{keyword} || '%'"
            + " OR description ILIKE '%' || #{keyword} || '%')</if>"
            + "<if test='categoryOid != null and categoryOid != \"\"'> AND category_oid = #{categoryOid}</if>"
            + "<if test='enabled != null'> AND enabled = #{enabled}</if>"
            + " ORDER BY updated_at DESC NULLS LAST, created_at DESC</script>")
    List<ProcessTemplate> selectList(@Param("keyword") String keyword,
                                     @Param("categoryOid") String categoryOid,
                                     @Param("enabled") Boolean enabled);

    @Override
    @Select("<script>SELECT COUNT(*) FROM " + TABLE + " WHERE key = #{key}"
            + "<if test='excludeOid != null and excludeOid != \"\"'> AND oid &lt;&gt; #{excludeOid}</if>"
            + "</script>")
    int countByKey(@Param("key") String key, @Param("excludeOid") String excludeOid);

    @Override
    @Update("UPDATE " + TABLE + " SET category_oid = #{categoryOid} WHERE oid = #{oid}")
    int updateCategory(@Param("oid") String oid, @Param("categoryOid") String categoryOid);

    @Override
    @Select("SELECT COUNT(*) FROM " + TABLE + " WHERE category_oid = #{categoryOid}")
    int countByCategory(@Param("categoryOid") String categoryOid);
}
