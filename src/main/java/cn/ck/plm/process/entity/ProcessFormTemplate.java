/*
 * Copyright (c) 2025 深圳乘恺科技有限公司
 * All rights reserved.
 *
 * @author Kiween.Hu; Roney.Liu
 */

package cn.ck.plm.process.entity;

import cn.ck.plm.base.entity.BaseEntity;
import cn.ck.plm.base.entity.TenantEntity;

/**
 * 流程表单模板 —— 对应 {@code ck_process_form_template} 表。
 *
 * <p>命名与 {@code ck_process_template} / {@code ck_process_category} 同族：
 * 它属于流程域（表单是流程节点办理时用的表），表名前缀统一为 {@code ck_process_}。
 *
 * <p>一个模板 = 一张办理页要用的表单。它与节点类型是<b>多对多</b>的：
 * <ul>
 *   <li>同一种节点类型可以挂<b>多张</b>模板（例如「审批」既可用内置的「审批意见」，
 *       也可用企业自定义的「技术评审单」「商务评审单」）—— 节点通过 DSL 的
 *       {@code formRef} 指到自己要用的那一张；</li>
 *   <li>一张模板也可以适用于多种节点类型（{@link #nodeTypes} 逗号分隔）。</li>
 * </ul>
 *
 * <p><b>为什么要有这张表</b>：早先"有哪些表单"只存在于前端代码里的
 * {@code dsl-core/forms.ts} 注册表 —— 企业想加一张自己的审批表单，没有任何地方可以配，
 * 只能改代码。表落库后，内置模板由启动初始化器自动登记，自定义模板在「业务配置 → 流程表单」里维护。
 *
 * <h3>租户与内置</h3>
 * <ul>
 *   <li>{@link #builtin} 为真的模板是<b>系统内置</b>：随应用启动自动登记，不允许删除，
 *       code / nodeTypes / component 三个"契约字段"也不允许改（它们决定运行期怎么渲染）。</li>
 *   <li>内置模板归属平台租户（见 {@code ProcessFormTemplateInitializer}），
 *       表在 {@code TenantStatementInterceptor} 里是 PLATFORM_SHARED ——
 *       所有租户查询时都能看到平台内置 + 自己租户的自定义模板。</li>
 * </ul>
 */
public class ProcessFormTemplate extends BaseEntity implements TenantEntity {

    /**
     * 模板编码 —— 唯一的对外契约。
     *
     * <p>DSL 节点的 {@code formRef}、编译出的 {@code flowable:formKey}、
     * 运行期的渲染派发，用的都是它。因此内置模板的 code 一经发布就不能改：
     * 改了等于在途实例"取不到表单"。
     */
    private String code;

    /** 显示名（设计器下拉与配置列表里给人看的名字） */
    private String name;

    /**
     * 适用的节点类型，逗号分隔（取值同 DSL 的 NodeType：START / APPROVAL /
     * COUNTERSIGN_APPROVAL / TASK / SET_ASSIGNEE）。
     *
     * <p>同一种类型可以出现在多张模板上 —— 这正是"一个节点类型多种模板"的落点。
     */
    private String nodeTypes;

    /**
     * 前端渲染器 key（内置模板专用，与 code 同值；自定义模板为空）。
     *
     * <p>为空表示前端没有专门的 Vue 组件：运行期走兜底表单。
     */
    private String component;

    /** 是否系统内置：内置模板不可删除，契约字段不可改 */
    private Boolean builtin;

    /** 是否启用：停用后设计器下拉里不再出现（已存在的引用不受影响） */
    private Boolean enabled;

    /** 排序（同类型模板在下拉里的先后） */
    private Integer sortOrder;

    /** 说明：这张表单用来做什么、什么时候该选它 */
    private String description;

    /** 租户 oid：内置模板=平台租户，自定义模板=创建它的租户 */
    private String tenantOid;

    // ==================== 构造方法 ====================

    public ProcessFormTemplate() {
    }

    public ProcessFormTemplate(String code, String name, String nodeTypes, String component,
                               boolean builtin, int sortOrder, String description) {
        this.code = code;
        this.name = name;
        this.nodeTypes = nodeTypes;
        this.component = component;
        this.builtin = builtin;
        this.sortOrder = sortOrder;
        this.description = description;
        this.enabled = true;
    }

    // ==================== 语义辅助 ====================

    /** 该模板是否适用于指定节点类型（nodeTypes 为空视为不限制） */
    public boolean appliesTo(String nodeType) {
        if (nodeTypes == null || nodeTypes.trim().isEmpty()) {
            return true;
        }
        if (nodeType == null) {
            return false;
        }
        for (String item : nodeTypes.split(",")) {
            if (item.trim().equalsIgnoreCase(nodeType.trim())) {
                return true;
            }
        }
        return false;
    }

    // ==================== Getter / Setter ====================

    public String getCode() { return code; }
    public void setCode(String code) { this.code = code; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getNodeTypes() { return nodeTypes; }
    public void setNodeTypes(String nodeTypes) { this.nodeTypes = nodeTypes; }

    public String getComponent() { return component; }
    public void setComponent(String component) { this.component = component; }

    public Boolean getBuiltin() { return builtin; }
    public void setBuiltin(Boolean builtin) { this.builtin = builtin; }

    public Boolean getEnabled() { return enabled; }
    public void setEnabled(Boolean enabled) { this.enabled = enabled; }

    public Integer getSortOrder() { return sortOrder; }
    public void setSortOrder(Integer sortOrder) { this.sortOrder = sortOrder; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    @Override
    public String getTenantOid() { return tenantOid; }

    @Override
    public void setTenantOid(String tenantOid) { this.tenantOid = tenantOid; }

    @Override
    public String toString() {
        return "ProcessFormTemplate{" +
                "code='" + code + '\'' +
                ", name='" + name + '\'' +
                ", nodeTypes='" + nodeTypes + '\'' +
                ", builtin=" + builtin +
                '}';
    }
}
