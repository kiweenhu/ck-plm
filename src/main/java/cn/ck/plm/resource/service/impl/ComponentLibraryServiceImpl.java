/*
 * Copyright (c) 2025 深圳乘恺科技有限公司
 * All rights reserved.
 *
 * @author Kiween.Hu; Roney.Liu
 */

package cn.ck.plm.resource.service.impl;

import cn.ck.plm.resource.entity.ComponentCategory;
import cn.ck.plm.resource.entity.ElectronicComponent;
import cn.ck.plm.resource.mapper.ComponentCategoryMapper;
import cn.ck.plm.resource.mapper.ElectronicComponentMapper;
import cn.ck.plm.resource.service.api.ComponentLibraryService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * 企业资源库-电子元器件库服务实现。
 */
@Service
public class ComponentLibraryServiceImpl implements ComponentLibraryService {

    private final ComponentCategoryMapper categoryMapper;
    private final ElectronicComponentMapper componentMapper;

    public ComponentLibraryServiceImpl(ComponentCategoryMapper categoryMapper,
                                       ElectronicComponentMapper componentMapper) {
        this.categoryMapper = categoryMapper;
        this.componentMapper = componentMapper;
    }

    // ==================== 分类 ====================

    @Override
    @Transactional
    public ComponentCategory createCategory(ComponentCategory category) {
        if (category.getName() == null || category.getName().trim().isEmpty()) {
            throw new IllegalArgumentException("分类名称不能为空");
        }
        if (categoryMapper.existsByName(category.getParentCategoryOid(),
                category.getName().trim(), null) > 0) {
            throw new IllegalArgumentException("同级分类下已存在同名分类: " + category.getName());
        }
        category.setName(category.getName().trim());
        category.setCreatedAt(LocalDateTime.now());
        category.setUpdatedAt(LocalDateTime.now());
        categoryMapper.insert(category);
        return category;
    }

    @Override
    @Transactional
    public ComponentCategory updateCategory(ComponentCategory category) {
        ComponentCategory existing = categoryMapper.selectByOid(category.getOid());
        if (existing == null) {
            throw new IllegalArgumentException("分类不存在: " + category.getOid());
        }
        if (category.getName() != null && !category.getName().trim().isEmpty()) {
            if (categoryMapper.existsByName(existing.getParentCategoryOid(),
                    category.getName().trim(), category.getOid()) > 0) {
                throw new IllegalArgumentException("同级分类下已存在同名分类: " + category.getName());
            }
            existing.setName(category.getName().trim());
        }
        if (category.getSortOrder() != null) {
            existing.setSortOrder(category.getSortOrder());
        }
        existing.setUpdatedAt(LocalDateTime.now());
        categoryMapper.update(existing);
        return existing;
    }

    @Override
    @Transactional
    public void deleteCategory(String oid) {
        if (categoryMapper.countByParentOid(oid) > 0) {
            throw new IllegalArgumentException("该分类下存在子分类，无法删除");
        }
        if (!componentMapper.selectByCondition(oid, null).isEmpty()) {
            throw new IllegalArgumentException("该分类下存在元器件，无法删除");
        }
        categoryMapper.deleteByOid(oid);
    }

    @Override
    public List<ComponentCategory> findCategoryTree() {
        List<ComponentCategory> all = categoryMapper.selectAll();
        return buildTree(all, null);
    }

    /** 递归组树（parentCategoryOid 匹配；防御异常数据导致的无限递归由层级深度限制兜底） */
    private List<ComponentCategory> buildTree(List<ComponentCategory> all, String parentOid) {
        List<ComponentCategory> result = new ArrayList<>();
        for (ComponentCategory c : all) {
            String parent = c.getParentCategoryOid();
            boolean isRoot = (parent == null || parent.isEmpty());
            boolean match = parentOid == null ? isRoot : parentOid.equals(parent);
            if (match) {
                c.setChildren(buildTree(all, c.getOid()));
                result.add(c);
            }
        }
        return result;
    }

    // ==================== 元器件 ====================

    @Override
    @Transactional
    public ElectronicComponent createComponent(ElectronicComponent component) {
        if (component.getName() == null || component.getName().trim().isEmpty()) {
            throw new IllegalArgumentException("元器件名称不能为空");
        }
        // 编码为空时自动生成（EC-YYYYMM-序号），否则校验唯一
        if (component.getCode() == null || component.getCode().trim().isEmpty()) {
            component.setCode(generateCode());
        } else {
            component.setCode(component.getCode().trim());
            if (componentMapper.existsByCode(component.getCode(), null) > 0) {
                throw new IllegalArgumentException("元器件编码已存在: " + component.getCode());
            }
        }
        component.setName(component.getName().trim());
        component.setCreatedAt(LocalDateTime.now());
        component.setUpdatedAt(LocalDateTime.now());
        componentMapper.insert(component);
        return component;
    }

    /** 编码自动生成：EC-YYYYMM-6位随机（冲突重试，最多 5 次） */
    private String generateCode() {
        String ym = java.time.LocalDate.now().format(java.time.format.DateTimeFormatter.ofPattern("yyyyMM"));
        for (int i = 0; i < 5; i++) {
            String candidate = "EC-" + ym + "-" + UUID.randomUUID().toString().substring(0, 6).toUpperCase();
            if (componentMapper.existsByCode(candidate, null) == 0) {
                return candidate;
            }
        }
        return "EC-" + ym + "-" + System.currentTimeMillis();
    }

    @Override
    @Transactional
    public ElectronicComponent updateComponent(ElectronicComponent component) {
        ElectronicComponent existing = componentMapper.selectByOid(component.getOid());
        if (existing == null) {
            throw new IllegalArgumentException("元器件不存在: " + component.getOid());
        }
        if (component.getCode() != null && !component.getCode().trim().isEmpty()) {
            String code = component.getCode().trim();
            if (componentMapper.existsByCode(code, component.getOid()) > 0) {
                throw new IllegalArgumentException("元器件编码已存在: " + code);
            }
            existing.setCode(code);
        }
        if (component.getName() != null) existing.setName(component.getName().trim());
        existing.setCategoryOid(component.getCategoryOid());
        existing.setModel(component.getModel());
        existing.setPackageType(component.getPackageType());
        existing.setValueSpec(component.getValueSpec());
        existing.setManufacturer(component.getManufacturer());
        existing.setStockQty(component.getStockQty());
        existing.setSafeStockQty(component.getSafeStockQty());
        existing.setUnit(component.getUnit());
        existing.setUnitCost(component.getUnitCost());
        existing.setDescription(component.getDescription());
        existing.setUpdatedAt(LocalDateTime.now());
        componentMapper.update(existing);
        return existing;
    }

    @Override
    @Transactional
    public void deleteComponent(String oid) {
        componentMapper.deleteByOid(oid);
    }

    @Override
    public ElectronicComponent findComponentByOid(String oid) {
        return componentMapper.selectByOid(oid);
    }

    @Override
    public List<ElectronicComponent> findComponents(String categoryOid, String keyword) {
        String kw = (keyword == null || keyword.trim().isEmpty()) ? null : keyword.trim();
        return componentMapper.selectByCondition(
                (categoryOid == null || categoryOid.isEmpty()) ? null : categoryOid, kw);
    }

    @Override
    public long countComponents() {
        return componentMapper.countAll();
    }

    @Override
    public long sumStockQty() {
        return componentMapper.sumStockQty();
    }
}
