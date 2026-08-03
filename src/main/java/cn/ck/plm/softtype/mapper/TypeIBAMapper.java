/*
 * Copyright (c) 2025 深圳乘恺科技有限公司
 * All rights reserved.
 *
 * @author Kiween.Hu; Roney.Liu
 */

package cn.ck.plm.softtype.mapper;

import cn.ck.plm.softtype.entity.TypeIBA;

import java.util.List;

/**
 * 类型-属性关联数据访问接口。
 */
public interface TypeIBAMapper {

    int insert(TypeIBA mapping);

    int update(TypeIBA mapping);

    int deleteByOid(String oid);

    TypeIBA selectByOid(String oid);

    /** 查询某类型下的所有 IBA 关联（含 IBA 基本信息） */
    List<TypeIBA> selectByTypeOid(String typeOid);

    /** 查询某类型下的所有 IBA 关联（含 IBA 基本信息），带 entityCode */
    List<TypeIBA> selectByOwnerOid(String ownerOid, String entityCode);

    /** 查询某 IBA 被哪些类型使用 */
    List<TypeIBA> selectByIbaOid(String ibaOid);

    int existsByTypeAndIba(String typeOid, String ibaOid);

    /** 批量删除某类型的所有 IBA 关联 */
    int deleteByTypeOid(String typeOid);

    /** 递归查询继承链上所有祖先类型的 IBA 映射（不含自身） */
    List<TypeIBA> selectInheritedMappings(String typeOid);

    /** 递归查询继承链上所有祖先类型的 IBA 映射（带 ownerType，不含自身） */
    List<TypeIBA> selectInheritedMappingsByOwner(String ownerOid, String ownerType);
}
