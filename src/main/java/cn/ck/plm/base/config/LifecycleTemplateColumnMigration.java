package cn.ck.plm.base.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * 一次性数据库迁移：将 ck_lifecycle_template_state 和 ck_lifecycle_template_transition
 * 中的 template_oid 列重命名为 iteration_oid，并将外键指向 iteration 表。
 *
 * <p>迁移后，schema.sql 中的 CREATE TABLE 已使用新列名，此组件可安全删除。
 */
@Component
@Order(1)
public class LifecycleTemplateColumnMigration implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(LifecycleTemplateColumnMigration.class);

    private final JdbcTemplate jdbc;

    public LifecycleTemplateColumnMigration(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void run(ApplicationArguments args) {
        migrateTable("ck_lifecycle_template_state", "idx_ltstate_iter");
        migrateTable("ck_lifecycle_template_transition", "idx_lttrans_iter");
        dropStatusOrderColumn();
    }

    /** 删除 ck_lifecycle_status 表中已废弃的 status_order 列 */
    private void dropStatusOrderColumn() {
        try {
            Integer count = jdbc.queryForObject(
                    "SELECT COUNT(*) FROM information_schema.columns " +
                    "WHERE table_name = 'ck_lifecycle_status' AND column_name = 'status_order' AND table_schema = 'public'",
                    Integer.class);
            if (count != null && count > 0) {
                log.info("迁移 ck_lifecycle_status: 删除废弃列 status_order");
                jdbc.execute("ALTER TABLE ck_lifecycle_status DROP COLUMN status_order");
                log.info("迁移 ck_lifecycle_status: status_order 列已删除");
            }
        } catch (Exception e) {
            log.warn("删除 status_order 列失败: {}（可能已删除或为新安装）", e.getMessage());
        }
    }

    private void migrateTable(String tableName, String indexName) {
        try {
            Integer count = jdbc.queryForObject(
                    "SELECT COUNT(*) FROM information_schema.columns " +
                    "WHERE table_name = ? AND column_name = ? AND table_schema = 'public'",
                    Integer.class, tableName, "template_oid");

            if (count != null && count > 0) {
                log.info("迁移 {}: template_oid → iteration_oid", tableName);

                // 删除旧外键和索引
                jdbc.execute("ALTER TABLE " + tableName + " DROP CONSTRAINT IF EXISTS " +
                        tableName + "_template_oid_fkey");
                jdbc.execute("DROP INDEX IF EXISTS idx_ltstate_tmpl");
                jdbc.execute("DROP INDEX IF EXISTS idx_lttrans_tmpl");

                // 重命名列
                jdbc.execute("ALTER TABLE " + tableName +
                        " RENAME COLUMN template_oid TO iteration_oid");

                // 添加新外键
                jdbc.execute("ALTER TABLE " + tableName +
                        " ADD FOREIGN KEY (iteration_oid) " +
                        "REFERENCES ck_lifecycle_template_iteration(oid) ON DELETE CASCADE");

                log.info("迁移 {}: 列重命名完成", tableName);
            }

            // 确保索引存在（迁移后或新安装都需要）
            try {
                jdbc.execute("CREATE INDEX IF NOT EXISTS " + indexName +
                        " ON " + tableName + "(iteration_oid)");
            } catch (Exception e) {
                log.debug("索引 {} 已存在或创建失败: {}", indexName, e.getMessage());
            }
        } catch (Exception e) {
            log.warn("迁移 {} 失败: {}（可能已迁移或为新安装）", tableName, e.getMessage());
        }
    }
}
