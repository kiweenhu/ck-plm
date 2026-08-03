package cn.ck.plm.base.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * 数据库迁移：为 IBA 相关三张表添加 tenant_oid 列，支持多租户隔离。
 *
 * <p>迁移内容：
 * <ul>
 *   <li>ck_iba: 添加 tenant_oid 列，删除 code 单列唯一约束，改为 (code, tenant_oid) 联合唯一</li>
 *   <li>ck_type_iba: 添加 tenant_oid 列</li>
 *   <li>ck_type_iba_data: 添加 tenant_oid 列</li>
 * </ul>
 *
 * <p>迁移完成后，此组件可安全保留（幂等操作）。
 */
@Component
@Order(2)
public class IbaTenantColumnMigration implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(IbaTenantColumnMigration.class);

    private final JdbcTemplate jdbc;

    public IbaTenantColumnMigration(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void run(ApplicationArguments args) {
        migrateCkIba();
        migrateCkTypeIba();
        migrateCkTypeIbaData();
    }

    private void migrateCkIba() {
        try {
            // 1. 添加 tenant_oid 列
            Integer count = columnExists("ck_iba", "tenant_oid");
            if (count == null || count == 0) {
                jdbc.execute("ALTER TABLE ck_iba ADD COLUMN tenant_oid CHAR(36)");
                log.info("ck_iba: tenant_oid 列已添加");
            } else {
                log.debug("ck_iba: tenant_oid 列已存在，跳过");
            }

            // 2. 删除旧的 code UNIQUE 约束（如果存在）
            try {
                jdbc.execute("ALTER TABLE ck_iba DROP CONSTRAINT IF EXISTS ck_iba_code_key");
                log.info("ck_iba: 旧唯一约束 ck_iba_code_key 已删除（如有）");
            } catch (Exception e) {
                log.debug("ck_iba: 删除旧唯一约束跳过: {}", e.getMessage());
            }

            // 3. 添加 (code, tenant_oid) 联合唯一约束
            try {
                jdbc.execute("ALTER TABLE ck_iba DROP CONSTRAINT IF EXISTS ck_iba_code_tenant_oid_key");
            } catch (Exception e) {
                log.debug("ck_iba: 删除已有联合约束跳过");
            }
            jdbc.execute("ALTER TABLE ck_iba ADD CONSTRAINT ck_iba_code_tenant_oid_key UNIQUE (code, tenant_oid)");
            log.info("ck_iba: (code, tenant_oid) 联合唯一约束已添加");

        } catch (Exception e) {
            log.warn("ck_iba 迁移失败: {}", e.getMessage());
        }
    }

    private void migrateCkTypeIba() {
        try {
            Integer count = columnExists("ck_type_iba", "tenant_oid");
            if (count == null || count == 0) {
                jdbc.execute("ALTER TABLE ck_type_iba ADD COLUMN tenant_oid CHAR(36)");
                log.info("ck_type_iba: tenant_oid 列已添加");
            } else {
                log.debug("ck_type_iba: tenant_oid 列已存在，跳过");
            }
        } catch (Exception e) {
            log.warn("ck_type_iba 迁移失败: {}", e.getMessage());
        }
    }

    private void migrateCkTypeIbaData() {
        try {
            Integer count = columnExists("ck_type_iba_data", "tenant_oid");
            if (count == null || count == 0) {
                jdbc.execute("ALTER TABLE ck_type_iba_data ADD COLUMN tenant_oid CHAR(36)");
                log.info("ck_type_iba_data: tenant_oid 列已添加");
            } else {
                log.debug("ck_type_iba_data: tenant_oid 列已存在，跳过");
            }
        } catch (Exception e) {
            log.warn("ck_type_iba_data 迁移失败: {}", e.getMessage());
        }
    }

    private Integer columnExists(String tableName, String columnName) {
        try {
            return jdbc.queryForObject(
                    "SELECT COUNT(*) FROM information_schema.columns " +
                    "WHERE table_name = ? AND column_name = ? AND table_schema = 'public'",
                    Integer.class, tableName, columnName);
        } catch (Exception e) {
            return null;
        }
    }
}
