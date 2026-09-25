/*
 * Copyright (c) 2025 深圳乘恺科技有限公司
 * All rights reserved.
 *
 * @author Kiween.Hu; Roney.Liu
 */

package cn.ck.plm.integration.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.Statement;

/**
 * 目标系统注册表建表（幂等）—— 与 {@code ProcessTemplateSchemaInitializer} 同一做法：
 * 应用启动自建，避免"部署时忘了跑 DDL"导致的运行期 500。
 *
 * <pre>
 * ck_target_system   流程可调用的外部系统（地址 / 认证 / 凭据 / 启用）
 * </pre>
 *
 * <p>本表是<b>业务表</b>（含 {@code tenant_oid}），由 {@code TenantStatementInterceptor}
 * 自动注入租户过滤 —— 未列入其 SHARED / PLATFORM_SHARED 白名单，
 * 于是"每家租户只能看到、只能调用自己的目标系统"。
 *
 * <p>留档迁移脚本见 {@code db/migration/V026__target_system.sql}（DBA 手工建库时用）。
 */
@Component
public class TargetSystemSchemaInitializer implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(TargetSystemSchemaInitializer.class);

    private final DataSource dataSource;

    public TargetSystemSchemaInitializer(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @Override
    public void run(String... args) {
        try (Connection connection = dataSource.getConnection();
             Statement stmt = connection.createStatement()) {
            createTable(stmt);
        } catch (Exception e) {
            // 建表失败不能让应用起不来：目标系统是"流程里用得到才配"的能力，
            // 起不来会把无关功能一起拖下水；真要用到时会因表不存在而明确报错
            log.error("目标系统注册表初始化失败", e);
        }
    }

    private void createTable(Statement stmt) throws Exception {
        stmt.executeUpdate(
                "CREATE TABLE IF NOT EXISTS ck_target_system ("
                        + "  oid VARCHAR(64) PRIMARY KEY,"
                        + "  code VARCHAR(64) NOT NULL,"
                        + "  name VARCHAR(128) NOT NULL,"
                        + "  base_url VARCHAR(512) NOT NULL,"
                        + "  auth_type VARCHAR(16) NOT NULL DEFAULT 'NONE',"
                        + "  username VARCHAR(128),"
                        + "  secret VARCHAR(1024),"
                        + "  enabled BOOLEAN NOT NULL DEFAULT TRUE,"
                        + "  sort_order INTEGER NOT NULL DEFAULT 0,"
                        + "  description VARCHAR(512),"
                        + "  tenant_oid VARCHAR(64) NOT NULL,"
                        + "  creator VARCHAR(128),"
                        + "  created_at TIMESTAMP,"
                        + "  updater VARCHAR(128),"
                        + "  updated_at TIMESTAMP"
                        + ")");
        // 编码在租户内唯一：流程节点按 code 引用该系统，同名会让"调的是哪一个"变得不确定
        stmt.executeUpdate(
                "CREATE UNIQUE INDEX IF NOT EXISTS uk_target_system_code_tenant "
                        + "ON ck_target_system (code, tenant_oid)");
        stmt.executeUpdate(
                "CREATE INDEX IF NOT EXISTS idx_target_system_tenant "
                        + "ON ck_target_system (tenant_oid)");
        log.info("目标系统注册表已就绪（ck_target_system）");
    }
}
