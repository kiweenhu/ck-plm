/*
 * Copyright (c) 2025 深圳乘恺科技有限公司
 * All rights reserved.
 *
 * @author Kiween.Hu; Roney.Liu
 */

package cn.ck.plm.base.entity;

/**
 * 生命周期状态独立实体对象，参考 Windchill Part 生命周期模型。
 *
 * <p>继承 {@link WithoutVersionEntity}，获得 code / name / description 标准字段及审计能力。
 * 额外定义 displayName 字段，对应数据库 display_name 列，独立于父类 name 字段。
 *
 * <p>使用方式：
 * <ul>
 *   <li>通过 {@code cn.ck.plm.base.service.api.LifecycleStatusService} 进行 CRUD 操作</li>
 *   <li>状态间的跃迁和回退由 {@link LifecycleTemplateIteration} 控制</li>
 *   <li>MasterEntity 子类中通过 {@code LifecycleStatus status} 字段引用</li>
 * </ul>
 */
public class LifecycleStatus extends WithoutVersionEntity implements TenantEntity {

    // ==================== 构造方法 ====================

    public LifecycleStatus() {
    }

    public LifecycleStatus(String code, String name) {
        this.setCode(code);
        this.setName(name);
    }

    // ==================== 自有字段 ====================

    /** 显示名称（对应数据库 display_name 列，用于前端展示） */
    private String displayName;

    /** 租户 oid */
    private String tenantOid;

    // ==================== Getter / Setter ====================

    public String getDisplayName() {
        return displayName;
    }

    public void setDisplayName(String displayName) {
        this.displayName = displayName;
    }

    @Override
    public String getTenantOid() { return tenantOid; }

    @Override
    public void setTenantOid(String tenantOid) { this.tenantOid = tenantOid; }

    // ==================== equals / hashCode / toString ====================

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        LifecycleStatus that = (LifecycleStatus) o;
        String code = getCode();
        return code != null && code.equals(that.getCode());
    }

    @Override
    public int hashCode() {
        String code = getCode();
        return code != null ? code.hashCode() : 0;
    }

    @Override
    public String toString() {
        return "LifecycleStatus{" +
                "code='" + getCode() + '\'' +
                ", name='" + getName() + '\'' +
                ", displayName='" + displayName + '\'' +
                '}';
    }
}
