/*
 * Copyright (c) 2025 深圳乘恺科技有限公司
 * All rights reserved.
 *
 * @author Kiween.Hu; Roney.Liu
 */

package cn.ck.plm.base.entity;

/**
 * 版本主数据对象（MasterEntity），参考 Windchill Mastered 抽象类。
 *
 * <p>作为所有业务主对象的纯数据父类，定义在多个子版本间共享的核心属性。
 * 本身不携带业务方法——版本工厂、数据拷贝等操作由独立的
 * {@link cn.ck.plm.base.service.impl.MasterServiceImpl} 提供。
 *
 * <h3>Windchill 对应</h3>
 * <pre>
 * Persistable → BaseEntity → MasterEntity(this)   ←  wt.fc.Mastered → WTDocumentMaster / WTPartMaster
 * Persistable → BaseEntity → IterationEntity  ←  wt.fc.RevisionControlled → WTDocument / WTPart
 * </pre>
 *
 * <h3>继承链条（贫血模型）</h3>
 * <pre>
 * BaseEntity (oid, creator, createdAt, updater, updatedAt)
 *   └── MasterEntity (name, number, description)  ← 纯数据，无业务方法
 *         └── Document / Part / ...          ← 纯数据 + 业务专属字段
 *
 * 业务逻辑由独立的 Service 层提供：
 *   MasterServiceImpl   →  createInitialIteration / createDerivedIteration / updateFrom
 *   IterationServiceImpl→  checkOut / checkIn / promoteLifecycle / ...
 * </pre>
 *
 * <p>注意：本类为抽象类，不标注 MyBatis 注解，由子类自行映射。
 */
public abstract class MasterEntity extends BaseEntity {

    /** 名称 */
    private String name;

    /** 编号 */
    private String number;

    /** 描述 */
    private String description;

    /** 所属上下文 OID（如产品系列、型号、企业资源库的 OID） */
    private String containerOid;

    /** 所属上下文类别（如：产品系列、型号、企业资源库） */
    private String containerType;

    // ==================== 构造方法 ====================

    protected MasterEntity() {
    }

    // ==================== Getter / Setter ====================

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getNumber() { return number; }
    public void setNumber(String number) { this.number = number; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public String getContainerOid() { return containerOid; }
    public void setContainerOid(String containerOid) { this.containerOid = containerOid; }

    public String getContainerType() { return containerType; }
    public void setContainerType(String containerType) { this.containerType = containerType; }

    @Override
    public String toString() {
        return "MasterEntity{name='" + name + "', number='" + number + "'}";
    }
}
