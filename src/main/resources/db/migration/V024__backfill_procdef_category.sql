-- =================================================================
--  V024  流程定义分组（act_re_procdef.category_）回填为「模板分组的名称」
--
--  背景：引擎的 category 默认落在 BPMN 的 targetNamespace（一串 URL，如
--        http://www.ck.com/plm/workflow）—— 那是设计期的 XML 命名空间，不是给人看的分组。
--        新部署已由部署代码显式设置（模板部署取模板分组名；内置流程取固定组名），
--        历史定义需要一次回填。
--
--  迁移口径：
--    1. 能对上流程模板的定义 → 该模板所属分组的【名称】（按 key + 租户 对上，
--       与模板引用分组 oid 的口径一致；分组改名后，下次应用启动会再对齐成新名称）；
--    2. 内置流程 → 固定分组名「内置流程」（内置流程没有模板分组）；
--    3. 对不上模板的定义（例如测试遗留）保持原样 —— 不猜它属于哪个分组。
--
--  说明：应用启动时由 ProcessTemplateSchemaInitializer 自动执行同样的对齐（幂等）。
--        本脚本用于 DBA 手工库 / 需要显式留痕的场景，可重复执行。
--        ⚠️ 手工执行后请重启应用：引擎在内存里缓存了流程定义，不重启的话
--           它的视图仍是回填前的旧值。
-- =================================================================

-- 1. 定义：按 key + 租户 对上模板，取分组名称
UPDATE act_re_procdef d
   SET category_ = c.name
  FROM ck_process_template t
  JOIN ck_process_category c ON c.oid = t.category_oid
 WHERE d.key_ = t.key
   AND d.tenant_id_ = t.tenant_oid
   AND d.category_ IS DISTINCT FROM c.name;

-- 2. 部署：同上（部署上也存了一份 category）
UPDATE act_re_deployment dep
   SET category_ = c.name
  FROM ck_process_template t
  JOIN ck_process_category c ON c.oid = t.category_oid
 WHERE dep.key_ = t.key
   AND dep.tenant_id_ = t.tenant_oid
   AND dep.category_ IS DISTINCT FROM c.name;

-- 3. 内置流程：给固定分组名（key 与 ProcessDeploymentSupport.BUILT_IN_PROCESSES 一致）
UPDATE act_re_procdef
   SET category_ = '内置流程'
 WHERE key_ = 'plm-change-review'
   AND category_ IS DISTINCT FROM '内置流程';

UPDATE act_re_deployment
   SET category_ = '内置流程'
 WHERE id_ IN (SELECT deployment_id_ FROM act_re_procdef WHERE key_ = 'plm-change-review')
   AND category_ IS DISTINCT FROM '内置流程';
