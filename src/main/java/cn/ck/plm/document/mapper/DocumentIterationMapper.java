/*
 * Copyright (c) 2025 深圳乘恺科技有限公司
 * All rights reserved.
 *
 * @author Kiween.Hu; Roney.Liu
 */

package cn.ck.plm.document.mapper;

import cn.ck.plm.document.entity.DocumentIteration;

import java.util.List;

/**
 * DocumentIteration 子版本数据访问接口，定义数据库无关的持久化契约。
 */
public interface DocumentIterationMapper {

    int insert(DocumentIteration iteration);

    int update(DocumentIteration iteration);

    int deleteByOid(String oid);

    DocumentIteration selectByOid(String oid);

    /** 查询某主对象的最新子版本 */
    DocumentIteration selectLatestByMasterOid(String masterOid);

    /** 查询某主对象的所有子版本（按 revision, iteration 降序） */
    List<DocumentIteration> selectByMasterOid(String masterOid);

    /** 查询指定用户所有检出态的迭代 */
    List<DocumentIteration> selectCheckedOutByUser(String checkedOutBy);
}
