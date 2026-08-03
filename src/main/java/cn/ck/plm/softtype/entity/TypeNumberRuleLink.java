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
 * 类型-编码规则关联实体。
 *
 * <p>记录每个 {@link cn.ck.plm.softtype.entity.TypeDefinition} 选择的编码规则，
 * 未来在创建该类型的业务对象时，将依据此规则自动生成编码。
 *
 * <p>约束：每个类型（type_oid）最多绑定一条编码规则。
 *
 * <h3>多租户</h3>
 * 实现 {@link TenantEntity} 接口，不同租户可为同一平台级 Type 绑定不同编码规则。
 *
 * <h3>关联关系</h3>
 * <ul>
 *   <li>{@code typeOid} → {@code ck_type_definition.oid}</li>
 *   <li>{@code numberRuleCode} → {@code ck_number.code}（业务唯一键）</li>
 * </ul>
 */
public class TypeNumberRuleLink extends BaseEntity implements TenantEntity {

    /** 类型 OID，关联 ck_type_definition.oid */
    private String typeOid;

    /** 编码规则编码，关联 ck_number.code */
    private String numberRuleCode;

    /** 租户 oid（多租户隔离） */
    private String tenantOid;

    // ==================== 构造方法 ====================

    public TypeNumberRuleLink() {
    }

    public TypeNumberRuleLink(String typeOid, String numberRuleCode) {
        this.typeOid = typeOid;
        this.numberRuleCode = numberRuleCode;
    }

    // ==================== Getter / Setter ====================

    public String getTypeOid() {
        return typeOid;
    }

    public void setTypeOid(String typeOid) {
        this.typeOid = typeOid;
    }

    public String getNumberRuleCode() {
        return numberRuleCode;
    }

    public void setNumberRuleCode(String numberRuleCode) {
        this.numberRuleCode = numberRuleCode;
    }

    @Override
    public String getTenantOid() { return tenantOid; }

    @Override
    public void setTenantOid(String tenantOid) { this.tenantOid = tenantOid; }

    @Override
    public String toString() {
        return "TypeNumberRuleLink{" +
                "typeOid='" + typeOid + '\'' +
                ", numberRuleCode='" + numberRuleCode + '\'' +
                '}';
    }
}
