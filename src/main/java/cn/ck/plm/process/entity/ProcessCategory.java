/*
 * Copyright (c) 2025 深圳乘恺科技有限公司
 * All rights reserved.
 *
 * @author Kiween.Hu; Roney.Liu
 */

package cn.ck.plm.process.entity;

import cn.ck.plm.base.entity.BaseEntity;
import cn.ck.plm.base.entity.TenantEntity;

/**
 * 流程分组（分类）字典 —— 流程清单页的第一层组织维度。
 *
 * <p>使用流程是「先建分组 → 选中分组 → 在组内设计流程」：分组是流程清单的导航骨架，
 * 因此它必须是一份<b>可维护的字典</b>，而不是模板上一串自由输入的文本 ——
 * 后者会产生「研发 / 研发部 / 研发中心」这类近义分裂，且改名要逐条改模板。
 *
 * <p><b>与模板的关联方式</b>：{@code ck_process_template.category_oid} 引用本表的 {@link #oid}。
 * 取 oid 而不是名称：
 * <ul>
 *   <li>分组是清单页的导航维度，将来还可能承载权限 / 可见性 / 外部引用 —— 引用必须<b>稳定</b>，
 *       改个显示名不该牵动任何一张流程模板（模板表里也不再保留名称冗余，
 *       从根上消除"改了字典、忘了同步模板"这类两处真相）；</li>
 *   <li>名称的唯一性只用来<b>避免重名</b>（租户内唯一），不再充当外键。</li>
 * </ul>
 *
 * <p><b>与 DSL 的关系</b>：流程 DSL 的 {@code meta.category} 仍是<b>可读的名称</b>
 * （DSL 要能脱离数据库读懂），但它只是创建时的快照、只用于导出展示 ——
 * <b>归属判定一律以模板表的 {@code category_oid} 为准</b>，设计器也不再提供该字段。
 *
 * <p>名称在<b>租户内</b>唯一（{@code (tenant_oid, name)} 复合唯一索引）。注意不能做成全局唯一：
 * 多租户下 A 租户建了「研发」，B 租户就建不了 —— 这与
 * {@code ck_process_template} 的 {@code (key, tenant_oid)} 口径保持一致。
 */
public class ProcessCategory extends BaseEntity implements TenantEntity {

    /** 分组名（租户内唯一） */
    private String name;

    /** 排序序号（越小越靠前；同序号按名称排） */
    private Integer sortOrder;

    /** 说明（可空） */
    private String description;

    /** 租户 oid */
    private String tenantOid;

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public Integer getSortOrder() {
        return sortOrder;
    }

    public void setSortOrder(Integer sortOrder) {
        this.sortOrder = sortOrder;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    @Override
    public String getTenantOid() {
        return tenantOid;
    }

    @Override
    public void setTenantOid(String tenantOid) {
        this.tenantOid = tenantOid;
    }
}
