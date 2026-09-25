/*
 * Copyright (c) 2025 深圳乘恺科技有限公司
 * All rights reserved.
 *
 * @author Kiween.Hu; Roney.Liu
 */

package cn.ck.plm.document.mapper.impl.postgresql;

import cn.ck.plm.document.entity.EngDocMemberLink;
import cn.ck.plm.document.mapper.EngDocMemberLinkMapper;
import org.apache.ibatis.annotations.*;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;

import java.util.List;

/**
 * {@link EngDocMemberLinkMapper} 的 PostgreSQL 实现（MyBatis 注解模式）。
 *
 * <p>对应表 {@code ck_eng_doc_member_link}（对齐 Windchill {@code EPMMemberLink}）。
 */
@Mapper
@ConditionalOnProperty(name = "plm.database.type", havingValue = "postgresql", matchIfMissing = true)
public interface PostgreSqlEngDocMemberLinkMapper extends EngDocMemberLinkMapper {

    @Override
    @Insert("INSERT INTO ck_eng_doc_member_link (oid, used_by_iteration_oid, uses_master_oid, quantity, " +
            "placed, has_transform, transform, fixed, substitute, suppressed, annotated, " +
            "model_item_owner_id, model_item_owner_type, comp_number, comp_rev_number, comp_layer_index, " +
            "name, identifier, identifier_space_name, as_stored_child_name, dep_type, required, unique_link_id, " +
            "sort_order, tenant_oid, creator, created_at, updater, updated_at) " +
            "VALUES (#{oid}, #{usedByIterationOid}, #{usesMasterOid}, #{quantity}, " +
            "#{placed}, #{hasTransform}, #{transform}, #{fixed}, #{substitute}, #{suppressed}, #{annotated}, " +
            "#{modelItemOwnerId}, #{modelItemOwnerType}, #{compNumber}, #{compRevNumber}, #{compLayerIndex}, " +
            "#{name}, #{identifier}, #{identifierSpaceName}, #{asStoredChildName}, #{depType}, #{required}, #{uniqueLinkId}, " +
            "#{sortOrder}, #{tenantOid}, #{creator}, #{createdAt}, #{updater}, #{updatedAt})")
    int insert(EngDocMemberLink link);

    @Override
    @Update("UPDATE ck_eng_doc_member_link SET " +
            "used_by_iteration_oid = #{usedByIterationOid}, uses_master_oid = #{usesMasterOid}, " +
            "quantity = #{quantity}, placed = #{placed}, has_transform = #{hasTransform}, transform = #{transform}, " +
            "fixed = #{fixed}, substitute = #{substitute}, suppressed = #{suppressed}, annotated = #{annotated}, " +
            "model_item_owner_id = #{modelItemOwnerId}, model_item_owner_type = #{modelItemOwnerType}, " +
            "comp_number = #{compNumber}, comp_rev_number = #{compRevNumber}, comp_layer_index = #{compLayerIndex}, " +
            "name = #{name}, identifier = #{identifier}, identifier_space_name = #{identifierSpaceName}, " +
            "as_stored_child_name = #{asStoredChildName}, dep_type = #{depType}, required = #{required}, " +
            "unique_link_id = #{uniqueLinkId}, sort_order = #{sortOrder}, " +
            "updater = #{updater}, updated_at = #{updatedAt} " +
            "WHERE oid = #{oid}")
    int update(EngDocMemberLink link);

    @Override
    @Delete("DELETE FROM ck_eng_doc_member_link WHERE oid = #{oid}")
    int deleteByOid(@Param("oid") String oid);

    @Override
    @Delete("DELETE FROM ck_eng_doc_member_link WHERE used_by_iteration_oid = #{usedByIterationOid}")
    int deleteByUsedByIterationOid(@Param("usedByIterationOid") String usedByIterationOid);

    String SELECT_COLUMNS = "SELECT oid, used_by_iteration_oid, uses_master_oid, quantity, " +
            "placed, has_transform, transform, fixed, substitute, suppressed, annotated, " +
            "model_item_owner_id, model_item_owner_type, comp_number, comp_rev_number, comp_layer_index, " +
            "name, identifier, identifier_space_name, as_stored_child_name, dep_type, required, unique_link_id, " +
            "sort_order, tenant_oid, creator, created_at, updater, updated_at FROM ck_eng_doc_member_link ";

    @Select(SELECT_COLUMNS + "WHERE oid = #{oid}")
    @Results(id = "engDocMemberLinkResult", value = {
            @Result(property = "oid",                 column = "oid"),
            @Result(property = "usedByIterationOid",  column = "used_by_iteration_oid"),
            @Result(property = "usesMasterOid",       column = "uses_master_oid"),
            @Result(property = "quantity",            column = "quantity"),
            @Result(property = "placed",              column = "placed"),
            @Result(property = "hasTransform",        column = "has_transform"),
            @Result(property = "transform",           column = "transform"),
            @Result(property = "fixed",               column = "fixed"),
            @Result(property = "substitute",          column = "substitute"),
            @Result(property = "suppressed",          column = "suppressed"),
            @Result(property = "annotated",           column = "annotated"),
            @Result(property = "modelItemOwnerId",    column = "model_item_owner_id"),
            @Result(property = "modelItemOwnerType",  column = "model_item_owner_type"),
            @Result(property = "compNumber",          column = "comp_number"),
            @Result(property = "compRevNumber",       column = "comp_rev_number"),
            @Result(property = "compLayerIndex",      column = "comp_layer_index"),
            @Result(property = "name",                column = "name"),
            @Result(property = "identifier",          column = "identifier"),
            @Result(property = "identifierSpaceName", column = "identifier_space_name"),
            @Result(property = "asStoredChildName",   column = "as_stored_child_name"),
            @Result(property = "depType",             column = "dep_type"),
            @Result(property = "required",            column = "required"),
            @Result(property = "uniqueLinkId",        column = "unique_link_id"),
            @Result(property = "sortOrder",           column = "sort_order"),
            @Result(property = "tenantOid",           column = "tenant_oid"),
            @Result(property = "creator",             column = "creator"),
            @Result(property = "createdAt",           column = "created_at"),
            @Result(property = "updater",             column = "updater"),
            @Result(property = "updatedAt",           column = "updated_at")
    })
    @Override
    EngDocMemberLink selectByOid(@Param("oid") String oid);

    @Override
    @Select(SELECT_COLUMNS + "WHERE used_by_iteration_oid = #{usedByIterationOid} ORDER BY sort_order ASC, created_at ASC")
    @ResultMap("engDocMemberLinkResult")
    List<EngDocMemberLink> selectByUsedByIterationOid(@Param("usedByIterationOid") String usedByIterationOid);

    @Override
    @Select(SELECT_COLUMNS + "WHERE uses_master_oid = #{usesMasterOid} ORDER BY created_at DESC")
    @ResultMap("engDocMemberLinkResult")
    List<EngDocMemberLink> selectByUsesMasterOid(@Param("usesMasterOid") String usesMasterOid);
}
