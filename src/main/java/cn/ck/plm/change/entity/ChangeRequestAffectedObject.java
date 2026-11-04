/*
 * Copyright (c) 2026~2028 深圳乘恺科技有限公司
 * All rights reserved.
 *
 * @author Kiween.Hu; Roney.Liu
 */

package cn.ck.plm.change.entity;

import cn.ck.plm.base.entity.BaseEntity;
import cn.ck.plm.base.entity.TenantEntity;

/**
 * 变更请求（ECR）的<b>预估受影响对象</b>（表 {@code ck_change_request_affected_object}）。
 *
 * <h3>与 {@link ChangeAffectedObject} 的区别</h3>
 * <p>本表回答"这次大概会动到哪些对象"，是<b>评审输入</b>：
 * <ul>
 *   <li>没有 {@code base_iteration_oid / base_version} —— 提出阶段还不知道要改哪一版；</li>
 *   <li>没有 {@code locked / executed_at / result_*} —— 不参与修订执行，不占变更锁；</li>
 *   <li>ECN 建单时按这张表带入受影响对象（取对象当前最新版作为 base），
 *       之后执行与否只由 ECN 侧决定。</li>
 * </ul>
 *
 * <p>两条快照 {@code entity_code / entity_name} 与 {@code container_oid} 都在录入时写入，
 * 不跟随对象后来改名 / 迁移更新 —— 与 {@code ChangeAffectedObject} 的快照语义一致。
 */
public class ChangeRequestAffectedObject extends BaseEntity implements TenantEntity {

    /** 所属变更请求（ck_change_request.oid） */
    private String requestOid;

    /** 对象类型：PART / DOCUMENT / ENG_DOCUMENT（与 ReviseProvider 同口径） */
    private String entityType;

    /** 主对象 oid */
    private String entityOid;

    /** 编码快照 */
    private String entityCode;

    /** 名称快照 */
    private String entityName;

    /**
     * 所属容器快照（录入时写入）。
     *
     * <p>只用于展示、按产品/资源库筛选与审计追溯，<b>不用于权限判定</b>，
     * 也不存 {@code container_type}（容器 oid 已能唯一定位，类型靠容器表 join 推出）。
     */
    private String containerOid;

    /** 容器名称（查询期 join 得到，不落库） */
    private String containerName;

    /** 备注 */
    private String note;

    /** 租户 oid */
    private String tenantOid;

    public ChangeRequestAffectedObject() {
    }

    public String getRequestOid() { return requestOid; }
    public void setRequestOid(String requestOid) { this.requestOid = requestOid; }

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

    public String getNote() { return note; }
    public void setNote(String note) { this.note = note; }

    @Override
    public String getTenantOid() { return tenantOid; }

    @Override
    public void setTenantOid(String tenantOid) { this.tenantOid = tenantOid; }

    @Override
    public String toString() {
        return "ChangeRequestAffectedObject{" + entityType + ":" + entityOid + "}";
    }
}
