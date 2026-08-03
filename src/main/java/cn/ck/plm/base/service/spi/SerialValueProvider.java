/*
 * Copyright (c) 2025 深圳乘恺科技有限公司
 * All rights reserved.
 *
 * @author Kiween.Hu; Roney.Liu
 */

package cn.ck.plm.base.service.spi;

import cn.ck.plm.base.entity.NumberSegment;
import cn.ck.plm.base.mapper.NumberSegmentMapper;
import org.springframework.stereotype.Component;

/**
 * SERIAL 类型段值提供器 —— 流水号。
 *
 * <p>生产模式 ({@code increment=true})：原子递增 current_value 并返回；
 * 预览模式 ({@code increment=false})：返回 serial_start 起始值。
 *
 * <p>需要注入 {@link NumberSegmentMapper} 来执行数据库递增操作。
 */
@Component
public class SerialValueProvider implements SegmentValueProvider {

    private final NumberSegmentMapper segmentMapper;

    public SerialValueProvider(NumberSegmentMapper segmentMapper) {
        this.segmentMapper = segmentMapper;
    }

    @Override
    public boolean supports(String segmentType) {
        return "SERIAL".equalsIgnoreCase(segmentType);
    }

    @Override
    public String generate(NumberSegment segment, boolean increment) {
        int serialValue;
        if (increment) {
            // 原子递增
            segmentMapper.incrementCurrentValue(segment.getOid());
            NumberSegment updated = segmentMapper.selectByOid(segment.getOid());
            Integer updatedVal = updated != null ? updated.getCurrentValue() : null;
            Integer currentVal = segment.getCurrentValue();
            if (updatedVal != null) {
                serialValue = updatedVal;
            } else if (currentVal != null) {
                serialValue = currentVal;
            } else {
                serialValue = segment.getSerialStart() != null ? segment.getSerialStart() : 1;
            }
        } else {
            // 预览：用起始值
            serialValue = segment.getSerialStart() != null ? segment.getSerialStart() : 1;
        }
        int length = segment.getSerialLength() != null ? segment.getSerialLength() : 4;
        return String.format("%0" + length + "d", serialValue);
    }
}
