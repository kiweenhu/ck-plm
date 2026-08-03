/*
 * Copyright (c) 2025 深圳乘恺科技有限公司
 * All rights reserved.
 *
 * @author Kiween.Hu; Roney.Liu
 */

package cn.ck.plm.base.entity;

import java.time.LocalDateTime;

/**
 * 子版本数据对象（IterationEntity），参考 Windchill RevisionControlled 模型。
 *
 * <p>作为所有业务子版本的纯数据父类，承载版本控制与生命周期相关字段。
 * 本身不携带业务方法——检出/检入、生命周期流转等操作由独立的
 * {@link cn.ck.plm.base.service.impl.IterationServiceImpl} 提供。
 *
 * <h3>Windchill 对应</h3>
 * <pre>
 * Persistable → BaseEntity → IterationEntity(this)  ←  wt.fc.RevisionControlled → WTDocument / WTPart
 * </pre>
 *
 * <h3>继承链条（贫血模型）</h3>
 * <pre>
 * BaseEntity (oid, creator, createdAt, updater, updatedAt)
 *   └── IterationEntity (masterOid, revision, iteration, checkedOut, latest, view, status)
 *         └── DocumentIteration / PartIteration / ...
 *
 * MasterEntity  1 ── N  IterationEntity  (通过 masterOid 关联)
 *   View  1 ── N  IterationEntity
 * </pre>
 */
public class IterationEntity extends BaseEntity {

    /** 关联 Master 的 oid */
    private String masterOid;

    /** 大版本（如 A, B, C...） */
    private String revision;

    /** 小版本/迭代号 */
    private int iteration;

    /** 显示版本（如 A.1、B.3），由 revision + "." + iteration 组成 */
    private String displayVersion;

    /** 是否已被检出 */
    private boolean checkedOut;

    /** 检出人 */
    private String checkedOutBy;

    /** 检出注释 */
    private String checkedOutComment;

    /** 是否最新小版本 */
    private boolean latest;

    /** 副本来源版本 oid（为空表示原创） */
    private String derivedFromOid;

    /** 副本创建时间 */
    private LocalDateTime derivedAt;

    /** 所属视图 */
    private View view;

    /** 当前生命周期状态 */
    private LifecycleStatus status;

    /** 绑定的生命周期模板迭代版本 oid（指向 ck_lifecycle_template_iteration.oid） */
    private String lifecycleTemplateIterationOid;

    /** 版本排序号 */
    private int versionSort;

    /** 分支 ID（用于分支管理） */
    private String branchId;

    /** 删除标记（软删除） */
    private boolean deleteMark;

    // ==================== 构造方法 ====================

    public IterationEntity() {
        this.revision = "A";
        this.iteration = 1;
        this.checkedOut = false;
        this.latest = true;
    }

    // ==================== 计算属性 ====================

    /** 获取完整版本号，格式：revision.iteration（如 A.1） */
    public String getVersion() {
        return revision + "." + iteration;
    }

    // ==================== Getter / Setter ====================

    public String getMasterOid() { return masterOid; }
    public void setMasterOid(String masterOid) { this.masterOid = masterOid; }

    public String getRevision() { return revision; }
    public void setRevision(String revision) {
        this.revision = revision;
        syncDisplayVersion();
    }

    public int getIteration() { return iteration; }
    public void setIteration(int iteration) {
        this.iteration = iteration;
        syncDisplayVersion();
    }

    public String getDisplayVersion() { return displayVersion; }
    public void setDisplayVersion(String displayVersion) { this.displayVersion = displayVersion; }

    private void syncDisplayVersion() {
        this.displayVersion = (revision != null ? revision : "") + "." + iteration;
    }

    public boolean isCheckedOut() { return checkedOut; }
    public void setCheckedOut(boolean checkedOut) { this.checkedOut = checkedOut; }

    public String getCheckedOutBy() { return checkedOutBy; }
    public void setCheckedOutBy(String checkedOutBy) { this.checkedOutBy = checkedOutBy; }

    public String getCheckedOutComment() { return checkedOutComment; }
    public void setCheckedOutComment(String checkedOutComment) { this.checkedOutComment = checkedOutComment; }

    public boolean isLatest() { return latest; }
    public void setLatest(boolean latest) { this.latest = latest; }

    public String getDerivedFromOid() { return derivedFromOid; }
    public void setDerivedFromOid(String derivedFromOid) { this.derivedFromOid = derivedFromOid; }

    public LocalDateTime getDerivedAt() { return derivedAt; }
    public void setDerivedAt(LocalDateTime derivedAt) { this.derivedAt = derivedAt; }

    public View getView() { return view; }
    public void setView(View view) { this.view = view; }

    public LifecycleStatus getStatus() { return status; }
    public void setStatus(LifecycleStatus status) { this.status = status; }

    public String getLifecycleTemplateIterationOid() { return lifecycleTemplateIterationOid; }
    public void setLifecycleTemplateIterationOid(String lifecycleTemplateIterationOid) { this.lifecycleTemplateIterationOid = lifecycleTemplateIterationOid; }

    public int getVersionSort() { return versionSort; }
    public void setVersionSort(int versionSort) { this.versionSort = versionSort; }

    public String getBranchId() { return branchId; }
    public void setBranchId(String branchId) { this.branchId = branchId; }

    public boolean isDeleteMark() { return deleteMark; }
    public void setDeleteMark(boolean deleteMark) { this.deleteMark = deleteMark; }

    @Override
    public String toString() {
        return "IterationEntity{masterOid='" + masterOid + "', version=" + getVersion()
                + ", view=" + (view != null ? view.getCode() : "null") + "}";
    }
}
