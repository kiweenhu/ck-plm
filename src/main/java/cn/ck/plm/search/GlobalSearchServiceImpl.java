/*
 * Copyright (c) 2025 深圳乘恺科技有限公司
 * All rights reserved.
 */

package cn.ck.plm.search;

import cn.ck.plm.search.dto.SearchResultVO;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * 全局搜索服务实现 —— 使用 JDBC 跨表 UNION ALL + LIKE 模糊匹配。
 *
 * <p>不依赖额外 mapper，通过原生 SQL 实现，便于一处集中管理四张表的搜索逻辑。
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
        int max = Math.max(1, Math.min(limit, 100));

        // 跨 4 张表 UNION ALL，每个子查询对 name 和 code/number 做 LIKE 匹配。
        // 末尾带具体类型编码（type_definition_code）：调用方（如"发起流程"多选对象）要按类型过滤，
        // 只看 type（哪张表）不够 —— 同是 ck_part 的零件也分 ELECTRONIC / STRUCTURAL。
        // 产品系列·型号没有类型概念，给 NULL（列数必须一致）
        String sql =
            "(SELECT 'PRODUCT_LINE' AS type, oid, code, name, " +
            "        '/resource?type=PRODUCT_LINE&oid=' || oid AS link, " +
            "        NULL AS type_definition_code " +
            "   FROM ck_product_line WHERE LOWER(name) LIKE ? OR LOWER(code) LIKE ?) " +
            "UNION ALL " +
            "(SELECT 'PRODUCT_MODEL' AS type, oid, code, name, " +
            "        '/resource?type=PRODUCT_MODEL&oid=' || oid AS link, " +
            "        NULL AS type_definition_code " +
            "   FROM ck_product_model WHERE LOWER(name) LIKE ? OR LOWER(code) LIKE ?) " +
            "UNION ALL " +
            "(SELECT 'PART' AS type, oid, number AS code, name, " +
            "        '/resource?type=PART&oid=' || oid AS link, type_definition_code " +
            "   FROM ck_part WHERE LOWER(name) LIKE ? OR LOWER(number) LIKE ?) " +
            "UNION ALL " +
            "(SELECT 'DOCUMENT' AS type, oid, number AS code, name, " +
            "        '/resource?type=DOCUMENT&oid=' || oid AS link, type_definition_code " +
            "   FROM ck_document WHERE LOWER(name) LIKE ? OR LOWER(number) LIKE ?) " +
            "ORDER BY type, code LIMIT ?";

        Object[] args = new Object[] {
            q, q,   // product_line
            q, q,   // product_model
            q, q,   // part
            q, q,   // document
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