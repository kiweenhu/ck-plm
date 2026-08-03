/*
 * Copyright (c) 2025 深圳乘恺科技有限公司
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
        int inserted = 0, failed = 0;

        try {
            // ==== PRODUCT_LINE: list ====
            if (ensureLayout("PRODUCT_LINE", "list", "产品系列列表", buildProductLineListLayout())) {
                inserted++;
            } else {
                failed++;
            }

            // ==== PRODUCT_LINE: create ====
            if (ensureLayout("PRODUCT_LINE", "create", "新建产品系列", buildProductLineCreateLayout())) {
                inserted++;
            } else {
                failed++;
            }

            // ==== PRODUCT_LINE: update ====
            if (ensureLayout("PRODUCT_LINE", "update", "编辑产品系列", buildProductLineUpdateLayout())) {
                inserted++;
            } else {
                failed++;
            }

            // ==== PRODUCT_LINE: detail ====
            if (ensureLayout("PRODUCT_LINE", "detail", "产品系列详情", buildProductLineDetailLayout())) {
                inserted++;
            } else {
                failed++;
            }

            // ==== PRODUCT_MODEL: list ====
            if (ensureLayout("PRODUCT_MODEL", "list", "产品型号列表", buildProductModelListLayout())) {
                inserted++;
            } else {
                failed++;
            }

            // ==== PRODUCT_MODEL: create ====
            if (ensureLayout("PRODUCT_MODEL", "create", "新建产品型号", buildProductModelCreateLayout())) {
                inserted++;
            } else {
                failed++;
            }

            // ==== PRODUCT_MODEL: update ====
            if (ensureLayout("PRODUCT_MODEL", "update", "编辑产品型号", buildProductModelUpdateLayout())) {
                inserted++;
            } else {
                failed++;
            }

            // ==== PRODUCT_MODEL: detail ====
            if (ensureLayout("PRODUCT_MODEL", "detail", "产品型号详情", buildProductModelDetailLayout())) {
                inserted++;
            } else {
                failed++;
            }

            // ==== DOCUMENT: list ====
            if (ensureLayout("DOCUMENT", "list", "文档列表", buildDocumentListLayout())) {
                inserted++;
            } else {
                failed++;
            }

            // ==== DOCUMENT: create ====
            if (ensureLayout("DOCUMENT", "create", "新建文档", buildDocumentCreateLayout())) {
                inserted++;
            } else {
                failed++;
            }

            // ==== DOCUMENT: update ====
            if (ensureLayout("DOCUMENT", "update", "编辑文档", buildDocumentUpdateLayout())) {
                inserted++;
            } else {
                failed++;
            }

            // ==== DOCUMENT: detail ====
            if (ensureLayout("DOCUMENT", "detail", "文档详情", buildDocumentDetailLayout())) {
                inserted++;
            } else {
                failed++;
            }

        } catch (Exception e) {
            log.error("初始化页面布局失败: {}", e.getMessage(), e);
            return;
        }

        log.info("OOTB 默认页面布局初始化完成: 成功 {} 个, 失败 {} 个", inserted, failed);
    }

    /**
     * 注册一条页面布局记录。先在 ck_type_definition 中查找 entity_oid，然后插入 ck_type_page_layout。
     * <p>幂等：已存在的布局不会重复插入（如需更新布局请通过 PageDesigner 手动操作或手动删除 DB 记录）。
     *
     * @return true 表示新插入，false 表示已存在跳过或 entity_oid 未找到
     */
    private boolean ensureLayout(String entityCode, String operationCode, String operationName, String layoutJson) {
        // 1. 查找 entity_oid
        String findOidSql = "SELECT oid FROM ck_type_definition WHERE code = ?";
        String entityOid;
        try {
            entityOid = jdbcTemplate.queryForObject(findOidSql, String.class, entityCode);
        } catch (Exception e) {
            log.warn("  ✗ 未找到实体类型定义: {}", entityCode);
            return false;
        }

        if (entityOid == null || entityOid.isEmpty()) {
            log.warn("  ✗ 实体 {} 的 OID 为空", entityCode);
            return false;
        }

        // 2. 检查是否已存在（幂等：通过 entity_oid + operation_code 判断，已存在则跳过）
        String countSql = "SELECT COUNT(*) FROM ck_type_page_layout WHERE entity_oid = ? AND operation_code = ?";
        Integer count = jdbcTemplate.queryForObject(countSql, Integer.class, entityOid, operationCode);
        if (count != null && count > 0) {
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
}
