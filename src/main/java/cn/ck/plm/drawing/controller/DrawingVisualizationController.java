/*
 * Copyright (c) 2026~2028 深圳乘恺科技有限公司
 * All rights reserved.
 *
 * @author Kiween.Hu
 */

package cn.ck.plm.drawing.controller;

import cn.ck.plm.drawing.entity.DrawingVisualization;
import cn.ck.plm.drawing.service.api.DrawingConvertService;
import cn.ck.plm.iam.dto.ApiResponse;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * 二维图纸轻量化转换的对外接口（异步语义，与 CadVisualizationController 对称）。
 *
 * <pre>
 *   1) POST /api/drawing-visualizations/convert  → 立刻拿到记录，status = QUEUED
 *   2) 轮询 GET  /api/drawing-visualizations/latest?engDocIterationOid=xxx
 *   3) status = SUCCESS 后，用返回的 svgCkfileOid 走 /api/ckfiles/{oid}/stream 取预览
 * </pre>
 */
@RestController
@RequestMapping("/api/drawing-visualizations")
public class DrawingVisualizationController {

    private final DrawingConvertService convertService;

    public DrawingVisualizationController(DrawingConvertService convertService) {
        this.convertService = convertService;
    }

    /** 转换器是否可用 —— 前端据此决定要不要显示 2D 预览入口 */
    @GetMapping("/available")
    public ApiResponse<Map<String, Object>> available() {
        return ApiResponse.ok(Map.of("available", convertService.isAvailable()));
    }

    /** 触发转换（幂等：内容没变且已有产物则复用）。body: {engDocIterationOid, sourceCkfileOid} */
    @PostMapping("/convert")
    public ApiResponse<Object> convert(@RequestBody Map<String, Object> body) {
        String iterationOid = str(body.get("engDocIterationOid"));
        String ckfileOid = str(body.get("sourceCkfileOid"));
        if (iterationOid == null || ckfileOid == null) {
            return ApiResponse.fail(400, "缺少 engDocIterationOid 或 sourceCkfileOid");
        }
        try {
            return ApiResponse.ok(convertService.submit(iterationOid, ckfileOid));
        } catch (IllegalArgumentException e) {
            return ApiResponse.fail(400, e.getMessage());
        } catch (Exception e) {
            return ApiResponse.fail(500, "提交二维转换失败: " + e.getMessage());
        }
    }

    /** 取该迭代最近一次转换记录（前端轮询用） */
    @GetMapping("/latest")
    public ApiResponse<Object> latest(@RequestParam String engDocIterationOid) {
        return ApiResponse.ok(convertService.getLatest(engDocIterationOid));
    }

    /** 按 oid 查记录 */
    @GetMapping("/{oid}")
    public ApiResponse<Object> get(@PathVariable String oid) {
        DrawingVisualization record = convertService.getByOid(oid);
        return record == null ? ApiResponse.fail(404, "记录不存在") : ApiResponse.ok(record);
    }

    /** 重试 */
    @PostMapping("/{oid}/retry")
    public ApiResponse<Object> retry(@PathVariable String oid) {
        try {
            return ApiResponse.ok(convertService.retry(oid));
        } catch (Exception e) {
            return ApiResponse.fail(400, e.getMessage());
        }
    }

    /** 取消 */
    @PostMapping("/{oid}/cancel")
    public ApiResponse<Object> cancel(@PathVariable String oid) {
        try {
            return ApiResponse.ok(convertService.cancel(oid));
        } catch (Exception e) {
            return ApiResponse.fail(400, e.getMessage());
        }
    }

    /** 测试连接（页面「测试连接」按钮） */
    @GetMapping("/health")
    public ApiResponse<Map<String, Object>> health() {
        return ApiResponse.ok(convertService.health());
    }

    private static String str(Object v) {
        if (v == null) return null;
        String s = String.valueOf(v).trim();
        return s.isEmpty() ? null : s;
    }
}
