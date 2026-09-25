/*
 * Copyright (c) 2025 深圳乘恺科技有限公司
 * All rights reserved.
 *
 * @author Kiween.Hu; Roney.Liu
 */

package cn.ck.plm.bom.service;

import cn.ck.plm.bom.dto.BomTreeNode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * BOM 成本卷积的口径测试。
 *
 * <p>成本计算是"看起来显然、算错了很难发现"的那类逻辑（数字不会报错，只会不对），
 * 所以把口径用手算得出的例子钉住：每层用量连乘、单位成本只在自己那行计一次、
 * 父件汇总用子件的<b>完整</b>成本。
 */
class BomCostCalculatorTest {

    /** 造一行：用量 qty、单位成本 unitCost（null 表示没填） */
    private static BomTreeNode line(Double qty, Double unitCost, BomTreeNode... children) {
        BomTreeNode node = new BomTreeNode();
        node.setQuantity(qty);
        node.setUnitCost(unitCost);
        if (children.length > 0) {
            node.setChildren(List.of(children));
        }
        return node;
    }

    @Test
    @DisplayName("单层：本层金额与总成本都是数量乘单位成本")
    void singleLevel() {
        BomCostCalculator.Summary summary = BomCostCalculator.apply(List.of(line(2d, 3d)));

        assertEquals(6d, summary.getDirectCost(), 1e-9);
        assertEquals(6d, summary.getTotalCost(), 1e-9);
        assertEquals(1, summary.getTotalLines());
        assertEquals(1, summary.getMaxDepth());
    }

    @Test
    @DisplayName("多层：用量在路径上连乘，单位成本只在自己那行计一次")
    void multiLevel() {
        // B：用量 2、单位成本 3；B 下挂 C：用量 5、单位成本 1
        //   C 完整单位成本 = 1          → C 行金额 = 5 × 1 = 5
        //   B 完整单位成本 = 3 + 5 = 8  → B 行金额 = 2 × 8 = 16
        //   父件本层直接成本 = 2 × 3 = 6，总成本 = 16
        BomTreeNode b = line(2d, 3d, line(5d, 1d));
        BomTreeNode c = b.getChildren().get(0);
        BomCostCalculator.Summary summary = BomCostCalculator.apply(List.of(b));

        assertEquals(5d, c.getExtendedCost(), 1e-9);
        assertEquals(5d, c.getRolledUpCost(), 1e-9);
        assertEquals(8d, b.getRolledUpUnitCost(), 1e-9);
        assertEquals(16d, b.getRolledUpCost(), 1e-9);
        assertEquals(6d, b.getExtendedCost(), 1e-9);
        assertEquals(6d, summary.getDirectCost(), 1e-9);
        assertEquals(16d, summary.getTotalCost(), 1e-9);
        assertEquals(2, summary.getTotalLines());
        assertEquals(2, summary.getMaxDepth());
    }

    @Test
    @DisplayName("多分支：总成本是各分支金额之和")
    void multipleBranches() {
        BomTreeNode a = line(2d, 10d);
        BomTreeNode b = line(3d, 5d, line(4d, 2d));
        BomCostCalculator.Summary summary = BomCostCalculator.apply(List.of(a, b));

        assertEquals(35d, summary.getDirectCost(), 1e-9);
        assertEquals(59d, summary.getTotalCost(), 1e-9);
        assertEquals(3, summary.getTotalLines());
    }

    @Test
    @DisplayName("未填单位成本：按 0 计入并单独计数，不静默")
    void missingUnitCost() {
        BomCostCalculator.Summary summary = BomCostCalculator.apply(List.of(line(2d, null), line(1d, 4d)));

        assertEquals(4d, summary.getTotalCost(), 1e-9);
        assertEquals(1, summary.getMissingCostLines());
    }

    @Test
    @DisplayName("缺数量同样按 0 计，不抛异常")
    void missingQuantity() {
        BomCostCalculator.Summary summary = BomCostCalculator.apply(List.of(line(null, 7d)));

        assertEquals(0d, summary.getTotalCost(), 1e-9);
        assertEquals(0d, summary.getDirectCost(), 1e-9);
    }

    @Test
    @DisplayName("空树与 null 输入给出零值而不是异常")
    void emptyInput() {
        assertEquals(0d, BomCostCalculator.apply(List.of()).getTotalCost(), 1e-9);
        assertEquals(0, BomCostCalculator.apply(null).getTotalLines());
    }

    @Test
    @DisplayName("每层落到分：报表里「金额 = 数量 × 显示的单位成本」，明细与合计也对得上")
    void roundingPerLevel() {
        // 单位成本 0.3333 先落到分 = 0.33，再 3 × 0.33 = 0.99（而不是 0.9999 → 1.00）
        // 口径选择：宁可让每层的数字"看起来自洽"（数量 × 显示的单位成本 = 显示金额），
        // 也不要出现一行"怎么乘都对不上"的金额 —— 对账的人会先怀疑报表算错了。
        BomTreeNode child = line(3d, 0.3333d);
        BomCostCalculator.Summary summary = BomCostCalculator.apply(List.of(line(1d, 0d, child)));

        assertNotNull(child.getRolledUpCost());
        assertEquals(0.33d, child.getRolledUpUnitCost(), 1e-9);
        assertEquals(0.99d, child.getRolledUpCost(), 1e-9);
        assertEquals(0.99d, summary.getTotalCost(), 1e-9);
    }
}
