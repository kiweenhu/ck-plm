/*
 * Copyright (c) 2025 深圳乘恺科技有限公司
 * All rights reserved.
 *
 * @author Kiween.Hu; Roney.Liu
 */

package cn.ck.plm.softtype.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;

/**
 * 「类型-生命周期状态-流程模板」关联表结构初始化。
 *
 * <pre>
 * ck_type_lifecycle_state_process_link   关联：type_oid + lifecycle_template_code + status_code → 流程模板
 * </pre>
 *
 * <p><b>为什么主键里必须有类型</b>：同一个生命周期模板（如 STANDARD）会被多个类型复用
 * （{@code ck_type_lifecycle_template_link} 里 STANDARD 挂着 20+ 个类型），
 * 不含类型就退化成"该模板的某状态全局只能用一个流程"，表达不了"每个类型各自指定"。
 *
 * <p><b>表名沿革</b>（本表改过两次名，两次都顺手带走数据）：
 * <ol>
 *   <li>{@code ck_lifecycle_state_process}（首版，主键是"生命周期模板子版本 + 状态"）；</li>
 *   <li>补上类型维度（{@code V020}）——老行是<b>模板级</b>绑定，按"当时绑了该模板的类型"
 *       展开成多行，语义与迁移前一致，不丢用户已配好的绑定；</li>
 *   <li>更名 {@code ck_type_lifecycle_state_process_link}（{@code V021}）+ 归位到类型模块，
 *       与同族 {@code ck_type_lifecycle_template_link} 命名对齐；</li>
 *   <li>配置粒度由"模板 code"改为"模板<b>子版本</b> oid"（{@code V022}）——
 *       与业务对象迭代固化的 {@code lifecycle_template_iteration_oid} 同层，
 *       运行期可按实例自带的版本精确解析；老行落到该模板的最新子版本（见
 *       {@link #migrateFromTemplateCode}）。</li>
 *   <li>唯一键补上<b>租户</b>（{@code V023}）——各租户各配各的，平台租户的行作共享默认
 *       （见 {@link #createIndexes}）。</li>
 * </ol>
 *
 * <p>幂等：改名 / 建表 / 加列 / 迁移 / 建索引全部可重复执行。
 */
@Component
public class TypeLifecycleStateProcessLinkTableInitializer implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(TypeLifecycleStateProcessLinkTableInitializer.class);

    private static final String TABLE = "ck_type_lifecycle_state_process_link";
    /** 更名前的表名 */
    private static final String LEGACY_TABLE = "ck_lifecycle_state_process";

    private final DataSource dataSource;

    public TypeLifecycleStateProcessLinkTableInitializer(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @Override
    public void run(String... args) {
        try (Connection conn = dataSource.getConnection();
             Statement stmt = conn.createStatement()) {
            renameLegacyTable(conn, stmt);
            createTable(stmt);
            migrateFromIterationShape(conn, stmt);
            migrateFromTemplateCode(conn, stmt);
            createIndexes(stmt);
            log.info("类型-生命周期状态-流程模板关联表结构初始化完成");
        } catch (Exception e) {
            log.error("类型-生命周期状态-流程模板关联表结构初始化失败: {}", e.getMessage(), e);
        }
    }

    /**
     * 更名 {@code ck_lifecycle_state_process} → {@code ck_type_lifecycle_state_process_link}。
     *
     * <p>整表改名（而非建新表搬数据）：保留既有行、数据零搬运，也避免"新表建好忘了搬"。
     * 索引名一并归正 —— 改表名不会改索引名，残留的 {@code uk_lsp_*} 会让读者以为还有别的表。
     */
    private void renameLegacyTable(Connection conn, Statement stmt) throws Exception {
        if (relationExists(conn, LEGACY_TABLE) && !relationExists(conn, TABLE)) {
            stmt.executeUpdate("ALTER TABLE " + LEGACY_TABLE + " RENAME TO " + TABLE);
            log.info("关联表已更名: {} → {}", LEGACY_TABLE, TABLE);
        }
        renameIndex(conn, stmt, LEGACY_TABLE + "_pkey", TABLE + "_pkey");
        renameIndex(conn, stmt, "idx_lsp_type", "idx_tlspl_type");
        // 旧的"不含租户"唯一键由 createIndexes 显式替换为含租户的新键
        renameIndex(conn, stmt, "idx_lsp_process", "idx_tlspl_process");
        renameIndex(conn, stmt, "idx_lsp_tenant", "idx_tlspl_tenant");
    }

    private void createTable(Statement stmt) throws Exception {
        stmt.executeUpdate(
                "CREATE TABLE IF NOT EXISTS " + TABLE + " ("
                        + "  oid VARCHAR(64) PRIMARY KEY,"
                        // 类型（与 ck_type_lifecycle_template_link.type_oid 同一口径）
                        + "  type_oid CHAR(36) NOT NULL,"
                        // 生命周期模板【子版本】oid：与业务对象迭代固化的
                        // ck_part_iteration.lifecycle_template_iteration_oid 同层，运行期按版本精确解析
                        + "  lifecycle_template_iteration_oid CHAR(36) NOT NULL,"
                        + "  status_code VARCHAR(50) NOT NULL,"
                        // 跨模块软引用 ck_process_template.oid（process 模块依赖本模块，故不加外键）
                        + "  process_template_oid VARCHAR(64) NOT NULL,"
                        + "  tenant_oid CHAR(36),"
                        + "  creator VARCHAR(128),"
                        + "  created_at TIMESTAMP,"
                        + "  updater VARCHAR(128),"
                        + "  updated_at TIMESTAMP"
                        + ")");
    }

    /**
     * 历史结构（{@code iteration_oid} 版）→ 现结构（类型 + 模板 code 版）。
     *
     * <p>顺序不能颠倒：老列是 {@code NOT NULL}，必须<b>先退役老列</b>（顺带带走它的外键与唯一索引），
     * 之后才能插入"没有 iteration_oid"的新行。
     */
    private void migrateFromIterationShape(Connection conn, Statement stmt) throws Exception {
        stmt.executeUpdate("ALTER TABLE " + TABLE + " ADD COLUMN IF NOT EXISTS type_oid CHAR(36)");
        stmt.executeUpdate("ALTER TABLE " + TABLE
                + " ADD COLUMN IF NOT EXISTS lifecycle_template_code VARCHAR(50)");

        if (columnExists(conn, TABLE, "iteration_oid")) {
            int before = countRows(stmt);
            // 1) 老行补上模板 code（子版本 → master.code）
            stmt.executeUpdate(
                    "UPDATE " + TABLE + " p SET lifecycle_template_code = t.code "
                            + "FROM ck_lifecycle_template_iteration i "
                            + "JOIN ck_lifecycle_template t ON t.oid = i.master_oid "
                            + "WHERE i.oid = p.iteration_oid AND p.lifecycle_template_code IS NULL");
            // 2) 老列退役（NOT NULL 约束、外键、依赖它的唯一索引都随之消失）
            stmt.executeUpdate("ALTER TABLE " + TABLE + " DROP COLUMN iteration_oid");
            stmt.executeUpdate("DROP INDEX IF EXISTS uk_lsp_iteration_status");
            // 3) 模板级老行 → 按"绑了该模板的类型"展开（一行变多行，语义与迁移前一致）。
            //    租户取【类型关联行】的租户：老行是模板级配置，本身没有租户语义，
            //    照抄老行会得到"配了却谁都不显示"的隐身行（这是当时的迁移口径；
            //    此后新建的配置一律用【当前用户的租户】，见 DefaultTypeLifecycleStateProcessService#bind）。
            int expanded = stmt.executeUpdate(
                    "INSERT INTO " + TABLE + " (oid, type_oid, lifecycle_template_code, status_code, "
                            + "process_template_oid, tenant_oid, creator, created_at, updater, updated_at) "
                            + "SELECT gen_random_uuid()::text, l.type_oid, p.lifecycle_template_code, "
                            + "       p.status_code, p.process_template_oid, l.tenant_oid, "
                            + "       p.creator, p.created_at, p.updater, p.updated_at "
                            + "FROM " + TABLE + " p "
                            + "JOIN ck_type_lifecycle_template_link l "
                            + "  ON l.lifecycle_template_code = p.lifecycle_template_code "
                            + "WHERE p.type_oid IS NULL AND p.lifecycle_template_code IS NOT NULL");
            // 4) 展开完成：丢弃无类型归属的老行（该模板已无任何类型使用）
            int dropped = stmt.executeUpdate("DELETE FROM " + TABLE + " WHERE type_oid IS NULL");
            log.info("状态→流程关联表已迁移到「类型 + 模板 code」结构: 迁移前 {} 条，"
                            + "按类型展开 {} 条，丢弃无类型归属 {} 条",
                    before, expanded, dropped);
        }
    }

    /**
     * 配置粒度：模板 <b>code</b> → 模板<b>子版本 oid</b>。
     *
     * <p>为什么改：code 不带版本，与业务对象迭代固化的 {@code lifecycle_template_iteration_oid}
     * 不同层 —— 运行期拿着实例自带的子版本 oid，无法还原"当时那一版配置"。
     *
     * <p>迁移口径：老行按 code 落到"该模板的<b>最新子版本</b>"
     * （老语义本就是"按模板当前版本生效"，这是唯一无损的对应关系）；
     * 模板已被删除、解析不到子版本的行丢弃（挂在哪里都无意义）。
     *
     * <p>新列是 {@code NOT NULL}，故顺序为：加列 → 回填 → 丢弃解析不到的行 → 退役老列。
     */
    private void migrateFromTemplateCode(Connection conn, Statement stmt) throws Exception {
        stmt.executeUpdate("ALTER TABLE " + TABLE
                + " ADD COLUMN IF NOT EXISTS lifecycle_template_iteration_oid CHAR(36)");
        if (!columnExists(conn, TABLE, "lifecycle_template_code")) {
            return;
        }
        int migrated = stmt.executeUpdate(
                "UPDATE " + TABLE + " SET lifecycle_template_iteration_oid = i.oid "
                        + "FROM ck_lifecycle_template_iteration i "
                        + "JOIN ck_lifecycle_template m ON m.oid = i.master_oid "
                        + "WHERE m.code = " + TABLE + ".lifecycle_template_code "
                        + "  AND i.latest = TRUE "
                        + "  AND " + TABLE + ".lifecycle_template_iteration_oid IS NULL");
        int dropped = stmt.executeUpdate(
                "DELETE FROM " + TABLE + " WHERE lifecycle_template_iteration_oid IS NULL");
        stmt.executeUpdate("ALTER TABLE " + TABLE + " DROP COLUMN lifecycle_template_code");
        stmt.executeUpdate("DROP INDEX IF EXISTS uk_tlspl_type_template_status");
        log.info("状态→流程关联表已从「模板 code」迁移到「子版本 oid」: 迁移 {} 条，"
                + "模板已不存在而丢弃 {} 条", migrated, dropped);
    }

    private void createIndexes(Statement stmt) throws Exception {
        // 1:1：一个【租户】在一个类型的一个子版本下，一个状态至多一个流程模板。
        // 唯一键含 tenant_oid：各租户各配各的（平台租户的行作共享默认），互不覆盖。
        // 历史唯一定义都"不含租户"，名字换过三次，一并清掉：
        stmt.executeUpdate("DROP INDEX IF EXISTS uk_lsp_type_template_status");
        stmt.executeUpdate("DROP INDEX IF EXISTS uk_tlspl_type_template_status");
        stmt.executeUpdate("DROP INDEX IF EXISTS uk_tlspl_type_iteration_status");
        stmt.executeUpdate("CREATE UNIQUE INDEX IF NOT EXISTS uk_tlspl_tenant_type_iteration_status "
                + "ON " + TABLE + " (tenant_oid, type_oid, lifecycle_template_iteration_oid, status_code)");
        stmt.executeUpdate("CREATE INDEX IF NOT EXISTS idx_tlspl_type ON " + TABLE + " (type_oid)");
        // 删除流程模板前的引用检查走这一列
        stmt.executeUpdate("CREATE INDEX IF NOT EXISTS idx_tlspl_process "
                + "ON " + TABLE + " (process_template_oid)");
        stmt.executeUpdate("CREATE INDEX IF NOT EXISTS idx_tlspl_tenant ON " + TABLE + " (tenant_oid)");
    }

    // ==================== 元数据工具 ====================

    /** 表/索引等 relation 是否存在（不存在时 to_regclass 返回 null） */
    private static boolean relationExists(Connection conn, String name) throws Exception {
        try (PreparedStatement ps = conn.prepareStatement("SELECT to_regclass(?)")) {
            ps.setString(1, name);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() && rs.getString(1) != null;
            }
        }
    }

    private void renameIndex(Connection conn, Statement stmt, String from, String to) throws Exception {
        if (relationExists(conn, from) && !relationExists(conn, to)) {
            stmt.executeUpdate("ALTER INDEX " + from + " RENAME TO " + to);
        }
    }

    private static boolean columnExists(Connection conn, String table, String column) throws Exception {
        try (ResultSet rs = conn.getMetaData().getColumns(null, null, table, column)) {
            return rs.next();
        }
    }

    private static int countRows(Statement stmt) throws Exception {
        try (ResultSet rs = stmt.executeQuery("SELECT COUNT(*) FROM " + TABLE)) {
            return rs.next() ? rs.getInt(1) : 0;
        }
    }
}
