/*
 * Copyright (c) 2025 深圳乘恺科技有限公司
 * All rights reserved.
 *
 * @author Kiween.Hu; Roney.Liu
 */

package cn.ck.plm.softtype.entity;

import cn.ck.plm.base.entity.BaseEntity;
import cn.ck.plm.base.entity.TenantEntity;

/**
 * 可互换属性（IBA）实体 —— 对应 ck_iba 表。
 *
 * <p>Windchill IBA（Interchangeable Attribute）是租户可自定义的属性定义，
 * 可以被分配给多个软类型，并通过映射层进行覆写（required、默认值等）。
 * 不同租户的 IBA 属性定义相互独立。
 *
 * <h3>dataType 枚举</h3>
 * <ul>
 *   <li>STRING  — 字符串</li>
 *   <li>INTEGER — 整数</li>
 *   <li>FLOAT   — 浮点数</li>
 *   <li>BOOLEAN — 布尔值</li>
 *   <li>DATE    — 日期</li>
 *   <li>DATETIME— 日期时间</li>
 *   <li>ENUM    — 枚举（值在 constraints_json 中定义）</li>
 *   <li>URL     — 链接</li>
 * </ul>
 *
 * <h3>主键规范</h3>
 * 继承 {@link BaseEntity#oid} 作为全局唯一主键，
 * {@code code} 为业务唯一键。
 *
 * <h3>多租户</h3>
 * 实现 {@link TenantEntity} 接口，每个租户拥有独立的 IBA 属性定义。
 */
public class IBA extends BaseEntity implements TenantEntity {

    /** 属性编码（业务唯一键，如 WATTAGE / COLOR / MOQ） */
    private String code;

    /** 属性名称 */
    private String name;

    /** 显示名称（UI 展示用） */
    private String displayName;

    /** 数据类型：STRING | INTEGER | FLOAT | BOOLEAN | DATE | DATETIME | ENUM | URL */
    private String dataType;

    /** 默认值 */
    private String defaultValue;

    /**
     * 约束条件 JSON。
     * <pre>
     * 数值: {"min":0,"max":100,"step":1}
     * 枚举: {"enumValues":["A","B","C"]}
     * 正则: {"pattern":"^[A-Z]{3}$"}
     * 长度: {"minLength":1,"maxLength":100}
     * </pre>
     */
    private String constraintsJson;

    /** 是否必填 */
    private boolean required;

    /** 描述 */
    private String description;

    /** 排序序号 */
    private int sortOrder;

    /** 是否启用 */
    private boolean enabled = true;

    /** 租户 oid（多租户隔离） */
    private String tenantOid;

    // ==================== 构造方法 ====================

    public IBA() {
    }

    public IBA(String code, String name, String dataType) {
        this.code = code;
        this.name = name;
        this.dataType = dataType;
    }

    // ==================== Getter / Setter ====================

    public String getCode() {
        return code;
    }

    public void setCode(String code) {
        this.code = code;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getDisplayName() {
        return displayName;
    }

    public void setDisplayName(String displayName) {
        this.displayName = displayName;
    }

    public String getDataType() {
        return dataType;
    }

    public void setDataType(String dataType) {
        this.dataType = dataType;
    }

    public String getDefaultValue() {
        return defaultValue;
    }

    public void setDefaultValue(String defaultValue) {
        this.defaultValue = defaultValue;
    }

    public String getConstraintsJson() {
        return constraintsJson;
    }

    public void setConstraintsJson(String constraintsJson) {
        this.constraintsJson = constraintsJson;
    }

    public boolean isRequired() {
        return required;
    }

    public void setRequired(boolean required) {
        this.required = required;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public int getSortOrder() {
        return sortOrder;
    }

    public void setSortOrder(int sortOrder) {
        this.sortOrder = sortOrder;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    @Override
    public String getTenantOid() {
        return tenantOid;
    }

    @Override
    public void setTenantOid(String tenantOid) {
        this.tenantOid = tenantOid;
    }

    @Override
    public String toString() {
        return "IBA{" +
                "code='" + code + '\'' +
                ", name='" + name + '\'' +
                ", dataType='" + dataType + '\'' +
                ", required=" + required +
                '}';
    }
}
