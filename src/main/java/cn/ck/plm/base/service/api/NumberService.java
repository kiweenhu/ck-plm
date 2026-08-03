/*
 * Copyright (c) 2025 深圳乘恺科技有限公司
 * All rights reserved.
 *
 * @author Kiween.Hu; Roney.Liu
 */

package cn.ck.plm.base.service.api;

import cn.ck.plm.base.entity.MasterEntity;
import cn.ck.plm.base.entity.Number;
import cn.ck.plm.base.entity.NumberSegment;

import java.util.List;

/**
 * 编码规则服务接口。
 *
 * <p>核心能力：
 * <ul>
 *   <li>编码规则 CRUD（含段定义的级联管理）</li>
 *   <li>根据规则生成编码（自动拼接各段 + 递增流水号）</li>
 *   <li>预览编码格式（不递增流水号）</li>
 *   <li>根据 TypeDefinition 编码自动查找绑定规则并生成编码</li>
 * </ul>
 */
public interface NumberService {

    /**
     * 创建编码规则（含段定义）。
     * <p>SERIAL 段的 {@code currentValue} 会自动设为 {@code serialStart}。
     *
     * @param number 编码规则（须含 segments 列表）
     * @return 创建后的规则（含段 ID 回填）
     */
    Number create(Number number);

    /**
     * 更新编码规则。
     * <p>段定义采用<b>全量替换</b>策略：先删旧段，再批量插入新段。
     * SERIAL 段的流水号将被重置为起始值。
     *
     * @param number 包含更新字段及 segments 列表
     * @return 更新后的规则
     */
    Number update(Number number);

    /**
     * 删除编码规则（级联删除所有段）。
     *
     * @param code 规则编码
     * @return true 删除成功
     */
    boolean delete(String code);

    /**
     * 按编码查找规则（含段列表，按 sortOrder 升序）。
     *
     * @param code 规则编码
     * @return 规则实体（含 segments），未找到返回 null
     */
    Number findByCode(String code);

    /**
     * 查询所有编码规则（不含段详情，仅主表字段）。
     */
    List<Number> findAll();

    /**
     * 模糊搜索编码规则。
     */
    List<Number> search(String keyword);

    /**
     * 判断编码规则是否存在。
     */
    boolean exists(String code);

    /**
     * 根据编码规则生成下一个编码。
     * <p>对所有 SERIAL 段执行原子递增并拼接完整编码。
     *
     * @param ruleCode 规则编码
     * @return 生成的编码字符串（如 "PART-2026-0042"）
     */
    String generate(String ruleCode);

    /**
     * 预览编码格式（不递增流水号，SERIAL 段用起始值占位）。
     *
     * @param ruleCode 规则编码
     * @return 预览编码（如 "PART-2026-0001"）
     */
    String preview(String ruleCode);

    /**
     * 获取规则的所有段定义（按 sortOrder 升序）。
     */
    List<NumberSegment> getSegments(String ruleCode);

    /**
     * 根据实体类型编码自动查找绑定的编码规则并生成编号。
     *
     * <p>适用于所有继承 {@code MasterEntity} 的实体对象（Document / Part / CR 等）。
     * 查找链路：typeCode → TypeDefinition.oid → TypeNumberRuleLink.numberRuleCode → generate(ruleCode)。
     *
     * @param typeCode 实体类型编码（对应 TypeDefinition.code，如 DOCUMENT / PART 等）
     * @return 生成的编码字符串；若类型未绑定规则则返回 null
     */
    String generateNumberForType(String typeCode);

    /**
     * 为 MasterEntity 自动生成编号（若 number 为空）。
     *
     * <p>根据 typeCode 查找绑定的编码规则并生成编号，写入 entity.setNumber()。若已有编号则跳过。
     *
     * @param entity   主实体对象（Document / Part / 任意 MasterEntity 子类）
     * @param typeCode 类型定义编码（如 DOCUMENT / PART）
     */
    void generateNumberIfNeeded(MasterEntity entity, String typeCode);
}
