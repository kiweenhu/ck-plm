/*
 * Copyright (c) 2026~2028 深圳乘恺科技有限公司
 * All rights reserved.
 *
 * @author Kiween.Hu; Roney.Liu
 */

package cn.ck.plm.base.mapper.impl.postgresql;

import cn.ck.plm.base.entity.View;
import cn.ck.plm.base.mapper.ViewMapper;
import org.apache.ibatis.annotations.*;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;

import java.util.List;

/**
 * {@link ViewMapper} 的 PostgreSQL 实现（MyBatis 注解模式）。
 *
 * <p>通过 {@code plm.database.type=postgresql} 配置激活此实现。
 *
 * <p>视图**按租户隔离**（2026-10-11 自 PLATFORM_SHARED 名单移出）：随 platform-base 包各装一份，
 * 运行期按 code 取视图（如 {@code PartServiceImpl.defaultView} 找 "Design"）命中本租户那份。
 * 唯一键为 {@code (code, tenant_oid)}。
 */
@Mapper
@ConditionalOnProperty(name = "plm.database.type", havingValue = "postgresql", matchIfMissing = true)
public interface PostgreSqlViewMapper extends ViewMapper {

    @Override
    @Insert("INSERT INTO ck_view (code, oid, name, description, sort_order, enabled, tenant_oid, creator, created_at, updater, updated_at) " +
            "VALUES (#{code}, #{oid}, #{name}, #{description}, #{sortOrder}, #{enabled}, #{tenantOid}, #{creator}, #{createdAt}, #{updater}, #{updatedAt})")
    int insert(View view);

    // 2026-10-11 起按租户隔离：SET 不再写 tenant_oid —— 否则 SQL 含 tenant_oid 字样会使拦截器
    // 整体跳过注入，WHERE code 变成跨租户更新（平台+各租户同 code 行全改）。去掉后由拦截器追加本租户条件。
    @Override
    @Update("UPDATE ck_view SET name = #{name}, description = #{description}, sort_order = #{sortOrder}, " +
            "enabled = #{enabled}, updater = #{updater}, updated_at = #{updatedAt} WHERE code = #{code}")
    int update(View view);

    @Override
    @Delete("DELETE FROM ck_view WHERE code = #{code}")
    int deleteByCode(@Param("code") String code);

    // ==================== 查询映射定义 ====================

    @Select("SELECT code, oid, name, description, sort_order, enabled, tenant_oid, creator, created_at, updater, updated_at " +
            "FROM ck_view WHERE code = #{code}")
    @Results(id = "viewResult", value = {
            @Result(property = "code",        column = "code"),
            @Result(property = "oid",         column = "oid"),
            @Result(property = "name",        column = "name"),
            @Result(property = "description", column = "description"),
            @Result(property = "sortOrder",   column = "sort_order"),
            @Result(property = "enabled",     column = "enabled"),
            @Result(property = "tenantOid",   column = "tenant_oid"),
            @Result(property = "creator",     column = "creator"),
            @Result(property = "createdAt",   column = "created_at"),
            @Result(property = "updater",     column = "updater"),
            @Result(property = "updatedAt",   column = "updated_at")
    })
    @Override
    View selectByCode(@Param("code") String code);

    @Select("SELECT code, oid, name, description, sort_order, enabled, tenant_oid, creator, created_at, updater, updated_at " +
            "FROM ck_view WHERE enabled = TRUE ORDER BY sort_order ASC")
    @ResultMap("viewResult")
    @Override
    List<View> selectAllEnabled();

    @Select("SELECT code, oid, name, description, sort_order, enabled, tenant_oid, creator, created_at, updater, updated_at " +
            "FROM ck_view ORDER BY sort_order ASC")
    @ResultMap("viewResult")
    @Override
    List<View> selectAll();

    // 括号必须包住 OR 组：拦截器把本租户条件拼在 WHERE 之后（如 "AND tenant_oid IN (...)"），
    // 少了括号会变成 A OR (B AND 租户) —— 隔离后 search 会漏出别家的行。
    @Select("SELECT code, oid, name, description, sort_order, enabled, tenant_oid, creator, created_at, updater, updated_at " +
            "FROM ck_view " +
            "WHERE (LOWER(code) LIKE LOWER('%' || #{keyword} || '%') " +
            "OR LOWER(name) LIKE LOWER('%' || #{keyword} || '%')) " +
            "ORDER BY sort_order ASC")
    @ResultMap("viewResult")
    @Override
    List<View> search(@Param("keyword") String keyword);

    @Select("SELECT COUNT(*) FROM ck_view WHERE code = #{code}")
    @Override
    int existsByCode(@Param("code") String code);
}
