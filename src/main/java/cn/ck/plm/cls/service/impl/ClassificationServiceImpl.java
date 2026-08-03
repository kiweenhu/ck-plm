package cn.ck.plm.cls.service.impl;

import cn.ck.plm.base.util.TenantContext;
import cn.ck.plm.base.util.UserContext;
import cn.ck.plm.cls.entity.Classification;
import cn.ck.plm.cls.entity.ClassificationIBA;
import cn.ck.plm.cls.mapper.ClassificationIBAMapper;
import cn.ck.plm.cls.mapper.ClassificationMapper;
import cn.ck.plm.cls.service.api.ClassificationService;
import cn.ck.plm.iam.entity.User;
import cn.ck.plm.iam.service.api.UserService;
import cn.ck.plm.softtype.entity.IBA;
import cn.ck.plm.softtype.mapper.IBAMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

@Service
public class ClassificationServiceImpl implements ClassificationService {

    private static final Logger log = LoggerFactory.getLogger(ClassificationServiceImpl.class);

    private final ClassificationMapper mapper;
    private final ClassificationIBAMapper ibaMapper;
    private final IBAMapper ibaDefMapper;
    private final UserService userService;

    public ClassificationServiceImpl(ClassificationMapper mapper,
                                      ClassificationIBAMapper ibaMapper,
                                      IBAMapper ibaDefMapper,
                                      UserService userService) {
        this.mapper = mapper;
        this.ibaMapper = ibaMapper;
        this.ibaDefMapper = ibaDefMapper;
        this.userService = userService;
    }

    private String tenantOid() {
        String username = UserContext.get();
        if (username != null) {
            User user = userService.findByUsername(username);
            if (user != null && user.getTenantOid() != null) {
                return user.getTenantOid();
            }
        }
        return TenantContext.get();
    }

    // ==================== CRUD ====================

    @Override
    @Transactional
    public Classification create(Classification c) {
        if (c.getIdentifier() == null || c.getIdentifier().trim().isEmpty()) {
            throw new IllegalArgumentException("分类标识不能为空");
        }
        c.setIdentifier(c.getIdentifier().trim());
        if (c.getParentOid() != null && c.getParentOid().trim().isEmpty()) {
            c.setParentOid(null);
        }
        if (c.getSortOrder() == null) {
            c.setSortOrder(0);
        }
        if (mapper.existsByIdentifier(c.getIdentifier(), tenantOid()) > 0) {
            throw new IllegalArgumentException("分类标识 '" + c.getIdentifier() + "' 已存在");
        }
        if (c.getTenantOid() == null) {
            c.setTenantOid(tenantOid());
        }
        mapper.insert(c);
        return c;
    }

    @Override
    @Transactional
    public Classification update(Classification c) {
        Classification existing = mapper.selectByOid(c.getOid());
        if (existing == null) {
            throw new IllegalArgumentException("分类不存在: " + c.getOid());
        }
        if (c.getParentOid() != null && c.getParentOid().trim().isEmpty()) {
            c.setParentOid(null);
        }
        mapper.update(c);
        return c;
    }

    @Override
    @Transactional
    public void delete(String oid) {
        int children = mapper.countChildren(oid);
        if (children > 0) {
            throw new IllegalArgumentException("存在子分类，无法删除");
        }
        // 级联删除 IBA 关联
        ibaMapper.deleteByClassificationOid(oid);
        mapper.deleteByOid(oid);
    }

    @Override
    public Classification findByOid(String oid) {
        return mapper.selectByOid(oid);
    }

    @Override
    public Classification findByIdentifier(String identifier) {
        return mapper.selectByIdentifier(identifier, tenantOid());
    }

    @Override
    public List<Classification> findAll() {
        return mapper.selectAll(tenantOid());
    }

    @Override
    public List<Classification> search(String keyword) {
        return mapper.search(keyword, tenantOid());
    }

    @Override
    public List<Classification> findRoots() {
        return mapper.selectRoots(tenantOid());
    }

    @Override
    public List<Classification> findChildren(String parentOid) {
        return mapper.selectByParentOid(parentOid, tenantOid());
    }

    @Override
    public List<Classification> findTree() {
        List<Classification> all = findAll();
        if (all == null || all.isEmpty()) return Collections.emptyList();

        Map<String, List<Classification>> parentMap = all.stream()
                .filter(c -> c.getParentOid() != null)
                .collect(Collectors.groupingBy(Classification::getParentOid));

        List<Classification> roots = all.stream()
                .filter(c -> c.getParentOid() == null)
                .collect(Collectors.toList());

        for (Classification node : all) {
            List<Classification> children = parentMap.get(node.getOid());
            if (children != null) {
                children.sort(Comparator.comparingInt(c -> c.getSortOrder() != null ? c.getSortOrder() : 0));
                node.setChildren(children);
            }
        }

        roots.sort(Comparator.comparingInt(c -> c.getSortOrder() != null ? c.getSortOrder() : 0));
        return roots;
    }

    // ==================== 分类-IBA 关联 ====================

    @Override
    @Transactional
    public ClassificationIBA assignIba(ClassificationIBA mapping) {
        if (mapping.getClassificationOid() == null || mapping.getIbaOid() == null) {
            throw new IllegalArgumentException("分类 oid 和 IBA oid 不能为空");
        }
        if (ibaMapper.existsByClsAndIba(mapping.getClassificationOid(), mapping.getIbaOid()) > 0) {
            throw new IllegalArgumentException("该 IBA 已分配给此分类");
        }
        if (mapping.getTenantOid() == null) {
            mapping.setTenantOid(tenantOid());
        }
        ibaMapper.insert(mapping);
        return mapping;
    }

    @Override
    @Transactional
    public List<ClassificationIBA> batchAssignIbas(String classificationOid, List<String> ibaOids) {
        List<ClassificationIBA> results = new ArrayList<>();
        for (String ibaOid : ibaOids) {
            if (ibaMapper.existsByClsAndIba(classificationOid, ibaOid) > 0) continue;
            ClassificationIBA mapping = new ClassificationIBA(classificationOid, ibaOid);
            mapping.setTenantOid(tenantOid());
            ibaMapper.insert(mapping);
            results.add(mapping);
        }
        return results;
    }

    @Override
    @Transactional
    public ClassificationIBA updateIBAMapping(ClassificationIBA mapping) {
        ClassificationIBA existing = ibaMapper.selectByOid(mapping.getOid());
        if (existing == null) {
            throw new IllegalArgumentException("IBA 映射不存在: " + mapping.getOid());
        }
        existing.setRequired(mapping.isRequired());
        existing.setDefaultValue(mapping.getDefaultValue());
        existing.setSortOrder(mapping.getSortOrder());
        ibaMapper.update(existing);
        return existing;
    }

    @Override
    @Transactional
    public void removeIBAMapping(String mappingOid) {
        ClassificationIBA existing = ibaMapper.selectByOid(mappingOid);
        if (existing == null) {
            throw new IllegalArgumentException("IBA 映射不存在: " + mappingOid);
        }
        ibaMapper.deleteByOid(mappingOid);
    }

    @Override
    public List<ClassificationIBA> findIBAsByClassificationOid(String classificationOid) {
        return ibaMapper.selectByClassificationOid(classificationOid);
    }

    @Override
    public List<IBA> findUnassignedIBAs(String classificationOid, String keyword) {
        // 获取已分配的 IBA oid 列表
        List<ClassificationIBA> assigned = ibaMapper.selectByClassificationOid(classificationOid);
        Set<String> assignedOids = assigned.stream().map(ClassificationIBA::getIbaOid).collect(Collectors.toSet());

        // 获取所有 IBA 定义
        List<IBA> allIbas = ibaDefMapper.selectAll();
        return allIbas.stream()
                .filter(iba -> !assignedOids.contains(iba.getOid()))
                .filter(iba -> keyword == null || keyword.trim().isEmpty()
                        || iba.getCode().toLowerCase().contains(keyword.toLowerCase())
                        || (iba.getName() != null && iba.getName().toLowerCase().contains(keyword.toLowerCase())))
                .collect(Collectors.toList());
    }
}
