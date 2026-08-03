/*
 * Copyright (c) 2025 深圳乘恺科技有限公司
 * All rights reserved.
 *
 * @author Kiween.Hu; Roney.Liu
 */

package cn.ck.plm.base.entity;

/**
 * 视图对象（View），参考 Windchill View 模型。
 *
 * <p>视图定义了一个业务视角（如 Design / Manufacturing / Service），
 * 用于在多版本迭代中筛选出该视角下可见的 {@link IterationEntity}。
 *
 * <h3>典型用法</h3>
 * <ul>
 *   <li><b>Design（设计视图）</b>：展示最新 Working 状态的迭代</li>
 *   <li><b>Manufacturing（制造视图）</b>：展示最新 Released 状态的迭代</li>
 *   <li><b>Service（服务视图）</b>：展示最新 UnderReview 状态的迭代</li>
 * </ul>
 *
 * <h3>与 IterationEntity 的关系</h3>
 * <pre>
 * View  1 ── N  IterationEntity  (通过 View 筛选同一 MasterEntity 下不同生命周期状态的 IterationEntity)
 * </pre>
 * 同一个 MasterEntity 在不同 View 下可能展示不同 IterationEntity：
 * MasterEntity(A) → Design → A.3 (Working) / Manufacturing → A.1 (Released)
 *
 * <p>继承 {@link WithoutVersionEntity} 获得 code / name / description 标准字段及审计能力，
 * 与 {@link LifecycleStatus} 配合实现视图级版本筛选。
 */
public class View extends WithoutVersionEntity implements TenantEntity {

    /** 排序序号 */
    private int sortOrder;

    /** 是否启用 */
    private boolean enabled;

    /** 租户 oid */
    private String tenantOid;

    // ==================== 构造方法 ====================

    public View() {
        this.enabled = true;
    }

    // ==================== Getter / Setter ====================

    public int getSortOrder() { return sortOrder; }
    public void setSortOrder(int sortOrder) { this.sortOrder = sortOrder; }

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }

    @Override
    public String getTenantOid() { return tenantOid; }

    @Override
    public void setTenantOid(String tenantOid) { this.tenantOid = tenantOid; }

    @Override
    public String toString() {
        return "View{code='" + getCode() + "', name='" + getName() + "'}";
    }
}
