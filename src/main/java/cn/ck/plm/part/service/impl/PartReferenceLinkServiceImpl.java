package cn.ck.plm.part.service.impl;

import cn.ck.plm.part.entity.PartReferenceLink;
import cn.ck.plm.part.mapper.PartReferenceLinkMapper;
import cn.ck.plm.part.service.api.PartReferenceLinkService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Service
public class PartReferenceLinkServiceImpl implements PartReferenceLinkService {

    private final PartReferenceLinkMapper referenceLinkMapper;

    public PartReferenceLinkServiceImpl(PartReferenceLinkMapper referenceLinkMapper) {
        this.referenceLinkMapper = referenceLinkMapper;
    }

    @Override
    @Transactional
    public PartReferenceLink create(PartReferenceLink link) {
        if (link.getOid() == null || link.getOid().isEmpty()) {
            link.setOid(UUID.randomUUID().toString());
        }
        if (link.getCreatedAt() == null) {
            link.setCreatedAt(LocalDateTime.now());
        }
        if (link.getUpdatedAt() == null) {
            link.setUpdatedAt(LocalDateTime.now());
        }
        referenceLinkMapper.insert(link);
        return link;
    }

    @Override
    @Transactional
    public PartReferenceLink update(PartReferenceLink link) {
        PartReferenceLink existing = referenceLinkMapper.selectByOid(link.getOid());
        if (existing == null) {
            throw new IllegalArgumentException("参考关系不存在: " + link.getOid());
        }
        existing.setPartIterationOid(link.getPartIterationOid());
        existing.setDocMasterOid(link.getDocMasterOid());
        existing.setDocIterationOid(link.getDocIterationOid());
        existing.setResolvedIterationOid(link.getResolvedIterationOid());
        existing.setCategory(link.getCategory());
        existing.setUpdatedAt(LocalDateTime.now());
        referenceLinkMapper.update(existing);
        return existing;
    }

    @Override
    @Transactional
    public void delete(String oid) {
        referenceLinkMapper.deleteByOid(oid);
    }

    @Override
    public PartReferenceLink findByOid(String oid) {
        return referenceLinkMapper.selectByOid(oid);
    }

    @Override
    public List<PartReferenceLink> findByPartIteration(String partIterationOid) {
        return referenceLinkMapper.selectByPartIterationOid(partIterationOid);
    }

    @Override
    public List<PartReferenceLink> findByDocMaster(String docMasterOid) {
        return referenceLinkMapper.selectByDocMasterOid(docMasterOid);
    }

    @Override
    public List<PartReferenceLink> findByDocIteration(String docIterationOid) {
        return referenceLinkMapper.selectByDocIterationOid(docIterationOid);
    }
}
