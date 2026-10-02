/*
 * Copyright (c) 2025 深圳乘恺科技有限公司
 * All rights reserved.
 */
package cn.ck.plm.checkout.service.impl;

import cn.ck.plm.part.entity.PartIteration;
import cn.ck.plm.part.mapper.PartIterationMapper;
import cn.ck.plm.part.mapper.PartMapper;
import cn.ck.plm.part.entity.Part;
import cn.ck.plm.base.entity.UserActivity;
import cn.ck.plm.base.mapper.UserActivityMapper;
import cn.ck.plm.base.service.api.LifecycleStatusService;
import cn.ck.plm.bom.dto.BomSubstituteGroupVO;
import cn.ck.plm.bom.dto.BomSubstituteVO;
import cn.ck.plm.bom.entity.BomLinks;
import cn.ck.plm.bom.entity.BomSubstituteGroup;
import cn.ck.plm.bom.entity.BomSubstituteGroupMember;
import cn.ck.plm.bom.entity.BomSubstituteLink;
import cn.ck.plm.bom.mapper.BomLinksMapper;
import cn.ck.plm.bom.mapper.BomSubstituteGroupMapper;
import cn.ck.plm.bom.mapper.BomSubstituteLinkMapper;
import cn.ck.plm.checkout.dto.CheckoutVO;
import cn.ck.plm.checkout.service.api.CheckoutProvider;
import cn.ck.plm.cls.service.api.ClsIbaDataService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * 部件检出提供者 —— 统一处理部件的检出查询和检出操作。
 */
@Component
public class PartCheckoutProvider implements CheckoutProvider {

    private static final Logger log = LoggerFactory.getLogger(PartCheckoutProvider.class);

    private final PartMapper partMapper;
    private final PartIterationMapper iterationMapper;
    private final UserActivityMapper activityMapper;
    private final ClsIbaDataService clsIbaDataService;
    private final BomLinksMapper bomLinksMapper;
    private final BomSubstituteLinkMapper bomSubstituteLinkMapper;
    private final BomSubstituteGroupMapper bomSubstituteGroupMapper;
    /** 状态 code → 显示名（列表给"人看得懂"的那个字，见 LifecycleStatusService#displayName） */
    private final LifecycleStatusService lifecycleStatusService;

    public PartCheckoutProvider(PartMapper partMapper, PartIterationMapper iterationMapper,
                                 UserActivityMapper activityMapper,
                                 ClsIbaDataService clsIbaDataService,
                                 BomLinksMapper bomLinksMapper,
                                 BomSubstituteLinkMapper bomSubstituteLinkMapper,
                                 BomSubstituteGroupMapper bomSubstituteGroupMapper,
                                 LifecycleStatusService lifecycleStatusService) {
        this.partMapper = partMapper;
        this.iterationMapper = iterationMapper;
        this.activityMapper = activityMapper;
        this.clsIbaDataService = clsIbaDataService;
        this.bomLinksMapper = bomLinksMapper;
        this.bomSubstituteLinkMapper = bomSubstituteLinkMapper;
        this.bomSubstituteGroupMapper = bomSubstituteGroupMapper;
        this.lifecycleStatusService = lifecycleStatusService;
    }

    @Override
    public String getEntityType() {
        return "PART";
    }

    @Override
    public String getEntityTypeName() {
        return "部件";
    }

    @Override
    public List<CheckoutVO> findCheckedOutByUser(String userOid) {
        List<CheckoutVO> result = new ArrayList<>();
        List<PartIteration> checkedOutIters = iterationMapper.selectCheckedOutByUser(userOid);
        for (PartIteration iter : checkedOutIters) {
            Part part = partMapper.selectByOid(iter.getMasterOid());
            if (part == null) continue;
            CheckoutVO vo = new CheckoutVO();
            vo.setOid(part.getOid());
            vo.setName(part.getName());
            vo.setCode(part.getNumber());
            vo.setEntityType("PART");
            vo.setEntityTypeName("部件");
            vo.setDisplayVersion(iter.getRevision() + "." + iter.getIteration());
            vo.setCheckedOutBy(iter.getCheckedOutBy());
            vo.setCheckedOutComment(iter.getCheckedOutComment());
            vo.setCheckedOutAt(iter.getUpdatedAt() != null ? iter.getUpdatedAt().toString() : null);
            if (iter.getStatus() != null) {
                vo.setStatusCode(iter.getStatus().getCode());
                // 不能取 getDisplayName()：迭代上的 status 只带 code（LifecycleStatusTypeHandler），
                // 那样"我的检出"列表只会显示 IN_WORK 这种 code
                vo.setStatusName(lifecycleStatusService.displayName(
                        iter.getLifecycleTemplateIterationOid(), iter.getStatus().getCode()));
            }
            vo.setLinkPath("/part");
            result.add(vo);
        }
        return result;
    }

    @Override
    @Transactional
    public void checkout(String entityOid, String comment, String user) {
        Part part = partMapper.selectByOid(entityOid);
        if (part == null) {
            throw new IllegalArgumentException("部件不存在: " + entityOid);
        }
        PartIteration currentIter = iterationMapper.selectLatestByMasterOid(entityOid);
        if (currentIter == null) {
            throw new IllegalArgumentException("部件没有可用版本: " + entityOid);
        }
        if (currentIter.isCheckedOut()) {
            throw new IllegalStateException(
                "部件已被 " + currentIter.getCheckedOutBy() + " 检出，检出注释: " +
                (currentIter.getCheckedOutComment() != null ? currentIter.getCheckedOutComment() : "无")
            );
        }

        // 1. 源版本 latest → false
        currentIter.setLatest(false);
        iterationMapper.update(currentIter);

        // 2. 创建同大版本的新小版本（iteration+1）
        PartIteration copy = new PartIteration();
        copy.setMasterOid(currentIter.getMasterOid());
        copy.setRevision(currentIter.getRevision());
        copy.setIteration(currentIter.getIteration() + 1);
        copy.setLatest(true);                              // 标记为最新
        copy.setCheckedOut(true);                          // 标记为已检出
        copy.setCheckedOutBy(user);
        copy.setCheckedOutComment(comment);
        copy.setDerivedFromOid(currentIter.getOid());      // 记录来源版本
        copy.setDerivedAt(LocalDateTime.now());
        copy.setBranchId(currentIter.getBranchId() != null ? currentIter.getBranchId() : "master");
        copy.setView(currentIter.getView());
        copy.setStatus(currentIter.getStatus());
        copy.setLifecycleTemplateIterationOid(currentIter.getLifecycleTemplateIterationOid());
        copy.setUnit(currentIter.getUnit());
        copy.setSource(currentIter.getSource());
        iterationMapper.insert(copy);

        // 复制分类 IBA 数据到新小版本（避免检出后编辑页面分类属性值丢失）
        if (part.getClsOid() != null) {
            Map<String, Object> clsIba = clsIbaDataService.getValues(currentIter.getOid(), part.getClsOid());
            if (clsIba != null && !clsIba.isEmpty()) {
                clsIbaDataService.saveValues(copy.getOid(), part.getClsOid(), clsIba);
            }
        }

        // 复制源版本的下挂 BOM 行到新版本（否则检出后 BOM 结构会丢失），
        // 连同行上的局部替代一起复制（替代挂在 BOM 行上，行 oid 换成新的，不跟着复制就会丢）
        copyBomLinksToIteration(currentIter.getOid(), copy.getOid(), user);

        log.info("检出成功: partOid={}, {}.{} -> {}.{}, user={}", entityOid,
                currentIter.getRevision(), currentIter.getIteration(),
                copy.getRevision(), copy.getIteration(), user);

        recordActivity(user, "检出部件", part);
    }

    /** 将源迭代的全部 BOM 行复制到目标迭代（检出时保留 BOM 结构），行上的局部替代一并复制 */
    private void copyBomLinksToIteration(String sourceIterationOid, String targetIterationOid, String user) {
        List<BomLinks> sourceLinks = bomLinksMapper.selectByParentIterationOid(sourceIterationOid);
        // 先一条批量查询问出"哪些行设了替代"：绝大多数行没有，逐行取明细纯属浪费
        Set<String> linksWithSubstitutes = linksWithSubstitutes(sourceLinks);
        // 旧行 oid → 新行 oid：成组替代的原料侧成员要按它改指到新行上
        Map<String, String> linkOidMap = new HashMap<>();
        int copiedSubstitutes = 0;
        for (BomLinks source : sourceLinks) {
            BomLinks copy = new BomLinks();
            copy.setOid(UUID.randomUUID().toString());
            copy.setCode(source.getCode());
            copy.setName(source.getName());
            copy.setDescription(source.getDescription());
            copy.setParentIterationOid(targetIterationOid);
            copy.setChildPartOid(source.getChildPartOid());
            copy.setChildIterationOid(source.getChildIterationOid());
            copy.setResolvedIterationOid(source.getResolvedIterationOid());
            copy.setQuantity(source.getQuantity());
            copy.setUnit(source.getUnit());
            copy.setLineNumber(source.getLineNumber());
            copy.setUnitCost(source.getUnitCost());
            copy.setTenantOid(source.getTenantOid());
            copy.setCreatedAt(LocalDateTime.now());
            copy.setUpdatedAt(LocalDateTime.now());
            bomLinksMapper.insert(copy);
            linkOidMap.put(source.getOid(), copy.getOid());
            if (linksWithSubstitutes.contains(source.getOid())) {
                copiedSubstitutes += copySubstituteLinks(source, copy, user);
            }
        }
        // 成组替代挂在父件迭代上、原料侧成员指 BOM 行 —— 两处都得重指，故需要上面攒下的行映射
        int copiedGroups = copySubstituteGroups(sourceIterationOid, targetIterationOid, linkOidMap, user);
        log.info("检出复制 BOM 行: source={}, target={}, count={}, 其中替代件={}, 成组替代={}",
                sourceIterationOid, targetIterationOid, sourceLinks.size(), copiedSubstitutes, copiedGroups);
    }

    /** 先批量问出"哪些 BOM 行设了局部替代"（一条 SQL；没有替代的行不用再查明细） */
    private Set<String> linksWithSubstitutes(List<BomLinks> links) {
        List<String> oids = new ArrayList<>();
        for (BomLinks link : links) {
            if (link.getOid() != null && !link.getOid().isEmpty()) {
                oids.add(link.getOid());
            }
        }
        if (oids.isEmpty()) {
            return Collections.emptySet();
        }
        Set<String> result = new HashSet<>();
        for (Map<String, Object> row : bomSubstituteLinkMapper.countByBomLinkOids(String.join(",", oids))) {
            Object linkOid = row.get("bomLinkOid");
            if (linkOid != null) {
                result.add(String.valueOf(linkOid).trim());
            }
        }
        return result;
    }

    /**
     * 把一条 BOM 行的局部替代复制到它的新行上。
     *
     * <p>局部替代挂在 <b>BOM 行</b>（{@code ck_bom_substitute_link.bom_link_oid}）上，而检出会新建行、
     * 行 oid 全新 —— 不复制的话，检出后「替代标记」会凭空消失（用户只会当成数据丢了）。
     * 替代件的两端零件与参数原样保留，只换挂载点、换新 oid、记上是谁检出的。
     *
     * <p>停用中的替代也照抄（"停用"是用户的意思表示，不该被检出悄悄改成启用）。
     */
    private int copySubstituteLinks(BomLinks source, BomLinks target, String user) {
        List<BomSubstituteVO> substitutes = bomSubstituteLinkMapper.selectVoByBomLinkOid(source.getOid());
        LocalDateTime now = LocalDateTime.now();
        for (BomSubstituteVO vo : substitutes) {
            BomSubstituteLink copy = new BomSubstituteLink();
            copy.setOid(UUID.randomUUID().toString());
            copy.setBomLinkOid(target.getOid());
            copy.setSourcePartOid(target.getChildPartOid());
            copy.setSubstitutePartOid(vo.getSubstitutePartOid());
            copy.setSubstituteType(vo.getSubstituteType());
            copy.setSubstituteQuantity(vo.getSubstituteQuantity());
            copy.setSubstituteUnit(vo.getSubstituteUnit());
            copy.setPriority(vo.getPriority());
            copy.setEnabled(vo.getEnabled() != null ? vo.getEnabled() : Boolean.TRUE);
            copy.setDescription(vo.getDescription());
            copy.setCreator(user);
            copy.setUpdater(user);
            copy.setCreatedAt(now);
            copy.setUpdatedAt(now);
            bomSubstituteLinkMapper.insert(copy);
        }
        return substitutes.size();
    }

    /**
     * 把源迭代上的<b>成组替代</b>复制到新迭代。
     *
     * <p>成组替代同时挂在两个东西上：组头挂父件迭代、原料侧成员指 BOM 行 —— 检出既新建迭代、
     * 又重造 BOM 行，所以两处都要重指（组头挂新迭代、原料侧成员改指新行）。少任何一步，
     * 检出后这一组要么直接消失、要么指向已经不属于本版的行。
     *
     * <p>原料侧有任意一行没能映射到新行（例如那行没被复制），<b>整组跳过</b>：
     * 一个"只剩一半原料侧"的成组替代会让"必须整组替换"的约束变得毫无意义，比没有更危险。
     * 替代侧成员不含 BOM 行，原样照搬（物料本身不随检出改）。
     *
     * @return 实际复制的组数
     */
    private int copySubstituteGroups(String sourceIterationOid, String targetIterationOid,
                                     Map<String, String> linkOidMap, String user) {
        List<BomSubstituteGroupVO> groups = bomSubstituteGroupMapper.selectVoByParentIterationOid(sourceIterationOid);
        if (groups == null || groups.isEmpty()) {
            return 0;
        }
        List<String> groupOids = new ArrayList<>(groups.size());
        for (BomSubstituteGroupVO group : groups) {
            groupOids.add(group.getOid());
        }
        List<BomSubstituteGroupVO.MemberVO> members =
                bomSubstituteGroupMapper.selectMembersByGroupOids(String.join(",", groupOids));
        Map<String, List<BomSubstituteGroupVO.MemberVO>> byGroup = new HashMap<>();
        if (members != null) {
            for (BomSubstituteGroupVO.MemberVO m : members) {
                byGroup.computeIfAbsent(m.getGroupOid(), k -> new ArrayList<>()).add(m);
            }
        }

        LocalDateTime now = LocalDateTime.now();
        int copied = 0;
        for (BomSubstituteGroupVO group : groups) {
            List<BomSubstituteGroupVO.MemberVO> list =
                    byGroup.getOrDefault(group.getOid(), new ArrayList<>());
            // 原料侧成员 oid → 复制出来的新成员该指哪一行
            Map<String, String> memberNewLinkOid = new HashMap<>();
            boolean complete = true;
            for (BomSubstituteGroupVO.MemberVO m : list) {
                if (!"SOURCE".equalsIgnoreCase(m.getMemberSide())) {
                    continue;
                }
                String newLinkOid = linkOidMap.get(m.getBomLinkOid());
                if (newLinkOid == null) {
                    complete = false;
                    break;
                }
                memberNewLinkOid.put(m.getOid(), newLinkOid);
            }
            if (!complete) {
                log.warn("检出复制成组替代: 组 {} 有原料侧行未随检出复制，整组跳过", group.getOid());
                continue;
            }

            BomSubstituteGroup groupCopy = new BomSubstituteGroup();
            groupCopy.setOid(UUID.randomUUID().toString());
            groupCopy.setCode(group.getCode());
            groupCopy.setName(group.getName());
            groupCopy.setDescription(group.getDescription());
            groupCopy.setParentIterationOid(targetIterationOid);
            // 状态照抄：检出是"把这一版原样带走"，不该顺手把已批准改成草稿（也不该把草稿变成批准）
            groupCopy.setStatus(group.getStatus());
            groupCopy.setAtomicReplace(group.getAtomicReplace());
            groupCopy.setEnabled(group.getEnabled());
            groupCopy.setCreator(user);
            groupCopy.setUpdater(user);
            groupCopy.setCreatedAt(now);
            groupCopy.setUpdatedAt(now);
            bomSubstituteGroupMapper.insertGroup(groupCopy);

            for (BomSubstituteGroupVO.MemberVO m : list) {
                BomSubstituteGroupMember memberCopy = new BomSubstituteGroupMember();
                memberCopy.setOid(UUID.randomUUID().toString());
                memberCopy.setGroupOid(groupCopy.getOid());
                memberCopy.setMemberSide(m.getMemberSide());
                memberCopy.setBomLinkOid("SOURCE".equalsIgnoreCase(m.getMemberSide())
                        ? memberNewLinkOid.get(m.getOid()) : null);
                memberCopy.setPartOid(m.getPartOid());
                memberCopy.setQuantity(m.getQuantity());
                memberCopy.setUnit(m.getUnit());
                memberCopy.setSortOrder(m.getSortOrder());
                memberCopy.setCreator(user);
                memberCopy.setUpdater(user);
                memberCopy.setCreatedAt(now);
                memberCopy.setUpdatedAt(now);
                bomSubstituteGroupMapper.insertMember(memberCopy);
            }
            copied++;
        }
        return copied;
    }

    @Override
    @Transactional
    public void undoCheckout(String entityOid, String user) {
        Part part = partMapper.selectByOid(entityOid);
        if (part == null) {
            throw new IllegalArgumentException("部件不存在: " + entityOid);
        }

        // 找到检出时创建的新版本（checkedOut=true, latest=true, derivedFromOid 指向源版本）
        List<PartIteration> allIters = iterationMapper.selectByMasterOid(entityOid);
        PartIteration checkedOutIter = null;
        PartIteration sourceIter = null;

        for (PartIteration iter : allIters) {
            if (iter.isCheckedOut() && iter.isLatest() && iter.getDerivedFromOid() != null) {
                checkedOutIter = iter;
                sourceIter = iterationMapper.selectByOid(iter.getDerivedFromOid());
            }
        }

        if (checkedOutIter == null) {
            throw new IllegalStateException("该部件未被检出: " + entityOid);
        }
        if (!user.equals(checkedOutIter.getCheckedOutBy())) {
            throw new IllegalStateException("只有检出人 " + checkedOutIter.getCheckedOutBy() + " 才能取消检出");
        }

        // 1. 清除检出后版本的下挂 BOM 行（撤销检出后，检出后版本的修改作废）
        int deletedLinks = bomLinksMapper.deleteByParentIterationOid(checkedOutIter.getOid());

        // 2. 删除检出时创建的新版本
        iterationMapper.deleteByOid(checkedOutIter.getOid());

        // 3. 恢复源版本 latest = true
        if (sourceIter != null) {
            sourceIter.setLatest(true);
            iterationMapper.update(sourceIter);
        }

        log.info("取消检出成功: partOid={}, user={}, deletedBomLinks={}", entityOid, user, deletedLinks);

        recordActivity(user, "取消检出", part);
    }

    @Override
    @Transactional
    public void checkin(String entityOid, String user) {
        Part part = partMapper.selectByOid(entityOid);
        if (part == null) {
            throw new IllegalArgumentException("部件不存在: " + entityOid);
        }

        // 找到检出版本（checkedOut=true, latest=true, derivedFromOid 指向源版本）
        List<PartIteration> allIters = iterationMapper.selectByMasterOid(entityOid);
        PartIteration checkedOutIter = null;
        for (PartIteration iter : allIters) {
            if (iter.isCheckedOut() && iter.isLatest() && iter.getDerivedFromOid() != null) {
                checkedOutIter = iter;
            }
        }

        if (checkedOutIter == null) {
            throw new IllegalStateException("该部件未被检出: " + entityOid);
        }
        if (!user.equals(checkedOutIter.getCheckedOutBy())) {
            throw new IllegalStateException("只有检出人 " + checkedOutIter.getCheckedOutBy() + " 才能检入");
        }

        // 解除检出锁定，将工作副本保存为正式的新版本
        checkedOutIter.setCheckedOut(false);
        checkedOutIter.setCheckedOutBy(null);
        checkedOutIter.setCheckedOutComment(null);
        checkedOutIter.setUpdatedAt(LocalDateTime.now());
        iterationMapper.update(checkedOutIter);

        log.info("检入成功: partOid={}, version={}.{}, user={}", entityOid,
                checkedOutIter.getRevision(), checkedOutIter.getIteration(), user);

        recordActivity(user, "检入部件", part);
    }

    private void recordActivity(String user, String actionDesc, Part part) {
        try {
            UserActivity activity = new UserActivity();
            activity.setOid(UUID.randomUUID().toString());
            activity.setUserOid(user);
            activity.setActivityType("OPERATION");
            activity.setActionDesc(actionDesc);
            activity.setTargetName((part.getNumber() != null ? part.getNumber() + " " : "") + part.getName());
            activity.setTargetType("部件");
            activity.setTargetPath("/part");
            activity.setResult("SUCCESS");
            activity.setCreator(user);
            activity.setUpdater(user);
            activityMapper.insert(activity);
        } catch (Exception e) {
            log.warn("记录操作日志失败[{}]: {}", actionDesc, e.getMessage());
        }
    }
}
