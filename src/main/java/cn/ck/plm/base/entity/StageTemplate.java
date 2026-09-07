/*
 * Copyright (c) 2025 深圳乘恺科技有限公司
 * All rights reserved.
 */

package cn.ck.plm.base.entity;

/**
 * 研发阶段模板实体。
 *
 * <p>支持多套行业模板（TRADITIONAL/IPD/MILITARY/AUTOMOTIVE 等），通过 {@code industry} 区分。
 * 每个阶段可声明管理的业务语义对象类型（{@code managedObjectTypes}），如 FUNCTIONAL/PART/DOCUMENT。
 */
public class StageTemplate extends WithoutVersionEntity implements TenantEntity {

    private String icon;
    private String color;
    private Integer sortOrder;
    private String defaultFolders;
    private String tenantOid;

    /** 行业/场景标识：TRADITIONAL（传统）/ IPD（电子高科）/ MILITARY（军工）/ AUTOMOTIVE（汽车） */
    private String industry;

    /** 阶段管理的业务语义对象类型（JSON 数组），如 ["FUNCTIONAL","PART","DOCUMENT"] */
    private String managedObjectTypes;

    public StageTemplate() {}

    public StageTemplate(String code, String name) {
        setCode(code);
        setName(name);
    }

    public String getIcon() { return icon; }
    public void setIcon(String icon) { this.icon = icon; }

    public String getColor() { return color; }
    public void setColor(String color) { this.color = color; }

    public Integer getSortOrder() { return sortOrder; }
    public void setSortOrder(Integer sortOrder) { this.sortOrder = sortOrder; }

    public String getDefaultFolders() { return defaultFolders; }
    public void setDefaultFolders(String defaultFolders) { this.defaultFolders = defaultFolders; }

    @Override
    public String getTenantOid() { return tenantOid; }

    @Override
    public void setTenantOid(String tenantOid) { this.tenantOid = tenantOid; }

    public String getIndustry() { return industry; }
    public void setIndustry(String industry) { this.industry = industry; }

    public String getManagedObjectTypes() { return managedObjectTypes; }
    public void setManagedObjectTypes(String managedObjectTypes) { this.managedObjectTypes = managedObjectTypes; }
}