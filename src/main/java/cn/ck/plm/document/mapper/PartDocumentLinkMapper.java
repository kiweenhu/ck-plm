/*
 * Copyright (c) 2025 深圳乘恺科技有限公司
 * All rights reserved.
 *
 * @author Kiween.Hu; Roney.Liu
 */

package cn.ck.plm.document.mapper;

import cn.ck.plm.document.entity.PartDocumentLink;

import java.util.List;

/**
 * PartDocumentLink（部件关联文档）数据访问接口，定义数据库无关的持久化契约。
 *
 * <p>对应表 {@code ck_part_document_link}。(part_oid, document_oid, link_type)
 * 唯一约束防重复关联。
 */
public interface PartDocumentLinkMapper {

    int insert(PartDocumentLink link);

    int update(PartDocumentLink link);

    int deleteByOid(String oid);

    PartDocumentLink selectByOid(String oid);

    /** 查询某部件指定类型的全部关联文档（linkType 可空 = 全部类型） */
    List<PartDocumentLink> selectByPartOid(String partOid, String linkType);

    /** 查询某部件的全部关联文档（无论类型） */
    List<PartDocumentLink> selectByPartOidAll(String partOid);

    List<PartDocumentLink> selectAll();
}
