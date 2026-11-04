/*
 * Copyright (c) 2026~2028 深圳乘恺科技有限公司
 * All rights reserved.
 *
 * @author Kiween.Hu; Roney.Liu
 */

package cn.ck.plm.product.service.impl;

import cn.ck.plm.base.util.TenantContext;
import cn.ck.plm.bom.dto.BomCostReportVO;
import cn.ck.plm.bom.service.api.BomLinksService;
import cn.ck.plm.product.entity.Metric;
import cn.ck.plm.product.mapper.MetricFactsMapper;
import cn.ck.plm.product.mapper.MetricMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * 指标评估器 —— 把 {@code ck_metric} 里 11 项指标的数值真正算出来并写回（2026-10-26）。
 *
 * <p>调用点与 {@link DeliverableStatusEvaluator} 一致：{@code GET /api/instances} 返回前重算，
 * "看的时候算"而不是"定时算"—— 算的永远是打开面板那一刻的数据。
 *
 * <h3>能算的 8 项，口径逐条写死</h3>
 * <table>
 *   <tr><th>指标</th><th>口径</th></tr>
 *   <tr><td>BOM_TOTAL_COST</td><td>BOM 卷积总成本（{@code BomCostCalculator}）；无 BOM 行 → 不算</td></tr>
 *   <tr><td>COST_GAP_RATE</td><td>未填单位成本的行 / 总行数 × 100</td></tr>
 *   <tr><td>ISSUE_CLOSE_RATE</td><td>CLOSED / (总数 − CANCELLED) × 100，与 {@code IssueService.stats} 同一口径</td></tr>
 *   <tr><td>QUALIFIED_RATE</td><td>有 AML {@code approved_status ∈ {PREFERRED,APPROVED}} 的可认证件 / 可认证件（ELECTRONIC / PCBA）× 100</td></tr>
 *   <tr><td>PREFERRED_RATE</td><td>有 AML {@code approved_status = PREFERRED} 的可认证件 / 可认证件 × 100</td></tr>
 *   <tr><td>ALT_COVERAGE</td><td>有启用替代关系（{@code enabled=TRUE}）的器件 / 容器器件总数 × 100</td></tr>
 *   <tr><td>EOL_RISK_COUNT</td><td>PART_LIFE_CYCLE∈{EOL,OBSOLETE} 的器件数</td></tr>
 *   <tr><td>COST_ACHIEVE</td><td>目标成本（IBA {@code TARGET_COST}）/ 实际成本（BOM 卷积）× 100，≥100% 为达成；任一侧缺 → 不算</td></tr>
 *   <tr><td>TIME_TO_MARKET_DEV</td><td>LDCP（上市决策评审）通过日 − 目标上市日期（IBA {@code TARGET_LAUNCH_DATE}），单位天，正数延期；任一侧缺 → 不算</td></tr>
 * </table>
 *
 * <h3>两项不碰</h3>
 * <p>{@code REUSE_GAIN} / {@code QUALITY_LOSS_COST} 的 source 在行业包里
 * 就是 {@code "-"}（C 级，无取数依据）—— 评估器跳过它们，库里保持空白，界面显示「暂未评估」。
 * 给它们编个算法，正是文档第 16 节禁止的事。
 *
 * <h3>为什么"未填"有时不算 0</h3>
 * <p>停产风险看的是 IBA（{@code PART_LIFE_CYCLE}）：没人填过就无从判断，所以显示
 * 「器件未填…」而不是 0 个风险件 —— "没维护"和"没有风险"必须区分。
 *
 * <p>准入率 / 优选率 / 替代料覆盖率反过来：读的都是表里的事实（AML 认证状态、替代关系），
 * "没有"就是"没通过认证 / 没有替代"，照常出 0%。分母分别是可认证件（准入、优选）与器件总数（替代）。
 */
@Component
public class MetricEvaluator {

    private static final Logger log = LoggerFactory.getLogger(MetricEvaluator.class);

    /** 行业包 attributes.json 里是 camelCase，落库是大写（见 AttributeInstaller 注释） */
    private static final String ATTR_LIFE_CYCLE = "PART_LIFE_CYCLE";
    /**
     * AML 的认证状态（{@code ck_part_aml.approved_status}）。
     *
     * <p>准入 = PREFERRED 或 APPROVED：一条合格料号就代表这个器件可用；
     * DISALLOWED 是"禁用"，既不算准入也不算优选。
     */
    private static final List<String> AML_QUALIFIED = List.of("PREFERRED", "APPROVED");
    /** 优选 = 只有 PREFERRED（首选），APPROVED 不算优选。 */
    private static final List<String> AML_PREFERRED = List.of("PREFERRED");
    /** 容器（系列 / 型号）上的目标成本，元 —— 行业包 electronics-core 的 COST_TARGET 组 */
    private static final String ATTR_TARGET_COST = "TARGET_COST";
    /** 容器（系列 / 型号）上的目标上市日期，yyyy-MM-dd —— 同包的 LAUNCH_TARGET 组 */
    private static final String ATTR_TARGET_LAUNCH_DATE = "TARGET_LAUNCH_DATE";
    /** 上市决策评审的门编码（IPD 包里的「上市决策评审 LDCP」），实际上市日以它通过为准 */
    private static final String GATE_LAUNCH = "LDCP";

    private static final String ISSUE_CLOSED = "CLOSED";
    private static final String ISSUE_CANCELLED = "CANCELLED";

    private final MetricMapper metricMapper;
    private final MetricFactsMapper factsMapper;
    private final BomLinksService bomLinksService;

    public MetricEvaluator(MetricMapper metricMapper,
                           MetricFactsMapper factsMapper,
                           BomLinksService bomLinksService) {
        this.metricMapper = metricMapper;
        this.factsMapper = factsMapper;
        this.bomLinksService = bomLinksService;
    }

    /**
     * 重算某容器（系列 / 型号）的全部指标实例。
     *
     * @return 实际写回的条数（跳过 C 级无依据项与已停用项）
     */
    @Transactional
    public int reevaluate(String ownerOid) {
        if (ownerOid == null || ownerOid.trim().isEmpty()) {
            return 0;
        }
        List<Metric> metrics = metricMapper.selectByOwnerOid(ownerOid);
        if (metrics == null || metrics.isEmpty()) {
            return 0;
        }
        Facts facts = collect(ownerOid);
        int updated = 0;
        for (Metric m : metrics) {
            if (m == null || Boolean.TRUE.equals(m.getDisabled()) || m.getCode() == null) {
                continue;
            }
            try {
                if (evaluate(m, facts)) {
                    updated++;
                }
            } catch (Exception e) {
                // 单项算炸不能影响其它项，更不能影响面板打开
                log.warn("指标评估失败: owner={} code={} err={}", ownerOid, m.getCode(), e.getMessage());
            }
        }
        return updated;
    }

    // ==================== 取事实 ====================

    private Facts collect(String ownerOid) {
        Facts f = new Facts();

        f.parts = factsMapper.countPartsByContainer(ownerOid);
        if (f.parts > 0) {
            List<Map<String, Object>> rows = factsMapper.selectPartIbaValues(ownerOid,
                    List.of(ATTR_LIFE_CYCLE));
            if (rows != null) {
                for (Map<String, Object> row : rows) {
                    String code = text(pick(row, "attr_code"));
                    String value = text(pick(row, "attr_value"));
                    if (code == null || value == null) {
                        continue;
                    }
                    String v = value.trim().toUpperCase(Locale.ROOT);
                    if (ATTR_LIFE_CYCLE.equals(code.trim().toUpperCase(Locale.ROOT))) {
                        f.lifeFilled++;
                        if ("EOL".equals(v) || "OBSOLETE".equals(v)) {
                            f.eolRisky++;
                        }
                    }
                }
            }
            f.altCovered = factsMapper.countPartsWithAlternate(ownerOid);
        }

        /*
         * 准入 / 优选改走 AML 的 approved_status（2026-10-30 用户拍板「用 AML 的认证状态」）。
         *
         * 原口径读 IBA（QUALIFICATION_STATUS / PREFERRED_LEVEL），但那些属性只分配给 ELECTRONIC、
         * 且没有表单入口，全库 0 行 —— 指标永远"暂未评估"。AML 是「器件 → 厂商料号」面板与
         * 元器件认证流程一直在写的表，既有的业务事实，不必再让人抄一遍。
         *
         * 分母是"可认证件"（ELECTRONIC / PCBA）：结构件、电气件不做器件认证，
         * 放进分母只会把准入率永久拉低。
         */
        f.certifiableParts = factsMapper.countCertifiableParts(ownerOid);
        if (f.certifiableParts > 0) {
            f.qualifiedParts = factsMapper.countCertifiablePartsWithAmlStatus(ownerOid, AML_QUALIFIED);
            f.preferredParts = factsMapper.countCertifiablePartsWithAmlStatus(ownerOid, AML_PREFERRED);
        }

        String bomRoot = factsMapper.selectBomRootIteration(ownerOid);
        if (bomRoot != null) {
            BomCostReportVO report = bomLinksService.costReport(bomRoot);
            if (report != null) {
                f.bomLines = report.getTotalLines() == null ? 0 : report.getTotalLines();
                f.missingCostLines = report.getMissingCostLines() == null ? 0 : report.getMissingCostLines();
                f.totalCost = report.getTotalCost();
            }
        }

        /*
         * COST_ACHIEVE 的两侧。
         *
         * 目标：只认本容器自己登记的 IBA（型号没填就是没填，不回溯父系列 ——
         *      拿整条产品线的预算当型号目标会让达成率凭空好看）。
         * 实际：容器当前卷积成本；系列会并入旗下型号，与「成本趋势」卡片同一口径 ——
         *      BOM 挂在型号的零件上，只看系列容器永远是 0。
         */
        String targetText = factsMapper.selectTargetCost(ownerOid, ATTR_TARGET_COST);
        if (targetText != null && !targetText.trim().isEmpty()) {
            try {
                f.targetCost = new BigDecimal(targetText.trim());
            } catch (NumberFormatException e) {
                // 属性是 FLOAT，理论上不会走到这里；真走到了就不出数，别拿脏值算达成率
                log.warn("目标成本不是合法数字: owner={} raw={}", ownerOid, targetText);
            }
        }
        f.actualCost = bomLinksService.containerCurrentCost(ownerOid);

        /*
         * TIME_TO_MARKET_DEV：计划上市日（IBA）vs 实际上市日（LDCP 通过日）。
         *
         * 实际日期取「上市决策评审」的通过日 —— 门通过了才算上市这件事发生了；
         * 不用状态字段、不用创建时间，那些都不是"上市"的时点。
         */
        f.targetLaunchDate = parseDate(factsMapper.selectTargetCost(ownerOid, ATTR_TARGET_LAUNCH_DATE),
                "目标上市日期", ownerOid);
        f.actualLaunchDate = parseDate(factsMapper.selectGateDecidedDate(ownerOid, GATE_LAUNCH),
                "LDCP 通过日期", ownerOid);

        String tenantOid = TenantContext.getOrNull();
        if (tenantOid != null) {
            List<Map<String, Object>> rows = factsMapper.countIssueStatusByProduct(tenantOid, ownerOid);
            if (rows != null) {
                for (Map<String, Object> row : rows) {
                    String status = text(pick(row, "status"));
                    if (status == null) {
                        continue;
                    }
                    long cnt = number(pick(row, "cnt"));
                    f.issues.merge(status.trim().toUpperCase(Locale.ROOT), cnt, Long::sum);
                    f.issueTotal += cnt;
                }
            }
        }
        return f;
    }

    // ==================== 逐项评估 ====================

    /** @return true = 写回了（false = 该指标不归本评估器管，保持库中现值） */
    private boolean evaluate(Metric m, Facts f) {
        switch (m.getCode().trim().toUpperCase(Locale.ROOT)) {
            case "BOM_TOTAL_COST" -> {
                if (f.bomLines == 0) {
                    write(m, null, "无 BOM 结构（成本来自 BOM 卷积）");
                } else if (f.totalCost == null) {
                    write(m, null, "成本报告未返回总成本");
                } else {
                    String text = f.missingCostLines > 0
                            ? f.missingCostLines + "/" + f.bomLines + " 行未填单位成本（按 0 计入）"
                            : f.bomLines + " 行全部有单位成本";
                    write(m, pct(f.totalCost), text);
                }
                return true;
            }
            case "COST_GAP_RATE" -> {
                if (f.bomLines == 0) {
                    write(m, null, "无 BOM 行");
                } else {
                    write(m, pct(f.missingCostLines * 100.0 / f.bomLines),
                            f.missingCostLines + "/" + f.bomLines + " 行缺单位成本");
                }
                return true;
            }
            case "COST_ACHIEVE" -> {
                // 达成率 = 目标 / 实际 × 100：≥100% 表示实际没超目标（"达成了"）。
                // 反过来算（actual / target）会让超支读成 >100%，与"达成率"这三个字相拗。
                if (f.targetCost == null) {
                    write(m, null, "缺目标成本：在系列 / 型号上填「目标成本(元)」后才会出数");
                } else if (f.actualCost == null) {
                    write(m, null, "缺实际成本：该容器的 BOM 还没有可计价的顶层件");
                } else if (f.actualCost <= 0D) {
                    write(m, null, "实际成本为 0：" + f.targetCost.toPlainString() + " 元的目标无法与之相比");
                } else {
                    double rate = f.targetCost.doubleValue() * 100.0 / f.actualCost;
                    write(m, pct(rate),
                            "目标 " + money(f.targetCost) + " / 实际 " + money(BigDecimal.valueOf(f.actualCost)));
                }
                return true;
            }
            case "TIME_TO_MARKET_DEV" -> {
                // 偏差天数 = 实际上市（LDCP 通过日）− 计划上市日：正数延期、负数提前、0 按期
                if (f.targetLaunchDate == null) {
                    write(m, null, "缺目标上市日期：在系列 / 型号上填「目标上市日期」后才会出数");
                } else if (f.actualLaunchDate == null) {
                    write(m, null, "上市决策评审（LDCP）还没通过，实际上市日期无从谈起");
                } else {
                    long days = ChronoUnit.DAYS.between(f.targetLaunchDate, f.actualLaunchDate);
                    String verdict = days == 0
                            ? "按期"
                            : (days > 0 ? "延期 " : "提前 ") + Math.abs(days) + " 天";
                    write(m, BigDecimal.valueOf(days),
                            "计划 " + f.targetLaunchDate + " / 实际 " + f.actualLaunchDate + "（" + verdict + "）");
                }
                return true;
            }
            case "ISSUE_CLOSE_RATE" -> {
                long closed = f.issues.getOrDefault(ISSUE_CLOSED, 0L);
                long cancelled = f.issues.getOrDefault(ISSUE_CANCELLED, 0L);
                long denominator = f.issueTotal - cancelled;
                if (denominator <= 0) {
                    write(m, null, f.issueTotal == 0 ? "该产品暂无问题记录" : "问题全部为已取消");
                } else {
                    write(m, pct(closed * 100.0 / denominator), closed + "/" + denominator + " 已闭环");
                }
                return true;
            }
            case "QUALIFIED_RATE" -> {
                if (f.certifiableParts == 0) {
                    write(m, null, "本容器无可认证器件（仅统计电子元器件 / PCBA）");
                } else {
                    // 没有 AML 的器件就是"未准入"，记 0 照常出数 —— 这是业务事实，不是缺维护
                    write(m, pct(f.qualifiedParts * 100.0 / f.certifiableParts),
                            f.qualifiedParts + "/" + f.certifiableParts
                                    + " 已准入（有 APPROVED / PREFERRED 厂商料号）");
                }
                return true;
            }
            case "PREFERRED_RATE" -> {
                if (f.certifiableParts == 0) {
                    write(m, null, "本容器无可认证器件（仅统计电子元器件 / PCBA）");
                } else {
                    write(m, pct(f.preferredParts * 100.0 / f.certifiableParts),
                            f.preferredParts + "/" + f.certifiableParts + " 有 PREFERRED 厂商料号");
                }
                return true;
            }
            case "ALT_COVERAGE" -> {
                if (f.parts == 0) {
                    write(m, null, "本容器无器件");
                } else {
                    write(m, pct(f.altCovered * 100.0 / f.parts),
                            f.altCovered + "/" + f.parts + " 个器件有替代料");
                }
                return true;
            }
            case "EOL_RISK_COUNT" -> {
                if (f.parts == 0) {
                    write(m, null, "本容器无器件");
                } else if (f.lifeFilled == 0) {
                    write(m, null, "器件未填「生命周期」");
                } else {
                    write(m, BigDecimal.valueOf(f.eolRisky),
                            f.eolRisky + "/" + f.lifeFilled + " 个器件为 EOL/OBSOLETE");
                }
                return true;
            }
            default -> {
                // TIME_TO_MARKET_DEV / REUSE_GAIN / QUALITY_LOSS_COST：行业包里 source 就是 "-"，
                // 没有取数依据 → 不写，保持「暂未评估」
                return false;
            }
        }
    }

    private void write(Metric m, BigDecimal value, String valueText) {
        // 有值才带就绪度；没值就不该声称"这条数已经评估过了"
        metricMapper.updateEvaluation(m.getOid(), value, valueText, value == null ? null : m.getLevel());
    }

    // ==================== 工具 ====================

    private static BigDecimal pct(double value) {
        return BigDecimal.valueOf(value).setScale(2, RoundingMode.HALF_UP);
    }

    /** 金额文案：千分位 + 两位小数（元）—— 只用于 valueText，不参与计算 */
    private static String money(BigDecimal value) {
        return value == null ? "-" : "¥" + String.format(Locale.ROOT, "%,.2f", value);
    }

    /**
     * 解析 {@code yyyy-MM-dd}。空值 → null；格式不对 → 记一条 warn 后当没填，
     * 不抛异常（一项脏属性不该让整份指标面板打不开），也不能猜成"随便某一天"。
     */
    private static LocalDate parseDate(String text, String label, String ownerOid) {
        if (text == null || text.trim().isEmpty()) {
            return null;
        }
        try {
            return LocalDate.parse(text.trim());
        } catch (DateTimeParseException e) {
            log.warn("{}不是合法日期: owner={} raw={}", label, ownerOid, text);
            return null;
        }
    }

    private static Object pick(Map<String, Object> row, String key) {
        Object v = row.get(key);
        return v != null ? v : row.get(key.toUpperCase(Locale.ROOT));
    }

    private static String text(Object value) {
        if (value == null) {
            return null;
        }
        String s = String.valueOf(value).trim();
        return s.isEmpty() ? null : s;
    }

    private static long number(Object value) {
        if (value instanceof Number n) {
            return n.longValue();
        }
        try {
            return value == null ? 0L : Long.parseLong(String.valueOf(value).trim());
        } catch (NumberFormatException e) {
            return 0L;
        }
    }

    /** 一次评估要用到的事实，集中取一次 —— 每项指标各查一遍库，11 项就是几十个往返 */
    private static final class Facts {
        private int parts;
        private int altCovered;
        /** 可认证件（ELECTRONIC / PCBA）数 —— 准入率与优选率的分母 */
        private int certifiableParts;
        /** 有 APPROVED / PREFERRED AML 的可认证件数 */
        private int qualifiedParts;
        /** 有 PREFERRED AML 的可认证件数 */
        private int preferredParts;
        private int lifeFilled;
        private int eolRisky;
        private int bomLines;
        private int missingCostLines;
        private Double totalCost;
        /** 计划上市日期（IBA TARGET_LAUNCH_DATE）；没填 = null */
        private LocalDate targetLaunchDate;
        /** 实际上市日期（LDCP 通过日）；门没过 = null */
        private LocalDate actualLaunchDate;
        /** 容器自己登记的目标成本（元）；没填 = null，不拿父系列顶替 */
        private BigDecimal targetCost;
        /** 容器当前卷积总成本（系列并入旗下型号）；没有可计价顶层件 = null */
        private Double actualCost;
        private long issueTotal;
        private final Map<String, Long> issues = new java.util.HashMap<>();
    }
}
