/*
 * Copyright (c) 2026~2028 深圳乘恺科技有限公司
 * All rights reserved.
 *
 * @author Kiween.Hu; Roney.Liu
 */

package cn.ck.plm.process.dto;

/**
 * 节点「表单填写」项 —— 某节点办理时写入流程变量的一个键值对。
 *
 * <p>用于流程详情页点开已办节点时回答"这一步填了什么"：
 * 责任人 / 结论 / 意见 / 时间在节点头部已展示，本 VO 只承载**表单类**变量 ——
 * 例如「设置审批人」节点写入的 {@code ckplmSetupAssignees_<节点id>}（各环节审批人）。
 *
 * <p>为什么不猜语义：只有能确定映射的（{@code ckplmSetupAssignees_*} → BPMN 里的节点名）
 * 才给中文标签，其余**原样露出键名** —— 宁可不好看，也不编造含义。
 */
public class ProcessActivityFormVO {

    /** 变量名（原始键，如 ckplmSetupAssignees_n_iqc） */
    private String key;

    /** 展示名：可解析时给「审批人 · 样品来料检验（IQC）」这类中文；否则与 key 相同 */
    private String label;

    /** 值（字符串化；集合 / JSON 原样给）。为 null 表示该变量未设值 */
    private String value;

    public String getKey() {
        return key;
    }

    public void setKey(String key) {
        this.key = key;
    }

    public String getLabel() {
        return label;
    }

    public void setLabel(String label) {
        this.label = label;
    }

    public String getValue() {
        return value;
    }

    public void setValue(String value) {
        this.value = value;
    }
}
