/*
 * Copyright (c) 2025 深圳乘恺科技有限公司
 * All rights reserved.
 *
 * @author Kiween.Hu; Roney.Liu
 */

package cn.ck.plm.softtype.service.impl;

import cn.ck.plm.base.util.TenantContext;
import cn.ck.plm.softtype.entity.PageLayout;
import cn.ck.plm.softtype.entity.TypeDefinition;
import cn.ck.plm.softtype.mapper.TypeDefinitionMapper;
import cn.ck.plm.softtype.mapper.TypeIBAMapper;
import cn.ck.plm.softtype.mapper.TypeNumberRuleLinkMapper;
import cn.ck.plm.softtype.mapper.TypeVersionRuleLinkMapper;
import cn.ck.plm.softtype.mapper.TypeLifecycleTemplateLinkMapper;
import cn.ck.plm.softtype.mapper.PageLayoutMapper;
import cn.ck.plm.softtype.service.api.TypeDefinitionService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * {@link TypeDefinitionService} 的实现。
 */
@Service
public class TypeDefinitionServiceImpl implements TypeDefinitionService {

    private final TypeDefinitionMapper mapper;
    private final TypeIBAMapper typeIBAMapper;
    private final TypeNumberRuleLinkMapper numberRuleLinkMapper;
    private final TypeVersionRuleLinkMapper versionRuleLinkMapper;
    private final TypeLifecycleTemplateLinkMapper lifecycleTemplateLinkMapper;
    private final PageLayoutMapper pageLayoutMapper;

    public TypeDefinitionServiceImpl(TypeDefinitionMapper mapper,
                                     TypeIBAMapper typeIBAMapper,
                                     TypeNumberRuleLinkMapper numberRuleLinkMapper,
                                     TypeVersionRuleLinkMapper versionRuleLinkMapper,
                                     TypeLifecycleTemplateLinkMapper lifecycleTemplateLinkMapper,
                                     PageLayoutMapper pageLayoutMapper) {
        this.mapper = mapper;
        this.typeIBAMapper = typeIBAMapper;
        this.numberRuleLinkMapper = numberRuleLinkMapper;
        this.versionRuleLinkMapper = versionRuleLinkMapper;
        this.lifecycleTemplateLinkMapper = lifecycleTemplateLinkMapper;
        this.pageLayoutMapper = pageLayoutMapper;
    }

    private String tenantOid() {
        return TenantContext.get();
    }

    private String platformOid() {
        return TenantContext.PLATFORM_TENANT_OID;
    }

    @Override
    @Transactional
    public TypeDefinition create(TypeDefinition td) {
        if (td.getCode() == null || td.getCode().trim().isEmpty()) {
            throw new IllegalArgumentException("编码不能为空");
        }
        td.setCode(td.getCode().trim());
        if (mapper.existsByCode(td.getCode(), tenantOid(), platformOid()) > 0) {
            throw new IllegalArgumentException("编码 '" + td.getCode() + "' 已存在");
        }
        if (td.getTypeKind() == null || td.getTypeKind().isEmpty()) {
            td.setTypeKind(TypeDefinition.KIND_SOFT_TYPE);
        }
        if (td.getSource() == null || td.getSource().isEmpty()) {
            td.setSource("USER");
        }
        if (td.getTenantOid() == null) {
            td.setTenantOid(tenantOid());
        }

        // OOTB 类型：rootTypeCode = code；子类型：沿 parentOid 追溯
        if (td.isOotb()) {
            td.setRootTypeCode(td.getCode());
        } else if (td.isSoftType() && td.getParentOid() != null && !td.getParentOid().isEmpty()) {
            String rootCode = findRootCode(td.getParentOid(), 0);
            if (rootCode != null) {
                td.setRootTypeCode(rootCode);
            }
        }

        mapper.insert(td);

        // 子类型（SOFT_TYPE）初始化 PageLayout、IBA 映射、编码规则、版本规则、生命周期模板
        if (td.isSoftType() && td.getParentOid() != null && !td.getParentOid().isEmpty()) {
            inheritPageLayouts(td);
            inheritIBAMappings(td);
            inheritRuleLinks(td);
        }

        return td;
    }

    /**
     * 递归查找父类型的 PageLayout 并复制给当前子类型。
     * <p>查找顺序：当前 tenantOid → 父类型 tenantOid → 平台租户
     */
    private void inheritPageLayouts(TypeDefinition childTd) {
        TypeDefinition parent = mapper.selectByOid(childTd.getParentOid());
        if (parent == null) return;

        // 尝试按租户优先级查找父类型的 PageLayout
        // 1) 当前租户（子类型所在租户）  2) 父类型所在租户  3) 平台租户
        String[] tryTenants = {
            childTd.getTenantOid(),
            parent.getTenantOid(),
            platformOid()
        };

        List<PageLayout> parentLayouts = null;
        for (String tOid : tryTenants) {
            if (tOid == null) continue;
            parentLayouts = pageLayoutMapper.selectAllByEntity(
                parent.getOid(), parent.getCode(), tOid, platformOid());
            if (parentLayouts != null && !parentLayouts.isEmpty()) break;
        }

        // 如果父类型在当前任何租户下都没有 PageLayout，则递归向上查找
        if (parentLayouts == null || parentLayouts.isEmpty()) {
            inheritPageLayouts(childTd); // 注意：这里需要基于 parent 继续递归
            // 重新设计：基于 parent 的 parentOid 递归
            if (parent.isSoftType() && parent.getParentOid() != null && !parent.getParentOid().isEmpty()) {
                // 继续向上追溯
                inheritFromAncestor(childTd, parent.getParentOid());
            }
            return;
        }

        // 复制父类型的 PageLayout 给子类型
        copyPageLayouts(parentLayouts, childTd);
    }

    /** 递归向上追溯祖先类型，复制其 PageLayout */
    private void inheritFromAncestor(TypeDefinition childTd, String ancestorOid) {
        TypeDefinition ancestor = mapper.selectByOid(ancestorOid);
        if (ancestor == null) return;

        String[] tryTenants = {
            childTd.getTenantOid(),
            ancestor.getTenantOid(),
            platformOid()
        };

        List<PageLayout> ancestorLayouts = null;
        for (String tOid : tryTenants) {
            if (tOid == null) continue;
            ancestorLayouts = pageLayoutMapper.selectAllByEntity(
                ancestor.getOid(), ancestor.getCode(), tOid, platformOid());
            if (ancestorLayouts != null && !ancestorLayouts.isEmpty()) break;
        }

        if (ancestorLayouts != null && !ancestorLayouts.isEmpty()) {
            copyPageLayouts(ancestorLayouts, childTd);
        } else if (ancestor.isSoftType() && ancestor.getParentOid() != null && !ancestor.getParentOid().isEmpty()) {
            inheritFromAncestor(childTd, ancestor.getParentOid());
        }
    }

    /** 将父类型的 PageLayout 复制给子类型 */
    private void copyPageLayouts(List<PageLayout> sourceLayouts, TypeDefinition childTd) {
        for (PageLayout src : sourceLayouts) {
            PageLayout copy = new PageLayout();
            copy.setOid(java.util.UUID.randomUUID().toString());
            copy.setEntityOid(childTd.getOid());
            copy.setEntityCode(childTd.getCode());
            copy.setOperationCode(src.getOperationCode());
            copy.setOperationName(src.getOperationName());
            copy.setLayoutJson(src.getLayoutJson());
            copy.setTenantOid(childTd.getTenantOid());
            copy.setCreator(childTd.getCreator());
            pageLayoutMapper.insert(copy);
        }
    }

    /**
     * 递归查找父类型的 IBA 映射并复制给当前子类型。
     */
    private void inheritIBAMappings(TypeDefinition childTd) {
        TypeDefinition parent = mapper.selectByOid(childTd.getParentOid());
        if (parent == null) return;

        // 查找父类型的 IBA 映射
        List<cn.ck.plm.softtype.entity.TypeIBA> parentMappings = typeIBAMapper.selectByTypeOid(parent.getOid());

        // 如果父类型在当前没有 IBA，则递归向上查找
        if (parentMappings == null || parentMappings.isEmpty()) {
            if (parent.isSoftType() && parent.getParentOid() != null && !parent.getParentOid().isEmpty()) {
                inheritIBAMappingsFromAncestor(childTd, parent.getParentOid());
            }
            return;
        }

        copyIBAMappings(parentMappings, childTd);
    }

    /** 递归向上追溯祖先类型，复制其 IBA 映射 */
    private void inheritIBAMappingsFromAncestor(TypeDefinition childTd, String ancestorOid) {
        TypeDefinition ancestor = mapper.selectByOid(ancestorOid);
        if (ancestor == null) return;

        List<cn.ck.plm.softtype.entity.TypeIBA> ancestorMappings = typeIBAMapper.selectByTypeOid(ancestor.getOid());

        if (ancestorMappings != null && !ancestorMappings.isEmpty()) {
            copyIBAMappings(ancestorMappings, childTd);
        } else if (ancestor.isSoftType() && ancestor.getParentOid() != null && !ancestor.getParentOid().isEmpty()) {
            inheritIBAMappingsFromAncestor(childTd, ancestor.getParentOid());
        }
    }

    /** 将父类型的 IBA 映射复制给子类型 */
    private void copyIBAMappings(List<cn.ck.plm.softtype.entity.TypeIBA> sourceMappings, TypeDefinition childTd) {
        for (cn.ck.plm.softtype.entity.TypeIBA src : sourceMappings) {
            // 跳过已存在的映射
            if (typeIBAMapper.existsByTypeAndIba(childTd.getOid(), src.getIbaOid()) > 0) continue;

            cn.ck.plm.softtype.entity.TypeIBA copy = new cn.ck.plm.softtype.entity.TypeIBA(childTd.getOid(), src.getIbaOid());
            copy.setOid(java.util.UUID.randomUUID().toString());
            copy.setEntityCode(childTd.getCode());
            copy.setRequired(src.isRequired());
            copy.setDefaultValue(src.getDefaultValue());
            copy.setSortOrder(src.getSortOrder());
            copy.setTenantOid(childTd.getTenantOid());
            copy.setCreator(childTd.getCreator());
            typeIBAMapper.insert(copy);
        }
    }

    /**
     * 递归查找父类型的编码规则、版本规则、生命周期模板绑定，复制给子类型。
     */
    private void inheritRuleLinks(TypeDefinition childTd) {
        TypeDefinition parent = mapper.selectByOid(childTd.getParentOid());
        if (parent == null) return;

        String targetOid = findRuleLinkSource(parent);
        if (targetOid == null) return;

        // 编码规则
        if (numberRuleLinkMapper.existsByTypeOid(targetOid) > 0
                && numberRuleLinkMapper.existsByTypeOid(childTd.getOid()) == 0) {
            cn.ck.plm.softtype.entity.TypeNumberRuleLink parentLink = numberRuleLinkMapper.selectByTypeOid(targetOid);
            if (parentLink != null) {
                cn.ck.plm.softtype.entity.TypeNumberRuleLink link = new cn.ck.plm.softtype.entity.TypeNumberRuleLink(childTd.getOid(), parentLink.getNumberRuleCode());
                link.setOid(java.util.UUID.randomUUID().toString());
                link.setTenantOid(childTd.getTenantOid());
                link.setCreator(childTd.getCreator());
                numberRuleLinkMapper.insert(link);
            }
        }

        // 版本规则
        if (versionRuleLinkMapper.existsByTypeOid(targetOid) > 0
                && versionRuleLinkMapper.existsByTypeOid(childTd.getOid()) == 0) {
            cn.ck.plm.softtype.entity.TypeVersionRuleLink parentLink = versionRuleLinkMapper.selectByTypeOid(targetOid);
            if (parentLink != null) {
                cn.ck.plm.softtype.entity.TypeVersionRuleLink link = new cn.ck.plm.softtype.entity.TypeVersionRuleLink(childTd.getOid(), parentLink.getVersionRuleCode());
                link.setOid(java.util.UUID.randomUUID().toString());
                link.setTenantOid(childTd.getTenantOid());
                link.setCreator(childTd.getCreator());
                versionRuleLinkMapper.insert(link);
            }
        }

        // 生命周期模板
        if (lifecycleTemplateLinkMapper.existsByTypeOid(targetOid) > 0
                && lifecycleTemplateLinkMapper.existsByTypeOid(childTd.getOid()) == 0) {
            cn.ck.plm.softtype.entity.TypeLifecycleTemplateLink parentLink = lifecycleTemplateLinkMapper.selectByTypeOid(targetOid);
            if (parentLink != null) {
                cn.ck.plm.softtype.entity.TypeLifecycleTemplateLink link = new cn.ck.plm.softtype.entity.TypeLifecycleTemplateLink(childTd.getOid(), parentLink.getLifecycleTemplateCode());
                link.setOid(java.util.UUID.randomUUID().toString());
                link.setTenantOid(childTd.getTenantOid());
                link.setCreator(childTd.getCreator());
                lifecycleTemplateLinkMapper.insert(link);
            }
        }
    }

    /**
     * 沿父链查找第一个有规则绑定的祖先类型 oid。
     */
    private String findRuleLinkSource(TypeDefinition td) {
        if (td == null) return null;
        if (numberRuleLinkMapper.existsByTypeOid(td.getOid()) > 0) return td.getOid();
        if (td.isSoftType() && td.getParentOid() != null && !td.getParentOid().isEmpty()) {
            return findRuleLinkSource(mapper.selectByOid(td.getParentOid()));
        }
        return null;
    }

    /** 递归查找根 OOTB 类型的 code（上限 10 层） */
    private String findRootCode(String oid, int depth) {
        if (oid == null || depth > 10) return null;
        TypeDefinition td = mapper.selectByOid(oid);
        if (td == null) return null;
        if (TypeDefinition.KIND_OOTB.equals(td.getTypeKind())) {
            return td.getCode();
        }
        return findRootCode(td.getParentOid(), depth + 1);
    }

    @Override
    @Transactional
    public TypeDefinition update(TypeDefinition td) {
        if (td.getOid() == null) {
            throw new IllegalArgumentException("oid 不能为空");
        }
        TypeDefinition existing = mapper.selectByOid(td.getOid());
        if (existing == null) {
            throw new IllegalArgumentException("类型定义不存在");
        }
        TenantContext.requireEditPermission(existing.getTenantOid(), "类型定义");
        if (td.getTenantOid() == null) {
            td.setTenantOid(existing.getTenantOid());
        }
        mapper.update(td);
        if (td.getName() != null) existing.setName(td.getName());
        if (td.getIcon() != null) existing.setIcon(td.getIcon());
        if (td.getDescription() != null) existing.setDescription(td.getDescription());
        existing.setSortOrder(td.getSortOrder());
        existing.setEnabled(td.isEnabled());
        return existing;
    }

    @Override
    @Transactional
    public boolean delete(String oid) {
        if (oid == null || oid.trim().isEmpty()) return false;
        TypeDefinition existing = mapper.selectByOid(oid);
        if (existing == null) return false;
        // OOTB 系统预置类型不可删除
        if (TypeDefinition.KIND_OOTB.equals(existing.getTypeKind())) {
            throw new IllegalArgumentException("系统预置类型（OOTB）不可删除");
        }
        TenantContext.requireEditPermission(existing.getTenantOid(), "类型定义");
        if (mapper.countChildren(oid) > 0) {
            throw new IllegalArgumentException("该类型下存在子类型，无法删除");
        }

        // 级联删除关联数据：IBA 映射、编码规则绑定、版本规则绑定、生命周期模板绑定、页面布局
        typeIBAMapper.deleteByTypeOid(oid);
        numberRuleLinkMapper.deleteByTypeOid(oid);
        versionRuleLinkMapper.deleteByTypeOid(oid);
        lifecycleTemplateLinkMapper.deleteByTypeOid(oid);
        pageLayoutMapper.deleteAllByEntity(oid);

        // 删除类型定义本身
        mapper.deleteByOid(oid);
        return true;
    }

    @Override
    public TypeDefinition findByOid(String oid) {
        return oid != null ? mapper.selectByOid(oid) : null;
    }

    @Override
    public TypeDefinition findByCode(String code) {
        return code != null ? mapper.selectByCode(code, tenantOid(), platformOid()) : null;
    }

    @Override
    public List<TypeDefinition> findAll() {
        return mapper.selectAll(tenantOid(), platformOid());
    }

    @Override
    public List<TypeDefinition> findEnabled() {
        return mapper.selectEnabled(tenantOid(), platformOid());
    }

    @Override
    public List<TypeDefinition> findByTypeKind(String typeKind) {
        return mapper.selectByTypeKind(typeKind, tenantOid(), platformOid());
    }

    @Override
    public List<TypeDefinition> findRoots() {
        return mapper.selectRoots(tenantOid(), platformOid());
    }

    @Override
    public List<TypeDefinition> findChildren(String parentOid) {
        return mapper.selectByParentOid(parentOid, tenantOid(), platformOid());
    }

    @Override
    public List<TypeDefinition> findTree() {
        List<TypeDefinition> all = mapper.selectAll(tenantOid(), platformOid());
        if (all == null || all.isEmpty()) return new ArrayList<>();

        Map<String, List<TypeDefinition>> childrenMap = all.stream()
                .filter(td -> td.getParentOid() != null && !td.getParentOid().isEmpty())
                .collect(Collectors.groupingBy(TypeDefinition::getParentOid));

        Map<String, String> nameMap = all.stream()
                .collect(Collectors.toMap(TypeDefinition::getOid, TypeDefinition::getName, (a, b) -> a));

        List<TypeDefinition> roots = new ArrayList<>();
        for (TypeDefinition td : all) {
            if (td.isRoot()) {
                roots.add(td);
                buildTree(td, childrenMap, nameMap);
            }
        }
        return roots;
    }

    private void buildTree(TypeDefinition parent, Map<String, List<TypeDefinition>> childrenMap,
                           Map<String, String> nameMap) {
        List<TypeDefinition> children = childrenMap.get(parent.getOid());
        if (children != null) {
            for (TypeDefinition child : children) {
                child.setParentName(nameMap.get(child.getParentOid()));
            }
            parent.setChildren(children);
            for (TypeDefinition child : children) {
                buildTree(child, childrenMap, nameMap);
            }
        }
    }
}
