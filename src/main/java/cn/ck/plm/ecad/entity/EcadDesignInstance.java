/*
 * Copyright (c) 2026~2028 深圳乘恺科技有限公司
 * All rights reserved.
 *
 * @author Kiween.Hu
 */

package cn.ck.plm.ecad.entity;

import cn.ck.plm.base.entity.BaseEntity;
import cn.ck.plm.base.entity.TenantEntity;

/**
 * 设计实例（Design Instance，{@code ck_design_instance}）—— 原理图/PCB 设计里的单行器件。
 *
 * <p>BOM 同步时由 Capture 上传的 BOM 行落地：一个位号（reference）一行，
 * {@code componentOid} 为按编号匹配到的 PLM 部件（匹配不到为 null，留在对账结果里提示补建）。
 */
public class EcadDesignInstance extends BaseEntity implements TenantEntity {

    /** 所属设计 oid（原理图 master oid；design_type=SCHEMATIC） */
    private String designOid;

    /** 设计类型（SCHEMATIC / PCB / SYMBOL / FOOTPRINT） */
    private String designType;

    /** 位号（Reference Designator，如 R1） */
    private String reference;

    /** 匹配到的 PLM 部件 oid（ck_part.oid；未匹配为 null） */
    private String componentOid;

    /** 匹配到的封装 oid（ck_ecad_footprint_ext.oid；未匹配为 null） */
    private String footprintOid;

    /** 匹配到的图符 oid（未匹配为 null） */
    private String symbolOid;

    /** 数量 */
    private Integer quantity;

    /** 值/型号（Capture Value 属性，同步留痕） */
    private String value;

    /** PCB 封装名（Capture PCB Footprint 属性，同步留痕） */
    private String footprintName;

    /** PLM 部件编号（Capture Part Number 属性，同步留痕） */
    private String partNumber;

    /** 租户 oid */
    private String tenantOid;

    // ==================== Getter / Setter ====================

    public String getDesignOid() { return designOid; }
    public void setDesignOid(String designOid) { this.designOid = designOid; }

    public String getDesignType() { return designType; }
    public void setDesignType(String designType) { this.designType = designType; }

    public String getReference() { return reference; }
    public void setReference(String reference) { this.reference = reference; }

    public String getComponentOid() { return componentOid; }
    public void setComponentOid(String componentOid) { this.componentOid = componentOid; }

    public String getFootprintOid() { return footprintOid; }
    public void setFootprintOid(String footprintOid) { this.footprintOid = footprintOid; }

    public String getSymbolOid() { return symbolOid; }
    public void setSymbolOid(String symbolOid) { this.symbolOid = symbolOid; }

    public Integer getQuantity() { return quantity; }
    public void setQuantity(Integer quantity) { this.quantity = quantity; }

    public String getValue() { return value; }
    public void setValue(String value) { this.value = value; }

    public String getFootprintName() { return footprintName; }
    public void setFootprintName(String footprintName) { this.footprintName = footprintName; }

    public String getPartNumber() { return partNumber; }
    public void setPartNumber(String partNumber) { this.partNumber = partNumber; }

    @Override
    public String getTenantOid() { return tenantOid; }

    @Override
    public void setTenantOid(String tenantOid) { this.tenantOid = tenantOid; }
}
