-- =================================================================
--  ck_workflow_category → ck_process_category：流程分组升级为可维护的字典。
--
--  背景：旧表从建表起就没有任何代码读写它（无实体 / Mapper / 种子数据），
--        分类实际存在 ck_process_template.category 的自由文本里 —— 结果是
--        「研发 / 研发部 / 研发中心」这类近义分裂，且改名要逐条改模板。
--        现改为「先建分组 → 选分组 → 组内设计流程」，分组成为清单页的导航骨架。
--
--  同时修一个多租户缺陷：旧表把 name 做成【全局唯一】（A 租户建了"研发"，
--  B 租户就建不了），与 ck_process_template 的 (key, tenant_oid) 口径不一致；
--  改为 (name, tenant_oid) 复合唯一。
--
--  说明：应用启动时由 ProcessTemplateSchemaInitializer 自动完成同样的迁移
--        （幂等）。本脚本用于 DBA 手工库 / 需要显式留痕的场景，可重复执行。
-- =================================================================

-- 1. 整表改名（保留既有数据与索引；两张表同时存在时跳过）
DO $$
BEGIN
    IF to_regclass('ck_workflow_category') IS NOT NULL
       AND to_regclass('ck_process_category') IS NULL THEN
        ALTER TABLE ck_workflow_category RENAME TO ck_process_category;
    END IF;
END $$;

-- 2. 摘掉旧的全局唯一约束（约束名随旧表，故按 pg_constraint 动态查找）
DO $$
DECLARE c record;
BEGIN
    IF to_regclass('ck_process_category') IS NOT NULL THEN
        FOR c IN SELECT conname FROM pg_constraint
                 WHERE conrelid = 'ck_process_category'::regclass AND contype = 'u'
        LOOP
            EXECUTE 'ALTER TABLE ck_process_category DROP CONSTRAINT ' || quote_ident(c.conname);
        END LOOP;
    END IF;
END $$;

-- 3. 补齐由旧表改名而来的容器所缺少的列（新建表时为无操作）
ALTER TABLE ck_process_category ADD COLUMN IF NOT EXISTS description VARCHAR(512);
ALTER TABLE ck_process_category ADD COLUMN IF NOT EXISTS creator     VARCHAR(128);
ALTER TABLE ck_process_category ADD COLUMN IF NOT EXISTS created_at  TIMESTAMP;
ALTER TABLE ck_process_category ADD COLUMN IF NOT EXISTS updater     VARCHAR(128);
ALTER TABLE ck_process_category ADD COLUMN IF NOT EXISTS updated_at  TIMESTAMP;

-- 4. 索引：租户内唯一 + 租户过滤
CREATE UNIQUE INDEX IF NOT EXISTS uk_process_category_name_tenant ON ck_process_category(name, tenant_oid);
CREATE INDEX IF NOT EXISTS idx_process_category_tenant ON ck_process_category(tenant_oid);
DROP INDEX IF EXISTS idx_wfc_tenant;
