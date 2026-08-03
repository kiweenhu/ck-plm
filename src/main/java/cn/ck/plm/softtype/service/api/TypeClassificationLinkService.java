/*
 * Copyright (c) 2025 深圳乘恺科技有限公司
 * All rights reserved.
 */

package cn.ck.plm.softtype.service.api;

import cn.ck.plm.softtype.entity.TypeClassificationLink;

import java.util.List;

/**
 * 类型-分类关联服务接口。
 *
 * <p>管理 TypeDefinition 与 Classification 的绑定关系，
 * 为该类型创建 Part 实例时自动继承此分类。
 */
public interface TypeClassificationLinkService {

    /**
     * 为类型绑定分类（若已绑定则更新）
     *
     * @param typeOid            类型 OID
     * @param classificationOid  分类 OID
     * @return 关联记录
     */
    TypeClassificationLink bindClassification(String typeOid, String classificationOid);

    /**
     * 解除类型的分类绑定
     *
     * @param typeOid 类型 OID
     */
    void unbindClassification(String typeOid);

    /**
     * 根据类型 OID 查询已绑定的分类
     *
     * @param typeOid 类型 OID
     * @return 关联记录，未绑定时返回 null
     */
    TypeClassificationLink getByTypeOid(String typeOid);

    /**
     * 根据分类 OID 查询所有绑定了该分类的类型
     *
     * @param classificationOid 分类 OID
     * @return 关联记录列表
     */
    List<TypeClassificationLink> listByClassificationOid(String classificationOid);

    /**
     * 查询所有关联
     */
    List<TypeClassificationLink> listAll();
}
