package cn.ck.plm.base.entity;

/**
 * 研发阶段模板实体
 */
public class StageTemplate extends WithoutVersionEntity implements TenantEntity {

    private String icon;
    private String color;
    private Integer sortOrder;
    private String defaultFolders;
    private String tenantOid;

    public StageTemplate() {}

    public StageTemplate(String code, String name) {
        setCode(code);
        setName(name);
    }

    public String getIcon() { return icon; }
    public void setIcon(String icon) { this.icon = icon; }

    public String getColor() { return color; }
    public void setColor(String color) { this.color = color; }

    public Integer getSortOrder() { return sortOrder; }
    public void setSortOrder(Integer sortOrder) { this.sortOrder = sortOrder; }

    public String getDefaultFolders() { return defaultFolders; }
    public void setDefaultFolders(String defaultFolders) { this.defaultFolders = defaultFolders; }

    @Override
    public String getTenantOid() { return tenantOid; }

    @Override
    public void setTenantOid(String tenantOid) { this.tenantOid = tenantOid; }
}
