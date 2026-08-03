/*
 * Copyright (c) 2025 深圳乘恺科技有限公司
 * All rights reserved.
 *
 * @author Kiween.Hu; Roney.Liu
 */

package cn.ck.plm.base.mapper;

import cn.ck.plm.base.entity.CKFile;

/**
 * CKFile 文件存储数据访问接口，定义数据库无关的持久化契约。
 */
public interface CKFileMapper {

    int insert(CKFile file);

    int update(CKFile file);

    int deleteByOid(String oid);

    CKFile selectByOid(String oid);
}
