/*
 * Copyright (c) 2025 深圳乘恺科技有限公司
 * All rights reserved.
 *
 * @author Kiween.Hu; Roney.Liu
 */

package cn.ck.plm.bom.service.impl;

import cn.ck.plm.bom.entity.BomLinks;
import cn.ck.plm.bom.mapper.BomLinksMapper;
import cn.ck.plm.bom.service.api.BomLinksService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Date;
import java.util.List;

/**
 * BomLinks 业务服务实现。
 */
@Service
@Transactional
public class BomLinksServiceImpl implements BomLinksService {

    @Autowired
    private BomLinksMapper bomLinksMapper;

    @Override
    public BomLinks create(BomLinks bomLinks) {
        Date now = new Date();
        bomLinks.setCreatedAt(now);
        bomLinks.setUpdatedAt(now);
        bomLinksMapper.insert(bomLinks);
        return bomLinks;
    }

    @Override
    public BomLinks update(BomLinks bomLinks) {
        bomLinks.setUpdatedAt(new Date());
        bomLinksMapper.update(bomLinks);
        return bomLinks;
    }

    @Override
    public void deleteByOid(String oid) {
        bomLinksMapper.deleteByOid(oid);
    }

    @Override
    public BomLinks getByOid(String oid) {
        return bomLinksMapper.selectByOid(oid);
    }

    @Override
    public List<BomLinks> listByParentIterationOid(String parentIterationOid) {
        return bomLinksMapper.selectByParentIterationOid(parentIterationOid);
    }

    @Override
    public List<BomLinks> listByChildPartOid(String childPartOid) {
        return bomLinksMapper.selectByChildPartOid(childPartOid);
    }

    @Override
    public List<BomLinks> listByChildIterationOid(String childIterationOid) {
        return bomLinksMapper.selectByChildIterationOid(childIterationOid);
    }

    @Override
    public void refreshResolvedIteration(String oid, String resolvedIterationOid) {
        bomLinksMapper.updateResolvedIterationOid(oid, resolvedIterationOid);
    }

    @Override
    public List<BomLinks> listAll() {
        return bomLinksMapper.selectAll();
    }
}
