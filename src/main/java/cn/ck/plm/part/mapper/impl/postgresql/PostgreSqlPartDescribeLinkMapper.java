package cn.ck.plm.part.mapper.impl.postgresql;

import cn.ck.plm.part.entity.PartDescribeLink;
import cn.ck.plm.part.mapper.PartDescribeLinkMapper;
import org.apache.ibatis.annotations.*;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;

import java.util.List;

@Mapper
@ConditionalOnProperty(name = "plm.database.type", havingValue = "postgresql", matchIfMissing = true)
public interface PostgreSqlPartDescribeLinkMapper extends PartDescribeLinkMapper {

    @Override
    @Insert("INSERT INTO ck_doc_part_link (oid, link_type, part_iteration_oid, doc_master_oid, " +
            "doc_iteration_oid, resolved_iteration_oid, category, tenant_oid, " +
            "creator, created_at, updater, updated_at) " +
            "VALUES (#{oid}, 'DESCRIBES', #{partIterationOid}, #{docMasterOid}, " +
            "#{docIterationOid}, #{resolvedIterationOid}, #{category}, #{tenantOid}, " +
            "#{creator}, #{createdAt}, #{updater}, #{updatedAt})")
    int insert(PartDescribeLink link);

    @Override
    @Update("UPDATE ck_doc_part_link SET " +
            "part_iteration_oid = #{partIterationOid}, doc_master_oid = #{docMasterOid}, " +
            "doc_iteration_oid = #{docIterationOid}, resolved_iteration_oid = #{resolvedIterationOid}, " +
            "category = #{category}, updater = #{updater}, updated_at = #{updatedAt} " +
            "WHERE oid = #{oid} AND link_type = 'DESCRIBES'")
    int update(PartDescribeLink link);

    @Override
    @Delete("DELETE FROM ck_doc_part_link WHERE oid = #{oid} AND link_type = 'DESCRIBES'")
    int deleteByOid(@Param("oid") String oid);

    String SELECT_COLUMNS = "SELECT oid, part_iteration_oid, doc_master_oid, doc_iteration_oid, " +
            "resolved_iteration_oid, category, tenant_oid, " +
            "creator, created_at, updater, updated_at FROM ck_doc_part_link ";

    @Select(SELECT_COLUMNS + "WHERE link_type = 'DESCRIBES' AND oid = #{oid}")
    @Results(id = "partDescribeLinkResult", value = {
            @Result(property = "oid",                 column = "oid"),
            @Result(property = "partIterationOid",    column = "part_iteration_oid"),
            @Result(property = "docMasterOid",        column = "doc_master_oid"),
            @Result(property = "docIterationOid",     column = "doc_iteration_oid"),
            @Result(property = "resolvedIterationOid", column = "resolved_iteration_oid"),
            @Result(property = "category",            column = "category"),
            @Result(property = "tenantOid",           column = "tenant_oid"),
            @Result(property = "creator",             column = "creator"),
            @Result(property = "createdAt",           column = "created_at"),
            @Result(property = "updater",             column = "updater"),
            @Result(property = "updatedAt",           column = "updated_at")
    })
    @Override
    PartDescribeLink selectByOid(@Param("oid") String oid);

    @Override
    @Select(SELECT_COLUMNS + "WHERE link_type = 'DESCRIBES' AND part_iteration_oid = #{partIterationOid} ORDER BY created_at DESC")
    @ResultMap("partDescribeLinkResult")
    List<PartDescribeLink> selectByPartIterationOid(@Param("partIterationOid") String partIterationOid);

    @Override
    @Select(SELECT_COLUMNS + "WHERE link_type = 'DESCRIBES' AND doc_master_oid = #{docMasterOid} ORDER BY created_at DESC")
    @ResultMap("partDescribeLinkResult")
    List<PartDescribeLink> selectByDocMasterOid(@Param("docMasterOid") String docMasterOid);

    @Override
    @Select(SELECT_COLUMNS + "WHERE link_type = 'DESCRIBES' AND doc_iteration_oid = #{docIterationOid} ORDER BY created_at DESC")
    @ResultMap("partDescribeLinkResult")
    List<PartDescribeLink> selectByDocIterationOid(@Param("docIterationOid") String docIterationOid);
}
