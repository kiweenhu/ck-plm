/*
 * Copyright (c) 2025 深圳乘恺科技有限公司
 * All rights reserved.
 *
 * @author Kiween.Hu; Roney.Liu
 */

package cn.ck.plm.base.controller;

import cn.ck.plm.base.entity.Number;
import cn.ck.plm.base.entity.NumberSegment;
import cn.ck.plm.base.mapper.NumberSegmentMapper;
import cn.ck.plm.base.service.api.NumberService;
import cn.ck.plm.iam.dto.ApiResponse;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.*;

import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * 编码规则管理控制器 —— 定义业务对象编码（物料编码、文档编码等）的生成规则。
 *
 * <p>每个编码规则由多个有序的 {@link NumberSegment} 组成，
 * 支持 CONST / SEPARATOR / YEAR / MONTH / DAY / SERIAL 六种段类型。
 */
@RestController
@RequestMapping("/api/number-rules")
public class NumberRuleController {

    private static final Logger log = LoggerFactory.getLogger(NumberRuleController.class);

    private final NumberService numberService;
    private final NumberSegmentMapper segmentMapper;

    public NumberRuleController(NumberService numberService, NumberSegmentMapper segmentMapper) {
        this.numberService = numberService;
        this.segmentMapper = segmentMapper;
    }

    // ==================== 查询 ====================

    /**
     * 获取所有编码规则（不含段详情，仅主表字段）。
     */
    @GetMapping
    public ApiResponse<List<Number>> list(@RequestParam(required = false) String keyword) {
        log.debug("查询编码规则列表, keyword={}", keyword);
        List<Number> list;
        if (keyword != null && !keyword.trim().isEmpty()) {
            list = numberService.search(keyword.trim());
        } else {
            list = numberService.findAll();
        }
        return ApiResponse.ok(list != null ? list : Collections.emptyList());
    }

    /**
     * 获取编码规则详情（含所有段定义，按 sortOrder 升序）。
     */
    @GetMapping("/{code}")
    public ApiResponse<Number> detail(@PathVariable String code) {
        log.debug("查询编码规则详情, code={}", code);
        Number rule = numberService.findByCode(code);
        if (rule == null) {
            return ApiResponse.fail(404, "编码规则 '" + code + "' 不存在");
        }
        // 确保段列表已加载
        if (rule.getSegments() == null || rule.getSegments().isEmpty()) {
            rule.setSegments(numberService.getSegments(code));
        }
        return ApiResponse.ok(rule);
    }

    // ==================== 增删改 ====================

    /**
     * 创建编码规则（含段定义）。
     */
    @PostMapping
    public ApiResponse<Number> create(@RequestBody Number number) {
        log.info("创建编码规则: code={}, name={}", number.getCode(), number.getName());
        try {
            Number created = numberService.create(number);
            return ApiResponse.ok(created);
        } catch (IllegalArgumentException e) {
            log.warn("创建编码规则失败: {}", e.getMessage());
            return ApiResponse.fail(400, e.getMessage());
        }
    }

    /**
     * 更新编码规则（段定义采用全量替换策略）。
     */
    @PutMapping("/{code}")
    public ApiResponse<Number> update(@PathVariable String code, @RequestBody Number number) {
        log.info("更新编码规则: code={}", code);
        number.setCode(code);
        try {
            Number updated = numberService.update(number);
            return ApiResponse.ok(updated);
        } catch (IllegalArgumentException e) {
            log.warn("更新编码规则失败: {}", e.getMessage());
            return ApiResponse.fail(400, e.getMessage());
        }
    }

    /**
     * 删除编码规则（级联删除所有段定义）。
     */
    @DeleteMapping("/{code}")
    public ApiResponse<Void> delete(@PathVariable String code) {
        log.info("删除编码规则: code={}", code);
        try {
            boolean deleted = numberService.delete(code);
            if (deleted) {
                return ApiResponse.ok();
            }
            return ApiResponse.fail(404, "编码规则 '" + code + "' 不存在");
        } catch (Exception e) {
            log.warn("删除编码规则失败: {}", e.getMessage());
            return ApiResponse.fail(500, e.getMessage());
        }
    }

    // ==================== 编码操作 ====================

    /**
     * 生成下一个编码（SERIAL 段原子递增）。
     */
    @PostMapping("/{code}/generate")
    public ApiResponse<String> generate(@PathVariable String code) {
        log.info("生成编码: ruleCode={}", code);
        try {
            String generated = numberService.generate(code);
            return ApiResponse.ok(generated);
        } catch (IllegalArgumentException e) {
            return ApiResponse.fail(400, e.getMessage());
        } catch (IllegalStateException e) {
            return ApiResponse.fail(400, e.getMessage());
        }
    }

    /**
     * 预览编码格式（SERIAL 段使用起始值占位，不递增）。
     */
    @PostMapping("/{code}/preview")
    public ApiResponse<String> preview(@PathVariable String code) {
        log.debug("预览编码: ruleCode={}", code);
        try {
            String previewed = numberService.preview(code);
            return ApiResponse.ok(previewed);
        } catch (IllegalArgumentException e) {
            return ApiResponse.fail(400, e.getMessage());
        } catch (IllegalStateException e) {
            return ApiResponse.fail(400, e.getMessage());
        }
    }

    /**
     * 重置 SERIAL 段流水号。
     *
     * <p>请求体示例：{@code {"segmentOid": "xxx", "value": 1}}
     */
    @PostMapping("/{code}/reset-sequence")
    public ApiResponse<Void> resetSequence(@PathVariable String code, @RequestBody Map<String, Object> payload) {
        log.info("重置流水号: ruleCode={}, payload={}", code, payload);
        String segmentOid = (String) payload.get("segmentOid");
        if (segmentOid == null) {
            return ApiResponse.fail(400, "segmentOid 不能为空");
        }
        Integer value = payload.get("value") != null
                ? Integer.valueOf(payload.get("value").toString()) : null;
        if (value == null) {
            return ApiResponse.fail(400, "value 不能为空");
        }

        NumberSegment seg = segmentMapper.selectByOid(segmentOid);
        if (seg == null || !code.equals(seg.getRuleCode())) {
            return ApiResponse.fail(404, "未找到指定的 SERIAL 段 (oid=" + segmentOid + ")");
        }
        if (!"SERIAL".equals(seg.getSegmentType())) {
            return ApiResponse.fail(400, "指定的段不是 SERIAL 类型");
        }

        segmentMapper.resetCurrentValue(segmentOid, value);
        log.info("流水号已重置: segmentOid={}, newStart={}", segmentOid, value);
        return ApiResponse.ok();
    }
}
