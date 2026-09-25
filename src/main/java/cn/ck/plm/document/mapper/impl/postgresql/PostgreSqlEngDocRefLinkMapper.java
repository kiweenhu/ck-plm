/*
 * Copyright (c) 2025 深圳乘恺科技有限公司
 * All rights reserved.
 *
 * @author Kiween.Hu; Roney.Liu
 */

package cn.ck.plm.document.mapper.impl.postgresql;

import cn.ck.plm.document.entity.EngDocRefLink;
import cn.ck.plm.document.mapper.EngDocRefLinkMapper;
import org.apache.ibatis.annotations.*;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;

import java.util.List;

/**
 * {@link EngDocRefLinkMapper} 的 PostgreSQL 实现（MyBatis 注解模式）。
 *
 * <p>对应表 {@code ck_eng_doc_ref_link}（对齐 Windchill {@code EPMReferenceLink}）。
 */
@Mapper
@ConditionalOnProperty(name = "plm.database.type", havingValue = "postgresql", matchIfMissing = true)
public interface PostgreSqlEngDocRefLinkMapper extends EngDocRefLinkMapper {

    @Override
    @Insert("INSERT INTO ck_eng_doc_ref_link (oid, referenced_by_iteration_oid, references_master_oid, " +
            "references_type, reference_type, as_stored_child_name, dep_type, required, unique_link_id, " +
            "tenant_oid, creator, created_at, updater, updated_at) " +
            "VALUES (#{oid}, #{referencedByIterationOid}, #{referencesMasterOid}, " +
            "#{referencesType}, #{referenceType}, #{asStoredChildName}, #{depType}, #{required}, #{uniqueLinkId}, " +
            "#{tenantOid}, #{creator}, #{createdAt}, #{updater}, #{updatedAt})")
    int insert(EngDocRefLink link);

    @Override
    @Update("UPDATE ck_eng_doc_ref_link SET " +
            "referenced_by_iteration_oid = #{referencedByIterationOid}, references_master_oid = #{referencesMasterOid}, " +
            "references_type = #{referencesType}, reference_type = #{referenceType}, " +
            "as_stored_child_name = #{asStoredChildName}, dep_type = #{depType}, required = #{required}, " +
            "unique_link_id = #{uniqueLinkId}, updater = #{updater}, updated_at = #{updatedAt} " +
            "WHERE oid = #{oid}")
    int update(EngDocRefLink link);

    @Override
    @Delete("DELETE FROM ck_eng_doc_ref_link WHERE oid = #{oid}")
    int deleteByOid(@Param("oid") String oid);

    @Override
    @Delete("DELETE FROM ck_eng_doc_ref_link WHERE referenced_by_iteration_oid = #{referencedByIterationOid}")
    int deleteByReferencedByIterationOid(@Param("referencedByIterationOid") String referencedByIterationOid);

    String SELECT_COLUMNS = "SELECT oid, referenced_by_iteration_oid, references_master_oid, " +
            "references_type, reference_type, as_stored_child_name, dep_type, required, unique_link_id, " +
            "tenant_oid, creator, created_at, updater, updated_at FROM ck_eng_doc_ref_link ";

    @Select(SELECT_COLUMNS + "WHERE oid = #{oid}")
    @Results(id = "engDocRefLinkResult", value = {
            @Result(property = "oid",                       column = "oid"),
            @Result(property = "referencedByIterationOid",  column = "referenced_by_iteration_oid"),
            @Result(property = "referencesMasterOid",       column = "references_master_oid"),
            @Result(property = "referencesType",            column = "references_type"),
            @Result(property = "referenceType",             column = "reference_type"),
            @Result(property = "asStoredChildName",         column = "as_stored_child_name"),
            @Result(property = "depType",                   column = "dep_type"),
            @Result(property = "required",                  column = "required"),
            @Result(property = "uniqueLinkId",              column = "unique_link_id"),
            @Result(property = "tenantOid",                 column = "tenant_oid"),
            @Result(property = "creator",                   column = "creator"),
            @Result(property = "createdAt",                 column = "created_at"),
            @Result(property = "updater",                   column = "updater"),
            @Result(property = "updatedAt",                 column = "updated_at")
    })
    @Override
    EngDocRefLink selectByOid(@Param("oid") String oid);

    @Override
    @Select(SELECT_COLUMNS + "WHERE referenced_by_iteration_oid = #{referencedByIterationOid} ORDER BY created_at DESC")
    @ResultMap("engDocRefLinkResult")
    List<EngDocRefLink> selectByReferencedByIterationOid(@Param("referencedByIterationOid") String referencedByIterationOid);

    @Override
    @Select(SELECT_COLUMNS + "WHERE references_master_oid = #{referencesMasterOid} ORDER BY created_at DESC")
    @ResultMap("engDocRefLinkResult")
    List<EngDocRefLink> selectByReferencesMasterOid(@Param("referencesMasterOid") String referencesMasterOid);

    @Override
    @Select(SELECT_COLUMNS + "WHERE reference_type = #{referenceType} ORDER BY created_at DESC")
    @ResultMap("engDocRefLinkResult")
    List<EngDocRefLink> selectByReferenceType(@Param("referenceType") String referenceType);
}
