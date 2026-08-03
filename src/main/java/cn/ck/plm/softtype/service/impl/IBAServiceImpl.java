/*
 * Copyright (c) 2025 深圳乘恺科技有限公司
 * All rights reserved.
 *
 * @author Kiween.Hu; Roney.Liu
 */

package cn.ck.plm.softtype.service.impl;

import cn.ck.plm.base.util.TenantContext;
import cn.ck.plm.softtype.entity.IBA;
import cn.ck.plm.softtype.entity.TypeIBA;
import cn.ck.plm.softtype.entity.TypeDefinition;
import cn.ck.plm.softtype.mapper.IBAMapper;
import cn.ck.plm.softtype.mapper.TypeIBAMapper;
import cn.ck.plm.softtype.mapper.TypeDefinitionMapper;
import cn.ck.plm.softtype.service.api.IBAService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

/**
 * {@link IBAService} 的数据库实现，同时管理 IBA 和类型映射。
 * 不同租户的 IBA 属性定义相互独立。
 */
@Service
public class IBAServiceImpl implements IBAService {

    private final IBAMapper ibaMapper;
    private final TypeIBAMapper mappingMapper;
    private final TypeDefinitionMapper typeDefMapper;

    public IBAServiceImpl(IBAMapper ibaMapper,
                          TypeIBAMapper mappingMapper,
                          TypeDefinitionMapper typeDefMapper) {
        this.ibaMapper = ibaMapper;
        this.mappingMapper = mappingMapper;
        this.typeDefMapper = typeDefMapper;
    }

    // ==================== IBA CRUD ====================

    @Override
    @Transactional
    public IBA create(IBA iba) {
        if (iba == null || iba.getCode() == null || iba.getCode().trim().isEmpty()) {
            throw new IllegalArgumentException("IBA 编码不能为空");
        }
        String code = iba.getCode().trim().toUpperCase();
        iba.setCode(code);
        if (iba.getName() == null || iba.getName().trim().isEmpty()) {
            throw new IllegalArgumentException("IBA 名称不能为空");
        }
        String tenantOid = TenantContext.get();
        if (iba.getTenantOid() == null) {
            iba.setTenantOid(tenantOid);
        }
        if (ibaMapper.existsByCode(code, tenantOid) > 0) {
            throw new IllegalArgumentException("IBA 编码 '" + code + "' 已存在");
        }
        if (iba.getDataType() == null || iba.getDataType().trim().isEmpty()) {
            iba.setDataType("STRING");
        }
        ibaMapper.insert(iba);
        return iba;
    }

    @Override
    @Transactional
    public IBA update(IBA iba) {
        if (iba == null || iba.getOid() == null) {
            throw new IllegalArgumentException("IBA oid 不能为空");
        }
        IBA existing = ibaMapper.selectByOid(iba.getOid());
        if (existing == null) {
            throw new IllegalArgumentException("IBA 不存在");
        }
        ibaMapper.update(iba);
        existing.setName(iba.getName());
        existing.setDisplayName(iba.getDisplayName());
        existing.setDataType(iba.getDataType());
        existing.setDefaultValue(iba.getDefaultValue());
        existing.setConstraintsJson(iba.getConstraintsJson());
        existing.setRequired(iba.isRequired());
        existing.setDescription(iba.getDescription());
        existing.setSortOrder(iba.getSortOrder());
        existing.setEnabled(iba.isEnabled());
        return existing;
    }

    @Override
    @Transactional
    public boolean delete(String oid) {
        if (oid == null || oid.trim().isEmpty()) return false;
        if (ibaMapper.selectByOid(oid) == null) return false;
        ibaMapper.deleteByOid(oid);
        return true;
    }

    @Override
    public IBA findByOid(String oid) {
        return oid != null ? ibaMapper.selectByOid(oid) : null;
    }

    @Override
    public IBA findByCode(String code) {
        return code != null ? ibaMapper.selectByCode(code.trim().toUpperCase(), TenantContext.get()) : null;
    }

    @Override
    public List<IBA> findAll() {
        return ibaMapper.selectAll();
    }

    @Override
    public List<IBA> findEnabled() {
        return ibaMapper.selectEnabled();
    }

    @Override
    public List<IBA> search(String keyword) {
        if (keyword == null || keyword.trim().isEmpty()) return findAll();
        return ibaMapper.search(keyword.trim());
    }

    @Override
    public boolean existsByCode(String code) {
        return code != null && ibaMapper.existsByCode(code.trim().toUpperCase(), TenantContext.get()) > 0;
    }

    // ==================== IBA 映射管理 ====================

    @Override
    @Transactional
    public TypeIBA assignIba(TypeIBA mapping) {
        if (mapping.getTypeOid() == null || mapping.getIbaOid() == null) {
            throw new IllegalArgumentException("类型 oid 和 IBA oid 不能为空");
        }
        if (mappingMapper.existsByTypeAndIba(mapping.getTypeOid(), mapping.getIbaOid()) > 0) {
            throw new IllegalArgumentException("该 IBA 已分配给此类型");
        }
        if (mapping.getTenantOid() == null) {
            mapping.setTenantOid(TenantContext.get());
        }
        mappingMapper.insert(mapping);
        return mapping;
    }

    @Override
    @Transactional
    public TypeIBA updateMapping(TypeIBA mapping) {
        if (mapping == null || mapping.getOid() == null) {
            throw new IllegalArgumentException("映射 oid 不能为空");
        }
        TypeIBA existing = mappingMapper.selectByOid(mapping.getOid());
        if (existing == null) {
            throw new IllegalArgumentException("映射不存在");
        }
        mappingMapper.update(mapping);
        existing.setRequired(mapping.isRequired());
        existing.setDefaultValue(mapping.getDefaultValue());
        existing.setSortOrder(mapping.getSortOrder());
        return existing;
    }

    @Override
    @Transactional
    public boolean removeMapping(String mappingOid) {
        if (mappingOid == null || mappingOid.trim().isEmpty()) return false;
        TypeIBA mapping = mappingMapper.selectByOid(mappingOid);
        if (mapping == null) return false;
        mappingMapper.deleteByOid(mappingOid);
        return true;
    }

    @Override
    public List<IBA> findIbasByTypeOid(String typeOid) {
        if (typeOid == null) return new ArrayList<>();
        return ibaMapper.selectByTypeOid(typeOid);
    }

    @Override
    public List<TypeIBA> findMappingsByTypeOid(String typeOid) {
        if (typeOid == null) return new ArrayList<>();
        return mappingMapper.selectByTypeOid(typeOid);
    }

    @Override
    public List<TypeIBA> findMappingsByOwner(String ownerOid, String entityCode) {
        if (ownerOid == null || entityCode == null) return new ArrayList<>();
        return mappingMapper.selectByOwnerOid(ownerOid, entityCode);
    }

    @Override
    @Transactional
    public List<TypeIBA> batchAssign(String typeOid, List<String> ibaOids) {
        return batchAssign(typeOid, null, ibaOids);
    }

    @Override
    @Transactional
    public List<TypeIBA> batchAssign(String ownerOid, String entityCode, List<String> ibaOids) {
        if (ownerOid == null || ibaOids == null || ibaOids.isEmpty()) {
            throw new IllegalArgumentException("参数不能为空");
        }
        // 查找 TypeDefinition 获取 entityName（code）
        TypeDefinition td = typeDefMapper.selectByOid(ownerOid);
        if (td == null) throw new IllegalArgumentException("类型定义不存在");
        String entityName = td.getCode();
        String tenantOid = TenantContext.get();

        List<TypeIBA> results = new ArrayList<>();
        for (String ibaOid : ibaOids) {
            if (mappingMapper.existsByTypeAndIba(ownerOid, ibaOid) > 0) continue;
            TypeIBA mapping = new TypeIBA(ownerOid, ibaOid);
            mapping.setEntityCode(entityCode != null ? entityCode : entityName);
            mapping.setTenantOid(tenantOid);
            mappingMapper.insert(mapping);
            results.add(mapping);
        }
        return results;
    }

    @Override
    public List<IBA> findUnassignedIbas(String typeOid, String keyword) {
        return ibaMapper.selectUnassignedByTypeOid(typeOid, keyword);
    }

    @Override
    public List<IBA> findUnassignedIbas(String ownerOid, String entityCode, String keyword) {
        return ibaMapper.selectUnassignedByOwnerOid(ownerOid, entityCode, keyword);
    }

    @Override
    public List<TypeIBA> findTypesByIbaOid(String ibaOid) {
        return mappingMapper.selectByIbaOid(ibaOid);
    }

    @Override
    public List<TypeIBA> findInheritedMappings(String typeOid) {
        if (typeOid == null) return new ArrayList<>();
        return mappingMapper.selectInheritedMappings(typeOid);
    }

    @Override
    public List<TypeIBA> findInheritedMappingsByOwner(String ownerOid, String entityCode) {
        if (ownerOid == null || entityCode == null) return new ArrayList<>();
        return mappingMapper.selectInheritedMappingsByOwner(ownerOid, entityCode);
    }
}
