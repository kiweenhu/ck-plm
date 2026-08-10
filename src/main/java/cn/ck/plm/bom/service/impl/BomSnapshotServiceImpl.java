/*
 * Copyright (c) 2025 深圳乘恺科技有限公司
 * All rights reserved.
 *
 * @author Kiween.Hu; Roney.Liu
 */

package cn.ck.plm.bom.service.impl;

import cn.ck.plm.bom.entity.BomSnapshot;
import cn.ck.plm.bom.mapper.BomSnapshotMapper;
import cn.ck.plm.bom.service.api.BomSnapshotService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * BomSnapshot 业务服务实现。
 */
@Service
@Transactional
public class BomSnapshotServiceImpl implements BomSnapshotService {

    @Autowired
    private BomSnapshotMapper bomSnapshotMapper;

    @Override
    public BomSnapshot save(BomSnapshot snapshot) {
        LocalDateTime now = LocalDateTime.now();
        if (snapshot.getCreatedAt() == null) {
            snapshot.setCreatedAt(now);
        }
        snapshot.setUpdatedAt(now);
        bomSnapshotMapper.upsert(snapshot);
        return snapshot;
    }

    @Override
    public void deleteByIterationOid(String iterationOid) {
        bomSnapshotMapper.deleteByIterationOid(iterationOid);
    }

    @Override
    public BomSnapshot getByIterationOid(String iterationOid) {
        return bomSnapshotMapper.selectByIterationOid(iterationOid);
    }
}
