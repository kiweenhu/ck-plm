/*
 * Copyright (c) 2025 深圳乘恺科技有限公司
 * All rights reserved.
 *
 * @author Kiween.Hu; Roney.Liu
 */

package cn.ck.plm.softtype.service.api;

import cn.ck.plm.softtype.entity.IBA;
import cn.ck.plm.softtype.entity.TypeIBA;

import java.util.List;

/**
 * 可互换属性（IBA）与映射服务接口。
 */
public interface IBAService {

    // ==================== IBA 管理 ====================

    IBA create(IBA iba);

    IBA update(IBA iba);

    boolean delete(String oid);

    IBA findByOid(String oid);

    IBA findByCode(String code);

    List<IBA> findAll();

    List<IBA> findEnabled();

    List<IBA> search(String keyword);

    boolean existsByCode(String code);

    // ==================== 类型-IBA 映射管理 ====================

    /** 为类型分配 IBA */
    TypeIBA assignIba(TypeIBA mapping);

    /** 更新映射（覆写 required / defaultValue） */
    TypeIBA updateMapping(TypeIBA mapping);

    /** 移除类型上的某个 IBA 关联 */
    boolean removeMapping(String mappingOid);

    /** 查询类型关联的所有 IBA（含继承的） */
    List<IBA> findIbasByTypeOid(String typeOid);

    /** 查询类型关联的 IBA 映射列表（含 IBA 信息） */
    List<TypeIBA> findMappingsByTypeOid(String typeOid);

    /** 查询类型关联的 IBA 映射列表（带 entityCode） */
    List<TypeIBA> findMappingsByOwner(String ownerOid, String entityCode);

    /** 批量分配 IBA */
    List<TypeIBA> batchAssign(String typeOid, List<String> ibaOids);

    /** 批量分配 IBA（带 entityCode） */
    List<TypeIBA> batchAssign(String ownerOid, String entityCode, List<String> ibaOids);

    /** 查询尚未分配给指定类型的可用 IBA */
    List<IBA> findUnassignedIbas(String typeOid, String keyword);

    /** 查询尚未分配给指定类型的可用 IBA（带 entityCode） */
    List<IBA> findUnassignedIbas(String ownerOid, String entityCode, String keyword);

    /** 查询 IBA 被哪些类型使用 */
    List<TypeIBA> findTypesByIbaOid(String ibaOid);

    /** 递归查询类型继承链上祖先类型的 IBA 映射（不含自身） */
    List<TypeIBA> findInheritedMappings(String typeOid);

    /** 递归查询类型继承链上祖先类型的 IBA 映射（带 entityCode，不含自身） */
    List<TypeIBA> findInheritedMappingsByOwner(String ownerOid, String entityCode);
}
