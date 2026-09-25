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
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * 零件-文档关联表（{@code ck_doc_part_link}）初始化 —— Windchill 两分模型（DESCRIBES / REFERENCE）。
 *
 * <p>本表由 {@code PartDescribeLink} 与 {@code PartReferenceLink} 两个实体共享，
 * 通过 {@code link_type} 列区分，此前缺少建表初始化器，此处补齐。
 *
 * <p>同时承担历史数据迁移：将已废弃的 {@code ck_part_document_link}
 * （主数据级、linkType=REFERENCE/DESCRIPTION）迁移为本表记录（迭代级、DESCRIBES/REFERENCE），
 * 迁移时按 part_oid 解析到最新零件迭代。
 *
 * <p>幂等操作，多次启动安全。
 */
@Component
public class PartDocLinkTableInitializer implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(PartDocLinkTableInitializer.class);

    private final JdbcTemplate jdbcTemplate;

    public PartDocLinkTableInitializer(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public void run(String... args) {
        try {
            createTable();
            migrateFromPartDocumentLink();
        } catch (Exception e) {
            log.error("零件-文档关联表（ck_doc_part_link）初始化失败: {}", e.getMessage(), e);
        }
    }

    private void createTable() {
        jdbcTemplate.execute("CREATE TABLE IF NOT EXISTS ck_doc_part_link (" +
                "oid CHAR(36) PRIMARY KEY, " +
                "link_type VARCHAR(30) NOT NULL, " +
                "part_iteration_oid CHAR(36) NOT NULL, " +
                "doc_master_oid CHAR(36) NOT NULL, " +
                "doc_iteration_oid CHAR(36), " +
                "resolved_iteration_oid CHAR(36), " +
                "category VARCHAR(100), " +
                "tenant_oid CHAR(36), creator VARCHAR(100), " +
                "created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP, " +
                "updater VARCHAR(100), updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP)");
        jdbcTemplate.execute("CREATE INDEX IF NOT EXISTS idx_dpl_part_iter ON ck_doc_part_link(part_iteration_oid)");
        jdbcTemplate.execute("CREATE INDEX IF NOT EXISTS idx_dpl_doc_master ON ck_doc_part_link(doc_master_oid)");
        jdbcTemplate.execute("CREATE INDEX IF NOT EXISTS idx_dpl_type ON ck_doc_part_link(link_type)");
        jdbcTemplate.execute("CREATE UNIQUE INDEX IF NOT EXISTS uk_dpl_triple " +
                "ON ck_doc_part_link(part_iteration_oid, doc_master_oid, link_type)");
        log.info("零件-文档关联表 ck_doc_part_link 初始化完成");
    }

    /**
     * 迁移历史数据：ck_part_document_link → ck_doc_part_link。
     * 仅迁移能解析到最新零件迭代的记录，DESCRIPTION 映射为 DESCRIBES。
     */
    private void migrateFromPartDocumentLink() {
        try {
            Integer exists = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM information_schema.tables " +
                    "WHERE table_schema = 'public' AND table_name = 'ck_part_document_link'", Integer.class);
            if (exists == null || exists == 0) return; // 旧表不存在，无需迁移

            int moved = jdbcTemplate.update(
                    "INSERT INTO ck_doc_part_link (oid, link_type, part_iteration_oid, doc_master_oid, " +
                    "category, tenant_oid, creator, created_at, updater, updated_at) " +
                    "SELECT gen_random_uuid()::text, " +
                    "  CASE WHEN pdl.link_type = 'DESCRIPTION' THEN 'DESCRIBES' ELSE 'REFERENCE' END, " +
                    "  pi.oid, pdl.document_oid, NULL, " +
                    "  pdl.tenant_oid, pdl.creator, pdl.created_at, pdl.updater, pdl.updated_at " +
                    "FROM ck_part_document_link pdl " +
                    "JOIN ck_part_iteration pi ON pi.master_oid = pdl.part_oid AND pi.latest = TRUE " +
                    "WHERE NOT EXISTS (SELECT 1 FROM ck_doc_part_link d " +
                    "  WHERE d.part_iteration_oid = pi.oid AND d.doc_master_oid = pdl.document_oid " +
                    "    AND d.link_type = CASE WHEN pdl.link_type = 'DESCRIPTION' THEN 'DESCRIBES' ELSE 'REFERENCE' END)");
            if (moved > 0) {
                log.info("已从 ck_part_document_link 迁移 {} 条历史关联到 ck_doc_part_link", moved);
            }
        } catch (Exception e) {
            log.warn("迁移 ck_part_document_link 历史数据失败（可忽略）: {}", e.getMessage());
        }
    }
}
