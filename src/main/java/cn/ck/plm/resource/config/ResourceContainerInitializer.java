/*
 * Copyright (c) 2025 深圳乘恺科技有限公司
 * All rights reserved.
 *
 * @author Kiween.Hu; Roney.Liu
 */

package cn.ck.plm.resource.config;

import cn.ck.plm.resource.entity.ResourceContainer;
import cn.ck.plm.resource.mapper.ResourceLibraryMapper;
import cn.ck.plm.resource.mapper.ResourceContainerMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.Statement;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * 企业级资源库初始化：
 * <ol>
 *   <li>创建资源库容器表 {@code ck_resource_container}（含旧表 ck_container 迁移）</li>
 *   <li>幂等预置「企业资源库」根节点与 7 个资源子库（平台级共享）</li>
 *   <li>为「元器件库」子库准备归属阶段（Part 需要 stage_oid）</li>
 *   <li>清理早期临时方案：借用在 ck_product_line 的 CORP_RESOURCE 行及其阶段</li>
 * </ol>
 */
@Component
public class ResourceContainerInitializer implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(ResourceContainerInitializer.class);

    private static final String TABLE = "ck_resource_container";
    private static final String LEGACY_TABLE = "ck_container";

    private static final String CREATE_TABLE_SQL =
            "CREATE TABLE IF NOT EXISTS ck_resource_container (" +
            "    oid             CHAR(36)     PRIMARY KEY," +
            "    code            VARCHAR(50)  NOT NULL," +
            "    name            VARCHAR(200) NOT NULL," +
            "    description     VARCHAR(1000)," +
            "    container_type  VARCHAR(30)  NOT NULL DEFAULT 'CORP_RESOURCE'," +
            "    thumbnail       VARCHAR(500)," +
            "    parent_oid      CHAR(36)," +
            "    team_oid        CHAR(36)," +
            "    sort_order      INTEGER      NOT NULL DEFAULT 0," +
            "    tenant_oid      CHAR(36)," +
            "    delete_mark     BOOLEAN      NOT NULL DEFAULT FALSE," +
            "    creator         VARCHAR(100)," +
            "    created_at      TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP," +
            "    updater         VARCHAR(100)," +
            "    updated_at      TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP" +
            ")";

    private static final String CREATE_CODE_UNIQUE =
            "CREATE UNIQUE INDEX IF NOT EXISTS uk_resource_container_code ON ck_resource_container(code)";

    private static final String CREATE_PARENT_IDX =
            "CREATE INDEX IF NOT EXISTS idx_resource_container_parent ON ck_resource_container(parent_oid)";

    private final ResourceContainerMapper mapper;
    private final ResourceLibraryMapper resourceLibraryMapper;
    private final DataSource dataSource;

    public ResourceContainerInitializer(ResourceContainerMapper mapper,
                                        ResourceLibraryMapper resourceLibraryMapper,
                                        DataSource dataSource) {
        this.mapper = mapper;
        this.resourceLibraryMapper = resourceLibraryMapper;
        this.dataSource = dataSource;
    }

    /**
     * 表更名迁移：早期表名为 {@code ck_container}，现统一为 {@code ck_resource_container}。
     * <ul>
     *   <li>仅旧表存在 → 直接 RENAME（数据与索引一并保留）</li>
     *   <li>两表都存在 → 补齐新表缺失的行后删除旧表</li>
     * </ul>
     */
    private void migrateLegacyTable(Connection conn, Statement stmt) throws Exception {
        boolean legacyExists = tableExists(conn, LEGACY_TABLE);
        if (!legacyExists) {
            return;
        }
        boolean newExists = tableExists(conn, TABLE);
        if (!newExists) {
            stmt.execute("ALTER TABLE " + LEGACY_TABLE + " RENAME TO " + TABLE);
            log.info("资源库表已更名: {} → {}", LEGACY_TABLE, TABLE);
            return;
        }
        int moved = stmt.executeUpdate(
                "INSERT INTO " + TABLE + " (oid, code, name, description, container_type, thumbnail, " +
                "parent_oid, team_oid, sort_order, tenant_oid, delete_mark, creator, created_at, updater, updated_at) " +
                "SELECT oid, code, name, description, container_type, thumbnail, " +
                "parent_oid, team_oid, sort_order, tenant_oid, delete_mark, creator, created_at, updater, updated_at " +
                "FROM " + LEGACY_TABLE + " l " +
                "WHERE NOT EXISTS (SELECT 1 FROM " + TABLE + " n WHERE n.code = l.code)");
        stmt.execute("DROP TABLE " + LEGACY_TABLE);
        if (moved > 0) {
            log.info("资源库表迁移完成：{} → {}（迁移 {} 行）", LEGACY_TABLE, TABLE, moved);
        } else {
            log.info("资源库表迁移完成：已删除旧表 {}", LEGACY_TABLE);
        }
    }

    private boolean tableExists(Connection conn, String table) throws Exception {
        try (java.sql.ResultSet rs = conn.createStatement().executeQuery(
                "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema = 'public' AND table_name = '" + table + "'")) {
            rs.next();
            return rs.getLong(1) > 0;
        }
    }

    @Override
    public void run(String... args) {
        try (Connection conn = dataSource.getConnection();
             Statement stmt = conn.createStatement()) {
            migrateLegacyTable(conn, stmt);
            stmt.execute(CREATE_TABLE_SQL);
            stmt.execute(CREATE_CODE_UNIQUE);
            stmt.execute(CREATE_PARENT_IDX);
        } catch (Exception e) {
            log.error("ck_resource_container 表初始化失败: {}", e.getMessage(), e);
            return;
        }

        try {
            ResourceContainer root = mapper.selectRoot();
            if (root == null) {
                root = newNode(ResourceContainer.ROOT_CODE, "企业资源库",
                        "企业级资源库（元器件、封装图符、标准件、通用件、技术文档、产品图册等统一归属）", null, 0);
                mapper.insert(root);
                log.info("已创建企业资源库根节点: CORP_RESOURCE");
            }
            int sort = 1;
            int created = 0;
            for (String[] def : ResourceContainer.defaultChildren()) {
                if (mapper.selectByCode(def[0]) == null) {
                    mapper.insert(newNode(def[0], def[1], def[1] + "（企业资源库子库）", root.getOid(), sort));
                    created++;
                }
                sort++;
            }
            log.info("企业资源库初始化完成（子库新增 {} 个）", created);

            // 先清理早期临时方案（其阶段挂在旧 product_line 行上），再补建归属阶段
            cleanLegacyHack();

            // 元器件库子库需要归属阶段（Part.stage_oid NOT NULL）
            ResourceContainer componentLib = mapper.selectByCode(ResourceContainer.COMPONENT);
            if (componentLib != null && resourceLibraryMapper.selectResourceStageOid(componentLib.getOid()) == null) {
                resourceLibraryMapper.insertResourceStage(UUID.randomUUID().toString(),
                        "COMPONENT", "元器件", componentLib.getOid(), LocalDateTime.now());
                log.info("已为元器件库创建归属阶段: COMPONENT");
            }
        } catch (Exception e) {
            log.error("企业资源库初始化失败: {}", e.getMessage(), e);
        }
    }

    /** 清理早期临时方案：资源库曾借用 ck_product_line 的 CORP_RESOURCE 行及其阶段 */
    private void cleanLegacyHack() {
        try (Connection conn = dataSource.getConnection();
             Statement stmt = conn.createStatement()) {
            stmt.execute("DELETE FROM ck_stage WHERE owner_oid IN " +
                    "(SELECT oid FROM ck_product_line WHERE code = 'CORP_RESOURCE')");
            int removed = stmt.executeUpdate("DELETE FROM ck_product_line WHERE code = 'CORP_RESOURCE'");
            if (removed > 0) {
                log.info("已清理临时方案数据：ck_product_line.CORP_RESOURCE（{} 行）", removed);
            }
        } catch (Exception e) {
            log.warn("清理临时资源库数据失败（不影响主流程）: {}", e.getMessage());
        }
    }

    private ResourceContainer newNode(String code, String name, String description, String parentOid, int sortOrder) {
        ResourceContainer node = new ResourceContainer();
        node.setOid(UUID.randomUUID().toString());
        node.setCode(code);
        node.setName(name);
        node.setDescription(description);
        node.setContainerType(ResourceContainer.CONTAINER_TYPE);
        node.setParentOid(parentOid);
        node.setSortOrder(sortOrder);
        node.setDeleteMark(false);
        LocalDateTime now = LocalDateTime.now();
        node.setCreatedAt(now);
        node.setUpdatedAt(now);
        return node;
    }
}
