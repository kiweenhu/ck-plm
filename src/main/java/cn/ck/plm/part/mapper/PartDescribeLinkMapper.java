/*
 * Copyright (c) 2025 深圳乘恺科技有限公司
 * All rights reserved.
 *
 * @author Kiween.Hu; Roney.Liu
 */

package cn.ck.plm.part.mapper;

import cn.ck.plm.part.entity.PartDescribeLink;

import java.util.List;

/**
 * PartDescribeLink（零件「定义」文档关系）数据访问接口，定义数据库无关的持久化契约。
 *
 * <p>对应表 {@code ck_doc_part_link}，仅操作 {@code link_type = 'DESCRIBES'} 的记录。
 */
public interface PartDescribeLinkMapper {

    int insert(PartDescribeLink link);

    int update(PartDescribeLink link);

    int deleteByOid(String oid);

    PartDescribeLink selectByOid(String oid);

    /** 查询某零件迭代下挂接的全部「定义」文档关系 */
    List<PartDescribeLink> selectByPartIterationOid(String partIterationOid);

    /** 查询某文档主对象被哪些零件迭代作为「定义」引用 */
    List<PartDescribeLink> selectByDocMasterOid(String docMasterOid);

    /** 查询某文档精确迭代被哪些零件迭代作为「定义」锁定引用 */
    List<PartDescribeLink> selectByDocIterationOid(String docIterationOid);
}
