/*
 * Copyright (c) 2025 深圳乘恺科技有限公司
 * All rights reserved.
 *
 * @author Kiween.Hu; Roney.Liu
 */

package cn.ck.plm.home.mapper.impl.postgresql;

import cn.ck.plm.home.dto.RecentObjectVO;
import cn.ck.plm.home.mapper.RecentObjectMapper;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

/**
 * {@link RecentObjectMapper} 的 PostgreSQL 实现。
 *
 * <h3>为什么这里用 JdbcTemplate 而不是 MyBatis 注解</h3>
 * <p>本查询要 JOIN 主表与版本表，而租户拦截器会改写 JOIN 语句（本项目里已有先例：
 * {@code TenantController} 同样为此改用 JdbcTemplate）。用 JdbcTemplate 直连，
 * 由本类<b>显式</b>带上 {@code tenant_oid} 条件 —— 绕开改写的代价是"租户过滤责任在自己"，
 * 所以下面每个 SQL 的 WHERE 都必须有 {@code m.tenant_oid = ?}，改动时别漏。
 *
 * <h3>为什么查版本表（{@code ck_*_iteration}）而不是主表</h3>
 * <p>编辑与检入只更新版本行的 {@code updated_at/updater}，主表的 {@code updated_at}
 * 停在创建那一刻 —— 只查主表会把"改过的对象"全漏掉。展示用的名字/编码再回主表取。
 */
@Repository
@ConditionalOnProperty(name = "plm.database.type", havingValue = "postgresql", matchIfMissing = true)
public class PostgreSqlRecentObjectMapper implements RecentObjectMapper {

    private final JdbcTemplate jdbcTemplate;

    public PostgreSqlRecentObjectMapper(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /**
     * 单个类型的 SQL 模板。
     *
     * <p>{@code DISTINCT ON (m.oid)} + 内层排序 = 同一对象有多个版本时只留"最近动过的那一版"，
     * 因为用户问的是"哪些对象"，不是"哪些版本"。
     */
    private static final String SQL_TEMPLATE =
            "SELECT * FROM (" +
            "  SELECT DISTINCT ON (m.oid) " +
            "         m.oid AS oid, m.name AS name, m.number AS code, " +
            "         i.revision AS revision, i.iteration AS iteration, " +
            "         i.creator AS creator, i.created_at AS created_at, " +
            "         i.updater AS updater, i.updated_at AS updated_at, " +
            "         GREATEST(i.created_at, COALESCE(i.updated_at, i.created_at)) AS touched_at " +
            "    FROM %s m JOIN %s i ON i.master_oid = m.oid " +
            "   WHERE m.tenant_oid = ? " +
            "     AND ( (i.creator = ? AND i.created_at >= ?) " +
            "        OR (i.updater = ? AND i.updated_at >= ?) ) " +
            "   ORDER BY m.oid, GREATEST(i.created_at, COALESCE(i.updated_at, i.created_at)) DESC " +
            ") t ORDER BY t.touched_at DESC LIMIT ?";

    @Override
    public List<RecentObjectVO> selectRecentlyTouched(String entityType, String entityTypeName,
                                                      String masterTable, String iterTable, String linkPath,
                                                      String tenantOid, String user,
                                                      LocalDateTime since, int limit) {
        if (masterTable == null || iterTable == null || tenantOid == null || user == null) {
            return List.of();
        }
        String sql = String.format(SQL_TEMPLATE, masterTable, iterTable);
        RowMapper<RecentObjectVO> rowMapper = (rs, rowNum) -> {
            RecentObjectVO vo = new RecentObjectVO();
            vo.setOid(rs.getString("oid"));
            vo.setName(rs.getString("name"));
            vo.setCode(rs.getString("code"));
            vo.setEntityType(entityType);
            vo.setEntityTypeName(entityTypeName);
            // revision 是版本字符串（如 "A"），iteration 是序号（如 1）—— 不要把它们都当数字
            String revision = rs.getString("revision");
            Integer iteration = (Integer) rs.getObject("iteration");
            vo.setDisplayVersion(revision == null ? null
                    : revision + "." + (iteration == null ? 0 : iteration));
            vo.setCreatedAt(rs.getObject("created_at", LocalDateTime.class));
            vo.setUpdatedAt(rs.getObject("updated_at", LocalDateTime.class));
            vo.setTouchedAt(rs.getObject("touched_at", LocalDateTime.class));
            vo.setLinkPath(linkPath);
            return vo;
        };
        return jdbcTemplate.query(sql, rowMapper,
                tenantOid, user, since, user, since, limit);
    }
}
