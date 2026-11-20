/*
 * Copyright (c) 2026~2028 深圳乘恺科技有限公司
 * All rights reserved.
 *
 * @author Kiween.Hu
 */

package cn.ck.plm.ecad.dto;

/**
 * BOM 同步条目 —— Capture Tcl 插件 / 桥接 CLI 上传的单行原理图 BOM。
 *
 * <p>字段对齐 Capture capDB 导出的 BOM 属性：
 * {@code Reference Designator}（位号）、{@code Value}（值/型号）、
 * {@code PCB Footprint}（封装）、{@code Part Number}（PLM 部件编号）。
 */
public class BomSyncItem {

    /** 位号（Reference Designator，如 R1 / U3 / C12-C15 多位号合并计数量） */
    private String reference;

    /** 数量（同一 value/MPN 聚合后的总数量） */
    private Integer quantity;

    /** 值/型号（Capture 的 Value 属性；同步时作为按编号匹配的兜底关键词） */
    private String value;

    /** PLM 部件编号（Capture 的 Part Number 属性；为空则按 value 关键词匹配） */
    private String partNumber;

    /** PCB 封装（Capture 的 PCB Footprint 属性） */
    private String footprint;

    public BomSyncItem() {
    }

    public BomSyncItem(String reference, Integer quantity, String value, String partNumber, String footprint) {
        this.reference = reference;
        this.quantity = quantity;
        this.value = value;
        this.partNumber = partNumber;
        this.footprint = footprint;
    }

    // ==================== Getter / Setter ====================

    public String getReference() { return reference; }
    public void setReference(String reference) { this.reference = reference; }

    public Integer getQuantity() { return quantity; }
    public void setQuantity(Integer quantity) { this.quantity = quantity; }

    public String getValue() { return value; }
    public void setValue(String value) { this.value = value; }

    public String getPartNumber() { return partNumber; }
    public void setPartNumber(String partNumber) { this.partNumber = partNumber; }

    public String getFootprint() { return footprint; }
    public void setFootprint(String footprint) { this.footprint = footprint; }
}
