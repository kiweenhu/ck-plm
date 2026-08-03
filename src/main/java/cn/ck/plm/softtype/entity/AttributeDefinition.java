/*
 * Copyright (c) 2025 深圳乘恺科技有限公司
 * All rights reserved.
 *
 * @author Kiween.Hu; Roney.Liu
 */

package cn.ck.plm.softtype.entity;

import cn.ck.plm.base.entity.BaseEntity;

/**
 * 实体属性定义 —— 对应 ck_attribute_definition 表。
 *
 * <p>将实体（ModelClass / SoftType）的所有属性（系统内置 + IBA 扩展）
 * 统一注册到数据库，支持通过配置驱动 CRUD UI 布局。
 *
 * <h3>source 类型</h3>
 * <ul>
 *   <li>SYSTEM —— 实体类自身字段（code、name、description 等），由初始化脚本注入</li>
 *   <li>IBA —— 通过 ck_type_iba 动态分配的扩展属性</li>
 * </ul>
 *
 * <h3>UI 驱动字段</h3>
 * <ul>
 *   <li>searchable —— 是否出现在搜索/过滤器中</li>
 *   <li>listable —— 是否出现在表格列中</li>
 *   <li>editable —— 是否在表单中可编辑</li>
 *   <li>uiComponent —— 前端渲染组件：input / textarea / select / switch / datepicker / input-number</li>
 * </ul>
 */
public class AttributeDefinition extends BaseEntity {

    /** 实体名称：ModelClass / SoftType */
    private String entityName;

    /** 字段名（entity 属性名）：code / name / description */
    private String fieldName;

    /** 显示名称（UI 展示）：编码 / 名称 / 描述 */
    private String displayName;

    /** 数据类型 */
    private String dataType;

    /** 来源：SYSTEM / IBA */
    private String source;

    /** 若 source=IBA，关联 ck_iba.oid */
    private String ibaOid;

    /** 是否必填 */
    private boolean required;

    /** 是否出现在搜索过滤器中 */
    private boolean searchable;

    /** 是否出现在列表/表格列中 */
    private boolean listable;

    /** 是否在表单中可编辑 */
    private boolean editable;

    /** 前端渲染组件：input / textarea / select / switch / datepicker / input-number */
    private String uiComponent;

    /** 默认值 */
    private String defaultValue;

    /** 约束条件 JSON */
    private String constraintsJson;

    /** 排序序号 */
    private int sortOrder;

    /** 是否启用 */
    private boolean enabled = true;

    // ==================== 构造方法 ====================

    public AttributeDefinition() {
    }

    public AttributeDefinition(String entityName, String fieldName, String displayName, String dataType) {
        this.entityName = entityName;
        this.fieldName = fieldName;
        this.displayName = displayName;
        this.dataType = dataType;
    }

    // ==================== Getter / Setter ====================

    public String getEntityName() { return entityName; }
    public void setEntityName(String entityName) { this.entityName = entityName; }

    public String getFieldName() { return fieldName; }
    public void setFieldName(String fieldName) { this.fieldName = fieldName; }

    public String getDisplayName() { return displayName; }
    public void setDisplayName(String displayName) { this.displayName = displayName; }

    public String getDataType() { return dataType; }
    public void setDataType(String dataType) { this.dataType = dataType; }

    public String getSource() { return source; }
    public void setSource(String source) { this.source = source; }

    public String getIbaOid() { return ibaOid; }
    public void setIbaOid(String ibaOid) { this.ibaOid = ibaOid; }

    public boolean isRequired() { return required; }
    public void setRequired(boolean required) { this.required = required; }

    public boolean isSearchable() { return searchable; }
    public void setSearchable(boolean searchable) { this.searchable = searchable; }

    public boolean isListable() { return listable; }
    public void setListable(boolean listable) { this.listable = listable; }

    public boolean isEditable() { return editable; }
    public void setEditable(boolean editable) { this.editable = editable; }

    public String getUiComponent() { return uiComponent; }
    public void setUiComponent(String uiComponent) { this.uiComponent = uiComponent; }

    public String getDefaultValue() { return defaultValue; }
    public void setDefaultValue(String defaultValue) { this.defaultValue = defaultValue; }

    public String getConstraintsJson() { return constraintsJson; }
    public void setConstraintsJson(String constraintsJson) { this.constraintsJson = constraintsJson; }

    public int getSortOrder() { return sortOrder; }
    public void setSortOrder(int sortOrder) { this.sortOrder = sortOrder; }

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }

    @Override
    public String toString() {
        return "AttributeDefinition{" +
                "entityName='" + entityName + '\'' +
                ", fieldName='" + fieldName + '\'' +
                ", displayName='" + displayName + '\'' +
                ", source='" + source + '\'' +
                '}';
    }
}
