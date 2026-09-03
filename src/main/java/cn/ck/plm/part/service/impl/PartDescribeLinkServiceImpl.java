package cn.ck.plm.part.service.impl;

import cn.ck.plm.part.entity.PartDescribeLink;
import cn.ck.plm.part.entity.PartReferenceLink;
import cn.ck.plm.part.mapper.PartDescribeLinkMapper;
import cn.ck.plm.part.mapper.PartReferenceLinkMapper;
import cn.ck.plm.part.service.api.PartDescribeLinkService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Service
public class PartDescribeLinkServiceImpl implements PartDescribeLinkService {

    private static final Logger log = LoggerFactory.getLogger(PartDescribeLinkServiceImpl.class);

    private final PartDescribeLinkMapper describeLinkMapper;
    private final PartReferenceLinkMapper referenceLinkMapper;

    public PartDescribeLinkServiceImpl(PartDescribeLinkMapper describeLinkMapper,
                                       PartReferenceLinkMapper referenceLinkMapper) {
        this.describeLinkMapper = describeLinkMapper;
        this.referenceLinkMapper = referenceLinkMapper;
    }

    @Override
    @Transactional
    public PartDescribeLink create(PartDescribeLink link) {
        if (link.getOid() == null || link.getOid().isEmpty()) {
            link.setOid(UUID.randomUUID().toString());
        }
        if (link.getCreatedAt() == null) {
            link.setCreatedAt(LocalDateTime.now());
        }
        if (link.getUpdatedAt() == null) {
            link.setUpdatedAt(LocalDateTime.now());
        }
        describeLinkMapper.insert(link);
        return link;
    }

    @Override
    @Transactional
    public PartDescribeLink update(PartDescribeLink link) {
        PartDescribeLink existing = describeLinkMapper.selectByOid(link.getOid());
        if (existing == null) {
            throw new IllegalArgumentException("定义关系不存在: " + link.getOid());
        }
        existing.setPartIterationOid(link.getPartIterationOid());
        existing.setDocMasterOid(link.getDocMasterOid());
        existing.setDocIterationOid(link.getDocIterationOid());
        existing.setResolvedIterationOid(link.getResolvedIterationOid());
        existing.setCategory(link.getCategory());
        existing.setUpdatedAt(LocalDateTime.now());
        describeLinkMapper.update(existing);
        return existing;
    }

    @Override
    @Transactional
    public void delete(String oid) {
        describeLinkMapper.deleteByOid(oid);
    }

    @Override
    public PartDescribeLink findByOid(String oid) {
        return describeLinkMapper.selectByOid(oid);
    }

    @Override
    public List<PartDescribeLink> findByPartIteration(String partIterationOid) {
        return describeLinkMapper.selectByPartIterationOid(partIterationOid);
    }

    @Override
    public List<PartDescribeLink> findByDocMaster(String docMasterOid) {
        return describeLinkMapper.selectByDocMasterOid(docMasterOid);
    }

    @Override
    public List<PartDescribeLink> findByDocIteration(String docIterationOid) {
        return describeLinkMapper.selectByDocIterationOid(docIterationOid);
    }

    /**
     * 显式晋升：REFERENCE → DESCRIBES。
     *
     * <p>「一个文档从证据变成定义」业务上是一次技术状态变更，必须留痕。此处以受控动作实现：
     * 读取原 REFERENCE 关系，复制为 DESCRIBES 关系并落库，随后删除原 REFERENCE 关系。
     */
    @Override
    @Transactional
    public PartDescribeLink promote(String referenceLinkOid) {
        PartReferenceLink ref = referenceLinkMapper.selectByOid(referenceLinkOid);
        if (ref == null) {
            throw new IllegalArgumentException("参考关系不存在: " + referenceLinkOid);
        }

        PartDescribeLink desc = new PartDescribeLink();
        desc.setOid(UUID.randomUUID().toString());
        desc.setPartIterationOid(ref.getPartIterationOid());
        desc.setDocMasterOid(ref.getDocMasterOid());
        desc.setDocIterationOid(ref.getDocIterationOid());
        desc.setResolvedIterationOid(ref.getResolvedIterationOid());
        desc.setCategory(ref.getCategory());
        desc.setTenantOid(ref.getTenantOid());
        desc.setCreator(ref.getCreator());
        desc.setCreatedAt(LocalDateTime.now());
        desc.setUpdatedAt(LocalDateTime.now());
        describeLinkMapper.insert(desc);

        referenceLinkMapper.deleteByOid(referenceLinkOid);

        log.info("文档关系晋升 REFERENCE → DESCRIBES: refOid={}, descOid={}, partIterationOid={}",
                referenceLinkOid, desc.getOid(), desc.getPartIterationOid());
        return desc;
    }
}
