-- 目标系统注册表（2026-09）：流程「REST 接口调用」节点可调用的外部系统。
--
-- 背景：地址与凭据原本要填在流程节点上，于是改一次地址要改所有流程、凭据随 DSL 复制导出。
-- 现在集中到这里维护（同通知渠道的做法），流程节点只填 systemCode + 路径 + 参数。
--
-- 应用启动由 TargetSystemSchemaInitializer 自动执行同样的变更（幂等）；
-- 本脚本用于 DBA 手工建库 / 需要显式留痕的场景。

CREATE TABLE IF NOT EXISTS ck_target_system (
    oid           VARCHAR(64) PRIMARY KEY,
    code          VARCHAR(64)  NOT NULL,
    name          VARCHAR(128) NOT NULL,
    base_url      VARCHAR(512) NOT NULL,
    auth_type     VARCHAR(16)  NOT NULL DEFAULT 'NONE',
    username      VARCHAR(128),
    secret        VARCHAR(1024),
    enabled       BOOLEAN      NOT NULL DEFAULT TRUE,
    sort_order    INTEGER      NOT NULL DEFAULT 0,
    description   VARCHAR(512),
    tenant_oid    VARCHAR(64)  NOT NULL,
    creator       VARCHAR(128),
    created_at    TIMESTAMP,
    updater       VARCHAR(128),
    updated_at    TIMESTAMP
);

COMMENT ON TABLE  ck_target_system IS '流程可调用的外部系统注册表（租户内维护地址与认证凭据）';
COMMENT ON COLUMN ck_target_system.code IS '系统编码：流程节点按它引用，租户内唯一';
COMMENT ON COLUMN ck_target_system.auth_type IS 'NONE / BASIC / BEARER / API_KEY';
COMMENT ON COLUMN ck_target_system.username IS 'Basic 用户名；API_KEY 时为请求头名称（如 X-API-Key）';
COMMENT ON COLUMN ck_target_system.secret IS '凭据：Basic 口令 / Bearer Token / API Key 取值（接口不回传）';

CREATE UNIQUE INDEX IF NOT EXISTS uk_target_system_code_tenant
    ON ck_target_system (code, tenant_oid);
CREATE INDEX IF NOT EXISTS idx_target_system_tenant
    ON ck_target_system (tenant_oid);
