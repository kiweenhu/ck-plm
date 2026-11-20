/*
 * Copyright (c) 2026~2028 深圳乘恺科技有限公司
 * All rights reserved.
 *
 * @author Kiween.Hu
 */

package cn.ck.plm.ecad.entity;

import cn.ck.plm.base.entity.IterationEntity;
import cn.ck.plm.base.entity.TenantEntity;

/**
 * ECAD 原理图子版本数据对象（Ecad Schematic Iteration）。
 *
 * <p>继承 {@link IterationEntity} 的全部版本字段（masterOid / revision / iteration /
 * checkedOut / latest / derivedFromOid / status），版本流转规则与文档迭代一致：
 * 检出时派生新迭代（iteration+1），检入解除锁定，新建大版本时 iteration 归 1。
 *
 * <h3>实体关系</h3>
 * <pre>
 * EcadSchematicIteration 1 ── 1 CKFile （.DSN 主文件，ckfileOid；FileCategory.CAD 存储）
 * </pre>
 */
public class EcadSchematicIteration extends IterationEntity implements TenantEntity {

    /** .DSN 设计文件 oid（指向 ck_file，FileCategory.CAD 分类存储） */
    private String ckfileOid;

    /** 原理图页数（最近一次 BOM/元数据同步回填） */
    private Integer sheetCount;

    /** 器件总数（最近一次 BOM 同步回填） */
    private Integer componentCount;

    /** 最近一次同步的 BOM 快照（JSON 数组：reference/quantity/value/footprint/componentOid） */
    private String bomJson;

    /** 租户 oid */
    private String tenantOid;

    // ==================== Getter / Setter ====================

    public String getCkfileOid() { return ckfileOid; }
    public void setCkfileOid(String ckfileOid) { this.ckfileOid = ckfileOid; }

    public Integer getSheetCount() { return sheetCount; }
    public void setSheetCount(Integer sheetCount) { this.sheetCount = sheetCount; }

    public Integer getComponentCount() { return componentCount; }
    public void setComponentCount(Integer componentCount) { this.componentCount = componentCount; }

    public String getBomJson() { return bomJson; }
    public void setBomJson(String bomJson) { this.bomJson = bomJson; }

    @Override
    public String getTenantOid() { return tenantOid; }

    @Override
    public void setTenantOid(String tenantOid) { this.tenantOid = tenantOid; }

    @Override
    public String toString() {
        return "EcadSchematicIteration{masterOid='" + getMasterOid()
                + "', version=" + getVersion() + "}";
    }
}
