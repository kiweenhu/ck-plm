/*
 * Copyright (c) 2026~2028 深圳乘恺科技有限公司
 * All rights reserved.
 *
 * @author Kiween.Hu; Roney.Liu
 */

package cn.ck.plm.softtype.config;

import cn.ck.plm.document.entity.Document;
import cn.ck.plm.document.entity.EngineeringDocument;
import cn.ck.plm.base.entity.*;
import cn.ck.plm.base.service.api.LifecycleTemplateService;
import cn.ck.plm.base.util.TenantContext;
import cn.ck.plm.part.entity.Part;
import cn.ck.plm.product.entity.ProductLine;
import cn.ck.plm.product.entity.ProductModel;
import cn.ck.plm.functional.entity.FunctionalEntity;
import cn.ck.plm.softtype.entity.*;
import cn.ck.plm.softtype.mapper.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.util.*;

/**
 * 类型定义（{@code ck_type_definition}）的种子注册与数据维护：启动时幂等地注册
 * OOTB 根类型与各宿主下的子类型（SOFT_TYPE），并绑定默认的编码规则、版本规则、生命周期模板。
 *
 * <p><b>本类不预置编码规则本身</b>：6 条默认编码规则（{@code ck_number} + 编码段）由
 * {@link NumberRuleInitializer}（{@code @Order(1)}）先行播种 —— 本类只负责把类型<b>绑</b>到规则上
 * （{@code ck_type_number_rule_link}）。原先两者混在一起，改编号段配置得动类型初始化器，已拆开。
 *
 * <h3>两个正交维度（本类全部逻辑的口径）</h3>
 * <ul>
 *   <li><b>类型继承</b> —— {@code parent_oid}：只回答"父类型是谁"，
 *       由 {@link #resolveParent} 按能力宿主（{@code root_type_code}）或兜底父类型解析；</li>
 *   <li><b>域归属</b> —— {@code domain_oid} → {@code ck_business_domain}：业务划分
 *       （产品主数据域 / 结构设计域 / 电子设计域 …），与类型继承无关、<b>允许跨域</b>，
 *       由 {@link #migrateTypesToDomains} 按 {@link #TYPE_DOMAIN} 维护。</li>
 * </ul>
 * 历史上域锚点是 {@code ck_type_definition} 里的 DOMAIN 行、且用 {@code parent_oid} 表达域归属
 * （一列两义：新建类型沿父链找不到能力宿主 → 误报"域锚点"错误）。域已拆为独立实体
 * （种子见 {@link BusinessDomainInitializer}），<b>本类不再注册任何 DOMAIN 行</b>。
 *
 * <h3>代码结构（六节，按阅读顺序排列）</h3>
 * <ol>
 *   <li>依赖与入口：注入 + {@link #run(String...)}（四阶段）；</li>
 *   <li>种子数据：所有 map 与其"一行一条"的填充器 —— <b>新增类型只改这一节</b>；</li>
 *   <li>类型注册：ensure*（幂等插入 + 规则绑定）；</li>
 *   <li>存量数据迁移与修正：面向老库的纠偏（幂等，可反复执行）；</li>
 *   <li>平台基础规则：生命周期模板（默认编码规则见 {@link NumberRuleInitializer}）；</li>
 *   <li>绑定工具：类型 → 编码规则 / 版本规则 / 生命周期模板。</li>
 * </ol>
 *
 * <h3>执行顺序</h3>
 * <ul>
 *   <li>{@link BusinessDomainInitializer}（{@code @Order(0)}）先备好业务域；</li>
 *   <li>{@link VersionRuleInitializer} / {@link NumberRuleInitializer}（{@code @Order(1)}）备好规则；</li>
 *   <li>本类（{@code @Order(2)}）注册类型定义并绑定规则/模板；</li>
 *   <li>{@link AttributeInitializer}（{@code @Order(3)}）扫描实体字段注册属性定义；</li>
 *   <li>{@link PageLayoutInitializer}（{@code @Order(4)}）创建默认页面布局。</li>
 * </ul>
 */
@Component
@Order(2) // 在 BusinessDomainInitializer(@Order=0) 之后，在 AttributeInitializer(@Order=3) 之前
public class TypeDefinitionInitializer implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(TypeDefinitionInitializer.class);

    // ========================================================================================
    // 一、依赖与入口
    // ========================================================================================

    private final TypeDefinitionMapper mapper;
    private final TypeNumberRuleLinkMapper numberRuleLinkMapper;
    private final TypeVersionRuleLinkMapper versionRuleLinkMapper;
    private final TypeLifecycleTemplateLinkMapper lifecycleTemplateLinkMapper;
    private final LifecycleTemplateService lifecycleTemplateService;
    /** 业务域读侧（域归属的唯一权威来源，见 {@link #migrateTypesToDomains}） */
    private final BusinessDomainMapper businessDomainMapper;

    public TypeDefinitionInitializer(TypeDefinitionMapper mapper,
                                     TypeNumberRuleLinkMapper numberRuleLinkMapper,
                                     TypeVersionRuleLinkMapper versionRuleLinkMapper,
                                     TypeLifecycleTemplateLinkMapper lifecycleTemplateLinkMapper,
                                     LifecycleTemplateService lifecycleTemplateService,
                                     BusinessDomainMapper businessDomainMapper) {
        this.mapper = mapper;
        this.numberRuleLinkMapper = numberRuleLinkMapper;
        this.versionRuleLinkMapper = versionRuleLinkMapper;
        this.lifecycleTemplateLinkMapper = lifecycleTemplateLinkMapper;
        this.lifecycleTemplateService = lifecycleTemplateService;
        this.businessDomainMapper = businessDomainMapper;
    }

    /**
     * 启动入口：四个阶段，全部幂等，可重复执行。
     *
     * <pre>
     * 阶段 1  平台基础数据   —— 生命周期模板（注册类型时要引用）；编码规则见 {@link NumberRuleInitializer}
     * 阶段 2  OOTB 根类型    —— 产品系列 / 产品型号 / 文档 / 工程数据 / 部件 / 功能架构
     * 阶段 3  子类型注册     —— 各宿主与域下的 SOFT_TYPE（顺序有依赖，见下）
     * 阶段 4  迁移与修正     —— 面向老库的纠偏（新库执行等于空转）
     * </pre>
     */
    @Override
    public void run(String... args) {
        log.info("开始初始化 OOTB 类型定义（含版本规则、生命周期模板与编码规则绑定）...");

        // ── 阶段 1/4：平台基础数据 ──
        addRootTypeCodeColumnIfAbsent();
        ensureDefaultLifecycleTemplates();
        // 注：默认编码规则（6 条 + 编码段）已移到 NumberRuleInitializer（@Order(1)，先于本类执行）

        // ── 阶段 2/4：OOTB 根类型 ──
        registerOotbRootTypes();

        // ── 阶段 3/4：子类型注册（顺序有依赖：结构件必须先于标准件/通用件）──
        ensureSoftTypes("FUNCTIONAL",  "功能架构", FUNCTIONAL_SOFT_TYPES);   // 系统 / 子系统 / CI
        ensureSoftTypes("PART",        "部件",     PART_SOFT_TYPES);         // 结构件 / 电气件 / 软件
        ensureSoftTypes("STRUCTURAL",  "结构件",   STRUCTURAL_SOFT_TYPES);   // 标准件 / 通用件
        ensureSoftTypes("DOCUMENT",    "文档",     DOCUMENT_SOFT_TYPES);     // 预留（电子域文档见 ECAD_CHILD_TYPES）
        ensureSoftTypes("ECAD_DOMAIN", "电子域",   ECAD_CHILD_TYPES);        // 封装 / 图符 / 电子元器件 / PCBA / 原理图 / PCB 设计
        ensureDomainOotbTypes();                                             // 域下独立对象类型（ECAD_PROJECT：能力宿主即自身）
        ensureDomainOotbBinds();                                             // 上述独立类型补编码规则（对存量类型同样生效）

        // ── 阶段 4/4：存量数据迁移与修正（幂等；新库空转）──
        migrateTypesToDomains();              // 补 domain_oid + 校正 ECAD 子类型的能力宿主
        migrateStdGenPartsUnderStructural();  // 标准件 / 通用件的所属类型 → 结构件
        repairSoftTypeNumberRules();          // SOFT_TYPE 的编码规则绑定 → 与其能力宿主一致
        normalizeTypeKindCase();              // type_kind 大小写归一（'ootb' → 'OOTB'）

        log.info("类型定义初始化流程结束");
    }

    /** 兼容旧表：确保 {@code root_type_code} 列存在（新库由 schema.sql 自带） */
    private void addRootTypeCodeColumnIfAbsent() {
        try {
            mapper.addRootTypeCodeColumn();
            log.info("ck_type_definition 表已添加 root_type_code 列（如之前不存在）");
        } catch (Exception e) {
            log.debug("添加 root_type_code 列: {}", e.getMessage());
        }
    }

    /** 注册 OOTB 根类型（{@link #ENTITY_META} 一行一条），并为存量根类型补齐 {@code root_type_code} */
    private void registerOotbRootTypes() {
        int inserted = 0, skipped = 0;
        for (Map.Entry<Class<? extends BaseEntity>, EntityMeta> entry : ENTITY_META.entrySet()) {
            Class<?> entityClass = entry.getKey();
            EntityMeta meta = entry.getValue();
            try {
                if (ensureOotb(meta.code, meta)) {
                    log.info("  √ {} → {} (code={})", entityClass.getSimpleName(), meta.displayName, meta.code);
                    inserted++;
                } else {
                    log.debug("  - {} (code={}) 已存在，跳过", meta.displayName, meta.code);
                    skipped++;
                }
            } catch (Exception e) {
                log.error("  ✗ 注册实体 {} 类型定义失败: {}", entityClass.getSimpleName(), e.getMessage(), e);
            }
        }
        int patched = mapper.patchRootTypeCodeForOotb();
        if (patched > 0) {
            log.info("已为 {} 个 OOTB 类型补充 root_type_code", patched);
        }
        log.info("OOTB 类型定义初始化完成: 新增 {} 个, 已存在 {} 个", inserted, skipped);
    }

    // ========================================================================================
    // 二、种子数据（唯一事实来源：新增类型、调整域归属只改这一节）
    // ========================================================================================

    /**
     * 实体元数据：code 由配置显式指定（如 PRODUCT_LINE、DOCUMENT），
     * 并声明其默认编码规则 / 版本规则 / 生命周期模板（null = 不绑定）。
     */
    private static class EntityMeta {
        final String code;
        final String displayName;
        final String icon;
        final String description;
        final int sortOrder;
        final String defaultNumberRuleCode;
        final String defaultVersionRuleCode;
        final String defaultLifecycleTemplateCode;

        EntityMeta(String code, String displayName, String icon, String description, int sortOrder,
                   String defaultNumberRuleCode, String defaultVersionRuleCode,
                   String defaultLifecycleTemplateCode) {
            this.code = code;
            this.displayName = displayName;
            this.icon = icon;
            this.description = description;
            this.sortOrder = sortOrder;
            this.defaultNumberRuleCode = defaultNumberRuleCode;
            this.defaultVersionRuleCode = defaultVersionRuleCode;
            this.defaultLifecycleTemplateCode = defaultLifecycleTemplateCode;
        }
    }

    /**
     * 子类型元数据（某宿主或某域下的一条 SOFT_TYPE）。
     */
    private static class SoftTypeMeta {
        final String code;
        final String name;
        final String displayName;
        final String icon;
        final String description;
        final int sortOrder;
        /**
         * 能力宿主 code（落哪张实体表、用哪套编码规则），如 PART / DOCUMENT / ENG_DOCUMENT：
         * <ul>
         *   <li>为 null：作为普通子类型挂在兜底父类型（PART / DOCUMENT / FUNCTIONAL / STRUCTURAL）下；</li>
         *   <li>非 null 且 ≠ 自身：父类型 = 该宿主（域下类型必须显式声明，因为域不是类型行）；</li>
         *   <li>= 自身 code：独立类型（如 ECAD_PROJECT），能力宿主即自己、无父类型。</li>
         * </ul>
         */
        final String rootTypeCode;
        /** 归属业务域 code（仅 {@link #DOMAIN_OOTB_TYPES} 使用，如 ECAD_PROJECT 归属 ECAD_DOMAIN） */
        final String domainCode;

        SoftTypeMeta(String code, String name, String displayName, String icon, String description, int sortOrder) {
            this(code, name, displayName, icon, description, sortOrder, null, null);
        }

        SoftTypeMeta(String code, String name, String displayName, String icon, String description,
                     int sortOrder, String rootTypeCode) {
            this(code, name, displayName, icon, description, sortOrder, rootTypeCode, null);
        }

        SoftTypeMeta(String code, String name, String displayName, String icon, String description,
                     int sortOrder, String rootTypeCode, String domainCode) {
            this.code = code;
            this.name = name;
            this.displayName = displayName;
            this.icon = icon;
            this.description = description;
            this.sortOrder = sortOrder;
            this.rootTypeCode = rootTypeCode;
            this.domainCode = domainCode;
        }
    }

    /** OOTB 实体 → 类型定义元数据；新增实体只需在此加一行 {@code entity(...)} */
    private static final Map<Class<? extends BaseEntity>, EntityMeta> ENTITY_META = new LinkedHashMap<>();
    static {
        entity(ProductLine.class,  "PRODUCT_LINE", "产品系列", "ApartmentOutlined",
                "产品系列管理，关联产品、团队与缩略图", 5,
                "PRODUCT_LINE", "LETTER_26", "STANDARD");
        entity(ProductModel.class, "PRODUCT_MODEL", "产品型号", "TagOutlined",
                "产品型号管理，隶属于产品系列，拥有独立团队和研发阶段", 6,
                "PRODUCT_MODEL", "LETTER_26", "STANDARD");
        entity(Document.class,     "DOCUMENT", "文档", "FileTextOutlined",
                "文档复合对象（主数据+子版本），支持版本控制、文件存储与阶段关联", 10,
                "DOC_NUMBER", "LETTER_26", "STANDARD");
        entity(EngineeringDocument.class, "ENG_DOCUMENT", "工程数据", "FileImageOutlined",
                "工程数据（参照 Windchill EPMDocument）：3D 数模 / 2D 工程图 / 材料规格说明等工程对象的统称，"
                        + "含 CAD 主文件、制图属性（图幅/比例/图号）与零部件描述关系，"
                        + "并可向电子领域扩展（符号、封装等 EDA 设计数据）", 11,
                "DOC_NUMBER", "LETTER_26", "STANDARD");
        entity(Part.class,         "PART", "部件", "ToolOutlined",
                "部件复合对象（主数据+子版本），支持版本控制、分类关联与单位管理", 12,
                "PART_NUMBER", "LETTER_26", "STANDARD");
        entity(FunctionalEntity.class, "FUNCTIONAL", "功能架构(构型)", "ClusterOutlined",
                "装备级功能系统（军工）/ 车型功能域（汽车），继承 Part 复合实体结构", 14,
                "FUNCTIONAL-NUM", "LETTER_26", "STANDARD");
    }

    private static void entity(Class<? extends BaseEntity> entityClass, String code, String displayName,
                               String icon, String description, int sortOrder, String defaultNumberRuleCode,
                               String defaultVersionRuleCode, String defaultLifecycleTemplateCode) {
        ENTITY_META.put(entityClass, new EntityMeta(code, displayName, icon, description, sortOrder,
                defaultNumberRuleCode, defaultVersionRuleCode, defaultLifecycleTemplateCode));
    }

    /** 功能架构（FUNCTIONAL）下的子类型：系统 / 子系统 / 配置项 */
    private static final Map<String, SoftTypeMeta> FUNCTIONAL_SOFT_TYPES = new LinkedHashMap<>();
    static {
        functional("SYSTEM", "系统", "系统", "ApartmentOutlined",
                "装备级系统，对应军工领域的武器系统/火控系统/导航系统，或汽车领域的动力域/底盘域", 15);
        functional("SUBSYSTEM", "子系统", "子系统", "BlockOutlined",
                "系统下的子功能模块，如武器系统下的发射子系统、制导子系统", 16);
        functional("CI", "CI", "配置/构型项目", "ControlOutlined",
                "配置项(Configuration Item)，可独立管理、版本控制的构型单元", 17);
    }

    private static void functional(String code, String name, String displayName, String icon,
                                   String description, int sortOrder) {
        FUNCTIONAL_SOFT_TYPES.put(code, new SoftTypeMeta(code, name, displayName, icon, description, sortOrder));
    }

    /**
     * 部件（PART）下的专业分类子类型：结构件 / 电气件 / 软件。
     * 不声明 rootTypeCode → 父 = PART、能力宿主继承 PART。
     *
     * <p>注：电子域对象类型（FOOTPRINT 封装、SYMBOL 图符、ELECTRONIC 电子元器件、PCBA 等）
     * 在 {@link #ECAD_CHILD_TYPES}；标准件 / 通用件不是直接挂在部件下，见 {@link #STRUCTURAL_SOFT_TYPES}。
     */
    private static final Map<String, SoftTypeMeta> PART_SOFT_TYPES = new LinkedHashMap<>();
    static {
        part("STRUCTURAL", "结构件", "结构件", "ToolOutlined",
                "机械结构件：钣金、机加、塑钢、装配体等结构专业对象；标准件 / 通用件为其子类型", 20);
        part("ELECTRICAL", "电气件", "电气件", "BulbOutlined",
                "电气件：线束、连接器、继电器、开关、电机等电气专业对象", 21);
        part("SOFTWARE", "软件", "软件", "CodeOutlined",
                "软件：嵌入式软件、应用软件、固件、算法等软件专业对象", 22);
    }

    private static void part(String code, String name, String displayName, String icon,
                             String description, int sortOrder) {
        PART_SOFT_TYPES.put(code, new SoftTypeMeta(code, name, displayName, icon, description, sortOrder));
    }

    /**
     * 结构件（STRUCTURAL）下的子类型：标准件 / 通用件。
     *
     * <p>二者在 PLM 实践中都是结构件的子类 —— 这里登记为<b>类型继承</b>（parent_oid → 结构件），
     * 与资源库侧取软类型的口径一致（{@code ResourceLibraryServiceImpl#resolveTypeCode}：
     * 标准件库 / 通用件库内的零件统一按 {@code STRUCTURAL} 取软类型）；
     * 具体是哪个库由资源库容器（containerOid）区分，两者不冲突。
     */
    private static final Map<String, SoftTypeMeta> STRUCTURAL_SOFT_TYPES = new LinkedHashMap<>();
    static {
        structural("STD_PART", "标准件", "标准件", "GoldOutlined",
                "按国标 / 行标 / 企标采购或外协的标准零组件：螺钉、螺母、垫圈、轴承、销、弹簧等", 23);
        structural("GEN_PART", "通用件", "通用件", "DeploymentUnitOutlined",
                "企业内部跨产品、跨型号复用的通用结构件（非国标，但有统一规格与图号）", 24);
    }

    private static void structural(String code, String name, String displayName, String icon,
                                   String description, int sortOrder) {
        STRUCTURAL_SOFT_TYPES.put(code, new SoftTypeMeta(code, name, displayName, icon, description, sortOrder));
    }

    /**
     * 文档（DOCUMENT）下的子类型 —— 目前为空（电子域文档类型在 {@link #ECAD_CHILD_TYPES}）。
     * 保留此 map 作为扩展位：新增时按 {@code put(code, new SoftTypeMeta(...))} 加一条即可。
     */
    private static final Map<String, SoftTypeMeta> DOCUMENT_SOFT_TYPES = new LinkedHashMap<>();

    /**
     * 电子域（ECAD_DOMAIN）下的对象类型。
     *
     * <p>每个类型<b>显式声明 rootTypeCode</b>（能力宿主）：
     * {@code ENG_DOCUMENT} → 数据落工程数据表、用 DOC_NUMBER 编码；
     * {@code PART} → 数据落 ck_part、用 PART_NUMBER 编码。
     */
    private static final Map<String, SoftTypeMeta> ECAD_CHILD_TYPES = new LinkedHashMap<>();
    static {
        // ===== EDA 设计数据类：宿主为工程数据 ENG_DOCUMENT（设计数据，非物料） =====
        ecad("FOOTPRINT", "封装", "封装", "BorderOutlined",
                "电子元器件封装（PCB 焊盘图形 / Land Pattern），遵循 IPC-7351 命名，如 RESC1608X55N；"
                        + "属 EDA 设计数据，宿主为工程数据 ENG_DOCUMENT", 31, "ENG_DOCUMENT");
        ecad("SYMBOL", "原理图图符", "原理图图符", "BlockOutlined",
                "原理图符号与引脚定义，供电子元器件在原理图设计中引用；"
                        + "属 EDA 设计数据，宿主为工程数据 ENG_DOCUMENT", 32, "ENG_DOCUMENT");
        // ===== 物料类：宿主为 PART（实物物料，参与 BOM） =====
        ecad("ELECTRONIC", "电子元器件", "电子元器件", "ThunderboltOutlined",
                "电子元器件，如电阻、电容、电感、二极管、IC、连接器等", 33, "PART");
        ecad("PCBA", "PCBA", "PCBA组件", "AppstoreOutlined",
                "印刷电路板组件，已完成电子元器件贴装的电路板", 34, "PART");
        // ===== EDA 设计数据类（续） =====
        ecad("SCHEMATIC", "原理图", "原理图", "FileTextOutlined",
                "电子原理图设计文件，含图页与器件实例；属 EDA 设计数据，宿主为工程数据 ENG_DOCUMENT", 35, "ENG_DOCUMENT");
        ecad("PCB_LAYOUT", "PCB设计", "PCB 设计", "LayoutOutlined",
                "PCB 布局布线设计，含层叠、板厚、Gerber 文件集；属 EDA 设计数据，宿主为工程数据 ENG_DOCUMENT", 36, "ENG_DOCUMENT");
    }

    private static void ecad(String code, String name, String displayName, String icon,
                             String description, int sortOrder, String rootTypeCode) {
        ECAD_CHILD_TYPES.put(code, new SoftTypeMeta(code, name, displayName, icon, description, sortOrder, rootTypeCode));
    }

    /**
     * 域下的独立对象类型：拥有自己的实体表（非 PART / DOCUMENT 宿主的 softtype），
     * 能力宿主 = 自身 code（类型树顶层），域归属由 {@link SoftTypeMeta#domainCode} 指定。
     */
    private static final Map<String, SoftTypeMeta> DOMAIN_OOTB_TYPES = new LinkedHashMap<>();
    static {
        domainRoot("ECAD_PROJECT", "设计项目", "设计项目", "ProjectOutlined",
                "电子设计项目：电子设计任务的容器对象，关联原理图 / PCB 设计、所属产品与项目阶段；"
                        + "拥有独立实体表 ck_ecad_project，非 Part / Document 宿主类型", "ECAD_DOMAIN", 40);
    }

    private static void domainRoot(String code, String name, String displayName, String icon,
                                   String description, String domainCode, int sortOrder) {
        DOMAIN_OOTB_TYPES.put(code, new SoftTypeMeta(code, name, displayName, icon, description, sortOrder, code, domainCode));
    }

    /**
     * 类型 code → 归属业务域 code（域归属的唯一映射表，由 {@link #migrateTypesToDomains} 执行）。
     *
     * <p>域 code 必须与 {@link BusinessDomainInitializer} 的种子一致；新增类型时同步登记，
     * 否则该类型在界面上没有域标签。
     */
    private static final Map<String, String> TYPE_DOMAIN = new LinkedHashMap<>();
    static {
        // 产品主数据域：文档 / 产品系列 / 产品型号 / 零组件
        domain("DOCUMENT", "PRODUCT_DATA_DOMAIN");
        domain("ENG_DOCUMENT", "PRODUCT_DATA_DOMAIN");
        domain("PRODUCT_LINE", "PRODUCT_DATA_DOMAIN");
        domain("PRODUCT_MODEL", "PRODUCT_DATA_DOMAIN");
        domain("PART", "PRODUCT_DATA_DOMAIN");
        // 架构设计域：功能架构
        domain("FUNCTIONAL", "ARCHITECTURE_DOMAIN");
        // 结构设计域：结构件 / 标准件 / 通用件
        domain("STRUCTURAL", "MCAD_DOMAIN");
        domain("STD_PART", "MCAD_DOMAIN");
        domain("GEN_PART", "MCAD_DOMAIN");
        // 电气设计域：电气件
        domain("ELECTRICAL", "ELECTRICAL_DOMAIN");
        // 软件设计域：软件
        domain("SOFTWARE", "SOFTWARE_DOMAIN");
        // 电子设计域：封装 / 原理图图符 / 原理图 / PCB 设计 / 电子元器件 / PCBA / 设计项目
        domain("FOOTPRINT", "ECAD_DOMAIN");
        domain("SYMBOL", "ECAD_DOMAIN");
        domain("SCHEMATIC", "ECAD_DOMAIN");
        domain("PCB_LAYOUT", "ECAD_DOMAIN");
        domain("ELECTRONIC", "ECAD_DOMAIN");
        domain("PCBA", "ECAD_DOMAIN");
        domain("ECAD_PROJECT", "ECAD_DOMAIN");
    }

    private static void domain(String typeCode, String domainCode) {
        TYPE_DOMAIN.put(typeCode, domainCode);
    }

    /**
     * 「域下独立对象类型」→ 默认编码规则。
     *
     * <p>这类类型的实体（如 {@code ck_ecad_project}）<b>没有迭代 / 版本 / 生命周期列</b>
     * （继承 BaseEntity 而非 MasterEntity），因此只需要、也只绑定编码规则。
     * 注册时不带规则、由 {@link #ensureDomainOotbBinds} 统一补齐（对存量类型同样生效）。
     */
    private static final Map<String, String> DOMAIN_OOTB_NUMBER_RULES = new LinkedHashMap<>();
    static {
        DOMAIN_OOTB_NUMBER_RULES.put("ECAD_PROJECT", "ECAD_PROJECT-NUM");
    }

    // ========================================================================================
    // 三、类型注册（幂等：已存在即跳过）
    // ========================================================================================

    /**
     * 注册某宿主下的子类型（{@link #run} 阶段 3 的唯一入口）。
     *
     * @param parentCode  兜底父类型 code：未显式声明能力宿主的子类型挂到它下面
     * @param parentLabel 日志用名称
     */
    private void ensureSoftTypes(String parentCode, String parentLabel, Map<String, SoftTypeMeta> softTypes) {
        if (softTypes == null || softTypes.isEmpty()) {
            return; // 该宿主下暂无种子（如文档），不产生日志噪音
        }
        TypeDefinition fallbackParent = mapper.selectByCode(parentCode,
                TenantContext.PLATFORM_TENANT_OID, TenantContext.PLATFORM_TENANT_OID);
        if (fallbackParent == null) {
            // 不整体跳过：域下类型（封装 / 图符等）的父类型是按能力宿主解析的，与该 parentCode 无关
            log.debug("未找到 {} 类型（父类型将按能力宿主解析）", parentCode);
        }

        log.info("开始注册 {} 子类型...", parentLabel);
        for (SoftTypeMeta meta : softTypes.values()) {
            try {
                if (mapper.existsByCode(meta.code, TenantContext.PLATFORM_TENANT_OID,
                        TenantContext.PLATFORM_TENANT_OID) > 0) {
                    log.debug("  - {} (code={}) 已存在，跳过", meta.displayName, meta.code);
                    continue;
                }
                TypeDefinition parent = resolveParent(meta, fallbackParent);
                ensureSoftType(parent, meta);
                log.info("  √ {} (code={}) 注册成功", meta.displayName, meta.code);
            } catch (Exception e) {
                log.error("  ✗ 注册 {} 子类型 {} 失败: {}", parentLabel, meta.code, e.getMessage(), e);
            }
        }
        log.info("{} 子类型注册完成", parentLabel);
    }

    /**
     * 解析子类型的<b>类型树父类型</b>（{@code parent_oid} 只表达类型继承，域归属走 {@code domain_oid}）：
     * <ul>
     *   <li>声明了能力宿主且 ≠ 自身 → 父 = 该宿主类型（封装 / 图符 → 工程数据、电子元器件 → 部件）；</li>
     *   <li>能力宿主即自身（独立类型）→ 顶层，返回 null；</li>
     *   <li>未声明宿主 → 用兜底父类型（部件 / 文档 / 功能架构 / 结构件下的普通子类型）。</li>
     * </ul>
     */
    private TypeDefinition resolveParent(SoftTypeMeta meta, TypeDefinition fallback) {
        String host = meta.rootTypeCode;
        if (host == null || host.isEmpty()) {
            return fallback;
        }
        if (host.equals(meta.code)) {
            return null;
        }
        return mapper.selectByCode(host, TenantContext.PLATFORM_TENANT_OID, TenantContext.PLATFORM_TENANT_OID);
    }

    /**
     * 注册单个 SOFT_TYPE 子类型，并绑定默认编码规则 / 版本规则 / 生命周期模板。
     *
     * @param parent 父类型；为 null 表示无父（顶层独立类型）
     */
    private void ensureSoftType(TypeDefinition parent, SoftTypeMeta meta) {
        TypeDefinition td = new TypeDefinition(meta.code, meta.name, TypeDefinition.KIND_SOFT_TYPE);
        td.setIcon(meta.icon);
        td.setSource("ootb");
        td.setDescription(meta.description);
        td.setSortOrder(meta.sortOrder);
        td.setTenantOid(TenantContext.PLATFORM_TENANT_OID);
        td.setParentOid(parent != null ? parent.getOid() : null);
        // 能力宿主：显式声明时以其为准（域下类型必须显式），否则继承父类型
        String rootTypeCode = meta.rootTypeCode != null
                ? meta.rootTypeCode
                : (parent != null
                        ? (parent.getRootTypeCode() != null ? parent.getRootTypeCode() : parent.getCode())
                        : meta.code);
        td.setRootTypeCode(rootTypeCode);
        mapper.insert(td);

        // 编码规则按「能力宿主 rootTypeCode」判定（父类型可能是域下类型，其 code 不携带宿主信息）
        bindNumberRule(td, resolveHostNumberRule(rootTypeCode));
        bindVersionRule(td, "LETTER_26");
        bindLifecycleTemplate(td, "STANDARD");
    }

    /**
     * 注册 OOTB 根类型一条记录并绑定规则/模板。
     *
     * @return true = 新插入；false = 已存在（跳过）
     */
    private boolean ensureOotb(String code, EntityMeta meta) {
        if (mapper.existsByCode(code, TenantContext.PLATFORM_TENANT_OID, TenantContext.PLATFORM_TENANT_OID) > 0) {
            return false;
        }
        TypeDefinition td = new TypeDefinition(code, meta.displayName, TypeDefinition.KIND_OOTB);
        td.setIcon(meta.icon);
        td.setSource("OOTB");
        td.setDescription(meta.description);
        td.setSortOrder(meta.sortOrder);
        td.setTenantOid(TenantContext.PLATFORM_TENANT_OID);
        td.setRootTypeCode(code);  // OOTB 根类型的能力宿主就是自身
        mapper.insert(td);

        if (meta.defaultNumberRuleCode != null) {
            bindNumberRule(td, meta.defaultNumberRuleCode);
        }
        if (meta.defaultVersionRuleCode != null) {
            bindVersionRule(td, meta.defaultVersionRuleCode);
        }
        if (meta.defaultLifecycleTemplateCode != null) {
            bindLifecycleTemplate(td, meta.defaultLifecycleTemplateCode);
        }
        return true;
    }

    /**
     * 注册「域下独立对象类型」（如电子设计项目 ECAD_PROJECT）：能力宿主 = 自身、无父类型，
     * 域归属写入 {@code domain_oid}（权威来源是 {@code ck_business_domain}，域已不是类型表里的行）。
     */
    private void ensureDomainOotbTypes() {
        if (DOMAIN_OOTB_TYPES.isEmpty()) {
            return;
        }
        log.info("开始注册域下独立对象类型...");
        for (SoftTypeMeta meta : DOMAIN_OOTB_TYPES.values()) {
            try {
                if (mapper.existsByCode(meta.code, TenantContext.PLATFORM_TENANT_OID,
                        TenantContext.PLATFORM_TENANT_OID) > 0) {
                    log.debug("  - {} (code={}) 已存在，跳过", meta.displayName, meta.code);
                    continue;
                }
                TypeDefinition td = new TypeDefinition(meta.code, meta.name, TypeDefinition.KIND_OOTB);
                td.setIcon(meta.icon);
                td.setSource("ootb");
                td.setDescription(meta.description);
                td.setSortOrder(meta.sortOrder);
                td.setTenantOid(TenantContext.PLATFORM_TENANT_OID);
                td.setParentOid(null);          // 独立类型：无父、位于类型树顶层
                td.setRootTypeCode(meta.code);  // 能力宿主即自身
                mapper.insert(td);

                // 域归属：域表尚未就绪时留空（下一轮启动由 migrateTypesToDomains 按 TYPE_DOMAIN 补齐）
                try {
                    BusinessDomain domain = meta.domainCode == null ? null
                            : businessDomainMapper.selectByCode(meta.domainCode);
                    if (domain != null) {
                        mapper.updateDomainOid(td.getOid(), domain.getOid());
                    }
                } catch (Exception e) {
                    log.debug("  域归属暂缓（{}）: {}", meta.code, e.getMessage());
                }
                log.info("  √ 域下独立类型 {} (code={}) 已注册（域 {}，能力宿主=自身）",
                        meta.displayName, meta.code, meta.domainCode);
            } catch (Exception e) {
                log.error("  ✗ 注册域下独立类型 {} 失败: {}", meta.code, e.getMessage(), e);
            }
        }
        log.info("域下独立对象类型注册完成");
    }

    /** 为「域下独立对象类型」补齐编码规则绑定（幂等，对存量类型同样生效） */
    private void ensureDomainOotbBinds() {
        for (Map.Entry<String, String> entry : DOMAIN_OOTB_NUMBER_RULES.entrySet()) {
            try {
                TypeDefinition td = mapper.selectByCode(entry.getKey(),
                        TenantContext.PLATFORM_TENANT_OID, TenantContext.PLATFORM_TENANT_OID);
                if (td == null) {
                    continue;
                }
                bindNumberRule(td, entry.getValue());
            } catch (Exception e) {
                log.warn("  ✗ 补齐 {} 的编码规则绑定失败: {}", entry.getKey(), e.getMessage());
            }
        }
    }

    /**
     * 按能力宿主解析子类型应继承的编码规则：<b>读取宿主根类型自身的绑定</b>
     * （PART / DOCUMENT / ENG_DOCUMENT / FUNCTIONAL 已在 {@link #ENTITY_META} 声明各自规则）。
     *
     * <p>此前为二元硬编码（DOCUMENT → DOC_NUMBER，其余一律 PART_NUMBER），
     * 使宿主为 ENG_DOCUMENT 的子类型（封装 / 图符）被错误绑定为 PART_NUMBER；
     * 改为读宿主绑定后，新增能力宿主无需再改本方法。
     */
    private String resolveHostNumberRule(String rootTypeCode) {
        if (rootTypeCode != null && !rootTypeCode.isEmpty()) {
            TypeDefinition root = mapper.selectByCode(rootTypeCode,
                    TenantContext.PLATFORM_TENANT_OID, TenantContext.PLATFORM_TENANT_OID);
            if (root != null) {
                TypeNumberRuleLink hostLink = numberRuleLinkMapper.selectByTypeOid(root.getOid());
                if (hostLink != null && hostLink.getNumberRuleCode() != null) {
                    return hostLink.getNumberRuleCode();
                }
            }
        }
        // 兜底：宿主未绑定规则时退回历史语义
        return "DOCUMENT".equals(rootTypeCode) ? "DOC_NUMBER" : "PART_NUMBER";
    }

    // ========================================================================================
    // 四、存量数据迁移与修正（幂等；只对老数据起作用）
    // ========================================================================================

    /**
     * 补/校正各类型的域归属（{@code domain_oid}），并顺带校正电子域子类型的能力宿主。
     *
     * <p>两件事按 {@link #TYPE_DOMAIN} 逐条执行：
     * <ol>
     *   <li>能力宿主校正 —— 必须独立于"是否已有域"的判断之外，否则已属域的类型会永远不更新宿主
     *       （如封装 / 图符由 PART/DOCUMENT 改为 ENG_DOCUMENT）；</li>
     *   <li>域归属 —— 写 {@code domain_oid}，<b>不动 {@code parent_oid}</b>（那列只表达类型继承）。</li>
     * </ol>
     */
    private void migrateTypesToDomains() {
        int moved = 0;
        for (Map.Entry<String, String> entry : TYPE_DOMAIN.entrySet()) {
            String typeCode = entry.getKey();
            String domainCode = entry.getValue();
            try {
                BusinessDomain domain = businessDomainMapper.selectByCode(domainCode);
                TypeDefinition td = mapper.selectByCode(typeCode,
                        TenantContext.PLATFORM_TENANT_OID, TenantContext.PLATFORM_TENANT_OID);
                if (domain == null || td == null) {
                    continue;
                }

                // 1) 能力宿主校正
                SoftTypeMeta ecad = ECAD_CHILD_TYPES.get(typeCode);
                if (ecad != null && ecad.rootTypeCode != null && !ecad.rootTypeCode.equals(td.getRootTypeCode())) {
                    mapper.updateRootTypeCode(td.getOid(), ecad.rootTypeCode);
                    log.info("  √ 类型 {} 能力宿主 rootTypeCode 已校正为 {}", typeCode, ecad.rootTypeCode);
                }

                // 2) 域归属
                if (!domain.getOid().equals(td.getDomainOid())) {
                    mapper.updateDomainOid(td.getOid(), domain.getOid());
                    moved++;
                    log.info("  √ 类型 {} 已归属域 {}", typeCode, domainCode);
                }
            } catch (Exception e) {
                log.error("  ✗ 迁移类型 {} 到域 {} 失败: {}", typeCode, domainCode, e.getMessage(), e);
            }
        }
        if (moved > 0) {
            log.info("域归属迁移完成：{} 个类型已挂到对应域下", moved);
        }
    }

    /**
     * 把「标准件」「通用件」的所属类型纠正为「结构件」（存量库的老数据纠偏）。
     *
     * <p>二者在 PLM 实践中都是机械结构件，资源库侧早已按此口径取软类型；
     * 老库里它们曾挂在「部件」下，于是界面显示与资源库口径不一致。
     * {@code root_type_code} 不动（能力宿主仍是 PART，平台能力不受影响）；幂等。
     */
    private void migrateStdGenPartsUnderStructural() {
        TypeDefinition structural = mapper.selectByCode("STRUCTURAL",
                TenantContext.PLATFORM_TENANT_OID, TenantContext.PLATFORM_TENANT_OID);
        if (structural == null) {
            log.warn("未找到结构件类型（STRUCTURAL），跳过标准件/通用件的归属迁移");
            return;
        }
        int moved = 0;
        for (String code : new String[]{"STD_PART", "GEN_PART"}) {
            try {
                TypeDefinition td = mapper.selectByCode(code,
                        TenantContext.PLATFORM_TENANT_OID, TenantContext.PLATFORM_TENANT_OID);
                if (td == null) {
                    continue;
                }
                if (!structural.getOid().equals(td.getParentOid())) {
                    mapper.updateParentOid(td.getOid(), structural.getOid());
                    moved++;
                    log.info("  √ 类型 {}（{}）的所属类型已改为 结构件", code, td.getName());
                }
            } catch (Exception e) {
                log.error("  ✗ 迁移类型 {} 到结构件下失败: {}", code, e.getMessage(), e);
            }
        }
        if (moved > 0) {
            log.info("标准件/通用件归属迁移完成：{} 个类型已挂到结构件下", moved);
        }
    }

    /**
     * 修正 SOFT_TYPE 的编码规则绑定，使其与能力宿主（{@code root_type_code} 对应根类型）一致。
     *
     * <p>早期版本按二元硬编码绑定（仅 DOCUMENT → DOC_NUMBER，其余 PART_NUMBER），
     * 使宿主为 ENG_DOCUMENT 的子类型（封装 / 图符）被错绑为 {@code PART_NUMBER}；
     * 本方法幂等地纠正为宿主根类型的规则（{@code DOC_NUMBER}）。
     */
    private void repairSoftTypeNumberRules() {
        List<TypeDefinition> softTypes;
        try {
            softTypes = mapper.selectByTypeKind(TypeDefinition.KIND_SOFT_TYPE,
                    TenantContext.PLATFORM_TENANT_OID, TenantContext.PLATFORM_TENANT_OID);
        } catch (Exception e) {
            log.debug("编码规则绑定修正跳过（查询失败）: {}", e.getMessage());
            return;
        }
        if (softTypes == null || softTypes.isEmpty()) {
            return;
        }
        int fixed = 0;
        for (TypeDefinition td : softTypes) {
            try {
                String rootTypeCode = td.getRootTypeCode();
                if (rootTypeCode == null || rootTypeCode.isEmpty()) {
                    continue;
                }
                TypeDefinition root = mapper.selectByCode(rootTypeCode,
                        TenantContext.PLATFORM_TENANT_OID, TenantContext.PLATFORM_TENANT_OID);
                if (root == null) {
                    continue;
                }
                TypeNumberRuleLink hostLink = numberRuleLinkMapper.selectByTypeOid(root.getOid());
                if (hostLink == null || hostLink.getNumberRuleCode() == null) {
                    continue;
                }
                TypeNumberRuleLink myLink = numberRuleLinkMapper.selectByTypeOid(td.getOid());
                if (myLink == null) {
                    bindNumberRule(td, hostLink.getNumberRuleCode());
                    fixed++;
                } else if (!hostLink.getNumberRuleCode().equals(myLink.getNumberRuleCode())) {
                    myLink.setNumberRuleCode(hostLink.getNumberRuleCode());
                    numberRuleLinkMapper.update(myLink);
                    log.info("  已修正编码规则绑定: {} → {}", td.getCode(), hostLink.getNumberRuleCode());
                    fixed++;
                }
            } catch (Exception e) {
                log.warn("  修正编码规则绑定失败: {} - {}", td.getCode(), e.getMessage());
            }
        }
        if (fixed > 0) {
            log.info("编码规则绑定修正完成: 共修正 {} 个类型", fixed);
        }
    }

    /**
     * 归一 {@code type_kind} 大小写（幂等）。
     *
     * <p>历史数据中 DOCUMENT / PART / PRODUCT_LINE / PRODUCT_MODEL 曾以 {@code 'ootb'} 小写存储，
     * 造成三个连带问题：{@code isOotb()} 判定失效（"系统预置类型不可删除"保护被绕过）、
     * {@code patchRootTypeCodeForOotb} 补不齐 root_type_code、{@code findRootCode} 无法识别根类型。
     * 代码侧已改为大小写不敏感，本方法把存量数据也归一。
     */
    private void normalizeTypeKindCase() {
        try {
            int n = mapper.normalizeTypeKindCase();
            if (n > 0) {
                log.info("type_kind 大小写已归一: {} 条", n);
            }
        } catch (Exception e) {
            log.warn("type_kind 大小写归一失败: {}", e.getMessage());
        }
    }

    // ========================================================================================
    // 五、平台基础规则（生命周期模板）
    // ========================================================================================

    /** 确保默认生命周期模板（STANDARD / SIMPLE）存在（幂等） */
    private void ensureDefaultLifecycleTemplates() {
        ensureLifecycleTemplateStandard();
        ensureLifecycleTemplateSimple();
    }

    /** STANDARD：DRAFT → IN_WORK → RELEASED */
    private void ensureLifecycleTemplateStandard() {
        final String code = "STANDARD";
        try {
            if (lifecycleTemplateService.exists(code)) {
                log.debug("  生命周期模板 {} 已存在，跳过", code);
                return;
            }
            LifecycleTemplateMaster template = new LifecycleTemplateMaster();
            template.setOid(UUID.randomUUID().toString());
            template.setCode(code);
            template.setName("标准生命周期");
            template.setDescription("DRAFT → IN_WORK → RELEASED 三阶段标准流程");
            template.setActive(true);
            template.setInitialStateCode("DRAFT");
            template.setTenantOid(TenantContext.PLATFORM_TENANT_OID);

            // 状态
            template.getStates().add(new LifecycleTemplateStatusRef("DRAFT", "草稿", 1));
            template.getStates().add(new LifecycleTemplateStatusRef("IN_WORK", "工作中", 2));
            template.getStates().add(new LifecycleTemplateStatusRef("RELEASED", "已发布", 3));

            // 升版流转
            template.getTransitions().add(new LifecycleTemplateTransitionRef("DRAFT", "IN_WORK", "PROMOTE"));
            template.getTransitions().add(new LifecycleTemplateTransitionRef("IN_WORK", "RELEASED", "PROMOTE"));

            // 驳回流转
            template.getRejections().add(new LifecycleTemplateTransitionRef("IN_WORK", "DRAFT", "REJECT"));
            template.getRejections().add(new LifecycleTemplateTransitionRef("RELEASED", "IN_WORK", "REJECT"));

            lifecycleTemplateService.create(template);
            log.info("  √ 生命周期模板已创建: STANDARD (标准生命周期)");
        } catch (Exception e) {
            log.error("  ✗ 创建生命周期模板 STANDARD 失败: {}", e.getMessage(), e);
        }
    }

    /** SIMPLE：DRAFT → RELEASED */
    private void ensureLifecycleTemplateSimple() {
        final String code = "SIMPLE";
        try {
            if (lifecycleTemplateService.exists(code)) {
                log.debug("  生命周期模板 {} 已存在，跳过", code);
                return;
            }
            LifecycleTemplateMaster template = new LifecycleTemplateMaster();
            template.setOid(UUID.randomUUID().toString());
            template.setCode(code);
            template.setName("简单生命周期");
            template.setDescription("DRAFT → RELEASED 两阶段简化流程");
            template.setActive(true);
            template.setInitialStateCode("DRAFT");
            template.setTenantOid(TenantContext.PLATFORM_TENANT_OID);

            // 状态
            template.getStates().add(new LifecycleTemplateStatusRef("DRAFT", "草稿", 1));
            template.getStates().add(new LifecycleTemplateStatusRef("RELEASED", "已发布", 2));

            // 升版流转
            template.getTransitions().add(new LifecycleTemplateTransitionRef("DRAFT", "RELEASED", "PROMOTE"));

            // 驳回流转
            template.getRejections().add(new LifecycleTemplateTransitionRef("RELEASED", "DRAFT", "REJECT"));

            lifecycleTemplateService.create(template);
            log.info("  √ 生命周期模板已创建: SIMPLE (简单生命周期)");
        } catch (Exception e) {
            log.error("  ✗ 创建生命周期模板 SIMPLE 失败: {}", e.getMessage(), e);
        }
    }

    // ========================================================================================
    // 六、绑定工具（类型 → 编码规则 / 版本规则 / 生命周期模板；均已存在则跳过，幂等）
    // ========================================================================================

    private void bindNumberRule(TypeDefinition td, String numberRuleCode) {
        try {
            if (numberRuleLinkMapper.existsByTypeOid(td.getOid()) > 0) {
                log.debug("  编码规则绑定已存在: {} → {}", td.getCode(), numberRuleCode);
                return;
            }
            TypeNumberRuleLink link = new TypeNumberRuleLink(td.getOid(), numberRuleCode);
            link.setOid(UUID.randomUUID().toString());
            link.setTenantOid(TenantContext.PLATFORM_TENANT_OID);
            numberRuleLinkMapper.insert(link);
            log.info("  已绑定编码规则: {} → {}", td.getCode(), numberRuleCode);
        } catch (Exception e) {
            log.error("  绑定编码规则失败: {} → {}, {}", td.getCode(), numberRuleCode, e.getMessage(), e);
        }
    }

    private void bindVersionRule(TypeDefinition td, String versionRuleCode) {
        try {
            if (versionRuleLinkMapper.existsByTypeOid(td.getOid()) > 0) {
                log.debug("  版本规则绑定已存在: {} → {}", td.getCode(), versionRuleCode);
                return;
            }
            TypeVersionRuleLink link = new TypeVersionRuleLink(td.getOid(), versionRuleCode);
            link.setOid(UUID.randomUUID().toString());
            link.setTenantOid(TenantContext.PLATFORM_TENANT_OID);
            versionRuleLinkMapper.insert(link);
            log.info("  已绑定版本规则: {} → {}", td.getCode(), versionRuleCode);
        } catch (Exception e) {
            log.error("  绑定版本规则失败: {} → {}, {}", td.getCode(), versionRuleCode, e.getMessage(), e);
        }
    }

    private void bindLifecycleTemplate(TypeDefinition td, String lifecycleTemplateCode) {
        try {
            if (lifecycleTemplateLinkMapper.existsByTypeOid(td.getOid()) > 0) {
                log.debug("  生命周期模板绑定已存在: {} → {}", td.getCode(), lifecycleTemplateCode);
                return;
            }
            TypeLifecycleTemplateLink link = new TypeLifecycleTemplateLink(td.getOid(), lifecycleTemplateCode);
            link.setOid(UUID.randomUUID().toString());
            link.setTenantOid(TenantContext.PLATFORM_TENANT_OID);
            lifecycleTemplateLinkMapper.insert(link);
            log.info("  已绑定生命周期模板: {} → {}", td.getCode(), lifecycleTemplateCode);
        } catch (Exception e) {
            log.error("  绑定生命周期模板失败: {} → {}, {}", td.getCode(), lifecycleTemplateCode, e.getMessage(), e);
        }
    }
}
