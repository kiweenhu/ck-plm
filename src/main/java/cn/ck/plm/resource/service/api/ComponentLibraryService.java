/*
 * Copyright (c) 2025 深圳乘恺科技有限公司
 * All rights reserved.
 *
 * @author Kiween.Hu; Roney.Liu
 */

package cn.ck.plm.resource.service.api;

import cn.ck.plm.resource.entity.ComponentCategory;
import cn.ck.plm.resource.entity.ElectronicComponent;

import java.util.List;

/**
 * 企业资源库-电子元器件库服务契约：分类树 + 元器件主数据管理。
 */
public interface ComponentLibraryService {

    // ==================== 分类 ====================

    /** 创建分类（同一父级下名称唯一） */
    ComponentCategory createCategory(ComponentCategory category);

    /** 更新分类（名称/排序） */
    ComponentCategory updateCategory(ComponentCategory category);

    /** 删除分类（有子分类或存在元器件时不可删除） */
    void deleteCategory(String oid);

    /** 查询完整分类树 */
    List<ComponentCategory> findCategoryTree();

    // ==================== 元器件 ====================

    /** 创建元器件（编码为空时自动生成；编码唯一） */
    ElectronicComponent createComponent(ElectronicComponent component);

    /** 更新元器件 */
    ElectronicComponent updateComponent(ElectronicComponent component);

    /** 删除元器件 */
    void deleteComponent(String oid);

    /** 按 oid 查询元器件 */
    ElectronicComponent findComponentByOid(String oid);

    /** 按分类 + 关键字查询元器件（categoryOid 可空 = 全部分类） */
    List<ElectronicComponent> findComponents(String categoryOid, String keyword);

    /** 元器件总数 */
    long countComponents();

    /** 库存总数 */
    long sumStockQty();
}
