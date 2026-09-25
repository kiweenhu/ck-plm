-- =================================================================
--  V023  流程实体关联表：版本口径从「迭代 oid」改为「大版本 revision」
--
--  背景：发起流程针对的是业务对象的【大版本】（revision，如 A），而大版本下还会继续产出
--        小版本（检出 → A.2 → 检入 → A.3 …）。原来记的是发起那一刻的迭代 oid，对象每推进
--        一个小版本，这份关联就指向了一个已经过时的版本。改记大版本后天然稳定，
--        需要具体版本时统一解析为"该大版本当前的最新小版本"。
--
--  迁移口径：老行的迭代 oid → 该迭代所属的大版本（revision）。
--           查不到的行版本落 NULL（与"无版本对象"同形，比编一个值更诚实）。
--
--  说明：应用启动时由 ProcessTemplateSchemaInitializer 自动执行同样的迁移（幂等）。
--        本脚本用于 DBA 手工库 / 需要显式留痕的场景，可重复执行。
-- =================================================================

DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM information_schema.columns
               WHERE table_name = 'ck_process_entity_set' AND column_name = 'entity_iteration_oid') THEN
        -- 1) 新列就位（老列先留着，回填还要用它）
        ALTER TABLE ck_process_entity_set ADD COLUMN IF NOT EXISTS version VARCHAR(64);

        -- 2) 按宿主迭代表回填大版本。某宿主迭代表不存在时跳过它，
        --    不让一条缺失的表把整条迁移拖失败（老库可能从没建过该宿主的表）
        IF to_regclass('ck_part_iteration') IS NOT NULL THEN
            UPDATE ck_process_entity_set e SET version = i.revision FROM ck_part_iteration i
             WHERE i.oid = e.entity_iteration_oid AND e.version IS NULL;
        END IF;
        IF to_regclass('ck_document_iteration') IS NOT NULL THEN
            UPDATE ck_process_entity_set e SET version = i.revision FROM ck_document_iteration i
             WHERE i.oid = e.entity_iteration_oid AND e.version IS NULL;
        END IF;
        IF to_regclass('ck_eng_document_iteration') IS NOT NULL THEN
            UPDATE ck_process_entity_set e SET version = i.revision FROM ck_eng_document_iteration i
             WHERE i.oid = e.entity_iteration_oid AND e.version IS NULL;
        END IF;
        IF to_regclass('ck_functional_iteration') IS NOT NULL THEN
            UPDATE ck_process_entity_set e SET version = i.revision FROM ck_functional_iteration i
             WHERE i.oid = e.entity_iteration_oid AND e.version IS NULL;
        END IF;

        -- 3) 回填完成，退役老列（顺带带走按它建的索引）
        ALTER TABLE ck_process_entity_set DROP COLUMN entity_iteration_oid;
    END IF;
END $$;

DROP INDEX IF EXISTS idx_pes_iteration;

-- 唯一键重建到新列上（唯一键里的表达式引用了列名，必须重建）
DROP INDEX IF EXISTS uk_pes_instance_entity;
CREATE UNIQUE INDEX IF NOT EXISTS uk_pes_instance_entity
    ON ck_process_entity_set(process_instance_id, entity_oid, COALESCE(version, ''));

CREATE INDEX IF NOT EXISTS idx_pes_version ON ck_process_entity_set(version);
