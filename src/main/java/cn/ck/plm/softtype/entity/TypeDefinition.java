/*
 * Copyright (c) 2025 深圳乘恺科技有限公司
 * All rights reserved.
 *
 * @author Kiween.Hu; Roney.Liu
 */

package cn.ck.plm.softtype.entity;

import cn.ck.plm.base.entity.BaseEntity;
import cn.ck.plm.base.entity.TenantEntity;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/**
 * 类型定义实体 —— 统一 ModelClass（硬类型）和 SoftType（软类型）。
 *
 * <p>通过 {@code typeKind} 区分：
 * <ul>
 *   <li>{@code OOTB} — 系统内置的实体对象（DOCUMENT、PART、PRODUCT、RESOURCE）</li>
 *   <li>{@code SOFT_TYPE} — 基于 OOTB 或另一个 SOFT_TYPE 创建的子类型</li>
 * </ul>
 *
 * <p>层级关系通过 {@code parentOid} 自引用构建：OOTB 类型 parentOid 为 null，
 * SOFT_TYPE 的 parentOid 指向其父类型（OOTB 或另一个 SOFT_TYPE）。
 *
 * <h3>Windchill 对应</h3>
 * <pre>
 * ModelClass  WTDocument / WTPart  →  type_kind = OOTB
 * SoftType    com.myco.Electronic  →  type_kind = SOFT_TYPE, parent_oid → PART
 * IBA         wattage / color      →  ck_iba
 * </pre>
 *
 * <h3>主键规范</h3>
 * 继承 {@link BaseEntity#oid} 作为全局唯一主键，
 * {@code code} 为业务唯一键。
 */
public class TypeDefinition extends BaseEntity implements TenantEntity {

    /** 类型编码（业务唯一键，如 DOCUMENT / PART / ELECTRONIC） */
    private String code;

    /** 类型名称 */
    private String name;

    /** 图标（Ant Design Vue 图标组件名） */
    private String icon;

    /** 来源（OOTB = 系统内置，USER = 用户自定义） */
    private String source;

    /**
     * 类型标志位：
     * {@code OOTB} — 系统内置实体对象（Document、PART 等）
     * {@code SOFT_TYPE} — 基于 OOTB 或 SOFT_TYPE 创建的子类型
     */
    private String typeKind;

    /**
     * 父类型 oid（自引用）：
     * <ul>
     *   <li>OOTB 类型：null</li>
     *   <li>SOFT_TYPE：指向其父类型（OOTB 或另一个 SOFT_TYPE）</li>
     * </ul>
     */
    private String parentOid;

    /** 描述 */
    private String description;

    /** 排序序号 */
    private int sortOrder;

    /** 是否启用 */
    private boolean enabled = true;

    /** 租户 oid（平台租户=默认类型，普通租户=自定义类型） */
    private String tenantOid;

    /**
     * 根 OOTB 内置对象的 code（如 DOCUMENT、PART）。
     * 子类型通过此字段追溯所属的内置对象，用于查询 AttributeDefinition。
     * OOTB 类型自身此字段与 code 相同。
     */
    private String rootTypeCode;

    // ===== 非持久化字段（仅用于查询填充） =====

    /** 子类型列表（树形结构） */
    private List<TypeDefinition> children;

    /** 父类型名称（联表填充） */
    private String parentName;

    /** 关联的 IBA 列表 */
    private List<IBA> ibas;

    /** 关联的 IBA 映射列表（含覆写信息） */
    private List<TypeIBA> ibaMappings;

    // ==================== 类型标志常量 ====================

    public static final String KIND_OOTB = "OOTB";
    public static final String KIND_SOFT_TYPE = "SOFT_TYPE";

    // ==================== 构造方法 ====================

    public TypeDefinition() {
    }

    public TypeDefinition(String code, String name) {
        this.code = code;
        this.name = name;
    }

    public TypeDefinition(String code, String name, String typeKind) {
        this.code = code;
        this.name = name;
        this.typeKind = typeKind;
    }

    // ==================== 便捷方法 ====================

    public boolean isOotb() {
        return KIND_OOTB.equals(typeKind);
    }

    public boolean isSoftType() {
        return KIND_SOFT_TYPE.equals(typeKind);
    }

    public boolean isRoot() {
        return parentOid == null || parentOid.isEmpty();
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

    public String getIcon() {
        return icon;
    }

    public void setIcon(String icon) {
        this.icon = icon;
    }

    @JsonProperty("source")
    public String getSource() {
        return source;
    }

    public void setSource(String source) {
        this.source = source;
    }

    public String getTypeKind() {
        return typeKind;
    }

    public void setTypeKind(String typeKind) {
        this.typeKind = typeKind;
    }

    public String getParentOid() {
        return parentOid;
    }

    public void setParentOid(String parentOid) {
        this.parentOid = parentOid;
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
    public String getTenantOid() { return tenantOid; }

    @Override
    public void setTenantOid(String tenantOid) { this.tenantOid = tenantOid; }

    public String getRootTypeCode() { return rootTypeCode; }

    public void setRootTypeCode(String rootTypeCode) { this.rootTypeCode = rootTypeCode; }

    public List<TypeDefinition> getChildren() {
        return children;
    }

    public void setChildren(List<TypeDefinition> children) {
        this.children = children;
    }

    public String getParentName() {
        return parentName;
    }

    public void setParentName(String parentName) {
        this.parentName = parentName;
    }

    public List<IBA> getIbas() {
        return ibas;
    }

    public void setIbas(List<IBA> ibas) {
        this.ibas = ibas;
    }

    public List<TypeIBA> getIbaMappings() {
        return ibaMappings;
    }

    public void setIbaMappings(List<TypeIBA> ibaMappings) {
        this.ibaMappings = ibaMappings;
    }

    @Override
    public String toString() {
        return "TypeDefinition{" +
                "code='" + code + '\'' +
                ", name='" + name + '\'' +
                ", typeKind='" + typeKind + '\'' +
                ", parentOid='" + parentOid + '\'' +
                ", enabled=" + enabled +
                '}';
    }
}
