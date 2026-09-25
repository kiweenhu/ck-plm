/*
 * Copyright (c) 2025 深圳乘恺科技有限公司
 * All rights reserved.
 *
 * @author Kiween.Hu; Roney.Liu
 */

package cn.ck.plm.home.dto;

import java.time.LocalDateTime;

/**
 * 「我最近创建 / 修改过的对象」视图 —— 个人中心用。
 *
 * <pre>
 * 前端取值：o.oid / o.name / o.code / o.entityType / o.entityTypeName
 *          o.displayVersion / o.touchType / o.createdAt / o.updatedAt / o.touchedAt / o.linkPath
 * touchType：CREATED 创建 / UPDATED 修改 / CREATED_UPDATED 创建后又改过
 * </pre>
 *
 * <h3>口径说明（重要）</h3>
 * <p>数据来自对象表自身的 {@code creator/created_at/updater/updated_at}，即
 * <b>"最后状态"口径</b>：只能回答"这个对象最近一次被谁在什么时候创建/改动"，
 * <b>不能</b>回答"历史上我改过它" —— 若我改完之后别人又改了，这里就看不到我的痕迹。
 * 真正的"谁改了什么"需要审计表（当前系统没有）。
 *
 * <p>另外：<b>修改只体现在版本表</b>（{@code ck_*_iteration}）—— 主表（{@code ck_part} 等）
 * 的 {@code updated_at} 在编辑/检入时并不变动，拿主表查会得出"没改过"的错误结论。
 */
public class RecentObjectVO {

    /** 对象主 oid */
    private String oid;

    /** 对象名称 */
    private String name;

    /** 对象编码 */
    private String code;

    /** 实体类型（PART / DOCUMENT / ENG_DOCUMENT） */
    private String entityType;

    /** 实体类型中文名 */
    private String entityTypeName;

    /** 显示版本，如 A.1 */
    private String displayVersion;

    /** 变更性质：CREATED / UPDATED / CREATED_UPDATED */
    private String touchType;

    /** 创建时间 */
    private LocalDateTime createdAt;

    /** 最后修改时间 */
    private LocalDateTime updatedAt;

    /** 本次"与我相关"的时间点（创建与修改里更近的那个），列表按它倒序 */
    private LocalDateTime touchedAt;

    /** 跳转路径（与检出列表同一套） */
    private String linkPath;

    // ==================== Getter / Setter ====================

    public String getOid() { return oid; }
    public void setOid(String oid) { this.oid = oid; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getCode() { return code; }
    public void setCode(String code) { this.code = code; }

    public String getEntityType() { return entityType; }
    public void setEntityType(String entityType) { this.entityType = entityType; }

    public String getEntityTypeName() { return entityTypeName; }
    public void setEntityTypeName(String entityTypeName) { this.entityTypeName = entityTypeName; }

    public String getDisplayVersion() { return displayVersion; }
    public void setDisplayVersion(String displayVersion) { this.displayVersion = displayVersion; }

    public String getTouchType() { return touchType; }
    public void setTouchType(String touchType) { this.touchType = touchType; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }

    public LocalDateTime getTouchedAt() { return touchedAt; }
    public void setTouchedAt(LocalDateTime touchedAt) { this.touchedAt = touchedAt; }

    public String getLinkPath() { return linkPath; }
    public void setLinkPath(String linkPath) { this.linkPath = linkPath; }
}
