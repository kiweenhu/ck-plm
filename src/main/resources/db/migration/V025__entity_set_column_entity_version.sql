-- =================================================================
--  V025  流程实体关联表：版本列改名 version → entity_version
--
--  背景：同一模块里还存着"流程模板版本 / 流程定义版本"（ck_process_template_version、
--        act_re_procdef.version_），光写 version 分不清是谁的版本。本列存的是
--        【业务对象的大版本】（revision，如 A），改叫 entity_version 才一眼看得出。
--
--  注意：这只是【改名】，不改语义也不动数据 —— 大版本的口径与回填见 V023。
--
--  说明：应用启动时由 ProcessTemplateSchemaInitializer 自动执行同样的改名（幂等）。
--        本脚本用于 DBA 手工库 / 需要显式留痕的场景，可重复执行。
--        ⚠️ 手工执行后请重启应用（避免引擎/会话里残留按旧列名拼出的语句）。
-- =================================================================

DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM information_schema.columns
               WHERE table_name = 'ck_process_entity_set' AND column_name = 'version') THEN
        ALTER TABLE ck_process_entity_set RENAME COLUMN version TO entity_version;
    END IF;
END $$;

-- 索引名跟着走（改名不会自动改索引名）
ALTER INDEX IF EXISTS idx_pes_version RENAME TO idx_pes_entity_version;

-- 唯一键 uk_pes_instance_entity 用的是 COALESCE(version, '') 表达式：
-- PostgreSQL 的索引表达式记的是列引用，列改名后表达式自动指向 entity_version，无需重建。
CREATE INDEX IF NOT EXISTS idx_pes_entity_version ON ck_process_entity_set(entity_version);
