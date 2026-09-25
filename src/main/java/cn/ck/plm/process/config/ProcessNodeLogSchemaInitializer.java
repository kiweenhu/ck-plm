/*
 * Copyright (c) 2025 深圳乘恺科技有限公司
 * All rights reserved.
 *
 * @author Kiween.Hu; Roney.Liu
 */

package cn.ck.plm.process.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.Statement;

/**
 * 流程节点执行日志表初始化（表 {@code ck_process_node_log}）。
 *
 * <p>为什么要有这张表：自动服务节点（「设置状态」「自动服务」「通知」）是<b>后台自己跑</b>的，
 * 它成功还是失败、为什么失败，只留在应用日志文件里 —— 用户点开流程详情看不到，
 * 运维也得翻服务器日志。把节点执行情况按"实例 + 节点"落到库里，才能做到
 * "点开某个节点，看它到底执行了什么、报了什么错"。
 *
 * <p>幂等操作，多次启动安全（{@code CREATE TABLE IF NOT EXISTS} + {@code ADD COLUMN IF NOT EXISTS}）。
 */
@Component
public class ProcessNodeLogSchemaInitializer implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(ProcessNodeLogSchemaInitializer.class);

    private static final String CREATE_TABLE_SQL =
            "CREATE TABLE IF NOT EXISTS ck_process_node_log (" +
            "    oid                  CHAR(36)     PRIMARY KEY," +
            "    tenant_oid           CHAR(36)     NOT NULL," +
            "    process_instance_id  CHAR(36)     NOT NULL," +
            "    activity_id          VARCHAR(128) NOT NULL," +
            "    activity_name        VARCHAR(255)," +
            "    level                VARCHAR(16)  NOT NULL," +   // INFO / WARN / ERROR
            "    source               VARCHAR(32)," +             // SERVICE / NOTIFY / SYSTEM
            "    message              VARCHAR(1000) NOT NULL," +
            "    detail               TEXT," +                    // 参数明细、异常堆栈
            "    created_at           TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP" +
            ")";

    /** 按实例 + 节点取日志（详情页点开一个节点就要这一条查询），并保证同一节点内按时间有序 */
    private static final String CREATE_INDEX_SQL =
            "CREATE INDEX IF NOT EXISTS idx_pnl_instance_activity " +
            "ON ck_process_node_log(process_instance_id, activity_id, created_at)";

    /** 节点经路上的"有错误"角标只按 ERROR 数，给一条更短的判定路径 */
    private static final String CREATE_LEVEL_INDEX_SQL =
            "CREATE INDEX IF NOT EXISTS idx_pnl_instance_level " +
            "ON ck_process_node_log(process_instance_id, level)";

    private final DataSource dataSource;

    public ProcessNodeLogSchemaInitializer(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @Override
    public void run(String... args) {
        try (Connection conn = dataSource.getConnection();
             Statement stmt = conn.createStatement()) {
            stmt.execute(CREATE_TABLE_SQL);
            stmt.execute(CREATE_INDEX_SQL);
            stmt.execute(CREATE_LEVEL_INDEX_SQL);
            log.info("流程节点执行日志表初始化完成（ck_process_node_log）");
        } catch (Exception e) {
            log.error("流程节点执行日志表初始化失败: {}", e.getMessage(), e);
        }
    }
}
