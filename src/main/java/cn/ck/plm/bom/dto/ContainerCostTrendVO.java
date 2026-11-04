/*
 * Copyright (c) 2026~2028 深圳乘恺科技有限公司
 * All rights reserved.
 *
 * @author Kiween.Hu; Roney.Liu
 */

package cn.ck.plm.bom.dto;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 容器（产品系列 / 型号）的成本趋势：<b>现值 + 按 BOM 快照回放的曲线 + 数据缺口</b>。
 *
 * <h3>曲线就用 BOM 快照画，不另建"成本快照"</h3>
 * <p>平台里只存在一种时间序列：{@code ck_bom_snapshot}（BOM 结构快照，按零件版本保存）。
 * 它每行带着当时的「数量 / 单位成本」，所以成本可以从它<b>算出来</b>，不必再存一份"成本快照"——
 * 多一份存储就多一处会和结构对不上的数据。
 *
 * <p>曲线与总额<b>同口径</b>，都是卷积成本：
 * <ul>
 *   <li>{@link #currentTotalCost}：当前版本的卷积总成本（Σ 数量 × 子件完整单位成本，逐层累加）；</li>
 *   <li>{@link Series#getPoints()} 的每个快照点：Σ(快照里的用量 × 子件在<b>该快照版本</b>上的完整单位成本)，
 *       末点则是当前卷积成本 —— 所以只有 1 份快照时，图上正好是「那时 → 现在」两个点。</li>
 * </ul>
 *
 * <h3>每个顶层件一条线</h3>
 * <p>容器里的零件多是彼此的装配件（安装支架装在整机上），全部求和会重复计价；
 * 只取"容器内没有父件的零件"作为计价入口。一个容器可能有多条入口（系列页下多个型号），
 * 而每条线的成本只属于它自己那个件 —— 硬合成一条线会让"换了型号"看起来像"成本暴跌"，
 * 所以按件分线（{@link #series}），前端各画一条。
 *
 * <h3>最后一点是"当前"</h3>
 * <p>{@code point.current = true} 表示这一点是实时算的当前值，不是快照。有了它，
 * 只要保存过 1 份 BOM 快照就能画出"那时 → 现在"的一段变化，不需要额外打点。
 */
public class ContainerCostTrendVO {

    /** 当前容器 oid */
    private String containerOid;

    /** 容器种类：MODEL / LINE / UNKNOWN（未知时不聚合子型号） */
    private String containerKind;

    /** 实际参与计价的容器（系列页 = 本系列 + 旗下各型号，因为 BOM 挂在型号的零件上） */
    private List<String> containerOids = new ArrayList<>();

    /** 是否有 BOM 行（false 时现值为 0，且不该画任何线） */
    private Boolean hasBom = Boolean.FALSE;

    /** 计价入口个数（容器内没有父件的零件） */
    private Integer topPartCount = 0;

    /** 参与的 BOM 行数（含各层，来自成本报告） */
    private Integer totalLines = 0;

    /** 未填「单位成本」的行数（按 0 计入，会低估成本） */
    private Integer missingCostLines = 0;

    /** 未填单位成本的顶层件编码（最多 5 个，用于提示去哪儿补数） */
    private List<String> missingCostNumbers = new ArrayList<>();

    /** 当前卷积总成本（含所有下级） */
    private Double currentTotalCost = 0D;

    /** 当前本层直接成本（各条线当前值之和，与曲线同口径） */
    private Double currentDirectCost = 0D;

    /** 当前值的时间（取数时刻） */
    private LocalDateTime currentTime;

    /** 口径说明（前端直接展示，避免同一张卡片在两处解释不一致） */
    private String basis;

    /** 每个顶层件一条曲线（按成本从高到低，前端最多画前 3 条） */
    private List<Series> series = new ArrayList<>();

    /** 数据缺口提示；没有问题时为空列表 */
    private List<String> warnings = new ArrayList<>();

    /** 一个顶层件的成本曲线 */
    public static class Series {

        /** 零件主对象 oid */
        private String partOid;

        /** 零件编码 */
        private String partNumber;

        /** 零件名称 */
        private String partName;

        /** 当前版本（A-Z.2） */
        private String versionLabel;

        /** 当前卷积总成本（曲线最后一点的纵值，与大数字同口径） */
        private Double currentCost;

        /** 快照点数（不含"当前"点） */
        private Integer snapshotCount = 0;

        /** 曲线点：快照点按时间升序 + 末尾一个当前点 */
        private List<Point> points = new ArrayList<>();

        public String getPartOid() { return partOid; }
        public void setPartOid(String partOid) { this.partOid = partOid; }

        public String getPartNumber() { return partNumber; }
        public void setPartNumber(String partNumber) { this.partNumber = partNumber; }

        public String getPartName() { return partName; }
        public void setPartName(String partName) { this.partName = partName; }

        public String getVersionLabel() { return versionLabel; }
        public void setVersionLabel(String versionLabel) { this.versionLabel = versionLabel; }

        public Double getCurrentCost() { return currentCost; }
        public void setCurrentCost(Double currentCost) { this.currentCost = currentCost; }

        public Integer getSnapshotCount() { return snapshotCount; }
        public void setSnapshotCount(Integer snapshotCount) { this.snapshotCount = snapshotCount; }

        public List<Point> getPoints() { return points; }
        public void setPoints(List<Point> points) { this.points = points; }
    }

    /** 曲线上的一个点 */
    public static class Point {

        /** 时间：快照点取快照创建时间，当前点取取数时刻 */
        private LocalDateTime time;

        /** 卷积成本：快照点＝Σ(当时用量 × 子件当时版本的完整单位成本)，当前点＝当前卷积总成本 */
        private Double cost;

        /** 标签：快照名称，或"当前" */
        private String label;

        /** 快照绑定的版本（当前点为 null） */
        private String versionLabel;

        /** 是否"当前"点（实时算的，不是快照） */
        private Boolean current = Boolean.FALSE;

        public LocalDateTime getTime() { return time; }
        public void setTime(LocalDateTime time) { this.time = time; }

        public Double getCost() { return cost; }
        public void setCost(Double cost) { this.cost = cost; }

        public String getLabel() { return label; }
        public void setLabel(String label) { this.label = label; }

        public String getVersionLabel() { return versionLabel; }
        public void setVersionLabel(String versionLabel) { this.versionLabel = versionLabel; }

        public Boolean getCurrent() { return current; }
        public void setCurrent(Boolean current) { this.current = current; }
    }

    public String getContainerOid() { return containerOid; }
    public void setContainerOid(String containerOid) { this.containerOid = containerOid; }

    public String getContainerKind() { return containerKind; }
    public void setContainerKind(String containerKind) { this.containerKind = containerKind; }

    public List<String> getContainerOids() { return containerOids; }
    public void setContainerOids(List<String> containerOids) { this.containerOids = containerOids; }

    public Boolean getHasBom() { return hasBom; }
    public void setHasBom(Boolean hasBom) { this.hasBom = hasBom; }

    public Integer getTopPartCount() { return topPartCount; }
    public void setTopPartCount(Integer topPartCount) { this.topPartCount = topPartCount; }

    public Integer getTotalLines() { return totalLines; }
    public void setTotalLines(Integer totalLines) { this.totalLines = totalLines; }

    public Integer getMissingCostLines() { return missingCostLines; }
    public void setMissingCostLines(Integer missingCostLines) { this.missingCostLines = missingCostLines; }

    public List<String> getMissingCostNumbers() { return missingCostNumbers; }
    public void setMissingCostNumbers(List<String> missingCostNumbers) { this.missingCostNumbers = missingCostNumbers; }

    public Double getCurrentTotalCost() { return currentTotalCost; }
    public void setCurrentTotalCost(Double currentTotalCost) { this.currentTotalCost = currentTotalCost; }

    public Double getCurrentDirectCost() { return currentDirectCost; }
    public void setCurrentDirectCost(Double currentDirectCost) { this.currentDirectCost = currentDirectCost; }

    public LocalDateTime getCurrentTime() { return currentTime; }
    public void setCurrentTime(LocalDateTime currentTime) { this.currentTime = currentTime; }

    public String getBasis() { return basis; }
    public void setBasis(String basis) { this.basis = basis; }

    public List<Series> getSeries() { return series; }
    public void setSeries(List<Series> series) { this.series = series; }

    public List<String> getWarnings() { return warnings; }
    public void setWarnings(List<String> warnings) { this.warnings = warnings; }
}
