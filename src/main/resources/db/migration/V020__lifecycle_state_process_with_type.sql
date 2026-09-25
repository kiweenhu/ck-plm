-- =================================================================
--  「类型 · 生命周期状态 → 流程模板」绑定表改造：补上类型维度。
--
--  背景：本表首版主键是 (iteration_oid, status_code) —— 语义不完整：
--        它表达的是"生命周期模板的某状态用哪个流程"，而同一个生命周期模板会被
--        多个类型复用（STANDARD 就挂着 20+ 个类型），于是无法表达
--        "电子元器件的 DRAFT 走 NPI 流程、文档的 DRAFT 走文档审批"。
--        主语应当是「类型」，故主键改为 (type_oid, lifecycle_template_code, status_code)。
--
--  同时把"子版本 oid"换成"模板 code"：绑定是配置，与 ck_type_lifecycle_template_link
--  （类型 → 模板 code）同层同构；编辑生命周期模板（新建子版本）不再需要搬运绑定，
--  也不会留下"历史子版本里的隐形绑定挡住流程模板删除"这类坑。
--
--  数据迁移：老行是模板级绑定 → 按"当时绑了该模板的类型"展开成多行（语义与迁移前一致）；
--          展开不到任何类型的老行丢弃（该模板已无类型使用）。
--
--  说明：应用启动时由 TypeLifecycleStateProcessLinkTableInitializer（当时类名还是
--        LifecycleStateProcessTableInitializer）自动执行同样的迁移（幂等）。
--        本脚本用于 DBA 手工库 / 需要显式留痕的场景，可重复执行。
--        本表随后在 V021 更名为 ck_type_lifecycle_state_process_link 并归位到软类型模块。
-- =================================================================

-- 1. 新列（全新库由建表语句直接给出新结构，这里是老库补列）
ALTER TABLE ck_lifecycle_state_process ADD COLUMN IF NOT EXISTS type_oid CHAR(36);
ALTER TABLE ck_lifecycle_state_process ADD COLUMN IF NOT EXISTS lifecycle_template_code VARCHAR(50);

-- 2. 老结构迁移（仅当 iteration_oid 列还在时执行）
DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM information_schema.columns
               WHERE table_name = 'ck_lifecycle_state_process' AND column_name = 'iteration_oid') THEN
        -- 2.1 老行补上模板 code（子版本 → master.code）
        UPDATE ck_lifecycle_state_process p
           SET lifecycle_template_code = t.code
          FROM ck_lifecycle_template_iteration i
          JOIN ck_lifecycle_template t ON t.oid = i.master_oid
         WHERE i.oid = p.iteration_oid
           AND p.lifecycle_template_code IS NULL;

        -- 2.2 老列必须先退役：它是 NOT NULL，留着就插不进"没有 iteration_oid"的新行
        --     （外键与依赖它的唯一索引随列一并消失）
        ALTER TABLE ck_lifecycle_state_process DROP COLUMN iteration_oid;

        -- 2.3 模板级老行 → 按"绑了该模板的类型"展开（一行变多行，语义不变）
        --     租户取【类型关联行】的租户：本表是平台共享表，按 tenant_oid IN (平台, 当前)
        --     过滤，照抄老行的租户会得到"配了却谁都不显示"的隐身行
        INSERT INTO ck_lifecycle_state_process
            (oid, type_oid, lifecycle_template_code, status_code, process_template_oid,
             tenant_oid, creator, created_at, updater, updated_at)
        SELECT gen_random_uuid()::text, l.type_oid, p.lifecycle_template_code, p.status_code,
               p.process_template_oid, l.tenant_oid,
               p.creator, p.created_at, p.updater, p.updated_at
          FROM ck_lifecycle_state_process p
          JOIN ck_type_lifecycle_template_link l
            ON l.lifecycle_template_code = p.lifecycle_template_code
         WHERE p.type_oid IS NULL
           AND p.lifecycle_template_code IS NOT NULL;

        -- 2.4 展开完成：丢弃无类型归属的老行（该模板已无任何类型使用）
        DELETE FROM ck_lifecycle_state_process WHERE type_oid IS NULL;
    END IF;
END $$;

DROP INDEX IF EXISTS uk_lsp_iteration_status;

-- 3. 绑定行的归属租户对齐到「类型 → 生命周期模板」关联行（幂等，可自愈错误租户）
UPDATE ck_lifecycle_state_process p
   SET tenant_oid = l.tenant_oid
  FROM ck_type_lifecycle_template_link l
 WHERE l.type_oid = p.type_oid
   AND l.lifecycle_template_code = p.lifecycle_template_code
   AND p.tenant_oid IS DISTINCT FROM l.tenant_oid;

-- 4. 新唯一键与索引
CREATE UNIQUE INDEX IF NOT EXISTS uk_lsp_type_template_status
    ON ck_lifecycle_state_process (type_oid, lifecycle_template_code, status_code);
CREATE INDEX IF NOT EXISTS idx_lsp_type    ON ck_lifecycle_state_process (type_oid);
CREATE INDEX IF NOT EXISTS idx_lsp_process ON ck_lifecycle_state_process (process_template_oid);
CREATE INDEX IF NOT EXISTS idx_lsp_tenant  ON ck_lifecycle_state_process (tenant_oid);
