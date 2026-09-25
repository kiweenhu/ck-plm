-- =================================================================
--  「类型-生命周期状态-流程模板」关联表：更名 + 归位到软类型模块。
--
--  背景：本表首版名为 ck_lifecycle_state_process，并在 V020 补上了类型维度
--        （type_oid + lifecycle_template_code + status_code）。
--        语义上它是"类型 → 生命周期状态 → 流程模板"的一条【关联】，
--        与同族 ck_type_lifecycle_template_link 完全对称，
--        故表名对齐为 ck_type_lifecycle_state_process_link（保留 link 后缀），
--        实体 / Mapper / Service 同步归位到 softtype 模块。
--
--  说明：应用启动时由 TypeLifecycleStateProcessLinkTableInitializer 自动执行同样的改名（幂等）。
--        本脚本用于 DBA 手工库 / 需要显式留痕的场景，可重复执行。
-- =================================================================

-- 1. 整表改名（保留既有行与索引，零数据搬运）
DO $$
BEGIN
    IF to_regclass('ck_lifecycle_state_process') IS NOT NULL
       AND to_regclass('ck_type_lifecycle_state_process_link') IS NULL THEN
        ALTER TABLE ck_lifecycle_state_process RENAME TO ck_type_lifecycle_state_process_link;
    END IF;
END $$;

-- 2. 索引名一并归正（改表名不会改索引名，残留的 uk_lsp_* 会让读者以为还有别的表）
DO $$
BEGIN
    IF to_regclass('ck_lifecycle_state_process_pkey') IS NOT NULL
       AND to_regclass('ck_type_lifecycle_state_process_link_pkey') IS NULL THEN
        ALTER INDEX ck_lifecycle_state_process_pkey RENAME TO ck_type_lifecycle_state_process_link_pkey;
    END IF;
    IF to_regclass('uk_lsp_type_template_status') IS NOT NULL
       AND to_regclass('uk_tlspl_type_template_status') IS NULL THEN
        ALTER INDEX uk_lsp_type_template_status RENAME TO uk_tlspl_type_template_status;
    END IF;
    IF to_regclass('idx_lsp_type') IS NOT NULL AND to_regclass('idx_tlspl_type') IS NULL THEN
        ALTER INDEX idx_lsp_type RENAME TO idx_tlspl_type;
    END IF;
    IF to_regclass('idx_lsp_process') IS NOT NULL AND to_regclass('idx_tlspl_process') IS NULL THEN
        ALTER INDEX idx_lsp_process RENAME TO idx_tlspl_process;
    END IF;
    IF to_regclass('idx_lsp_tenant') IS NOT NULL AND to_regclass('idx_tlspl_tenant') IS NULL THEN
        ALTER INDEX idx_lsp_tenant RENAME TO idx_tlspl_tenant;
    END IF;
END $$;
