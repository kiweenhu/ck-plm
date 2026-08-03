/*
 * Copyright (c) 2025 深圳乘恺科技有限公司
 * All rights reserved.
 *
 * @author Kiween.Hu; Roney.Liu
 */

package cn.ck.plm.base.entity;

/**
 * 编码规则段定义 —— 编码中每个组成部分的配置。
 *
 * <p>支持的段类型（{@link #segmentType}）：
 * <ul>
 *   <li>{@code CONST}      — 固定文本，使用 {@link #fixedValue}</li>
 *   <li>{@code SEPARATOR}  — 分隔符，使用 {@link #fixedValue}</li>
 *   <li>{@code YEAR}       — 年份，使用 {@link #dateFormat}（默认 yyyy）</li>
 *   <li>{@code MONTH}      — 月份，使用 {@link #dateFormat}（默认 MM）</li>
 *   <li>{@code DAY}        — 日，使用 {@link #dateFormat}（默认 dd）</li>
 *   <li>{@code SERIAL}     — 流水号，使用 {@link #serialLength} / {@link #serialStart} / {@link #currentValue}</li>
 * </table>
 *
 * <p>示例（规则 PART-{yyyy}-{0000}）：
 * <pre>{@code
 * segment1: type=CONST,     fixedValue="PART", sortOrder=1
 * segment2: type=SEPARATOR, fixedValue="-",    sortOrder=2
 * segment3: type=YEAR,      dateFormat="yyyy", sortOrder=3
 * segment4: type=SEPARATOR, fixedValue="-",    sortOrder=4
 * segment5: type=SERIAL,    serialLength=4, serialStart=1, sortOrder=5
 * }</pre>
 */
public class NumberSegment extends BaseEntity {

    /** 所属规则编码 */
    private String ruleCode;

    /** 段顺序（从 1 开始） */
    private Integer sortOrder;

    /** 段类型 */
    private String segmentType;

    /** 固定值（用于 CONST / SEPARATOR 类型） */
    private String fixedValue;

    /** 日期格式（用于 YEAR / MONTH / DAY 类型） */
    private String dateFormat;

    /** 流水号位数（用于 SERIAL 类型，如 4 → 0001） */
    private Integer serialLength;

    /** 流水号起始值（用于 SERIAL 类型，默认 1） */
    private Integer serialStart;

    /** 当前流水号值（用于 SERIAL 类型，原子递增） */
    private Integer currentValue;

    /** 段描述（可选） */
    private String description;

    /**
     * 扩展配置（JSON），用于自定义段类型的个性化参数。
     *
     * <p>内置类型使用专用字段，自定义扩展类型通过此字段存储任意配置。
     * 例如 SQL 段：{@code {"sql": "SELECT MAX(code) FROM item"}}
     * 脚本段：{@code {"scriptEngine": "groovy", "script": "..."}}
     */
    private String config;

    // ==================== 构造方法 ====================

    public NumberSegment() {
    }

    /** CONST / SEPARATOR 类型快捷构造 */
    public NumberSegment(String segmentType, String fixedValue, int sortOrder) {
        this.segmentType = segmentType;
        this.fixedValue = fixedValue;
        this.sortOrder = sortOrder;
    }

    /** YEAR / MONTH / DAY 类型快捷构造（带日期格式） */
    public NumberSegment(String segmentType, String dateFormat, String unusedIgnored, int sortOrder) {
        this.segmentType = segmentType;
        this.dateFormat = dateFormat;
        this.sortOrder = sortOrder;
    }

    /** SERIAL 类型快捷构造 */
    public NumberSegment(String segmentType, int serialLength, int serialStart, int sortOrder) {
        this.segmentType = segmentType;
        this.serialLength = serialLength;
        this.serialStart = serialStart;
        this.sortOrder = sortOrder;
    }

    // ==================== Getter / Setter ====================

    public String getRuleCode() {
        return ruleCode;
    }

    public void setRuleCode(String ruleCode) {
        this.ruleCode = ruleCode;
    }

    public Integer getSortOrder() {
        return sortOrder;
    }

    public void setSortOrder(Integer sortOrder) {
        this.sortOrder = sortOrder;
    }

    public String getSegmentType() {
        return segmentType;
    }

    public void setSegmentType(String segmentType) {
        this.segmentType = segmentType;
    }

    public String getFixedValue() {
        return fixedValue;
    }

    public void setFixedValue(String fixedValue) {
        this.fixedValue = fixedValue;
    }

    public String getDateFormat() {
        return dateFormat;
    }

    public void setDateFormat(String dateFormat) {
        this.dateFormat = dateFormat;
    }

    public Integer getSerialLength() {
        return serialLength;
    }

    public void setSerialLength(Integer serialLength) {
        this.serialLength = serialLength;
    }

    public Integer getSerialStart() {
        return serialStart;
    }

    public void setSerialStart(Integer serialStart) {
        this.serialStart = serialStart;
    }

    public Integer getCurrentValue() {
        return currentValue;
    }

    public void setCurrentValue(Integer currentValue) {
        this.currentValue = currentValue;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getConfig() {
        return config;
    }

    public void setConfig(String config) {
        this.config = config;
    }

    // ==================== toString ====================

    @Override
    public String toString() {
        return "NumberSegment{" +
                "oid='" + getOid() + '\'' +
                ", ruleCode='" + ruleCode + '\'' +
                ", sortOrder=" + sortOrder +
                ", segmentType='" + segmentType + '\'' +
                '}';
    }
}
