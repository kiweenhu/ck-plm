/*
 * Copyright (c) 2026~2028 深圳乘恺科技有限公司
 * All rights reserved.
 *
 * @author Kiween.Hu
 */

package cn.ck.plm.ecad.mapper;

import cn.ck.plm.ecad.entity.EcadSchematic;

import java.util.List;

/**
 * ECAD 原理图主数据（{@code ck_ecad_schematic}）数据访问接口。
 */
public interface EcadSchematicMapper {

    /** 新增原理图 */
    int insert(EcadSchematic schematic);

    /** 更新原理图 */
    int update(EcadSchematic schematic);

    /** 逻辑删除 */
    int markDeleted(String oid);

    /** 按 oid 查询 */
    EcadSchematic selectByOid(String oid);

    /** 按编号查询 */
    EcadSchematic selectByNumber(String number);

    /** 按设计项目查询（含未逻辑删除的） */
    List<EcadSchematic> selectByProjectOid(String projectOid);
}
