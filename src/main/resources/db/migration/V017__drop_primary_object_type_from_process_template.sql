-- =================================================================
--  删除 ck_process_template.primary_object_type（模板级「主业务对象」）。
--
--  背景：一个流程可能关联【多个】业务实体，单一 code 表达不了；
--        该关联改由 ProcessEntitySet（流程关联的业务实体集合）承担，
--        因此模板主档上不再保留这个字段（连同数据库列一起移除，
--        避免留下"字段还在、却没人读写"的死数据）。
--
--  影响面：
--    · 后端：ProcessTemplate 实体 / Mapper 的 INSERT、UPDATE / create、copy / 接口入参
--    · DSL：meta.primaryObjectType 一并移除；存量 DSL 里残留的该键会被 schema 自动丢弃
--           （z.object 默认剥掉未声明键），老模板仍能正常解析与保存
--    · 节点级绑定不受影响：审批节点的 binding.objectType、连线条件的 condition.objectType
--      仍然保留（那是"某个节点用哪个业务对象"，与模板级归属是两件事）
--
--  说明：应用启动时由 ProcessTemplateSchemaInitializer 自动执行同样的清理（幂等）。
--        本脚本用于 DBA 手工库 / 需要显式留痕的场景，可重复执行。
-- =================================================================

ALTER TABLE ck_process_template DROP COLUMN IF EXISTS primary_object_type;
