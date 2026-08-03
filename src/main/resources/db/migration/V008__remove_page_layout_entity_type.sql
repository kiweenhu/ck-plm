-- 移除 ck_type_page_layout 表的 entity_type 列（手动执行）
-- 请在 PostgreSQL 中执行此脚本

-- 1. 删除 entity_type 列（会级联删除基于该列的约束）
ALTER TABLE ck_type_page_layout DROP COLUMN IF EXISTS entity_type;

-- 2. 如果不存在新的唯一约束，则创建
-- PostgreSQL 中需要先检查
DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint
        WHERE conrelid = 'ck_type_page_layout'::regclass
          AND conname = 'uk_type_page_layout_entity_op_tenant'
    ) THEN
        ALTER TABLE ck_type_page_layout ADD CONSTRAINT uk_type_page_layout_entity_op_tenant
            UNIQUE (entity_oid, operation_code, tenant_oid);
    END IF;
END $$;
