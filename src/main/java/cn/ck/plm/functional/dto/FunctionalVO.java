/*
 * Copyright (c) 2025 深圳乘恺科技有限公司
 * All rights reserved.
 *
 * @author Kiween.Hu; Roney.Liu
 */

package cn.ck.plm.functional.dto;

import cn.ck.plm.base.entity.LifecycleStatus;
import cn.ck.plm.base.entity.View;
import com.fasterxml.jackson.annotation.JsonIgnore;

/**
 * System 前端展示 VO。
 */
public class FunctionalVO {

    private String oid;
    private String name;
    private String number;
    private String description;
    private String typeDefinitionCode;
    private String typeDefinitionName;
    private String folderOid;
    private String folderName;
    private String stageOid;
    private String stageName;
    private String containerOid;
    private String containerType;
    private String iterationOid;
    private String revision;
    private Integer iteration;
    private String displayVersion;
    private View view;
    private LifecycleStatus status;
    private boolean checkedOut;
    private String checkedOutBy;
    private String checkedOutComment;
    private boolean latest;
    private String creator;
    private String createdAt;

    // ==================== Getter / Setter ====================

    public String getOid() { return oid; }
    public void setOid(String oid) { this.oid = oid; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getNumber() { return number; }
    public void setNumber(String number) { this.number = number; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public String getTypeDefinitionCode() { return typeDefinitionCode; }
    public void setTypeDefinitionCode(String typeDefinitionCode) { this.typeDefinitionCode = typeDefinitionCode; }

    public String getTypeDefinitionName() { return typeDefinitionName; }
    public void setTypeDefinitionName(String typeDefinitionName) { this.typeDefinitionName = typeDefinitionName; }

    public String getFolderOid() { return folderOid; }
    public void setFolderOid(String folderOid) { this.folderOid = folderOid; }

    public String getFolderName() { return folderName; }
    public void setFolderName(String folderName) { this.folderName = folderName; }

    public String getStageOid() { return stageOid; }
    public void setStageOid(String stageOid) { this.stageOid = stageOid; }

    public String getStageName() { return stageName; }
    public void setStageName(String stageName) { this.stageName = stageName; }

    public String getContainerOid() { return containerOid; }
    public void setContainerOid(String containerOid) { this.containerOid = containerOid; }

    public String getContainerType() { return containerType; }
    public void setContainerType(String containerType) { this.containerType = containerType; }

    public String getIterationOid() { return iterationOid; }
    public void setIterationOid(String iterationOid) { this.iterationOid = iterationOid; }

    public String getRevision() { return revision; }
    public void setRevision(String revision) { this.revision = revision; }

    public Integer getIteration() { return iteration; }
    public void setIteration(Integer iteration) { this.iteration = iteration; }

    @JsonIgnore
    public String getVersionLabel() { return displayVersion; }

    public String getDisplayVersion() {
        if (displayVersion != null && !displayVersion.isEmpty()) return displayVersion;
        if (revision != null && iteration != null) return revision + "." + iteration;
        return null;
    }
    public void setDisplayVersion(String displayVersion) { this.displayVersion = displayVersion; }

    public View getView() { return view; }
    public void setView(View view) { this.view = view; }

    public LifecycleStatus getStatus() { return status; }
    public void setStatus(LifecycleStatus status) { this.status = status; }

    public boolean isCheckedOut() { return checkedOut; }
    public void setCheckedOut(boolean checkedOut) { this.checkedOut = checkedOut; }

    public String getCheckedOutBy() { return checkedOutBy; }
    public void setCheckedOutBy(String checkedOutBy) { this.checkedOutBy = checkedOutBy; }

    public String getCheckedOutComment() { return checkedOutComment; }
    public void setCheckedOutComment(String checkedOutComment) { this.checkedOutComment = checkedOutComment; }

    public boolean isLatest() { return latest; }
    public void setLatest(boolean latest) { this.latest = latest; }

    public String getCreator() { return creator; }
    public void setCreator(String creator) { this.creator = creator; }

    public String getCreatedAt() { return createdAt; }
    public void setCreatedAt(String createdAt) { this.createdAt = createdAt; }
}
