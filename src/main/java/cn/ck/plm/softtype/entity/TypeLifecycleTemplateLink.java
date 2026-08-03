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
 * 类型-生命周期模板关联实体。
 *
 * <p>记录每个 {@link cn.ck.plm.softtype.entity.TypeDefinition} 绑定的生命周期模板，
 * 用于控制该类型业务对象的生命周期状态流转。
 *
 * <p>约束：每个类型（type_oid）最多绑定一个生命周期模板。
 *
 * <h3>多租户</h3>
 * 实现 {@link TenantEntity} 接口，不同租户可为同一平台级 Type 绑定不同生命周期模板。
 *
 * <h3>关联关系</h3>
 * <ul>
 *   <li>{@code typeOid} → {@code ck_type_definition.oid}</li>
 *   <li>{@code lifecycleTemplateCode} → {@code ck_lifecycle_template.code}（业务唯一键）</li>
 * </ul>
 */
public class TypeLifecycleTemplateLink extends BaseEntity implements TenantEntity {

    /** 类型 OID，关联 ck_type_definition.oid */
    private String typeOid;

    /** 生命周期模板编码，关联 ck_lifecycle_template.code */
    private String lifecycleTemplateCode;

    /** 租户 oid（多租户隔离） */
    private String tenantOid;

    // ==================== 构造方法 ====================

    public TypeLifecycleTemplateLink() {
    }

    public TypeLifecycleTemplateLink(String typeOid, String lifecycleTemplateCode) {
        this.typeOid = typeOid;
        this.lifecycleTemplateCode = lifecycleTemplateCode;
    }

    // ==================== Getter / Setter ====================

    public String getTypeOid() {
        return typeOid;
    }

    public void setTypeOid(String typeOid) {
        this.typeOid = typeOid;
    }

    public String getLifecycleTemplateCode() {
        return lifecycleTemplateCode;
    }

    public void setLifecycleTemplateCode(String lifecycleTemplateCode) {
        this.lifecycleTemplateCode = lifecycleTemplateCode;
    }

    @Override
    public String getTenantOid() { return tenantOid; }

    @Override
    public void setTenantOid(String tenantOid) { this.tenantOid = tenantOid; }

    @Override
    public String toString() {
        return "TypeLifecycleTemplateLink{" +
                "typeOid='" + typeOid + '\'' +
                ", lifecycleTemplateCode='" + lifecycleTemplateCode + '\'' +
                '}';
    }
}
