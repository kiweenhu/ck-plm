/*
 * Copyright (c) 2025 深圳乘恺科技有限公司
 * All rights reserved.
 *
 * @author Kiween.Hu; Roney.Liu
 */

package cn.ck.plm.document.entity;

import cn.ck.plm.base.entity.MasterEntity;
import cn.ck.plm.base.entity.TenantEntity;

/**
 * 工程数据主数据对象（EngineeringDocument Master）—— 参照 Windchill {@code wt.epm.EPMDocumentMaster}。
 *
 * <p>「工程数据」是 3D 数模 / 2D 工程图 / 材料规格说明等工程对象的<b>统称</b>，
 * 并可向电子领域扩展（符号、封装等 EDA 设计数据），而非仅指 2D 图纸。
 *
 * <h3>与 Windchill 的对应</h3>
 * <pre>
 * EPMDocumentMaster  ←  本类（EngineeringDocument）
 * EPMDocument        ←  {@link EngineeringDocumentIteration}
 * </pre>
 *
 * <h3>为什么独立于 Document</h3>
 * <p>Windchill 中 EPMDocument（CAD 工程数据）与 WTDocument（通用文档）是<b>并列的两个类</b>，
 * 而非子类型关系：EPMDocument 具备 CAD 专有属性（CAD 名称 / CAD 类型 / 构建关系 / 参考关系），
 * 且由 CAD 工具（Creo、AutoCAD、SolidWorks、CATIA…）驱动创建与检入。
 *
 * <p>CK-PLM 沿用该设计：EngineeringDocument 为独立内置实体（typeKind=OOTB，rootTypeCode=ENG_DOCUMENT），
 * 拥有独立表 {@code ck_eng_document}，与通用文档 {@link Document} 并列，
 * 但<b>复用同一套基类能力</b>（{@link MasterEntity} 提供编码/名称/描述/容器，
 * {@link cn.ck.plm.base.entity.IterationEntity} 提供版本/检出/生命周期），无需重复实现。
 *
 * <h3>实体关系</h3>
 * <pre>
 * EngineeringDocument 1 ── N  EngineeringDocumentIteration  （版本历史，masterOid）
 * EngineeringDocumentIteration 1 ── 1  CKFile               （主文件，ckfileOid）
 * EngineeringDocument N ── M  Part                          （构建关联 EPMBuildRule，ck_eng_doc_part_link）
 * EngineeringDocument N ── M  EngineeringDocument           （装配成员 EPMMemberLink，ck_eng_doc_member_link）
 * EngineeringDocument N ── M  EngineeringDocument           （横向引用 EPMReferenceLink，ck_eng_doc_ref_link）
 * </pre>
 */
public class EngineeringDocument extends MasterEntity implements TenantEntity {

    /** 对象类型编码（ENG_DOCUMENT，或其软类型如 MODEL_3D / DRAWING_2D / MATERIAL_SPEC） */
    private String typeDefinitionCode;

    /** 所属文件夹 oid（引用 ck_folder.oid） */
    private String folderOid;

    /** 所属研发阶段 oid（引用 ck_stage.oid） */
    private String stageOid;

    /** 分类 oid（引用 ck_classification.oid） */
    private String clsOid;

    /** 租户 oid */
    private String tenantOid;

    public EngineeringDocument() {
        super();
    }

    public String getTypeDefinitionCode() { return typeDefinitionCode; }
    public void setTypeDefinitionCode(String typeDefinitionCode) { this.typeDefinitionCode = typeDefinitionCode; }

    public String getFolderOid() { return folderOid; }
    public void setFolderOid(String folderOid) { this.folderOid = folderOid; }

    public String getStageOid() { return stageOid; }
    public void setStageOid(String stageOid) { this.stageOid = stageOid; }

    public String getClsOid() { return clsOid; }
    public void setClsOid(String clsOid) { this.clsOid = clsOid; }

    @Override
    public String getTenantOid() { return tenantOid; }

    @Override
    public void setTenantOid(String tenantOid) { this.tenantOid = tenantOid; }

    @Override
    public String toString() {
        return "EngineeringDocument{name='" + getName() + "', number='" + getNumber()
                + "', typeDefCode='" + typeDefinitionCode + "'}";
    }
}
