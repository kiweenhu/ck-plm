/*
 * Copyright (c) 2025 深圳乘恺科技有限公司
 * All rights reserved.
 *
 * @author Kiween.Hu; Roney.Liu
 */

package cn.ck.plm.bom.service.api;

import cn.ck.plm.bom.dto.BomCostReportVO;
import cn.ck.plm.bom.dto.BomTreeNode;
import cn.ck.plm.bom.entity.BomLinks;

import java.util.List;

/**
 * BomLinks 业务服务接口。
 */
public interface BomLinksService {

    BomLinks create(BomLinks bomLinks);

    BomLinks update(BomLinks bomLinks);

    void deleteByOid(String oid);

    BomLinks getByOid(String oid);

    /** 获取某父部件迭代下的所有 BOM 行项（按 lineNumber 排序） */
    List<BomLinks> listByParentIterationOid(String parentIterationOid);

    /** 查询某个子部件被哪些父部件引用 */
    List<BomLinks> listByChildPartOid(String childPartOid);

    /** 查询某个子部件精确迭代被哪些 BOM 行引用 */
    List<BomLinks> listByChildIterationOid(String childIterationOid);

    /** 刷新非精确引用的解析缓存 */
    void refreshResolvedIteration(String oid, String resolvedIterationOid);

    /** 递归构建某父迭代下的完整多层 BOM 树（含子件展示信息，带防环与深度限制） */
    List<BomTreeNode> buildTree(String parentIterationOid);

    /**
     * BOM 成本报告（<b>卷积</b>口径）：把每一层的「数量 × 单位成本」递归累加。
     *
     * <p>为什么不直接挂在 {@link #buildTree} 上：成本要多遍历一遍并回写成本字段，
     * 而 BOM 结构页只看结构、不需要这些字段 —— 分开既省一次遍历，也不会让"结构树"里
     * 混进只在成本语境下成立的字段。
     */
    BomCostReportVO costReport(String parentIterationOid);

    List<BomLinks> listAll();
}
