-- =================================================================
--  ck_process_template.category（分组名）→ category_oid（引用 ck_process_category.oid）。
--
--  背景：原先模板直接存分组名，于是"改分组名"必须同步改一批模板行
--        （两步必须一起成功，否则模板会留在旧名下而从分组里"消失"）。
--        分组是清单页的导航维度、将来还要挂权限与外部引用 —— 引用必须稳定，
--        故改为引用字典 oid；模板表也不再保留名称冗余。
--
--  影响面：
--    · 后端：实体字段 category → categoryOid；Mapper 的 INSERT/UPDATE/过滤列；
--            ProcessCategoryService#require(name) → requireByOid(oid)；
--            分组改名不再需要同步模板（相关方法已删除）
--    · 接口：GET /api/plm/process-templates?categoryOid=...
--            POST /api/plm/process-templates/{oid}/category  body: { categoryOid }
--    · DSL：meta.category 仍是可读名称（创建时的快照，供导出展示），
--          但归属判定一律以 category_oid 为准 —— 设计器不提供该字段
--
--  说明：应用启动时由 ProcessTemplateSchemaInitializer 自动执行同样的迁移（幂等）。
--        本脚本用于 DBA 手工库 / 需要显式留痕的场景，可重复执行。
-- =================================================================

-- 1. 加列
ALTER TABLE ck_process_template ADD COLUMN IF NOT EXISTS category_oid VARCHAR(64);

-- 2. 回填：老的分组名 → 字典 oid（按 name + tenant_oid 匹配）
--    回填不到的行（老库里自由输入、字典中不存在）留空，界面上归入「未分类」
DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM information_schema.columns
               WHERE table_name = 'ck_process_template' AND column_name = 'category') THEN
        UPDATE ck_process_template t
           SET category_oid = c.oid
          FROM ck_process_category c
         WHERE t.category_oid IS NULL
           AND t.category = c.name
           AND t.tenant_oid = c.tenant_oid;
    END IF;
END $$;

-- 3. 老列与老索引退役
ALTER TABLE ck_process_template DROP COLUMN IF EXISTS category;
DROP INDEX IF EXISTS idx_process_template_category;

-- 4. 新列索引（分组过滤是清单页的主查询）
CREATE INDEX IF NOT EXISTS idx_process_template_category_oid ON ck_process_template(category_oid);
