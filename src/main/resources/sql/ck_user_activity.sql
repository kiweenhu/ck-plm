CREATE TABLE IF NOT EXISTS ck_user_activity (
    oid            VARCHAR(64)  PRIMARY KEY,
    user_oid       VARCHAR(64)  NOT NULL,
    activity_type  VARCHAR(20)  NOT NULL,  -- ACCESS / OPERATION
    target_name    VARCHAR(255),
    target_type    VARCHAR(64),             -- 产品系列 / 文档 / 变更单 ...
    target_path    VARCHAR(512),
    action_desc    VARCHAR(255),            -- 操作描述（仅 OPERATION）
    creator        VARCHAR(64),
    created_at     TIMESTAMP,
    updater        VARCHAR(64),
    updated_at     TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_activity_user_type ON ck_user_activity(user_oid, activity_type, created_at DESC);
