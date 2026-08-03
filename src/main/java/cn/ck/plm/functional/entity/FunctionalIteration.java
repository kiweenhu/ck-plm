/*
 * Copyright (c) 2025 深圳乘恺科技有限公司
 * All rights reserved.
 *
 * @author Kiween.Hu; Roney.Liu
 */

package cn.ck.plm.functional.entity;

import cn.ck.plm.base.entity.IterationEntity;
import cn.ck.plm.base.entity.TenantEntity;

/**
 * 功能架构（Functional）子版本实体。
 *
 * <p>每个 Functional 主对象可以有多个迭代版本，
 * 继承 {@link IterationEntity} 的版本控制体系（Revision/Iteration/CheckOut）。
 */
public class FunctionalIteration extends IterationEntity implements TenantEntity {

    /** 租户 */
    private String tenantOid;

    // ==================== 构造方法 ====================

    public FunctionalIteration() {}

    // ==================== Getter / Setter ====================

    @Override
    public String getTenantOid() { return tenantOid; }

    @Override
    public void setTenantOid(String tenantOid) { this.tenantOid = tenantOid; }

    @Override
    public String toString() {
        return "FunctionalIteration{masterOid='" + getMasterOid() +
                "', version='" + getVersion() + "'}";
    }
}
