/*
 * Copyright (c) 2025 深圳乘恺科技有限公司
 * All rights reserved.
 */

package cn.ck.plm.base.controller;

import cn.ck.plm.base.entity.VersionRule;
import cn.ck.plm.base.service.api.VersionRuleService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 版本规则 REST 控制器
 */
@RestController
@RequestMapping("/api/version-rules")
@Tag(name = "版本规则", description = "版本规则管理")
@CrossOrigin(origins = "*")
public class VersionRuleController {

    @Autowired
    private VersionRuleService versionRuleService;

    @GetMapping
    @Operation(summary = "获取所有版本规则")
    public ResponseEntity<Map<String, Object>> getAllRules() {
        List<VersionRule> rules = versionRuleService.getAllRules();
        Map<String, Object> result = new HashMap<>();
        result.put("code", 200);
        result.put("data", rules);
        result.put("total", rules.size());
        return ResponseEntity.ok(result);
    }

    @GetMapping("/{oid}")
    @Operation(summary = "根据 OID 获取版本规则")
    public ResponseEntity<Map<String, Object>> getRuleByOid(@PathVariable String oid) {
        VersionRule rule = versionRuleService.getRuleByOid(oid);
        Map<String, Object> result = new HashMap<>();
        if (rule != null) {
            result.put("code", 200);
            result.put("data", rule);
        } else {
            result.put("code", 404);
            result.put("message", "版本规则不存在");
        }
        return ResponseEntity.ok(result);
    }

    @GetMapping("/code/{code}")
    @Operation(summary = "根据 Code 获取版本规则")
    public ResponseEntity<Map<String, Object>> getRuleByCode(@PathVariable String code) {
        VersionRule rule = versionRuleService.getRuleByCode(code);
        Map<String, Object> result = new HashMap<>();
        if (rule != null) {
            result.put("code", 200);
            result.put("data", rule);
        } else {
            result.put("code", 404);
            result.put("message", "版本规则不存在");
        }
        return ResponseEntity.ok(result);
    }

    @PostMapping
    @Operation(summary = "创建版本规则")
    public ResponseEntity<Map<String, Object>> createRule(@RequestBody VersionRule rule) {
        try {
            VersionRule created = versionRuleService.createRule(rule);
            Map<String, Object> result = new HashMap<>();
            result.put("code", 200);
            result.put("data", created);
            result.put("message", "创建成功");
            return ResponseEntity.ok(result);
        } catch (IllegalArgumentException e) {
            Map<String, Object> result = new HashMap<>();
            result.put("code", 400);
            result.put("message", e.getMessage());
            return ResponseEntity.badRequest().body(result);
        }
    }

    @PutMapping("/{oid}")
    @Operation(summary = "更新版本规则")
    public ResponseEntity<Map<String, Object>> updateRule(@PathVariable String oid, @RequestBody VersionRule rule) {
        try {
            rule.setOid(oid);
            VersionRule updated = versionRuleService.updateRule(rule);
            Map<String, Object> result = new HashMap<>();
            result.put("code", 200);
            result.put("data", updated);
            result.put("message", "更新成功");
            return ResponseEntity.ok(result);
        } catch (IllegalArgumentException e) {
            Map<String, Object> result = new HashMap<>();
            result.put("code", 400);
            result.put("message", e.getMessage());
            return ResponseEntity.badRequest().body(result);
        }
    }

    @DeleteMapping("/{oid}")
    @Operation(summary = "删除版本规则")
    public ResponseEntity<Map<String, Object>> deleteRule(@PathVariable String oid) {
        versionRuleService.deleteRule(oid);
        Map<String, Object> result = new HashMap<>();
        result.put("code", 200);
        result.put("message", "删除成功");
        return ResponseEntity.ok(result);
    }

    @PostMapping("/generate/{code}")
    @Operation(summary = "生成下一个版本")
    public ResponseEntity<Map<String, Object>> generateVersion(@PathVariable String code) {
        try {
            String version = versionRuleService.generateNextVersion(code);
            Map<String, Object> result = new HashMap<>();
            result.put("code", 200);
            result.put("data", version);
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            Map<String, Object> result = new HashMap<>();
            result.put("code", 400);
            result.put("message", e.getMessage());
            return ResponseEntity.badRequest().body(result);
        }
    }

    @PostMapping("/reset-sequence/{code}")
    @Operation(summary = "重置序号")
    public ResponseEntity<Map<String, Object>> resetSequence(@PathVariable String code, @RequestBody Map<String, Long> body) {
        try {
            Long newValue = body.get("value");
            versionRuleService.resetSequence(code, newValue);
            Map<String, Object> result = new HashMap<>();
            result.put("code", 200);
            result.put("message", "序号已重置为: " + newValue);
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            Map<String, Object> result = new HashMap<>();
            result.put("code", 400);
            result.put("message", e.getMessage());
            return ResponseEntity.badRequest().body(result);
        }
    }
}
