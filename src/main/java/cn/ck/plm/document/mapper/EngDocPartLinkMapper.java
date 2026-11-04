/*
 * Copyright (c) 2026~2028 深圳乘恺科技有限公司
 * All rights reserved.
 *
 * @author Kiween.Hu; Roney.Liu
 */

package cn.ck.plm.document.mapper;

import cn.ck.plm.document.entity.EngDocPartLink;

import java.util.List;

/**
 * 工程数据 ↔ 零部件构建规则（{@code ck_eng_doc_part_link}）数据访问契约。
 *
 * <p>与其它 Mapper 一致：接口只声明契约，SQL 由 {@code impl/postgresql} 下的实现提供。
 * 三个查询入口对应三种典型场景：零件详情（按零件迭代）、工程文档详情（按工程文档主对象）、
 * 版本精确回溯（按工程文档迭代）。
 */
public interface EngDocPartLinkMapper {

    int insert(EngDocPartLink link);

    int update(EngDocPartLink link);

    int deleteByOid(String oid);

    EngDocPartLink selectByOid(String oid);

    /** 某个零件版本上的全部构建规则 */
    List<EngDocPartLink> selectByPartIterationOid(String partIterationOid);

    /** 某份工程数据（主对象）被哪些零件版本引用 */
    List<EngDocPartLink> selectByEngDocumentOid(String engDocumentOid);

    /** 某个工程数据版本被哪些零件版本引用 */
    List<EngDocPartLink> selectByEngDocIterationOid(String engDocIterationOid);
}
