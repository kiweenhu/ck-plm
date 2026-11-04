/*
 * Copyright (c) 2026~2028 深圳乘恺科技有限公司
 * All rights reserved.
 *
 * @author Kiween.Hu; Roney.Liu
 */

package cn.ck.plm.bom.service.impl;

import cn.ck.plm.bom.dto.BomSnapshotCompareVO;
import cn.ck.plm.bom.dto.BomTreeNode;
import cn.ck.plm.bom.entity.BomSnapshot;
import cn.ck.plm.bom.mapper.BomSnapshotMapper;
import cn.ck.plm.bom.service.api.BomLinksService;
import cn.ck.plm.bom.service.api.BomSnapshotService;
import cn.ck.plm.part.entity.PartIteration;
import cn.ck.plm.part.mapper.PartIterationMapper;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * BomSnapshot 业务服务实现。
 *
 * <p>快照内容 = 父件迭代<b>顶层</b> BOM 行（界面上那张子件清单）。孙件属于子件自己的结构，
 * 由子件的版本带着走；把它们也拍进来会让"用量改了 1 个"变成几百行差异，且与「BOM 版本对比」口径不一致。
 */
@Service
@Transactional
public class BomSnapshotServiceImpl implements BomSnapshotService {

    private static final Logger log = LoggerFactory.getLogger(BomSnapshotServiceImpl.class);

    private static final int NAME_MAX = 100;
    private static final int REMARK_MAX = 500;

    @Autowired
    private BomSnapshotMapper bomSnapshotMapper;

    @Autowired
    private BomLinksService bomLinksService;

    @Autowired
    private PartIterationMapper partIterationMapper;

    private final ObjectMapper objectMapper = new ObjectMapper();

    // ==================== 新增快照 ====================

    @Override
    public BomSnapshot createSnapshot(String iterationOid, String name, String remark) {
        if (isBlank(iterationOid)) {
            throw new IllegalArgumentException("缺少迭代标识，无法创建快照");
        }
        if (isBlank(name)) {
            throw new IllegalArgumentException("请填写快照名称");
        }
        PartIteration iteration = partIterationMapper.selectByOid(iterationOid.trim());
        if (iteration == null) {
            throw new IllegalArgumentException("迭代不存在: " + iterationOid);
        }

        List<BomTreeNode> rows = safeRows(bomLinksService.buildTree(iteration.getOid()));
        List<Map<String, Object>> items = new ArrayList<>(rows.size());
        for (BomTreeNode row : rows) {
            items.add(itemJson(row));
        }

        BomSnapshot snapshot = new BomSnapshot();
        snapshot.setIterationOid(iteration.getOid());
        snapshot.setPartOid(iteration.getMasterOid());
        snapshot.setName(truncate(name.trim(), NAME_MAX));
        snapshot.setRemark(isBlank(remark) ? null : truncate(remark.trim(), REMARK_MAX));
        snapshot.setVersionLabel(versionLabel(iteration));
        snapshot.setItemCount(items.size());
        snapshot.setNodeCount(countNodes(rows));
        snapshot.setMaxDepth(maxDepth(rows));
        snapshot.setSnapshotJson(writeItems(items));
        // oid / creator / tenant_oid 由 AuditInterceptor + TenantFillInterceptor 补齐
        bomSnapshotMapper.insert(snapshot);
        log.info("BOM 快照已创建: part={} iteration={} name={} items={}",
                snapshot.getPartOid(), snapshot.getIterationOid(), snapshot.getName(), snapshot.getItemCount());
        return snapshot;
    }

    // ==================== 查询 ====================

    @Override
    public List<BomSnapshot> listSnapshots(String partOid, String iterationOid) {
        List<BomSnapshot> list = bomSnapshotMapper.selectList(partOid, iterationOid);
        if (list == null) {
            return new ArrayList<>();
        }
        // 列表不需要 BOM 明细：几百行的 JSON 传过去只会拖慢弹窗，行数用 itemCount 展示
        for (BomSnapshot snapshot : list) {
            snapshot.setSnapshotJson(null);
        }
        return list;
    }

    @Override
    public BomSnapshot getByOid(String oid) {
        return isBlank(oid) ? null : bomSnapshotMapper.selectByOid(oid);
    }

    @Override
    public void delete(String oid) {
        if (isBlank(oid)) {
            throw new IllegalArgumentException("缺少快照标识");
        }
        bomSnapshotMapper.deleteByOid(oid);
    }

    // ==================== 对比 ====================

    @Override
    public BomSnapshotCompareVO compare(String fromSnapshotOid, String toSnapshotOid, String toIterationOid) {
        BomSnapshot from = getByOid(fromSnapshotOid);
        if (from == null) {
            throw new IllegalArgumentException("源快照不存在，可能已被删除");
        }
        List<Map<String, Object>> fromItems = readItems(from.getSnapshotJson());

        List<Map<String, Object>> toItems;
        String toLabel;
        if (!isBlank(toSnapshotOid)) {
            BomSnapshot to = getByOid(toSnapshotOid);
            if (to == null) {
                throw new IllegalArgumentException("目标快照不存在，可能已被删除");
            }
            toItems = readItems(to.getSnapshotJson());
            toLabel = label(to);
        } else if (!isBlank(toIterationOid)) {
            toItems = new ArrayList<>();
            for (BomTreeNode row : safeRows(bomLinksService.buildTree(toIterationOid.trim()))) {
                toItems.add(itemJson(row));
            }
            PartIteration iteration = partIterationMapper.selectByOid(toIterationOid.trim());
            toLabel = iteration == null ? "当前 BOM" : "当前 BOM（" + versionLabel(iteration) + "）";
        } else {
            throw new IllegalArgumentException("请指定对比目标（快照或当前 BOM）");
        }

        BomSnapshotCompareVO result = diff(fromItems, toItems);
        result.setFromLabel(label(from));
        result.setToLabel(toLabel);
        return result;
    }

    /**
     * 逐行比对：以子件主对象 oid 作为行身份（与「BOM 版本对比」同一口径）。
     *
     * <p>同一父件里同一子件出现多次时只保留最后一行 —— 与 {@code BomDiffServiceImpl#compareNow}
     * 的已知限制一致；要区分的话得把行号纳入身份，但那样"上移一行"会被算成删除 + 新增。
     */
    private BomSnapshotCompareVO diff(List<Map<String, Object>> fromItems, List<Map<String, Object>> toItems) {
        Map<String, Map<String, Object>> fromMap = indexByChild(fromItems);
        Map<String, Map<String, Object>> toMap = indexByChild(toItems);

        List<Map<String, Object>> added = new ArrayList<>();
        List<Map<String, Object>> removed = new ArrayList<>();
        List<Map<String, Object>> changed = new ArrayList<>();

        for (Map.Entry<String, Map<String, Object>> entry : toMap.entrySet()) {
            Map<String, Object> toRow = entry.getValue();
            Map<String, Object> fromRow = fromMap.get(entry.getKey());
            if (fromRow == null) {
                added.add(copyRow(toRow));
            } else if (isRowChanged(fromRow, toRow)) {
                Map<String, Object> row = copyRow(toRow);
                row.put("from", changedFrom(fromRow));
                changed.add(row);
            }
        }
        for (Map.Entry<String, Map<String, Object>> entry : fromMap.entrySet()) {
            if (!toMap.containsKey(entry.getKey())) {
                removed.add(copyRow(entry.getValue()));
            }
        }

        BomSnapshotCompareVO vo = new BomSnapshotCompareVO();
        vo.setAdded(added);
        vo.setRemoved(removed);
        vo.setChanged(changed);
        vo.setAddedCount(added.size());
        vo.setRemovedCount(removed.size());
        vo.setChangedCount(changed.size());
        return vo;
    }

    private Map<String, Map<String, Object>> indexByChild(List<Map<String, Object>> items) {
        Map<String, Map<String, Object>> map = new LinkedHashMap<>();
        for (Map<String, Object> item : items) {
            Object childPartOid = item.get("childPartOid");
            if (childPartOid != null && !String.valueOf(childPartOid).trim().isEmpty()) {
                map.put(String.valueOf(childPartOid), item);
            }
        }
        return map;
    }

    /** 有实质差异：用量 / 单位 / 子件版本 / 单位成本（行号与名称变化不算"改 BOM"） */
    private boolean isRowChanged(Map<String, Object> a, Map<String, Object> b) {
        return !numEquals(a.get("quantity"), b.get("quantity"))
                || !Objects.equals(trimToNull(a.get("unit")), trimToNull(b.get("unit")))
                || !Objects.equals(trimToNull(a.get("version")), trimToNull(b.get("version")))
                || !numEquals(a.get("unitCost"), b.get("unitCost"));
    }

    private Map<String, Object> changedFrom(Map<String, Object> fromRow) {
        Map<String, Object> from = new LinkedHashMap<>();
        from.put("lineNumber", fromRow.get("lineNumber"));
        from.put("quantity", fromRow.get("quantity"));
        from.put("unit", fromRow.get("unit"));
        from.put("unitCost", fromRow.get("unitCost"));
        from.put("version", fromRow.get("version"));
        return from;
    }

    private Map<String, Object> copyRow(Map<String, Object> row) {
        return new LinkedHashMap<>(row);
    }

    // ==================== 快照内容 ====================

    /** 单个 BOM 行 → 快照条目（字段名与 BomDiff 的差异行一致，前端可复用同一套渲染） */
    private Map<String, Object> itemJson(BomTreeNode node) {
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("lineNumber", node.getLineNumber());
        item.put("childPartOid", node.getChildPartOid());
        item.put("number", node.getChildPartNumber());
        item.put("name", node.getChildPartName());
        item.put("version", node.getChildVersion());
        item.put("quantity", node.getQuantity());
        item.put("unit", node.getUnit());
        item.put("unitCost", node.getUnitCost());
        return item;
    }

    private String writeItems(List<Map<String, Object>> items) {
        Map<String, Object> root = new LinkedHashMap<>();
        root.put("items", items);
        try {
            return objectMapper.writeValueAsString(root);
        } catch (Exception e) {
            // 序列化失败不该写出半份快照：宁可让用户看到失败提示
            throw new IllegalStateException("快照内容序列化失败: " + e.getMessage(), e);
        }
    }

    private List<Map<String, Object>> readItems(String snapshotJson) {
        if (isBlank(snapshotJson)) {
            return new ArrayList<>();
        }
        try {
            Map<String, Object> root = objectMapper.readValue(
                    snapshotJson, new TypeReference<Map<String, Object>>() {});
            Object items = root == null ? null : root.get("items");
            if (items instanceof List) {
                List<Map<String, Object>> result = new ArrayList<>();
                for (Object one : (List<?>) items) {
                    if (one instanceof Map) {
                        //noinspection unchecked
                        result.add((Map<String, Object>) one);
                    }
                }
                return result;
            }
            return new ArrayList<>();
        } catch (Exception e) {
            log.warn("解析 BOM 快照内容失败: {}", e.getMessage());
            return new ArrayList<>();
        }
    }

    private int countNodes(List<BomTreeNode> rows) {
        int count = 0;
        for (BomTreeNode row : rows) {
            count += 1 + countNodes(safeRows(row.getChildren()));
        }
        return count;
    }

    private int maxDepth(List<BomTreeNode> rows) {
        int depth = 0;
        for (BomTreeNode row : rows) {
            depth = Math.max(depth, 1 + maxDepth(safeRows(row.getChildren())));
        }
        return depth;
    }

    // ==================== 工具 ====================

    private List<BomTreeNode> safeRows(List<BomTreeNode> rows) {
        return rows == null ? new ArrayList<>() : rows;
    }

    private String label(BomSnapshot snapshot) {
        String name = isBlank(snapshot.getName()) ? "未命名快照" : snapshot.getName();
        return isBlank(snapshot.getVersionLabel()) ? name : name + "（" + snapshot.getVersionLabel() + "）";
    }

    private String versionLabel(PartIteration iteration) {
        if (!isBlank(iteration.getDisplayVersion())) {
            return iteration.getDisplayVersion();
        }
        String revision = iteration.getRevision() == null ? "" : iteration.getRevision();
        return revision + "." + iteration.getIteration();
    }

    private boolean numEquals(Object a, Object b) {
        Double da = toDouble(a);
        Double db = toDouble(b);
        if (da == null && db == null) {
            return true;
        }
        if (da == null || db == null) {
            return false;
        }
        // 去掉浮点尾差：2 与 2.0 视为相同
        return Math.abs(da - db) < 1e-9;
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

    private String trimToNull(Object value) {
        if (value == null) {
            return null;
        }
        String text = String.valueOf(value).trim();
        return text.isEmpty() ? null : text;
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

    private String truncate(String value, int max) {
        return value.length() <= max ? value : value.substring(0, max);
    }
}
