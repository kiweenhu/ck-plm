/*
 * Copyright (c) 2025 深圳乘恺科技有限公司
 * All rights reserved.
 *
 * @author Kiween.Hu; Roney.Liu
 */

package cn.ck.plm.document.mapper;

import cn.ck.plm.document.entity.EngineeringDocument;

import java.util.List;

/**
 * 工程数据主数据（{@code ck_eng_document}）数据访问接口。
 *
 * <p>参照 Windchill {@code wt.epm.EPMDocumentMaster}，与 {@link DocumentMapper}（通用文档）并列。
 * 实现见 {@code impl/postgresql/PostgreSqlEngineeringDocumentMapper}。
 */
public interface EngineeringDocumentMapper {

    /** 新增工程数据主对象 */
    int insert(EngineeringDocument engDocument);

    /** 更新工程数据主对象基本的容器/文件夹/阶段/分类信息 */
    int update(EngineeringDocument engDocument);

    /** 按 oid 删除（物理删除，子版本由调用方负责） */
    int deleteByOid(String oid);

    /** 按 oid 查询 */
    EngineeringDocument selectByOid(String oid);

    /** 按所属容器查询 */
    List<EngineeringDocument> selectByContainerOid(String containerOid);

    /** 按所属容器 + 阶段查询 */
    List<EngineeringDocument> selectByContainerAndStage(String containerOid, String stageOid);

    /** 按文件夹查询 */
    List<EngineeringDocument> selectByFolderOid(String folderOid);

    /** 按类型定义编码查询（如 FOOTPRINT / SYMBOL / DRAWING_2D） */
    List<EngineeringDocument> selectByTypeDefinitionCode(String typeDefinitionCode);

    /** 统计引用指定分类的主对象数量（分类删除前校验用） */
    int countByClassificationOid(String classificationOid);
}
