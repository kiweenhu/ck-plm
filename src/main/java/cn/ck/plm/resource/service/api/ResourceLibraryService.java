/*
 * Copyright (c) 2025 深圳乘恺科技有限公司
 * All rights reserved.
 *
 * @author Kiween.Hu; Roney.Liu
 */

package cn.ck.plm.resource.service.api;

import cn.ck.plm.cls.entity.Classification;

import java.util.List;
import java.util.Map;

/**
 * 企业资源库服务契约。
 *
 * <p>企业资源库包含三个子库：元器件库(COMPONENT) / 标准件库(STD_PART) / 通用件库(GEN_PART)，
 * 各子库管理的对象是 Part 主数据的软类型（元器件=ELECTRONIC，标准件/通用件=STRUCTURAL），
 * 按分类(clsOid)归属，挂靠企业级资源库的对应子库节点。
 *
 * <p>左侧分类树来自「分类管理」中租户为指定资源库绑定的分类根节点子树。
 */
public interface ResourceLibraryService {

    // ==================== 分类绑定 ====================

    /** 查询当前租户为指定资源库绑定的分类根节点及其子树（未绑定时返回 null） */
    Classification getCategoryTree(String resourceCode);

    /** 查询当前租户为指定资源库绑定的根分类 oid（未绑定时返回 null） */
    String getBindingRootOid(String resourceCode);

    /**
     * 一次性查询当前租户所有资源库（COMPONENT / STD_PART / GEN_PART）的分类根节点绑定。
     * 含平台租户回退（资源库是平台级共享容器，缺哪个补哪个）。
     *
     * @return Map&lt;resourceCode, rootClassificationOid&gt;，缺失的 key 表示该资源库未绑定
     */
    Map<String, String> getAllBindings();

    /** 绑定资源库的分类根节点（仅支持 COMPONENT / STD_PART / GEN_PART） */
    void bindCategory(String resourceCode, String rootClassificationOid);

    // ==================== 资源库容器上下文 ====================

    /**
     * 资源库归属上下文（COMPONENT / STD_PART / GEN_PART）：
     * { containerOid, containerType, containerCode, containerName, stageOid, typeCode }
     */
    Map<String, Object> getResourceContext(String resourceCode);

    // ==================== 资源库清单（按资源库对应的对象软类型） ====================

    /**
     * 查询资源库清单：指定分类集合（含子孙，由调用方展平）下、该资源库对应软类型的 Part。
     *
     * @param resourceCode 资源子库 code（COMPONENT→ELECTRONIC / STD_PART→STD_PART / GEN_PART→GEN_PART）
     * @return [{ oid, name, number, description, clsOid, createdAt, creator }]
     */
    List<Map<String, Object>> findItems(String resourceCode, List<String> clsOids, String keyword);
}
