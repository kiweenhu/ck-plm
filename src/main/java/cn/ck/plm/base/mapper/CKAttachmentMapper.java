/*
 * Copyright (c) 2025 深圳乘恺科技有限公司
 * All rights reserved.
 *
 * @author Kiween.Hu; Roney.Liu
 */

package cn.ck.plm.base.mapper;

import cn.ck.plm.base.entity.CKAttachment;

import java.util.List;

/**
 * CKAttachment 附件数据访问接口，定义数据库无关的持久化契约。
 * 附件通过 ownerOid 关联其所属业务对象，可被多种实体复用。
 */
public interface CKAttachmentMapper {

    int insert(CKAttachment attachment);

    int update(CKAttachment attachment);

    int deleteByOid(String oid);

    CKAttachment selectByOid(String oid);

    /** 按所属业务对象 oid 查询该对象的所有附件 */
    List<CKAttachment> selectByOwnerOid(String ownerOid);

    /** 删除某业务对象的全部附件 */
    int deleteByOwnerOid(String ownerOid);
}
