/*
 * Copyright (c) 2026~2028 深圳乘恺科技有限公司
 * All rights reserved.
 *
 * @author Kiween.Hu; Roney.Liu
 */

package cn.ck.plm.product.mapper.impl.postgresql;

import cn.ck.plm.product.entity.DeliverableArtifactLink;
import cn.ck.plm.product.mapper.DeliverableArtifactLinkMapper;
import org.apache.ibatis.annotations.*;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;

import java.util.List;
import java.util.Map;

/**
 * {@link DeliverableArtifactLinkMapper} 的 PostgreSQL 实现。
 *
 * <p>约定与项目一致：{@code tenant_oid} 不入 INSERT / SELECT 列（INSERT 由 AuditInterceptor 填充、
 * SELECT 由 TenantInterceptor 注入条件）；字段映射靠 map-underscore-to-camel-case。
 *
 * <p>返回 Map 的列一律用双引号别名显式转驼峰（Map 走不了下划线转驼峰，别名是唯一可靠手段）。
 */
@Mapper
@ConditionalOnProperty(name = "plm.database.type", havingValue = "postgresql", matchIfMissing = true)
public interface PostgreSqlDeliverableArtifactLinkMapper extends DeliverableArtifactLinkMapper {

    String COLS = "oid, deliverable_oid, entity_type, type_code, entity_oid, iteration_oid, role, "
            + "container_oid, creator, created_at, updater, updated_at";

    @Override
    @Insert("INSERT INTO ck_deliverable_artifact_link (" + COLS + ") VALUES ("
            + "#{oid}, #{deliverableOid}, #{entityType}, #{typeCode}, #{entityOid}, #{iterationOid}, #{role}, "
            + "#{containerOid}, #{creator}, #{createdAt}, #{updater}, #{updatedAt})")
    int insert(DeliverableArtifactLink link);

    @Override
    @Select("SELECT " + COLS + " FROM ck_deliverable_artifact_link WHERE oid = #{oid}")
    DeliverableArtifactLink selectByOid(@Param("oid") String oid);

    @Override
    @Delete("DELETE FROM ck_deliverable_artifact_link WHERE oid = #{oid}")
    int deleteByOid(@Param("oid") String oid);

    /**
     * 注意：不要用 UNION 子查询取产出物名称 —— {@code TenantStatementInterceptor.rewriteWhere} 会把
     * 租户条件插在「第一个 CLAUSE_BOUNDARY（RETURNING/ORDER BY/GROUP BY/LIMIT/UNION/…）之前」，
     * 子查询里的 UNION 会被当成本语句的收尾子句，条件被拼进子查询 → 语法错误（2026-10-11 踩过）。
     * 改用三个 LEFT JOIN + COALESCE（无子查询、无 UNION，条件会正确落到顶层 WHERE）。
     */
    @Override
    @Select("SELECT l.oid AS \"oid\", l.entity_type AS \"entityType\", l.type_code AS \"typeCode\", "
            + "l.entity_oid AS \"entityOid\", l.iteration_oid AS \"iterationOid\", l.role AS \"role\", "
            + "l.container_oid AS \"containerOid\", l.created_at AS \"createdAt\", "
            + "COALESCE(d.number, e.number, p.number) AS \"entityCode\", "
            + "COALESCE(d.name, e.name, p.name) AS \"entityName\" "
            + "FROM ck_deliverable_artifact_link l "
            + "LEFT JOIN ck_document d ON d.oid = l.entity_oid AND l.entity_type = 'DOCUMENT' "
            + "LEFT JOIN ck_eng_document e ON e.oid = l.entity_oid AND l.entity_type = 'ENG_DOCUMENT' "
            + "LEFT JOIN ck_part p ON p.oid = l.entity_oid AND l.entity_type = 'PART' "
            + "WHERE l.deliverable_oid = #{deliverableOid} ORDER BY l.created_at ASC")
    List<Map<String, Object>> selectByDeliverableOid(@Param("deliverableOid") String deliverableOid);

    @Override
    @Select("SELECT l.deliverable_oid AS \"deliverableOid\", COUNT(*) AS \"cnt\" "
            + "FROM ck_deliverable_artifact_link l JOIN ck_deliverable d ON d.oid = l.deliverable_oid "
            + "WHERE d.owner_oid = #{ownerOid} GROUP BY l.deliverable_oid")
    List<Map<String, Object>> countByOwnerOid(@Param("ownerOid") String ownerOid);

    @Override
    @Select("SELECT root_type_code FROM ck_type_definition WHERE code = #{code} LIMIT 1")
    String selectRootTypeCode(@Param("code") String code);

    // ==================== 候选查询（硬约束在 Controller 侧决定查哪张表、是否按类型过滤） ====================

    /**
     * 候选里排除**已经被任何交付件关联过**的对象（2026-10-29 用户口径：
     * 「已关联其他交付件的对象不要出现在下面可关联的清单中，也不需要明确标识出来」）。
     *
     * <p>一个产出物只属于一个交付件（右侧「产出物」列的已绑定状态按这个前提判定），
     * 所以这里不按交付件 / 归属过滤，全表排除；已解除关联后又会重新出现。
     * 前端不再需要「已关联」这层标识 —— 列表里出现的就一定是能关联的。
     */
    @Override
    @Select("<script>SELECT oid AS \"oid\", number AS \"code\", name AS \"name\", "
            + "type_definition_code AS \"typeCode\", container_oid AS \"containerOid\", "
            + "'DOCUMENT' AS \"entityType\" FROM ck_document "
            + "<where>AND NOT EXISTS (SELECT 1 FROM ck_deliverable_artifact_link l"
            + " WHERE l.entity_type = 'DOCUMENT' AND l.entity_oid = ck_document.oid)"
            + "<if test='keyword != null and keyword != \"\"'>"
            + "AND (name ILIKE '%' || #{keyword} || '%' OR number ILIKE '%' || #{keyword} || '%')"
            + "</if></where> ORDER BY created_at DESC LIMIT 50</script>")
    List<Map<String, Object>> searchDocumentCandidates(@Param("keyword") String keyword);

    @Override
    @Select("<script>SELECT oid AS \"oid\", number AS \"code\", name AS \"name\", "
            + "type_definition_code AS \"typeCode\", container_oid AS \"containerOid\", "
            + "'ENG_DOCUMENT' AS \"entityType\" FROM ck_eng_document "
            + "<where>AND NOT EXISTS (SELECT 1 FROM ck_deliverable_artifact_link l"
            + " WHERE l.entity_type = 'ENG_DOCUMENT' AND l.entity_oid = ck_eng_document.oid)"
            + "<if test='typeCode != null and typeCode != \"\"'>AND type_definition_code = #{typeCode} </if>"
            + "<if test='keyword != null and keyword != \"\"'>"
            + "AND (name ILIKE '%' || #{keyword} || '%' OR number ILIKE '%' || #{keyword} || '%')"
            + "</if></where> ORDER BY created_at DESC LIMIT 50</script>")
    List<Map<String, Object>> searchEngDocCandidates(@Param("typeCode") String typeCode,
                                                     @Param("keyword") String keyword);

    @Override
    @Select("<script>SELECT oid AS \"oid\", number AS \"code\", name AS \"name\", "
            + "type_definition_code AS \"typeCode\", container_oid AS \"containerOid\", "
            + "'PART' AS \"entityType\" FROM ck_part "
            + "<where>AND NOT EXISTS (SELECT 1 FROM ck_deliverable_artifact_link l"
            + " WHERE l.entity_type = 'PART' AND l.entity_oid = ck_part.oid)"
            + "<if test='typeCode != null and typeCode != \"\"'>AND type_definition_code = #{typeCode} </if>"
            + "<if test='keyword != null and keyword != \"\"'>"
            + "AND (name ILIKE '%' || #{keyword} || '%' OR number ILIKE '%' || #{keyword} || '%')"
            + "</if></where> ORDER BY created_at DESC LIMIT 50</script>")
    List<Map<String, Object>> searchPartCandidates(@Param("typeCode") String typeCode,
                                                   @Param("keyword") String keyword);

    /** 该对象已关联的交付件（唯一性：一个对象只属于一个交付件） */
    @Override
    @Select("SELECT deliverable_oid FROM ck_deliverable_artifact_link "
            + "WHERE entity_type = #{entityType} AND entity_oid = #{entityOid} LIMIT 1")
    String selectDeliverableOidByEntity(@Param("entityType") String entityType,
                                       @Param("entityOid") String entityOid);

    // ==================== 创建时的服务端复核（存在性 + 类别 + 容器快照） ====================

    @Override
    @Select("SELECT oid AS \"oid\", number AS \"code\", name AS \"name\", "
            + "type_definition_code AS \"typeCode\", container_oid AS \"containerOid\" "
            + "FROM ck_document WHERE oid = #{oid}")
    Map<String, Object> selectDocumentArtifact(@Param("oid") String oid);

    @Override
    @Select("SELECT oid AS \"oid\", number AS \"code\", name AS \"name\", "
            + "type_definition_code AS \"typeCode\", container_oid AS \"containerOid\" "
            + "FROM ck_eng_document WHERE oid = #{oid}")
    Map<String, Object> selectEngDocArtifact(@Param("oid") String oid);

    @Override
    @Select("SELECT oid AS \"oid\", number AS \"code\", name AS \"name\", "
            + "type_definition_code AS \"typeCode\", container_oid AS \"containerOid\" "
            + "FROM ck_part WHERE oid = #{oid}")
    Map<String, Object> selectPartArtifact(@Param("oid") String oid);

    // ==================== 状态判定用（2026-10-11） ====================

    @Override
    @Select("SELECT l.deliverable_oid AS \"deliverableOid\", l.entity_type AS \"entityType\", "
            + "l.type_code AS \"typeCode\", l.entity_oid AS \"entityOid\" "
            + "FROM ck_deliverable_artifact_link l JOIN ck_deliverable d ON d.oid = l.deliverable_oid "
            + "WHERE d.owner_oid = #{ownerOid}")
    List<Map<String, Object>> selectByOwnerOid(@Param("ownerOid") String ownerOid);

    /**
     * 挂在产出物实例上的未闭环问题 —— 按产出物**编码**对齐 {@code ck_issue.related_code}。
     *
     * <p>不用子查询 / UNION：租户拦截器只会给主表别名补条件，三层 LEFT JOIN 取编码是本文件既有做法
     * （见 {@code selectByDeliverableOid} 的同一条注释）。{@code i.tenant_oid} 不在这里写死 ——
     * 写了会让拦截器判定"已含租户条件"而整条都不注入。
     */
    @Override
    @Select("SELECT l.deliverable_oid AS \"deliverableOid\", l.entity_type AS \"entityType\", "
            + "COALESCE(d.number, e.number, p.number) AS \"entityCode\", "
            + "i.code AS \"issueCode\", i.name AS \"issueName\", "
            + "i.severity AS \"severity\", i.status AS \"issueStatus\" "
            + "FROM ck_deliverable_artifact_link l "
            + "JOIN ck_deliverable dl ON dl.oid = l.deliverable_oid "
            + "LEFT JOIN ck_document d ON d.oid = l.entity_oid AND l.entity_type = 'DOCUMENT' "
            + "LEFT JOIN ck_eng_document e ON e.oid = l.entity_oid AND l.entity_type = 'ENG_DOCUMENT' "
            + "LEFT JOIN ck_part p ON p.oid = l.entity_oid AND l.entity_type = 'PART' "
            + "JOIN ck_issue i ON i.related_type = l.entity_type "
            + "  AND i.related_code = COALESCE(d.number, e.number, p.number) "
            + "WHERE dl.owner_oid = #{ownerOid} AND i.status NOT IN ('CLOSED', 'CANCELLED')")
    List<Map<String, Object>> selectOpenIssuesOfOwnerArtifacts(@Param("ownerOid") String ownerOid);

    /*
     * 三条状态查询都多带一列 lifecycle_template_iteration_oid：状态 code → 显示名（「评审中」）
     * 的权威来源是该对象所用的生命周期模板（见 LifecycleStatusService#displayName），
     * 只给 code 的话悬停浮层里就只能显示 REVIEW 这种用户看不懂的标识。
     * 判定器（DeliverableStatusEvaluator）只读 entityOid / status，多出的列对它无影响。
     */
    @Override
    @Select("<script>SELECT master_oid AS \"entityOid\", status AS \"status\", "
            + "lifecycle_template_iteration_oid AS \"lifecycleTemplateIterationOid\" FROM ck_document_iteration "
            + "WHERE latest = TRUE AND master_oid IN "
            + "<foreach item='o' collection='masterOids' open='(' separator=',' close=')'>#{o}</foreach>"
            + "</script>")
    List<Map<String, Object>> selectLatestDocStatus(@Param("masterOids") List<String> masterOids);

    @Override
    @Select("<script>SELECT master_oid AS \"entityOid\", status AS \"status\", "
            + "lifecycle_template_iteration_oid AS \"lifecycleTemplateIterationOid\" FROM ck_eng_document_iteration "
            + "WHERE latest = TRUE AND master_oid IN "
            + "<foreach item='o' collection='masterOids' open='(' separator=',' close=')'>#{o}</foreach>"
            + "</script>")
    List<Map<String, Object>> selectLatestEngDocStatus(@Param("masterOids") List<String> masterOids);

    @Override
    @Select("<script>SELECT master_oid AS \"entityOid\", status AS \"status\", "
            + "lifecycle_template_iteration_oid AS \"lifecycleTemplateIterationOid\" FROM ck_part_iteration "
            + "WHERE latest = TRUE AND master_oid IN "
            + "<foreach item='o' collection='masterOids' open='(' separator=',' close=')'>#{o}</foreach>"
            + "</script>")
    List<Map<String, Object>> selectLatestPartStatus(@Param("masterOids") List<String> masterOids);

    /** 生命周期模板的状态序列（LIFECYCLE 达标判定按 sort_order 比大小，见 Mapper 接口注释） */
    @Override
    @Select("<script>SELECT iteration_oid AS \"templateIterationOid\", status_code AS \"statusCode\", "
            + "status_display_name AS \"statusDisplayName\", sort_order AS \"sortOrder\" "
            + "FROM ck_lifecycle_template_state WHERE iteration_oid IN "
            + "<foreach item='o' collection='templateIterationOids' open='(' separator=',' close=')'>#{o}</foreach>"
            + " ORDER BY sort_order</script>")
    List<Map<String, Object>> selectTemplateStates(@Param("templateIterationOids") List<String> templateIterationOids);
}
