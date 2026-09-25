/*
 * Copyright (c) 2025 深圳乘恺科技有限公司
 * All rights reserved.
 *
 * @author Kiween.Hu; Roney.Liu
 */

package cn.ck.plm.process.reject;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * 审批/会签节点的驳回配置 —— BPMN 上的 {@code ckplm:reject}（编译层由 DSL 的 {@code node.reject} 写入）。
 *
 * <pre>
 * {"enabled":true,"target":"PREVIOUS","targetNodeId":null,"commentRequired":true}
 * </pre>
 *
 * <h3>为什么运行期要读它，而不是靠图上的连线</h3>
 * <p>三种驳回目标里有两种<b>图里表达不了</b>：{@code PREVIOUS}（"实际走过的上一个办理节点"）
 * 要看运行历史才知道；{@code INITIATOR}（退回发起人）需要一个"发起人办理"节点，
 * 而发起本身不是流程里的节点 —— 直连开始事件的回退线在 BPMN 里也是非法结构。
 * 因此驳回目标统一在<b>运行期跳转</b>实现（见 {@link RejectRouter}），
 * 编译层只把这份声明写进属性。
 *
 * <p>没配（属性缺失）的旧流程不受影响：仍按老口径（{@code approved} 变量 + 图上的条件分支）走，
 * 那些用 {@code rejectEnd} 之类回退分支手写的流程照常工作。
 */
public class RejectSpec {

    /** 退回发起人（发起人重新办理后重新提交） */
    public static final String TARGET_INITIATOR = "INITIATOR";
    /** 退回实际走过的上一个办理节点 */
    public static final String TARGET_PREVIOUS = "PREVIOUS";
    /** 退回指定节点 */
    public static final String TARGET_NODE = "NODE";

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private boolean enabled;
    private String target;
    private String targetNodeId;
    private boolean commentRequired;

    /** 解析 {@code ckplm:reject} 的值；空值/坏值返回 null（当作"没配"，走老口径） */
    public static RejectSpec parse(String json) {
        if (json == null || json.trim().isEmpty()) {
            return null;
        }
        try {
            JsonNode node = MAPPER.readTree(json);
            if (node == null || !node.isObject()) {
                return null;
            }
            RejectSpec spec = new RejectSpec();
            spec.enabled = node.path("enabled").asBoolean(false);
            spec.target = text(node, "target");
            spec.targetNodeId = text(node, "targetNodeId");
            spec.commentRequired = node.path("commentRequired").asBoolean(false);
            return spec;
        } catch (Exception e) {
            // 坏值不抛给办理人：这类脏数据只会来自手工改过的 BPMN，
            // 按"没配"处理（老口径）比让整个办理失败更合理
            return null;
        }
    }

    private static String text(JsonNode node, String field) {
        JsonNode value = node.get(field);
        if (value == null || value.isNull()) {
            return null;
        }
        String text = value.asText();
        return text == null || text.trim().isEmpty() ? null : text.trim();
    }

    public boolean isEnabled() {
        return enabled;
    }

    public String getTarget() {
        return target;
    }

    public String getTargetNodeId() {
        return targetNodeId;
    }

    public boolean isCommentRequired() {
        return commentRequired;
    }

    /** 目标类型的中文说法（日志、提示、界面都用它，避免各处各写一份） */
    public String targetLabel() {
        if (TARGET_INITIATOR.equals(target)) {
            return "发起人";
        }
        if (TARGET_PREVIOUS.equals(target)) {
            return "上一步";
        }
        if (TARGET_NODE.equals(target)) {
            return "指定节点" + (targetNodeId == null ? "" : "「" + targetNodeId + "」");
        }
        return target == null ? "（未设置）" : target;
    }
}
