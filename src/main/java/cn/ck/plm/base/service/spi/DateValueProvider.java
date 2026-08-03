/*
 * Copyright (c) 2025 深圳乘恺科技有限公司
 * All rights reserved.
 *
 * @author Kiween.Hu; Roney.Liu
 */

package cn.ck.plm.base.service.spi;

import cn.ck.plm.base.entity.NumberSegment;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

/**
 * 日期类段值提供器 —— 支持 YEAR、MONTH、DAY 三种类型。
 *
 * <p>通过 {@link NumberSegment#getDateFormat()} 自定义格式，
 * 默认格式分别为 yyyy / MM / dd。
 */
@Component
public class DateValueProvider implements SegmentValueProvider {

    @Override
    public boolean supports(String segmentType) {
        return "YEAR".equalsIgnoreCase(segmentType)
                || "MONTH".equalsIgnoreCase(segmentType)
                || "DAY".equalsIgnoreCase(segmentType);
    }

    @Override
    public String generate(NumberSegment segment, boolean increment) {
        String type = segment.getSegmentType();
        String defaultFormat;
        if ("YEAR".equalsIgnoreCase(type)) {
            defaultFormat = "yyyy";
        } else if ("MONTH".equalsIgnoreCase(type)) {
            defaultFormat = "MM";
        } else {
            defaultFormat = "dd";
        }
        String format = (segment.getDateFormat() != null && !segment.getDateFormat().trim().isEmpty())
                ? segment.getDateFormat().trim()
                : defaultFormat;
        return LocalDate.now().format(DateTimeFormatter.ofPattern(format));
    }
}
