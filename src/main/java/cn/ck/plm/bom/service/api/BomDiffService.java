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

    /**
     * 实时对比两个迭代的顶层 BOM 行差异（新增/移除/修改），不依赖预计算数据。
     * 通过两次拉取 BOM 树并逐行比对实现，结果仅返回不落库。
     */
    BomDiff compareNow(String fromIterationOid, String toIterationOid);
}
