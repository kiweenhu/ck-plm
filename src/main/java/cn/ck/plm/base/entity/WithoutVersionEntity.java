/*
 * Copyright (c) 2025 深圳乘恺科技有限公司
 * All rights reserved.
 *
 * @author Kiween.Hu; Roney.Liu
 */

package cn.ck.plm.base.entity;

/**
 * 无版本实体基类（WithoutVersionEntity），适用于不需要版本控制的业务对象。
 *
 * <p>继承 {@link BaseEntity} 获得审计能力，提供 code / name / description 三个基础字段。
 * 适用于字典、分类、配置等无需版本管理的简单实体。
 *
 * <h3>与版本控制实体的区别</h3>
 * <pre>
 * BaseEntity
 *   ├── MasterEntity     ← 有版本控制（1:N IterationEntity）
 *   └── WithoutVersionEntity  ← 无版本控制（直接作为业务实体）
 * </pre>
 *
 * <h3>典型子类</h3>
 * <ul>
 *   <li>物料分类（MaterialCategory）</li>
 *   <li>组织部门（Department）</li>
 *   <li>系统配置（SystemConfig）</li>
 * </ul>
 */
public abstract class WithoutVersionEntity extends BaseEntity {

    /** 业务编码（唯一标识） */
    private String code;

    /** 名称 */
    private String name;

    /** 描述 */
    private String description;

    // ==================== 构造方法 ====================

    protected WithoutVersionEntity() {
    }

    // ==================== Getter / Setter ====================

    public String getCode() { return code; }
    public void setCode(String code) { this.code = code; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    @Override
    public String toString() {
        return "WithoutVersionEntity{code='" + code + "', name='" + name
                + "', description='" + description + "'}";
    }
}
