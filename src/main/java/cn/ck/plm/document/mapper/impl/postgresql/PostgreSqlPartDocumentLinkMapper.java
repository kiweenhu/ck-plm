/*
 * Copyright (c) 2025 深圳乘恺科技有限公司
 * All rights reserved.
 *
 * @author Kiween.Hu; Roney.Liu
 */

package cn.ck.plm.document.mapper.impl.postgresql;

import cn.ck.plm.document.entity.PartDocumentLink;
import cn.ck.plm.document.mapper.PartDocumentLinkMapper;
import org.apache.ibatis.annotations.*;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;

import java.util.List;

/**
 * PartDocumentLink PostgreSQL 持久化实现。
 */
@Mapper
@ConditionalOnProperty(name = "plm.database.type", havingValue = "postgresql", matchIfMissing = true)
public interface PostgreSqlPartDocumentLinkMapper extends PartDocumentLinkMapper {

    @Override
    @Insert("INSERT INTO ck_part_document_link (oid, code, name, description, " +
            "part_oid, document_oid, link_type, enabled, tenant_oid, creator, created_at, updater, updated_at) " +
            "VALUES (#{oid}, #{code}, #{name}, #{description}, " +
            "#{partOid}, #{documentOid}, #{linkType}, #{enabled}, #{tenantOid}, #{creator}, #{createdAt}, #{updater}, #{updatedAt})")
    int insert(PartDocumentLink link);

    @Override
    @Update("UPDATE ck_part_document_link SET " +
            "code = #{code}, name = #{name}, description = #{description}, " +
            "part_oid = #{partOid}, document_oid = #{documentOid}, link_type = #{linkType}, " +
            "enabled = #{enabled}, updater = #{updater}, updated_at = #{updatedAt} " +
            "WHERE oid = #{oid}")
    int update(PartDocumentLink link);

    @Override
    @Delete("DELETE FROM ck_part_document_link WHERE oid = #{oid}")
    int deleteByOid(@Param("oid") String oid);

    String SELECT_COLUMNS = "SELECT oid, code, name, description, " +
            "part_oid, document_oid, link_type, enabled, tenant_oid, creator, created_at, updater, updated_at FROM ck_part_document_link ";

    @Override
    @Select(SELECT_COLUMNS + "WHERE oid = #{oid}")
    @Results(id = "partDocumentLinkResult", value = {
            @Result(property = "oid",            column = "oid"),
            @Result(property = "code",           column = "code"),
            @Result(property = "name",           column = "name"),
            @Result(property = "description",    column = "description"),
            @Result(property = "partOid",        column = "part_oid"),
            @Result(property = "documentOid",    column = "document_oid"),
            @Result(property = "linkType",       column = "link_type"),
            @Result(property = "enabled",        column = "enabled"),
            @Result(property = "tenantOid",      column = "tenant_oid"),
            @Result(property = "creator",        column = "creator"),
            @Result(property = "createdAt",      column = "created_at"),
            @Result(property = "updater",        column = "updater"),
            @Result(property = "updatedAt",      column = "updated_at")
    })
    PartDocumentLink selectByOid(@Param("oid") String oid);

    @Override
    @Select("<script>" + SELECT_COLUMNS + "WHERE part_oid = #{partOid}" +
            "<if test='linkType != null and linkType != \"\"'> AND link_type = #{linkType}</if>" +
            " ORDER BY created_at DESC" +
            "</script>")
    @ResultMap("partDocumentLinkResult")
    List<PartDocumentLink> selectByPartOid(@Param("partOid") String partOid, @Param("linkType") String linkType);

    @Override
    @Select(SELECT_COLUMNS + "WHERE part_oid = #{partOid} ORDER BY created_at DESC")
    @ResultMap("partDocumentLinkResult")
    List<PartDocumentLink> selectByPartOidAll(@Param("partOid") String partOid);

    @Override
    @Select(SELECT_COLUMNS)
    @ResultMap("partDocumentLinkResult")
    List<PartDocumentLink> selectAll();
}
