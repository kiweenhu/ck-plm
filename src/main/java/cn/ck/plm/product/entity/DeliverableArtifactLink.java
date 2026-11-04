/*
 * Copyright (c) 2026~2028 深圳乘恺科技有限公司
 * All rights reserved.
 *
 * @author Kiween.Hu; Roney.Liu
 */

package cn.ck.plm.product.entity;

import cn.ck.plm.base.entity.TenantEntity;

import java.time.LocalDateTime;

/**
 * 交付件 ↔ 产出物 关联（2026-10-11 新增）。
 *
 * <p>交付件的产出物<b>不都是文档</b> —— 原理图（SCHEMATIC）/ PCB（PCB_LAYOUT）/ 结构件
 * （STRUCTURAL）/ 软件（SOFTWARE）等本身就是业务对象（工程数据或零组件），本表统一承载
 * "某个交付件由哪个业务对象来满足"。
 *
 * <h3>为什么一张表能统一三类产出物</h3>
 * 文档（ck_document）、工程数据（ck_eng_document）、零组件（ck_part）三张表结构同构
 * （oid / number / name / type_definition_code / container_oid），且都带版本与生命周期 ——
 * 所以关联只记 {@code entityType + entityOid + iterationOid}，完成判定（BOUND / LIFECYCLE）
 * 对三类走同一套逻辑。
 *
 * <h3>硬约束（服务端校验，见 DeliverableArtifactLinkController）</h3>
 * 能关联的对象类别由交付件模板的 {@code object_type} 决定：非空时只认该类型 code；
 * 为空时按 {@code bind_mode} 落到宿主级（DOCUMENT → 文档，PART_SET → 零组件）。
 *
 * <h3>跨容器</h3>
 * 允许引用资源库 / 其它型号的对象（用户明确要求）—— 故本表<b>不限制</b> container_oid，
 * 只记关联时刻的快照供展示与追溯。
 *
 * @see Deliverable#getObjectType()
 * @see ck_deliverable_artifact_link（schema.sql）
 */
public class DeliverableArtifactLink implements TenantEntity {

    /** 主键 */
    private String oid;

    /** 关联的交付件（ck_deliverable.oid，实例级） */
    private String deliverableOid;

    /** 产出物宿主：DOCUMENT / ENG_DOCUMENT / PART */
    private String entityType;

    /** 产出物具体类型 code（SCHEMATIC / PCB_LAYOUT / ELECTRONIC …） */
    private String typeCode;

    /** 产出物主数据 oid */
    private String entityOid;

    /** 关联时的迭代快照（可空 = 跟随最新；P0 不写，留列给"冻结版本"场景） */
    private String iterationOid;

    /** 关联角色：PRIMARY（主证据）/ SUPPORTING（辅证）—— P0 固定 PRIMARY */
    private String role;

    /** 产出物所属容器快照（跨容器引用时便于展示与追溯） */
    private String containerOid;

    /** 租户 oid（业务表：由 AuditInterceptor 注入，Mapper 不显式书写） */
    private String tenantOid;

    private String creator;
    private LocalDateTime createdAt;
    private String updater;
    private LocalDateTime updatedAt;

    public DeliverableArtifactLink() {}

    public String getOid() { return oid; }
    public void setOid(String oid) { this.oid = oid; }

    public String getDeliverableOid() { return deliverableOid; }
    public void setDeliverableOid(String deliverableOid) { this.deliverableOid = deliverableOid; }

    public String getEntityType() { return entityType; }
    public void setEntityType(String entityType) { this.entityType = entityType; }

    public String getTypeCode() { return typeCode; }
    public void setTypeCode(String typeCode) { this.typeCode = typeCode; }

    public String getEntityOid() { return entityOid; }
    public void setEntityOid(String entityOid) { this.entityOid = entityOid; }

    public String getIterationOid() { return iterationOid; }
    public void setIterationOid(String iterationOid) { this.iterationOid = iterationOid; }

    public String getRole() { return role; }
    public void setRole(String role) { this.role = role; }

    public String getContainerOid() { return containerOid; }
    public void setContainerOid(String containerOid) { this.containerOid = containerOid; }

    @Override
    public String getTenantOid() { return tenantOid; }
    @Override
    public void setTenantOid(String tenantOid) { this.tenantOid = tenantOid; }

    public String getCreator() { return creator; }
    public void setCreator(String creator) { this.creator = creator; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    public String getUpdater() { return updater; }
    public void setUpdater(String updater) { this.updater = updater; }

    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
