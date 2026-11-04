/*
 * Copyright (c) 2026~2028 深圳乘恺科技有限公司
 * All rights reserved.
 *
 * @author Kiween.Hu; Roney.Liu
 */

package cn.ck.plm.process.support;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 会签通过规则的汇总口径（{@link CountersignRule}）。
 *
 * <p>钉住的是「一票否决是真否决」：{@code approved} 是实例级变量，会签里每人完成一次就写一次，
 * 若结论取最后一票，1 人驳回 + 2 人同意只要最后办的是同意就会走"通过"。所以汇总必须
 * <b>看所有票</b>，并与编译层写进 {@code completionCondition} 的口径一致。
 */
class CountersignRuleTest {

    private static CountersignRule rule(String json) {
        return CountersignRule.parse(json);
    }

    @Test
    void 没配规则时全部同意才通过() {
        CountersignRule rule = rule(null);
        assertThat(rule.getMode()).isEqualTo(CountersignRule.Mode.ALL);
        assertThat(rule.passed(2, 0, 2)).isTrue();
        assertThat(rule.passed(2, 1, 3)).isFalse();
        // 一票都没投（0 同意）不算通过
        assertThat(rule.passed(0, 0, 2)).isFalse();
    }

    @Test
    void 一票否决_只要有一票驳回就不通过() {
        CountersignRule rule = rule("{\"mode\":\"VETO\",\"veto\":true}");
        assertThat(rule.isVeto()).isTrue();
        assertThat(rule.passed(2, 0, 2)).isTrue();
        assertThat(rule.passed(2, 1, 3)).isFalse();
    }

    @Test
    void 按比例_分母是本轮应到人数() {
        CountersignRule rule = rule("{\"mode\":\"PERCENT\",\"percent\":60}");
        // 5 人应到，3 票同意 = 60% → 通过
        assertThat(rule.passed(3, 2, 5)).isTrue();
        // 同样 3 票同意，但应到 6 人 = 50% < 60% → 不通过
        assertThat(rule.passed(3, 3, 6)).isFalse();
    }

    @Test
    void 按比例100_等同于全部同意() {
        CountersignRule rule = rule("{\"mode\":\"PERCENT\",\"percent\":100}");
        assertThat(rule.passed(3, 0, 3)).isTrue();
        assertThat(rule.passed(2, 1, 3)).isFalse();
    }

    @Test
    void 按票数_够票即通过() {
        CountersignRule rule = rule("{\"mode\":\"COUNT\",\"count\":2}");
        assertThat(rule.passed(2, 1, 4)).isTrue();
        assertThat(rule.passed(1, 1, 4)).isFalse();
    }

    @Test
    void 比例口径上再带一票否决_驳回压过比例() {
        // DSL 的 passRule.veto 允许加在 PERCENT/COUNT 上：4/5 = 80% 本该通过，但有一票驳回
        CountersignRule rule = rule("{\"mode\":\"PERCENT\",\"percent\":80,\"veto\":true}");
        assertThat(rule.passed(4, 1, 5)).isFalse();
        assertThat(rule.passed(5, 0, 5)).isTrue();
    }

    @Test
    void 坏值按没配规则处理_不让脏数据把流程变成一律通过() {
        assertThat(rule("not-json").getMode()).isEqualTo(CountersignRule.Mode.ALL);
        assertThat(rule("not-json").passed(1, 1, 2)).isFalse();
        assertThat(rule("{\"mode\":\"UNKNOWN\"}").getMode()).isEqualTo(CountersignRule.Mode.ALL);
        // 比例为 0 / 缺失时收敛到合法区间，不出现"0% 也算通过"的荒谬口径
        assertThat(rule("{\"mode\":\"PERCENT\",\"percent\":0}").passed(0, 1, 1)).isFalse();
        assertThat(rule("{\"mode\":\"COUNT\",\"count\":0}").passed(0, 0, 1)).isFalse();
    }

    @Test
    void 应到人数未知时_比例口径不判通过() {
        assertThat(rule("{\"mode\":\"PERCENT\",\"percent\":50}").passed(1, 0, 0)).isFalse();
    }

    @Test
    void 提前收口_一票否决与全部同意都一拍即死() {
        CountersignRule veto = rule("{\"mode\":\"VETO\"}");
        assertThat(veto.doomed(2, 1, 3)).isTrue();
        assertThat(veto.doomed(2, 0, 3)).isFalse();

        CountersignRule all = rule(null);
        assertThat(all.doomed(2, 1, 3)).isTrue();
        assertThat(all.doomed(2, 0, 2)).isFalse();
    }

    @Test
    void 提前收口_比例看剩下的票够不够() {
        CountersignRule rule = rule("{\"mode\":\"PERCENT\",\"percent\":80}");
        // 5 人应到：已投 1 同意 1 驳回，剩 3 票全同意 = 4/5 → 还有戏，不能掐
        assertThat(rule.doomed(1, 1, 5)).isFalse();
        // 已投 1 同意 2 驳回，剩 2 票全同意 = 3/5 < 80% → 不可能通过
        assertThat(rule.doomed(1, 2, 5)).isTrue();
        // 应到人数未知时算不出"还差几票"，不猜
        assertThat(rule.doomed(0, 1, 0)).isFalse();
    }

    @Test
    void 提前收口_票数够不够() {
        CountersignRule rule = rule("{\"mode\":\"COUNT\",\"count\":3}");
        // 5 人：1 同意 1 驳回，剩 3 票 → 最多 4 票，够 3
        assertThat(rule.doomed(1, 1, 5)).isFalse();
        // 1 同意 2 驳回，剩 2 票 → 最多 3 票，刚好够
        assertThat(rule.doomed(1, 2, 5)).isFalse();
        // 0 同意 3 驳回，剩 2 票 → 最多 2 票 < 3
        assertThat(rule.doomed(0, 3, 5)).isTrue();
        // 应到人数未知：算不出"还差几票"，不猜（与比例口径同一条纪律）
        assertThat(rule.doomed(0, 1, 0)).isFalse();
    }

    @Test
    void 提前收口_比例上带一票否决时驳回即收口() {
        CountersignRule rule = rule("{\"mode\":\"PERCENT\",\"percent\":80,\"veto\":true}");
        // 4/5 本来还能通过（80%），但有一票驳回 → 否决优先，立即收口
        assertThat(rule.doomed(4, 1, 5)).isTrue();
        assertThat(rule.doomed(4, 0, 5)).isFalse();
    }
}
