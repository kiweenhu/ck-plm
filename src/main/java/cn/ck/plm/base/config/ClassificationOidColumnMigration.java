/*
 * Copyright (c) 2025 深圳乘恺科技有限公司
 * All rights reserved.
 *
 * @author Kiween.Hu; Roney.Liu
 */

package cn.ck.plm.base.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * 数据库迁移：为分类关联补充/统一列名。
 *
 * <p>分类字段统一收敛到主对象（Master）层，命名为 cls_oid，迁移内容：
 * <ul>
 *   <li>ck_part: classification_oid → cls_oid（主对象分类关联）</li>
 *   <li>ck_document: classification_oid → cls_oid（主对象分类关联）</li>
 *   <li>ck_part_iteration: 删除废弃的 cls_oid 列（分类已收敛到主对象）</li>
 *   <li>ck_document_iteration: 删除废弃的 cls_oid 列（分类已收敛到主对象）</li>
 * </ul>
 *
 * <p>幂等操作，多次启动安全。
 */
@Component
@Order(3)
public class ClassificationOidColumnMigration implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(ClassificationOidColumnMigration.class);

    private final JdbcTemplate jdbc;

    public ClassificationOidColumnMigration(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void run(ApplicationArguments args) {
        // 主对象：classification_oid 重命名为 cls_oid（如有旧列），否则直接添加 cls_oid
        renameOrAddColumn("ck_part", "classification_oid", "cls_oid", "CHAR(36)");
        renameOrAddColumn("ck_document", "classification_oid", "cls_oid", "CHAR(36)");
        // 子版本：分类已收敛到主对象，删除迭代层的 cls_oid 列（如有）
        dropColumnIfExists("ck_part_iteration", "cls_oid");
        dropColumnIfExists("ck_document_iteration", "cls_oid");
    }

    private void renameOrAddColumn(String tableName, String oldColumn, String newColumn, String type) {
        try {
            if (columnExists(tableName, oldColumn)) {
                if (!columnExists(tableName, newColumn)) {
                    jdbc.execute("ALTER TABLE " + tableName + " RENAME COLUMN " + oldColumn + " TO " + newColumn);
                    log.info("{}.{} 已重命名为 {}", tableName, oldColumn, newColumn);
                }
            } else if (!columnExists(tableName, newColumn)) {
                jdbc.execute("ALTER TABLE " + tableName + " ADD COLUMN " + newColumn + " " + type);
                log.info("{}.{} 列已添加", tableName, newColumn);
            }
        } catch (Exception e) {
            log.warn("{}.{} 迁移失败: {}", tableName, newColumn, e.getMessage());
        }
    }

    private void dropColumnIfExists(String tableName, String columnName) {
        try {
            if (columnExists(tableName, columnName)) {
                jdbc.execute("ALTER TABLE " + tableName + " DROP COLUMN " + columnName);
                log.info("{}.{} 列已删除", tableName, columnName);
            } else {
                log.debug("{}.{} 列不存在，跳过", tableName, columnName);
            }
        } catch (Exception e) {
            log.warn("{}.{} 删除列失败: {}", tableName, columnName, e.getMessage());
        }
    }

    private boolean columnExists(String tableName, String columnName) {
        try {
            Integer count = jdbc.queryForObject(
                    "SELECT COUNT(*) FROM information_schema.columns " +
                    "WHERE table_name = ? AND column_name = ? AND table_schema = 'public'",
                    Integer.class, tableName, columnName);
            return count != null && count > 0;
        } catch (Exception e) {
            return false;
        }
    }
}
