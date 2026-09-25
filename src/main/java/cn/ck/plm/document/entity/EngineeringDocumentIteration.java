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
 * 工程数据子版本数据对象（EngineeringDocument Iteration）—— 参照 Windchill {@code wt.epm.EPMDocument}。
 *
 * <p>继承 {@link IterationEntity} 的全部版本字段（masterOid / revision / iteration /
 * displayVersion / checkedOut / latest / status / lifecycleTemplateIterationOid），
 * 新增 CAD 工程数据专有属性。
 *
 * <h3>Windchill EPMDocument 属性对照</h3>
 * <table border="1">
 *   <tr><th>EPMDocument 属性</th><th>本类字段</th><th>说明</th></tr>
 *   <tr><td>CADName</td><td>{@code cadName}</td><td>CAD 系统内的模型/图纸名称</td></tr>
 *   <tr><td>EPMDocumentType</td><td>{@code cadType}</td><td>CAD 类型：DRW 图纸 / MODEL 模型 / ASM 装配</td></tr>
 *   <tr><td>Author（作者应用/作者）</td><td>{@code cadTool} / {@code author}</td><td>来源 CAD 工具与设计者</td></tr>
 *   <tr><td>主内容（primary content）</td><td>{@code ckfileOid}</td><td>工程数据主文件（DWG/DXF/PDF/SLDDRW…）</td></tr>
 *   <tr><td>附加内容（secondary）</td><td>CKAttachment（ownerOid）</td><td>派生文件：STEP/IGES/TIFF/预览图</td></tr>
 *   <tr><td>EPMBuildRule（Association Type）</td><td>{@code ck_eng_doc_part_link.assoc_type}</td><td>
 *       本工程数据与零部件的构建关联：OWNER / CONTRIBUTING_IMAGE / IMAGE / CONTRIBUTING_CONTENT / CONTENT；
 *       关联类型决定结构 / 属性 / 表示三条 Build Link</td></tr>
 *   <tr><td>EPMMemberLink</td><td>{@code ck_eng_doc_member_link}</td><td>
 *       装配成员（BOM 结构）：本工程数据作为父装配 uses 的子件，必有 quantity
 *       （含放置/变换/固定/替代/抑制/阵列等成员属性）</td></tr>
 *   <tr><td>EPMReferenceLink</td><td>{@code ck_eng_doc_ref_link}</td><td>
 *       横向引用：本工程数据 referencedBy 引用的其它工程数据 / 通用文档，必有 referenceType</td></tr>
 * </table>
 *
 * <p>此外补充 2D 工程图必备的制图属性：图幅、比例、图号、图纸张数、投影方式等
 * （仅 DRAWING_2D 等 2D 软类型使用；3D 数模、材料规格说明等其它软类型为 NULL）。
 */
public class EngineeringDocumentIteration extends IterationEntity implements TenantEntity {

    /** 工程数据主文件 oid（指向 CKFile，存储该版本主文件物理元信息，不同版本可关联不同文件） */
    private String ckfileOid;

    /** CAD 名称（EPMDocument.CADName）：CAD 系统内的名称，如 part_a.prt / assy_top.dwg */
    private String cadName;

    /** CAD 类型（EPMDocumentType）：DRW=图纸 / MODEL=模型 / ASM=装配 / FORM=格式 */
    private String cadType;

    /** 来源 CAD 工具：AutoCAD / SolidWorks / CATIA / Creo / CAXA 等 */
    private String cadTool;

    /** 图幅：A0 / A1 / A2 / A3 / A4 */
    private String sheetSize;

    /** 比例：如 1:1、1:2、2:1 */
    private String scale;

    /** 图号（图纸编号，区别于对象编码） */
    private String sheetNumber;

    /** 图纸张数 */
    private Integer sheetCount;

    /** 投影方式：FIRST=第一角法 / THIRD=第三角法 */
    private String projection;

    /** 设计者（EPMDocument.author） */
    private String author;

    /** 材料 */
    private String material;

    /** 重量（文本，含单位：如 1.2kg） */
    private String weight;

    /** 租户 oid */
    private String tenantOid;

    // ==================== 构造方法 ====================

    public EngineeringDocumentIteration() {
        super();
    }

    // ==================== Getter / Setter ====================

    public String getCkfileOid() { return ckfileOid; }
    public void setCkfileOid(String ckfileOid) { this.ckfileOid = ckfileOid; }

    public String getCadName() { return cadName; }
    public void setCadName(String cadName) { this.cadName = cadName; }

    public String getCadType() { return cadType; }
    public void setCadType(String cadType) { this.cadType = cadType; }

    public String getCadTool() { return cadTool; }
    public void setCadTool(String cadTool) { this.cadTool = cadTool; }

    public String getSheetSize() { return sheetSize; }
    public void setSheetSize(String sheetSize) { this.sheetSize = sheetSize; }

    public String getScale() { return scale; }
    public void setScale(String scale) { this.scale = scale; }

    public String getSheetNumber() { return sheetNumber; }
    public void setSheetNumber(String sheetNumber) { this.sheetNumber = sheetNumber; }

    public Integer getSheetCount() { return sheetCount; }
    public void setSheetCount(Integer sheetCount) { this.sheetCount = sheetCount; }

    public String getProjection() { return projection; }
    public void setProjection(String projection) { this.projection = projection; }

    public String getAuthor() { return author; }
    public void setAuthor(String author) { this.author = author; }

    public String getMaterial() { return material; }
    public void setMaterial(String material) { this.material = material; }

    public String getWeight() { return weight; }
    public void setWeight(String weight) { this.weight = weight; }

    @Override
    public String getTenantOid() { return tenantOid; }

    @Override
    public void setTenantOid(String tenantOid) { this.tenantOid = tenantOid; }

    @Override
    public String toString() {
        return "EngineeringDocumentIteration{masterOid='" + getMasterOid()
                + "', version=" + getVersion() + ", cadName='" + cadName + "'}";
    }
}
