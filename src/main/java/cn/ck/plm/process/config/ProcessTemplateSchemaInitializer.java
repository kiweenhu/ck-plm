/*
 * Copyright (c) 2025 深圳乘恺科技有限公司
 * All rights reserved.
 *
 * @author Kiween.Hu; Roney.Liu
 */

package cn.ck.plm.process.config;

import cn.ck.plm.process.support.ProcessDeploymentSupport;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;

/**
 * 流程模板（前端设计器）表结构初始化。
 *
 * <p>对应 docs/ck-plm-flow-designer-spec.md §2.3 的后端契约：
 * <b>模板主存 DSL JSON</b>，部署时前端编译出 BPMN XML 存档快照。
 *
 * <p>两张表均为<b>业务表</b>（含 {@code tenant_oid}），由 {@code TenantStatementInterceptor}
 * 自动注入租户过滤 —— 因此未列入该拦截器的 SHARED / PLATFORM_SHARED 白名单。
 *
 * <pre>
 * ck_process_template           模板主档（一模板一行，持有最新 DSL 与部署状态）
 * ck_process_template_version   版本历史（每次保存生成一版，含 DSL 与部署产物快照）
 * ck_process_category           分组字典（清单页左侧导航；模板的 category 存分组名）
 * ck_process_entity_set         流程实例 ↔ 业务实体 关联（一行 = 集合里的一个成员）
 * </pre>
 *
 * <p>幂等：全部 {@code CREATE ... IF NOT EXISTS}，多次启动安全。
 */
@Component
public class ProcessTemplateSchemaInitializer implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(ProcessTemplateSchemaInitializer.class);

    private static final String ENTITY_SET_TABLE = "ck_process_entity_set";

    /**
     * 宿主迭代表（{@code root_type_code} → 迭代表）：迁移老数据时按迭代 oid 反查大版本用。
     *
     * <p>刻意只在迁移这一处列出映射，不去给每个宿主加"取大版本"的接口：一次性历史处理走一张
     * 静态表更轻，而这些表名在 {@code schema.sql} 里本就是稳定的。
     */
    private static final String[][] HOST_ITERATION_TABLES = {
            {"PART", "ck_part_iteration"},
            {"DOCUMENT", "ck_document_iteration"},
            {"ENG_DOCUMENT", "ck_eng_document_iteration"},
            {"FUNCTIONAL", "ck_functional_iteration"},
    };

    private final DataSource dataSource;

    public ProcessTemplateSchemaInitializer(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @Override
    public void run(String... args) {
        try (Connection conn = dataSource.getConnection();
             Statement stmt = conn.createStatement()) {
            migrateLegacyCategoryTable(stmt);
            createCategoryTable(stmt);
            // 模板表的分组列：老库的 category（分组名）→ category_oid（引用字典 oid）。
            // 必须排在 createTemplateTable 之前 —— 它建的索引打在新列上，而老库里这一列还没出现
            migrateCategoryToOid(stmt);
            createTemplateTable(stmt);
            dropLegacyTemplateColumns(stmt);
            createVersionTable(stmt);
            // 版本列迁移要排在建表之前：新列得先就位，建表/建索引才打在正确的列上
            migrateEntitySetVersionColumn(conn, stmt);
            createEntitySetTable(stmt);
            // 放在最后：引擎表相关，万一（无引擎的 profile）失败也不该拖垮建表
            alignProcessDefinitionCategory(stmt);
            log.info("流程模板表结构初始化完成");
        } catch (Exception e) {
            log.error("流程模板表结构初始化失败: {}", e.getMessage(), e);
        }
    }

    /**
     * 历史表迁移：{@code ck_workflow_category} → {@code ck_process_category}。
     *
     * <p>旧表是"流程分类"的早期实现，<b>从建表起就没有任何代码读写它</b>
     * （无实体 / Mapper / 种子数据，分类实际存在 {@code ck_process_template.category} 里）。
     * 现在分组升级为可维护的字典，沿用同一份数据容器，故<b>整表改名</b>而不是新建 ——
     * 改名保留既有行与索引，比"建新表 + 拷数据 + 删旧表"少一次数据搬运。
     *
     * <p>顺带修一个多租户缺陷：旧表把 {@code name} 做成<b>全局唯一</b>
     * （A 租户建了"研发"，B 租户就建不了），与 {@code ck_process_template}
     * 的 {@code (key, tenant_oid)} 口径不一致。这里把旧的唯一约束统一摘掉，
     * 由 {@code uk_process_category_name_tenant} 按租户重建。
     *
     * <p>幂等：表已改名 / 已无唯一约束 / 索引已删时全部为无操作。
     */
    private void migrateLegacyCategoryTable(Statement stmt) throws Exception {
        stmt.executeUpdate(
                "DO $$ BEGIN "
                        + "IF to_regclass('ck_workflow_category') IS NOT NULL "
                        + "AND to_regclass('ck_process_category') IS NULL THEN "
                        + "  ALTER TABLE ck_workflow_category RENAME TO ck_process_category; "
                        + "END IF; "
                        + "END $$");
        stmt.executeUpdate(
                "DO $$ DECLARE c record; BEGIN "
                        + "IF to_regclass('ck_process_category') IS NOT NULL THEN "
                        + "  FOR c IN SELECT conname FROM pg_constraint "
                        + "           WHERE conrelid = 'ck_process_category'::regclass AND contype = 'u' "
                        + "  LOOP "
                        + "    EXECUTE 'ALTER TABLE ck_process_category DROP CONSTRAINT ' || quote_ident(c.conname); "
                        + "  END LOOP; "
                        + "END IF; "
                        + "END $$");
        // 旧表的租户索引名（idx_wfc_tenant）随新表重建为 idx_process_category_tenant
        stmt.executeUpdate("DROP INDEX IF EXISTS idx_wfc_tenant");
        // 主键索引名同样会残留在旧表名下（改表名不会改约束/索引名），一并归正 —— 否则日后的
        // 读者会以为还有一张 ck_workflow_category
        stmt.executeUpdate(
                "DO $$ BEGIN "
                        + "IF to_regclass('ck_workflow_category_pkey') IS NOT NULL "
                        + "AND to_regclass('ck_process_category_pkey') IS NULL THEN "
                        + "  ALTER INDEX ck_workflow_category_pkey RENAME TO ck_process_category_pkey; "
                        + "END IF; "
                        + "END $$");
    }

    /**
     * 流程分组表 —— 清单页左侧的分组字典（先建分组 → 选分组 → 组内设计流程）。
     *
     * <p>注意最后那段 {@code ADD COLUMN IF NOT EXISTS}：由旧表<b>改名而来</b>的容器
     * 只有 {@code oid / name / sort_order / tenant_oid} 四列，缺少审计列与说明列，
     * 补齐后 Mapper 的 INSERT 才能成立（幂等，新建表时是无操作）。
     */
    private void createCategoryTable(Statement stmt) throws Exception {
        stmt.executeUpdate(
                "CREATE TABLE IF NOT EXISTS ck_process_category ("
                        + "  oid VARCHAR(64) PRIMARY KEY,"
                        + "  name VARCHAR(64) NOT NULL,"
                        + "  sort_order INTEGER NOT NULL DEFAULT 0,"
                        + "  description VARCHAR(512),"
                        + "  tenant_oid VARCHAR(64) NOT NULL,"
                        + "  creator VARCHAR(128),"
                        + "  created_at TIMESTAMP,"
                        + "  updater VARCHAR(128),"
                        + "  updated_at TIMESTAMP"
                        + ")");
        String[] legacyColumns = {
                "ALTER TABLE ck_process_category ADD COLUMN IF NOT EXISTS description VARCHAR(512)",
                "ALTER TABLE ck_process_category ADD COLUMN IF NOT EXISTS creator VARCHAR(128)",
                "ALTER TABLE ck_process_category ADD COLUMN IF NOT EXISTS created_at TIMESTAMP",
                "ALTER TABLE ck_process_category ADD COLUMN IF NOT EXISTS updater VARCHAR(128)",
                "ALTER TABLE ck_process_category ADD COLUMN IF NOT EXISTS updated_at TIMESTAMP",
        };
        for (String ddl : legacyColumns) {
            stmt.executeUpdate(ddl);
        }
        // 名称在租户内唯一（不能全局唯一：多租户下不同租户可以有同名分组）
        stmt.executeUpdate(
                "CREATE UNIQUE INDEX IF NOT EXISTS uk_process_category_name_tenant "
                        + "ON ck_process_category (name, tenant_oid)");
        stmt.executeUpdate(
                "CREATE INDEX IF NOT EXISTS idx_process_category_tenant "
                        + "ON ck_process_category (tenant_oid)");
    }

    private void createTemplateTable(Statement stmt) throws Exception {
        stmt.executeUpdate(
                "CREATE TABLE IF NOT EXISTS ck_process_template ("
                        + "  oid VARCHAR(64) PRIMARY KEY,"
                        + "  key VARCHAR(128) NOT NULL,"
                        + "  name VARCHAR(255) NOT NULL,"
                        + "  display_name VARCHAR(255),"
                        // 所属分组：引用 ck_process_category.oid（不是分组名，改显示名不该牵动模板）
                        + "  category_oid VARCHAR(64),"
                        + "  description TEXT,"
                        // 最新版本的 DSL（列表/编辑直取，避免每次 join 版本表）
                        + "  dsl_json TEXT,"
                        + "  latest_version INTEGER NOT NULL DEFAULT 0,"
                        + "  enabled BOOLEAN NOT NULL DEFAULT TRUE,"
                        // 已成功部署到 Flowable 的版本与部署标识
                        + "  deployed_version INTEGER,"
                        + "  deployment_id VARCHAR(64),"
                        + "  process_definition_id VARCHAR(128),"
                        + "  deployed_at TIMESTAMP,"
                        + "  tenant_oid VARCHAR(64) NOT NULL,"
                        + "  creator VARCHAR(128),"
                        + "  created_at TIMESTAMP,"
                        + "  updater VARCHAR(128),"
                        + "  updated_at TIMESTAMP"
                        + ")");
        stmt.executeUpdate(
                "CREATE UNIQUE INDEX IF NOT EXISTS uk_process_template_key_tenant "
                        + "ON ck_process_template (key, tenant_oid)");
        stmt.executeUpdate(
                "CREATE INDEX IF NOT EXISTS idx_process_template_tenant "
                        + "ON ck_process_template (tenant_oid)");
        // 分组过滤是清单页的主查询（左侧选中分组 → 右侧该组流程）
        stmt.executeUpdate(
                "CREATE INDEX IF NOT EXISTS idx_process_template_category_oid "
                        + "ON ck_process_template (category_oid)");
    }

    /**
     * 模板表的分组列：{@code category}（分组名）→ {@code category_oid}（引用字典 oid）。
     *
     * <p>为什么改：分组是清单页的导航维度、将来还要挂权限与外部引用，引用必须稳定 ——
     * 改个显示名不该牵动任何一张流程模板（原先"改字典名 + 同步模板名"是一对必须一起成功
     * 的操作，现在从结构上消失了）。模板表也不再保留名称冗余。
     *
     * <p>三步（幂等，顺序不能颠倒）：
     * <ol>
     *   <li>加列 {@code category_oid}；</li>
     *   <li>按 {@code (name, tenant_oid)} 把老的分组名回填成字典 oid
     *       —— 回填不到的行（老库里自由输入、字典中不存在）就留空，界面上归入「未分类」；</li>
     *   <li>删掉老列与它的索引。</li>
     * </ol>
     *
     * <p>表还不存在（全新库）时直接返回：那时候建表语句本身就已经是新结构。
     */
    private void migrateCategoryToOid(Statement stmt) throws Exception {
        stmt.executeUpdate(
                "DO $$ BEGIN "
                        + "IF to_regclass('ck_process_template') IS NULL THEN RETURN; END IF; "
                        + "ALTER TABLE ck_process_template "
                        + "  ADD COLUMN IF NOT EXISTS category_oid VARCHAR(64); "
                        + "IF EXISTS (SELECT 1 FROM information_schema.columns "
                        + "           WHERE table_name = 'ck_process_template' "
                        + "             AND column_name = 'category') THEN "
                        + "  UPDATE ck_process_template t SET category_oid = c.oid "
                        + "    FROM ck_process_category c "
                        + "   WHERE t.category_oid IS NULL "
                        + "     AND t.category = c.name "
                        + "     AND t.tenant_oid = c.tenant_oid; "
                        + "  ALTER TABLE ck_process_template DROP COLUMN category; "
                        + "END IF; "
                        + "END $$");
        stmt.executeUpdate("DROP INDEX IF EXISTS idx_process_template_category");
    }

    /**
     * 清理模板表上已废弃的列。
     *
     * <p>{@code primary_object_type}：原「主业务对象」字段。一个流程可能关联多个业务实体，
     * 单一 code 表达不了 —— 该关联改由 {@code ProcessEntitySet}（流程关联的业务实体集合）承担，
     * 故把列一并删掉（只停用不删会留下"字段还在、却没人读写"的死数据，
     * 下一个读代码的人还得先确认它是不是活的）。
     *
     * <p>幂等：{@code DROP COLUMN IF EXISTS}，列不存在时是无操作。
     */
    private void dropLegacyTemplateColumns(Statement stmt) throws Exception {
        stmt.executeUpdate("ALTER TABLE ck_process_template DROP COLUMN IF EXISTS primary_object_type");
    }

    /**
     * 流程实例 ↔ 业务实体 关联表（{@code ProcessEntitySet}）—— 一行 = 集合里的一个成员。
     *
     * <p>它取代了模板表上原来的 {@code primary_object_type}：一个流程可以关联多个业务实体，
     * 单一 code 表达不了（见 {@link #dropLegacyTemplateColumns}）。
     *
     * <p>实体引用：无版本对象只填 {@code entity_oid}；带版本对象填 {@code entity_version}
     * ——业务对象的<b>大版本</b>（{@code revision}，如 A），同时保留 {@code entity_oid} = 主对象 oid
     * （便于"某对象参与过哪些流程"一次查全）。列名刻意不叫 {@code version}：本模块里还有
     * "流程模板版本 / 流程定义版本"，{@code entity_version} 才一眼看出是<b>业务对象的版本</b>。
     *
     * <p><b>为什么记大版本而不是迭代 oid</b>：流程针对的是"某个大版本"，而大版本下还会继续产出
     * 小版本（检出 → A.2 → 检入 → A.3 …）。记迭代 oid 的话，对象每推进一个小版本，这份关联就
     * 指向了一个已经过时的版本；记大版本则天然稳定，需要具体版本时统一解析为"该大版本当前的最新小版本"。
     * 老库的历史列由 {@link #migrateEntitySetVersionColumn} 按此口径迁移。
     *
     * <p>唯一键用<b>表达式索引</b> {@code COALESCE(entity_version, '')}：无版本对象的版本列为
     * {@code NULL}，而 PostgreSQL 的唯一索引把多个 NULL 视为互不相同 —— 不套 COALESCE，
     * "同一实例 + 同一无版本对象"就能重复入行，幂等写入会失效。
     */
    private void createEntitySetTable(Statement stmt) throws Exception {
        stmt.executeUpdate(
                "CREATE TABLE IF NOT EXISTS ck_process_entity_set ("
                        + "  oid VARCHAR(64) PRIMARY KEY,"
                        // 发起流程时传入的业务标识（一般就是业务对象 oid）
                        + "  business_key VARCHAR(128),"
                        + "  process_instance_id VARCHAR(64) NOT NULL,"
                        // 业务对象 oid（带版本对象时为主对象 oid）
                        + "  entity_oid VARCHAR(64) NOT NULL,"
                        // 业务对象大版本（revision，如 A）：带版本对象才有，无版本对象为 NULL
                        + "  entity_version VARCHAR(64),"
                        + "  type_code VARCHAR(50),"
                        // 能力宿主（type_definition.root_type_code，如 PART / DOCUMENT）
                        + "  root_type_code VARCHAR(50),"
                        + "  tenant_oid VARCHAR(64) NOT NULL,"
                        + "  creator VARCHAR(128),"
                        + "  created_at TIMESTAMP,"
                        + "  updater VARCHAR(128),"
                        + "  updated_at TIMESTAMP"
                        + ")");
        stmt.executeUpdate(
                "CREATE UNIQUE INDEX IF NOT EXISTS uk_pes_instance_entity "
                        + "ON ck_process_entity_set (process_instance_id, entity_oid, COALESCE(entity_version, ''))");
        stmt.executeUpdate("CREATE INDEX IF NOT EXISTS idx_pes_instance ON ck_process_entity_set (process_instance_id)");
        stmt.executeUpdate("CREATE INDEX IF NOT EXISTS idx_pes_entity ON ck_process_entity_set (entity_oid)");
        stmt.executeUpdate("CREATE INDEX IF NOT EXISTS idx_pes_entity_version ON ck_process_entity_set (entity_version)");
        stmt.executeUpdate("CREATE INDEX IF NOT EXISTS idx_pes_tenant ON ck_process_entity_set (tenant_oid)");
    }

    /**
     * 关联表版本列的两步迁移（都幂等）：
     * <ol>
     *   <li>{@code entity_iteration_oid}（发起那一刻的迭代 oid）→ 大版本（见
     *       {@link #migrateLegacyIterationColumn}）；</li>
     *   <li>列名归正 {@code version} → {@code entity_version}：本模块里还有"流程模板版本 /
     *       流程定义版本"，光写 version 分不清是谁的版本。</li>
     * </ol>
     *
     * <p>顺序不能颠倒：第 ② 步改的正是第 ① 步建出来的列。
     */
    private void migrateEntitySetVersionColumn(Connection conn, Statement stmt) throws Exception {
        int filled = migrateLegacyIterationColumn(conn, stmt);
        if (filled > 0) {
            log.info("流程实体关联表历史数据已回填大版本: {} 行", filled);
        }
        if (columnExists(conn, ENTITY_SET_TABLE, "version")) {
            stmt.executeUpdate("ALTER TABLE " + ENTITY_SET_TABLE
                    + " RENAME COLUMN version TO entity_version");
            // 索引名要跟着走（改名不会自动改索引名）；唯一键的表达式会随列自动更新
            stmt.executeUpdate("ALTER INDEX IF EXISTS idx_pes_version RENAME TO idx_pes_entity_version");
            log.info("流程实体关联表版本列已归正: version → entity_version(业务对象大版本)");
        }
    }

    /**
     * 老库迁移：{@code entity_iteration_oid}（迭代 oid）→ 大版本列。
     *
     * <p>顺序不能颠倒 —— <b>先回填，再退役老列</b>：
     * <ol>
     *   <li>回填按迭代 oid 去各宿主迭代表取 {@code revision}：老行的迭代 oid 是发起那一刻的小版本，
     *       它所属的大版本才是新口径要的值；</li>
     *   <li>再 {@code DROP COLUMN} 退役老列（顺带带走那个按老列建的索引）。</li>
     * </ol>
     *
     * <p>回填不到的行（宿主迭代表里已查无此迭代）版本落 {@code NULL}：与"无版本对象"同形，
     * 比编一个值更诚实 —— 真要再取版本，只能回业务对象按它当前的大版本补。
     *
     * <p>某个宿主的迭代表不存在时跳过它（老库可能从没建过该宿主的表），
     * 不让一条缺失的表把整条迁移拖失败。
     *
     * @return 回填行数；老列不存在（新库 / 已迁过）时返回 0（无操作）
     */
    private int migrateLegacyIterationColumn(Connection conn, Statement stmt) throws Exception {
        if (!columnExists(conn, ENTITY_SET_TABLE, "entity_iteration_oid")) {
            return 0;
        }
        stmt.executeUpdate("ALTER TABLE " + ENTITY_SET_TABLE + " ADD COLUMN IF NOT EXISTS version VARCHAR(64)");
        int filled = 0;
        for (String[] host : HOST_ITERATION_TABLES) {
            if (!tableExists(conn, host[1])) {
                continue;
            }
            filled += stmt.executeUpdate(
                    "UPDATE " + ENTITY_SET_TABLE + " SET version = i.revision FROM " + host[1] + " i "
                            + "WHERE i.oid = " + ENTITY_SET_TABLE + ".entity_iteration_oid "
                            + "  AND " + ENTITY_SET_TABLE + ".version IS NULL");
        }
        stmt.executeUpdate("ALTER TABLE " + ENTITY_SET_TABLE + " DROP COLUMN entity_iteration_oid");
        stmt.executeUpdate("DROP INDEX IF EXISTS idx_pes_iteration");
        return filled;
    }

    private static boolean columnExists(Connection conn, String table, String column) throws Exception {
        try (ResultSet rs = conn.getMetaData().getColumns(null, null, table, column)) {
            return rs.next();
        }
    }

    private static boolean tableExists(Connection conn, String table) throws Exception {
        try (ResultSet rs = conn.getMetaData().getTables(null, null, table, null)) {
            return rs.next();
        }
    }

    /**
     * 对齐流程定义 / 部署的分组（{@code act_re_procdef.category_}、{@code act_re_deployment.category_}）。
     *
     * <p>引擎的 category 默认会落到 BPMN 的 targetNamespace（一串 URL）—— 那是设计期的 XML
     * 命名空间，不是给人看的分组。新部署已显式设置（见 {@code ProcessTemplateServiceImpl#deploy}
     * 与 {@code ProcessDeploymentSupport#ensureBuiltInDeployed}），这里是给<b>历史定义</b>补齐：
     * <ul>
     *   <li>能对上模板的定义 → 模板分组的<b>名称</b>（按 {@code key + tenant} 对上，
     *       与模板引用分组 oid 的口径一致）；</li>
     *   <li>内置流程 → 固定分组名（见 {@code ProcessDeploymentSupport#builtInCategory()}）。</li>
     * </ul>
     *
     * <p>放在启动期而不是只给一份手工脚本：任何一套库都靠一次启动自愈；且这里执行得早 ——
     * 引擎还没查过这些定义，定义缓存里不会留下改之前的旧值（否则要再重启一次才生效）。
     *
     * <p>幂等：只更新与目标值不一致的行。对不上模板的定义（例如测试遗留）保持原样 ——
     * 不猜它属于哪个分组。
     */
    private void alignProcessDefinitionCategory(Statement stmt) throws Exception {
        int definitions = stmt.executeUpdate(
                "UPDATE act_re_procdef d SET category_ = c.name "
                        + "FROM ck_process_template t "
                        + "JOIN ck_process_category c ON c.oid = t.category_oid "
                        + "WHERE d.key_ = t.key AND d.tenant_id_ = t.tenant_oid "
                        + "  AND d.category_ IS DISTINCT FROM c.name");
        int deployments = stmt.executeUpdate(
                "UPDATE act_re_deployment dep SET category_ = c.name "
                        + "FROM ck_process_template t "
                        + "JOIN ck_process_category c ON c.oid = t.category_oid "
                        + "WHERE dep.key_ = t.key AND dep.tenant_id_ = t.tenant_oid "
                        + "  AND dep.category_ IS DISTINCT FROM c.name");
        String builtIn = ProcessDeploymentSupport.builtInCategory();
        for (String key : ProcessDeploymentSupport.builtInKeys()) {
            definitions += stmt.executeUpdate(
                    "UPDATE act_re_procdef SET category_ = '" + builtIn + "' WHERE key_ = '" + key + "'"
                            + "  AND category_ IS DISTINCT FROM '" + builtIn + "'");
            // 更早的内置部署没写 key_，按「定义 → 部署」反查它所属的部署
            deployments += stmt.executeUpdate(
                    "UPDATE act_re_deployment SET category_ = '" + builtIn + "' "
                            + "WHERE id_ IN (SELECT deployment_id_ FROM act_re_procdef WHERE key_ = '" + key + "') "
                            + "  AND category_ IS DISTINCT FROM '" + builtIn + "'");
        }
        if (definitions + deployments > 0) {
            log.info("流程定义分组已对齐: 定义 {} 行、部署 {} 行", definitions, deployments);
        }
    }

    private void createVersionTable(Statement stmt) throws Exception {
        stmt.executeUpdate(
                "CREATE TABLE IF NOT EXISTS ck_process_template_version ("
                        + "  oid VARCHAR(64) PRIMARY KEY,"
                        + "  template_oid VARCHAR(64) NOT NULL,"
                        + "  version INTEGER NOT NULL,"
                        + "  dsl_json TEXT NOT NULL,"
                        // 部署时前端编译产物快照（未部署为 NULL）
                        + "  bpmn_xml TEXT,"
                        + "  change_note VARCHAR(512),"
                        + "  deployed BOOLEAN NOT NULL DEFAULT FALSE,"
                        + "  deployment_id VARCHAR(64),"
                        + "  tenant_oid VARCHAR(64) NOT NULL,"
                        + "  creator VARCHAR(128),"
                        + "  created_at TIMESTAMP"
                        + ")");
        stmt.executeUpdate(
                "CREATE UNIQUE INDEX IF NOT EXISTS uk_process_template_version "
                        + "ON ck_process_template_version (template_oid, version)");
        stmt.executeUpdate(
                "CREATE INDEX IF NOT EXISTS idx_process_template_version_tenant "
                        + "ON ck_process_template_version (tenant_oid)");
    }
}
