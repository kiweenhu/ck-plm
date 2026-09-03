/*
 * Copyright (c) 2025 深圳乘恺科技有限公司
 * All rights reserved.
 *
 * @author Kiween.Hu; Roney.Liu
 */

package cn.ck.plm.part.mapper;

import cn.ck.plm.part.entity.PartReferenceLink;

import java.util.List;

/**
 * PartReferenceLink（零件「参考」文档关系）数据访问接口，定义数据库无关的持久化契约。
 *
 * <p>对应表 {@code ck_doc_part_link}，仅操作 {@code link_type = 'REFERENCE'} 的记录。
 */
public interface PartReferenceLinkMapper {

    int insert(PartReferenceLink link);

    int update(PartReferenceLink link);

    int deleteByOid(String oid);

    PartReferenceLink selectByOid(String oid);

    /** 查询某零件迭代下挂接的全部「参考」文档关系 */
    List<PartReferenceLink> selectByPartIterationOid(String partIterationOid);

    /** 查询某文档主对象被哪些零件迭代作为「参考」引用 */
    List<PartReferenceLink> selectByDocMasterOid(String docMasterOid);

    /** 查询某文档精确迭代被哪些零件迭代作为「参考」锁定引用 */
    List<PartReferenceLink> selectByDocIterationOid(String docIterationOid);
}
