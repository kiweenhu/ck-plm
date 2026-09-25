/*
 * Copyright (c) 2025 深圳乘恺科技有限公司
 * All rights reserved.
 *
 * @author Kiween.Hu; Roney.Liu
 */

package cn.ck.plm.softtype.config;

import cn.ck.plm.document.entity.Document;
import cn.ck.plm.document.entity.EngineeringDocument;
import cn.ck.plm.base.entity.*;
import cn.ck.plm.base.mapper.NumberMapper;
import cn.ck.plm.base.mapper.NumberSegmentMapper;
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
 * 应用启动时自动扫描 ootb 实体对象，将其注册到 ck_type_definition 表，
 * 并自动绑定默认的编码规则、版本规则和生命周期模板。
 *
 * <p>通过 {@link #ENTITY_META} 配置实体类与其元数据及默认规则/模板的映射。
 * 实体 code 由配置显式指定（如 PRODUCT_LINE、DOCUMENT）。
 *
 * <h3>扩展方式</h3>
 * 在 {@link #ENTITY_META} 静态块中添加一行 {@code em()} 即可注册新实体类型。
 *
 * <h3>执行顺序</h3>
 * <ul>
 *   <li>本类（{@code @Order(2)}）先注册类型定义并绑定规则/模板</li>
 *   <li>{@link AttributeInitializer}（{@code @Order(3)}）随后扫描实体字段并注册属性定义</li>
 *   <li>{@link PageLayoutInitializer}（{@code @Order(4)}）创建默认页面布局</li>
 * </ul>
 *
 * <p>幂等：已存在的类型定义/编码规则/版本规则/生命周期模板/关联记录不会重复插入。</p>
 */
@Component
@Order(2) // 在 PageLayoutMigration(@Order=1) 之后，在 AttributeInitializer(@Order=3) 之前
public class TypeDefinitionInitializer implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(TypeDefinitionInitializer.class);

    private final TypeDefinitionMapper mapper;
    private final TypeNumberRuleLinkMapper numberRuleLinkMapper;
    private final TypeVersionRuleLinkMapper versionRuleLinkMapper;
    private final TypeLifecycleTemplateLinkMapper lifecycleTemplateLinkMapper;
    private final NumberMapper numberMapper;
    private final NumberSegmentMapper numberSegmentMapper;
    private final LifecycleTemplateService lifecycleTemplateService;

    // ==================== 实体类型元数据配置 ====================

    /**
     * 实体元数据，code 由配置显式指定（如 PRODUCT_LINE、DOCUMENT）。
     */
    private static class EntityMeta {
        /** TypeDefinition.code，如 PRODUCT_LINE、DOCUMENT */
        final String code;
        final String displayName;
        final String icon;
        final String description;
        final int sortOrder;
        /** 默认编码规则 code（Number.code），null 表示不绑定 */
        final String defaultNumberRuleCode;
        /** 默认版本规则 code（VersionRule.code），null 表示不绑定 */
        final String defaultVersionRuleCode;
        /** 默认生命周期模板 code（LifecycleTemplateMaster.code），null 表示不绑定 */
        final String defaultLifecycleTemplateCode;

        EntityMeta(String code, String displayName, String icon, String description, int sortOrder) {
            this(code, displayName, icon, description, sortOrder, null, null, null);
        }

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

    /** OOTB 实体 → 类型定义元数据映射（新增实体只需在此添加一行） */
    private static final Map<Class<? extends BaseEntity>, EntityMeta> ENTITY_META = new LinkedHashMap<>();
    static {
        em(ProductLine.class,  "PRODUCT_LINE", "产品系列", "ApartmentOutlined",
                "产品系列管理，关联产品、团队与缩略图", 5,
                "PRODUCT_LINE", "LETTER_8", "STANDARD");
        em(ProductModel.class, "PRODUCT_MODEL", "产品型号", "TagOutlined",
                "产品型号管理，隶属于产品系列，拥有独立团队和研发阶段", 6,
                "PRODUCT_MODEL", "LETTER_8", "STANDARD");
        em(Document.class,     "DOCUMENT", "文档", "FileTextOutlined",
                "文档复合对象（主数据+子版本），支持版本控制、文件存储与阶段关联", 10,
                "DOC_NUMBER", "LETTER_8", "STANDARD");
        em(EngineeringDocument.class, "ENG_DOCUMENT", "工程数据", "FileImageOutlined",
                "工程数据（参照 Windchill EPMDocument）：3D 数模 / 2D 工程图 / 材料规格说明等工程对象的统称，"
                        + "含 CAD 主文件、制图属性（图幅/比例/图号）与零部件描述关系，"
                        + "并可向电子领域扩展（符号、封装等 EDA 设计数据）", 11,
                "DOC_NUMBER", "LETTER_8", "STANDARD");
        em(Part.class,         "PART", "部件", "ToolOutlined",
                "部件复合对象（主数据+子版本），支持版本控制、分类关联与单位管理", 12,
                "PART_NUMBER", "LETTER_8", "STANDARD");
        em(FunctionalEntity.class, "FUNCTIONAL", "功能架构(构型)", "ClusterOutlined",
                "装备级功能系统（军工）/ 车型功能域（汽车），继承 Part 复合实体结构", 14,
                "FUNCTIONAL-NUM", "LETTER_8", "STANDARD");
    }

    /**
     * Functional 实体下的自定义子类型（SOFT_TYPE，source=ootb，typeKind=SOFT_TYPE）。
     * code → {displayName, icon, description, sortOrder}
     */
    private static final Map<String, SoftTypeMeta> FUNCTIONAL_SOFT_TYPES = new LinkedHashMap<>();
    static {
        fst("SYSTEM", "系统", "系统", "ApartmentOutlined",
                "装备级系统，对应军工领域的武器系统/火控系统/导航系统，或汽车领域的动力域/底盘域", 15);
        fst("SUBSYSTEM", "子系统", "子系统", "BlockOutlined",
                "系统下的子功能模块，如武器系统下的发射子系统、制导子系统", 16);
        fst("CI", "CI", "配置/构型项目", "ControlOutlined",
                "配置项(Configuration Item)，可独立管理、版本控制的构型单元", 17);
    }

    /**
     * Part 实体下的自定义子类型（SOFT_TYPE，source=ootb，typeKind=SOFT_TYPE）。
     * code → {name, displayName, icon, description, sortOrder}
     *
     * <p>注：电子域对象类型（FOOTPRINT 封装、SYMBOL 图符、ELECTRONIC 电子元器件、PCBA）
     * 已迁移至电子域锚点 {@code ECAD_DOMAIN} 之下，见 {@link #ECAD_CHILD_TYPES}。
     */
    private static final Map<String, SoftTypeMeta> PART_SOFT_TYPES = new LinkedHashMap<>();
    static {
        // 结构件 / 电气件 / 软件已迁移至各自设计域锚点之下，见 {@link #TYPE_DOMAIN}。
        //
        // 注：标准件库(STD_PART) / 通用件库(GEN_PART) 不再单独注册软类型——
        // 二者在 PLM 实践中均为机械标准件 / 通用结构件，统一复用 PART 下的结构件软类型
        // STRUCTURAL（挂靠结构设计域 MCAD_DOMAIN），由资源库容器(containerOid)区分具体库别。
        // 此处预留给未来直接挂在 Part 下的通用子类型。
    }

    /**
     * Document 实体下的子类型（SOFT_TYPE，source=ootb）。
     *
     * <p>注：电子域文档类型（SCHEMATIC 原理图、PCB_LAYOUT PCB 设计）已迁移至电子域锚点
     * {@code ECAD_DOMAIN} 之下，见 {@link #ECAD_CHILD_TYPES}；此处预留给非域相关文档子类型。
     */
    private static final Map<String, SoftTypeMeta> DOCUMENT_SOFT_TYPES = new LinkedHashMap<>();
    static {
        // 暂无非域相关文档子类型；电子域文档类型见 ECAD_CHILD_TYPES
    }

    private static void dst(String code, String name, String displayName, String icon, String description, int sortOrder) {
        DOCUMENT_SOFT_TYPES.put(code, new SoftTypeMeta(code, name, displayName, icon, description, sortOrder));
    }

    /**
     * 域锚点类型（DOMAIN，source=ootb）：业务域的命名空间根，位于类型树顶层（parentOid 为 null）。
     * 该业务域的对象类型作为 SOFT_TYPE 直接挂在其下，未来扩展新对象类型只需新增 softtype，零改表。
     */
    private static final Map<String, SoftTypeMeta> DOMAIN_TYPES = new LinkedHashMap<>();
    static {
        dm("PRODUCT_DATA_DOMAIN", "产品主数据域", "产品主数据", "DatabaseOutlined",
                "产品主数据域锚点：文档、产品系列、产品型号、零组件（Part）等企业主数据对象的命名空间根", 30);
        dm("ECAD_DOMAIN", "电子设计域", "电子域", "ThunderboltOutlined",
                "电子设计域（ECAD）锚点：封装、原理图图符、电子元器件、PCBA、原理图、PCB 设计等电子域对象类型的命名空间根；"
                        + "未来新增电子域对象类型只需在其下新增 softtype，自动继承域归属与统一导航入口", 31);
        dm("MCAD_DOMAIN", "结构设计域", "结构域", "ToolOutlined",
                "结构设计域（MCAD）锚点：结构件等结构专业对象类型的命名空间根", 32);
        dm("ELECTRICAL_DOMAIN", "电气设计域", "电气域", "BulbOutlined",
                "电气设计域锚点：电气件（线束、连接器、继电器、开关、电机等）的命名空间根", 33);
        dm("SOFTWARE_DOMAIN", "软件设计域", "软件域", "CodeOutlined",
                "软件设计域锚点：软件（嵌入式软件、应用软件、固件、算法等）的命名空间根", 34);
        dm("ARCHITECTURE_DOMAIN", "架构设计域", "架构域", "ClusterOutlined",
                "架构设计域锚点：功能架构（Functional，含系统/子系统/配置项）的命名空间根", 35);
    }

    /**
     * 电子域（ECAD_DOMAIN）下的对象类型（SOFT_TYPE，source=ootb）。
     *
     * <p>每个类型<b>显式声明 rootTypeCode</b>（能力宿主）：
     * {@code PART} → 数据落 ck_part、用 PART_NUMBER 编码；
     * {@code DOCUMENT} → 数据落 ck_document、用 DOC_NUMBER 编码。
     * 域锚点这一中间层因此不会削弱编码、版本、生命周期、宿主表等任何平台能力。
     */
    private static final Map<String, SoftTypeMeta> ECAD_CHILD_TYPES = new LinkedHashMap<>();
    static {
        // ===== EDA 设计数据类：宿主为工程数据 ENG_DOCUMENT（设计数据，非物料） =====
        ect("FOOTPRINT", "封装", "封装", "BorderOutlined",
                "电子元器件封装（PCB 焊盘图形 / Land Pattern），遵循 IPC-7351 命名，如 RESC1608X55N；"
                        + "属 EDA 设计数据，宿主为工程数据 ENG_DOCUMENT", 31, "ENG_DOCUMENT");
        ect("SYMBOL", "原理图图符", "原理图图符", "BlockOutlined",
                "原理图符号与引脚定义，供电子元器件在原理图设计中引用；"
                        + "属 EDA 设计数据，宿主为工程数据 ENG_DOCUMENT", 32, "ENG_DOCUMENT");
        // ===== 物料类：宿主为 PART（实物物料，参与 BOM） =====
        ect("ELECTRONIC", "电子元器件", "电子元器件", "ThunderboltOutlined",
                "电子元器件，如电阻、电容、电感、二极管、IC、连接器等", 33, "PART");
        ect("PCBA", "PCBA", "PCBA组件", "AppstoreOutlined",
                "印刷电路板组件，已完成电子元器件贴装的电路板", 34, "PART");
        // ===== EDA 设计数据类（续） =====
        ect("SCHEMATIC", "原理图", "原理图", "FileTextOutlined",
                "电子原理图设计文件，含图页与器件实例；属 EDA 设计数据，宿主为工程数据 ENG_DOCUMENT", 35, "ENG_DOCUMENT");
        ect("PCB_LAYOUT", "PCB设计", "PCB 设计", "LayoutOutlined",
                "PCB 布局布线设计，含层叠、板厚、Gerber 文件集；属 EDA 设计数据，宿主为工程数据 ENG_DOCUMENT", 36, "ENG_DOCUMENT");
    }

    private static void dm(String code, String name, String displayName, String icon, String description, int sortOrder) {
        DOMAIN_TYPES.put(code, new SoftTypeMeta(code, name, displayName, icon, description, sortOrder));
    }

    private static void ect(String code, String name, String displayName, String icon, String description,
                            int sortOrder, String rootTypeCode) {
        ECAD_CHILD_TYPES.put(code, new SoftTypeMeta(code, name, displayName, icon, description, sortOrder, rootTypeCode));
    }

    /**
     * 对象类型 code → 归属域锚点 code。
     *
     * <p>用于启动时把存量类型迁移到对应域锚点之下（OOTB 根类型与 softtype 均适用）；
     * 新建的域下子类型由 {@code ensureSoftTypes(domainCode, ...)} 直接挂载。
     */
    private static final Map<String, String> TYPE_DOMAIN = new LinkedHashMap<>();
    static {
        // 产品主数据域：文档 / 产品系列 / 产品型号 / 零组件
        tdm("DOCUMENT", "PRODUCT_DATA_DOMAIN");
        tdm("ENG_DOCUMENT", "PRODUCT_DATA_DOMAIN");
        tdm("PRODUCT_LINE", "PRODUCT_DATA_DOMAIN");
        tdm("PRODUCT_MODEL", "PRODUCT_DATA_DOMAIN");
        tdm("PART", "PRODUCT_DATA_DOMAIN");
        // 架构设计域：功能架构
        tdm("FUNCTIONAL", "ARCHITECTURE_DOMAIN");
        // 结构设计域：结构件
        tdm("STRUCTURAL", "MCAD_DOMAIN");
        // 电气设计域：电气件
        tdm("ELECTRICAL", "ELECTRICAL_DOMAIN");
        // 软件设计域：软件
        tdm("SOFTWARE", "SOFTWARE_DOMAIN");
        // 电子设计域：封装 / 原理图图符 / 电子元器件 / PCBA / 原理图 / PCB 设计
        tdm("FOOTPRINT", "ECAD_DOMAIN");
        tdm("SYMBOL", "ECAD_DOMAIN");
        tdm("ELECTRONIC", "ECAD_DOMAIN");
        tdm("PCBA", "ECAD_DOMAIN");
        tdm("SCHEMATIC", "ECAD_DOMAIN");
        tdm("PCB_LAYOUT", "ECAD_DOMAIN");
    }

    private static void tdm(String typeCode, String domainCode) {
        TYPE_DOMAIN.put(typeCode, domainCode);
    }

    /**
     * 域下的独立对象类型：拥有自己独立的实体表（非 PART / DOCUMENT 宿主的 softtype），
     * 语义上等价于 OOTB（能力宿主 = 自身 code），但在类型树上归属于某个域锚点。
     */
    private static final Map<String, SoftTypeMeta> DOMAIN_OOTB_TYPES = new LinkedHashMap<>();
    static {
        dot("ECAD_PROJECT", "设计项目", "设计项目", "ProjectOutlined",
                "电子设计项目：电子设计任务的容器对象，关联原理图 / PCB 设计、所属产品与项目阶段；"
                        + "拥有独立实体表 ck_ecad_project，非 Part / Document 宿主类型", "ECAD_DOMAIN", 40);
    }

    private static void dot(String code, String name, String displayName, String icon,
                            String description, String domainCode, int sortOrder) {
        DOMAIN_OOTB_TYPES.put(code, new SoftTypeMeta(code, name, displayName, icon, description, sortOrder, code, domainCode));
    }

    /**
     * 域下独立对象类型 → 默认编码规则。
     *
     * <p>这类类型的实体（如 {@code ck_ecad_project}）<b>没有迭代/版本/生命周期列</b>
     * （继承 BaseEntity 而非 MasterEntity），因此只需要、也只需要绑定编码规则。
     *
     * <p>此前该类类型注册后<b>完全没有绑定任何规则</b>，使其 {@code code} 列
     * （NOT NULL 唯一键）无处生成——经统一入口创建时必然失败。
     */
    private static final Map<String, String> DOMAIN_OOTB_NUMBER_RULES = new LinkedHashMap<>();
    static {
        DOMAIN_OOTB_NUMBER_RULES.put("ECAD_PROJECT", "ECAD_PROJECT-NUM");
    }

    /**
     * 为「域下独立对象类型」补齐编码规则绑定（幂等）。
     *
     * <p>{@link #ensureDomainOotbTypes()} 只在类型不存在时插入、且不绑定规则；
     * 存量的 ECAD_PROJECT 因此长期无规则可用。本方法对<b>已存在</b>的类型同样生效，
     * 由 {@code bindNumberRule} 自身的幂等性保证不重复绑定。
     */
    private void ensureDomainOotbBinds() {
        if (DOMAIN_OOTB_NUMBER_RULES.isEmpty()) {
            return;
        }
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

    /** 注册 Document 实体下的子类型 */
    private void ensureDocumentSoftTypes() {
        ensureSoftTypes("DOCUMENT", "Document", DOCUMENT_SOFT_TYPES);
    }

    /**
     * 注册域锚点类型（DOMAIN，source=ootb）：业务域的命名空间根，位于类型树顶层。
     *
     * <p>域锚点不绑定编码/版本规则——它本身不是业务对象类型的能力宿主，
     * 域实例的编码由 {@code ck_ecad_domain} 自行维护；域下对象类型的能力由各自的
     * rootTypeCode（PART / DOCUMENT）保证。
     */
    private void ensureDomainTypes() {
        log.info("开始注册域锚点类型...");
        for (SoftTypeMeta meta : DOMAIN_TYPES.values()) {
            try {
                if (mapper.existsByCode(meta.code, TenantContext.PLATFORM_TENANT_OID,
                        TenantContext.PLATFORM_TENANT_OID) > 0) {
                    log.debug("  - 域锚点 {} (code={}) 已存在，跳过", meta.displayName, meta.code);
                    continue;
                }
                TypeDefinition td = new TypeDefinition(meta.code, meta.name, TypeDefinition.KIND_DOMAIN);
                td.setIcon(meta.icon);
                td.setSource("ootb");
                td.setDescription(meta.description);
                td.setSortOrder(meta.sortOrder);
                td.setTenantOid(TenantContext.PLATFORM_TENANT_OID);
                // 域锚点是纯命名空间：无单一能力宿主，rootTypeCode 留空，由域下子类型显式声明
                td.setRootTypeCode(null);
                mapper.insert(td);
                log.info("  √ 域锚点 {} (code={}) 注册成功", meta.displayName, meta.code);
            } catch (Exception e) {
                log.error("  ✗ 注册域锚点 {} 失败: {}", meta.code, e.getMessage(), e);
            }
        }
        log.info("域锚点类型注册完成");
    }

    /** 注册电子域（ECAD_DOMAIN）下的对象类型：显式指定能力宿主 rootTypeCode（PART / DOCUMENT） */
    private void ensureEcadChildTypes() {
        ensureSoftTypes("ECAD_DOMAIN", "电子域", ECAD_CHILD_TYPES);
    }

    /**
     * 注册域下的独立对象类型（如电子设计项目 ECAD_PROJECT）。
     *
     * <p>这类对象拥有自己的实体表（ck_ecad_project），并非 PART / DOCUMENT 的 softtype，
     * 因此 rootTypeCode = 自身 code（能力宿主即自己），类型树归属 parentOid 指向域锚点。
     */
    private void ensureDomainOotbTypes() {
        if (DOMAIN_OOTB_TYPES.isEmpty()) return;
        log.info("开始注册域下独立对象类型...");
        for (SoftTypeMeta meta : DOMAIN_OOTB_TYPES.values()) {
            try {
                if (mapper.existsByCode(meta.code, TenantContext.PLATFORM_TENANT_OID,
                        TenantContext.PLATFORM_TENANT_OID) > 0) {
                    log.debug("  - {} (code={}) 已存在，跳过", meta.displayName, meta.code);
                    continue;
                }
                TypeDefinition domain = mapper.selectByCode(meta.domainCode,
                        TenantContext.PLATFORM_TENANT_OID, TenantContext.PLATFORM_TENANT_OID);
                if (domain == null) {
                    log.warn("  ✗ 未找到域锚点 {}，跳过注册 {}", meta.domainCode, meta.code);
                    continue;
                }
                TypeDefinition td = new TypeDefinition(meta.code, meta.name, TypeDefinition.KIND_OOTB);
                td.setIcon(meta.icon);
                td.setSource("ootb");
                td.setDescription(meta.description);
                td.setSortOrder(meta.sortOrder);
                td.setTenantOid(TenantContext.PLATFORM_TENANT_OID);
                td.setParentOid(domain.getOid());
                td.setRootTypeCode(meta.code); // 独立类型：能力宿主为自身
                mapper.insert(td);
                log.info("  √ 域下独立类型 {} (code={}) 已注册到 {} 下", meta.displayName, meta.code, meta.domainCode);
            } catch (Exception e) {
                log.error("  ✗ 注册域下独立类型 {} 失败: {}", meta.code, e.getMessage(), e);
            }
        }
        log.info("域下独立对象类型注册完成");
    }

    /**
     * 数据迁移：把各对象类型迁移到其归属域锚点之下（含 OOTB 根类型与 softtype）。
     *
     * <p>rootTypeCode 保持不变（平台能力不受影响），仅调整类型树归属；幂等，已在域下则跳过。
     */
    private void migrateTypesToDomains() {
        int moved = 0;
        for (Map.Entry<String, String> entry : TYPE_DOMAIN.entrySet()) {
            String typeCode = entry.getKey();
            String domainCode = entry.getValue();
            try {
                TypeDefinition domain = mapper.selectByCode(domainCode,
                        TenantContext.PLATFORM_TENANT_OID, TenantContext.PLATFORM_TENANT_OID);
                TypeDefinition td = mapper.selectByCode(typeCode,
                        TenantContext.PLATFORM_TENANT_OID, TenantContext.PLATFORM_TENANT_OID);
                if (domain == null || td == null) continue;

                // 1) 能力宿主 rootTypeCode 校正 —— 必须独立于「是否已在域下」的判断。
                //    若放在下面的 parentOid 判断之后，类型已挂域下时会因 continue 而永远
                //    不更新宿主（如封装/符号/原理图/PCB 由 PART/DOCUMENT 改为 ENG_DOCUMENT）。
                SoftTypeMeta ecad = ECAD_CHILD_TYPES.get(typeCode);
                if (ecad != null && ecad.rootTypeCode != null && !ecad.rootTypeCode.equals(td.getRootTypeCode())) {
                    mapper.updateRootTypeCode(td.getOid(), ecad.rootTypeCode);
                    log.info("  √ 类型 {} 能力宿主 rootTypeCode 已校正为 {}", typeCode, ecad.rootTypeCode);
                }

                // 2) 类型树归属：改挂到域锚点下（已在域下则跳过）
                if (!domain.getOid().equals(td.getParentOid())) {
                    mapper.updateParentOid(td.getOid(), domain.getOid());
                    moved++;
                    log.info("  √ 类型 {} 已迁移至域锚点 {} 下", typeCode, domainCode);
                }
            } catch (Exception e) {
                log.error("  ✗ 迁移类型 {} 到域 {} 失败: {}", typeCode, domainCode, e.getMessage(), e);
            }
        }
        if (moved > 0) {
            log.info("域归属迁移完成：{} 个类型已挂到对应域锚点下", moved);
        }
    }

    /**
     * Functional 子类型元数据
     */
    private static class SoftTypeMeta {
        final String code;
        final String name;
        final String displayName;
        final String icon;
        final String description;
        final int sortOrder;
        /**
         * 显式指定的根 OOTB 类型 code（能力宿主，如 PART / DOCUMENT）。
         * <ul>
         *   <li>挂在 OOTB 根下：为 null，自动继承父类型的 rootTypeCode</li>
         *   <li>挂在域锚点（DOMAIN）下：<b>必须显式指定</b>——域锚点无单一能力宿主，
         *       需由子类型自己声明落在 ck_part 还是 ck_document</li>
         * </ul>
         */
        final String rootTypeCode;
        /** 归属域锚点 code（仅「域下独立对象类型」使用，如 ECAD_PROJECT 归属 ECAD_DOMAIN） */
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

    private static void fst(String code, String name, String displayName, String icon, String description, int sortOrder) {
        FUNCTIONAL_SOFT_TYPES.put(code, new SoftTypeMeta(code, name, displayName, icon, description, sortOrder));
    }

    private static void pst(String code, String name, String displayName, String icon, String description, int sortOrder) {
        PART_SOFT_TYPES.put(code, new SoftTypeMeta(code, name, displayName, icon, description, sortOrder));
    }

    // ==================== 构造注入 ====================

    public TypeDefinitionInitializer(TypeDefinitionMapper mapper,
                                     TypeNumberRuleLinkMapper numberRuleLinkMapper,
                                     TypeVersionRuleLinkMapper versionRuleLinkMapper,
                                     TypeLifecycleTemplateLinkMapper lifecycleTemplateLinkMapper,
                                     NumberMapper numberMapper,
                                     NumberSegmentMapper numberSegmentMapper,
                                     LifecycleTemplateService lifecycleTemplateService) {
        this.mapper = mapper;
        this.numberRuleLinkMapper = numberRuleLinkMapper;
        this.versionRuleLinkMapper = versionRuleLinkMapper;
        this.lifecycleTemplateLinkMapper = lifecycleTemplateLinkMapper;
        this.numberMapper = numberMapper;
        this.numberSegmentMapper = numberSegmentMapper;
        this.lifecycleTemplateService = lifecycleTemplateService;
    }

    // ==================== 启动入口 ====================

    @Override
    public void run(String... args) {
        log.info("开始初始化 OOTB 类型定义（含编码规则、版本规则、生命周期模板绑定）...");

        // 兼容旧表：添加 root_type_code 列
        try {
            mapper.addRootTypeCodeColumn();
            log.info("ck_type_definition 表已添加 root_type_code 列（如之前不存在）");
        } catch (Exception e) {
            log.debug("添加 root_type_code 列: {}", e.getMessage());
        }

        // 先确保默认编码规则存在
        ensureDefaultNumberRules();

        // 先确保默认生命周期模板存在
        ensureDefaultLifecycleTemplates();

        int inserted = 0, skipped = 0;
        for (Map.Entry<Class<? extends BaseEntity>, EntityMeta> entry : ENTITY_META.entrySet()) {
            Class<?> entityClass = entry.getKey();
            EntityMeta meta = entry.getValue();

            try {
                String code = meta.code;
                boolean isNew = ensureOotb(code, meta);
                if (isNew) {
                    log.info("  √ {} → {} (code={})", entityClass.getSimpleName(), meta.displayName, code);
                    inserted++;
                } else {
                    log.debug("  - {} (code={}) 已存在，跳过", meta.displayName, code);
                    skipped++;
                }
            } catch (Exception e) {
                log.error("  ✗ 注册实体 {} 类型定义失败: {}", entityClass.getSimpleName(), e.getMessage(), e);
            }
        }

        // 为已有的 OOTB 类型补充 root_type_code = code
        int patched = mapper.patchRootTypeCodeForOotb();
        if (patched > 0) {
            log.info("已为 {} 个 OOTB 类型补充 root_type_code", patched);
        }

        log.info("OOTB 类型定义初始化完成: 新增 {} 个, 已存在 {} 个", inserted, skipped);

        // 注册域锚点类型（ECAD_DOMAIN 电子域等）：业务域命名空间根（必须先于域下子类型注册）
        ensureDomainTypes();

        // 注册 Functional 实体下的自定义子类型（System、Subsystem、CI）
        ensureFunctionalSoftTypes();

        // 注册 Part 实体下的自定义子类型（结构件、电气件、软件）
        ensurePartSoftTypes();

        // 注册 Document 实体下的子类型
        ensureDocumentSoftTypes();

        // 注册电子域（ECAD_DOMAIN）下的对象类型：封装、图符、电子元器件、PCBA、原理图、PCB 设计
        ensureEcadChildTypes();

        // 注册域下独立对象类型：电子设计项目（ECAD_PROJECT，独立实体表 ck_ecad_project）
        ensureDomainOotbTypes();

        // 补齐域下独立对象类型的编码规则绑定（该类类型注册时不带规则，需单独绑定；对存量类型也生效）
        ensureDomainOotbBinds();

        // 数据迁移：把各对象类型迁移到其归属域锚点下（产品主数据域 / 架构域 / 结构域 / 电气域 / 软件域 / 电子域）
        migrateTypesToDomains();

        // 数据修正：使 SOFT_TYPE 的编码规则绑定与其能力宿主一致（幂等）
        repairSoftTypeNumberRules();

        // 数据修正：type_kind 大小写归一（修复 isOotb() / 删除保护 / root_type_code 补偿失效）
        normalizeTypeKindCase();

        // 数据修正：编码规则 code 拼写修正 FUNCATIONAL-NUM → FUNCTIONAL-NUM（幂等）
        fixFunctionalNumberRuleCode();
    }

    /**
     * 归一 {@code type_kind} 大小写为统一的大写形式（幂等）。
     *
     * <p>历史数据中 DOCUMENT / PART / PRODUCT_LINE / PRODUCT_MODEL 以 {@code 'ootb'} 小写存储，
     * 造成三个连带问题：{@code isOotb()} 判定失效（"系统预置类型不可删除"保护被绕过）、
     * {@code patchRootTypeCodeForOotb} 补不齐 root_type_code、{@code findRootCode} 无法识别根类型。
     * 代码侧已改为大小写不敏感，本方法把存量数据也归一到统一形式。
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

    /**
     * 修正编码规则 code 拼写：{@code FUNCATIONAL-NUM} → {@code FUNCTIONAL-NUM}（幂等）。
     *
     * <p>该规则为历史遗留的手工数据，拼写有误且未在代码中声明（现已登记为 FUNCTIONAL 的默认规则）。
     * 处理顺序很关键：
     * <ol>
     *   <li>{@link #ensureDefaultNumberRules()} 先于本方法执行，会创建正确的 {@code FUNCTIONAL-NUM}；
     *       因此<b>不能无条件改名</b>——目标已存在时改名会撞唯一键。</li>
     *   <li>仅当「正式规则不存在、遗留规则存在」时才改名（可保留遗留规则的段配置与流水号计数）。</li>
     *   <li>无论如何都把类型绑定指向正式规则。</li>
     *   <li>最后清理遗留的孤儿规则（正式规则已存在且遗留规则已无任何绑定时）。</li>
     * </ol>
     * 每步独立 try-catch，缺数据时静默跳过。
     */
    private void fixFunctionalNumberRuleCode() {
        final String legacyCode = "FUNCATIONAL-NUM";
        final String properCode = "FUNCTIONAL-NUM";

        // 1) 仅当正式规则缺失时才改名（保留遗留规则的段与计数）
        try {
            if (numberMapper.selectByCode(properCode) == null && numberMapper.selectByCode(legacyCode) != null) {
                if (numberMapper.renameCode(legacyCode, properCode) > 0) {
                    log.info("编码规则 code 已修正: {} → {}", legacyCode, properCode);
                }
            }
        } catch (Exception e) {
            log.warn("编码规则 code 修正失败 {} → {}: {}", legacyCode, properCode, e.getMessage());
        }

        // 2) 类型绑定统一指向正式规则
        try {
            int relinked = numberRuleLinkMapper.updateNumberRuleCode(legacyCode, properCode);
            if (relinked > 0) {
                log.info("编码规则绑定已同步: {} → {}（{} 个类型）", legacyCode, properCode, relinked);
            }
        } catch (Exception e) {
            log.warn("编码规则绑定同步失败 {} → {}: {}", legacyCode, properCode, e.getMessage());
        }

        // 3) 清理遗留孤儿规则（正式规则已存在、且遗留规则无绑定时才删，避免误删在用规则）
        try {
            if (numberMapper.selectByCode(legacyCode) != null
                    && numberMapper.selectByCode(properCode) != null
                    && numberRuleLinkMapper.selectByNumberRuleCode(legacyCode).isEmpty()) {
                numberSegmentMapper.deleteByRuleCode(legacyCode);
                int deleted = numberMapper.deleteByCode(legacyCode);
                if (deleted > 0) {
                    log.info("已清理遗留编码规则: {}（已由 {} 取代）", legacyCode, properCode);
                }
            }
        } catch (Exception e) {
            log.warn("遗留编码规则清理失败 {}: {}", legacyCode, e.getMessage());
        }
    }

    /**
     * 修正 SOFT_TYPE 的编码规则绑定，使其与能力宿主（{@code root_type_code} 对应根类型）一致。
     *
     * <p>早期版本按二元硬编码绑定（仅 DOCUMENT → DOC_NUMBER，其余 PART_NUMBER），
     * 使宿主为 ENG_DOCUMENT 的子类型（FOOTPRINT / SYMBOL）被错误绑定为 {@code PART_NUMBER}。
     * 本方法幂等地将其纠正为宿主根类型的规则（{@code DOC_NUMBER}），
     * 已绑定记录才会插入的历史数据由此得到修正。
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
     * 注册 Functional 实体下的自定义子类型（SOFT_TYPE，source=ootb）。
     */
    private void ensureFunctionalSoftTypes() {
        ensureSoftTypes("FUNCTIONAL", "Functional", FUNCTIONAL_SOFT_TYPES);
    }

    /**
     * 注册 Part 实体下的自定义子类型（SOFT_TYPE，source=ootb）。
     */
    private void ensurePartSoftTypes() {
        ensureSoftTypes("PART", "Part", PART_SOFT_TYPES);
    }

    /**
     * 注册指定 OOTB 实体下的自定义子类型（SOFT_TYPE，source=ootb）。
     */
    private void ensureSoftTypes(String parentCode, String parentLabel, Map<String, SoftTypeMeta> softTypes) {
        // 查找父类型的 oid
        TypeDefinition parent = mapper.selectByCode(parentCode,
                TenantContext.PLATFORM_TENANT_OID, TenantContext.PLATFORM_TENANT_OID);
        if (parent == null) {
            log.warn("未找到 {} 父类型，跳过其子类型注册", parentCode);
            return;
        }

        log.info("开始注册 {} 子类型...", parentLabel);
        for (SoftTypeMeta meta : softTypes.values()) {
            try {
                if (mapper.existsByCode(meta.code, TenantContext.PLATFORM_TENANT_OID,
                        TenantContext.PLATFORM_TENANT_OID) > 0) {
                    log.debug("  - {} (code={}) 已存在，跳过", meta.displayName, meta.code);
                    continue;
                }
                ensureSoftType(parent, meta);
                log.info("  √ {} (code={}) 注册成功", meta.displayName, meta.code);
            } catch (Exception e) {
                log.error("  ✗ 注册 {} 子类型 {} 失败: {}", parentLabel, meta.code, e.getMessage(), e);
            }
        }
        log.info("{} 子类型注册完成", parentLabel);
    }

    /**
     * 注册单个 SOFT_TYPE 子类型。
     *
     * @param parent 父类型：可能是 OOTB 根类型（PART/DOCUMENT），也可能是域锚点（DOMAIN）
     */
    private void ensureSoftType(TypeDefinition parent, SoftTypeMeta meta) {
        TypeDefinition td = new TypeDefinition(meta.code, meta.name, TypeDefinition.KIND_SOFT_TYPE);
        td.setIcon(meta.icon);
        td.setSource("ootb");
        td.setDescription(meta.description);
        td.setSortOrder(meta.sortOrder);
        td.setTenantOid(TenantContext.PLATFORM_TENANT_OID);
        td.setParentOid(parent.getOid());
        // 能力宿主：子类型显式声明时以其为准（挂在域锚点下必须显式指定），否则继承父类型
        String rootTypeCode = meta.rootTypeCode != null
                ? meta.rootTypeCode
                : (parent.getRootTypeCode() != null ? parent.getRootTypeCode() : parent.getCode());
        td.setRootTypeCode(rootTypeCode);
        mapper.insert(td);

        // 继承规则和模板绑定：编码规则按「能力宿主 rootTypeCode」判定而非父类型 code——
        // 父类型可能是域锚点（如 ECAD_DOMAIN），其 code 不携带宿主信息
        bindNumberRule(td, resolveHostNumberRule(rootTypeCode));
        bindVersionRule(td, "LETTER_8");
        bindLifecycleTemplate(td, "STANDARD");
    }

    /**
     * 按能力宿主解析应继承的编码规则编码。
     *
     * <p>此前为二元硬编码（{@code DOCUMENT → DOC_NUMBER}，其余一律 {@code PART_NUMBER}），
     * 导致宿主为 {@code ENG_DOCUMENT} 的子类型（FOOTPRINT / SYMBOL 等）错误继承
     * {@code PART_NUMBER}，编号出现 {@code PART-} 前缀，与其工程数据身份不符。
     *
     * <p>现改为<b>读取宿主根类型自身的编码规则绑定</b>：OOTB 根类型
     * （PART / DOCUMENT / ENG_DOCUMENT / FUNCTIONAL）已在 {@code ENTITY_META} 中声明各自规则，
     * 子类型直接继承；今后新增能力宿主无需再改动本方法。
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

    // ==================== 类型注册 ====================

    /**
     * 注册一条 OOTB 类型定义记录，并绑定编码规则、版本规则、生命周期模板。
     *
     * @return true 表示新插入，false 表示已存在跳过
     */
    private boolean ensureOotb(String code, EntityMeta meta) {
        // 使用平台租户 oid 检查是否存在
        if (mapper.existsByCode(code, TenantContext.PLATFORM_TENANT_OID, TenantContext.PLATFORM_TENANT_OID) > 0) {
            return false;
        }
        TypeDefinition td = new TypeDefinition(code, meta.displayName, TypeDefinition.KIND_OOTB);
        td.setIcon(meta.icon);
        td.setSource("OOTB");
        td.setDescription(meta.description);
        td.setSortOrder(meta.sortOrder);
        td.setTenantOid(TenantContext.PLATFORM_TENANT_OID);
        td.setRootTypeCode(code);  // OOTB 类型的 rootTypeCode 就是自身 code
        mapper.insert(td);

        // 绑定编码规则
        if (meta.defaultNumberRuleCode != null) {
            bindNumberRule(td, meta.defaultNumberRuleCode);
        }
        // 绑定版本规则
        if (meta.defaultVersionRuleCode != null) {
            bindVersionRule(td, meta.defaultVersionRuleCode);
        }
        // 绑定生命周期模板
        if (meta.defaultLifecycleTemplateCode != null) {
            bindLifecycleTemplate(td, meta.defaultLifecycleTemplateCode);
        }

        return true;
    }

    // ==================== 默认编码规则初始化 ====================

    /**
     * 确保默认编码规则存在（幂等）。
     */
    private void ensureDefaultNumberRules() {
        Map<String, String> rules = new LinkedHashMap<>();
        rules.put("PRODUCT_LINE", "产品系列编码");
        rules.put("PRODUCT_MODEL", "产品型号编码");
        rules.put("DOC_NUMBER", "文档编号");
        rules.put("PART_NUMBER", "部件编号");
        rules.put("FUNCTIONAL-NUM", "功能架构编码规则");
        rules.put("ECAD_PROJECT-NUM", "电子设计项目编码");

        for (Map.Entry<String, String> entry : rules.entrySet()) {
            String ruleCode = entry.getKey();
            String ruleName = entry.getValue();
            try {
                if (numberMapper.existsByCode(ruleCode) > 0) {
                    log.debug("  编码规则 {} 已存在，跳过", ruleCode);
                    continue;
                }
                cn.ck.plm.base.entity.Number number = new cn.ck.plm.base.entity.Number(ruleCode, ruleName);
                number.setOid(UUID.randomUUID().toString());
                number.setEnabled(true);
                number.setDescription(ruleName + "（系统预置）");
                number.setTenantOid(TenantContext.PLATFORM_TENANT_OID);
                numberMapper.insert(number);

                // 创建编码段
                List<NumberSegment> segments = buildDefaultNumberSegments(ruleCode);
                for (NumberSegment seg : segments) {
                    seg.setOid(UUID.randomUUID().toString());
                    seg.setRuleCode(ruleCode);
                    numberSegmentMapper.insert(seg);
                }
                log.info("  √ 编码规则已创建: {} ({})", ruleCode, ruleName);
            } catch (Exception e) {
                log.error("  ✗ 创建编码规则 {} 失败: {}", ruleCode, e.getMessage(), e);
            }
        }
    }

    /**
     * 根据规则编码构建默认的编码段列表。
     */
    private List<NumberSegment> buildDefaultNumberSegments(String ruleCode) {
        List<NumberSegment> segments = new ArrayList<>();
        switch (ruleCode) {
            case "PRODUCT_LINE":
                // PL-001, PL-002...
                segments.add(new NumberSegment("CONST", "PL", 1));
                segments.add(new NumberSegment("SEPARATOR", "-", 2));
                segments.add(new NumberSegment("SERIAL", 3, 1, 3));
                break;
            case "PRODUCT_MODEL":
                // PM-2026-001
                segments.add(new NumberSegment("CONST", "PM", 1));
                segments.add(new NumberSegment("SEPARATOR", "-", 2));
                segments.add(new NumberSegment("YEAR", "yyyy", null, 3));
                segments.add(new NumberSegment("SEPARATOR", "-", 4));
                segments.add(new NumberSegment("SERIAL", 3, 1, 5));
                break;
            case "DOC_NUMBER":
                // DOC-202601-0001
                segments.add(new NumberSegment("CONST", "DOC", 1));
                segments.add(new NumberSegment("SEPARATOR", "-", 2));
                segments.add(new NumberSegment("YEAR", "yyyy", null, 3));
                segments.add(new NumberSegment("MONTH", "MM", null, 4));
                segments.add(new NumberSegment("SEPARATOR", "-", 5));
                segments.add(new NumberSegment("SERIAL", 4, 1, 6));
                break;
            case "PART_NUMBER":
                // PART-202601-0001
                segments.add(new NumberSegment("CONST", "PART", 1));
                segments.add(new NumberSegment("SEPARATOR", "-", 2));
                segments.add(new NumberSegment("YEAR", "yyyy", null, 3));
                segments.add(new NumberSegment("MONTH", "MM", null, 4));
                segments.add(new NumberSegment("SEPARATOR", "-", 5));
                segments.add(new NumberSegment("SERIAL", 4, 1, 6));
                break;
            case "FUNCTIONAL-NUM":
                // FUNC-20260101-00000001
                segments.add(new NumberSegment("CONST", "FUNC", 1));
                segments.add(new NumberSegment("SEPARATOR", "-", 2));
                segments.add(new NumberSegment("YEAR", "yyyy", null, 3));
                segments.add(new NumberSegment("MONTH", "MM", null, 4));
                segments.add(new NumberSegment("DAY", "dd", null, 5));
                segments.add(new NumberSegment("SERIAL", 8, 1, 6));
                break;
            case "ECAD_PROJECT-NUM":
                // ECAD-202601-0001
                segments.add(new NumberSegment("CONST", "ECAD", 1));
                segments.add(new NumberSegment("SEPARATOR", "-", 2));
                segments.add(new NumberSegment("YEAR", "yyyy", null, 3));
                segments.add(new NumberSegment("MONTH", "MM", null, 4));
                segments.add(new NumberSegment("SEPARATOR", "-", 5));
                segments.add(new NumberSegment("SERIAL", 4, 1, 6));
                break;
            default:
                break;
        }
        return segments;
    }

    // ==================== 默认生命周期模板初始化 ====================

    /**
     * 确保默认生命周期模板 STANDARD 和 SIMPLE 存在（幂等）。
     */
    private void ensureDefaultLifecycleTemplates() {
        ensureLifecycleTemplateStandard();
        ensureLifecycleTemplateSimple();
    }

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

    // ==================== 规则/模板绑定 ====================

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

    // ==================== 内部工具 ====================

    private static void em(Class<? extends BaseEntity> entityClass,
                           String code, String displayName, String icon,
                           String description, int sortOrder) {
        ENTITY_META.put(entityClass, new EntityMeta(code, displayName, icon, description, sortOrder));
    }

    private static void em(Class<? extends BaseEntity> entityClass,
                           String code, String displayName, String icon,
                           String description, int sortOrder,
                           String defaultNumberRuleCode, String defaultVersionRuleCode,
                           String defaultLifecycleTemplateCode) {
        ENTITY_META.put(entityClass, new EntityMeta(code, displayName, icon, description, sortOrder,
                defaultNumberRuleCode, defaultVersionRuleCode, defaultLifecycleTemplateCode));
    }
}
