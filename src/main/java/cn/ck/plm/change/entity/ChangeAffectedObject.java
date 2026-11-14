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
 * <h3>本表只记「变更前」（2026-11-09 拆表）</h3>
 * <pre>
 * ck_change_affected_object   一条 = "要把哪个对象的哪一版改掉"（base_*）
 * ck_change_result_object     一条 = 执行产出的一个版本（{@link ChangeResultObject}）
 * </pre>
 * <p>本类上的 {@code resultIterationOid / resultVersion / executedAt / executedBy}
 * 是<b>查询期 JOIN 出来的只读展示字段，不落本表</b> —— 列表页需要"改没改、改成哪一版"
 * 一眼可见，但事实只有一个来源：结果表。
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

    /**
     * 修订产物迭代 oid（B.1）—— <b>只读展示字段</b>：不落本表，查询期从
     * {@code ck_change_result_object} LEFT JOIN 出来。
     *
     * <p>一拆多时这里只会有其中一条（列表按"已执行 / 未执行"展示足够）；
     * 要完整的产出一律查结果表，不要指望这个字段。
     */
    private String resultIterationOid;

    /** 修订产物版本号（B.1）—— 同上，JOIN 得到的展示字段 */
    private String resultVersion;

    /**
     * 修订产物所在大版本的<b>最新小版本</b>（B.2）—— 服务层回查出来的展示字段，不落库。
     *
     * <p>结果表里记的是修订那一刻的快照 B.1；执行人拿到权限后检出 / 修改 / 检入，
     * 会在同一个 B 大版本下再出 B.2、B.3……审批人要看的是"这次改完的结果现在是哪一版"，
     * 而不是第一次修订的版本。限定同一 master + 同一 revision，别把下一次大版本 C.1 混进来。
     * 查不到时前端回落 {@link #resultVersion}。
     */
    private String currentResultVersion;

    /** 执行时间 —— JOIN 得到（非空即"已有结果行"） */
    private LocalDateTime executedAt;

    /** 执行人 —— JOIN 得到 */
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

    public String getCurrentResultVersion() { return currentResultVersion; }
    public void setCurrentResultVersion(String currentResultVersion) { this.currentResultVersion = currentResultVersion; }

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
