/*
 * Copyright (c) 2025 深圳乘恺科技有限公司
 * All rights reserved.
 *
 * @author Kiween.Hu; Roney.Liu
 */

package cn.ck.plm.resource.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;

/**
 * 自动创建电子元器件库表（ck_component_category / ck_electronic_component），幂等操作。
 *
 * <p>首次创建时预置常用根分类：电阻、电容、电感、二极管、三极管、IC、连接器、晶振、其他。
 */
@Component
public class ComponentLibraryTableInitializer implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(ComponentLibraryTableInitializer.class);

    private static final String CREATE_CATEGORY_SQL =
            "CREATE TABLE IF NOT EXISTS ck_component_category (" +
            "    oid                  CHAR(36)     PRIMARY KEY," +
            "    name                 VARCHAR(100) NOT NULL," +
            "    parent_category_oid  CHAR(36)," +
            "    sort_order           INTEGER      DEFAULT 0," +
            "    tenant_oid           CHAR(36)," +
            "    creator              VARCHAR(100)," +
            "    created_at           TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP," +
            "    updater              VARCHAR(100)," +
            "    updated_at           TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP" +
            ")";

    private static final String CREATE_COMPONENT_SQL =
            "CREATE TABLE IF NOT EXISTS ck_electronic_component (" +
            "    oid               CHAR(36)      PRIMARY KEY," +
            "    code              VARCHAR(50)   NOT NULL," +
            "    name              VARCHAR(200)  NOT NULL," +
            "    category_oid      CHAR(36)," +
            "    model             VARCHAR(200)," +
            "    package_type      VARCHAR(50)," +
            "    value_spec        VARCHAR(200)," +
            "    manufacturer      VARCHAR(200)," +
            "    stock_qty         INTEGER       DEFAULT 0," +
            "    safe_stock_qty    INTEGER       DEFAULT 0," +
            "    unit              VARCHAR(20)   DEFAULT 'ea'," +
            "    unit_cost         DOUBLE PRECISION DEFAULT 0," +
            "    description       VARCHAR(1000)," +
            "    tenant_oid        CHAR(36)," +
            "    creator           VARCHAR(100)," +
            "    created_at        TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP," +
            "    updater           VARCHAR(100)," +
            "    updated_at        TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP" +
            ")";

    private static final String CREATE_COMPONENT_CODE_UNIQUE =
            "CREATE UNIQUE INDEX IF NOT EXISTS uk_electronic_component_code ON ck_electronic_component(code)";

    private static final String CREATE_COMPONENT_CATEGORY_IDX =
            "CREATE INDEX IF NOT EXISTS idx_electronic_component_category ON ck_electronic_component(category_oid)";

    private static final String CREATE_CATEGORY_PARENT_IDX =
            "CREATE INDEX IF NOT EXISTS idx_component_category_parent ON ck_component_category(parent_category_oid)";

    /** 预置根分类（首次建表时插入） */
    private static final String[] DEFAULT_CATEGORIES = {"电阻", "电容", "电感", "二极管", "三极管", "IC", "连接器", "晶振", "其他"};

    private final DataSource dataSource;

    public ComponentLibraryTableInitializer(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @Override
    public void run(String... args) {
        try (Connection conn = dataSource.getConnection();
             Statement stmt = conn.createStatement()) {
            stmt.execute(CREATE_CATEGORY_SQL);
            stmt.execute(CREATE_COMPONENT_SQL);
            stmt.execute(CREATE_COMPONENT_CODE_UNIQUE);
            stmt.execute(CREATE_COMPONENT_CATEGORY_IDX);
            stmt.execute(CREATE_CATEGORY_PARENT_IDX);
            seedDefaultCategories(conn);
            log.info("电子元器件库表初始化完成（ck_component_category / ck_electronic_component）");
        } catch (Exception e) {
            log.error("电子元器件库表初始化失败: {}", e.getMessage(), e);
        }
    }

    /** 首次建表时预置常用根分类 */
    private void seedDefaultCategories(Connection conn) throws Exception {
        ResultSet rs = conn.createStatement().executeQuery("SELECT COUNT(*) FROM ck_component_category");
        rs.next();
        if (rs.getLong(1) > 0) return;
        try (Statement stmt = conn.createStatement()) {
            int i = 0;
            for (String name : DEFAULT_CATEGORIES) {
                stmt.execute("INSERT INTO ck_component_category (oid, name, parent_category_oid, sort_order, created_at, updated_at) " +
                        "VALUES ('" + java.util.UUID.randomUUID() + "', '" + name + "', NULL, " + (i++) + ", CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)");
            }
        }
        log.info("已预置 {} 个电子元器件根分类", DEFAULT_CATEGORIES.length);
    }
}
