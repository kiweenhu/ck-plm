/*
 * Copyright (c) 2026~2028 深圳乘恺科技有限公司
 * All rights reserved.
 *
 * @author Kiween.Hu; Roney.Liu
 */

package cn.ck.plm.base.pack.capability;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 * 平台"<b>已实现能力</b>"注册表 —— 能力码的唯一可信来源。
 *
 * <p>行业包里的 {@code capabilities.json} 是<b>声明</b>：它说"这个包会带来功能 X"。
 * 但声明不等于实现 —— 包里写了 {@code GATE_REVIEW}，平台若还没有阶段门/评审功能，
 * 界面标着"已启用"而用户点不到任何入口，就是"假就绪"的翻版（比没装更糟：没人会再去补）。
 *
 * <p>所以口径定死：<b>只有登记在这里的能力码，安装时才允许落到租户</b>。
 * 未登记的，安装报告里如实写"本包声明但平台当前未实现，未启用"（跳过，不算失败）。
 *
 * <h3>登记标准（怎么才算"已实现"）</h3>
 * 有可用的<b>实体表 + 读写路径</b>（控制器或服务，最好还有前端入口），缺一不算。
 * 每项都附"实现依据"注释，便于随时核对 —— 拿不准就别登记：
 * 宁可在界面上显示"平台未实现"（还能补），也不要显示"已启用"而用户找不到（信任补不回来）。
 *
 * <h3>与包的关系</h3>
 * 包升级新增能力码时：平台已实现 → 该租户下次安装即启用；未实现 → 报告里提示跳过。
 * 本类只增不改语义；删码要慎重（已落库的租户能力行会变成孤儿）。
 */
public final class PlatformCapabilities {

    /** 能力码 → 实现依据（登记即"已实现"；中文名以包内声明为准，这里只留依据备查） */
    private static final Map<String, String> IMPLEMENTED = new LinkedHashMap<>();

    static {
        // 交付件：模板层 + 实例层都已交付；完成判定（P3）与流程联动（P4）仍待做，如实写在依据里。
        IMPLEMENTED.put("DELIVERABLE_MANAGEMENT",
                "ck_deliverable_template + ck_deliverable（实例）+ DeliverableInstaller "
                        + "+ /api/deliverable-templates + /api/instances + 交付件模板页与系列/型号页「交付件」面板；"
                        + "完成条件自动判定待 P3");
        IMPLEMENTED.put("COMPONENT_MANAGEMENT",
                "ck_component_footprint_link / ck_component_symbol_link + 元器件库 + 电子分类树");
        IMPLEMENTED.put("ECAD_MANAGEMENT",
                "ck_ecad_project / _schematic_ext / _pcb_ext / _footprint_ext / _symbol_ext + ecad 模块");
        IMPLEMENTED.put("STD_PART_LIBRARY",
                "标准件库（资源子库 STD_PART）+ ck_std_part_inbound_config 入库配置");
        IMPLEMENTED.put("GEN_PART_QUALIFY",
                "通用件库（资源子库 GEN_PART）+ ck_gen_part_threshold_config 阈值驱动认定");
        IMPLEMENTED.put("BOM_COST",
                "ck_bom_links / _diff / _snapshot / _substitute* + BOM 成本核算");
        // MPN 管理（参考 Windchill 的 AML/AVL）：四张表 + 读写接口 + 资源库页「MPN 管理」入口齐备。
        // 顺带补上 electronics-core 属性声明里的空洞：supplierCount（= 去重供应商数）终于有表可派生。
        // 注意：属性自动派生（supplierCount 回填 IBA）尚未做 —— 当前由 /api/mpn/.../summary 现算，
        // 不写回属性、不假装已联动（与"宁缺勿滥"同口径）。
        IMPLEMENTED.put("MPN_MANAGEMENT",
                "ck_manufacturer / ck_supplier（主数据）+ ck_part_aml（AML）+ ck_part_avl（AVL）"
                        + "+ /api/mpn/* + 资源库页「MPN 管理」页签（制造商 / 供应商 / AML·AVL）；"
                        + "supplierCount 目前按查询现算，回填 IBA 与 R-SH-11 规则求值待 P3");
        // 功能架构（FUNCTIONAL）：Windchill 的 Product/Functional 结构 —— 装备级功能系统（军工）/ 车型功能域（汽车）。
        // 类型本身已从 platform-base 移入 military-gjb / automotive-apqp（它是行业概念，不是平台基础设施），
        // 故本能力码由这两个包各自声明一份；未装包的企业账本里没有它，前端入口据此隐藏。
        IMPLEMENTED.put("FUNCTIONAL_ARCHITECTURE",
                "ck_functional + ck_functional_iteration（Master/Iteration 版本体系 + 独立编码规则 FUNCTIONAL-NUM）"
                        + "+ FunctionalController /api/functionals（增删改查 + by-folder / by-container + SoftTypeInstance 统一路由）"
                        + "+ 系列/型号页「新建功能架构」按钮与 DynamicForm 创建弹窗；"
                        + "架构分解视图（功能树 / 接口矩阵）与类型驱动的专用表单待 P3");

        // 阶段门与评审：模板层 + 实例层 + 接口 + 页面入口（系列/型号页阶段内的「阶段门 / 评审」面板）齐备。
        // 注意：判定"门过没过"（按交付件完成率）尚未做，门实例目前是"可看可留痕的状态字段"，不是自动判定。
        IMPLEMENTED.put("GATE_REVIEW",
                "ck_gate_template / ck_review_template + ck_gate / ck_review（实例）+ GateInstaller / ReviewInstaller "
                        + "+ /api/gate-templates /api/review-templates /api/instances + 方法论页与系列/型号页面板；"
                        + "自动判定与评审流程联动待 P3/P4");
        IMPLEMENTED.put("RULE_ENGINE",
                "ck_rule_template + ck_rule（实例）+ RuleInstaller + /api/rule-templates /api/instances "
                        + "+ 方法论页与系列/型号页「检查规则」面板；规则求值（触发时机 / 严重度）待 P3");
        IMPLEMENTED.put("METRIC_MANAGEMENT",
                "ck_metric_template + ck_metric（实例）+ MetricInstaller + /api/metric-templates /api/instances "
                        + "+ 方法论页与系列/型号页「指标」面板；数值评估待数据就绪（未就绪显示「暂未评估」，不编造）");
        IMPLEMENTED.put("DASHBOARD",
                "ck_dashboard_template + ck_dashboard（实例）+ DashboardInstaller "
                        + "+ /api/dashboard-templates /api/instances + 方法论页与系列/型号页「驾驶舱」面板；"
                        + "真正的渲染件与生成节奏待后续");
        // 问题闭环管理：「持续改进」页 —— 问题单五态流转（提交验证 → 验证通过才算闭环）、
        // 全程留痕（ck_issue_activity 时间线）、各状态计数与闭环率。
        // 问题类型复用 ck_issue_type_template（随包装入、企业可裁剪）—— 类型给出默认严重度与
        // "是否必须根因/复测"的约束，服务层已按类型校验存在性。
        // 注：门的「ISSUE_OPEN」自动检查 2026-10-15 已接（/api/issues/gate-check：按产品统计未闭环问题 +
        // 逐门比对 maxCount；门面板「未闭环问题」列 + 发起前提示）；检查规则的 ISSUE_OPEN 待接。
        IMPLEMENTED.put("ISSUE_MANAGEMENT",
                "ck_issue + ck_issue_activity（处理时间线）+ /api/issues（列表/统计/详情/新建/编辑/"
                        + "/transition 五态流转//comments 备注//gate-check 门的 ISSUE_OPEN 自动检查/删除）"
                        + "+ 持续改进页（跟踪与闭环）；规则的 ISSUE_OPEN 待接");
        // CAD 轻量化可视化：异步提交 → 回调 → 看门狗补偿，三条通道齐备（见 CadConvertService）。
        // 登记依据只算"本工程内的编排链路"，不含转换器本身 —— 转换器是独立部署的
        // CK-CADForge（LGPL-3.0），未部署时接口返回 SKIPPED 而非假成功。
        IMPLEMENTED.put("CAD_VIEWER",
                "ck_cad_visualization + cn.ck.plm.cad（异步提交 / 令牌校验回调 / 看门狗超时补偿）"
                        + "+ /api/cad-visualizations（convert / latest / retry / available）+ /api/internal/cad/convert/callback "
                        + "+ CadViewer.vue + CadStructureTree.vue；需独立部署转换器 CK-CADForge，"
                        + "未部署时 enabled=false，转换请求返回 SKIPPED（不是假成功）");
    }

    // ==================== 已知未实现（包里声明了也不启用）====================
    //   CERTIFICATION_MANAGEMENT 认证管理 —— 无表、无控制器；
    //   SOFTWARE_VERSION         嵌入式软件版本 —— SEMVER 规则可承载，但尚未落地；
    //   CHANGE_MANAGEMENT        变更管理(ECR/ECN) —— 后端 M1 已交付（ck_change_* 五表 + 状态机
    //                            + /api/plm/change-* 读写路径），但**尚不登记**：前端 ChangeControl.vue
    //                            仍是占位页，且审批流程引擎接入在 M2。此时登记 = 界面标"已启用"却点不到
    //                            可用入口，正是本类要避免的"假就绪"。M2 前端工作台落地后再登记。
    // 注：ContinuousImprovement.vue 原先也是占位页，2026-10-15 已做成真功能（见 ISSUE_MANAGEMENT）。

    private PlatformCapabilities() {
    }

    /** 该能力码平台是否已实现（未登记 = 未实现） */
    public static boolean isImplemented(String code) {
        return code != null && IMPLEMENTED.containsKey(code);
    }

    /** 实现依据（未登记返回 null） */
    public static String evidenceOf(String code) {
        return code == null ? null : IMPLEMENTED.get(code);
    }

    /** 已实现能力码（按登记顺序，只读） */
    public static Set<String> codes() {
        return Collections.unmodifiableSet(IMPLEMENTED.keySet());
    }
}
