/*
 * Copyright (c) 2025 深圳乘恺科技有限公司
 * All rights reserved.
 *
 * @author Kiween.Hu; Roney.Liu
 */

package cn.ck.plm.part.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * 数据库迁移：为已有 Part 迭代补齐分支 ID（branch_id），默认为 master 分支。
 *
 * <p>历史数据中 branch_id 可能为 NULL，统一回填为 'master'。
 * 幂等操作，多次启动安全。
 */
@Component
@Order(6)
public class PartIterationBranchIdMigration implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(PartIterationBranchIdMigration.class);

    private final JdbcTemplate jdbc;

    public PartIterationBranchIdMigration(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void run(ApplicationArguments args) {
        try {
            int updated = jdbc.update(
                    "UPDATE ck_part_iteration SET branch_id = 'master' WHERE branch_id IS NULL");
            if (updated > 0) {
                log.info("已为 {} 条 Part 迭代补齐 branch_id=master", updated);
            }
        } catch (Exception e) {
            log.warn("Part 迭代 branch_id 回填失败: {}", e.getMessage());
        }
    }
}
