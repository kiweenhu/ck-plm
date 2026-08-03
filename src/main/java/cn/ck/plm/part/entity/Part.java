/*
 * Copyright (c) 2025 深圳乘恺科技有限公司
 * All rights reserved.
 *
 * @author Kiween.Hu; Roney.Liu
 */

package cn.ck.plm.part.entity;

import cn.ck.plm.base.entity.MasterEntity;
import cn.ck.plm.base.entity.TenantEntity;

/**
 * 部件主数据对象（Part Master），参考 Windchill WTPartMaster 模型。
 *
 * <p>作为部件业务模块的版本主对象，继承 {@link MasterEntity} 获得核心标识属性。
 * 所有业务方法由独立的 {@link cn.ck.plm.part.service.impl.PartServiceImpl} 提供。
 *
 * <h3>Windchill 对应</h3>
 * <pre>
 * Persistable → BaseEntity → MasterEntity → Part(this)           ←  wt.part.WTPartMaster
 * Persistable → BaseEntity → IterationEntity → PartIteration      ←  wt.part.WTPart
 * </pre>
 *
 * <h3>实体关系</h3>
 * <pre>
 * Part  1 ── 1  TypeDefinition   (通过 typeDefinitionCode → TypeDefinition.code，部件所属类型)
 * Part  1 ── N  PartIteration    (通过 masterOid，版本历史)
 * PartIteration  1 ── 1  CKFile  (通过 ckfileOid，该版本的主文件)
 * PartIteration  1 ── N  CKAttachment (通过 ownerOid，附件)
 * </pre>
 */
public class Part extends MasterEntity implements TenantEntity {

    /** 类型定义编码，关联 {@link cn.ck.plm.softtype.entity.TypeDefinition#code} */
    private String typeDefinitionCode;

    /** 所属文件夹 oid */
    private String folderOid;

    /** 所处研发阶段 oid */
    private String stageOid;

    /** 分类 oid（关联 ck_classification.oid） */
    private String classificationOid;

    /** 租户 oid（引用 ck_tenant.oid） */
    private String tenantOid;

    // ==================== 构造方法 ====================

    public Part() {
        super();
    }

    // ==================== Getter / Setter ====================

    public String getTypeDefinitionCode() { return typeDefinitionCode; }
    public void setTypeDefinitionCode(String typeDefinitionCode) { this.typeDefinitionCode = typeDefinitionCode; }

    public String getFolderOid() { return folderOid; }
    public void setFolderOid(String folderOid) { this.folderOid = folderOid; }

    public String getStageOid() { return stageOid; }
    public void setStageOid(String stageOid) { this.stageOid = stageOid; }

    public String getClassificationOid() { return classificationOid; }
    public void setClassificationOid(String classificationOid) { this.classificationOid = classificationOid; }

    @Override
    public String getTenantOid() { return tenantOid; }

    @Override
    public void setTenantOid(String tenantOid) { this.tenantOid = tenantOid; }

    @Override
    public String toString() {
        return "Part{name='" + getName() + "', number='" + getNumber()
                + "', typeDefCode='" + typeDefinitionCode + "'}";
    }
}
