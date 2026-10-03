/*
 * Copyright (c) 2026~2028 深圳乘恺科技有限公司
 * All rights reserved.
 *
 * @author Kiween.Hu; Roney.Liu
 */

package cn.ck.plm.bom.dto;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 成组替代组（{@code ck_bom_substitute_group} + 成员）的展示 DTO。
 *
 * <p>为什么要 VO：成组替代的单位是"一组行"，界面上要看的是"哪几行被哪几颗料整组替换"——
 * 光给一串 oid 没有意义，必须带上两侧的编码/名称（原料侧还要带它挂在第几行）。
 *
 * <p>成员按侧分开装在 {@link #sources} / {@link #substitutes} 两个 list 里，而不是混在一个
 * list 里让前端自己按 {@code memberSide} 分组：两侧在界面上本来就是两栏，接口顺着界面分。
 *
 * <p>两侧<b>不是按位次一一配对</b>的（见 {@code BomSubstituteGroup} 的类注释）：
 * 语义是"这一组原料行被这一组替代物料整组替换"，各自侧内部的 {@code sortOrder} 只决定展示顺序。
 */
public class BomSubstituteGroupVO {

    // ==================== 组头 ====================

    /** 组 oid */
    private String oid;

    /** 组编码（继承自 WithoutVersionEntity，可为空） */
    private String code;

    /** 组名称 */
    private String name;

    /** 说明 */
    private String description;

    /** 父件迭代 oid（组的挂载点） */
    private String parentIterationOid;

    /** 组状态：DRAFT / APPROVED / OBSOLETE（只有 APPROVED 参与下游解析） */
    private String status;

    /** 整组替换约束（true = 必须整组替换，不允许拆开换任何一个） */
    private Boolean atomicReplace;

    /** 是否启用 */
    private Boolean enabled;

    private String creator;

    private LocalDateTime createdAt;

    private String updater;

    private LocalDateTime updatedAt;

    /** 原料侧成员：被替换的 BOM 行 */
    private List<MemberVO> sources = new ArrayList<>();

    /** 替代侧成员：整组替换上去的物料 */
    private List<MemberVO> substitutes = new ArrayList<>();

    // ==================== 成员 ====================

    /**
     * 成组替代成员。一条成员行只能属于一侧：
     * {@code SOURCE} 用 {@code bomLinkOid}（表的 CHECK 约束保证另一侧字段为空）。
     */
    public static class MemberVO {

        private String oid;

        private String groupOid;

        /** SOURCE（原料侧）/ SUBSTITUTE（替代侧） */
        private String memberSide;

        /** 原料侧引用的 BOM 行 oid */
        private String bomLinkOid;

        /** 替代侧引用的物料 oid */
        private String partOid;

        /** 成员级数量因子（如 1 个原子件 = 3 个替代件） */
        private Double quantity;

        /** 单位 */
        private String unit;

        /** 同侧内部的展示顺序 */
        private Integer sortOrder;

        // ---------- 原料侧展示信息（来自 ck_bom_links + 其子件） ----------

        /** 该 BOM 行的行号 */
        private Integer bomLineNumber;

        /** 原料侧子件编码 */
        private String sourcePartNumber;

        /** 原料侧子件名称 */
        private String sourcePartName;

        // ---------- 替代侧展示信息（来自 ck_part） ----------

        /** 替代物料编码 */
        private String partNumber;

        /** 替代物料名称 */
        private String partName;

        /** 替代物料类型（类型定义 code） */
        private String partType;

        /** 替代物料最新版本的显示版本（service 补；查不到就留空，不编造） */
        private String partVersion;

        public String getOid() { return oid; }
        public void setOid(String oid) { this.oid = oid; }

        public String getGroupOid() { return groupOid; }
        public void setGroupOid(String groupOid) { this.groupOid = groupOid; }

        public String getMemberSide() { return memberSide; }
        public void setMemberSide(String memberSide) { this.memberSide = memberSide; }

        public String getBomLinkOid() { return bomLinkOid; }
        public void setBomLinkOid(String bomLinkOid) { this.bomLinkOid = bomLinkOid; }

        public String getPartOid() { return partOid; }
        public void setPartOid(String partOid) { this.partOid = partOid; }

        public Double getQuantity() { return quantity; }
        public void setQuantity(Double quantity) { this.quantity = quantity; }

        public String getUnit() { return unit; }
        public void setUnit(String unit) { this.unit = unit; }

        public Integer getSortOrder() { return sortOrder; }
        public void setSortOrder(Integer sortOrder) { this.sortOrder = sortOrder; }

        public Integer getBomLineNumber() { return bomLineNumber; }
        public void setBomLineNumber(Integer bomLineNumber) { this.bomLineNumber = bomLineNumber; }

        public String getSourcePartNumber() { return sourcePartNumber; }
        public void setSourcePartNumber(String sourcePartNumber) { this.sourcePartNumber = sourcePartNumber; }

        public String getSourcePartName() { return sourcePartName; }
        public void setSourcePartName(String sourcePartName) { this.sourcePartName = sourcePartName; }

        public String getPartNumber() { return partNumber; }
        public void setPartNumber(String partNumber) { this.partNumber = partNumber; }

        public String getPartName() { return partName; }
        public void setPartName(String partName) { this.partName = partName; }

        public String getPartType() { return partType; }
        public void setPartType(String partType) { this.partType = partType; }

        public String getPartVersion() { return partVersion; }
        public void setPartVersion(String partVersion) { this.partVersion = partVersion; }
    }

    // ==================== 组头 Getter / Setter ====================

    public String getOid() { return oid; }
    public void setOid(String oid) { this.oid = oid; }

    public String getCode() { return code; }
    public void setCode(String code) { this.code = code; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public String getParentIterationOid() { return parentIterationOid; }
    public void setParentIterationOid(String parentIterationOid) { this.parentIterationOid = parentIterationOid; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public Boolean getAtomicReplace() { return atomicReplace; }
    public void setAtomicReplace(Boolean atomicReplace) { this.atomicReplace = atomicReplace; }

    public Boolean getEnabled() { return enabled; }
    public void setEnabled(Boolean enabled) { this.enabled = enabled; }

    public String getCreator() { return creator; }
    public void setCreator(String creator) { this.creator = creator; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    public String getUpdater() { return updater; }
    public void setUpdater(String updater) { this.updater = updater; }

    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }

    public List<MemberVO> getSources() { return sources; }
    public void setSources(List<MemberVO> sources) { this.sources = sources; }

    public List<MemberVO> getSubstitutes() { return substitutes; }
    public void setSubstitutes(List<MemberVO> substitutes) { this.substitutes = substitutes; }
}
