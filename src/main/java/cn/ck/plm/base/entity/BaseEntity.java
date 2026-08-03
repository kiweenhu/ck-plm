/*
 * Copyright (c) 2025 深圳乘恺科技有限公司
 * All rights reserved.
 *
 * @author Kiween.Hu; Roney.Liu
 */

package cn.ck.plm.base.entity;

import cn.ck.plm.base.service.Persistable;
import com.fasterxml.jackson.annotation.JsonIgnore;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * 实体审计基类 —— 所有持久化实体的公共父类，实现 {@link Persistable} 契约。
 *
 * <p>参考 Windchill BusinessObject 的设计：提供统一的审计字段 + 全局唯一标识，
 * 由 {@link cn.ck.plm.base.config.AuditInterceptor} 在 INSERT/UPDATE 时自动填充。
 *
 * <h3>Windchill 对应</h3>
 * <pre>
 * Persistable  ←  wt.fc.Persistable
 *   ↑ implements
 * BaseEntity   ←  wt.fc.BusinessObject（简化版，无 Replica / Domain / Container 等企业特性）
 * </pre>
 *
 * <h3>继承规则</h3>
 * <ul>
 *   <li>需要持久化到数据库的实体 → 继承本类</li>
 *   <li>纯内存领域对象（如 LifecycleTemplateIteration） → 无需继承</li>
 * </ul>
 *
 * <h3>⚠ 主键规范（必须遵守）</h3>
 * <ul>
 *   <li>{@code oid} 是本项目唯一的持久化主键，对应数据库表 {@code oid CHAR(36) PRIMARY KEY}</li>
 *   <li><b>禁止</b>在任何实体子类中定义 {@code id} 字段（Long/String/Integer 等）</li>
 *   <li>如需业务唯一标识，使用 {@code code} 字段 + 数据库 {@code UNIQUE} 约束</li>
 *   <li>所有 Mapper SQL 的 WHERE 条件统一使用 {@code oid}</li>
 * </ul>
 */
public abstract class BaseEntity implements Persistable {

    /** 全局唯一标识（UUID v4，36 字符），构造时自动生成 */
    private String oid;

    /** 创建时间 */
    private LocalDateTime createdAt;

    /** 创建者 */
    private String creator;

    /** 修改时间 */
    private LocalDateTime updatedAt;

    /** 修改者 */
    private String updater;

    // ==================== 构造方法 ====================

    protected BaseEntity() {
        this.oid = UUID.randomUUID().toString();
    }

    // ==================== Getter / Setter ====================

    public String getOid() {
        return oid;
    }

    public void setOid(String oid) {
        this.oid = oid;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public String getCreator() {
        return creator;
    }

    public void setCreator(String creator) {
        this.creator = creator;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }

    public String getUpdater() {
        return updater;
    }

    public void setUpdater(String updater) {
        this.updater = updater;
    }

    // ==================== 持久化状态 ====================

    @Override
    @JsonIgnore
    public boolean isNew() {
        return createdAt == null;
    }

    @Override
    @JsonIgnore
    public boolean isPersisted() {
        return createdAt != null;
    }
}
