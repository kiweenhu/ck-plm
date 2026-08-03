/*
 * Copyright (c) 2025 深圳乘恺科技有限公司
 * All rights reserved.
 *
 * @author Kiween.Hu; Roney.Liu
 */

package cn.ck.plm.softtype.config;

import cn.ck.plm.document.entity.Document;
import cn.ck.plm.document.entity.DocumentIteration;
import cn.ck.plm.base.entity.BaseEntity;
import cn.ck.plm.part.entity.Part;
import cn.ck.plm.part.entity.PartIteration;
import cn.ck.plm.product.entity.ProductLine;
import cn.ck.plm.product.entity.ProductModel;
import cn.ck.plm.functional.entity.FunctionalEntity;
import cn.ck.plm.functional.entity.FunctionalIteration;
import cn.ck.plm.softtype.entity.AttributeDefinition;
import cn.ck.plm.softtype.service.api.AttributeDefinitionService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;

/**
 * 应用启动时自动扫描 {@link #ENTITY_CODE_MAP} 中指定的 OOTB 实体类，
 * 通过反射发现字段并自动注册属性定义到 ck_attribute_definition 表。
 *
 * <p>在 {@link TypeDefinitionInitializer}（{@code @Order(2)}）之后执行。
 *
 * <h3>扩展方式</h3>
 * 在 {@link #ENTITY_CODE_MAP} 中添加新实体类及其 TypeDefinition code 即可自动扫描注册。
 *
 * <h3>字段属性约定</h3>
 * <ul>
 *   <li>static / transient 字段 → 自动跳过</li>
 *   <li>Boolean / boolean → dataType=BOOLEAN, uiComponent=switch</li>
 *   <li>Integer / int / Long / long → dataType=INTEGER, uiComponent=input-number</li>
 *   <li>Float / double / Double / BigDecimal → dataType=FLOAT</li>
 *   <li>LocalDate → dataType=DATE</li>
 *   <li>LocalDateTime → dataType=DATETIME</li>
 *   <li>String + 字段名=description → uiComponent=textarea</li>
 *   <li>String + 字段名以 Oid 结尾 → uiComponent=select（引用字段）</li>
 *   <li>String + 字段名=parentOid → uiComponent=tree-select</li>
 *   <li>其余 String → uiComponent=input</li>
 *   <li>未在 FIELD_META 中的字段 → 默认 searchable=true, listable=true, editable=true</li>
 * </ul>
 *
 * <p>实体 code 由 {@link #ENTITY_CODE_MAP} 显式配置（如 PRODUCT_LINE、DOCUMENT），与 TypeDefinitionInitializer 保持一致。</p>
 *
 * <p>幂等：已存在的属性不会重复注册（由 {@link AttributeDefinitionService#registerSystemAttributes} 保证）。</p>
 */
@Component
@Order(3) // 在 PageLayoutMigration(1) → TypeDefinitionInitializer(2) 之后执行
public class AttributeInitializer implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(AttributeInitializer.class);

    private final AttributeDefinitionService service;

    // ==================== OOTB 实体类 → TypeDefinition code 映射 ====================

    /** 实体类 → TypeDefinition code 映射，code 必须与 TypeDefinitionInitializer.ENTITY_META 保持一致 */
    private static final Map<Class<? extends BaseEntity>, String> ENTITY_CODE_MAP = new LinkedHashMap<>();
    static {
        ENTITY_CODE_MAP.put(ProductLine.class,  "PRODUCT_LINE");
        ENTITY_CODE_MAP.put(ProductModel.class, "PRODUCT_MODEL");
        ENTITY_CODE_MAP.put(Document.class,     "DOCUMENT");
        ENTITY_CODE_MAP.put(Part.class,         "PART");
        ENTITY_CODE_MAP.put(FunctionalEntity.class, "FUNCTIONAL");
    }

    // ==================== 复合实体扫描配置 ====================

    /**
     * 复合实体的附加类扫描映射。
     * <p>Key = 附加实体类，Value = 目标复合实体 code。
     * 这些类的字段将注册到对应复合实体的属性定义中，而非独立类型。
     * 例如：DocumentIteration 的字段注册到 DOCUMENT 实体名下。</p>
     */
    private static final Map<Class<? extends BaseEntity>, String> COMPOSITE_ENTITY_MAP = new LinkedHashMap<>();
    static {
        composite(DocumentIteration.class, "DOCUMENT");
        composite(PartIteration.class, "PART");
    }

    // ==================== 字段元数据配置 ====================

    /**
     * 字段元数据：displayName, sortOrder, searchable, listable, editable。
     * <p>未在此映射中的字段将使用约定自动推断默认值。</p>
     */
    private static class FieldMeta {
        final String displayName;
        final int sortOrder;
        final boolean searchable;
        final boolean listable;
        final boolean editable;

        FieldMeta(String displayName, int sortOrder,
                  boolean searchable, boolean listable, boolean editable) {
            this.displayName = displayName;
            this.sortOrder = sortOrder;
            this.searchable = searchable;
            this.listable = listable;
            this.editable = editable;
        }
    }

    private static final Map<String, FieldMeta> FIELD_META = new LinkedHashMap<>();
    static {
        // ---------- BaseEntity 审计字段 ----------
        // oid：系统主键，不在 UI 中展示
        fm("oid",          "唯一标识", 91, false, false, false);
        fm("createdAt",    "创建时间", 92, false, false, false);
        fm("creator",      "创建者",   93, false, true,  false);
        fm("updatedAt",    "更新时间", 94, false, false, false);
        fm("updater",      "更新者",   95, false, true,  false);

        // ---------- MasterEntity 通用业务字段 ----------
        fm("code",         "编码",      1, true,  true,  true);
        fm("name",         "名称",      2, true,  true,  true);
        fm("number",       "编号",      3, true,  true,  false);   // 由编号规则自动生成，不可手动修改
        fm("description",  "描述",     60, true,  true,  true);

        // ---------- ProductLine 特有字段 ----------
        fm("thumbnail",    "缩略图",       3, false, true,  true);
        fm("teamOid",      "关联团队",     4, false, true,  true);
        fm("parentOid",    "父产品线",     5, false, true,  true);

        // ---------- Media 特有字段 ----------
        fm("originalName", "原始文件名", 10, false, true, false);
        fm("fileName",     "存储文件名", 11, false, true, false);
        fm("fileSize",     "文件大小",   12, false, true, false);
        fm("mimeType",     "MIME类型",   13, false, true, false);
        fm("storagePath",  "存储路径",   14, false, false, false);
        fm("width",        "宽度(px)",   15, false, true, false);
        fm("height",       "高度(px)",   16, false, true, false);

        // ---------- TeamMember 特有字段 ----------
        fm("roleName",     "角色名称",   10, true,  true,  true);
        fm("userId",       "成员用户ID", 20, true,  true,  true);

        // ---------- MasterEntity 容器字段（Document / Part 通用） ----------
        fm("containerOid",  "所属容器",     30, true,  true,  true);
        fm("containerType", "容器类型",     31, false, true,  true);

        // ---------- Document 特有字段 ----------
        fm("typeDefinitionCode", "类型定义编码", 10, true, true, true);
        fm("folderOid",    "所属文件夹",   40, true,  true,  true);
        fm("stageOid",     "研发阶段",     50, true,  true,  true);

        // ---------- DocumentIteration / IterationEntity 字段 ----------
        fm("masterOid",                     "主文档OID",           60, false, false, false);
        fm("revision",                      "大版本",               61, false, true,  false);
        fm("iteration",                     "小版本号",             62, false, true,  false);
        fm("checkedOut",                    "已检出",               63, false, true,  false);
        fm("checkedOutBy",                  "检出人",               64, false, true,  false);
        fm("checkedOutComment",             "检出注释",             65, false, false, false);
        fm("latest",                        "最新版本",             66, false, true,  false);
        fm("derivedFromOid",                "来源版本OID",          67, false, false, false);
        fm("derivedAt",                     "副本创建时间",         68, false, false, false);
        fm("view",                          "所属视图",             69, false, true,  false);
        fm("status",                        "生命周期状态",         70, false, true,  false);
        fm("lifecycleTemplateIterationOid", "生命周期模板迭代版本", 71, false, false, false);
        fm("versionSort",                   "版本排序",             72, false, false, false);
        fm("branchId",                      "分支ID",              73, false, true,  false);
        fm("deleteMark",                    "删除标记",             74, false, false, false);
        fm("ckfileOid",                     "主文档文件",           75, false, true,  true);

        // ---------- Part 特有字段 ----------
        fm("classificationOid", "分类OID",   20, true,  true,  true);

        // ---------- PartIteration 特有字段 ----------
        fm("unit",    "单位",   76, true,  true,  true);
        fm("source",  "来源",   77, true,  true,  true);
    }

    // ==================== 构造注入 ====================

    public AttributeInitializer(AttributeDefinitionService service) {
        this.service = service;
    }

    // ==================== 启动入口 ====================

    @Override
    public void run(String... args) {
        log.info("开始扫描 OOTB 实体对象并初始化属性定义...");

        int successCount = 0;
        for (Map.Entry<Class<? extends BaseEntity>, String> entry : ENTITY_CODE_MAP.entrySet()) {
            Class<? extends BaseEntity> entityClass = entry.getKey();
            String entityCode = entry.getValue();
            try {
                int count = registerEntityAttributes(entityClass, entityCode);
                if (count > 0) {
                    log.info("  √ {} (code={}): 扫描到 {} 个字段，注册 {} 个新属性",
                            entityClass.getSimpleName(), entityCode, count, count);
                } else {
                    log.info("  - {} (code={}): 属性已全部存在，跳过",
                            entityClass.getSimpleName(), entityCode);
                }
                successCount++;
            } catch (Exception e) {
                log.error("  ✗ 扫描实体 {} 属性失败: {}", entityClass.getSimpleName(), e.getMessage(), e);
            }
        }

        // 扫描复合实体附加类（DocumentIteration → DOCUMENT 等）
        for (Map.Entry<Class<? extends BaseEntity>, String> entry : COMPOSITE_ENTITY_MAP.entrySet()) {
            Class<? extends BaseEntity> entityClass = entry.getKey();
            String targetEntityCode = entry.getValue();
            try {
                int count = registerEntityAttributes(entityClass, targetEntityCode);
                if (count > 0) {
                    log.info("  √ [复合] {} → {}: 扫描到 {} 个字段，注册 {} 个新属性",
                            entityClass.getSimpleName(), targetEntityCode, count, count);
                } else {
                    log.info("  - [复合] {} → {}: 属性已全部存在，跳过",
                            entityClass.getSimpleName(), targetEntityCode);
                }
            } catch (Exception e) {
                log.error("  ✗ [复合] 扫描实体 {} → {} 属性失败: {}",
                        entityClass.getSimpleName(), targetEntityCode, e.getMessage(), e);
            }
        }

        // ===== DOCUMENT + DocumentIteration 复合实体属性完全重建 =====
        reinitDocumentAttributes();
        // ===== PART + PartIteration 复合实体属性完全重建 =====
        reinitPartAttributes();
        // ===== FUNCTIONAL + FunctionalIteration 复合实体属性完全重建 =====
        reinitFunctionalAttributes();
        // ===== ProductLine / ProductModel 属性完全重建 =====
        reinitProductLineAttributes();
        reinitProductModelAttributes();

        log.info("OOTB 实体属性定义初始化完成: {}/{} 实体处理成功", successCount, ENTITY_CODE_MAP.size());
    }

    /** DOCUMENT 复合实体属性重建（先清后建，确保与实体类字段完全一致） */
    private void reinitDocumentAttributes() {
        String entityCode = "DOCUMENT";
        try {
            // 1. 扫描 Document 自身字段
            List<AttributeDefinition> docDefs = scanFields(Document.class);
            // 2. 扫描 DocumentIteration 复合字段
            List<AttributeDefinition> iterDefs = scanFields(DocumentIteration.class);
            // 3. 合并去重（DocumentIteration 字段优先，避免与 Document 的同名字段冲突）
            Set<String> seen = new HashSet<>();
            List<AttributeDefinition> allDefs = new ArrayList<>();
            for (AttributeDefinition def : docDefs) {
                if (seen.add(def.getFieldName())) allDefs.add(def);
            }
            for (AttributeDefinition def : iterDefs) {
                if (seen.add(def.getFieldName())) allDefs.add(def);
            }
            // 4. 重建
            int count = service.reinitSystemAttributes(entityCode, allDefs);
            log.info("  √ [重建] Document + DocumentIteration → DOCUMENT: {} 个属性定义已重建", count);
        } catch (Exception e) {
            log.error("  ✗ [重建] DOCUMENT 属性定义失败: {}", e.getMessage(), e);
        }
    }

    /** PART 复合实体属性重建（先清后建，确保与实体类字段完全一致） */
    private void reinitPartAttributes() {
        String entityCode = "PART";
        try {
            List<AttributeDefinition> partDefs = scanFields(Part.class);
            List<AttributeDefinition> iterDefs = scanFields(PartIteration.class);
            mergeAndReinit(entityCode, partDefs, iterDefs, "Part + PartIteration → PART");
        } catch (Exception e) {
            log.error("  ✗ [重建] PART 属性定义失败: {}", e.getMessage(), e);
        }
    }

    /** SYSTEM 复合实体属性重建（继承 Part 结构） */
    private void reinitFunctionalAttributes() {
        String entityCode = "FUNCTIONAL";
        try {
            List<AttributeDefinition> sysDefs = scanFields(FunctionalEntity.class);
            List<AttributeDefinition> iterDefs = scanFields(FunctionalIteration.class);
            mergeAndReinit(entityCode, sysDefs, iterDefs, "FunctionalEntity + FunctionalIteration → FUNCTIONAL");
        } catch (Exception e) {
            log.error("  ✗ [重建] SYSTEM 属性定义失败: {}", e.getMessage(), e);
        }
    }

    private void mergeAndReinit(String entityCode, List<AttributeDefinition> masterDefs,
                                 List<AttributeDefinition> iterDefs, String label) {
        Set<String> seen = new HashSet<>();
        List<AttributeDefinition> allDefs = new ArrayList<>();
        for (AttributeDefinition def : masterDefs) {
            if (seen.add(def.getFieldName())) allDefs.add(def);
        }
        for (AttributeDefinition def : iterDefs) {
            if (seen.add(def.getFieldName())) allDefs.add(def);
        }
        int count = service.reinitSystemAttributes(entityCode, allDefs);
        log.info("  √ [重建] {}: {} 个属性定义已重建", label, count);
    }

    /** ProductLine 属性重建 */
    private void reinitProductLineAttributes() {
        try {
            List<AttributeDefinition> defs = scanFields(ProductLine.class);
            int count = service.reinitSystemAttributes("PRODUCT_LINE", defs);
            log.info("  √ [重建] ProductLine → PRODUCT_LINE: {} 个属性定义已重建", count);
        } catch (Exception e) {
            log.error("  ✗ [重建] PRODUCT_LINE 属性定义失败: {}", e.getMessage(), e);
        }
    }

    /** ProductModel 属性重建 */
    private void reinitProductModelAttributes() {
        try {
            List<AttributeDefinition> defs = scanFields(ProductModel.class);
            int count = service.reinitSystemAttributes("PRODUCT_MODEL", defs);
            log.info("  √ [重建] ProductModel → PRODUCT_MODEL: {} 个属性定义已重建", count);
        } catch (Exception e) {
            log.error("  ✗ [重建] PRODUCT_MODEL 属性定义失败: {}", e.getMessage(), e);
        }
    }

    // ==================== 实体扫描与注册 ====================

    /**
     * 扫描实体类的所有字段（含继承链），构建属性定义列表并注册。
     *
     * @param entityClass 实体类
     * @param entityCode  对应的类型定义 code（如 PRODUCT_LINE）
     * @return 扫描到的字段总数
     */
    private int registerEntityAttributes(Class<?> entityClass, String entityCode) {
        List<AttributeDefinition> defs = scanFields(entityClass);
        if (defs.isEmpty()) {
            log.warn("  实体 {} 未扫描到任何有效字段", entityClass.getSimpleName());
            return 0;
        }
        service.registerSystemAttributes(entityCode, defs);
        return defs.size();
    }

    /**
     * 递归扫描类继承层次中的所有有效字段。
     * <p>扫描顺序：父类字段在前，子类字段在后，确保 sortOrder 自然递增。</p>
     */
    private List<AttributeDefinition> scanFields(Class<?> entityClass) {
        List<AttributeDefinition> defs = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        scanHierarchy(entityClass, defs, seen, 0);
        return defs;
    }

    /** 递归向上扫描继承链，depth 表示与目标类的层级距离（子类=0，父类=1…） */
    private void scanHierarchy(Class<?> clazz, List<AttributeDefinition> defs,
                               Set<String> seen, int depth) {
        if (clazz == null || clazz == Object.class) return;

        // 先扫描父类（父类字段 sortOrder 排在前）
        scanHierarchy(clazz.getSuperclass(), defs, seen, depth + 1);

        int sortBase = depth * 100;
        int idx = 0;

        for (Field field : clazz.getDeclaredFields()) {
            int mod = field.getModifiers();

            // 跳过 static、transient、合成字段
            if (Modifier.isStatic(mod) || Modifier.isTransient(mod) || field.isSynthetic()) {
                continue;
            }

            String fieldName = field.getName();

            // 去重：子类可能声明与父类同名字段（field hiding）
            if (!seen.add(fieldName)) continue;

            // 构建属性定义
            AttributeDefinition def = buildDefinition(field, sortBase + idx);
            defs.add(def);
            idx++;
        }
    }

    // ==================== 属性定义构建 ====================

    /** 根据 field 反射信息和 FIELD_META 配置构建一条 AttributeDefinition */
    private AttributeDefinition buildDefinition(Field field, int sortOrder) {
        String fieldName = field.getName();
        Class<?> javaType = field.getType();

        // 数据类型推断
        String dataType = inferDataType(javaType);

        // 字段元数据（优先使用配置，其次使用约定默认值）
        FieldMeta meta = FIELD_META.get(fieldName);
        String displayName;
        boolean searchable, listable, editable;

        if (meta != null) {
            displayName = meta.displayName;
            searchable   = meta.searchable;
            listable     = meta.listable;
            editable     = meta.editable;
            // 若配置了 sortOrder，使用配置值
            sortOrder = meta.sortOrder;
        } else {
            displayName = camelToHuman(fieldName);
            searchable  = true;
            listable    = true;
            editable    = !isAuditLike(fieldName);
        }

        // UI 组件推断
        String uiComponent = inferUiComponent(fieldName, javaType);

        AttributeDefinition def = new AttributeDefinition();
        def.setFieldName(fieldName);
        def.setDisplayName(displayName);
        def.setDataType(dataType);
        def.setSource("FUNCTIONAL");
        def.setRequired("code".equals(fieldName) || "name".equals(fieldName));
        def.setSearchable(searchable);
        def.setListable(listable);
        def.setEditable(editable);
        def.setUiComponent(uiComponent);
        def.setSortOrder(sortOrder);
        def.setEnabled(true);
        return def;
    }

    // ==================== 类型推断 ====================

    /** Java 类型 → PLM 数据类型 */
    private static String inferDataType(Class<?> javaType) {
        if (javaType == String.class)                return "STRING";
        if (javaType == Integer.class || javaType == int.class)    return "INTEGER";
        if (javaType == Long.class    || javaType == long.class)   return "INTEGER";
        if (javaType == Float.class   || javaType == float.class)  return "FLOAT";
        if (javaType == Double.class  || javaType == double.class) return "FLOAT";
        if (javaType == Boolean.class || javaType == boolean.class) return "BOOLEAN";
        if (javaType == LocalDate.class)             return "DATE";
        if (javaType == LocalDateTime.class)         return "DATETIME";
        if (javaType == java.math.BigDecimal.class)  return "FLOAT";
        return "STRING"; // 默认回退
    }

    /** 根据字段名和 Java 类型推断最佳 UI 组件 */
    private static String inferUiComponent(String fieldName, Class<?> javaType) {
        // 布尔类型 → switch
        if (javaType == Boolean.class || javaType == boolean.class) {
            return "switch";
        }
        // 数值类型 → input-number
        if (javaType == Integer.class || javaType == int.class
                || javaType == Long.class || javaType == long.class
                || javaType == Float.class || javaType == float.class
                || javaType == Double.class || javaType == double.class
                || javaType == java.math.BigDecimal.class) {
            return "input-number";
        }
        // 日期时间 → datepicker
        if (javaType == LocalDate.class || javaType == LocalDateTime.class) {
            return "datepicker";
        }
        // String 特殊字段名
        if ("description".equalsIgnoreCase(fieldName)) {
            return "textarea";
        }
        if ("parentOid".equalsIgnoreCase(fieldName)) {
            return "tree-select";
        }
        if (fieldName.toLowerCase().endsWith("oid")) {
            return "select";
        }
        return "input";
    }

    // ==================== 辅助方法 ====================

    /** 将 camelCase 字段名转换为人类可读的中文映射（用于 FIELD_META 未覆盖的回退） */
    private static String camelToHuman(String camelCase) {
        String spaced = camelCase.replaceAll("([a-z])([A-Z])", "$1 $2");
        return spaced.substring(0, 1).toUpperCase() + spaced.substring(1);
    }

    /** 判断字段是否为类审计字段（createdAt / updatedAt / creator / updater / oid） */
    private static boolean isAuditLike(String fieldName) {
        return "createdAt".equalsIgnoreCase(fieldName)
                || "updatedAt".equalsIgnoreCase(fieldName)
                || "creator".equalsIgnoreCase(fieldName)
                || "updater".equalsIgnoreCase(fieldName)
                || "oid".equalsIgnoreCase(fieldName);
    }

    // ==================== 内部工具 ====================

    private static void fm(String fieldName, String displayName, int sortOrder,
                           boolean searchable, boolean listable, boolean editable) {
        FIELD_META.put(fieldName, new FieldMeta(displayName, sortOrder, searchable, listable, editable));
    }

    private static void composite(Class<? extends BaseEntity> entityClass, String targetEntityCode) {
        COMPOSITE_ENTITY_MAP.put(entityClass, targetEntityCode);
    }
}
