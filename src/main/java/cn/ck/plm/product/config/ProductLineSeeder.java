/*
 * Copyright (c) 2025 深圳乘恺科技有限公司
 * All rights reserved.
 *
 * @author Kiween.Hu; Roney.Liu
 */

package cn.ck.plm.product.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * 应用启动时自动初始化种子产品线数据。
 *
 * <p>仅在 ck_product_line 表为空时插入示例数据（幂等：已有数据则跳过）。
 *
 * <p>执行顺序：在 PageLayoutInitializer(@Order=4) 之后。
 */
// @Component  -- 启动时无需初始化产品线种子数据
// @Order(5)
public class ProductLineSeeder implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(ProductLineSeeder.class);

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Override
    public void run(String... args) {
        Integer count = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM ck_product_line", Integer.class);
        if (count != null && count > 0) {
            log.info("产品线表已有 {} 条数据，跳过种子数据初始化", count);
            return;
        }

        log.info("开始初始化产品线种子数据...");

        LocalDateTime now = LocalDateTime.now();
        String creator = "system";

        // 根级产品线
        String pl1 = UUID.randomUUID().toString();
        String pl2 = UUID.randomUUID().toString();
        String pl3 = UUID.randomUUID().toString();
        // 子级产品线
        String pl1a = UUID.randomUUID().toString();
        String pl1b = UUID.randomUUID().toString();

        jdbcTemplate.update(
            "INSERT INTO ck_product_line (oid, code, name, description, parent_oid, creator, created_at, updater, updated_at) " +
            "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)",
            pl1, "PL-SMART-HOME", "智能家居系列", "涵盖智能灯具、智能安防、智能温控等产品线", null,
            creator, now, creator, now
        );
        jdbcTemplate.update(
            "INSERT INTO ck_product_line (oid, code, name, description, parent_oid, creator, created_at, updater, updated_at) " +
            "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)",
            pl2, "PL-WEARABLE", "可穿戴设备系列", "智能手表、健康手环、智能眼镜等产品", null,
            creator, now, creator, now
        );
        jdbcTemplate.update(
            "INSERT INTO ck_product_line (oid, code, name, description, parent_oid, creator, created_at, updater, updated_at) " +
            "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)",
            pl3, "PL-IOT-PLATFORM", "物联网平台系列", "IoT 设备管理、数据采集与边缘计算平台", null,
            creator, now, creator, now
        );

        // 智能家居的子系列
        jdbcTemplate.update(
            "INSERT INTO ck_product_line (oid, code, name, description, parent_oid, creator, created_at, updater, updated_at) " +
            "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)",
            pl1a, "PL-SMART-LIGHTING", "智能灯具", "智能灯泡、灯带、场景照明控制器", pl1,
            creator, now, creator, now
        );
        jdbcTemplate.update(
            "INSERT INTO ck_product_line (oid, code, name, description, parent_oid, creator, created_at, updater, updated_at) " +
            "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)",
            pl1b, "PL-SMART-SECURITY", "智能安防", "智能门锁、摄像头、门窗传感器", pl1,
            creator, now, creator, now
        );

        log.info("产品线种子数据初始化完成: 新增 {} 条记录", 5);
    }
}
