/*
 * Copyright (c) 2026~2028 深圳乘恺科技有限公司
 * All rights reserved.
 *
 * @author Kiween.Hu; Roney.Liu
 */

package cn.ck.plm.change.mapper.impl.postgresql;

import cn.ck.plm.change.entity.ChangeRequestAffectedObject;
import cn.ck.plm.change.mapper.ChangeRequestAffectedObjectMapper;
import org.apache.ibatis.annotations.*;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;

import java.util.List;

/**
 * {@link ChangeRequestAffectedObjectMapper} 的 PostgreSQL 实现。
 *
 * <p>容器名不落库：展示时用三个 LEFT JOIN + COALESCE 从三类容器表取，
 * 与 {@code ck_deliverable_artifact_link} 的做法一致（无子查询、无 UNION）。
 */
@Mapper
@ConditionalOnProperty(name = "plm.database.type", havingValue = "postgresql", matchIfMissing = true)
public interface PostgreSqlChangeRequestAffectedObjectMapper extends ChangeRequestAffectedObjectMapper {

    String SELECT_WITH_CONTAINER =
            "SELECT a.oid, a.request_oid, a.entity_type, a.entity_oid, a.entity_code, a.entity_name, "
                    + "a.container_oid, a.note, a.tenant_oid, a.creator, a.created_at, a.updater, a.updated_at, "
                    + "COALESCE(pl.name, pm.name, rc.name) AS container_name "
                    + "FROM ck_change_request_affected_object a "
                    + "LEFT JOIN ck_product_line pl ON pl.oid = a.container_oid "
                    + "LEFT JOIN ck_product_model pm ON pm.oid = a.container_oid "
                    + "LEFT JOIN ck_resource_container rc ON rc.oid = a.container_oid ";

    /**
     * 行是"替换式"的（先删后插，不更新），created_at / updated_at 交给数据库默认值。
     */
    @Override
    @Insert("INSERT INTO ck_change_request_affected_object (oid, request_oid, entity_type, entity_oid, "
            + "entity_code, entity_name, container_oid, note, tenant_oid, creator) VALUES ("
            + "#{oid}, #{requestOid}, #{entityType}, #{entityOid}, #{entityCode}, #{entityName}, "
            + "#{containerOid}, #{note}, #{tenantOid}, #{creator})")
    int insert(ChangeRequestAffectedObject row);

    @Override
    @Delete("DELETE FROM ck_change_request_affected_object WHERE request_oid = #{requestOid} AND tenant_oid = #{tenantOid}")
    int deleteByRequestOid(@Param("requestOid") String requestOid, @Param("tenantOid") String tenantOid);

    @Override
    @Delete("DELETE FROM ck_change_request_affected_object WHERE oid = #{oid} AND tenant_oid = #{tenantOid}")
    int deleteByOid(@Param("oid") String oid, @Param("tenantOid") String tenantOid);

    @Override
    @Select(SELECT_WITH_CONTAINER + "WHERE a.oid = #{oid} AND a.tenant_oid = #{tenantOid}")
    @Results(id = "craoResult", value = {
            @Result(property = "oid",          column = "oid"),
            @Result(property = "requestOid",   column = "request_oid"),
            @Result(property = "entityType",   column = "entity_type"),
            @Result(property = "entityOid",    column = "entity_oid"),
            @Result(property = "entityCode",   column = "entity_code"),
            @Result(property = "entityName",   column = "entity_name"),
            @Result(property = "containerOid", column = "container_oid"),
            @Result(property = "containerName", column = "container_name"),
            @Result(property = "note",         column = "note"),
            @Result(property = "tenantOid",    column = "tenant_oid"),
            @Result(property = "creator",      column = "creator"),
            @Result(property = "createdAt",    column = "created_at"),
            @Result(property = "updater",      column = "updater"),
            @Result(property = "updatedAt",    column = "updated_at")
    })
    ChangeRequestAffectedObject selectByOid(@Param("oid") String oid, @Param("tenantOid") String tenantOid);

    @Override
    @Select(SELECT_WITH_CONTAINER
            + "WHERE a.request_oid = #{requestOid} AND a.tenant_oid = #{tenantOid} ORDER BY a.created_at")
    @ResultMap("craoResult")
    List<ChangeRequestAffectedObject> selectByRequest(@Param("requestOid") String requestOid,
                                                      @Param("tenantOid") String tenantOid);

    @Override
    @Select("SELECT COUNT(*) FROM ck_change_request_affected_object "
            + "WHERE request_oid = #{requestOid} AND entity_type = #{entityType} "
            + "AND entity_oid = #{entityOid} AND tenant_oid = #{tenantOid}")
    int countByRequestAndEntity(@Param("requestOid") String requestOid,
                                @Param("entityType") String entityType,
                                @Param("entityOid") String entityOid,
                                @Param("tenantOid") String tenantOid);
}
