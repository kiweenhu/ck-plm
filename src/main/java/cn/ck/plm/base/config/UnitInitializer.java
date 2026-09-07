/*
 * Copyright (c) 2025 深圳乘恺科技有限公司
 * All rights reserved.
 *
 * @author Kiween.Hu; Roney.Liu
 */

package cn.ck.plm.base.config;

import cn.ck.plm.base.entity.QuantityType;
import cn.ck.plm.base.entity.Unit;
import cn.ck.plm.base.mapper.UnitMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

/**
 * 应用启动时初始化常用计量单位到 ck_unit 表。
 *
 * <h3>覆盖范围</h3>
 * 覆盖 {@link QuantityType} 全部 10 个量纲的典型单位，并补充少量工程常用单位：
 * <ul>
 *   <li>{@link QuantityType#DISCRETE} 离散计数：ea / pcs / set / box / pair / roll / sheet</li>
 *   <li>{@link QuantityType#MASS} 质量：kg(SI) / g / mg / t / lb / oz</li>
 *   <li>{@link QuantityType#LENGTH} 长度：m(SI) / cm / mm / um / km / in / ft / yd</li>
 *   <li>{@link QuantityType#AREA} 面积：m²(SI) / cm² / mm² / ft²</li>
 *   <li>{@link QuantityType#VOLUME} 体积：m³(SI) / L / mL / gal</li>
 *   <li>{@link QuantityType#TIME} 时间：s(SI) / min / h / day</li>
 *   <li>{@link QuantityType#TEMPERATURE} 温度：K(SI) / °C / °F（带 offset 偏移换算）</li>
 *   <li>{@link QuantityType#ANGLE} 角度：rad(SI) / deg</li>
 *   <li>{@link QuantityType#ELECTRIC_CURRENT} 电流：A(SI) / mA / uA</li>
 *   <li>{@link QuantityType#LUMINOUS_INTENSITY} 发光强度：cd(SI)</li>
 * </ul>
 *
 * <h3>换算公式</h3>
 * <pre>{@code
 *   基准值 = 当前值 × factor + offset
 * }</pre>
 * 例：°C → K 为 {@code K = °C × 1 + 273.15}；°F → K 为 {@code K = °F × 5/9 + 255.372...}
 *
 * <h3>幂等性</h3>
 * 仅当单位 name 不存在时才插入。已存在的单位（含用户后续自定义修改）不会被覆盖，
 * 避免每次启动重置管理员的调整。
 */
@Component
public class UnitInitializer implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(UnitInitializer.class);

    private final UnitMapper unitMapper;

    public UnitInitializer(UnitMapper unitMapper) {
        this.unitMapper = unitMapper;
    }

    @Override
    public void run(String... args) {
        log.info("开始初始化常用计量单位...");
        int added = 0;

        // ===== 离散计数（基准 ea）=====
        added += init("ea", "个", QuantityType.DISCRETE, false, "ea", 1.0, 0.0, 1,
                "离散计数基本单位（each）");
        added += init("pcs", "件", QuantityType.DISCRETE, false, "ea", 1.0, 0.0, 2,
                "件（pieces）");
        added += init("set", "套", QuantityType.DISCRETE, false, "ea", 1.0, 0.0, 3,
                "套（set）");
        added += init("box", "盒", QuantityType.DISCRETE, false, "ea", 1.0, 0.0, 4,
                "盒（box）");
        added += init("pair", "对", QuantityType.DISCRETE, false, "ea", 1.0, 0.0, 5,
                "对/双（pair）");
        added += init("roll", "卷", QuantityType.DISCRETE, false, "ea", 1.0, 0.0, 6,
                "卷（roll）");
        added += init("sheet", "张", QuantityType.DISCRETE, false, "ea", 1.0, 0.0, 7,
                "张（sheet）");

        // ===== 质量（基准 kg）=====
        added += init("kg", "kg", QuantityType.MASS, true, "kg", 1.0, 0.0, 10,
                "千克（SI 基本单位）");
        added += init("g", "g", QuantityType.MASS, false, "kg", 0.001, 0.0, 11,
                "克，1 g = 0.001 kg");
        added += init("mg", "mg", QuantityType.MASS, false, "kg", 1.0e-6, 0.0, 12,
                "毫克，1 mg = 1e-6 kg");
        added += init("t", "t", QuantityType.MASS, false, "kg", 1000.0, 0.0, 13,
                "吨，1 t = 1000 kg");
        added += init("lb", "lb", QuantityType.MASS, false, "kg", 0.45359237, 0.0, 14,
                "磅，1 lb = 0.45359237 kg");
        added += init("oz", "oz", QuantityType.MASS, false, "kg", 0.028349523125, 0.0, 15,
                "盎司，1 oz = 0.028349523125 kg");

        // ===== 长度（基准 m）=====
        added += init("m", "m", QuantityType.LENGTH, true, "m", 1.0, 0.0, 20,
                "米（SI 基本单位）");
        added += init("cm", "cm", QuantityType.LENGTH, false, "m", 0.01, 0.0, 21,
                "厘米，1 cm = 0.01 m");
        added += init("mm", "mm", QuantityType.LENGTH, false, "m", 0.001, 0.0, 22,
                "毫米，1 mm = 0.001 m");
        added += init("um", "μm", QuantityType.LENGTH, false, "m", 1.0e-6, 0.0, 23,
                "微米，1 μm = 1e-6 m");
        added += init("km", "km", QuantityType.LENGTH, false, "m", 1000.0, 0.0, 24,
                "千米，1 km = 1000 m");
        added += init("in", "in", QuantityType.LENGTH, false, "m", 0.0254, 0.0, 25,
                "英寸，1 in = 0.0254 m");
        added += init("ft", "ft", QuantityType.LENGTH, false, "m", 0.3048, 0.0, 26,
                "英尺，1 ft = 0.3048 m");
        added += init("yd", "yd", QuantityType.LENGTH, false, "m", 0.9144, 0.0, 27,
                "码，1 yd = 0.9144 m");

        // ===== 面积（基准 m²）=====
        added += init("m²", "m²", QuantityType.AREA, true, "m²", 1.0, 0.0, 30,
                "平方米（SI 导出单位）");
        added += init("cm²", "cm²", QuantityType.AREA, false, "m²", 1.0e-4, 0.0, 31,
                "平方厘米，1 cm² = 1e-4 m²");
        added += init("mm²", "mm²", QuantityType.AREA, false, "m²", 1.0e-6, 0.0, 32,
                "平方毫米，1 mm² = 1e-6 m²");
        added += init("ft²", "ft²", QuantityType.AREA, false, "m²", 0.09290304, 0.0, 33,
                "平方英尺，1 ft² = 0.09290304 m²");

        // ===== 体积（基准 m³）=====
        added += init("m³", "m³", QuantityType.VOLUME, true, "m³", 1.0, 0.0, 40,
                "立方米（SI 导出单位）");
        added += init("L", "L", QuantityType.VOLUME, false, "m³", 0.001, 0.0, 41,
                "升，1 L = 0.001 m³");
        added += init("mL", "mL", QuantityType.VOLUME, false, "m³", 1.0e-6, 0.0, 42,
                "毫升，1 mL = 1e-6 m³");
        added += init("gal", "gal", QuantityType.VOLUME, false, "m³", 0.003785411784, 0.0, 43,
                "加仑（美制），1 gal = 0.003785411784 m³");

        // ===== 时间（基准 s）=====
        added += init("s", "s", QuantityType.TIME, true, "s", 1.0, 0.0, 50,
                "秒（SI 基本单位）");
        added += init("min", "min", QuantityType.TIME, false, "s", 60.0, 0.0, 51,
                "分钟，1 min = 60 s");
        added += init("h", "h", QuantityType.TIME, false, "s", 3600.0, 0.0, 52,
                "小时，1 h = 3600 s");
        added += init("day", "d", QuantityType.TIME, false, "s", 86400.0, 0.0, 53,
                "天，1 d = 86400 s");

        // ===== 温度（基准 K，非线性换算需 offset）=====
        added += init("K", "K", QuantityType.TEMPERATURE, true, "K", 1.0, 0.0, 60,
                "开尔文（SI 基本单位）");
        // K = °C × 1 + 273.15
        added += init("°C", "°C", QuantityType.TEMPERATURE, false, "K", 1.0, 273.15, 61,
                "摄氏度，K = °C + 273.15");
        // K = °F × 5/9 + 255.3722...
        added += init("°F", "°F", QuantityType.TEMPERATURE, false, "K",
                5.0 / 9.0, 255.3722222222222, 62,
                "华氏度，K = °F × 5/9 + 255.3722");

        // ===== 角度（基准 rad）=====
        added += init("rad", "rad", QuantityType.ANGLE, true, "rad", 1.0, 0.0, 70,
                "弧度（SI 导出单位）");
        added += init("deg", "°", QuantityType.ANGLE, false, "rad", Math.PI / 180.0, 0.0, 71,
                "度，1° = π/180 rad");

        // ===== 电流（基准 A）=====
        added += init("A", "A", QuantityType.ELECTRIC_CURRENT, true, "A", 1.0, 0.0, 80,
                "安培（SI 基本单位）");
        added += init("mA", "mA", QuantityType.ELECTRIC_CURRENT, false, "A", 0.001, 0.0, 81,
                "毫安，1 mA = 0.001 A");
        added += init("uA", "μA", QuantityType.ELECTRIC_CURRENT, false, "A", 1.0e-6, 0.0, 82,
                "微安，1 μA = 1e-6 A");

        // ===== 发光强度（基准 cd）=====
        added += init("cd", "cd", QuantityType.LUMINOUS_INTENSITY, true, "cd", 1.0, 0.0, 90,
                "坎德拉（SI 基本单位）");

        log.info("常用计量单位初始化完成，本次新增 {} 个单位", added);
    }

    /**
     * 插入单个单位（已存在则跳过，返回 0）。
     *
     * @param name        单位唯一标识（与 ck_unit.name 对应，也是 base_unit_name 的引用值）
     * @param display     UI 展示符号
     * @param qType       所属量纲
     * @param isSI        是否为国际单位制单位
     * @param baseUnitName 同量纲基准单位的 name（基准单位自身指向自己）
     * @param factor      到基准单位的换算系数
     * @param offset      换算偏移量（温度等非线性换算使用）
     * @param sortOrder   排序权重（越小越靠前）
     * @param description 备注说明
     */
    private int init(String name, String display, QuantityType qType, boolean isSI,
                     String baseUnitName, double factor, double offset,
                     int sortOrder, String description) {
        if (unitMapper.existsByName(name) > 0) {
            return 0;
        }
        Unit unit = new Unit();
        unit.setName(name);
        unit.setDisplay(display);
        unit.setQuantityType(qType.name());
        unit.setIsSI(isSI);
        unit.setBaseUnitName(baseUnitName);
        unit.setFactor(factor);
        unit.setOffset(offset);
        unit.setSortOrder(sortOrder);
        unit.setDescription(description);
        unit.setCreator("system");
        unit.setUpdater("system");
        LocalDateTime now = LocalDateTime.now();
        unit.setCreatedAt(now);
        unit.setUpdatedAt(now);
        unitMapper.insert(unit);
        log.debug("  新增单位: {} ({})", name, display);
        return 1;
    }
}
