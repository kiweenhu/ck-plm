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
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.Statement;

/**
 * 自动创建 ck_media 表（如果尚未存在），避免依赖 schema profile。
 */
@Component
public class MediaTableInitializer implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(MediaTableInitializer.class);

    private static final String CREATE_TABLE_SQL =
            "CREATE TABLE IF NOT EXISTS ck_media (" +
            "    oid           CHAR(36)     PRIMARY KEY," +
            "    original_name VARCHAR(255) NOT NULL," +
            "    file_name     VARCHAR(255) NOT NULL," +
            "    file_size     BIGINT," +
            "    mime_type     VARCHAR(100)," +
            "    storage_path  VARCHAR(500)," +
            "    description   VARCHAR(500)," +
            "    width         INT," +
            "    height        INT," +
            "    tenant_oid    CHAR(36)," +
            "    creator       VARCHAR(100)," +
            "    created_at    TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP," +
            "    updater       VARCHAR(100)," +
            "    updated_at    TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP" +
            ")";

    private static final String ADD_TENANT_OID_SQL =
            "DO $$ BEGIN " +
            "  IF NOT EXISTS (SELECT 1 FROM information_schema.columns " +
            "    WHERE table_name='ck_media' AND column_name='tenant_oid') THEN " +
            "    ALTER TABLE ck_media ADD COLUMN tenant_oid CHAR(36); " +
            "  END IF; " +
            "END $$";

    private static final String CREATE_INDEX_SQL =
            "CREATE INDEX IF NOT EXISTS idx_media_created ON ck_media(created_at DESC)";

    private final DataSource dataSource;

    public MediaTableInitializer(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @Override
    public void run(String... args) {
        try (Connection conn = dataSource.getConnection();
             Statement stmt = conn.createStatement()) {
            stmt.execute(CREATE_TABLE_SQL);
            stmt.execute(CREATE_INDEX_SQL);

            // 添加 tenant_oid 列（兼容已有表）
            try {
                stmt.execute(ADD_TENANT_OID_SQL);
                log.info("ck_media 表已添加 tenant_oid 列（如之前不存在）");
            } catch (Exception e) {
                log.warn("添加 tenant_oid 列失败: {}", e.getMessage());
            }

            // 为已有记录填充 tenant_oid
            int filled = stmt.executeUpdate(
                "UPDATE ck_media SET tenant_oid = " +
                "  COALESCE((SELECT oid FROM ck_tenant LIMIT 1), '00000000-0000-0000-0000-000000000000') " +
                "WHERE tenant_oid IS NULL");
            if (filled > 0) {
                log.info("已为 {} 条 ck_media 记录填充 tenant_oid", filled);
            }

            // 迁移旧的 storage_path 格式 /media/{uuid}.ext → /media/{tenantOid}/{uuid}.ext
            int mediaUpdated = stmt.executeUpdate(
                "UPDATE ck_media SET storage_path = " +
                "  REPLACE(storage_path, '/media/', '/media/' || tenant_oid || '/') " +
                "WHERE storage_path LIKE '/media/%' " +
                "  AND storage_path NOT LIKE '/media/' || tenant_oid || '/%'");
            if (mediaUpdated > 0) {
                log.info("已迁移 {} 条 ck_media 的 storage_path 格式", mediaUpdated);
            }

            // 迁移 product_line 的 thumbnail 引用
            int plUpdated = stmt.executeUpdate(
                "UPDATE ck_product_line SET thumbnail = " +
                "  REPLACE(thumbnail, '/media/', '/media/' || tenant_oid || '/') " +
                "WHERE thumbnail IS NOT NULL " +
                "  AND thumbnail LIKE '/media/%' " +
                "  AND thumbnail NOT LIKE '/media/' || tenant_oid || '/%'");
            if (plUpdated > 0) {
                log.info("已迁移 {} 条 ck_product_line 的 thumbnail 引用", plUpdated);
            }

            log.info("ck_media 表初始化完成");
        } catch (Exception e) {
            log.error("ck_media 表初始化失败: {}", e.getMessage(), e);
        }
    }
}
