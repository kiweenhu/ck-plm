/*
 * Copyright (c) 2026~2028 深圳乘恺科技有限公司
 * All rights reserved.
 *
 * @author Kiween.Hu; Roney.Liu
 */

package cn.ck.plm.softtype.config;

import cn.ck.plm.base.util.TenantContext;
import cn.ck.plm.softtype.entity.PageLayout;
import cn.ck.plm.softtype.mapper.PageLayoutMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * 应用启动时自动为 OOTB 实体创建默认页面布局。
 *
 * <p>幂等：已存在的布局（根据 entity_code + operation_code 判断）不会重复插入。
 *
 * <p>执行顺序：在 TypeDefinitionInitializer(@Order=2) 之后，确保实体类型定义已存在。
 */
@Component
@Order(4)
public class PageLayoutInitializer implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(PageLayoutInitializer.class);

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private PageLayoutMapper pageLayoutMapper;

    @Override
    public void run(String... args) {
        log.info("开始初始化 OOTB 默认页面布局...");
        // existing 计数的是"已存在、幂等跳过"，不是失败 —— 早前把它叫 failed，
        // 于是每次启动日志都在喊"失败 12 个"，白白吓人一跳（真实的失败由 ensureLayout 内的 warn/error 报出）。
        int inserted = 0, existing = 0;

        try {
            // ==== PRODUCT_LINE: list ====
            if (ensureLayout("PRODUCT_LINE", "list", "产品系列列表", buildProductLineListLayout())) {
                inserted++;
            } else {
                existing++;
            }

            // ==== PRODUCT_LINE: create ====
            if (ensureLayout("PRODUCT_LINE", "create", "新建产品系列", buildProductLineCreateLayout())) {
                inserted++;
            } else {
                existing++;
            }

            // ==== PRODUCT_LINE: update ====
            if (ensureLayout("PRODUCT_LINE", "update", "编辑产品系列", buildProductLineUpdateLayout())) {
                inserted++;
            } else {
                existing++;
            }

            // ==== PRODUCT_LINE: detail ====
            if (ensureLayout("PRODUCT_LINE", "detail", "产品系列详情", buildProductLineDetailLayout())) {
                inserted++;
            } else {
                existing++;
            }

            // ==== PRODUCT_MODEL: list ====
            if (ensureLayout("PRODUCT_MODEL", "list", "产品型号列表", buildProductModelListLayout())) {
                inserted++;
            } else {
                existing++;
            }

            // ==== PRODUCT_MODEL: create ====
            if (ensureLayout("PRODUCT_MODEL", "create", "新建产品型号", buildProductModelCreateLayout())) {
                inserted++;
            } else {
                existing++;
            }

            // ==== PRODUCT_MODEL: update ====
            if (ensureLayout("PRODUCT_MODEL", "update", "编辑产品型号", buildProductModelUpdateLayout())) {
                inserted++;
            } else {
                existing++;
            }

            // ==== PRODUCT_MODEL: detail ====
            if (ensureLayout("PRODUCT_MODEL", "detail", "产品型号详情", buildProductModelDetailLayout())) {
                inserted++;
            } else {
                existing++;
            }

            // ==== DOCUMENT: list ====
            if (ensureLayout("DOCUMENT", "list", "文档列表", buildDocumentListLayout())) {
                inserted++;
            } else {
                existing++;
            }

            // ==== DOCUMENT: create ====
            if (ensureLayout("DOCUMENT", "create", "新建文档", buildDocumentCreateLayout())) {
                inserted++;
            } else {
                existing++;
            }

            // ==== DOCUMENT: update ====
            if (ensureLayout("DOCUMENT", "update", "编辑文档", buildDocumentUpdateLayout())) {
                inserted++;
            } else {
                existing++;
            }

            // ==== DOCUMENT: detail ====
            if (ensureLayout("DOCUMENT", "detail", "文档详情", buildDocumentDetailLayout())) {
                inserted++;
            } else {
                existing++;
            }

            // ==== PART: list / create / update / detail ====
            // PART 是「零部件家族」的模板根：子类型（结构件 / 标准件 / 通用件 / 电子元器件 / PCBA /
            // 电气件 / 软件）自己没有属性定义（属性由能力宿主 PART 解析而来），前端 DynamicForm 的
            // fallbackEntityCode 也指向它 —— PART 没有布局，这些表单就全是空的。
            if (ensureLayout("PART", "list", "零部件列表", buildPartListLayout())) {
                inserted++;
            } else {
                existing++;
            }

            // ==== PART: create ====
            if (ensureLayout("PART", "create", "新建零部件", buildPartCreateLayout())) {
                inserted++;
            } else {
                existing++;
            }

            // ==== PART: update ====
            if (ensureLayout("PART", "update", "编辑零部件", buildPartUpdateLayout())) {
                inserted++;
            } else {
                existing++;
            }

            // ==== PART: detail ====
            if (ensureLayout("PART", "detail", "零部件详情", buildPartDetailLayout())) {
                inserted++;
            } else {
                existing++;
            }

            // ==== Part 子类型继承 PART 刚刚确定的布局 ====
            clonePartLayoutToSoftTypes();

        } catch (Exception e) {
            log.error("初始化页面布局失败: {}", e.getMessage(), e);
            return;
        }

        log.info("OOTB 默认页面布局初始化完成: 新增 {} 个, 已存在 {} 个", inserted, existing);
    }

    /**
     * 注册一条页面布局记录。先在 ck_type_definition 中查找 entity_oid，然后插入 ck_type_page_layout。
     *
     * <p><b>幂等口径是 entity_code + operation_code</b>（与类注释一致）。早期实现按
     * {@code entity_oid + operation_code} 判断 —— 类型定义行的 oid 一旦变更（历史上整批重建过），
     * 旧布局就"看起来不存在"，于是再插一份：同一实体同一操作出现<b>两套布局</b>，
     * 其中一套挂在已不存在的类型 oid 上（界面按当前 oid 查，僵尸行永远读不到，只积垃圾）。
     * 现在查到旧 oid 的行会<b>原地改挂到当前 oid</b>（自愈），不再产生重复。
     *
     * @return true 表示新插入，false 表示已存在跳过、已自愈改挂或实体类型未找到
     */
    private boolean ensureLayout(String entityCode, String operationCode, String operationName, String layoutJson) {
        // 1. 查找 entity_oid（同一 code 有平台行 + 租户覆盖行时取"当前租户优先"的一条，
        //    避免 queryForObject 因多行直接抛异常、被当成"找不到类型"）
        String findOidSql = "SELECT oid FROM ck_type_definition WHERE code = ? "
                + "ORDER BY CASE WHEN tenant_oid = ? THEN 0 ELSE 1 END LIMIT 1";
        String entityOid;
        try {
            entityOid = jdbcTemplate.queryForObject(findOidSql, String.class, entityCode,
                    TenantContext.get());
        } catch (Exception e) {
            log.warn("  ✗ 未找到实体类型定义: {}", entityCode);
            return false;
        }

        if (entityOid == null || entityOid.isEmpty()) {
            log.warn("  ✗ 实体 {} 的 OID 为空", entityCode);
            return false;
        }

        // 2. 幂等 + 自愈：按 entity_code + operation_code 找既有布局；
        //    若它的 entity_oid 已失效（指向不存在的类型），原地改挂到当前 oid，而不是再插第二份
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                "SELECT oid, entity_oid FROM ck_type_page_layout WHERE entity_code = ? AND operation_code = ?",
                entityCode, operationCode);
        if (!rows.isEmpty()) {
            Object rowEntityOid = rows.get(0).get("entity_oid");
            if (rowEntityOid != null && !entityOid.equals(rowEntityOid.toString())) {
                jdbcTemplate.update(
                        "UPDATE ck_type_page_layout SET entity_oid = ?, updated_at = now() WHERE oid = ?",
                        entityOid, rows.get(0).get("oid").toString());
                log.info("  √ {}/{} 布局已自愈：entity_oid 由 {}… 改挂到当前类型 {}…",
                        entityCode, operationCode,
                        rowEntityOid.toString().substring(0, 8), entityOid.substring(0, 8));
                return false;
            }
            log.debug("  - {}/{} 已存在，跳过", entityCode, operationCode);
            return false;
        }

        // 3. 插入新记录，归属平台租户
        PageLayout layout = new PageLayout(entityOid, entityCode, operationCode,
                operationName, layoutJson);
        layout.setTenantOid(TenantContext.PLATFORM_TENANT_OID);
        pageLayoutMapper.insert(layout);
        log.info("  √ {}/{} (oid={}, tenantOid={})", entityCode, operationCode, layout.getOid(), layout.getTenantOid());
        return true;
    }

    /**
     * 将 PART 的布局复制到其子类型（结构件 / 标准件 / 通用件 / 电子元器件 / PCBA / 电气件 / 软件）。
     *
     * <p>子类型没有自己的属性定义（属性由能力宿主 PART 解析而来），复制一份让每种类型在
     * 页面设计器里都有可直接改的布局，省去逐类型手建的工作量。
     *
     * <p>目标类型按「能力宿主 = PART」推导（root_type_code），因此新增的 Part 子类型
     * 会自动纳入，无需再改这里的清单。
     *
     * <p>不含 FUNCTIONAL：它有自己的一套属性（无 unit / source 等物料字段），
     * 复制 PART 布局会得到字段对不上的表单，应由它自己的布局模板提供。
     *
     * <p>幂等口径与 {@link #ensureLayout} 一致：entity_code + operation_code 已存在即跳过 ——
     * 历史上类型定义整批重建过（oid 变更），按 oid 判断会误判为"不存在"而插入重复布局。
     */
    private void clonePartLayoutToSoftTypes() {
        try {
            // 1. 查询 PART 当前的布局定义
            List<Map<String, Object>> partLayouts = jdbcTemplate.queryForList(
                    "SELECT operation_code, operation_name, layout_json::text AS layout_json " +
                    "FROM ck_type_page_layout WHERE entity_code = 'PART' ORDER BY operation_code");
            if (partLayouts.isEmpty()) {
                log.warn("未找到 PART 布局，跳过 Part 子类型布局复制");
                return;
            }

            // 2. 查询 Part 的子类型（能力宿主 = PART）
            List<Map<String, Object>> softTypes = jdbcTemplate.queryForList(
                    "SELECT oid, code FROM ck_type_definition " +
                    "WHERE root_type_code = 'PART' AND code <> 'PART' AND tenant_oid = ? " +
                    "ORDER BY sort_order",
                    TenantContext.PLATFORM_TENANT_OID);

            int copied = 0, skipped = 0;
            for (Map<String, Object> softType : softTypes) {
                String softTypeOid = (String) softType.get("oid");
                String softTypeCode = (String) softType.get("code");
                for (Map<String, Object> partLayout : partLayouts) {
                    String opCode = (String) partLayout.get("operation_code");
                    String opName = (String) partLayout.get("operation_name");
                    String layoutJson = (String) partLayout.get("layout_json");

                    // 幂等：按 entity_code + operation_code 判断（类型 oid 变更过也不会重复插）
                    Integer count = jdbcTemplate.queryForObject(
                            "SELECT COUNT(*) FROM ck_type_page_layout WHERE entity_code = ? AND operation_code = ?",
                            Integer.class, softTypeCode, opCode);
                    if (count != null && count > 0) {
                        skipped++;
                        continue;
                    }

                    PageLayout layout = new PageLayout(softTypeOid, softTypeCode, opCode, opName, layoutJson);
                    layout.setTenantOid(TenantContext.PLATFORM_TENANT_OID);
                    pageLayoutMapper.insert(layout);
                    copied++;
                    log.info("  √ {}/{} 布局已从 PART 复制", softTypeCode, opCode);
                }
            }
            log.info("Part 子类型布局复制完成: 复制 {} 个, 跳过 {} 个", copied, skipped);
        } catch (Exception e) {
            log.warn("复制 PART 布局到子类型失败: {}", e.getMessage());
        }
    }

    // ==================== 布局 JSON 模板 ====================

    private String buildProductLineListLayout() {
        return "{" +
            "\"search\": {" +
                "\"enabled\": true," +
                "\"fields\": [" +
                    "{" +
                        "\"fieldName\": \"code\"," +
                        "\"label\": \"编码\"," +
                        "\"uiComponent\": \"input\"," +
                        "\"placeholder\": \"搜索编码\"" +
                    "}," +
                    "{" +
                        "\"fieldName\": \"name\"," +
                        "\"label\": \"名称\"," +
                        "\"uiComponent\": \"input\"," +
                        "\"placeholder\": \"搜索名称\"" +
                    "}," +
                    "{" +
                        "\"fieldName\": \"description\"," +
                        "\"label\": \"描述\"," +
                        "\"uiComponent\": \"input\"," +
                        "\"placeholder\": \"搜索描述\"" +
                    "}" +
                "]" +
            "}," +
            "\"table\": {" +
                "\"enabled\": true," +
                "\"toolbarEnabled\": true," +
                "\"toolbar\": [\"create\", \"export\"]," +
                "\"hasEdit\": true," +
                "\"hasDelete\": true," +
                "\"columns\": [" +
                    "{" +
                        "\"fieldName\": \"code\"," +
                        "\"label\": \"编码\"," +
                        "\"width\": 130," +
                        "\"sortable\": false" +
                    "}," +
                    "{" +
                        "\"fieldName\": \"name\"," +
                        "\"label\": \"名称\"," +
                        "\"width\": 160," +
                        "\"sortable\": false" +
                    "}," +
                    "{" +
                        "\"fieldName\": \"description\"," +
                        "\"label\": \"描述\"," +
                        "\"sortable\": false" +
                    "}," +
                    "{" +
                        "\"fieldName\": \"childrenCount\"," +
                        "\"label\": \"子系列\"," +
                        "\"width\": 90," +
                        "\"sortable\": false" +
                    "}," +
                    "{" +
                        "\"fieldName\": \"parent\"," +
                        "\"label\": \"父级\"," +
                        "\"width\": 130," +
                        "\"sortable\": false" +
                    "}," +
                    "{" +
                        "\"fieldName\": \"thumbnail\"," +
                        "\"label\": \"缩略图\"," +
                        "\"width\": 100," +
                        "\"sortable\": false" +
                    "}," +
                    "{" +
                        "\"fieldName\": \"team\"," +
                        "\"label\": \"团队\"," +
                        "\"width\": 90," +
                        "\"sortable\": false" +
                    "}," +
                    "{" +
                        "\"fieldName\": \"createdAt\"," +
                        "\"label\": \"创建时间\"," +
                        "\"width\": 170," +
                        "\"sortable\": false" +
                    "}," +
                    "{" +
                        "\"fieldName\": \"action\"," +
                        "\"label\": \"操作\"," +
                        "\"width\": 150," +
                        "\"sortable\": false," +
                        "\"fixed\": \"right\"" +
                    "}" +
                "]" +
            "}," +
            "\"form\": {" +
                "\"enabled\": true," +
                "\"name\": \"编辑表单\"," +
                "\"fields\": []" +
            "}" +
        "}";
    }

    private String buildProductLineCreateLayout() {
        return "{" +
            "\"form\": {" +
                "\"fields\": [" +
                    "{" +
                        "\"id\": \"fld-code\"," +
                        "\"fieldName\": \"code\"," +
                        "\"label\": \"编码\"," +
                        "\"uiComponent\": \"input\"," +
                        "\"required\": true," +
                        "\"placeholder\": \"请输入产品线编码（唯一标识）\"" +
                    "}," +
                    "{" +
                        "\"id\": \"fld-name\"," +
                        "\"fieldName\": \"name\"," +
                        "\"label\": \"名称\"," +
                        "\"uiComponent\": \"input\"," +
                        "\"required\": true," +
                        "\"placeholder\": \"请输入产品线名称\"" +
                    "}," +
                    "{" +
                        "\"id\": \"fld-desc\"," +
                        "\"fieldName\": \"description\"," +
                        "\"label\": \"描述\"," +
                        "\"uiComponent\": \"textarea\"," +
                        "\"rows\": 3," +
                        "\"placeholder\": \"请输入产品线描述（可选）\"" +
                    "}," +
                    "{" +
                        "\"id\": \"fld-parent\"," +
                        "\"fieldName\": \"parentOid\"," +
                        "\"label\": \"所属产品系列\"," +
                        "\"uiComponent\": \"product-line-select\"," +
                        "\"placeholder\": \"请选择所属产品系列（可选）\"" +
                    "}," +
                    "{" +
                        "\"id\": \"fld-thumbnail\"," +
                        "\"fieldName\": \"thumbnail\"," +
                        "\"label\": \"缩略图\"," +
                        "\"uiComponent\": \"image-upload\"," +
                        "\"required\": false," +
                        "\"placeholder\": \"请上传产品系列缩略图\"" +
                    "}" +
                "]" +
            "}" +
        "}";
    }

    private String buildProductLineUpdateLayout() {
        return "{" +
            "\"form\": {" +
                "\"fields\": [" +
                    "{" +
                        "\"id\": \"fld-code\"," +
                        "\"fieldName\": \"code\"," +
                        "\"label\": \"编码\"," +
                        "\"uiComponent\": \"input\"," +
                        "\"required\": true," +
                        "\"readonly\": true," +
                        "\"placeholder\": \"编码不可修改\"" +
                    "}," +
                    "{" +
                        "\"id\": \"fld-name\"," +
                        "\"fieldName\": \"name\"," +
                        "\"label\": \"名称\"," +
                        "\"uiComponent\": \"input\"," +
                        "\"required\": true," +
                        "\"placeholder\": \"请输入产品线名称\"" +
                    "}," +
                    "{" +
                        "\"id\": \"fld-desc\"," +
                        "\"fieldName\": \"description\"," +
                        "\"label\": \"描述\"," +
                        "\"uiComponent\": \"textarea\"," +
                        "\"rows\": 3," +
                        "\"placeholder\": \"请输入产品线描述（可选）\"" +
                    "}," +
                    "{" +
                        "\"id\": \"fld-parent\"," +
                        "\"fieldName\": \"parentOid\"," +
                        "\"label\": \"所属产品系列\"," +
                        "\"uiComponent\": \"product-line-select\"," +
                        "\"placeholder\": \"请选择所属产品系列（可选）\"" +
                    "}," +
                    "{" +
                        "\"id\": \"fld-thumbnail\"," +
                        "\"fieldName\": \"thumbnail\"," +
                        "\"label\": \"缩略图\"," +
                        "\"uiComponent\": \"image-upload\"," +
                        "\"required\": false," +
                        "\"placeholder\": \"请上传产品系列缩略图\"" +
                    "}" +
                "]" +
            "}" +
        "}";
    }

    // ==================== PRODUCT_MODEL 布局 JSON 模板 ====================

    private String buildProductModelListLayout() {
        return "{" +
            "\"search\": {" +
                "\"enabled\": true," +
                "\"fields\": [" +
                    "{" +
                        "\"fieldName\": \"code\"," +
                        "\"label\": \"编码\"," +
                        "\"uiComponent\": \"input\"," +
                        "\"placeholder\": \"搜索编码\"" +
                    "}," +
                    "{" +
                        "\"fieldName\": \"name\"," +
                        "\"label\": \"名称\"," +
                        "\"uiComponent\": \"input\"," +
                        "\"placeholder\": \"搜索名称\"" +
                    "}," +
                    "{" +
                        "\"fieldName\": \"description\"," +
                        "\"label\": \"描述\"," +
                        "\"uiComponent\": \"input\"," +
                        "\"placeholder\": \"搜索描述\"" +
                    "}" +
                "]" +
            "}," +
            "\"table\": {" +
                "\"enabled\": true," +
                "\"toolbarEnabled\": true," +
                "\"toolbar\": [\"create\", \"export\"]," +
                "\"hasEdit\": true," +
                "\"hasDelete\": true," +
                "\"columns\": [" +
                    "{" +
                        "\"fieldName\": \"code\"," +
                        "\"label\": \"编码\"," +
                        "\"width\": 130," +
                        "\"sortable\": false" +
                    "}," +
                    "{" +
                        "\"fieldName\": \"name\"," +
                        "\"label\": \"名称\"," +
                        "\"width\": 160," +
                        "\"sortable\": false" +
                    "}," +
                    "{" +
                        "\"fieldName\": \"description\"," +
                        "\"label\": \"描述\"," +
                        "\"sortable\": false" +
                    "}," +
                    "{" +
                        "\"fieldName\": \"productLine\"," +
                        "\"label\": \"所属系列\"," +
                        "\"width\": 140," +
                        "\"sortable\": false" +
                    "}," +
                    "{" +
                        "\"fieldName\": \"thumbnail\"," +
                        "\"label\": \"缩略图\"," +
                        "\"width\": 100," +
                        "\"sortable\": false" +
                    "}," +
                    "{" +
                        "\"fieldName\": \"team\"," +
                        "\"label\": \"团队\"," +
                        "\"width\": 90," +
                        "\"sortable\": false" +
                    "}," +
                    "{" +
                        "\"fieldName\": \"createdAt\"," +
                        "\"label\": \"创建时间\"," +
                        "\"width\": 170," +
                        "\"sortable\": false" +
                    "}," +
                    "{" +
                        "\"fieldName\": \"action\"," +
                        "\"label\": \"操作\"," +
                        "\"width\": 150," +
                        "\"sortable\": false," +
                        "\"fixed\": \"right\"" +
                    "}" +
                "]" +
            "}," +
            "\"form\": {" +
                "\"enabled\": true," +
                "\"name\": \"编辑表单\"," +
                "\"fields\": []" +
            "}" +
        "}";
    }

    private String buildProductModelCreateLayout() {
        return "{" +
            "\"form\": {" +
                "\"fields\": [" +
                    "{" +
                        "\"id\": \"fld-code\"," +
                        "\"fieldName\": \"code\"," +
                        "\"label\": \"编码\"," +
                        "\"uiComponent\": \"input\"," +
                        "\"required\": true," +
                        "\"placeholder\": \"请输入产品型号编码（唯一标识）\"" +
                    "}," +
                    "{" +
                        "\"id\": \"fld-name\"," +
                        "\"fieldName\": \"name\"," +
                        "\"label\": \"名称\"," +
                        "\"uiComponent\": \"input\"," +
                        "\"required\": true," +
                        "\"placeholder\": \"请输入产品型号名称\"" +
                    "}," +
                    "{" +
                        "\"id\": \"fld-desc\"," +
                        "\"fieldName\": \"description\"," +
                        "\"label\": \"描述\"," +
                        "\"uiComponent\": \"textarea\"," +
                        "\"rows\": 3," +
                        "\"placeholder\": \"请输入产品型号描述（可选）\"" +
                    "}," +
                    "{" +
                        "\"id\": \"fld-parentOid\"," +
                        "\"fieldName\": \"parentOid\"," +
                        "\"label\": \"所属产品系列\"," +
                        "\"uiComponent\": \"product-line-select\"," +
                        "\"required\": true," +
                        "\"placeholder\": \"请选择所属产品系列\"" +
                    "}," +
                    "{" +
                        "\"id\": \"fld-thumbnail\"," +
                        "\"fieldName\": \"thumbnail\"," +
                        "\"label\": \"缩略图\"," +
                        "\"uiComponent\": \"image-upload\"," +
                        "\"required\": false," +
                        "\"placeholder\": \"请上传产品型号缩略图\"" +
                    "}" +
                "]" +
            "}" +
        "}";
    }

    private String buildProductModelUpdateLayout() {
        return "{" +
            "\"form\": {" +
                "\"fields\": [" +
                    "{" +
                        "\"id\": \"fld-code\"," +
                        "\"fieldName\": \"code\"," +
                        "\"label\": \"编码\"," +
                        "\"uiComponent\": \"input\"," +
                        "\"required\": true," +
                        "\"readonly\": true," +
                        "\"placeholder\": \"编码不可修改\"" +
                    "}," +
                    "{" +
                        "\"id\": \"fld-name\"," +
                        "\"fieldName\": \"name\"," +
                        "\"label\": \"名称\"," +
                        "\"uiComponent\": \"input\"," +
                        "\"required\": true," +
                        "\"placeholder\": \"请输入产品型号名称\"" +
                    "}," +
                    "{" +
                        "\"id\": \"fld-desc\"," +
                        "\"fieldName\": \"description\"," +
                        "\"label\": \"描述\"," +
                        "\"uiComponent\": \"textarea\"," +
                        "\"rows\": 3," +
                        "\"placeholder\": \"请输入产品型号描述（可选）\"" +
                    "}," +
                    "{" +
                        "\"id\": \"fld-parentOid\"," +
                        "\"fieldName\": \"parentOid\"," +
                        "\"label\": \"所属产品系列\"," +
                        "\"uiComponent\": \"product-line-select\"," +
                        "\"required\": true," +
                        "\"placeholder\": \"请选择所属产品系列\"" +
                    "}," +
                    "{" +
                        "\"id\": \"fld-thumbnail\"," +
                        "\"fieldName\": \"thumbnail\"," +
                        "\"label\": \"缩略图\"," +
                        "\"uiComponent\": \"image-upload\"," +
                        "\"required\": false," +
                        "\"placeholder\": \"请上传产品型号缩略图\"" +
                    "}" +
                "]" +
            "}" +
        "}";
    }

    // ==================== DOCUMENT 布局 JSON 模板 ====================

    private String buildDocumentListLayout() {
        return "{" +
            "\"search\": {" +
                "\"enabled\": true," +
                "\"fields\": [" +
                    "{" +
                        "\"fieldName\": \"name\"," +
                        "\"label\": \"文档名称\"," +
                        "\"uiComponent\": \"input\"," +
                        "\"placeholder\": \"搜索文档名称\"" +
                    "}," +
                    "{" +
                        "\"fieldName\": \"number\"," +
                        "\"label\": \"编号\"," +
                        "\"uiComponent\": \"input\"," +
                        "\"placeholder\": \"搜索编号\"" +
                    "}," +
                    "{" +
                        "\"fieldName\": \"typeDefinitionCode\"," +
                        "\"label\": \"文档类型\"," +
                        "\"uiComponent\": \"select\"," +
                        "\"placeholder\": \"筛选文档类型\"" +
                    "}," +
                    "{" +
                        "\"fieldName\": \"stageOid\"," +
                        "\"label\": \"所属研发阶段\"," +
                        "\"uiComponent\": \"select\"," +
                        "\"placeholder\": \"筛选阶段\"" +
                    "}" +
                "]" +
            "}," +
            "\"table\": {" +
                "\"enabled\": true," +
                "\"toolbarEnabled\": true," +
                "\"toolbar\": [\"create\", \"export\"]," +
                "\"hasEdit\": true," +
                "\"hasDelete\": true," +
                "\"columns\": [" +
                    "{" +
                        "\"fieldName\": \"name\"," +
                        "\"label\": \"名称\"," +
                        "\"width\": 160," +
                        "\"sortable\": false" +
                    "}," +
                    "{" +
                        "\"fieldName\": \"number\"," +
                        "\"label\": \"编号\"," +
                        "\"width\": 130," +
                        "\"sortable\": false" +
                    "}," +
                    "{" +
                        "\"fieldName\": \"typeDefinitionCode\"," +
                        "\"label\": \"类型\"," +
                        "\"width\": 90," +
                        "\"sortable\": false" +
                    "}," +
                    "{" +
                        "\"fieldName\": \"stageOid\"," +
                        "\"label\": \"阶段\"," +
                        "\"width\": 100," +
                        "\"sortable\": false" +
                    "}," +
                    "{" +
                        "\"fieldName\": \"location\"," +
                        "\"label\": \"位置\"," +
                        "\"width\": 120," +
                        "\"sortable\": false" +
                    "}," +
                    "{" +
                        "\"fieldName\": \"revision\"," +
                        "\"label\": \"版本\"," +
                        "\"width\": 80," +
                        "\"sortable\": false" +
                    "}," +
                    "{" +
                        "\"fieldName\": \"fileName\"," +
                        "\"label\": \"文件\"," +
                        "\"width\": 140," +
                        "\"sortable\": false" +
                    "}," +
                    "{" +
                        "\"fieldName\": \"createdAt\"," +
                        "\"label\": \"创建时间\"," +
                        "\"width\": 170," +
                        "\"sortable\": false" +
                    "}," +
                    "{" +
                        "\"fieldName\": \"action\"," +
                        "\"label\": \"操作\"," +
                        "\"width\": 150," +
                        "\"sortable\": false," +
                        "\"fixed\": \"right\"" +
                    "}" +
                "]" +
            "}," +
            "\"form\": {" +
                "\"enabled\": true," +
                "\"name\": \"编辑表单\"," +
                "\"fields\": []" +
            "}" +
        "}";
    }

    private String buildDocumentCreateLayout() {
        return "{" +
            "\"form\": {" +
                "\"fields\": [" +
                    "{" +
                        "\"id\": \"fld-number\"," +
                        "\"fieldName\": \"number\"," +
                        "\"label\": \"文档编号\"," +
                        "\"uiComponent\": \"input\"," +
                        "\"readonly\": true," +
                        "\"placeholder\": \"系统根据编码规则自动生成\"" +
                    "}," +
                    "{" +
                        "\"id\": \"fld-name\"," +
                        "\"fieldName\": \"name\"," +
                        "\"label\": \"文档名称\"," +
                        "\"uiComponent\": \"input\"," +
                        "\"required\": true," +
                        "\"placeholder\": \"请输入文档名称\"" +
                    "}," +
                    "{" +
                        "\"id\": \"fld-doctype\"," +
                        "\"fieldName\": \"typeDefinitionCode\"," +
                        "\"label\": \"文档类型\"," +
                        "\"uiComponent\": \"document-type-select\"," +
                        "\"required\": false," +
                        "\"placeholder\": \"选择文档类型（TypeDefinition）\"" +
                    "}," +
                    "{" +
                        "\"id\": \"fld-desc\"," +
                        "\"fieldName\": \"description\"," +
                        "\"label\": \"描述\"," +
                        "\"uiComponent\": \"textarea\"," +
                        "\"rows\": 3," +
                        "\"placeholder\": \"请输入文档描述\"" +
                    "}," +
                    "{" +
                        "\"id\": \"fld-stage\"," +
                        "\"fieldName\": \"stageOid\"," +
                        "\"label\": \"所属研发阶段\"," +
                        "\"uiComponent\": \"stage-select\"," +
                        "\"required\": true," +
                        "\"placeholder\": \"请选择所属研发阶段\"" +
                    "}," +
                    "{" +
                        "\"id\": \"fld-folder\"," +
                        "\"fieldName\": \"folderOid\"," +
                        "\"label\": \"所属文件夹\"," +
                        "\"uiComponent\": \"input\"," +
                        "\"readonly\": true," +
                        "\"placeholder\": \"由当前文件夹自动填充\"" +
                    "}," +
                    "{" +
                        "\"id\": \"fld-owner\"," +
                        "\"fieldName\": \"ownerOid\"," +
                        "\"label\": \"所属产品\"," +
                        "\"uiComponent\": \"product-select\"," +
                        "\"required\": true," +
                        "\"placeholder\": \"选择所属产品\"" +
                    "}," +
                    "{" +
                        "\"id\": \"fld-mainfile\"," +
                        "\"fieldName\": \"ckfileOid\"," +
                        "\"label\": \"主文档\"," +
                        "\"uiComponent\": \"ckfile-upload\"," +
                        "\"required\": false," +
                        "\"placeholder\": \"上传主文档文件或录入网络地址\"" +
                    "}," +
                    "{" +
                        "\"id\": \"fld-attachment\"," +
                        "\"fieldName\": \"attachmentOid\"," +
                        "\"label\": \"附件上传\"," +
                        "\"uiComponent\": \"file-upload\"," +
                        "\"required\": false," +
                        "\"placeholder\": \"上传文档附件\"" +
                    "}" +
                "]" +
            "}" +
        "}";
    }

    private String buildDocumentUpdateLayout() {
        return "{" +
            "\"form\": {" +
                "\"fields\": [" +
                    "{" +
                        "\"id\": \"fld-name\"," +
                        "\"fieldName\": \"name\"," +
                        "\"label\": \"文档名称\"," +
                        "\"uiComponent\": \"input\"," +
                        "\"required\": true," +
                        "\"placeholder\": \"请输入文档名称\"" +
                    "}," +
                    "{" +
                        "\"id\": \"fld-doctype\"," +
                        "\"fieldName\": \"typeDefinitionCode\"," +
                        "\"label\": \"文档类型\"," +
                        "\"uiComponent\": \"document-type-select\"," +
                        "\"placeholder\": \"选择文档类型（TypeDefinition）\"" +
                    "}," +
                    "{" +
                        "\"id\": \"fld-desc\"," +
                        "\"fieldName\": \"description\"," +
                        "\"label\": \"描述\"," +
                        "\"uiComponent\": \"textarea\"," +
                        "\"rows\": 3," +
                        "\"placeholder\": \"请输入文档描述\"" +
                    "}," +
                    "{" +
                        "\"id\": \"fld-stage\"," +
                        "\"fieldName\": \"stageOid\"," +
                        "\"label\": \"所属研发阶段\"," +
                        "\"uiComponent\": \"stage-select\"," +
                        "\"placeholder\": \"请选择所属研发阶段\"" +
                    "}," +
                    "{" +
                        "\"id\": \"fld-location\"," +
                        "\"fieldName\": \"location\"," +
                        "\"label\": \"存放位置\"," +
                        "\"uiComponent\": \"input\"," +
                        "\"placeholder\": \"文档存放位置\"" +
                    "}," +
                    "{" +
                        "\"id\": \"fld-ckfile\"," +
                        "\"fieldName\": \"ckfileOid\"," +
                        "\"label\": \"附件上传\"," +
                        "\"uiComponent\": \"file-upload\"," +
                        "\"required\": false," +
                        "\"placeholder\": \"上传文档附件\"" +
                    "}" +
                "]" +
            "}" +
        "}";
    }

    // ==================== detail（详情页）布局 JSON 模板 ====================

    private String buildProductLineDetailLayout() {
        return "{" +
            "\"form\": {" +
                "\"readonly\": true," +
                "\"fields\": [" +
                    "{" +
                        "\"id\": \"fld-code\"," +
                        "\"fieldName\": \"code\"," +
                        "\"label\": \"编码\"," +
                        "\"uiComponent\": \"input\"," +
                        "\"readonly\": true" +
                    "}," +
                    "{" +
                        "\"id\": \"fld-name\"," +
                        "\"fieldName\": \"name\"," +
                        "\"label\": \"名称\"," +
                        "\"uiComponent\": \"input\"," +
                        "\"readonly\": true" +
                    "}," +
                    "{" +
                        "\"id\": \"fld-desc\"," +
                        "\"fieldName\": \"description\"," +
                        "\"label\": \"描述\"," +
                        "\"uiComponent\": \"textarea\"," +
                        "\"rows\": 3," +
                        "\"readonly\": true" +
                    "}," +
                    "{" +
                        "\"id\": \"fld-thumbnail\"," +
                        "\"fieldName\": \"thumbnail\"," +
                        "\"label\": \"缩略图\"," +
                        "\"uiComponent\": \"image\"," +
                        "\"readonly\": true" +
                    "}" +
                "]" +
            "}" +
        "}";
    }

    private String buildProductModelDetailLayout() {
        return "{" +
            "\"form\": {" +
                "\"readonly\": true," +
                "\"fields\": [" +
                    "{" +
                        "\"id\": \"fld-code\"," +
                        "\"fieldName\": \"code\"," +
                        "\"label\": \"编码\"," +
                        "\"uiComponent\": \"input\"," +
                        "\"readonly\": true" +
                    "}," +
                    "{" +
                        "\"id\": \"fld-name\"," +
                        "\"fieldName\": \"name\"," +
                        "\"label\": \"名称\"," +
                        "\"uiComponent\": \"input\"," +
                        "\"readonly\": true" +
                    "}," +
                    "{" +
                        "\"id\": \"fld-desc\"," +
                        "\"fieldName\": \"description\"," +
                        "\"label\": \"描述\"," +
                        "\"uiComponent\": \"textarea\"," +
                        "\"rows\": 3," +
                        "\"readonly\": true" +
                    "}," +
                    "{" +
                        "\"id\": \"fld-productLine\"," +
                        "\"fieldName\": \"productLine\"," +
                        "\"label\": \"所属产品系列\"," +
                        "\"uiComponent\": \"input\"," +
                        "\"readonly\": true" +
                    "}," +
                    "{" +
                        "\"id\": \"fld-thumbnail\"," +
                        "\"fieldName\": \"thumbnail\"," +
                        "\"label\": \"缩略图\"," +
                        "\"uiComponent\": \"image\"," +
                        "\"readonly\": true" +
                    "}" +
                "]" +
            "}" +
        "}";
    }

    private String buildDocumentDetailLayout() {
        return "{" +
            "\"form\": {" +
                "\"readonly\": true," +
                "\"fields\": [" +
                    "{" +
                        "\"id\": \"fld-number\"," +
                        "\"fieldName\": \"number\"," +
                        "\"label\": \"文档编号\"," +
                        "\"uiComponent\": \"input\"," +
                        "\"readonly\": true" +
                    "}," +
                    "{" +
                        "\"id\": \"fld-name\"," +
                        "\"fieldName\": \"name\"," +
                        "\"label\": \"文档名称\"," +
                        "\"uiComponent\": \"input\"," +
                        "\"readonly\": true" +
                    "}," +
                    "{" +
                        "\"id\": \"fld-doctype\"," +
                        "\"fieldName\": \"typeDefinitionCode\"," +
                        "\"label\": \"文档类型\"," +
                        "\"uiComponent\": \"input\"," +
                        "\"readonly\": true" +
                    "}," +
                    "{" +
                        "\"id\": \"fld-desc\"," +
                        "\"fieldName\": \"description\"," +
                        "\"label\": \"描述\"," +
                        "\"uiComponent\": \"textarea\"," +
                        "\"rows\": 3," +
                        "\"readonly\": true" +
                    "}," +
                    "{" +
                        "\"id\": \"fld-stage\"," +
                        "\"fieldName\": \"stageOid\"," +
                        "\"label\": \"所属研发阶段\"," +
                        "\"uiComponent\": \"input\"," +
                        "\"readonly\": true" +
                    "}," +
                    "{" +
                        "\"id\": \"fld-location\"," +
                        "\"fieldName\": \"location\"," +
                        "\"label\": \"存放位置\"," +
                        "\"uiComponent\": \"input\"," +
                        "\"readonly\": true" +
                    "}," +
                    "{" +
                        "\"id\": \"fld-revision\"," +
                        "\"fieldName\": \"revision\"," +
                        "\"label\": \"版本\"," +
                        "\"uiComponent\": \"input\"," +
                        "\"readonly\": true" +
                    "}" +
                "]" +
            "}" +
        "}";
    }

    // ==================== PART 布局 JSON 模板 ====================
    // PART 是零部件家族的模板根（子类型按 root_type_code=PART 解析属性），字段取自 PART 的
    // 属性定义：name / number / typeDefinitionCode / clsOid / containerOid / folderOid / stageOid /
    // unit / source / displayVersion / revision / iteration / status / description。
    // 控件 id 见前端 widgets/catalog.js（number-preview、version-display、unit-select、
    // source-select、classification-bound-select、resource-container-select、folder-select、stage-select）。

    private String buildPartListLayout() {
        return """
                {
                  "search": {
                    "enabled": true,
                    "fields": [
                      { "fieldName": "name", "label": "名称", "uiComponent": "input", "placeholder": "搜索名称" },
                      { "fieldName": "number", "label": "编号", "uiComponent": "input", "placeholder": "搜索编号" },
                      { "fieldName": "typeDefinitionCode", "label": "类型", "uiComponent": "input", "placeholder": "搜索类型编码" },
                      { "fieldName": "displayVersion", "label": "版本", "uiComponent": "input", "placeholder": "搜索版本" }
                    ]
                  },
                  "table": {
                    "enabled": true,
                    "toolbarEnabled": true,
                    "toolbar": ["create", "export"],
                    "hasEdit": true,
                    "hasDelete": true,
                    "columns": [
                      { "fieldName": "name", "label": "名称", "width": 200, "sortable": false },
                      { "fieldName": "number", "label": "编号", "width": 150, "sortable": false },
                      { "fieldName": "typeDefinitionCode", "label": "类型", "width": 120, "sortable": false },
                      { "fieldName": "status", "label": "生命周期状态", "width": 130, "sortable": false },
                      { "fieldName": "revision", "label": "大版本", "width": 80, "sortable": false },
                      { "fieldName": "iteration", "label": "小版本", "width": 80, "sortable": false },
                      { "fieldName": "unit", "label": "单位", "width": 80, "sortable": false },
                      { "fieldName": "source", "label": "来源", "width": 90, "sortable": false },
                      { "fieldName": "action", "label": "操作", "width": 150, "sortable": false, "fixed": "right" }
                    ]
                  },
                  "form": { "enabled": true, "name": "编辑表单", "fields": [] }
                }
                """;
    }

    private String buildPartCreateLayout() {
        return """
                {
                  "form": {
                    "fields": [
                      { "id": "fld-number", "fieldName": "number", "label": "编号", "uiComponent": "number-preview", "readonly": true, "placeholder": "系统根据编码规则自动生成" },
                      { "id": "fld-name", "fieldName": "name", "label": "名称", "uiComponent": "input", "required": true, "placeholder": "请输入零部件名称" },
                      { "id": "fld-version", "fieldName": "displayVersion", "label": "版本", "uiComponent": "version-display", "readonly": true, "placeholder": "由系统生成" },
                      { "id": "fld-cls", "fieldName": "clsOid", "label": "分类", "uiComponent": "classification-bound-select", "placeholder": "请选择分类" },
                      { "id": "fld-container", "fieldName": "containerOid", "label": "所属容器", "uiComponent": "resource-container-select", "placeholder": "请选择所属库或产品" },
                      { "id": "fld-folder", "fieldName": "folderOid", "label": "所属文件夹", "uiComponent": "folder-select", "placeholder": "请选择所属文件夹" },
                      { "id": "fld-stage", "fieldName": "stageOid", "label": "研发阶段", "uiComponent": "stage-select", "required": false, "placeholder": "请选择研发阶段" },
                      { "id": "fld-unit", "fieldName": "unit", "label": "单位", "uiComponent": "unit-select", "placeholder": "请选择单位" },
                      { "id": "fld-source", "fieldName": "source", "label": "来源", "uiComponent": "source-select", "placeholder": "请选择来源" },
                      { "id": "fld-desc", "fieldName": "description", "label": "描述", "uiComponent": "textarea", "rows": 3, "placeholder": "请输入描述（可选）" }
                    ]
                  }
                }
                """;
    }

    private String buildPartUpdateLayout() {
        return """
                {
                  "form": {
                    "fields": [
                      { "id": "fld-number", "fieldName": "number", "label": "编号", "uiComponent": "input", "readonly": true, "placeholder": "编码不可修改" },
                      { "id": "fld-name", "fieldName": "name", "label": "名称", "uiComponent": "input", "required": true, "placeholder": "请输入零部件名称" },
                      { "id": "fld-type", "fieldName": "typeDefinitionCode", "label": "类型", "uiComponent": "input", "readonly": true },
                      { "id": "fld-version", "fieldName": "displayVersion", "label": "版本", "uiComponent": "version-display", "readonly": true },
                      { "id": "fld-cls", "fieldName": "clsOid", "label": "分类", "uiComponent": "classification-bound-select", "placeholder": "请选择分类" },
                      { "id": "fld-container", "fieldName": "containerOid", "label": "所属容器", "uiComponent": "resource-container-select", "placeholder": "请选择所属库或产品" },
                      { "id": "fld-folder", "fieldName": "folderOid", "label": "所属文件夹", "uiComponent": "folder-select", "placeholder": "请选择所属文件夹" },
                      { "id": "fld-stage", "fieldName": "stageOid", "label": "研发阶段", "uiComponent": "stage-select", "required": false, "placeholder": "请选择研发阶段" },
                      { "id": "fld-unit", "fieldName": "unit", "label": "单位", "uiComponent": "unit-select", "placeholder": "请选择单位" },
                      { "id": "fld-source", "fieldName": "source", "label": "来源", "uiComponent": "source-select", "placeholder": "请选择来源" },
                      { "id": "fld-desc", "fieldName": "description", "label": "描述", "uiComponent": "textarea", "rows": 3, "placeholder": "请输入描述（可选）" }
                    ]
                  }
                }
                """;
    }

    private String buildPartDetailLayout() {
        return """
                {
                  "form": {
                    "readonly": true,
                    "fields": [
                      { "id": "fld-number", "fieldName": "number", "label": "编号", "uiComponent": "input", "readonly": true },
                      { "id": "fld-name", "fieldName": "name", "label": "名称", "uiComponent": "input", "readonly": true },
                      { "id": "fld-type", "fieldName": "typeDefinitionCode", "label": "类型", "uiComponent": "input", "readonly": true },
                      { "id": "fld-status", "fieldName": "status", "label": "生命周期状态", "uiComponent": "input", "readonly": true },
                      { "id": "fld-version", "fieldName": "displayVersion", "label": "版本", "uiComponent": "input", "readonly": true },
                      { "id": "fld-cls", "fieldName": "clsOid", "label": "分类", "uiComponent": "input", "readonly": true },
                      { "id": "fld-folder", "fieldName": "folderOid", "label": "所属文件夹", "uiComponent": "input", "readonly": true },
                      { "id": "fld-stage", "fieldName": "stageOid", "label": "研发阶段", "uiComponent": "input", "readonly": true },
                      { "id": "fld-unit", "fieldName": "unit", "label": "单位", "uiComponent": "input", "readonly": true },
                      { "id": "fld-source", "fieldName": "source", "label": "来源", "uiComponent": "input", "readonly": true },
                      { "id": "fld-creator", "fieldName": "creator", "label": "创建者", "uiComponent": "input", "readonly": true },
                      { "id": "fld-desc", "fieldName": "description", "label": "描述", "uiComponent": "textarea", "rows": 3, "readonly": true }
                    ]
                  }
                }
                """;
    }
}
