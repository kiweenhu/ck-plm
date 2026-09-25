/*
 * Copyright (c) 2025 深圳乘恺科技有限公司
 * All rights reserved.
 *
 * @author Kiween.Hu; Roney.Liu
 */

package cn.ck.plm.product.entity;

import cn.ck.plm.base.entity.CKContainer;
import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.List;

/**
 * 产品系列实体（ProductLine），无需版本控制，继承 {@link CKContainer}。
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
 * BaseEntity → WithoutVersionEntity → CKContainer → ProductLine(this)
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
public class ProductLine extends CKContainer {

    /** 节点类型（PRODUCT_LINE = 产品系列，PRODUCT_MODEL = 产品型号），用于树形结构区分 */
    private transient String nodeType;

    /** 类型图标（来自 TypeDefinition.icon），用于树形结构渲染 */
    private transient String icon;

    // ==================== 构造方法 ====================

    public ProductLine() {
        super();
    }

    // ==================== Getter / Setter ====================
    // 说明：thumbnail / teamOid / parentOid / tenantOid / deleteMark / children 均由基类 CKContainer 提供

    public List<ProductLine> getProductChildren() {
        return getChildren();
    }

    public String getNodeType() { return nodeType; }
    public void setNodeType(String nodeType) { this.nodeType = nodeType; }

    public String getIcon() { return icon; }
    public void setIcon(String icon) { this.icon = icon; }

    @Override
    public String toString() {
        return "ProductLine{code='" + getCode() + "', name='" + getName()
                + "', teamOid='" + getTeamOid() + "', parentOid='" + getParentOid()
                + "'}";
    }
}
