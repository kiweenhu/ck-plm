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

import java.util.List;

/**
 * Part 主对象服务契约，扩展 MasterService 的版本控制能力，
 * 增加部件 CRUD 业务操作。
 */
public interface PartService extends MasterService {

    /** 创建部件（含初始子版本 A.1、CKFile 主文件关联、CKAttachment 附件绑定） */
    Part create(Part part, String ckfileOid, String attachmentOid);

    /** 更新部件基本信息 */
    Part update(Part part);

    /** 删除部件及其全部子版本 */
    void delete(String oid);

    /** 按 oid 查询 */
    Part findByOid(String oid);

    /** 按所属容器查询全部部件 */
    List<Part> findByContainerOid(String containerOid);

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
}
