/*
 * Copyright (c) 2026~2028 深圳乘恺科技有限公司
 * All rights reserved.
 *
 * @author Kiween.Hu; Roney.Liu
 */

package cn.ck.plm.change.entity;

import cn.ck.plm.base.entity.BaseEntity;
import cn.ck.plm.base.entity.TenantEntity;

import java.time.LocalDateTime;

/**
 * 变更结果对象 —— <b>变更后</b>（2026-11-09 与「变更前」分表）。
 *
 * <h3>为什么要单独一张表</h3>
 * <p>「变更前」回答"要把什么改成什么"，一条变更对象只该有一条；「变更后」是执行产物，
 * 一个变更前对象可能产出多个版本（拆件）、甚至产出一个原本不在清单里的新件。
 * 把它们塞在同一行的两组字段里，一对一能凑合，一拆多就无处可放。
 *
 * <pre>
 * ck_change_affected_object   base_iteration_oid / base_version   变更前（A.2）
 * ck_change_result_object     result_iteration_oid / result_version 变更后（B.1）
 *                             affected_oid 指回变更前那一行
 * </pre>
 *
 * <h3>change_kind</h3>
 * <ul>
 *   <li>{@code REVISE}：修订同一主对象（当前唯一在用的一种）</li>
 *   <li>{@code SPLIT}：从变更前对象拆出另一个对象</li>
 *   <li>{@code NEW}：变更过程中新建的对象（变更前清单里没有它）</li>
 * </ul>
 *
 * <h3>幂等</h3>
 * <p>唯一索引 {@code uk_change_result (affected_oid, result_iteration_oid)} 兜底：
 * 同一个变更前对象的同一版产出重复写只会冲突，不会出现两条"变更后"。
 */
public class ChangeResultObject extends BaseEntity implements TenantEntity {

    /** 变更前那一行（ck_change_affected_object.oid） */
    private String affectedOid;

    /** 所属变更通告 */
    private String noticeOid;

    /** 由哪个变更任务执行产出；可空（通告级操作，如后续在通告上直接补结果） */
    private String taskOid;

    /** 对象类型：PART / DOCUMENT / ENG_DOCUMENT */
    private String entityType;

    /** 主对象 oid（修订不会换主对象；SPLIT / NEW 时是新对象自己的 oid） */
    private String entityOid;

    /** 编码快照 */
    private String entityCode;

    /** 名称快照 */
    private String entityName;

    /** 从哪一版改的（= 变更前行的 base_iteration_oid） */
    private String sourceIterationOid;

    /** 从哪个版本号改的（A.2） */
    private String sourceVersion;

    /** 产出迭代 oid（B.1） */
    private String resultIterationOid;

    /** 产出迭代版本号（B.1） */
    private String resultVersion;

    /** 产出方式：REVISE / SPLIT / NEW */
    private String changeKind;

    /** 执行时间 */
    private LocalDateTime executedAt;

    /** 执行人 */
    private String executedBy;

    /** 租户 oid */
    private String tenantOid;

    public ChangeResultObject() {
    }

    public String getAffectedOid() { return affectedOid; }
    public void setAffectedOid(String affectedOid) { this.affectedOid = affectedOid; }

    public String getNoticeOid() { return noticeOid; }
    public void setNoticeOid(String noticeOid) { this.noticeOid = noticeOid; }

    public String getTaskOid() { return taskOid; }
    public void setTaskOid(String taskOid) { this.taskOid = taskOid; }

    public String getEntityType() { return entityType; }
    public void setEntityType(String entityType) { this.entityType = entityType; }

    public String getEntityOid() { return entityOid; }
    public void setEntityOid(String entityOid) { this.entityOid = entityOid; }

    public String getEntityCode() { return entityCode; }
    public void setEntityCode(String entityCode) { this.entityCode = entityCode; }

    public String getEntityName() { return entityName; }
    public void setEntityName(String entityName) { this.entityName = entityName; }

    public String getSourceIterationOid() { return sourceIterationOid; }
    public void setSourceIterationOid(String sourceIterationOid) { this.sourceIterationOid = sourceIterationOid; }

    public String getSourceVersion() { return sourceVersion; }
    public void setSourceVersion(String sourceVersion) { this.sourceVersion = sourceVersion; }

    public String getResultIterationOid() { return resultIterationOid; }
    public void setResultIterationOid(String resultIterationOid) { this.resultIterationOid = resultIterationOid; }

    public String getResultVersion() { return resultVersion; }
    public void setResultVersion(String resultVersion) { this.resultVersion = resultVersion; }

    public String getChangeKind() { return changeKind; }
    public void setChangeKind(String changeKind) { this.changeKind = changeKind; }

    public LocalDateTime getExecutedAt() { return executedAt; }
    public void setExecutedAt(LocalDateTime executedAt) { this.executedAt = executedAt; }

    public String getExecutedBy() { return executedBy; }
    public void setExecutedBy(String executedBy) { this.executedBy = executedBy; }

    @Override
    public String getTenantOid() { return tenantOid; }

    @Override
    public void setTenantOid(String tenantOid) { this.tenantOid = tenantOid; }

    @Override
    public String toString() {
        return "ChangeResultObject{" + changeKind + ":" + entityType + ":" + entityOid
                + ", " + sourceVersion + " -> " + resultVersion + "}";
    }
}
