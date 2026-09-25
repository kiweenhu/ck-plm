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
import java.sql.Statement;

/**
 * 资源库分类绑定配置表初始化（表 {@code ck_library_cls_config}）。
 *
 * <p>按资源库节点（resource_code / resource_node_oid）+ 租户维度记录其绑定的
 * 「分类管理」分类根节点。仅支持绑定分类树的资源子库可用：
 * 元器件库(COMPONENT) / 标准件库(STD_PART) / 通用件库(GEN_PART)。
 *
 * <p>幂等操作，多次启动安全；已存在旧表时自动补齐新增列。
 */
@Component
public class ResourceLibraryTableInitializer implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(ResourceLibraryTableInitializer.class);

    private static final String CREATE_CONFIG_SQL =
            "CREATE TABLE IF NOT EXISTS ck_library_cls_config (" +
            "    oid                      CHAR(36)  PRIMARY KEY," +
            "    tenant_oid               CHAR(36)  NOT NULL," +
            "    resource_code            VARCHAR(50)," +
            "    resource_node_oid         CHAR(36)," +
            "    root_classification_oid  CHAR(36)  NOT NULL," +
            "    created_at               TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP," +
            "    updated_at               TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP" +
            ")";

    private static final String ADD_RESOURCE_CODE =
            "ALTER TABLE ck_library_cls_config ADD COLUMN IF NOT EXISTS resource_code VARCHAR(50)";

    private static final String ADD_RESOURCE_NODE =
            "ALTER TABLE ck_library_cls_config ADD COLUMN IF NOT EXISTS resource_node_oid CHAR(36)";

    /**
     * 历史表名 ck_component_library_config 语义过窄（含 component，但实际存三个资源库
     * COMPONENT / STD_PART / GEN_PART 的分类绑定），重命名为 ck_library_cls_config。
     * 数据完整保留；已迁移过（新表已存在或旧表不存在）时无副作用。
     */
    private static final String RENAME_TABLE_SQL =
            "ALTER TABLE IF EXISTS ck_component_library_config RENAME TO ck_library_cls_config";

    /** 原按租户唯一；改为按 租户 + 资源库 唯一（旧索引名清理） */
    private static final String DROP_OLD_TENANT_UNIQUE =
            "DROP INDEX IF EXISTS uk_component_library_config_tenant";

    private static final String DROP_OLD_TENANT_CODE_UNIQUE =
            "DROP INDEX IF EXISTS uk_component_lib_config_tenant_code";

    private static final String CREATE_TENANT_CODE_UNIQUE =
            "CREATE UNIQUE INDEX IF NOT EXISTS uk_library_cls_config_tenant_code " +
            "ON ck_library_cls_config(tenant_oid, COALESCE(resource_code, ''))";

    private final DataSource dataSource;

    public ResourceLibraryTableInitializer(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @Override
    public void run(String... args) {
        try (Connection conn = dataSource.getConnection();
             Statement stmt = conn.createStatement()) {
            // 历史表重命名（幂等）：ck_component_library_config → ck_library_cls_config
            try {
                stmt.execute(RENAME_TABLE_SQL);
            } catch (Exception renameErr) {
                // 新表已存在（已迁移过）或旧表不存在时忽略
                log.debug("表重命名跳过（已迁移或旧表不存在）: {}", renameErr.getMessage());
            }
            stmt.execute(CREATE_CONFIG_SQL);
            stmt.execute(ADD_RESOURCE_CODE);
            stmt.execute(ADD_RESOURCE_NODE);
            stmt.execute(DROP_OLD_TENANT_UNIQUE);
            stmt.execute(DROP_OLD_TENANT_CODE_UNIQUE);
            stmt.execute(CREATE_TENANT_CODE_UNIQUE);
            log.info("资源库分类绑定配置表初始化完成（ck_library_cls_config）");
        } catch (Exception e) {
            log.error("资源库分类绑定配置表初始化失败: {}", e.getMessage(), e);
        }
    }
}
