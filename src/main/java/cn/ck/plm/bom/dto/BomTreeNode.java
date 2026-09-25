/*
 * Copyright (c) 2025 深圳乘恺科技有限公司
 * All rights reserved.
 *
 * @author Kiween.Hu; Roney.Liu
 */

package cn.ck.plm.bom.dto;

import java.util.ArrayList;
import java.util.List;

/**
 * BOM 树节点（递归 BOM 结构树的 DTO）。
 *
 * <p>将 BomLinks 的行级属性与子件的展示信息（名称/编码/版本/视图/状态/检出）合并，
 * 并携带 children 递归子节点，供前端一次性渲染完整多层 BOM 树，避免 N+1 查询。
 */
public class BomTreeNode {

    // ==================== BomLinks 行级属性 ====================

    /** BOM 行 oid（BomLinks.oid） */
    private String oid;

    /** 替代关系编码（继承自 WithoutVersionEntity） */
    private String code;

    /** 名称 */
    private String name;

    /** 描述 */
    private String description;

    /** 父迭代 oid */
    private String parentIterationOid;

    /** 子件主对象 oid */
    private String childPartOid;

    /** 子件精确迭代 oid（可空，NULL = 跟随最新） */
    private String childIterationOid;

    /** 非精确引用的解析缓存 */
    private String resolvedIterationOid;

    /** 用量 */
    private Double quantity;

    /** 单位 */
    private String unit;

    /** 行号 */
    private Integer lineNumber;

    /** 单位成本 */
    private Double unitCost;

    // ==================== 成本（卷积结果，由 BomCostCalculator 写入）====================

    /**
     * 本行<b>本层金额</b>：用量 × 单位成本（不含下级）。
     *
     * <p>只有跑成本报告时才写；BOM 结构树接口不带它（不需要，也不该多算一遍）。
     */
    private Double extendedCost;

    /**
     * 该子件的<b>完整单位成本</b>：它自己的单位成本 + 它下挂各行金额之和。
     *
     * <p>父件算总成本时用的是这一个字段（再乘用量），所以它必须已经包含下级。
     */
    private Double rolledUpUnitCost;

    /** 本行<b>累计金额</b>：用量 × 完整单位成本（含全部下级） */
    private Double rolledUpCost;

    // ==================== 子件展示信息 ====================

    /** 子件名称 */
    private String childPartName;

    /** 子件编码 */
    private String childPartNumber;

    /** 子件版本（displayVersion，如 A.1） */
    private String childVersion;

    /** 子件视图 code */
    private String childView;

    /** 子件生命周期状态 code */
    private String childStatus;

    /** 子件是否已检出 */
    private Boolean childCheckedOut;

    /** 子件检出人 */
    private String childCheckedOutBy;

    /** 子件自身的最新迭代 oid（用于递归加载孙件 / 在子件下添加孙件） */
    private String childLatestIterationOid;

    // ==================== 子节点 ====================

    /** 子节点（子件的子件） */
    private List<BomTreeNode> children;

    // ==================== Getter / Setter ====================

    public String getOid() { return oid; }
    public void setOid(String oid) { this.oid = oid; }

    public String getCode() { return code; }
    public void setCode(String code) { this.code = code; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public String getParentIterationOid() { return parentIterationOid; }
    public void setParentIterationOid(String parentIterationOid) { this.parentIterationOid = parentIterationOid; }

    public String getChildPartOid() { return childPartOid; }
    public void setChildPartOid(String childPartOid) { this.childPartOid = childPartOid; }

    public String getChildIterationOid() { return childIterationOid; }
    public void setChildIterationOid(String childIterationOid) { this.childIterationOid = childIterationOid; }

    public String getResolvedIterationOid() { return resolvedIterationOid; }
    public void setResolvedIterationOid(String resolvedIterationOid) { this.resolvedIterationOid = resolvedIterationOid; }

    public Double getQuantity() { return quantity; }
    public void setQuantity(Double quantity) { this.quantity = quantity; }

    public String getUnit() { return unit; }
    public void setUnit(String unit) { this.unit = unit; }

    public Integer getLineNumber() { return lineNumber; }
    public void setLineNumber(Integer lineNumber) { this.lineNumber = lineNumber; }

    public Double getUnitCost() { return unitCost; }
    public void setUnitCost(Double unitCost) { this.unitCost = unitCost; }

    public Double getExtendedCost() { return extendedCost; }
    public void setExtendedCost(Double extendedCost) { this.extendedCost = extendedCost; }

    public Double getRolledUpUnitCost() { return rolledUpUnitCost; }
    public void setRolledUpUnitCost(Double rolledUpUnitCost) { this.rolledUpUnitCost = rolledUpUnitCost; }

    public Double getRolledUpCost() { return rolledUpCost; }
    public void setRolledUpCost(Double rolledUpCost) { this.rolledUpCost = rolledUpCost; }

    public String getChildPartName() { return childPartName; }
    public void setChildPartName(String childPartName) { this.childPartName = childPartName; }

    public String getChildPartNumber() { return childPartNumber; }
    public void setChildPartNumber(String childPartNumber) { this.childPartNumber = childPartNumber; }

    public String getChildVersion() { return childVersion; }
    public void setChildVersion(String childVersion) { this.childVersion = childVersion; }

    public String getChildView() { return childView; }
    public void setChildView(String childView) { this.childView = childView; }

    public String getChildStatus() { return childStatus; }
    public void setChildStatus(String childStatus) { this.childStatus = childStatus; }

    public Boolean getChildCheckedOut() { return childCheckedOut; }
    public void setChildCheckedOut(Boolean childCheckedOut) { this.childCheckedOut = childCheckedOut; }

    public String getChildCheckedOutBy() { return childCheckedOutBy; }
    public void setChildCheckedOutBy(String childCheckedOutBy) { this.childCheckedOutBy = childCheckedOutBy; }

    public String getChildLatestIterationOid() { return childLatestIterationOid; }
    public void setChildLatestIterationOid(String childLatestIterationOid) { this.childLatestIterationOid = childLatestIterationOid; }

    public List<BomTreeNode> getChildren() { return children; }
    public void setChildren(List<BomTreeNode> children) { this.children = children; }

    /** 添加子节点 */
    public void addChild(BomTreeNode child) {
        if (children == null) {
            children = new ArrayList<>();
        }
        children.add(child);
    }
}
