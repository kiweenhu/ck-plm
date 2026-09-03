-- =================================================================
--  分类表增加 template_type 字段，用于区分「分类模板」与普通分类。
--  template_type: NULL/空 = 普通分类；ELECTRONIC = 电子元器件分类模板；STRUCTURE = 结构件分类模板
-- =================================================================

ALTER TABLE ck_classification ADD COLUMN IF NOT EXISTS template_type VARCHAR(20);

CREATE INDEX IF NOT EXISTS idx_cls_template_type ON ck_classification(template_type);
