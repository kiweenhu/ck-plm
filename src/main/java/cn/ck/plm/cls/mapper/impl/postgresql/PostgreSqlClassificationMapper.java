package cn.ck.plm.cls.mapper.impl.postgresql;

import cn.ck.plm.cls.entity.Classification;
import cn.ck.plm.cls.mapper.ClassificationMapper;
import org.apache.ibatis.annotations.*;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;

import java.util.List;

@Mapper
@ConditionalOnProperty(name = "plm.database.type", havingValue = "postgresql", matchIfMissing = true)
public interface PostgreSqlClassificationMapper extends ClassificationMapper {

    String SELECT_COLUMNS = "SELECT oid, code, name, display_name, description, identifier, " +
            "thumbnail, TRIM(parent_oid) AS parent_oid, tenant_oid, sort_order, " +
            "creator, created_at, updater, updated_at FROM ck_classification ";

    @Override
    @Insert("INSERT INTO ck_classification (oid, code, name, display_name, description, identifier, " +
            "thumbnail, parent_oid, tenant_oid, sort_order, " +
            "creator, created_at, updater, updated_at) " +
            "VALUES (#{oid}, #{code}, #{name}, #{displayName}, #{description}, #{identifier}, " +
            "#{thumbnail}, #{parentOid}, #{tenantOid}, #{sortOrder}, " +
            "#{creator}, #{createdAt}, #{updater}, #{updatedAt})")
    int insert(Classification c);

    @Override
    @Update("UPDATE ck_classification SET code = #{code}, name = #{name}, display_name = #{displayName}, " +
            "description = #{description}, identifier = #{identifier}, thumbnail = #{thumbnail}, " +
            "parent_oid = #{parentOid}, sort_order = #{sortOrder}, " +
            "updater = #{updater}, updated_at = #{updatedAt} WHERE oid = #{oid}")
    int update(Classification c);

    @Override
    @Delete("DELETE FROM ck_classification WHERE oid = #{oid}")
    int deleteByOid(@Param("oid") String oid);

    @Override
    @Select(SELECT_COLUMNS + "WHERE oid = #{oid}")
    @Results(id = "classificationResult", value = {
            @Result(property = "oid",         column = "oid"),
            @Result(property = "code",        column = "code"),
            @Result(property = "name",        column = "name"),
            @Result(property = "displayName", column = "display_name"),
            @Result(property = "description", column = "description"),
            @Result(property = "identifier",  column = "identifier"),
            @Result(property = "thumbnail",   column = "thumbnail"),
            @Result(property = "parentOid",   column = "parent_oid"),
            @Result(property = "tenantOid",   column = "tenant_oid"),
            @Result(property = "sortOrder",   column = "sort_order"),
            @Result(property = "creator",     column = "creator"),
            @Result(property = "createdAt",   column = "created_at"),
            @Result(property = "updater",     column = "updater"),
            @Result(property = "updatedAt",   column = "updated_at")
    })
    Classification selectByOid(@Param("oid") String oid);

    @Override
    @Select(SELECT_COLUMNS + "WHERE identifier = #{identifier} AND tenant_oid = #{tenantOid}")
    @ResultMap("classificationResult")
    Classification selectByIdentifier(@Param("identifier") String identifier,
                                       @Param("tenantOid") String tenantOid);

    @Override
    @Select(SELECT_COLUMNS + "WHERE tenant_oid = #{tenantOid} ORDER BY sort_order, code")
    @ResultMap("classificationResult")
    List<Classification> selectAll(@Param("tenantOid") String tenantOid);

    @Override
    @Select("SELECT oid, code, name, display_name, description, identifier, " +
            "thumbnail, TRIM(parent_oid) AS parent_oid, tenant_oid, sort_order, " +
            "creator, created_at, updater, updated_at " +
            "FROM ck_classification " +
            "WHERE (LOWER(name) LIKE LOWER('%' || #{keyword} || '%') " +
            "   OR LOWER(display_name) LIKE LOWER('%' || #{keyword} || '%') " +
            "   OR LOWER(identifier) LIKE LOWER('%' || #{keyword} || '%')) " +
            "  AND tenant_oid = #{tenantOid} " +
            "ORDER BY sort_order, code")
    @ResultMap("classificationResult")
    List<Classification> search(@Param("keyword") String keyword,
                                 @Param("tenantOid") String tenantOid);

    @Override
    @Select(SELECT_COLUMNS +
            "WHERE (parent_oid IS NULL OR TRIM(parent_oid) = '') AND tenant_oid = #{tenantOid} " +
            "ORDER BY sort_order, code")
    @ResultMap("classificationResult")
    List<Classification> selectRoots(@Param("tenantOid") String tenantOid);

    @Override
    @Select(SELECT_COLUMNS +
            "WHERE parent_oid = #{parentOid} AND tenant_oid = #{tenantOid} " +
            "ORDER BY sort_order, code")
    @ResultMap("classificationResult")
    List<Classification> selectByParentOid(@Param("parentOid") String parentOid,
                                            @Param("tenantOid") String tenantOid);

    @Override
    @Select("SELECT COUNT(*) FROM ck_classification " +
            "WHERE identifier = #{identifier} AND tenant_oid = #{tenantOid}")
    int existsByIdentifier(@Param("identifier") String identifier,
                           @Param("tenantOid") String tenantOid);

    @Override
    @Select("SELECT COUNT(*) FROM ck_classification WHERE parent_oid = #{oid}")
    int countChildren(@Param("oid") String oid);
}
