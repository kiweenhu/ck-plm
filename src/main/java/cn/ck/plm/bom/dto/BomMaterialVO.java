/*
 * Copyright (c) 2026~2028 深圳乘恺科技有限公司
 * All rights reserved.
 *
 * @author Kiween.Hu; Roney.Liu
 */

package cn.ck.plm.bom.dto;

/**
 * 「物料清单」一行：某容器（产品系列 / 型号）下的<b>一个零件</b>（最新大版本的最新小版本）。
 *
 * <p>2026-10-22 改口径：以前只列"被 BOM 行用到的子件"，零件刚建、BOM 还没搭时清单恒为空；
 * 现在只要 {@code container_oid} 属于该系列/型号就进清单，<b>不区分阶段、不区分文件夹</b>，
 * BOM 用量与父件只是可选补充（没有 BOM 行时 totalQuantity = 0、parentNumbers 为空）。
 *
 * <p>型号页会同时列出"型号自己的零件"和"挂在父系列上的零件"（历史数据），与创建时的
 * 自动建零件逻辑一致（见 ContainerPartProvisioner）。
 */
public class BomMaterialVO {

    /** 物料（子件）主对象 oid */
    private String childPartOid;

    /** 物料编码 */
    private String number;

    /** 物料名称 */
    private String name;

    /** 类型编码（如 ELECTRONIC / STRUCTURAL，与「研发资料」列表同一套取值） */
    private String typeCode;

    /** 零件最新迭代的显示版本（如 A.1）；没有迭代时为空 */
    private String version;

    /** 物料最新迭代的生命周期状态 code */
    private String status;

    /** 用量单位（取 BOM 行上的单位；同一物料出现多种单位时会分成多行，不做无意义的跨单位求和） */
    private String unit;

    /** 用量合计；BOM 行都没填用量时为空（不编造 0） */
    private Double totalQuantity;

    /** 被多少条 BOM 行引用 —— 同一个物料可能被多个父件各用一次 */
    private Integer lineCount;

    /** 用到它的父件编码（逗号分隔，便于在一行里看清"装在哪几个件上"） */
    private String parentNumbers;

    // ==================== 与「研发资料」列表对齐的字段 ====================
    // 2026-10-22：物料清单按研发资料的列展示（编码/名称/类型/版本/单位/生命周期/检出/操作），
    // 行上的「操作」直接用零件本身的能力，所以这里要带齐零件行所需的标识与状态。

    /** 零件主对象 oid（操作列要用它调检出/检入/编辑等接口） */
    private String oid;

    /** 编码（与 number 同值，取研发资料列表的字段名） */
    private String code;

    /** 类型显示名（如 零组件 / 电子零件） */
    private String typeDefinitionName;

    /** 显示版本（A.1 这种，等同 version） */
    private String displayVersion;

    /** 生命周期状态 code */
    private String statusCode;

    /** 生命周期状态显示名（草稿 / 工作中…） */
    private String statusName;

    /** 最新迭代 oid（检出/检入按迭代操作） */
    private String iterationOid;

    /** 生命周期模板迭代 oid（状态显示名按模板解析，见 LifecycleStatusService#displayName） */
    private String lifecycleTemplateIterationOid;

    /** 是否处于检出状态 */
    private Boolean checkedOut;

    /** 检出人 */
    private String checkedOutBy;

    /** 检出注释 */
    private String checkedOutComment;

    /** 当前状态是否允许修订（模板里配的 revisable，未知按允许） */
    private Boolean revisable;

    /** 行类型标记：物料清单里恒为 PART（前端操作菜单按它分派） */
    private String entityType;

    /**
     * 缩略图文件 oid（{@code ck_file.oid}）—— 2026-10-26 用户要求：
     * 零件若关联了 CAD 描述（工程数据），清单里要能直接看到形状。
     *
     * <p>为空 = 没关联工程数据，或关联了但还没生成缩略图（转换未完成）——
     * 前端给占位图，不编造图片。
     */
    private String thumbnailCkfileOid;

    public String getChildPartOid() { return childPartOid; }
    public void setChildPartOid(String childPartOid) { this.childPartOid = childPartOid; }

    public String getNumber() { return number; }
    public void setNumber(String number) { this.number = number; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getTypeCode() { return typeCode; }
    public void setTypeCode(String typeCode) { this.typeCode = typeCode; }

    public String getVersion() { return version; }
    public void setVersion(String version) { this.version = version; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public String getUnit() { return unit; }
    public void setUnit(String unit) { this.unit = unit; }

    public Double getTotalQuantity() { return totalQuantity; }
    public void setTotalQuantity(Double totalQuantity) { this.totalQuantity = totalQuantity; }

    public Integer getLineCount() { return lineCount; }
    public void setLineCount(Integer lineCount) { this.lineCount = lineCount; }

    public String getParentNumbers() { return parentNumbers; }
    public void setParentNumbers(String parentNumbers) { this.parentNumbers = parentNumbers; }

    public String getOid() { return oid; }
    public void setOid(String oid) { this.oid = oid; }

    public String getCode() { return code; }
    public void setCode(String code) { this.code = code; }

    public String getTypeDefinitionName() { return typeDefinitionName; }
    public void setTypeDefinitionName(String typeDefinitionName) { this.typeDefinitionName = typeDefinitionName; }

    public String getDisplayVersion() { return displayVersion; }
    public void setDisplayVersion(String displayVersion) { this.displayVersion = displayVersion; }

    public String getStatusCode() { return statusCode; }
    public void setStatusCode(String statusCode) { this.statusCode = statusCode; }

    public String getStatusName() { return statusName; }
    public void setStatusName(String statusName) { this.statusName = statusName; }

    public String getIterationOid() { return iterationOid; }
    public void setIterationOid(String iterationOid) { this.iterationOid = iterationOid; }

    public String getLifecycleTemplateIterationOid() { return lifecycleTemplateIterationOid; }
    public void setLifecycleTemplateIterationOid(String lifecycleTemplateIterationOid) { this.lifecycleTemplateIterationOid = lifecycleTemplateIterationOid; }

    public Boolean getCheckedOut() { return checkedOut; }
    public void setCheckedOut(Boolean checkedOut) { this.checkedOut = checkedOut; }

    public String getCheckedOutBy() { return checkedOutBy; }
    public void setCheckedOutBy(String checkedOutBy) { this.checkedOutBy = checkedOutBy; }

    public String getCheckedOutComment() { return checkedOutComment; }
    public void setCheckedOutComment(String checkedOutComment) { this.checkedOutComment = checkedOutComment; }

    public Boolean getRevisable() { return revisable; }
    public void setRevisable(Boolean revisable) { this.revisable = revisable; }

    public String getEntityType() { return entityType; }
    public void setEntityType(String entityType) { this.entityType = entityType; }

    public String getThumbnailCkfileOid() { return thumbnailCkfileOid; }
    public void setThumbnailCkfileOid(String thumbnailCkfileOid) { this.thumbnailCkfileOid = thumbnailCkfileOid; }
}
