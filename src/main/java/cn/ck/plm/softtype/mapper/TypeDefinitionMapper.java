/*
 * Copyright (c) 2026~2028 深圳乘恺科技有限公司
 * All rights reserved.
 *
 * @author Kiween.Hu; Roney.Liu
 */

package cn.ck.plm.softtype.mapper;

import cn.ck.plm.softtype.entity.TypeDefinition;
import org.apache.ibatis.annotations.Param;

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

    /**
     * 归一 type_kind 大小写为统一的大写形式（幂等）。
     *
     * <p>历史数据中 DOCUMENT / PART / PRODUCT_LINE / PRODUCT_MODEL 以 {@code 'ootb'} 小写存储，
     * 与约定的大写 {@code 'OOTB'} 不一致，导致 {@code isOotb()} 判定、"系统预置类型不可删除"
     * 保护、{@code patchRootTypeCodeForOotb} 等逻辑失效。
     */
    int normalizeTypeKindCase();

    /** 更新类型的父类型 oid（**只表达类型继承/类型树父子**，与域归属无关） */
    int updateParentOid(@Param("oid") String oid, @Param("parentOid") String parentOid);

    /**
     * 更新类型的业务域归属（软引用 {@code ck_business_domain.oid}）。
     *
     * <p>与 {@link #updateParentOid} 分成两个方法/两列：域是业务划分、允许跨域；
     * 类型继承是对象能力的细化 —— 两个正交维度，历史上都由 parent_oid 表达才有那堆问题。
     */
    int updateDomainOid(@Param("oid") String oid, @Param("domainOid") String domainOid);
}
