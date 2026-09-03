-- 为 ck_cls_iba_data 表增加 entity_oid 字段（手动执行）
-- 目的：分类 IBA 值需定位到具体对象实例（Part/Document），而非仅按分类节点存储。
-- 请在 PostgreSQL 中执行此脚本。

-- 1. 增加 entity_oid 字段（空字符串表示「分类节点默认值」，非空表示对象实例）
ALTER TABLE ck_cls_iba_data ADD COLUMN IF NOT EXISTS entity_oid CHAR(36) NOT NULL DEFAULT '';

-- 2. 删除旧主键，重建为包含 entity_oid 的主键
ALTER TABLE ck_cls_iba_data DROP CONSTRAINT IF EXISTS ck_cls_iba_data_pkey;
ALTER TABLE ck_cls_iba_data ADD PRIMARY KEY (entity_oid, classification_oid, attr_code);

-- 3. 新增 entity_oid + classification_oid 联合索引
CREATE INDEX IF NOT EXISTS idx_cid_entity ON ck_cls_iba_data (entity_oid, classification_oid);
