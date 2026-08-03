package cn.ck.plm.cls.mapper.impl.postgresql;

import cn.ck.plm.cls.entity.ClassificationIBA;
import cn.ck.plm.cls.mapper.ClassificationIBAMapper;
import org.apache.ibatis.annotations.*;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;

import java.util.List;

@Mapper
@ConditionalOnProperty(name = "plm.database.type", havingValue = "postgresql", matchIfMissing = true)
public interface PostgreSqlClassificationIBAMapper extends ClassificationIBAMapper {

    @Override
    @Insert("INSERT INTO ck_cls_iba (oid, classification_oid, iba_oid, required, default_value, " +
            "sort_order, tenant_oid, creator, created_at, updater, updated_at) " +
            "VALUES (#{oid}, #{classificationOid}, #{ibaOid}, #{required}, #{defaultValue}, " +
            "#{sortOrder}, #{tenantOid}, #{creator}, #{createdAt}, #{updater}, #{updatedAt})")
    int insert(ClassificationIBA mapping);

    @Override
    @Update("UPDATE ck_cls_iba SET required = #{required}, default_value = #{defaultValue}, " +
            "sort_order = #{sortOrder}, updater = #{updater}, updated_at = #{updatedAt} " +
            "WHERE oid = #{oid}")
    int update(ClassificationIBA mapping);

    @Override
    @Delete("DELETE FROM ck_cls_iba WHERE oid = #{oid}")
    int deleteByOid(@Param("oid") String oid);

    // ==================== 查询映射 ====================

    @Select("SELECT ci.oid, ci.classification_oid, ci.iba_oid, ci.required, ci.default_value, " +
            "ci.sort_order, ci.tenant_oid, ci.creator, ci.created_at, ci.updater, ci.updated_at, " +
            "i.code AS iba_code, i.name AS iba_name, i.display_name AS iba_display_name, " +
            "i.data_type AS iba_data_type " +
            "FROM ck_cls_iba ci " +
            "INNER JOIN ck_iba i ON ci.iba_oid = i.oid " +
            "WHERE ci.oid = #{oid}")
    @Results(id = "ciResult", value = {
            @Result(property = "oid",               column = "oid"),
            @Result(property = "classificationOid", column = "classification_oid"),
            @Result(property = "ibaOid",            column = "iba_oid"),
            @Result(property = "required",          column = "required"),
            @Result(property = "defaultValue",      column = "default_value"),
            @Result(property = "sortOrder",         column = "sort_order"),
            @Result(property = "tenantOid",         column = "tenant_oid"),
            @Result(property = "creator",           column = "creator"),
            @Result(property = "createdAt",         column = "created_at"),
            @Result(property = "updater",           column = "updater"),
            @Result(property = "updatedAt",         column = "updated_at"),
            @Result(property = "ibaCode",           column = "iba_code"),
            @Result(property = "ibaName",           column = "iba_name"),
            @Result(property = "ibaDisplayName",    column = "iba_display_name"),
            @Result(property = "ibaDataType",       column = "iba_data_type")
    })
    @Override
    ClassificationIBA selectByOid(@Param("oid") String oid);

    @Select("SELECT ci.oid, ci.classification_oid, ci.iba_oid, ci.required, ci.default_value, " +
            "ci.sort_order, ci.tenant_oid, ci.creator, ci.created_at, ci.updater, ci.updated_at, " +
            "i.code AS iba_code, i.name AS iba_name, i.display_name AS iba_display_name, " +
            "i.data_type AS iba_data_type " +
            "FROM ck_cls_iba ci " +
            "INNER JOIN ck_iba i ON ci.iba_oid = i.oid " +
            "WHERE ci.classification_oid = #{classificationOid} " +
            "ORDER BY ci.sort_order ASC, i.sort_order ASC")
    @ResultMap("ciResult")
    @Override
    List<ClassificationIBA> selectByClassificationOid(@Param("classificationOid") String classificationOid);

    @Select("SELECT ci.oid, ci.classification_oid, ci.iba_oid, ci.required, ci.default_value, " +
            "ci.sort_order, ci.tenant_oid, ci.creator, ci.created_at, ci.updater, ci.updated_at, " +
            "i.code AS iba_code, i.name AS iba_name, i.display_name AS iba_display_name, " +
            "i.data_type AS iba_data_type " +
            "FROM ck_cls_iba ci " +
            "INNER JOIN ck_iba i ON ci.iba_oid = i.oid " +
            "WHERE ci.iba_oid = #{ibaOid} " +
            "ORDER BY ci.classification_oid ASC")
    @ResultMap("ciResult")
    @Override
    List<ClassificationIBA> selectByIbaOid(@Param("ibaOid") String ibaOid);

    @Select("SELECT COUNT(*) FROM ck_cls_iba WHERE classification_oid = #{classificationOid} AND iba_oid = #{ibaOid}")
    @Override
    int existsByClsAndIba(@Param("classificationOid") String classificationOid,
                          @Param("ibaOid") String ibaOid);

    @Override
    @Delete("DELETE FROM ck_cls_iba WHERE classification_oid = #{classificationOid}")
    int deleteByClassificationOid(@Param("classificationOid") String classificationOid);
}
