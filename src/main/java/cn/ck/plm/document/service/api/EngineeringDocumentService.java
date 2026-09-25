/*
 * Copyright (c) 2025 深圳乘恺科技有限公司
 * All rights reserved.
 *
 * @author Kiween.Hu; Roney.Liu
 */

package cn.ck.plm.document.service.api;

import cn.ck.plm.base.service.MasterService;
import cn.ck.plm.document.dto.EngineeringDocumentVO;
import cn.ck.plm.document.entity.EngineeringDocument;
import cn.ck.plm.document.entity.EngineeringDocumentIteration;

import java.util.List;

/**
 * 工程数据（EngineeringDocument）主对象服务契约。
 *
 * <p>参照 Windchill {@code wt.epm.EPMDocumentService} / {@code EPMDocumentMaster}，
 * 形态与 {@link DocumentService} / {@code PartService} 保持一致：
 * 扩展 {@link MasterService} 的版本控制能力，增加工程数据 CRUD 业务操作。
 *
 * <h3>与 DocumentService 的区别</h3>
 * <p>{@code DocumentService} 管理通用文档（{@code ck_document}），本服务管理
 * <b>CAD 工程数据</b>（{@code ck_eng_document}）—— 二者在 Windchill 中是并列的两个类。
 * 工程数据额外携带 CAD 专有属性（{@code cadName / cadType / cadTool}）与 2D 制图属性。
 */
public interface EngineeringDocumentService extends MasterService {

    /**
     * 创建工程数据（含初始子版本 A.1、主文件关联、CAD 属性）。
     *
     * @param engDocument   工程数据主对象（含 typeDefinitionCode，支持软类型如 FOOTPRINT / SYMBOL）
     * @param ckfileOid     主文件 oid（写入迭代的 ckfile_oid，可为 null）
     * @param attachmentOid 附件 oid（与 PartService 一致，由调用方/附件服务处理，本方法不落库）
     * @param cadName       CAD 名称（如 part_a.prt）
     * @param cadType       CAD 类型（DRW / MODEL / ASM / FORM）
     * @param cadTool       来源 CAD 工具（AutoCAD / SolidWorks / Creo…）
     */
    EngineeringDocument create(EngineeringDocument engDocument, String ckfileOid, String attachmentOid,
                               String cadName, String cadType, String cadTool);

    /** 更新工程数据基本信息（容器/阶段/文件夹/分类/类型） */
    EngineeringDocument update(EngineeringDocument engDocument);

    /** 重命名（仅更新 name） */
    EngineeringDocument rename(String oid, String name);

    /** 移动到新的容器/阶段/文件夹位置 */
    EngineeringDocument move(String oid, String containerOid, String containerType,
                             String folderOid, String stageOid);

    /** 删除工程数据及其全部子版本 */
    void delete(String oid);

    /**
     * 删除最新小版本（仅删除最新 iteration）。
     *
     * <p>若删除后无剩余版本，则连同主对象一并删除；否则把剩余中最新的版本标记为 latest。
     */
    void deleteLatestIteration(String oid);

    /**
     * 新建视图版本：为工程数据创建新的大版本（revision+1，iteration=1，latest=true）。
     *
     * <p>继承原最新版本的 CAD 专有属性与生命周期状态。
     */
    void newViewVersion(String oid);

    /**
     * 按文件夹查询视图对象（含最新迭代、生命周期、类型名与 CAD 属性），用于列表展示。
     */
    List<EngineeringDocumentVO> findVOsByFolder(String folderOid);

    /** 按 oid 查询 */
    EngineeringDocument findByOid(String oid);

    /** 按所属容器查询 */
    List<EngineeringDocument> findByContainerOid(String containerOid);

    /** 按所属容器 + 阶段查询 */
    List<EngineeringDocument> findByContainerAndStage(String containerOid, String stageOid);

    /** 按文件夹查询 */
    List<EngineeringDocument> findByFolder(String folderOid);

    /** 按类型定义编码查询（如 FOOTPRINT / SYMBOL） */
    List<EngineeringDocument> findByTypeDefinitionCode(String typeDefinitionCode);

    /** 查询主对象的最新子版本（回填 CAD 属性、生命周期等迭代级字段） */
    EngineeringDocumentIteration findLatestIteration(String masterOid);

    /**
     * 更新最新子版本的迭代级属性（CAD 专有属性与 2D 制图属性、主文件）。
     *
     * <p>这些字段是 {@code ck_eng_document_iteration} 的真实列，不属于 IBA；
     * 若不在更新链路中显式落库，编辑表单里的 CAD 字段会被静默丢弃。
     * 实现只覆盖 {@code attributes} 中<b>非 null</b> 的字段。
     *
     * @param masterOid  工程数据主对象 oid
     * @param attributes 迭代级属性载体（仅非 null 字段生效）
     */
    void updateLatestIterationAttributes(String masterOid, EngineeringDocumentIteration attributes);

    /** 按迭代 oid 查询指定迭代（查看历史版本详情） */
    EngineeringDocumentIteration findIterationByOid(String iterationOid);

    /** 查询主对象的全部子版本（按 revision / iteration 降序） */
    List<EngineeringDocumentIteration> findIterationsByMaster(String masterOid);
}
