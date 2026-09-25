/*
 * Copyright (c) 2025 深圳乘恺科技有限公司
 * All rights reserved.
 *
 * @author Kiween.Hu; Roney.Liu
 */

package cn.ck.plm.process.delegate;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 自动化服务参数里的 {@code ${流程变量}} 解析（见 {@link PlmServiceDelegate#resolveVariables}）。
 *
 * <p>钉住的是<b>面板承诺与运行期行为一致</b>：设计器「参数集」的说明写着"取值可写
 * {@code ${变量名}}"，那么运行期就必须真把变量值填进去 —— 而不是把 {@code ${orderNo}}
 * 原样发给被调用的服务（对方系统收到这种"看起来有值"的垃圾数据，比直接报错难查得多）。
 */
class ServiceParamVariablesTest {

    private static final Map<String, Object> VARS = Map.of(
            "orderNo", "PO-2026-001",
            "count", 3,
            "approved", true);

    private static String resolve(String value, String field) {
        return PlmServiceDelegate.resolveVariables(value, field, "定制代码服务", VARS::get);
    }

    @Test
    void 整个值是变量() {
        assertThat(resolve("${orderNo}", "orderNo")).isEqualTo("PO-2026-001");
    }

    @Test
    void 变量可嵌在文本中间() {
        assertThat(resolve("P-${orderNo}-X", "code")).isEqualTo("P-PO-2026-001-X");
    }

    @Test
    void 一个值里可以有多个变量与重复引用() {
        assertThat(resolve("${orderNo}/${orderNo}/${count}", "id"))
                .isEqualTo("PO-2026-001/PO-2026-001/3");
    }

    @Test
    void 非字符串变量按字符串拼接() {
        assertThat(resolve("${approved}", "flag")).isEqualTo("true");
    }

    @Test
    void 字面量与别的花括号内容一律不动() {
        assertThat(resolve("IN_WORK", "state")).isEqualTo("IN_WORK");
        // 整段 JSON 报文模板：花括号不是变量名，必须原样保留
        assertThat(resolve("{\"a\":1}", "body")).isEqualTo("{\"a\":1}");
        assertThat(resolve(null, "any")).isNull();
    }

    @Test
    void 变量取不到时报错并指明节点参数与变量名() {
        assertThatThrownBy(() -> resolve("${notExist}", "orderNo"))
                .isInstanceOf(PlmServiceException.class)
                .hasMessageContaining("定制代码服务")
                .hasMessageContaining("orderNo")
                .hasMessageContaining("${notExist}");
    }

    @Test
    void 好变量加坏变量时整体失败而不是发出半个值() {
        assertThatThrownBy(() -> resolve("${orderNo}-${missing}", "mix"))
                .isInstanceOf(PlmServiceException.class)
                .hasMessageContaining("${missing}");
    }
}
