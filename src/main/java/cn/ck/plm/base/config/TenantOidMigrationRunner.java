/*
 * Copyright (c) 2025 深圳乘恺科技有限公司
 * All rights reserved.
 *
 * @author Kiween.Hu; Roney.Liu
 */

package cn.ck.plm.base.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.List;

/**
 * 数据库迁移：将业务表的隔离列从 tenant_id(VARCHAR) 迁移到 tenant_oid(CHAR(36))。
 * <p>
 * 在 SysAdminInitializer 之前执行（Order=1），确保 tenant_oid 列存在后再初始化业务数据。
 * <p>
 * 幂等执行：多次启动不会重复添加列或数据。
 */
@Component
@Order(1)
public class TenantOidMigrationRunner implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(TenantOidMigrationRunner.class);

    /** 默认租户 oid */
    private static final String DEFAULT_TENANT_OID = "00000000-0000-0000-0000-000000000001";
    /** 平台租户 oid */
    private static final String PLATFORM_TENANT_OID = "00000000-0000-0000-0000-000000000000";

    /** 需要添加 tenant_oid 列的所有业务表 */
    private static final List<String> BUSINESS_TABLES = Arrays.asList(
            "ck_token", "ck_organization", "ck_user", "ck_role", "ck_role_member",
            "ck_product_line", "ck_product_model", "ck_stage", "ck_folder",
            "ck_team", "ck_team_member", "ck_document", "ck_document_iteration",
            "ck_file", "ck_attachment", "ck_media", "ck_workflow_category",
            "ck_user_activity", "ck_type_iba_data"
    );

    private final JdbcTemplate jdbcTemplate;

    public TenantOidMigrationRunner(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public void run(String... args) {
        log.info("开始执行 tenant_id → tenant_oid 数据库迁移...");

        // 1. 为所有业务表添加 tenant_oid 列（幂等）
        addTenantOidColumns();

        // 2. 为 ck_token 补充 tenant_name 列
        addTokenTenantNameColumn();

        // 3. 存量数据迁移：将 tenant_id 映射为 tenant_oid
        migrateExistingData();

        // 4. 平台层角色归属迁移
        migratePlatformRoles();

        log.info("tenant_id → tenant_oid 数据库迁移完成");
    }

    private void addTenantOidColumns() {
        for (String table : BUSINESS_TABLES) {
            try {
                // 直接尝试添加列，忽略"列已存在"错误
                jdbcTemplate.execute("ALTER TABLE " + table + " ADD COLUMN tenant_oid CHAR(36)");
                log.info("  已添加列: {}.tenant_oid", table);
            } catch (Exception e) {
                // 列已存在时会报错，忽略
                log.debug("  列已存在或添加失败: {}.tenant_oid, error={}", table, e.getMessage());
            }
        }
    }

    private void addTokenTenantNameColumn() {
        try {
            jdbcTemplate.execute("ALTER TABLE ck_token ADD COLUMN tenant_name VARCHAR(100)");
            log.info("  已添加列: ck_token.tenant_name");
        } catch (Exception e) {
            log.debug("  列已存在或添加失败: ck_token.tenant_name");
        }
    }

    private void migrateExistingData() {
        for (String table : BUSINESS_TABLES) {
            try {
                // 使用 ck_tenant 表 JOIN 映射旧 tenant_id → tenant_oid
                int updated = jdbcTemplate.update(
                        "UPDATE " + table + " SET tenant_oid = t.oid " +
                                "FROM ck_tenant t " +
                                "WHERE " + table + ".tenant_id = t.tenant_id " +
                                "AND " + table + ".tenant_oid IS NULL");
                if (updated > 0) {
                    log.info("  迁移数据: {} ({} 行)", table, updated);
                }

                // 将仍未匹配的设置为默认租户 oid
                int defaulted = jdbcTemplate.update(
                        "UPDATE " + table + " SET tenant_oid = ? WHERE tenant_oid IS NULL",
                        DEFAULT_TENANT_OID);
                if (defaulted > 0) {
                    log.info("  设置默认租户: {} ({} 行)", table, defaulted);
                }
            } catch (Exception e) {
                log.warn("  迁移数据失败: {}, error={}", table, e.getMessage());
            }
        }
    }

    private void migratePlatformRoles() {
        try {
            int updated = jdbcTemplate.update(
                    "UPDATE ck_role SET tenant_oid = ? " +
                            "WHERE code IN ('PLATFORM_ADMIN', 'TENANT_ADMIN', 'CATEGORY_ADMIN', 'AUDIT_ADMIN', 'SECURITY_ADMIN') " +
                            "AND (tenant_oid = ? OR tenant_oid IS NULL)",
                    PLATFORM_TENANT_OID, DEFAULT_TENANT_OID);
            if (updated > 0) {
                log.info("  平台角色迁移: {} 个角色", updated);
            }
        } catch (Exception e) {
            log.warn("  平台角色迁移失败: {}", e.getMessage());
        }
    }
}
