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
 * 类型-属性关联实体 —— 对应 ck_type_iba 表。
 *
 * <p>建立类型定义与 IBA 的多对多关联关系，并可在映射层覆写
 * {@code required} 和 {@code defaultValue}。
 *
 * <h3>主键规范</h3>
 * 继承 {@link BaseEntity#oid} 作为全局唯一主键，
 * ({@code typeOid}, {@code ibaOid}) 联合唯一。
 *
 * <h3>多租户</h3>
 * 实现 {@link TenantEntity} 接口，不同租户的类型-IBA 关联相互独立。
 */
public class TypeIBA extends BaseEntity implements TenantEntity {

    /** 所属类型 oid（关联 ck_type_definition.oid） */
    private String typeOid;

    /** 实体编码（如 PRODUCT_LINE / DOCUMENT / PRODUCT_MODEL） */
    private String entityCode;

    /** IBA oid */
    private String ibaOid;

    /** 是否必填（映射层可覆写 IBA 本身的 required） */
    private boolean required;

    /** 默认值（映射层可覆写 IBA 本身的 defaultValue） */
    private String defaultValue;

    /** 排序序号 */
    private int sortOrder;

    /** 租户 oid（多租户隔离） */
    private String tenantOid;

    // ===== 非持久化字段（查询联表填充） =====

    /** IBA 编码 */
    private String ibaCode;

    /** IBA 名称 */
    private String ibaName;

    /** IBA 显示名称 */
    private String ibaDisplayName;

    /** IBA 数据类型 */
    private String ibaDataType;

    /** 来源类型名称（继承属性查询时填充） */
    private String parentTypeName;

    // ===== 非持久化字段：来自 ck_attribute_definition（继承属性查询 JOIN 填充） =====

    /** 属性定义中的显示名称（可能覆盖 IBA 自身的 displayName） */
    private String adDisplayName;

    /** 前端渲染组件 */
    private String uiComponent;

    /** 是否可搜索 */
    private Boolean searchable;

    /** 是否在列表中显示 */
    private Boolean listable;

    /** 是否可编辑 */
    private Boolean editable;

    /** 属性定义的字段名 */
    private String fieldName;

    // ==================== 构造方法 ====================

    public TypeIBA() {
    }

    public TypeIBA(String typeOid, String ibaOid) {
        this.typeOid = typeOid;
        this.ibaOid = ibaOid;
    }

    // ==================== Getter / Setter ====================

    public String getTypeOid() {
        return typeOid;
    }

    public void setTypeOid(String typeOid) {
        this.typeOid = typeOid;
    }

    public String getEntityCode() {
        return entityCode;
    }

    public void setEntityCode(String entityCode) {
        this.entityCode = entityCode;
    }

    public String getIbaOid() {
        return ibaOid;
    }

    public void setIbaOid(String ibaOid) {
        this.ibaOid = ibaOid;
    }

    public boolean isRequired() {
        return required;
    }

    public void setRequired(boolean required) {
        this.required = required;
    }

    public String getDefaultValue() {
        return defaultValue;
    }

    public void setDefaultValue(String defaultValue) {
        this.defaultValue = defaultValue;
    }

    public int getSortOrder() {
        return sortOrder;
    }

    public void setSortOrder(int sortOrder) {
        this.sortOrder = sortOrder;
    }

    @Override
    public String getTenantOid() {
        return tenantOid;
    }

    @Override
    public void setTenantOid(String tenantOid) {
        this.tenantOid = tenantOid;
    }

    public String getIbaCode() {
        return ibaCode;
    }

    public void setIbaCode(String ibaCode) {
        this.ibaCode = ibaCode;
    }

    public String getIbaName() {
        return ibaName;
    }

    public void setIbaName(String ibaName) {
        this.ibaName = ibaName;
    }

    public String getIbaDisplayName() {
        return ibaDisplayName;
    }

    public void setIbaDisplayName(String ibaDisplayName) {
        this.ibaDisplayName = ibaDisplayName;
    }

    public String getIbaDataType() {
        return ibaDataType;
    }

    public void setIbaDataType(String ibaDataType) {
        this.ibaDataType = ibaDataType;
    }

    public String getParentTypeName() {
        return parentTypeName;
    }

    public void setParentTypeName(String parentTypeName) {
        this.parentTypeName = parentTypeName;
    }

    public String getAdDisplayName() {
        return adDisplayName;
    }

    public void setAdDisplayName(String adDisplayName) {
        this.adDisplayName = adDisplayName;
    }

    public String getUiComponent() {
        return uiComponent;
    }

    public void setUiComponent(String uiComponent) {
        this.uiComponent = uiComponent;
    }

    public Boolean getSearchable() {
        return searchable;
    }

    public void setSearchable(Boolean searchable) {
        this.searchable = searchable;
    }

    public Boolean getListable() {
        return listable;
    }

    public void setListable(Boolean listable) {
        this.listable = listable;
    }

    public Boolean getEditable() {
        return editable;
    }

    public void setEditable(Boolean editable) {
        this.editable = editable;
    }

    public String getFieldName() {
        return fieldName;
    }

    public void setFieldName(String fieldName) {
        this.fieldName = fieldName;
    }

    @Override
    public String toString() {
        return "TypeIBA{" +
                "typeOid='" + typeOid + '\'' +
                ", entityCode='" + entityCode + '\'' +
                ", ibaOid='" + ibaOid + '\'' +
                ", required=" + required +
                '}';
    }
}
