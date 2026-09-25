/*
 * Copyright (c) 2025 深圳乘恺科技有限公司
 * All rights reserved.
 *
 * @author Kiween.Hu; Roney.Liu
 */

package cn.ck.plm.process.mapper.impl.postgresql;

import cn.ck.plm.process.entity.ProcessFormTemplate;
import cn.ck.plm.process.mapper.ProcessFormTemplateMapper;
import org.apache.ibatis.annotations.*;

import java.util.List;

/**
 * {@link ProcessFormTemplateMapper} 的 PostgreSQL 实现。
 *
 * <p>SQL 里<b>不出现 tenant_oid（查询侧）</b>：本表是 PLATFORM_SHARED，
 * 查询条件 {@code tenant_oid IN (平台, 当前租户)} 由 {@code TenantStatementInterceptor} 注入。
 * INSERT 侧写 {@code #{tenantOid}}：内置模板由初始化器指定平台租户，自定义模板由服务层取当前租户。
 */
@Mapper
public interface PostgreSqlProcessFormTemplateMapper extends ProcessFormTemplateMapper {

    String TABLE = "ck_process_form_template";

    String COLUMNS = "oid, code, name, node_types, component, builtin, enabled, sort_order, "
            + "description, tenant_oid, creator, created_at, updater, updated_at";

    @Override
    @Insert("INSERT INTO " + TABLE + " (" + COLUMNS + ") VALUES (#{oid}, #{code}, #{name}, "
            + "#{nodeTypes}, #{component}, #{builtin}, #{enabled}, #{sortOrder}, #{description}, "
            + "#{tenantOid}, #{creator}, #{createdAt}, #{updater}, #{updatedAt})")
    int insert(ProcessFormTemplate template);

    @Override
    @Update("UPDATE " + TABLE + " SET name = #{name}, node_types = #{nodeTypes}, "
            + "component = #{component}, enabled = #{enabled}, sort_order = #{sortOrder}, "
            + "description = #{description}, updater = #{updater}, updated_at = #{updatedAt} "
            + "WHERE oid = #{oid}")
    int update(ProcessFormTemplate template);

    @Override
    @Delete("DELETE FROM " + TABLE + " WHERE oid = #{oid}")
    int deleteByOid(@Param("oid") String oid);

    @Override
    @Select("SELECT " + COLUMNS + " FROM " + TABLE + " WHERE oid = #{oid}")
    ProcessFormTemplate selectByOid(@Param("oid") String oid);

    @Override
    @Select("SELECT " + COLUMNS + " FROM " + TABLE + " WHERE code = #{code} "
            + "ORDER BY CASE WHEN tenant_oid = '00000000-0000-0000-0000-000000000000' THEN 1 ELSE 0 END "
            + "LIMIT 1")
    ProcessFormTemplate selectByCode(@Param("code") String code);

    @Override
    @Select("SELECT " + COLUMNS + " FROM " + TABLE + " ORDER BY sort_order, code")
    List<ProcessFormTemplate> selectList();

    @Override
    @Select("<script>SELECT COUNT(*) FROM " + TABLE + " WHERE code = #{code}"
            + "<if test='excludeOid != null and excludeOid != \"\"'> AND oid &lt;&gt; #{excludeOid}</if>"
            + "</script>")
    int countByCode(@Param("code") String code, @Param("excludeOid") String excludeOid);
}
