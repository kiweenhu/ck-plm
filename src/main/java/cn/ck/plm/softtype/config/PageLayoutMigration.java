/*
 * Copyright (c) 2025 深圳乘恺科技有限公司
 * All rights reserved.
 *
 * @author Kiween.Hu; Roney.Liu
 */

package cn.ck.plm.softtype.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * 数据库迁移：为 ck_type_page_layout 表增加 entity_code / operation_code / operation_name 字段，
 * 并管理唯一约束。entity_type 列已废弃，通过 entity_oid 关联 ck_type_definition 获取。
 *
 * <p>幂等：重复执行不会出错。
 */
@Component
@Order(1)
public class PageLayoutMigration implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(PageLayoutMigration.class);

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Override
    public void run(String... args) {
        try {
            // 1. 添加列（如果不存在）
            jdbcTemplate.execute(
                "ALTER TABLE ck_type_page_layout ADD COLUMN IF NOT EXISTS entity_code VARCHAR(50)");
            jdbcTemplate.execute(
                "ALTER TABLE ck_type_page_layout ADD COLUMN IF NOT EXISTS operation_code VARCHAR(30) NOT NULL DEFAULT 'list'");
            jdbcTemplate.execute(
                "ALTER TABLE ck_type_page_layout ADD COLUMN IF NOT EXISTS operation_name VARCHAR(50)");

            // 2. 删除所有旧唯一约束（entity_oid + entity_type 或 entity_oid + entity_type + operation_code）
            jdbcTemplate.execute(
                "ALTER TABLE ck_type_page_layout DROP CONSTRAINT IF EXISTS ck_type_page_layout_entity_oid_entity_type_key");
            jdbcTemplate.execute(
                "ALTER TABLE ck_type_page_layout DROP CONSTRAINT IF EXISTS ck_type_page_layout_eo_et_oc_key");

            // 3. 删除废弃的 entity_type 列（如果还存在）
            jdbcTemplate.execute(
                "ALTER TABLE ck_type_page_layout DROP COLUMN IF EXISTS entity_type");

            // 3.5 回填老数据中为 NULL 的 entity_code（通过 entity_oid 关联 ck_type_definition.code）
            jdbcTemplate.execute(
                "UPDATE ck_type_page_layout pl SET entity_code = td.code " +
                "FROM ck_type_definition td " +
                "WHERE pl.entity_oid = td.oid AND pl.entity_code IS NULL");

            // 4. 添加新的唯一约束：entity_oid + operation_code + tenant_oid（如果不存在）
            jdbcTemplate.execute(
                "DO $$ BEGIN " +
                "  IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'uk_type_page_layout_entity_op_tenant') THEN " +
                "    ALTER TABLE ck_type_page_layout ADD CONSTRAINT uk_type_page_layout_entity_op_tenant " +
                "    UNIQUE (entity_oid, operation_code, tenant_oid); " +
                "  END IF; " +
                "END $$;");

            log.info("ck_type_page_layout 表结构迁移完成（已移除 entity_type，新约束: entity_oid + operation_code + tenant_oid）");
        } catch (Exception e) {
            log.warn("ck_type_page_layout 迁移跳过（表可能尚未创建）: {}", e.getMessage());
        }
    }
}
