/*
 * Copyright (c) 2025 深圳乘恺科技有限公司
 * All rights reserved.
 *
 * @author Kiween.Hu; Roney.Liu
 */

package cn.ck.plm.part.service.api;

import cn.ck.plm.base.service.MasterService;
import cn.ck.plm.part.dto.PartVO;
import cn.ck.plm.part.entity.Part;
import cn.ck.plm.part.entity.PartIteration;

import java.util.List;

/**
 * Part 主对象服务契约，扩展 MasterService 的版本控制能力，
 * 增加部件 CRUD 业务操作。
 */
public interface PartService extends MasterService {

    /** 创建部件（含初始子版本 A.1、CKFile 主文件关联、CKAttachment 附件绑定、迭代级 unit/source） */
    Part create(Part part, String ckfileOid, String attachmentOid, String unit, String source);

    /** 更新部件基本信息 */
    Part update(Part part);

    /** 重命名部件（仅更新 name） */
    Part rename(String oid, String name);

    /** 另存为：复制部件为一个新部件（新编号、新初始 iteration，IBA 值由 Controller 层复制） */
    Part saveAs(String sourceOid, String newName);

    /** 移动部件到新的容器/阶段/文件夹位置 */
    Part move(String oid, String containerOid, String containerType, String folderOid, String stageOid);

    /** 更新部件最新迭代的 unit/source 属性（迭代级字段） */
    void updateLatestIterationAttributes(String masterOid, String unit, String source);

    /** 查询部件主对象的最新迭代（用于回填迭代级字段，如 unit/source） */
    PartIteration findLatestIteration(String masterOid);

    /** 按迭代 oid 查询指定迭代（用于查看历史版本详情） */
    PartIteration findIterationByOid(String iterationOid);

    /** 删除部件及其全部子版本 */
    void delete(String oid);

    /** 删除部件的最新小版本（仅删除最新 iteration，若删除后无剩余版本则删除主对象） */
    void deleteLatestIteration(String oid);

    /** 新建视图版本：为部件创建一个新的大版本（revision+1，iteration=1，view 继承） */
    void newViewVersion(String oid);

    /** 按 oid 查询 */
    Part findByOid(String oid);

    /** 按所属容器查询全部部件 */
    List<Part> findByContainerOid(String containerOid);

    /** 按所属容器 + 关键字（名称/编码）查询部件 */
    List<Part> findByContainerAndKeyword(String containerOid, String keyword);

    /** 按所属容器 + 阶段查询 */
    List<Part> findByContainerAndStage(String containerOid, String stageOid);

    /** 按文件夹查询 */
    List<Part> findByFolder(String folderOid);

    /** 按分类查询 */
    List<Part> findByClassification(String classificationOid);

    /** 按文件夹查询部件视图对象（含迭代、生命周期、类型名），用于阶段页面 DataTable */
    List<PartVO> findVOsByFolder(String folderOid);

    /** 按分类查询部件视图对象 */
    List<PartVO> findVOsByClassification(String classificationOid);

    /** 查询部件主对象的所有子版本（历史版本，按 revision, iteration 降序） */
    List<PartIteration> findIterationsByMaster(String masterOid);
}
