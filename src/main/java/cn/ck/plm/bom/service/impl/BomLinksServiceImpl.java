/*
 * Copyright (c) 2026~2028 深圳乘恺科技有限公司
 * All rights reserved.
 *
 * @author Kiween.Hu; Roney.Liu
 */

package cn.ck.plm.bom.service.impl;

import cn.ck.plm.bom.dto.BomCostReportVO;
import cn.ck.plm.bom.dto.BomMaterialVO;
import cn.ck.plm.bom.dto.BomTreeNode;
import cn.ck.plm.bom.dto.BomUsedByVO;
import cn.ck.plm.bom.dto.ContainerCostTrendVO;
import cn.ck.plm.bom.entity.BomLinks;
import cn.ck.plm.bom.entity.BomSnapshot;
import cn.ck.plm.bom.mapper.BomLinksMapper;
import cn.ck.plm.bom.mapper.BomSnapshotMapper;
import cn.ck.plm.bom.mapper.BomSubstituteGroupMapper;
import cn.ck.plm.bom.mapper.BomSubstituteLinkMapper;
import cn.ck.plm.bom.service.BomCostCalculator;
import cn.ck.plm.bom.service.api.BomLinksService;
import cn.ck.plm.base.service.api.LifecycleStatusService;
import cn.ck.plm.base.service.api.LifecycleTemplateService;
import cn.ck.plm.cad.mapper.CadVisualizationMapper;
import cn.ck.plm.part.entity.Part;
import cn.ck.plm.part.entity.PartIteration;
import cn.ck.plm.part.mapper.PartIterationMapper;
import cn.ck.plm.part.service.api.PartService;
import cn.ck.plm.product.entity.ProductModel;
import cn.ck.plm.product.service.api.ProductLineService;
import cn.ck.plm.product.service.api.ProductModelService;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * BomLinks 业务服务实现。
 */
@Service
@Transactional
public class BomLinksServiceImpl implements BomLinksService {

    /** 递归构建树的最大深度，防止异常循环数据导致栈溢出 */
    private static final int MAX_TREE_DEPTH = 20;

    /** 成本趋势保留的最近快照点数：趋势看"最近怎么变"，给一整条长线反而看不清 */
    private static final int MAX_COST_POINTS = 20;

    /** 缺「单位成本」时最多点几个零件编码出来，够定位即可 */
    private static final int MAX_MISSING_COST_NUMBERS = 5;

    private static final Logger log = LoggerFactory.getLogger(BomLinksServiceImpl.class);

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Autowired
    private BomLinksMapper bomLinksMapper;

    @Autowired
    private PartService partService;

    /** 状态 code → 显示名（草稿 / 工作中…）；物料清单与研发资料列表同一套口径 */
    @Autowired
    private LifecycleStatusService lifecycleStatusService;

    /** 模板状态上的 revisable：决定行内「修订」是否出现 */
    @Autowired
    private LifecycleTemplateService lifecycleTemplateService;

    /**
     * 物料清单缩略图：零件版本 → 关联工程数据 → 轻量化产物（2026-10-26 用户要求）。
     *
     * <p>只读、批量（见 {@link CadVisualizationMapper#selectThumbnailsByPartIterations}）——
     * 清单行数多，逐行查会把一次列表请求拖成上百次往返。
     */
    @Autowired
    private CadVisualizationMapper cadVisualizationMapper;

    /** 成本快照：容器成本趋势的历史点来源 */
    @Autowired
    private BomSnapshotMapper bomSnapshotMapper;

    /** 快照里的「子件编码 + 版本」要反查那一版迭代，才能按当时的单价算卷积 */
    @Autowired
    private PartIterationMapper partIterationMapper;

    /** 容器种类判定：系列要并入旗下型号（BOM 挂在型号的零件上） */
    @Autowired
    private ProductLineService productLineService;

    @Autowired
    private ProductModelService productModelService;

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

    /**
     * 「被使用情况」：把每条 BOM 行的父迭代解析成父件（编码 / 名称 / 版本）。
     *
     * <p>逐行反查（迭代 → 主对象）在这里是合适的：一条零件通常只被个位数父件使用，
     * 而改成 JOIN 需要再写一份排版口径（版本、视图怎么取）的 SQL，反而更容易和服务端其它地方错开。
     *
     * <p>父迭代查不到时（例如父件已被删除）仍保留该行：BOM 行是事实，
     * 只把父件信息留空，前端显示"未知父件"而不是把这一行悄悄吞掉。
     */
    @Override
    public List<BomUsedByVO> listUsedBy(String childPartOid) {
        List<BomLinks> links = bomLinksMapper.selectByChildPartOid(childPartOid);
        List<BomUsedByVO> result = new ArrayList<>(links.size());
        for (BomLinks link : links) {
            BomUsedByVO vo = new BomUsedByVO();
            vo.setLinkOid(link.getOid());
            vo.setParentIterationOid(link.getParentIterationOid());
            vo.setQuantity(link.getQuantity());
            vo.setUnit(link.getUnit());
            vo.setLineNumber(link.getLineNumber());
            vo.setExactReference(link.isExactReference());
            if (link.getParentIterationOid() != null && !link.getParentIterationOid().isEmpty()) {
                PartIteration parentIteration = partService.findIterationByOid(link.getParentIterationOid());
                if (parentIteration != null) {
                    vo.setParentPartOid(parentIteration.getMasterOid());
                    vo.setParentVersion(parentIteration.getDisplayVersion());
                    vo.setParentView(parentIteration.getView() != null ? parentIteration.getView().getCode() : null);
                    Part parentPart = partService.findByOid(parentIteration.getMasterOid());
                    if (parentPart != null) {
                        vo.setParentNumber(parentPart.getNumber());
                        vo.setParentName(parentPart.getName());
                    }
                }
            }
            result.add(vo);
        }
        return result;
    }

    @Override
    public void refreshResolvedIteration(String oid, String resolvedIterationOid) {
        bomLinksMapper.updateResolvedIterationOid(oid, resolvedIterationOid);
    }

    @Autowired
    private BomSubstituteLinkMapper bomSubstituteLinkMapper;

    @Autowired
    private BomSubstituteGroupMapper bomSubstituteGroupMapper;

    @Override
    public List<BomTreeNode> buildTree(String parentIterationOid) {
        List<BomTreeNode> roots = buildTreeRecursive(parentIterationOid, new HashSet<>(), 0);
        fillSubstituteCounts(roots);
        fillSubstituteGroupCounts(roots);
        return roots;
    }

    /**
     * 批量回填「局部替代件数量」。
     *
     * <p>整棵树<b>只查一次</b>：先把所有行 oid 拍平，再一条 GROUP BY 拿回数量，最后写回节点。
     * 逐节点查在这里就是 N+1（BOM 动辄几百行），所以宁可多一次带 IN 条件的查询。
     */
    private void fillSubstituteCounts(List<BomTreeNode> roots) {
        List<BomTreeNode> flat = new ArrayList<>();
        collectNodes(roots, flat);
        if (flat.isEmpty()) {
            return;
        }
        List<String> oids = new ArrayList<>(flat.size());
        for (BomTreeNode node : flat) {
            // 先把默认值写成 0：前端据此判断"这行有没有替代"，undefined/0 都是"没有"
            node.setSubstituteCount(0);
            node.setSubstituteEnabledCount(0);
            if (node.getOid() != null && !node.getOid().isEmpty()) {
                oids.add(node.getOid());
            }
        }
        if (oids.isEmpty()) {
            return;
        }
        Map<String, int[]> counts = new HashMap<>();
        for (Map<String, Object> row : bomSubstituteLinkMapper.countByBomLinkOids(String.join(",", oids))) {
            Object linkOid = row.get("bomLinkOid");
            if (linkOid == null) {
                continue;
            }
            counts.put(String.valueOf(linkOid).trim(), new int[]{
                    toInt(row.get("substituteCount")), toInt(row.get("substituteEnabledCount"))});
        }
        for (BomTreeNode node : flat) {
            int[] count = counts.get(node.getOid());
            if (count != null) {
                node.setSubstituteCount(count[0]);
                node.setSubstituteEnabledCount(count[1]);
            }
        }
    }

    /** 递归拍平树节点（回填替代数量用） */
    private void collectNodes(List<BomTreeNode> nodes, List<BomTreeNode> out) {
        if (nodes == null) {
            return;
        }
        for (BomTreeNode node : nodes) {
            out.add(node);
            collectNodes(node.getChildren(), out);
        }
    }

    /** 计数结果转 int：PostgreSQL 的 COUNT/SUM 经 JDBC 回来是 Long 等 Number 子类 */
    private int toInt(Object value) {
        return value instanceof Number ? ((Number) value).intValue() : 0;
    }

    /**
     * 批量回填「本行被几个成组替代组当作原料侧引用」。
     *
     * <p>与局部替代的计数一样只查一次。为什么不与它合并成一个方法：两者数据源不同表、
     * 语义也不同（一个数"这行换几颗料"，一个数"这行属于几组整组替换"），
     * 混在一起只会让下次改其中一个时被迫把另一个也读一遍。
     */
    private void fillSubstituteGroupCounts(List<BomTreeNode> roots) {
        List<BomTreeNode> flat = new ArrayList<>();
        collectNodes(roots, flat);
        if (flat.isEmpty()) {
            return;
        }
        List<String> oids = new ArrayList<>(flat.size());
        for (BomTreeNode node : flat) {
            node.setSubstituteGroupCount(0);
            if (node.getOid() != null && !node.getOid().isEmpty()) {
                oids.add(node.getOid());
            }
        }
        if (oids.isEmpty()) {
            return;
        }
        Map<String, Integer> counts = new HashMap<>();
        for (Map<String, Object> row : bomSubstituteGroupMapper.countGroupsByBomLinkOids(String.join(",", oids))) {
            Object linkOid = row.get("bomLinkOid");
            if (linkOid != null) {
                counts.put(String.valueOf(linkOid).trim(), toInt(row.get("groupCount")));
            }
        }
        for (BomTreeNode node : flat) {
            Integer count = counts.get(node.getOid());
            if (count != null) {
                node.setSubstituteGroupCount(count);
            }
        }
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
    public List<BomMaterialVO> materialsByContainer(String containerOid) {
        // 空容器不查库：没有归属的零件不该被算进任何一个产品的物料清单
        if (containerOid == null || containerOid.trim().isEmpty()) {
            return new ArrayList<>();
        }
        // 只认当前容器：型号页不混入父系列零件（父子容器各看各的）
        List<BomMaterialVO> rows = bomLinksMapper.selectMaterialsByContainerOid(containerOid.trim());
        // mapper 只拿得到状态 code：显示名与「能否修订」要按对象固化模板解析，
        // 与研发资料列表同一套口径（否则这两列会直接显示 WORKING 之类）
        Map<String, Map<String, Boolean>> revisableCache = new HashMap<>();
        for (BomMaterialVO row : rows) {
            String statusCode = row.getStatusCode();
            if (statusCode == null) {
                continue;
            }
            row.setStatusName(lifecycleStatusService.displayName(
                    row.getLifecycleTemplateIterationOid(), statusCode));
            Map<String, Boolean> flags = revisableCache.computeIfAbsent(
                    row.getLifecycleTemplateIterationOid(), lifecycleTemplateService::revisableFlags);
            row.setRevisable(flags.getOrDefault(statusCode, true));
        }
        // 缩略图：零件关联了工程数据（CAD 描述）且转换器已出图，清单里就直接显示形状
        fillThumbnails(rows);
        return rows;
    }

    @Override
    public ContainerCostTrendVO containerCostTrend(String containerOid) {
        ContainerCostTrendVO vo = new ContainerCostTrendVO();
        if (containerOid == null || containerOid.trim().isEmpty()) {
            vo.getWarnings().add("未指定容器，无法计算成本");
            return vo;
        }
        String oid = containerOid.trim();
        vo.setContainerOid(oid);
        vo.setCurrentTime(LocalDateTime.now());
        vo.setBasis("折线＝各 BOM 快照时点的卷积成本（当时用量 × 子件当时版本的完整单位成本），"
                + "末点为当前 BOM 的卷积成本；只有 1 份快照时即为「那时 → 现在」两个点");

        /*
         * 参与计价的容器。
         * 系列页必须并入旗下型号：BOM 行挂在型号的零件上，系列容器自己通常一个零件都没有 ——
         * 只按系列容器查，永远得到 0（原先那张卡"本容器还没有 BOM 行"就是这么来的）。
         */
        List<String> containers = new ArrayList<>();
        containers.add(oid);
        ProductModel model = productModelService.findByOid(oid);
        if (model != null) {
            vo.setContainerKind("MODEL");
        } else if (productLineService.findByOid(oid) != null) {
            vo.setContainerKind("LINE");
            for (ProductModel childModel : productModelService.findByProductLineOid(oid)) {
                if (childModel.getOid() != null && !childModel.getOid().isEmpty()) {
                    containers.add(childModel.getOid());
                }
            }
        } else {
            vo.setContainerKind("UNKNOWN");
        }
        vo.setContainerOids(containers);

        /*
         * 顶层件 = 容器内没有被别的零件用掉的零件（parentNumbers 为空）。
         * 物料清单里"总成 + 它的每个零件"都在，逐个求和会把同一批成本重复累加，
         * 所以只从顶层入口做卷积，一次就含全部下级。
         * 直接用 mapper：这里不需要缩略图 / 状态显示名那些展示字段。
         */
        List<BomMaterialVO> tops = new ArrayList<>();
        for (String container : containers) {
            List<BomMaterialVO> rows = bomLinksMapper.selectMaterialsByContainerOid(container);
            if (rows == null) {
                continue;
            }
            for (BomMaterialVO row : rows) {
                if (row.getIterationOid() == null || row.getIterationOid().isEmpty()) {
                    continue;
                }
                String parents = row.getParentNumbers() == null ? "" : row.getParentNumbers().trim();
                if (parents.isEmpty()) {
                    tops.add(row);
                }
            }
        }
        vo.setTopPartCount(tops.size());

        double totalCost = 0D;
        double directCost = 0D;
        int totalLines = 0;
        int missingLines = 0;
        // 子件单价缓存（迭代 oid → 完整单位成本）：同一零件会出现在多份快照里，别重复跑成本报告
        Map<String, Double> unitCostCache = new HashMap<>();
        for (BomMaterialVO top : tops) {
            BomCostReportVO report = costReport(top.getIterationOid());
            totalCost += report.getTotalCost() == null ? 0D : report.getTotalCost();
            directCost += report.getDirectCost() == null ? 0D : report.getDirectCost();
            totalLines += report.getTotalLines() == null ? 0 : report.getTotalLines();
            int missing = report.getMissingCostLines() == null ? 0 : report.getMissingCostLines();
            missingLines += missing;
            if (missing > 0 && vo.getMissingCostNumbers().size() < MAX_MISSING_COST_NUMBERS
                    && top.getNumber() != null && !vo.getMissingCostNumbers().contains(top.getNumber())) {
                vo.getMissingCostNumbers().add(top.getNumber());
            }
            // 没有下级的单件不画线：一条恒等于自身价的水平线说明不了任何事
            if (report.getTotalLines() == null || report.getTotalLines() <= 0) {
                continue;
            }
            vo.getSeries().add(buildCostSeries(top, report, unitCostCache));
        }

        vo.setCurrentTotalCost(round2(totalCost));
        vo.setCurrentDirectCost(round2(directCost));
        vo.setTotalLines(totalLines);
        vo.setMissingCostLines(missingLines);
        vo.setHasBom(totalLines > 0);

        // 线多的时候按"当前成本"从高到低：最贵的那个件才是这张卡的主角
        vo.getSeries().sort((a, b) -> Double.compare(
                b.getCurrentCost() == null ? 0D : b.getCurrentCost(),
                a.getCurrentCost() == null ? 0D : a.getCurrentCost()));

        // 缺口如实说明：这些正是"为什么没有/只有一段线"的原因
        if (totalLines == 0) {
            vo.getWarnings().add("当前容器（含旗下型号）没有 BOM 行，成本为 0");
            /*
             * 型号页最常见的困惑：明明产品有 BOM，这儿却是 0 —— 因为 BOM 挂在父系列容器的零件上。
             * 查一下父系列有没有零件，有就直接指路，别让人以为是功能坏了。
             */
            if (model != null && model.getParentOid() != null && !model.getParentOid().isEmpty()) {
                List<BomMaterialVO> parentRows = bomLinksMapper.selectMaterialsByContainerOid(model.getParentOid());
                if (parentRows != null && !parentRows.isEmpty()) {
                    vo.getWarnings().add("该型号容器下没有 BOM 行，但父系列容器有零件 —— 当前 BOM 挂在系列层，成本请到系列页查看");
                }
            }
        }
        if (missingLines > 0) {
            vo.getWarnings().add("有 " + missingLines + " 行未填「单位成本」，已按 0 计入 —— 实际成本可能更高");
        }
        int snapshotTotal = 0;
        for (ContainerCostTrendVO.Series series : vo.getSeries()) {
            snapshotTotal += series.getSnapshotCount() == null ? 0 : series.getSnapshotCount();
        }
        if (snapshotTotal == 0 && totalLines > 0) {
            vo.getWarnings().add("还没有保存过 BOM 快照：曲线现在只有\"当前\"一个点，"
                    + "在「BOM 结构 → 快照」保存一份后就会多一个历史点");
        }
        return vo;
    }

    /**
     * 容器（系列会并入旗下各型号）的当前卷积总成本 —— 与 {@link #containerCostTrend(String)} 同口径，
     * 但不回放 BOM 快照。
     *
     * <p>为什么单开一个：指标评估（{@code COST_ACHIEVE}）在每次 {@code GET /api/instances} 时都会跑，
     * 而趋势接口会为每个顶层件把历史快照逐份重算一遍 —— 打开一页的成本不该由"画不画曲线"决定。
     */
    @Override
    public Double containerCurrentCost(String containerOid) {
        if (containerOid == null || containerOid.trim().isEmpty()) {
            return null;
        }
        String oid = containerOid.trim();
        List<String> containers = new ArrayList<>();
        containers.add(oid);
        // 系列必须并入旗下型号：BOM 挂在型号的零件上，系列容器自己通常一个零件都没有
        if (productModelService.findByOid(oid) == null && productLineService.findByOid(oid) != null) {
            for (ProductModel child : productModelService.findByProductLineOid(oid)) {
                if (child.getOid() != null && !child.getOid().isEmpty()) {
                    containers.add(child.getOid());
                }
            }
        }

        double total = 0D;
        boolean any = false;
        for (String container : containers) {
            List<BomMaterialVO> rows = bomLinksMapper.selectMaterialsByContainerOid(container);
            if (rows == null) {
                continue;
            }
            for (BomMaterialVO row : rows) {
                if (row.getIterationOid() == null || row.getIterationOid().isEmpty()) {
                    continue;
                }
                // 只从顶层件卷积：总成 + 它的零件逐项相加会把同一批成本重复累加
                String parents = row.getParentNumbers() == null ? "" : row.getParentNumbers().trim();
                if (!parents.isEmpty()) {
                    continue;
                }
                BomCostReportVO report = costReport(row.getIterationOid());
                if (report == null) {
                    continue;
                }
                any = true;
                total += report.getTotalCost() == null ? 0D : report.getTotalCost();
            }
        }
        return any ? round2(total) : null;
    }

    /**
     * 一个顶层件的成本曲线：<b>BOM 快照点（按时间升序）+ 末尾一个"当前"点</b>。
     *
     * <p>刻意<b>不</b>再单独记"成本快照"：{@code ck_bom_snapshot} 的行上带着当时的「数量 / 单位成本」，
     * 成本能算出来；另存一份成本必然出现"结构改了、成本没改"的偏差。
     *
     * <p><b>纵轴统一是卷积成本</b>（与卡片大数字同口径）：
     * <ul>
     *   <li>快照点 = Σ(快照里的用量 × 子件在<b>该快照版本</b>上的完整单位成本)；</li>
     *   <li>末点 = 当前 BOM 的卷积成本（实时算）。</li>
     * </ul>
     * 这样"只有 1 份快照"就能画出两个点（那时 → 现在），且两个点的含义一致。
     */
    private ContainerCostTrendVO.Series buildCostSeries(BomMaterialVO top, BomCostReportVO report,
                                                        Map<String, Double> unitCostCache) {
        ContainerCostTrendVO.Series series = new ContainerCostTrendVO.Series();
        series.setPartOid(top.getChildPartOid() == null ? top.getOid() : top.getChildPartOid());
        series.setPartNumber(top.getNumber());
        series.setPartName(top.getName());
        series.setVersionLabel(top.getVersion());
        // 与末点同口径：当前卷积总成本
        series.setCurrentCost(round2(report.getTotalCost() == null ? 0D : report.getTotalCost()));

        List<ContainerCostTrendVO.Point> points = new ArrayList<>();
        // 快照列表接口会把 snapshotJson 清空（列表不该背几百行明细），这里要算成本，所以直接走 mapper
        List<BomSnapshot> snapshots = series.getPartOid() == null || series.getPartOid().isEmpty()
                ? null : bomSnapshotMapper.selectList(series.getPartOid(), null);
        if (snapshots != null) {
            for (BomSnapshot snapshot : snapshots) {
                Double cost = snapshotRolledUpCost(snapshot.getSnapshotJson(), unitCostCache);
                if (cost == null) {
                    continue;
                }
                ContainerCostTrendVO.Point point = new ContainerCostTrendVO.Point();
                point.setTime(snapshot.getCreatedAt());
                point.setCost(round2(cost));
                point.setLabel(snapshot.getName());
                point.setVersionLabel(snapshot.getVersionLabel());
                point.setCurrent(Boolean.FALSE);
                points.add(point);
            }
        }
        points.sort((a, b) -> {
            if (a.getTime() == null || b.getTime() == null) {
                return 0;
            }
            return a.getTime().compareTo(b.getTime());
        });
        series.setSnapshotCount(points.size());
        // 只留最近一段：趋势看的是"最近怎么变"，不是考古
        if (points.size() > MAX_COST_POINTS) {
            points = new ArrayList<>(points.subList(points.size() - MAX_COST_POINTS, points.size()));
        }

        // 末尾补"当前"点：存过 1 份快照就有"那时 → 现在"的一段变化
        ContainerCostTrendVO.Point now = new ContainerCostTrendVO.Point();
        now.setTime(LocalDateTime.now());
        now.setCost(series.getCurrentCost());
        now.setLabel("当前");
        now.setCurrent(Boolean.TRUE);
        points.add(now);
        series.setPoints(points);
        return series;
    }

    /**
     * BOM 快照 → 该时点的<b>卷积</b>成本，口径与 {@code costReport(parentIteration).totalCost} 完全一致：
     *
     * <pre>
     *   本行完整单位成本 = 快照里这一行的「单位成本」 + 子件自己那棵树的成本
     *   快照总成本      = Σ 当时用量 × 本行完整单位成本
     * </pre>
     *
     * <p>为什么能算准：BOM 行挂在迭代上、迭代发布后不可变，单位成本也是行上的字段 ——
     * 拿快照里记的「子件 + 版本」找回那一版迭代，它自己的成本报告就是当时的子树成本。
     *
     * <p><b>容易踩的坑</b>：子件自身的单价记在<b>父件的这一行</b>上，不在子件自己的 BOM 里。
     * 只取子件子树成本会漏掉这一层（本库里 295 会被算成 45）。
     *
     * <p>兜底：版本找不到 → 用子件最新迭代；连子件标识都没有 → 只算本行单位成本；
     * 整份快照没有可用行 → 返回 {@code null}，让这个点不画。
     *
     * <p>{@code unitCostCache} 按迭代缓存子树成本：同一批零件会在多份快照里反复出现，
     * 不缓存就会对每个点重跑一遍成本报告。
     */
    private Double snapshotRolledUpCost(String snapshotJson, Map<String, Double> unitCostCache) {
        if (snapshotJson == null || snapshotJson.trim().isEmpty()) {
            return null;
        }
        try {
            Map<String, Object> root = objectMapper.readValue(
                    snapshotJson, new TypeReference<Map<String, Object>>() {});
            Object items = root == null ? null : root.get("items");
            if (!(items instanceof List)) {
                return null;
            }
            double sum = 0D;
            boolean any = false;
            for (Object one : (List<?>) items) {
                if (!(one instanceof Map)) {
                    continue;
                }
                Map<?, ?> item = (Map<?, ?>) one;
                Double quantity = toDouble(item.get("quantity"));
                if (quantity == null) {
                    continue;
                }
                // 完整单位成本 = 本行记的单价（该件的"自身成本"） + 子件自己那棵树的成本
                Double ownUnitCost = toDouble(item.get("unitCost"));
                double unitCost = ownUnitCost == null ? 0D : ownUnitCost;
                String childPartOid = asText(item.get("childPartOid"));
                if (childPartOid != null) {
                    Double subtreeCost = childSubtreeCost(childPartOid, asText(item.get("version")), unitCostCache);
                    if (subtreeCost != null) {
                        unitCost += subtreeCost;
                    }
                }
                sum += quantity * unitCost;
                any = true;
            }
            return any ? sum : null;
        } catch (Exception e) {
            log.warn("解析 BOM 快照成本失败: {}", e.getMessage());
            return null;
        }
    }

    /**
     * 子件在指定版本上"自己那棵树"的成本（＝它自己 BOM 展开后的一件成本，<b>不含</b>父件行上记的它自身单价）。
     *
     * <p>版本优先精确匹配 {@code display_version}（如 A.2），匹配不到就退回最新迭代 ——
     * 快照里的版本串可能来自被删掉的版本，不能让整条曲线因此断掉。
     */
    private Double childSubtreeCost(String childPartOid, String version, Map<String, Double> cache) {
        List<PartIteration> iterations = partIterationMapper.selectByMasterOid(childPartOid);
        if (iterations == null || iterations.isEmpty()) {
            return null;
        }
        PartIteration matched = null;
        if (version != null && !version.isEmpty()) {
            for (PartIteration iteration : iterations) {
                String display = iteration.getDisplayVersion();
                // iteration 是 int，无需判空；display_version 缺失时才用 revision.iteration 拼
                String composed = (iteration.getRevision() == null ? "" : iteration.getRevision())
                        + "." + iteration.getIteration();
                if (version.equals(display) || version.equals(composed)) {
                    matched = iteration;
                    break;
                }
            }
        }
        if (matched == null) {
            // mapper 已按 revision, iteration 降序，第一条就是最新
            matched = iterations.get(0);
        }
        Double cached = cache.get(matched.getOid());
        if (cached != null) {
            return cached;
        }
        BomCostReportVO report = costReport(matched.getOid());
        Double unitCost = report.getTotalCost() == null ? 0D : report.getTotalCost();
        cache.put(matched.getOid(), unitCost);
        return unitCost;
    }

    private String asText(Object value) {
        if (value == null) {
            return null;
        }
        String text = String.valueOf(value).trim();
        return text.isEmpty() ? null : text;
    }

    private Double toDouble(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Number) {
            return ((Number) value).doubleValue();
        }
        try {
            return Double.parseDouble(String.valueOf(value).trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private Double round2(double value) {
        return Math.round(value * 100D) / 100D;
    }

    /**
     * 给物料清单每行补缩略图。
     *
     * <p>取不到就留空（前端给占位图）：没关联工程数据、或关联了但还没转换完，
     * 都不该让列表报错 —— 缩略图是"锦上添花"，列表本身不能因为它挂掉。
     */
    private void fillThumbnails(List<BomMaterialVO> rows) {
        if (rows == null || rows.isEmpty()) {
            return;
        }
        List<String> iterationOids = new ArrayList<>();
        for (BomMaterialVO row : rows) {
            if (row.getIterationOid() != null && !iterationOids.contains(row.getIterationOid())) {
                iterationOids.add(row.getIterationOid());
            }
        }
        if (iterationOids.isEmpty()) {
            return;
        }
        try {
            Map<String, String> thumbByIteration = new HashMap<>();
            for (Map<String, Object> found : cadVisualizationMapper.selectThumbnailsByPartIterations(iterationOids)) {
                Object iterationOid = found.get("partIterationOid");
                Object thumbOid = found.get("thumbnailCkfileOid");
                if (iterationOid != null && thumbOid != null) {
                    thumbByIteration.put(iterationOid.toString(), thumbOid.toString());
                }
            }
            for (BomMaterialVO row : rows) {
                if (row.getIterationOid() != null) {
                    row.setThumbnailCkfileOid(thumbByIteration.get(row.getIterationOid()));
                }
            }
        } catch (RuntimeException e) {
            // 缩略图查询失败不拖垮物料清单：截图失败是"少一张图"，列表报错是"用不了"
            log.warn("物料清单缩略图查询失败（列表继续返回）: {}", e.getMessage());
        }
    }

    @Override
    public List<BomLinks> listAll() {
        return bomLinksMapper.selectAll();
    }
}
