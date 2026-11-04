/*
 * Copyright (c) 2026~2028 深圳乘恺科技有限公司
 * All rights reserved.
 *
 * @author Kiween.Hu; Roney.Liu
 */

package cn.ck.plm.base.pack.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 行业包清单（{@code pack.json}）的内存模型。
 *
 * <h3>它是什么</h3>
 * 一个行业包 = 一堆<b>数据模板文件</b>（软类型 / 属性 / 分类 / 编码规则 / 阶段 / 交付物 / 角色 …）
 * 加上一份清单。清单回答三个问题：<b>我是谁</b>（code/version/industry/devMethod）、
 * <b>我依赖谁</b>（basePack/engineRange）、<b>我装了什么</b>（resources 逐项）。
 *
 * <h3>为什么用 JSON 而不是 Java 常量</h3>
 * 行业差异应当是<b>数据差异</b>：换行业=换包，不改代码（零代码判据）。
 * 同时解决了"行业配置禁止写进 Java Initializer"这条架构纪律 —— 包外置后，
 * Initializer 里只留平台级种子。
 *
 * <h3>字段分为三段</h3>
 * <ol>
 *   <li><b>稳定段</b>（本类强类型建模）：code/version/basePack/engineRange/industry/devMethod/
 *       targetScale/license/primaryScopeType/author/installCheck/capabilities/resources；</li>
 *   <li><b>引导段</b>（{@code questionnaire}/{@code parameters}/{@code previewHints}/{@code nextSteps}/
 *       {@code sample}）：驱动"引导式初始化"，本版先以原始结构承载，待引导页落地时再强类型化
 *       —— 避免过早设计；</li>
 *   <li><b>未知段</b>：{@code @JsonIgnoreProperties(ignoreUnknown = true)} 保证
 *       新版本包被旧引擎读取时不会因多了字段而报错（包与平台版本解耦的关键）。</li>
 * </ol>
 */
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
public class PackManifest {

    /** 包标识，如 {@code electronics-core} / {@code smart-hardware-ipd} */
    private String code;

    /** 包名（界面展示），如「智能硬件 · IPD 研发体系包」 */
    private String name;

    /** 包版本（语义化），如 {@code 1.0.0} */
    private String version;

    /** 依赖的父包：{@code electronics-core@1.0.0}；为空表示不依赖（一基两变里的"基"） */
    private String basePack;

    /** 适用平台版本区间，如 {@code >=0.2.0 <1.0.0}（只挡版本，挡不了模块缺失 → 见 capabilities） */
    private String engineRange;

    /** 行业：{@code SMART_HARDWARE} / {@code ELECTRONICS_HITECH} …（平台基础包可空） */
    private String industry;

    /** 研发方法论：{@code IPD} / {@code IPD_LITE} …（与行业一起决定"用户选了什么"） */
    private String devMethod;

    /** 适用企业规模（引导页展示用），如 {@code 100-1000人} */
    private String targetScale;

    /** 管控级别：{@code LITE / STANDARD / STRICT}（体现"行业方法论"的严格程度） */
    private String controlLevel;

    /**
     * 授权：{@code OPEN_SOURCE}（开源包，随仓库分发）/ {@code BUSINESS}（商业交付包）。
     *
     * <p>它与仓库根的 {@code .gitignore} 是配套的：商业包的目录<b>不进版本库</b>，
     * 故新克隆的仓库里只有 {@code platform-base}。商业包通过外部包目录投放
     * （{@code plm.industry-pack.dir} 配置），不改 jar 即可安装。
     *
     * <p>历史值 {@code RESTRICTED}（军用包曾用）已并入 {@code BUSINESS} ——
     * 一个概念一个词，免得界面与规则各说各话。
     */
    private String license;

    /**
     * 启用标记：<b>只有启用的包才会出现在租户引导的可选行业里</b>。
     *
     * <p>缺省（未声明 / null）按<b>启用</b>处理 —— 老包没有这个字段时行为不变；
     * 关掉一个包是显式动作（行业内容尚未定稿、暂不对租户开放等）。
     *
     * <p>它管的是"<b>能不能被选</b>"，不是"能不能装"：平台管理员仍可手动安装来做验证，
     * 已引导租户的既有选择也不受影响。变体包还要看整条 {@code basePack} 链 ——
     * 基包被关掉时变体也不能被选，否则装出来是个"缺一半"的解决方案（最难排查的一类问题）。
     */
    private Boolean enabled;

    /** 启用标记（缺省启用） */
    public boolean isEnabled() {
        return enabled == null || enabled;
    }

    /** 健康度报告的主粒度：{@code PRODUCT_MODEL}（智能硬件按型号；将来汽车按项目） */
    private String primaryScopeType;

    /** 作者信息（组织 + 最后更新日期） */
    private Author author;

    /** 资源清单：逐项声明"这个包里有哪些数据模板文件、各多少条" */
    private List<Resource> resources = new ArrayList<>();

    /** 安装期检查项，如 {@code CODE_CONFLICT} / {@code DEP_SATISFIED}（缺失项只提示，不阻断） */
    private List<String> installCheck = new ArrayList<>();

    /** 引导段：企业画像题目（原始结构，待引导页落地时强类型化） */
    private List<Map<String, Object>> questionnaire = new ArrayList<>();

    /**
     * 引导段：<b>细分行业</b> —— 该行业包覆盖的子领域，如智能穿戴 / 汽车电子 / 雷达与电子对抗。
     *
     * <p>引导页"细分领域"下拉的选项来源：细分到什么粒度由<b>行业包自己定</b>，
     * 前端不硬编码任何行业名单（换行业 = 换包，细分项随之变化）。
     * 结构 {@code [{code, name}]}；选中值随企业画像一起存进 {@code ck_tenant.profile_json}，
     * 后续（P2 起）用于推荐参数默认值与规则开关。
     */
    private List<Map<String, Object>> segments = new ArrayList<>();

    /**
     * 引导段：<b>主要产品形态</b>（引导页下拉的选项来源，允许<b>多选</b>），
     * 如整机 / 板卡·模组 / 元器件·工艺 / 配套软件。
     *
     * <p>与 {@link #segments} 同源同形：由行业包声明、前端不硬编码。之所以多选：
     * 一家企业常同时做多种形态（既做整机又做板卡），而形态影响推荐参数与规则开关的取值。
     * 结构 {@code [{code, name}]}；选中值随企业画像存进 {@code ck_tenant.profile_json}。
     */
    private List<Map<String, Object>> productForms = new ArrayList<>();

    /** 引导段：可调参数（含按企业规模的建议值） */
    private List<Map<String, Object>> parameters = new ArrayList<>();

    /** 引导段：预演亮点（"你会得到什么"，给引导页第 3 步用） */
    private List<Map<String, Object>> previewHints = new ArrayList<>();

    /** 引导段：装完后的待办引导 */
    private List<Map<String, Object>> nextSteps = new ArrayList<>();

    /** 引导段：示例数据声明（默认安装，可精确回滚） */
    private Map<String, Object> sample;

    /** 加载期附加信息（不来自 JSON）：资源基准路径与清单指纹 */
    private transient String basePath;
    private transient String contentHash;

    // ==================== 便捷方法 ====================

    /** {@code code@version} —— 包的唯一标识，日志与依赖里统一用这个写法 */
    public String getId() {
        return code + "@" + version;
    }

    /** 父包坐标（{@code basePack} 原样返回；无依赖则 null） */
    public String getBasePackId() {
        return (basePack == null || basePack.trim().isEmpty()) ? null : basePack.trim();
    }

    /** 该包是否声明了行业/方法论（引导页据此决定是否出现在选项里） */
    public boolean isSelectableInOnboarding() {
        return industry != null && !industry.trim().isEmpty()
                && devMethod != null && !devMethod.trim().isEmpty();
    }

    /** 资源类型 → 条数（引导页与日志展示用） */
    public Map<String, Integer> resourceCounts() {
        Map<String, Integer> counts = new LinkedHashMap<>();
        for (Resource r : resources) {
            counts.merge(r.getType() == null ? "UNKNOWN" : r.getType(), Math.max(1, r.getCount()), Integer::sum);
        }
        return counts;
    }

    // ==================== 嵌套类型 ====================

    /** 作者信息 */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Author {
        private String org;
        private String updatedAt;

        public String getOrg() { return org; }
        public void setOrg(String org) { this.org = org; }
        public String getUpdatedAt() { return updatedAt; }
        public void setUpdatedAt(String updatedAt) { this.updatedAt = updatedAt; }
    }

    /**
     * 资源声明 —— 一条 = 一个数据模板文件。
     *
     * <p>{@code type} 是引擎的分派键（SOFTTYPE / ATTRIBUTE / CLASSIFICATION / NUMBER_RULE /
     * VERSION_RULE / LIFECYCLE / STAGE / RESOURCE_LIBRARY / ROLE / ISSUE_TYPE / DELIVERABLE /
     * GATE / REVIEW / RULE / METRIC / DASHBOARD / PROCESS / SAMPLE）。
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Resource {
        /** 资源类型（引擎按它选 Installer） */
        private String type;
        /** 模板文件名（相对包目录），如 {@code softtypes.json} 或 {@code processes/xxx.bpmn20.xml} */
        private String file;
        /** 条数（清单里声明的期望条数，用于对账与展示） */
        private Integer count;
        /** 可覆盖的默认租户归属：{@code PLATFORM}（平台级，全租户共享）或 {@code TENANT}（本租户一份） */
        private String scope;
        /**
         * 面向用户的<b>中文名</b>（引导页清单、包详情页展示用），如"生命周期状态字典"、"研发阶段模板"。
         *
         * <p>{@link #type} 是引擎的分派键（{@code LIFECYCLE} / {@code SOFT_TYPE} …），
         * 那是给代码看的；租户管理员在"行业解决方案"清单上该看到的是中文 ——
         * 名字写在包里（不在前端硬编码），换包即换词。
         */
        private String display;
        /**
         * 能否在租户级安装时<b>直接复用平台副本</b>（不写库）。
         *
         * <p>声明 true 的前提：该资源落的表在 {@code TenantStatementInterceptor} 的共享名单里
         * （查询注入 {@code tenant_oid IN (平台, 本租户)}，平台那份本租户天然可见）——
         * 如类型定义、生命周期、编码/版本规则、视图、单位。
         *
         * <p>反之如分类树（{@code ck_classification} / {@code ck_cls_iba}）、分类参数（{@code ck_iba}）、
         * 企业资源库（{@code ck_resource_container} / {@code ck_stage}）、阶段模板（{@code ck_stage_template}）：
         * 查询被改写成 {@code tenant_oid = '本租户'}，平台那份<b>本租户读不到</b> → 必须装自己一份。
         *
         * <p><b>缺省 / null 一律按 false 处理</b>：不确定就装一份 —— 宁可多写一份租户数据，
         * 也不能让界面写着"已就绪"而租户实际读不到。
         */
        private Boolean reusable;

        public String getDisplay() { return display; }
        public void setDisplay(String display) { this.display = display; }
        public Boolean getReusable() { return reusable; }
        public void setReusable(Boolean reusable) { this.reusable = reusable; }

        public String getType() { return type; }
        public void setType(String type) { this.type = type; }
        public String getFile() { return file; }
        public void setFile(String file) { this.file = file; }
        public Integer getCount() { return count; }
        public void setCount(Integer count) { this.count = count; }
        public String getScope() { return scope; }
        public void setScope(String scope) { this.scope = scope; }
    }

    // ==================== Getter / Setter ====================

    public String getCode() { return code; }
    public void setCode(String code) { this.code = code; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getVersion() { return version; }
    public void setVersion(String version) { this.version = version; }

    public String getBasePack() { return basePack; }
    public void setBasePack(String basePack) { this.basePack = basePack; }

    public String getEngineRange() { return engineRange; }
    public void setEngineRange(String engineRange) { this.engineRange = engineRange; }

    public String getIndustry() { return industry; }
    public void setIndustry(String industry) { this.industry = industry; }

    public String getDevMethod() { return devMethod; }
    public void setDevMethod(String devMethod) { this.devMethod = devMethod; }

    public String getTargetScale() { return targetScale; }
    public void setTargetScale(String targetScale) { this.targetScale = targetScale; }

    public String getControlLevel() { return controlLevel; }
    public void setControlLevel(String controlLevel) { this.controlLevel = controlLevel; }

    public String getLicense() { return license; }
    public void setLicense(String license) { this.license = license; }

    public String getPrimaryScopeType() { return primaryScopeType; }
    public void setPrimaryScopeType(String primaryScopeType) { this.primaryScopeType = primaryScopeType; }

    public Author getAuthor() { return author; }
    public void setAuthor(Author author) { this.author = author; }

    public List<Resource> getResources() { return resources; }
    public void setResources(List<Resource> resources) {
        this.resources = resources == null ? new ArrayList<>() : resources;
    }

    public List<String> getInstallCheck() { return installCheck; }
    public void setInstallCheck(List<String> installCheck) {
        this.installCheck = installCheck == null ? new ArrayList<>() : installCheck;
    }

    public List<Map<String, Object>> getQuestionnaire() { return questionnaire; }
    public void setQuestionnaire(List<Map<String, Object>> questionnaire) {
        this.questionnaire = questionnaire == null ? new ArrayList<>() : questionnaire;
    }

    /** 细分行业（该行业包覆盖的子领域；引导页下拉的选项来源） */
    public List<Map<String, Object>> getSegments() { return segments; }
    public void setSegments(List<Map<String, Object>> segments) {
        this.segments = segments == null ? new ArrayList<>() : segments;
    }

    /** 主要产品形态（引导页多选下拉的选项来源） */
    public List<Map<String, Object>> getProductForms() { return productForms; }
    public void setProductForms(List<Map<String, Object>> productForms) {
        this.productForms = productForms == null ? new ArrayList<>() : productForms;
    }

    public List<Map<String, Object>> getParameters() { return parameters; }
    public void setParameters(List<Map<String, Object>> parameters) {
        this.parameters = parameters == null ? new ArrayList<>() : parameters;
    }

    public List<Map<String, Object>> getPreviewHints() { return previewHints; }
    public void setPreviewHints(List<Map<String, Object>> previewHints) {
        this.previewHints = previewHints == null ? new ArrayList<>() : previewHints;
    }

    public List<Map<String, Object>> getNextSteps() { return nextSteps; }
    public void setNextSteps(List<Map<String, Object>> nextSteps) {
        this.nextSteps = nextSteps == null ? new ArrayList<>() : nextSteps;
    }

    public Map<String, Object> getSample() { return sample; }
    public void setSample(Map<String, Object> sample) { this.sample = sample; }

    public String getBasePath() { return basePath; }
    public void setBasePath(String basePath) { this.basePath = basePath; }

    public String getContentHash() { return contentHash; }
    public void setContentHash(String contentHash) { this.contentHash = contentHash; }

    @Override
    public String toString() {
        return "PackManifest{" + getId() + ", industry=" + industry + ", devMethod=" + devMethod
                + ", resources=" + resources.size() + "}";
    }
}
