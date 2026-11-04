/*
 * Copyright (c) 2026~2028 深圳乘恺科技有限公司
 * All rights reserved.
 */

package cn.ck.plm.change.mapper.impl.postgresql;

import cn.ck.plm.change.entity.ChangeNotice;
import cn.ck.plm.change.mapper.ChangeNoticeMapper;
import org.apache.ibatis.annotations.*;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;

import java.util.List;
import java.util.Map;

/**
 * {@link ChangeNoticeMapper} 的 PostgreSQL 实现。
 */
@Mapper
@ConditionalOnProperty(name = "plm.database.type", havingValue = "postgresql", matchIfMissing = true)
public interface PostgreSqlChangeNoticeMapper extends ChangeNoticeMapper {

    String COLUMNS = "oid, code, name, description, attachment_oid, request_oid, product_oid, product_type, "
            + "change_type, change_level, priority, "
            + "cert_impact, customer_impact, inventory_impact, reason, proposal, "
            + "status, plan_start, plan_end, "
            + "process_instance_id, approved_at, completed_at, closed_at, owner, "
            + "approval_bypassed, bypass_reason, bypassed_by, bypassed_at, "
            + "tenant_oid, creator, created_at, updater, updated_at";

    /**
     * 查询用列：实体列 + 所属容器名。
     *
     * <p>名称用标量子查询而不是 JOIN：product_oid 可能指向系列也可能指向型号，
     * 两表 LEFT JOIN 要写两次再 COALESCE，且列表/详情/子单三处 SQL 都得复制一遍 JOIN。
     * 这里只在一处定义，代价是每行两次索引点查（列表按页取，量可控）。
     *
     * <p>不能塞进 {@link #COLUMNS}：那个常量同时用于 INSERT 的列名列表，
     * 掺进子查询会让插入语法直接报错。
     */
    String SELECT_COLUMNS = COLUMNS + ", COALESCE("
            + "(SELECT pm.name FROM ck_product_model pm WHERE pm.oid = ck_change_notice.product_oid), "
            + "(SELECT pl.name FROM ck_product_line  pl WHERE pl.oid = ck_change_notice.product_oid)"
            + ") AS product_name";

    @Override
    @Insert("INSERT INTO ck_change_notice (" + COLUMNS + ") VALUES (" +
            "#{oid}, #{code}, #{name}, #{description}, #{attachmentOid}, #{requestOid}, #{productOid}, #{productType}, "
            + "#{changeType}, #{changeLevel}, #{priority}, "
            + "#{certImpact}, #{customerImpact}, #{inventoryImpact}, #{reason}, #{proposal}, "
            + "#{status}, #{planStart}, #{planEnd}, " +
            "#{processInstanceId}, #{approvedAt}, #{completedAt}, #{closedAt}, #{owner}, " +
            "#{approvalBypassed}, #{bypassReason}, #{bypassedBy}, #{bypassedAt}, " +
            "#{tenantOid}, #{creator}, #{createdAt}, #{updater}, #{updatedAt})")
    int insert(ChangeNotice notice);

    @Override
    @Update("UPDATE ck_change_notice SET name = #{name}, description = #{description}, " +
            "attachment_oid = #{attachmentOid}, " +
            "request_oid = #{requestOid}, product_oid = #{productOid}, product_type = #{productType}, " +
            "change_type = #{changeType}, change_level = #{changeLevel}, priority = #{priority}, " +
            "cert_impact = #{certImpact}, customer_impact = #{customerImpact}, " +
            "inventory_impact = #{inventoryImpact}, reason = #{reason}, proposal = #{proposal}, " +
            "status = #{status}, plan_start = #{planStart}, plan_end = #{planEnd}, " +
            "process_instance_id = #{processInstanceId}, approved_at = #{approvedAt}, " +
            "completed_at = #{completedAt}, closed_at = #{closedAt}, owner = #{owner}, " +
            "approval_bypassed = #{approvalBypassed}, bypass_reason = #{bypassReason}, " +
            "bypassed_by = #{bypassedBy}, bypassed_at = #{bypassedAt}, " +
            "updater = #{updater}, updated_at = #{updatedAt} " +
            "WHERE oid = #{oid}")
    int update(ChangeNotice notice);

    @Override
    @Delete("DELETE FROM ck_change_notice WHERE oid = #{oid}")
    int deleteByOid(@Param("oid") String oid);

    @Override
    @Select("SELECT " + SELECT_COLUMNS + " FROM ck_change_notice WHERE oid = #{oid}")
    @Results(id = "changeNoticeResult", value = {
            @Result(property = "oid",               column = "oid"),
            @Result(property = "code",              column = "code"),
            @Result(property = "name",              column = "name"),
            @Result(property = "description",       column = "description"),
            @Result(property = "attachmentOid",     column = "attachment_oid"),
            @Result(property = "requestOid",        column = "request_oid"),
            @Result(property = "productOid",        column = "product_oid"),
            @Result(property = "productType",       column = "product_type"),
            @Result(property = "productName",       column = "product_name"),
            @Result(property = "changeType",        column = "change_type"),
            @Result(property = "changeLevel",       column = "change_level"),
            @Result(property = "priority",          column = "priority"),
            @Result(property = "certImpact",        column = "cert_impact"),
            @Result(property = "customerImpact",    column = "customer_impact"),
            @Result(property = "inventoryImpact",   column = "inventory_impact"),
            @Result(property = "reason",            column = "reason"),
            @Result(property = "proposal",          column = "proposal"),
            @Result(property = "status",            column = "status"),
            @Result(property = "planStart",         column = "plan_start"),
            @Result(property = "planEnd",           column = "plan_end"),
            @Result(property = "processInstanceId", column = "process_instance_id"),
            @Result(property = "approvedAt",        column = "approved_at"),
            @Result(property = "completedAt",       column = "completed_at"),
            @Result(property = "closedAt",          column = "closed_at"),
            @Result(property = "owner",             column = "owner"),
            @Result(property = "approvalBypassed",  column = "approval_bypassed"),
            @Result(property = "bypassReason",      column = "bypass_reason"),
            @Result(property = "bypassedBy",        column = "bypassed_by"),
            @Result(property = "bypassedAt",        column = "bypassed_at"),
            @Result(property = "tenantOid",         column = "tenant_oid"),
            @Result(property = "creator",           column = "creator"),
            @Result(property = "createdAt",         column = "created_at"),
            @Result(property = "updater",           column = "updater"),
            @Result(property = "updatedAt",         column = "updated_at")
    })
    ChangeNotice selectByOid(@Param("oid") String oid);

    @Override
    @Select("<script>" +
            "SELECT " + SELECT_COLUMNS + " FROM ck_change_notice " +
            "WHERE tenant_oid = #{tenantOid} " +
            "<if test='status != null and status != \"\"'>  AND status = #{status} </if>" +
            "<if test='requestOid != null and requestOid != \"\"'>  AND request_oid = #{requestOid} </if>" +
            "<if test='keyword != null and keyword != \"\"'>" +
            "  AND (code ILIKE '%' || #{keyword} || '%' OR name ILIKE '%' || #{keyword} || '%'" +
            "       OR reason ILIKE '%' || #{keyword} || '%') " +
            "</if>" +
            "ORDER BY CASE WHEN status IN ('CLOSED', 'CANCELLED') THEN 1 ELSE 0 END, created_at DESC " +
            "LIMIT #{limit} OFFSET #{offset}" +
            "</script>")
    @ResultMap("changeNoticeResult")
    List<ChangeNotice> selectList(@Param("tenantOid") String tenantOid,
                                  @Param("status") String status,
                                  @Param("requestOid") String requestOid,
                                  @Param("keyword") String keyword,
                                  @Param("limit") int limit,
                                  @Param("offset") int offset);

    @Override
    @Select("<script>" +
            "SELECT COUNT(*) FROM ck_change_notice WHERE tenant_oid = #{tenantOid} " +
            "<if test='status != null and status != \"\"'>  AND status = #{status} </if>" +
            "<if test='requestOid != null and requestOid != \"\"'>  AND request_oid = #{requestOid} </if>" +
            "<if test='keyword != null and keyword != \"\"'>" +
            "  AND (code ILIKE '%' || #{keyword} || '%' OR name ILIKE '%' || #{keyword} || '%'" +
            "       OR reason ILIKE '%' || #{keyword} || '%') " +
            "</if>" +
            "</script>")
    int countList(@Param("tenantOid") String tenantOid,
                  @Param("status") String status,
                  @Param("requestOid") String requestOid,
                  @Param("keyword") String keyword);

    @Override
    @Select("SELECT status, COUNT(*) AS cnt FROM ck_change_notice " +
            "WHERE tenant_oid = #{tenantOid} GROUP BY status")
    List<Map<String, Object>> countByStatus(@Param("tenantOid") String tenantOid);

    @Override
    @Select("SELECT COALESCE(MAX(SUBSTRING(code FROM 5)::INTEGER), 0) FROM ck_change_notice " +
            "WHERE tenant_oid = #{tenantOid} AND code ~ '^ECN-[0-9]+$'")
    int maxCodeSeq(@Param("tenantOid") String tenantOid);

    /**
     * 未关闭的子通告。不设 LIMIT：调用方要的是"还有没有、是哪几个"，
     * 截断会给出错误的门槛判断（漏掉第 201 个就等于放行）。
     */
    @Override
    @Select("SELECT " + SELECT_COLUMNS + " FROM ck_change_notice " +
            "WHERE request_oid = #{requestOid} AND tenant_oid = #{tenantOid} " +
            "  AND status NOT IN ('CLOSED', 'CANCELLED') " +
            "ORDER BY code")
    @ResultMap("changeNoticeResult")
    List<ChangeNotice> selectOpenByRequest(@Param("requestOid") String requestOid,
                                          @Param("tenantOid") String tenantOid);

    @Override
    @Select("SELECT COUNT(*) FROM ck_change_notice " +
            "WHERE request_oid = #{requestOid} AND tenant_oid = #{tenantOid}")
    int countByRequest(@Param("requestOid") String requestOid, @Param("tenantOid") String tenantOid);
}
