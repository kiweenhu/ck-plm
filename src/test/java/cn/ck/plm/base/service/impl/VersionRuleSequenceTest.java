package cn.ck.plm.base.service.impl;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * LETTER_26 的「大版本字母序列」解析 —— 2026-10-14 起 {@code ruleDefinition} 为
 * 逗号分隔的 26 个大写字母（见 platform-base 的 versionRules.json）。
 *
 * <p>只测纯函数 {@code parseRevisionSequence}（不碰 mapper，故直接 new 服务实例）。
 */
class VersionRuleSequenceTest {

    private final VersionRuleServiceImpl service = new VersionRuleServiceImpl();

    @Test
    void 逗号分隔的26个字母_解析为26个元素() {
        List<String> seq = service.parseRevisionSequence(
                "(A,B,C,D,E,F,G,H,I,J,K,L,M,N,O,P,Q,R,S,T,U,V,W,X,Y,Z)");
        assertThat(seq).hasSize(26);
        assertThat(seq.get(0)).isEqualTo("A");
        assertThat(seq.get(25)).isEqualTo("Z");
    }

    @Test
    void 裸逗号分隔的26个字母_也能解析_平台种子LETTER_26就是这种写法() {
        List<String> seq = service.parseRevisionSequence(
                "A,B,C,D,E,F,G,H,I,J,K,L,M,N,O,P,Q,R,S,T,U,V,W,X,Y,Z");
        assertThat(seq).hasSize(26);
        assertThat(seq.get(0)).isEqualTo("A");
        assertThat(seq.get(25)).isEqualTo("Z");
    }

    @Test
    void 全序列可作为首个与末个版本_推演A到B() {
        List<String> seq = service.parseRevisionSequence(
                "(A,B,C,D,E,F,G,H,I,J,K,L,M,N,O,P,Q,R,S,T,U,V,W,X,Y,Z)");
        // getNextRevision 用 index+1 取下一个；这里直接按同一语义验证元素顺序
        assertThat(seq.indexOf("B")).isEqualTo(1);
        assertThat(seq.get(seq.indexOf("A") + 1)).isEqualTo("B");
        assertThat(seq).doesNotContain("A-Z");
    }

    /**
     * 范围写法必须先于逗号序列匹配：旧实现先跑逗号正则，{@code (A-Z)} 被当成单元素 "A-Z"，
     * 于是规则永远推不到 Z，也就永远判不出"已是最后一个大版本"。
     */
    @Test
    void 范围写法A到Z_展开为26个元素() {
        List<String> seq = service.parseRevisionSequence("(A-Z)");
        assertThat(seq).hasSize(26);
        assertThat(seq.get(0)).isEqualTo("A");
        assertThat(seq.get(25)).isEqualTo("Z");
    }

    @Test
    void 范围写法A到D_展开为4个元素_倒序范围返回空() {
        assertThat(service.parseRevisionSequence("(A-D)")).containsExactly("A", "B", "C", "D");
        assertThat(service.parseRevisionSequence("(D-A)")).isEmpty();
    }

    @Test
    void 无字母段_返回空列表() {
        assertThat(service.parseRevisionSequence("PREFIX-ABC")).isEmpty();
        assertThat(service.parseRevisionSequence("")).isEmpty();
    }
}
