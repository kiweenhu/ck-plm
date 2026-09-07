/*
 * Copyright (c) 2025 深圳乘恺科技有限公司
 * All rights reserved.
 *
 * @author Kiween.Hu; Roney.Liu
 */

package cn.ck.plm.bom.service.impl;

import cn.ck.plm.bom.dto.BomTreeNode;
import cn.ck.plm.bom.entity.BomDiff;
import cn.ck.plm.bom.mapper.BomDiffMapper;
import cn.ck.plm.bom.service.api.BomDiffService;
import cn.ck.plm.bom.service.api.BomLinksService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * BomDiff 业务服务实现。
 */
@Service
@Transactional
public class BomDiffServiceImpl implements BomDiffService {

    private static final Logger log = LoggerFactory.getLogger(BomDiffServiceImpl.class);

    @Autowired
    private BomDiffMapper bomDiffMapper;

    @Autowired
    private BomLinksService bomLinksService;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public BomDiff create(BomDiff diff) {
        LocalDateTime now = LocalDateTime.now();
        diff.setCreatedAt(now);
        diff.setUpdatedAt(now);
        bomDiffMapper.insert(diff);
        return diff;
    }

    @Override
    public void deleteByOid(String oid) {
        bomDiffMapper.deleteByOid(oid);
    }

    @Override
    public BomDiff getByOid(String oid) {
        return bomDiffMapper.selectByOid(oid);
    }

    @Override
    public List<BomDiff> listByFromIteration(String fromIterationOid) {
        return bomDiffMapper.selectByFromIterationOid(fromIterationOid);
    }

    @Override
    public List<BomDiff> listByToIteration(String toIterationOid) {
        return bomDiffMapper.selectByToIterationOid(toIterationOid);
    }

    @Override
    public BomDiff getByFromAndTo(String fromIterationOid, String toIterationOid) {
        return bomDiffMapper.selectByFromAndTo(fromIterationOid, toIterationOid);
    }

    /**
     * 实时对比两个迭代的顶层 BOM 行差异（新增/移除/修改）。
     * 不依赖预计算数据，两次拉取 BOM 树后按子件主键逐行比对。
     */
    @Override
    public BomDiff compareNow(String fromIterationOid, String toIterationOid) {
        List<BomTreeNode> fromRows = safeTree(bomLinksService.buildTree(fromIterationOid));
        List<BomTreeNode> toRows = safeTree(bomLinksService.buildTree(toIterationOid));

        // 以 childPartOid 作为行身份 key（同一父迭代下同一子件只允许出现一次，由行号唯一性兜底）
        Map<String, BomTreeNode> fromMap = indexByChild(fromRows);
        Map<String, BomTreeNode> toMap = indexByChild(toRows);

        List<Map<String, Object>> added = new ArrayList<>();
        List<Map<String, Object>> removed = new ArrayList<>();
        List<Map<String, Object>> changed = new ArrayList<>();

        // 目标相对源：新增 / 修改
        for (Map.Entry<String, BomTreeNode> e : toMap.entrySet()) {
            BomTreeNode toNode = e.getValue();
            BomTreeNode fromNode = fromMap.get(e.getKey());
            if (fromNode == null) {
                added.add(nodeJson(toNode, null));
            } else if (isRowChanged(fromNode, toNode)) {
                changed.add(nodeJson(toNode, fromNode));
            }
        }
        // 源相对目标：移除
        for (String key : fromMap.keySet()) {
            if (!toMap.containsKey(key)) {
                removed.add(nodeJson(fromMap.get(key), null));
            }
        }

        BomDiff diff = new BomDiff();
        diff.setFromIterationOid(fromIterationOid);
        diff.setToIterationOid(toIterationOid);
        diff.setAddedCount(added.size());
        diff.setRemovedCount(removed.size());
        diff.setChangedCount(changed.size());
        try {
            Map<String, Object> json = new LinkedHashMap<>();
            json.put("added", added);
            json.put("removed", removed);
            json.put("changed", changed);
            diff.setDiffJson(objectMapper.writeValueAsString(json));
        } catch (Exception ex) {
            log.warn("序列化 BOM Diff JSON 失败: {}", ex.getMessage());
        }
        return diff;
    }

    private List<BomTreeNode> safeTree(List<BomTreeNode> tree) {
        return tree == null ? new ArrayList<>() : tree;
    }

    private Map<String, BomTreeNode> indexByChild(List<BomTreeNode> rows) {
        Map<String, BomTreeNode> map = new LinkedHashMap<>();
        for (BomTreeNode row : rows) {
            if (row != null && row.getChildPartOid() != null) {
                map.put(row.getChildPartOid(), row);
            }
        }
        return map;
    }

    /** 判断两行是否有实质差异（用量/单位/成本/子件版本变化） */
    private boolean isRowChanged(BomTreeNode a, BomTreeNode b) {
        return !Objects.equals(normalize(a.getQuantity()), normalize(b.getQuantity()))
                || !Objects.equals(trimToNull(a.getUnit()), trimToNull(b.getUnit()))
                || !Objects.equals(trimToNull(a.getChildVersion()), trimToNull(b.getChildVersion()))
                || !Objects.equals(normalize(a.getUnitCost()), normalize(b.getUnitCost()));
    }

    private Double normalize(Double d) {
        if (d == null) return null;
        if (d == Math.floor(d)) return (double) d.longValue(); // 去除浮点尾差（2.0 → 2.0）
        return Math.round(d * 1e9) / 1e9;
    }

    private String trimToNull(String s) {
        return s == null || s.trim().isEmpty() ? null : s.trim();
    }

    /**
     * 生成差异行 JSON。
     * to 非空表示该行来自目标版本；from 非空（changed 场景）表示目标行的原值对照。
     */
    private Map<String, Object> nodeJson(BomTreeNode node, BomTreeNode fromNode) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("lineNumber", node.getLineNumber());
        m.put("childPartOid", node.getChildPartOid());
        m.put("number", node.getChildPartNumber());
        m.put("name", node.getChildPartName());
        m.put("version", node.getChildVersion());
        m.put("quantity", node.getQuantity());
        m.put("unit", node.getUnit());
        m.put("unitCost", node.getUnitCost());
        if (fromNode != null) {
            Map<String, Object> f = new LinkedHashMap<>();
            f.put("lineNumber", fromNode.getLineNumber());
            f.put("quantity", fromNode.getQuantity());
            f.put("unit", fromNode.getUnit());
            f.put("unitCost", fromNode.getUnitCost());
            f.put("version", fromNode.getChildVersion());
            m.put("from", f);
        }
        return m;
    }
}
