/*
 * Copyright (c) 2025 深圳乘恺科技有限公司
 * All rights reserved.
 *
 * @author Kiween.Hu; Roney.Liu
 */

package cn.ck.plm.base.entity;

/**
 * 视图切换规则对象（ViewTransition），参考 Windchill View Association 模型。
 *
 * <p>定义从一种 {@link View} 切换到另一种 {@link View} 的规则，每条规则描述：
 * <ul>
 *   <li><b>fromViewCode / toViewCode</b>：源视图 → 目标视图的切换方向</li>
 *   <li><b>conditionStatus</b>：切换前需满足的生命周期状态（可选，为空表示无前置条件）</li>
 *   <li><b>conditionViewLatest</b>：是否要求目标视图的 IterationEntity 为最新小版本（默认 true）</li>
 * </ul>
 *
 * <h3>典型切换场景</h3>
 * <pre>
 * Design ──→ Manufacturing  (条件: Design 视图的 IterationEntity 处于 Released 状态)
 * Manufacturing ──→ Service (条件: Manufacturing 视图的 IterationEntity 为最新小版本)
 * Service ──→ Design        (无条件)
 * </pre>
 *
 * <h3>与 View 的关系</h3>
 * <pre>
 * View  1 ── N  ViewTransition (fromViewCode)   │  一条规则定义一个切换方向
 * View  1 ── N  ViewTransition (toViewCode)     │  双向切换需两条规则
 * </pre>
 *
 * <p>继承 {@link BaseEntity} 获得审计能力（oid / creator / createdAt / updater / updatedAt）。
 */
public class ViewTransition extends BaseEntity implements TenantEntity {

    /** 源视图编码（如 Design） */
    private String fromViewCode;

    /** 目标视图编码（如 Manufacturing） */
    private String toViewCode;

    /** 切换前需满足的生命周期状态编码（为空表示无前置条件） */
    private String conditionStatus;

    /** 是否要求目标视图的 IterationEntity 为最新小版本 */
    private boolean conditionViewLatest;

    /** 规则描述 */
    private String description;

    /** 排序序号 */
    private int sortOrder;

    /** 是否启用 */
    private boolean enabled;

    /** 租户 oid */
    private String tenantOid;

    // ==================== 构造方法 ====================

    public ViewTransition() {
        this.conditionViewLatest = true;
        this.enabled = true;
    }

    /**
     * 便捷构造：创建一条视图切换规则。
     *
     * @param fromViewCode 源视图编码
     * @param toViewCode   目标视图编码
     */
    public ViewTransition(String fromViewCode, String toViewCode) {
        this();
        this.fromViewCode = fromViewCode;
        this.toViewCode = toViewCode;
    }

    // ==================== 业务方法 ====================

    /**
     * 判断给定切换是否符合本规则。
     *
     * @param fromViewCode  源视图编码
     * @param toViewCode    目标视图编码
     * @return true 表示本规则匹配该切换方向
     */
    public boolean matches(String fromViewCode, String toViewCode) {
        return this.enabled
                && this.fromViewCode.equals(fromViewCode)
                && this.toViewCode.equals(toViewCode);
    }

    /**
     * 判断给定生命周期状态是否满足本规则的前置条件。
     *
     * @param currentStatus 当前生命周期状态编码
     * @return true 表示状态满足前置条件，允许切换
     */
    public boolean isConditionSatisfied(String currentStatus) {
        if (conditionStatus == null || conditionStatus.isEmpty()) {
            return true;
        }
        return conditionStatus.equals(currentStatus);
    }

    // ==================== Getter / Setter ====================

    public String getFromViewCode() { return fromViewCode; }
    public void setFromViewCode(String fromViewCode) { this.fromViewCode = fromViewCode; }

    public String getToViewCode() { return toViewCode; }
    public void setToViewCode(String toViewCode) { this.toViewCode = toViewCode; }

    public String getConditionStatus() { return conditionStatus; }
    public void setConditionStatus(String conditionStatus) { this.conditionStatus = conditionStatus; }

    public boolean isConditionViewLatest() { return conditionViewLatest; }
    public void setConditionViewLatest(boolean conditionViewLatest) { this.conditionViewLatest = conditionViewLatest; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

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
        return "ViewTransition{" + fromViewCode + " → " + toViewCode
                + (conditionStatus != null ? ", require=" + conditionStatus : "")
                + ", enabled=" + enabled + "}";
    }
}
