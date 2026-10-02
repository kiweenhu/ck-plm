/*
 * Copyright (c) 2025 深圳乘恺科技有限公司
 * All rights reserved.
 *
 * @author Kiween.Hu; Roney.Liu
 */

package cn.ck.plm.softtype.entity;

import cn.ck.plm.base.entity.BaseEntity;

/**
 * 业务域（BusinessDomain）—— 类型体系的<b>第一层划分</b>，类型树上的域锚点节点。
 *
 * <p>为什么从 {@code TypeDefinition} 拆出来：域与类型不是一类东西 ——
 * <ul>
 *   <li>域没有能力宿主（{@code root_type_code} 对它无意义），不绑编码/版本/生命周期规则；</li>
 *   <li>域是<b>业务的划分</b>（产品主数据域 / 电子设计域 / 结构设计域…），
 *       而类型继承是"对象能力的细化"，二者正交 —— 允许子类型与父类型不同域；</li>
 *   <li>历史上域用 {@code ck_type_definition} 的 DOMAIN 行表示、归属借 {@code parent_oid} 表达，
 *       一列两义，导致"沿父链找不到能力宿主"误报域锚点错误。</li>
 * </ul>
 *
 * <p>域是<b>平台预置</b>数据（{@code source=OOTB}，租户不可自定义），因此本实体只读；
 * 类型通过 {@code TypeDefinition.domainOid} 软引用其 {@code oid}（不加外键）。
 * 与 {@code ck_ecad_domain}（电子域的<b>域实例</b>，供设计项目引用）不是一回事。
 */
public class BusinessDomain extends BaseEntity {

    /** 域 code（沿用原域锚点 code，如 PRODUCT_DATA_DOMAIN / ECAD_DOMAIN / MCAD_DOMAIN） */
    private String code;

    /** 域名称（如「结构设计域」） */
    private String name;

    /** 图标 */
    private String icon;

    /** 描述 */
    private String description;

    /** 排序序号（类型树第一层的展示顺序） */
    private int sortOrder;

    /** 是否启用（停用的域不在类型树上展示，其下类型按需兜底显示） */
    private boolean enabled = true;

    /** 来源：OOTB = 平台预置（域不允许租户自定义） */
    private String source;

    /** 租户 oid（域为平台级，固定平台租户） */
    private String tenantOid;

    /** 域锚点节点 code —— 与 {@code TypeDefinition.typeKind='DOMAIN'} 的历史取值保持一致，前端据此识别域层 */
    public static final String TYPE_KIND = "DOMAIN";

    // ==================== Getter / Setter ====================

    public String getCode() { return code; }
    public void setCode(String code) { this.code = code; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getIcon() { return icon; }
    public void setIcon(String icon) { this.icon = icon; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public int getSortOrder() { return sortOrder; }
    public void setSortOrder(int sortOrder) { this.sortOrder = sortOrder; }

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }

    public String getSource() { return source; }
    public void setSource(String source) { this.source = source; }

    public String getTenantOid() { return tenantOid; }
    public void setTenantOid(String tenantOid) { this.tenantOid = tenantOid; }
}
