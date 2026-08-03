/*
 * Copyright (c) 2025 深圳乘恺科技有限公司
 * All rights reserved.
 */

package cn.ck.plm.base.service.api;

import cn.ck.plm.base.entity.CKAttachment;

import java.util.List;

/**
 * CKAttachment 附件服务接口。
 */
public interface CKAttachmentService {

    CKAttachment create(CKAttachment attachment);

    CKAttachment findByOid(String oid);

    List<CKAttachment> findByOwner(String ownerOid);

    boolean delete(String oid);

    /** 绑定附件到业务对象（设置 ownerOid） */
    void bindToOwner(String attachmentOid, String ownerOid);
}
