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
    view                 VARCHAR(50),
    status               VARCHAR(50),
    tenant_oid           CHAR(36),
    creator              VARCHAR(100),
    created_at           TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updater              VARCHAR(100),
    updated_at           TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_lti_master  ON ck_lifecycle_template_iteration(master_oid);
CREATE INDEX IF NOT EXISTS idx_lti_latest  ON ck_lifecycle_template_iteration(master_oid, latest);

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
    code         VARCHAR(50)  NOT NULL UNIQUE,
    name         VARCHAR(100) NOT NULL,
    parent_oid   CHAR(36),
    description  VARCHAR(500),
    enabled      BOOLEAN      NOT NULL DEFAULT TRUE,
    creator      VARCHAR(100),
    created_at   TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updater      VARCHAR(100),
    updated_at   TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_org_parent ON ck_organization(parent_oid);

-- 多租户迁移：将 code 唯一约束改为 (code, tenant_oid) 联合唯一
-- 注意：执行前需确保所有记录的 tenant_oid 已填充
ALTER TABLE ck_organization DROP CONSTRAINT IF EXISTS ck_organization_code_key;
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
    creator      VARCHAR(100),
    created_at   TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updater      VARCHAR(100),
    updated_at   TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE UNIQUE INDEX IF NOT EXISTS idx_rm_unique ON ck_role_member(user_oid, role_oid);

-- ==================== 流程分类 ====================
-- oid 为全局唯一主键，name 为分类名称唯一键
CREATE TABLE IF NOT EXISTS ck_workflow_category (
    oid         CHAR(36)     PRIMARY KEY,
    name        VARCHAR(100) NOT NULL UNIQUE,
    sort_order  INTEGER      NOT NULL DEFAULT 0,
    creator     VARCHAR(100),
    created_at  TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updater     VARCHAR(100),
    updated_at  TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP
);

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

-- 迁移：owner_type → entity_code（兼容新旧数据库）
ALTER TABLE ck_type_iba ADD COLUMN IF NOT EXISTS owner_type VARCHAR(50);
ALTER TABLE ck_type_iba ADD COLUMN IF NOT EXISTS entity_code VARCHAR(50) NOT NULL DEFAULT '';
UPDATE ck_type_iba SET entity_code = owner_type WHERE entity_code = '' AND owner_type IS NOT NULL;
ALTER TABLE ck_type_iba DROP COLUMN IF EXISTS owner_type;

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

-- 迁移：已有 ck_tenant 表存量数据升级
ALTER TABLE ck_tenant ADD COLUMN IF NOT EXISTS admin_username VARCHAR(50);
ALTER TABLE ck_tenant ADD COLUMN IF NOT EXISTS admin_password VARCHAR(200);
ALTER TABLE ck_tenant ADD COLUMN IF NOT EXISTS admin_display_name VARCHAR(100);
ALTER TABLE ck_tenant ADD COLUMN IF NOT EXISTS approved_at TIMESTAMP;
ALTER TABLE ck_tenant ADD COLUMN IF NOT EXISTS approved_by VARCHAR(100);
ALTER TABLE ck_tenant ADD COLUMN IF NOT EXISTS reject_reason VARCHAR(500);

-- 初始化平台层租户（所有租户共享的系统配置数据归属）
INSERT INTO ck_tenant (oid, tenant_id, name, status)
VALUES ('00000000-0000-0000-0000-000000000000', 'platform', '平台层（系统配置共享）', 'ACTIVE')
ON CONFLICT (oid) DO NOTHING;

-- 初始化默认租户（已激活）
INSERT INTO ck_tenant (oid, tenant_id, name, status)
VALUES ('00000000-0000-0000-0000-000000000001', 'default', '默认租户', 'ACTIVE')
ON CONFLICT (oid) DO NOTHING;

-- ==================== 多租户：业务表添加 tenant_oid 列 ====================
-- 隔离列使用 tenant_oid CHAR(36) 引用 ck_tenant.oid（而非 tenant_id VARCHAR）
-- 原因：tenant_id 是业务标识，可能随企业更名而修改；oid 是主键，永不改变
-- 以下系统配置表不加 tenant_oid（所有租户共享）:
--   ck_lifecycle_status, ck_lifecycle_template, ck_lifecycle_template_*
--   ck_number, ck_number_segment, ck_version_rule
--   ck_type_definition, ck_iba, ck_type_iba
--   ck_attribute_definition, ck_type_page_layout
--   ck_view, ck_view_transition
--   ck_type_version_rule_link, ck_type_number_rule_link, ck_type_lifecycle_template_link
--   ck_token, ck_file_storage_config

-- Token 表增加租户信息缓存（tenant_oid 引用 ck_tenant.oid）
ALTER TABLE ck_token ADD COLUMN tenant_oid VARCHAR(50);
ALTER TABLE ck_token ADD COLUMN tenant_name VARCHAR(100);

-- 为所有业务表添加 tenant_oid 列
ALTER TABLE ck_organization ADD COLUMN tenant_oid CHAR(36);
ALTER TABLE ck_user ADD COLUMN tenant_oid CHAR(36);
ALTER TABLE ck_role ADD COLUMN tenant_oid CHAR(36);
ALTER TABLE ck_role_member ADD COLUMN tenant_oid CHAR(36);
ALTER TABLE ck_product_line ADD COLUMN tenant_oid CHAR(36);
ALTER TABLE ck_product_model ADD COLUMN tenant_oid CHAR(36);
ALTER TABLE ck_stage ADD COLUMN tenant_oid CHAR(36);
ALTER TABLE ck_folder ADD COLUMN tenant_oid CHAR(36);
ALTER TABLE ck_team ADD COLUMN tenant_oid CHAR(36);
ALTER TABLE ck_team_member ADD COLUMN tenant_oid CHAR(36);
ALTER TABLE ck_document ADD COLUMN tenant_oid CHAR(36);
ALTER TABLE ck_document_iteration ADD COLUMN tenant_oid CHAR(36);
ALTER TABLE ck_file ADD COLUMN tenant_oid CHAR(36);
ALTER TABLE ck_attachment ADD COLUMN tenant_oid CHAR(36);
ALTER TABLE ck_media ADD COLUMN tenant_oid CHAR(36);
ALTER TABLE ck_workflow_category ADD COLUMN tenant_oid CHAR(36);
ALTER TABLE ck_user_activity ADD COLUMN tenant_oid CHAR(36);
ALTER TABLE ck_type_iba_data ADD COLUMN tenant_oid CHAR(36);
-- 平台共享表
ALTER TABLE ck_number ADD COLUMN tenant_oid CHAR(36);
ALTER TABLE ck_version_rule ADD COLUMN tenant_oid CHAR(36);
ALTER TABLE ck_lifecycle_status ADD COLUMN tenant_oid CHAR(36);
ALTER TABLE ck_lifecycle_template ADD COLUMN tenant_oid CHAR(36);
ALTER TABLE ck_lifecycle_template_iteration ADD COLUMN tenant_oid CHAR(36);
ALTER TABLE ck_lifecycle_template_iteration ADD COLUMN IF NOT EXISTS display_version VARCHAR(20);
ALTER TABLE ck_lifecycle_template_state ADD COLUMN tenant_oid CHAR(36);
ALTER TABLE ck_lifecycle_template_transition ADD COLUMN tenant_oid CHAR(36);
ALTER TABLE ck_view ADD COLUMN tenant_oid CHAR(36);
ALTER TABLE ck_view_transition ADD COLUMN tenant_oid CHAR(36);
ALTER TABLE ck_type_page_layout ADD COLUMN tenant_oid CHAR(36);
ALTER TABLE ck_type_definition ADD COLUMN tenant_oid CHAR(36);
ALTER TABLE ck_stage_template ADD COLUMN tenant_oid CHAR(36);
ALTER TABLE ck_cls_page_layout ADD COLUMN tenant_oid CHAR(36);

-- ==================== 租户 oid 索引 ====================
CREATE INDEX IF NOT EXISTS idx_org_tenant ON ck_organization(tenant_oid);
CREATE INDEX IF NOT EXISTS idx_user_tenant ON ck_user(tenant_oid);
CREATE INDEX IF NOT EXISTS idx_role_tenant ON ck_role(tenant_oid);
CREATE INDEX IF NOT EXISTS idx_role_member_tenant ON ck_role_member(tenant_oid);
CREATE INDEX IF NOT EXISTS idx_pl_tenant ON ck_product_line(tenant_oid);
CREATE INDEX IF NOT EXISTS idx_pm_tenant ON ck_product_model(tenant_oid);
CREATE INDEX IF NOT EXISTS idx_stage_tenant ON ck_stage(tenant_oid);
CREATE INDEX IF NOT EXISTS idx_folder_tenant ON ck_folder(tenant_oid);
CREATE INDEX IF NOT EXISTS idx_team_tenant ON ck_team(tenant_oid);
CREATE INDEX IF NOT EXISTS idx_team_member_tenant ON ck_team_member(tenant_oid);
CREATE INDEX IF NOT EXISTS idx_doc_tenant ON ck_document(tenant_oid);
CREATE INDEX IF NOT EXISTS idx_di_tenant ON ck_document_iteration(tenant_oid);
CREATE INDEX IF NOT EXISTS idx_file_tenant ON ck_file(tenant_oid);
CREATE INDEX IF NOT EXISTS idx_att_tenant ON ck_attachment(tenant_oid);
CREATE INDEX IF NOT EXISTS idx_media_tenant ON ck_media(tenant_oid);
CREATE INDEX IF NOT EXISTS idx_wfc_tenant ON ck_workflow_category(tenant_oid);
CREATE INDEX IF NOT EXISTS idx_ua_tenant ON ck_user_activity(tenant_oid);
CREATE INDEX IF NOT EXISTS idx_eid_tenant ON ck_type_iba_data(tenant_oid);

-- ===== 删除旧 tenant_id 列（确认迁移无误后执行） =====
-- ALTER TABLE ck_organization          DROP COLUMN IF EXISTS tenant_id;
-- ALTER TABLE ck_user                  DROP COLUMN IF EXISTS tenant_id;
-- ALTER TABLE ck_role                  DROP COLUMN IF EXISTS tenant_id;
-- ALTER TABLE ck_role_member           DROP COLUMN IF EXISTS tenant_id;
-- ALTER TABLE ck_product_line          DROP COLUMN IF EXISTS tenant_id;
-- ALTER TABLE ck_product_model         DROP COLUMN IF EXISTS tenant_id;
-- ALTER TABLE ck_stage                 DROP COLUMN IF EXISTS tenant_id;
-- ALTER TABLE ck_folder                DROP COLUMN IF EXISTS tenant_id;
-- ALTER TABLE ck_team                  DROP COLUMN IF EXISTS tenant_id;
-- ALTER TABLE ck_team_member           DROP COLUMN IF EXISTS tenant_id;
-- ALTER TABLE ck_document              DROP COLUMN IF EXISTS tenant_id;
-- ALTER TABLE ck_document_iteration    DROP COLUMN IF EXISTS tenant_id;
-- ALTER TABLE ck_file                  DROP COLUMN IF EXISTS tenant_id;
-- ALTER TABLE ck_attachment            DROP COLUMN IF EXISTS tenant_id;
-- ALTER TABLE ck_media                 DROP COLUMN IF EXISTS tenant_id;
-- ALTER TABLE ck_workflow_category     DROP COLUMN IF EXISTS tenant_id;
-- ALTER TABLE ck_user_activity         DROP COLUMN IF EXISTS tenant_id;
-- ALTER TABLE ck_type_iba_data       DROP COLUMN IF EXISTS tenant_id;
-- ALTER TABLE ck_token                 DROP COLUMN IF EXISTS tenant_id;

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
CREATE INDEX IF NOT EXISTS idx_wfc_tenant            ON ck_workflow_category(tenant_oid);
CREATE INDEX IF NOT EXISTS idx_ua_tenant             ON ck_user_activity(tenant_oid);
CREATE INDEX IF NOT EXISTS idx_eid_tenant            ON ck_type_iba_data(tenant_oid);
CREATE INDEX IF NOT EXISTS idx_number_tenant          ON ck_number(tenant_oid);
CREATE INDEX IF NOT EXISTS idx_vr_tenant              ON ck_version_rule(tenant_oid);
CREATE INDEX IF NOT EXISTS idx_ls_tenant              ON ck_lifecycle_status(tenant_oid);
CREATE INDEX IF NOT EXISTS idx_lt_tenant              ON ck_lifecycle_template(tenant_oid);
CREATE INDEX IF NOT EXISTS idx_lti_tenant             ON ck_lifecycle_template_iteration(tenant_oid);
CREATE INDEX IF NOT EXISTS idx_lts_tenant             ON ck_lifecycle_template_state(tenant_oid);
CREATE INDEX IF NOT EXISTS idx_ltt_tenant             ON ck_lifecycle_template_transition(tenant_oid);
CREATE INDEX IF NOT EXISTS idx_view_tenant            ON ck_view(tenant_oid);
CREATE INDEX IF NOT EXISTS idx_vt_tenant              ON ck_view_transition(tenant_oid);
CREATE INDEX IF NOT EXISTS idx_pl_tenant2             ON ck_type_page_layout(tenant_oid);
CREATE INDEX IF NOT EXISTS idx_td_tenant              ON ck_type_definition(tenant_oid);
CREATE INDEX IF NOT EXISTS idx_st_tenant              ON ck_stage_template(tenant_oid);
CREATE INDEX IF NOT EXISTS idx_cpl_tenant2            ON ck_cls_page_layout(tenant_oid);

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
    creator          VARCHAR(100),
    created_at       TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updater          VARCHAR(100),
    updated_at       TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- 迁移：product_line_oid → parent_oid
-- 1. 确保 parent_oid 列存在
ALTER TABLE ck_product_model ADD COLUMN IF NOT EXISTS parent_oid CHAR(36);
-- 2. 确保 product_line_oid 列存在（若已误删则加回）
ALTER TABLE ck_product_model ADD COLUMN IF NOT EXISTS product_line_oid CHAR(36);
-- 3. 将 product_line_oid 值复制到 parent_oid（parent_oid 为空时）
UPDATE ck_product_model SET parent_oid = product_line_oid WHERE parent_oid IS NULL AND product_line_oid IS NOT NULL;
-- 4. 清理约束和索引
ALTER TABLE ck_product_model DROP CONSTRAINT IF EXISTS ck_product_model_product_line_oid_fkey;
DROP INDEX IF EXISTS idx_model_product_line;
-- 5. 删除冗余列
ALTER TABLE ck_product_model DROP COLUMN IF EXISTS product_line_oid;

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
    creator          VARCHAR(100),
    created_at       TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updater          VARCHAR(100),
    updated_at       TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE (owner_oid, owner_type, code)
);

-- ===== 存量数据库迁移：product_line_oid → owner_oid + 新增 owner_type =====
-- 为旧表补齐 owner_oid 和 owner_type 列（新表 CREATE TABLE 已含这些列，此处幂等）
ALTER TABLE ck_stage ADD COLUMN IF NOT EXISTS owner_oid CHAR(36);
ALTER TABLE ck_stage ADD COLUMN IF NOT EXISTS owner_type VARCHAR(10) NOT NULL DEFAULT 'LINE';

-- 为已有数据库添加 show_on_dashboard 列
ALTER TABLE ck_stage ADD COLUMN IF NOT EXISTS show_on_dashboard BOOLEAN NOT NULL DEFAULT TRUE;

-- 重建唯一约束（若旧约束仍在）
ALTER TABLE ck_stage DROP CONSTRAINT IF EXISTS ck_stage_product_line_oid_code_key;
ALTER TABLE ck_stage DROP CONSTRAINT IF EXISTS ck_stage_owner_code_unique;
ALTER TABLE ck_stage ADD CONSTRAINT ck_stage_owner_code_unique UNIQUE (owner_oid, owner_type, code);

-- 迁移索引
DROP INDEX IF EXISTS idx_stage_product_line;
CREATE INDEX IF NOT EXISTS idx_stage_owner ON ck_stage(owner_oid, owner_type);

-- ===== 提示：旧列 product_line_oid 仍保留在表中，需手动执行以下迁移 =====
-- UPDATE ck_stage SET owner_oid = product_line_oid WHERE owner_oid IS NULL;
-- ALTER TABLE ck_stage DROP COLUMN IF EXISTS product_line_oid;



-- ==================== 团队 ====================
-- oid 为全局唯一主键，code 为业务唯一键
CREATE TABLE IF NOT EXISTS ck_team (
    oid          CHAR(36)     PRIMARY KEY,
    code         VARCHAR(50)  NOT NULL UNIQUE,
    name         VARCHAR(100) NOT NULL,
    description  VARCHAR(500),
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
-- owner_oid 关联业务对象（产品线、产品型号等），通过 owner_type（未来扩展）区分

-- 迁移脚本：先尝试删除旧索引
DROP INDEX IF EXISTS idx_folder_product_stage;

-- 为已有数据库添加 type 字段
ALTER TABLE ck_folder ADD COLUMN IF NOT EXISTS type VARCHAR(10) NOT NULL DEFAULT 'USER';

-- 创建文件夹表（如果表不存在则创建，使用新的 owner_oid 和 stage_oid 字段）
CREATE TABLE IF NOT EXISTS ck_folder (
    oid               CHAR(36)     PRIMARY KEY,
    owner_oid         CHAR(36),
    stage_oid         CHAR(36),
    parent_folder_oid CHAR(36),
    name              VARCHAR(200) NOT NULL,
    type              VARCHAR(10)  NOT NULL DEFAULT 'USER',
    sort_order        INTEGER      NOT NULL DEFAULT 0,
    creator           VARCHAR(100),
    created_at        TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updater           VARCHAR(100),
    updated_at        TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- 为已有数据库添加缺失的列（如果表已存在但缺少这些列）
ALTER TABLE ck_folder ADD COLUMN IF NOT EXISTS owner_oid CHAR(36);
ALTER TABLE ck_folder ADD COLUMN IF NOT EXISTS stage_oid CHAR(36);
ALTER TABLE ck_folder ADD COLUMN IF NOT EXISTS parent_folder_oid CHAR(36);

-- 创建索引
CREATE INDEX IF NOT EXISTS idx_folder_owner_stage  ON ck_folder(owner_oid, stage_oid);
CREATE INDEX IF NOT EXISTS idx_folder_parent       ON ck_folder(parent_folder_oid);

-- ==================== 文档主对象 (Document Master) ====================
-- Windchill 对应: WTDocumentMaster
-- oid 为全局唯一主键，number 为业务编码（可由编码规则自动生成）
-- owner_oid 指向归属的产品系列(ck_product_line)或产品型号(ck_product_model)
-- owner_type 标记归属类型：LINE 或 MODEL
-- folder_oid 关联所属文件夹，stage_oid 标记所处研发阶段
-- 主文档文件通过 ck_document_iteration.ckfile_oid 关联（不同版本可关联不同主文档文件）

-- 为已有数据库添加缺失的字段
ALTER TABLE ck_document ADD COLUMN IF NOT EXISTS type_definition_code VARCHAR(50);

-- 迁移：product_line_oid → container_oid + container_type
ALTER TABLE ck_document ADD COLUMN IF NOT EXISTS container_oid CHAR(36);
ALTER TABLE ck_document ADD COLUMN IF NOT EXISTS container_type VARCHAR(20) NOT NULL DEFAULT 'PRODUCT_LINE';
-- 迁移已有数据：product_line_oid → container_oid
-- 注意：CREATE TABLE IF NOT EXISTS 不会覆盖已有表，此处迁移针对存量数据
-- 如果 container_oid 为空且 product_line_oid 有值，则复制
ALTER TABLE ck_document DROP CONSTRAINT IF EXISTS fk_doc_product_line;
DROP INDEX IF EXISTS idx_doc_product_line;

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
    creator           VARCHAR(100),
    created_at        TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updater           VARCHAR(100),
    updated_at        TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_doc_folder       FOREIGN KEY (folder_oid)       REFERENCES ck_folder(oid) ON DELETE SET NULL
);

CREATE INDEX IF NOT EXISTS idx_doc_container ON ck_document(container_oid);
CREATE INDEX IF NOT EXISTS idx_doc_folder ON ck_document(folder_oid);
CREATE INDEX IF NOT EXISTS idx_doc_stage  ON ck_document(stage_oid);

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
    classification_oid    CHAR(36),
    creator               VARCHAR(100),
    created_at            TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updater               VARCHAR(100),
    updated_at            TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_part_folder         FOREIGN KEY (folder_oid)         REFERENCES ck_folder(oid) ON DELETE SET NULL,
    CONSTRAINT fk_part_classification FOREIGN KEY (classification_oid) REFERENCES ck_classification(oid) ON DELETE SET NULL
);

CREATE INDEX IF NOT EXISTS idx_part_container  ON ck_part(container_oid);
CREATE INDEX IF NOT EXISTS idx_part_folder    ON ck_part(folder_oid);
CREATE INDEX IF NOT EXISTS idx_part_stage     ON ck_part(stage_oid);
CREATE INDEX IF NOT EXISTS idx_part_cls       ON ck_part(classification_oid);

-- ==================== 部件子版本 (Part Iteration) ====================
-- 参考 Windchill WTPart，与 Part 为 1:N 版本历史关系
CREATE TABLE IF NOT EXISTS ck_part_iteration (
    oid                              CHAR(36)     PRIMARY KEY,
    master_oid                       CHAR(36)     NOT NULL REFERENCES ck_part(oid) ON DELETE CASCADE,
    revision                         VARCHAR(10)  NOT NULL DEFAULT 'A',
    iteration                        INTEGER      NOT NULL DEFAULT 1,
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
    branch_id                        VARCHAR(50),
    delete_mark                      BOOLEAN      NOT NULL DEFAULT FALSE,
    creator                          VARCHAR(100),
    created_at                       TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updater                          VARCHAR(100),
    updated_at                       TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_pi_master  ON ck_part_iteration(master_oid);
CREATE INDEX IF NOT EXISTS idx_pi_latest  ON ck_part_iteration(master_oid, latest);

-- 数据库迁移：添加 tenant_oid 列
ALTER TABLE ck_part ADD COLUMN IF NOT EXISTS tenant_oid CHAR(36);
ALTER TABLE ck_part_iteration ADD COLUMN IF NOT EXISTS tenant_oid CHAR(36);

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
    view                             VARCHAR(50),
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

-- 数据库迁移：添加 IterationEntity 新增字段
ALTER TABLE ck_part_iteration ADD COLUMN IF NOT EXISTS version_sort INTEGER NOT NULL DEFAULT 0;
ALTER TABLE ck_part_iteration ADD COLUMN IF NOT EXISTS branch_id VARCHAR(50);
ALTER TABLE ck_part_iteration ADD COLUMN IF NOT EXISTS delete_mark BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE ck_part_iteration ADD COLUMN IF NOT EXISTS display_version VARCHAR(20);

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
    creator            VARCHAR(100),
    created_at         TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updater            VARCHAR(100),
    updated_at         TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- 数据库迁移：为已有 ck_file 表添加 source_type / source_url（兼容旧数据）
ALTER TABLE ck_file ADD COLUMN IF NOT EXISTS source_type VARCHAR(10) DEFAULT 'LOCAL';
ALTER TABLE ck_file ADD COLUMN IF NOT EXISTS source_url VARCHAR(2000);

-- ==================== 附件存储实体 (CKAttachment) ====================
-- 通用附件实体，通过 owner_oid 关联其所属业务对象（可被 DocumentIteration、Part、CR 等多种实体复用），1:N
-- 兼容旧表结构：确保 owner_oid 字段存在
ALTER TABLE IF EXISTS ck_attachment ADD COLUMN IF NOT EXISTS owner_oid CHAR(36);

CREATE TABLE IF NOT EXISTS ck_attachment (
    oid                CHAR(36)     PRIMARY KEY,
    owner_oid          CHAR(36)     NOT NULL,
    file_name          VARCHAR(255) NOT NULL,
    file_size          BIGINT,
    storage_path       VARCHAR(500),
    mime_type          VARCHAR(100),
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
    checked_out        BOOLEAN      NOT NULL DEFAULT FALSE,
    checked_out_by     VARCHAR(100),
    checked_out_comment VARCHAR(500),
    latest             BOOLEAN      NOT NULL DEFAULT TRUE,
    derived_from_oid   CHAR(36),
    derived_at         TIMESTAMP,
    view               VARCHAR(50),
    status             VARCHAR(50),
    ckfile_oid         CHAR(36)     REFERENCES ck_file(oid) ON DELETE SET NULL,
    version_sort       INTEGER      NOT NULL DEFAULT 0,
    branch_id          VARCHAR(50),
    delete_mark        BOOLEAN      NOT NULL DEFAULT FALSE,
    creator            VARCHAR(100),
    created_at         TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updater            VARCHAR(100),
    updated_at         TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- 数据库迁移：为已有表添加 ckfile_oid 列（若表已存在但缺少该字段）
ALTER TABLE ck_document_iteration ADD COLUMN IF NOT EXISTS ckfile_oid CHAR(36);

-- 数据库迁移：添加 lifecycle_template_iteration_oid（记录绑定的生命周期模板迭代版本）
ALTER TABLE ck_document_iteration ADD COLUMN IF NOT EXISTS lifecycle_template_iteration_oid CHAR(36);

-- 数据库迁移：添加 IterationEntity 新增字段
ALTER TABLE ck_document_iteration ADD COLUMN IF NOT EXISTS version_sort INTEGER NOT NULL DEFAULT 0;
ALTER TABLE ck_document_iteration ADD COLUMN IF NOT EXISTS branch_id VARCHAR(50);
ALTER TABLE ck_document_iteration ADD COLUMN IF NOT EXISTS delete_mark BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE ck_document_iteration ADD COLUMN IF NOT EXISTS display_version VARCHAR(20);

CREATE INDEX IF NOT EXISTS idx_di_master   ON ck_document_iteration(master_oid);
CREATE INDEX IF NOT EXISTS idx_di_latest   ON ck_document_iteration(master_oid, latest);
CREATE INDEX IF NOT EXISTS idx_di_ckfile   ON ck_document_iteration(ckfile_oid);
  
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
    creator        VARCHAR(64),
    created_at     TIMESTAMP,
    updater        VARCHAR(64),
    updated_at     TIMESTAMP
);
-- 扩展字段兼容（已存在表时安全追加）
ALTER TABLE ck_user_activity ADD COLUMN IF NOT EXISTS operator_ip VARCHAR(64);
ALTER TABLE ck_user_activity ADD COLUMN IF NOT EXISTS user_agent VARCHAR(512);
ALTER TABLE ck_user_activity ADD COLUMN IF NOT EXISTS result VARCHAR(20);
ALTER TABLE ck_user_activity ADD COLUMN IF NOT EXISTS duration_ms INTEGER;
ALTER TABLE ck_user_activity ADD COLUMN IF NOT EXISTS error_message VARCHAR(500);
ALTER TABLE ck_user_activity ADD COLUMN IF NOT EXISTS detail_json TEXT;
  
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
    creator          VARCHAR(64),
    created_at       TIMESTAMP,
    updater          VARCHAR(64),
    updated_at       TIMESTAMP
);
-- MinIO/跨平台字段兼容（已存在表时安全追加）
ALTER TABLE ck_file_storage_config ADD COLUMN IF NOT EXISTS endpoint VARCHAR(512);
ALTER TABLE ck_file_storage_config ADD COLUMN IF NOT EXISTS access_key VARCHAR(256);
ALTER TABLE ck_file_storage_config ADD COLUMN IF NOT EXISTS secret_key VARCHAR(256);
ALTER TABLE ck_file_storage_config ADD COLUMN IF NOT EXISTS bucket_name VARCHAR(128);
ALTER TABLE ck_file_storage_config ADD COLUMN IF NOT EXISTS base_url VARCHAR(512);
ALTER TABLE ck_file_storage_config ADD COLUMN IF NOT EXISTS max_capacity_mb INTEGER;
ALTER TABLE ck_file_storage_config ADD COLUMN IF NOT EXISTS alert_threshold_percent INTEGER DEFAULT 80;
ALTER TABLE ck_file_storage_config ADD COLUMN IF NOT EXISTS tenant_oid CHAR(36);

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

-- ==================== 分类管理 ====================
-- 树形层级结构，通过 parent_oid 自引用，identifier 为 API 路由标识
CREATE TABLE IF NOT EXISTS ck_classification (
    oid          CHAR(36)     PRIMARY KEY,
    code         VARCHAR(50)  NOT NULL,
    name         VARCHAR(100) NOT NULL,
    display_name VARCHAR(200),
    description  VARCHAR(500),
    identifier   VARCHAR(100),
    thumbnail    VARCHAR(500),
    parent_oid   CHAR(36),
    tenant_oid   CHAR(36),
    sort_order   INTEGER      NOT NULL DEFAULT 0,
    creator      VARCHAR(100),
    created_at   TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updater      VARCHAR(100),
    updated_at   TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_cls_parent   ON ck_classification(parent_oid);
CREATE INDEX IF NOT EXISTS idx_cls_tenant   ON ck_classification(tenant_oid);
CREATE INDEX IF NOT EXISTS idx_cls_identifier ON ck_classification(identifier);

-- ==================== 分类 IBA 数据 ====================
-- 存储分类节点对应的 IBA 属性值，一个分类节点的每个 IBA 属性对应一条记录
CREATE TABLE IF NOT EXISTS ck_cls_iba_data (
    classification_oid CHAR(36)     NOT NULL,
    attr_code          VARCHAR(100) NOT NULL,
    attr_value         JSONB        NOT NULL DEFAULT 'null'::jsonb,
    tenant_oid         CHAR(36),
    creator            VARCHAR(100),
    created_at         TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updater            VARCHAR(100),
    updated_at         TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (classification_oid, attr_code)
);

CREATE INDEX IF NOT EXISTS idx_cid_cls    ON ck_cls_iba_data(classification_oid);
CREATE INDEX IF NOT EXISTS idx_cid_attr   ON ck_cls_iba_data(attr_code);
CREATE INDEX IF NOT EXISTS idx_cid_tenant ON ck_cls_iba_data(tenant_oid);

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




