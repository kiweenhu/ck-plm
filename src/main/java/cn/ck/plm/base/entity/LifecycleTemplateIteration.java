/*
 * Copyright (c) 2025 深圳乘恺科技有限公司
 * All rights reserved.
 *
 * @author Kiween.Hu; Roney.Liu
 */

package cn.ck.plm.base.entity;

/**
 * 生命周期模板 Iteration 实体 —— 复合实体的 Iteration 端。
 *
 * <p>继承 {@link IterationEntity} 获得版本控制 / 检出 / 视图 / 生命周期状态等字段。
 * 与 {@link LifecycleTemplateMaster}（Master）构成 Master-Iteration 复合实体。
 *
 * <p>对应表 ck_lifecycle_template_iteration。
 */
public class LifecycleTemplateIteration extends IterationEntity {

    // ==================== 构造方法 ====================

    public LifecycleTemplateIteration() {
        super();
    }

    @Override
    public String toString() {
        return "LifecycleTemplateIteration{masterOid='" + getMasterOid() + "', version=" + getVersion() + "}";
    }
}
