-- =================================================================
--  「类型-生命周期状态-流程模板」关联：配置粒度由「模板 code」改为「模板子版本 oid」。
--
--  背景：V020 之后本表记的是 lifecycle_template_code（不带版本）。但业务对象迭代固化的是
--        ck_part_iteration.lifecycle_template_iteration_oid —— 即实例"出生"时用的是哪一版模板。
--        配置挂在 code 上，运行期拿着实例自带的子版本 oid 无法还原"当时那一版配置"，
--        改配置还会追溯性改写所有在途实例的行为。
--        故改为与迭代同层的 lifecycle_template_iteration_oid。
--
--  迁移口径：老行按 code 落到"该模板的最新子版本"——老语义本就是"按模板当前版本生效"，
--           这是唯一无损的对应关系；模板已被删除、解析不到子版本的行丢弃。
--
--  配套：模板每次编辑生成新子版本时，由 LifecycleTemplateService 回调
--        TypeLifecycleStateProcessService#inherit 把配置继承过去（只保留仍存在的状态），
--        旧子版本的行保留 —— 在途实例固化的是旧子版本，仍要能解析出当时那一版配置。
--
--  说明：应用启动时由 TypeLifecycleStateProcessLinkTableInitializer 自动执行同样的迁移（幂等）。
--        本脚本用于 DBA 手工库 / 需要显式留痕的场景，可重复执行。
-- =================================================================

-- 1. 新列（全新库由建表语句直接给出新结构，这里是老库补列）
ALTER TABLE ck_type_lifecycle_state_process_link
    ADD COLUMN IF NOT EXISTS lifecycle_template_iteration_oid CHAR(36);

-- 2. 老数据迁移（仅当 lifecycle_template_code 列还在时执行）
DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM information_schema.columns
               WHERE table_name = 'ck_type_lifecycle_state_process_link'
                 AND column_name = 'lifecycle_template_code') THEN
        -- 2.1 code → 该模板的最新子版本 oid
        UPDATE ck_type_lifecycle_state_process_link
           SET lifecycle_template_iteration_oid = i.oid
          FROM ck_lifecycle_template_iteration i
          JOIN ck_lifecycle_template m ON m.oid = i.master_oid
         WHERE m.code = ck_type_lifecycle_state_process_link.lifecycle_template_code
           AND i.latest = TRUE
           AND ck_type_lifecycle_state_process_link.lifecycle_template_iteration_oid IS NULL;

        -- 2.2 解析不到子版本（模板已删）的行丢弃，否则新列的 NOT NULL 无法成立
        DELETE FROM ck_type_lifecycle_state_process_link
         WHERE lifecycle_template_iteration_oid IS NULL;

        -- 2.3 老列退役（依赖它的唯一索引随之消失；索引名若已被改到新名，这里显式兜底）
        ALTER TABLE ck_type_lifecycle_state_process_link DROP COLUMN lifecycle_template_code;
        DROP INDEX IF EXISTS uk_tlspl_type_iteration_status;
    END IF;
END $$;

DROP INDEX IF EXISTS uk_tlspl_type_template_status;

-- 3. 新唯一键：类型 + 子版本 + 状态
CREATE UNIQUE INDEX IF NOT EXISTS uk_tlspl_type_iteration_status
    ON ck_type_lifecycle_state_process_link (type_oid, lifecycle_template_iteration_oid, status_code);
