/*
 * Copyright (c) 2025 深圳乘恺科技有限公司
 * All rights reserved.
 *
 * @author Kiween.Hu; Roney.Liu
 */

package cn.ck.plm.bom.mapper;

import cn.ck.plm.bom.entity.BomLinks;

import java.util.List;

/**
 * BomLinks 数据访问接口，定义数据库无关的持久化契约。
 */
public interface BomLinksMapper {

    int insert(BomLinks bomLinks);

    int update(BomLinks bomLinks);

    int deleteByOid(String oid);

    BomLinks selectByOid(String oid);

    /** 按父部件迭代查询 BOM 行项（按 lineNumber 排序） */
    List<BomLinks> selectByParentIterationOid(String parentIterationOid);

    /** 按子部件查询所有引用了该部件的 BOM 行项 */
    List<BomLinks> selectByChildPartOid(String childPartOid);

    /** 按子部件精确迭代查询所有引用了该迭代的 BOM 行项 */
    List<BomLinks> selectByChildIterationOid(String childIterationOid);

    /** 批量更新 resolved_iteration_oid（非精确引用的解析缓存刷新） */
    int updateResolvedIterationOid(String oid, String resolvedIterationOid);

    List<BomLinks> selectAll();
}
