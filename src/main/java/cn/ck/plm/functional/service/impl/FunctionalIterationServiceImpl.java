/*
 * Copyright (c) 2025 深圳乘恺科技有限公司
 * All rights reserved.
 */

package cn.ck.plm.functional.service.impl;

import cn.ck.plm.functional.entity.FunctionalIteration;
import cn.ck.plm.functional.mapper.FunctionalIterationMapper;
import cn.ck.plm.functional.service.api.FunctionalIterationService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
public class FunctionalIterationServiceImpl implements FunctionalIterationService {

    private final FunctionalIterationMapper mapper;

    public FunctionalIterationServiceImpl(FunctionalIterationMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    @Transactional
    public FunctionalIteration create(FunctionalIteration iteration) {
        iteration.setOid(UUID.randomUUID().toString());
        mapper.insert(iteration);
        return iteration;
    }

    @Override
    @Transactional
    public FunctionalIteration update(FunctionalIteration iteration) {
        mapper.update(iteration);
        return mapper.selectByOid(iteration.getOid());
    }

    @Override
    @Transactional
    public void delete(String oid) {
        mapper.deleteByOid(oid);
    }

    @Override
    public FunctionalIteration findByOid(String oid) {
        return mapper.selectByOid(oid);
    }

    @Override
    public FunctionalIteration findLatestByMasterOid(String masterOid) {
        return mapper.selectLatestByMasterOid(masterOid);
    }

    @Override
    public List<FunctionalIteration> findByMasterOid(String masterOid) {
        return mapper.selectByMasterOid(masterOid);
    }
}
