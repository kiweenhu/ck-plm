/*
 * Copyright (c) 2026~2028 深圳乘恺科技有限公司
 * All rights reserved.
 *
 * @author Kiween.Hu; Roney.Liu
 */

package cn.ck.plm.change.mapper.impl.postgresql;

import cn.ck.plm.change.mapper.ChangeObjectLookupMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;

import java.util.List;
import java.util.Map;

/**
 * {@link ChangeObjectLookupMapper} 的 PostgreSQL 实现。
 *
 * <p>版本取 {@code latest = TRUE} 的那一版（与各迭代表的口径一致）。
 * 关键字为空时返回最近更新的 N 条，保证选择器打开就有内容。
 */
@Mapper
@ConditionalOnProperty(name = "plm.database.type", havingValue = "postgresql", matchIfMissing = true)
public interface PostgreSqlChangeObjectLookupMapper extends ChangeObjectLookupMapper {

    String PART_SNAPSHOT =
            "SELECT p.oid AS \"oid\", 'PART' AS \"entityType\", p.number AS \"code\", p.name AS \"name\", "
                    + "p.container_oid AS \"containerOid\", i.oid AS \"iterationOid\", "
                    + "i.display_version AS \"version\" "
                    + "FROM ck_part p LEFT JOIN ck_part_iteration i ON i.master_oid = p.oid AND i.latest = TRUE "
                    + "WHERE p.oid = #{oid} AND p.tenant_oid = #{tenantOid}";

    String DOC_SNAPSHOT =
            "SELECT d.oid AS \"oid\", 'DOCUMENT' AS \"entityType\", d.number AS \"code\", d.name AS \"name\", "
                    + "d.container_oid AS \"containerOid\", i.oid AS \"iterationOid\", "
                    + "i.display_version AS \"version\" "
                    + "FROM ck_document d LEFT JOIN ck_document_iteration i ON i.master_oid = d.oid AND i.latest = TRUE "
                    + "WHERE d.oid = #{oid} AND d.tenant_oid = #{tenantOid}";

    String ENG_SNAPSHOT =
            "SELECT e.oid AS \"oid\", 'ENG_DOCUMENT' AS \"entityType\", e.number AS \"code\", e.name AS \"name\", "
                    + "e.container_oid AS \"containerOid\", i.oid AS \"iterationOid\", "
                    + "i.display_version AS \"version\" "
                    + "FROM ck_eng_document e LEFT JOIN ck_eng_document_iteration i ON i.master_oid = e.oid AND i.latest = TRUE "
                    + "WHERE e.oid = #{oid} AND e.tenant_oid = #{tenantOid}";

    @Override
    @Select(PART_SNAPSHOT)
    Map<String, Object> selectPartSnapshot(@Param("oid") String oid, @Param("tenantOid") String tenantOid);

    @Override
    @Select(DOC_SNAPSHOT)
    Map<String, Object> selectDocumentSnapshot(@Param("oid") String oid, @Param("tenantOid") String tenantOid);

    @Override
    @Select(ENG_SNAPSHOT)
    Map<String, Object> selectEngDocumentSnapshot(@Param("oid") String oid, @Param("tenantOid") String tenantOid);

    // ==================== 候选 ====================

    /**
     * 「该对象是否已经在一张在途的变更通告里」—— 候选要不要置灰，就靠这段 LATERAL 带出的 {@code changeCode}。
     *
     * <p><b>为什么算通告、不算变更请求（2026-11-03 用户口径：*"处在变更中对象不能选"*）</b>：
     * 变更占用（{@code ck_change_affected_object.locked}）本来就只建在通告上 —— 一张通告是
     * 一份<b>执行清单</b>，同一对象同时只能被一份清单持有（见
     * {@code PostgreSqlChangeAffectedObjectMapper} 的 {@code uk_change_affected_lock}）。
     * ECR 阶段只是"预估影响面"，两张请求针对同一对象是正常评审过程，不该互相锁死。
     *
     * <p><b>为什么状态表里不含 REJECTED / COMPLETED / CLOSED / CANCELLED</b>：
     * 与 {@code ChangeNoticeFlow.ACTIVE} 同一口径 —— 驳回后要改完重提、已完成/已关闭/已取消
     * 的变更不再占用对象，继续置灰等于永久封死这个对象。
     *
     * <p>为什么要 LATERAL 而不是 JOIN：一个对象可能同时挂在多张通告上（历史数据、或跨产品），
     * 直接 join 会把候选复制成多行；这里只取最近一张，用于提示"已被谁占着"。
     */
    String ACTIVE_CHANGE_STATUSES = "'DRAFT','SUBMITTED','APPROVING','APPROVED','IN_WORK'";

    String ACTIVE_CHANGE_PART = "LEFT JOIN LATERAL ("
            + "  SELECT n.code FROM ck_change_affected_object a "
            + "  JOIN ck_change_notice n ON n.oid = a.notice_oid "
            + "  WHERE a.entity_type = 'PART' AND a.entity_oid = p.oid "
            + "    AND n.status IN (" + ACTIVE_CHANGE_STATUSES + ") "
            + "  ORDER BY n.created_at DESC LIMIT 1"
            + ") chg ON TRUE ";

    String ACTIVE_CHANGE_DOCUMENT = "LEFT JOIN LATERAL ("
            + "  SELECT n.code FROM ck_change_affected_object a "
            + "  JOIN ck_change_notice n ON n.oid = a.notice_oid "
            + "  WHERE a.entity_type = 'DOCUMENT' AND a.entity_oid = d.oid "
            + "    AND n.status IN (" + ACTIVE_CHANGE_STATUSES + ") "
            + "  ORDER BY n.created_at DESC LIMIT 1"
            + ") chg ON TRUE ";

    String ACTIVE_CHANGE_ENG_DOCUMENT = "LEFT JOIN LATERAL ("
            + "  SELECT n.code FROM ck_change_affected_object a "
            + "  JOIN ck_change_notice n ON n.oid = a.notice_oid "
            + "  WHERE a.entity_type = 'ENG_DOCUMENT' AND a.entity_oid = e.oid "
            + "    AND n.status IN (" + ACTIVE_CHANGE_STATUSES + ") "
            + "  ORDER BY n.created_at DESC LIMIT 1"
            + ") chg ON TRUE ";

    /**
     * 产品范围 CTE（仅在给了 {@code containerOid} 时注入）。
     *
     * <p>选系列：该系列 + 所有子系列 + 这些系列下的全部型号；选型号：只有它自己。
     * 这样"变更系列/型号"选的是系列时，系列下型号里的零件也能被找到，与产品树的直觉一致。
     *
     * <p>必须放在语句最前面，所以用 {@code <if>} 包住整个 WITH 前缀独立拼接。
     */
    String SCOPE_CTE =
            "<if test='containerOid != null and containerOid != \"\"'>"
                    + "WITH RECURSIVE line_scope AS ("
                    + "  SELECT oid FROM ck_product_line WHERE oid = #{containerOid}"
                    + "  UNION ALL"
                    + "  SELECT l.oid FROM ck_product_line l JOIN line_scope s ON l.parent_oid = s.oid"
                    + "), container_scope AS ("
                    + "  SELECT oid FROM line_scope"
                    + "  UNION"
                    + "  SELECT m.oid FROM ck_product_model m"
                    + "   WHERE m.oid = #{containerOid} OR m.parent_oid IN (SELECT oid FROM line_scope)"
                    + ") "
                    + "</if>";

    @Override
    @Select("<script>" + SCOPE_CTE +
            "SELECT p.oid AS \"oid\", 'PART' AS \"entityType\", p.number AS \"code\", p.name AS \"name\", " +
            "p.container_oid AS \"containerOid\", chg.code AS \"changeCode\" " +
            "FROM ck_part p " + ACTIVE_CHANGE_PART +
            "WHERE p.tenant_oid = #{tenantOid} " +
            "<if test='keyword != null and keyword != \"\"'>" +
            "  AND (p.number ILIKE '%' || #{keyword} || '%' OR p.name ILIKE '%' || #{keyword} || '%') " +
            "</if>" +
            "<if test='containerOid != null and containerOid != \"\"'>" +
            "  AND p.container_oid IN (SELECT oid FROM container_scope) " +
            "</if>" +
            "ORDER BY p.updated_at DESC LIMIT #{limit}" +
            "</script>")
    List<Map<String, Object>> searchParts(@Param("keyword") String keyword,
                                          @Param("tenantOid") String tenantOid,
                                          @Param("containerOid") String containerOid,
                                          @Param("limit") int limit);

    @Override
    @Select("<script>" + SCOPE_CTE +
            "SELECT d.oid AS \"oid\", 'DOCUMENT' AS \"entityType\", d.number AS \"code\", d.name AS \"name\", " +
            "d.container_oid AS \"containerOid\", chg.code AS \"changeCode\" " +
            "FROM ck_document d " + ACTIVE_CHANGE_DOCUMENT +
            "WHERE d.tenant_oid = #{tenantOid} " +
            "<if test='keyword != null and keyword != \"\"'>" +
            "  AND (d.number ILIKE '%' || #{keyword} || '%' OR d.name ILIKE '%' || #{keyword} || '%') " +
            "</if>" +
            "<if test='containerOid != null and containerOid != \"\"'>" +
            "  AND d.container_oid IN (SELECT oid FROM container_scope) " +
            "</if>" +
            "ORDER BY d.updated_at DESC LIMIT #{limit}" +
            "</script>")
    List<Map<String, Object>> searchDocuments(@Param("keyword") String keyword,
                                              @Param("tenantOid") String tenantOid,
                                              @Param("containerOid") String containerOid,
                                              @Param("limit") int limit);

    @Override
    @Select("<script>" + SCOPE_CTE +
            "SELECT e.oid AS \"oid\", 'ENG_DOCUMENT' AS \"entityType\", e.number AS \"code\", e.name AS \"name\", " +
            "e.container_oid AS \"containerOid\", chg.code AS \"changeCode\" " +
            "FROM ck_eng_document e " + ACTIVE_CHANGE_ENG_DOCUMENT +
            "WHERE e.tenant_oid = #{tenantOid} " +
            "<if test='keyword != null and keyword != \"\"'>" +
            "  AND (e.number ILIKE '%' || #{keyword} || '%' OR e.name ILIKE '%' || #{keyword} || '%') " +
            "</if>" +
            "<if test='containerOid != null and containerOid != \"\"'>" +
            "  AND e.container_oid IN (SELECT oid FROM container_scope) " +
            "</if>" +
            "ORDER BY e.updated_at DESC LIMIT #{limit}" +
            "</script>")
    List<Map<String, Object>> searchEngDocuments(@Param("keyword") String keyword,
                                                 @Param("tenantOid") String tenantOid,
                                                 @Param("containerOid") String containerOid,
                                                 @Param("limit") int limit);

    // ==================== 关联对象收集 ====================

    /**
     * 关联文档：{@code ck_doc_part_link} 里 DESCRIBES / REFERENCE 两类，按零件的<b>全部迭代</b>取。
     *
     * <p>{@code DISTINCT} 是必要的：同一份文档可能在多个迭代上各挂过一条。
     */
    String RELATED_DOCUMENTS =
            "SELECT DISTINCT d.oid AS \"oid\", 'DOCUMENT' AS \"entityType\", d.number AS \"code\", "
                    + "d.name AS \"name\", d.container_oid AS \"containerOid\", 'DOCUMENT' AS \"relation\" "
                    + "FROM ck_part_iteration i "
                    + "JOIN ck_doc_part_link l ON l.part_iteration_oid = i.oid "
                    + "  AND l.link_type IN ('DESCRIBES', 'REFERENCE') "
                    + "JOIN ck_document d ON d.oid = l.doc_master_oid "
                    + "WHERE i.master_oid = #{partOid} AND d.tenant_oid = #{tenantOid} "
                    + "ORDER BY d.number";

    @Override
    @Select(RELATED_DOCUMENTS)
    List<Map<String, Object>> selectRelatedDocuments(@Param("partOid") String partOid,
                                                     @Param("tenantOid") String tenantOid);

    /**
     * 替代件：A 端 / B 端双向查（有序对里本方可能在任一端），取另一端。
     *
     * <p>停用（{@code enabled = false}）的不收：已经不成立的替代关系，收进变更范围只会误导。
     */
    String ALTERNATE_PARTS =
            "SELECT p.oid AS \"oid\", 'PART' AS \"entityType\", p.number AS \"code\", p.name AS \"name\", "
                    + "p.container_oid AS \"containerOid\", 'ALTERNATE' AS \"relation\", "
                    + "a.alternate_type AS \"alternateType\" "
                    + "FROM ck_part_alternate_link a "
                    + "JOIN ck_part p ON p.oid = CASE WHEN a.role_a_part_oid = #{partOid} "
                    + "  THEN a.role_b_part_oid ELSE a.role_a_part_oid END "
                    + "WHERE (a.role_a_part_oid = #{partOid} OR a.role_b_part_oid = #{partOid}) "
                    + "  AND a.enabled IS NOT FALSE "
                    + "  AND p.tenant_oid = #{tenantOid} "
                    + "ORDER BY p.number";

    @Override
    @Select(ALTERNATE_PARTS)
    List<Map<String, Object>> selectAlternateParts(@Param("partOid") String partOid,
                                                   @Param("tenantOid") String tenantOid);
}
