/*
 * Copyright (c) 2025 深圳乘恺科技有限公司
 * All rights reserved.
 *
 * @author Kiween.Hu; Roney.Liu
 */

package cn.ck.plm.document.entity;

import cn.ck.plm.base.entity.IterationEntity;
import cn.ck.plm.base.entity.TenantEntity;

/**
 * 文档子版本数据对象（Document Iteration），参考 Windchill WTDocument 模型。
 *
 * <p>继承 {@link IterationEntity} 的全部版本字段，新增文档特有的文件属性。
 * 业务方法（updateFrom 文档字段拷贝等）由独立的
 * {@link cn.ck.plm.document.service.impl.DocumentIterationServiceImpl} 提供。
 *
 * <h3>Windchill 对应</h3>
 * <pre>
 * Persistable → BaseEntity → IterationEntity → DocumentIteration(this)  ←  wt.doc.WTDocument
 * </pre>
 *
 * <h3>实体关系</h3>
 * <pre>
 * Document         1 ── N  DocumentIteration  (版本历史，通过 masterOid)
 * DocumentIteration  1 ── 1  CKFile          (主文档文件，通过 ckfileOid，不同版本可有不同主文档)
 * DocumentIteration  1 ── N  CKAttachment    (附件，通过 ownerOid)
 * </pre>
 */
public class DocumentIteration extends IterationEntity implements TenantEntity {

    /** 主文档文件 oid（指向 {@link cn.ck.plm.base.entity.CKFile}，存储该版本主文档物理文件元信息，不同迭代版本可关联不同主文档文件） */
    private String ckfileOid;

    /** 租户 oid（引用 ck_tenant.oid） */
    private String tenantOid;

    // ==================== 构造方法 ====================

    public DocumentIteration() {
        super();
    }

    // ==================== Getter / Setter ====================

    public String getCkfileOid() { return ckfileOid; }
    public void setCkfileOid(String ckfileOid) { this.ckfileOid = ckfileOid; }

    @Override
    public String getTenantOid() { return tenantOid; }

    @Override
    public void setTenantOid(String tenantOid) { this.tenantOid = tenantOid; }

    @Override
    public String toString() {
        return "DocumentIteration{masterOid='" + getMasterOid() + "', version=" + getVersion() + "}";
    }
}
