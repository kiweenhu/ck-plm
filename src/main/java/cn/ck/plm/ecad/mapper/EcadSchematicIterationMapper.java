/*
 * Copyright (c) 2026~2028 深圳乘恺科技有限公司
 * All rights reserved.
 *
 * @author Kiween.Hu
 */

package cn.ck.plm.ecad.mapper;

import cn.ck.plm.ecad.entity.EcadSchematicIteration;

import java.util.List;

/**
 * ECAD 原理图迭代（{@code ck_ecad_schematic_iteration}）数据访问接口。
 *
 * <p>版本流转方法签名与文档迭代 Mapper 保持一致，便于检出提供者复用同一套逻辑。
 */
public interface EcadSchematicIterationMapper {

    /** 新增迭代 */
    int insert(EcadSchematicIteration iteration);

    /** 更新迭代 */
    int update(EcadSchematicIteration iteration);

    /** 按 oid 物理删除（取消检出时删除派生的工作副本） */
    int deleteByOid(String oid);

    /** 按 oid 查询 */
    EcadSchematicIteration selectByOid(String oid);

    /** 按 master 查询全部迭代（版本历史） */
    List<EcadSchematicIteration> selectByMasterOid(String masterOid);

    /** 按 master 查询最新迭代（latest = true） */
    EcadSchematicIteration selectLatestByMasterOid(String masterOid);

    /** 查询指定用户检出的迭代（统一检出页使用） */
    List<EcadSchematicIteration> selectCheckedOutByUser(String user);
}
