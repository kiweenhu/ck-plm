/*
 * Copyright (c) 2025 深圳乘恺科技有限公司
 * All rights reserved.
 *
 * @author Kiween.Hu; Roney.Liu
 */

package cn.ck.plm.process.mapper.impl.postgresql;

import cn.ck.plm.process.entity.ProcessCategory;
import cn.ck.plm.process.mapper.ProcessCategoryMapper;
import org.apache.ibatis.annotations.*;

import java.util.List;

/**
 * {@link ProcessCategoryMapper} 的 PostgreSQL 实现。
 *
 * <p>与流程模板同源：SQL 中<b>不出现 tenant_oid</b> —— 查询条件与 INSERT 的租户列
 * 都由 {@code TenantStatementInterceptor} 注入（本表为业务表）。
 */
@Mapper
public interface PostgreSqlProcessCategoryMapper extends ProcessCategoryMapper {

    String TABLE = "ck_process_category";

    @Override
    @Insert("INSERT INTO " + TABLE + " (oid, name, sort_order, description, tenant_oid, "
            + "creator, created_at, updater, updated_at) VALUES (#{oid}, #{name}, #{sortOrder}, "
            + "#{description}, #{tenantOid}, #{creator}, #{createdAt}, #{updater}, #{updatedAt})")
    int insert(ProcessCategory category);

    @Override
    @Update("UPDATE " + TABLE + " SET name = #{name}, sort_order = #{sortOrder}, "
            + "description = #{description}, updater = #{updater}, updated_at = #{updatedAt} "
            + "WHERE oid = #{oid}")
    int update(ProcessCategory category);

    @Override
    @Delete("DELETE FROM " + TABLE + " WHERE oid = #{oid}")
    int deleteByOid(@Param("oid") String oid);

    @Override
    @Select("SELECT * FROM " + TABLE + " WHERE oid = #{oid}")
    ProcessCategory selectByOid(@Param("oid") String oid);

    @Override
    @Select("SELECT * FROM " + TABLE + " ORDER BY sort_order, name")
    List<ProcessCategory> selectList();

    @Override
    @Select("<script>SELECT COUNT(*) FROM " + TABLE + " WHERE name = #{name}"
            + "<if test='excludeOid != null and excludeOid != \"\"'> AND oid &lt;&gt; #{excludeOid}</if>"
            + "</script>")
    int countByName(@Param("name") String name, @Param("excludeOid") String excludeOid);
}
