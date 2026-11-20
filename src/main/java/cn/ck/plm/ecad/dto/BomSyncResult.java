/*
 * Copyright (c) 2026~2028 深圳乘恺科技有限公司
 * All rights reserved.
 *
 * @author Kiween.Hu
 */

package cn.ck.plm.ecad.dto;

import java.util.List;

/**
 * BOM 同步结果 —— 本次同步与上次原理图 BOM 的差异，前端可直接用于"一键发起 ECR"。
 */
public class BomSyncResult {

    /** 同步总行数 */
    private int totalCount;

    /** 新增行（上次 BOM 没有的位号） */
    private List<BomSyncItem> added;

    /** 移除行（本次 BOM 没有的位号） */
    private List<BomSyncItem> removed;

    /** 变化行（位号相同但数量 / 部件 / 封装不同） */
    private List<BomSyncItem> changed;

    /** 未能匹配到 PLM 部件的行（需建新部件或补编号） */
    private List<BomSyncItem> unmatched;

    public int getTotalCount() { return totalCount; }
    public void setTotalCount(int totalCount) { this.totalCount = totalCount; }

    public List<BomSyncItem> getAdded() { return added; }
    public void setAdded(List<BomSyncItem> added) { this.added = added; }

    public List<BomSyncItem> getRemoved() { return removed; }
    public void setRemoved(List<BomSyncItem> removed) { this.removed = removed; }

    public List<BomSyncItem> getChanged() { return changed; }
    public void setChanged(List<BomSyncItem> changed) { this.changed = changed; }

    public List<BomSyncItem> getUnmatched() { return unmatched; }
    public void setUnmatched(List<BomSyncItem> unmatched) { this.unmatched = unmatched; }
}
