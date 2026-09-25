/*
 * Copyright (c) 2025 深圳乘恺科技有限公司
 * All rights reserved.
 *
 * @author Kiween.Hu; Roney.Liu
 */

package cn.ck.plm.document.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.Statement;

/**
 * 工程数据（EngineeringDocument）表结构初始化 —— 参照 Windchill EPMDocument 体系。
 *
 * <p>「工程数据」是 3D 数模 / 2D 工程图 / 材料规格说明等工程对象的<b>统称</b>，并可向电子领域扩展。
 *
 * <p>建立五张表：
 * <pre>
 * ck_eng_document              工程数据主数据（对应 EPMDocumentMaster）
 * ck_eng_document_iteration    工程数据子版本（对应 EPMDocument）
 * ck_eng_doc_part_link         工程数据 ↔ 零部件（对应 EPMBuildRule：CAD ↔ Part 构建关联）
 * ck_eng_doc_member_link       工程数据 ↔ 工程数据（对应 EPMMemberLink：装配成员 / BOM 结构）
 * ck_eng_doc_ref_link          工程数据 ↔ 工程数据（对应 EPMReferenceLink：横向引用）
 * </pre>
 *
 * <h3>ck_eng_doc_part_link 与 Windchill EPMBuildRule 的对齐</h3>
 * <p>Windchill 中 CAD 文档（EPMDocument）与零部件（WTPart）的关联由 <b>Build Rule</b> 驱动：
 * 用户在界面上选择的 <b>关联类型（Association Type）</b> 决定激活哪些
 * <b>构建链接（Build Link）</b> —— 结构（Structure）/ 属性（Attribute）/ 表示（Representation）。
 *
 * <pre>
 * 关联类型 assoc_type            结构  属性  表示   基数约束（每个 Part）
 * ---------------------------------------------------------------------
 * OWNER（拥有）                  ✔     ✔     ✔     至多 1 个（唯一驱动结构者）
 * CONTRIBUTING_IMAGE（贡献图像）  —     ✔     ✔     至多 1 个
 * IMAGE（图像）                  —     —     ✔     不限（参与父级结构）
 * CONTRIBUTING_CONTENT（贡献内容） —    ✔     —     不限
 * CONTENT（内容）                —     —     —     不限（被动描述，不参与构建）
 * </pre>
 *
 * <p>设计说明：
 * <ul>
 *   <li>{@code assoc_type} 为权威字段；三条 {@code build_*} 布尔列由它推导后冗余存储，
 *       以便按「能力」检索，并为将来支持 CUSTOM 组合预留。</li>
 *   <li>{@code CALCULATED}（系统自动生成的派生关联，如由 Owner 模型派生的 2D 工程图）
 *       以 {@code assoc_source='CALCULATED'} 标识，不占用上述标准五类。</li>
 *   <li>{@code is_primary} 对应 Windchill 的 "Primary Owner-Associated Part"：
 *       一个 CAD 文档可关联多个 Part，结构 / 表示仅传递到第一个（主）Owner 关联的 Part。</li>
 *   <li><b>用量（quantity）不在此表</b>：Windchill 中它属于零部件用途链接（Part Usage Link），
 *       由 CAD 结构构建而来；本表只表达 CAD ↔ Part 的关联<i>性质</i>。</li>
 * </ul>
 *
 * <h3>工程数据之间的链接：对照 wt.epm.structure 拆分 MemberLink / ReferenceLink</h3>
 * <p>Windchill 中 {@code EPMDocument} 之间的链接位于 {@code wt.epm.structure} 包，
 * 采用「<b>一个接口 + 多个具体链接类（各自独立表）</b>」架构：
 * <pre>
 * EPMDependencyLink（接口：依赖关系的通用形式，<b>无独立表</b>）
 *     ├── EPMMemberLink    装配成员（父装配 uses 子件，必有 quantity）→ ck_eng_doc_member_link
 *     └── EPMReferenceLink 横向引用（referencedBy 引用 references，必有 referenceType）→ ck_eng_doc_ref_link
 *     （同包另有 EPMDescribeLink / EPMVariantLink，本项目暂不实现）
 * </pre>
 * <p>因此本项目<b>不建「依赖表」</b>，而是让两张链接表各自携带
 * {@code EPMDependencyLink} 接口约定的公共列：
 * {@code as_stored_child_name / dep_type / required / unique_link_id}。
 *
 * <pre>
 * 对比维度      ck_eng_doc_member_link                ck_eng_doc_ref_link
 * ---------------------------------------------------------------------------------
 * 语义          组成 / 装配关系（BOM 层级）            指向 / 引用关系（文档横向引用）
 * roleA         used_by_iteration_oid（父装配迭代）   referenced_by_iteration_oid（发起引用的迭代）
 * roleB         uses_master_oid（子件主对象）         references_master_oid（被引用主对象）
 * 数量          quantity 必有（默认 1）               无（引用无用量概念）
 * 必填类型      —                                     reference_type（EPMReferenceType）
 * 特有属性      放置/变换/固定/替代/抑制/标注/阵列…   references_type（被引用对象可为通用文档）
 * </pre>
 *
 * <p>两类链接的方向均为「<b>迭代 → 主对象</b>」：roleA 是 {@code Iterated} 端、roleB 是 {@code Mastered} 端
 * （对齐 Windchill {@code IteratedUsageLink}）—— 每个装配<b>版本</b>拥有自己的成员结构，
 * 但指向子件的<b>主对象</b>而非某个具体版本。代码层对应
 * {@code EngDocDependencyLink}（接口）/ {@code EngDocMemberLink} / {@code EngDocRefLink} /
 * {@code EngDocStructureService}（导航服务，对齐 {@code EPMStructureService}）。
 *
 * <p>历史表名 ck_ed_document / ck_ed_document_iteration / ck_ed_doc_part_link / ck_ed_doc_ref_link
 * 易被误解为仅指 2D 工程图纸，启动时自动重命名为 ck_eng_*（数据完整保留）。
 *
 * <p>幂等操作，多次启动安全。
 */
@Component
public class EngineeringDocumentSchemaInitializer implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(EngineeringDocumentSchemaInitializer.class);

    private final DataSource dataSource;

    public EngineeringDocumentSchemaInitializer(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @Override
    public void run(String... args) {
        try (Connection conn = dataSource.getConnection();
             Statement stmt = conn.createStatement()) {
            // 各步骤相互隔离：任一步骤失败（如历史脏数据导致重命名冲突）不影响其余步骤
            step("历史表重命名", () -> renameLegacyTables(stmt));
            step("历史列重命名", () -> renameLegacyColumns(stmt));
            step("历史索引重命名", () -> renameLegacyIndexes(stmt));
            step("主数据表", () -> createMasterTable(stmt));
            step("子版本表", () -> createIterationTable(stmt));
            step("关系表", () -> createLinkTables(stmt));
            step("EPMBuildRule 对齐", () -> migrateBuildRuleSchema(stmt));
            step("EPM 链接拆分", () -> migrateRefLinkSplit(stmt));
            step("类型编码迁移", () -> migrateTypeCode(stmt));
            log.info("工程数据（EngineeringDocument）表结构初始化完成");
        } catch (Exception e) {
            log.error("工程数据（EngineeringDocument）表结构初始化失败: {}", e.getMessage(), e);
        }
    }

    /** 允许抛出受检异常的步骤体。 */
    @FunctionalInterface
    private interface SqlStep {
        void run() throws Exception;
    }

    /** 单步执行并隔离异常，避免一步失败中断其余步骤。 */
    private void step(String name, SqlStep action) {
        try {
            action.run();
        } catch (Exception e) {
            log.warn("工程数据表初始化步骤失败 [{}]: {}", name, e.getMessage());
        }
    }

    /**
     * 历史表重命名（幂等）：ck_ed_* → ck_eng_*。
     * 旧表名 ed 易被误解为仅指 2D 工程图（Drawing），实际为 3D 数模 / 2D 工程图 /
     * 材料规格说明等工程对象的统称；RENAME 数据完整保留。
     */
    private void renameLegacyTables(Statement stmt) {
        String[][] pairs = {
                {"ck_ed_document", "ck_eng_document"},
                {"ck_ed_document_iteration", "ck_eng_document_iteration"},
                {"ck_ed_doc_part_link", "ck_eng_doc_part_link"},
                {"ck_ed_doc_ref_link", "ck_eng_doc_ref_link"},
        };
        for (String[] p : pairs) {
            try {
                stmt.execute("ALTER TABLE IF EXISTS " + p[0] + " RENAME TO " + p[1]);
            } catch (Exception e) {
                // 新表已存在（已迁移过）或旧表不存在时忽略
                log.debug("表重命名跳过 {}: {}", p[0], e.getMessage());
            }
        }
    }

    /**
     * 历史列名重命名（幂等）：ed_document_oid → eng_document_oid。
     *
     * <p>注意：{@code ALTER TABLE ... RENAME} 只改表名、<b>不改列名</b>，
     * 因此需单独将关系表的外键列一并重命名，才能与代码中的新列名保持一致。
     * 若表为新建（列名已是 eng_document_oid）或列不存在，则跳过。
     */
    private void renameLegacyColumns(Statement stmt) {
        String[][] pairs = {
                {"ck_eng_doc_part_link", "ed_document_oid", "eng_document_oid"},
                {"ck_eng_doc_ref_link", "from_ed_document_oid", "from_eng_document_oid"},
                {"ck_eng_doc_ref_link", "to_ed_document_oid", "to_eng_document_oid"},
        };
        for (String[] p : pairs) {
            try {
                stmt.execute("ALTER TABLE " + p[0] + " RENAME COLUMN " + p[1] + " TO " + p[2]);
            } catch (Exception e) {
                log.debug("列重命名跳过 {}.{}: {}", p[0], p[1], e.getMessage());
            }
        }
    }

    /**
     * 历史主键索引名规范化（幂等）：ck_ed_*_pkey → ck_eng_*_pkey。
     *
     * <p>{@code ALTER TABLE ... RENAME} 只改表名，<b>不改索引/约束名</b>，
     * 因此重命名后主键索引仍残留旧表名，此处一并规范化（仅改名，无数据影响）。
     */
    private void renameLegacyIndexes(Statement stmt) {
        String[][] pairs = {
                {"ck_ed_document_pkey", "ck_eng_document_pkey"},
                {"ck_ed_document_iteration_pkey", "ck_eng_document_iteration_pkey"},
                {"ck_ed_doc_part_link_pkey", "ck_eng_doc_part_link_pkey"},
                {"ck_ed_doc_ref_link_pkey", "ck_eng_doc_ref_link_pkey"},
        };
        for (String[] p : pairs) {
            try {
                stmt.execute("ALTER INDEX IF EXISTS " + p[0] + " RENAME TO " + p[1]);
            } catch (Exception e) {
                // 新名已存在（已规范化）或旧名不存在时忽略
                log.debug("索引重命名跳过 {}: {}", p[0], e.getMessage());
            }
        }
    }

    /** 类型编码迁移：ED_DOCUMENT → ENG_DOCUMENT（已有业务数据同步，幂等） */
    private void migrateTypeCode(Statement stmt) {
        try {
            stmt.execute("UPDATE ck_eng_document SET type_definition_code = 'ENG_DOCUMENT' " +
                    "WHERE type_definition_code = 'ED_DOCUMENT'");
        } catch (Exception e) {
            log.debug("类型编码迁移跳过: {}", e.getMessage());
        }
    }

    // ==================== 主数据（EPMDocumentMaster） ====================

    private void createMasterTable(Statement stmt) throws Exception {
        stmt.execute("CREATE TABLE IF NOT EXISTS ck_eng_document (" +
                "oid CHAR(36) PRIMARY KEY, " +
                "number VARCHAR(100), name VARCHAR(200), description VARCHAR(1000), " +
                "container_oid CHAR(36), container_type VARCHAR(50), " +
                "type_definition_code VARCHAR(100), " +
                "folder_oid CHAR(36), stage_oid CHAR(36), cls_oid CHAR(36), " +
                "tenant_oid CHAR(36), creator VARCHAR(100), " +
                "created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP, " +
                "updater VARCHAR(100), updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP)");
        stmt.execute("CREATE UNIQUE INDEX IF NOT EXISTS uk_eng_document_number ON ck_eng_document(number)");
        stmt.execute("CREATE INDEX IF NOT EXISTS idx_eng_document_type ON ck_eng_document(type_definition_code)");
        stmt.execute("CREATE INDEX IF NOT EXISTS idx_eng_document_folder ON ck_eng_document(folder_oid)");
    }

    // ==================== 子版本（EPMDocument） ====================

    private void createIterationTable(Statement stmt) throws Exception {
        stmt.execute("CREATE TABLE IF NOT EXISTS ck_eng_document_iteration (" +
                "oid CHAR(36) PRIMARY KEY, " +
                "master_oid CHAR(36) NOT NULL, revision VARCHAR(20), iteration INTEGER, " +
                "display_version VARCHAR(30), " +
                "checked_out BOOLEAN NOT NULL DEFAULT FALSE, checked_out_by VARCHAR(100), " +
                "checked_out_comment VARCHAR(1000), latest BOOLEAN NOT NULL DEFAULT FALSE, " +
                "derived_from_oid CHAR(36), derived_at TIMESTAMP, " +
                "status VARCHAR(50), lifecycle_template_iteration_oid CHAR(36), " +
                "version_sort INTEGER, branch_id VARCHAR(64), delete_mark BOOLEAN NOT NULL DEFAULT FALSE, " +
                "ckfile_oid CHAR(36), " +
                "cad_name VARCHAR(200), cad_type VARCHAR(30), cad_tool VARCHAR(100), " +
                "sheet_size VARCHAR(20), scale VARCHAR(30), sheet_number VARCHAR(100), " +
                "sheet_count INTEGER, projection VARCHAR(20), " +
                "author VARCHAR(100), material VARCHAR(200), weight VARCHAR(100), " +
                "tenant_oid CHAR(36), creator VARCHAR(100), " +
                "created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP, " +
                "updater VARCHAR(100), updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP)");
        stmt.execute("CREATE INDEX IF NOT EXISTS idx_engd_iter_master ON ck_eng_document_iteration(master_oid)");
        stmt.execute("CREATE INDEX IF NOT EXISTS idx_engd_iter_latest ON ck_eng_document_iteration(latest)");
    }

    // ==================== 关系表 ====================

    private void createLinkTables(Statement stmt) throws Exception {
        // 工程数据 ↔ 零部件：对应 Windchill EPMBuildRule（CAD ↔ Part 构建关联）
        // 关联类型（assoc_type）决定激活哪些「构建链接（Build Link）」：
        //   OWNER              结构+属性+表示（唯一驱动结构者，每 Part 限 1 个）
        //   CONTRIBUTING_IMAGE 属性+表示（每 Part 限 1 个，不驱动结构）
        //   IMAGE              仅表示（可多个，参与父级结构）
        //   CONTRIBUTING_CONTENT 仅属性（可多个，不参与结构）
        //   CONTENT            纯被动描述（三者皆无，不参与构建，无数量限制）
        stmt.execute("CREATE TABLE IF NOT EXISTS ck_eng_doc_part_link (" +
                "oid CHAR(36) PRIMARY KEY, " +
                "eng_document_oid CHAR(36) NOT NULL, part_oid CHAR(36) NOT NULL, " +
                "assoc_type VARCHAR(30) NOT NULL DEFAULT 'CONTENT', " +
                "build_structure BOOLEAN NOT NULL DEFAULT FALSE, " +
                "build_attribute BOOLEAN NOT NULL DEFAULT FALSE, " +
                "build_representation BOOLEAN NOT NULL DEFAULT FALSE, " +
                "assoc_source VARCHAR(20) NOT NULL DEFAULT 'MANUAL', " +
                "is_primary BOOLEAN NOT NULL DEFAULT FALSE, sort_order INTEGER NOT NULL DEFAULT 0, " +
                "tenant_oid CHAR(36), creator VARCHAR(100), " +
                "created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP, " +
                "updater VARCHAR(100), updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP)");
        stmt.execute("CREATE INDEX IF NOT EXISTS idx_engpl_doc ON ck_eng_doc_part_link(eng_document_oid)");
        stmt.execute("CREATE INDEX IF NOT EXISTS idx_engpl_part ON ck_eng_doc_part_link(part_oid)");
        stmt.execute("CREATE UNIQUE INDEX IF NOT EXISTS uk_engpl_doc_part ON ck_eng_doc_part_link(eng_document_oid, part_oid)");

        // 工程数据 ↔ 工程数据（对应 Windchill EPMMemberLink）：装配成员 / BOM 结构
        // roleA = usedBy（父装配的「迭代」），roleB = uses（子件的「主对象」）—— IteratedUsageLink 语义
        // 「成员链接必有数量」：quantity required（默认 1）；带 transform 时 quantity 必须为 1 且 placed = true
        stmt.execute("CREATE TABLE IF NOT EXISTS ck_eng_doc_member_link (" +
                "oid CHAR(36) PRIMARY KEY, " +
                "used_by_iteration_oid CHAR(36) NOT NULL, uses_master_oid CHAR(36) NOT NULL, " +
                "quantity NUMERIC(18,4) NOT NULL DEFAULT 1, " +
                "placed BOOLEAN NOT NULL DEFAULT FALSE, " +
                "has_transform BOOLEAN NOT NULL DEFAULT FALSE, transform TEXT, " +
                "fixed BOOLEAN NOT NULL DEFAULT FALSE, substitute BOOLEAN NOT NULL DEFAULT FALSE, " +
                "suppressed BOOLEAN NOT NULL DEFAULT FALSE, annotated BOOLEAN NOT NULL DEFAULT FALSE, " +
                "model_item_owner_id VARCHAR(200), model_item_owner_type VARCHAR(50), " +
                "comp_number INTEGER NOT NULL DEFAULT -1, comp_rev_number INTEGER NOT NULL DEFAULT -1, " +
                "comp_layer_index INTEGER NOT NULL DEFAULT -1, " +
                "name VARCHAR(200), identifier INTEGER, identifier_space_name VARCHAR(100), " +
                "as_stored_child_name VARCHAR(200), dep_type INTEGER NOT NULL DEFAULT 0, " +
                "required BOOLEAN NOT NULL DEFAULT FALSE, unique_link_id BIGINT, " +
                "sort_order INTEGER NOT NULL DEFAULT 0, " +
                "tenant_oid CHAR(36), creator VARCHAR(100), " +
                "created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP, " +
                "updater VARCHAR(100), updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP)");
        // 注意：成员表索引同样在 migrateRefLinkSplit() 中建立——旧表列名为 used_by_oid / uses_oid，
        // 需先完成列重命名，否则在此建索引会报「字段不存在」并中断整个初始化

        // 工程数据 ↔ 工程数据（对应 Windchill EPMReferenceLink）：横向引用
        // roleA = referencedBy（发起引用的「迭代」），roleB = references（被引用的「主对象」）—— IteratedUsageLink 语义
        // reference_type（EPMReferenceType）必填；被引用对象可为工程数据或通用文档（references_type）
        // 注意：索引在 migrateRefLinkSplit() 中建立——旧表需先完成列重命名，否则建索引会报列不存在
        stmt.execute("CREATE TABLE IF NOT EXISTS ck_eng_doc_ref_link (" +
                "oid CHAR(36) PRIMARY KEY, " +
                "referenced_by_iteration_oid CHAR(36) NOT NULL, references_master_oid CHAR(36) NOT NULL, " +
                "references_type VARCHAR(30) NOT NULL DEFAULT 'ENG_DOCUMENT', " +
                "reference_type VARCHAR(50) NOT NULL DEFAULT 'DEPENDENCY', " +
                "as_stored_child_name VARCHAR(200), dep_type INTEGER NOT NULL DEFAULT 0, " +
                "required BOOLEAN NOT NULL DEFAULT FALSE, unique_link_id BIGINT, " +
                "tenant_oid CHAR(36), creator VARCHAR(100), " +
                "created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP, " +
                "updater VARCHAR(100), updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP)");
    }

    // ==================== EPMBuildRule 对齐迁移 ====================

    /** 关联类型 → 三条构建链接（Structure / Attribute / Representation）。 */
    private static final String[][] ASSOC_BUILD_LINKS = {
            {"OWNER", "TRUE", "TRUE", "TRUE"},
            {"CONTRIBUTING_IMAGE", "FALSE", "TRUE", "TRUE"},
            {"IMAGE", "FALSE", "FALSE", "TRUE"},
            {"CONTRIBUTING_CONTENT", "FALSE", "TRUE", "FALSE"},
            {"CONTENT", "FALSE", "FALSE", "FALSE"},
    };

    /**
     * Windchill EPMBuildRule 对齐迁移（幂等）。
     *
     * <p>旧版 {@code ck_eng_doc_part_link} 仅有 {@code link_type}（取值 DESCRIBE / REFERENCE），
     * 未表达 Windchill「关联类型 → 构建链接」模型。此处：
     * <ol>
     *   <li>列重命名 {@code link_type → assoc_type}；</li>
     *   <li>旧值映射：DESCRIBE / DESCRIBES → IMAGE，REFERENCE → CONTENT；</li>
     *   <li>补列 {@code build_structure / build_attribute / build_representation / assoc_source}；</li>
     *   <li>按 {@code assoc_type} 回填三条 Build Link；</li>
     *   <li>建立新列索引与 Windchill 基数约束（部分唯一索引）。</li>
     * </ol>
     *
     * <p>全部步骤各自 try-catch：表不存在或已迁移时静默跳过，多次启动安全。
     */
    private void migrateBuildRuleSchema(Statement stmt) {
        // 1) 列重命名：link_type → assoc_type
        try {
            stmt.execute("ALTER TABLE ck_eng_doc_part_link RENAME COLUMN link_type TO assoc_type");
        } catch (Exception e) {
            log.debug("assoc_type 列重命名跳过（已迁移或结构已最新）: {}", e.getMessage());
        }
        // 2) 旧关联类型值映射到 Windchill 关联类型
        try {
            stmt.execute("UPDATE ck_eng_doc_part_link SET assoc_type = 'IMAGE' " +
                    "WHERE assoc_type IN ('DESCRIBE', 'DESCRIBES')");
            stmt.execute("UPDATE ck_eng_doc_part_link SET assoc_type = 'CONTENT' " +
                    "WHERE assoc_type = 'REFERENCE'");
        } catch (Exception e) {
            log.debug("assoc_type 旧值映射跳过: {}", e.getMessage());
        }
        // 3) 补列（旧表缺少的新列）
        String[] addCols = {
                "ADD COLUMN IF NOT EXISTS build_structure BOOLEAN NOT NULL DEFAULT FALSE",
                "ADD COLUMN IF NOT EXISTS build_attribute BOOLEAN NOT NULL DEFAULT FALSE",
                "ADD COLUMN IF NOT EXISTS build_representation BOOLEAN NOT NULL DEFAULT FALSE",
                "ADD COLUMN IF NOT EXISTS assoc_source VARCHAR(20) NOT NULL DEFAULT 'MANUAL'",
        };
        for (String col : addCols) {
            try {
                stmt.execute("ALTER TABLE ck_eng_doc_part_link " + col);
            } catch (Exception e) {
                log.debug("BuildRule 补列跳过 [{}]: {}", col, e.getMessage());
            }
        }
        // 4) 默认值对齐（旧表默认值为 'DESCRIBE'）
        try {
            stmt.execute("ALTER TABLE ck_eng_doc_part_link ALTER COLUMN assoc_type SET DEFAULT 'CONTENT'");
        } catch (Exception e) {
            log.debug("assoc_type 默认值对齐跳过: {}", e.getMessage());
        }
        // 5) 按关联类型回填三条 Build Link（assoc_type 决定 build link，故天然幂等）
        for (String[] m : ASSOC_BUILD_LINKS) {
            try {
                stmt.execute("UPDATE ck_eng_doc_part_link SET build_structure = " + m[1] +
                        ", build_attribute = " + m[2] + ", build_representation = " + m[3] +
                        " WHERE assoc_type = '" + m[0] + "'");
            } catch (Exception e) {
                log.debug("Build Link 回填跳过 [{}]: {}", m[0], e.getMessage());
            }
        }
        // 6) 索引与基数约束
        //    先清理历史表名（ck_ed_*）时代的同义索引：ALTER TABLE RENAME 不改索引名，
        //    这些 idx_edpl_* / uk_edpl_* 与新建的 idx_engpl_* 完全重复，保留会造成重复索引与写放大
        for (String idx : new String[]{"idx_edpl_doc", "idx_edpl_part", "uk_edpl_doc_part"}) {
            try {
                stmt.execute("DROP INDEX IF EXISTS " + idx);
            } catch (Exception e) {
                log.debug("历史索引删除跳过 {}: {}", idx, e.getMessage());
            }
        }
        try {
            stmt.execute("CREATE INDEX IF NOT EXISTS idx_engpl_assoc ON ck_eng_doc_part_link(assoc_type)");
            // Windchill：每个 Part 至多 1 个 Owner 关联
            stmt.execute("CREATE UNIQUE INDEX IF NOT EXISTS uk_engpl_part_owner " +
                    "ON ck_eng_doc_part_link(part_oid) WHERE assoc_type = 'OWNER'");
            // Windchill：每个 Part 至多 1 个 Contributing Image 关联
            stmt.execute("CREATE UNIQUE INDEX IF NOT EXISTS uk_engpl_part_contrib_image " +
                    "ON ck_eng_doc_part_link(part_oid) WHERE assoc_type = 'CONTRIBUTING_IMAGE'");
        } catch (Exception e) {
            log.debug("BuildRule 索引创建跳过: {}", e.getMessage());
        }
    }

    // ==================== EPM 链接拆分（MemberLink / ReferenceLink 对齐） ====================

    /**
     * 原 {@code ck_eng_doc_ref_link} 把「装配成员」与「横向引用」混在一张表
     * （含 {@code quantity} 与 {@code ref_type}），按 Windchill {@code wt.epm.structure} 拆分（幂等）。
     *
     * <p>Windchill 的链接架构为「一个接口 + 多个独立表」，<b>没有依赖表</b>；故本方法：
     * <ol>
     *   <li>把「装配成员」性质的旧记录迁入 {@code ck_eng_doc_member_link}；</li>
     *   <li>列重命名对齐 Windchill 角色名（from/to → referenced_by/references，ref_type → reference_type）；</li>
     *   <li>补 {@code EPMDependencyLink} 公共列与被引用对象类型；</li>
     *   <li>旧引用类型归一；</li>
     *   <li>删除 {@code quantity}（用量只属 EPMMemberLink）；</li>
     *   <li>重建索引（含对齐 EPMReferenceLink 的复合索引）。</li>
     * </ol>
     */
    private void migrateRefLinkSplit(Statement stmt) {
        // 1) 迁移「装配成员」性质的旧记录（必须在改列名 / 删列之前执行）
        try {
            stmt.execute("INSERT INTO ck_eng_doc_member_link (oid, used_by_iteration_oid, uses_master_oid, quantity, " +
                    "as_stored_child_name, dep_type, required, tenant_oid, creator, created_at, updater, updated_at) " +
                    "SELECT oid, from_eng_document_oid, to_eng_document_oid, COALESCE(quantity, 1), " +
                    "NULL, 0, FALSE, tenant_oid, creator, created_at, updater, updated_at " +
                    "FROM ck_eng_doc_ref_link " +
                    "WHERE ref_type IN ('MEMBER', 'ASSEMBLY', 'STRUCTURE') " +
                    "ON CONFLICT (oid) DO NOTHING");
            stmt.execute("DELETE FROM ck_eng_doc_ref_link WHERE ref_type IN ('MEMBER', 'ASSEMBLY', 'STRUCTURE')");
        } catch (Exception e) {
            log.debug("成员链接数据拆分跳过（表为空或结构已最新）: {}", e.getMessage());
        }
        // 2) 列重命名：引用表对齐 Windchill 角色名（referencedBy / references）
        renameColumn(stmt, "ck_eng_doc_ref_link", "from_eng_document_oid", "referenced_by_oid");
        renameColumn(stmt, "ck_eng_doc_ref_link", "to_eng_document_oid", "references_oid");
        renameColumn(stmt, "ck_eng_doc_ref_link", "ref_type", "reference_type");
        // 2b) 列重命名：显式「迭代 → 主对象」（对齐 IteratedUsageLink：roleA = Iterated，roleB = Mastered）
        renameColumn(stmt, "ck_eng_doc_member_link", "used_by_oid", "used_by_iteration_oid");
        renameColumn(stmt, "ck_eng_doc_member_link", "uses_oid", "uses_master_oid");
        renameColumn(stmt, "ck_eng_doc_ref_link", "referenced_by_oid", "referenced_by_iteration_oid");
        renameColumn(stmt, "ck_eng_doc_ref_link", "references_oid", "references_master_oid");
        // 3) 补 EPMDependencyLink 公共列 + 被引用对象类型
        addColumn(stmt, "ck_eng_doc_ref_link", "references_type VARCHAR(30) NOT NULL DEFAULT 'ENG_DOCUMENT'");
        addColumn(stmt, "ck_eng_doc_ref_link", "as_stored_child_name VARCHAR(200)");
        addColumn(stmt, "ck_eng_doc_ref_link", "dep_type INTEGER NOT NULL DEFAULT 0");
        addColumn(stmt, "ck_eng_doc_ref_link", "required BOOLEAN NOT NULL DEFAULT FALSE");
        addColumn(stmt, "ck_eng_doc_ref_link", "unique_link_id BIGINT");
        // 4) 旧引用类型归一 + 默认值对齐
        try {
            stmt.execute("UPDATE ck_eng_doc_ref_link SET reference_type = 'DEPENDENCY' " +
                    "WHERE reference_type IS NULL OR reference_type IN ('REFERENCE', 'REF')");
            stmt.execute("ALTER TABLE ck_eng_doc_ref_link ALTER COLUMN reference_type SET DEFAULT 'DEPENDENCY'");
        } catch (Exception e) {
            log.debug("reference_type 归一跳过: {}", e.getMessage());
        }
        // 5) 删除 quantity：引用链接无用量概念（用量属 EPMMemberLink）
        try {
            stmt.execute("ALTER TABLE ck_eng_doc_ref_link DROP COLUMN IF EXISTS quantity");
        } catch (Exception e) {
            log.debug("quantity 列删除跳过: {}", e.getMessage());
        }
        // 6) 索引重建：清理历代旧名，按新的「迭代 → 主对象」列名建立
        for (String idx : new String[]{
                // 引用表：ck_ed_* 时代的初始名（ALTER TABLE RENAME 不改索引名，故仍带 edrl 前缀）
                "idx_edrl_from", "idx_edrl_to", "uk_edrl_pair",
                // 引用表历代旧名
                "idx_engrl_from", "idx_engrl_to", "uk_engrl_pair",
                "idx_engrl_refby", "idx_engrl_refs", "uk_engrl_refby_refs", "idx_engrl_refs_type_refby",
                // 成员表旧名
                "idx_engml_usedby", "idx_engml_uses", "uk_engml_usedby_uses"}) {
            try {
                stmt.execute("DROP INDEX IF EXISTS " + idx);
            } catch (Exception e) {
                log.debug("旧索引删除跳过 {}: {}", idx, e.getMessage());
            }
        }
        try {
            // 装配成员（EPMMemberLink）
            stmt.execute("CREATE INDEX IF NOT EXISTS idx_engml_usedby_iter ON ck_eng_doc_member_link(used_by_iteration_oid)");
            stmt.execute("CREATE INDEX IF NOT EXISTS idx_engml_uses_master ON ck_eng_doc_member_link(uses_master_oid)");
            stmt.execute("CREATE UNIQUE INDEX IF NOT EXISTS uk_engml_iter_master " +
                    "ON ck_eng_doc_member_link(used_by_iteration_oid, uses_master_oid)");
            // 横向引用（EPMReferenceLink）
            stmt.execute("CREATE INDEX IF NOT EXISTS idx_engrl_refby_iter ON ck_eng_doc_ref_link(referenced_by_iteration_oid)");
            stmt.execute("CREATE INDEX IF NOT EXISTS idx_engrl_refs_master ON ck_eng_doc_ref_link(references_master_oid)");
            stmt.execute("CREATE UNIQUE INDEX IF NOT EXISTS uk_engrl_iter_master " +
                    "ON ck_eng_doc_ref_link(referenced_by_iteration_oid, references_master_oid)");
            // 对齐 Windchill EPMReferenceLink 的 compositeIndex5：被引用方 + 引用类型 + 引用方
            stmt.execute("CREATE INDEX IF NOT EXISTS idx_engrl_refs_type_iter " +
                    "ON ck_eng_doc_ref_link(references_master_oid, reference_type, referenced_by_iteration_oid)");
            // 对齐 Windchill EPMReferenceLink 的 compositeIndex6：按引用类型检索
            stmt.execute("CREATE INDEX IF NOT EXISTS idx_engrl_reftype ON ck_eng_doc_ref_link(reference_type)");
        } catch (Exception e) {
            log.debug("链接索引创建跳过: {}", e.getMessage());
        }
    }

    /** 幂等列重命名（列不存在或已重命名时跳过）。 */
    private void renameColumn(Statement stmt, String table, String from, String to) {
        try {
            stmt.execute("ALTER TABLE " + table + " RENAME COLUMN " + from + " TO " + to);
        } catch (Exception e) {
            log.debug("列重命名跳过 {}.{} → {}: {}", table, from, to, e.getMessage());
        }
    }

    /** 幂等补列。 */
    private void addColumn(Statement stmt, String table, String columnDef) {
        try {
            stmt.execute("ALTER TABLE " + table + " ADD COLUMN IF NOT EXISTS " + columnDef);
        } catch (Exception e) {
            log.debug("补列跳过 {}.{}: {}", table, columnDef, e.getMessage());
        }
    }
}
