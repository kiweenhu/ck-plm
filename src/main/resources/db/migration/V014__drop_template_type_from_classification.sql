-- =================================================================
--  移除分类表 ck_classification 的 template_type 字段。
--  背景：分类不再区分「分类模板」与普通分类，初始化时直接作为普通分类预置。
--  本脚本幂等，可重复执行。
-- =================================================================

DROP INDEX IF EXISTS idx_cls_template_type;

ALTER TABLE ck_classification DROP COLUMN IF EXISTS template_type;
