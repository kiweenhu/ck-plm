/*
 * Copyright (c) 2025 深圳乘恺科技有限公司
 * All rights reserved.
 *
 * @author Kiween.Hu; Roney.Liu
 */

package cn.ck.plm.resource.entity;

import cn.ck.plm.base.entity.CKContainer;

import java.util.ArrayList;
import java.util.List;

/**
 * 企业级资源库容器（ResourceContainer），继承容器基类 {@link CKContainer}。
 *
 * <p>资源体系以「企业资源库」为根节点（code=CORP_RESOURCE，containerType=CORP_RESOURCE），
 * 下挂若干资源子库节点（parentOid 自引用）：
 * <pre>
 * CORP_RESOURCE  企业资源库（根）
 *   ├── COMPONENT       元器件库        （可绑定分类树）
 *   ├── PACKAGE_SYMBOL  封装·图符库
 *   ├── STD_PART        标准件库        （可绑定分类树）
 *   ├── GEN_PART        通用件库        （可绑定分类树）
 *   ├── TECH_DOC        技术文档知识库
 *   ├── MEDIA           产品图册
 *   └── OTHER           其他
 * </pre>
 *
 * <h3>说明</h3>
 * 资源子库是业务对象（如 ELECTRONIC 软类型的 Part）的归属容器：
 * 主数据通过 containerOid 指向具体资源子库节点，containerType 统一为 CORP_RESOURCE。
 */
public class ResourceContainer extends CKContainer {

    /** 资源库根节点 code */
    public static final String ROOT_CODE = "CORP_RESOURCE";

    /** 资源子库 code 常量 */
    public static final String COMPONENT = "COMPONENT";            // 元器件库
    public static final String PACKAGE_SYMBOL = "PACKAGE_SYMBOL";  // 封装·图符库
    public static final String STD_PART = "STD_PART";              // 标准件库
    public static final String GEN_PART = "GEN_PART";              // 通用件库
    public static final String TECH_DOC = "TECH_DOC";              // 技术文档知识库
    public static final String MEDIA = "MEDIA";                    // 产品图册
    public static final String OTHER = "OTHER";                    // 其他

    /** 容器类型（资源体系统一取值） */
    public static final String CONTAINER_TYPE = "CORP_RESOURCE";

    /** 排序序号（同级展示顺序） */
    private Integer sortOrder;

    /** 子节点列表（类型具体化） */
    private transient List<ResourceContainer> resourceChildren = new ArrayList<>();

    public ResourceContainer() {
        super();
        setContainerType(CONTAINER_TYPE);
    }

    public Integer getSortOrder() { return sortOrder; }
    public void setSortOrder(Integer sortOrder) { this.sortOrder = sortOrder; }

    public List<ResourceContainer> getResourceChildren() { return resourceChildren; }
    public void setResourceChildren(List<ResourceContainer> resourceChildren) {
        this.resourceChildren = resourceChildren;
    }

    /**
     * 预置资源子库定义（code → 名称），供初始化使用。
     * 顺序即展示顺序。
     */
    public static List<String[]> defaultChildren() {
        List<String[]> list = new ArrayList<>();
        list.add(new String[]{COMPONENT, "元器件库"});
        list.add(new String[]{PACKAGE_SYMBOL, "封装·图符库"});
        list.add(new String[]{STD_PART, "标准件库"});
        list.add(new String[]{GEN_PART, "通用件库"});
        list.add(new String[]{TECH_DOC, "技术文档知识库"});
        list.add(new String[]{MEDIA, "产品图册"});
        list.add(new String[]{OTHER, "其他"});
        return list;
    }

    /** 支持绑定分类树的资源子库 code（元器件/标准件/通用件） */
    public static boolean supportsCategoryBinding(String code) {
        return COMPONENT.equals(code) || STD_PART.equals(code) || GEN_PART.equals(code);
    }
}
