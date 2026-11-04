/*
 * Copyright (c) 2026~2028 深圳乘恺科技有限公司
 * All rights reserved.
 */

package cn.ck.plm.search;

import cn.ck.plm.base.util.TenantContext;
import cn.ck.plm.search.dto.SearchResultVO;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * 全局搜索服务实现 —— 使用 JDBC 跨表 UNION ALL + LIKE 模糊匹配。
 *
 * <p>不依赖额外 mapper，通过原生 SQL 实现，便于一处集中管理各表的搜索逻辑。
 *
 * <p><b>租户条件必须显式写</b>：这条路走的是 {@link JdbcTemplate}，绕开了
 * {@code TenantStatementInterceptor}（那是 MyBatis 的 StatementHandler 插件），
 * 不写 {@code tenant_oid = ?} 就会搜到别的租户的零件、文档乃至变更单。
 */
@Service
public class GlobalSearchServiceImpl implements GlobalSearchService {

    private final JdbcTemplate jdbcTemplate;

    public GlobalSearchServiceImpl(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public List<SearchResultVO> search(String keyword, int limit) {
        if (keyword == null || keyword.trim().isEmpty()) {
            return new ArrayList<>();
        }
        String q = "%" + keyword.trim().toLowerCase() + "%";
        String tenantOid = TenantContext.get();
        int max = Math.max(1, Math.min(limit, 100));

        // 跨 6 张表 UNION ALL，每个子查询对 name 和 code/number 做 LIKE 匹配。
        // 末尾带具体类型编码（type_definition_code）：调用方（如"发起流程"多选对象）要按类型过滤，
        // 只看 type（哪张表）不够 —— 同是 ck_part 的零件也分 ELECTRONIC / STRUCTURAL。
        // 产品系列·型号与变更单没有类型概念，给 NULL（列数必须一致）
        //
        // 2026-11-01 补两类（用户：*"变更对象和Eng_document对象也可以被检索到"*）：
        //   ENG_DOCUMENT（工程文档）/ CHANGE_REQUEST + CHANGE_NOTICE（变更单）——
        //   原来只能搜到产品系列·型号·零件·文档，变更单得先进「变更控制」再找。
        String sql =
            "(SELECT 'PRODUCT_LINE' AS type, oid, code, name, " +
            "        '/resource?type=PRODUCT_LINE&oid=' || oid AS link, " +
            "        NULL AS type_definition_code " +
            "   FROM ck_product_line WHERE tenant_oid = ? AND (LOWER(name) LIKE ? OR LOWER(code) LIKE ?)) " +
            "UNION ALL " +
            "(SELECT 'PRODUCT_MODEL' AS type, oid, code, name, " +
            "        '/resource?type=PRODUCT_MODEL&oid=' || oid AS link, " +
            "        NULL AS type_definition_code " +
            "   FROM ck_product_model WHERE tenant_oid = ? AND (LOWER(name) LIKE ? OR LOWER(code) LIKE ?)) " +
            "UNION ALL " +
            "(SELECT 'PART' AS type, oid, number AS code, name, " +
            "        '/resource?type=PART&oid=' || oid AS link, type_definition_code " +
            "   FROM ck_part WHERE tenant_oid = ? AND (LOWER(name) LIKE ? OR LOWER(number) LIKE ?)) " +
            "UNION ALL " +
            "(SELECT 'DOCUMENT' AS type, oid, number AS code, name, " +
            "        '/resource?type=DOCUMENT&oid=' || oid AS link, type_definition_code " +
            "   FROM ck_document WHERE tenant_oid = ? AND (LOWER(name) LIKE ? OR LOWER(number) LIKE ?)) " +
            "UNION ALL " +
            "(SELECT 'ENG_DOCUMENT' AS type, oid, number AS code, name, " +
            "        '/resource?type=ENG_DOCUMENT&oid=' || oid AS link, type_definition_code " +
            "   FROM ck_eng_document WHERE tenant_oid = ? AND (LOWER(name) LIKE ? OR LOWER(number) LIKE ?)) " +
            "UNION ALL " +
            "(SELECT 'CHANGE_REQUEST' AS type, oid, code, name, " +
            "        '/change?tab=request&requestOid=' || oid AS link, " +
            "        NULL AS type_definition_code " +
            "   FROM ck_change_request WHERE tenant_oid = ? AND (LOWER(name) LIKE ? OR LOWER(code) LIKE ?)) " +
            "UNION ALL " +
            "(SELECT 'CHANGE_NOTICE' AS type, oid, code, name, " +
            "        '/change?tab=notice&noticeOid=' || oid AS link, " +
            "        NULL AS type_definition_code " +
            "   FROM ck_change_notice WHERE tenant_oid = ? AND (LOWER(name) LIKE ? OR LOWER(code) LIKE ?)) " +
            "ORDER BY type, code LIMIT ?";

        // 每个子查询 3 个参数：tenant_oid + 关键字（name / code 各一次）
        Object[] args = new Object[] {
            tenantOid, q, q,   // product_line
            tenantOid, q, q,   // product_model
            tenantOid, q, q,   // part
            tenantOid, q, q,   // document
            tenantOid, q, q,   // eng_document
            tenantOid, q, q,   // change_request
            tenantOid, q, q,   // change_notice
            max
        };

        return jdbcTemplate.query(sql, (rs, i) -> new SearchResultVO(
                rs.getString("type"),
                rs.getString("oid"),
                rs.getString("code"),
                rs.getString("name"),
                rs.getString("link"),
                rs.getString("type_definition_code")
        ), args);
    }
}
