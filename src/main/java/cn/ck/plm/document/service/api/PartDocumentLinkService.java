/*
 * Copyright (c) 2025 深圳乘恺科技有限公司
 * All rights reserved.
 *
 * @author Kiween.Hu; Roney.Liu
 */

package cn.ck.plm.document.service.api;

import cn.ck.plm.document.entity.PartDocumentLink;

import java.util.List;

/**
 * PartDocumentLink（部件关联文档）服务契约。
 *
 * <p>提供部件与文档之间的全局关联关系（参考 REFERENCE / 说明 DESCRIPTION 两类）的
 * CRUD 与按部件查询。关联挂在部件主数据（Master）级别，不随迭代升版变化。
 */
public interface PartDocumentLinkService {

    /** 创建关联（同一部件 + 文档 + 类型唯一，重复创建抛出 IllegalArgumentException） */
    PartDocumentLink create(PartDocumentLink link);

    /** 更新关联 */
    PartDocumentLink update(PartDocumentLink link);

    /** 删除关联 */
    void delete(String oid);

    /** 按 oid 查询 */
    PartDocumentLink findByOid(String oid);

    /** 查询某部件指定类型的关联文档（linkType 可空 = 全部类型） */
    List<PartDocumentLink> findByPart(String partOid, String linkType);

    List<PartDocumentLink> listAll();
}
