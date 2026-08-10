/*
 * Copyright (c) 2025 深圳乘恺科技有限公司
 * All rights reserved.
 *
 * @author Kiween.Hu; Roney.Liu
 */

package cn.ck.plm.bom.mapper;

import cn.ck.plm.bom.entity.BomDiff;

import java.util.List;

/**
 * BomDiff 数据访问接口，定义数据库无关的持久化契约。
 */
public interface BomDiffMapper {

    int insert(BomDiff diff);

    int deleteByOid(String oid);

    BomDiff selectByOid(String oid);

    /** 查询从某个迭代出发的所有 Diff */
    List<BomDiff> selectByFromIterationOid(String fromIterationOid);

    /** 查询到达某个迭代的所有 Diff */
    List<BomDiff> selectByToIterationOid(String toIterationOid);

    /** 查询两个迭代之间的 Diff */
    BomDiff selectByFromAndTo(String fromIterationOid, String toIterationOid);
}
