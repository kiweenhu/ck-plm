/*
 * Copyright (c) 2025 深圳乘恺科技有限公司
 * All rights reserved.
 *
 * @author Kiween.Hu; Roney.Liu
 */

package cn.ck.plm.base.service.impl;

import cn.ck.plm.base.entity.MasterEntity;
import cn.ck.plm.base.entity.Number;
import cn.ck.plm.base.entity.NumberSegment;
import cn.ck.plm.base.mapper.NumberMapper;
import cn.ck.plm.base.mapper.NumberSegmentMapper;
import cn.ck.plm.base.service.api.NumberService;
import cn.ck.plm.base.service.spi.SegmentValueProvider;
import cn.ck.plm.base.util.TenantContext;
import cn.ck.plm.base.entity.Number;
import cn.ck.plm.softtype.entity.TypeDefinition;
import cn.ck.plm.softtype.entity.TypeNumberRuleLink;
import cn.ck.plm.softtype.mapper.TypeDefinitionMapper;
import cn.ck.plm.softtype.service.api.TypeNumberRuleLinkService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * {@link NumberService} 的默认实现。
 *
 * <p>核心设计：
 * <ul>
 *   <li>CRUD 操作：主表 + 段表 事务级联</li>
 *   <li>{@link #generate}：通过 {@link SegmentValueProvider} 策略集合调度，
 *       遍历段列表，按 segmentType 匹配对应的 Provider 生成值</li>
 *   <li>{@link #preview}：与 generate 逻辑一致，但 SERIAL 段使用起始值代替当前值</li>
 *   <li>扩展新型段：只需实现 {@link SegmentValueProvider} + {@code @Component}，无需修改本类</li>
 * </ul>
 */
@Service
public class DefaultNumberService implements NumberService {

    private static final Logger log = LoggerFactory.getLogger(DefaultNumberService.class);

    private final NumberMapper numberMapper;
    private final NumberSegmentMapper segmentMapper;
    private final TypeDefinitionMapper typeDefinitionMapper;
    private final TypeNumberRuleLinkService ruleLinkService;

    /** 段类型 → 值提供器映射（保持注册顺序） */
    private final Map<String, SegmentValueProvider> providerMap = new LinkedHashMap<>();

    /**
     * 构造注入：收集所有 {@link SegmentValueProvider} 实现，建立类型→策略映射。
     */
    public DefaultNumberService(NumberMapper numberMapper,
                                NumberSegmentMapper segmentMapper,
                                TypeDefinitionMapper typeDefinitionMapper,
                                TypeNumberRuleLinkService ruleLinkService,
                                List<SegmentValueProvider> providers) {
        this.numberMapper = numberMapper;
        this.segmentMapper = segmentMapper;
        this.typeDefinitionMapper = typeDefinitionMapper;
        this.ruleLinkService = ruleLinkService;
        for (SegmentValueProvider provider : providers) {
            providerMap.put(provider.getClass().getSimpleName(), provider);
        }
        log.info("编码段策略已加载 {} 个 Provider", providerMap.size());
    }

    // ==================== CRUD ====================

    @Override
    @Transactional
    public Number create(Number number) {
        validateNumber(number);
        String code = number.getCode().trim();
        if (numberMapper.existsByCode(code) > 0) {
            throw new IllegalArgumentException("编码规则 '" + code + "' 已存在");
        }

        // 默认值
        if (number.getEnabled() == null) {
            number.setEnabled(true);
        }

        numberMapper.insert(number);
        log.info("编码规则创建成功: {}", code);

        // 级联保存段列表
        saveSegments(number);
        return number;
    }

    @Override
    @Transactional
    public Number update(Number number) {
        validateNumber(number);
        String code = number.getCode().trim();
        Number existing = numberMapper.selectByCode(code);
        if (existing == null) {
            throw new IllegalArgumentException("编码规则 '" + code + "' 不存在");
        }
        TenantContext.requireEditPermission(existing.getTenantOid(), "编码规则");

        numberMapper.update(number);
        log.info("编码规则更新成功: {}", code);

        // 全量替换段：删除旧段 → 插入新段
        segmentMapper.deleteByRuleCode(code);
        saveSegments(number);
        return number;
    }

    @Override
    @Transactional
    public boolean delete(String code) {
        if (code == null || code.trim().isEmpty()) {
            return false;
        }
        String normalized = code.trim();
        Number existing = numberMapper.selectByCode(normalized);
        if (existing == null) {
            return false;
        }
        TenantContext.requireEditPermission(existing.getTenantOid(), "编码规则");
        // 由数据库外键 ON DELETE CASCADE 自动清理段
        numberMapper.deleteByCode(normalized);
        log.info("编码规则删除成功: {}", normalized);
        return true;
    }

    @Override
    public Number findByCode(String code) {
        if (code == null || code.trim().isEmpty()) {
            return null;
        }
        Number number = numberMapper.selectByCode(code.trim());
        if (number != null) {
            number.setSegments(segmentMapper.selectByRuleCode(number.getCode()));
        }
        return number;
    }

    @Override
    public List<Number> findAll() {
        return numberMapper.selectAll();
    }

    @Override
    public List<Number> search(String keyword) {
        if (keyword == null || keyword.trim().isEmpty()) {
            return findAll();
        }
        return numberMapper.search(keyword.trim());
    }

    @Override
    public boolean exists(String code) {
        return code != null && numberMapper.existsByCode(code.trim()) > 0;
    }

    @Override
    public List<NumberSegment> getSegments(String ruleCode) {
        if (ruleCode == null || ruleCode.trim().isEmpty()) {
            return Collections.emptyList();
        }
        return segmentMapper.selectByRuleCode(ruleCode.trim());
    }

    // ==================== 编码生成 ====================

    @Override
    @Transactional
    public String generate(String ruleCode) {
        Number rule = loadRule(ruleCode);
        List<NumberSegment> segments = rule.getSegments();
        if (segments == null || segments.isEmpty()) {
            throw new IllegalStateException("编码规则 '" + ruleCode + "' 未定义任何段");
        }
        return buildCode(segments, true);
    }

    @Override
    public String preview(String ruleCode) {
        Number rule = loadRule(ruleCode);
        List<NumberSegment> segments = rule.getSegments();
        if (segments == null || segments.isEmpty()) {
            throw new IllegalStateException("编码规则 '" + ruleCode + "' 未定义任何段");
        }
        return buildCode(segments, false);
    }

    // ==================== 类型绑定编码生成 ====================

    @Override
    public String generateNumberForType(String typeCode) {
        if (typeCode == null || typeCode.trim().isEmpty()) {
            return null;
        }
        TypeDefinition typeDef = typeDefinitionMapper.selectByCode(typeCode.trim(), TenantContext.get(), TenantContext.PLATFORM_TENANT_OID);
        if (typeDef == null) {
            log.debug("未找到类型定义: {}", typeCode);
            return null;
        }
        TypeNumberRuleLink link = ruleLinkService.getByTypeOid(typeDef.getOid());
        if (link == null) {
            log.debug("类型 {} 未绑定编码规则", typeCode);
            return null;
        }
        String number = generate(link.getNumberRuleCode());
        log.info("自动生成编号: typeCode={}, ruleCode={}, number={}",
                typeCode, link.getNumberRuleCode(), number);
        return number;
    }

    @Override
    public void generateNumberIfNeeded(MasterEntity entity, String typeCode) {
        if (entity == null) return;
        String number = entity.getNumber();
        if (number != null && !number.trim().isEmpty()) return;

        if (typeCode == null || typeCode.trim().isEmpty()) return;

        try {
            String generated = generateNumberForType(typeCode);
            if (generated != null) {
                entity.setNumber(generated);
            }
        } catch (Exception e) {
            log.warn("编号生成失败，跳过: typeCode={}, error={}", typeCode, e.getMessage());
        }
    }

    // ==================== 内部方法 ====================

    /**
     * 保存段列表：设置 ruleCode、初始化 SERIAL 段 currentValue，批量插入。
     */
    private void saveSegments(Number number) {
        List<NumberSegment> segments = number.getSegments();
        if (segments == null || segments.isEmpty()) {
            return;
        }
        for (NumberSegment seg : segments) {
            seg.setRuleCode(number.getCode());
            if ("SERIAL".equals(seg.getSegmentType())) {
                if (seg.getSerialStart() == null) seg.setSerialStart(1);
                if (seg.getSerialLength() == null) seg.setSerialLength(4);
                if (seg.getCurrentValue() == null) seg.setCurrentValue(seg.getSerialStart());
            }
        }
        segmentMapper.batchInsert(segments);
    }

    /**
     * 加载规则及段，校验启用状态。
     */
    private Number loadRule(String ruleCode) {
        if (ruleCode == null || ruleCode.trim().isEmpty()) {
            throw new IllegalArgumentException("规则编码不能为空");
        }
        String code = ruleCode.trim();
        Number rule = numberMapper.selectByCode(code);
        if (rule == null) {
            throw new IllegalArgumentException("编码规则 '" + code + "' 不存在");
        }
        if (rule.getEnabled() == null || !rule.getEnabled()) {
            throw new IllegalStateException("编码规则 '" + code + "' 已禁用");
        }
        rule.setSegments(segmentMapper.selectByRuleCode(code));
        return rule;
    }

    /**
     * 遍历段列表拼接编码（策略模式调度）。
     *
     * <p>对每个段，遍历已注册的 {@link SegmentValueProvider}，找到第一个
     * {@code supports(segmentType)} 返回 true 的 Provider 并调用其 generate 方法。
     *
     * @param segments   段列表（须按 sortOrder 升序）
     * @param doIncrement true 则对 SERIAL 段执行原子递增，false 预览模式
     * @return 拼接后的编码字符串
     */
    private String buildCode(List<NumberSegment> segments, boolean doIncrement) {
        StringBuilder sb = new StringBuilder();

        for (NumberSegment seg : segments) {
            String type = seg.getSegmentType();
            SegmentValueProvider matched = findProvider(type);
            if (matched != null) {
                sb.append(matched.generate(seg, doIncrement));
            } else {
                log.warn("未找到段类型 '{}' 对应的 Provider (段oid={})", type, seg.getOid());
            }
        }
        return sb.toString();
    }

    /**
     * 从注册的 Provider 集合中查找匹配该段类型的第一个实现。
     */
    private SegmentValueProvider findProvider(String segmentType) {
        for (SegmentValueProvider provider : providerMap.values()) {
            if (provider.supports(segmentType)) {
                return provider;
            }
        }
        return null;
    }

    private void validateNumber(Number number) {
        if (number == null || number.getCode() == null || number.getCode().trim().isEmpty()) {
            throw new IllegalArgumentException("编码规则编码不能为空");
        }
        if (number.getName() == null || number.getName().trim().isEmpty()) {
            throw new IllegalArgumentException("编码规则名称不能为空");
        }
    }
}
