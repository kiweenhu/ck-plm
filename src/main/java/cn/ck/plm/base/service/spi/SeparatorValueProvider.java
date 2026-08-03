/*
 * Copyright (c) 2025 深圳乘恺科技有限公司
 * All rights reserved.
 *
 * @author Kiween.Hu; Roney.Liu
 */

package cn.ck.plm.base.service.spi;

import cn.ck.plm.base.entity.NumberSegment;
import org.springframework.stereotype.Component;

/**
 * SEPARATOR 类型段值提供器 —— 返回分隔符固定值。
 */
@Component
public class SeparatorValueProvider implements SegmentValueProvider {

    @Override
    public boolean supports(String segmentType) {
        return "SEPARATOR".equalsIgnoreCase(segmentType);
    }

    @Override
    public String generate(NumberSegment segment, boolean increment) {
        return segment.getFixedValue() != null ? segment.getFixedValue() : "";
    }
}
