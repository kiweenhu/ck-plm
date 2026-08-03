/*
 * Copyright (c) 2025 深圳乘恺科技有限公司
 * All rights reserved.
 *
 * @author Kiween.Hu; Roney.Liu
 */

package cn.ck.plm.functional.entity;

import cn.ck.plm.base.entity.MasterEntity;
import cn.ck.plm.base.entity.TenantEntity;

/**
 * 功能架构（Functional）主对象实体。
 *
 * <p>继承 Part 的复合实体结构，面向多行业语义：
 * <ul>
 *   <li><b>军工领域</b>：装备级功能系统（如武器系统、火控系统、导航系统）</li>
 *   <li><b>汽车领域</b>：车型功能域（如动力域、底盘域、车身域、智驾域）</li>
 * </ul>
 *
 * <p>与 Part 共享相同的版本控制体系（Revision/Iteration、CheckOut/CheckIn、Lifecycle），
 * 但作为独立的实体类型，拥有独立的编码规则和属性定义。
 */
public class FunctionalEntity extends MasterEntity implements TenantEntity {

    /** 类型定义编码 */
    private String typeDefinitionCode;

    /** 所属文件夹 */
    private String folderOid;

    /** 研发阶段 */
    private String stageOid;

    /** 租户 */
    private String tenantOid;

    // ==================== 构造方法 ====================

    public FunctionalEntity() {}

    // ==================== Getter / Setter ====================

    public String getTypeDefinitionCode() { return typeDefinitionCode; }
    public void setTypeDefinitionCode(String typeDefinitionCode) { this.typeDefinitionCode = typeDefinitionCode; }

    public String getFolderOid() { return folderOid; }
    public void setFolderOid(String folderOid) { this.folderOid = folderOid; }

    public String getStageOid() { return stageOid; }
    public void setStageOid(String stageOid) { this.stageOid = stageOid; }

    @Override
    public String getTenantOid() { return tenantOid; }

    @Override
    public void setTenantOid(String tenantOid) { this.tenantOid = tenantOid; }

    @Override
    public String toString() {
        return "FunctionalEntity{name='" + getName() + "', number='" + getNumber() +
                "', typeDefinitionCode='" + typeDefinitionCode + "'}";
    }
}
