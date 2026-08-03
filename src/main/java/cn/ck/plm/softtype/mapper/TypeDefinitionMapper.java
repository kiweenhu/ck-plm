/*
 * Copyright (c) 2025 深圳乘恺科技有限公司
 * All rights reserved.
 *
 * @author Kiween.Hu; Roney.Liu
 */

package cn.ck.plm.softtype.mapper;

import cn.ck.plm.softtype.entity.TypeDefinition;
import java.util.List;

/**
 * 类型定义数据访问接口。
 */
public interface TypeDefinitionMapper {

    int insert(TypeDefinition td);

    int update(TypeDefinition td);

    int deleteByOid(String oid);

    TypeDefinition selectByOid(String oid);

    /**
     * 按 code 查询（优先本租户，无则回退平台租户）
     */
    TypeDefinition selectByCode(String code, String tenantOid, String platformOid);

    /**
     * 查询全部类型（平台 + 本租户），优先本租户
     */
    List<TypeDefinition> selectAll(String tenantOid, String platformOid);

    /**
     * 查询已启用类型（平台 + 本租户），优先本租户
     */
    List<TypeDefinition> selectEnabled(String tenantOid, String platformOid);

    List<TypeDefinition> selectByTypeKind(String typeKind, String tenantOid, String platformOid);

    List<TypeDefinition> selectByParentOid(String parentOid, String tenantOid, String platformOid);

    List<TypeDefinition> selectRoots(String tenantOid, String platformOid);

    int existsByCode(String code, String tenantOid, String platformOid);

    int countChildren(String oid);

    int countIbaMappings(String oid);

    /** 添加 root_type_code 列（兼容旧表） */
    void addRootTypeCodeColumn();

    /** 更新类型的根 OOTB 类型 code */
    int updateRootTypeCode(String oid, String rootTypeCode);

    /** 为已有 OOTB 类型补充 root_type_code = code（兼容旧数据） */
    int patchRootTypeCodeForOotb();
}
