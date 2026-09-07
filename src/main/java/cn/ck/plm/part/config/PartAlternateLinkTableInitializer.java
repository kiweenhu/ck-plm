/*
 * Copyright (c) 2025 深圳乘恺科技有限公司
 * All rights reserved.
 *
 * @author Kiween.Hu; Roney.Liu
 */

package cn.ck.plm.part.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.Statement;

/**
 * 自动创建 ck_part_alternate_link 表（如果尚未存在），避免依赖 schema profile。
 *
 * <p>部件双向替代关系表，参考 Windchill WTPartAlternateLink 模型。
 * 幂等操作，多次启动安全。
 */
@Component
public class PartAlternateLinkTableInitializer implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(PartAlternateLinkTableInitializer.class);

    private static final String CREATE_TABLE_SQL =
            "CREATE TABLE IF NOT EXISTS ck_part_alternate_link (" +
            "    oid                 CHAR(36)     PRIMARY KEY," +
            "    code                VARCHAR(50)," +
            "    name                VARCHAR(200)," +
            "    description         VARCHAR(1000)," +
            "    role_a_part_oid     CHAR(36)     NOT NULL," +
            "    role_b_part_oid     CHAR(36)     NOT NULL," +
            "    alternate_type      VARCHAR(20)," +
            "    alternate_quantity  DOUBLE PRECISION DEFAULT 1.0," +
            "    alternate_unit      VARCHAR(50)," +
            "    enabled             BOOLEAN      NOT NULL DEFAULT TRUE," +
            "    effectivity_json    JSONB," +
            "    tenant_oid          CHAR(36)," +
            "    creator             VARCHAR(100)," +
            "    created_at          TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP," +
            "    updater             VARCHAR(100)," +
            "    updated_at          TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP" +
            ")";

    private static final String CREATE_UNIQUE_INDEX_SQL =
            "CREATE UNIQUE INDEX IF NOT EXISTS uk_part_alternate_link_pair ON ck_part_alternate_link(role_a_part_oid, role_b_part_oid)";

    private static final String CREATE_ROLE_A_INDEX_SQL =
            "CREATE INDEX IF NOT EXISTS idx_part_alternate_link_role_a ON ck_part_alternate_link(role_a_part_oid)";

    private static final String CREATE_ROLE_B_INDEX_SQL =
            "CREATE INDEX IF NOT EXISTS idx_part_alternate_link_role_b ON ck_part_alternate_link(role_b_part_oid)";

    private final DataSource dataSource;

    public PartAlternateLinkTableInitializer(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @Override
    public void run(String... args) {
        try (Connection conn = dataSource.getConnection();
             Statement stmt = conn.createStatement()) {
            stmt.execute(CREATE_TABLE_SQL);
            stmt.execute(CREATE_UNIQUE_INDEX_SQL);
            stmt.execute(CREATE_ROLE_A_INDEX_SQL);
            stmt.execute(CREATE_ROLE_B_INDEX_SQL);
            log.info("ck_part_alternate_link 表初始化完成");
        } catch (Exception e) {
            log.error("ck_part_alternate_link 表初始化失败: {}", e.getMessage(), e);
        }
    }
}
