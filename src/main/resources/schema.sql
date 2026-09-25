-- =================================================================
--  主键规范：所有表统一使用 oid CHAR(36) PRIMARY KEY（UUID v4）
--  禁止使用自增 id (BIGSERIAL / AUTO_INCREMENT) 作为主键
--  业务唯一标识使用 code VARCHAR(50) UNIQUE NOT NULL
-- =================================================================

-- ==================== 生命周期状态 ====================
-- oid 为全局唯一主键，code 为业务唯一键
CREATE TABLE IF NOT EXISTS ck_lifecycle_status (
    oid          CHAR(36)     PRIMARY KEY,
    code         VARCHAR(50)  NOT NULL UNIQUE,
    name         VARCHAR(200),
    display_name VARCHAR(200),
    tenant_oid   CHAR(36),
    creator      VARCHAR(100),
    created_at   TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updater      VARCHAR(100),
    updated_at   TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- ==================== 生命周期模板 ====================
CREATE TABLE IF NOT EXISTS ck_lifecycle_template (
    oid                CHAR(36)     PRIMARY KEY,
    code               VARCHAR(50)  NOT NULL UNIQUE,
    name               VARCHAR(100) NOT NULL,
    description        VARCHAR(500),
    is_active          BOOLEAN      NOT NULL DEFAULT TRUE,
    initial_state_code VARCHAR(50),
    tenant_oid         CHAR(36),
    creator            VARCHAR(100),
    created_at         TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updater            VARCHAR(100),
    updated_at         TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- ==================== 生命周期模板子版本 ====================
CREATE TABLE IF NOT EXISTS ck_lifecycle_template_iteration (
    oid                  CHAR(36)     PRIMARY KEY,
    master_oid           CHAR(36)     NOT NULL REFERENCES ck_lifecycle_template(oid) ON DELETE CASCADE,
    revision             VARCHAR(10)  NOT NULL DEFAULT 'A',
    iteration            INTEGER      NOT NULL DEFAULT 1,
    display_version      VARCHAR(20),
    checked_out          BOOLEAN      NOT NULL DEFAULT FALSE,
    checked_out_by       VARCHAR(100),
    checked_out_comment  VARCHAR(500),
    latest               BOOLEAN      NOT NULL DEFAULT TRUE,
    derived_from_oid     CHAR(36),
    derived_at           TIMESTAMP,
    status               VARCHAR(50),
    tenant_oid           CHAR(36),
    creator              VARCHAR(100),
    created_at           TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updater              VARCHAR(100),
    updated_at           TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_lti_master  ON ck_lifecycle_template_iteration(master_oid);
CREATE INDEX IF NOT EXISTS idx_lti_latest  ON ck_lifecycle_template_iteration(master_oid, latest);

-- 生命周期模板子版本 → 状态关联
CREATE TABLE IF NOT EXISTS ck_lifecycle_template_state (
    oid                 CHAR(36)     PRIMARY KEY,
    iteration_oid       CHAR(36)     NOT NULL,
    status_code         VARCHAR(50)  NOT NULL,
    status_display_name VARCHAR(100),
    sort_order          INTEGER      NOT NULL DEFAULT 0,
    tenant_oid          CHAR(36),
    FOREIGN KEY (iteration_oid) REFERENCES ck_lifecycle_template_iteration(oid) ON DELETE CASCADE
);
-- 生命周期模板子版本 → 状态流转规则
CREATE TABLE IF NOT EXISTS ck_lifecycle_template_transition (
    oid              CHAR(36)     PRIMARY KEY,
    iteration_oid    CHAR(36)     NOT NULL,
    from_status_code VARCHAR(50)  NOT NULL,
    to_status_code   VARCHAR(50)  NOT NULL,
    transition_type  VARCHAR(20)  NOT NULL DEFAULT 'PROMOTE',
    tenant_oid       CHAR(36),
    FOREIGN KEY (iteration_oid) REFERENCES ck_lifecycle_template_iteration(oid) ON DELETE CASCADE
);

-- 类型-生命周期状态-流程模板 关联（1:1）。
-- 三个维度都不能省：
--   类型：同一生命周期模板（如 STANDARD）会被多个类型复用，各自可绑不同流程；
--   版本：业务对象迭代固化的是 lifecycle_template_iteration_oid（实例"出生"时用哪一版模板），
--         配置挂同一层，运行期才能按实例自带的版本精确解析，而不被后续改配置追溯性改写；
--   租户：配置归属"保存时当前用户的租户"，各租户各配各的（唯一键含 tenant_oid）；
--         平台租户的行作共享默认，读取时本租户优先。
-- process_template_oid 为跨模块软引用（ck_process_template.oid），不加外键。
-- 表名前身：ck_lifecycle_state_process（由软类型模块的建表器整表改名而来）。
CREATE TABLE IF NOT EXISTS ck_type_lifecycle_state_process_link (
    oid                               VARCHAR(64)  PRIMARY KEY,
    type_oid                          CHAR(36)     NOT NULL,
    lifecycle_template_iteration_oid  CHAR(36)     NOT NULL,
    status_code                       VARCHAR(50)  NOT NULL,
    process_template_oid              VARCHAR(64)  NOT NULL,
    tenant_oid                        CHAR(36),
    creator                           VARCHAR(128),
    created_at                        TIMESTAMP,
    updater                           VARCHAR(128),
    updated_at                        TIMESTAMP
);
CREATE UNIQUE INDEX IF NOT EXISTS uk_tlspl_tenant_type_iteration_status
    ON ck_type_lifecycle_state_process_link(tenant_oid, type_oid, lifecycle_template_iteration_oid, status_code);
CREATE INDEX IF NOT EXISTS idx_tlspl_type    ON ck_type_lifecycle_state_process_link(type_oid);
CREATE INDEX IF NOT EXISTS idx_tlspl_process ON ck_type_lifecycle_state_process_link(process_template_oid);

-- ==================== 编码规则主表 ====================
-- oid 为全局唯一主键，code 为业务唯一键
CREATE TABLE IF NOT EXISTS ck_number (
    oid         CHAR(36)     PRIMARY KEY,
    code        VARCHAR(50)  NOT NULL UNIQUE,
    name        VARCHAR(100) NOT NULL,
    description VARCHAR(500),
    enabled     BOOLEAN      NOT NULL DEFAULT TRUE,
    tenant_oid  CHAR(36),
    creator     VARCHAR(100),
    created_at  TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updater     VARCHAR(100),
    updated_at  TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- ==================== 版本规则定义 ====================
-- 简化的版本规则表，支持模板化规则定义
-- 规则定义格式示例: (A,B,C,D,E,F,G,H), (YYYYMMDD)-(SEQ:6), (PREFIX:DOC)-(SEQ:4)
CREATE TABLE IF NOT EXISTS ck_version_rule (
    oid               CHAR(36)     PRIMARY KEY,
    name              VARCHAR(100) NOT NULL,
    code              VARCHAR(50)  NOT NULL UNIQUE,
    rule_definition   VARCHAR(500) NOT NULL,
    description       VARCHAR(500),
    applicable_type   VARCHAR(50),
    sequence_value    BIGINT       NOT NULL DEFAULT 0,
    enabled           BOOLEAN      NOT NULL DEFAULT TRUE,
    tenant_oid        CHAR(36),
    creator           VARCHAR(100),
    created_at        TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updater           VARCHAR(100),
    updated_at        TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_version_rule_code ON ck_version_rule(code);
CREATE INDEX IF NOT EXISTS idx_version_rule_type ON ck_version_rule(applicable_type);

-- ==================== 类型-版本规则关联 ====================
-- 记录数据对象（TypeDefinition）选择的版本编码规则
-- type_oid: 关联 ck_type_definition.oid，每个类型只能绑定一个版本规则
-- version_rule_code: 关联 ck_version_rule.code（业务唯一键）
CREATE TABLE IF NOT EXISTS ck_type_version_rule_link (
    oid               CHAR(36)     PRIMARY KEY,
    type_oid          CHAR(36)     NOT NULL,
    version_rule_code VARCHAR(50)  NOT NULL,
    tenant_oid        CHAR(36),
    creator           VARCHAR(100),
    created_at        TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updater           VARCHAR(100),
    updated_at        TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE (type_oid)
);

CREATE INDEX IF NOT EXISTS idx_tvrl_type ON ck_type_version_rule_link(type_oid);
CREATE INDEX IF NOT EXISTS idx_tvrl_rule ON ck_type_version_rule_link(version_rule_code);

-- ==================== 类型-生命周期模板关联 ====================
-- 记录数据对象（TypeDefinition）绑定的生命周期模板
-- type_oid: 关联 ck_type_definition.oid，每个类型只能绑定一个生命周期模板
-- lifecycle_template_code: 关联 ck_lifecycle_template.code（业务唯一键）
CREATE TABLE IF NOT EXISTS ck_type_lifecycle_template_link (
    oid                     CHAR(36)     PRIMARY KEY,
    type_oid                CHAR(36)     NOT NULL,
    lifecycle_template_code VARCHAR(50)  NOT NULL,
    tenant_oid              CHAR(36),
    creator                 VARCHAR(100),
    created_at              TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updater                 VARCHAR(100),
    updated_at              TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE (type_oid)
);

CREATE INDEX IF NOT EXISTS idx_tltl_type ON ck_type_lifecycle_template_link(type_oid);
CREATE INDEX IF NOT EXISTS idx_tltl_tmpl ON ck_type_lifecycle_template_link(lifecycle_template_code);

-- ==================== 类型-编码规则关联 ====================
-- 记录数据对象（TypeDefinition）选择的编码规则
-- type_oid: 关联 ck_type_definition.oid，每个类型只能绑定一个编码规则
-- number_rule_code: 关联 ck_number.code（业务唯一键）
CREATE TABLE IF NOT EXISTS ck_type_number_rule_link (
    oid               CHAR(36)     PRIMARY KEY,
    type_oid          CHAR(36)     NOT NULL,
    number_rule_code  VARCHAR(50)  NOT NULL,
    tenant_oid        CHAR(36),
    creator           VARCHAR(100),
    created_at        TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updater           VARCHAR(100),
    updated_at        TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE (type_oid)
);

CREATE INDEX IF NOT EXISTS idx_tnrl_type ON ck_type_number_rule_link(type_oid);
CREATE INDEX IF NOT EXISTS idx_tnrl_rule ON ck_type_number_rule_link(number_rule_code);

-- ==================== 类型-分类关联 ====================
-- 记录数据对象（TypeDefinition）绑定的分类节点
-- type_oid: 关联 ck_type_definition.oid，每个类型只能绑定一个分类
-- classification_oid: 关联 ck_classification.oid
CREATE TABLE IF NOT EXISTS ck_type_classification_link (
    oid                CHAR(36)     PRIMARY KEY,
    type_oid           CHAR(36)     NOT NULL,
    classification_oid CHAR(36)     NOT NULL,
    tenant_oid         CHAR(36),
    creator            VARCHAR(100),
    created_at         TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updater            VARCHAR(100),
    updated_at         TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE (type_oid)
);

CREATE INDEX IF NOT EXISTS idx_tcl_type ON ck_type_classification_link(type_oid);
CREATE INDEX IF NOT EXISTS idx_tcl_cls  ON ck_type_classification_link(classification_oid);

-- ==================== 编码规则段定义 ====================
-- oid 为全局唯一主键，rule_code 引用 ck_number.code（唯一业务键）
-- segment_type 取值: CONST | SEPARATOR | YEAR | MONTH | DAY | SERIAL
CREATE TABLE IF NOT EXISTS ck_number_segment (
    oid           CHAR(36)     PRIMARY KEY,
    rule_code     VARCHAR(50)  NOT NULL REFERENCES ck_number(code) ON DELETE CASCADE,
    sort_order    INTEGER      NOT NULL,
    segment_type  VARCHAR(20)  NOT NULL,
    fixed_value   VARCHAR(100),
    date_format   VARCHAR(20),
    serial_length INTEGER,
    serial_start  INTEGER      DEFAULT 1,
    current_value INTEGER      DEFAULT 0,
    description   VARCHAR(200),
    config        TEXT,
    creator       VARCHAR(100),
    created_at    TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updater       VARCHAR(100),
    updated_at    TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_number_segment_rule ON ck_number_segment(rule_code, sort_order);

-- ==================== 视图定义 ====================
-- oid 为全局唯一主键，code 为业务唯一键
CREATE TABLE IF NOT EXISTS ck_view (
    oid         CHAR(36)     PRIMARY KEY,
    code        VARCHAR(50)  NOT NULL UNIQUE,
    name        VARCHAR(100) NOT NULL,
    description VARCHAR(500),
    sort_order  INTEGER      NOT NULL DEFAULT 0,
    enabled     BOOLEAN      NOT NULL DEFAULT TRUE,
    tenant_oid  CHAR(36),
    creator     VARCHAR(100),
    created_at  TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updater     VARCHAR(100),
    updated_at  TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- ==================== 视图切换规则 ====================
-- oid 为全局唯一主键；from_view_code / to_view_code 引用 ck_view.code（唯一业务键）
-- 定义从 from_view_code 切换到 to_view_code 的规则
CREATE TABLE IF NOT EXISTS ck_view_transition (
    oid                   CHAR(36)     PRIMARY KEY,
    from_view_code        VARCHAR(50)  NOT NULL REFERENCES ck_view(code) ON DELETE CASCADE,
    to_view_code          VARCHAR(50)  NOT NULL REFERENCES ck_view(code) ON DELETE CASCADE,
    condition_status      VARCHAR(50),   -- 需要满足的生命周期状态编码（为空=无条件）
    condition_view_latest BOOLEAN      NOT NULL DEFAULT TRUE,
    description           VARCHAR(500),
    sort_order            INTEGER      NOT NULL DEFAULT 0,
    enabled               BOOLEAN      NOT NULL DEFAULT TRUE,
    tenant_oid            CHAR(36),
    creator               VARCHAR(100),
    created_at            TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updater               VARCHAR(100),
    updated_at            TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_vt_from_view ON ck_view_transition(from_view_code);
CREATE INDEX IF NOT EXISTS idx_vt_from_to ON ck_view_transition(from_view_code, to_view_code);

-- ==================== 组织架构 ====================
-- oid 为全局唯一主键，code 为业务唯一键，parent_oid 自引用实现树形结构
CREATE TABLE IF NOT EXISTS ck_organization (
    oid          CHAR(36)     PRIMARY KEY,
    code         VARCHAR(50)  NOT NULL,
    name         VARCHAR(100) NOT NULL,
    parent_oid   CHAR(36),
    description  VARCHAR(500),
    enabled      BOOLEAN      NOT NULL DEFAULT TRUE,
    tenant_oid   CHAR(36),
    creator      VARCHAR(100),
    created_at   TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updater      VARCHAR(100),
    updated_at   TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_org_parent ON ck_organization(parent_oid);
CREATE UNIQUE INDEX IF NOT EXISTS idx_org_code_tenant ON ck_organization(code, tenant_oid);

-- ==================== 用户 ====================
-- oid 为全局唯一主键，username 为登录唯一键，org_oid 关联组织
CREATE TABLE IF NOT EXISTS ck_user (
    oid          CHAR(36)     PRIMARY KEY,
    username     VARCHAR(50)  NOT NULL UNIQUE,
    password     VARCHAR(200) NOT NULL,
    display_name VARCHAR(100),
    email        VARCHAR(100),
    phone        VARCHAR(30),
    org_oid      CHAR(36)     REFERENCES ck_organization(oid) ON DELETE SET NULL,
    enabled      BOOLEAN      NOT NULL DEFAULT TRUE,
    locked       BOOLEAN      NOT NULL DEFAULT FALSE,
    tenant_oid   CHAR(36),
    creator      VARCHAR(100),
    created_at   TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updater      VARCHAR(100),
    updated_at   TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_user_org ON ck_user(org_oid);

-- ==================== 角色 ====================
-- oid 为全局唯一主键，code 为角色编码唯一键
-- role_type 取值: PLATFORM（平台级角色，系统初始化导入，不可编辑/删除）| BUSINESS（自定义业务流程角色，可自由维护）
CREATE TABLE IF NOT EXISTS ck_role (
    oid          CHAR(36)     PRIMARY KEY,
    code         VARCHAR(50)  NOT NULL UNIQUE,
    name         VARCHAR(100) NOT NULL,
    description  VARCHAR(500),
    role_type    VARCHAR(20)  NOT NULL DEFAULT 'BUSINESS',
    tenant_oid   CHAR(36),
    creator      VARCHAR(100),
    created_at   TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updater      VARCHAR(100),
    updated_at   TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- ==================== 角色成员关联 ====================
-- oid 为全局唯一主键，user_oid / role_oid 级联删除
CREATE TABLE IF NOT EXISTS ck_role_member (
    oid          CHAR(36)     PRIMARY KEY,
    user_oid     CHAR(36)     NOT NULL REFERENCES ck_user(oid) ON DELETE CASCADE,
    role_oid     CHAR(36)     NOT NULL REFERENCES ck_role(oid) ON DELETE CASCADE,
    tenant_oid   CHAR(36),
    creator      VARCHAR(100),
    created_at   TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updater      VARCHAR(100),
    updated_at   TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE UNIQUE INDEX IF NOT EXISTS idx_rm_unique ON ck_role_member(user_oid, role_oid);

-- ==================== 流程分组（分类字典） ====================
-- 流程清单页的第一层导航：先建分组 → 选中分组 → 在组内设计流程。
-- 名称在【租户内】唯一（不是全局唯一）：多租户下不同租户可以有同名分组，
-- 口径与 ck_process_template 的 (key, tenant_oid) 一致。
-- 模板侧 ck_process_template.category_oid 引用本表 oid（改显示名不牵动模板）。
-- 历史表 ck_workflow_category 已由 ProcessTemplateSchemaInitializer 整表改名为本表。
CREATE TABLE IF NOT EXISTS ck_process_category (
    oid         VARCHAR(64)  PRIMARY KEY,
    name        VARCHAR(64)  NOT NULL,
    sort_order  INTEGER      NOT NULL DEFAULT 0,
    description VARCHAR(512),
    tenant_oid  VARCHAR(64),
    creator     VARCHAR(128),
    created_at  TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updater     VARCHAR(128),
    updated_at  TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE UNIQUE INDEX IF NOT EXISTS uk_process_category_name_tenant ON ck_process_category(name, tenant_oid);

-- ==================== 流程实例 ↔ 业务实体 关联（ProcessEntitySet） ====================
-- 一行 = 集合里的一个成员；取代模板表上原来的 primary_object_type（单一 code 表达不了一个流程关联多个实体）。
-- 实体引用：无版本对象只填 entity_oid；带版本对象填 entity_version（业务对象的【大版本】revision，如 A，
-- 同时保留 entity_oid = 主对象 oid）—— 记大版本而不是迭代 oid：流程针对大版本发起，其下还会继续
-- 产出小版本（A.2 → A.3…），记迭代 oid 会随对象推进而过时；需要具体版本时解析为"该大版本当前的最新小版本"。
-- 列名刻意不叫 version：本模块里还有"流程模板版本 / 流程定义版本"，entity_version 才一眼是业务对象的版本。
-- type_code / root_type_code 冗余存一份：免 join，且是"当时关联的是什么类型"的快照。
-- 列名前身：entity_iteration_oid（迭代 oid）→ version → entity_version，由建表器幂等迁移。
-- 实际建表由 ProcessTemplateSchemaInitializer#createEntitySetTable 执行（与同模块的模板表一致），此处为 DDL 参考。
CREATE TABLE IF NOT EXISTS ck_process_entity_set (
    oid                  VARCHAR(64) PRIMARY KEY,
    business_key         VARCHAR(128),
    process_instance_id  VARCHAR(64) NOT NULL,
    entity_oid           VARCHAR(64) NOT NULL,
    entity_version       VARCHAR(64),
    type_code            VARCHAR(50),
    root_type_code       VARCHAR(50),
    tenant_oid           VARCHAR(64) NOT NULL,
    creator              VARCHAR(128),
    created_at           TIMESTAMP,
    updater              VARCHAR(128),
    updated_at           TIMESTAMP
);

-- 一个流程实例 + 一个实体(大版本) 只记一次；COALESCE 是为了让"无版本对象"(NULL) 也参与去重
CREATE UNIQUE INDEX IF NOT EXISTS uk_pes_instance_entity
    ON ck_process_entity_set(process_instance_id, entity_oid, COALESCE(entity_version, ''));
CREATE INDEX IF NOT EXISTS idx_pes_instance       ON ck_process_entity_set(process_instance_id);
CREATE INDEX IF NOT EXISTS idx_pes_entity         ON ck_process_entity_set(entity_oid);
CREATE INDEX IF NOT EXISTS idx_pes_entity_version ON ck_process_entity_set(entity_version);
CREATE INDEX IF NOT EXISTS idx_pes_tenant         ON ck_process_entity_set(tenant_oid);

-- ==================== 流程节点执行日志 ====================
-- 「这个节点后台跑了什么、报了什么错」：自动服务（设置状态 / 自动服务 / 通知）由后台执行，
-- 失败时用户只看到"流程卡住了"，原因原本只留在服务器日志里。落库后流程详情页点开节点即可查看。
-- 实际建表由 ProcessNodeLogSchemaInitializer 执行（与本模块其它表一致），此处为 DDL 参考。
CREATE TABLE IF NOT EXISTS ck_process_node_log (
    oid                  CHAR(36)     PRIMARY KEY,
    tenant_oid           CHAR(36)     NOT NULL,
    process_instance_id  CHAR(36)     NOT NULL,
    activity_id          VARCHAR(128) NOT NULL,
    activity_name        VARCHAR(255),
    level                VARCHAR(16)  NOT NULL,
    source               VARCHAR(32),
    message              VARCHAR(1000) NOT NULL,
    detail               TEXT,
    created_at           TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX IF NOT EXISTS idx_pnl_instance_activity
    ON ck_process_node_log(process_instance_id, activity_id, created_at);
CREATE INDEX IF NOT EXISTS idx_pnl_instance_level
    ON ck_process_node_log(process_instance_id, level);

-- ==================== 统一的类型定义（v2.0 重构） ====================
-- 已废弃 plm_model_class + plm_softtype 双表设计，统一为 ck_type_definition 单表。
-- type_kind = 'OOTB'      → 系统内置实体对象（DOCUMENT / PART / PRODUCT / RESOURCE）
-- type_kind = 'SOFT_TYPE' → 基于 OOTB 或另一个 SOFT_TYPE 创建的子类型
-- parent_oid 自引用：OOTB 为 NULL，SOFT_TYPE 指向其父类型 oid
-- oid 为全局唯一主键，code 为全局业务唯一键
CREATE TABLE IF NOT EXISTS ck_type_definition (
    oid            CHAR(36)     PRIMARY KEY,
    code           VARCHAR(50)  NOT NULL UNIQUE,
    name           VARCHAR(100) NOT NULL,
    icon           VARCHAR(50),
    source         VARCHAR(20)  NOT NULL DEFAULT 'OOTB',
    type_kind      VARCHAR(20)  NOT NULL DEFAULT 'SOFT_TYPE',  -- OOTB | SOFT_TYPE
    parent_oid     CHAR(36),                                   -- 自引用父类型 oid
    root_type_code VARCHAR(50),                                -- 根 OOTB 内置对象 code（子类型追溯用）
    description    VARCHAR(500),
    sort_order     INTEGER      NOT NULL DEFAULT 0,
    enabled        BOOLEAN      NOT NULL DEFAULT TRUE,
    tenant_oid     CHAR(36),
    creator        VARCHAR(100),
    created_at     TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updater        VARCHAR(100),
    updated_at     TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_td_type_kind ON ck_type_definition(type_kind);
CREATE INDEX IF NOT EXISTS idx_td_parent ON ck_type_definition(parent_oid);

-- v2.0 迁移指南（从 plm_model_class + plm_softtype 升级到 ck_type_definition）:
--
-- 1. 迁移 ModelClass → TypeDefinition (type_kind='OOTB'):
--    INSERT INTO ck_type_definition (oid, code, name, icon, source, type_kind, parent_oid,
--        description, sort_order, enabled, creator, created_at, updater, updated_at)
--    SELECT oid, code, name, icon, source, 'OOTB', NULL,
--        description, sort_order, enabled, creator, created_at, updater, updated_at
--    FROM plm_model_class
--    ON CONFLICT (code) DO NOTHING;
--
-- 2. 迁移 SoftType → TypeDefinition (type_kind='SOFT_TYPE'):
--    INSERT INTO ck_type_definition (oid, code, name, icon, source, type_kind, parent_oid,
--        description, sort_order, enabled, creator, created_at, updater, updated_at)
--    SELECT oid, code, name, icon, source, 'SOFT_TYPE', COALESCE(parent_oid, model_class_oid),
--        description, sort_order, enabled, creator, created_at, updater, updated_at
--    FROM plm_softtype
--    ON CONFLICT (code) DO NOTHING;
--
-- 3. 迁移完成后可安全删除旧表:
--    DROP TABLE IF EXISTS plm_softtype;
--    DROP TABLE IF EXISTS plm_model_class;

-- ==================== 实体 IBA 属性值存储（通用架构） ====================
-- 所有 IBAExtensible 实体的动态属性值统一存储于此表，
-- 无需在各实体表中单独添加 ext_attrs JSONB 列。
-- entity_type: 实体类型标识（如 product_line, team, document）
-- entity_oid: 实体实例 oid
-- attr_code: IBA 属性编码，对应 ck_iba.code
-- attr_value: 属性值，JSONB 类型，支持任意数据类型
CREATE TABLE IF NOT EXISTS ck_type_iba_data (
    entity_type  VARCHAR(100) NOT NULL,
    entity_oid   CHAR(36)     NOT NULL,
    attr_code    VARCHAR(100) NOT NULL,
    attr_value   JSONB        NOT NULL DEFAULT 'null'::jsonb,
    tenant_oid   CHAR(36),
    creator      VARCHAR(100),
    created_at   TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updater      VARCHAR(100),
    updated_at   TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (entity_type, entity_oid, attr_code)
);

CREATE INDEX IF NOT EXISTS idx_eid_type_oid ON ck_type_iba_data(entity_type, entity_oid);
CREATE INDEX IF NOT EXISTS idx_eid_attr_code ON ck_type_iba_data(entity_type, attr_code);

-- 数据迁移: 如有历史 ck_product_line.ext_attrs 数据，可执行以下 SQL:
-- INSERT INTO ck_type_iba_data (entity_type, entity_oid, attr_code, attr_value, creator, created_at, updater, updated_at)
-- SELECT 'product_line', pl.oid, kv.key, kv.value::jsonb, pl.creator, pl.created_at, pl.updater, pl.updated_at
-- FROM ck_product_line pl,
--      jsonb_each_text(pl.ext_attrs) AS kv(key, value)
-- WHERE pl.ext_attrs IS NOT NULL AND jsonb_typeof(pl.ext_attrs) = 'object'
-- ON CONFLICT (entity_type, entity_oid, attr_code) DO NOTHING;

-- ==================== 可互换属性定义（IBA） ====================
-- Windchill 对应: Interchangeable Attribute / Reusable Attribute
-- data_type 取值: STRING | INTEGER | FLOAT | BOOLEAN | DATE | DATETIME | ENUM | URL
-- oid 为全局唯一主键，code 为业务唯一键
CREATE TABLE IF NOT EXISTS ck_iba (
    oid              CHAR(36)     PRIMARY KEY,
    code             VARCHAR(50)  NOT NULL,
    name             VARCHAR(100) NOT NULL,
    display_name     VARCHAR(100),
    data_type        VARCHAR(20)  NOT NULL DEFAULT 'STRING',
    default_value    VARCHAR(500),
    constraints_json TEXT,          -- JSON 格式约束: {"min":0,"max":100,"pattern":"...","enumValues":["A","B"]}
    required         BOOLEAN      NOT NULL DEFAULT FALSE,
    description      VARCHAR(500),
    sort_order       INTEGER      NOT NULL DEFAULT 0,
    enabled          BOOLEAN      NOT NULL DEFAULT TRUE,
    tenant_oid       CHAR(36),
    creator          VARCHAR(100),
    created_at       TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updater          VARCHAR(100),
    updated_at       TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE (code, tenant_oid)
);

CREATE INDEX IF NOT EXISTS idx_iba_data_type ON ck_iba(data_type);

-- ==================== 类型-属性关联 ====================
-- Windchill 对应: ModelClass / SoftType → IBA Attribute Mapping
-- type_oid 指向 ck_type_definition.oid，owner_type 取值 MODEL_CLASS / SOFT_TYPE
-- （按 type_kind 对应：OOTB → MODEL_CLASS, SOFT_TYPE → SOFT_TYPE）
-- oid 为全局唯一主键，(type_oid, iba_oid) 联合唯一
CREATE TABLE IF NOT EXISTS ck_type_iba (
    oid              CHAR(36)     PRIMARY KEY,
    type_oid         CHAR(36)     NOT NULL,                 -- 关联 ck_type_definition.oid
    entity_code      VARCHAR(50)  NOT NULL DEFAULT '',          -- 实体编码，如 PRODUCT_LINE
    iba_oid          CHAR(36)     NOT NULL REFERENCES ck_iba(oid) ON DELETE CASCADE,
    required         BOOLEAN      NOT NULL DEFAULT FALSE,   -- 可在映射层覆写 required
    default_value    VARCHAR(500),                          -- 可在映射层覆写默认值
    sort_order       INTEGER      NOT NULL DEFAULT 0,
    tenant_oid       CHAR(36),
    creator          VARCHAR(100),
    created_at       TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updater          VARCHAR(100),
    updated_at       TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE (type_oid, iba_oid)
);

CREATE INDEX IF NOT EXISTS idx_ti_type ON ck_type_iba(type_oid);
CREATE INDEX IF NOT EXISTS idx_ti_iba ON ck_type_iba(iba_oid);
CREATE INDEX IF NOT EXISTS idx_ti_entity_code ON ck_type_iba(entity_code);

-- ==================== 实体属性定义 ====================
-- 统一注册 TypeDefinition 的所有属性元数据
-- source: SYSTEM（实体类自身字段） / IBA（通过 ck_type_iba 分配的扩展属性）
-- 为 CRUD UI 配置化布局提供元数据支撑
CREATE TABLE IF NOT EXISTS ck_attribute_definition (
    oid              CHAR(36)     PRIMARY KEY,
    entity_name      VARCHAR(100) NOT NULL,            -- 实体名称: ModelClass / SoftType
    field_name       VARCHAR(50)  NOT NULL,            -- 字段名: code / name / description
    display_name     VARCHAR(100) NOT NULL,            -- 显示名称: 编码 / 名称 / 描述
    data_type        VARCHAR(20)  NOT NULL DEFAULT 'STRING',
    source           VARCHAR(20)  NOT NULL DEFAULT 'SYSTEM',  -- SYSTEM / IBA
    iba_oid          CHAR(36),                         -- 若 source=IBA, 关联 ck_iba.oid
    required         BOOLEAN      NOT NULL DEFAULT FALSE,
    searchable       BOOLEAN      NOT NULL DEFAULT FALSE,     -- 是否出现在搜索/过滤器中
    listable         BOOLEAN      NOT NULL DEFAULT TRUE,      -- 是否出现在表格列中
    editable         BOOLEAN      NOT NULL DEFAULT TRUE,      -- 是否在表单中可编辑
    ui_component     VARCHAR(30)  NOT NULL DEFAULT 'input',   -- input/textarea/select/switch/datepicker/input-number
    default_value    VARCHAR(500),
    constraints_json TEXT,
    sort_order       INTEGER      NOT NULL DEFAULT 0,
    enabled          BOOLEAN      NOT NULL DEFAULT TRUE,
    creator          VARCHAR(100),
    created_at       TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updater          VARCHAR(100),
    updated_at       TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE (entity_name, field_name)
);

CREATE INDEX IF NOT EXISTS idx_ad_entity ON ck_attribute_definition(entity_name);
CREATE INDEX IF NOT EXISTS idx_ad_iba ON ck_attribute_definition(iba_oid);

-- ==================== 低代码页面布局定义 ====================
-- 为 TypeDefinition 的每个操作（list/create/update/detail/自定义）
-- 存储可视化设计器编排的页面布局 JSON 配置
-- entity_oid + entity_type + operation_code 联合唯一：每个实体的每个操作保留一份布局定义
CREATE TABLE IF NOT EXISTS ck_type_page_layout (
    oid            CHAR(36)     PRIMARY KEY,
    entity_oid     CHAR(36)     NOT NULL,                 -- 关联 ck_type_definition.oid
    entity_code    VARCHAR(50),                           -- 实体类型编码（如 PRODUCT_LINE）
    entity_type    VARCHAR(20)  NOT NULL,                 -- 实体类型: OOTB | SOFT_TYPE
    operation_code VARCHAR(30)  NOT NULL DEFAULT 'list',  -- 操作编码: list|create|update|detail|用户自定义
    operation_name VARCHAR(50),                           -- 操作显示名称
    layout_json    JSONB        NOT NULL DEFAULT '{}',    -- 布局 JSON 配置
    tenant_oid     CHAR(36),
    creator        VARCHAR(100),
    created_at     TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updater        VARCHAR(100),
    updated_at     TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE (entity_oid, entity_type, operation_code, tenant_oid)
);

CREATE INDEX IF NOT EXISTS idx_pl_entity ON ck_type_page_layout(entity_oid, entity_type);

-- ==================== 租户 ====================
-- oid 为全局唯一主键，tenant_id 为租户标识
-- status: PENDING（待审核）→ ACTIVE（已激活）→ SUSPENDED / DISABLED
CREATE TABLE IF NOT EXISTS ck_tenant (
    oid              CHAR(36)     PRIMARY KEY,
    tenant_id        VARCHAR(50)  NOT NULL UNIQUE,
    name             VARCHAR(200) NOT NULL,
    status           VARCHAR(20)  NOT NULL DEFAULT 'PENDING',
    contact_name     VARCHAR(100),
    contact_email    VARCHAR(200),
    admin_username   VARCHAR(50),
    admin_password   VARCHAR(200),
    admin_display_name VARCHAR(100),
    approved_at      TIMESTAMP,
    approved_by      VARCHAR(100),
    reject_reason    VARCHAR(500),
    creator          VARCHAR(100),
    created_at       TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updater          VARCHAR(100),
    updated_at       TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- 初始化平台层租户（所有租户共享的系统配置数据归属）
INSERT INTO ck_tenant (oid, tenant_id, name, status)
VALUES ('00000000-0000-0000-0000-000000000000', 'platform', '平台层（系统配置共享）', 'ACTIVE')
ON CONFLICT (oid) DO NOTHING;

-- 初始化默认租户（已激活）
INSERT INTO ck_tenant (oid, tenant_id, name, status)
VALUES ('00000000-0000-0000-0000-000000000001', 'default', '默认租户', 'ACTIVE')
ON CONFLICT (oid) DO NOTHING;

-- ==================== 系统通知 ====================
-- 共享表，所有管理员可见系统级通知
CREATE TABLE IF NOT EXISTS ck_notification (
    oid          CHAR(36)     PRIMARY KEY,
    user_oid     CHAR(36)     NOT NULL,
    title        VARCHAR(200) NOT NULL,
    content      VARCHAR(1000),
    type         VARCHAR(50)  NOT NULL DEFAULT 'INFO',
    target_type  VARCHAR(50),
    target_oid   CHAR(36),
    is_read      BOOLEAN      NOT NULL DEFAULT FALSE,
    creator      VARCHAR(100),
    created_at   TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updater      VARCHAR(100),
    updated_at   TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_notif_user_unread ON ck_notification(user_oid, is_read);

-- ==================== 用户认证 Token ====================
-- oid 为全局唯一主键；token 为 UUID 字符串，存储在浏览器 localStorage，
-- 服务端持久化到数据库，设置 3 天自动过期。
-- tenant_oid 缓存当前用户的租户 oid，避免每次校验都查用户表
CREATE TABLE IF NOT EXISTS ck_token (
    oid          CHAR(36)     PRIMARY KEY,
    token        VARCHAR(36)  NOT NULL UNIQUE,
    username     VARCHAR(50)  NOT NULL,
    expire_at    TIMESTAMP    NOT NULL,
    tenant_oid   VARCHAR(50),
    tenant_name  VARCHAR(100),
    creator      VARCHAR(100),
    created_at   TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updater      VARCHAR(100),
    updated_at   TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_token_value ON ck_token(token);
CREATE INDEX IF NOT EXISTS idx_token_username ON ck_token(username);
CREATE INDEX IF NOT EXISTS idx_token_expire ON ck_token(expire_at);

-- ==================== 产品线 ====================
-- oid 为全局唯一主键，code 为业务唯一键，team_oid 关联团队
-- parent_oid 自引用外键，支持多级树形结构
CREATE TABLE IF NOT EXISTS ck_product_line (
    oid              CHAR(36)     PRIMARY KEY,
    code             VARCHAR(50)  NOT NULL UNIQUE,
    name             VARCHAR(100) NOT NULL,
    description      VARCHAR(500),
    thumbnail        VARCHAR(500),
    team_oid         CHAR(36),
    parent_oid       CHAR(36)     REFERENCES ck_product_line(oid) ON DELETE SET NULL,
    ext_attrs        JSONB        NOT NULL DEFAULT '{}',
    tenant_oid       CHAR(36),
    creator          VARCHAR(100),
    created_at       TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updater          VARCHAR(100),
    updated_at       TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_product_line_parent ON ck_product_line(parent_oid);


-- ==================== 产品型号 ====================
-- oid 为全局唯一主键，code 为业务唯一键，parent_oid 关联所属产品系列（复用父类字段）
-- 继承 ProductLine 全部字段，parent_oid 表示归属产品系列
CREATE TABLE IF NOT EXISTS ck_product_model (
    oid              CHAR(36)     PRIMARY KEY,
    code             VARCHAR(50)  NOT NULL UNIQUE,
    name             VARCHAR(100) NOT NULL,
    description      VARCHAR(500),
    thumbnail        VARCHAR(500),
    team_oid         CHAR(36),
    parent_oid       CHAR(36)     NOT NULL,
    ext_attrs        JSONB        NOT NULL DEFAULT '{}',
    tenant_oid       CHAR(36),
    creator          VARCHAR(100),
    created_at       TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updater          VARCHAR(100),
    updated_at       TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_model_parent_oid ON ck_product_model(parent_oid);



-- ==================== 研发阶段 ====================
-- 每个产品系列/产品型号自有其阶段列表，(owner_oid, owner_type, code) 联合唯一
-- owner_type: LINE（产品系列）/ MODEL（产品型号）
-- default_folders 为 JSON 数组字符串，存储该阶段默认文件夹名称列表
CREATE TABLE IF NOT EXISTS ck_stage (
    oid              CHAR(36)     PRIMARY KEY,
    code             VARCHAR(50)  NOT NULL,
    name             VARCHAR(100) NOT NULL,
    description      VARCHAR(500),
    icon             VARCHAR(100),
    color            VARCHAR(10),
    sort_order       INTEGER      NOT NULL DEFAULT 0,
    owner_oid        CHAR(36)     NOT NULL,
    owner_type       VARCHAR(10)  NOT NULL DEFAULT 'LINE',
    show_on_dashboard BOOLEAN     NOT NULL DEFAULT TRUE,
    default_folders  VARCHAR(2000),
    tenant_oid       CHAR(36),
    creator          VARCHAR(100),
    created_at       TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updater          VARCHAR(100),
    updated_at       TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE (owner_oid, owner_type, code)
);

CREATE INDEX IF NOT EXISTS idx_stage_owner ON ck_stage(owner_oid, owner_type);



-- ==================== 团队 ====================
-- oid 为全局唯一主键，code 为业务唯一键
CREATE TABLE IF NOT EXISTS ck_team (
    oid          CHAR(36)     PRIMARY KEY,
    code         VARCHAR(50)  NOT NULL UNIQUE,
    name         VARCHAR(100) NOT NULL,
    description  VARCHAR(500),
    tenant_oid   CHAR(36),
    creator      VARCHAR(100),
    created_at   TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updater      VARCHAR(100),
    updated_at   TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- ==================== 团队成员 ====================
-- oid 为全局唯一主键，(team_oid, user_id) 联合唯一
CREATE TABLE IF NOT EXISTS ck_team_member (
    oid          CHAR(36)     PRIMARY KEY,
    team_oid     CHAR(36)     NOT NULL REFERENCES ck_team(oid) ON DELETE CASCADE,
    user_id      VARCHAR(50)  NOT NULL,
    role_name    VARCHAR(100),
    tenant_oid   CHAR(36),
    creator      VARCHAR(100),
    created_at   TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updater      VARCHAR(100),
    updated_at   TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE (team_oid, user_id)
);

CREATE INDEX IF NOT EXISTS idx_tm_team ON ck_team_member(team_oid);

-- ==================== 图片空间 ====================
-- oid 为全局唯一主键，用于统一管理和复用图片资源
CREATE TABLE IF NOT EXISTS ck_media (
    oid           CHAR(36)     PRIMARY KEY,
    original_name VARCHAR(255) NOT NULL,
    file_name     VARCHAR(255) NOT NULL,
    file_size     BIGINT,
    mime_type     VARCHAR(100),
    storage_path  VARCHAR(500),
    description   VARCHAR(500),
    width         INT,
    height        INT,
    tenant_oid    CHAR(36),
    creator       VARCHAR(100),
    created_at    TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updater       VARCHAR(100),
    updated_at    TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_media_created ON ck_media(created_at DESC);



-- ==================== 迁移：支持 TypeDefinition 分配 IBA ====================
-- ck_type_iba.type_oid 指向 ck_type_definition.oid（统一类型定义表）
-- 注意：由于去掉了 type_oid 对外键约束，任何 TypeDefinition 的 oid 都可以存入

-- ==================== 文件夹 ====================
-- owner_oid 关联业务对象（产品线、产品型号等）
CREATE TABLE IF NOT EXISTS ck_folder (
    oid               CHAR(36)     PRIMARY KEY,
    owner_oid         CHAR(36),
    stage_oid         CHAR(36),
    parent_folder_oid CHAR(36),
    name              VARCHAR(200) NOT NULL,
    type              VARCHAR(10)  NOT NULL DEFAULT 'USER',
    sort_order        INTEGER      NOT NULL DEFAULT 0,
    tenant_oid        CHAR(36),
    creator           VARCHAR(100),
    created_at        TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updater           VARCHAR(100),
    updated_at        TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_folder_owner_stage  ON ck_folder(owner_oid, stage_oid);
CREATE INDEX IF NOT EXISTS idx_folder_parent       ON ck_folder(parent_folder_oid);

-- ==================== 文档主对象 (Document Master) ====================
-- Windchill 对应: WTDocumentMaster
-- oid 为全局唯一主键，number 为业务编码（可由编码规则自动生成）
-- owner_oid 指向归属的产品系列(ck_product_line)或产品型号(ck_product_model)
-- owner_type 标记归属类型：LINE 或 MODEL
-- folder_oid 关联所属文件夹，stage_oid 标记所处研发阶段
-- 主文档文件通过 ck_document_iteration.ckfile_oid 关联（不同版本可关联不同主文档文件）

CREATE TABLE IF NOT EXISTS ck_document (
    oid               CHAR(36)     PRIMARY KEY,
    name              VARCHAR(200) NOT NULL,
    number            VARCHAR(100),
    description       VARCHAR(1000),
    type_definition_code VARCHAR(50),
    container_oid     CHAR(36)     NOT NULL,
    container_type    VARCHAR(20)  NOT NULL DEFAULT 'PRODUCT_LINE',
    folder_oid        CHAR(36),
    stage_oid         VARCHAR(50)  NOT NULL,
    cls_oid           CHAR(36),
    tenant_oid        CHAR(36),
    creator           VARCHAR(100),
    created_at        TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updater           VARCHAR(100),
    updated_at        TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_doc_folder       FOREIGN KEY (folder_oid)       REFERENCES ck_folder(oid) ON DELETE SET NULL
);

CREATE INDEX IF NOT EXISTS idx_doc_container ON ck_document(container_oid);
CREATE INDEX IF NOT EXISTS idx_doc_folder ON ck_document(folder_oid);
CREATE INDEX IF NOT EXISTS idx_doc_stage  ON ck_document(stage_oid);

-- ==================== 分类管理 ====================
-- 树形层级结构，通过 parent_oid 自引用，identifier 为 API 路由标识
CREATE TABLE IF NOT EXISTS ck_classification (
    oid           CHAR(36)     PRIMARY KEY,
    code          VARCHAR(50)  NOT NULL,
    name          VARCHAR(100) NOT NULL,
    display_name  VARCHAR(200),
    description   VARCHAR(500),
    identifier    VARCHAR(100),
    thumbnail     VARCHAR(500),
    parent_oid    CHAR(36),
    tenant_oid    CHAR(36),
    sort_order    INTEGER      NOT NULL DEFAULT 0,
    creator       VARCHAR(100),
    created_at    TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updater       VARCHAR(100),
    updated_at    TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_cls_parent   ON ck_classification(parent_oid);
CREATE INDEX IF NOT EXISTS idx_cls_tenant   ON ck_classification(tenant_oid);
CREATE INDEX IF NOT EXISTS idx_cls_identifier ON ck_classification(identifier);

-- ==================== 分类-IBA属性关联 (Classification IBA Mapping) ====================
-- 为分类分配 IBA 属性，类似 ck_type_iba 的类型-属性关联
CREATE TABLE IF NOT EXISTS ck_cls_iba (
    oid                CHAR(36)     PRIMARY KEY,
    classification_oid CHAR(36)     NOT NULL REFERENCES ck_classification(oid) ON DELETE CASCADE,
    iba_oid            CHAR(36)     NOT NULL REFERENCES ck_iba(oid) ON DELETE CASCADE,
    required           BOOLEAN      NOT NULL DEFAULT FALSE,
    default_value      VARCHAR(500),
    sort_order         INTEGER      NOT NULL DEFAULT 0,
    tenant_oid         CHAR(36),
    creator            VARCHAR(100),
    created_at         TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updater            VARCHAR(100),
    updated_at         TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE (classification_oid, iba_oid)
);

CREATE INDEX IF NOT EXISTS idx_cls_iba_cls ON ck_cls_iba(classification_oid);
CREATE INDEX IF NOT EXISTS idx_cls_iba_iba ON ck_cls_iba(iba_oid);

-- ==================== 部件主数据 (Part Master) ====================
-- 参考 Windchill WTPartMaster，Part 作为部件的版本主对象
CREATE TABLE IF NOT EXISTS ck_part (
    oid                   CHAR(36)     PRIMARY KEY,
    name                  VARCHAR(200) NOT NULL,
    number                VARCHAR(100),
    description           VARCHAR(1000),
    type_definition_code  VARCHAR(50),
    container_oid         CHAR(36),
    container_type        VARCHAR(20),
    folder_oid            CHAR(36),
    stage_oid             VARCHAR(50)  NOT NULL,
    cls_oid               CHAR(36),
    tenant_oid            CHAR(36),
    creator               VARCHAR(100),
    created_at            TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updater               VARCHAR(100),
    updated_at            TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_part_folder FOREIGN KEY (folder_oid) REFERENCES ck_folder(oid) ON DELETE SET NULL,
    CONSTRAINT fk_part_classification FOREIGN KEY (cls_oid) REFERENCES ck_classification(oid) ON DELETE SET NULL
);

CREATE INDEX IF NOT EXISTS idx_part_container  ON ck_part(container_oid);
CREATE INDEX IF NOT EXISTS idx_part_folder    ON ck_part(folder_oid);
CREATE INDEX IF NOT EXISTS idx_part_stage     ON ck_part(stage_oid);
CREATE INDEX IF NOT EXISTS idx_part_cls       ON ck_part(cls_oid);

-- ==================== 部件子版本 (Part Iteration) ====================
-- 参考 Windchill WTPart，与 Part 为 1:N 版本历史关系
CREATE TABLE IF NOT EXISTS ck_part_iteration (
    oid                              CHAR(36)     PRIMARY KEY,
    master_oid                       CHAR(36)     NOT NULL REFERENCES ck_part(oid) ON DELETE CASCADE,
    revision                         VARCHAR(10)  NOT NULL DEFAULT 'A',
    iteration                        INTEGER      NOT NULL DEFAULT 1,
    display_version                  VARCHAR(20),
    checked_out                      BOOLEAN      NOT NULL DEFAULT FALSE,
    checked_out_by                   VARCHAR(100),
    checked_out_comment              VARCHAR(500),
    latest                           BOOLEAN      NOT NULL DEFAULT TRUE,
    derived_from_oid                 CHAR(36),
    derived_at                       TIMESTAMP,
    view                             VARCHAR(50),
    status                           VARCHAR(50),
    unit                             VARCHAR(50),
    source                           VARCHAR(50),
    version_sort                     INTEGER      NOT NULL DEFAULT 0,
    branch_id                        VARCHAR(50)  NOT NULL DEFAULT 'master',
    delete_mark                      BOOLEAN      NOT NULL DEFAULT FALSE,
    tenant_oid                       CHAR(36),
    creator                          VARCHAR(100),
    created_at                       TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updater                          VARCHAR(100),
    updated_at                       TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_pi_master  ON ck_part_iteration(master_oid);
CREATE INDEX IF NOT EXISTS idx_pi_latest  ON ck_part_iteration(master_oid, latest);

-- ==================== 功能架构主数据 (Functional Master) ====================
-- 继承 Part 复合实体结构，面向军工功能系统（装备级功能系统 / 车型功能域） & 汽车车型功能域
CREATE TABLE IF NOT EXISTS ck_functional (
    oid                   CHAR(36)     PRIMARY KEY,
    name                  VARCHAR(200) NOT NULL,
    number                VARCHAR(100),
    description           VARCHAR(1000),
    type_definition_code  VARCHAR(50),
    container_oid         CHAR(36),
    container_type        VARCHAR(20),
    folder_oid            CHAR(36),
    stage_oid             VARCHAR(50)  NOT NULL,
    tenant_oid            CHAR(36),
    creator               VARCHAR(100),
    created_at            TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updater               VARCHAR(100),
    updated_at            TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_fun_folder FOREIGN KEY (folder_oid) REFERENCES ck_folder(oid) ON DELETE SET NULL
);

CREATE INDEX IF NOT EXISTS idx_fun_container ON ck_functional(container_oid);
CREATE INDEX IF NOT EXISTS idx_fun_folder    ON ck_functional(folder_oid);
CREATE INDEX IF NOT EXISTS idx_fun_stage     ON ck_functional(stage_oid);

-- ==================== 功能架构子版本数据 (Functional) ====================
CREATE TABLE IF NOT EXISTS ck_functional_iteration (
    oid                              CHAR(36)     PRIMARY KEY,
    master_oid                       CHAR(36)     NOT NULL REFERENCES ck_functional(oid) ON DELETE CASCADE,
    revision                         VARCHAR(10)  NOT NULL DEFAULT 'A',
    iteration                        INTEGER      NOT NULL DEFAULT 1,
    display_version                  VARCHAR(20),
    checked_out                      BOOLEAN      NOT NULL DEFAULT FALSE,
    checked_out_by                   VARCHAR(100),
    checked_out_comment              VARCHAR(500),
    latest                           BOOLEAN      NOT NULL DEFAULT TRUE,
    derived_from_oid                 CHAR(36),
    derived_at                       TIMESTAMP,
    status                           VARCHAR(50),
    lifecycle_template_iteration_oid CHAR(36),
    version_sort                     INTEGER      NOT NULL DEFAULT 0,
    branch_id                        VARCHAR(50),
    delete_mark                      BOOLEAN      NOT NULL DEFAULT FALSE,
    tenant_oid                       CHAR(36),
    creator                          VARCHAR(100),
    created_at                       TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updater                          VARCHAR(100),
    updated_at                       TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_fi_master  ON ck_functional_iteration(master_oid);
CREATE INDEX IF NOT EXISTS idx_fi_latest  ON ck_functional_iteration(master_oid, latest);

-- ==================== 文件存储实体 (CKFile) ====================
-- 主文档文件，通过 ck_document_iteration.ckfile_oid 关联
-- source_type: LOCAL(本地上传) / URL(网络资源)
CREATE TABLE IF NOT EXISTS ck_file (
    oid                CHAR(36)     PRIMARY KEY,
    source_type        VARCHAR(10)  NOT NULL DEFAULT 'LOCAL',
    source_url         VARCHAR(2000),
    file_name          VARCHAR(255),
    file_size          BIGINT,
    storage_path       VARCHAR(500),
    mime_type          VARCHAR(100),
    tenant_oid         CHAR(36),
    creator            VARCHAR(100),
    created_at         TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updater            VARCHAR(100),
    updated_at         TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- ==================== 附件存储实体 (CKAttachment) ====================
-- 通用附件实体，通过 owner_oid 关联其所属业务对象（可被 DocumentIteration、Part、CR 等多种实体复用），1:N
CREATE TABLE IF NOT EXISTS ck_attachment (
    oid                CHAR(36)     PRIMARY KEY,
    owner_oid          CHAR(36)     NOT NULL,
    file_name          VARCHAR(255) NOT NULL,
    file_size          BIGINT,
    storage_path       VARCHAR(500),
    mime_type          VARCHAR(100),
    tenant_oid         CHAR(36),
    creator            VARCHAR(100),
    created_at         TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updater            VARCHAR(100),
    updated_at         TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_cka_owner ON ck_attachment(owner_oid);

-- ==================== 文档子版本 (Document Iteration) ====================
-- Windchill 对应: WTDocument (Iteration)
-- 通过 master_oid 关联 Document，1:N 关系
-- revision/iteration 实现 Windchill 式版本控制 (如 A.1, A.2, B.1...)
-- ckfile_oid 关联 CKFile（该版本的主文档文件，不同迭代版本可关联不同主文档）
-- 附件通过 ck_attachment 表示独立存储，1:N 关联
CREATE TABLE IF NOT EXISTS ck_document_iteration (
    oid                CHAR(36)     PRIMARY KEY,
    master_oid         CHAR(36)     NOT NULL REFERENCES ck_document(oid) ON DELETE CASCADE,
    revision           VARCHAR(10)  NOT NULL DEFAULT 'A',
    iteration          INTEGER      NOT NULL DEFAULT 1,
    display_version    VARCHAR(20),
    checked_out        BOOLEAN      NOT NULL DEFAULT FALSE,
    checked_out_by     VARCHAR(100),
    checked_out_comment VARCHAR(500),
    latest             BOOLEAN      NOT NULL DEFAULT TRUE,
    derived_from_oid   CHAR(36),
    derived_at         TIMESTAMP,
    status             VARCHAR(50),
    lifecycle_template_iteration_oid CHAR(36),
    ckfile_oid         CHAR(36)     REFERENCES ck_file(oid) ON DELETE SET NULL,
    version_sort       INTEGER      NOT NULL DEFAULT 0,
    branch_id          VARCHAR(50),
    delete_mark        BOOLEAN      NOT NULL DEFAULT FALSE,
    tenant_oid         CHAR(36),
    creator            VARCHAR(100),
    created_at         TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updater            VARCHAR(100),
    updated_at         TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_di_master   ON ck_document_iteration(master_oid);
CREATE INDEX IF NOT EXISTS idx_di_latest   ON ck_document_iteration(master_oid, latest);
CREATE INDEX IF NOT EXISTS idx_di_ckfile   ON ck_document_iteration(ckfile_oid);

-- ==================== 零件-文档关系 (Part-Document Link) ====================
-- 描述一份文档对某个零件构成「定义（DESCRIBES）」还是「参考（REFERENCE）」的关系。
-- 判定准则：不看文档是什么，看它变了会怎样——文档变更后果 = 零件变更后果 时为 DESCRIBES，否则 REFERENCE。
-- 分界线画在「挂」的动作上（link_type），而非文档类型属性：同一份文档对 A 零件是定义、对 B 零件是参考。
-- 挂接粒度：part_iteration_oid 挂零件迭代（对齐 BOM 的挂法）。
-- doc_iteration_oid 为 NULL 表示「跟随最新」；resolved_iteration_oid 为解析缓存，渲染直接 JOIN。
CREATE TABLE IF NOT EXISTS ck_doc_part_link (
    oid                    CHAR(36)     PRIMARY KEY,
    link_type              VARCHAR(32)  NOT NULL,                -- DESCRIBES / REFERENCE
    part_iteration_oid     CHAR(36)     NOT NULL,                -- 挂零件迭代（对齐 BOM 的挂法）
    doc_master_oid         CHAR(36)     NOT NULL,                -- 文档主对象
    doc_iteration_oid      CHAR(36),                            -- NULL = 跟随最新
    resolved_iteration_oid CHAR(36),                            -- 解析缓存，渲染直接 JOIN
    category               VARCHAR(64),                         -- 图纸/规格书/报告（软类型驱动）
    tenant_oid             CHAR(36)     NOT NULL,
    creator                VARCHAR(100),
    created_at             TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updater                VARCHAR(100),
    updated_at             TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_doc_part_link_part_iteration   FOREIGN KEY (part_iteration_oid)     REFERENCES ck_part_iteration(oid) ON DELETE CASCADE,
    CONSTRAINT fk_doc_part_link_doc_master       FOREIGN KEY (doc_master_oid)         REFERENCES ck_document(oid) ON DELETE CASCADE,
    CONSTRAINT fk_doc_part_link_doc_iteration    FOREIGN KEY (doc_iteration_oid)      REFERENCES ck_document_iteration(oid) ON DELETE SET NULL,
    CONSTRAINT fk_doc_part_link_resolved_iteration FOREIGN KEY (resolved_iteration_oid) REFERENCES ck_document_iteration(oid) ON DELETE SET NULL
);

CREATE INDEX IF NOT EXISTS idx_dpl_part_iteration ON ck_doc_part_link(part_iteration_oid, link_type);
CREATE INDEX IF NOT EXISTS idx_dpl_doc_master     ON ck_doc_part_link(doc_master_oid, link_type);
CREATE INDEX IF NOT EXISTS idx_dpl_doc_iteration  ON ck_doc_part_link(doc_iteration_oid);
CREATE INDEX IF NOT EXISTS idx_dpl_tenant         ON ck_doc_part_link(tenant_oid);

CREATE TABLE IF NOT EXISTS ck_user_activity (
    oid            VARCHAR(64)  PRIMARY KEY,
    user_oid       VARCHAR(64)  NOT NULL,
    activity_type  VARCHAR(20)  NOT NULL,
    target_name    VARCHAR(255),
    target_type    VARCHAR(64),
    target_path    VARCHAR(512),
    action_desc    VARCHAR(255),
    operator_ip    VARCHAR(64),
    user_agent     VARCHAR(512),
    result         VARCHAR(20),
    duration_ms    INTEGER,
    error_message  VARCHAR(500),
    detail_json    TEXT,
    tenant_oid     CHAR(36),
    creator        VARCHAR(64),
    created_at     TIMESTAMP,
    updater        VARCHAR(64),
    updated_at     TIMESTAMP
);
  
CREATE TABLE IF NOT EXISTS ck_file_storage_config (
    oid              VARCHAR(64)  PRIMARY KEY,
    category_code    VARCHAR(32)  NOT NULL,
    category_name    VARCHAR(64),
    storage_path     VARCHAR(512),
    storage_type     VARCHAR(32)  DEFAULT 'LOCAL',
    max_file_size_mb INTEGER DEFAULT 100,
    max_capacity_mb  INTEGER,
    alert_threshold_percent INTEGER DEFAULT 80,
    enabled          BOOLEAN      DEFAULT true,
    sort_order       INTEGER DEFAULT 0,
    description      VARCHAR(500),
    endpoint         VARCHAR(512),
    access_key       VARCHAR(256),
    secret_key       VARCHAR(256),
    bucket_name      VARCHAR(128),
    base_url         VARCHAR(512),
    tenant_oid       CHAR(36),
    creator          VARCHAR(64),
    created_at       TIMESTAMP,
    updater          VARCHAR(64),
    updated_at       TIMESTAMP
);

-- ==================== 研发阶段模板 ====================
CREATE TABLE IF NOT EXISTS ck_stage_template (
    oid            CHAR(36)     PRIMARY KEY,
    code           VARCHAR(50)  NOT NULL,
    name           VARCHAR(100) NOT NULL,
    description    VARCHAR(500),
    icon           VARCHAR(50),
    color          VARCHAR(20),
    sort_order     INTEGER      NOT NULL DEFAULT 0,
    default_folders TEXT,
    tenant_oid     CHAR(36),
    creator        VARCHAR(100),
    created_at     TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updater        VARCHAR(100),
    updated_at     TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP
);


-- ==================== 计量单位 ====================
-- 单位体系核心表，每个单位归属于一个量纲类型（quantity_type）
-- 同量纲内通过 factor + offset 换算到基准单位（base_unit_name）
-- 换算公式：基准值 = 当前值 × factor + offset
CREATE TABLE IF NOT EXISTS ck_unit (
    oid             CHAR(36)     PRIMARY KEY,
    name            VARCHAR(50)  NOT NULL,
    display         VARCHAR(50),
    quantity_type   VARCHAR(50),
    is_si           BOOLEAN      NOT NULL DEFAULT FALSE,
    base_unit_name  VARCHAR(50),
    factor          DOUBLE PRECISION NOT NULL DEFAULT 1.0,
    unit_shift      DOUBLE PRECISION NOT NULL DEFAULT 0.0,
    sort_order      INTEGER      NOT NULL DEFAULT 0,
    description     VARCHAR(500),
    creator         VARCHAR(100),
    created_at      TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updater         VARCHAR(100),
    updated_at      TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE (name)
);

CREATE INDEX IF NOT EXISTS idx_unit_qtype ON ck_unit(quantity_type);
CREATE INDEX IF NOT EXISTS idx_unit_base  ON ck_unit(base_unit_name);

-- ==================== 分类 IBA 数据 ====================
-- 存储分类节点对应的 IBA 属性值，一个分类节点的每个 IBA 属性对应一条记录。
-- entity_oid 为空字符串表示「分类节点默认值」（分类管理界面配置），
-- 非空表示具体对象实例（Part/Document）的分类 IBA 属性值。
CREATE TABLE IF NOT EXISTS ck_cls_iba_data (
    entity_oid         CHAR(36)     NOT NULL DEFAULT '',
    classification_oid CHAR(36)     NOT NULL,
    attr_code          VARCHAR(100) NOT NULL,
    attr_value         JSONB        NOT NULL DEFAULT 'null'::jsonb,
    tenant_oid         CHAR(36),
    creator            VARCHAR(100),
    created_at         TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updater            VARCHAR(100),
    updated_at         TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (entity_oid, classification_oid, attr_code)
);

CREATE INDEX IF NOT EXISTS idx_cid_cls    ON ck_cls_iba_data(classification_oid);
CREATE INDEX IF NOT EXISTS idx_cid_attr   ON ck_cls_iba_data(attr_code);
CREATE INDEX IF NOT EXISTS idx_cid_tenant ON ck_cls_iba_data(tenant_oid);
CREATE INDEX IF NOT EXISTS idx_cid_entity ON ck_cls_iba_data(entity_oid, classification_oid);

-- ==================== 分类 IBA 页面布局 ====================
-- 为分类节点的 IBA 属性集存储表单布局配置（create / update / detail）
CREATE TABLE IF NOT EXISTS ck_cls_page_layout (
    oid            CHAR(36)     PRIMARY KEY,
    cls_oid        CHAR(36)     NOT NULL,
    operation_code VARCHAR(100) NOT NULL,
    operation_name VARCHAR(200),
    layout_json    JSONB        NOT NULL DEFAULT '{}'::jsonb,
    tenant_oid     CHAR(36),
    creator        VARCHAR(100),
    created_at     TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updater        VARCHAR(100),
    updated_at     TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_cpl_cls    ON ck_cls_page_layout(cls_oid);
CREATE INDEX IF NOT EXISTS idx_cpl_op     ON ck_cls_page_layout(operation_code);
CREATE INDEX IF NOT EXISTS idx_cpl_tenant ON ck_cls_page_layout(tenant_oid);
CREATE UNIQUE INDEX IF NOT EXISTS uk_cpl_cls_op_tenant ON ck_cls_page_layout(cls_oid, operation_code, tenant_oid);


-- ==================== BOM 行项 (BomLinks) ====================
-- 参考 Windchill WTPartUsageLink，描述部件的 BOM 构成关系
-- parent_iteration_oid: 父部件迭代 oid（关联 ck_part_iteration.oid，迭代本身已携带 view）
-- child_part_oid: 子部件主对象 oid（关联 ck_part.oid）
-- child_iteration_oid: 子部件精确迭代 oid（关联 ck_part_iteration.oid，可空。NULL = 非精确引用，跟随最新）
-- resolved_iteration_oid: 非精确引用的解析缓存（child_iteration_oid 为 NULL 时，缓存解析到的最新迭代 oid）
-- line_number: BOM 行号，同一父迭代下唯一
--
-- 精确引用 vs 非精确引用：
--   精确引用：child_iteration_oid NOT NULL → 锁定到子件某个迭代，适用已发布基线、合规追溯
--   非精确引用：child_iteration_oid NULL → 始终取子件最新迭代，适用设计阶段、快速迭代
CREATE TABLE IF NOT EXISTS ck_bom_links (
    oid                   CHAR(36)     PRIMARY KEY,
    code                  VARCHAR(50),
    name                  VARCHAR(200),
    description           VARCHAR(1000),
    parent_iteration_oid  CHAR(36)     NOT NULL,
    child_part_oid        CHAR(36)     NOT NULL,
    child_iteration_oid   CHAR(36),
    resolved_iteration_oid CHAR(36),
    quantity              DOUBLE PRECISION,
    unit                  VARCHAR(50),
    line_number           INTEGER,
    unit_cost             DOUBLE PRECISION,
    tenant_oid            CHAR(36),
    creator               VARCHAR(100),
    created_at            TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updater               VARCHAR(100),
    updated_at            TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_bom_links_parent_iteration   FOREIGN KEY (parent_iteration_oid)   REFERENCES ck_part_iteration(oid) ON DELETE CASCADE,
    CONSTRAINT fk_bom_links_child_part         FOREIGN KEY (child_part_oid)         REFERENCES ck_part(oid) ON DELETE CASCADE,
    CONSTRAINT fk_bom_links_child_iteration    FOREIGN KEY (child_iteration_oid)    REFERENCES ck_part_iteration(oid) ON DELETE SET NULL,
    CONSTRAINT fk_bom_links_resolved_iteration FOREIGN KEY (resolved_iteration_oid) REFERENCES ck_part_iteration(oid) ON DELETE SET NULL
);

CREATE INDEX IF NOT EXISTS idx_bom_links_parent           ON ck_bom_links(parent_iteration_oid);
CREATE INDEX IF NOT EXISTS idx_bom_links_child_part       ON ck_bom_links(child_part_oid);
CREATE INDEX IF NOT EXISTS idx_bom_links_child_iteration  ON ck_bom_links(child_iteration_oid);
CREATE INDEX IF NOT EXISTS idx_bom_links_resolved         ON ck_bom_links(resolved_iteration_oid);
CREATE INDEX IF NOT EXISTS idx_bom_links_tenant           ON ck_bom_links(tenant_oid);
CREATE UNIQUE INDEX IF NOT EXISTS uk_bom_links_line       ON ck_bom_links(parent_iteration_oid, line_number);

-- ==================== BOM 快照 (BomSnapshot) ====================
-- 为 RELEASED 迭代预计算整棵 BOM 树的 JSONB 快照
-- iteration_oid 唯一（一个迭代一个快照），同时作为主键
-- 已发布 BOM 的渲染 = 一行读取，零递归
CREATE TABLE IF NOT EXISTS ck_bom_snapshot (
    oid           CHAR(36)     PRIMARY KEY,
    iteration_oid CHAR(36)     NOT NULL,
    snapshot_json JSONB        NOT NULL DEFAULT '{}'::jsonb,
    node_count    INTEGER,
    max_depth     INTEGER,
    tenant_oid    CHAR(36),
    creator       VARCHAR(100),
    created_at    TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updater       VARCHAR(100),
    updated_at    TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_bom_snapshot_iteration FOREIGN KEY (iteration_oid) REFERENCES ck_part_iteration(oid) ON DELETE CASCADE
);

CREATE UNIQUE INDEX IF NOT EXISTS uk_bom_snapshot_iteration ON ck_bom_snapshot(iteration_oid);
CREATE INDEX IF NOT EXISTS idx_bom_snapshot_tenant ON ck_bom_snapshot(tenant_oid);

-- ==================== BOM 差异 (BomDiff) ====================
-- 预计算相邻迭代之间的 BOM 结构差异
-- (from_iteration_oid, to_iteration_oid) 联合唯一
-- diff_json 格式：{"added": [...], "removed": [...], "changed": [...]}
CREATE TABLE IF NOT EXISTS ck_bom_diff (
    oid                CHAR(36)     PRIMARY KEY,
    from_iteration_oid CHAR(36)     NOT NULL,
    to_iteration_oid   CHAR(36)     NOT NULL,
    diff_json          JSONB        NOT NULL DEFAULT '{}'::jsonb,
    added_count        INTEGER      DEFAULT 0,
    removed_count      INTEGER      DEFAULT 0,
    changed_count      INTEGER      DEFAULT 0,
    tenant_oid         CHAR(36),
    creator            VARCHAR(100),
    created_at         TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updater            VARCHAR(100),
    updated_at         TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_bom_diff_from_iteration FOREIGN KEY (from_iteration_oid) REFERENCES ck_part_iteration(oid) ON DELETE CASCADE,
    CONSTRAINT fk_bom_diff_to_iteration   FOREIGN KEY (to_iteration_oid)   REFERENCES ck_part_iteration(oid) ON DELETE CASCADE
);

CREATE UNIQUE INDEX IF NOT EXISTS uk_bom_diff_from_to ON ck_bom_diff(from_iteration_oid, to_iteration_oid);
CREATE INDEX IF NOT EXISTS idx_bom_diff_from ON ck_bom_diff(from_iteration_oid);
CREATE INDEX IF NOT EXISTS idx_bom_diff_to   ON ck_bom_diff(to_iteration_oid);
CREATE INDEX IF NOT EXISTS idx_bom_diff_tenant ON ck_bom_diff(tenant_oid);

-- ==================== 部件双向替代关系 (PartAlternateLink) ====================
-- 参考 Windchill WTPartAlternateLink，描述部件之间全局可用的双向替代关系
-- 挂在部件主数据（Master）级别（非迭代），不限定于某个 BOM，任意 BOM 中遇到任一部件均可引用
-- role_a_part_oid / role_b_part_oid: 对称双向的两个角色端（roleA / roleB），有序对唯一约束防重复
-- alternate_type: 替代类型 EQUIVALENT/COMPLETE/PARTIAL/SUBSTITUTE
-- effectivity_json: 生效性配置 JSONB 预留（日期/批次/序列号生效性）
CREATE TABLE IF NOT EXISTS ck_part_alternate_link (
    oid                 CHAR(36)     PRIMARY KEY,
    code                VARCHAR(50),
    name                VARCHAR(200),
    description         VARCHAR(1000),
    role_a_part_oid     CHAR(36)     NOT NULL,
    role_b_part_oid     CHAR(36)     NOT NULL,
    alternate_type      VARCHAR(20),
    alternate_quantity  DOUBLE PRECISION DEFAULT 1.0,
    alternate_unit      VARCHAR(50),
    enabled             BOOLEAN      NOT NULL DEFAULT TRUE,
    effectivity_json    JSONB,
    tenant_oid          CHAR(36),
    creator             VARCHAR(100),
    created_at          TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updater             VARCHAR(100),
    updated_at          TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_part_alternate_link_role_a FOREIGN KEY (role_a_part_oid) REFERENCES ck_part(oid) ON DELETE CASCADE,
    CONSTRAINT fk_part_alternate_link_role_b FOREIGN KEY (role_b_part_oid) REFERENCES ck_part(oid) ON DELETE CASCADE
);

CREATE UNIQUE INDEX IF NOT EXISTS uk_part_alternate_link_pair ON ck_part_alternate_link(role_a_part_oid, role_b_part_oid);
CREATE INDEX IF NOT EXISTS idx_part_alternate_link_role_a ON ck_part_alternate_link(role_a_part_oid);
CREATE INDEX IF NOT EXISTS idx_part_alternate_link_role_b ON ck_part_alternate_link(role_b_part_oid);
CREATE INDEX IF NOT EXISTS idx_part_alternate_link_tenant ON ck_part_alternate_link(tenant_oid);

-- ==================== BOM 行替代件 (BomSubstituteLink) ====================
-- 参考 Windchill WTPartSubstituteLink，描述某个 BOM 行内子部件的局部替代件
-- 挂在 BOM 行级别，随父件迭代受控，仅在父部件该 BOM 行的上下文内生效
-- bom_link_oid: 父 BOM 行 oid（关联 ck_bom_links.oid，局部替代挂载点）
-- source_part_oid: 原子部件主对象 oid（被替代方）
-- substitute_part_oid: 替代件主对象 oid
-- substitute_type: 替代类型 EQUIVALENT/COMPLETE/PARTIAL/SUBSTITUTE
-- priority: 同 BOM 行多个替代件时的优先顺序，越小越优先
-- effectivity_json: 生效性配置 JSONB 预留（日期/批次/序列号生效性）
-- (bom_link_oid, substitute_part_oid) 联合唯一，避免同一 BOM 行重复替代件
CREATE TABLE IF NOT EXISTS ck_bom_substitute_link (
    oid                  CHAR(36)     PRIMARY KEY,
    code                 VARCHAR(50),
    name                 VARCHAR(200),
    description          VARCHAR(1000),
    bom_link_oid         CHAR(36)     NOT NULL,
    source_part_oid      CHAR(36)     NOT NULL,
    substitute_part_oid  CHAR(36)     NOT NULL,
    substitute_type      VARCHAR(20),
    substitute_quantity  DOUBLE PRECISION DEFAULT 1.0,
    substitute_unit      VARCHAR(50),
    priority             INTEGER,
    enabled              BOOLEAN      NOT NULL DEFAULT TRUE,
    effectivity_json     JSONB,
    tenant_oid           CHAR(36),
    creator              VARCHAR(100),
    created_at           TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updater              VARCHAR(100),
    updated_at           TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_bom_substitute_link_link       FOREIGN KEY (bom_link_oid)        REFERENCES ck_bom_links(oid) ON DELETE CASCADE,
    CONSTRAINT fk_bom_substitute_link_source     FOREIGN KEY (source_part_oid)     REFERENCES ck_part(oid) ON DELETE CASCADE,
    CONSTRAINT fk_bom_substitute_link_substitute FOREIGN KEY (substitute_part_oid) REFERENCES ck_part(oid) ON DELETE CASCADE
);

CREATE UNIQUE INDEX IF NOT EXISTS uk_bom_substitute_link_pair ON ck_bom_substitute_link(bom_link_oid, substitute_part_oid);
CREATE INDEX IF NOT EXISTS idx_bom_substitute_link_link       ON ck_bom_substitute_link(bom_link_oid);
CREATE INDEX IF NOT EXISTS idx_bom_substitute_link_source     ON ck_bom_substitute_link(source_part_oid);
CREATE INDEX IF NOT EXISTS idx_bom_substitute_link_substitute ON ck_bom_substitute_link(substitute_part_oid);
CREATE INDEX IF NOT EXISTS idx_bom_substitute_link_tenant     ON ck_bom_substitute_link(tenant_oid);

-- ==================== 成组替代组头 (BomSubstituteGroup) ====================
-- 描述"多个 BOM 行成员被一组替代物料整组替换"的成组替代关系
-- 组头挂父件迭代（parent_iteration_oid），随父件变更流程受控
-- 原料侧挂多个 BOM 行成员，替代侧挂一组替代物料（见 ck_bom_substitute_group_member）
-- atomic_replace: 整组替换约束（true = 必须整组替换，不允许拆开换）
-- status: 组状态 DRAFT/APPROVED/OBSOLETE，仅 APPROVED 参与下游解析
-- effectivity_json: 生效性配置 JSONB 预留（日期/批次/序列号生效性）
CREATE TABLE IF NOT EXISTS ck_bom_substitute_group (
    oid                   CHAR(36)     PRIMARY KEY,
    code                  VARCHAR(50),
    name                  VARCHAR(200),
    description           VARCHAR(1000),
    parent_iteration_oid  CHAR(36)     NOT NULL,
    status                VARCHAR(20)  NOT NULL DEFAULT 'DRAFT',
    atomic_replace        BOOLEAN      NOT NULL DEFAULT TRUE,
    enabled               BOOLEAN      NOT NULL DEFAULT TRUE,
    effectivity_json      JSONB,
    tenant_oid            CHAR(36),
    creator               VARCHAR(100),
    created_at            TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updater               VARCHAR(100),
    updated_at            TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_bom_substitute_group_parent FOREIGN KEY (parent_iteration_oid) REFERENCES ck_part_iteration(oid) ON DELETE CASCADE
);

CREATE INDEX IF NOT EXISTS idx_bom_substitute_group_parent ON ck_bom_substitute_group(parent_iteration_oid);
CREATE INDEX IF NOT EXISTS idx_bom_substitute_group_status ON ck_bom_substitute_group(status);
CREATE INDEX IF NOT EXISTS idx_bom_substitute_group_tenant ON ck_bom_substitute_group(tenant_oid);

-- ==================== 成组替代成员 (BomSubstituteGroupMember) ====================
-- 表达成组替代的 N:M 成员关系：原料侧挂多个 BOM 行成员，替代侧挂一组替代物料
-- member_side: SOURCE（原料侧，bom_link_oid 有值）/ SUBSTITUTE（替代侧，part_oid 有值）
-- quantity: 成员级数量因子（如 1 个原子件 = 3 个替代件）
-- CHECK 约束保证成员侧的字段一致性
CREATE TABLE IF NOT EXISTS ck_bom_substitute_group_member (
    oid           CHAR(36)     PRIMARY KEY,
    group_oid     CHAR(36)     NOT NULL,
    member_side   VARCHAR(20)  NOT NULL,
    bom_link_oid  CHAR(36),
    part_oid      CHAR(36),
    quantity      DOUBLE PRECISION DEFAULT 1.0,
    unit          VARCHAR(50),
    sort_order    INTEGER,
    tenant_oid    CHAR(36),
    creator       VARCHAR(100),
    created_at    TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updater       VARCHAR(100),
    updated_at    TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_bom_substitute_group_member_group FOREIGN KEY (group_oid) REFERENCES ck_bom_substitute_group(oid) ON DELETE CASCADE,
    CONSTRAINT fk_bom_substitute_group_member_link  FOREIGN KEY (bom_link_oid) REFERENCES ck_bom_links(oid) ON DELETE CASCADE,
    CONSTRAINT fk_bom_substitute_group_member_part  FOREIGN KEY (part_oid) REFERENCES ck_part(oid) ON DELETE CASCADE,
    CONSTRAINT chk_bom_substitute_group_member_side CHECK (
        (member_side = 'SOURCE' AND bom_link_oid IS NOT NULL AND part_oid IS NULL) OR
        (member_side = 'SUBSTITUTE' AND part_oid IS NOT NULL AND bom_link_oid IS NULL)
    )
);

CREATE INDEX IF NOT EXISTS idx_bom_substitute_group_member_group ON ck_bom_substitute_group_member(group_oid);
CREATE INDEX IF NOT EXISTS idx_bom_substitute_group_member_link  ON ck_bom_substitute_group_member(bom_link_oid);
CREATE INDEX IF NOT EXISTS idx_bom_substitute_group_member_part  ON ck_bom_substitute_group_member(part_oid);
CREATE INDEX IF NOT EXISTS idx_bom_substitute_group_member_tenant ON ck_bom_substitute_group_member(tenant_oid);

-- ==================== 租户 oid 索引 ====================
CREATE INDEX IF NOT EXISTS idx_org_tenant            ON ck_organization(tenant_oid);
CREATE INDEX IF NOT EXISTS idx_user_tenant           ON ck_user(tenant_oid);
CREATE INDEX IF NOT EXISTS idx_role_tenant           ON ck_role(tenant_oid);
CREATE INDEX IF NOT EXISTS idx_role_member_tenant    ON ck_role_member(tenant_oid);
CREATE INDEX IF NOT EXISTS idx_pl_tenant             ON ck_product_line(tenant_oid);
CREATE INDEX IF NOT EXISTS idx_pm_tenant             ON ck_product_model(tenant_oid);
CREATE INDEX IF NOT EXISTS idx_stage_tenant          ON ck_stage(tenant_oid);
CREATE INDEX IF NOT EXISTS idx_folder_tenant         ON ck_folder(tenant_oid);
CREATE INDEX IF NOT EXISTS idx_team_tenant           ON ck_team(tenant_oid);
CREATE INDEX IF NOT EXISTS idx_team_member_tenant    ON ck_team_member(tenant_oid);
CREATE INDEX IF NOT EXISTS idx_doc_tenant            ON ck_document(tenant_oid);
CREATE INDEX IF NOT EXISTS idx_di_tenant             ON ck_document_iteration(tenant_oid);
CREATE INDEX IF NOT EXISTS idx_file_tenant           ON ck_file(tenant_oid);
CREATE INDEX IF NOT EXISTS idx_att_tenant            ON ck_attachment(tenant_oid);
CREATE INDEX IF NOT EXISTS idx_media_tenant          ON ck_media(tenant_oid);
CREATE INDEX IF NOT EXISTS idx_process_category_tenant ON ck_process_category(tenant_oid);
CREATE INDEX IF NOT EXISTS idx_ua_tenant             ON ck_user_activity(tenant_oid);
CREATE INDEX IF NOT EXISTS idx_eid_tenant            ON ck_type_iba_data(tenant_oid);
CREATE INDEX IF NOT EXISTS idx_number_tenant          ON ck_number(tenant_oid);
CREATE INDEX IF NOT EXISTS idx_vr_tenant              ON ck_version_rule(tenant_oid);
CREATE INDEX IF NOT EXISTS idx_ls_tenant              ON ck_lifecycle_status(tenant_oid);
CREATE INDEX IF NOT EXISTS idx_lt_tenant              ON ck_lifecycle_template(tenant_oid);
CREATE INDEX IF NOT EXISTS idx_lti_tenant             ON ck_lifecycle_template_iteration(tenant_oid);
CREATE INDEX IF NOT EXISTS idx_lts_tenant             ON ck_lifecycle_template_state(tenant_oid);
CREATE INDEX IF NOT EXISTS idx_tlspl_tenant           ON ck_type_lifecycle_state_process_link(tenant_oid);
CREATE INDEX IF NOT EXISTS idx_ltt_tenant             ON ck_lifecycle_template_transition(tenant_oid);
CREATE INDEX IF NOT EXISTS idx_view_tenant            ON ck_view(tenant_oid);
CREATE INDEX IF NOT EXISTS idx_vt_tenant              ON ck_view_transition(tenant_oid);
CREATE INDEX IF NOT EXISTS idx_pl_tenant2             ON ck_type_page_layout(tenant_oid);
CREATE INDEX IF NOT EXISTS idx_td_tenant              ON ck_type_definition(tenant_oid);
CREATE INDEX IF NOT EXISTS idx_st_tenant              ON ck_stage_template(tenant_oid);
CREATE INDEX IF NOT EXISTS idx_cpl_tenant2            ON ck_cls_page_layout(tenant_oid);


-- ============================================================================
-- 流程表单模板（业务配置 → 流程表单）
--   · 内置模板由 ProcessFormTemplateInitializer 在启动时自动登记（属平台租户）
--   · 表在 TenantStatementInterceptor 里登记为 PLATFORM_SHARED：
--     查询自动放宽为 tenant_oid IN (平台, 当前租户) → 所有租户都能选到内置模板
--   · 同一节点类型允许挂多张模板（node_types 逗号分隔，节点用 DSL 的 formRef 指向其一）
-- ============================================================================
CREATE TABLE IF NOT EXISTS ck_process_form_template (
    oid           CHAR(36)     PRIMARY KEY,
    code          VARCHAR(64)  NOT NULL,
    name          VARCHAR(128) NOT NULL,
    node_types    VARCHAR(256) NOT NULL,
    component     VARCHAR(64),
    builtin       BOOLEAN      NOT NULL DEFAULT FALSE,
    enabled       BOOLEAN      NOT NULL DEFAULT TRUE,
    sort_order    INTEGER      NOT NULL DEFAULT 0,
    description   VARCHAR(1024),
    tenant_oid    CHAR(36)     NOT NULL,
    creator       VARCHAR(64),
    created_at    TIMESTAMP,
    updater       VARCHAR(64),
    updated_at    TIMESTAMP
);

CREATE UNIQUE INDEX IF NOT EXISTS uk_ck_process_form_template_code ON ck_process_form_template(tenant_oid, code);
CREATE INDEX IF NOT EXISTS idx_pft_sort        ON ck_process_form_template(builtin, sort_order);
CREATE INDEX IF NOT EXISTS idx_pft_tenant      ON ck_process_form_template(tenant_oid);
