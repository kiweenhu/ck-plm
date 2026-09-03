-- =================================================================
--  历史内联迁移整合脚本（从 schema.sql 提取，集中收敛到此版本化迁移）
--  背景：schema.sql 原先混有大量内联 ALTER TABLE 迁移语句，
--  现统一收敛到本迁移脚本；schema.sql 仅保留纯净的 CREATE TABLE 定义。
--  所有语句均使用 IF NOT EXISTS / IF EXISTS 保证幂等，可重复执行。
--  请在 PostgreSQL 中执行此脚本。
-- =================================================================

-- 1. ck_organization：code 唯一约束 → (code, tenant_oid) 联合唯一
ALTER TABLE ck_organization DROP CONSTRAINT IF EXISTS ck_organization_code_key;
CREATE UNIQUE INDEX IF NOT EXISTS idx_org_code_tenant ON ck_organization(code, tenant_oid);

-- 2. ck_type_iba：owner_type → entity_code（兼容新旧数据库）
ALTER TABLE ck_type_iba ADD COLUMN IF NOT EXISTS owner_type VARCHAR(50);
ALTER TABLE ck_type_iba ADD COLUMN IF NOT EXISTS entity_code VARCHAR(50) NOT NULL DEFAULT '';
UPDATE ck_type_iba SET entity_code = owner_type WHERE entity_code = '' AND owner_type IS NOT NULL;
ALTER TABLE ck_type_iba DROP COLUMN IF EXISTS owner_type;

-- 3. ck_tenant：存量数据管理字段（新表 CREATE TABLE 已含，此处幂等）
ALTER TABLE ck_tenant ADD COLUMN IF NOT EXISTS admin_username VARCHAR(50);
ALTER TABLE ck_tenant ADD COLUMN IF NOT EXISTS admin_password VARCHAR(200);
ALTER TABLE ck_tenant ADD COLUMN IF NOT EXISTS admin_display_name VARCHAR(100);
ALTER TABLE ck_tenant ADD COLUMN IF NOT EXISTS approved_at TIMESTAMP;
ALTER TABLE ck_tenant ADD COLUMN IF NOT EXISTS approved_by VARCHAR(100);
ALTER TABLE ck_tenant ADD COLUMN IF NOT EXISTS reject_reason VARCHAR(500);

-- 4. ck_token：租户信息缓存列
ALTER TABLE ck_token ADD COLUMN IF NOT EXISTS tenant_oid VARCHAR(50);
ALTER TABLE ck_token ADD COLUMN IF NOT EXISTS tenant_name VARCHAR(100);

-- 5. 多租户：为所有业务表补齐 tenant_oid 列（新表 CREATE TABLE 已含，此处幂等）
ALTER TABLE ck_organization ADD COLUMN IF NOT EXISTS tenant_oid CHAR(36);
ALTER TABLE ck_user ADD COLUMN IF NOT EXISTS tenant_oid CHAR(36);
ALTER TABLE ck_role ADD COLUMN IF NOT EXISTS tenant_oid CHAR(36);
ALTER TABLE ck_role_member ADD COLUMN IF NOT EXISTS tenant_oid CHAR(36);
ALTER TABLE ck_product_line ADD COLUMN IF NOT EXISTS tenant_oid CHAR(36);
ALTER TABLE ck_product_model ADD COLUMN IF NOT EXISTS tenant_oid CHAR(36);
ALTER TABLE ck_stage ADD COLUMN IF NOT EXISTS tenant_oid CHAR(36);
ALTER TABLE ck_folder ADD COLUMN IF NOT EXISTS tenant_oid CHAR(36);
ALTER TABLE ck_team ADD COLUMN IF NOT EXISTS tenant_oid CHAR(36);
ALTER TABLE ck_team_member ADD COLUMN IF NOT EXISTS tenant_oid CHAR(36);
ALTER TABLE ck_document ADD COLUMN IF NOT EXISTS tenant_oid CHAR(36);
ALTER TABLE ck_document_iteration ADD COLUMN IF NOT EXISTS tenant_oid CHAR(36);
ALTER TABLE ck_file ADD COLUMN IF NOT EXISTS tenant_oid CHAR(36);
ALTER TABLE ck_attachment ADD COLUMN IF NOT EXISTS tenant_oid CHAR(36);
ALTER TABLE ck_media ADD COLUMN IF NOT EXISTS tenant_oid CHAR(36);
ALTER TABLE ck_workflow_category ADD COLUMN IF NOT EXISTS tenant_oid CHAR(36);
ALTER TABLE ck_user_activity ADD COLUMN IF NOT EXISTS tenant_oid CHAR(36);
ALTER TABLE ck_type_iba_data ADD COLUMN IF NOT EXISTS tenant_oid CHAR(36);
-- 平台共享表
ALTER TABLE ck_number ADD COLUMN IF NOT EXISTS tenant_oid CHAR(36);
ALTER TABLE ck_version_rule ADD COLUMN IF NOT EXISTS tenant_oid CHAR(36);
ALTER TABLE ck_lifecycle_status ADD COLUMN IF NOT EXISTS tenant_oid CHAR(36);
ALTER TABLE ck_lifecycle_template ADD COLUMN IF NOT EXISTS tenant_oid CHAR(36);
ALTER TABLE ck_lifecycle_template_iteration ADD COLUMN IF NOT EXISTS tenant_oid CHAR(36);
ALTER TABLE ck_lifecycle_template_iteration ADD COLUMN IF NOT EXISTS display_version VARCHAR(20);
ALTER TABLE ck_lifecycle_template_state ADD COLUMN IF NOT EXISTS tenant_oid CHAR(36);
ALTER TABLE ck_lifecycle_template_transition ADD COLUMN IF NOT EXISTS tenant_oid CHAR(36);
ALTER TABLE ck_view ADD COLUMN IF NOT EXISTS tenant_oid CHAR(36);
ALTER TABLE ck_view_transition ADD COLUMN IF NOT EXISTS tenant_oid CHAR(36);
ALTER TABLE ck_type_page_layout ADD COLUMN IF NOT EXISTS tenant_oid CHAR(36);
ALTER TABLE ck_type_definition ADD COLUMN IF NOT EXISTS tenant_oid CHAR(36);
ALTER TABLE ck_stage_template ADD COLUMN IF NOT EXISTS tenant_oid CHAR(36);
ALTER TABLE ck_cls_page_layout ADD COLUMN IF NOT EXISTS tenant_oid CHAR(36);

-- 6. ck_product_model：product_line_oid → parent_oid
ALTER TABLE ck_product_model ADD COLUMN IF NOT EXISTS parent_oid CHAR(36);
ALTER TABLE ck_product_model ADD COLUMN IF NOT EXISTS product_line_oid CHAR(36);
UPDATE ck_product_model SET parent_oid = product_line_oid WHERE parent_oid IS NULL AND product_line_oid IS NOT NULL;
ALTER TABLE ck_product_model DROP CONSTRAINT IF EXISTS ck_product_model_product_line_oid_fkey;
DROP INDEX IF EXISTS idx_model_product_line;
ALTER TABLE ck_product_model DROP COLUMN IF EXISTS product_line_oid;

-- 7. ck_stage：product_line_oid → owner_oid + owner_type + show_on_dashboard
ALTER TABLE ck_stage ADD COLUMN IF NOT EXISTS owner_oid CHAR(36);
ALTER TABLE ck_stage ADD COLUMN IF NOT EXISTS owner_type VARCHAR(10) NOT NULL DEFAULT 'LINE';
ALTER TABLE ck_stage ADD COLUMN IF NOT EXISTS show_on_dashboard BOOLEAN NOT NULL DEFAULT TRUE;
ALTER TABLE ck_stage DROP CONSTRAINT IF EXISTS ck_stage_product_line_oid_code_key;
ALTER TABLE ck_stage DROP CONSTRAINT IF EXISTS ck_stage_owner_code_unique;
ALTER TABLE ck_stage ADD CONSTRAINT ck_stage_owner_code_unique UNIQUE (owner_oid, owner_type, code);
DROP INDEX IF EXISTS idx_stage_product_line;

-- 8. ck_folder：type / owner_oid / stage_oid / parent_folder_oid
DROP INDEX IF EXISTS idx_folder_product_stage;
ALTER TABLE ck_folder ADD COLUMN IF NOT EXISTS type VARCHAR(10) NOT NULL DEFAULT 'USER';
ALTER TABLE ck_folder ADD COLUMN IF NOT EXISTS owner_oid CHAR(36);
ALTER TABLE ck_folder ADD COLUMN IF NOT EXISTS stage_oid CHAR(36);
ALTER TABLE ck_folder ADD COLUMN IF NOT EXISTS parent_folder_oid CHAR(36);

-- 9. ck_document：type_definition_code / container_oid / container_type / cls_oid
ALTER TABLE ck_document ADD COLUMN IF NOT EXISTS type_definition_code VARCHAR(50);
ALTER TABLE ck_document ADD COLUMN IF NOT EXISTS container_oid CHAR(36);
ALTER TABLE ck_document ADD COLUMN IF NOT EXISTS container_type VARCHAR(20) NOT NULL DEFAULT 'PRODUCT_LINE';
ALTER TABLE ck_document DROP CONSTRAINT IF EXISTS fk_doc_product_line;
DROP INDEX IF EXISTS idx_doc_product_line;
ALTER TABLE ck_document ADD COLUMN IF NOT EXISTS cls_oid CHAR(36);

-- 10. ck_part / ck_part_iteration：tenant_oid
ALTER TABLE ck_part ADD COLUMN IF NOT EXISTS tenant_oid CHAR(36);
ALTER TABLE ck_part_iteration ADD COLUMN IF NOT EXISTS tenant_oid CHAR(36);

-- 11. ck_part_iteration：IterationEntity 新增字段
ALTER TABLE ck_part_iteration ADD COLUMN IF NOT EXISTS version_sort INTEGER NOT NULL DEFAULT 0;
ALTER TABLE ck_part_iteration ADD COLUMN IF NOT EXISTS branch_id VARCHAR(50);
ALTER TABLE ck_part_iteration ADD COLUMN IF NOT EXISTS delete_mark BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE ck_part_iteration ADD COLUMN IF NOT EXISTS display_version VARCHAR(20);

-- 12. ck_file：source_type / source_url
ALTER TABLE ck_file ADD COLUMN IF NOT EXISTS source_type VARCHAR(10) DEFAULT 'LOCAL';
ALTER TABLE ck_file ADD COLUMN IF NOT EXISTS source_url VARCHAR(2000);

-- 13. ck_attachment：owner_oid
ALTER TABLE IF EXISTS ck_attachment ADD COLUMN IF NOT EXISTS owner_oid CHAR(36);

-- 14. ck_document_iteration：ckfile_oid / lifecycle_template_iteration_oid / IterationEntity 字段
ALTER TABLE ck_document_iteration ADD COLUMN IF NOT EXISTS ckfile_oid CHAR(36);
ALTER TABLE ck_document_iteration ADD COLUMN IF NOT EXISTS lifecycle_template_iteration_oid CHAR(36);
ALTER TABLE ck_document_iteration ADD COLUMN IF NOT EXISTS version_sort INTEGER NOT NULL DEFAULT 0;
ALTER TABLE ck_document_iteration ADD COLUMN IF NOT EXISTS branch_id VARCHAR(50);
ALTER TABLE ck_document_iteration ADD COLUMN IF NOT EXISTS delete_mark BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE ck_document_iteration ADD COLUMN IF NOT EXISTS display_version VARCHAR(20);

-- 15. ck_user_activity：扩展字段
ALTER TABLE ck_user_activity ADD COLUMN IF NOT EXISTS operator_ip VARCHAR(64);
ALTER TABLE ck_user_activity ADD COLUMN IF NOT EXISTS user_agent VARCHAR(512);
ALTER TABLE ck_user_activity ADD COLUMN IF NOT EXISTS result VARCHAR(20);
ALTER TABLE ck_user_activity ADD COLUMN IF NOT EXISTS duration_ms INTEGER;
ALTER TABLE ck_user_activity ADD COLUMN IF NOT EXISTS error_message VARCHAR(500);
ALTER TABLE ck_user_activity ADD COLUMN IF NOT EXISTS detail_json TEXT;

-- 16. ck_file_storage_config：MinIO/跨平台字段
ALTER TABLE ck_file_storage_config ADD COLUMN IF NOT EXISTS endpoint VARCHAR(512);
ALTER TABLE ck_file_storage_config ADD COLUMN IF NOT EXISTS access_key VARCHAR(256);
ALTER TABLE ck_file_storage_config ADD COLUMN IF NOT EXISTS secret_key VARCHAR(256);
ALTER TABLE ck_file_storage_config ADD COLUMN IF NOT EXISTS bucket_name VARCHAR(128);
ALTER TABLE ck_file_storage_config ADD COLUMN IF NOT EXISTS base_url VARCHAR(512);
ALTER TABLE ck_file_storage_config ADD COLUMN IF NOT EXISTS max_capacity_mb INTEGER;
ALTER TABLE ck_file_storage_config ADD COLUMN IF NOT EXISTS alert_threshold_percent INTEGER DEFAULT 80;
ALTER TABLE ck_file_storage_config ADD COLUMN IF NOT EXISTS tenant_oid CHAR(36);
