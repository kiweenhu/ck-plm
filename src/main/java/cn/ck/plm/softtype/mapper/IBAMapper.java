/*
 * Copyright (c) 2025 深圳乘恺科技有限公司
 * All rights reserved.
 *
 * @author Kiween.Hu; Roney.Liu
 */

package cn.ck.plm.softtype.mapper;

import cn.ck.plm.softtype.entity.IBA;

import java.util.List;

/**
 * 可互换属性（IBA）数据访问接口。
 */
public interface IBAMapper {

    int insert(IBA iba);

    int update(IBA iba);

    int deleteByOid(String oid);

    IBA selectByOid(String oid);

    IBA selectByCode(String code, String tenantOid);

    List<IBA> selectAll();

    List<IBA> selectEnabled();

    /** 模糊搜索 IBA（按 code / name / display_name） */
    List<IBA> search(String keyword);

    int existsByCode(String code, String tenantOid);

    /** 查询某个类型关联的所有 IBA */
    List<IBA> selectByTypeOid(String typeOid);

    /** 查询某个类型关联的所有 IBA（带 entityCode） */
    List<IBA> selectByOwnerOid(String ownerOid, String entityCode);

    /** 查询尚未分配给指定类型的可用 IBA */
    List<IBA> selectUnassignedByTypeOid(String typeOid, String keyword);

    /** 查询尚未分配给指定类型的可用 IBA（带 entityCode） */
    List<IBA> selectUnassignedByOwnerOid(String ownerOid, String entityCode, String keyword);
}
