/*
 * Copyright (c) 2025 深圳乘恺科技有限公司
 * All rights reserved.
 *
 * @author Kiween.Hu; Roney.Liu
 */

package cn.ck.plm.base.config;

import cn.ck.plm.base.util.TenantContext;
import org.apache.ibatis.executor.statement.StatementHandler;
import org.apache.ibatis.mapping.BoundSql;
import org.apache.ibatis.mapping.MappedStatement;
import org.apache.ibatis.mapping.SqlCommandType;
import org.apache.ibatis.plugin.*;
import org.apache.ibatis.reflection.MetaObject;
import org.apache.ibatis.reflection.SystemMetaObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.sql.Connection;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Properties;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static cn.ck.plm.base.util.TenantContext.PLATFORM_TENANT_OID;

/**
 * 多租户 SQL 改写拦截器 —— 自动注入 tenant_oid 过滤条件。
 *
 * <h3>三层数据模型</h3>
 * <pre>
 * 平台层 (tenant_oid = '00000000-...-0000')
 *   └── 系统配置表: 生命周期、IBA、类型定义、编码规则、视图、角色等
 *       查询时自动追加 {@code WHERE tenant_oid IN ('平台oid', '<当前租户oid>')}
 *       所有租户共享平台数据，也可以有租户自定义配置
 *
 * 业务层 (tenant_oid = 具体租户oid)
 *   └── 业务表: 文档、产品线、用户、团队、文件夹等
 *       查询时自动追加 {@code WHERE tenant_oid = '<当前租户oid>'}
 *       严格隔离，租户之间不可见
 *
 * 共享层 (无 tenant_oid 隔离)
 *   └── Token、通知、文件存储配置、ck_tenant 本身
 *       不注入 tenant_oid，所有租户直接共享
 * </pre>
 *
 * <h3>Phase 2/3 扩展</h3>
 * 替换本拦截器实现即可升级到 Schema 隔离或实例隔离。
 */
@Component
@Intercepts({
        @Signature(type = StatementHandler.class, method = "prepare",
                args = {Connection.class, Integer.class})
})
public class TenantStatementInterceptor implements Interceptor {

    private static final Logger log = LoggerFactory.getLogger(TenantStatementInterceptor.class);

    /** 完全共享表 —— 不注入 tenant_oid（系统基础设施，所有租户直接共享） */
    private static final Set<String> SHARED_TABLES = new HashSet<>(Arrays.asList(
            "ck_token", "ck_notification", "ck_file_storage_config", "ck_tenant",
            // 用户（通过 username 全局唯一查询，不受租户隔离限制）
            "ck_user",
            // 属性定义/编码段（无 tenant_oid 列，跨租户共享）
            "ck_attribute_definition", "ck_number_segment",
            // 计量单位（无 tenant_oid 列，跨租户共享）
            "ck_unit"
    ));

    /** 平台层共享表 —— 查询时注入 tenant_oid IN ('platform_oid', ?)；INSERT 时注入 ? */
    private static final Set<String> PLATFORM_SHARED_TABLES = new HashSet<>(Arrays.asList(
            // 类型关联表（有 tenant_oid 列，不同租户可绑定不同规则）
            "ck_type_version_rule_link", "ck_type_number_rule_link",
            "ck_type_lifecycle_template_link",
            // 生命周期
            "ck_lifecycle_status", "ck_lifecycle_template", "ck_lifecycle_template_state",
            "ck_lifecycle_template_transition", "ck_lifecycle_template_iteration",
            // 编码/版本规则
            "ck_number", "ck_version_rule",
            // 类型系统（ck_iba / ck_type_iba 已实现 TenantEntity，按业务表隔离）
            "ck_type_definition",
            // 布局
            "ck_type_page_layout", "ck_cls_page_layout",
            // 视图
            "ck_view", "ck_view_transition",
            // 角色和角色成员（平台角色所有租户可见，租户可自定义业务角色）
            "ck_role", "ck_role_member"
    ));

    private static final Pattern TABLE_PATTERN = Pattern.compile(
            "(FROM|JOIN|INTO|UPDATE)\\s+(ck_\\w+)", Pattern.CASE_INSENSITIVE
    );
    /** 提取 FROM 子句中的主表别名: "FROM ck_user u" → group(1)=u；排除 SQL 关键字 */
    private static final Pattern MAIN_TABLE_ALIAS = Pattern.compile(
            "FROM\\s+ck_\\w+\\s+((?!WHERE|ORDER|GROUP|LIMIT|INNER|LEFT|RIGHT|JOIN|ON|SET|HAVING)\\w+)\\b",
            Pattern.CASE_INSENSITIVE
    );
    private static final Pattern INSERT_COLS = Pattern.compile(
            "(INSERT\\s+INTO\\s+ck_\\w+\\s*)\\(([^)]+)\\)\\s*(VALUES\\s*)\\(([^)]+)\\)",
            Pattern.CASE_INSENSITIVE | Pattern.DOTALL
    );
    private static final Pattern TENANT_FILTER = Pattern.compile(
            "\\btenant_oid\\s*(=|IN|in)\\s*", Pattern.CASE_INSENSITIVE
    );
    /** 检测 INSERT 列中是否已包含 tenant_oid */
    private static final Pattern INSERT_HAS_TENANT_OID = Pattern.compile(
            "INSERT\\s+INTO\\s+ck_\\w+\\s*\\([^)]*\\btenant_oid\\b", Pattern.CASE_INSENSITIVE
    );
    private static final Pattern WHERE_PATTERN = Pattern.compile(
            "\\bWHERE\\b", Pattern.CASE_INSENSITIVE
    );

    @Override
    public Object intercept(Invocation invocation) throws Throwable {
        String tenantOid = TenantContext.getOrNull();
        if (tenantOid == null) {
            return invocation.proceed();
        }

        StatementHandler handler = (StatementHandler) invocation.getTarget();
        MetaObject metaObject;
        MappedStatement ms;
        try {
            metaObject = SystemMetaObject.forObject(handler);
            ms = (MappedStatement) metaObject.getValue("delegate.mappedStatement");
        } catch (Exception e) {
            return invocation.proceed();
        }
        if (ms == null) {
            return invocation.proceed();
        }

        SqlCommandType commandType = ms.getSqlCommandType();
        BoundSql boundSql = handler.getBoundSql();
        String originalSql = boundSql.getSql();

        // 已有 tenant_oid 条件，不重复注入
        if (TENANT_FILTER.matcher(originalSql).find()) {
            return invocation.proceed();
        }

        // 判断 SQL 涉及的表类型
        TableCategory category = classifyTableCategory(originalSql);
        if (category == TableCategory.SHARED) {
            return invocation.proceed();
        }

        // 提取 FROM 子句中的主表别名，用于 tenant_oid 前缀
        String alias = extractMainAlias(originalSql);

        String rewrittenSql;
        if (category == TableCategory.PLATFORM_SHARED) {
            rewrittenSql = rewriteForPlatformShared(originalSql, commandType, tenantOid, alias);
        } else {
            rewrittenSql = rewriteForBusiness(originalSql, commandType, tenantOid, alias);
        }

        if (rewrittenSql != null && !rewrittenSql.equals(originalSql)) {
            metaObject.setValue("delegate.boundSql.sql", rewrittenSql);
            if (log.isDebugEnabled()) {
                log.debug("Tenant SQL [{}]: {} → {}", commandType,
                        truncate(originalSql, 200), truncate(rewrittenSql, 200));
            }
        }

        return invocation.proceed();
    }

    /** 从 FROM 子句中提取主表别名（如 "FROM ck_user u" → "u"），没有别名返回 null */
    private String extractMainAlias(String sql) {
        Matcher m = MAIN_TABLE_ALIAS.matcher(sql);
        return m.find() ? m.group(1) : null;
    }

    private String tenantOidCol(String alias) {
        return alias != null ? alias + ".tenant_oid" : "tenant_oid";
    }

    // ==================== 表分类 ====================

    private enum TableCategory { SHARED, PLATFORM_SHARED, BUSINESS }

    /**
     * 根据 SQL 涉及的表名判断分类。
     * 只要涉及一个业务表 → 按 BUSINESS 处理；
     * 全部是平台共享表 → 按 PLATFORM_SHARED 处理；
     * 全部是完全共享表 → 按 SHARED 处理。
     */
    private TableCategory classifyTableCategory(String sql) {
        Matcher matcher = TABLE_PATTERN.matcher(sql);
        boolean hasTable = false;
        TableCategory result = TableCategory.SHARED;
        while (matcher.find()) {
            hasTable = true;
            String tableName = matcher.group(2).toLowerCase();
            if (SHARED_TABLES.contains(tableName)) {
                continue;
            }
            if (PLATFORM_SHARED_TABLES.contains(tableName)) {
                result = TableCategory.PLATFORM_SHARED;
                continue;
            }
            return TableCategory.BUSINESS;
        }
        return hasTable ? result : TableCategory.SHARED;
    }

    // ==================== 业务表改写 ====================

    private String rewriteForBusiness(String sql, SqlCommandType commandType, String tenantOid, String alias) {
        switch (commandType) {
            case INSERT: return rewriteInsert(sql, tenantOid);
            case SELECT:
            case UPDATE:
            case DELETE: return rewriteWhere(sql, tenantOidCol(alias) + " = '" + tenantOid + "'");
            default: return sql;
        }
    }

    // ==================== 平台共享表改写 ====================

    private String rewriteForPlatformShared(String sql, SqlCommandType commandType, String tenantOid, String alias) {
        switch (commandType) {
            case INSERT:
                return rewriteInsert(sql, tenantOid);
            case SELECT:
            case UPDATE:
            case DELETE:
                String platformOid = TenantContext.PLATFORM_TENANT_OID;
                String col = tenantOidCol(alias);
                String condition = col + " IN ('" + platformOid + "', '" + tenantOid + "')";
                if (platformOid.equals(tenantOid)) {
                    condition = col + " = '" + tenantOid + "'";
                }
                return rewriteWhere(sql, condition);
            default:
                return sql;
        }
    }

    // ==================== INSERT 改写 ====================

    private String rewriteInsert(String sql, String tenantOid) {
        if (INSERT_HAS_TENANT_OID.matcher(sql).find()) {
            return sql;
        }
        Matcher m = INSERT_COLS.matcher(sql);
        if (m.find()) {
            String prefix = m.group(1);
            String columns = m.group(2);
            String valuesKw = m.group(3);
            String values = m.group(4);
            return prefix + "(" + columns + ", tenant_oid) " + valuesKw
                    + "(" + values + ", '" + tenantOid + "')";
        }
        return sql;
    }

    private String rewriteWhere(String sql, String tenantCondition) {
        String[] keywords = {"ORDER BY", "GROUP BY", "LIMIT", "OFFSET", "FOR UPDATE", "UNION", "HAVING"};
        if (WHERE_PATTERN.matcher(sql).find()) {
            int insertPos = sql.length();
            String upperSql = sql.toUpperCase();
            for (String kw : keywords) {
                int pos = upperSql.indexOf(kw.toUpperCase());
                if (pos > 0 && pos < insertPos) insertPos = pos;
            }
            return sql.substring(0, insertPos).trim() + " AND " + tenantCondition
                    + (insertPos < sql.length() ? " " + sql.substring(insertPos).trim() : "");
        } else {
            int insertPos = sql.length();
            String upperSql = sql.toUpperCase();
            for (String kw : keywords) {
                int pos = upperSql.indexOf(kw.toUpperCase());
                if (pos > 0 && pos < insertPos) insertPos = pos;
            }
            return sql.substring(0, insertPos).trim() + " WHERE " + tenantCondition
                    + (insertPos < sql.length() ? " " + sql.substring(insertPos).trim() : "");
        }
    }

    private String truncate(String s, int maxLen) {
        return s.length() <= maxLen ? s : s.substring(0, maxLen) + "...";
    }

    @Override
    public Object plugin(Object target) {
        return Plugin.wrap(target, this);
    }

    @Override
    public void setProperties(Properties properties) {
    }
}
