/*
 * Copyright (c) 2025 深圳乘恺科技有限公司
 * All rights reserved.
 *
 * @author Kiween.Hu; Roney.Liu
 */

package cn.ck.plm.softtype.service.api;

import cn.ck.plm.softtype.entity.TypeDefinition;

import java.util.List;

/**
 * 类型定义业务接口。
 */
public interface TypeDefinitionService {

    /** 创建类型定义 */
    TypeDefinition create(TypeDefinition td);

    /** 更新类型定义 */
    TypeDefinition update(TypeDefinition td);

    /** 删除类型定义（含级联检查） */
    boolean delete(String oid);

    /** 按 oid 查询 */
    TypeDefinition findByOid(String oid);

    /** 按 code 查询 */
    TypeDefinition findByCode(String code);

    /** 查询全部 */
    List<TypeDefinition> findAll();

    /** 查询启用的 */
    List<TypeDefinition> findEnabled();

    /** 按 typeKind 筛选 */
    List<TypeDefinition> findByTypeKind(String typeKind);

    /** 查询 OOTB 根类型 */
    List<TypeDefinition> findRoots();

    /** 查询某父类型下的子类型 */
    List<TypeDefinition> findChildren(String parentOid);

    /** 查询完整树（OOTB 根 + 递归子节点） */
    List<TypeDefinition> findTree();
}
