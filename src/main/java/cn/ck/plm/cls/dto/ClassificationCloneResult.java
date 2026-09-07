package cn.ck.plm.cls.dto;

/**
 * 分类克隆结果统计。
 *
 * <p>从平台租户克隆分类到当前租户时，返回本次克隆的分类节点数、
 * 新增 IBA 定义数（按 code 去重后实际新增）以及建立的分类-IBA 关联数。
 */
public class ClassificationCloneResult {

    /** 克隆的分类节点数 */
    private int classificationCount;

    /** 克隆的 IBA 定义数（去重后新增） */
    private int ibaDefinitionCount;

    /** 克隆的分类-IBA 关联数 */
    private int ibaLinkCount;

    public int getClassificationCount() { return classificationCount; }
    public void setClassificationCount(int classificationCount) { this.classificationCount = classificationCount; }
    public void incrementClassification() { this.classificationCount++; }

    public int getIbaDefinitionCount() { return ibaDefinitionCount; }
    public void setIbaDefinitionCount(int ibaDefinitionCount) { this.ibaDefinitionCount = ibaDefinitionCount; }
    public void incrementIbaDefinition() { this.ibaDefinitionCount++; }

    public int getIbaLinkCount() { return ibaLinkCount; }
    public void setIbaLinkCount(int ibaLinkCount) { this.ibaLinkCount = ibaLinkCount; }
    public void incrementIbaLink() { this.ibaLinkCount++; }
}
