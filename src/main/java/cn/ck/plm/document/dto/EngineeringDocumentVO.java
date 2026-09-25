/*
 * Copyright (c) 2025 深圳乘恺科技有限公司
 * All rights reserved.
 *
 * @author Kiween.Hu; Roney.Liu
 */

package cn.ck.plm.document.dto;

/**
 * 工程数据视图对象 —— 工程数据基本信息 + 最新迭代状态 + 类型中文名 + CAD 专有属性。
 *
 * <p>字段与 {@link DocumentVO} 保持一致（便于复用同一套列表组件），
 * 并追加工程数据特有的 {@code cadName / cadType / cadTool}。
 * 用于阶段页面 / 文件夹视图的列表展示。
 */
public class EngineeringDocumentVO {

    // ===== 主数据信息 =====
    private String oid;
    private String name;
    private String description;
    private String code;
    private String number;
    private String typeDefinitionCode;
    /** 类型定义中文名称（从 TypeDefinition 联查） */
    private String typeDefinitionName;
    private String containerOid;
    private String containerType;
    private String folderOid;
    private String stageOid;
    private String creator;
    private String createdAt;
    private String updater;
    private String updatedAt;

    // ===== 最新迭代信息 =====
    private String iterationOid;
    /** 大版本，如 A / B / C */
    private String revision;
    /** 小版本 */
    private Integer iteration;
    /** 显示版本，如 A.1 */
    private String displayVersion;
    /** 是否已检出 */
    private Boolean checkedOut;
    /** 检出人 */
    private String checkedOutBy;
    /** 检出注释 */
    private String checkedOutComment;
    /** 是否为最新版 */
    private Boolean latest;

    // ===== 生命周期状态 =====
    /** 生命周期状态编码 */
    private String statusCode;
    /** 生命周期状态显示名 */
    private String statusName;

    // ===== 文件信息 =====
    private String ckfileOid;

    // ===== CAD 专有属性（工程数据特有） =====
    /** CAD 名称，如 part_a.prt */
    private String cadName;
    /** CAD 类型：DRW / MODEL / ASM / FORM */
    private String cadType;
    /** 来源 CAD 工具 */
    private String cadTool;

    // ==================== Getters & Setters ====================

    public String getOid() { return oid; }
    public void setOid(String oid) { this.oid = oid; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public String getCode() { return code; }
    public void setCode(String code) { this.code = code; }

    public String getNumber() { return number; }
    public void setNumber(String number) { this.number = number; }

    public String getTypeDefinitionCode() { return typeDefinitionCode; }
    public void setTypeDefinitionCode(String typeDefinitionCode) { this.typeDefinitionCode = typeDefinitionCode; }

    public String getTypeDefinitionName() { return typeDefinitionName; }
    public void setTypeDefinitionName(String typeDefinitionName) { this.typeDefinitionName = typeDefinitionName; }

    public String getContainerOid() { return containerOid; }
    public void setContainerOid(String containerOid) { this.containerOid = containerOid; }

    public String getContainerType() { return containerType; }
    public void setContainerType(String containerType) { this.containerType = containerType; }

    public String getFolderOid() { return folderOid; }
    public void setFolderOid(String folderOid) { this.folderOid = folderOid; }

    public String getStageOid() { return stageOid; }
    public void setStageOid(String stageOid) { this.stageOid = stageOid; }

    public String getCreator() { return creator; }
    public void setCreator(String creator) { this.creator = creator; }

    public String getCreatedAt() { return createdAt; }
    public void setCreatedAt(String createdAt) { this.createdAt = createdAt; }

    public String getUpdater() { return updater; }
    public void setUpdater(String updater) { this.updater = updater; }

    public String getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(String updatedAt) { this.updatedAt = updatedAt; }

    public String getIterationOid() { return iterationOid; }
    public void setIterationOid(String iterationOid) { this.iterationOid = iterationOid; }

    public String getRevision() { return revision; }
    public void setRevision(String revision) { this.revision = revision; }

    public Integer getIteration() { return iteration; }
    public void setIteration(Integer iteration) { this.iteration = iteration; }

    public String getDisplayVersion() {
        if (displayVersion != null) return displayVersion;
        if (revision == null) return "";
        return revision + (iteration != null ? "." + iteration : "");
    }
    public void setDisplayVersion(String displayVersion) { this.displayVersion = displayVersion; }

    public Boolean getCheckedOut() { return checkedOut; }
    public void setCheckedOut(Boolean checkedOut) { this.checkedOut = checkedOut; }

    public String getCheckedOutBy() { return checkedOutBy; }
    public void setCheckedOutBy(String checkedOutBy) { this.checkedOutBy = checkedOutBy; }

    public String getCheckedOutComment() { return checkedOutComment; }
    public void setCheckedOutComment(String checkedOutComment) { this.checkedOutComment = checkedOutComment; }

    public Boolean getLatest() { return latest; }
    public void setLatest(Boolean latest) { this.latest = latest; }

    public String getStatusCode() { return statusCode; }
    public void setStatusCode(String statusCode) { this.statusCode = statusCode; }

    public String getStatusName() { return statusName; }
    public void setStatusName(String statusName) { this.statusName = statusName; }

    public String getCkfileOid() { return ckfileOid; }
    public void setCkfileOid(String ckfileOid) { this.ckfileOid = ckfileOid; }

    public String getCadName() { return cadName; }
    public void setCadName(String cadName) { this.cadName = cadName; }

    public String getCadType() { return cadType; }
    public void setCadType(String cadType) { this.cadType = cadType; }

    public String getCadTool() { return cadTool; }
    public void setCadTool(String cadTool) { this.cadTool = cadTool; }

    /** 获取版本标签，如 "A.1" */
    public String getLabel() {
        if (revision == null) return "";
        return revision + (iteration != null ? "." + iteration : "");
    }
}
