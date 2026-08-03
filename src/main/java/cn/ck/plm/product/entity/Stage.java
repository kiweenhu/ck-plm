/*
 * Copyright (c) 2025 深圳乘恺科技有限公司
 * All rights reserved.
 *
 * @author Kiween.Hu; Roney.Liu
 */

package cn.ck.plm.product.entity;

import cn.ck.plm.base.entity.TenantEntity;
import cn.ck.plm.base.entity.WithoutVersionEntity;

/**
 * 研发阶段实体（Stage），每个产品系列或产品型号自有其阶段列表。
 *
 * <p>继承 {@link WithoutVersionEntity} 获得 code / name / description 基础字段。
 * 每一行代表一个归属单元的一个研发阶段，用于在 Dashboard 中渲染阶段 Tab。
 *
 * <h3>关系</h3>
 * <pre>
 * ProductLine  1 ── N  Stage        (通过 ownerOid + ownerType = LINE)
 * ProductModel 1 ── N  Stage        (通过 ownerOid + ownerType = MODEL)
 * Stage        1 ── N  Folder       (通过 stageOid → stage.code)
 * </pre>
 *
 * <h3>默认阶段（与前端 stageDefs.js 保持一致）</h3>
 * <pre>
 * MARKET_VALIDATION → 市场验证         (sortOrder=1, icon=ShoppingCartOutlined, color=#eb2f96)
 * REQUIREMENTS      → 需求论证         (sortOrder=2, icon=AuditOutlined,       color=#1677ff)
 * SOLUTION          → 方案设计         (sortOrder=3, icon=BulbOutlined,        color=#722ed1)
 * DETAILED          → 详细设计         (sortOrder=4, icon=FundProjectionScreenOutlined, color=#13c2c2)
 * PROCESS           → 工艺规划         (sortOrder=5, icon=ToolOutlined,        color=#fa8c16)
 * TRIAL             → 试产            (sortOrder=6, icon=RocketOutlined,       color=#52c41a)
 * </pre>
 *
 * <h3>使用场景</h3>
 * <ul>
 *   <li>产品线/产品型号创建时自动初始化 6 个默认阶段</li>
 *   <li>Dashboard 页面按阶段 Tab 展示文件夹树和文档</li>
 *   <li>产品系列清单页可对各产品线/型号的阶段进行管理</li>
 * </ul>
 */
public class Stage extends WithoutVersionEntity implements TenantEntity {

    /** 阶段归属类型：产品系列 */
    public static final String OWNER_TYPE_LINE = "LINE";

    /** 阶段归属类型：产品型号 */
    public static final String OWNER_TYPE_MODEL = "MODEL";

    /** 前端图标组件名称（Ant Design Vue 图标，如 ShoppingCartOutlined） */
    private String icon;

    /** 阶段标识色（HEX，如 #eb2f96） */
    private String color;

    /** 排序序号（1-based） */
    private Integer sortOrder;

    /** 所属归属单元 oid（产品系列或产品型号的 oid） */
    private String ownerOid;

    /** 归属类型：LINE（产品系列）或 MODEL（产品型号） */
    private String ownerType;

    /** 是否在仪表盘页面显示 */
    private Boolean showOnDashboard;

    /** 阶段默认文件夹名称列表（JSON 数组字符串，如 ["市场调研分析","目标用户验证"]） */
    private String defaultFolders;

    /** 租户 oid（引用 ck_tenant.oid） */
    private String tenantOid;

    // ==================== 构造方法 ====================

    public Stage() {
        super();
    }

    // ==================== Getter / Setter ====================

    public String getIcon() { return icon; }
    public void setIcon(String icon) { this.icon = icon; }

    public String getColor() { return color; }
    public void setColor(String color) { this.color = color; }

    public Integer getSortOrder() { return sortOrder; }
    public void setSortOrder(Integer sortOrder) { this.sortOrder = sortOrder; }

    public String getOwnerOid() { return ownerOid; }
    public void setOwnerOid(String ownerOid) { this.ownerOid = ownerOid; }

    public String getOwnerType() { return ownerType; }
    public void setOwnerType(String ownerType) { this.ownerType = ownerType; }

    public Boolean getShowOnDashboard() { return showOnDashboard; }
    public void setShowOnDashboard(Boolean showOnDashboard) { this.showOnDashboard = showOnDashboard; }

    public String getDefaultFolders() { return defaultFolders; }
    public void setDefaultFolders(String defaultFolders) { this.defaultFolders = defaultFolders; }

    @Override
    public String getTenantOid() { return tenantOid; }

    @Override
    public void setTenantOid(String tenantOid) { this.tenantOid = tenantOid; }

    @Override
    public String toString() {
        return "Stage{code='" + getCode() + "', name='" + getName()
                + "', ownerOid='" + ownerOid + "', ownerType='" + ownerType
                + "', sortOrder=" + sortOrder
                + ", showOnDashboard=" + showOnDashboard + "}";
    }
}
