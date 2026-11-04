/*
 * Copyright (c) 2026~2028 深圳乘恺科技有限公司
 * All rights reserved.
 *
 * @author Kiween.Hu; Roney.Liu
 */

package cn.ck.plm.bom.service.api;

import cn.ck.plm.bom.dto.BomCostReportVO;
import cn.ck.plm.bom.dto.BomMaterialVO;
import cn.ck.plm.bom.dto.BomTreeNode;
import cn.ck.plm.bom.dto.BomUsedByVO;
import cn.ck.plm.bom.dto.ContainerCostTrendVO;
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

    /**
     * 「被使用情况」（反向 BOM）的<b>可读口径</b>：一行 = 一条 BOM 行，
     * 父迭代已解析成父件编码 / 名称 / 版本，另附 {@code parentPartOid} 供逐层往上追溯。
     *
     * <p>与 {@link #listByChildPartOid} 的分工：那个返回实体（带 parentIterationOid 这类内部标识），
     * 只适合服务端内部用；界面要的是"装在哪几个父件上"，所以由服务端解析后返回。
     */
    List<BomUsedByVO> listUsedBy(String childPartOid);

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

    /**
     * 某容器（产品系列 / 型号）的<b>物料清单</b>：{@code container_oid} 恰好等于该容器的零件，
     * 一行一个（最新大版本的最新小版本），BOM 用量 / 使用于只是补充信息。
     *
     * <p>只认<b>当前容器</b>，不回溯父系列 —— 型号页不混入父系列零件，系列页也不反向包含子型号零件。
     *
     * <p>与 {@link #buildTree} 的分工：那个回答"某个件的结构长什么样"，这个回答
     * "这个产品下有哪些件、各用多少" —— 面向采购 / 成本 / 齐套，不关心层级。
     */
    List<BomMaterialVO> materialsByContainer(String containerOid);

    /**
     * 容器（产品系列 / 型号）的<b>成本趋势</b>：当前卷积总成本 + 历史快照点 + 数据缺口。
     *
     * <p>系列页会自动并入旗下各型号的零件（BOM 挂在型号的零件上，系列容器自己通常是空的）；
     * 顶层件口径 = 容器内没有父件的零件，避免把同一批成本按"总成 + 零件"重复相加。
     *
     * <p>历史点只来自 {@code ck_bom_snapshot}（按版本保存的快照），没有快照就只有当前值 ——
     * 这时 {@code warnings} 会说明缺什么，而不是画一条假的平线。
     */
    ContainerCostTrendVO containerCostTrend(String containerOid);

    /**
     * 容器（系列会并入旗下各型号）的<b>当前卷积总成本</b> —— 只算数，不建历史序列。
     *
     * <p>与 {@link #containerCostTrend(String)} 同一口径（顶层件 = 容器内没有父件的零件，
     * 逐个 {@code BomCostCalculator} 卷积后求和），但跳过"历史点"：指标评估是"打开页面就算一次"，
     * 每次回放所有 BOM 快照代价太大。
     *
     * @return 没有顶层件（或顶层件都不可计价）时返回 {@code null} —— "没有"不是"0 元"
     */
    Double containerCurrentCost(String containerOid);

    List<BomLinks> listAll();
}
