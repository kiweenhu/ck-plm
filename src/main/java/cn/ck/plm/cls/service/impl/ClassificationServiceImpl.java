package cn.ck.plm.cls.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;

import cn.ck.plm.base.util.TenantContext;
import cn.ck.plm.base.util.UserContext;
import cn.ck.plm.cls.dto.ClassificationCloneResult;
import cn.ck.plm.cls.entity.Classification;
import cn.ck.plm.cls.entity.ClassificationIBA;
import cn.ck.plm.cls.entity.ClsPageLayout;
import cn.ck.plm.cls.mapper.ClassificationIBAMapper;
import cn.ck.plm.cls.mapper.ClassificationMapper;
import cn.ck.plm.cls.mapper.ClsIbaDataMapper;
import cn.ck.plm.cls.mapper.ClsPageLayoutMapper;
import cn.ck.plm.cls.service.api.ClassificationService;
import cn.ck.plm.document.mapper.DocumentMapper;
import cn.ck.plm.iam.entity.User;
import cn.ck.plm.iam.service.api.UserService;
import cn.ck.plm.part.mapper.PartMapper;
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
    private final ClsPageLayoutMapper clsPageLayoutMapper;
    private final PartMapper partMapper;
    private final DocumentMapper documentMapper;
    private final ClsIbaDataMapper clsIbaDataMapper;
    private final UserService userService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public ClassificationServiceImpl(ClassificationMapper mapper,
                                      ClassificationIBAMapper ibaMapper,
                                      IBAMapper ibaDefMapper,
                                      ClsPageLayoutMapper clsPageLayoutMapper,
                                      PartMapper partMapper,
                                      DocumentMapper documentMapper,
                                      ClsIbaDataMapper clsIbaDataMapper,
                                      UserService userService) {
        this.mapper = mapper;
        this.ibaMapper = ibaMapper;
        this.ibaDefMapper = ibaDefMapper;
        this.clsPageLayoutMapper = clsPageLayoutMapper;
        this.partMapper = partMapper;
        this.documentMapper = documentMapper;
        this.clsIbaDataMapper = clsIbaDataMapper;
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
        // 守卫：被零组件绑定则禁止删除
        int partCount = partMapper.countByClassificationOid(oid);
        if (partCount > 0) {
            throw new IllegalStateException("当前分类已被 " + partCount + " 个零组件绑定，无法删除");
        }
        // 守卫：被文档绑定则禁止删除
        int docCount = documentMapper.countByClassificationOid(oid);
        if (docCount > 0) {
            throw new IllegalStateException("当前分类已被 " + docCount + " 个文档绑定，无法删除");
        }
        // 守卫：存在非空的分类属性值（实例 IBA）则禁止删除
        int ibaCount = clsIbaDataMapper.countNonEmptyByClassificationOid(oid);
        if (ibaCount > 0) {
            throw new IllegalStateException("当前分类存在 " + ibaCount + " 条非空分类属性值，无法删除");
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
                .filter(c -> {
                    String p = c.getParentOid();
                    return p == null || p.trim().isEmpty();
                })
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

    @Override
    public Classification findSubtree(String rootOid) {
        List<Classification> all = findAll();
        if (all == null || all.isEmpty()) return null;

        // 从全量列表中找目标节点
        Classification root = all.stream()
                .filter(c -> rootOid.equals(c.getOid()))
                .findFirst().orElse(null);
        if (root == null) return null;

        // 收集 rootOid 的所有后代 oid
        Set<String> descendantOids = new HashSet<>();
        collectDescendants(all, rootOid, descendantOids);

        if (descendantOids.isEmpty()) {
            root.setChildren(Collections.emptyList());
            return root;
        }

        // 过滤出后代节点
        List<Classification> descendants = all.stream()
                .filter(c -> descendantOids.contains(c.getOid()))
                .collect(Collectors.toList());

        // 在后代中构建父子关系
        Map<String, List<Classification>> parentMap = descendants.stream()
                .filter(c -> c.getParentOid() != null)
                .collect(Collectors.groupingBy(Classification::getParentOid));

        // 为每个后代装配 children
        for (Classification node : descendants) {
            List<Classification> children = parentMap.get(node.getOid());
            if (children != null) {
                children.sort(Comparator.comparingInt(c -> c.getSortOrder() != null ? c.getSortOrder() : 0));
                node.setChildren(children);
            }
        }

        // 装配 root 的直接 children
        List<Classification> rootChildren = parentMap.getOrDefault(rootOid, Collections.emptyList());
        rootChildren.sort(Comparator.comparingInt(c -> c.getSortOrder() != null ? c.getSortOrder() : 0));
        root.setChildren(rootChildren);

        return root;
    }

    /** 递归收集 rootOid 的所有后代 oid */
    private void collectDescendants(List<Classification> all, String parentOid, Set<String> result) {
        for (Classification c : all) {
            if (parentOid.equals(c.getParentOid())) {
                result.add(c.getOid());
                collectDescendants(all, c.getOid(), result);
            }
        }
    }

    @Override
    public List<Classification> findPlatformTree() {
        String platform = TenantContext.PLATFORM_TENANT_OID;
        List<Classification> all = mapper.selectAll(platform);
        if (all == null || all.isEmpty()) return Collections.emptyList();

        Map<String, List<Classification>> parentMap = all.stream()
                .filter(c -> {
                    String p = c.getParentOid();
                    return p != null && !p.trim().isEmpty();
                })
                .collect(Collectors.groupingBy(Classification::getParentOid));

        List<Classification> roots = all.stream()
                .filter(c -> {
                    String p = c.getParentOid();
                    return p == null || p.trim().isEmpty();
                })
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

    @Override
    @Transactional
    public ClassificationCloneResult cloneFromPlatform(List<String> rootOids) {
        String targetTenant = tenantOid();
        if (targetTenant == null) {
            throw new IllegalArgumentException("无法识别当前租户");
        }
        if (TenantContext.PLATFORM_TENANT_OID.equals(targetTenant)) {
            throw new IllegalArgumentException("平台租户无需克隆");
        }
        if (rootOids == null || rootOids.isEmpty()) {
            throw new IllegalArgumentException("rootOids 不能为空");
        }
        ClassificationCloneResult result = new ClassificationCloneResult();
        // 临时切换租户上下文到平台租户，绕过租户拦截器，以读取平台分类与 IBA 定义；
        // 目标租户数据由各 insert 语句显式携带 tenant_oid（拦截器对已含 tenant_oid 的 INSERT 不重复注入）。
        String originalTenant = TenantContext.getOrNull();
        try {
            TenantContext.set(TenantContext.PLATFORM_TENANT_OID);
            for (String rootOid : rootOids) {
                cloneOneFromPlatform(rootOid, targetTenant, result);
            }
        } finally {
            if (originalTenant != null) {
                TenantContext.set(originalTenant);
            } else {
                TenantContext.clear();
            }
        }
        return result;
    }

    private void cloneOneFromPlatform(String rootOid, String targetTenant, ClassificationCloneResult result) {
        Classification platformRoot = mapper.selectByOid(rootOid);
        if (platformRoot == null) {
            throw new IllegalArgumentException("平台分类根不存在: " + rootOid);
        }
        if (!TenantContext.PLATFORM_TENANT_OID.equals(platformRoot.getTenantOid())) {
            throw new IllegalArgumentException("指定的分类不是平台分类: " + rootOid);
        }
        cloneSubtree(platformRoot, null, targetTenant, result);
    }

    /** 递归增量克隆平台分类到目标租户：已存在则复用，缺失才创建，并补齐 IBA 与页面布局 */
    private void cloneSubtree(Classification source, String newParentOid, String targetTenant, ClassificationCloneResult result) {
        Classification copy = mapper.selectByIdentifier(source.getIdentifier(), targetTenant);
        if (copy == null) {
            copy = new Classification();
            copy.setCode(source.getCode());
            copy.setName(source.getName());
            copy.setDisplayName(source.getDisplayName());
            copy.setDescription(source.getDescription());
            copy.setIdentifier(source.getIdentifier());
            copy.setThumbnail(source.getThumbnail());
            copy.setParentOid(newParentOid);
            copy.setSortOrder(source.getSortOrder());
            copy.setTenantOid(targetTenant);
            mapper.insert(copy);
            result.incrementClassification();
        }
        // 增量补齐 IBA 定义、关联与页面布局
        syncIbasAndLayouts(source, copy, targetTenant, result);

        // 递归克隆子分类（按平台租户查询源子节点）
        List<Classification> children = mapper.selectByParentOid(source.getOid(), TenantContext.PLATFORM_TENANT_OID);
        for (Classification child : children) {
            cloneSubtree(child, copy.getOid(), targetTenant, result);
        }
    }

    /** 增量补齐目标节点的 IBA 定义、关联与页面布局（已存在的跳过） */
    private void syncIbasAndLayouts(Classification source, Classification copy, String targetTenant, ClassificationCloneResult result) {
        List<IBA> clonedIbas = new ArrayList<>();
        List<ClassificationIBA> sourceMappings = ibaMapper.selectByClassificationOid(source.getOid());
        List<ClassificationIBA> targetMappings = ibaMapper.selectByClassificationOid(copy.getOid());
        Set<String> targetIbaOids = new HashSet<>();
        for (ClassificationIBA m : targetMappings) {
            targetIbaOids.add(m.getIbaOid());
        }

        for (ClassificationIBA m : sourceMappings) {
            IBA sourceIba = ibaDefMapper.selectByOid(m.getIbaOid());
            if (sourceIba == null) continue;
            IBA targetIba = cloneIbaDefinition(sourceIba, targetTenant, result);
            clonedIbas.add(targetIba);
            if (targetIbaOids.contains(targetIba.getOid())) continue;
            ClassificationIBA newMapping = new ClassificationIBA(copy.getOid(), targetIba.getOid());
            newMapping.setTenantOid(targetTenant);
            newMapping.setRequired(m.isRequired());
            newMapping.setDefaultValue(m.getDefaultValue());
            newMapping.setSortOrder(m.getSortOrder());
            ibaMapper.insert(newMapping);
            result.incrementIbaLink();
        }

        // 补齐 create/update/detail 页面布局（缺失的）
        syncClsPageLayouts(copy.getOid(), targetTenant, clonedIbas);
    }

    /** 克隆 IBA 定义到目标租户（按 code 去重，幂等） */
    private IBA cloneIbaDefinition(IBA source, String targetTenant, ClassificationCloneResult result) {
        IBA existing = ibaDefMapper.selectByCode(source.getCode(), targetTenant);
        if (existing != null) return existing;

        IBA copy = new IBA();
        copy.setCode(source.getCode());
        copy.setName(source.getName());
        copy.setDisplayName(source.getDisplayName());
        copy.setDataType(source.getDataType());
        copy.setDefaultValue(source.getDefaultValue());
        copy.setConstraintsJson(source.getConstraintsJson());
        copy.setRequired(source.isRequired());
        copy.setDescription(source.getDescription());
        copy.setSortOrder(source.getSortOrder());
        copy.setEnabled(source.isEnabled());
        copy.setTenantOid(targetTenant);
        ibaDefMapper.insert(copy);
        result.incrementIbaDefinition();
        return copy;
    }

    // ==================== 分类页面布局初始化 ====================

    /** 为分类节点增量补齐 create/update/detail 三个页面布局（已存在的跳过） */
    private void syncClsPageLayouts(String clsOid, String tenantOid, List<IBA> ibas) {
        if (ibas == null || ibas.isEmpty()) return;
        for (String[] op : new String[][]{{"create", "新建页"}, {"update", "编辑页"}, {"detail", "详情页"}}) {
            ClsPageLayout existing = clsPageLayoutMapper.selectByClsAndOperation(clsOid, op[0], tenantOid);
            if (existing != null) continue;
            insertClsPageLayout(clsOid, op[0], op[1], tenantOid, ibas, "detail".equals(op[0]));
        }
    }

    private void insertClsPageLayout(String clsOid, String opCode, String opName,
                                     String tenantOid, List<IBA> ibas, boolean readonly) {
        ClsPageLayout layout = new ClsPageLayout(clsOid, opCode, opName, buildLayoutJson(ibas, readonly));
        layout.setTenantOid(tenantOid);
        clsPageLayoutMapper.insert(layout);
    }

    /** 参考「贴片电阻」样例，按分类 IBA 属性生成表单布局 JSON */
    private String buildLayoutJson(List<IBA> ibas, boolean readonly) {
        try {
            List<Map<String, Object>> children = new ArrayList<>();
            for (IBA iba : ibas) {
                children.add(buildField(iba, readonly));
            }

            Map<String, Object> row = new LinkedHashMap<>();
            row.put("type", "layout-row");
            row.put("label", "布局行");
            row.put("columns", 2);
            row.put("children", children);

            List<Map<String, Object>> fields = new ArrayList<>();
            fields.add(row);

            Map<String, Object> form = new LinkedHashMap<>();
            form.put("name", "编辑表单");
            form.put("fields", fields);
            form.put("enabled", true);

            Map<String, Object> root = new LinkedHashMap<>();
            root.put("form", form);
            return objectMapper.writeValueAsString(root);
        } catch (Exception e) {
            log.warn("生成分类页面布局失败: {}", e.getMessage());
            return "{\"form\":{\"name\":\"编辑表单\",\"fields\":[],\"enabled\":true}}";
        }
    }

    private Map<String, Object> buildField(IBA iba, boolean readonly) {
        String dataType = iba.getDataType() != null ? iba.getDataType() : "STRING";
        String label = (iba.getDisplayName() != null && !iba.getDisplayName().isEmpty())
                ? iba.getDisplayName() : iba.getName();

        Map<String, Object> field = new LinkedHashMap<>();
        field.put("label", label);
        field.put("source", "IBA");
        field.put("dataType", dataType);
        field.put("readonly", readonly);
        field.put("required", iba.isRequired());
        field.put("fieldName", iba.getCode() != null ? iba.getCode().toLowerCase() : "");
        field.put("labelLayout", "horizontal");
        field.put("placeholder", "");
        field.put("uiComponent", readonly ? "input" : uiComponentFor(dataType));
        field.put("defaultValue", "");
        return field;
    }

    private String uiComponentFor(String dataType) {
        switch (dataType) {
            case "FLOAT":
            case "INTEGER":
            case "NUMBER":
                return "input-number";
            case "BOOLEAN":
                return "switch";
            case "ENUM":
                return "select";
            case "DATE":
            case "DATETIME":
                return "datepicker";
            default:
                return "input";
        }
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
