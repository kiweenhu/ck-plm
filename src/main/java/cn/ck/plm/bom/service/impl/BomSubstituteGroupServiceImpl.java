/*
 * Copyright (c) 2025 深圳乘恺科技有限公司
 * All rights reserved.
 *
 * @author Kiween.Hu; Roney.Liu
 */

package cn.ck.plm.bom.service.impl;

import cn.ck.plm.base.util.UserContext;
import cn.ck.plm.bom.dto.BomSubstituteGroupRequest;
import cn.ck.plm.bom.dto.BomSubstituteGroupVO;
import cn.ck.plm.bom.entity.BomLinks;
import cn.ck.plm.bom.entity.BomSubstituteGroup;
import cn.ck.plm.bom.entity.BomSubstituteGroupMember;
import cn.ck.plm.bom.mapper.BomSubstituteGroupMapper;
import cn.ck.plm.bom.service.api.BomLinksService;
import cn.ck.plm.bom.service.api.BomSubstituteGroupService;
import cn.ck.plm.part.entity.PartIteration;
import cn.ck.plm.part.service.api.PartService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * {@link BomSubstituteGroupService} 实现。
 *
 * <h3>几条口径（写下来免得后人猜）</h3>
 * <ol>
 *   <li><b>原料侧必须来自本迭代</b>：组挂在父件迭代上，成员却指 BOM 行 —— 若采信前端传来的
 *       任意行 oid，就会做出"这个版本的成组替代里躺着另一个版本的行"，事后没人查得出。</li>
 *   <li><b>两侧都要有成员</b>：只有原料侧没有替代侧（或反过来）不成一组，直接拒掉。</li>
 *   <li><b>改动已批准的组 → 退回 DRAFT</b>：批准是对"当时那份内容"的批准，内容变了就得重新批。
 *       否则"批准"会变成一次性的橡皮图章。</li>
 *   <li><b>成员整体替换</b>：更新时先清空再重建，不玩增量 diff。</li>
 *   <li><b>写后回读</b>：返回值一律取自库。</li>
 * </ol>
 */
@Service
public class BomSubstituteGroupServiceImpl implements BomSubstituteGroupService {

    private static final Logger log = LoggerFactory.getLogger(BomSubstituteGroupServiceImpl.class);

    /** 成员侧（与表的 CHECK 约束同一个字面量） */
    private static final String SIDE_SOURCE = "SOURCE";
    private static final String SIDE_SUBSTITUTE = "SUBSTITUTE";

    /** 组状态（见 BomSubstituteGroup 的注释） */
    private static final String STATUS_DRAFT = "DRAFT";
    private static final String STATUS_APPROVED = "APPROVED";
    private static final String STATUS_OBSOLETE = "OBSOLETE";
    private static final Set<String> STATUSES = Set.of(STATUS_DRAFT, STATUS_APPROVED, STATUS_OBSOLETE);

    /** 成员排序步长：与 BOM 行号 10/20/30 的惯例保持一致 */
    private static final int SORT_STEP = 10;

    @Autowired
    private BomSubstituteGroupMapper mapper;

    @Autowired
    private BomLinksService bomLinksService;

    @Autowired
    private PartService partService;

    // ==================== 查询 ====================

    @Override
    public List<BomSubstituteGroupVO> listByParentIteration(String parentIterationOid) {
        if (isBlank(parentIterationOid)) {
            return List.of();
        }
        List<BomSubstituteGroupVO> groups = mapper.selectVoByParentIterationOid(parentIterationOid);
        attachMembers(groups);
        return groups;
    }

    // ==================== 写入 ====================

    @Override
    @Transactional
    public BomSubstituteGroupVO create(BomSubstituteGroupRequest request) {
        if (request == null || isBlank(request.getParentIterationOid())) {
            throw new IllegalArgumentException("缺少父件迭代（parentIterationOid）");
        }
        Map<String, BomLinks> sourceLinks = validateAndLoadSourceLinks(request, request.getParentIterationOid());

        LocalDateTime now = LocalDateTime.now();
        String user = UserContext.get();

        BomSubstituteGroup group = new BomSubstituteGroup();
        group.setOid(UUID.randomUUID().toString());
        group.setName(isBlank(request.getName()) ? defaultName(sourceLinks) : request.getName());
        group.setDescription(request.getDescription());
        group.setParentIterationOid(request.getParentIterationOid());
        // 新建一律从草稿开始：批准是一个显式动作，不该在创建时顺手发生
        group.setStatus(STATUS_DRAFT);
        group.setAtomicReplace(request.getAtomicReplace() != null ? request.getAtomicReplace() : Boolean.TRUE);
        group.setEnabled(request.getEnabled() != null ? request.getEnabled() : Boolean.TRUE);
        group.setCreator(user);
        group.setCreatedAt(now);
        group.setUpdater(user);
        group.setUpdatedAt(now);
        mapper.insertGroup(group);
        insertMembers(group.getOid(), request, sourceLinks, user, now);

        log.info("成组替代已新建: group={} 原料侧={} 替代侧={} 整组替换={}",
                group.getOid(), request.getSources().size(), request.getSubstitutes().size(),
                group.getAtomicReplace());
        return getByOid(group.getOid());
    }

    @Override
    @Transactional
    public BomSubstituteGroupVO update(String oid, BomSubstituteGroupRequest request) {
        if (isBlank(oid)) {
            throw new IllegalArgumentException("缺少成组替代 oid");
        }
        BomSubstituteGroupVO existing = mapper.selectVoByOid(oid);
        if (existing == null) {
            throw new IllegalArgumentException("该成组替代已不存在（可能已被删除），请刷新页面后重试");
        }
        if (request == null) {
            throw new IllegalArgumentException("缺少请求内容");
        }
        // 挂载点以库里为准：组属于哪个版本是既成事实，不接受前端改挂到别的版本上
        Map<String, BomLinks> sourceLinks = validateAndLoadSourceLinks(request, existing.getParentIterationOid());

        LocalDateTime now = LocalDateTime.now();
        String user = UserContext.get();
        boolean backToDraft = STATUS_APPROVED.equalsIgnoreCase(existing.getStatus());

        BomSubstituteGroup group = new BomSubstituteGroup();
        group.setOid(oid);
        group.setName(isBlank(request.getName()) ? existing.getName() : request.getName());
        group.setDescription(request.getDescription());
        group.setStatus(backToDraft ? STATUS_DRAFT : existing.getStatus());
        group.setAtomicReplace(request.getAtomicReplace() != null
                ? request.getAtomicReplace() : existing.getAtomicReplace());
        group.setEnabled(request.getEnabled() != null ? request.getEnabled() : existing.getEnabled());
        group.setUpdater(user);
        group.setUpdatedAt(now);
        mapper.updateGroup(group);

        // 成员整体替换：先清空再按请求重建 —— 不留"删了一半"的中间态，也不用推敲增删差异
        mapper.deleteMembersByGroupOid(oid);
        insertMembers(oid, request, sourceLinks, user, now);

        log.info("成组替代已更新: group={} 原料侧={} 替代侧={} 状态={} 已退回草稿={}",
                oid, request.getSources().size(), request.getSubstitutes().size(),
                group.getStatus(), backToDraft);
        return getByOid(oid);
    }

    @Override
    @Transactional
    public void delete(String oid) {
        if (isBlank(oid)) {
            throw new IllegalArgumentException("缺少成组替代 oid");
        }
        int removed = mapper.deleteGroupByOid(oid);
        log.info("成组替代已删除: group={} 影响 {} 行（成员随组级联删除）", oid, removed);
    }

    @Override
    @Transactional
    public BomSubstituteGroupVO changeStatus(String oid, String status) {
        if (isBlank(oid)) {
            throw new IllegalArgumentException("缺少成组替代 oid");
        }
        String next = normalizeStatus(status);
        BomSubstituteGroupVO existing = mapper.selectVoByOid(oid);
        if (existing == null) {
            throw new IllegalArgumentException("该成组替代已不存在（可能已被删除），请刷新页面后重试");
        }
        if (STATUS_APPROVED.equals(next)) {
            // 批准的是"整组替换"这件事：两侧都得有成员，缺一侧的组批准了也没有意义
            attachMembers(List.of(existing));
            if (existing.getSources().isEmpty() || existing.getSubstitutes().isEmpty()) {
                throw new IllegalStateException("组内两侧成员不完整，不能批准（原料侧与替代侧都至少需要一个成员）");
            }
        }
        BomSubstituteGroup group = new BomSubstituteGroup();
        group.setOid(oid);
        group.setName(existing.getName());
        group.setDescription(existing.getDescription());
        group.setStatus(next);
        group.setAtomicReplace(existing.getAtomicReplace());
        group.setEnabled(existing.getEnabled());
        group.setUpdater(UserContext.get());
        group.setUpdatedAt(LocalDateTime.now());
        mapper.updateGroup(group);
        log.info("成组替代状态变更: group={} {} -> {}", oid, existing.getStatus(), next);
        return getByOid(oid);
    }

    // ==================== 内部 ====================

    /** 单个组（写后回读用） */
    private BomSubstituteGroupVO getByOid(String oid) {
        BomSubstituteGroupVO vo = mapper.selectVoByOid(oid);
        if (vo == null) {
            throw new IllegalStateException("成组替代写入后未能回读到，请刷新页面后重试");
        }
        attachMembers(List.of(vo));
        return vo;
    }

    /** 把成员按两侧分装到各组里（成员一次批量取回，避免逐组查询） */
    private void attachMembers(List<BomSubstituteGroupVO> groups) {
        if (groups == null || groups.isEmpty()) {
            return;
        }
        List<String> oids = new ArrayList<>(groups.size());
        for (BomSubstituteGroupVO g : groups) {
            oids.add(g.getOid());
        }
        List<BomSubstituteGroupVO.MemberVO> members = mapper.selectMembersByGroupOids(String.join(",", oids));
        Map<String, List<BomSubstituteGroupVO.MemberVO>> byGroup = new HashMap<>();
        if (members != null) {
            for (BomSubstituteGroupVO.MemberVO m : members) {
                byGroup.computeIfAbsent(m.getGroupOid(), k -> new ArrayList<>()).add(m);
            }
        }
        for (BomSubstituteGroupVO g : groups) {
            for (BomSubstituteGroupVO.MemberVO m : byGroup.getOrDefault(g.getOid(), List.of())) {
                if (SIDE_SOURCE.equalsIgnoreCase(m.getMemberSide())) {
                    g.getSources().add(m);
                } else {
                    g.getSubstitutes().add(m);
                }
            }
            for (BomSubstituteGroupVO.MemberVO m : g.getSubstitutes()) {
                m.setPartVersion(versionOf(m.getPartOid()));
            }
        }
    }

    /**
     * 校验两侧成员，并返回原料侧的 BOM 行（后续建成员时直接复用，不重复查库）。
     *
     * @param parentIterationOid 该组所属父件迭代 —— 原料侧的行必须属于它
     */
    private Map<String, BomLinks> validateAndLoadSourceLinks(BomSubstituteGroupRequest request, String parentIterationOid) {
        if (request.getSources() == null || request.getSources().isEmpty()) {
            throw new IllegalArgumentException("请至少选择一条被替换的 BOM 行（原料侧）");
        }
        if (request.getSubstitutes() == null || request.getSubstitutes().isEmpty()) {
            throw new IllegalArgumentException("请至少选择一个替代物料（替代侧）");
        }
        Map<String, BomLinks> sourceLinks = new LinkedHashMap<>();
        for (BomSubstituteGroupRequest.MemberInput in : request.getSources()) {
            if (in == null || isBlank(in.getBomLinkOid())) {
                throw new IllegalArgumentException("原料侧成员缺少 BOM 行");
            }
            if (sourceLinks.containsKey(in.getBomLinkOid())) {
                throw new IllegalArgumentException("同一条 BOM 行在原料侧只能出现一次");
            }
            BomLinks link = bomLinksService.getByOid(in.getBomLinkOid());
            if (link == null) {
                throw new IllegalArgumentException("原料侧的 BOM 行已不存在（可能已被检出的工作副本取代或删除），请刷新页面后重试");
            }
            if (!isBlank(parentIterationOid) && !parentIterationOid.equals(link.getParentIterationOid())) {
                throw new IllegalArgumentException("原料侧的 BOM 行不属于当前版本 —— 成组替代只在同一版 BOM 内成立，请刷新页面后重试");
            }
            sourceLinks.put(in.getBomLinkOid(), link);
        }
        Set<String> seenParts = new HashSet<>();
        for (BomSubstituteGroupRequest.MemberInput in : request.getSubstitutes()) {
            if (in == null || isBlank(in.getPartOid())) {
                throw new IllegalArgumentException("替代侧成员缺少物料");
            }
            if (!seenParts.add(in.getPartOid())) {
                throw new IllegalArgumentException("同一颗物料在替代侧只能出现一次");
            }
            if (partService.findByOid(in.getPartOid()) == null) {
                throw new IllegalArgumentException("替代物料不存在（或不属于当前租户）");
            }
        }
        return sourceLinks;
    }

    /** 建成员：两侧分别从 10 开始按 10 步长给 sortOrder（同侧内部的展示顺序） */
    private void insertMembers(String groupOid, BomSubstituteGroupRequest request,
                               Map<String, BomLinks> sourceLinks, String user, LocalDateTime now) {
        int order = SORT_STEP;
        for (BomSubstituteGroupRequest.MemberInput in : request.getSources()) {
            BomLinks link = sourceLinks.get(in.getBomLinkOid());
            BomSubstituteGroupMember member = new BomSubstituteGroupMember();
            member.setOid(UUID.randomUUID().toString());
            member.setGroupOid(groupOid);
            member.setMemberSide(SIDE_SOURCE);
            member.setBomLinkOid(in.getBomLinkOid());
            // 数量因子默认取该 BOM 行的用量（成组替换最常见的场景就是按原用量换一套）
            member.setQuantity(in.getQuantity() != null ? in.getQuantity()
                    : (link != null && link.getQuantity() != null ? link.getQuantity() : 1.0));
            member.setUnit(!isBlank(in.getUnit()) ? in.getUnit() : (link != null ? link.getUnit() : null));
            member.setSortOrder(order);
            fillAudit(member, user, now);
            mapper.insertMember(member);
            order += SORT_STEP;
        }
        order = SORT_STEP;
        for (BomSubstituteGroupRequest.MemberInput in : request.getSubstitutes()) {
            BomSubstituteGroupMember member = new BomSubstituteGroupMember();
            member.setOid(UUID.randomUUID().toString());
            member.setGroupOid(groupOid);
            member.setMemberSide(SIDE_SUBSTITUTE);
            member.setPartOid(in.getPartOid());
            member.setQuantity(in.getQuantity() != null ? in.getQuantity() : 1.0);
            member.setUnit(isBlank(in.getUnit()) ? null : in.getUnit());
            member.setSortOrder(order);
            fillAudit(member, user, now);
            mapper.insertMember(member);
            order += SORT_STEP;
        }
    }

    private void fillAudit(BomSubstituteGroupMember member, String user, LocalDateTime now) {
        member.setCreator(user);
        member.setCreatedAt(now);
        member.setUpdater(user);
        member.setUpdatedAt(now);
    }

    /** 没起名字时按行号给个默认名（"成组替代（行 10、20）"比"未命名"有用得多） */
    private String defaultName(Map<String, BomLinks> sourceLinks) {
        List<String> lines = new ArrayList<>();
        for (BomLinks link : sourceLinks.values()) {
            if (link.getLineNumber() != null) {
                lines.add(String.valueOf(link.getLineNumber()));
            }
        }
        return lines.isEmpty() ? null : "成组替代（行 " + String.join("、", lines) + "）";
    }

    /** 组状态：只认表里登记的三种 */
    private static String normalizeStatus(String value) {
        if (isBlank(value)) {
            throw new IllegalArgumentException("缺少目标状态");
        }
        String v = value.trim().toUpperCase();
        if (!STATUSES.contains(v)) {
            throw new IllegalArgumentException("不支持的组状态: " + value + "（只支持 " + STATUSES + "）");
        }
        return v;
    }

    /** 替代物料当前最新版本的显示版本（展示用；查不到就留空，不编造） */
    private String versionOf(String partOid) {
        if (isBlank(partOid)) {
            return null;
        }
        try {
            PartIteration iteration = partService.findLatestIteration(partOid);
            return iteration == null ? null : iteration.getDisplayVersion();
        } catch (Exception e) {
            log.debug("取替代物料版本失败: part={} err={}", partOid, e.getMessage());
            return null;
        }
    }

    private static boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }
}
