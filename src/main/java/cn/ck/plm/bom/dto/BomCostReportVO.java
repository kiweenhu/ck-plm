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
 * BOM 成本报告（卷积口径）。
 *
 * <pre>
 *   本层直接成本 directCost  = Σ 父件直接下挂各行「用量 × 单位成本」
 *   卷积总成本   totalCost   = Σ 直接下挂各行「用量 × 子件完整单位成本」（子件完整成本已含其所有下级）
 * </pre>
 *
 * <p>报告同时给出明细树（{@link #lines}，每个节点带 extendedCost / rolledUpUnitCost / rolledUpCost），
 * 让"总成本是怎么来的"可以逐层展开核对 —— 只给一个总数、不给来源的成本数字没人敢用。
 */
public class BomCostReportVO {

    /** 计价口径的一版定位（父件迭代 oid） */
    private String parentIterationOid;

    /** 父件编码 */
    private String parentCode;

    /** 父件名称 */
    private String parentName;

    /** 父件版本（displayVersion，如 A.4） */
    private String parentVersion;

    /** 生命周期状态 code */
    private String parentStatus;

    /** 本层直接成本 */
    private Double directCost;

    /** 卷积总成本（含所有下级） */
    private Double totalCost;

    /** 参与计算的行数（含各层） */
    private Integer totalLines;

    /** 未填「单位成本」的行数（按 0 计入 —— 报告里要提示，别让人以为成本就这么低） */
    private Integer missingCostLines;

    /** 明细树（带成本字段，可直接展开核对） */
    private List<BomTreeNode> lines = new ArrayList<>();

    /** 提示（数据缺口 / 层级截断等）；没有问题时为空列表 */
    private List<String> warnings = new ArrayList<>();

    public String getParentIterationOid() { return parentIterationOid; }
    public void setParentIterationOid(String parentIterationOid) { this.parentIterationOid = parentIterationOid; }

    public String getParentCode() { return parentCode; }
    public void setParentCode(String parentCode) { this.parentCode = parentCode; }

    public String getParentName() { return parentName; }
    public void setParentName(String parentName) { this.parentName = parentName; }

    public String getParentVersion() { return parentVersion; }
    public void setParentVersion(String parentVersion) { this.parentVersion = parentVersion; }

    public String getParentStatus() { return parentStatus; }
    public void setParentStatus(String parentStatus) { this.parentStatus = parentStatus; }

    public Double getDirectCost() { return directCost; }
    public void setDirectCost(Double directCost) { this.directCost = directCost; }

    public Double getTotalCost() { return totalCost; }
    public void setTotalCost(Double totalCost) { this.totalCost = totalCost; }

    public Integer getTotalLines() { return totalLines; }
    public void setTotalLines(Integer totalLines) { this.totalLines = totalLines; }

    public Integer getMissingCostLines() { return missingCostLines; }
    public void setMissingCostLines(Integer missingCostLines) { this.missingCostLines = missingCostLines; }

    public List<BomTreeNode> getLines() { return lines; }
    public void setLines(List<BomTreeNode> lines) { this.lines = lines; }

    public List<String> getWarnings() { return warnings; }
    public void setWarnings(List<String> warnings) { this.warnings = warnings; }
}
