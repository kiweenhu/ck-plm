/*
 * Copyright (c) 2025 深圳乘恺科技有限公司
 * All rights reserved.
 *
 * @author Kiween.Hu; Roney.Liu
 */

package cn.ck.plm.iam.entity;

import cn.ck.plm.base.entity.BaseEntity;
import java.time.LocalDateTime;

/**
 * 通知实体 —— 对应 ck_notification 表。
 *
 * <p>系统管理员审核租户注册等场景使用，目标用户登录后可见未读通知数。
 * 本表为共享表，不按租户隔离（所有管理员可见系统级通知）。
 */
public class Notification extends BaseEntity {

    /** 目标用户 oid */
    private String userOid;

    /** 通知标题 */
    private String title;

    /** 通知内容 */
    private String content;

    /** 通知类型：TENANT_REGISTRATION / SYSTEM / APPROVAL */
    private String type;

    /** 关联目标类型（如 TENANT） */
    private String targetType;

    /** 关联目标 oid */
    private String targetOid;

    /** 是否已读 */
    private boolean isRead;

    // ==================== 构造方法 ====================

    public Notification() {
        super();
        this.isRead = false;
    }

    // ==================== Getter / Setter ====================

    public String getUserOid() { return userOid; }
    public void setUserOid(String userOid) { this.userOid = userOid; }

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }

    public String getContent() { return content; }
    public void setContent(String content) { this.content = content; }

    public String getType() { return type; }
    public void setType(String type) { this.type = type; }

    public String getTargetType() { return targetType; }
    public void setTargetType(String targetType) { this.targetType = targetType; }

    public String getTargetOid() { return targetOid; }
    public void setTargetOid(String targetOid) { this.targetOid = targetOid; }

    public boolean isRead() { return isRead; }
    public void setRead(boolean read) { isRead = read; }
}
