-- =================================================================
--  零件-文档关系表（Part-Document Link）
--  参考《一份仿真报告，算不算零件的"定义"？》—— Describes 与 Reference 两分模型
--  link_type 区分 DESCRIBES（定义）/ REFERENCE（参考），分界线画在「挂」的动作上
-- =================================================================

CREATE TABLE IF NOT EXISTS ck_doc_part_link (
    oid                    CHAR(36)     PRIMARY KEY,
    link_type              VARCHAR(32)  NOT NULL,                -- DESCRIBES / REFERENCE
    part_iteration_oid     CHAR(36)     NOT NULL,                -- 挂零件迭代（对齐 BOM 的挂法）
    doc_master_oid         CHAR(36)     NOT NULL,                -- 文档主对象
    doc_iteration_oid      CHAR(36),                            -- NULL = 跟随最新
    resolved_iteration_oid CHAR(36),                            -- 解析缓存，渲染直接 JOIN
    category               VARCHAR(64),                         -- 图纸/规格书/报告（软类型驱动）
    tenant_oid             CHAR(36)     NOT NULL,
    creator                VARCHAR(100),
    created_at             TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updater                VARCHAR(100),
    updated_at             TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_doc_part_link_part_iteration    FOREIGN KEY (part_iteration_oid)      REFERENCES ck_part_iteration(oid) ON DELETE CASCADE,
    CONSTRAINT fk_doc_part_link_doc_master        FOREIGN KEY (doc_master_oid)          REFERENCES ck_document(oid) ON DELETE CASCADE,
    CONSTRAINT fk_doc_part_link_doc_iteration     FOREIGN KEY (doc_iteration_oid)       REFERENCES ck_document_iteration(oid) ON DELETE SET NULL,
    CONSTRAINT fk_doc_part_link_resolved_iteration FOREIGN KEY (resolved_iteration_oid) REFERENCES ck_document_iteration(oid) ON DELETE SET NULL
);

CREATE INDEX IF NOT EXISTS idx_dpl_part_iteration ON ck_doc_part_link(part_iteration_oid, link_type);
CREATE INDEX IF NOT EXISTS idx_dpl_doc_master     ON ck_doc_part_link(doc_master_oid, link_type);
CREATE INDEX IF NOT EXISTS idx_dpl_doc_iteration  ON ck_doc_part_link(doc_iteration_oid);
CREATE INDEX IF NOT EXISTS idx_dpl_tenant         ON ck_doc_part_link(tenant_oid);
