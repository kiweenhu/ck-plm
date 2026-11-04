/*
 * Copyright (c) 2026~2028 深圳乘恺科技有限公司
 * All rights reserved.
 *
 * @author Kiween.Hu; Roney.Liu
 */

package cn.ck.plm.base.pack.install;

import cn.ck.plm.base.pack.model.PackManifest;
import cn.ck.plm.cls.entity.Classification;
import cn.ck.plm.cls.entity.ClassificationIBA;
import cn.ck.plm.cls.mapper.ClassificationIBAMapper;
import cn.ck.plm.cls.mapper.ClassificationMapper;
import cn.ck.plm.softtype.entity.IBA;
import cn.ck.plm.softtype.mapper.IBAMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 分类安装器（{@code CLASSIFICATION}）—— 读包内 {@code classifications.json}。
 *
 * <p>落地两样东西：
 * <ol>
 *   <li><b>分类树</b>：根 + 大类 + 细分类。identifier 由「父 identifier + '-' + slug」逐级拼出
 *       （与原先 {@code Category} DSL 的口径一致，如 {@code tpl-electronic-diode-schottky-diode}）；</li>
 *   <li><b>细分类参数（IBA）</b>：先按 code 幂等建属性定义，再把「细分类 → 属性」的绑定按顺序写进
 *       {@code ck_cls_iba}（sortOrder 从 1 起，决定表单里的字段顺序）。</li>
 * </ol>
 *
 * <p><b>数据来源变迁</b>：原先两棵树写在 {@code ClassificationInitializer} 的 {@code Category} DSL 里，
 * 现按归属拆开 —— 电子元器件树（18 大类 / 65 细分类）随 {@code electronics-core}，
 * 结构件树（6 大类 / 16 工艺件）随 {@code platform-base}。换行业时可整体替换分类体系，不必改代码。
 *
 * <h3>幂等与"结构变更"</h3>
 * 沿用原先的语义：<b>根 + 全部节点（按 identifier 逐一核对）都在 → 跳过</b>；
 * 只要缺一个节点，就清掉该根子树后整棵重建（否则新旧混杂、identifier 撞车）。
 * 之所以保留"重建"而不是逐节点补建：identifier 是路径拼出来的，结构改动后旧节点的 identifier
 * 已经不对，逐节点补建会留下两套并存的树。重建范围仅限本包声明的这两个模板根，不碰用户自建分类。
 * 预演（dry-run）下不删不建，只回报将发生什么。
 */
@Component
public class ClassificationInstaller implements ResourceInstaller {

    private static final Logger log = LoggerFactory.getLogger(ClassificationInstaller.class);

    private final ClassificationMapper clsMapper;
    private final ClassificationIBAMapper clsIbaMapper;
    private final IBAMapper ibaMapper;

    public ClassificationInstaller(ClassificationMapper clsMapper,
                                   ClassificationIBAMapper clsIbaMapper,
                                   IBAMapper ibaMapper) {
        this.clsMapper = clsMapper;
        this.clsIbaMapper = clsIbaMapper;
        this.ibaMapper = ibaMapper;
    }

    @Override
    public String resourceType() {
        return "CLASSIFICATION";
    }

    @Override
    public List<InstallResult> install(InstallContext context, PackManifest.Resource resource) {
        List<InstallResult> results = new ArrayList<>();
        Map<String, Object> json;
        try {
            json = context.getSource().readJsonMap(context.getObjectMapper(), resource.getFile());
        } catch (Exception e) {
            results.add(InstallResult.failed(resourceType(), resource.getFile(),
                    "读取模板文件失败: " + e.getMessage()));
            return results;
        }
        String tenant = context.getTenantOid();

        // 1) 属性定义（IBA）—— 按 code 幂等，先到先建
        Object ibaDefs = json.get("ibaDefinitions");
        if (ibaDefs instanceof List) {
            for (Object element : (List<?>) ibaDefs) {
                if (!(element instanceof Map)) {
                    continue;
                }
                Map<?, ?> def = (Map<?, ?>) element;
                String code = text(def.get("code"));
                if (code == null) {
                    continue;
                }
                try {
                    if (ibaMapper.selectByCode(code, tenant) != null) {
                        results.add(InstallResult.skipped("IBA", code, "属性定义已存在"));
                        continue;
                    }
                    if (context.isDryRun()) {
                        results.add(InstallResult.inserted("IBA", code)
                                .why("预演：将新增属性「" + text(def.get("name")) + "」"));
                        continue;
                    }
                    IBA iba = new IBA();
                    iba.setCode(code);
                    iba.setName(text(def.get("name")));
                    iba.setDisplayName(text(def.get("name")));
                    iba.setDataType(text(def.get("dataType")));
                    iba.setDescription(text(def.get("description")));
                    iba.setTenantOid(tenant);
                    iba.setEnabled(true);
                    iba.setRequired(false);
                    ibaMapper.insert(iba);
                    results.add(InstallResult.inserted("IBA", code)
                            .why("属性：" + iba.getName() + "（" + iba.getDataType() + "）")
                            .on("ck_iba", iba.getOid()));
                } catch (Exception e) {
                    results.add(InstallResult.failed("IBA", code,
                            e.getClass().getSimpleName() + ": " + e.getMessage()));
                }
            }
        }

        // 2) 分类树
        Object treeNodes = json.get("classifications");
        if (treeNodes instanceof List) {
            for (Object element : (List<?>) treeNodes) {
                if (!(element instanceof Map)) {
                    continue;
                }
                Map<?, ?> rootDef = (Map<?, ?>) element;
                String name = text(rootDef.get("name"));
                String identifier = text(rootDef.get("identifier"));
                if (name == null || identifier == null) {
                    continue;
                }
                try {
                    Object children = rootDef.get("children");
                    List<?> childList = children instanceof List ? (List<?>) children : new ArrayList<>();
                    if (isInitialized(identifier, childList, tenant)) {
                        results.add(InstallResult.skipped(resourceType(), identifier,
                                "分类已初始化（根 + 全部节点齐备）"));
                        continue;
                    }
                    if (context.isDryRun()) {
                        results.add(InstallResult.inserted(resourceType(), identifier)
                                .why("预演：将重建分类「" + name + "」（根 + " + childList.size() + " 个大类）"));
                        continue;
                    }
                    int removed = cleanupTree(identifier, tenant);
                    Classification root = newClassification(name, identifier, null, 0, tenant);
                    clsMapper.insert(root);
                    int created = buildChildren(identifier, root.getOid(), childList, 1, tenant);
                    results.add(InstallResult.inserted(resourceType(), identifier)
                            .why("分类树：" + name + "（根 + " + childList.size() + " 个大类 / "
                                    + created + " 个节点" + (removed > 0 ? "，重建前清理 " + removed + " 个旧节点" : "") + "）")
                            .on("ck_classification", root.getOid()));
                } catch (Exception e) {
                    results.add(InstallResult.failed(resourceType(), identifier,
                            e.getClass().getSimpleName() + ": " + e.getMessage()));
                }
            }
        }

        log.debug("分类安装完成: {} 条明细", results.size());
        return results;
    }

    // ==================== 幂等判断（根 + 全部节点） ====================

    private boolean isInitialized(String rootIdentifier, List<?> children, String tenant) {
        if (clsMapper.existsByIdentifier(rootIdentifier, tenant) <= 0) {
            return false;
        }
        for (Object child : children) {
            if (child instanceof Map && !isNodeInitialized(rootIdentifier, (Map<?, ?>) child, tenant)) {
                return false;
            }
        }
        return true;
    }

    private boolean isNodeInitialized(String parentIdentifier, Map<?, ?> node, String tenant) {
        String identifier = parentIdentifier + "-" + str(node.get("slug"));
        if (clsMapper.existsByIdentifier(identifier, tenant) <= 0) {
            return false;
        }
        Object children = node.get("children");
        if (children instanceof List) {
            for (Object child : (List<?>) children) {
                if (child instanceof Map && !isNodeInitialized(identifier, (Map<?, ?>) child, tenant)) {
                    return false;
                }
            }
        }
        return true;
    }

    // ==================== 重建 ====================

    private int cleanupTree(String rootIdentifier, String tenant) {
        Classification root = clsMapper.selectByIdentifier(rootIdentifier, tenant);
        return root == null ? 0 : deleteSubtree(root.getOid(), tenant);
    }

    private int deleteSubtree(String parentOid, String tenant) {
        int count = 0;
        for (Classification child : clsMapper.selectByParentOid(parentOid, tenant)) {
            count += deleteSubtree(child.getOid(), tenant);
        }
        clsMapper.deleteByOid(parentOid);
        return count + 1;
    }

    /** 递归建树：返回新建节点数（含大类与细分类） */
    private int buildChildren(String parentIdentifier, String parentOid, List<?> children,
                              int startSort, String tenant) {
        int sort = startSort;
        int created = 0;
        for (Object element : children) {
            if (!(element instanceof Map)) {
                sort++;
                continue;
            }
            Map<?, ?> node = (Map<?, ?>) element;
            String name = text(node.get("name"));
            String identifier = parentIdentifier + "-" + str(node.get("slug"));
            Classification row = newClassification(name, identifier, parentOid, sort, tenant);
            clsMapper.insert(row);
            created++;
            bindIbas(row.getOid(), node.get("ibas"), tenant);
            Object grandChildren = node.get("children");
            if (grandChildren instanceof List && !((List<?>) grandChildren).isEmpty()) {
                created += buildChildren(identifier, row.getOid(), (List<?>) grandChildren, 1, tenant);
            }
            sort++;
        }
        return created;
    }

    private void bindIbas(String classificationOid, Object ibas, String tenant) {
        if (!(ibas instanceof List)) {
            return;
        }
        int sort = 1;
        for (Object element : (List<?>) ibas) {
            String code = text(element);
            if (code == null) {
                continue;
            }
            IBA iba = ibaMapper.selectByCode(code, tenant);
            if (iba == null) {
                log.warn("未定义的属性 code（分类参数绑定已跳过）: {}", code);
                continue;
            }
            ClassificationIBA mapping = new ClassificationIBA(classificationOid, iba.getOid());
            mapping.setTenantOid(tenant);
            mapping.setSortOrder(sort++);
            clsIbaMapper.insert(mapping);
        }
    }

    private Classification newClassification(String name, String identifier, String parentOid,
                                            int sortOrder, String tenant) {
        Classification c = new Classification();
        c.setCode(identifier);
        c.setName(name);
        c.setDisplayName(name);
        c.setIdentifier(identifier);
        c.setParentOid(parentOid);
        c.setSortOrder(sortOrder);
        c.setTenantOid(tenant);
        return c;
    }

    private static String text(Object value) {
        if (value == null) {
            return null;
        }
        String s = String.valueOf(value).trim();
        return s.isEmpty() ? null : s;
    }

    private static String str(Object value) {
        return value == null ? "" : String.valueOf(value);
    }
}
