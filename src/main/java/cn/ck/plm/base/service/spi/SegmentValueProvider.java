/*
 * Copyright (c) 2025 深圳乘恺科技有限公司
 * All rights reserved.
 *
 * @author Kiween.Hu; Roney.Liu
 */

package cn.ck.plm.base.service.spi;

import cn.ck.plm.base.entity.NumberSegment;

/**
 * 编码段值提供器 SPI —— 将段类型映射到实际生成值。
 *
 * <p>每种段类型对应一个实现类，通过 {@link #supports(String)} 声明匹配的类型。
 * Spring 容器中所有实现会被自动注入到 DefaultNumberService, as a 策略映射表。
 *
 * <h3>扩展新段类型</h3>
 * <ol>
 *   <li>实现本接口（{@code @Component} 注册为 Bean）</li>
 *   <li>{@code supports()} 返回新的类型标识（如 "SQL_QUERY"）</li>
 *   <li>在 {@code generate()} 中解析段配置生成值</li>
 * </ol>
 * 无需修改 NumberSegment 字段、表结构、buildCode() 逻辑。
 */
public interface SegmentValueProvider {

    /**
     * 判断当前 Provider 是否支持该段类型。
     *
     * @param segmentType 段类型标识（如 "CONST"、"SERIAL"、"SQL_QUERY"）
     * @return true 表示由本 Provider 处理
     */
    boolean supports(String segmentType);

    /**
     * 生成该段的值字符串。
     *
     * @param segment   段定义（含类型专用字段和 config 扩展配置）
     * @param increment true: 生产模式（递增流水号等），false: 预览模式
     * @return 生成的值字符串（如 "PART"、"2026"、"0042"），不应为 null
     */
    String generate(NumberSegment segment, boolean increment);
}
