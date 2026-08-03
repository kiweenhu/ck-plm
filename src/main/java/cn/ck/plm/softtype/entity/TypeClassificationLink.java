/*
 * Copyright (c) 2025 深圳乘恺科技有限公司
 * All rights reserved.
 *
 * @author Kiween.Hu; Roney.Liu
 */

package cn.ck.plm.softtype.entity;

import cn.ck.plm.base.entity.BaseEntity;
import cn.ck.plm.base.entity.TenantEntity;

/**
 * 类型-分类关联实体。
 *
 * <p>记录每个 {@link TypeDefinition} 绑定的分类节点，
 * 该类型创建实例时可自动继承此分类。
 *
 * <p>约束：每个类型（type_oid）最多绑定一个分类。
 *
 * <h3>关联关系</h3>
 * <ul>
 *   <li>{@code typeOid} → {@code ck_type_definition.oid}</li>
 *   <li>{@code classificationOid} → {@code ck_classification.oid}</li>
 * </ul>
 */
public class TypeClassificationLink extends BaseEntity implements TenantEntity {

    /** 类型 OID，关联 ck_type_definition.oid */
    private String typeOid;

    /** 分类 OID，关联 ck_classification.oid */
    private String classificationOid;

    /** 租户 oid（多租户隔离） */
    private String tenantOid;

    // ==================== 构造方法 ====================

    public TypeClassificationLink() {
    }

    public TypeClassificationLink(String typeOid, String classificationOid) {
        this.typeOid = typeOid;
        this.classificationOid = classificationOid;
    }

    // ==================== Getter / Setter ====================

    public String getTypeOid() {
        return typeOid;
    }

    public void setTypeOid(String typeOid) {
        this.typeOid = typeOid;
    }

    public String getClassificationOid() {
        return classificationOid;
    }

    public void setClassificationOid(String classificationOid) {
        this.classificationOid = classificationOid;
    }

    @Override
    public String getTenantOid() { return tenantOid; }

    @Override
    public void setTenantOid(String tenantOid) { this.tenantOid = tenantOid; }

    @Override
    public String toString() {
        return "TypeClassificationLink{" +
                "typeOid='" + typeOid + '\'' +
                ", classificationOid='" + classificationOid + '\'' +
                '}';
    }
}
