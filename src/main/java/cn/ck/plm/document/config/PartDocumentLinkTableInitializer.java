/*
 * Copyright (c) 2025 深圳乘恺科技有限公司
 * All rights reserved.
 *
 * @author Kiween.Hu; Roney.Liu
 */

package cn.ck.plm.document.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.Statement;

/**
 * 自动创建 ck_part_document_link 表（如果尚未存在），避免依赖 schema profile。
 *
 * <p>部件关联文档表（参考 REFERENCE / 说明 DESCRIPTION 两类），幂等操作，多次启动安全。
 */
@Component
public class PartDocumentLinkTableInitializer implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(PartDocumentLinkTableInitializer.class);

    private static final String CREATE_TABLE_SQL =
            "CREATE TABLE IF NOT EXISTS ck_part_document_link (" +
            "    oid           CHAR(36)     PRIMARY KEY," +
            "    code          VARCHAR(50)," +
            "    name          VARCHAR(200)," +
            "    description   VARCHAR(1000)," +
            "    part_oid      CHAR(36)     NOT NULL," +
            "    document_oid  CHAR(36)     NOT NULL," +
            "    link_type     VARCHAR(20)  NOT NULL DEFAULT 'REFERENCE'," +
            "    enabled       BOOLEAN      NOT NULL DEFAULT TRUE," +
            "    tenant_oid    CHAR(36)," +
            "    creator       VARCHAR(100)," +
            "    created_at    TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP," +
            "    updater       VARCHAR(100)," +
            "    updated_at    TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP" +
            ")";

    private static final String CREATE_UNIQUE_INDEX_SQL =
            "CREATE UNIQUE INDEX IF NOT EXISTS uk_part_document_link ON ck_part_document_link(part_oid, document_oid, link_type)";

    private static final String CREATE_PART_INDEX_SQL =
            "CREATE INDEX IF NOT EXISTS idx_part_document_link_part ON ck_part_document_link(part_oid)";

    private static final String CREATE_DOC_INDEX_SQL =
            "CREATE INDEX IF NOT EXISTS idx_part_document_link_doc ON ck_part_document_link(document_oid)";

    private final DataSource dataSource;

    public PartDocumentLinkTableInitializer(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @Override
    public void run(String... args) {
        try (Connection conn = dataSource.getConnection();
             Statement stmt = conn.createStatement()) {
            stmt.execute(CREATE_TABLE_SQL);
            stmt.execute(CREATE_UNIQUE_INDEX_SQL);
            stmt.execute(CREATE_PART_INDEX_SQL);
            stmt.execute(CREATE_DOC_INDEX_SQL);
            log.info("ck_part_document_link 表初始化完成");
        } catch (Exception e) {
            log.error("ck_part_document_link 表初始化失败: {}", e.getMessage(), e);
        }
    }
}
