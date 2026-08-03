/*
 * Copyright (c) 2025 深圳乘恺科技有限公司
 * All rights reserved.
 *
 * @author Kiween.Hu; Roney.Liu
 */

package cn.ck.plm.softtype.mapper.impl.postgresql;

import cn.ck.plm.softtype.entity.IBA;
import cn.ck.plm.softtype.mapper.IBAMapper;
import org.apache.ibatis.annotations.*;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;

import java.util.List;

/**
 * {@link IBAMapper} 的 PostgreSQL 实现。
 */
@Mapper
@ConditionalOnProperty(name = "plm.database.type", havingValue = "postgresql", matchIfMissing = true)
public interface PostgreSqlIBAMapper extends IBAMapper {

    @Override
    @Insert("INSERT INTO ck_iba (oid, code, name, display_name, data_type, default_value, " +
            "constraints_json, required, description, sort_order, enabled, tenant_oid, " +
            " creator, created_at, updater, updated_at) " +
            "VALUES (#{oid}, #{code}, #{name}, #{displayName}, #{dataType}, #{defaultValue}, " +
            "#{constraintsJson}, #{required}, #{description}, #{sortOrder}, #{enabled}, #{tenantOid}, " +
            "#{creator}, #{createdAt}, #{updater}, #{updatedAt})")
    int insert(IBA iba);

    @Override
    @Update("UPDATE ck_iba SET name = #{name}, display_name = #{displayName}, " +
            "data_type = #{dataType}, default_value = #{defaultValue}, " +
            "constraints_json = #{constraintsJson}, required = #{required}, " +
            "description = #{description}, sort_order = #{sortOrder}, enabled = #{enabled}, " +
            "tenant_oid = #{tenantOid}, " +
            "updater = #{updater}, updated_at = #{updatedAt} WHERE oid = #{oid}")
    int update(IBA iba);

    @Override
    @Delete("DELETE FROM ck_iba WHERE oid = #{oid}")
    int deleteByOid(@Param("oid") String oid);

    // ==================== 查询映射 ====================

    @Select("SELECT oid, code, name, display_name, data_type, default_value, " +
            "constraints_json, required, description, sort_order, enabled, tenant_oid, " +
            " creator, created_at, updater, updated_at " +
            "FROM ck_iba WHERE oid = #{oid}")
    @Results(id = "ibaResult", value = {
            @Result(property = "oid",             column = "oid"),
            @Result(property = "code",            column = "code"),
            @Result(property = "name",            column = "name"),
            @Result(property = "displayName",     column = "display_name"),
            @Result(property = "dataType",        column = "data_type"),
            @Result(property = "defaultValue",    column = "default_value"),
            @Result(property = "constraintsJson", column = "constraints_json"),
            @Result(property = "required",        column = "required"),
            @Result(property = "description",     column = "description"),
            @Result(property = "sortOrder",       column = "sort_order"),
            @Result(property = "enabled",         column = "enabled"),
            @Result(property = "tenantOid",       column = "tenant_oid"),
            @Result(property = "creator",         column = "creator"),
            @Result(property = "createdAt",       column = "created_at"),
            @Result(property = "updater",         column = "updater"),
            @Result(property = "updatedAt",       column = "updated_at")
    })
    @Override
    IBA selectByOid(@Param("oid") String oid);

    @Select("SELECT oid, code, name, display_name, data_type, default_value, " +
            "constraints_json, required, description, sort_order, enabled, tenant_oid, " +
            " creator, created_at, updater, updated_at " +
            "FROM ck_iba WHERE code = #{code} AND tenant_oid = #{tenantOid}")
    @ResultMap("ibaResult")
    @Override
    IBA selectByCode(@Param("code") String code, @Param("tenantOid") String tenantOid);

    @Select("SELECT oid, code, name, display_name, data_type, default_value, " +
            "constraints_json, required, description, sort_order, enabled, tenant_oid, " +
            " creator, created_at, updater, updated_at " +
            "FROM ck_iba ORDER BY sort_order ASC, code ASC")
    @ResultMap("ibaResult")
    @Override
    List<IBA> selectAll();

    @Select("SELECT oid, code, name, display_name, data_type, default_value, " +
            "constraints_json, required, description, sort_order, enabled, tenant_oid, " +
            " creator, created_at, updater, updated_at " +
            "FROM ck_iba WHERE enabled = TRUE ORDER BY sort_order ASC, code ASC")
    @ResultMap("ibaResult")
    @Override
    List<IBA> selectEnabled();

    @Select("SELECT oid, code, name, display_name, data_type, default_value, " +
            "constraints_json, required, description, sort_order, enabled, tenant_oid, " +
            " creator, created_at, updater, updated_at " +
            "FROM ck_iba " +
            "WHERE LOWER(code) LIKE LOWER('%' || #{keyword} || '%') " +
            "   OR LOWER(name) LIKE LOWER('%' || #{keyword} || '%') " +
            "   OR LOWER(COALESCE(display_name,'')) LIKE LOWER('%' || #{keyword} || '%') " +
            "ORDER BY sort_order ASC, code ASC")
    @ResultMap("ibaResult")
    @Override
    List<IBA> search(@Param("keyword") String keyword);

    @Select("SELECT COUNT(*) FROM ck_iba WHERE code = #{code} AND tenant_oid = #{tenantOid}")
    @Override
    int existsByCode(@Param("code") String code, @Param("tenantOid") String tenantOid);

    @Select("SELECT i.oid, i.code, i.name, i.display_name, i.data_type, i.default_value, " +
            "i.constraints_json, i.required, i.description, i.sort_order, i.enabled, i.tenant_oid, " +
            "i.creator, i.created_at, i.updater, i.updated_at " +
            "FROM ck_iba i " +
            "INNER JOIN ck_type_iba ti ON i.oid = ti.iba_oid " +
            "WHERE ti.type_oid = #{typeOid} " +
            "ORDER BY ti.sort_order ASC, i.sort_order ASC")
    @ResultMap("ibaResult")
    @Override
    List<IBA> selectByTypeOid(@Param("typeOid") String typeOid);

    @Select("SELECT i.oid, i.code, i.name, i.display_name, i.data_type, i.default_value, " +
            "i.constraints_json, i.required, i.description, i.sort_order, i.enabled, i.tenant_oid, " +
            "i.creator, i.created_at, i.updater, i.updated_at " +
            "FROM ck_iba i " +
            "INNER JOIN ck_type_iba ti ON i.oid = ti.iba_oid " +
            "WHERE ti.type_oid = #{ownerOid} AND ti.entity_code = #{entityCode} " +
            "ORDER BY ti.sort_order ASC, i.sort_order ASC")
    @ResultMap("ibaResult")
    @Override
    List<IBA> selectByOwnerOid(@Param("ownerOid") String ownerOid,
                               @Param("entityCode") String entityCode);

    @Select("<script>" +
            "SELECT i.oid, i.code, i.name, i.display_name, i.data_type, i.default_value, " +
            "i.constraints_json, i.required, i.description, i.sort_order, i.enabled, i.tenant_oid, " +
            "i.creator, i.created_at, i.updater, i.updated_at " +
            "FROM ck_iba i " +
            "WHERE i.oid NOT IN (SELECT iba_oid FROM ck_type_iba WHERE type_oid = #{typeOid}) " +
            "<if test='keyword != null and keyword != \"\"'>" +
            "  AND (LOWER(i.code) LIKE LOWER('%' || #{keyword} || '%') " +
            "    OR LOWER(i.name) LIKE LOWER('%' || #{keyword} || '%') " +
            "    OR LOWER(COALESCE(i.display_name,'')) LIKE LOWER('%' || #{keyword} || '%')) " +
            "</if>" +
            "ORDER BY i.sort_order ASC, i.code ASC" +
            "</script>")
    @ResultMap("ibaResult")
    @Override
    List<IBA> selectUnassignedByTypeOid(@Param("typeOid") String typeOid,
                                        @Param("keyword") String keyword);

    @Select("<script>" +
            "SELECT i.oid, i.code, i.name, i.display_name, i.data_type, i.default_value, " +
            "i.constraints_json, i.required, i.description, i.sort_order, i.enabled, i.tenant_oid, " +
            "i.creator, i.created_at, i.updater, i.updated_at " +
            "FROM ck_iba i " +
            "WHERE i.oid NOT IN (SELECT iba_oid FROM ck_type_iba WHERE type_oid = #{ownerOid} AND entity_code = #{entityCode}) " +
            "<if test='keyword != null and keyword != \"\"'>" +
            "  AND (LOWER(i.code) LIKE LOWER('%' || #{keyword} || '%') " +
            "    OR LOWER(i.name) LIKE LOWER('%' || #{keyword} || '%') " +
            "    OR LOWER(COALESCE(i.display_name,'')) LIKE LOWER('%' || #{keyword} || '%')) " +
            "</if>" +
            "ORDER BY i.sort_order ASC, i.code ASC" +
            "</script>")
    @ResultMap("ibaResult")
    @Override
    List<IBA> selectUnassignedByOwnerOid(@Param("ownerOid") String ownerOid,
                                         @Param("entityCode") String entityCode,
                                         @Param("keyword") String keyword);
}
