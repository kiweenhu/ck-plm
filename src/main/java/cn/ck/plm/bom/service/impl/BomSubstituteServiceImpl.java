/*
 * Copyright (c) 2025 深圳乘恺科技有限公司
 * All rights reserved.
 *
 * @author Kiween.Hu; Roney.Liu
 */

package cn.ck.plm.bom.service.impl;

import cn.ck.plm.base.util.UserContext;
import cn.ck.plm.bom.dto.BomSubstituteVO;
import cn.ck.plm.bom.entity.BomLinks;
import cn.ck.plm.bom.entity.BomSubstituteLink;
import cn.ck.plm.bom.mapper.BomSubstituteLinkMapper;
import cn.ck.plm.bom.service.api.BomLinksService;
import cn.ck.plm.bom.service.api.BomSubstituteService;
import cn.ck.plm.part.entity.Part;
import cn.ck.plm.part.entity.PartIteration;
import cn.ck.plm.part.service.api.PartService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * {@link BomSubstituteService} 实现。
 *
 * <h3>三个口径</h3>
 * <ol>
 *   <li><b>源件永远来自 BOM 行</b>（{@code link.childPartOid}）：前端传什么都不采信 ——
 *       否则会出现"替代关系挂在这行、被替代的却是另一个零件"这种事后没人查得出的错配。</li>
 *   <li><b>重复设置 = 更新参数</b>：同一个 BOM 行的同一个替代件再设置一次，是改它的
 *       类型/数量/优先级，而不是报"已存在"让用户先去删一遍。</li>
 *   <li><b>写后回读</b>：返回值来自库，不返回入参 —— 前端拿到的就是事实。</li>
 * </ol>
 */
@Service
public class BomSubstituteServiceImpl implements BomSubstituteService {

    private static final Logger log = LoggerFactory.getLogger(BomSubstituteServiceImpl.class);

    /** 替代类型：表里登记的四种（见 schema.sql 注释） */
    private static final String TYPE_EQUIVALENT = "EQUIVALENT";

    @Autowired
    private BomSubstituteLinkMapper mapper;

    @Autowired
    private BomLinksService bomLinksService;

    @Autowired
    private PartService partService;

    @Override
    public List<BomSubstituteVO> listByBomLink(String bomLinkOid) {
        if (isBlank(bomLinkOid)) {
            return List.of();
        }
        List<BomSubstituteVO> list = mapper.selectVoByBomLinkOid(bomLinkOid);
        if (list == null) {
            return List.of();
        }
        for (BomSubstituteVO vo : list) {
            vo.setSubstituteVersion(versionOf(vo.getSubstitutePartOid()));
        }
        return list;
    }

    @Override
    @Transactional
    public BomSubstituteVO add(BomSubstituteLink input) {
        if (input == null || isBlank(input.getBomLinkOid())) {
            throw new IllegalArgumentException("缺少 BOM 行（bomLinkOid）");
        }
        BomLinks link = bomLinksService.getByOid(input.getBomLinkOid());
        if (link == null) {
            throw new IllegalArgumentException("该 BOM 行已不存在（可能已被检出的工作副本取代或删除），请刷新页面后重试");
        }
        if (isBlank(input.getSubstitutePartOid())) {
            throw new IllegalArgumentException("请选择替代件");
        }
        if (input.getSubstitutePartOid().equals(link.getChildPartOid())) {
            throw new IllegalArgumentException("替代件不能是该行自己的子件");
        }
        Part substitute = partService.findByOid(input.getSubstitutePartOid());
        if (substitute == null) {
            throw new IllegalArgumentException("替代件不存在（或不属于当前租户）");
        }

        LocalDateTime now = LocalDateTime.now();
        String user = UserContext.get();
        String type = normalizeType(input.getSubstituteType());

        BomSubstituteVO existing = listByBomLink(input.getBomLinkOid()).stream()
                .filter(v -> input.getSubstitutePartOid().equals(v.getSubstitutePartOid()))
                .findFirst()
                .orElse(null);

        if (existing != null) {
            BomSubstituteLink upd = new BomSubstituteLink();
            upd.setOid(existing.getOid());
            upd.setSubstituteType(type);
            upd.setSubstituteQuantity(input.getSubstituteQuantity() != null
                    ? input.getSubstituteQuantity() : existing.getSubstituteQuantity());
            upd.setSubstituteUnit(input.getSubstituteUnit() != null
                    ? input.getSubstituteUnit() : existing.getSubstituteUnit());
            upd.setPriority(input.getPriority() != null ? input.getPriority() : existing.getPriority());
            upd.setEnabled(input.getEnabled() != null ? input.getEnabled() : Boolean.TRUE);
            upd.setDescription(input.getDescription() != null ? input.getDescription() : existing.getDescription());
            upd.setUpdater(user);
            upd.setUpdatedAt(now);
            mapper.update(upd);
            log.info("局部替代已更新: bomLink={} 替代件={} 类型={}",
                    input.getBomLinkOid(), substitute.getNumber(), type);
        } else {
            // 源件由 BOM 行决定；单位默认与该行一致（1:1 替换最常见）
            input.setSourcePartOid(link.getChildPartOid());
            input.setSubstituteType(type);
            if (input.getSubstituteQuantity() == null) {
                input.setSubstituteQuantity(1.0);
            }
            if (isBlank(input.getSubstituteUnit())) {
                input.setSubstituteUnit(link.getUnit());
            }
            if (input.getEnabled() == null) {
                input.setEnabled(Boolean.TRUE);
            }
            input.setCreator(user);
            input.setCreatedAt(now);
            input.setUpdater(user);
            input.setUpdatedAt(now);
            mapper.insert(input);
            log.info("局部替代已设置: bomLink={} 源件={} 替代件={} 类型={}",
                    input.getBomLinkOid(), link.getChildPartOid(), substitute.getNumber(), type);
        }

        return listByBomLink(input.getBomLinkOid()).stream()
                .filter(v -> input.getSubstitutePartOid().equals(v.getSubstitutePartOid()))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("替代关系写入后未能回读到，请刷新页面后重试"));
    }

    @Override
    public void delete(String oid) {
        if (isBlank(oid)) {
            throw new IllegalArgumentException("缺少替代关系 oid");
        }
        mapper.deleteByOid(oid);
    }

    @Override
    @Transactional
    public int clearByBomLink(String bomLinkOid) {
        if (isBlank(bomLinkOid)) {
            throw new IllegalArgumentException("缺少 BOM 行（bomLinkOid）");
        }
        int removed = mapper.deleteByBomLinkOid(bomLinkOid);
        log.info("局部替代已清空: bomLink={} 删除 {} 条", bomLinkOid, removed);
        return removed;
    }

    /** 替代件当前最新版本的显示版本（列表展示用；查不到就留空，不编造） */
    private String versionOf(String partOid) {
        try {
            PartIteration iteration = partService.findLatestIteration(partOid);
            return iteration == null ? null : iteration.getDisplayVersion();
        } catch (Exception e) {
            log.debug("取替代件版本失败: part={} err={}", partOid, e.getMessage());
            return null;
        }
    }

    /** 替代类型：只认表里登记的四种，其余/为空按「等效替代」 */
    private static String normalizeType(String value) {
        if (value == null) {
            return TYPE_EQUIVALENT;
        }
        String v = value.trim().toUpperCase();
        switch (v) {
            case "EQUIVALENT":
            case "COMPLETE":
            case "PARTIAL":
            case "SUBSTITUTE":
                return v;
            default:
                return TYPE_EQUIVALENT;
        }
    }

    private static boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }
}
