/*
 * Copyright (c) 2026~2028 深圳乘恺科技有限公司
 * All rights reserved.
 *
 * @author Kiween.Hu; Roney.Liu
 */

package cn.ck.plm.part.entity;

import cn.ck.plm.base.entity.TenantEntity;
import cn.ck.plm.base.entity.WithoutVersionEntity;
import java.math.BigDecimal;

/**
 * AVL 行（Approved Vendor List，认可供应商清单），参考 Windchill 的 ApprovedVendorList 模型。
 *
 * <p>回答「这个制造商料号，能从哪些供应商拿货」：<b>挂在 AML 行上，不是挂在内部件上</b> ——
 * 因为"从哪家买"是某个制造商料号的性质，换料号就换渠道。这是 AML/AVL 分层的关键，
 * 把两层压平（内部件直接连供应商）会丢掉"哪家供应商供的是哪个原厂料"这层事实。
 *
 * <h3>Windchill 对应</h3>
 * <pre>
 * ManufacturerPart / AML 行      → amlOid（ck_part_aml.oid）
 * Vendor                         → supplierOid（ck_supplier.oid）
 * vendorPartNumber               → supplierPartNo（供应商侧料号，对账与下单用）
 * preferredVendor                → preferred（首选货源）
 * </pre>
 *
 * <h3>实体关系</h3>
 * <pre>
 * PartAml   1 ── N  PartAvl   (amlOid → PartAml.oid，级联删除)
 * Supplier  1 ── N  PartAvl   (supplierOid → Supplier.oid)
 * </pre>
 *
 * <h3>★ 本表补上了行业包声明里的一个空洞</h3>
 * <p>{@code electronics-core/attributes.json} 的 SUPPLIER_INFO 组声明了
 * {@code manufacturer / mpn / supplier / supplierCount / leadTime / moq / spq}，其中
 * {@code supplierCount.derivedBy = count(distinct supplierRefs)} —— 而 {@code supplierRefs}
 * <b>一直没有落地的表</b>，于是 {@code smart-hardware-ipd/rules.json} 的
 * R-SH-11「单一来源器件风险提示」（field: supplierCount）也就永远无法求值。
 *
 * <p>本表就是那个 supplierRefs，派生口径为：
 * <pre>
 * supplierCount(内部件) = count(distinct ck_part_avl.supplier_oid)
 *                         where aml_oid in (该内部件的全部 AML 行)
 * </pre>
 * 即"某内部件合计只剩 1 家供应商"时命中单一来源风险。
 *
 * <h3>preferred 不做唯一约束</h3>
 * <p>现实里常见"双源"（两家都标首选），由使用方按业务判断；结构上硬拦反而让人改不动
 * （与 DashboardTemplate 的 primary_scope 同口径）。
 */
public class PartAvl extends WithoutVersionEntity implements TenantEntity {

    /** 所属 AML 行 oid（ck_part_aml.oid） */
    private String amlOid;

    /** 供应商 oid（ck_supplier.oid） */
    private String supplierOid;

    /** 供应商侧料号（对账与下单用） */
    private String supplierPartNo;

    /** 是否首选货源 */
    private Boolean preferred;

    /** 参考单价 */
    private BigDecimal price;

    /** 币种（CNY / USD …） */
    private String currency;

    /** 交期（天）——与 IBA 的 leadTime 对应，落地后属性可改为从本表派生 */
    private Integer leadTimeDays;

    /** 最小起订量（与 IBA 的 moq 对应） */
    private Integer moq;

    /** 最小包装量（与 IBA 的 spq 对应） */
    private Integer spq;

    /** 生效性配置 JSONB（预留日期/批次/序列号生效性，与 PartAlternateLink 同口径） */
    private String effectivityJson;

    /** 备注 */
    private String note;

    /** 显示顺序 */
    private Integer sortOrder;

    /** 租户 oid（引用 ck_tenant.oid） */
    private String tenantOid;

    // ==================== 构造方法 ====================

    public PartAvl() {
        super();
        this.preferred = false;
        this.sortOrder = 0;
    }

    // ==================== Getter / Setter ====================

    public String getAmlOid() { return amlOid; }
    public void setAmlOid(String amlOid) { this.amlOid = amlOid; }

    public String getSupplierOid() { return supplierOid; }
    public void setSupplierOid(String supplierOid) { this.supplierOid = supplierOid; }

    public String getSupplierPartNo() { return supplierPartNo; }
    public void setSupplierPartNo(String supplierPartNo) { this.supplierPartNo = supplierPartNo; }

    public Boolean getPreferred() { return preferred; }
    public void setPreferred(Boolean preferred) { this.preferred = preferred; }

    public BigDecimal getPrice() { return price; }
    public void setPrice(BigDecimal price) { this.price = price; }

    public String getCurrency() { return currency; }
    public void setCurrency(String currency) { this.currency = currency; }

    public Integer getLeadTimeDays() { return leadTimeDays; }
    public void setLeadTimeDays(Integer leadTimeDays) { this.leadTimeDays = leadTimeDays; }

    public Integer getMoq() { return moq; }
    public void setMoq(Integer moq) { this.moq = moq; }

    public Integer getSpq() { return spq; }
    public void setSpq(Integer spq) { this.spq = spq; }

    public String getEffectivityJson() { return effectivityJson; }
    public void setEffectivityJson(String effectivityJson) { this.effectivityJson = effectivityJson; }

    public String getNote() { return note; }
    public void setNote(String note) { this.note = note; }

    public Integer getSortOrder() { return sortOrder; }
    public void setSortOrder(Integer sortOrder) { this.sortOrder = sortOrder; }

    @Override
    public String getTenantOid() { return tenantOid; }

    @Override
    public void setTenantOid(String tenantOid) { this.tenantOid = tenantOid; }

    // ==================== 便捷方法 ====================

    /** 是否为首选货源（命名理由同 Manufacturer#isEffectivelyEnabled） */
    public boolean isEffectivelyPreferred() {
        return preferred != null && preferred;
    }

    @Override
    public String toString() {
        return "PartAvl{amlOid='" + amlOid + "', supplierOid='" + supplierOid
                + "', supplierPartNo='" + supplierPartNo + "', preferred=" + preferred
                + ", price=" + price + " " + currency + ", leadTimeDays=" + leadTimeDays + "}";
    }
}
