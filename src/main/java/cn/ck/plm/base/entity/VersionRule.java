/*
 * Copyright (c) 2025 深圳乘恺科技有限公司
 * All rights reserved.
 *
 * @author Kiween.Hu; Roney.Liu
 */

package cn.ck.plm.base.entity;

/**
 * 版本规则实体类
 *
 * <p>定义业务对象的版本编码规则模板，支持灵活的前缀、日期格式、序号等组合。
 * 典型样例模式：(A,B,C,D,E,F,G,H) 表示 8 位大写字母序列，
 * 可进一步扩展为 (PREFIX)-(YYYYMMDD)-(SEQ:6) 等复杂规则。
 *
 * <h3>规则定义格式</h3>
 * <ul>
 *   <li>(A-Z) - 大写字母序列，每位从 A-Z 循环</li>
 *   <li>(a-z) - 小写字母序列，每位从 a-z 循环</li>
 *   <li>(0-9) - 数字序列，每位从 0-9 循环</li>
 *   <li>(YYYY/MM/DD) - 日期格式，可自定义分隔符</li>
 *   <li>(SEQ:N) - 序号格式，N 为位数，不足补零</li>
 *   <li>(PREFIX:XXX) - 前缀固定值</li>
 * </ul>
 */
public class VersionRule extends WithoutVersionEntity implements TenantEntity {

    /** 规则名称 */
    private String name;

    /** 规则编码，唯一标识 */
    private String code;

    /** 规则定义，如 (A,B,C,D,E,F,G,H) */
    private String ruleDefinition;

    /** 描述说明 */
    private String description;

    /** 适用对象类型，如 CK_DOCUMENT, CK_PRODUCT_MODEL */
    private String applicableType;

    /** 序号当前值 */
    private Long sequenceValue;

    /** 是否启用 */
    private Boolean enabled;

    /** 租户 oid */
    private String tenantOid;

    // ========== Getter/Setter ==========

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getCode() {
        return code;
    }

    public void setCode(String code) {
        this.code = code;
    }

    public String getRuleDefinition() {
        return ruleDefinition;
    }

    public void setRuleDefinition(String ruleDefinition) {
        this.ruleDefinition = ruleDefinition;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getApplicableType() {
        return applicableType;
    }

    public void setApplicableType(String applicableType) {
        this.applicableType = applicableType;
    }

    public Long getSequenceValue() {
        return sequenceValue;
    }

    public void setSequenceValue(Long sequenceValue) {
        this.sequenceValue = sequenceValue;
    }

    public Boolean getEnabled() {
        return enabled;
    }

    public void setEnabled(Boolean enabled) {
        this.enabled = enabled;
    }

    @Override
    public String getTenantOid() { return tenantOid; }

    @Override
    public void setTenantOid(String tenantOid) { this.tenantOid = tenantOid; }
}
