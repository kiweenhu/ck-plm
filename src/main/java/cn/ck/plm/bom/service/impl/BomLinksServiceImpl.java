/*
 * Copyright (c) 2025 深圳乘恺科技有限公司
 * All rights reserved.
 *
 * @author Kiween.Hu; Roney.Liu
 */

package cn.ck.plm.bom.service.impl;

import cn.ck.plm.bom.dto.BomCostReportVO;
import cn.ck.plm.bom.dto.BomTreeNode;
import cn.ck.plm.bom.entity.BomLinks;
import cn.ck.plm.bom.mapper.BomLinksMapper;
import cn.ck.plm.bom.service.BomCostCalculator;
import cn.ck.plm.bom.service.api.BomLinksService;
import cn.ck.plm.part.entity.Part;
import cn.ck.plm.part.entity.PartIteration;
import cn.ck.plm.part.service.api.PartService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * BomLinks 业务服务实现。
 */
@Service
@Transactional
public class BomLinksServiceImpl implements BomLinksService {

    /** 递归构建树的最大深度，防止异常循环数据导致栈溢出 */
    private static final int MAX_TREE_DEPTH = 20;

    @Autowired
    private BomLinksMapper bomLinksMapper;

    @Autowired
    private PartService partService;

    @Override
    public BomLinks create(BomLinks bomLinks) {
        LocalDateTime now = LocalDateTime.now();
        // 行号遵循「10/20/30 步长编号」：父迭代内唯一，作为 BOM 行的稳定地址；
        // 后端根据当前最大行号自动分配（无行时从 10 开始），避免前端并发/手工赋值导致的冲突。
        Integer maxLineNumber = bomLinksMapper.selectMaxLineNumber(bomLinks.getParentIterationOid());
        int lineNumber = (maxLineNumber == null || maxLineNumber < 10) ? 10 : maxLineNumber + 10;
        bomLinks.setLineNumber(lineNumber);
        // 默认单位：未指定单位时使用 ea（个/件）
        if (bomLinks.getUnit() == null || bomLinks.getUnit().trim().isEmpty()) {
            bomLinks.setUnit("ea");
        }
        bomLinks.setCreatedAt(now);
        bomLinks.setUpdatedAt(now);
        bomLinksMapper.insert(bomLinks);
        return bomLinks;
    }

    /**
     * 更新 BOM 行。
     *
     * <p><b>必须检查影响行数</b>：检出会新建一个小版本迭代并把 BOM 行整份复制过去（新 oid），
     * 取消检出又会把工作副本的行删掉 —— 页面如果拿着上一个迭代的行 oid 来保存，
     * UPDATE 会命中 0 行。早先这里不看行数、直接返回入参，前端于是把"保存"算作成功，
     * 刷新后才发现数量没变（表现为"点了保存没生效，又退回旧数据"，且全程没有报错）。
     *
     * <p>现在：命中 0 行就抛错（由控制器折成 code=400 + 可读原因），
     * 成功则<b>回读库里的行</b>再返回 —— 返回值就是事实，前端可以拿它核对。
     */
    @Override
    public BomLinks update(BomLinks bomLinks) {
        bomLinks.setUpdatedAt(LocalDateTime.now());
        int updated = bomLinksMapper.update(bomLinks);
        if (updated == 0) {
            throw new IllegalStateException(
                    "该 BOM 行已不存在（可能已被检出的工作副本取代或被删除），"
                    + "请刷新页面后重试：oid=" + bomLinks.getOid());
        }
        return bomLinksMapper.selectByOid(bomLinks.getOid());
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
    public List<BomTreeNode> buildTree(String parentIterationOid) {
        return buildTreeRecursive(parentIterationOid, new HashSet<>(), 0);
    }

    /** 递归构建 BOM 树，pathVisited 按路径防环（仅阻止当前路径上的环，不阻止不同分支重复展开），depth 限制深度 */
    private List<BomTreeNode> buildTreeRecursive(String parentIterationOid, Set<String> pathVisited, int depth) {
        if (parentIterationOid == null || parentIterationOid.isEmpty() || depth >= MAX_TREE_DEPTH) {
            return new ArrayList<>();
        }
        List<BomLinks> links = bomLinksMapper.selectByParentIterationOid(parentIterationOid);
        List<BomTreeNode> nodes = new ArrayList<>();
        for (BomLinks link : links) {
            // 每个分支使用独立的 visited 副本，避免同层兄弟节点互相影响导致下挂丢失
            BomTreeNode node = toTreeNode(link, new HashSet<>(pathVisited), depth);
            if (node != null) {
                nodes.add(node);
            }
        }
        return nodes;
    }

    /** 将单个 BomLinks 转换为树节点，附加子件信息并递归加载孙件 */
    private BomTreeNode toTreeNode(BomLinks link, Set<String> pathVisited, int depth) {
        BomTreeNode node = new BomTreeNode();
        node.setOid(link.getOid());
        node.setCode(link.getCode());
        node.setName(link.getName());
        node.setDescription(link.getDescription());
        node.setParentIterationOid(link.getParentIterationOid());
        node.setChildPartOid(link.getChildPartOid());
        node.setChildIterationOid(link.getChildIterationOid());
        node.setResolvedIterationOid(link.getResolvedIterationOid());
        node.setQuantity(link.getQuantity());
        node.setUnit(link.getUnit());
        node.setLineNumber(link.getLineNumber());
        node.setUnitCost(link.getUnitCost());

        // 解析子件的 latest iteration oid：优先精确引用，其次解析缓存，最后查最新迭代
        String childIterOid = link.getChildIterationOid();
        if (childIterOid == null || childIterOid.isEmpty()) {
            childIterOid = link.getResolvedIterationOid();
        }
        if (childIterOid == null || childIterOid.isEmpty()) {
            PartIteration latest = partService.findLatestIteration(link.getChildPartOid());
            if (latest != null) {
                childIterOid = latest.getOid();
            }
        }
        node.setChildLatestIterationOid(childIterOid);

        // 附加子件主对象信息（名称/编码）
        Part childPart = partService.findByOid(link.getChildPartOid());
        if (childPart != null) {
            node.setChildPartName(childPart.getName());
            node.setChildPartNumber(childPart.getNumber());
        }

        // 附加子件迭代信息（版本/视图/状态/检出）
        if (childIterOid != null && !childIterOid.isEmpty()) {
            PartIteration iter = partService.findIterationByOid(childIterOid);
            if (iter != null) {
                node.setChildVersion(iter.getDisplayVersion());
                node.setChildView(iter.getView() != null ? iter.getView().getCode() : null);
                node.setChildStatus(iter.getStatus() != null ? iter.getStatus().getCode() : null);
                node.setChildCheckedOut(iter.isCheckedOut());
                node.setChildCheckedOutBy(iter.getCheckedOutBy());
            }

            // 防环：仅阻止当前路径上重复出现的迭代（环），允许不同分支重复展开
            if (pathVisited.add(childIterOid)) {
                node.setChildren(buildTreeRecursive(childIterOid, pathVisited, depth + 1));
            }
        }

        return node;
    }

    @Override
    public BomCostReportVO costReport(String parentIterationOid) {
        BomCostReportVO vo = new BomCostReportVO();
        vo.setParentIterationOid(parentIterationOid);
        if (parentIterationOid == null || parentIterationOid.isEmpty()) {
            vo.setWarnings(new ArrayList<>(List.of("未指定父件迭代，无法计算成本")));
            return vo;
        }

        // 先复用结构树（含防环与深度限制），再在它之上做一次卷积遍历
        List<BomTreeNode> lines = buildTree(parentIterationOid);
        BomCostCalculator.Summary summary = BomCostCalculator.apply(lines);
        vo.setLines(lines);
        vo.setDirectCost(summary.getDirectCost());
        vo.setTotalCost(summary.getTotalCost());
        vo.setTotalLines(summary.getTotalLines());
        vo.setMissingCostLines(summary.getMissingCostLines());

        // 报告头部要说明"这是谁的成本"：光有数字，过两天没人认得出算的是哪一版
        PartIteration parentIteration = partService.findIterationByOid(parentIterationOid);
        if (parentIteration != null) {
            vo.setParentVersion(parentIteration.getDisplayVersion());
            if (parentIteration.getStatus() != null) {
                vo.setParentStatus(parentIteration.getStatus().getCode());
            }
            Part parentPart = partService.findByOid(parentIteration.getMasterOid());
            if (parentPart != null) {
                vo.setParentCode(parentPart.getNumber());
                vo.setParentName(parentPart.getName());
            }
        }

        List<String> warnings = new ArrayList<>();
        if (summary.getMissingCostLines() > 0) {
            warnings.add("有 " + summary.getMissingCostLines()
                    + " 行未填「单位成本」，已按 0 计入 —— 实际成本可能高于本报告");
        }
        if (summary.getMaxDepth() >= MAX_TREE_DEPTH - 1) {
            warnings.add("BOM 层级接近上限（" + MAX_TREE_DEPTH + " 层），更深的层级未展开，成本可能不完整");
        }
        vo.setWarnings(warnings);
        return vo;
    }

    @Override
    public List<BomLinks> listAll() {
        return bomLinksMapper.selectAll();
    }
}
