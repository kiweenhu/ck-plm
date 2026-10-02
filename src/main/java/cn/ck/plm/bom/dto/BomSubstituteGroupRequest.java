/*
 * Copyright (c) 2025 深圳乘恺科技有限公司
 * All rights reserved.
 *
 * @author Kiween.Hu; Roney.Liu
 */

package cn.ck.plm.bom.dto;

import java.util.ArrayList;
import java.util.List;

/**
 * 成组替代的写入请求（新建与更新共用）。
 *
 * <p>为什么不复用 {@link BomSubstituteGroupVO}：VO 是"读"的口径（带一堆 join 出来的展示字段，
 * 前端也不该回传这些）。写入只需要"哪几行 + 哪几颗料 + 各自的换算"，
 * 分开之后字段归属一目了然，也避免前端把展示字段当真值传回来。
 *
 * <p>更新时 {@link #sources}/{@link #substitutes} 是<b>整体替换</b>：给什么就是什么，
 * 不玩增量 diff —— 一次提交说清"这个组现在是什么样"，比推敲"删了哪条加了哪条"可靠得多。
 */
public class BomSubstituteGroupRequest {

    /** 父件迭代 oid（新建必填；更新时以此为准，忽略请求里的新值） */
    private String parentIterationOid;

    /** 组名称 */
    private String name;

    /** 说明 */
    private String description;

    /** 整组替换约束（null = 按 true） */
    private Boolean atomicReplace;

    /** 是否启用（null = 按 true） */
    private Boolean enabled;

    /** 原料侧：被替换的 BOM 行 */
    private List<MemberInput> sources = new ArrayList<>();

    /** 替代侧：整组替换上去的物料 */
    private List<MemberInput> substitutes = new ArrayList<>();

    /** 成员输入：原料侧填 {@link #bomLinkOid}，替代侧填 {@link #partOid} */
    public static class MemberInput {

        /** 原料侧：BOM 行 oid */
        private String bomLinkOid;

        /** 替代侧：物料 oid */
        private String partOid;

        /** 数量因子（null = 1） */
        private Double quantity;

        /** 单位（空 = 原料侧取该 BOM 行的单位 / 替代侧取物料默认） */
        private String unit;

        public String getBomLinkOid() { return bomLinkOid; }
        public void setBomLinkOid(String bomLinkOid) { this.bomLinkOid = bomLinkOid; }

        public String getPartOid() { return partOid; }
        public void setPartOid(String partOid) { this.partOid = partOid; }

        public Double getQuantity() { return quantity; }
        public void setQuantity(Double quantity) { this.quantity = quantity; }

        public String getUnit() { return unit; }
        public void setUnit(String unit) { this.unit = unit; }
    }

    public String getParentIterationOid() { return parentIterationOid; }
    public void setParentIterationOid(String parentIterationOid) { this.parentIterationOid = parentIterationOid; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public Boolean getAtomicReplace() { return atomicReplace; }
    public void setAtomicReplace(Boolean atomicReplace) { this.atomicReplace = atomicReplace; }

    public Boolean getEnabled() { return enabled; }
    public void setEnabled(Boolean enabled) { this.enabled = enabled; }

    public List<MemberInput> getSources() { return sources; }
    public void setSources(List<MemberInput> sources) { this.sources = sources; }

    public List<MemberInput> getSubstitutes() { return substitutes; }
    public void setSubstitutes(List<MemberInput> substitutes) { this.substitutes = substitutes; }
}
