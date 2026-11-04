/*
 * Copyright (c) 2026~2028 深圳乘恺科技有限公司
 * All rights reserved.
 */

package cn.ck.plm.improve.flow;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 问题闭环状态机 —— 纯函数测试（不碰 mapper，直接调静态方法）。
 *
 * <p>钉住两条口径：① 不许一步关闭（必须经 VERIFYING 再 VERIFY_PASS）；
 * ② CANCELLED 是终态（取消后不可再流转，除 REOPEN 的适用面只有 CLOSED）。
 */
class IssueFlowTest {

    @Test
    void 完整闭环路径_提出到关闭逐跳可走() {
        String s1 = IssueFlow.resolve(IssueFlow.START, IssueFlow.OPEN);
        assertThat(s1).isEqualTo(IssueFlow.IN_PROGRESS);
        String s2 = IssueFlow.resolve(IssueFlow.SUBMIT_VERIFY, s1);
        assertThat(s2).isEqualTo(IssueFlow.VERIFYING);
        String s3 = IssueFlow.resolve(IssueFlow.VERIFY_PASS, s2);
        assertThat(s3).isEqualTo(IssueFlow.CLOSED);
    }

    @Test
    void 不许一步关闭_待处理直接验证通过被拒() {
        assertThatThrownBy(() -> IssueFlow.resolve(IssueFlow.VERIFY_PASS, IssueFlow.OPEN))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("OPEN");
        assertThatThrownBy(() -> IssueFlow.resolve(IssueFlow.VERIFY_PASS, IssueFlow.IN_PROGRESS))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void 验证退回_从待验证回到处理中_且处理中不可重复提交() {
        assertThat(IssueFlow.resolve(IssueFlow.VERIFY_FAIL, IssueFlow.VERIFYING))
                .isEqualTo(IssueFlow.IN_PROGRESS);
        assertThatThrownBy(() -> IssueFlow.resolve(IssueFlow.SUBMIT_VERIFY, IssueFlow.VERIFYING))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void 取消_三种进行中状态都可_终态不可再动() {
        for (String from : new String[]{IssueFlow.OPEN, IssueFlow.IN_PROGRESS, IssueFlow.VERIFYING}) {
            assertThat(IssueFlow.resolve(IssueFlow.CANCEL, from)).isEqualTo(IssueFlow.CANCELLED);
        }
        assertThat(IssueFlow.isTerminal(IssueFlow.CANCELLED)).isTrue();
        assertThatThrownBy(() -> IssueFlow.resolve(IssueFlow.CANCEL, IssueFlow.CANCELLED))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> IssueFlow.resolve(IssueFlow.REOPEN, IssueFlow.CANCELLED))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("CLOSED");
    }

    @Test
    void 重开_仅已关闭可重开() {
        assertThat(IssueFlow.resolve(IssueFlow.REOPEN, IssueFlow.CLOSED)).isEqualTo(IssueFlow.OPEN);
        assertThatThrownBy(() -> IssueFlow.resolve(IssueFlow.REOPEN, IssueFlow.IN_PROGRESS))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void 未知动作_消息带允许取值() {
        assertThatThrownBy(() -> IssueFlow.resolve("CLOSE", IssueFlow.OPEN))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("未知动作");
        assertThat(IssueFlow.isKnownAction("START")).isTrue();
        assertThat(IssueFlow.isKnownAction("CLOSE")).isFalse();
    }

    @Test
    void 必填约束_退回取消重开要说明_验证通过要结论() {
        assertThat(IssueFlow.requiresComment(IssueFlow.VERIFY_FAIL)).isTrue();
        assertThat(IssueFlow.requiresComment(IssueFlow.CANCEL)).isTrue();
        assertThat(IssueFlow.requiresComment(IssueFlow.REOPEN)).isTrue();
        assertThat(IssueFlow.requiresComment(IssueFlow.START)).isFalse();
        assertThat(IssueFlow.requiresVerifyResult(IssueFlow.VERIFY_PASS)).isTrue();
        assertThat(IssueFlow.requiresVerifyResult(IssueFlow.VERIFY_FAIL)).isFalse();
    }

    @Test
    void 必填约束_提交验证要根因与处理措施() {
        assertThat(IssueFlow.requiresRootCause(IssueFlow.SUBMIT_VERIFY)).isTrue();
        assertThat(IssueFlow.requiresResolution(IssueFlow.SUBMIT_VERIFY)).isTrue();
        assertThat(IssueFlow.requiresRootCause(IssueFlow.START)).isFalse();
        assertThat(IssueFlow.requiresRootCause(IssueFlow.VERIFY_PASS)).isFalse();
        assertThat(IssueFlow.requiresResolution(IssueFlow.VERIFY_PASS)).isFalse();
    }

    @Test
    void 责任人约束_仅开始处理与提交验证限责任人() {
        assertThat(IssueFlow.requiresOwner(IssueFlow.START)).isTrue();
        assertThat(IssueFlow.requiresOwner(IssueFlow.SUBMIT_VERIFY)).isTrue();
        assertThat(IssueFlow.requiresOwner(IssueFlow.VERIFY_PASS)).isFalse();
        assertThat(IssueFlow.requiresOwner(IssueFlow.VERIFY_FAIL)).isFalse();
        assertThat(IssueFlow.requiresOwner(IssueFlow.CANCEL)).isFalse();
        assertThat(IssueFlow.requiresOwner(IssueFlow.REOPEN)).isFalse();
    }

    @Test
    void 动作中文名_面向用户的消息不带动作码() {
        assertThat(IssueFlow.label(IssueFlow.SUBMIT_VERIFY)).isEqualTo("提交验证");
        assertThat(IssueFlow.label(IssueFlow.START)).isEqualTo("开始处理");
        assertThat(IssueFlow.label("CREATE")).isEqualTo("提出");
        assertThat(IssueFlow.label("UNKNOWN")).isEqualTo("UNKNOWN");
    }

    @Test
    void 初始状态与状态白名单() {
        assertThat(IssueFlow.INITIAL).isEqualTo(IssueFlow.OPEN);
        assertThat(IssueFlow.statuses()).containsExactly(
                IssueFlow.OPEN, IssueFlow.IN_PROGRESS, IssueFlow.VERIFYING,
                IssueFlow.CLOSED, IssueFlow.CANCELLED);
        assertThat(IssueFlow.isValidStatus("DONE")).isFalse();
    }
}
