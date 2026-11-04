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
 * 受影响对象 —— 变更模块的<b>核心表</b>：一条 = "把某个对象的某一版改成新大版本"。
 *
 * <h3>处置统一为 REVISE</h3>
 * <p>不再有 disposition 枚举：变更要表达的就是"这些对象需要出新大版本"。
 * 作废、状态推进属于对象自身的生命周期动作，不从这里走 ——
 * 否则处置枚举会重新长回来，两套语义又会分叉。
 *
 * <h3>三段版本信息</h3>
 * <pre>
 * baseIterationOid / baseVersion      被修订的那一版（A.2）
 * resultIterationOid / resultVersion  修订产物（B.1）；执行后回填
 * executedAt 非空                     已执行 —— 幂等判据：重复提交不产生第二个 B 版
 * </pre>
 *
 * <h3>locked（变更占用）</h3>
 * <p>同一主对象同时只能被一个未完成的变更持有。锁建在
 * {@code (entityType, entityOid)} 而不是迭代上：修订是<b>主对象级</b>的大版本跃迁，
 * 两个变更分别盯 A.1 / A.2 也会争抢 B 这个大版本号
 * （{@code ck_part_iteration} 没有 (master_oid, revision, iteration) 唯一约束，
 * 数据库不会替你拦）。
 */
public class ChangeAffectedObject extends BaseEntity implements TenantEntity {

    /** 所属变更通告（ck_change_notice.oid） */
    private String noticeOid;

    /** 交给哪个变更任务执行；空 = 待分配 */
    private String taskOid;

    /** 对象类型：PART / DOCUMENT / ENG_DOCUMENT（与 CheckoutProvider / ReviseProvider 同口径） */
    private String entityType;

    /** 主对象 oid */
    private String entityOid;

    /** 编码快照（列表展示免 join） */
    private String entityCode;

    /** 名称快照 */
    private String entityName;

    /**
     * 所属容器快照（录入时写入）。
     *
     * <p>只用于展示、按产品/资源库筛选与审计追溯，不用于权限判定；
     * 不存 {@code container_type} —— 容器 oid 已能唯一定位，类型靠容器表 join 推出。
     */
    private String containerOid;

    /** 容器名称（查询期 join 得到，不落库） */
    private String containerName;

    /** 被修订的迭代 oid（A.2） */
    private String baseIterationOid;

    /** 被修订的版本号（A.2） */
    private String baseVersion;

    /** 修订产物迭代 oid（B.1）；执行后回填 */
    private String resultIterationOid;

    /** 修订产物版本号（B.1） */
    private String resultVersion;

    /** 执行时间（非空 = 已执行，幂等判据） */
    private LocalDateTime executedAt;

    /** 执行人 */
    private String executedBy;

    /** 变更占用：ECN 批准时置 true，完成/取消时释放 */
    private boolean locked;

    /** 备注 */
    private String note;

    /** 租户 oid */
    private String tenantOid;

    public ChangeAffectedObject() {
    }

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

    public String getContainerOid() { return containerOid; }
    public void setContainerOid(String containerOid) { this.containerOid = containerOid; }

    public String getContainerName() { return containerName; }
    public void setContainerName(String containerName) { this.containerName = containerName; }

    public String getBaseIterationOid() { return baseIterationOid; }
    public void setBaseIterationOid(String baseIterationOid) { this.baseIterationOid = baseIterationOid; }

    public String getBaseVersion() { return baseVersion; }
    public void setBaseVersion(String baseVersion) { this.baseVersion = baseVersion; }

    public String getResultIterationOid() { return resultIterationOid; }
    public void setResultIterationOid(String resultIterationOid) { this.resultIterationOid = resultIterationOid; }

    public String getResultVersion() { return resultVersion; }
    public void setResultVersion(String resultVersion) { this.resultVersion = resultVersion; }

    public LocalDateTime getExecutedAt() { return executedAt; }
    public void setExecutedAt(LocalDateTime executedAt) { this.executedAt = executedAt; }

    public String getExecutedBy() { return executedBy; }
    public void setExecutedBy(String executedBy) { this.executedBy = executedBy; }

    public boolean isLocked() { return locked; }
    public void setLocked(boolean locked) { this.locked = locked; }

    public String getNote() { return note; }
    public void setNote(String note) { this.note = note; }

    @Override
    public String getTenantOid() { return tenantOid; }

    @Override
    public void setTenantOid(String tenantOid) { this.tenantOid = tenantOid; }

    @Override
    public String toString() {
        return "ChangeAffectedObject{" + entityType + ":" + entityOid
                + ", base=" + baseVersion + ", result=" + resultVersion + "}";
    }
}
