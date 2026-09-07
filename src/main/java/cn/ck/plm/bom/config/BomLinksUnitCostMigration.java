/*
 * Copyright (c) 2025 深圳乘恺科技有限公司
 * All rights reserved.
 *
 * @author Kiween.Hu; Roney.Liu
 */

package cn.ck.plm.bom.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * 数据库迁移：为 BOM 行项（ck_bom_links）补充单位成本列 unit_cost。
 *
 * <p>单位成本（unitCost）用于 BOM 成本核算，单行基础成本，与数量无关。
 * 幂等操作，多次启动安全。
 */
@Component
@Order(4)
public class BomLinksUnitCostMigration implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(BomLinksUnitCostMigration.class);

    private final JdbcTemplate jdbc;

    public BomLinksUnitCostMigration(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void run(ApplicationArguments args) {
        addColumnIfNotExists("ck_bom_links", "unit_cost", "DOUBLE PRECISION");
    }

    private void addColumnIfNotExists(String tableName, String columnName, String type) {
        try {
            Integer count = jdbc.queryForObject(
                    "SELECT COUNT(*) FROM information_schema.columns " +
                    "WHERE table_name = ? AND column_name = ? AND table_schema = 'public'",
                    Integer.class, tableName, columnName);
            if (count != null && count > 0) {
                log.debug("{}.{} 列已存在，跳过", tableName, columnName);
                return;
            }
            jdbc.execute("ALTER TABLE " + tableName + " ADD COLUMN " + columnName + " " + type);
            log.info("{}.{} 列已添加", tableName, columnName);
        } catch (Exception e) {
            log.warn("{}.{} 迁移失败: {}", tableName, columnName, e.getMessage());
        }
    }
}
