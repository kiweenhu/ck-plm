/*
 * Copyright (c) 2025 深圳乘恺科技有限公司
 * All rights reserved.
 *
 * @author Kiween.Hu; Roney.Liu
 */

package cn.ck.plm.part.service.api;

import cn.ck.plm.part.entity.PartReferenceLink;

import java.util.List;

/**
 * PartReferenceLink（零件「参考」文档关系）服务契约。
 *
 * <p>提供 REFERENCE 关系的 CRUD。REFERENCE 是「轻量伴随」关系：设计早期以参考关系挂接
 * 分析/评审文档，不影响零件本身；设计冻结时可通过
 * {@link PartDescribeLinkService#promote} 显式晋升为 DESCRIBES。
 */
public interface PartReferenceLinkService {

    /** 创建「参考」文档关系 */
    PartReferenceLink create(PartReferenceLink link);

    /** 更新「参考」文档关系 */
    PartReferenceLink update(PartReferenceLink link);

    /** 删除「参考」文档关系 */
    void delete(String oid);

    /** 按 oid 查询 */
    PartReferenceLink findByOid(String oid);

    /** 查询某零件迭代下挂接的全部「参考」文档关系 */
    List<PartReferenceLink> findByPartIteration(String partIterationOid);

    /** 查询某文档主对象被哪些零件迭代作为「参考」引用 */
    List<PartReferenceLink> findByDocMaster(String docMasterOid);

    /** 查询某文档精确迭代被哪些零件迭代作为「参考」锁定引用 */
    List<PartReferenceLink> findByDocIteration(String docIterationOid);
}
