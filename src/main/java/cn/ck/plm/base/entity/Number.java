/*
 * Copyright (c) 2025 深圳乘恺科技有限公司
 * All rights reserved.
 *
 * @author Kiween.Hu; Roney.Liu
 */

package cn.ck.plm.base.entity;

import java.util.ArrayList;
import java.util.List;

/**
 * 编码规则实体 —— 定义业务对象编码的生成规则。
 *
 * <p>继承 {@link WithoutVersionEntity} 获得 code / name / description 标准字段及审计能力。
 *
 * <p>每条规则由多个有序的 {@link NumberSegment} 组成，支持以下段类型：
 * <table border="1">
 *   <tr><th>类型</th><th>说明</th><th>示例</th></tr>
 *   <tr><td>{@code CONST}</td><td>固定文本</td><td>"PART"</td></tr>
 *   <tr><td>{@code SEPARATOR}</td><td>分隔符</td><td>"-"</td></tr>
 *   <tr><td>{@code YEAR}</td><td>年份</td><td>"2026" (格式 yyyy)</td></tr>
 *   <tr><td>{@code MONTH}</td><td>月份</td><td>"06" (格式 MM)</td></tr>
 *   <tr><td>{@code DAY}</td><td>日期</td><td>"19" (格式 dd)</td></tr>
 *   <tr><td>{@code SERIAL}</td><td>流水号(自增)</td><td>"0042" (4位,起始1)</td></tr>
 * </table>
 *
 * <p>使用示例：
 * <pre>{@code
 * // 创建规则 PART-{yyyy}-{0000}
 * Number rule = new Number();
 * rule.setCode("PART-NO");
 * rule.setName("零件编号规则");
 * rule.setSegments(List.of(
 *     new NumberSegment("CONST", "PART", 1),
 *     new NumberSegment("SEPARATOR", "-", 2),
 *     new NumberSegment("YEAR", "yyyy", 3),
 *     new NumberSegment("SEPARATOR", "-", 4),
 *     new NumberSegment("SERIAL", 4, 1, 5)  // 4位流水号,起始1
 * ));
 * }</pre>
 */
public class Number extends WithoutVersionEntity implements TenantEntity {

    /** 是否启用 */
    private Boolean enabled;

    /** 租户 oid */
    private String tenantOid;

    /** 段列表（内存聚合，非持久化到 number 表） */
    private List<NumberSegment> segments;

    // ==================== 构造方法 ====================

    public Number() {
    }

    public Number(String code, String name) {
        setCode(code);
        setName(name);
    }

    // ==================== Getter / Setter ====================

    public Boolean getEnabled() {
        return enabled;
    }

    public void setEnabled(Boolean enabled) {
        this.enabled = enabled;
    }

    @Override
    public String getTenantOid() { return tenantOid; }

    @Override
    public void setTenantOid(String tenantOid) { this.tenantOid = tenantOid; }

    public List<NumberSegment> getSegments() {
        return segments;
    }

    public void setSegments(List<NumberSegment> segments) {
        this.segments = segments;
    }

    /**
     * 便捷方法：按 sortOrder 升序设置段列表。
     */
    public void addSegment(NumberSegment segment) {
        if (this.segments == null) {
            this.segments = new ArrayList<>();
        }
        this.segments.add(segment);
    }

    // ==================== equals / hashCode / toString ====================

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Number that = (Number) o;
        String code = getCode();
        return code != null && code.equals(that.getCode());
    }

    @Override
    public int hashCode() {
        String code = getCode();
        return code != null ? code.hashCode() : 0;
    }

    @Override
    public String toString() {
        return "Number{" +
                "code='" + getCode() + '\'' +
                ", name='" + getName() + '\'' +
                ", enabled=" + enabled +
                ", segments=" + (segments != null ? segments.size() : 0) +
                '}';
    }
}
