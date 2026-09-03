/*
 * Copyright (c) 2025 深圳乘恺科技有限公司
 * All rights reserved.
 *
 * @author Kiween.Hu; Roney.Liu
 */

package cn.ck.plm.cls.config;

import cn.ck.plm.base.util.TenantContext;
import cn.ck.plm.cls.entity.Classification;
import cn.ck.plm.cls.entity.ClassificationIBA;
import cn.ck.plm.cls.mapper.ClassificationIBAMapper;
import cn.ck.plm.cls.mapper.ClassificationMapper;
import cn.ck.plm.softtype.entity.IBA;
import cn.ck.plm.softtype.mapper.IBAMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

/**
 * 应用启动时初始化平台级预置「分类」。
 *
 * <p>包含两类预置分类，均直接作为普通分类预置到平台租户：
 * <ul>
 *   <li><b>电子元器件分类</b>—— 参照嘉立创（立创）商城的分类体系构建，
 *       采用「大类 → 细分类」多级分类树，并为每个细分类定义对应的器件关键参数（IBA 属性）。</li>
 *   <li><b>结构件分类</b>—— 钣金件、机加件、注塑件、铸造件、焊接件、标准件，
 *       并细分到具体工艺件，定义材质/厚度/表面处理/强度等级等属性。</li>
 * </ul>
 *
 * <p>幂等：以「根分类 identifier + 全部节点 identifier」判断是否已完成初始化；
 * 若检测到旧结构或结构变更，则清理该分类子树后按新结构重建。
 */
@Component
@Order(6)
public class ClassificationInitializer implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(ClassificationInitializer.class);
    private static final String PLATFORM = TenantContext.PLATFORM_TENANT_OID;

    private final ClassificationMapper clsMapper;
    private final ClassificationIBAMapper clsIbaMapper;
    private final IBAMapper ibaMapper;

    public ClassificationInitializer(ClassificationMapper clsMapper,
                                             ClassificationIBAMapper clsIbaMapper,
                                             IBAMapper ibaMapper) {
        this.clsMapper = clsMapper;
        this.clsIbaMapper = clsIbaMapper;
        this.ibaMapper = ibaMapper;
    }

    @Override
    public void run(String... args) {
        try {
            initElectronicClassification();
        } catch (Exception e) {
            log.warn("电子元器件分类初始化失败: {}", e.getMessage());
        }
        try {
            initStructureClassification();
        } catch (Exception e) {
            log.warn("结构件分类初始化失败: {}", e.getMessage());
        }
    }

    // ==================== 电子元器件分类（嘉立创结构） ====================

    private void initElectronicClassification() {
        Category root = new Category("电子元器件", null).with(
                cat("电阻器", "resistor").with(
                        cat("贴片电阻", "chip-resistor", "RESISTANCE", "TOLERANCE", "POWER_RATING", "TEMP_COEFFICIENT", "PACKAGE"),
                        cat("插件电阻", "through-hole-resistor", "RESISTANCE", "TOLERANCE", "POWER_RATING", "PACKAGE"),
                        cat("排阻/网络电阻", "resistor-network", "RESISTANCE", "TOLERANCE", "POWER_RATING", "PACKAGE"),
                        cat("电流采样电阻", "current-sense-resistor", "RESISTANCE", "TOLERANCE", "POWER_RATING", "PACKAGE"),
                        cat("精密电阻", "precision-resistor", "RESISTANCE", "TOLERANCE", "POWER_RATING", "TEMP_COEFFICIENT", "PACKAGE"),
                        cat("热敏电阻", "thermistor", "RESISTANCE", "B_VALUE", "TOLERANCE", "PACKAGE"),
                        cat("压敏电阻", "varistor", "VARISTOR_VOLTAGE", "CLAMP_VOLTAGE", "PACKAGE")
                ),
                cat("电容器", "capacitor").with(
                        cat("贴片电容(MLCC)", "mlcc", "CAPACITANCE", "VOLTAGE_RATING", "TOLERANCE", "TEMP_CHARACTERISTIC", "PACKAGE"),
                        cat("钽电容", "tantalum-capacitor", "CAPACITANCE", "VOLTAGE_RATING", "TOLERANCE", "PACKAGE"),
                        cat("贴片铝电解电容", "smd-aluminum-capacitor", "CAPACITANCE", "VOLTAGE_RATING", "TOLERANCE", "PACKAGE"),
                        cat("直插铝电解电容", "dip-aluminum-capacitor", "CAPACITANCE", "VOLTAGE_RATING", "TOLERANCE", "PACKAGE"),
                        cat("薄膜电容", "film-capacitor", "CAPACITANCE", "VOLTAGE_RATING", "TOLERANCE", "PACKAGE"),
                        cat("超级电容", "super-capacitor", "CAPACITANCE", "VOLTAGE_RATING", "PACKAGE")
                ),
                cat("电感器", "inductor").with(
                        cat("贴片电感", "chip-inductor", "INDUCTANCE", "RATED_CURRENT", "DC_RESISTANCE", "PACKAGE"),
                        cat("功率电感", "power-inductor", "INDUCTANCE", "RATED_CURRENT", "SATURATION_CURRENT", "DC_RESISTANCE"),
                        cat("磁珠", "ferrite-bead", "IMPEDANCE", "RATED_CURRENT", "PACKAGE"),
                        cat("共模滤波器", "common-mode-filter", "IMPEDANCE", "RATED_CURRENT", "VOLTAGE_RATING")
                ),
                cat("二极管", "diode").with(
                        cat("肖特基二极管", "schottky-diode", "FORWARD_CURRENT", "REVERSE_VOLTAGE", "FORWARD_VOLTAGE", "PACKAGE"),
                        cat("稳压二极管", "zener-diode", "ZENER_VOLTAGE", "POWER_RATING", "TOLERANCE", "PACKAGE"),
                        cat("整流二极管", "rectifier-diode", "FORWARD_CURRENT", "REVERSE_VOLTAGE", "FORWARD_VOLTAGE", "PACKAGE"),
                        cat("快恢复二极管", "fast-recovery-diode", "FORWARD_CURRENT", "REVERSE_VOLTAGE", "RECOVERY_TIME", "PACKAGE"),
                        cat("TVS二极管", "tvs-diode", "BREAKDOWN_VOLTAGE", "CLAMP_VOLTAGE", "PEAK_PULSE_POWER", "PACKAGE"),
                        cat("ESD保护二极管", "esd-diode", "BREAKDOWN_VOLTAGE", "CLAMP_VOLTAGE", "PACKAGE"),
                        cat("整流桥", "bridge-rectifier", "FORWARD_CURRENT", "REVERSE_VOLTAGE", "FORWARD_VOLTAGE", "PACKAGE")
                ),
                cat("三极管", "transistor").with(
                        cat("NPN三极管", "npn-transistor", "POLARITY", "COLLECTOR_CURRENT", "COLLECTOR_EMITTER_VOLTAGE", "DC_GAIN", "PACKAGE"),
                        cat("PNP三极管", "pnp-transistor", "POLARITY", "COLLECTOR_CURRENT", "COLLECTOR_EMITTER_VOLTAGE", "DC_GAIN", "PACKAGE"),
                        cat("数字晶体管", "digital-transistor", "POLARITY", "COLLECTOR_CURRENT", "COLLECTOR_EMITTER_VOLTAGE", "PACKAGE")
                ),
                cat("MOSFET", "mosfet").with(
                        cat("N沟道MOSFET", "n-mosfet", "CHANNEL_TYPE", "DRAIN_SOURCE_VOLTAGE", "DRAIN_CURRENT", "ON_RESISTANCE", "PACKAGE"),
                        cat("P沟道MOSFET", "p-mosfet", "CHANNEL_TYPE", "DRAIN_SOURCE_VOLTAGE", "DRAIN_CURRENT", "ON_RESISTANCE", "PACKAGE"),
                        cat("IGBT", "igbt", "COLLECTOR_CURRENT", "COLLECTOR_EMITTER_VOLTAGE", "PACKAGE")
                ),
                cat("电源管理IC", "power-ic").with(
                        cat("线性稳压器(LDO)", "ldo", "MODEL", "INPUT_VOLTAGE", "OUTPUT_VOLTAGE", "OUTPUT_CURRENT", "PACKAGE"),
                        cat("DC-DC电源芯片", "dc-dc", "MODEL", "INPUT_VOLTAGE", "OUTPUT_VOLTAGE", "OUTPUT_CURRENT", "PACKAGE"),
                        cat("LED驱动芯片", "led-driver", "MODEL", "INPUT_VOLTAGE", "OUTPUT_CURRENT", "PACKAGE"),
                        cat("电池保护芯片", "battery-protection", "MODEL", "WORKING_VOLTAGE", "PACKAGE")
                ),
                cat("单片机/微控制器", "mcu").with(
                        cat("单片机(MCU)", "mcu", "MODEL", "CORE", "MAIN_FREQUENCY", "FLASH_SIZE", "WORKING_VOLTAGE", "PACKAGE"),
                        cat("FPGA/CPLD", "fpga-cpld", "MODEL", "LOGIC_CELLS", "WORKING_VOLTAGE", "PACKAGE")
                ),
                cat("存储器", "memory").with(
                        cat("Flash存储器", "flash-memory", "MODEL", "CAPACITY", "INTERFACE", "WORKING_VOLTAGE", "PACKAGE"),
                        cat("EEPROM存储器", "eeprom-memory", "MODEL", "CAPACITY", "INTERFACE", "WORKING_VOLTAGE", "PACKAGE")
                ),
                cat("逻辑器件", "logic").with(
                        cat("逻辑门电路", "logic-gate", "MODEL", "SERIES", "WORKING_VOLTAGE", "PACKAGE"),
                        cat("缓冲器/驱动器", "buffer-driver", "MODEL", "SERIES", "CHANNEL_COUNT", "PACKAGE")
                ),
                cat("运算放大器/比较器", "amplifier").with(
                        cat("运算放大器", "op-amp", "MODEL", "CHANNEL_COUNT", "BANDWIDTH", "WORKING_VOLTAGE", "PACKAGE"),
                        cat("比较器", "comparator", "MODEL", "CHANNEL_COUNT", "WORKING_VOLTAGE", "PACKAGE")
                ),
                cat("连接器", "connector").with(
                        cat("线对板针座", "wire-to-board-header", "TYPE", "PIN_COUNT", "PITCH", "RATED_CURRENT", "PACKAGE"),
                        cat("胶壳", "housing", "TYPE", "PIN_COUNT", "PITCH"),
                        cat("FFC/FPC连接器", "ffc-fpc-connector", "TYPE", "PIN_COUNT", "PITCH", "VOLTAGE_RATING"),
                        cat("USB连接器", "usb-connector", "TYPE", "PIN_COUNT", "PACKAGE"),
                        cat("接线端子", "terminal-block", "TYPE", "PIN_COUNT", "PITCH", "RATED_CURRENT")
                ),
                cat("开关", "switch").with(
                        cat("轻触开关", "tactile-switch", "TYPE", "RATED_CURRENT", "CONTACT_FORM"),
                        cat("拨动开关", "toggle-switch", "TYPE", "RATED_CURRENT", "VOLTAGE_RATING"),
                        cat("拨码开关", "dip-switch", "PIN_COUNT", "PITCH", "RATED_CURRENT")
                ),
                cat("LED/光电器件", "optoelectronics").with(
                        cat("发光二极管(LED)", "led", "COLOR", "WAVELENGTH", "LUMINOUS_INTENSITY", "FORWARD_CURRENT", "PACKAGE"),
                        cat("数码管", "digital-display", "TYPE", "DIGIT_COUNT", "COLOR"),
                        cat("光电耦合器", "optocoupler", "TYPE", "FORWARD_CURRENT", "OUTPUT_CURRENT", "PACKAGE")
                ),
                cat("晶振/定时", "crystal").with(
                        cat("无源晶振", "crystal-resonator", "FREQUENCY", "FREQUENCY_TOLERANCE", "LOAD_CAPACITANCE", "PACKAGE"),
                        cat("有源晶振", "oscillator", "FREQUENCY", "WORKING_VOLTAGE", "PACKAGE")
                ),
                cat("保险丝/保护器件", "protection").with(
                        cat("自恢复保险丝", "ptc-fuse", "HOLD_CURRENT", "RATED_CURRENT", "VOLTAGE_RATING", "PACKAGE"),
                        cat("一次性保险丝", "fuse", "RATED_CURRENT", "VOLTAGE_RATING", "BREAKING_CAPACITY", "PACKAGE"),
                        cat("气体放电管", "gdt", "BREAKDOWN_VOLTAGE", "CLAMP_VOLTAGE")
                ),
                cat("继电器", "relay").with(
                        cat("电磁继电器", "electromagnetic-relay", "COIL_VOLTAGE", "CONTACT_FORM", "CONTACT_CURRENT"),
                        cat("固态继电器", "solid-state-relay", "INPUT_VOLTAGE", "OUTPUT_CURRENT", "OUTPUT_VOLTAGE")
                ),
                cat("传感器", "sensor").with(
                        cat("温度传感器", "temperature-sensor", "TYPE", "ACCURACY", "WORKING_VOLTAGE", "PACKAGE"),
                        cat("霍尔传感器", "hall-sensor", "TYPE", "WORKING_VOLTAGE", "PACKAGE"),
                        cat("电流传感器", "current-sensor", "TYPE", "RANGE", "WORKING_VOLTAGE", "PACKAGE")
                )
        );
        initClassification("电子元器件", "tpl-electronic", root);
    }

    // ==================== 结构件分类 ====================

    private void initStructureClassification() {
        Category root = new Category("结构件", null).with(
                cat("钣金件", "sheet-metal").with(
                        cat("折弯件", "bending", "MATERIAL", "THICKNESS", "SURFACE_TREATMENT", "PROCESS"),
                        cat("冲压件", "stamping", "MATERIAL", "THICKNESS", "SURFACE_TREATMENT", "PROCESS"),
                        cat("拉伸件", "drawing", "MATERIAL", "THICKNESS", "SURFACE_TREATMENT")
                ),
                cat("机加件", "machining").with(
                        cat("车削件", "turning", "MATERIAL", "PRECISION", "SURFACE_TREATMENT", "PROCESS"),
                        cat("铣削件", "milling", "MATERIAL", "PRECISION", "SURFACE_TREATMENT", "PROCESS"),
                        cat("钻孔件", "drilling", "MATERIAL", "PRECISION", "SURFACE_TREATMENT")
                ),
                cat("注塑件", "injection").with(
                        cat("通用注塑件", "general", "MATERIAL", "COLOR", "PROCESS"),
                        cat("精密注塑件", "precision", "MATERIAL", "COLOR", "PRECISION", "PROCESS")
                ),
                cat("铸造件", "casting").with(
                        cat("砂型铸造件", "sand-casting", "MATERIAL", "PROCESS", "SURFACE_TREATMENT"),
                        cat("压铸件", "die-casting", "MATERIAL", "PROCESS", "SURFACE_TREATMENT"),
                        cat("精密铸造件", "investment-casting", "MATERIAL", "PRECISION", "SURFACE_TREATMENT")
                ),
                cat("焊接件", "welding").with(
                        cat("角焊缝件", "fillet-weld", "MATERIAL", "PROCESS", "SURFACE_TREATMENT"),
                        cat("对接焊件", "butt-weld", "MATERIAL", "PROCESS", "SURFACE_TREATMENT")
                ),
                cat("标准件", "standard-part").with(
                        cat("螺栓", "bolt", "SPECIFICATION", "MATERIAL", "STRENGTH_GRADE", "SURFACE_TREATMENT"),
                        cat("螺母", "nut", "SPECIFICATION", "MATERIAL", "STRENGTH_GRADE", "SURFACE_TREATMENT"),
                        cat("垫圈", "washer", "SPECIFICATION", "MATERIAL", "SURFACE_TREATMENT"),
                        cat("销钉", "pin", "SPECIFICATION", "MATERIAL", "DIAMETER", "LENGTH"),
                        cat("轴承", "bearing", "SPECIFICATION", "MATERIAL", "PRECISION")
                )
        );
        initClassification("结构件", "tpl-structure", root);
    }

    // ==================== 通用分类初始化 ====================

    private void initClassification(String rootName, String rootIdentifier, Category rootCategory) {
        // 幂等判断：根 + 全部节点（大类/细分类）均已存在，则认为已初始化
        if (isInitialized(rootIdentifier, rootCategory)) {
            log.info("分类已初始化，跳过: {}", rootName);
            return;
        }

        // 清理旧结构（结构变更时），ck_cls_iba 由外键级联删除
        cleanupTree(rootIdentifier);

        // 重建根分类
        Classification rootNode = newClassification(rootName, rootIdentifier, null, 0);
        clsMapper.insert(rootNode);

        // 递归创建大类 + 细分类，并绑定 IBA
        buildChildren(rootIdentifier, rootNode.getOid(), rootCategory.children, 1);

        log.info("分类初始化完成: {}（根 + {} 个大类）", rootName, rootCategory.children.size());
    }

    /** 递归判断分类是否已完整初始化（根 + 全部大类/细分类节点均已存在） */
    private boolean isInitialized(String rootIdentifier, Category rootCategory) {
        if (clsMapper.existsByIdentifier(rootIdentifier, PLATFORM) <= 0) {
            return false;
        }
        for (Category child : rootCategory.children) {
            if (!isNodeInitialized(rootIdentifier, child)) {
                return false;
            }
        }
        return true;
    }

    private boolean isNodeInitialized(String parentIdentifier, Category node) {
        String identifier = parentIdentifier + "-" + node.slug;
        if (clsMapper.existsByIdentifier(identifier, PLATFORM) <= 0) {
            return false;
        }
        for (Category child : node.children) {
            if (!isNodeInitialized(identifier, child)) {
                return false;
            }
        }
        return true;
    }

    /** 清理平台租户下指定根分类的旧分类子树（IBA 关联由外键级联删除） */
    private void cleanupTree(String rootIdentifier) {
        Classification root = clsMapper.selectByIdentifier(rootIdentifier, PLATFORM);
        if (root == null) return;
        int removed = deleteSubtree(root.getOid());
        if (removed > 0) {
            log.info("已清理旧分类: {}（{} 个节点）", rootIdentifier, removed);
        }
    }

    /** 后序递归删除子树，返回删除的节点数 */
    private int deleteSubtree(String parentOid) {
        int count = 0;
        for (Classification child : clsMapper.selectByParentOid(parentOid, PLATFORM)) {
            count += deleteSubtree(child.getOid());
        }
        clsMapper.deleteByOid(parentOid);
        return count + 1;
    }

    /** 递归创建子分类并绑定 IBA */
    private void buildChildren(String parentIdentifier, String parentOid,
                               List<Category> children, int startSort) {
        int sort = startSort;
        for (Category child : children) {
            String identifier = parentIdentifier + "-" + child.slug;
            Classification node = newClassification(child.name, identifier, parentOid, sort);
            clsMapper.insert(node);

            bindIbas(node.getOid(), child.ibaCodes);

            if (!child.children.isEmpty()) {
                buildChildren(identifier, node.getOid(), child.children, 1);
            }
            sort++;
        }
    }

    /** 为分类节点绑定 IBA 属性（幂等：已绑定则跳过由 DB 唯一约束兜底） */
    private void bindIbas(String classificationOid, String[] ibaCodes) {
        if (ibaCodes == null || ibaCodes.length == 0) return;
        int ibaSort = 1;
        for (String code : ibaCodes) {
            IBA iba = ensureIba(code);
            if (iba == null) continue;
            ClassificationIBA mapping = new ClassificationIBA(classificationOid, iba.getOid());
            mapping.setTenantOid(PLATFORM);
            mapping.setSortOrder(ibaSort++);
            clsIbaMapper.insert(mapping);
        }
    }

    private Classification newClassification(String name, String identifier,
                                             String parentOid, int sortOrder) {
        Classification c = new Classification();
        c.setCode(identifier);
        c.setName(name);
        c.setDisplayName(name);
        c.setIdentifier(identifier);
        c.setParentOid(parentOid);
        c.setSortOrder(sortOrder);
        c.setTenantOid(PLATFORM);
        return c;
    }

    /** 按 code 查询平台租户的 IBA，不存在则创建（幂等） */
    private IBA ensureIba(String code) {
        String[] def = IBA_DEFS.get(code);
        if (def == null) {
            log.warn("未定义的 IBA code: {}", code);
            return null;
        }
        IBA existing = ibaMapper.selectByCode(code, PLATFORM);
        if (existing != null) return existing;

        IBA iba = new IBA();
        iba.setCode(code);
        iba.setName(def[0]);
        iba.setDisplayName(def[0]);
        iba.setDataType(def[1]);
        iba.setDescription(def.length > 2 ? def[2] : def[0]);
        iba.setTenantOid(PLATFORM);
        iba.setEnabled(true);
        iba.setRequired(false);
        ibaMapper.insert(iba);
        return iba;
    }

    // ==================== 分类树节点结构 ====================

    private static Category cat(String name, String slug, String... ibaCodes) {
        return new Category(name, slug, ibaCodes);
    }

    /** 分类树节点：支持「大类 → 细分类」任意层级 */
    private static final class Category {
        final String name;
        final String slug;
        final String[] ibaCodes;
        final List<Category> children = new ArrayList<>();

        Category(String name, String slug, String... ibaCodes) {
            this.name = name;
            this.slug = slug == null ? "" : slug;
            this.ibaCodes = ibaCodes == null ? new String[0] : ibaCodes;
        }

        Category with(Category... children) {
            this.children.addAll(Arrays.asList(children));
            return this;
        }
    }

    // ==================== 器件属性定义 ====================
    // code → [名称, 数据类型, 描述]

    private static final Map<String, String[]> IBA_DEFS = Map.ofEntries(
            // ---- 通用 ----
            Map.entry("TYPE", new String[]{"类型", "STRING", "器件类型"}),
            Map.entry("PACKAGE", new String[]{"封装", "STRING", "封装形式，如 0402、SOT-23、SMD"}),
            Map.entry("MATERIAL", new String[]{"材质", "STRING", "材料，如 X7R、C0G、碳膜"}),
            // ---- 电阻 ----
            Map.entry("RESISTANCE", new String[]{"阻值", "STRING", "标称阻值，如 10kΩ、4.7Ω"}),
            Map.entry("TOLERANCE", new String[]{"精度", "STRING", "容差，如 ±1%、±5%"}),
            Map.entry("POWER_RATING", new String[]{"额定功率", "FLOAT", "额定功率，单位 W"}),
            Map.entry("TEMP_COEFFICIENT", new String[]{"温度系数", "STRING", "温度系数，如 ±100ppm/℃"}),
            Map.entry("B_VALUE", new String[]{"B值", "FLOAT", "NTC 热敏电阻 B 值"}),
            Map.entry("VARISTOR_VOLTAGE", new String[]{"压敏电压", "FLOAT", "压敏电阻标称压敏电压，单位 V"}),
            // ---- 电容 ----
            Map.entry("CAPACITANCE", new String[]{"容值", "STRING", "标称容值，如 100nF、10uF"}),
            Map.entry("VOLTAGE_RATING", new String[]{"额定电压", "FLOAT", "额定耐压，单位 V"}),
            Map.entry("TEMP_CHARACTERISTIC", new String[]{"温度特性", "STRING", "介质温度特性，如 X7R、C0G"}),
            // ---- 电感 ----
            Map.entry("INDUCTANCE", new String[]{"电感值", "STRING", "标称电感值，如 4.7uH、10mH"}),
            Map.entry("RATED_CURRENT", new String[]{"额定电流", "FLOAT", "额定电流，单位 A"}),
            Map.entry("DC_RESISTANCE", new String[]{"直流电阻", "FLOAT", "直流电阻 DCR，单位 Ω"}),
            Map.entry("SATURATION_CURRENT", new String[]{"饱和电流", "FLOAT", "饱和电流，单位 A"}),
            Map.entry("IMPEDANCE", new String[]{"阻抗", "FLOAT", "阻抗值（磁珠/滤波器），单位 Ω"}),
            // ---- 二极管 ----
            Map.entry("FORWARD_CURRENT", new String[]{"正向电流", "FLOAT", "正向额定电流，单位 A"}),
            Map.entry("REVERSE_VOLTAGE", new String[]{"反向电压", "FLOAT", "反向耐压，单位 V"}),
            Map.entry("FORWARD_VOLTAGE", new String[]{"正向压降", "FLOAT", "正向压降 Vf，单位 V"}),
            Map.entry("ZENER_VOLTAGE", new String[]{"稳压值", "FLOAT", "齐纳稳压值 Vz，单位 V"}),
            Map.entry("RECOVERY_TIME", new String[]{"恢复时间", "FLOAT", "反向恢复时间，单位 ns"}),
            Map.entry("BREAKDOWN_VOLTAGE", new String[]{"击穿电压", "FLOAT", "击穿电压，单位 V"}),
            Map.entry("PEAK_PULSE_POWER", new String[]{"峰值脉冲功率", "FLOAT", "峰值脉冲功率，单位 W"}),
            Map.entry("CLAMP_VOLTAGE", new String[]{"钳位电压", "FLOAT", "钳位电压，单位 V"}),
            // ---- 三极管 ----
            Map.entry("POLARITY", new String[]{"极性", "STRING", "极性类型，如 NPN/PNP"}),
            Map.entry("COLLECTOR_CURRENT", new String[]{"集电极电流", "FLOAT", "集电极额定电流 Ic，单位 A"}),
            Map.entry("COLLECTOR_EMITTER_VOLTAGE", new String[]{"集电极-发射极电压", "FLOAT", "集电极-发射极耐压 Vceo，单位 V"}),
            Map.entry("DC_GAIN", new String[]{"直流增益", "FLOAT", "直流电流增益 hFE"}),
            // ---- MOSFET ----
            Map.entry("CHANNEL_TYPE", new String[]{"沟道类型", "STRING", "沟道类型，如 N沟道/P沟道"}),
            Map.entry("DRAIN_SOURCE_VOLTAGE", new String[]{"漏源电压", "FLOAT", "漏源耐压 Vds，单位 V"}),
            Map.entry("DRAIN_CURRENT", new String[]{"漏极电流", "FLOAT", "连续漏极电流 Id，单位 A"}),
            Map.entry("ON_RESISTANCE", new String[]{"导通电阻", "FLOAT", "导通电阻 Rds(on)，单位 mΩ"}),
            // ---- 集成电路 ----
            Map.entry("MODEL", new String[]{"型号", "STRING", "器件型号/料号"}),
            Map.entry("INPUT_VOLTAGE", new String[]{"输入电压", "FLOAT", "输入电压范围，单位 V"}),
            Map.entry("OUTPUT_VOLTAGE", new String[]{"输出电压", "FLOAT", "输出电压，单位 V"}),
            Map.entry("OUTPUT_CURRENT", new String[]{"输出电流", "FLOAT", "输出电流，单位 A"}),
            Map.entry("CORE", new String[]{"内核", "STRING", "处理器内核，如 ARM Cortex-M0"}),
            Map.entry("MAIN_FREQUENCY", new String[]{"主频", "FLOAT", "主频，单位 MHz"}),
            Map.entry("FLASH_SIZE", new String[]{"Flash容量", "FLOAT", "Flash 容量，单位 KB"}),
            Map.entry("CAPACITY", new String[]{"容量", "FLOAT", "存储容量，单位 Mbit"}),
            Map.entry("INTERFACE", new String[]{"接口", "STRING", "通信接口，如 SPI、I2C、UART"}),
            Map.entry("SERIES", new String[]{"系列", "STRING", "逻辑系列，如 74HC、4000"}),
            Map.entry("CHANNEL_COUNT", new String[]{"通道数", "INTEGER", "通道数量"}),
            Map.entry("BANDWIDTH", new String[]{"带宽", "FLOAT", "增益带宽积，单位 MHz"}),
            Map.entry("LOGIC_CELLS", new String[]{"逻辑单元数", "INTEGER", "逻辑单元/门数量"}),
            Map.entry("WORKING_VOLTAGE", new String[]{"工作电压", "FLOAT", "工作/供电电压，单位 V"}),
            // ---- 连接器 / 开关 / 继电器 ----
            Map.entry("PIN_COUNT", new String[]{"针数", "INTEGER", "引脚/针数量"}),
            Map.entry("PITCH", new String[]{"间距", "FLOAT", "引脚间距，单位 mm"}),
            Map.entry("CONTACT_FORM", new String[]{"触点形式", "STRING", "触点形式，如 SPST、SPDT"}),
            Map.entry("CONTACT_CURRENT", new String[]{"触点电流", "FLOAT", "触点额定电流，单位 A"}),
            Map.entry("COIL_VOLTAGE", new String[]{"线圈电压", "FLOAT", "继电器线圈电压，单位 V"}),
            // ---- LED / 光电器件 ----
            Map.entry("COLOR", new String[]{"颜色", "STRING", "发光颜色"}),
            Map.entry("WAVELENGTH", new String[]{"波长", "FLOAT", "主波长，单位 nm"}),
            Map.entry("LUMINOUS_INTENSITY", new String[]{"发光强度", "FLOAT", "发光强度，单位 mcd"}),
            Map.entry("DIGIT_COUNT", new String[]{"位数", "INTEGER", "数码管位数"}),
            // ---- 晶振 ----
            Map.entry("FREQUENCY", new String[]{"频率", "FLOAT", "标称频率，单位 MHz"}),
            Map.entry("FREQUENCY_TOLERANCE", new String[]{"频率容差", "FLOAT", "频率容差，单位 ppm"}),
            Map.entry("LOAD_CAPACITANCE", new String[]{"负载电容", "FLOAT", "负载电容 CL，单位 pF"}),
            // ---- 保险丝 / 保护 ----
            Map.entry("HOLD_CURRENT", new String[]{"保持电流", "FLOAT", "保持电流 Ih，单位 A"}),
            Map.entry("BREAKING_CAPACITY", new String[]{"分断能力", "FLOAT", "分断能力，单位 A"}),
            // ---- 传感器 ----
            Map.entry("ACCURACY", new String[]{"测量精度", "STRING", "测量精度"}),
            Map.entry("RANGE", new String[]{"量程", "STRING", "测量量程"}),
            // ---- 结构件 ----
            Map.entry("THICKNESS", new String[]{"厚度", "FLOAT", "材料厚度，单位 mm"}),
            Map.entry("SURFACE_TREATMENT", new String[]{"表面处理", "STRING", "表面处理，如 镀锌、阳极氧化"}),
            Map.entry("PROCESS", new String[]{"工艺", "STRING", "加工工艺"}),
            Map.entry("PRECISION", new String[]{"加工精度", "STRING", "加工精度等级"}),
            Map.entry("SPECIFICATION", new String[]{"规格", "STRING", "规格型号，如 M8、GB/T 5782"}),
            Map.entry("STRENGTH_GRADE", new String[]{"强度等级", "STRING", "强度等级，如 8.8、10.9"}),
            Map.entry("DIAMETER", new String[]{"直径", "FLOAT", "公称直径，单位 mm"}),
            Map.entry("LENGTH", new String[]{"长度", "FLOAT", "长度，单位 mm"})
    );
}
