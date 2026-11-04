/*
 * Copyright (c) 2026~2028 深圳乘恺科技有限公司
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
            // CAD 转换器对接配置：单行表、整个部署共享一套（转换器不属于任何租户）。
            // 不加进来会被租户条件过滤掉，页面永远读不到那条配置
            "ck_cad_forge_config",
            // 用户（通过 username 全局唯一查询，不受租户隔离限制）
            "ck_user",
            // 注：属性定义 ck_attribute_definition 已按**租户隔离**：2026-10-10 起不在任何共享名单
            //     （走默认业务表，读注入 = 本租户）；2026-10-11 起装包把包内 entityFields 声明
            //     **真正写入本租户**（AttributeInstaller），与 AttributeInitializer 注册的平台那份互不干扰。
            // 注：计量单位 ck_unit 已改为「按租户隔离」（2026-10-10：加 tenant_oid 列 + 唯一键
            //     改为 (name, tenant_oid)），从本名单移到默认业务表类，随 platform-base 包按租户各装一份。
            //     不要再把它加回"完全共享"—— 否则各租户的单位会互相可见。
            // 注：编码段 ck_number_segment 已改为「按租户隔离」（2026-10-11：加 tenant_oid 列 +
            //     外键改复合 (rule_code, tenant_oid) → ck_number(code, tenant_oid)），随编码规则
            //     一起按租户各装一份。不要再把它加回"完全共享"—— 否则各租户的编码段会互相可见。
            // 业务域（类型树/对象的域标签）：平台预置、不允许租户自定义，所有租户共享同一套域。
            // 放在"完全共享"里（而不是 PLATFORM_SHARED）：域行只有平台租户一份，
            // 完全共享即不做任何租户过滤，查询天然可见。
            "ck_business_domain"
    ));

    /** 平台层共享表 —— 查询时注入 tenant_oid IN ('platform_oid', ?)；INSERT 时注入 ? */
    private static final Set<String> PLATFORM_SHARED_TABLES = new HashSet<>(Arrays.asList(
            // ★ 2026-10-10：「模型定义」链的以下 7 张表已改为**按租户隔离**，从本名单移到默认业务表类 ——
            //    ck_type_definition / ck_attribute_definition / ck_type_page_layout /
            //    ck_type_version_rule_link / ck_type_number_rule_link /
            //    ck_type_lifecycle_template_link / ck_type_lifecycle_state_process_link
            //    理由：类型定义与它的属性、布局、规则绑定是一组，随 platform-base 包按租户各装一份
            //    （各包 SOFT_TYPE 与 platform-base ATTRIBUTE 的 reusable 已改 false）；
            //    只隔离主表会留半隔离（类型是租户的、它的属性/布局还是平台的）。
            //    其中 ck_type_definition / ck_type_page_layout 的 Mapper SQL 也同步去掉了
            //    "IN (#{tenantOid}, #{platformOid})" 分支（否则拦截器见 SQL 已含 tenant_oid 会跳过注入）。
            //    不要再把这 7 张加回本名单。
            // ★ 2026-10-11：生命周期（状态/模板/迭代/状态关联/流转关联 5 张）与编码/版本规则、
            //    编码段已改为**按租户隔离**，从本名单移到默认业务表类 —— 随 platform-base /
            //    electronics-core 包按租户各装一份（两包相应资源的 reusable 已改 false）。
            //    理由：这些字典会被租户**绑定并修改**（类型绑 code、编码规则按租户自增序号），
            //    "平台一份全员共享"意味着任一租户改状态词/规则会波及所有租户。
            //    对应迁移见 CHANGELOG（唯一键改 (code, tenant_oid)；4 条 update 的 WHERE 补租户条件；
            //    ResourceLibrary/PackageSymbol 两处 JOIN/子查询补租户条件 —— 否则双份后行膨胀）。
            //    不要再把这 8 张加回本名单。
            // 业务域（类型树第一层）：平台预置、不允许租户自定义 → 查询侧注入"平台租户 or 当前租户"，
            // 否则域行（tenant_oid=平台租户）会被当成业务表按当前租户过滤掉，类型树第一层会凭空消失
            "ck_business_domain",
            // 布局：分类页面布局（★ ck_type_page_layout 已移出，见上；★ 它是本名单最后一个成员，勿在末尾留逗号）
            "ck_cls_page_layout"
            // ★ 2026-10-11（第二批）：视图（ck_view / ck_view_transition）已改为**按租户隔离**，从本名单移出 ——
            //    随 platform-base 各装一份（reusable 改 false）；运行期按 code 取视图（如 PartServiceImpl.defaultView
            //    找 "Design"）必须命中本租户那份；code 唯一键改 (code, tenant_oid)，transition 的 from/to 外键改复合。
            //    不要再把这 2 张加回本名单。
            // ★ 2026-10-11（第二批·角色）：角色与角色成员已改为**按租户隔离**，从本名单移出 ——
            //    系统角色（TENANT_ADMIN / CATEGORY_ADMIN / AUDIT_ADMIN / SECURITY_ADMIN）与业务角色
            //    （随 electronics-core / smart-hardware-ipd 的 roles.json）各租户一份；PLATFORM_ADMIN 仅平台。
            //    成员行归属 = 用户所在租户（RoleServiceImpl / UserServiceImpl 已改）；
            //    新租户的系统角色副本由 TenantServiceImpl.approve 的 ensureSystemRoles 补齐；
            //    迁移已把现有成员绑定改绑到本租户副本（否则登录后的角色解析会查不到）。
            //    不要再把这 2 张加回本名单。
            // ★ 2026-10-10：流程表单模板 ck_process_form_template 已改为**按租户隔离**，从本名单移出 ——
            //   内置 3 张（设置流程参与者 / 审批意见 / 会签）是引擎与前端的渲染契约，运行期按 code 派发，
            //   故必须"每个租户都有一份"：现已随 platform-base 包的 forms.json 播种
            //   （IndustryPackBootstrap 启动时装到平台租户；租户引导装包时各得一份；见 FormInstaller）。
            //   不要再把它加回本名单 —— 否则租户看不到自己那份，也就无法按需停用。
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
    /**
     * 租户条件要插在「这些子句」之前 —— 否则条件会落到子句后面变成语法错误。
     *
     * <p>{@code RETURNING} 必须在内：PostgreSQL 的 {@code UPDATE … WHERE … RETURNING col}
     * 里 RETURNING 是收尾子句，早前清单漏了它，于是拼出
     * {@code … WHERE code = ? RETURNING sequence_value AND tenant_oid = '…'} ——
     * "AND 的参数必需是类型 boolean，而不是类型 bigint"（版本规则自增序号那条语句踩过）。
     *
     * <p>用词边界匹配，避免列名/字面量里出现 limit、union 等词时被误当成子句起点。
     */
    private static final Pattern CLAUSE_BOUNDARY = Pattern.compile(
            "\\b(RETURNING|ORDER\\s+BY|GROUP\\s+BY|LIMIT|OFFSET|FOR\\s+UPDATE|UNION|HAVING)\\b",
            Pattern.CASE_INSENSITIVE
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

        String rewrittenSql = rewrite(originalSql, commandType, tenantOid);

        if (rewrittenSql != null && !rewrittenSql.equals(originalSql)) {
            metaObject.setValue("delegate.boundSql.sql", rewrittenSql);
            if (log.isDebugEnabled()) {
                log.debug("Tenant SQL [{}]: {} → {}", commandType,
                        truncate(originalSql, 200), truncate(rewrittenSql, 200));
            }
        }

        return invocation.proceed();
    }

    /**
     * 纯函数：按表分类与命令类型给 SQL 注入租户条件。
     *
     * <p>包内可见：{@code intercept} 只负责"从 MyBatis 取 SQL / 把改写结果写回"，
     * 规则本身是"字符串进、字符串出"，单测直接打这里（不必起 MyBatis、也不必连库）。
     *
     * @return 改写后的 SQL；返回原样 = 不改写（共享表 / 已含 tenant_oid 条件 / 不认识的命令）
     */
    String rewrite(String sql, SqlCommandType commandType, String tenantOid) {
        // 已有 tenant_oid 条件，不重复注入
        if (TENANT_FILTER.matcher(sql).find()) {
            return sql;
        }
        // 判断 SQL 涉及的表类型
        TableCategory category = classifyTableCategory(sql);
        if (category == TableCategory.SHARED) {
            return sql;
        }
        // 主表别名只认**最外层** FROM（子查询里的 FROM 不算，见 extractMainAlias）
        String alias = extractMainAlias(sql);
        return category == TableCategory.PLATFORM_SHARED
                ? rewriteForPlatformShared(sql, commandType, tenantOid, alias)
                : rewriteForBusiness(sql, commandType, tenantOid, alias);
    }

    /**
     * 从**最外层** FROM 子句中提取主表别名（如 "FROM ck_user u" → "u"），没有别名返回 null。
     *
     * <p>为什么必须跳过括号里的 FROM：SELECT 列表里的标量子查询会先于主表出现，例如
     * {@code COALESCE((SELECT pm.name FROM ck_product_model pm WHERE …), (SELECT pl.name …))} ——
     * 取到的是子查询别名 {@code pm}。把它拼到外层 WHERE 上就是
     * {@code WHERE oid = ? AND pm.tenant_oid = '…'}，而外层 FROM 里根本没有 pm，
     * PostgreSQL 报"对表 pm 丢失 FROM 子句项"（2026-10-25 变更请求详情
     * {@code GET /api/plm/change-requests/{oid}} 的 500 正是这个原因）。
     */
    private String extractMainAlias(String sql) {
        int[] depths = parenDepths(sql);
        Matcher m = MAIN_TABLE_ALIAS.matcher(sql);
        while (m.find()) {
            if (depths[m.start()] == 0) {
                return m.group(1);
            }
        }
        return null;
    }

    /**
     * 每个字符位置所处的括号深度（下标 i = 处理第 i 个字符**之前**已打开几层括号）。
     * 单引号字面量里的括号不计数 —— 字符串里的 {@code '('} 不该改变层级判断。
     */
    private static int[] parenDepths(String sql) {
        int[] depths = new int[sql.length() + 1];
        int depth = 0;
        boolean inQuote = false;
        for (int i = 0; i < sql.length(); i++) {
            depths[i] = depth;
            char c = sql.charAt(i);
            if (c == '\'') {
                inQuote = !inQuote;
            } else if (!inQuote && c == '(') {
                depth++;
            } else if (!inQuote && c == ')' && depth > 0) {
                depth--;
            }
        }
        depths[sql.length()] = depth;
        return depths;
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
        int insertPos = clauseStart(sql);
        return sql.substring(0, insertPos).trim()
                + (WHERE_PATTERN.matcher(sql).find() ? " AND " : " WHERE ") + tenantCondition
                + (insertPos < sql.length() ? " " + sql.substring(insertPos).trim() : "");
    }

    /**
     * 收尾子句（RETURNING / ORDER BY / GROUP BY / LIMIT …）的起点；没有则返回 SQL 末尾。
     *
     * <p>同样只认最外层：子查询里的 ORDER BY / LIMIT 不是本语句的收尾子句 ——
     * 把租户条件插进去，条件就落到了子查询内部（轻则漏过滤，重则语法错误）。
     */
    private int clauseStart(String sql) {
        int[] depths = parenDepths(sql);
        Matcher m = CLAUSE_BOUNDARY.matcher(sql);
        while (m.find()) {
            if (depths[m.start()] == 0) {
                return m.start();
            }
        }
        return sql.length();
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
