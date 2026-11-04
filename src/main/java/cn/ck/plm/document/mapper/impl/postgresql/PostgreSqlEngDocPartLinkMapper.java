/*
 * Copyright (c) 2026~2028 深圳乘恺科技有限公司
 * All rights reserved.
 *
 * @author Kiween.Hu; Roney.Liu
 */

package cn.ck.plm.document.mapper.impl.postgresql;

import cn.ck.plm.document.entity.EngDocPartLink;
import cn.ck.plm.document.mapper.EngDocPartLinkMapper;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Result;
import org.apache.ibatis.annotations.ResultMap;
import org.apache.ibatis.annotations.Results;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;

import java.util.List;

@Mapper
@ConditionalOnProperty(name = "plm.database.type", havingValue = "postgresql", matchIfMissing = true)
public interface PostgreSqlEngDocPartLinkMapper extends EngDocPartLinkMapper {

    @Override
    @Insert("INSERT INTO ck_eng_doc_part_link (oid, eng_document_oid, eng_doc_iteration_oid, part_iteration_oid, " +
            "assoc_type, build_structure, build_attribute, build_representation, assoc_source, " +
            "is_primary, sort_order, tenant_oid, creator, created_at, updater, updated_at) " +
            "VALUES (#{oid}, #{engDocumentOid}, #{engDocIterationOid}, #{partIterationOid}, " +
            "#{assocType}, #{buildStructure}, #{buildAttribute}, #{buildRepresentation}, #{assocSource}, " +
            "#{primary}, #{sortOrder}, #{tenantOid}, #{creator}, #{createdAt}, #{updater}, #{updatedAt})")
    int insert(EngDocPartLink link);

    @Override
    @Update("UPDATE ck_eng_doc_part_link SET " +
            "eng_doc_iteration_oid = #{engDocIterationOid}, assoc_type = #{assocType}, " +
            "build_structure = #{buildStructure}, build_attribute = #{buildAttribute}, " +
            "build_representation = #{buildRepresentation}, assoc_source = #{assocSource}, " +
            "is_primary = #{primary}, sort_order = #{sortOrder}, " +
            "updater = #{updater}, updated_at = #{updatedAt} " +
            "WHERE oid = #{oid}")
    int update(EngDocPartLink link);

    @Override
    @Delete("DELETE FROM ck_eng_doc_part_link WHERE oid = #{oid}")
    int deleteByOid(@Param("oid") String oid);

    String SELECT_COLUMNS = "SELECT oid, eng_document_oid, eng_doc_iteration_oid, part_iteration_oid, " +
            "assoc_type, build_structure, build_attribute, build_representation, assoc_source, " +
            "is_primary, sort_order, tenant_oid, creator, created_at, updater, updated_at " +
            "FROM ck_eng_doc_part_link ";

    @Select(SELECT_COLUMNS + "WHERE oid = #{oid}")
    @Results(id = "engDocPartLinkResult", value = {
            @Result(property = "oid",                column = "oid"),
            @Result(property = "engDocumentOid",     column = "eng_document_oid"),
            @Result(property = "engDocIterationOid", column = "eng_doc_iteration_oid"),
            @Result(property = "partIterationOid",   column = "part_iteration_oid"),
            @Result(property = "assocType",          column = "assoc_type"),
            @Result(property = "buildStructure",     column = "build_structure"),
            @Result(property = "buildAttribute",     column = "build_attribute"),
            @Result(property = "buildRepresentation", column = "build_representation"),
            @Result(property = "assocSource",        column = "assoc_source"),
            @Result(property = "primary",            column = "is_primary"),
            @Result(property = "sortOrder",          column = "sort_order"),
            @Result(property = "tenantOid",          column = "tenant_oid"),
            @Result(property = "creator",            column = "creator"),
            @Result(property = "createdAt",          column = "created_at"),
            @Result(property = "updater",            column = "updater"),
            @Result(property = "updatedAt",          column = "updated_at")
    })
    EngDocPartLink selectByOid(@Param("oid") String oid);

    @Override
    @Select(SELECT_COLUMNS + "WHERE part_iteration_oid = #{partIterationOid} ORDER BY sort_order, created_at")
    @ResultMap("engDocPartLinkResult")
    List<EngDocPartLink> selectByPartIterationOid(@Param("partIterationOid") String partIterationOid);

    @Override
    @Select(SELECT_COLUMNS + "WHERE eng_document_oid = #{engDocumentOid} ORDER BY sort_order, created_at")
    @ResultMap("engDocPartLinkResult")
    List<EngDocPartLink> selectByEngDocumentOid(@Param("engDocumentOid") String engDocumentOid);

    @Override
    @Select(SELECT_COLUMNS + "WHERE eng_doc_iteration_oid = #{engDocIterationOid} ORDER BY sort_order, created_at")
    @ResultMap("engDocPartLinkResult")
    List<EngDocPartLink> selectByEngDocIterationOid(@Param("engDocIterationOid") String engDocIterationOid);
}
