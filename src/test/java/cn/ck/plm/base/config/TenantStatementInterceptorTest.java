/*
 * Copyright (c) 2026~2028 深圳乘恺科技有限公司
 * All rights reserved.
 *
 * @author Kiween.Hu; Roney.Liu
 */

package cn.ck.plm.base.config;

import org.apache.ibatis.mapping.SqlCommandType;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 租户拦截器改写规则 —— 这些断言就是"注入的 tenant_oid 条件必须落在**本语句**能看见的表上"这条承诺的凭据。
 *
 * <p>不连库、不起 MyBatis：{@code rewrite} 是"字符串进、字符串出"的纯函数，这里只校验拼出来的 SQL。
 * 关键回归用例是 {@link #SELECT列表里的子查询不算主表别名()} —— 2026-10-25 变更请求详情 500
 * （`对表 pm 丢失 FROM 子句项`）就是别名取到了子查询的 `pm`。
 */
class TenantStatementInterceptorTest {

    private static final String TENANT = "ten-1";
    private static final String PLATFORM = "00000000-0000-0000-0000-000000000000";

    private final TenantStatementInterceptor interceptor = new TenantStatementInterceptor();

    @Test
    void SELECT列表里的子查询不算主表别名() {
        // 真实语句形态（PostgreSqlChangeRequestMapper.SELECT_COLUMNS）
        String sql = "SELECT oid, code, COALESCE("
                + "(SELECT pm.name FROM ck_product_model pm WHERE pm.oid = ck_change_request.product_oid), "
                + "(SELECT pl.name FROM ck_product_line  pl WHERE pl.oid = ck_change_request.product_oid)"
                + ") AS product_name FROM ck_change_request WHERE oid = ?";

        String out = interceptor.rewrite(sql, SqlCommandType.SELECT, TENANT);

        // 条件必须加在外层，且不能带上子查询的别名（pm / pl 在外层不存在）
        assertThat(out).endsWith("WHERE oid = ? AND tenant_oid = '" + TENANT + "'");
        assertThat(out).doesNotContain("pm.tenant_oid").doesNotContain("pl.tenant_oid");
    }

    @Test
    void 主表有别名时按别名限定() {
        String sql = "SELECT d.oid FROM ck_document d LEFT JOIN ck_file f ON f.oid = d.ckfile_oid WHERE d.oid = ?";

        String out = interceptor.rewrite(sql, SqlCommandType.SELECT, TENANT);

        assertThat(out).endsWith("AND d.tenant_oid = '" + TENANT + "'");
    }

    @Test
    void 子查询里的ORDER_BY与LIMIT不是收尾子句() {
        String sql = "SELECT p.oid, (SELECT d.name FROM ck_lifecycle_status d WHERE d.code = p.status "
                + "ORDER BY d.name LIMIT 1) AS status_name FROM ck_part p WHERE p.oid = ?";

        String out = interceptor.rewrite(sql, SqlCommandType.SELECT, TENANT);

        // 条件要落在最外层 WHERE 末尾，而不是插进子查询的 ORDER BY / LIMIT 之前
        assertThat(out).endsWith("WHERE p.oid = ? AND p.tenant_oid = '" + TENANT + "'");
    }

    @Test
    void UPDATE的RETURNING仍是收尾子句() {
        String sql = "UPDATE ck_number SET sequence_value = sequence_value + 1 "
                + "WHERE rule_code = ? RETURNING sequence_value";

        String out = interceptor.rewrite(sql, SqlCommandType.UPDATE, TENANT);

        assertThat(out).contains("WHERE rule_code = ? AND tenant_oid = '" + TENANT + "' RETURNING sequence_value");
    }

    @Test
    void 已含tenant_oid条件的不重复注入() {
        String sql = "SELECT oid FROM ck_deliverable WHERE tenant_oid = ?";

        assertThat(interceptor.rewrite(sql, SqlCommandType.SELECT, TENANT)).isEqualTo(sql);
    }

    @Test
    void 共享表完全不动() {
        String sql = "SELECT username FROM ck_user WHERE username = ?";

        assertThat(interceptor.rewrite(sql, SqlCommandType.SELECT, TENANT)).isEqualTo(sql);
    }

    @Test
    void 平台共享表注入平台或本租户() {
        String sql = "SELECT oid FROM ck_cls_page_layout WHERE cls_oid = ?";

        String out = interceptor.rewrite(sql, SqlCommandType.SELECT, TENANT);

        assertThat(out).endsWith("AND tenant_oid IN ('" + PLATFORM + "', '" + TENANT + "')");
    }

    @Test
    void 平台租户自己查平台共享表只等值匹配() {
        String sql = "SELECT oid FROM ck_cls_page_layout WHERE cls_oid = ?";

        String out = interceptor.rewrite(sql, SqlCommandType.SELECT, PLATFORM);

        assertThat(out).endsWith("AND tenant_oid = '" + PLATFORM + "'");
    }
}
