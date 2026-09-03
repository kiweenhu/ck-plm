/*
 * Copyright (c) 2025 深圳乘恺科技有限公司
 * All rights reserved.
 *
 * @author Kiween.Hu; Roney.Liu
 */

package cn.ck.plm.part.service.api;

import cn.ck.plm.part.entity.PartDescribeLink;

import java.util.List;

/**
 * PartDescribeLink（零件「定义」文档关系）服务契约。
 *
 * <p>提供 DESCRIBES 关系的 CRUD 与「显式晋升」：将一条 {@code REFERENCE} 关系晋升为
 * {@code DESCRIBES}（对应业务上的「进入技术状态基线」），晋升是受控动作、需留痕。
 */
public interface PartDescribeLinkService {

    /** 创建「定义」文档关系 */
    PartDescribeLink create(PartDescribeLink link);

    /** 更新「定义」文档关系（如重新锁定文档精确版本） */
    PartDescribeLink update(PartDescribeLink link);

    /** 删除「定义」文档关系 */
    void delete(String oid);

    /** 按 oid 查询 */
    PartDescribeLink findByOid(String oid);

    /** 查询某零件迭代下挂接的全部「定义」文档关系 */
    List<PartDescribeLink> findByPartIteration(String partIterationOid);

    /** 查询某文档主对象被哪些零件迭代作为「定义」引用 */
    List<PartDescribeLink> findByDocMaster(String docMasterOid);

    /** 查询某文档精确迭代被哪些零件迭代作为「定义」锁定引用 */
    List<PartDescribeLink> findByDocIteration(String docIterationOid);

    /** 显式晋升：将一条 REFERENCE 关系提升为 DESCRIBES（进入技术状态基线），返回新关系 */
    PartDescribeLink promote(String referenceLinkOid);
}
