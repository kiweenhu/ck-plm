/*
 * Copyright (c) 2025 深圳乘恺科技有限公司
 * All rights reserved.
 *
 * @author Kiween.Hu; Roney.Liu
 */

package cn.ck.plm.bom.mapper;

import cn.ck.plm.bom.entity.BomSnapshot;

/**
 * BomSnapshot 数据访问接口，定义数据库无关的持久化契约。
 */
public interface BomSnapshotMapper {

    /** 插入或更新快照（iterationOid 唯一，存在则覆盖） */
    int upsert(BomSnapshot snapshot);

    int deleteByIterationOid(String iterationOid);

    BomSnapshot selectByIterationOid(String iterationOid);
}
