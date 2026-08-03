package cn.ck.plm.part.dto;

/**
 * Part View Object.
 */
public class PartVO {

    private String oid;
    private String name;
    private String description;
    private String code;
    private String number;
    private String typeDefinitionCode;
    private String typeDefinitionName;
    private String containerOid;
    private String containerType;
    private String folderOid;
    private String stageOid;
    private String classificationOid;
    private String creator;
    private String createdAt;
    private String updater;
    private String updatedAt;

    private String iterationOid;
    private String revision;
    private Integer iteration;
    private String displayVersion;
    private Boolean checkedOut;
    private String checkedOutBy;
    private String checkedOutComment;
    private String checkedOutAt;
    private Boolean latest;

    private String statusCode;
    private String statusName;
    private String unit;

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

    public String getClassificationOid() { return classificationOid; }
    public void setClassificationOid(String classificationOid) { this.classificationOid = classificationOid; }

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

    public String getCheckedOutAt() { return checkedOutAt; }
    public void setCheckedOutAt(String checkedOutAt) { this.checkedOutAt = checkedOutAt; }

    public Boolean getLatest() { return latest; }
    public void setLatest(Boolean latest) { this.latest = latest; }

    public String getStatusCode() { return statusCode; }
    public void setStatusCode(String statusCode) { this.statusCode = statusCode; }

    public String getStatusName() { return statusName; }
    public void setStatusName(String statusName) { this.statusName = statusName; }

    public String getUnit() { return unit; }
    public void setUnit(String unit) { this.unit = unit; }

    public String getLabel() {
        if (revision == null) return "";
        return revision + (iteration != null ? "." + iteration : "");
    }
}
