/*
 * Copyright (c) 2025 深圳乘恺科技有限公司
 * All rights reserved.
 *
 * @author Kiween.Hu; Roney.Liu
 */

package cn.ck.plm.bom.service;

import cn.ck.plm.bom.dto.BomTreeNode;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

/**
 * BOM 成本卷积计算（纯函数，无 Spring / 无数据库依赖，可直接单测）。
 *
 * <h3>口径（一句话）</h3>
 * <p><b>把每一条 BOM 行的「数量 × 单位成本」按层级递归相加</b>：
 * <pre>
 *   某子件的完整单位成本 = 它自己的单位成本 + 它下挂各行金额之和
 *   本行金额             = 用量 × 子件完整单位成本
 *   父件总成本           = Σ 它直接下挂各行的金额
 * </pre>
 * 每一层的用量在路径上<b>连乘</b>（2 个 A 各带 5 个 B ⇒ 10 个 B 的成本），
 * 单位成本只在它所在的那一行计一次 —— 这就是"卷积"的确切含义。
 *
 * <h3>两个刻意的取舍</h3>
 * <ol>
 *   <li><b>每层落账到分（2 位小数），父件汇总用<b>已落账</b>的子行金额</b>：
 *       若只在最后统一取整，报表里"明细加起来 ≠ 合计"（差几分钱），
 *       对账时会被当成 bug 反复排查。宁可每层四舍五入。</li>
 *   <li><b>没填单位成本的行按 0 计入，但单独计数</b>（{@link Summary#missingCostLines}）：
 *       悄悄按 0 算会让成本报告整体偏小而无人察觉；如实提示"有 N 行未填成本"，
 *       由人决定是补数据还是接受。</li>
 * </ol>
 *
 * <p>不做的事情：不管币种、不含人工/制造费用、不做汇率换算 —— 那些是成本核算体系的范畴，
 * 本类只回答"按 BOM 结构与现有单位成本，这套结构的直接材料成本是多少"。
 */
public final class BomCostCalculator {

    /** 金额保留位数（分） */
    private static final int SCALE = 2;

    private BomCostCalculator() {
    }

    /** 计算结果（金额 + 规模 + 数据缺口） */
    public static final class Summary {
        /** 本层直接成本：父件直接下挂各行的「数量 × 单位成本」 */
        private final double directCost;
        /** 卷积总成本：含所有下级的完整成本 */
        private final double totalCost;
        /** 参与计算的行数（含各层） */
        private final int totalLines;
        /** 未填单位成本的行数（按 0 计入） */
        private final int missingCostLines;
        /** 实际遍历到的最大层数（1 = 只有直接子件） */
        private final int maxDepth;

        Summary(double directCost, double totalCost, int totalLines, int missingCostLines, int maxDepth) {
            this.directCost = directCost;
            this.totalCost = totalCost;
            this.totalLines = totalLines;
            this.missingCostLines = missingCostLines;
            this.maxDepth = maxDepth;
        }

        public double getDirectCost() { return directCost; }

        public double getTotalCost() { return totalCost; }

        public int getTotalLines() { return totalLines; }

        public int getMissingCostLines() { return missingCostLines; }

        public int getMaxDepth() { return maxDepth; }
    }

    /**
     * 就地给整棵树写上成本字段，并汇总。
     *
     * <p>写回的字段（见 {@link BomTreeNode}）：
     * <ul>
     *   <li>{@code extendedCost} 本行本层金额（数量 × 单位成本）；</li>
     *   <li>{@code rolledUpUnitCost} 子件的完整单位成本（含其所有下级）；</li>
     *   <li>{@code rolledUpCost} 本行累计金额（数量 × 完整单位成本）。</li>
     * </ul>
     *
     * @param roots 某父迭代下的直接子件（{@code buildTree} 的结果），可为 null
     * @return 汇总；{@code roots} 为空时各项为 0
     */
    public static Summary apply(List<BomTreeNode> roots) {
        if (roots == null || roots.isEmpty()) {
            return new Summary(0d, 0d, 0, 0, 0);
        }
        Counters counters = new Counters();
        double directCost = 0d;
        double totalCost = 0d;
        for (BomTreeNode root : roots) {
            if (root == null) {
                continue;
            }
            walk(root, 1, counters);
            directCost += root.getExtendedCost() == null ? 0d : root.getExtendedCost();
            totalCost += root.getRolledUpCost() == null ? 0d : root.getRolledUpCost();
        }
        return new Summary(
                round2(directCost),
                round2(totalCost),
                counters.lines,
                counters.missingCost,
                counters.maxDepth);
    }

    /** 后序遍历：先算子件（子件的完整成本是父行金额的输入） */
    private static void walk(BomTreeNode node, int depth, Counters counters) {
        counters.lines += 1;
        counters.maxDepth = Math.max(counters.maxDepth, depth);

        Double unitCost = node.getUnitCost();
        double own = unitCost == null ? 0d : unitCost;
        if (unitCost == null) {
            counters.missingCost += 1;
        }
        double quantity = node.getQuantity() == null ? 0d : node.getQuantity();

        double childrenAmount = 0d;
        List<BomTreeNode> children = node.getChildren();
        if (children != null) {
            for (BomTreeNode child : children) {
                if (child == null) {
                    continue;
                }
                walk(child, depth + 1, counters);
                childrenAmount += child.getRolledUpCost() == null ? 0d : child.getRolledUpCost();
            }
        }

        double rolledUpUnitCost = round2(own + childrenAmount);
        node.setExtendedCost(round2(quantity * own));
        node.setRolledUpUnitCost(rolledUpUnitCost);
        node.setRolledUpCost(round2(quantity * rolledUpUnitCost));
    }

    /**
     * 四舍五入到分。
     *
     * <p>用 {@link BigDecimal#ROUND_HALF_UP} 而不是 {@code Math.round(v * 100) / 100.0}：
     * 后者在 1.005 这类"二进制表示恰好略小于十进制"的数值上会向下取整（0.01 变 0.00），
     * 金额上属于看得见的错。
     */
    private static double round2(double value) {
        if (Double.isNaN(value) || Double.isInfinite(value)) {
            return 0d;
        }
        return BigDecimal.valueOf(value).setScale(SCALE, RoundingMode.HALF_UP).doubleValue();
    }

    /** 遍历过程中的累计量（避免用可变字段挂在静态类上） */
    private static final class Counters {
        private int lines;
        private int missingCost;
        private int maxDepth;
    }
}
