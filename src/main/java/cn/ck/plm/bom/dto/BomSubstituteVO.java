/*
 * Copyright (c) 2026~2028 深圳乘恺科技有限公司
 * All rights reserved.
 *
 * @author Kiween.Hu; Roney.Liu
 */

package cn.ck.plm.bom.dto;

/**
 * 局部替代（BOM 行替代）视图对象 —— 替代关系 + 两端的展示信息。
 *
 * <p>为什么要 VO 而不是直接把实体丢给前端：设置替代件时用户选的是"某个零件"，
 * 之后回看列表只看到一串 uuid 没有意义 —— 必须带上替代件的编码/名称/类型/版本，
 * 以及被替代的子件是谁。这些都要 join {@code ck_part} 才有。
 */
public class BomSubstituteVO {

    /** 替代关系 oid */
    private String oid;

    /** 父 BOM 行 oid（局部替代的挂载点） */
    private String bomLinkOid;

    /** 原子部件（被替代方）主对象 oid */
    private String sourcePartOid;

    /** 替代件主对象 oid */
    private String substitutePartOid;

    /** 替代类型：EQUIVALENT / COMPLETE / PARTIAL / SUBSTITUTE */
    private String substituteType;

    /** 替代数量因子（1 个原子部件 = N 个替代件） */
    private Double substituteQuantity;

    /** 替代单位 */
    private String substituteUnit;

    /** 优先级（越小越优先） */
    private Integer priority;

    /** 是否启用 */
    private Boolean enabled;

    /** 说明 */
    private String description;

    // ==================== 替代件展示信息（join ck_part）====================

    private String substitutePartNumber;
    private String substitutePartName;
    private String substitutePartType;

    /** 替代件当前最新版本的显示版本（如 A.1）；由服务层补，mapper 里不查子查询（见实现说明） */
    private String substituteVersion;

    // ==================== 原子部件展示信息（被替代的子件）====================

    private String sourcePartNumber;
    private String sourcePartName;

    public String getOid() { return oid; }
    public void setOid(String oid) { this.oid = oid; }

    public String getBomLinkOid() { return bomLinkOid; }
    public void setBomLinkOid(String bomLinkOid) { this.bomLinkOid = bomLinkOid; }

    public String getSourcePartOid() { return sourcePartOid; }
    public void setSourcePartOid(String sourcePartOid) { this.sourcePartOid = sourcePartOid; }

    public String getSubstitutePartOid() { return substitutePartOid; }
    public void setSubstitutePartOid(String substitutePartOid) { this.substitutePartOid = substitutePartOid; }

    public String getSubstituteType() { return substituteType; }
    public void setSubstituteType(String substituteType) { this.substituteType = substituteType; }

    public Double getSubstituteQuantity() { return substituteQuantity; }
    public void setSubstituteQuantity(Double substituteQuantity) { this.substituteQuantity = substituteQuantity; }

    public String getSubstituteUnit() { return substituteUnit; }
    public void setSubstituteUnit(String substituteUnit) { this.substituteUnit = substituteUnit; }

    public Integer getPriority() { return priority; }
    public void setPriority(Integer priority) { this.priority = priority; }

    public Boolean getEnabled() { return enabled; }
    public void setEnabled(Boolean enabled) { this.enabled = enabled; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public String getSubstitutePartNumber() { return substitutePartNumber; }
    public void setSubstitutePartNumber(String substitutePartNumber) { this.substitutePartNumber = substitutePartNumber; }

    public String getSubstitutePartName() { return substitutePartName; }
    public void setSubstitutePartName(String substitutePartName) { this.substitutePartName = substitutePartName; }

    public String getSubstitutePartType() { return substitutePartType; }
    public void setSubstitutePartType(String substitutePartType) { this.substitutePartType = substitutePartType; }

    public String getSubstituteVersion() { return substituteVersion; }
    public void setSubstituteVersion(String substituteVersion) { this.substituteVersion = substituteVersion; }

    public String getSourcePartNumber() { return sourcePartNumber; }
    public void setSourcePartNumber(String sourcePartNumber) { this.sourcePartNumber = sourcePartNumber; }

    public String getSourcePartName() { return sourcePartName; }
    public void setSourcePartName(String sourcePartName) { this.sourcePartName = sourcePartName; }
}
