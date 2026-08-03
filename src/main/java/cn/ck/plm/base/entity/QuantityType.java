/*
 * Copyright (c) 2025 深圳乘恺科技有限公司
 * All rights reserved.
 *
 * @author Kiween.Hu; Roney.Liu
 */

package cn.ck.plm.base.entity;

import java.util.Arrays;
import java.util.List;

/**
 * 量纲类型枚举。
 *
 * <p>决定单位属于哪个物理量类别。不同量纲之间不可换算，
 * 同量纲内才可以换算。
 *
 * <p>量纲是不可扩展的枚举——不能在运行时新增 QuantityType。
 * 如需自定义单位，可通过 IBA 软类型属性实现。
 *
 * <h3>量纲定义</h3>
 * <table>
 *   <tr><th>枚举</th><th>显示名</th><th>基准单位</th><th>典型单位</th><th>业务场景</th></tr>
 *   <tr><td>DISCRETE</td><td>离散计数</td><td>ea</td><td>ea、pcs、box、set</td><td>离散计数件，最常见的 PLM 部件单位</td></tr>
 *   <tr><td>MASS</td><td>质量</td><td>kg</td><td>kg、g、mg、t、lb、oz</td><td>重量计算、物料核算</td></tr>
 *   <tr><td>LENGTH</td><td>长度</td><td>m</td><td>m、cm、mm、km、in、ft、yd</td><td>尺寸标注、CAD 集成</td></tr>
 *   <tr><td>AREA</td><td>面积</td><td>m²</td><td>m²、cm²、mm²、ft²</td><td>面积计算（板材、涂层）</td></tr>
 *   <tr><td>VOLUME</td><td>体积</td><td>m³</td><td>m³、L、mL、gal</td><td>体积计算（液体、容积）</td></tr>
 *   <tr><td>TIME</td><td>时间</td><td>s</td><td>s、min、h、day</td><td>工时统计、保质期</td></tr>
 *   <tr><td>TEMPERATURE</td><td>温度</td><td>K</td><td>K、°C、°F</td><td>环境条件、工艺参数</td></tr>
 *   <tr><td>ANGLE</td><td>角度</td><td>rad</td><td>rad、deg</td><td>CAD 角度、机械设计</td></tr>
 *   <tr><td>ELECTRIC_CURRENT</td><td>电流</td><td>A</td><td>A、mA</td><td>电气参数</td></tr>
 *   <tr><td>LUMINOUS_INTENSITY</td><td>发光强度</td><td>cd</td><td>cd</td><td>光学参数</td></tr>
 * </table>
 */
public enum QuantityType {

    /** 离散计数件（基准：ea） */
    DISCRETE("离散计数", "ea", "ea", "pcs", "box", "set"),

    /** 质量（基准：kg） */
    MASS("质量", "kg", "kg", "g", "mg", "t", "lb", "oz"),

    /** 长度（基准：m） */
    LENGTH("长度", "m", "m", "cm", "mm", "km", "in", "ft", "yd"),

    /** 面积（基准：m²） */
    AREA("面积", "m²", "m²", "cm²", "mm²", "ft²"),

    /** 体积（基准：m³） */
    VOLUME("体积", "m³", "m³", "L", "mL", "gal"),

    /** 时间（基准：s） */
    TIME("时间", "s", "s", "min", "h", "day"),

    /** 温度（基准：K） */
    TEMPERATURE("温度", "K", "K", "°C", "°F"),

    /** 角度（基准：rad） */
    ANGLE("角度", "rad", "rad", "deg"),

    /** 电流（基准：A） */
    ELECTRIC_CURRENT("电流", "A", "A", "mA"),

    /** 发光强度（基准：cd） */
    LUMINOUS_INTENSITY("发光强度", "cd", "cd");

    private final String displayName;
    private final String baseUnitName;
    private final List<String> typicalUnits;

    QuantityType(String displayName, String baseUnitName, String... typicalUnits) {
        this.displayName = displayName;
        this.baseUnitName = baseUnitName;
        this.typicalUnits = Arrays.asList(typicalUnits);
    }

    public String getDisplayName() {
        return displayName;
    }

    /** 获取该量纲的基准单位名称 */
    public String getBaseUnitName() {
        return baseUnitName;
    }

    /** 获取该量纲的典型单位名称列表 */
    public List<String> getTypicalUnits() {
        return typicalUnits;
    }
}
