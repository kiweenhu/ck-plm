/*
 * Copyright (c) 2025 深圳乘恺科技有限公司
 * All rights reserved.
 *
 * @author Kiween.Hu; Roney.Liu
 */

package cn.ck.plm.bom.entity;

import cn.ck.plm.base.entity.BaseEntity;
import cn.ck.plm.base.entity.TenantEntity;

/**
 * 成组替代成员（BOM Substitute Group Member）。
 *
 * <p>表达成组替代（{@link BomSubstituteGroup}）的 N:M 成员关系：
 * 原料侧（{@code SOURCE}）挂多个被替换的 BOM 行成员，替代侧（{@code SUBSTITUTE}）
 * 挂一组替代物料，每个成员带数量因子。与组头的"整组替换"约束配合，
 * 完整表达"多个分立元件被一组物料整组替换"的语义。
 *
 * <p>继承 {@link BaseEntity}，纯关系行，无 code/name，不需要版本控制。
 *
 * <h3>实体关系</h3>
 * <pre>
 * BomSubstituteGroup  1 ── N  BomSubstituteGroupMember  (groupOid → BomSubstituteGroup.oid)
 * BomLinks            1 ── N  BomSubstituteGroupMember  (bomLinkOid → BomLinks.oid，原料侧)
 * Part                1 ── N  BomSubstituteGroupMember  (partOid → Part.oid，替代侧)
 * </pre>
 *
 * <h3>成员侧（memberSide）</h3>
 * <pre>
 * SOURCE      原料侧 —— 被替换的原始 BOM 行（bomLinkOid 有值，partOid 为空）
 * SUBSTITUTE  替代侧 —— 替换上去的替代物料（partOid 有值，bomLinkOid 为空）
 * </pre>
 */
public class BomSubstituteGroupMember extends BaseEntity implements TenantEntity {

    /** 所属成组替代组头 oid（关联 ck_bom_substitute_group.oid） */
    private String groupOid;

    /** 成员侧：SOURCE（原料侧）/ SUBSTITUTE（替代侧） */
    private String memberSide;

    /** 原料侧引用的 BOM 行 oid（关联 ck_bom_links.oid，SOURCE 侧使用） */
    private String bomLinkOid;

    /** 替代侧挂的替代物料 oid（关联 ck_part.oid，SUBSTITUTE 侧使用） */
    private String partOid;

    /** 数量因子（如 1 个原子件 = 3 个替代件，或 2:1 换算） */
    private Double quantity;

    /** 单位 */
    private String unit;

    /** 成员排序号（原料侧/替代侧内部的展示顺序） */
    private Integer sortOrder;

    /** 租户 oid（引用 ck_tenant.oid） */
    private String tenantOid;

    // ==================== 构造方法 ====================

    public BomSubstituteGroupMember() {
        super();
        this.quantity = 1.0;
    }

    // ==================== Getter / Setter ====================

    public String getGroupOid() { return groupOid; }
    public void setGroupOid(String groupOid) { this.groupOid = groupOid; }

    public String getMemberSide() { return memberSide; }
    public void setMemberSide(String memberSide) { this.memberSide = memberSide; }

    public String getBomLinkOid() { return bomLinkOid; }
    public void setBomLinkOid(String bomLinkOid) { this.bomLinkOid = bomLinkOid; }

    public String getPartOid() { return partOid; }
    public void setPartOid(String partOid) { this.partOid = partOid; }

    public Double getQuantity() { return quantity; }
    public void setQuantity(Double quantity) { this.quantity = quantity; }

    public String getUnit() { return unit; }
    public void setUnit(String unit) { this.unit = unit; }

    public Integer getSortOrder() { return sortOrder; }
    public void setSortOrder(Integer sortOrder) { this.sortOrder = sortOrder; }

    @Override
    public String getTenantOid() { return tenantOid; }

    @Override
    public void setTenantOid(String tenantOid) { this.tenantOid = tenantOid; }

    // ==================== 便捷方法 ====================

    /** 判断是否为原料侧成员 */
    public boolean isSource() {
        return "SOURCE".equalsIgnoreCase(memberSide);
    }

    /** 判断是否为替代侧成员 */
    public boolean isSubstitute() {
        return "SUBSTITUTE".equalsIgnoreCase(memberSide);
    }

    @Override
    public String toString() {
        return "BomSubstituteGroupMember{groupOid='" + groupOid
                + "', memberSide='" + memberSide
                + "', bomLinkOid='" + bomLinkOid
                + "', partOid='" + partOid
                + "', quantity=" + quantity
                + ", unit='" + unit
                + "', sortOrder=" + sortOrder + "}";
    }
}
