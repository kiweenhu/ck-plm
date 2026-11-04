/*
 * Copyright (c) 2026~2028 深圳乘恺科技有限公司
 * All rights reserved.
 *
 * @author Kiween.Hu; Roney.Liu
 */

package cn.ck.plm.bom.dto;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * BOM 快照对比结果。
 *
 * <p>差异行的字段与 {@code BomDiff.diffJson} 完全同名（{@code lineNumber/childPartOid/number/name/
 * version/quantity/unit/unitCost}，修改行另外带 {@code from} 原值对照），前端可以直接复用
 * 「BOM 版本对比」那套渲染。
 */
public class BomSnapshotCompareVO {

    /** 源侧标签（快照名 + 版本） */
    private String fromLabel;

    /** 目标侧标签（快照名 + 版本 / 当前 BOM） */
    private String toLabel;

    /** 目标侧相对源侧新增的行数 */
    private int addedCount;

    /** 目标侧已删除（源侧有、目标侧没有）的行数 */
    private int removedCount;

    /** 两侧都有但属性有变化的行数 */
    private int changedCount;

    private List<Map<String, Object>> added = new ArrayList<>();

    private List<Map<String, Object>> removed = new ArrayList<>();

    private List<Map<String, Object>> changed = new ArrayList<>();

    public String getFromLabel() { return fromLabel; }
    public void setFromLabel(String fromLabel) { this.fromLabel = fromLabel; }

    public String getToLabel() { return toLabel; }
    public void setToLabel(String toLabel) { this.toLabel = toLabel; }

    public int getAddedCount() { return addedCount; }
    public void setAddedCount(int addedCount) { this.addedCount = addedCount; }

    public int getRemovedCount() { return removedCount; }
    public void setRemovedCount(int removedCount) { this.removedCount = removedCount; }

    public int getChangedCount() { return changedCount; }
    public void setChangedCount(int changedCount) { this.changedCount = changedCount; }

    public List<Map<String, Object>> getAdded() { return added; }
    public void setAdded(List<Map<String, Object>> added) { this.added = added; }

    public List<Map<String, Object>> getRemoved() { return removed; }
    public void setRemoved(List<Map<String, Object>> removed) { this.removed = removed; }

    public List<Map<String, Object>> getChanged() { return changed; }
    public void setChanged(List<Map<String, Object>> changed) { this.changed = changed; }
}
