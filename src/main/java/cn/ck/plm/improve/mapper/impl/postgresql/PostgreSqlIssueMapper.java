/*
 * Copyright (c) 2026~2028 深圳乘恺科技有限公司
 * All rights reserved.
 */

package cn.ck.plm.improve.mapper.impl.postgresql;

import cn.ck.plm.improve.entity.Issue;
import cn.ck.plm.improve.mapper.IssueMapper;
import org.apache.ibatis.annotations.*;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;

import java.util.List;
import java.util.Map;

/**
 * {@link IssueMapper} 的 PostgreSQL 实现。
 *
 * <p>每条语句都<b>显式</b>写明 {@code tenant_oid}（INSERT 写列、SELECT 写条件）：
 * 显式书写使语义不依赖拦截器注入（拦截器见 SQL 已含 tenant_oid 会跳过改写），固定为"只本租户"。
 */
@Mapper
@ConditionalOnProperty(name = "plm.database.type", havingValue = "postgresql", matchIfMissing = true)
public interface PostgreSqlIssueMapper extends IssueMapper {

    /** 查询列（与实体字段一一对应；gate_label 是响应装配字段，不入库） */
    String COLUMNS = "oid, code, name, description, issue_type, severity, source, status, owner, reporter, "
            + "due_date, product_oid, product_type, gate_oid, related_type, related_code, "
            + "root_cause, resolution, verify_result, closed_at, "
            + "tenant_oid, creator, created_at, updater, updated_at";

    @Override
    @Insert("INSERT INTO ck_issue (" + COLUMNS + ") " +
            "VALUES (#{oid}, #{code}, #{name}, #{description}, #{issueType}, #{severity}, #{source}, " +
            "#{status}, #{owner}, #{reporter}, #{dueDate}, #{productOid}, #{productType}, #{gateOid}, " +
            "#{relatedType}, #{relatedCode}, " +
            "#{rootCause}, #{resolution}, #{verifyResult}, #{closedAt}, " +
            "#{tenantOid}, #{creator}, #{createdAt}, #{updater}, #{updatedAt})")
    int insert(Issue issue);

    @Override
    @Update("UPDATE ck_issue SET name = #{name}, description = #{description}, " +
            "issue_type = #{issueType}, severity = #{severity}, source = #{source}, status = #{status}, " +
            "owner = #{owner}, reporter = #{reporter}, due_date = #{dueDate}, " +
            "product_oid = #{productOid}, product_type = #{productType}, gate_oid = #{gateOid}, " +
            "related_type = #{relatedType}, related_code = #{relatedCode}, " +
            "root_cause = #{rootCause}, resolution = #{resolution}, verify_result = #{verifyResult}, " +
            "closed_at = #{closedAt}, updater = #{updater}, updated_at = #{updatedAt} " +
            "WHERE oid = #{oid}")
    int update(Issue issue);

    @Override
    @Delete("DELETE FROM ck_issue WHERE oid = #{oid}")
    int deleteByOid(@Param("oid") String oid);

    @Override
    @Select("SELECT " + COLUMNS + " FROM ck_issue WHERE oid = #{oid}")
    @Results(id = "issueResult", value = {
            @Result(property = "oid",          column = "oid"),
            @Result(property = "code",         column = "code"),
            @Result(property = "name",         column = "name"),
            @Result(property = "description",  column = "description"),
            @Result(property = "issueType",    column = "issue_type"),
            @Result(property = "severity",     column = "severity"),
            @Result(property = "source",       column = "source"),
            @Result(property = "status",       column = "status"),
            @Result(property = "owner",        column = "owner"),
            @Result(property = "reporter",     column = "reporter"),
            @Result(property = "dueDate",      column = "due_date"),
            @Result(property = "productOid",   column = "product_oid"),
            @Result(property = "productType",  column = "product_type"),
            @Result(property = "gateOid",      column = "gate_oid"),
            @Result(property = "relatedType",  column = "related_type"),
            @Result(property = "relatedCode",  column = "related_code"),
            @Result(property = "rootCause",    column = "root_cause"),
            @Result(property = "resolution",   column = "resolution"),
            @Result(property = "verifyResult", column = "verify_result"),
            @Result(property = "closedAt",     column = "closed_at"),
            @Result(property = "tenantOid",    column = "tenant_oid"),
            @Result(property = "creator",      column = "creator"),
            @Result(property = "createdAt",    column = "created_at"),
            @Result(property = "updater",      column = "updater"),
            @Result(property = "updatedAt",    column = "updated_at")
    })
    Issue selectByOid(@Param("oid") String oid);

    @Override
    @Select("<script>" +
            "SELECT " + COLUMNS + " FROM ck_issue " +
            "WHERE tenant_oid = #{tenantOid} " +
            "<if test='status != null and status != \"\"'>" +
            "  AND status = #{status} " +
            "</if>" +
            "<if test='severity != null and severity != \"\"'>" +
            "  AND severity = #{severity} " +
            "</if>" +
            "<if test='owner != null and owner != \"\"'>" +
            "  AND owner = #{owner} " +
            "</if>" +
            "<if test='keyword != null and keyword != \"\"'>" +
            "  AND (code ILIKE '%' || #{keyword} || '%' OR name ILIKE '%' || #{keyword} || '%' " +
            "       OR description ILIKE '%' || #{keyword} || '%') " +
            "</if>" +
            "<if test='relatedType != null and relatedType != \"\"'>" +
            "  AND related_type = #{relatedType} " +
            "</if>" +
            "<if test='relatedCode != null and relatedCode != \"\"'>" +
            "  AND related_code = #{relatedCode} " +
            "</if>" +
            "ORDER BY CASE status WHEN 'OPEN' THEN 1 WHEN 'IN_PROGRESS' THEN 2 WHEN 'VERIFYING' THEN 3 " +
            "         WHEN 'CLOSED' THEN 4 ELSE 5 END, created_at DESC " +
            "LIMIT #{limit} OFFSET #{offset}" +
            "</script>")
    @ResultMap("issueResult")
    List<Issue> selectList(@Param("tenantOid") String tenantOid,
                           @Param("status") String status,
                           @Param("severity") String severity,
                           @Param("owner") String owner,
                           @Param("keyword") String keyword,
                           @Param("relatedType") String relatedType,
                           @Param("relatedCode") String relatedCode,
                           @Param("limit") int limit,
                           @Param("offset") int offset);

    @Override
    @Select("<script>" +
            "SELECT COUNT(*) FROM ck_issue " +
            "WHERE tenant_oid = #{tenantOid} " +
            "<if test='status != null and status != \"\"'>" +
            "  AND status = #{status} " +
            "</if>" +
            "<if test='severity != null and severity != \"\"'>" +
            "  AND severity = #{severity} " +
            "</if>" +
            "<if test='owner != null and owner != \"\"'>" +
            "  AND owner = #{owner} " +
            "</if>" +
            "<if test='keyword != null and keyword != \"\"'>" +
            "  AND (code ILIKE '%' || #{keyword} || '%' OR name ILIKE '%' || #{keyword} || '%' " +
            "       OR description ILIKE '%' || #{keyword} || '%') " +
            "</if>" +
            "<if test='relatedType != null and relatedType != \"\"'>" +
            "  AND related_type = #{relatedType} " +
            "</if>" +
            "<if test='relatedCode != null and relatedCode != \"\"'>" +
            "  AND related_code = #{relatedCode} " +
            "</if>" +
            "</script>")
    int countList(@Param("tenantOid") String tenantOid,
                  @Param("status") String status,
                  @Param("severity") String severity,
                  @Param("owner") String owner,
                  @Param("keyword") String keyword,
                  @Param("relatedType") String relatedType,
                  @Param("relatedCode") String relatedCode);

    @Override
    @Select("SELECT status, COUNT(*) AS cnt FROM ck_issue WHERE tenant_oid = #{tenantOid} GROUP BY status")
    List<Map<String, Object>> countByStatus(@Param("tenantOid") String tenantOid);

    @Override
    @Select("SELECT " + COLUMNS + " FROM ck_issue " +
            "WHERE tenant_oid = #{tenantOid} AND product_oid = #{productOid} " +
            "AND status IN ('OPEN', 'IN_PROGRESS', 'VERIFYING') " +
            "ORDER BY CASE severity WHEN 'BLOCK' THEN 0 ELSE 1 END, created_at DESC")
    @ResultMap("issueResult")
    List<Issue> selectOpenByProduct(@Param("tenantOid") String tenantOid,
                                    @Param("productOid") String productOid);

    @Override
    @Select("SELECT COALESCE(MAX(CAST(SUBSTRING(code FROM 5) AS INTEGER)), 0) FROM ck_issue " +
            "WHERE tenant_oid = #{tenantOid} AND code LIKE 'ISS-%'")
    int maxCodeSeq(@Param("tenantOid") String tenantOid);
}
