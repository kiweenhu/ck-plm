/*
 * Copyright (c) 2025 深圳乘恺科技有限公司
 * All rights reserved.
 *
 * @author Kiween.Hu; Roney.Liu
 */

package cn.ck.plm.bom.service.api;

import cn.ck.plm.bom.entity.BomDiff;

import java.util.List;

/**
 * BomDiff 业务服务接口。
 */
public interface BomDiffService {

    BomDiff create(BomDiff diff);

    void deleteByOid(String oid);

    BomDiff getByOid(String oid);

    /** 查询从某个迭代出发的所有 Diff */
    List<BomDiff> listByFromIteration(String fromIterationOid);

    /** 查询到达某个迭代的所有 Diff */
    List<BomDiff> listByToIteration(String toIterationOid);

    /** 查询两个迭代之间的 Diff */
    BomDiff getByFromAndTo(String fromIterationOid, String toIterationOid);
}
