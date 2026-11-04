/*
 * Copyright (c) 2026~2028 深圳乘恺科技有限公司
 * All rights reserved.
 *
 * @author Kiween.Hu; Roney.Liu
 */

package cn.ck.plm.part.entity;

import cn.ck.plm.base.entity.TenantEntity;
import cn.ck.plm.base.entity.WithoutVersionEntity;

/**
 * AML 行（Approved Manufacturer List，认可制造商清单），参考 Windchill 的
 * ManufacturerPart + ApprovedManufacturerList 模型。
 *
 * <p>回答「这个内部件，认可哪些制造商料号」：一个内部件（{@code ck_part}，三套资源库
 * 共用的主表）可以认可<b>多个</b>制造商料号，每个料号由「制造商 + 料号(mpn)」唯一确定。
 * 这是行业里"一个内部件号对应多家原厂替代料"的标准表述。
 *
 * <h3>Windchill 对应</h3>
 * <pre>
 * WTPart（内部件）              → partOid（ck_part.oid）
 * ManufacturerPart             → manufacturerOid + mpn（制造商 + 料号 即该对象的身份）
 * ApprovedManufacturerList     → PartAml 本身
 * (Preferred/Approved/Disallowed) → approvedStatus（三态枚举）
 * lifecycle（原厂生命周期状态）  → lifecycleState（ACTIVE / NRND / EOL …）
 * </pre>
 *
 * <h3>实体关系</h3>
 * <pre>
 * Part          1 ── N  PartAml   (partOid → Part.oid)          ※ PART 的软类型可为
 * Manufacturer  1 ── N  PartAml   (manufacturerOid → Manufacturer.oid)   ELECTRONIC / STD_PART / GEN_PART
 * PartAml       1 ── N  PartAvl   (amlOid → PartAml.oid)
 * </pre>
 *
 * <h3>为什么 approvedStatus 与 lifecycleState 是两列</h3>
 * <p>两者正交：一个被「认可」（APPROVED）的料号很可能已经 NRND（不推荐用于新设计）。
 * 采购需要的正是"认可但已进入寿命末期"这层信息——合成一列就丢了。
 *
 * <h3>DISALLOWED 不是删除</h3>
 * <p>禁用状态保留行与原因（note），用于采购排除与追溯；物理删除留给"录错了"的场景。
 */
public class PartAml extends WithoutVersionEntity implements TenantEntity {

    /** 内部件 oid（ck_part.oid；软类型可为 ELECTRONIC / STD_PART / GEN_PART） */
    private String partOid;

    /** 制造商 oid（ck_manufacturer.oid） */
    private String manufacturerOid;

    /** 制造商料号（Manufacturer Part Number）——与 manufacturerOid 共同构成该 AML 行的业务身份 */
    private String mpn;

    /** 原厂描述（与内部件的 description 区分：一个说内部用途，一个抄原厂口径） */
    private String mpnDesc;

    /** 认证状态（界面用词；原"认可状态"与值 APPROVED 同名、语义窄）：PREFERRED（首选）/ APPROVED（认可）/ DISALLOWED（禁用） */
    private String approvedStatus;

    /** 原厂生命周期：ACTIVE / NRND / EOL …（与 approvedStatus 正交，见类注释） */
    private String lifecycleState;

    /** 生效性配置 JSONB（预留日期/批次/序列号生效性，与 PartAlternateLink 同口径） */
    private String effectivityJson;

    /** 备注（DISALLOWED 时建议写明原因，供采购追溯） */
    private String note;

    /** 显示顺序 */
    private Integer sortOrder;

    /** 租户 oid（引用 ck_tenant.oid） */
    private String tenantOid;

    // ==================== 构造方法 ====================

    public PartAml() {
        super();
        this.approvedStatus = "APPROVED";
        this.sortOrder = 0;
    }

    // ==================== Getter / Setter ====================

    public String getPartOid() { return partOid; }
    public void setPartOid(String partOid) { this.partOid = partOid; }

    public String getManufacturerOid() { return manufacturerOid; }
    public void setManufacturerOid(String manufacturerOid) { this.manufacturerOid = manufacturerOid; }

    public String getMpn() { return mpn; }
    public void setMpn(String mpn) { this.mpn = mpn; }

    public String getMpnDesc() { return mpnDesc; }
    public void setMpnDesc(String mpnDesc) { this.mpnDesc = mpnDesc; }

    public String getApprovedStatus() { return approvedStatus; }
    public void setApprovedStatus(String approvedStatus) { this.approvedStatus = approvedStatus; }

    public String getLifecycleState() { return lifecycleState; }
    public void setLifecycleState(String lifecycleState) { this.lifecycleState = lifecycleState; }

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

    /** 是否为首选料号 */
    public boolean isPreferred() {
        return "PREFERRED".equalsIgnoreCase(approvedStatus);
    }

    /** 是否被禁用（采购排除用） */
    public boolean isDisallowed() {
        return "DISALLOWED".equalsIgnoreCase(approvedStatus);
    }

    @Override
    public String toString() {
        return "PartAml{partOid='" + partOid + "', manufacturerOid='" + manufacturerOid
                + "', mpn='" + mpn + "', approvedStatus='" + approvedStatus
                + "', lifecycleState='" + lifecycleState + "'}";
    }
}
