/*
 * Copyright (c) 2025 深圳乘恺科技有限公司
 * All rights reserved.
 *
 * @author Kiween.Hu; Roney.Liu
 */

package cn.ck.plm.bom.service.impl;

import cn.ck.plm.bom.entity.BomDiff;
import cn.ck.plm.bom.mapper.BomDiffMapper;
import cn.ck.plm.bom.service.api.BomDiffService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * BomDiff 业务服务实现。
 */
@Service
@Transactional
public class BomDiffServiceImpl implements BomDiffService {

    @Autowired
    private BomDiffMapper bomDiffMapper;

    @Override
    public BomDiff create(BomDiff diff) {
        LocalDateTime now = LocalDateTime.now();
        diff.setCreatedAt(now);
        diff.setUpdatedAt(now);
        bomDiffMapper.insert(diff);
        return diff;
    }

    @Override
    public void deleteByOid(String oid) {
        bomDiffMapper.deleteByOid(oid);
    }

    @Override
    public BomDiff getByOid(String oid) {
        return bomDiffMapper.selectByOid(oid);
    }

    @Override
    public List<BomDiff> listByFromIteration(String fromIterationOid) {
        return bomDiffMapper.selectByFromIterationOid(fromIterationOid);
    }

    @Override
    public List<BomDiff> listByToIteration(String toIterationOid) {
        return bomDiffMapper.selectByToIterationOid(toIterationOid);
    }

    @Override
    public BomDiff getByFromAndTo(String fromIterationOid, String toIterationOid) {
        return bomDiffMapper.selectByFromAndTo(fromIterationOid, toIterationOid);
    }
}
