/*
 * Copyright (c) 2025 深圳乘恺科技有限公司
 * All rights reserved.
 *
 * @author Kiween.Hu; Roney.Liu
 */

package cn.ck.plm.resource.entity;

import cn.ck.plm.base.entity.BaseEntity;
import cn.ck.plm.base.entity.TenantEntity;

/**
 * 电子元器件，企业资源库-电子元器件库的库存物料主数据。
 *
 * <p>管理电阻、电容、IC、连接器等元器件的型号规格、封装、关键参数、
 * 厂商、库存与成本信息，供产品 BOM 引用。
 */
public class ElectronicComponent extends BaseEntity implements TenantEntity {

    /** 元器件编码（业务唯一标识，为空时后端自动生成） */
    private String code;

    /** 元器件名称 */
    private String name;

    /** 所属分类 oid（关联 ck_component_category.oid，可空 = 未分类） */
    private String categoryOid;

    /** 型号规格（如 STM32F103C8T6） */
    private String model;

    /** 封装（如 0805、LQFP48） */
    private String packageType;

    /** 关键参数/值（如 10KΩ、100nF/16V） */
    private String valueSpec;

    /** 厂商（如 Yageo、TI） */
    private String manufacturer;

    /** 库存数量 */
    private Integer stockQty;

    /** 安全库存（低于该值预警） */
    private Integer safeStockQty;

    /** 单位 */
    private String unit;

    /** 单位成本 */
    private Double unitCost;

    /** 描述 */
    private String description;

    /** 租户 oid */
    private String tenantOid;

    public String getCode() { return code; }
    public void setCode(String code) { this.code = code; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getCategoryOid() { return categoryOid; }
    public void setCategoryOid(String categoryOid) { this.categoryOid = categoryOid; }

    public String getModel() { return model; }
    public void setModel(String model) { this.model = model; }

    public String getPackageType() { return packageType; }
    public void setPackageType(String packageType) { this.packageType = packageType; }

    public String getValueSpec() { return valueSpec; }
    public void setValueSpec(String valueSpec) { this.valueSpec = valueSpec; }

    public String getManufacturer() { return manufacturer; }
    public void setManufacturer(String manufacturer) { this.manufacturer = manufacturer; }

    public Integer getStockQty() { return stockQty; }
    public void setStockQty(Integer stockQty) { this.stockQty = stockQty; }

    public Integer getSafeStockQty() { return safeStockQty; }
    public void setSafeStockQty(Integer safeStockQty) { this.safeStockQty = safeStockQty; }

    public String getUnit() { return unit; }
    public void setUnit(String unit) { this.unit = unit; }

    public Double getUnitCost() { return unitCost; }
    public void setUnitCost(Double unitCost) { this.unitCost = unitCost; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    @Override
    public String getTenantOid() { return tenantOid; }

    @Override
    public void setTenantOid(String tenantOid) { this.tenantOid = tenantOid; }
}
