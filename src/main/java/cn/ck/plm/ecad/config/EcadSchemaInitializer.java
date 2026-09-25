/*
 * Copyright (c) 2025 深圳乘恺科技有限公司
 * All rights reserved.
 *
 * @author Kiween.Hu; Roney.Liu
 */

package cn.ck.plm.ecad.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.UUID;

/**
 * 电子域（ECAD）表结构与基础数据初始化。
 *
 * <p>采用「宿主（PART/DOCUMENT）+ 1:1 专业扩展表」的混合模式：
 * 宿主提供编码/版本/生命周期/检出等平台能力，扩展表承载电子领域专业字段。
 *
 * <p>域归属由<b>类型定义树</b>表达：电子域在 {@code ck_type_definition} 中注册为域锚点类型
 * {@code ECAD_DOMAIN}（typeKind=DOMAIN），封装 / 图符 / 电子元器件 / PCBA / 原理图 / PCB 设计等
 * 对象类型作为 softtype 直接挂在其下，并通过显式 rootTypeCode（PART / DOCUMENT）追溯能力宿主。
 * 未来新增电子域对象类型只需在域锚点下新增 softtype，零改表。
 *
 * <p>{@code ck_ecad_domain} 表存储域锚点类型的<b>实例</b>（如 ECAD 电子设计域），
 * 支持创建多个域实例并独立治理/授权。原 {@code ck_ecad_domain_member} 成员挂载表已废弃
 * （归属改由类型树 parentOid 表达），启动时自动删除。
 *
 * <p>幂等操作，多次启动安全。
 */
@Component
public class EcadSchemaInitializer implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(EcadSchemaInitializer.class);

    private static final String PLATFORM_OID = "00000000-0000-0000-0000-000000000000";

    private final DataSource dataSource;

    public EcadSchemaInitializer(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @Override
    public void run(String... args) {
        try (Connection conn = dataSource.getConnection();
             Statement stmt = conn.createStatement()) {
            createDomainTables(stmt);
            createExtTables(stmt);
            createLinkTables(stmt);
            createProjectTable(stmt);
            migratePackageSymbolToEngDocument(stmt);
            initEcadDomain(stmt);
            log.info("电子域（ECAD）表结构初始化完成");
        } catch (Exception e) {
            log.error("电子域（ECAD）表结构初始化失败: {}", e.getMessage(), e);
        }
    }

    /**
     * 封装 / 图符归属迁移：从 {@code ck_part}（物料）迁到 {@code ck_eng_document}（工程数据）。
     *
     * <p>封装（FOOTPRINT）与图符（SYMBOL）已重新定义为 <b>EDA 设计数据</b>，宿主由 PART
     * 改为 ENG_DOCUMENT，故历史主数据需从 ck_part 复制到 ck_eng_document（含版本记录）。
     * 幂等：已迁移（oid 冲突）时跳过。
     */
    private void migratePackageSymbolToEngDocument(Statement stmt) {
        final String types = "('FOOTPRINT','SYMBOL')";
        // 1) 主数据（两表列结构一致，整行复制）
        try {
            int n = stmt.executeUpdate(
                    "INSERT INTO ck_eng_document (oid, number, name, description, container_oid, container_type, " +
                            "type_definition_code, folder_oid, stage_oid, cls_oid, tenant_oid, creator, created_at, " +
                            "updater, updated_at) " +
                            "SELECT oid, number, name, description, container_oid, container_type, type_definition_code, " +
                            "folder_oid, stage_oid, cls_oid, tenant_oid, creator, created_at, updater, updated_at " +
                            "FROM ck_part WHERE type_definition_code IN " + types + " " +
                            "ON CONFLICT (oid) DO NOTHING");
            if (n > 0) {
                log.info("封装/图符主数据已迁移至工程数据（ck_eng_document）：{} 条", n);
            }
        } catch (Exception e) {
            log.debug("封装/图符主数据迁移跳过: {}", e.getMessage());
        }
        // 2) 版本记录（工程数据迭代表含 CAD/制图专有列，此处仅复制两表公共列）
        try {
            int n = stmt.executeUpdate(
                    "INSERT INTO ck_eng_document_iteration (oid, master_oid, revision, iteration, display_version, " +
                            "checked_out, checked_out_by, checked_out_comment, latest, status, " +
                            "lifecycle_template_iteration_oid, version_sort, branch_id, delete_mark, " +
                            "tenant_oid, creator, created_at, updater, updated_at) " +
                            "SELECT i.oid, i.master_oid, i.revision, i.iteration, i.display_version, " +
                            "i.checked_out, i.checked_out_by, i.checked_out_comment, i.latest, i.status, " +
                            "i.lifecycle_template_iteration_oid, i.version_sort, i.branch_id, i.delete_mark, " +
                            "i.tenant_oid, i.creator, i.created_at, i.updater, i.updated_at " +
                            "FROM ck_part_iteration i JOIN ck_part p ON p.oid = i.master_oid " +
                            "WHERE p.type_definition_code IN " + types + " " +
                            "ON CONFLICT (oid) DO NOTHING");
            if (n > 0) {
                log.info("封装/图符版本记录已迁移至工程数据（ck_eng_document_iteration）：{} 条", n);
            }
        } catch (Exception e) {
            log.debug("封装/图符版本记录迁移跳过: {}", e.getMessage());
        }
    }

    // ==================== 超级域对象 ====================

    private void createDomainTables(Statement stmt) throws Exception {
        stmt.execute("CREATE TABLE IF NOT EXISTS ck_ecad_domain (" +
                "oid CHAR(36) PRIMARY KEY, code VARCHAR(50) NOT NULL, name VARCHAR(200) NOT NULL, " +
                "description VARCHAR(1000), enabled BOOLEAN NOT NULL DEFAULT TRUE, tenant_oid CHAR(36), " +
                "creator VARCHAR(100), created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP, " +
                "updater VARCHAR(100), updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP)");
        stmt.execute("CREATE UNIQUE INDEX IF NOT EXISTS uk_ecad_domain_code ON ck_ecad_domain(code)");

        // 废弃：域成员挂载表。域归属已由类型定义树（parentOid → 域锚点 ECAD_DOMAIN）表达，此表冗余，直接删除
        stmt.execute("DROP TABLE IF EXISTS ck_ecad_domain_member");
    }

    // ==================== 专业扩展表（1:1 挂宿主 oid） ====================

    private void createExtTables(Statement stmt) throws Exception {
        stmt.execute("CREATE TABLE IF NOT EXISTS ck_ecad_footprint_ext (" +
                "oid CHAR(36) PRIMARY KEY, ipc_name VARCHAR(200) NOT NULL, mount_type VARCHAR(20) NOT NULL, " +
                "pad_count INTEGER NOT NULL, pitch_mm FLOAT, body_size VARCHAR(100), height_mm FLOAT, " +
                "ipc_compliant BOOLEAN NOT NULL DEFAULT FALSE, eda_format VARCHAR(20) NOT NULL, " +
                "preview_file_oid CHAR(36), source_library VARCHAR(200), datasheet_url VARCHAR(500), " +
                "tenant_oid CHAR(36), creator VARCHAR(100), created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP, " +
                "updater VARCHAR(100), updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP)");

        stmt.execute("CREATE TABLE IF NOT EXISTS ck_ecad_symbol_ext (" +
                "oid CHAR(36) PRIMARY KEY, symbol_category VARCHAR(30) NOT NULL, pin_count INTEGER NOT NULL, " +
                "pin_mapping VARCHAR(2000), eda_format VARCHAR(20) NOT NULL, preview_file_oid CHAR(36), " +
                "source_library VARCHAR(200), tenant_oid CHAR(36), creator VARCHAR(100), " +
                "created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP, updater VARCHAR(100), " +
                "updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP)");

        stmt.execute("CREATE TABLE IF NOT EXISTS ck_ecad_schematic_ext (" +
                "oid CHAR(36) PRIMARY KEY, sheet_count INTEGER, eda_tool VARCHAR(100), design_phase VARCHAR(20), " +
                "component_count INTEGER, tenant_oid CHAR(36), creator VARCHAR(100), " +
                "created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP, updater VARCHAR(100), " +
                "updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP)");

        stmt.execute("CREATE TABLE IF NOT EXISTS ck_ecad_pcb_ext (" +
                "oid CHAR(36) PRIMARY KEY, layer_count INTEGER NOT NULL, board_thickness_mm FLOAT, " +
                "board_size VARCHAR(100), stackup VARCHAR(2000), impedance_ctrl VARCHAR(500), " +
                "surface_finish VARCHAR(20), gerber_file_oid CHAR(36), tenant_oid CHAR(36), " +
                "creator VARCHAR(100), created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP, " +
                "updater VARCHAR(100), updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP)");
    }

    // ==================== 关系表 ====================

    private void createLinkTables(Statement stmt) throws Exception {
        stmt.execute("CREATE TABLE IF NOT EXISTS ck_component_footprint_link (" +
                "oid CHAR(36) PRIMARY KEY, component_oid CHAR(36) NOT NULL, footprint_oid CHAR(36) NOT NULL, " +
                "is_default BOOLEAN NOT NULL DEFAULT FALSE, variant_note VARCHAR(500), " +
                "sort_order INTEGER NOT NULL DEFAULT 0, tenant_oid CHAR(36), creator VARCHAR(100), " +
                "created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP, updater VARCHAR(100), " +
                "updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP)");
        stmt.execute("CREATE INDEX IF NOT EXISTS idx_cfl_component ON ck_component_footprint_link(component_oid)");
        stmt.execute("CREATE INDEX IF NOT EXISTS idx_cfl_footprint ON ck_component_footprint_link(footprint_oid)");

        stmt.execute("CREATE TABLE IF NOT EXISTS ck_component_symbol_link (" +
                "oid CHAR(36) PRIMARY KEY, component_oid CHAR(36) NOT NULL, symbol_oid CHAR(36) NOT NULL, " +
                "is_default BOOLEAN NOT NULL DEFAULT FALSE, variant_note VARCHAR(500), tenant_oid CHAR(36), " +
                "creator VARCHAR(100), created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP, " +
                "updater VARCHAR(100), updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP)");
        stmt.execute("CREATE INDEX IF NOT EXISTS idx_csl_component ON ck_component_symbol_link(component_oid)");
        stmt.execute("CREATE INDEX IF NOT EXISTS idx_csl_symbol ON ck_component_symbol_link(symbol_oid)");

        stmt.execute("CREATE TABLE IF NOT EXISTS ck_footprint_3d_link (" +
                "oid CHAR(36) PRIMARY KEY, footprint_oid CHAR(36) NOT NULL, model3d_oid CHAR(36) NOT NULL, " +
                "is_default BOOLEAN NOT NULL DEFAULT FALSE, scale FLOAT NOT NULL DEFAULT 1.0, " +
                "offset_x FLOAT, offset_y FLOAT, offset_z FLOAT, rotation_x FLOAT, rotation_y FLOAT, rotation_z FLOAT, " +
                "tenant_oid CHAR(36), creator VARCHAR(100), created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP, " +
                "updater VARCHAR(100), updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP)");
        stmt.execute("CREATE INDEX IF NOT EXISTS idx_f3l_footprint ON ck_footprint_3d_link(footprint_oid)");

        stmt.execute("CREATE TABLE IF NOT EXISTS ck_ecad_project_design_link (" +
                "oid CHAR(36) PRIMARY KEY, project_oid CHAR(36) NOT NULL, design_oid CHAR(36) NOT NULL, " +
                "design_type VARCHAR(30) NOT NULL, sort_order INTEGER NOT NULL DEFAULT 0, tenant_oid CHAR(36), " +
                "creator VARCHAR(100), created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP, " +
                "updater VARCHAR(100), updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP)");
        stmt.execute("CREATE INDEX IF NOT EXISTS idx_epdl_project ON ck_ecad_project_design_link(project_oid)");
        stmt.execute("CREATE INDEX IF NOT EXISTS idx_epdl_design ON ck_ecad_project_design_link(design_oid)");

        stmt.execute("CREATE TABLE IF NOT EXISTS ck_design_instance (" +
                "oid CHAR(36) PRIMARY KEY, design_oid CHAR(36) NOT NULL, design_type VARCHAR(30) NOT NULL, " +
                "reference VARCHAR(50), component_oid CHAR(36), footprint_oid CHAR(36), symbol_oid CHAR(36), " +
                "quantity INTEGER NOT NULL DEFAULT 1, tenant_oid CHAR(36), creator VARCHAR(100), " +
                "created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP, updater VARCHAR(100), " +
                "updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP)");
        stmt.execute("CREATE INDEX IF NOT EXISTS idx_dins_design ON ck_design_instance(design_oid)");
        stmt.execute("CREATE INDEX IF NOT EXISTS idx_dins_component ON ck_design_instance(component_oid)");
        stmt.execute("CREATE INDEX IF NOT EXISTS idx_dins_footprint ON ck_design_instance(footprint_oid)");
    }

    // ==================== 设计项目（独立容器类型） ====================

    private void createProjectTable(Statement stmt) throws Exception {
        stmt.execute("CREATE TABLE IF NOT EXISTS ck_ecad_project (" +
                "oid CHAR(36) PRIMARY KEY, code VARCHAR(50) NOT NULL, name VARCHAR(200) NOT NULL, " +
                "description VARCHAR(1000), container_type VARCHAR(30) NOT NULL DEFAULT 'ECAD_PROJECT', " +
                "parent_oid CHAR(36), domain_oid CHAR(36), related_product CHAR(36), " +
                "project_phase VARCHAR(20) NOT NULL DEFAULT 'PLAN', owner VARCHAR(100), tenant_oid CHAR(36), " +
                "creator VARCHAR(100), created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP, " +
                "updater VARCHAR(100), updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP)");
        stmt.execute("CREATE UNIQUE INDEX IF NOT EXISTS uk_ecad_project_code ON ck_ecad_project(code)");
        stmt.execute("CREATE INDEX IF NOT EXISTS idx_ecad_project_domain ON ck_ecad_project(domain_oid)");
    }

    // ==================== 初始化电子域 ====================

    /** 确保存在默认电子域（code=ECAD，平台级共享） */
    private void initEcadDomain(Statement stmt) throws Exception {
        try (ResultSet rs = stmt.executeQuery(
                "SELECT oid FROM ck_ecad_domain WHERE code = 'ECAD' LIMIT 1")) {
            if (rs.next()) {
                return;
            }
        }
        stmt.execute("INSERT INTO ck_ecad_domain (oid, code, name, description, enabled, tenant_oid, created_at, updated_at) VALUES ('"
                + UUID.randomUUID() + "', 'ECAD', '电子设计域', " +
                "'电子设计域（ECAD）：封装、原理图图符、原理图、PCB 设计、电子设计项目等对象的统一域锚点', TRUE, '"
                + PLATFORM_OID + "', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)");
        log.info("已初始化电子域: ECAD（电子设计域）");
    }
}
