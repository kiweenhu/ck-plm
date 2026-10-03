/*
 * Copyright (c) 2026~2028 深圳乘恺科技有限公司
 * All rights reserved.
 *
 * @author Kiween.Hu; Roney.Liu
 */

package cn.ck.plm.softtype.config;

import cn.ck.plm.base.util.TenantContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.UUID;

/**
 * 业务域（{@code ck_business_domain}）的结构与种子 —— <b>从零安装也能得到正确的域体系</b>。
 *
 * <p>为什么要有这个类：域最初是"借" {@code ck_type_definition} 的 DOMAIN 行表示的，域表是后来
 * 用一段手工 SQL 建出来并回填的 —— 于是<b>新装环境根本没有这张表</b>，类型树/域标签会直接坏掉。
 * 这里把"结构 + 种子"补进启动流程，幂等、可重复执行（第一次装、每天启动都安全）。
 *
 * <p>三件事：
 * <ol>
 *   <li>建表 + 唯一索引 + 给 {@code ck_type_definition} 补 {@code domain_oid} 列与索引；</li>
 *   <li>预置 6 个平台级业务域（域是平台数据、<b>不允许租户自定义</b>，故 {@code source=OOTB}）；</li>
 *   <li>沿用存量库里同 code 的域锚点 oid —— 已有的 {@code domain_oid} 引用都指向它们，
 *       换 oid 会让全部类型瞬间失去域归属。</li>
 * </ol>
 *
 * <p><b>后续（方案 1 全量清理时）</b>：结构应并入 {@code schema.sql}、域锚点行应彻底退场
 * （域表成为唯一来源，类型侧只按 code 关联），届时本类收缩为"纯种子 upsert"。
 */
@Component
@Order(0) // 必须先于 TypeDefinitionInitializer(@Order=2)：类型注册与域归属都要读业务域
public class BusinessDomainInitializer implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(BusinessDomainInitializer.class);

    // 说明（批 2 重构）：本类原先自带 CREATE TABLE / CREATE INDEX / ALTER 的 DDL —— 那让"结构定义"
    // 又多了一处来源（改表要改 Java 字符串）。现全部结构已并入 src/main/resources/schema.sql
    // （ck_business_domain 建表、ck_type_definition.domain_oid 列与索引都在里面），
    // 本类只负责<b>种子</b>与<b>数据自愈</b>。后续同类初始化器按同一模式清理。

    /**
     * 把域表的 oid 对齐到<b>当前域锚点行</b>的 oid。
     *
     * <p>为什么需要：域锚点行是历史遗留的"域的替身"，它可能在某次启动里被重建（新 oid）——
     * 而类型的 {@code domain_oid} 是跟着锚点走的，域表却还留着旧 oid，两边就此分叉
     * （症状：类型树/列表里只有极少数类型能显示出域标签）。此行把它拉回一致，幂等；
     * 域表是软引用目标，改 oid 不影响任何外键。
     */
    private static final String ALIGN_OID_TO_ANCHOR =
            "UPDATE ck_business_domain d SET oid = a.oid, updated_at = CURRENT_TIMESTAMP "
            + "FROM ck_type_definition a "
            + "WHERE upper(a.type_kind) = 'DOMAIN' AND a.code = d.code AND a.oid <> d.oid";

    /**
     * 自愈：<b>域归属失效的类型，继承父类型的域</b>。
     *
     * <p>域是"从父类型沿下来"的常识（结构件的子类型还在结构域），所以父类型有域而自己没有、
     * 或自己的 {@code domain_oid} 指向一个已不存在的域（历史分叉遗留）时，按父类型补齐。
     * 幂等；必须先跑 {@link #ALIGN_OID_TO_ANCHOR}（让域表成为权威）再跑本句。
     */
    /** 清理历史遗留：域锚点行不再属于类型表（域已由 ck_business_domain 单独承担） */
    private static final String DELETE_LEGACY_DOMAIN_ROWS =
            "DELETE FROM ck_type_definition WHERE upper(type_kind) = 'DOMAIN'";

    /**
     * 修复历史 {@code parent_oid}：过去把"域锚点"当父类型写进去，现按<b>能力宿主</b>（root_type_code）归位。
     *
     * <p>只动"父指向域锚点"的行 —— 其它父子关系（例如标准件/通用件特意挂在结构件下）一律不碰。
     * 必须在 {@link #DELETE_LEGACY_DOMAIN_ROWS} 之前执行（它要读那些行）。
     */
    private static final String REPAIR_PARENT_OID_OFF_DOMAIN =
            "UPDATE ck_type_definition t SET parent_oid = r.oid, updated_at = CURRENT_TIMESTAMP "
            + "FROM ck_type_definition r "
            + "WHERE upper(t.type_kind) <> 'DOMAIN' AND t.root_type_code IS NOT NULL "
            + "AND t.root_type_code <> t.code AND r.code = t.root_type_code "
            + "AND t.parent_oid IN (SELECT oid FROM ck_type_definition WHERE upper(type_kind) = 'DOMAIN')";

    private static final String HEAL_DOMAIN_FROM_PARENT =
            "UPDATE ck_type_definition c SET domain_oid = p.domain_oid, updated_at = CURRENT_TIMESTAMP "
            + "FROM ck_type_definition p "
            + "WHERE p.oid = c.parent_oid AND p.domain_oid IS NOT NULL "
            + "AND upper(c.type_kind) <> 'DOMAIN' "
            + "AND (c.domain_oid IS NULL OR NOT EXISTS "
            + "     (SELECT 1 FROM ck_business_domain d WHERE d.oid = c.domain_oid))";

    /** 存量库里域锚点行的 oid（有就沿用，保证已回填的 domain_oid 不失效） */
    private static final String SELECT_ANCHOR_OID =
            "SELECT oid FROM ck_type_definition WHERE code = ? AND upper(type_kind) = 'DOMAIN' LIMIT 1";

    private static final String UPSERT_DOMAIN =
            "INSERT INTO ck_business_domain (oid, code, name, description, sort_order, enabled, source, "
            + "tenant_oid, creator, created_at, updater, updated_at) "
            + "VALUES (?, ?, ?, ?, ?, TRUE, 'OOTB', ?, 'system', CURRENT_TIMESTAMP, 'system', CURRENT_TIMESTAMP) "
            + "ON CONFLICT (code) DO UPDATE SET name = EXCLUDED.name, description = EXCLUDED.description, "
            + "sort_order = EXCLUDED.sort_order, enabled = TRUE, updated_at = CURRENT_TIMESTAMP";

    /**
     * 平台预置的业务域：{@code code, name, 排序}。
     * code 必须与 {@code TypeDefinitionInitializer.TYPE_DOMAIN} 的取值一致（类型按域归属用同一套 code）。
     */
    private static final String[][] DOMAINS = {
            {"PRODUCT_DATA_DOMAIN", "产品主数据域", "0"},
            {"ARCHITECTURE_DOMAIN", "架构设计域", "1"},
            {"MCAD_DOMAIN", "结构设计域", "2"},
            {"ECAD_DOMAIN", "电子设计域", "3"},
            {"ELECTRICAL_DOMAIN", "电气设计域", "4"},
            {"SOFTWARE_DOMAIN", "软件设计域", "5"},
    };

    private final DataSource dataSource;

    public BusinessDomainInitializer(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @Override
    public void run(String... args) {
        // 结构由 schema.sql 负责（见类头说明）；这里只做"数据自愈"：
        // 1) 域表 oid 与域锚点分叉 → 对齐；2) 类型 domain_oid 缺失/失效 → 继承父类型。
        try (Connection conn = dataSource.getConnection();
             Statement stmt = conn.createStatement()) {
            int aligned = stmt.executeUpdate(ALIGN_OID_TO_ANCHOR);
            if (aligned > 0) {
                log.info("业务域 oid 已对齐到当前域锚点: {} 条（此前域表与类型的 domain_oid 分叉）", aligned);
            }
            int healed = stmt.executeUpdate(HEAL_DOMAIN_FROM_PARENT);
            if (healed > 0) {
                log.info("类型域归属已按父类型补齐: {} 条（原 domain_oid 缺失或指向不存在的域）", healed);
            }
            // 历史 parent_oid 归位（父≠域锚点）→ 再清理类型表里的域锚点行（顺序不能反）
            int reparented = stmt.executeUpdate(REPAIR_PARENT_OID_OFF_DOMAIN);
            if (reparented > 0) {
                log.info("类型 parent_oid 已按能力宿主归位: {} 条（原父级是域锚点）", reparented);
            }
            int removedDomains = stmt.executeUpdate(DELETE_LEGACY_DOMAIN_ROWS);
            if (removedDomains > 0) {
                log.info("已清理类型表中的历史域锚点行: {} 条（域现由 ck_business_domain 承担）", removedDomains);
            }
        } catch (Exception e) {
            log.warn("业务域数据自愈失败（不影响其它初始化）: {}", e.getMessage());
            return;
        }

        int created = 0;
        int updated = 0;
        try (Connection conn = dataSource.getConnection();
             PreparedStatement upsert = conn.prepareStatement(UPSERT_DOMAIN)) {
            String tenant = TenantContext.PLATFORM_TENANT_OID;
            for (String[] domain : DOMAINS) {
                String code = domain[0];
                String anchorOid = selectAnchorOid(conn, code);
                // 没有锚点行（全新安装）就用新 oid：域表成为域的唯一来源
                String oid = anchorOid != null ? anchorOid : UUID.randomUUID().toString();

                upsert.setString(1, oid);
                upsert.setString(2, code);
                upsert.setString(3, domain[1]);
                upsert.setString(4, null);
                upsert.setInt(5, Integer.parseInt(domain[2]));
                upsert.setString(6, tenant);
                boolean isNew = anchorOid == null || !domainExists(conn, code);
                if (upsert.executeUpdate() > 0) {
                    if (isNew) {
                        created++;
                    } else {
                        updated++;
                    }
                }
            }
        } catch (Exception e) {
            log.warn("业务域种子写入失败: {}", e.getMessage());
            return;
        }
        log.info("业务域就绪: ck_business_domain（新增 {} 个，更新 {} 个，共 {} 个平台预置域）",
                created, updated, DOMAINS.length);
    }

    /** 存量库里的域锚点 oid；表不存在或没有锚点行时返回 null（视为全新安装） */
    private String selectAnchorOid(Connection conn, String code) {
        try (PreparedStatement ps = conn.prepareStatement(SELECT_ANCHOR_OID)) {
            ps.setString(1, code);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getString(1) : null;
            }
        } catch (Exception e) {
            return null;
        }
    }

    /** 该 code 的域行是否已存在（只用于日志口径：新增 vs 更新） */
    private boolean domainExists(Connection conn, String code) {
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT 1 FROM ck_business_domain WHERE code = ? LIMIT 1")) {
            ps.setString(1, code);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        } catch (Exception e) {
            return false;
        }
    }
}
