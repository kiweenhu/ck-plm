/*
 * Copyright (c) 2025 深圳乘恺科技有限公司
 * All rights reserved.
 *
 * @author Kiween.Hu; Roney.Liu
 */

package cn.ck.plm.ecad.mapper;

import cn.ck.plm.ecad.entity.EcadProject;

import java.util.List;

/**
 * 电子设计项目（{@code ck_ecad_project}）数据访问接口。
 *
 * <p>{@code ECAD_PROJECT} 是拥有独立表的 OOTB 对象类型（rootTypeCode 为自身），
 * 建表见 {@code EcadSchemaInitializer#createProjectTable}。
 */
public interface EcadProjectMapper {

    /** 新增项目 */
    int insert(EcadProject project);

    /** 更新项目 */
    int update(EcadProject project);

    /** 按 oid 删除 */
    int deleteByOid(String oid);

    /** 按 oid 查询 */
    EcadProject selectByOid(String oid);

    /** 按项目编码查询（code 为业务唯一键） */
    EcadProject selectByCode(String code);

    /** 按所属电子域查询 */
    List<EcadProject> selectByDomainOid(String domainOid);

    /** 按父项目查询（项目分层） */
    List<EcadProject> selectByParentOid(String parentOid);

    /** 按关联产品查询 */
    List<EcadProject> selectByRelatedProduct(String relatedProduct);
}
