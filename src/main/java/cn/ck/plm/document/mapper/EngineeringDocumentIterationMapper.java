/*
 * Copyright (c) 2025 深圳乘恺科技有限公司
 * All rights reserved.
 *
 * @author Kiween.Hu; Roney.Liu
 */

package cn.ck.plm.document.mapper;

import cn.ck.plm.document.entity.EngineeringDocumentIteration;

import java.util.List;

/**
 * 工程数据子版本（{@code ck_eng_document_iteration}）数据访问接口。
 *
 * <p>参照 Windchill {@code wt.epm.EPMDocument}，与 {@link DocumentIterationMapper} 并列。
 */
public interface EngineeringDocumentIterationMapper {

    /** 新增子版本 */
    int insert(EngineeringDocumentIteration iteration);

    /** 更新子版本可变字段（版本号、检出状态、生命周期、CAD 属性等） */
    int update(EngineeringDocumentIteration iteration);

    /** 按 oid 删除 */
    int deleteByOid(String oid);

    /** 按 oid 查询 */
    EngineeringDocumentIteration selectByOid(String oid);

    /** 查询主对象的最新子版本（latest = TRUE） */
    EngineeringDocumentIteration selectLatestByMasterOid(String masterOid);

    /** 查询主对象的全部子版本（按 revision / iteration 降序） */
    List<EngineeringDocumentIteration> selectByMasterOid(String masterOid);

    /** 查询用户当前检出的全部工程数据子版本 */
    List<EngineeringDocumentIteration> selectCheckedOutByUser(String checkedOutBy);
}
