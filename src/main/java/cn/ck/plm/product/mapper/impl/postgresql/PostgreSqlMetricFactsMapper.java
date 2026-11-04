/*
 * Copyright (c) 2026~2028 深圳乘恺科技有限公司
 * All rights reserved.
 *
 * @author Kiween.Hu; Roney.Liu
 */

package cn.ck.plm.product.mapper.impl.postgresql;

import cn.ck.plm.product.mapper.MetricFactsMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;

import java.util.List;
import java.util.Map;

/**
 * {@link MetricFactsMapper} 的 PostgreSQL 实现。
 */
@Mapper
@ConditionalOnProperty(name = "plm.database.type", havingValue = "postgresql", matchIfMissing = true)
public interface PostgreSqlMetricFactsMapper extends MetricFactsMapper {

    @Override
    @Select("SELECT status, COUNT(*) AS cnt FROM ck_issue "
            + "WHERE tenant_oid = #{tenantOid} AND product_oid = #{productOid} "
            + "GROUP BY status")
    List<Map<String, Object>> countIssueStatusByProduct(@Param("tenantOid") String tenantOid,
                                                        @Param("productOid") String productOid);

    @Override
    @Select("SELECT COUNT(*) FROM ck_part WHERE container_oid = #{containerOid}")
    int countPartsByContainer(@Param("containerOid") String containerOid);

    /**
     * {@code d.attr_value #>> '{}'}：JSONB 取标量文本。库里存的是 {@code "QUALIFIED"}（带引号），
     * 直接 {@code ::text} 会连引号一起返回，判等就永远不成立。
     */
    @Override
    @Select("<script>"
            + "SELECT d.entity_oid AS part_oid, d.attr_code AS attr_code, "
            + "       d.attr_value #>> '{}' AS attr_value "
            + "FROM ck_type_iba_data d "
            + "JOIN ck_part p ON p.oid = d.entity_oid "
            + "WHERE d.entity_type = 'PART' AND p.container_oid = #{containerOid} "
            + "  AND d.attr_code IN "
            + "<foreach collection='attrCodes' item='c' open='(' separator=',' close=')'>#{c}</foreach>"
            + "</script>")
    List<Map<String, Object>> selectPartIbaValues(@Param("containerOid") String containerOid,
                                                  @Param("attrCodes") List<String> attrCodes);

    /**
     * 替代关系是双向的（{@code role_a} / {@code role_b}），所以两端都要看；
     * {@code enabled = TRUE} 才算数（行业包 attributes 里写的 {@code status = 'ACTIVE'} 与真实表结构不符，
     * 以表为准）。
     */
    @Override
    @Select("SELECT COUNT(DISTINCT p.oid) FROM ck_part p "
            + "JOIN ck_part_alternate_link l "
            + "  ON (l.role_a_part_oid = p.oid OR l.role_b_part_oid = p.oid) "
            + "WHERE p.container_oid = #{containerOid} AND l.enabled = TRUE")
    int countPartsWithAlternate(@Param("containerOid") String containerOid);

    @Override
    @Select("SELECT pi.oid FROM ck_part_iteration pi "
            + "JOIN ck_part p ON p.oid = pi.master_oid "
            + "WHERE p.container_oid = #{containerOid} AND pi.latest = TRUE "
            + "  AND EXISTS (SELECT 1 FROM ck_bom_links bl WHERE bl.parent_iteration_oid = pi.oid) "
            + "LIMIT 1")
    String selectBomRootIteration(@Param("containerOid") String containerOid);

    /**
     * {@code attr_value #>> '{}'}：JSONB 取标量文本（库里存的是 {@code 1000.00}，直接 ::text 会带引号的情形同 PART）。
     * 属性没登记/没填 → 无行 → null，调用方据此区分"没填"与"填了 0"。
     */
    @Override
    @Select("SELECT d.attr_value #>> '{}' FROM ck_type_iba_data d "
            + "WHERE d.entity_oid = #{containerOid} AND d.attr_code = #{attrCode} "
            + "  AND d.entity_type IN ('PRODUCT_LINE','PRODUCT_MODEL') "
            + "LIMIT 1")
    String selectTargetCost(@Param("containerOid") String containerOid,
                            @Param("attrCode") String attrCode);

    /**
     * 可认证件 = 电子元器件 / PCBA。收口在软类型上，结构件与电气件不参与器件认证。
     */
    @Override
    @Select("SELECT COUNT(*) FROM ck_part p "
            + "WHERE p.container_oid = #{containerOid} "
            + "  AND p.type_definition_code IN ('ELECTRONIC','PCBA')")
    int countCertifiableParts(@Param("containerOid") String containerOid);

    /**
     * 准入 / 优选都用这一条：同一器件挂多个 MPN 时只算一件（DISTINCT p.oid），
     * 有一条合格料号即代表该器件可用。
     */
    @Override
    @Select("<script>"
            + "SELECT COUNT(DISTINCT p.oid) FROM ck_part p "
            + "JOIN ck_part_aml a ON a.part_oid = p.oid "
            + "WHERE p.container_oid = #{containerOid} "
            + "  AND p.type_definition_code IN ('ELECTRONIC','PCBA') "
            + "  AND a.approved_status IN "
            + "<foreach collection='statuses' item='s' open='(' separator=',' close=')'>#{s}</foreach>"
            + "</script>")
    int countCertifiablePartsWithAmlStatus(@Param("containerOid") String containerOid,
                                           @Param("statuses") List<String> statuses);

    /**
     * 「通过」只认 GO / GO_WITH_CONDITION —— 有条件通过也算上市放行，
     * 但 Hold / Terminate / 退回重做都不算"这事发生了"。
     */
    @Override
    @Select("SELECT to_char(g.decided_at, 'YYYY-MM-DD') FROM ck_gate g "
            + "WHERE g.owner_oid = #{ownerOid} AND g.code = #{gateCode} "
            + "  AND COALESCE(g.disabled, FALSE) = FALSE AND g.decided_at IS NOT NULL "
            + "  AND g.decision IN ('GO','GO_WITH_CONDITION') "
            + "ORDER BY g.decided_at DESC LIMIT 1")
    String selectGateDecidedDate(@Param("ownerOid") String ownerOid,
                                 @Param("gateCode") String gateCode);
}
