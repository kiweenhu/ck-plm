/*
 * Copyright (c) 2026~2028 深圳乘恺科技有限公司
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
 * <p>SQL 里<b>不出现 tenant_oid（查询侧）</b>：本表按租户隔离，
 * 查询条件 {@code tenant_oid = 当前租户} 由 {@code TenantStatementInterceptor} 注入；
 * 唯一例外是 {@link #selectBuiltins} —— 装载入器补齐内置副本时要显式读**平台租户**那份母版。
 * INSERT 侧写 {@code #{tenantOid}}：内置副本由装载入器按目标租户写入，自定义模板由服务层取当前租户。
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

    /** 按 code 查（只看本租户 —— 租户条件由拦截器注入）；原先那条"平台 oid 优先"的 CASE 已不需要 */
    @Override
    @Select("SELECT " + COLUMNS + " FROM " + TABLE + " WHERE code = #{code} LIMIT 1")
    ProcessFormTemplate selectByCode(@Param("code") String code);

    /** 本租户全部模板（内置副本 + 自定义） */
    @Override
    @Select("SELECT " + COLUMNS + " FROM " + TABLE + " ORDER BY sort_order, code")
    List<ProcessFormTemplate> selectList();

    /** code 是否已被占用（只看本租户） */
    @Override
    @Select("<script>SELECT COUNT(*) FROM " + TABLE + " WHERE code = #{code}"
            + "<if test='excludeOid != null and excludeOid != \"\"'> AND oid &lt;&gt; #{excludeOid}</if>"
            + "</script>")
    int countByCode(@Param("code") String code, @Param("excludeOid") String excludeOid);
}
