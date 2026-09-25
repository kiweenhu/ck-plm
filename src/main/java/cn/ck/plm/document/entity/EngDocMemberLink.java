/*
 * Copyright (c) 2025 深圳乘恺科技有限公司
 * All rights reserved.
 *
 * @author Kiween.Hu; Roney.Liu
 */

package cn.ck.plm.document.entity;

import cn.ck.plm.base.entity.BaseEntity;
import cn.ck.plm.base.entity.TenantEntity;

import java.math.BigDecimal;

/**
 * 工程数据装配成员链接（EPM Member Link）—— 对齐 Windchill {@code wt.epm.structure.EPMMemberLink}。
 *
 * <p>表达 CAD <b>装配结构（BOM）</b>：父装配「使用（uses）」了某个子件。对应表
 * {@code ck_eng_doc_member_link}。
 *
 * <h3>与 {@link EngDocRefLink} 的根本区别</h3>
 * <ul>
 *   <li>成员链接 = <b>组成/装配</b>关系，构成产品结构的层级（装配树）；<b>必有数量</b>。</li>
 *   <li>引用链接 = <b>横向引用</b>关系，构成文档间的引用网；无数量概念。</li>
 * </ul>
 * 二者均实现 {@link EngDocDependencyLink}（公共契约），正如 Windchill 中
 * {@code EPMMemberLink} / {@code EPMReferenceLink} 均实现 {@code EPMDependencyLink}。
 *
 * <h3>Windchill 属性对照</h3>
 * <table border="1">
 *   <tr><th>EPMMemberLink</th><th>本类字段</th><th>说明</th></tr>
 *   <tr><td>roleA「usedBy」</td><td>{@code usedByIterationOid}</td><td>父装配的<b>迭代</b>（Iterated 端）</td></tr>
 *   <tr><td>roleB「uses」</td><td>{@code usesMasterOid}</td><td>子件<b>主对象</b>（Mastered 端）</td></tr>
 *   <tr><td>{@code QUANTITY}（required）</td><td>{@code quantity}</td><td>用量，必有；带 transform 时必须为 1</td></tr>
 *   <tr><td>{@code placed} / {@code hasTransform()}</td><td>{@code placed} / {@code hasTransform} / {@code transform}</td><td>是否放置、是否真带变换、变换矩阵</td></tr>
 *   <tr><td>{@code FIXED} / {@code substitute} / {@code SUPPRESSED} / {@code ANNOTATED}</td><td>同名字段</td><td>固定件 / 替代件 / 抑制 / 标注</td></tr>
 *   <tr><td>{@code modelItemOwnerId} / {@code modelItemOwnerType}</td><td>同名字段</td><td>阵列/组（pattern/group）归属</td></tr>
 *   <tr><td>{@code compNumber} / {@code compRevNumber} / {@code compLayerIdx}</td><td>同名字段</td><td>组件编号 / 版本号 / 层索引</td></tr>
 *   <tr><td>{@code NAME} / {@code IDENTIFIER} / {@code IDENTIFIER_SPACE_NAME}</td><td>同名字段</td><td>应用可赋予的名称与标识</td></tr>
 *   <tr><td>{@link EngDocDependencyLink} 公共项</td><td>{@code asStoredChildName} 等</td><td>接口契约的四项公共属性</td></tr>
 * </table>
 *
 * <h3>强一致性约束（对齐 {@code EPMMemberLink.checkAttributes()}）</h3>
 * <p>若成员带 transform，则 {@code quantity} 必须为 1 且 {@code placed} 必须为 true。
 * 另注意：{@code placed = true} 不等于带 transform（应用可只放置成员而不发布变换），
 * 故 {@code hasTransform} 独立表达。
 */
public class EngDocMemberLink extends BaseEntity implements EngDocDependencyLink, TenantEntity {

    /** 数据表名 */
    public static final String TABLE = "ck_eng_doc_member_link";

    // ==================== 角色（roleA / roleB） ====================

    /** 父装配的迭代 oid（roleA「usedBy」，关联 ck_eng_document_iteration.oid） */
    private String usedByIterationOid;

    /** 子件主对象 oid（roleB「uses」，关联 ck_eng_document.oid） */
    private String usesMasterOid;

    // ==================== 数量（必有） ====================

    /** 用量（EPMMemberLink.QUANTITY，required，默认 1）；带 transform 时必须为 1 */
    private BigDecimal quantity;

    // ==================== 放置与变换 ====================

    /** 该成员是否已被应用「放置」 */
    private boolean placed;

    /** 是否真正携带变换矩阵（placed = true 也可能不带 transform，故独立标记） */
    private boolean hasTransform;

    /** 变换矩阵（序列化文本；对齐 Windchill {@code Transform}，不做数学运算） */
    private String transform;

    // ==================== 成员标志 ====================

    /** 固定件（FIXED） */
    private boolean fixed;

    /** 替代件（substitute） */
    private boolean substitute;

    /** 是否被抑制（SUPPRESSED） */
    private boolean suppressed;

    /** 是否被标注（ANNOTATED） */
    private boolean annotated;

    // ==================== 阵列 / 组归属 ====================

    /** 拥有该成员的阵列/组（pattern/group）所有者的唯一 ID */
    private String modelItemOwnerId;

    /** 阵列/组所有者的类型 */
    private String modelItemOwnerType;

    // ==================== 组件定位 ====================

    /** 组件编号（-1 = 未设置） */
    private Integer compNumber;

    /** 组件版本号（-1 = 未设置） */
    private Integer compRevNumber;

    /** 组件层索引（-1 = 未设置） */
    private Integer compLayerIndex;

    // ==================== 应用可赋予的名称 / 标识 ====================

    /** 应用赋予成员链接的名称（NAME） */
    private String name;

    /** 应用指派的整数标识（IDENTIFIER） */
    private Integer identifier;

    /** 标识所在命名空间名（IDENTIFIER_SPACE_NAME） */
    private String identifierSpaceName;

    // ==================== EngDocDependencyLink 契约 ====================

    /** 创建时「子」文档的名称（AS_STORED_CHILD_NAME） */
    private String asStoredChildName;

    /** 应用自定义依赖类型（DEP_TYPE），0 = UNSPECIFIED */
    private Integer depType = DEP_TYPE_UNSPECIFIED;

    /** 是否强依赖（REQUIRED） */
    private boolean required;

    /** 链接唯一 ID（UNIQUE_LINK_ID） */
    private Long uniqueLinkId;

    // ==================== 项目扩展 ====================

    /** 排序（项目扩展，非 Windchill 属性；用于界面展示 BOM 行序） */
    private Integer sortOrder;

    /** 租户 oid（引用 ck_tenant.oid） */
    private String tenantOid;

    // ==================== 构造方法 ====================

    public EngDocMemberLink() {
        super();
    }

    // ==================== EngDocDependencyLink 实现 ====================

    @Override
    public String getRoleAOid() {
        return usedByIterationOid;
    }

    @Override
    public String getRoleBOid() {
        return usesMasterOid;
    }

    @Override
    public String getAsStoredChildName() {
        return asStoredChildName;
    }

    @Override
    public void setAsStoredChildName(String asStoredChildName) {
        this.asStoredChildName = asStoredChildName;
    }

    @Override
    public Integer getDepType() {
        return depType;
    }

    @Override
    public void setDepType(Integer depType) {
        this.depType = depType;
    }

    @Override
    public boolean isRequired() {
        return required;
    }

    @Override
    public void setRequired(boolean required) {
        this.required = required;
    }

    @Override
    public Long getUniqueLinkId() {
        return uniqueLinkId;
    }

    @Override
    public void setUniqueLinkId(Long uniqueLinkId) {
        this.uniqueLinkId = uniqueLinkId;
    }

    // ==================== Getter / Setter ====================

    public String getUsedByIterationOid() { return usedByIterationOid; }
    public void setUsedByIterationOid(String usedByIterationOid) { this.usedByIterationOid = usedByIterationOid; }

    public String getUsesMasterOid() { return usesMasterOid; }
    public void setUsesMasterOid(String usesMasterOid) { this.usesMasterOid = usesMasterOid; }

    public BigDecimal getQuantity() { return quantity; }
    public void setQuantity(BigDecimal quantity) { this.quantity = quantity; }

    public boolean isPlaced() { return placed; }
    public void setPlaced(boolean placed) { this.placed = placed; }

    public boolean isHasTransform() { return hasTransform; }
    public void setHasTransform(boolean hasTransform) { this.hasTransform = hasTransform; }

    public String getTransform() { return transform; }
    public void setTransform(String transform) { this.transform = transform; }

    public boolean isFixed() { return fixed; }
    public void setFixed(boolean fixed) { this.fixed = fixed; }

    public boolean isSubstitute() { return substitute; }
    public void setSubstitute(boolean substitute) { this.substitute = substitute; }

    public boolean isSuppressed() { return suppressed; }
    public void setSuppressed(boolean suppressed) { this.suppressed = suppressed; }

    public boolean isAnnotated() { return annotated; }
    public void setAnnotated(boolean annotated) { this.annotated = annotated; }

    public String getModelItemOwnerId() { return modelItemOwnerId; }
    public void setModelItemOwnerId(String modelItemOwnerId) { this.modelItemOwnerId = modelItemOwnerId; }

    public String getModelItemOwnerType() { return modelItemOwnerType; }
    public void setModelItemOwnerType(String modelItemOwnerType) { this.modelItemOwnerType = modelItemOwnerType; }

    public Integer getCompNumber() { return compNumber; }
    public void setCompNumber(Integer compNumber) { this.compNumber = compNumber; }

    public Integer getCompRevNumber() { return compRevNumber; }
    public void setCompRevNumber(Integer compRevNumber) { this.compRevNumber = compRevNumber; }

    public Integer getCompLayerIndex() { return compLayerIndex; }
    public void setCompLayerIndex(Integer compLayerIndex) { this.compLayerIndex = compLayerIndex; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public Integer getIdentifier() { return identifier; }
    public void setIdentifier(Integer identifier) { this.identifier = identifier; }

    public String getIdentifierSpaceName() { return identifierSpaceName; }
    public void setIdentifierSpaceName(String identifierSpaceName) { this.identifierSpaceName = identifierSpaceName; }

    public Integer getSortOrder() { return sortOrder; }
    public void setSortOrder(Integer sortOrder) { this.sortOrder = sortOrder; }

    @Override
    public String getTenantOid() { return tenantOid; }

    @Override
    public void setTenantOid(String tenantOid) { this.tenantOid = tenantOid; }

    // ==================== 便捷方法 ====================

    /** 校验成员属性一致性（对齐 Windchill {@code EPMMemberLink.checkAttributes()}）。 */
    public boolean isConsistent() {
        if (hasTransform) {
            boolean unitQuantity = quantity == null || quantity.compareTo(BigDecimal.ONE) == 0;
            return unitQuantity && placed;
        }
        return true;
    }

    @Override
    public String toString() {
        return "EngDocMemberLink{usedByIterationOid='" + usedByIterationOid
                + "', usesMasterOid='" + usesMasterOid
                + "', quantity=" + quantity
                + ", depType=" + depType
                + ", required=" + required + "}";
    }
}
