-- =================================================================
--  「生命周期状态 → 流程模板」绑定表（1:1，挂生命周期模板的【子版本】）。
--
--  ⚠️ 本脚本记录的是本表的【首版结构】，随后经历两次改造：
--     V020：主键补上类型维度（type_oid + lifecycle_template_code + status_code），
--           去掉 iteration_oid —— 因为同一生命周期模板会被多个类型复用，语义必须带类型；
--     V021：更名 ck_type_lifecycle_state_process_link（与同族 xxx_link 命名对齐）+ 索引改名。
--     新库请按 V019 → V020 → V021 顺序执行（后两者会把这里的结构迁移过去）；
--     也可以直接以 V021 末尾的最终结构建表（见 schema.sql）。
--
--  背景：业务配置 → 模型定义 → 规则绑定 → 生命周期模板里，需要为每个状态指定
--        「该状态用哪个流程模板」。之前没有这个能力，生命周期状态与流程模板相互孤立。
--
--  为什么挂子版本（iteration）而不是模板（master）：
--    业务对象的 iteration 在创建时就把生命周期模板的子版本 oid 固化下来了
--    （DefaultLifecycleTemplateService#initLifecycle 写 lifecycle_template_iteration_oid），
--    所以绑定挂子版本天然"按版本生效" —— 改生命周期模板不影响在途实例当初用的那一版。
--
--  1:1 由唯一索引 uk_lsp_iteration_status 保证（将来要 1:N 去掉它即可，表结构不变）。
--
--  process_template_oid 是【跨模块软引用】（指向 ck_process_template.oid）：process 模块在
--  base 之上，故不加外键；悬空引用由"删除流程模板前检查引用"兜底。
--
--  说明：应用启动时由 LifecycleStateProcessTableInitializer 自动建表（幂等）。
--        本脚本用于 DBA 手工库 / 需要显式留痕的场景，可重复执行。
-- =================================================================

CREATE TABLE IF NOT EXISTS ck_lifecycle_state_process (
    oid                  CHAR(36)     PRIMARY KEY,
    iteration_oid        CHAR(36)     NOT NULL
                         REFERENCES ck_lifecycle_template_iteration(oid) ON DELETE CASCADE,
    status_code          VARCHAR(50)  NOT NULL,
    process_template_oid VARCHAR(64)  NOT NULL,
    tenant_oid           CHAR(36),
    creator              VARCHAR(128),
    created_at           TIMESTAMP,
    updater              VARCHAR(128),
    updated_at           TIMESTAMP
);

-- 1:1：一个状态至多一个流程模板
CREATE UNIQUE INDEX IF NOT EXISTS uk_lsp_iteration_status
    ON ck_lifecycle_state_process (iteration_oid, status_code);
-- 删除流程模板前的引用检查
CREATE INDEX IF NOT EXISTS idx_lsp_process ON ck_lifecycle_state_process (process_template_oid);
CREATE INDEX IF NOT EXISTS idx_lsp_tenant  ON ck_lifecycle_state_process (tenant_oid);
