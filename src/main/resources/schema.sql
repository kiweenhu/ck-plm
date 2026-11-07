-- =================================================================
--  主键规范：所有表统一使用 oid CHAR(36) PRIMARY KEY（UUID v4）
--  禁止使用自增 id (BIGSERIAL / AUTO_INCREMENT) 作为主键
--  业务唯一标识使用 code VARCHAR(50) UNIQUE NOT NULL
-- =================================================================

-- ==================== 生命周期状态 ====================
-- oid 为全局唯一主键，code 为业务唯一键；唯一键 (code, tenant_oid) ——
-- 生命周期状态**按租户隔离**（2026-10-11 起，随 platform-base / electronics-core 各装一份），
-- 否则第二个租户装包会与平台行撞 (code) 唯一约束。
CREATE TABLE IF NOT EXISTS ck_lifecycle_status (
    oid          CHAR(36)     PRIMARY KEY,
    code         VARCHAR(50)  NOT NULL,
    name         VARCHAR(200),
    display_name VARCHAR(200),
    tenant_oid   CHAR(36),
    creator      VARCHAR(100),
    created_at   TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updater      VARCHAR(100),
    updated_at   TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE (code, tenant_oid)
);

-- ==================== 生命周期模板 ====================
-- 同款隔离与复合唯一键（见 ck_lifecycle_status 上注释）
CREATE TABLE IF NOT EXISTS ck_lifecycle_template (
    oid                CHAR(36)     PRIMARY KEY,
    code               VARCHAR(50)  NOT NULL,
    name               VARCHAR(100) NOT NULL,
    description        VARCHAR(500),
    is_active          BOOLEAN      NOT NULL DEFAULT TRUE,
    initial_state_code VARCHAR(50),
    tenant_oid         CHAR(36),
    creator            VARCHAR(100),
    created_at         TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updater            VARCHAR(100),
    updated_at         TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE (code, tenant_oid)
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
    -- 该状态下是否允许修订（新建大版本），同时也是「能否发起变更」的判据。
    -- 默认 TRUE：存量模板保持原有"任何状态都能修订"的行为，由管理员按状态显式关闭。
    revisable           BOOLEAN      NOT NULL DEFAULT TRUE,
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
-- oid 为全局唯一主键，code 为业务唯一键；唯一键 (code, tenant_oid) ——
-- 编码规则**按租户隔离**（2026-10-11 起，随 platform-base 各装一份；序号按租户各自自增）
CREATE TABLE IF NOT EXISTS ck_number (
    oid         CHAR(36)     PRIMARY KEY,
    code        VARCHAR(50)  NOT NULL,
    name        VARCHAR(100) NOT NULL,
    description VARCHAR(500),
    enabled     BOOLEAN      NOT NULL DEFAULT TRUE,
    tenant_oid  CHAR(36),
    creator     VARCHAR(100),
    created_at  TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updater     VARCHAR(100),
    updated_at  TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE (code, tenant_oid)
);

-- ==================== 版本规则定义 ====================
-- 简化的版本规则表，支持模板化规则定义；同款按租户隔离与复合唯一键（见上）
-- 规则定义格式示例: (A,B,C,D,E,F,G,H), (YYYYMMDD)-(SEQ:6), (PREFIX:DOC)-(SEQ:4)
CREATE TABLE IF NOT EXISTS ck_version_rule (
    oid               CHAR(36)     PRIMARY KEY,
    name              VARCHAR(100) NOT NULL,
    code              VARCHAR(50)  NOT NULL,
    rule_definition   VARCHAR(500) NOT NULL,
    description       VARCHAR(500),
    applicable_type   VARCHAR(50),
    sequence_value    BIGINT       NOT NULL DEFAULT 0,
    enabled           BOOLEAN      NOT NULL DEFAULT TRUE,
    tenant_oid        CHAR(36),
    creator           VARCHAR(100),
    created_at        TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updater           VARCHAR(100),
    updated_at        TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE (code, tenant_oid)
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
-- oid 为全局唯一主键；rule_code 引用 ck_number.code（业务唯一键）
-- segment_type 取值: CONST | SEPARATOR | YEAR | MONTH | DAY | SERIAL
-- 2026-10-11：本表**按租户隔离**（从 SHARED_TABLES 移出）—— 增加 tenant_oid 列，
-- 外键改为复合 (rule_code, tenant_oid) → ck_number(code, tenant_oid)：段必须归属与规则相同的租户。
-- 不改会怎样：ck_number 唯一键改为 (code, tenant_oid) 后，旧的单列外键引用非唯一列，PG 直接拒绝。
CREATE TABLE IF NOT EXISTS ck_number_segment (
    oid           CHAR(36)     PRIMARY KEY,
    rule_code     VARCHAR(50)  NOT NULL,
    sort_order    INTEGER      NOT NULL,
    segment_type  VARCHAR(20)  NOT NULL,
    fixed_value   VARCHAR(100),
    date_format   VARCHAR(20),
    serial_length INTEGER,
    serial_start  INTEGER      DEFAULT 1,
    current_value INTEGER      DEFAULT 0,
    description   VARCHAR(200),
    config        TEXT,
    tenant_oid    CHAR(36),
    creator       VARCHAR(100),
    created_at    TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updater       VARCHAR(100),
    updated_at    TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (rule_code, tenant_oid) REFERENCES ck_number(code, tenant_oid) ON DELETE CASCADE
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
    -- 部门主管（引用 ck_user.oid）：流程里「发起人上级」策略据此解析审批人
    leader_oid   CHAR(36),
    description  VARCHAR(500),
    enabled      BOOLEAN      NOT NULL DEFAULT TRUE,
    tenant_oid   CHAR(36),
    creator      VARCHAR(100),
    created_at   TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updater      VARCHAR(100),
    updated_at   TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- 存量库升级：本工程没有迁移框架（sql.init.mode=never），本文件同时充当升级脚本，
-- 重跑一次即可补齐新增列。新增列请统一写成这种 ADD COLUMN IF NOT EXISTS。
ALTER TABLE ck_organization ADD COLUMN IF NOT EXISTS leader_oid CHAR(36);

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
-- oid 为全局唯一主键；(code, tenant_oid) 组合唯一（下方唯一索引 uk_ck_role_code_tenant）——
-- 角色按租户隔离（2026-10-11），每个租户持有同名 code 的角色副本，code 不能再全局唯一
-- role_type 取值: PLATFORM（平台级角色，系统初始化导入，不可编辑/删除）| BUSINESS（自定义业务流程角色，可自由维护）
CREATE TABLE IF NOT EXISTS ck_role (
    oid          CHAR(36)     PRIMARY KEY,
    code         VARCHAR(50)  NOT NULL,
    name         VARCHAR(100) NOT NULL,
    description  VARCHAR(500),
    role_type    VARCHAR(20)  NOT NULL DEFAULT 'BUSINESS',
    tenant_oid   CHAR(36),
    creator      VARCHAR(100),
    created_at   TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updater      VARCHAR(100),
    updated_at   TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- 存量库迁移（幂等，兼容 Spring SQL 脚本解析器——不能用 DO $$ 块）：
-- 早期版本 code 全局唯一（约束 ck_role_code_key），会阻断"审核通过时为租户复制角色副本"；
-- 统一用组合唯一索引保证 (code, tenant_oid) 唯一，新老库行为一致。
ALTER TABLE ck_role DROP CONSTRAINT IF EXISTS ck_role_code_key;
CREATE UNIQUE INDEX IF NOT EXISTS uk_ck_role_code_tenant ON ck_role (code, tenant_oid);

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
-- 注：此前实际建表由 Java 启动初始化器执行、此处仅作 DDL 参考；现统一以本脚本为准。
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
-- 注：此前实际建表由 Java 启动初始化器执行、此处仅作 DDL 参考；现统一以本脚本为准。
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
-- ==================== 业务域 (BusinessDomain) ====================
-- 类型/对象的「域标签」：域是业务的划分（产品主数据域 / 电子设计域 / 结构设计域 …），
-- 与类型继承（ck_type_definition.parent_oid）是两个正交维度，允许子类型与父类型不同域。
-- 平台预置、不允许租户自定义 → 全租户共享同一套域（TenantStatementInterceptor 里按共享表处理）。
-- 类型通过 ck_type_definition.domain_oid 软引用本表 oid（不加外键）。
CREATE TABLE IF NOT EXISTS ck_business_domain (
    oid          CHAR(36)     PRIMARY KEY,
    code         VARCHAR(64)  NOT NULL,
    name         VARCHAR(200) NOT NULL,
    icon         VARCHAR(100),
    description  VARCHAR(1000),
    sort_order   INTEGER      NOT NULL DEFAULT 0,
    enabled      BOOLEAN      NOT NULL DEFAULT TRUE,
    source       VARCHAR(20)  NOT NULL DEFAULT 'OOTB',
    tenant_oid   CHAR(36)     NOT NULL,
    creator      VARCHAR(100),
    created_at   TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updater      VARCHAR(100),
    updated_at   TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE UNIQUE INDEX IF NOT EXISTS uk_business_domain_code ON ck_business_domain(code);

-- ==================== 流程模板 / 交付物 / 零件-文档关联（最后一批） ====================
-- 来源：ck_process_template 原先由 Java 启动初始化器在代码里建（DDL 已迁入本脚本）、
--       备份库结构（ck_deliverable / ck_part_document_link 及其索引）。
CREATE TABLE IF NOT EXISTS ck_process_template (
    oid                   VARCHAR(64)  PRIMARY KEY,
    key                   VARCHAR(128) NOT NULL,
    name                  VARCHAR(255) NOT NULL,
    display_name          VARCHAR(255),
    category_oid          VARCHAR(64),
    description           TEXT,
    dsl_json              TEXT,
    latest_version        INTEGER      NOT NULL DEFAULT 0,
    enabled               BOOLEAN      NOT NULL DEFAULT TRUE,
    deployed_version      INTEGER,
    deployment_id         VARCHAR(64),
    process_definition_id VARCHAR(128),
    deployed_at           TIMESTAMP,
    tenant_oid            VARCHAR(64)  NOT NULL,
    creator               VARCHAR(128),
    created_at            TIMESTAMP,
    updater               VARCHAR(128),
    updated_at            TIMESTAMP
);

CREATE UNIQUE INDEX IF NOT EXISTS uk_process_template_key_tenant ON ck_process_template (key, tenant_oid);
CREATE INDEX IF NOT EXISTS idx_process_template_tenant ON ck_process_template (tenant_oid);
CREATE INDEX IF NOT EXISTS idx_process_template_category_oid ON ck_process_template (category_oid);

-- ==================== 方法论实例（六类）====================
-- 创建产品系列 / 型号时，从本租户的六类方法论模板「快照」出实例（与 ck_stage 同一套路数）：
--   交付件 ck_deliverable / 阶段门 ck_gate / 评审 ck_review / 检查规则 ck_rule /
--   指标 ck_metric / 驾驶舱 ck_dashboard
--
-- 三个共同约定：
--   · 归属：owner_oid + owner_type（LINE=产品系列 / MODEL=产品型号），与 ck_stage 完全一致；
--   · 快照：内容列是创建那一刻从模板复制的 —— 模板以后改了不回灌，实例上的裁剪也不会被模板覆盖；
--   · 幂等键：UNIQUE (owner_oid, owner_type, code)，同一归属单元同一个码只应有一条。
-- template_code 记录"从哪条模板快照而来"（项目自建条目为 NULL），供溯源与将来与模板对齐。
-- disabled = 本项目裁剪（这一条不参与本项目）；各表另有各自的执行态字段（status/decision/verdict/value…）。
--
-- 注：本组表按「业务表」处理（与 ck_stage 一致）—— tenant_oid 由租户拦截器在 INSERT 时注入，
--     Mapper 的 INSERT / SELECT 都不显式书写租户条件；但列必须存在，否则一条语句都跑不通
--     （旧的 ck_deliverable 就因为没有这一列而"物理不可访问"）。

-- 交付件实例（阶段交付清单）
CREATE TABLE IF NOT EXISTS ck_deliverable (
    oid             CHAR(36)      PRIMARY KEY,
    owner_oid       CHAR(36)      NOT NULL,
    owner_type      VARCHAR(10)   NOT NULL DEFAULT 'LINE',
    code            VARCHAR(50)   NOT NULL,
    name            VARCHAR(200)  NOT NULL,
    description     VARCHAR(1000),
    stage_key       VARCHAR(50)   NOT NULL,          -- ↔ ck_stage.code
    discipline      VARCHAR(30),
    mandatory       BOOLEAN       NOT NULL DEFAULT FALSE,
    owner_role      VARCHAR(50),                     -- 责任人（角色码；具体指派的人记在 owner）
    object_type     VARCHAR(50),
    bind_mode       VARCHAR(30),
    completion_rule TEXT,                            -- 判定"什么叫完成"（快照）
    due_offset_days INTEGER,
    gate            VARCHAR(30),                     -- 须等于本阶段的门
    industry_note   VARCHAR(1000),
    template_code   VARCHAR(50),
    status          VARCHAR(20)   NOT NULL DEFAULT 'PENDING',  -- PENDING/IN_PROGRESS/DONE/NA
    owner           VARCHAR(100),
    due_date        DATE,
    evidence        TEXT,                            -- 判定证据（谁/何时/按哪条判的）
    artifact_refs   TEXT,                            -- 产出物关联（文档/零件 oid）
    disabled        BOOLEAN       NOT NULL DEFAULT FALSE,
    sort_order      INTEGER       NOT NULL DEFAULT 0,
    tenant_oid      CHAR(36),
    creator         VARCHAR(100),
    created_at      TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updater         VARCHAR(100),
    updated_at      TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE (owner_oid, owner_type, code)
);

CREATE INDEX IF NOT EXISTS idx_del_owner  ON ck_deliverable (owner_oid, owner_type, stage_key);
CREATE INDEX IF NOT EXISTS idx_del_status ON ck_deliverable (owner_oid, owner_type, status);

-- 阶段门实例（TR/决策门）
CREATE TABLE IF NOT EXISTS ck_gate (
    oid                   CHAR(36)      PRIMARY KEY,
    owner_oid             CHAR(36)      NOT NULL,
    owner_type            VARCHAR(10)   NOT NULL DEFAULT 'LINE',
    code                  VARCHAR(50)   NOT NULL,
    name                  VARCHAR(200)  NOT NULL,
    description           VARCHAR(1000),
    stage_key             VARCHAR(50),
    type                  VARCHAR(30),
    process_key           VARCHAR(100),
    decision_options      TEXT,
    auto_checks           TEXT,
    required_participants TEXT,
    record_fields         TEXT,
    evidence_required     BOOLEAN       NOT NULL DEFAULT FALSE,  -- 与模板同型（模板该列即 BOOLEAN）
    industry_note         VARCHAR(1000),
    template_code         VARCHAR(50),
    status                VARCHAR(20)   NOT NULL DEFAULT 'PENDING',  -- PENDING/READY/PASSED/REJECTED
    decision              VARCHAR(50),
    decided_by            VARCHAR(100),
    decided_at            TIMESTAMP,
    decision_note         VARCHAR(1000),
    disabled              BOOLEAN       NOT NULL DEFAULT FALSE,
    sort_order            INTEGER       NOT NULL DEFAULT 0,
    tenant_oid            CHAR(36),
    creator               VARCHAR(100),
    created_at            TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updater               VARCHAR(100),
    updated_at            TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE (owner_oid, owner_type, code)
);

CREATE INDEX IF NOT EXISTS idx_gate_owner ON ck_gate (owner_oid, owner_type, stage_key);

-- 评审实例
CREATE TABLE IF NOT EXISTS ck_review (
    oid             CHAR(36)      PRIMARY KEY,
    owner_oid       CHAR(36)      NOT NULL,
    owner_type      VARCHAR(10)   NOT NULL DEFAULT 'LINE',
    code            VARCHAR(50)   NOT NULL,
    name            VARCHAR(200)  NOT NULL,
    description     VARCHAR(1000),
    stage_key       VARCHAR(50),                 -- → ck_stage.code（2026-10-29 起技术评审直接挂阶段）
    gate            VARCHAR(30),                 -- 老数据/兼容：仍可能带 gate（技术门时代写入的）
    type            VARCHAR(30),
    process_key     VARCHAR(100),
    check_items     TEXT,
    output_types    TEXT,
    verdict_options TEXT,
    industry_note   VARCHAR(1000),
    template_code   VARCHAR(50),
    status          VARCHAR(20)   NOT NULL DEFAULT 'PENDING',  -- PENDING/DONE/NA
    verdict         VARCHAR(50),
    verdict_note    VARCHAR(1000),
    reviewed_at     TIMESTAMP,
    disabled        BOOLEAN       NOT NULL DEFAULT FALSE,
    sort_order      INTEGER       NOT NULL DEFAULT 0,
    tenant_oid      CHAR(36),
    creator         VARCHAR(100),
    created_at      TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updater         VARCHAR(100),
    updated_at      TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE (owner_oid, owner_type, code)
);

CREATE INDEX IF NOT EXISTS idx_review_owner ON ck_review (owner_oid, owner_type, gate);
CREATE INDEX IF NOT EXISTS idx_review_stage ON ck_review (owner_oid, owner_type, stage_key);

-- 检查规则实例
CREATE TABLE IF NOT EXISTS ck_rule (
    oid              CHAR(36)      PRIMARY KEY,
    owner_oid        CHAR(36)      NOT NULL,
    owner_type       VARCHAR(10)   NOT NULL DEFAULT 'LINE',
    code             VARCHAR(50)   NOT NULL,
    name             VARCHAR(200)  NOT NULL,
    description      VARCHAR(1000),
    trigger_type     VARCHAR(30),
    scope            VARCHAR(50),
    category         VARCHAR(50),
    stage_key        VARCHAR(50),
    gate             VARCHAR(30),
    condition        TEXT,
    severity         VARCHAR(20),
    message          VARCHAR(1000),
    default_override VARCHAR(200),
    fix_action       VARCHAR(500),
    industry_note    VARCHAR(1000),
    template_code    VARCHAR(50),
    status           VARCHAR(20)   NOT NULL DEFAULT 'PENDING',  -- PENDING/PASS/FAIL/NA
    last_checked_at  TIMESTAMP,
    findings         TEXT,
    disabled         BOOLEAN       NOT NULL DEFAULT FALSE,
    sort_order       INTEGER       NOT NULL DEFAULT 0,
    tenant_oid       CHAR(36),
    creator          VARCHAR(100),
    created_at       TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updater          VARCHAR(100),
    updated_at       TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE (owner_oid, owner_type, code)
);

CREATE INDEX IF NOT EXISTS idx_rule_owner ON ck_rule (owner_oid, owner_type, stage_key);

-- 指标实例（计算口径随模板快照；value/readiness 是运行期评估结果）
CREATE TABLE IF NOT EXISTS ck_metric (
    oid           CHAR(36)      PRIMARY KEY,
    owner_oid     CHAR(36)      NOT NULL,
    owner_type    VARCHAR(10)   NOT NULL DEFAULT 'LINE',
    code          VARCHAR(50)   NOT NULL,
    name          VARCHAR(200)  NOT NULL,
    description   VARCHAR(1000),
    layer         VARCHAR(50),
    level         VARCHAR(50),
    source        VARCHAR(500),
    industry_note VARCHAR(1000),
    template_code VARCHAR(50),
    value         NUMERIC(20,4),
    value_text    VARCHAR(200),
    readiness     VARCHAR(10),                     -- A/B/C（数据就绪度；未评估则为空）
    evaluated_at  TIMESTAMP,
    disabled      BOOLEAN       NOT NULL DEFAULT FALSE,
    sort_order    INTEGER       NOT NULL DEFAULT 0,
    tenant_oid    CHAR(36),
    creator       VARCHAR(100),
    created_at    TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updater       VARCHAR(100),
    updated_at    TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE (owner_oid, owner_type, code)
);

CREATE INDEX IF NOT EXISTS idx_metric_owner ON ck_metric (owner_oid, owner_type, layer);

-- 驾驶舱实例（视角布局：经营战报 / 系列战况 / 型号作战地图）
CREATE TABLE IF NOT EXISTS ck_dashboard (
    oid                CHAR(36)      PRIMARY KEY,
    owner_oid          CHAR(36)      NOT NULL,
    owner_type         VARCHAR(10)   NOT NULL DEFAULT 'LINE',
    code               VARCHAR(50)   NOT NULL,
    name               VARCHAR(200)  NOT NULL,
    description        VARCHAR(1000),
    audience           VARCHAR(100),
    page_count         INTEGER,
    widgets            TEXT,
    generate_policy    VARCHAR(50),
    primary_scope      BOOLEAN       NOT NULL DEFAULT FALSE,
    industry_note      VARCHAR(1000),
    template_code      VARCHAR(50),
    last_generated_at  TIMESTAMP,
    disabled           BOOLEAN       NOT NULL DEFAULT FALSE,
    sort_order         INTEGER       NOT NULL DEFAULT 0,
    tenant_oid         CHAR(36),
    creator            VARCHAR(100),
    created_at         TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updater            VARCHAR(100),
    updated_at         TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE (owner_oid, owner_type, code)
);

CREATE INDEX IF NOT EXISTS idx_dashboard_owner ON ck_dashboard (owner_oid, owner_type);

-- 零件 ↔ 文档 关联
CREATE TABLE IF NOT EXISTS ck_part_document_link (
    oid          CHAR(36)     PRIMARY KEY,
    code         VARCHAR(50),
    name         VARCHAR(200),
    description  VARCHAR(1000),
    part_oid     CHAR(36)     NOT NULL,
    document_oid CHAR(36)     NOT NULL,
    link_type    VARCHAR(20)  NOT NULL DEFAULT 'REFERENCE',
    enabled      BOOLEAN      NOT NULL DEFAULT TRUE,
    tenant_oid   CHAR(36),
    creator      VARCHAR(100),
    created_at   TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updater      VARCHAR(100),
    updated_at   TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_part_document_link_doc ON ck_part_document_link (document_oid);
CREATE INDEX IF NOT EXISTS idx_part_document_link_part ON ck_part_document_link (part_oid);
CREATE UNIQUE INDEX IF NOT EXISTS uk_part_document_link ON ck_part_document_link (part_oid, document_oid, link_type);

-- 零件 ↔ 文档 关联（权威表）：Windchill 两分模型 DESCRIBES / REFERENCE，迭代级。
-- 由 PartDescribeLink 与 PartReferenceLink 共享，用 link_type 区分；
-- 上面的 ck_part_document_link 是主数据级旧表（DESCRIPTION / REFERENCE），仅作历史迁移来源。
-- 注：本表原先只由 Java 启动初始化器在代码里建，现已收进本脚本。
CREATE TABLE IF NOT EXISTS ck_doc_part_link (
    oid                    CHAR(36)     PRIMARY KEY,
    link_type              VARCHAR(30)  NOT NULL,
    part_iteration_oid     CHAR(36)     NOT NULL,
    doc_master_oid         CHAR(36)     NOT NULL,
    doc_iteration_oid      CHAR(36),
    resolved_iteration_oid CHAR(36),
    category               VARCHAR(100),
    tenant_oid             CHAR(36),
    creator                VARCHAR(100),
    created_at             TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updater                VARCHAR(100),
    updated_at             TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX IF NOT EXISTS idx_dpl_part_iter  ON ck_doc_part_link(part_iteration_oid);
CREATE INDEX IF NOT EXISTS idx_dpl_doc_master ON ck_doc_part_link(doc_master_oid);
CREATE INDEX IF NOT EXISTS idx_dpl_type       ON ck_doc_part_link(link_type);
CREATE UNIQUE INDEX IF NOT EXISTS uk_dpl_triple
    ON ck_doc_part_link(part_iteration_oid, doc_master_oid, link_type);

-- 交付件 ↔ 产出物 关联（2026-10-11 新增）。
-- 背景：IPD 交付件的产出物**不只文档** —— 原理图 / PCB / 结构件 / 软件等也是业务对象
-- （见 ck_type_definition：SCHEMATIC / PCB_LAYOUT / FOOTPRINT / SYMBOL 挂 ENG_DOCUMENT，
--   STRUCTURAL / SOFTWARE / ELECTRONIC 挂 PART）。三类产出物表结构同构
-- （oid / number / name / type_definition_code / container_oid / stage_oid），故统一一张关联表。
-- 硬约束：可关联的对象类别由交付件模板的 object_type 决定（object_type 非空时只认该类型；
--   为空时按 bind_mode：DOCUMENT→文档宿主，PART_SET→零组件宿主），服务端在创建时校验。
-- 跨容器：允许引用资源库 / 其它型号的对象 —— 本表只记 container_oid 快照供展示，不设容器限制。
-- role 预留 PRIMARY / SUPPORTING（P0 固定 PRIMARY，为后续主证据/辅证分级留列不返工）。
CREATE TABLE IF NOT EXISTS ck_deliverable_artifact_link (
    oid             CHAR(36)     PRIMARY KEY,
    deliverable_oid CHAR(36)     NOT NULL,
    entity_type     VARCHAR(30)  NOT NULL,
    type_code       VARCHAR(50)  NOT NULL,
    entity_oid      CHAR(36)     NOT NULL,
    iteration_oid   CHAR(36),
    role            VARCHAR(20)  NOT NULL DEFAULT 'PRIMARY',
    container_oid   CHAR(36),
    tenant_oid      CHAR(36),
    creator         VARCHAR(100),
    created_at      TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updater         VARCHAR(100),
    updated_at      TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX IF NOT EXISTS idx_dal_deliverable ON ck_deliverable_artifact_link(deliverable_oid);
CREATE INDEX IF NOT EXISTS idx_dal_entity      ON ck_deliverable_artifact_link(entity_type, entity_oid);
CREATE UNIQUE INDEX IF NOT EXISTS uk_dal_triple
    ON ck_deliverable_artifact_link(deliverable_oid, entity_oid, role);

-- ==================== 通用件阈值 / 标准件入库 配置（一租户一行） ====================
-- 来源：原先由 Java 启动初始化器在代码里建，DDL 原样迁移到本脚本。
-- 注：两张表的"租户唯一"索引也已在脚本中（uk_std_part_inbound_config_tenant 见下方，
--     uk_gen_part_threshold_config_tenant 见文件末尾租户索引段）。
CREATE TABLE IF NOT EXISTS ck_gen_part_threshold_config (
    oid                  CHAR(36)  PRIMARY KEY,
    enabled              BOOLEAN   NOT NULL DEFAULT TRUE,
    min_model_count      INTEGER   NOT NULL DEFAULT 3,
    min_usage_count      INTEGER   NOT NULL DEFAULT 5,
    stat_window_months   INTEGER   NOT NULL DEFAULT 12,
    scope_type_code      VARCHAR(50),
    process_template_oid VARCHAR(64),
    description          VARCHAR(500),
    tenant_oid           CHAR(36)  NOT NULL,
    creator              VARCHAR(100),
    created_at           TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updater              VARCHAR(100),
    updated_at           TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS ck_std_part_inbound_config (
    oid                  CHAR(36)  PRIMARY KEY,
    enabled              BOOLEAN   NOT NULL DEFAULT FALSE,
    process_template_oid VARCHAR(64),
    description          VARCHAR(500),
    tenant_oid           CHAR(36)  NOT NULL,
    creator              VARCHAR(100),
    created_at           TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updater              VARCHAR(100),
    updated_at           TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE UNIQUE INDEX IF NOT EXISTS uk_std_part_inbound_config_tenant
    ON ck_std_part_inbound_config(tenant_oid);

-- ==================== 流程模板版本 ====================
-- 来源：原先由 Java 启动初始化器在代码里建（DDL 已迁入本脚本）；唯一索引 uk_process_template_version 见文件末尾索引段。
CREATE TABLE IF NOT EXISTS ck_process_template_version (
    oid            VARCHAR(64) PRIMARY KEY,
    template_oid   VARCHAR(64) NOT NULL,
    version        INTEGER     NOT NULL,
    dsl_json       TEXT        NOT NULL,
    bpmn_xml       TEXT,
    change_note    VARCHAR(512),
    deployed       BOOLEAN     NOT NULL DEFAULT FALSE,
    deployment_id  VARCHAR(64),
    tenant_oid     VARCHAR(64) NOT NULL,
    creator        VARCHAR(128),
    created_at     TIMESTAMP
);

-- ==================== 目标系统注册表 ====================
-- 来源：原先由 Java 启动初始化器在代码里建（该初始化器已退场），结构以本脚本为准。
CREATE TABLE IF NOT EXISTS ck_target_system (
    oid          VARCHAR(64)  PRIMARY KEY,
    code         VARCHAR(64)  NOT NULL,
    name         VARCHAR(128) NOT NULL,
    base_url     VARCHAR(512) NOT NULL,
    auth_type    VARCHAR(16)  NOT NULL DEFAULT 'NONE',
    username     VARCHAR(128),
    secret       VARCHAR(1024),
    enabled      BOOLEAN      NOT NULL DEFAULT TRUE,
    sort_order   INTEGER      NOT NULL DEFAULT 0,
    description  VARCHAR(512),
    tenant_oid   VARCHAR(64)  NOT NULL,
    creator      VARCHAR(128),
    created_at   TIMESTAMP,
    updater      VARCHAR(128),
    updated_at   TIMESTAMP
);

CREATE UNIQUE INDEX IF NOT EXISTS uk_target_system_code_tenant ON ck_target_system (code, tenant_oid);
CREATE INDEX IF NOT EXISTS idx_target_system_tenant ON ck_target_system (tenant_oid);

-- ==================== 工程数据子版本 / 引用链接 ====================
-- 来源：EngineeringDocumentSchemaInitializer，DDL 原样迁移。
-- 注：ck_eng_doc_ref_link 与 ck_eng_doc_member_link 的索引已收全，见本文件末尾 idx_engml_* / idx_engrl_* 段。
CREATE TABLE IF NOT EXISTS ck_eng_document_iteration (
    oid                              CHAR(36)     PRIMARY KEY,
    master_oid                       CHAR(36)     NOT NULL,
    revision                         VARCHAR(20),
    iteration                        INTEGER,
    display_version                  VARCHAR(30),
    checked_out                      BOOLEAN      NOT NULL DEFAULT FALSE,
    checked_out_by                   VARCHAR(100),
    checked_out_comment              VARCHAR(1000),
    latest                           BOOLEAN      NOT NULL DEFAULT FALSE,
    derived_from_oid                 CHAR(36),
    derived_at                       TIMESTAMP,
    status                           VARCHAR(50),
    lifecycle_template_iteration_oid CHAR(36),
    version_sort                     INTEGER,
    branch_id                        VARCHAR(64),
    delete_mark                      BOOLEAN      NOT NULL DEFAULT FALSE,
    ckfile_oid                       CHAR(36),
    cad_name                         VARCHAR(200),
    cad_type                         VARCHAR(30),
    cad_tool                         VARCHAR(100),
    sheet_size                       VARCHAR(20),
    scale                            VARCHAR(30),
    sheet_number                     VARCHAR(100),
    sheet_count                      INTEGER,
    projection                       VARCHAR(20),
    author                           VARCHAR(100),
    material                         VARCHAR(200),
    weight                           VARCHAR(100),
    tenant_oid                       CHAR(36),
    creator                          VARCHAR(100),
    created_at                       TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updater                          VARCHAR(100),
    updated_at                       TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_engd_iter_master ON ck_eng_document_iteration(master_oid);
CREATE INDEX IF NOT EXISTS idx_engd_iter_latest ON ck_eng_document_iteration(latest);

-- 工程数据 ↔ 工程数据：横向引用（对应 Windchill EPMReferenceLink）
CREATE TABLE IF NOT EXISTS ck_eng_doc_ref_link (
    oid                         CHAR(36)     PRIMARY KEY,
    referenced_by_iteration_oid CHAR(36)     NOT NULL,
    references_master_oid       CHAR(36)     NOT NULL,
    references_type             VARCHAR(30)  NOT NULL DEFAULT 'ENG_DOCUMENT',
    reference_type              VARCHAR(50)  NOT NULL DEFAULT 'DEPENDENCY',
    as_stored_child_name        VARCHAR(200),
    dep_type                    INTEGER      NOT NULL DEFAULT 0,
    required                    BOOLEAN      NOT NULL DEFAULT FALSE,
    unique_link_id              BIGINT,
    tenant_oid                  CHAR(36),
    creator                     VARCHAR(100),
    created_at                  TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updater                     VARCHAR(100),
    updated_at                  TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- ==================== 企业资源库容器 / 资源库分类绑定 ====================
-- 来源：两张表原先均由 Java 启动初始化器在代码里建，DDL 已全部迁入本脚本。
CREATE TABLE IF NOT EXISTS ck_resource_container (
    oid            CHAR(36)     PRIMARY KEY,
    code           VARCHAR(50)  NOT NULL,
    name           VARCHAR(200) NOT NULL,
    description    VARCHAR(1000),
    container_type VARCHAR(30)  NOT NULL DEFAULT 'CORP_RESOURCE',
    thumbnail      VARCHAR(500),
    parent_oid     CHAR(36),
    team_oid       CHAR(36),
    sort_order     INTEGER      NOT NULL DEFAULT 0,
    tenant_oid     CHAR(36),
    delete_mark    BOOLEAN      NOT NULL DEFAULT FALSE,
    creator        VARCHAR(100),
    created_at     TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updater        VARCHAR(100),
    updated_at     TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- 唯一键 (code, tenant_oid)：资源库按租户各一份（2026-10-11 由"全局唯一"改）——
-- 否则第二个租户装包会与平台行撞 (code) 唯一约束（同 ck_unit / ck_type_definition 的教训）
CREATE UNIQUE INDEX IF NOT EXISTS uk_resource_container_code ON ck_resource_container(code, tenant_oid);
CREATE INDEX IF NOT EXISTS idx_resource_container_parent ON ck_resource_container(parent_oid);

CREATE TABLE IF NOT EXISTS ck_library_cls_config (
    oid                     CHAR(36)  PRIMARY KEY,
    tenant_oid              CHAR(36)  NOT NULL,
    resource_code           VARCHAR(50),
    resource_node_oid       CHAR(36),
    root_classification_oid CHAR(36)  NOT NULL,
    created_at              TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at              TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE UNIQUE INDEX IF NOT EXISTS uk_library_cls_config_tenant_code
    ON ck_library_cls_config(tenant_oid, COALESCE(resource_code, ''));

-- ==================== 电子设计项目 / 工程数据 ====================
-- 来源：EcadSchemaInitializer（ck_ecad_project）、EngineeringDocumentSchemaInitializer（ck_eng_*），DDL 原样迁移。
CREATE TABLE IF NOT EXISTS ck_ecad_project (
    oid             CHAR(36)     PRIMARY KEY,
    code            VARCHAR(50)  NOT NULL,
    name            VARCHAR(200) NOT NULL,
    description     VARCHAR(1000),
    container_type  VARCHAR(30)  NOT NULL DEFAULT 'ECAD_PROJECT',
    parent_oid      CHAR(36),
    domain_oid      CHAR(36),
    related_product CHAR(36),
    project_phase   VARCHAR(20)  NOT NULL DEFAULT 'PLAN',
    owner           VARCHAR(100),
    tenant_oid      CHAR(36),
    creator         VARCHAR(100),
    created_at      TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updater         VARCHAR(100),
    updated_at      TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE UNIQUE INDEX IF NOT EXISTS uk_ecad_project_code ON ck_ecad_project(code);
CREATE INDEX IF NOT EXISTS idx_ecad_project_domain ON ck_ecad_project(domain_oid);

-- 工程数据主数据（对应 Windchill EPMDocumentMaster）
CREATE TABLE IF NOT EXISTS ck_eng_document (
    oid                  CHAR(36)     PRIMARY KEY,
    number               VARCHAR(100),
    name                 VARCHAR(200),
    description          VARCHAR(1000),
    container_oid        CHAR(36),
    container_type       VARCHAR(50),
    type_definition_code VARCHAR(100),
    folder_oid           CHAR(36),
    stage_oid            CHAR(36),
    cls_oid              CHAR(36),
    tenant_oid           CHAR(36),
    creator              VARCHAR(100),
    created_at           TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updater              VARCHAR(100),
    updated_at           TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE UNIQUE INDEX IF NOT EXISTS uk_eng_document_number ON ck_eng_document(number);
CREATE INDEX IF NOT EXISTS idx_eng_document_type ON ck_eng_document(type_definition_code);
CREATE INDEX IF NOT EXISTS idx_eng_document_folder ON ck_eng_document(folder_oid);

-- 工程数据 ↔ 零部件（对应 Windchill EPMBuildRule）
-- 挂接粒度：零件迭代（part_iteration_oid）↔ 工程数据主对象（eng_document_oid）+ 可选精确迭代
-- （eng_doc_iteration_oid 为空 = 跟随该工程数据的最新版本，与 ck_doc_part_link 的挂法一致）。
CREATE TABLE IF NOT EXISTS ck_eng_doc_part_link (
    oid                    CHAR(36)     PRIMARY KEY,
    eng_document_oid       CHAR(36)     NOT NULL,
    eng_doc_iteration_oid  CHAR(36),
    part_iteration_oid     CHAR(36)     NOT NULL,
    assoc_type             VARCHAR(30)  NOT NULL DEFAULT 'CONTENT',
    build_structure        BOOLEAN      NOT NULL DEFAULT FALSE,
    build_attribute        BOOLEAN      NOT NULL DEFAULT FALSE,
    build_representation   BOOLEAN      NOT NULL DEFAULT FALSE,
    assoc_source           VARCHAR(20)  NOT NULL DEFAULT 'MANUAL',
    is_primary             BOOLEAN      NOT NULL DEFAULT FALSE,
    sort_order             INTEGER      NOT NULL DEFAULT 0,
    -- 历史列：早期按"零件主数据"建表时使用，现改为迭代级，保留只为兼容旧库（不再写入）
    part_oid               CHAR(36),
    tenant_oid             CHAR(36),
    creator                VARCHAR(100),
    created_at             TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updater                VARCHAR(100),
    updated_at             TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- 兼容已建好的旧库（主数据级 part_oid）：补迭代列并放开旧列的非空约束
ALTER TABLE ck_eng_doc_part_link ADD COLUMN IF NOT EXISTS eng_doc_iteration_oid CHAR(36);
ALTER TABLE ck_eng_doc_part_link ADD COLUMN IF NOT EXISTS part_iteration_oid CHAR(36);
ALTER TABLE ck_eng_doc_part_link ALTER COLUMN part_oid DROP NOT NULL;

CREATE INDEX IF NOT EXISTS idx_engpl_doc ON ck_eng_doc_part_link(eng_document_oid);
CREATE INDEX IF NOT EXISTS idx_engpl_doc_iter ON ck_eng_doc_part_link(eng_doc_iteration_oid);
CREATE INDEX IF NOT EXISTS idx_engpl_part_iter ON ck_eng_doc_part_link(part_iteration_oid);
CREATE INDEX IF NOT EXISTS idx_engpl_assoc ON ck_eng_doc_part_link(assoc_type);
-- 主数据级索引已被迭代级取代（旧库里存在，先清掉再建新的）
DROP INDEX IF EXISTS uk_engpl_doc_part;
DROP INDEX IF EXISTS uk_engpl_part_owner;
DROP INDEX IF EXISTS uk_engpl_part_contrib_image;
DROP INDEX IF EXISTS idx_engpl_part;
-- 同一零件版本对同一份工程数据只允许一条构建规则
CREATE UNIQUE INDEX IF NOT EXISTS uk_engpl_doc_part_iter
    ON ck_eng_doc_part_link(eng_document_oid, part_iteration_oid);
-- 基数约束（2026-10-26 用户口径）：一个零件版本**有且只有一份**工程数据 —— 不是"每种关联类型各一份"，
-- 而是整体只允许一条（要换对象先解除原有关系）。服务层先拦并给可读提示，这里用唯一索引兜并发。
-- 注意：若存量库里已存在同一零件版本的多条关联，本索引会创建失败（启动报错）—— 需先人工清理重复行。
CREATE UNIQUE INDEX IF NOT EXISTS uk_engpl_part_iter
    ON ck_eng_doc_part_link(part_iteration_oid);
-- 下面两个按关联类型的部分唯一索引已被上面的整体唯一索引覆盖，保留只为兼容旧库（不再起约束作用）
CREATE UNIQUE INDEX IF NOT EXISTS uk_engpl_part_iter_owner
    ON ck_eng_doc_part_link(part_iteration_oid) WHERE assoc_type = 'OWNER';
CREATE UNIQUE INDEX IF NOT EXISTS uk_engpl_part_iter_contrib
    ON ck_eng_doc_part_link(part_iteration_oid) WHERE assoc_type = 'CONTRIBUTING_IMAGE';

-- 工程数据 ↔ 工程数据：装配成员 / BOM 结构（对应 Windchill EPMMemberLink）
-- 注：该表的索引已收全（idx_engml_usedby_iter / idx_engml_uses_master / uk_engml_iter_master），见文件末尾索引段。
CREATE TABLE IF NOT EXISTS ck_eng_doc_member_link (
    oid                     CHAR(36)      PRIMARY KEY,
    used_by_iteration_oid   CHAR(36)      NOT NULL,
    uses_master_oid         CHAR(36)      NOT NULL,
    quantity                NUMERIC(18,4) NOT NULL DEFAULT 1,
    placed                  BOOLEAN       NOT NULL DEFAULT FALSE,
    has_transform           BOOLEAN       NOT NULL DEFAULT FALSE,
    transform               TEXT,
    fixed                   BOOLEAN       NOT NULL DEFAULT FALSE,
    substitute              BOOLEAN       NOT NULL DEFAULT FALSE,
    suppressed              BOOLEAN       NOT NULL DEFAULT FALSE,
    annotated               BOOLEAN       NOT NULL DEFAULT FALSE,
    model_item_owner_id     VARCHAR(200),
    model_item_owner_type   VARCHAR(50),
    comp_number             INTEGER       NOT NULL DEFAULT -1,
    comp_rev_number         INTEGER       NOT NULL DEFAULT -1,
    comp_layer_index        INTEGER       NOT NULL DEFAULT -1,
    name                    VARCHAR(200),
    identifier              INTEGER,
    identifier_space_name   VARCHAR(100),
    as_stored_child_name    VARCHAR(200),
    dep_type                INTEGER       NOT NULL DEFAULT 0,
    required                BOOLEAN       NOT NULL DEFAULT FALSE,
    unique_link_id          BIGINT,
    sort_order              INTEGER       NOT NULL DEFAULT 0,
    tenant_oid              CHAR(36),
    creator                 VARCHAR(100),
    created_at              TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updater                 VARCHAR(100),
    updated_at              TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- ==================== 电子域：3D 模型关联 / 设计项目关联 / 设计实例 ====================
-- 同样来自 EcadSchemaInitializer，DDL 原样迁移。
CREATE TABLE IF NOT EXISTS ck_footprint_3d_link (
    oid           CHAR(36) PRIMARY KEY,
    footprint_oid CHAR(36) NOT NULL,
    model3d_oid   CHAR(36) NOT NULL,
    is_default    BOOLEAN  NOT NULL DEFAULT FALSE,
    scale         FLOAT    NOT NULL DEFAULT 1.0,
    offset_x      FLOAT,
    offset_y      FLOAT,
    offset_z      FLOAT,
    rotation_x    FLOAT,
    rotation_y    FLOAT,
    rotation_z    FLOAT,
    tenant_oid    CHAR(36),
    creator       VARCHAR(100),
    created_at    TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updater       VARCHAR(100),
    updated_at    TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_f3l_footprint ON ck_footprint_3d_link(footprint_oid);

CREATE TABLE IF NOT EXISTS ck_ecad_project_design_link (
    oid         CHAR(36)    PRIMARY KEY,
    project_oid CHAR(36)    NOT NULL,
    design_oid  CHAR(36)    NOT NULL,
    design_type VARCHAR(30) NOT NULL,
    sort_order  INTEGER     NOT NULL DEFAULT 0,
    tenant_oid  CHAR(36),
    creator     VARCHAR(100),
    created_at  TIMESTAMP   NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updater     VARCHAR(100),
    updated_at  TIMESTAMP   NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_epdl_project ON ck_ecad_project_design_link(project_oid);
CREATE INDEX IF NOT EXISTS idx_epdl_design ON ck_ecad_project_design_link(design_oid);

CREATE TABLE IF NOT EXISTS ck_design_instance (
    oid           CHAR(36)    PRIMARY KEY,
    design_oid    CHAR(36)    NOT NULL,
    design_type   VARCHAR(30) NOT NULL,
    reference     VARCHAR(50),
    component_oid CHAR(36),
    footprint_oid CHAR(36),
    symbol_oid    CHAR(36),
    quantity      INTEGER     NOT NULL DEFAULT 1,
    tenant_oid    CHAR(36),
    creator       VARCHAR(100),
    created_at    TIMESTAMP   NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updater       VARCHAR(100),
    updated_at    TIMESTAMP   NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_dins_design ON ck_design_instance(design_oid);
CREATE INDEX IF NOT EXISTS idx_dins_component ON ck_design_instance(component_oid);
CREATE INDEX IF NOT EXISTS idx_dins_footprint ON ck_design_instance(footprint_oid);

-- 电子域（域实例，供设计项目引用）——注意与"业务域" ck_business_domain 的分工：
--   ck_business_domain = 类型体系的业务划分（域标签）；ck_ecad_domain = 电子设计域的实例。
CREATE TABLE IF NOT EXISTS ck_ecad_domain (
    oid         CHAR(36)     PRIMARY KEY,
    code        VARCHAR(50)  NOT NULL,
    name        VARCHAR(200) NOT NULL,
    description VARCHAR(1000),
    enabled     BOOLEAN      NOT NULL DEFAULT TRUE,
    tenant_oid  CHAR(36),
    creator     VARCHAR(100),
    created_at  TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updater     VARCHAR(100),
    updated_at  TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE UNIQUE INDEX IF NOT EXISTS uk_ecad_domain_code ON ck_ecad_domain(code);

CREATE TABLE IF NOT EXISTS ck_ecad_footprint_ext (
    oid              CHAR(36)     PRIMARY KEY,
    ipc_name         VARCHAR(200) NOT NULL,
    mount_type       VARCHAR(20)  NOT NULL,
    pad_count        INTEGER      NOT NULL,
    pitch_mm         FLOAT,
    body_size        VARCHAR(100),
    height_mm        FLOAT,
    ipc_compliant    BOOLEAN      NOT NULL DEFAULT FALSE,
    eda_format       VARCHAR(20)  NOT NULL,
    preview_file_oid CHAR(36),
    source_library   VARCHAR(200),
    datasheet_url    VARCHAR(500),
    tenant_oid       CHAR(36),
    creator          VARCHAR(100),
    created_at       TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updater          VARCHAR(100),
    updated_at       TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- ==================== 电子域扩展表 (ECAD) ====================
-- 以下表原先只在 EcadSchemaInitializer 里 CREATE TABLE，现并入本文件（DDL 原样迁移，未做改动）。
-- 说明：域归属由类型体系的 ck_business_domain 表达，本组表只存"域扩展属性"。

CREATE TABLE IF NOT EXISTS ck_ecad_symbol_ext (
    oid              CHAR(36)     PRIMARY KEY,
    symbol_category  VARCHAR(30)  NOT NULL,
    pin_count        INTEGER      NOT NULL,
    pin_mapping      VARCHAR(2000),
    eda_format       VARCHAR(20)  NOT NULL,
    preview_file_oid CHAR(36),
    source_library   VARCHAR(200),
    tenant_oid       CHAR(36),
    creator          VARCHAR(100),
    created_at       TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updater          VARCHAR(100),
    updated_at       TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS ck_ecad_schematic_ext (
    oid             CHAR(36)    PRIMARY KEY,
    sheet_count     INTEGER,
    eda_tool        VARCHAR(100),
    design_phase    VARCHAR(20),
    component_count INTEGER,
    tenant_oid      CHAR(36),
    creator         VARCHAR(100),
    created_at      TIMESTAMP   NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updater         VARCHAR(100),
    updated_at      TIMESTAMP   NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS ck_ecad_pcb_ext (
    oid                CHAR(36)     PRIMARY KEY,
    layer_count        INTEGER      NOT NULL,
    board_thickness_mm FLOAT,
    board_size         VARCHAR(100),
    stackup            VARCHAR(2000),
    impedance_ctrl     VARCHAR(500),
    surface_finish     VARCHAR(20),
    gerber_file_oid    CHAR(36),
    tenant_oid         CHAR(36),
    creator            VARCHAR(100),
    created_at         TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updater            VARCHAR(100),
    updated_at         TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- ==================== 元器件 ↔ 封装 / 图符 关联表 ====================
CREATE TABLE IF NOT EXISTS ck_component_footprint_link (
    oid            CHAR(36)     PRIMARY KEY,
    component_oid  CHAR(36)     NOT NULL,
    footprint_oid  CHAR(36)     NOT NULL,
    is_default     BOOLEAN      NOT NULL DEFAULT FALSE,
    variant_note   VARCHAR(500),
    sort_order     INTEGER      NOT NULL DEFAULT 0,
    tenant_oid     CHAR(36),
    creator        VARCHAR(100),
    created_at     TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updater        VARCHAR(100),
    updated_at     TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_cfl_component ON ck_component_footprint_link(component_oid);
CREATE INDEX IF NOT EXISTS idx_cfl_footprint ON ck_component_footprint_link(footprint_oid);

CREATE TABLE IF NOT EXISTS ck_component_symbol_link (
    oid           CHAR(36)     PRIMARY KEY,
    component_oid CHAR(36)     NOT NULL,
    symbol_oid    CHAR(36)     NOT NULL,
    is_default    BOOLEAN      NOT NULL DEFAULT FALSE,
    variant_note  VARCHAR(500),
    tenant_oid    CHAR(36),
    creator       VARCHAR(100),
    created_at    TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updater       VARCHAR(100),
    updated_at    TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_csl_component ON ck_component_symbol_link(component_oid);
CREATE INDEX IF NOT EXISTS idx_csl_symbol ON ck_component_symbol_link(symbol_oid);

CREATE TABLE IF NOT EXISTS ck_type_definition (
    -- 业务域归属（软引用 ck_business_domain.oid）：域是业务划分、允许跨域；
    -- 与 parent_oid（类型继承）正交，历史上由 parent_oid 兼表域归属，已拆分
    domain_oid   CHAR(36),
    oid            CHAR(36)     PRIMARY KEY,
    -- code 的唯一性**按租户**（不是全局）：类型随包按租户各装一份，
    -- 全局唯一会让第二个租户装包撞键（2026-10-10 由列级 UNIQUE 改为表级 (code, tenant_oid)）
    code           VARCHAR(50)  NOT NULL,
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
    updated_at     TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE (code, tenant_oid)
);

CREATE INDEX IF NOT EXISTS idx_td_type_kind ON ck_type_definition(type_kind);
CREATE INDEX IF NOT EXISTS idx_td_tenant    ON ck_type_definition(tenant_oid);
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
--
-- tenant_oid：行业包安装的行业属性按租户落一份（平台级元数据 tenant_oid=平台租户，各租户共用，
-- 与 ck_iba 的租户口径一致 —— ck_iba 侧本来就是 UNIQUE(code, tenant_oid)）。
-- 于是唯一键从 (entity_name, field_name) 放宽为 (entity_name, field_name, tenant_oid)：
-- 同一实体同一字段允许"平台一份 + 各租户各一份"，租户可覆写平台定义而不互相影响。
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
    tenant_oid       CHAR(36),                         -- 平台租户=平台元数据；其余=该租户的行业属性
    creator          VARCHAR(100),
    created_at       TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updater          VARCHAR(100),
    updated_at       TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE (entity_name, field_name, tenant_oid)
);

CREATE INDEX IF NOT EXISTS idx_ad_entity ON ck_attribute_definition(entity_name);
CREATE INDEX IF NOT EXISTS idx_ad_iba ON ck_attribute_definition(iba_oid);
CREATE INDEX IF NOT EXISTS idx_ad_tenant ON ck_attribute_definition(tenant_oid);

-- ==================== 低代码页面布局定义 ====================
-- 为 TypeDefinition 的每个操作（list/create/update/detail/自定义）
-- 存储可视化设计器编排的页面布局 JSON 配置
-- entity_oid + entity_type + operation_code 联合唯一：每个实体的每个操作保留一份布局定义
CREATE TABLE IF NOT EXISTS ck_type_page_layout (
    oid            CHAR(36)     PRIMARY KEY,
    entity_oid     CHAR(36)     NOT NULL,                 -- 关联 ck_type_definition.oid
    entity_code    VARCHAR(50),                           -- 实体类型编码（如 PRODUCT_LINE）
    operation_code VARCHAR(30)  NOT NULL DEFAULT 'list',  -- 操作编码: list|create|update|detail|用户自定义
    operation_name VARCHAR(50),                           -- 操作显示名称
    layout_json    JSONB        NOT NULL DEFAULT '{}',    -- 布局 JSON 配置
    tenant_oid     CHAR(36),
    creator        VARCHAR(100),
    created_at     TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updater        VARCHAR(100),
    updated_at     TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE (entity_oid, operation_code, tenant_oid)
);



-- ==================== 租户 ====================
-- oid 为全局唯一主键，tenant_id 为租户标识
-- status: PENDING（待审核）→ ACTIVE（已激活）→ SUSPENDED / DISABLED
--
-- onboarding_state（行业初始化）：PENDING（待初始化）→ DONE
--   租户审批通过后，其成员首次登录时若仍为 PENDING：管理员被强制进"选行业 + 选方法论"引导页，
--   普通成员看到"等待管理员完成行业初始化"拦截页；完成后按该租户安装对应的行业包。
--   行业一经选定不可改（要换只能新开租户）—— 见行业包方案。
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
    -- ==== 行业与内嵌方法论（行业包引导） ====
    industry_code      VARCHAR(50),                             -- 选定行业：SMART_HARDWARE / ELECTRONICS_HITECH …
    dev_method_code    VARCHAR(30),                             -- 选定方法论：IPD / IPD_LITE（板级四阶段）…
    industry_pack_code VARCHAR(80),                             -- 实际安装的变体包：smart-hardware-ipd@1.0.0
    onboarding_state   VARCHAR(20)  NOT NULL DEFAULT 'PENDING', -- PENDING（待初始化）→ DONE
    onboarded_at       TIMESTAMP,                               -- 完成引导的时间
    onboarded_by       VARCHAR(100),                            -- 完成引导的人（租户管理员）
    profile_json       TEXT,                                    -- 企业画像答案（驱动推荐与参数建议）
    creator          VARCHAR(100),
    created_at       TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updater          VARCHAR(100),
    updated_at       TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- 初始化平台层租户（所有租户共享的系统配置数据归属）
-- 平台本身不是"客户"，不需要走行业引导 → onboarding_state 显式 DONE
INSERT INTO ck_tenant (oid, tenant_id, name, status, onboarding_state)
VALUES ('00000000-0000-0000-0000-000000000000', 'platform', '平台层（系统配置共享）', 'ACTIVE', 'DONE')
ON CONFLICT (oid) DO NOTHING;

-- ==================== 租户能力（能力开关的"事实账本"） ====================
-- 装行业包时由安装引擎写入：包里 capabilities.json 声明了、且平台【已实现】的能力才落一行。
-- 「平台已实现」的唯一口径是代码里的 PlatformCapabilities（注册表）——
-- 包声明是"主张"，这里是"事实"；包里声明了而平台未实现的，只在安装报告里提示跳过，不落库。
-- 幂等键 = tenant_oid + capability_code（唯一约束）：已存在即跳过，不覆盖（管理员手动关掉的能力
-- 不该被下次装包重新打开）。
CREATE TABLE IF NOT EXISTS ck_tenant_capability (
    oid             CHAR(36)     PRIMARY KEY,
    tenant_oid      CHAR(36)     NOT NULL,
    capability_code VARCHAR(60)  NOT NULL,
    capability_name VARCHAR(100),
    enabled         BOOLEAN      NOT NULL DEFAULT TRUE,
    source_pack     VARCHAR(120),          -- 哪个包声明的：electronics-core@1.0.0
    note            VARCHAR(500),          -- 包内说明原文（如"依赖 change 模块"）
    creator         VARCHAR(100),
    created_at      TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updater         VARCHAR(100),
    updated_at      TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_tenant_capability UNIQUE (tenant_oid, capability_code)
);
CREATE INDEX IF NOT EXISTS idx_tenant_capability_tenant ON ck_tenant_capability (tenant_oid);

-- ==================== 行业包（Industry Pack）机制表 ====================
-- 包清单源自 classpath:industry-packs/<pack>/<version>/pack.json（平台内置：platform-base /
-- electronics-core / smart-hardware-ipd …），启动时自动安装平台层与行业基础包；
-- 行业变体包在租户引导式初始化时按租户安装（tenant_oid=该租户）。
-- 逐资源记账：幂等键 = resource_key，升级 diff 与"示例数据精确回滚"都靠它。
CREATE TABLE IF NOT EXISTS ck_industry_pack (
    oid             CHAR(36)     PRIMARY KEY,
    pack_code       VARCHAR(80)  NOT NULL,                 -- electronics-core / smart-hardware-ipd …
    pack_version    VARCHAR(30)  NOT NULL,                 -- 1.0.0
    base_pack       VARCHAR(100),                          -- 依赖的父包：electronics-core@1.0.0
    engine_range    VARCHAR(50),                           -- 适用平台版本区间：>=0.2.0 <1.0.0
    industry_code   VARCHAR(50),                           -- SMART_HARDWARE / ELECTRONICS_HITECH …
    dev_method_code VARCHAR(30),                           -- IPD / IPD_LITE（板级四阶段）…
    target_scale    VARCHAR(30),                           -- 适用企业规模（引导页展示用）
    install_state   VARCHAR(20)  NOT NULL DEFAULT 'INSTALLED',  -- INSTALLED / PARTIAL / FAILED
    content_hash    VARCHAR(64),                           -- 清单+内容指纹（升级 diff 用）
    parameters_json TEXT,                                  -- 安装时定下的参数终值（租户覆写层，升级不覆盖）
    installed_at    TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    installed_by    VARCHAR(100),
    tenant_oid      CHAR(36),                              -- 平台级包=平台租户；行业包=安装它的租户
    UNIQUE (pack_code, pack_version, tenant_oid)
);

CREATE INDEX IF NOT EXISTS idx_ipack_tenant ON ck_industry_pack(tenant_oid);
CREATE INDEX IF NOT EXISTS idx_ipack_code   ON ck_industry_pack(pack_code);

CREATE TABLE IF NOT EXISTS ck_industry_pack_resource (
    oid                CHAR(36)     PRIMARY KEY,
    pack_oid           CHAR(36)     NOT NULL,              -- 关联 ck_industry_pack.oid
    resource_type      VARCHAR(30)  NOT NULL,              -- SOFTTYPE/CLASSIFICATION/NUMBER_RULE/…/SAMPLE
    resource_key       VARCHAR(200) NOT NULL,              -- 幂等键（业务键）；重复安装据此判 SKIP
    action             VARCHAR(20)  NOT NULL,              -- INSERT / SKIP / UPDATE / FAILED
    target_table       VARCHAR(60),                        -- 落到哪张表（排障与卸载用）
    target_oid         CHAR(36),                           -- 该资源的行 oid
    created_object_oid CHAR(36),                           -- 示例数据：本次创建的业务对象 oid（精确回滚用）
    message            VARCHAR(500),                       -- 失败原因 / 跳过说明
    content_hash       VARCHAR(64),                        -- 单条内容指纹
    tenant_oid         CHAR(36),
    created_at         TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE (pack_oid, resource_type, resource_key)
);

CREATE INDEX IF NOT EXISTS idx_ipackres_pack   ON ck_industry_pack_resource(pack_oid);
CREATE INDEX IF NOT EXISTS idx_ipackres_tenant ON ck_industry_pack_resource(tenant_oid);
CREATE INDEX IF NOT EXISTS idx_ipackres_object ON ck_industry_pack_resource(created_object_oid);

-- 初始化默认租户（已激活）
-- 系统兜底租户（无租户上下文的写入落这里），同样不是"客户" → onboarding_state 显式 DONE
INSERT INTO ck_tenant (oid, tenant_id, name, status, onboarding_state)
VALUES ('00000000-0000-0000-0000-000000000001', 'default', '默认租户', 'ACTIVE', 'DONE')
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
    -- 逻辑删除标记（原先只由初始化器 ALTER 补列）
    delete_mark     BOOLEAN DEFAULT FALSE,
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
    -- 逻辑删除标记（原先只由初始化器 ALTER 补列）
    delete_mark      BOOLEAN DEFAULT FALSE,
    oid              CHAR(36)     PRIMARY KEY,
    code             VARCHAR(50)  NOT NULL UNIQUE,
    name             VARCHAR(100) NOT NULL,
    description      VARCHAR(500),
    thumbnail        VARCHAR(500),
    team_oid         CHAR(36),
    parent_oid       CHAR(36)     NOT NULL,
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
    -- 该阶段可管理的对象类型（逗号分隔；原先只由初始化器 ALTER 补列）
    managed_object_types VARCHAR(500),
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
-- 2026-10-30：stage_oid 去掉 NOT NULL —— 零件管理自 2026-10-22 起就独立于阶段与文件夹，
--   新建零件只挂容器（系列 / 型号），不再携带 stageOid / folderOid；
--   约束不放开时会在「物料清单 → 创建零组件」直接报
--   `null value in column "stage_oid" of relation "ck_part" violates not-null constraint`。
--   列保留（老数据仍有值、索引也还在），只是不再必填。
CREATE TABLE IF NOT EXISTS ck_part (
    oid                   CHAR(36)     PRIMARY KEY,
    name                  VARCHAR(200) NOT NULL,
    number                VARCHAR(100),
    description           VARCHAR(1000),
    type_definition_code  VARCHAR(50),
    container_oid         CHAR(36),
    container_type        VARCHAR(20),
    folder_oid            CHAR(36),
    stage_oid             VARCHAR(50),
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
    -- 主文件 oid / 版本固化的生命周期模板迭代（原先只由初始化器 ALTER 补列，现随建表就位）
    ckfile_oid                       CHAR(36),
    lifecycle_template_iteration_oid CHAR(36),
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
    -- owner_oid 可空：单据保存前上传的附件还没有归属（变更单的「补充材料」就是这样 ——
    -- 表单里先上传拿到 oid，保存时再把附件挂到单据上，见 ChangeRequestServiceImpl#bindAttachment）
    owner_oid          CHAR(36),
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

-- 2026-11-01 存量升级：owner_oid 放开 NOT NULL（原建表是 NOT NULL，导致"先上传、后建单"必 500）
ALTER TABLE ck_attachment ALTER COLUMN owner_oid DROP NOT NULL;

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

-- 最近访问 / 最近操作：WHERE user_oid = ? AND activity_type ... ORDER BY created_at DESC
-- （原先只写在 src/main/resources/sql/ck_user_activity.sql 里，随该脚本归档进本文件）
CREATE INDEX IF NOT EXISTS idx_activity_user_type ON ck_user_activity(user_oid, activity_type, created_at DESC);
  
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
    -- 行业（默认传统行业）/ 可管理的对象类型（原先只由初始化器 ALTER 补列）
    industry             VARCHAR(50)  DEFAULT 'TRADITIONAL',
    managed_object_types VARCHAR(500),
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


-- ==================== 交付件（交付物）模板 ====================
-- 与 ck_stage_template 同型：行业包随包装入"企业一份"（tenant_oid = 安装目标租户），
-- 企业在自己的那份上裁剪（改强制 / 改完成条件 / 改责任人 / 停用）；创建产品系列或型号时
-- 按本表"快照"出实例（实例表 ck_deliverable，P2 落地）。
--
-- 与研发阶段的关系只有一列：stage_key ↔ ck_stage.code；gate 必须等于该阶段的门
-- （门按"本阶段交付件完成率"判定）。不一致时判定永远算不清，故安装与新增时都要守这条不变量。
--
-- 停用（disabled）而非删除：保留行与幂等键（code + tenant_oid），企业停用某条后重装行业包
-- 只会 SKIP —— 既不会被当成缺失又装回来，也不会把企业改过的值冲掉。
--
-- 注：本表不在 TenantStatementInterceptor 的共享名单里 → 按「业务表」处理
--     （SELECT 会被注入 tenant_oid = 当前租户）。因此 Mapper 必须像
--     PostgreSqlStageTemplateMapper 那样<b>显式书写 tenant_oid</b>（命中即跳过注入），
--     否则读不到平台副本、也读不到本租户副本。
CREATE TABLE IF NOT EXISTS ck_deliverable_template (
    oid             CHAR(36)      PRIMARY KEY,
    code            VARCHAR(50)   NOT NULL,      -- 包里稳定的码（如 DLV-SCHEMATIC）
    name            VARCHAR(200)  NOT NULL,
    description     VARCHAR(1000),
    industry        VARCHAR(50),                 -- 与 ck_stage_template.industry 同值
    stage_key       VARCHAR(50)   NOT NULL,      -- → ck_stage.code
    discipline      VARCHAR(30),                 -- 专业（PRODUCT/SYSTEM/HARDWARE/…）
    mandatory       BOOLEAN       NOT NULL DEFAULT FALSE,
    owner_role      VARCHAR(50),                 -- → ck_role.code
    object_type     VARCHAR(50),                 -- 产出物类型（DOCUMENT/PART…）
    bind_mode       VARCHAR(30),                 -- 绑定方式（DOCUMENT…）
    completion_rule TEXT,                        -- {"all":[{type,…}]}，存 JSON 字符串（同 default_folders 口径）
    due_offset_days INTEGER,                     -- 相对阶段结束日（负数=提前）
    gate            VARCHAR(30),                 -- 阶段门（须等于该阶段的门）
    industry_note   VARCHAR(1000),               -- 行业注解（这一条为什么这么定）
    disabled        BOOLEAN       NOT NULL DEFAULT FALSE,   -- 企业裁剪：停用而非删除
    sort_order      INTEGER       NOT NULL DEFAULT 0,
    tenant_oid      CHAR(36),
    creator         VARCHAR(100),
    created_at      TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updater         VARCHAR(100),
    updated_at      TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE (code, tenant_oid)
);

CREATE INDEX IF NOT EXISTS idx_dlt_tenant ON ck_deliverable_template (tenant_oid, industry, sort_order);
CREATE INDEX IF NOT EXISTS idx_dlt_stage  ON ck_deliverable_template (stage_key);


-- ==================== 阶段门（评审门）模板 ====================
-- 与 ck_stage_template / ck_deliverable_template 同型：行业包随包装入"企业一份"，企业可裁剪、可停用；
-- 后续按模板实例化到具体产品系列/型号（门实例表待 P3 落地）。
--
-- 门分两类（文档第 11 节的硬口径：技术评审与决策评审分开建模，合并建模一定四不像）：
--   TECHNICAL_REVIEW（TR）—— 产出问题清单与整改项；
--   DECISION_REVIEW （DCP）—— 产出 Go / Hold / Terminate 与商业留痕。
--
-- 与阶段、交付件的关系：stage_key ↔ ck_stage.code；auto_checks 里的 DELIVERABLE_RATE + stage
-- 按"本阶段交付件完成率"判定 → 故 (code, stage) 与 deliverables 的 (gate, stage) 应一一对应，可交叉校验。
--
-- process_key ↔ 包内 processes/<key>.bpmn20.xml（可落在包链任一包中；安装器逐条校验，不允许悬空引用）。
--
-- 注：本表同样不在 TenantStatementInterceptor 的共享名单里 → Mapper 必须显式书写 tenant_oid。
CREATE TABLE IF NOT EXISTS ck_gate_template (
    oid                   CHAR(36)      PRIMARY KEY,
    code                  VARCHAR(50)   NOT NULL,      -- TR1 / CHARTER / PDCP / ADCP / LDCP …
    name                  VARCHAR(200)  NOT NULL,
    description           VARCHAR(1000),
    industry              VARCHAR(50),                 -- 与 ck_stage_template.industry 同值；空=通用
    stage_key             VARCHAR(50)   NOT NULL,      -- → ck_stage.code（该门属于哪个阶段）
    type                  VARCHAR(30)   NOT NULL,      -- TECHNICAL_REVIEW / DECISION_REVIEW
    process_key           VARCHAR(100),                -- → processes/<key>.bpmn20.xml（无流程则 NULL）
    decision_options      TEXT,                        -- JSON 数组：GO / GO_WITH_CONDITION / RECYCLE / HOLD / TERMINATE
    auto_checks           TEXT,                        -- JSON 数组：DELIVERABLE_RATE / RULE_VIOLATION / ISSUE_OPEN / COST_ACHIEVE / CERT_COMPLETE / SUPPLY_CONFIRMED
    required_participants TEXT,                        -- JSON 数组：角色码（→ ck_role.code）
    record_fields         TEXT,                        -- JSON 数组：留痕字段（decision / decisionBasis / riskList …）
    evidence_required     BOOLEAN       NOT NULL DEFAULT TRUE,
    industry_note         VARCHAR(1000),
    disabled              BOOLEAN       NOT NULL DEFAULT FALSE,
    sort_order            INTEGER       NOT NULL DEFAULT 0,
    tenant_oid            CHAR(36),
    creator               VARCHAR(100),
    created_at            TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updater               VARCHAR(100),
    updated_at            TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE (code, tenant_oid)
);

CREATE INDEX IF NOT EXISTS idx_gt_tenant ON ck_gate_template (tenant_oid, industry, sort_order);
CREATE INDEX IF NOT EXISTS idx_gt_stage  ON ck_gate_template (stage_key);


-- ==================== 评审模板 ====================
-- 与阶段/交付件/门模板同型：随包装入"企业一份"，企业可裁剪、可停用；后续按模板实例化出评审单。
--
-- 一份评审模板 = 归属阶段（stage_key，2026-10-29 起技术评审直接挂阶段）+ 逐条检查项
-- （check_items，每条带 BLOCK/WARN 级别）+ 输出（output_types：问题清单 / 结论）
-- + 结论选项（verdict_options：PASS / PASS_WITH_CONDITION / FAIL）。
--
-- 两处引用：stage_key ↔ ck_stage.code；process_key ↔ processes/<key>.bpmn20.xml。
-- 兼容：老库里的评审是"挂在技术门下"的（gate 非空、stage_key 为空），取阶段时按
-- stage_key → gate→门阶段 的顺序回落，所以这两列都可空、同一行只会有一列有值。
-- 反向还有一处：交付件的 completion_rule 里 REVIEW_PASSED.reviewTemplate 引用本表 code
-- （交付件的那条"完成条件"就是"这份评审通过"）。
--
-- 注：本表同样不在 TenantStatementInterceptor 的共享名单里 → Mapper 必须显式书写 tenant_oid。
CREATE TABLE IF NOT EXISTS ck_review_template (
    oid             CHAR(36)      PRIMARY KEY,
    code            VARCHAR(50)   NOT NULL,      -- 如 TR4_HW_SCHEMATIC
    name            VARCHAR(200)  NOT NULL,
    description     VARCHAR(1000),
    industry        VARCHAR(50),                 -- 与 ck_stage_template.industry 同值；空=通用
    stage_key       VARCHAR(50),                 -- → ck_stage.code（技术评审直接挂阶段，2026-10-29 起）
    gate            VARCHAR(50),                 -- → ck_gate_template.code（老数据/兼容；技术门时代写入）
    type            VARCHAR(30)   NOT NULL,      -- TECHNICAL（技术评审）/ DECISION（决策类评审）
    process_key     VARCHAR(100),                -- → processes/<key>.bpmn20.xml（无流程则 NULL）
    check_items     TEXT,                        -- JSON 数组：[{"seq":1,"text":"…","severity":"BLOCK"}]
    output_types    TEXT,                        -- JSON 数组：ISSUE_LIST / VERDICT
    verdict_options TEXT,                        -- JSON 数组：PASS / PASS_WITH_CONDITION / FAIL
    industry_note   VARCHAR(1000),
    disabled        BOOLEAN       NOT NULL DEFAULT FALSE,
    sort_order      INTEGER       NOT NULL DEFAULT 0,
    tenant_oid      CHAR(36),
    creator         VARCHAR(100),
    created_at      TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updater         VARCHAR(100),
    updated_at      TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE (code, tenant_oid)
);

CREATE INDEX IF NOT EXISTS idx_rt_tenant ON ck_review_template (tenant_oid, industry, sort_order);
CREATE INDEX IF NOT EXISTS idx_rt_gate   ON ck_review_template (gate);
CREATE INDEX IF NOT EXISTS idx_rt_stage  ON ck_review_template (stage_key);


-- ==================== 检查规则模板 ====================
-- 与阶段/交付件/门/评审模板同型：随包装入"企业一份"，企业可裁剪、可停用。
-- 声明式 JSON，不是脚本：一条规则 = 时机（trigger_type）+ 对象（scope）+ 条件（condition）
-- + 严重度（severity）+ 提示（message）；可选：分类、所属阶段/门、企业画像联动（default_override）、
-- 修复动作（fix_action，如"跳到某份交付件去补"）。
--
-- 四组枚举（与包内 note 同口径，安装器逐条校验，越界即如实记 FAILED）：
--   trigger_type   ∈ STAGE_ENTRY / PART_SAVE / BOM_SAVE / GATE_CHECK / RELEASE
--   scope          ∈ PRODUCT_MODEL / PART / BOM
--   condition.type ∈ FIELD_CHECK / FIELD_EMPTY / ISSUE_OPEN / OBJECT_MISSING
--   severity       ∈ BLOCK / WARN
--
-- 与其它模板的关系：stage_key ↔ ck_stage.code；gate ↔ ck_gate_template.code；
-- fix_action.code 指向交付件（ck_deliverable_template.code）—— 三处引用安装器都会校验，
-- 引用对象尚未就绪时记 SKIP + 原因（依赖顺序问题，重装即可补齐）。
--
-- 注：本表同样不在 TenantStatementInterceptor 的共享名单里 → Mapper 必须显式书写 tenant_oid。
-- 注：列名 trigger_type 而非 trigger —— 后者是 SQL 保留字。
CREATE TABLE IF NOT EXISTS ck_rule_template (
    oid              CHAR(36)      PRIMARY KEY,
    code             VARCHAR(50)   NOT NULL,     -- R-SH-01 …
    name             VARCHAR(200)  NOT NULL,
    description      VARCHAR(1000),
    industry         VARCHAR(50),                -- 与 ck_stage_template.industry 同值；空=通用（基础包共用规则即空）
    trigger_type     VARCHAR(30)   NOT NULL,     -- 检查时机
    scope            VARCHAR(30)   NOT NULL,     -- 检查对象
    category         VARCHAR(50),                -- 可选分类，如 battery
    stage_key        VARCHAR(50),                -- 可选 → ck_stage.code
    gate             VARCHAR(50),                -- 可选 → ck_gate_template.code
    condition        TEXT,                       -- JSON：{type,field,operator,value} / {type,field} / {type,issueType,minCount} / {type,objectType,stage}
    severity         VARCHAR(20)   NOT NULL,     -- BLOCK / WARN
    message          VARCHAR(500),               -- 提示语（含 {model.code} / {count} 等占位符）
    default_override TEXT,                       -- JSON：{severity,when} 企业画像联动
    fix_action       TEXT,                       -- JSON：{type,target,code} 一键修复入口
    industry_note    VARCHAR(1000),
    disabled         BOOLEAN       NOT NULL DEFAULT FALSE,
    sort_order       INTEGER       NOT NULL DEFAULT 0,
    tenant_oid       CHAR(36),
    creator          VARCHAR(100),
    created_at       TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updater          VARCHAR(100),
    updated_at       TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE (code, tenant_oid)
);

CREATE INDEX IF NOT EXISTS idx_rulet_tenant ON ck_rule_template (tenant_oid, industry, sort_order);
CREATE INDEX IF NOT EXISTS idx_rulet_trigger ON ck_rule_template (trigger_type, scope);


-- ==================== 问题类型模板 ====================
-- 与前面五类同型：随包装入"企业一份"，企业可裁剪、可停用。
--
-- 问题类型必须是独立数据（文档第 13 节的硬口径）："问题闭环率""超期问题数"是健康度报告里
-- 最有说服力的指标，而它们只有在问题被结构化之后才算得出来 —— 塞进流程表单里就废了。
--
-- 一条 = 编码 + 名称 + 默认严重度（BLOCK / WARN，与检查规则、阶段门同一口径）
-- + 是否必须做根因分析（require_root_cause）+ 是否必须复测（require_retest）+ 行业注解。
--
-- 与其它模板的反向引用：检查规则的 condition.issueType（如 R-SH-03 只认 TEST_FAIL）、
-- 门的 ISSUE_OPEN 自动检查都可能按本表编码统计 —— 故本表是"问题被结构化"的地基。
--
-- 注：本表同样不在 TenantStatementInterceptor 的共享名单里 → Mapper 必须显式书写 tenant_oid。
CREATE TABLE IF NOT EXISTS ck_issue_type_template (
    oid                CHAR(36)      PRIMARY KEY,
    code               VARCHAR(50)   NOT NULL,     -- DESIGN_DEFECT / TEST_FAIL …
    name               VARCHAR(200)  NOT NULL,
    description        VARCHAR(1000),
    industry           VARCHAR(50),                -- 与 ck_stage_template.industry 同值；空=通用
    default_severity   VARCHAR(20)   NOT NULL,     -- BLOCK / WARN
    require_root_cause BOOLEAN       NOT NULL DEFAULT FALSE,
    require_retest     BOOLEAN       NOT NULL DEFAULT FALSE,
    industry_note      VARCHAR(1000),              -- 行业注解（包内字段名是 note，安装器映射过来）
    disabled           BOOLEAN       NOT NULL DEFAULT FALSE,
    sort_order         INTEGER       NOT NULL DEFAULT 0,
    tenant_oid         CHAR(36),
    creator            VARCHAR(100),
    created_at         TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updater            VARCHAR(100),
    updated_at         TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE (code, tenant_oid)
);

CREATE INDEX IF NOT EXISTS idx_itt_tenant ON ck_issue_type_template (tenant_oid, industry, sort_order);


-- ==================== 指标（驾驶舱）模板 ====================
-- 与前面六类同型（随包装入"企业一份"、企业可裁剪、可停用），但有一处本质不同：
-- 指标不是"清单式内容"，而是**计算口径 + 数据就绪度** —— 它回答"这个数能不能算"，而不只是"有没有"。
--
-- 分层：{@code businessLayer}（研发总监看：成本 / 器件 / 问题）与 {@code executiveLayer}
-- （老板看：上市 / 复用 / 质量成本）—— 与包内 metrics.json 的两个数组一一对应。
--
-- level（A/B/C **数据就绪度**）是硬约束，文档第 16 节原文：
--   "C 级指标第一版显示『暂未评估』，禁止编造数值 —— 一个编出来的数字被客户当场问穿，
--    整个健康度报告的可信度就归零了。"
-- 故 level 在这里是一等字段：它是"这个数能不能显示"的开关，不是装饰。
--
-- source = 取数依据（BomCostCalculator / missingCostLines / PartAlternate / qualificationStatus …）。
-- 就绪度与来源应当对得上：A/B 级的来源是平台或包内已落地之物，C 级的来源尚未落地
-- （如 ReviewIssue 需评审引擎、Project 实体尚不存在）—— 安装时与验收时都按这条互相校验。
--
-- 注：本表同样不在 TenantStatementInterceptor 的共享名单里 → Mapper 必须显式书写 tenant_oid。
CREATE TABLE IF NOT EXISTS ck_metric_template (
    oid         CHAR(36)      PRIMARY KEY,
    code        VARCHAR(50)   NOT NULL,     -- BOM_TOTAL_COST / ISSUE_CLOSE_RATE …
    name        VARCHAR(200)  NOT NULL,
    description VARCHAR(1000),
    industry    VARCHAR(50),                -- 与 ck_stage_template.industry 同值；空=通用（基础包共用指标即空）
    layer       VARCHAR(30)   NOT NULL,     -- businessLayer / executiveLayer
    level       VARCHAR(2)    NOT NULL,     -- A / B / C —— 数据就绪度
    source      VARCHAR(150),               -- 取数依据
    industry_note VARCHAR(1000),            -- 行业注解（包内字段名是 note，安装器映射过来）
    disabled    BOOLEAN       NOT NULL DEFAULT FALSE,
    sort_order  INTEGER       NOT NULL DEFAULT 0,
    tenant_oid  CHAR(36),
    creator     VARCHAR(100),
    created_at  TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updater     VARCHAR(100),
    updated_at  TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE (code, tenant_oid)
);

CREATE INDEX IF NOT EXISTS idx_mt_tenant ON ck_metric_template (tenant_oid, industry, sort_order);
CREATE INDEX IF NOT EXISTS idx_mt_layer  ON ck_metric_template (layer, level);


-- ==================== 驾驶舱（布局）模板 ====================
-- 与前七类同型（随包装入"企业一份"、企业可裁剪、可停用），但形状最特殊：
-- 包内 dashboards.json 是 `scopes[]` + 两个<b>包级键</b>：
--   primaryScopeType —— 主视角（产品经理/研发日常工作的那个面）→ 落成命中那条的 primary_scope 标记
--   generatePolicy   —— { 视角 → 生成节奏 } 映射 → 摊平到各行的 generate_policy
--
-- code 取包内 scopes[].type（ENTERPRISE / PRODUCT_LINE / PRODUCT_MODEL）—— 视角类型就是它的稳定身份；
-- 若将来某包要定义同一视角的多个布局，再让 code 与 scope_type 分开（现在不为假想需求加列）。
--
-- widgets = 分组件码的 JSON 数组（如 ["HEALTH_SCORE_TREND","COST_ACHIEVE",…]）。
-- 其中一部分分组件码<b>就是指标码</b>（COST_ACHIEVE 在 metrics.json 里有）→ 可与 ck_metric_template 对账；
-- 其余（MODEL_RANKING / STAGE_READINESS / DELIVERABLE_MATRIX…）目前<b>没有定义表</b>，
-- 它们是"渲染件"而非数据项 —— 故安装器只校验非空，不假装能校验其存在性。
--
-- 注：本表同样不在 TenantStatementInterceptor 的共享名单里 → Mapper 必须显式书写 tenant_oid。
CREATE TABLE IF NOT EXISTS ck_dashboard_template (
    oid             CHAR(36)      PRIMARY KEY,
    code            VARCHAR(50)   NOT NULL,     -- 视角类型：ENTERPRISE / PRODUCT_LINE / PRODUCT_MODEL
    name            VARCHAR(200)  NOT NULL,
    description     VARCHAR(1000),
    industry        VARCHAR(50),                -- 与 ck_stage_template.industry 同值；空=通用
    audience        VARCHAR(100),               -- 受众（GM / GM+RDD / RD/PM）
    page_count      INTEGER,                    -- 页数（可选）
    widgets         TEXT,                       -- JSON 数组：分组件码
    generate_policy VARCHAR(50),                -- 生成节奏（摊平自包级 generatePolicy 映射）
    primary_scope   BOOLEAN       NOT NULL DEFAULT FALSE,  -- 是否主视角（= 包级 primaryScopeType）
    disabled        BOOLEAN       NOT NULL DEFAULT FALSE,
    sort_order      INTEGER       NOT NULL DEFAULT 0,
    tenant_oid      CHAR(36),
    creator         VARCHAR(100),
    created_at      TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updater         VARCHAR(100),
    updated_at      TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE (code, tenant_oid)
);

CREATE INDEX IF NOT EXISTS idx_dbt_tenant ON ck_dashboard_template (tenant_oid, industry, sort_order);


-- ==================== MPN 管理：制造商 / 供应商主数据 ====================
-- 参考 Windchill 的 Manufacturer（制造商）与 Vendor/Supplier（供应商）对象：两者都是**主数据**，
-- 被 AML/AVL 引用；企业先在主数据里登记，再到具体内部件上挂"认可料号"与"认可货源"。
--
-- 为什么不塞进 ck_part 的 IBA？—— 行业包（electronics-core/attributes.json 的 SUPPLIER_INFO 组）
-- 早就声明了 manufacturer / mpn / supplier / supplierCount / leadTime / moq / spq 这些属性，
-- 但它们是"一个件的单值说明"，装不下"一个件对多个制造商料号、每个料号对多个供货渠道"的**一对多**结构。
-- 更关键：supplierCount 的声明写着 derivedBy = count(distinct supplierRefs)，而 supplierRefs 一直没有表 ——
-- 本组表就是把它补上（见 ck_part_avl 注释）。
--
-- 注：这三张表都不在 TenantStatementInterceptor 的共享名单里 → 按「业务表」处理，Mapper 必须显式书写 tenant_oid。
CREATE TABLE IF NOT EXISTS ck_manufacturer (
    oid             CHAR(36)      PRIMARY KEY,
    code            VARCHAR(50)   NOT NULL,     -- 制造商代码（企业自定，如 TI / MURATA）
    name            VARCHAR(200)  NOT NULL,     -- 制造商名称（如 德州仪器）
    description     VARCHAR(1000),              -- 描述（WithoutVersionEntity 的基础字段，实体继承自基类）
    english_name    VARCHAR(200),               -- 英文名，与原厂资料一致（如 Texas Instruments）
    website         VARCHAR(500),
    country         VARCHAR(100),               -- 原产地 / 注册地（供应风险判断的常见维度）
    note            VARCHAR(1000),
    disabled        BOOLEAN       NOT NULL DEFAULT FALSE,
    sort_order      INTEGER       NOT NULL DEFAULT 0,
    tenant_oid      CHAR(36),
    creator         VARCHAR(100),
    created_at      TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updater         VARCHAR(100),
    updated_at      TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE (code, tenant_oid)
);

CREATE INDEX IF NOT EXISTS idx_mfr_tenant ON ck_manufacturer (tenant_oid, disabled, sort_order);

-- 供应商（Vendor / Supplier）：AVL 的引用目标。与制造商是**两个对象** —— 一家供应商会代理多家制造商的料，
-- 同一制造商料也会有多家供应商供货，这正是"多货源"与"单一来源风险"的判定基础。
CREATE TABLE IF NOT EXISTS ck_supplier (
    oid             CHAR(36)      PRIMARY KEY,
    code            VARCHAR(50)   NOT NULL,     -- 供应商代码（企业自定）
    name            VARCHAR(200)  NOT NULL,     -- 供应商简称
    description     VARCHAR(1000),              -- 描述（WithoutVersionEntity 的基础字段，实体继承自基类）
    full_name       VARCHAR(300),               -- 供应商全称（合同主体）
    contact         VARCHAR(100),               -- 联系人
    phone           VARCHAR(50),
    email           VARCHAR(200),
    website         VARCHAR(500),
    note            VARCHAR(1000),
    disabled        BOOLEAN       NOT NULL DEFAULT FALSE,
    sort_order      INTEGER       NOT NULL DEFAULT 0,
    tenant_oid      CHAR(36),
    creator         VARCHAR(100),
    created_at      TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updater         VARCHAR(100),
    updated_at      TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE (code, tenant_oid)
);

CREATE INDEX IF NOT EXISTS idx_supplier_tenant ON ck_supplier (tenant_oid, disabled, sort_order);


-- ==================== MPN 管理：AML（认可制造商清单）====================
-- 参考 Windchill 的 ManufacturerPart + ApprovedManufacturerList：一个内部件（ck_part）可以认可
-- **多个制造商料号**，每个料号由「制造商 + 制造商料号(mpn)」唯一确定 —— 这是行业里"一个内部件号
-- 对应多家原厂替代料"的标准表述。
--
-- approved_status：Windchill 口径的三态 —— PREFERRED（首选）/ APPROVED（认可）/ DISALLOWED（禁用）。
--   DISALLOWED 不作为"删除"：保留历史与原因（note），供采购排除与追溯。
-- lifecycle_state：原厂生命周期（ACTIVE / NRND / EOL…）—— 与 approved_status 正交：
--   一个"认可"的料可能已经 NRND，采购需要看到这层信息。
-- effectivity_json：生效性配置 JSONB 预留（与 ck_part_alternate_link 同口径）。
CREATE TABLE IF NOT EXISTS ck_part_aml (
    oid                CHAR(36)      PRIMARY KEY,
    code               VARCHAR(50),              -- 业务编码（WithoutVersionEntity 的基础字段）
    name               VARCHAR(200),             -- 名称（同上，实体继承自基类，故结构上必须留列）
    description        VARCHAR(1000),            -- 描述（同上）
    part_oid           CHAR(36)      NOT NULL,   -- 内部件（ck_part.oid）
    manufacturer_oid   CHAR(36)      NOT NULL,   -- 制造商（ck_manufacturer.oid）
    mpn                VARCHAR(200)  NOT NULL,   -- 制造商料号 Manufacturer Part Number
    mpn_desc           VARCHAR(500),             -- 原厂描述（与内部件 description 区分）
    approved_status    VARCHAR(20)   NOT NULL DEFAULT 'APPROVED',
    lifecycle_state    VARCHAR(50),              -- ACTIVE / NRND / EOL …
    effectivity_json   JSONB,
    note               VARCHAR(1000),
    sort_order         INTEGER       NOT NULL DEFAULT 0,
    tenant_oid         CHAR(36),
    creator            VARCHAR(100),
    created_at         TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updater            VARCHAR(100),
    updated_at         TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_part_aml_part         FOREIGN KEY (part_oid) REFERENCES ck_part(oid) ON DELETE CASCADE,
    CONSTRAINT fk_part_aml_manufacturer FOREIGN KEY (manufacturer_oid) REFERENCES ck_manufacturer(oid)
);

-- 幂等键：同一内部件 + 同一制造商 + 同一料号只允许一行（part_oid 已按租户隔离，故不必再带 tenant_oid）
CREATE UNIQUE INDEX IF NOT EXISTS uk_part_aml_mpn ON ck_part_aml(part_oid, manufacturer_oid, mpn);
CREATE INDEX IF NOT EXISTS idx_part_aml_part ON ck_part_aml(part_oid);
CREATE INDEX IF NOT EXISTS idx_part_aml_mfr  ON ck_part_aml(manufacturer_oid);
CREATE INDEX IF NOT EXISTS idx_part_aml_tenant ON ck_part_aml(tenant_oid);


-- ==================== MPN 管理：AVL（认可供应商清单）====================
-- 参考 Windchill 的 ApprovedVendorList：**挂在 AML 行上**（不是挂在内部件上）——
-- 因为"从哪家买"是某个制造商料号的性质，换料号就换渠道。这是 AML/AVL 分层的关键，不能压平。
--
-- ★ 补上行业包声明里那个空洞：
--   electronics-core/attributes.json 写着 supplierCount.derivedBy = count(distinct supplierRefs)，
--   而 supplierRefs 一直没有落地的表 —— 本表就是它。于是：
--     supplierCount = count(distinct ck_part_avl.supplier_oid) where aml_oid in (该件的 AML 行)
--   随之 smart-hardware-ipd/rules.json 的 R-SH-11「单一来源器件风险提示」（field: supplierCount）
--   终于有数据可算：某内部件的全部 AML 行合计只剩 1 家供应商时即命中。
--
-- preferred：首选货源（Windchill 的 preferred vendor）。允许多家 preferred（现实里常有双源），
--   故不做唯一约束 —— 由使用方按业务判断，不在结构上硬拦。
-- price/currency/lead_time_days/moq/spq：与 IBA 的 leadTime / moq / spq 同名对应，落地后属性可改为从本表派生。
CREATE TABLE IF NOT EXISTS ck_part_avl (
    oid                 CHAR(36)      PRIMARY KEY,
    code                VARCHAR(50),             -- 业务编码（WithoutVersionEntity 的基础字段）
    name                VARCHAR(200),            -- 名称（同上，实体继承自基类，故结构上必须留列）
    description         VARCHAR(1000),           -- 描述（同上）
    aml_oid             CHAR(36)      NOT NULL,  -- 所属 AML 行（ck_part_aml.oid）
    supplier_oid        CHAR(36)      NOT NULL,  -- 供应商（ck_supplier.oid）
    supplier_part_no    VARCHAR(200),            -- 供应商侧料号（对账与下单用）
    preferred           BOOLEAN       NOT NULL DEFAULT FALSE,
    price               NUMERIC(18,4),           -- 参考单价
    currency            VARCHAR(10),             -- 币种（CNY / USD …）
    lead_time_days      INTEGER,                 -- 交期（天）
    moq                 INTEGER,                 -- 最小起订量
    spq                 INTEGER,                 -- 最小包装量
    effectivity_json    JSONB,
    note                VARCHAR(1000),
    sort_order          INTEGER       NOT NULL DEFAULT 0,
    tenant_oid          CHAR(36),
    creator             VARCHAR(100),
    created_at          TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updater             VARCHAR(100),
    updated_at          TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_part_avl_aml      FOREIGN KEY (aml_oid) REFERENCES ck_part_aml(oid) ON DELETE CASCADE,
    CONSTRAINT fk_part_avl_supplier FOREIGN KEY (supplier_oid) REFERENCES ck_supplier(oid)
);

CREATE UNIQUE INDEX IF NOT EXISTS uk_part_avl_pair ON ck_part_avl(aml_oid, supplier_oid);
CREATE INDEX IF NOT EXISTS idx_part_avl_aml      ON ck_part_avl(aml_oid);
CREATE INDEX IF NOT EXISTS idx_part_avl_supplier ON ck_part_avl(supplier_oid);
CREATE INDEX IF NOT EXISTS idx_part_avl_tenant   ON ck_part_avl(tenant_oid);


-- ==================== 计量单位 ====================
-- 单位体系核心表，每个单位归属于一个量纲类型（quantity_type）
-- 同量纲内通过 factor + offset 换算到基准单位（base_unit_name）
-- 换算公式：基准值 = 当前值 × factor + offset
--
-- 租户归属（2026-10-10 由"全租户共享"改为"按租户隔离"）：
-- 单位随 platform-base 包按租户各装一份（见该包 pack.json 的 UNIT 声明 reusable:false），
-- 租户可在「单位管理」页自建 / 改名 / 删除自己那份，互不可见。
-- 唯一键因此是 (name, tenant_oid) 而非 name —— 否则第二个租户装包会撞唯一约束。
-- 平台租户那份（tenant_oid = 全 0）是母版。
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
    tenant_oid      CHAR(36),
    creator         VARCHAR(100),
    created_at      TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updater         VARCHAR(100),
    updated_at      TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE (name, tenant_oid)
);

CREATE INDEX IF NOT EXISTS idx_unit_qtype  ON ck_unit(quantity_type);
CREATE INDEX IF NOT EXISTS idx_unit_base   ON ck_unit(base_unit_name);
CREATE INDEX IF NOT EXISTS idx_unit_tenant ON ck_unit(tenant_oid);

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
-- 用户手动创建、可命名、可对比的 BOM 快照：把某一时刻父件迭代的顶层 BOM 行（子件清单）定格。
-- snapshot_json 形如 {"items":[{lineNumber,childPartOid,number,name,version,quantity,unit,unitCost}]}。
--
-- 2026-10-30 语义调整：本表原设想是「PUBLISHED 迭代预计算缓存」（一个迭代一份，按 iteration_oid 唯一）。
-- 但那样存不了「同一迭代不同时刻」的两份（检出状态下 BOM 行仍会增删改），也无法命名与对比，于是：
--   ① 一个迭代允许多份快照（唯一索引降级为普通索引）；
--   ② 增加 part_oid / name / remark / item_count / version_label。
-- 快照是只读记录：建好之后 BOM 再改也不会回写它 —— 这正是「对比」能成立的前提。
CREATE TABLE IF NOT EXISTS ck_bom_snapshot (
    oid           CHAR(36)     PRIMARY KEY,
    iteration_oid CHAR(36)     NOT NULL,
    part_oid      CHAR(36),
    name          VARCHAR(100),
    remark        VARCHAR(500),
    version_label VARCHAR(50),
    snapshot_json JSONB        NOT NULL DEFAULT '{}'::jsonb,
    item_count    INTEGER,
    node_count    INTEGER,
    max_depth     INTEGER,
    tenant_oid    CHAR(36),
    creator       VARCHAR(100),
    created_at    TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updater       VARCHAR(100),
    updated_at    TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_bom_snapshot_iteration FOREIGN KEY (iteration_oid) REFERENCES ck_part_iteration(oid) ON DELETE CASCADE
);

-- 存量库升级（schema.sql 同时充当升级脚本，重跑无副作用）
ALTER TABLE ck_bom_snapshot ADD COLUMN IF NOT EXISTS part_oid      CHAR(36);
ALTER TABLE ck_bom_snapshot ADD COLUMN IF NOT EXISTS name          VARCHAR(100);
ALTER TABLE ck_bom_snapshot ADD COLUMN IF NOT EXISTS remark        VARCHAR(500);
ALTER TABLE ck_bom_snapshot ADD COLUMN IF NOT EXISTS version_label VARCHAR(50);
ALTER TABLE ck_bom_snapshot ADD COLUMN IF NOT EXISTS item_count    INTEGER;
-- 一个迭代允许多份快照：原唯一索引降级为普通索引（DROP 幂等，跑过即空操作）
DROP INDEX IF EXISTS uk_bom_snapshot_iteration;
CREATE INDEX IF NOT EXISTS idx_bom_snapshot_iteration ON ck_bom_snapshot(iteration_oid);
CREATE INDEX IF NOT EXISTS idx_bom_snapshot_part      ON ck_bom_snapshot(part_oid);
CREATE INDEX IF NOT EXISTS idx_bom_snapshot_tenant    ON ck_bom_snapshot(tenant_oid);

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
--   · 内置模板（设置流程参与者 / 审批意见 / 会签）声明在 platform-base 包的 forms.json：
--     由 IndustryPackBootstrap 启动时装到平台租户，租户引导装包时各得一份
--   · 表**按租户隔离**（2026-10-10 由 PLATFORM_SHARED 改来）：查询注入 tenant_oid = 当前租户
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

-- ============================================================================
-- 问题（持续改进 —— 跟踪与闭环）
--   · 一条 = 一个问题单：提出 →（处理）→（验证）→ 关闭，全程留痕在 ck_issue_activity
--   · 状态机固定五态（见 cn.ck.plm.improve.flow.IssueFlow）：
--       OPEN 待处理 → IN_PROGRESS 处理中 → VERIFYING 待验证 → CLOSED 已关闭
--       （三态可 CANCEL 到 CANCELLED 已取消；CLOSED 可 REOPEN 回 OPEN）
--   · 与 ck_issue_type_template（随包装入的问题类型）配合：类型给出默认严重度、
--     以及"是否必须根因分析 / 必须复测"的约束（服务层校验，不在本表冗余）
--   · 本表不在 TenantStatementInterceptor 的共享名单里 → Mapper 必须显式书写 tenant_oid
-- ============================================================================
CREATE TABLE IF NOT EXISTS ck_issue (
    oid           CHAR(36)      PRIMARY KEY,
    code          VARCHAR(50)   NOT NULL,               -- ISS-0001（租户内递增，服务端生成）
    name          VARCHAR(300)  NOT NULL,               -- 标题
    description   VARCHAR(4000),                        -- 问题描述
    issue_type    VARCHAR(50),                          -- → ck_issue_type_template.code；空=未分类
    severity      VARCHAR(20)   NOT NULL DEFAULT 'WARN',-- BLOCK 阻塞 / WARN 警告（与检查规则、阶段门同一口径）
    source        VARCHAR(30),                          -- AUDIT 内部审核 / CUSTOMER 客户反馈 / PRODUCTION 生产现场 / TEST 测试发现 / OTHER 其他
    status        VARCHAR(20)   NOT NULL DEFAULT 'OPEN',-- OPEN / IN_PROGRESS / VERIFYING / CLOSED / CANCELLED
    owner         VARCHAR(100),                         -- 责任人（用户名；显示名由前端按用户表映射）
    reporter      VARCHAR(100),                         -- 提出人（用户名，创建时自动 = 当前用户）
    due_date      DATE,                                 -- 计划完成日期
    product_oid   CHAR(36),                             -- 关联产品系列/型号 oid（IPD 上下文；服务端必填，列可空以便存量库平滑升级）
    product_type  VARCHAR(30),                          -- PRODUCT_LINE / PRODUCT_MODEL（与 /instances 的 ownerType 同口径）
    gate_oid      CHAR(36),                             -- 关联阶段门（→ ck_gate.oid；可选 —— 不是每个问题都来自门评审）
    related_type  VARCHAR(30),                          -- 关联对象类型（如 PART / DOCUMENT；空=不关联）
    related_code  VARCHAR(100),                         -- 关联对象编码
    root_cause    VARCHAR(2000),                        -- 根因分析
    resolution    VARCHAR(2000),                        -- 处理措施
    verify_result VARCHAR(2000),                        -- 验证结论（验证通过时必填）
    closed_at     TIMESTAMP,                            -- 关闭时间（CLOSED 时填；重开清空）
    tenant_oid    CHAR(36),
    creator       VARCHAR(100),
    created_at    TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updater       VARCHAR(100),
    updated_at    TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE (code, tenant_oid)
);

CREATE INDEX IF NOT EXISTS idx_issue_tenant_status ON ck_issue (tenant_oid, status, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_issue_tenant_owner  ON ck_issue (tenant_oid, owner);
CREATE INDEX IF NOT EXISTS idx_issue_tenant_product ON ck_issue (tenant_oid, product_oid);

-- ============================================================================
-- 问题处理记录（跟踪时间线）
--   · 每次创建 / 编辑 / 流转 / 备注追加一行；"跟踪"= 能看这条问题从提出到关闭的完整历程
--   · action 取值见 IssueFlow：CREATE / UPDATE / ASSIGN / START / SUBMIT_VERIFY /
--     VERIFY_PASS / VERIFY_FAIL / CANCEL / REOPEN / COMMENT
--   · from_status / to_status：流转类动作填；编辑与备注类留空
-- ============================================================================
CREATE TABLE IF NOT EXISTS ck_issue_activity (
    oid          CHAR(36)      PRIMARY KEY,
    issue_oid    CHAR(36)      NOT NULL,
    action       VARCHAR(30)   NOT NULL,
    from_status  VARCHAR(20),
    to_status    VARCHAR(20),
    comment      VARCHAR(2000),
    operator     VARCHAR(100),                          -- 操作人（用户名；冗余，避免与审计列语义混淆）
    tenant_oid   CHAR(36),
    creator      VARCHAR(100),
    created_at   TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updater      VARCHAR(100),
    updated_at   TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_issue_activity_issue FOREIGN KEY (issue_oid) REFERENCES ck_issue (oid) ON DELETE CASCADE
);

CREATE INDEX IF NOT EXISTS idx_issue_activity_issue ON ck_issue_activity (issue_oid, created_at);

-- ============================================================================
-- 变更管理（ECR / ECN / 变更任务）
--   · 三段链路：变更请求（为什么改）→ 变更通告（改什么、谁能改）→ 变更任务（谁去改）
--   · 变更对象**不做版本控制**（WithoutVersionEntity）：价值在状态机与追溯链，
--     同一张单不该有"多个版本"；真正被修订的是受影响对象（Part / Document / EngDocument）
--   · 受影响对象处置统一为 REVISE：执行时转调 ReviseProvider，
--     本模块不复制任何版本逻辑（行操作修订与变更驱动修订必须同一段代码）
--   · 本表不在 TenantStatementInterceptor 的共享名单里 → Mapper 必须显式书写 tenant_oid
-- ============================================================================
CREATE TABLE IF NOT EXISTS ck_change_request (
    oid              CHAR(36)      PRIMARY KEY,
    code             VARCHAR(50)   NOT NULL,               -- ECR-0001（租户内递增，服务端生成）
    name             VARCHAR(300)  NOT NULL,
    description      VARCHAR(4000),
    attachment_oid   CHAR(36),                             -- 补充材料（ck_attachment.oid）；评审佐证材料，随单据保存
    change_type      VARCHAR(50),                          -- DESIGN 设计 / DOCUMENT 文档 / BOM 结构 / PROCESS 工艺 / MATERIAL 物料
    priority         VARCHAR(20)   NOT NULL DEFAULT 'MEDIUM', -- HIGH / MEDIUM / LOW
    reason           VARCHAR(2000),                        -- 变更原因（提交时必填）
    proposal         VARCHAR(2000),                        -- 建议方案
    status           VARCHAR(20)   NOT NULL DEFAULT 'DRAFT', -- 见 cn.ck.plm.change.flow.ChangeRequestFlow
    source_issue_oid CHAR(36),                             -- 来源问题（ck_issue.oid）；可选
    product_oid      CHAR(36),                             -- 关联产品系列/型号
    product_type     VARCHAR(30),                          -- PRODUCT_LINE / PRODUCT_MODEL
    owner            VARCHAR(100),
    requester        VARCHAR(100),                         -- 提出人（创建时自动 = 当前用户）
    due_date         DATE,
    accepted_at      TIMESTAMP,
    closed_at        TIMESTAMP,
    process_instance_id VARCHAR(64),                       -- 评审流程实例（发起评审时绑定）
    change_level     VARCHAR(20)   DEFAULT 'STANDARD',     -- QUICK 快速 / STANDARD 标准 / MAJOR 重大（决定审签路径）
    cert_impact      VARCHAR(20),                          -- 认证合规影响：NONE / REVIEW / YES
    customer_impact  VARCHAR(20),                          -- 客户合同影响：NONE / NOTIFY / APPROVAL
    inventory_impact VARCHAR(20),                          -- 在制与库存影响：NONE / PENDING / DISPOSAL
    tenant_oid       CHAR(36),
    creator          VARCHAR(100),
    created_at       TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updater          VARCHAR(100),
    updated_at       TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE (code, tenant_oid)
);

CREATE INDEX IF NOT EXISTS idx_change_request_status ON ck_change_request (tenant_oid, status, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_change_request_issue  ON ck_change_request (source_issue_oid);

-- 存量库升级：评审流程实例列（发起评审时才写入，历史单据保持 NULL）
ALTER TABLE ck_change_request ADD COLUMN IF NOT EXISTS process_instance_id VARCHAR(64);

-- 存量库升级：「提出 → 影响分析 → 评审」三段所需的字段（2026-11-01）
--   · change_level：变更分级，决定审签路径（QUICK 单人审批 / STANDARD 会签+产品经理 / MAJOR 会签+CCB 会签）
--   · cert/customer/inventory_impact：影响分析的结构化结论，评审时逐项看，不埋在正文里
ALTER TABLE ck_change_request ADD COLUMN IF NOT EXISTS change_level     VARCHAR(20) DEFAULT 'STANDARD';
ALTER TABLE ck_change_request ADD COLUMN IF NOT EXISTS cert_impact      VARCHAR(20);
ALTER TABLE ck_change_request ADD COLUMN IF NOT EXISTS customer_impact  VARCHAR(20);
ALTER TABLE ck_change_request ADD COLUMN IF NOT EXISTS inventory_impact VARCHAR(20);
-- 补充材料：存 ck_attachment.oid（上传在保存之前完成，随单据落库；与 PART/DOCUMENT 的 attachment_oid 同口径）
-- 注意：ck_change_notice 的同名补列放在其 CREATE TABLE 之后（见下方变更通告一节），
-- 否则新装库首次执行时表还不存在，ALTER 会直接失败 —— 2026-11-06 新装部署踩坑修复
ALTER TABLE ck_change_request ADD COLUMN IF NOT EXISTS attachment_oid  CHAR(36);

-- ============================================================================
-- 变更请求 ↔ 问题（多对多）
--   · 为什么单独建表：一张变更单常常由**多个**问题驱动（同一处结构缺陷会同时引出
--     装配、外观、可靠性几条问题），单个 source_issue_oid 存不下
--   · source_issue_oid 保留不废：仍写入"第一个关联问题"，历史反查与既有索引不受影响
--   · 与 ck_change_request 一样不在租户拦截器的共享名单里 → Mapper 必须显式书写 tenant_oid
-- ============================================================================
CREATE TABLE IF NOT EXISTS ck_change_request_issue (
    oid          CHAR(36)     PRIMARY KEY,
    request_oid  CHAR(36)     NOT NULL,
    issue_oid    CHAR(36)     NOT NULL,
    tenant_oid   CHAR(36),
    creator      VARCHAR(100),
    created_at   TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_cri_request FOREIGN KEY (request_oid) REFERENCES ck_change_request (oid) ON DELETE CASCADE,
    CONSTRAINT fk_cri_issue   FOREIGN KEY (issue_oid)   REFERENCES ck_issue (oid) ON DELETE CASCADE,
    UNIQUE (request_oid, issue_oid)
);

CREATE INDEX IF NOT EXISTS idx_cri_request ON ck_change_request_issue (tenant_oid, request_oid);
CREATE INDEX IF NOT EXISTS idx_cri_issue   ON ck_change_request_issue (issue_oid);

-- ============================================================================
-- 变更请求 ↔ 预估受影响对象
--   · 与 ck_change_affected_object（执行清单）刻意分表：这里不知道"改哪一版"，
--     只表达影响面，供评审判断与 ECN 建单时带入；没有版本 / 锁 / 修订字段，
--     避免"预估对象"被误当成执行对象占用变更锁
--   · container_oid 是录入时快照：只用于展示与按产品筛选，不跟随对象迁移更新
--   · 与 ck_change_request 一样不在租户拦截器的共享名单里 → Mapper 必须显式写 tenant_oid
-- ============================================================================
CREATE TABLE IF NOT EXISTS ck_change_request_affected_object (
    oid          CHAR(36)     PRIMARY KEY,
    request_oid  CHAR(36)     NOT NULL,
    entity_type  VARCHAR(30)  NOT NULL,          -- PART / DOCUMENT / ENG_DOCUMENT
    entity_oid   CHAR(36)     NOT NULL,
    entity_code  VARCHAR(100),                   -- 编码快照
    entity_name  VARCHAR(200),                   -- 名称快照
    container_oid CHAR(36),                      -- 所属容器快照
    note         VARCHAR(500),
    tenant_oid   CHAR(36),
    creator      VARCHAR(100),
    created_at   TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updater      VARCHAR(100),
    updated_at   TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_crao_request FOREIGN KEY (request_oid)
        REFERENCES ck_change_request (oid) ON DELETE CASCADE,
    UNIQUE (request_oid, entity_type, entity_oid)
);

CREATE INDEX IF NOT EXISTS idx_crao_request   ON ck_change_request_affected_object (tenant_oid, request_oid);
CREATE INDEX IF NOT EXISTS idx_crao_container ON ck_change_request_affected_object (tenant_oid, container_oid);

-- ============================================================================
-- 变更通告 ↔ 问题（多对多）与存量补录：见下方「变更通告（ECN）」建表之后
-- ============================================================================

-- 存量数据补录：把已有的单个来源问题搬进关联表（幂等，重复启动不重复插）
INSERT INTO ck_change_request_issue (oid, request_oid, issue_oid, tenant_oid, created_at)
SELECT gen_random_uuid()::text, c.oid, c.source_issue_oid, c.tenant_oid, CURRENT_TIMESTAMP
FROM ck_change_request c
WHERE c.source_issue_oid IS NOT NULL
  AND NOT EXISTS (
      SELECT 1 FROM ck_change_request_issue x
      WHERE x.request_oid = c.oid AND x.issue_oid = c.source_issue_oid
  );

-- ============================================================================
-- 变更通告（ECN）
--   · 一次变更的授权与实施主体；request_oid 可空（中小场景允许直接开 ECN）
--   · 审批委托给流程引擎：process_instance_id 指向 Flowable 实例；
--     未配流程的租户若开启免审，SUBMIT 时直接置 APPROVED 并留 bypass 痕迹
-- ============================================================================
CREATE TABLE IF NOT EXISTS ck_change_notice (
    oid                 CHAR(36)      PRIMARY KEY,
    code                VARCHAR(50)   NOT NULL,            -- ECN-0001
    name                VARCHAR(300)  NOT NULL,
    description         VARCHAR(4000),
    attachment_oid      CHAR(36),                          -- 补充材料（ck_attachment.oid）；返工方案/试产报告等
    request_oid         CHAR(36),                          -- 来源 ECR；可空
    product_oid         CHAR(36),                          -- 所属容器：产品系列 / 型号
    product_type        VARCHAR(50),                       -- PRODUCT_LINE / PRODUCT_MODEL
    -- 变更本身的定性信息：直接开的 ECN（无来源请求）由填单人给；
    -- 从 ECR 派生的通告是创建那一刻的**快照**（判定发生在 ECR 的评审 / CCB，通告不重复判定）
    change_type         VARCHAR(20),                       -- DESIGN / DOCUMENT / BOM / PROCESS / MATERIAL
    change_level        VARCHAR(20),                       -- QUICK / STANDARD / MAJOR
    priority            VARCHAR(20),                       -- HIGH / MEDIUM / LOW
    cert_impact         VARCHAR(20),                       -- NONE / REVIEW / YES
    customer_impact     VARCHAR(20),                       -- NONE / NOTIFY / APPROVAL
    inventory_impact    VARCHAR(20),                       -- NONE / PENDING / DISPOSAL
    reason              VARCHAR(4000),                     -- 变更原因
    proposal            VARCHAR(4000),                     -- 执行方案（派生时以 ECR 的建议方案为起点）
    status              VARCHAR(20)   NOT NULL DEFAULT 'DRAFT',
    plan_start          DATE,
    plan_end            DATE,
    process_instance_id VARCHAR(64),                       -- 绑定的 Flowable 实例
    approved_at         TIMESTAMP,
    completed_at        TIMESTAMP,
    closed_at           TIMESTAMP,
    owner               VARCHAR(100),
    approval_bypassed   BOOLEAN       NOT NULL DEFAULT FALSE, -- 免审直接通过；审计必须能看出来
    bypass_reason       VARCHAR(500),                      -- 免审时必填
    bypassed_by         VARCHAR(100),
    bypassed_at         TIMESTAMP,
    tenant_oid          CHAR(36),
    creator             VARCHAR(100),
    created_at          TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updater             VARCHAR(100),
    updated_at          TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE (code, tenant_oid),
    CONSTRAINT fk_change_notice_request FOREIGN KEY (request_oid)
        REFERENCES ck_change_request (oid) ON DELETE SET NULL
);

CREATE INDEX IF NOT EXISTS idx_change_notice_status  ON ck_change_notice (tenant_oid, status, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_change_notice_request ON ck_change_notice (request_oid);

-- 存量库补列：变更通告原先只有 request_oid（来源 ECR），没有自己的产品归属，
-- 直接开的 ECN（无来源请求）就没法挂到任何产品下。
ALTER TABLE ck_change_notice ADD COLUMN IF NOT EXISTS product_oid  CHAR(36);
ALTER TABLE ck_change_notice ADD COLUMN IF NOT EXISTS product_type VARCHAR(50);
-- 存量库升级（2026-11-01）补充材料列：新装库建表时已含此列（幂等），此处为已有老库补列。
-- 必须位于 CREATE TABLE ck_change_notice 之后，否则新装库执行到此表尚不存在
ALTER TABLE ck_change_notice ADD COLUMN IF NOT EXISTS attachment_oid  CHAR(36);

-- 存量库补列（2026-11-03）：只用变更通告的租户也要能说清「改什么、多严重、影响什么、为什么改、怎么做」。
--   · 无来源请求（独立 ECN）：这 8 列由填单人给，是审批人判断的唯一依据；
--   · 有来源请求（派生 ECN）：创建时从 ECR 继承一份快照，表单上锁定只读 ——
--     变更类型 / 分级 / 优先级 / 三项影响是 ECR 评审（必要时要过 CCB）定下的结论，
--     通告再判一次会让两单各说一套，审批路径也就失去意义。
ALTER TABLE ck_change_notice ADD COLUMN IF NOT EXISTS change_type      VARCHAR(20);
ALTER TABLE ck_change_notice ADD COLUMN IF NOT EXISTS change_level     VARCHAR(20);
ALTER TABLE ck_change_notice ADD COLUMN IF NOT EXISTS priority         VARCHAR(20);
ALTER TABLE ck_change_notice ADD COLUMN IF NOT EXISTS cert_impact      VARCHAR(20);
ALTER TABLE ck_change_notice ADD COLUMN IF NOT EXISTS customer_impact  VARCHAR(20);
ALTER TABLE ck_change_notice ADD COLUMN IF NOT EXISTS inventory_impact VARCHAR(20);
ALTER TABLE ck_change_notice ADD COLUMN IF NOT EXISTS reason           VARCHAR(4000);
ALTER TABLE ck_change_notice ADD COLUMN IF NOT EXISTS proposal         VARCHAR(4000);

CREATE INDEX IF NOT EXISTS idx_change_notice_product ON ck_change_notice (tenant_oid, product_oid);

-- ============================================================================
-- 变更通告 ↔ 问题（多对多）
--   · 与 ck_change_request_issue 同构、刻意不合并成一张多态表：
--     两张表各自有外键与唯一约束，SQL 直白；多态表要在每条语句里带 owner_type，
--     换来的只是"少建一张表"，得不偿失
--   · 从 ECR 转来的通告会把 ECR 的关联问题**带过来**（只带未关闭的，见服务层）
--   · 位置约束：必须位于 ck_change_notice 建表之后（外键引用），2026-11-06 修复顺序 bug
-- ============================================================================
CREATE TABLE IF NOT EXISTS ck_change_notice_issue (
    oid          CHAR(36)     PRIMARY KEY,
    notice_oid   CHAR(36)     NOT NULL,
    issue_oid    CHAR(36)     NOT NULL,
    tenant_oid   CHAR(36),
    creator      VARCHAR(100),
    created_at   TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_cni_notice FOREIGN KEY (notice_oid) REFERENCES ck_change_notice (oid) ON DELETE CASCADE,
    CONSTRAINT fk_cni_issue  FOREIGN KEY (issue_oid)  REFERENCES ck_issue (oid) ON DELETE CASCADE,
    UNIQUE (notice_oid, issue_oid)
);

CREATE INDEX IF NOT EXISTS idx_cni_notice ON ck_change_notice_issue (tenant_oid, notice_oid);
CREATE INDEX IF NOT EXISTS idx_cni_issue  ON ck_change_notice_issue (issue_oid);

-- ============================================================================
-- 变更任务
--   · 执行单元：按分工把受影响对象拆给不同的人/角色，失败可局部重试
-- ============================================================================
CREATE TABLE IF NOT EXISTS ck_change_task (
    oid          CHAR(36)      PRIMARY KEY,
    code         VARCHAR(50)   NOT NULL,                  -- ECT-0001
    name         VARCHAR(300)  NOT NULL,
    description  VARCHAR(2000),
    notice_oid   CHAR(36)      NOT NULL,
    task_type    VARCHAR(30),                             -- DESIGN / DOCUMENT / BOM / PROCESS / VERIFY
    assignee     VARCHAR(100),                            -- 责任人（用户名）
    status       VARCHAR(20)   NOT NULL DEFAULT 'OPEN',   -- 见 ChangeTaskFlow
    plan_start   DATE,
    plan_end     DATE,
    completed_at TIMESTAMP,
    tenant_oid   CHAR(36),
    creator      VARCHAR(100),
    created_at   TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updater      VARCHAR(100),
    updated_at   TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE (code, tenant_oid),
    CONSTRAINT fk_change_task_notice FOREIGN KEY (notice_oid)
        REFERENCES ck_change_notice (oid) ON DELETE CASCADE
);

CREATE INDEX IF NOT EXISTS idx_change_task_notice ON ck_change_task (notice_oid, status);
CREATE INDEX IF NOT EXISTS idx_change_task_assignee ON ck_change_task (tenant_oid, assignee, status);

-- ============================================================================
-- 受影响对象（变更的核心表）
--   · base_iteration_oid = 被修订的那一版（A.2）；result_iteration_oid = 修订产物（B.1）
--   · locked：变更占用 —— 同一主对象同时只能被一个未完成的变更持有
--     锁必须建在 (entity_type, entity_oid) 而不是迭代上：修订是主对象级的大版本跃迁，
--     两个变更分别盯 A.1 / A.2 同样会争抢 B 这个大版本号（ck_part_iteration 无
--     (master_oid, revision, iteration) 唯一约束）
--   · executed_at 非空即视为已执行 —— 变更执行必须幂等（重复提交不产生第二个 B 版）
-- ============================================================================
CREATE TABLE IF NOT EXISTS ck_change_affected_object (
    oid                  CHAR(36)      PRIMARY KEY,
    notice_oid           CHAR(36)      NOT NULL,
    task_oid             CHAR(36),                        -- 交给哪个变更任务执行；可空（待分配）
    entity_type          VARCHAR(30)   NOT NULL,          -- PART / DOCUMENT / ENG_DOCUMENT
    entity_oid           CHAR(36)      NOT NULL,          -- 主对象 oid
    entity_code          VARCHAR(100),                    -- 编码快照（列表免 join）
    entity_name          VARCHAR(200),                    -- 名称快照
    base_iteration_oid   CHAR(36)      NOT NULL,          -- 受影响版本（被执行的那一版）
    base_version         VARCHAR(20)   NOT NULL,          -- A.2
    result_iteration_oid CHAR(36),                        -- 修订产物（B.1）；执行后回填
    result_version       VARCHAR(20),
    executed_at          TIMESTAMP,
    executed_by          VARCHAR(100),
    locked               BOOLEAN       NOT NULL DEFAULT FALSE,
    note                 VARCHAR(500),
    tenant_oid           CHAR(36),
    creator              VARCHAR(100),
    created_at           TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updater              VARCHAR(100),
    updated_at           TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_change_affected_notice FOREIGN KEY (notice_oid)
        REFERENCES ck_change_notice (oid) ON DELETE CASCADE,
    CONSTRAINT fk_change_affected_task FOREIGN KEY (task_oid)
        REFERENCES ck_change_task (oid) ON DELETE SET NULL
);

-- 一个 ECN 里同一个对象只出现一次
CREATE UNIQUE INDEX IF NOT EXISTS uk_change_affected_object
    ON ck_change_affected_object (notice_oid, entity_type, entity_oid);
-- 变更占用（部分唯一索引：只有 locked=TRUE 的行参与唯一性）
CREATE UNIQUE INDEX IF NOT EXISTS uk_change_affected_lock
    ON ck_change_affected_object (entity_type, entity_oid) WHERE locked = TRUE;
CREATE INDEX IF NOT EXISTS idx_change_affected_notice ON ck_change_affected_object (notice_oid);
CREATE INDEX IF NOT EXISTS idx_change_affected_task   ON ck_change_affected_object (task_oid);
CREATE INDEX IF NOT EXISTS idx_change_affected_entity ON ck_change_affected_object (entity_type, entity_oid);

-- 所属容器快照（录入时写入，只用于展示 / 按产品筛选 / 审计追溯）
--   · 不存 container_type：容器 oid 已能唯一定位，类型靠三张容器表 join 推出
--   · 不跟随对象迁移更新：与 entity_code / entity_name 快照同语义
ALTER TABLE ck_change_affected_object ADD COLUMN IF NOT EXISTS container_oid CHAR(36);
CREATE INDEX IF NOT EXISTS idx_change_affected_container
    ON ck_change_affected_object (tenant_oid, container_oid);

-- 存量数据补齐（幂等）：按对象主表回填容器快照
UPDATE ck_change_affected_object a SET container_oid = p.container_oid
  FROM ck_part p WHERE a.entity_type = 'PART' AND a.entity_oid = p.oid AND a.container_oid IS NULL;
UPDATE ck_change_affected_object a SET container_oid = d.container_oid
  FROM ck_document d WHERE a.entity_type = 'DOCUMENT' AND a.entity_oid = d.oid AND a.container_oid IS NULL;
UPDATE ck_change_affected_object a SET container_oid = e.container_oid
  FROM ck_eng_document e WHERE a.entity_type = 'ENG_DOCUMENT' AND a.entity_oid = e.oid AND a.container_oid IS NULL;

-- ============================================================================
-- 变更操作记录（时间线，三类变更对象共用）
--   · 与 ck_issue_activity 同构：谁在什么时候把哪张单从什么状态改到什么状态
--   · 无外键（多态：change_type 决定 change_oid 指向哪张表）
-- ============================================================================
CREATE TABLE IF NOT EXISTS ck_change_activity (
    oid          CHAR(36)      PRIMARY KEY,
    change_type  VARCHAR(20)   NOT NULL,                  -- REQUEST / NOTICE / TASK
    change_oid   CHAR(36)      NOT NULL,
    action       VARCHAR(30)   NOT NULL,
    from_status  VARCHAR(20),
    to_status    VARCHAR(20),
    comment      VARCHAR(2000),
    operator     VARCHAR(100),
    tenant_oid   CHAR(36),
    creator      VARCHAR(100),
    created_at   TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updater      VARCHAR(100),
    updated_at   TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_change_activity_owner
    ON ck_change_activity (change_type, change_oid, created_at);

-- ============================================================================
-- 变更租户配置
--   · approval_required = FALSE 即"免审"：SUBMIT 时直接置 APPROVED 并记 bypass 痕迹
--   · 默认 TRUE（必须审批）—— 免审是显式选择，不能是默认行为
--   · bypass_for_released：免审时是否允许改"已发布/已归档"的对象；默认 FALSE
-- ============================================================================
CREATE TABLE IF NOT EXISTS ck_change_config (
    tenant_oid           CHAR(36)     PRIMARY KEY,
    approval_required    BOOLEAN      NOT NULL DEFAULT TRUE,
    approval_process_key VARCHAR(100) NOT NULL DEFAULT 'change-notice',
    bypass_for_released  BOOLEAN      NOT NULL DEFAULT FALSE,
    request_enabled      BOOLEAN      NOT NULL DEFAULT TRUE,
    updater              VARCHAR(100),
    updated_at           TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- 2026-11-01 「是否启用变更请求」：默认开启（老租户升级后行为不变）。
-- 关掉之后「变更控制」里不再出现变更请求页签 —— 有些团队只用 ECN 驱动改动，ECR 全程用不到，
-- 摆一个永远没单的页签只会占位置。注意这是**入口开关**，不清存量 ECR，也不拦服务层接口。
ALTER TABLE ck_change_config ADD COLUMN IF NOT EXISTS request_enabled BOOLEAN NOT NULL DEFAULT TRUE;

-- 2026-10-21 三类单据的流程各自可配。
--   · request_process_key → 变更请求（ECR）
--   · approval_process_key → 变更通告（ECN）：沿用旧列名，它是唯一已接入流程引擎的入口
--     （ChangeNoticeServiceImpl 的「发起审批」读它；更名会牵动存量行，故只收窄语义）
--   · task_process_key → 变更任务（ECT）
-- 为什么要分开：内置三流程是 change-request → change-notice → change-task 的父子链，
-- 一个入口就能带起全链；但流程是**可被用户在设计器里改**的，改完可能不再是父子链
-- （例如把通告做成独立审批流），届时三张单据各指各的 key 才说得清。
ALTER TABLE ck_change_config ADD COLUMN IF NOT EXISTS request_process_key VARCHAR(100) NOT NULL DEFAULT 'change-request';
ALTER TABLE ck_change_config ADD COLUMN IF NOT EXISTS task_process_key    VARCHAR(100) NOT NULL DEFAULT 'change-task';

-- 旧表的列默认值 **不会**被上面的 CREATE TABLE IF NOT EXISTS 修正：早期建表时默认值写的是
-- change-notice-approval，而该流程已合并进 change-notice 并随包删除。任何一次"插入行但没带
-- 这一列"都会落进一个不存在的 key，故在此显式重设（幂等，重复执行无副作用）。
ALTER TABLE ck_change_config ALTER COLUMN approval_process_key SET DEFAULT 'change-notice';
ALTER TABLE ck_change_config ALTER COLUMN request_process_key  SET DEFAULT 'change-request';
ALTER TABLE ck_change_config ALTER COLUMN task_process_key     SET DEFAULT 'change-task';

-- 2026-10-21 存量升级：审批流程 key 更名（change-notice-approval → change-notice）。
-- 变更域三流程落地后 change-notice-approval 已合并进 change-notice 并随包删除，
-- 存量行不改的话，配置页会显示一个已不存在的 key。
UPDATE ck_change_config SET approval_process_key = 'change-notice'
 WHERE approval_process_key = 'change-notice-approval';

-- ==================== 补全索引（批 2 收尾；**必须放在文件末尾**） ====================
-- 这些索引原先由迁移方法 / 初始化器建立。放在最后：本文件是按模块顺序执行的，
-- 若排在相关建表语句之前，首次执行会因"关系不存在"报错（幂等的 IF NOT EXISTS 救不了顺序问题）。
CREATE UNIQUE INDEX IF NOT EXISTS uk_gen_part_threshold_config_tenant
    ON ck_gen_part_threshold_config (tenant_oid);

CREATE UNIQUE INDEX IF NOT EXISTS uk_process_template_version
    ON ck_process_template_version (template_oid, version);
CREATE INDEX IF NOT EXISTS idx_process_template_version_tenant
    ON ck_process_template_version (tenant_oid);

CREATE INDEX IF NOT EXISTS idx_engml_usedby_iter ON ck_eng_doc_member_link (used_by_iteration_oid);
CREATE INDEX IF NOT EXISTS idx_engml_uses_master ON ck_eng_doc_member_link (uses_master_oid);
CREATE UNIQUE INDEX IF NOT EXISTS uk_engml_iter_master
    ON ck_eng_doc_member_link (used_by_iteration_oid, uses_master_oid);

CREATE INDEX IF NOT EXISTS idx_engrl_refby_iter ON ck_eng_doc_ref_link (referenced_by_iteration_oid);
CREATE INDEX IF NOT EXISTS idx_engrl_refs_master ON ck_eng_doc_ref_link (references_master_oid);
CREATE INDEX IF NOT EXISTS idx_engrl_refs_type_iter
    ON ck_eng_doc_ref_link (references_master_oid, reference_type, referenced_by_iteration_oid);
CREATE INDEX IF NOT EXISTS idx_engrl_reftype ON ck_eng_doc_ref_link (reference_type);
CREATE UNIQUE INDEX IF NOT EXISTS uk_engrl_iter_master
    ON ck_eng_doc_ref_link (referenced_by_iteration_oid, references_master_oid);

-- ==================== CAD 转换器对接配置 ====================
-- 转换器的地址、令牌、超时、限流与看门狗参数，原先只存在 application*.yml
-- （plm.cad.forge.*）。改一次地址要改文件 + 重启，而"转换器部署到哪台机器"本身就是
-- 运维现场随时会变的事 —— 下沉到库里，页面改完即时生效。
--
-- 单行表：转换器是**整个部署共享的一套基础设施**（不是某个租户的资产），
-- 所以没有 tenant_oid，也必须在 TenantStatementInterceptor 的完全共享表名单里 ——
-- 否则租户上下文会把这一行过滤掉，页面永远读不到配置。
-- 平台管理员可改，其它角色只读（见 CadForgeConfigService#update）。
CREATE TABLE IF NOT EXISTS ck_cad_forge_config (
    oid                     VARCHAR(64)  PRIMARY KEY,
    enabled                 BOOLEAN      DEFAULT false,
    base_url                VARCHAR(512),
    callback_token          VARCHAR(256),
    callback_url            VARCHAR(512),
    connect_timeout_ms      INTEGER      DEFAULT 3000,
    read_timeout_ms         INTEGER      DEFAULT 10000,
    max_source_size_mb      INTEGER      DEFAULT 200,
    supported_extensions    VARCHAR(255),
    max_attempts            INTEGER      DEFAULT 3,
    watchdog_enabled        BOOLEAN      DEFAULT true,
    stale_after_minutes     INTEGER      DEFAULT 15,
    watchdog_batch_size     INTEGER      DEFAULT 50,
    watchdog_interval_ms    BIGINT       DEFAULT 120000,
    creator                 VARCHAR(64),
    created_at              TIMESTAMP,
    updater                 VARCHAR(64),
    updated_at              TIMESTAMP
);

-- ==================== CAD 轻量化可视化 ====================
-- 一次「STEP 源数模 → Web 可渲染资产」的转换任务。本表只记编排与状态：
-- 产物（GLB / 结构树 JSON / 缩略图）各自作为独立的 ck_file 记录落在 cad/ 目录，
-- 从而复用既有的下载、权限与清理通道，不必为产物再开一套文件管理。
--
-- 转换器（CK-CADForge）是独立进程、独立仓库（LGPL-3.0，与 Apache-2.0 的本工程
-- 只能通过网络协作），本表记录的就是这条 HTTP 协作链路的结果。
CREATE TABLE IF NOT EXISTS ck_cad_visualization (
    oid                     CHAR(36)     PRIMARY KEY,
    tenant_oid              CHAR(36),
    eng_doc_iteration_oid   CHAR(36)     NOT NULL,
    source_ckfile_oid       CHAR(36)     NOT NULL,
    source_sha1             VARCHAR(40),
    source_file_name        VARCHAR(500),
    status                  VARCHAR(24)  NOT NULL,
    progress                INTEGER      NOT NULL DEFAULT 0,
    stage                   VARCHAR(64),
    forge_job_id            VARCHAR(64),
    glb_ckfile_oid          CHAR(36),
    structure_ckfile_oid    CHAR(36),
    thumbnail_ckfile_oid    CHAR(36),
    lod_ckfile_oid          CHAR(36),
    units                   VARCHAR(8),
    triangle_count          BIGINT,
    part_count              INTEGER,
    source_size             BIGINT,
    output_size             BIGINT,
    duration_ms             BIGINT,
    converter_version       VARCHAR(32),
    error_code              VARCHAR(64),
    error_message           TEXT,
    attempt_count           INTEGER      NOT NULL DEFAULT 0,
    submitted_at            TIMESTAMP,
    converted_at            TIMESTAMP,
    creator                 VARCHAR(100),
    created_at              TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updater                 VARCHAR(100),
    updated_at              TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- 内容指纹去重：同一迭代 + 同一份文件内容只转换一次。
-- 转换是这个系统里最贵的资源，而"反复上传同一份 STEP"是用户常态。
-- 注意 source_sha1 为 NULL 时不参与唯一判定（PostgreSQL 语义）——
-- 读不到源文件就无法去重，这种情况放行而不是报错。
CREATE UNIQUE INDEX IF NOT EXISTS uk_cadvis_iter_sha1
    ON ck_cad_visualization (eng_doc_iteration_oid, source_sha1);

CREATE INDEX IF NOT EXISTS idx_cadvis_iter ON ck_cad_visualization (eng_doc_iteration_oid);
CREATE INDEX IF NOT EXISTS idx_cadvis_job ON ck_cad_visualization (forge_job_id);
-- 看门狗扫描用：捞仍在 QUEUED/RUNNING 且提交时间早于阈值的任务
CREATE INDEX IF NOT EXISTS idx_cadvis_inflight
    ON ck_cad_visualization (status, submitted_at);

-- 存量库平滑升级：当前阶段（转换器上报的一句话，如「网格化零件 42/118」）。
-- 只有进度百分比时，卡在 85% 的用户完全不知道在等什么。
ALTER TABLE ck_cad_visualization ADD COLUMN IF NOT EXISTS stage VARCHAR(64);
