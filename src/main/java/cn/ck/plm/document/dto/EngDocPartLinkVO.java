/*
 * Copyright (c) 2026~2028 深圳乘恺科技有限公司
 * All rights reserved.
 *
 * @author Kiween.Hu; Roney.Liu
 */

package cn.ck.plm.document.dto;

import java.time.LocalDateTime;

/**
 * 工程数据 ↔ 零部件构建规则视图对象（同时供两个方向使用）。
 *
 * <p>为什么不让前端自己拼：一条规则只有两端 oid，零件详情要显示「文档编号 + 名称 + 版本」，
 * 工程文档详情要显示「零件编号 + 名称 + 版本」，各查一次就是 N+1。
 * 这里由后端一次性补齐两端的可读信息，两个页面共用同一份 DTO。
 */
public class EngDocPartLinkVO {

    /** 关系 oid */
    private String oid;

    // ==================== 零件侧 ====================

    /** 零件主对象 oid */
    private String partOid;
    /** 零件编号 */
    private String partNumber;
    /** 零件名称 */
    private String partName;
    /** 零件迭代 oid */
    private String partIterationOid;
    /** 零件版本（如 A.2） */
    private String partVersion;

    // ==================== 工程数据侧 ====================

    /** 工程数据主对象 oid */
    private String engDocumentOid;
    /** 工程数据迭代 oid（跟随最新时返回解析到的实际迭代） */
    private String engDocIterationOid;
    /** 工程数据编号 */
    private String engDocNumber;
    /** 工程数据名称 */
    private String engDocName;
    /** 工程数据类型（3D 数模 / 2D 工程图 / 封装 / 图符） */
    private String engDocType;
    /** 工程数据版本（如 A.2） */
    private String engDocVersion;

    // ==================== 构建规则 ====================

    /** 关联类型：OWNER / CONTENT / CONTRIBUTING_IMAGE / IMAGE */
    private String assocType;
    private boolean buildStructure;
    private boolean buildAttribute;
    private boolean buildRepresentation;
    /** 关联来源：MANUAL / IMPORT / DERIVED */
    private String assocSource;
    /** 是否主关联 */
    private boolean primary;
    private int sortOrder;
    /** 是否锁定到具体工程数据版本（false = 跟随最新） */
    private boolean pinned;
    private LocalDateTime createdAt;

    public String getOid() { return oid; }
    public void setOid(String oid) { this.oid = oid; }

    public String getPartOid() { return partOid; }
    public void setPartOid(String partOid) { this.partOid = partOid; }

    public String getPartNumber() { return partNumber; }
    public void setPartNumber(String partNumber) { this.partNumber = partNumber; }

    public String getPartName() { return partName; }
    public void setPartName(String partName) { this.partName = partName; }

    public String getPartIterationOid() { return partIterationOid; }
    public void setPartIterationOid(String partIterationOid) { this.partIterationOid = partIterationOid; }

    public String getPartVersion() { return partVersion; }
    public void setPartVersion(String partVersion) { this.partVersion = partVersion; }

    public String getEngDocumentOid() { return engDocumentOid; }
    public void setEngDocumentOid(String engDocumentOid) { this.engDocumentOid = engDocumentOid; }

    public String getEngDocIterationOid() { return engDocIterationOid; }
    public void setEngDocIterationOid(String engDocIterationOid) { this.engDocIterationOid = engDocIterationOid; }

    public String getEngDocNumber() { return engDocNumber; }
    public void setEngDocNumber(String engDocNumber) { this.engDocNumber = engDocNumber; }

    public String getEngDocName() { return engDocName; }
    public void setEngDocName(String engDocName) { this.engDocName = engDocName; }

    public String getEngDocType() { return engDocType; }
    public void setEngDocType(String engDocType) { this.engDocType = engDocType; }

    public String getEngDocVersion() { return engDocVersion; }
    public void setEngDocVersion(String engDocVersion) { this.engDocVersion = engDocVersion; }

    public String getAssocType() { return assocType; }
    public void setAssocType(String assocType) { this.assocType = assocType; }

    public boolean isBuildStructure() { return buildStructure; }
    public void setBuildStructure(boolean buildStructure) { this.buildStructure = buildStructure; }

    public boolean isBuildAttribute() { return buildAttribute; }
    public void setBuildAttribute(boolean buildAttribute) { this.buildAttribute = buildAttribute; }

    public boolean isBuildRepresentation() { return buildRepresentation; }
    public void setBuildRepresentation(boolean buildRepresentation) { this.buildRepresentation = buildRepresentation; }

    public String getAssocSource() { return assocSource; }
    public void setAssocSource(String assocSource) { this.assocSource = assocSource; }

    public boolean isPrimary() { return primary; }
    public void setPrimary(boolean primary) { this.primary = primary; }

    public int getSortOrder() { return sortOrder; }
    public void setSortOrder(int sortOrder) { this.sortOrder = sortOrder; }

    public boolean isPinned() { return pinned; }
    public void setPinned(boolean pinned) { this.pinned = pinned; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
