/*
 * Copyright (c) 2025 深圳乘恺科技有限公司
 * All rights reserved.
 *
 * @author Kiween.Hu; Roney.Liu
 */

package cn.ck.plm.softtype.config;

import cn.ck.plm.document.entity.Document;
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
        em(Part.class,         "PART", "部件", "ToolOutlined",
                "部件复合对象（主数据+子版本），支持版本控制、分类关联与单位管理", 12,
                "PART_NUMBER", "LETTER_8", "STANDARD");
        em(FunctionalEntity.class, "FUNCTIONAL", "功能架构(构型)", "ClusterOutlined",
                "装备级功能系统（军工）/ 车型功能域（汽车），继承 Part 复合实体结构", 14,
                "PART_NUMBER", "LETTER_8", "STANDARD");
    }

    /**
     * Functional 实体下的自定义子类型（SOFT_TYPE，source=ootb，typeKind=SOFT_TYPE）。
     * code → {displayName, icon, description, sortOrder}
     */
    private static final Map<String, SoftTypeMeta> FUNCTIONAL_SOFT_TYPES = new LinkedHashMap<>();
    static {
        fst("SYSTEM", "System", "系统", "ApartmentOutlined",
                "装备级系统，对应军工领域的武器系统/火控系统/导航系统，或汽车领域的动力域/底盘域", 15);
        fst("SUBSYSTEM", "Subsystem", "子系统", "BlockOutlined",
                "系统下的子功能模块，如武器系统下的发射子系统、制导子系统", 16);
        fst("CI", "CI", "配置/构型项目", "ControlOutlined",
                "配置项(Configuration Item)，可独立管理、版本控制的构型单元", 17);
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

        SoftTypeMeta(String code, String name, String displayName, String icon, String description, int sortOrder) {
            this.code = code;
            this.name = name;
            this.displayName = displayName;
            this.icon = icon;
            this.description = description;
            this.sortOrder = sortOrder;
        }
    }

    private static void fst(String code, String name, String displayName, String icon, String description, int sortOrder) {
        FUNCTIONAL_SOFT_TYPES.put(code, new SoftTypeMeta(code, name, displayName, icon, description, sortOrder));
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

        // 注册 Functional 实体下的自定义子类型（System、Subsystem、CI）
        ensureFunctionalSoftTypes();
    }

    /**
     * 注册 Functional 实体下的自定义子类型（SOFT_TYPE，source=ootb）。
     */
    private void ensureFunctionalSoftTypes() {
        // 查找 FUNCTIONAL 父类型的 oid
        TypeDefinition functionalParent = mapper.selectByCode("FUNCTIONAL",
                TenantContext.PLATFORM_TENANT_OID, TenantContext.PLATFORM_TENANT_OID);
        if (functionalParent == null) {
            log.warn("未找到 FUNCTIONAL 父类型，跳过 Functional 子类型注册");
            return;
        }

        log.info("开始注册 Functional 子类型 (System/Subsystem/CI)...");
        for (Map.Entry<String, SoftTypeMeta> entry : FUNCTIONAL_SOFT_TYPES.entrySet()) {
            SoftTypeMeta meta = entry.getValue();
            try {
                if (mapper.existsByCode(meta.code, TenantContext.PLATFORM_TENANT_OID,
                        TenantContext.PLATFORM_TENANT_OID) > 0) {
                    log.debug("  - {} (code={}) 已存在，跳过", meta.displayName, meta.code);
                    continue;
                }
                ensureFunctionalSoftType(functionalParent, meta);
                log.info("  √ {} (code={}) 注册成功", meta.displayName, meta.code);
            } catch (Exception e) {
                log.error("  ✗ 注册 Functional 子类型 {} 失败: {}", meta.code, e.getMessage(), e);
            }
        }
        log.info("Functional 子类型注册完成");
    }

    /**
     * 注册单个 Functional 子类型（SOFT_TYPE）。
     */
    private void ensureFunctionalSoftType(TypeDefinition parent, SoftTypeMeta meta) {
        TypeDefinition td = new TypeDefinition(meta.code, meta.name, TypeDefinition.KIND_SOFT_TYPE);
        td.setIcon(meta.icon);
        td.setSource("ootb");
        td.setDescription(meta.description);
        td.setSortOrder(meta.sortOrder);
        td.setTenantOid(TenantContext.PLATFORM_TENANT_OID);
        td.setParentOid(parent.getOid());
        td.setRootTypeCode(parent.getRootTypeCode() != null ? parent.getRootTypeCode() : parent.getCode());
        mapper.insert(td);

        // 继承父类型的规则和模板绑定（FUNCTIONAL 已绑定 PART_NUMBER、LETTER_8、STANDARD）
        bindNumberRule(td, "PART_NUMBER");
        bindVersionRule(td, "LETTER_8");
        bindLifecycleTemplate(td, "STANDARD");
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
