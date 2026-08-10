/*
 * Copyright (c) 2025 深圳乘恺科技有限公司
 * All rights reserved.
 *
 * @author Kiween.Hu; Roney.Liu
 */

package cn.ck.plm.bom.service.api;

import cn.ck.plm.bom.entity.BomSnapshot;

/**
 * BomSnapshot 业务服务接口。
 */
public interface BomSnapshotService {

    /** 保存或更新 BOM 快照（upsert） */
    BomSnapshot save(BomSnapshot snapshot);

    /** 删除指定迭代的 BOM 快照 */
    void deleteByIterationOid(String iterationOid);

    /** 获取指定迭代的 BOM 快照 */
    BomSnapshot getByIterationOid(String iterationOid);
}
