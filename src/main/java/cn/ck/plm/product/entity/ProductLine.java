/*
 * Copyright (c) 2025 深圳乘恺科技有限公司
 * All rights reserved.
 *
 * @author Kiween.Hu; Roney.Liu
 */

package cn.ck.plm.product.entity;

import cn.ck.plm.base.entity.TenantEntity;
import cn.ck.plm.base.entity.WithoutVersionEntity;
import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.List;

/**
 * 产品系列实体（ProductLine），无需版本控制，继承 {@link WithoutVersionEntity}。
 *
 * <p>产品系列用于将关联产品归类管理，记录缩略图与负责团队信息。
 * 业务方法由独立的 Service 层提供。
 *
 * <h3>关系</h3>
 * <pre>
 * ProductLine  N ── 1  ProductLine  (parentOid 自引用，支持多级树形结构)
 * ProductLine  1 ── 1  Team         (通过 teamOid 关联)
 * Team         1 ── N  TeamMember
 * </pre>
 *
 * <h3>继承链条</h3>
 * <pre>
 * BaseEntity → WithoutVersionEntity → ProductLine(this)
 * </pre>
 *
 * <h3>使用示例</h3>
 * <pre>{@code
 * Team team = new Team();
 * team.setCode("TEAM-001");
 * team.setName("产品A组");
 *
 * ProductLine line = new ProductLine();
 * line.setCode("PL-001");
 * line.setName("智能家居系列");
 * line.setThumbnail("/images/pl-001.png");
 * line.setTeamOid(team.getOid());
 * }</pre>
 */
public class ProductLine extends WithoutVersionEntity implements TenantEntity {

    /** 缩略图（图片路径或URL） */
    private String thumbnail;

    /** 关联团队 oid */
    private String teamOid;

    /** 父级产品线 oid（自引用，支持多级树形结构，null 表示根节点） */
    private String parentOid;

    /** 子节点列表（仅用于树形查询返回，不持久化） */
    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    private transient List<ProductLine> children;

    /** 节点类型（PRODUCT_LINE = 产品系列，PRODUCT_MODEL = 产品型号），用于树形结构区分 */
    private transient String nodeType;

    /** 类型图标（来自 TypeDefinition.icon），用于树形结构渲染 */
    private transient String icon;

    /** 租户 oid（引用 ck_tenant.oid） */
    private String tenantOid;

    // ==================== 构造方法 ====================

    public ProductLine() {
        super();
    }

    // ==================== Getter / Setter ====================

    public String getThumbnail() { return thumbnail; }
    public void setThumbnail(String thumbnail) { this.thumbnail = thumbnail; }

    public String getTeamOid() { return teamOid; }
    public void setTeamOid(String teamOid) { this.teamOid = teamOid; }

    public String getParentOid() { return parentOid; }
    public void setParentOid(String parentOid) { this.parentOid = parentOid; }

    public List<ProductLine> getChildren() { return children; }
    public void setChildren(List<ProductLine> children) { this.children = children; }

    public String getNodeType() { return nodeType; }
    public void setNodeType(String nodeType) { this.nodeType = nodeType; }

    public String getIcon() { return icon; }
    public void setIcon(String icon) { this.icon = icon; }

    @Override
    public String getTenantOid() { return tenantOid; }

    @Override
    public void setTenantOid(String tenantOid) { this.tenantOid = tenantOid; }

    @Override
    public String toString() {
        return "ProductLine{code='" + getCode() + "', name='" + getName()
                + "', teamOid='" + teamOid + "', parentOid='" + parentOid
                + "'}";
    }
}
