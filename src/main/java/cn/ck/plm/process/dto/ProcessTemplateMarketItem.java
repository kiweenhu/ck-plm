/*
 * Copyright (c) 2026~2028 深圳乘恺科技有限公司
 * All rights reserved.
 *
 * @author Kiween.Hu; Roney.Liu
 */

package cn.ck.plm.process.dto;

import java.util.ArrayList;
import java.util.List;

/**
 * 预置流程模板市场的一个条目（只读投影，不落库）。
 *
 * <p>数据源是<b>行业包目录</b>里的 {@code processes/*.dsl.json}，不是数据库 ——
 * 包是随 jar 发布的只读内容，租户取用后才会在 {@code ck_process_template} 里生成自己的模板行。
 *
 * <p>{@link #installed} 与 {@link #missingForms} 是两个"能不能直接用"的判断：
 * <ul>
 *   <li>{@code installed}：本租户已有同 key 的模板（市场里灰掉，避免重复取用）；</li>
 *   <li>{@code missingForms}：模板引用了、但本租户表单列表里没有的 {@code formRef}。
 *       流程能部署能跑，但那些节点的任务页会渲染不出表单 —— 取用前应当先装对应的包。</li>
 * </ul>
 */
public class ProcessTemplateMarketItem {

    /** 包坐标 {@code code@version} */
    private String packId;
    /** 包的中文名（清单里的 name） */
    private String packName;
    /** 包版本 */
    private String packVersion;
    /** 包内相对路径（{@code processes/technical-review.dsl.json}） */
    private String file;

    private String key;
    private String name;
    private String displayName;
    private String description;
    /** DSL 里的节点数（展示"这个流程有多大"，不解析图） */
    private int nodeCount;

    /** 本租户是否已有同 key 的模板 */
    private boolean installed;
    /** 引用了但本租户不存在的表单 code（空 = 依赖已就绪） */
    private List<String> missingForms = new ArrayList<>();

    public String getPackId() { return packId; }
    public void setPackId(String packId) { this.packId = packId; }

    public String getPackName() { return packName; }
    public void setPackName(String packName) { this.packName = packName; }

    public String getPackVersion() { return packVersion; }
    public void setPackVersion(String packVersion) { this.packVersion = packVersion; }

    public String getFile() { return file; }
    public void setFile(String file) { this.file = file; }

    public String getKey() { return key; }
    public void setKey(String key) { this.key = key; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getDisplayName() { return displayName; }
    public void setDisplayName(String displayName) { this.displayName = displayName; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public int getNodeCount() { return nodeCount; }
    public void setNodeCount(int nodeCount) { this.nodeCount = nodeCount; }

    public boolean isInstalled() { return installed; }
    public void setInstalled(boolean installed) { this.installed = installed; }

    public List<String> getMissingForms() { return missingForms; }
    public void setMissingForms(List<String> missingForms) {
        this.missingForms = missingForms == null ? new ArrayList<>() : missingForms;
    }
}
