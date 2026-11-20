/*
 * Copyright (c) 2026~2028 深圳乘恺科技有限公司
 * All rights reserved.
 *
 * @author Kiween.Hu
 */

package cn.ck.plm.ecad.entity;

import cn.ck.plm.base.entity.BaseEntity;
import cn.ck.plm.base.entity.TenantEntity;

/**
 * BOM 同步日志（{@code ck_ecad_bom_sync_log}）—— 原理图 BOM 同步的差异留痕。
 */
public class EcadBomSyncLog extends BaseEntity implements TenantEntity {

    /** 原理图 master oid */
    private String schematicOid;

    /** 本次同步写入的迭代 oid */
    private String iterationOid;

    /** 新增行数 */
    private Integer addedCount;

    /** 移除行数 */
    private Integer removedCount;

    /** 变化行数 */
    private Integer changedCount;

    /** 差异 JSON（BomSyncResult 序列化） */
    private String diffJson;

    /** 租户 oid */
    private String tenantOid;

    // ==================== Getter / Setter ====================

    public String getSchematicOid() { return schematicOid; }
    public void setSchematicOid(String schematicOid) { this.schematicOid = schematicOid; }

    public String getIterationOid() { return iterationOid; }
    public void setIterationOid(String iterationOid) { this.iterationOid = iterationOid; }

    public Integer getAddedCount() { return addedCount; }
    public void setAddedCount(Integer addedCount) { this.addedCount = addedCount; }

    public Integer getRemovedCount() { return removedCount; }
    public void setRemovedCount(Integer removedCount) { this.removedCount = removedCount; }

    public Integer getChangedCount() { return changedCount; }
    public void setChangedCount(Integer changedCount) { this.changedCount = changedCount; }

    public String getDiffJson() { return diffJson; }
    public void setDiffJson(String diffJson) { this.diffJson = diffJson; }

    @Override
    public String getTenantOid() { return tenantOid; }

    @Override
    public void setTenantOid(String tenantOid) { this.tenantOid = tenantOid; }
}
