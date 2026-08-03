/*
 * Copyright (c) 2025 深圳乘恺科技有限公司
 * All rights reserved.
 *
 * @author Kiween.Hu; Roney.Liu
 */

package cn.ck.plm.document.service.api;

import cn.ck.plm.document.dto.DocumentVO;
import cn.ck.plm.document.entity.Document;
import cn.ck.plm.base.service.MasterService;

import java.util.List;

/**
 * Document 主对象服务契约，扩展 MasterService 的版本控制能力，
 * 增加文档 CRUD 业务操作。
 */
public interface DocumentService extends MasterService {

    /** 创建文档（含初始子版本 A.1、CKFile 主文档关联、CKAttachment 附件绑定） */
    Document create(Document document, String ckfileOid, String attachmentOid);

    /** 更新文档基本信息 */
    Document update(Document document);

    /** 删除文档及其全部子版本 */
    void delete(String oid);

    /** 按 oid 查询 */
    Document findByOid(String oid);

    /** 按所属容器查询全部文档 */
    List<Document> findByContainerOid(String containerOid);

    /** 按所属容器 + 阶段查询 */
    List<Document> findByContainerAndStage(String containerOid, String stageOid);

    /** 按文件夹查询 */
    List<Document> findByFolder(String folderOid);

    /** 按文件夹查询文档视图对象（含迭代、生命周期、类型名），用于阶段页面 DataTable */
    List<DocumentVO> findVOsByFolder(String folderOid);
}
