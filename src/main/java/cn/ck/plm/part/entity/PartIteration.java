/*
 * Copyright (c) 2025 深圳乘恺科技有限公司
 * All rights reserved.
 *
 * @author Kiween.Hu; Roney.Liu
 */

package cn.ck.plm.part.entity;

import cn.ck.plm.base.entity.IterationEntity;
import cn.ck.plm.base.entity.TenantEntity;

/**
 * 部件子版本数据对象（Part Iteration），参考 Windchill WTPart 模型。
 *
 * <p>继承 {@link IterationEntity} 的全部版本字段，新增部件特有的文件属性。
 * 业务方法（updateFrom 部件字段拷贝等）由独立的
 * {@link cn.ck.plm.part.service.impl.PartIterationServiceImpl} 提供。
 *
 * <h3>Windchill 对应</h3>
 * <pre>
 * Persistable → BaseEntity → IterationEntity → PartIteration(this)  ←  wt.part.WTPart
 * </pre>
 *
 * <h3>实体关系</h3>
 * <pre>
 * Part           1 ── N  PartIteration    (版本历史，通过 masterOid)
 * PartIteration  1 ── 1  CKFile          (主文件，通过 ckfileOid，不同版本可有不同文件)
 * PartIteration  1 ── N  CKAttachment    (附件，通过 ownerOid)
 * </pre>
 */
public class PartIteration extends IterationEntity implements TenantEntity {

    /** 单位（如：个、米、千克） */
    private String unit;

    /** 来源（如：自制、采购） */
    private String source;

    /** 租户 oid（引用 ck_tenant.oid） */
    private String tenantOid;

    // ==================== 构造方法 ====================

    public PartIteration() {
        super();
    }

    // ==================== Getter / Setter ====================

    public String getUnit() { return unit; }
    public void setUnit(String unit) { this.unit = unit; }

    public String getSource() { return source; }
    public void setSource(String source) { this.source = source; }

    @Override
    public String getTenantOid() { return tenantOid; }

    @Override
    public void setTenantOid(String tenantOid) { this.tenantOid = tenantOid; }

    @Override
    public String toString() {
        return "PartIteration{masterOid='" + getMasterOid() + "', version=" + getVersion() + "}";
    }
}
