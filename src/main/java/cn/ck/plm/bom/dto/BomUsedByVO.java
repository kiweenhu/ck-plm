/*
 * Copyright (c) 2026~2028 深圳乘恺科技有限公司
 * All rights reserved.
 *
 * @author Kiween.Hu; Roney.Liu
 */

package cn.ck.plm.bom.dto;

/**
 * 「被使用情况」（反向 BOM）一行：某个零件被<b>哪个父件</b>用掉多少。
 *
 * <p>为什么不能直接返回 {@code BomLinks}：那个实体上的 {@code parentIterationOid} 是内部标识，
 * 界面上原样显示出来用户读不懂（"这条零件装在哪"才是他要的答案）。这里把父迭代解析成
 * 父件主对象 + 版本，一次请求给全，前端不必拿 oid 再逐行反查。
 *
 * <h3>往上追溯</h3>
 * <p>{@code parentPartOid} 就是"上一层"的身份：用它可以继续调本接口看<b>父件自己</b>被谁使用，
 * 从而沿 where-used 链逐层向上。层级由调用方控制，服务端不做递归（避免深链一次拉全）。
 */
public class BomUsedByVO {

    /** BOM 行 oid（同一父件可能有多行引用同一子件，行才是身份） */
    private String linkOid;

    /** 父件主对象 oid —— 往上追溯下一层的入口 */
    private String parentPartOid;

    /** 父件编码 */
    private String parentNumber;

    /** 父件名称 */
    private String parentName;

    /** 父件版本（如 A.1，取该 BOM 行所属的父迭代） */
    private String parentVersion;

    /** 父件视图 code（Design / Manufacturing …，迭代级） */
    private String parentView;

    /** 父迭代 oid：排错/深链时用，界面不再直接展示 */
    private String parentIterationOid;

    /** 用量 */
    private Double quantity;

    /** 单位 */
    private String unit;

    /** 行号（父件 BOM 里的位置） */
    private Integer lineNumber;

    /**
     * 是否精确引用：true = 该行锁定了子件某个迭代；false = 跟随子件最新版本。
     * 由 {@code childIterationOid} 是否为空折算，前端不必自己判空。
     */
    private Boolean exactReference;

    public String getLinkOid() { return linkOid; }
    public void setLinkOid(String linkOid) { this.linkOid = linkOid; }

    public String getParentPartOid() { return parentPartOid; }
    public void setParentPartOid(String parentPartOid) { this.parentPartOid = parentPartOid; }

    public String getParentNumber() { return parentNumber; }
    public void setParentNumber(String parentNumber) { this.parentNumber = parentNumber; }

    public String getParentName() { return parentName; }
    public void setParentName(String parentName) { this.parentName = parentName; }

    public String getParentVersion() { return parentVersion; }
    public void setParentVersion(String parentVersion) { this.parentVersion = parentVersion; }

    public String getParentView() { return parentView; }
    public void setParentView(String parentView) { this.parentView = parentView; }

    public String getParentIterationOid() { return parentIterationOid; }
    public void setParentIterationOid(String parentIterationOid) { this.parentIterationOid = parentIterationOid; }

    public Double getQuantity() { return quantity; }
    public void setQuantity(Double quantity) { this.quantity = quantity; }

    public String getUnit() { return unit; }
    public void setUnit(String unit) { this.unit = unit; }

    public Integer getLineNumber() { return lineNumber; }
    public void setLineNumber(Integer lineNumber) { this.lineNumber = lineNumber; }

    public Boolean getExactReference() { return exactReference; }
    public void setExactReference(Boolean exactReference) { this.exactReference = exactReference; }
}
