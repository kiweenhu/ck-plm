/*
 * Copyright (c) 2025 深圳乘恺科技有限公司
 * All rights reserved.
 *
 * @author Kiween.Hu; Roney.Liu
 */

package cn.ck.plm.product.entity;

/**
 * 产品型号实体（ProductModel），继承 {@link ProductLine}，附属于某个产品系列。
 *
 * <p>产品型号是产品系列下的具体型号定义，拥有独立的团队和研发阶段。
 * 创建时自动初始化关联团队、6 个默认研发阶段及系统文件夹。
 *
 * <h3>关系</h3>
 * <pre>
 * ProductModel  N ── 1  ProductLine  (通过父类 parentOid 关联所属产品系列)
 * ProductModel  1 ── 1  Team         (通过 teamOid 关联)
 * </pre>
 *
 * <h3>继承链条</h3>
 * <pre>
 * BaseEntity → WithoutVersionEntity → ProductLine → ProductModel(this)
 * </pre>
 */
public class ProductModel extends ProductLine {

    // ==================== 构造方法 ====================

    public ProductModel() {
        super();
    }

    @Override
    public String toString() {
        return "ProductModel{code='" + getCode() + "', name='" + getName()
                + "', parentOid='" + getParentOid() + "', teamOid='" + getTeamOid() + "'}";
    }
}
