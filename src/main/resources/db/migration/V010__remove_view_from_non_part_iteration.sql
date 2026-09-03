-- 移除非 Part 迭代表的 view 列（手动执行）
-- 背景：view 属性已从 IterationEntity 下沉到 PartIteration，
-- Document / Functional / LifecycleTemplate 迭代不再具有视图概念。
-- ck_part_iteration 保留 view 列（Part 特有视图）。
-- 请在 PostgreSQL 中执行此脚本。

ALTER TABLE ck_document_iteration DROP COLUMN IF EXISTS view;
ALTER TABLE ck_functional_iteration DROP COLUMN IF EXISTS view;
ALTER TABLE ck_lifecycle_template_iteration DROP COLUMN IF EXISTS view;
