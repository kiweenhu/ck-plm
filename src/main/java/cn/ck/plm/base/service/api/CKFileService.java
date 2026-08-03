/*
 * Copyright (c) 2025 深圳乘恺科技有限公司
 * All rights reserved.
 */

package cn.ck.plm.base.service.api;

import cn.ck.plm.base.entity.CKFile;

/**
 * 主文档文件 CKFile 服务接口。
 */
public interface CKFileService {

    /** 创建文件记录 */
    CKFile create(CKFile file);

    /** 根据 oid 查询 */
    CKFile findByOid(String oid);

    /** 删除文件记录 */
    boolean delete(String oid);
}
