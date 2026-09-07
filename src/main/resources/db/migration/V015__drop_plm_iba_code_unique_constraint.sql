-- =================================================================
--  移除 ck_iba 表历史遗留的跨租户 code 唯一约束 plm_iba_code_key。
--  背景：该约束来自旧表 plm_iba，要求 code 全局唯一，破坏了多租户 IBA 隔离，
--        导致从平台克隆 IBA 定义到租户时违反唯一约束。
--  正确约束应为 UNIQUE(code, tenant_oid)（即 ck_iba_code_tenant_oid_key）。
--  本脚本幂等，可重复执行。
-- =================================================================

ALTER TABLE ck_iba DROP CONSTRAINT IF EXISTS plm_iba_code_key;
