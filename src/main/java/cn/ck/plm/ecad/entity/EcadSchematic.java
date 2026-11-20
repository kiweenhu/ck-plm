/*
 * Copyright (c) 2026~2028 深圳乘恺科技有限公司
 * All rights reserved.
 *
 * @author Kiween.Hu
 */

package cn.ck.plm.ecad.entity;

import cn.ck.plm.base.entity.BaseEntity;
import cn.ck.plm.base.entity.TenantEntity;

/**
 * ECAD 原理图主数据（Ecad Schematic Master）—— Cadence 原理图设计对象。
 *
 * <h3>定位</h3>
 * <ul>
 *   <li>挂在电子设计项目（{@code ck_ecad_project}，.opj 容器）之下的<b>原理图对象</b></li>
 *   <li>主文件为 OrCAD Capture 设计文件（.DSN），经 {@code ckfile_oid} 关联</li>
 *   <li>版本控制走 master/iteration 两级（{@code ck_ecad_schematic_iteration}），
 *       检出/检入经 {@code EcadSchematicCheckoutProvider} 接入统一检出体系</li>
 * </ul>
 *
 * <h3>实体关系</h3>
 * <pre>
 * EcadProject 1 ── N EcadSchematic            （设计项目 → 原理图，project_oid 直连；
 *                                              同时经 ck_ecad_project_design_link 留排序）
 * EcadSchematic 1 ── N EcadSchematicIteration （版本历史，master_oid）
 * EcadSchematic 1 ── N CKDesignInstance       （原理图器件实例，design_oid + design_type=SCHEMATIC）
 * </pre>
 *
 * <p>主键与审计字段继承 {@link BaseEntity}。
 */
public class EcadSchematic extends BaseEntity implements TenantEntity {

    /** 编号（业务唯一键，如 SCH-2026-0001） */
    private String number;

    /** 名称（一般与 Capture 设计名一致） */
    private String name;

    /** 描述 */
    private String description;

    /** 所属设计项目 oid（引用 ck_ecad_project.oid） */
    private String projectOid;

    /** EDA 工具（默认 Cadence OrCAD Capture，预留兼容其他工具） */
    private String edaTool;

    /** 设计文件格式（默认 DSN；预留 DSN/XML 等扩展） */
    private String fileFormat;

    /** 最新迭代 oid（冗余，加速"取最新版"） */
    private String latestIterationOid;

    /** 租户 oid */
    private String tenantOid;

    /** 逻辑删除标记 */
    private boolean deleteMark;

    // ==================== Getter / Setter ====================

    public String getNumber() { return number; }
    public void setNumber(String number) { this.number = number; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public String getProjectOid() { return projectOid; }
    public void setProjectOid(String projectOid) { this.projectOid = projectOid; }

    public String getEdaTool() { return edaTool; }
    public void setEdaTool(String edaTool) { this.edaTool = edaTool; }

    public String getFileFormat() { return fileFormat; }
    public void setFileFormat(String fileFormat) { this.fileFormat = fileFormat; }

    public String getLatestIterationOid() { return latestIterationOid; }
    public void setLatestIterationOid(String latestIterationOid) { this.latestIterationOid = latestIterationOid; }

    @Override
    public String getTenantOid() { return tenantOid; }

    @Override
    public void setTenantOid(String tenantOid) { this.tenantOid = tenantOid; }

    public boolean isDeleteMark() { return deleteMark; }
    public void setDeleteMark(boolean deleteMark) { this.deleteMark = deleteMark; }
}
