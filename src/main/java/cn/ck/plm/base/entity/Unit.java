/*
 * Copyright (c) 2025 深圳乘恺科技有限公司
 * All rights reserved.
 *
 * @author Kiween.Hu; Roney.Liu
 */

package cn.ck.plm.base.entity;

/**
 * 计量单位实体。
 *
 * <p>每个单位归属于一个 {@link QuantityType}（量纲类型），
 * 同量纲内的单位通过 {@link #factor} 和 {@link #offset} 换算到 {@link #baseUnit}。
 *
 * <h3>换算公式</h3>
 * <pre>{@code
 *   基准值 = 当前值 × factor + offset
 * }</pre>
 *
 * <h3>字段说明</h3>
 * <table>
 *   <tr><th>name</th><td>程序内唯一标识，如 kg、ea、m。数据库列存储的就是这个值</td></tr>
 *   <tr><th>display</th><td>UI 展示符号，大多数与 name 一致，但有些场景需区分（如 name=ea，display=EA）</td></tr>
 *   <tr><th>quantityType</th><td>量纲类型</td></tr>
 *   <tr><th>isSI</th><td>是否为国际单位制（SI）标准单位</td></tr>
 *   <tr><th>baseUnitName</th><td>同量纲下的基准单位名称，不存储完整对象引用</td></tr>
 *   <tr><th>factor</th><td>到基准单位的换算系数</td></tr>
 *   <tr><th>offset</th><td>换算偏移量，用于非线性转换（如温度 °C→K 的 offset=273.15）</td></tr>
 * </table>
 */
public class Unit extends BaseEntity {

    /** 枚举常量名，程序内唯一标识（如 kg、ea、m） */
    private String name;

    /** UI 展示符号 */
    private String display;

    /** 量纲类型 */
    private String quantityType;

    /** 是否为国际单位制（SI）标准单位 */
    private Boolean isSI;

    /** 该量纲下的基准单位名称（name） */
    private String baseUnitName;

    /** 到基准单位的换算系数（基准值 = 当前值 × factor + offset） */
    private Double factor;

    /** 换算偏移量 */
    private Double offset;

    /** 排序权重（数值越小越靠前） */
    private Integer sortOrder;

    /** 备注 */
    private String description;

    // ==================== 构造方法 ====================

    public Unit() {}

    // ==================== Getter / Setter ====================

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getDisplay() { return display; }
    public void setDisplay(String display) { this.display = display; }

    public String getQuantityType() { return quantityType; }
    public void setQuantityType(String quantityType) { this.quantityType = quantityType; }

    public Boolean getIsSI() { return isSI; }
    public void setIsSI(Boolean isSI) { this.isSI = isSI; }

    public String getBaseUnitName() { return baseUnitName; }
    public void setBaseUnitName(String baseUnitName) { this.baseUnitName = baseUnitName; }

    public Double getFactor() { return factor; }
    public void setFactor(Double factor) { this.factor = factor; }

    public Double getOffset() { return offset; }
    public void setOffset(Double offset) { this.offset = offset; }

    public Integer getSortOrder() { return sortOrder; }
    public void setSortOrder(Integer sortOrder) { this.sortOrder = sortOrder; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    @Override
    public String toString() {
        return "Unit{name='" + name + "', display='" + display +
                "', quantityType=" + quantityType + ", isSI=" + isSI +
                ", baseUnitName='" + baseUnitName + "', factor=" + factor +
                ", offset=" + offset + '}';
    }
}
