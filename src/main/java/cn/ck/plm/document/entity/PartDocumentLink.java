/*
 * Copyright (c) 2025 深圳乘恺科技有限公司
 * All rights reserved.
 *
 * @author Kiween.Hu; Roney.Liu
 */

package cn.ck.plm.document.entity;

import cn.ck.plm.base.entity.WithoutVersionEntity;
import cn.ck.plm.base.entity.TenantEntity;

/**
 * 部件关联文档（Part Document Link），参考 Windchill WTPartReferenceLink 语义。
 *
 * <p>描述部件与文档之间的全局关联关系，挂在部件主数据（Master）级别，
 * 继承 {@link WithoutVersionEntity}，不需要自身的版本控制。
 *
 * <h3>关联类型（linkType）</h3>
 * <pre>
 * REFERENCE    参考文档 —— 设计/制造过程中供查阅的参考资料（图纸、规范、手册等）
 * DESCRIPTION  说明文档 —— 描述部件本身用途、安装、维护等说明性文档
 * </pre>
 *
 * <h3>防重复</h3>
 * 数据库层通过 (part_oid, document_oid, link_type) 唯一约束防止重复关联。
 */
public class PartDocumentLink extends WithoutVersionEntity implements TenantEntity {

    /** 关联类型：参考文档 */
    public static final String TYPE_REFERENCE = "REFERENCE";
    /** 关联类型：说明文档 */
    public static final String TYPE_DESCRIPTION = "DESCRIPTION";

    /** 部件主数据 oid（关联 ck_part.oid） */
    private String partOid;

    /** 文档主数据 oid（关联 ck_document.oid） */
    private String documentOid;

    /** 关联类型：REFERENCE（参考）/ DESCRIPTION（说明） */
    private String linkType;

    /** 是否启用 */
    private Boolean enabled;

    /** 租户 oid（引用 ck_tenant.oid） */
    private String tenantOid;

    // ==================== 构造方法 ====================

    public PartDocumentLink() {
        super();
        this.enabled = true;
    }

    // ==================== Getter / Setter ====================

    public String getPartOid() { return partOid; }
    public void setPartOid(String partOid) { this.partOid = partOid; }

    public String getDocumentOid() { return documentOid; }
    public void setDocumentOid(String documentOid) { this.documentOid = documentOid; }

    public String getLinkType() { return linkType; }
    public void setLinkType(String linkType) { this.linkType = linkType; }

    /**
     * 判断关联是否生效启用。
     * <p>注意：不可命名为 {@code isEnabled()}——与 {@link #getEnabled()}（Boolean）构成
     * JavaBeans 规范中的模糊重载 getter，会导致 MyBatis 反射报
     * {@code Illegal overloaded getter method} 异常。
     */
    public boolean isEffectivelyEnabled() {
        return enabled == null || enabled;
    }

    public Boolean getEnabled() { return enabled; }
    public void setEnabled(Boolean enabled) { this.enabled = enabled; }

    @Override
    public String getTenantOid() { return tenantOid; }

    @Override
    public void setTenantOid(String tenantOid) { this.tenantOid = tenantOid; }

    @Override
    public String toString() {
        return "PartDocumentLink{partOid='" + partOid
                + "', documentOid='" + documentOid
                + "', linkType='" + linkType
                + "', enabled=" + enabled + "}";
    }
}
