-- =================================================================
--  「类型-生命周期状态-流程模板」关联：唯一键补上租户。
--
--  背景：配置的归属租户此前取自「类型 → 生命周期模板」关联行，而那条关联绝大多数落在平台租户
--        （平台级类型 + 平台管理员配置）—— 于是业务租户用户配出来的配置也成了平台行，对所有租户可见；
--        而唯一键又不含租户，各租户无法各配各的（先配的赢，别人改不了）。
--
--  现在：归属租户 = 保存时当前用户的租户；读取时「本租户优先，平台租户的行作共享默认」。
--        唯一键由 (type_oid, iteration_oid, status_code)
--        改为 (tenant_oid, type_oid, iteration_oid, status_code)。
--
--  说明：应用启动时由 TypeLifecycleStateProcessLinkTableInitializer 自动执行同样的替换（幂等）。
--        既有行不动 —— 它们保留原租户：历史行的归属是当时的语义，改判会改变可见范围。
--        （若要清理"平台行却指向某业务租户的流程模板"这类历史错配，请重新在该租户下配置一次，
--        新行会以该租户落库并优先生效。）
-- =================================================================

-- 1. 历史上这个唯一键换过三个名字，全部"不含租户"，一并清掉
DROP INDEX IF EXISTS uk_lsp_type_template_status;
DROP INDEX IF EXISTS uk_tlspl_type_template_status;
DROP INDEX IF EXISTS uk_tlspl_type_iteration_status;

-- 2. 新唯一键：租户 + 类型 + 生命周期模板子版本 + 状态
CREATE UNIQUE INDEX IF NOT EXISTS uk_tlspl_tenant_type_iteration_status
    ON ck_type_lifecycle_state_process_link (tenant_oid, type_oid, lifecycle_template_iteration_oid, status_code);
