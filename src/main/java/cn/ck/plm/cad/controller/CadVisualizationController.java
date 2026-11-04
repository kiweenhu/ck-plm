/*
 * Copyright (c) 2026~2028 深圳乘恺科技有限公司
 * All rights reserved.
 *
 * @author Kiween.Hu; Roney.Liu
 */

package cn.ck.plm.cad.controller;

import cn.ck.plm.cad.dto.CadConvertBatchItem;
import cn.ck.plm.cad.dto.CadConvertBatchResultVO;
import cn.ck.plm.cad.dto.CadConvertJobPageVO;
import cn.ck.plm.cad.dto.ContainerCadModelVO;
import cn.ck.plm.cad.entity.CadVisualization;
import cn.ck.plm.cad.service.api.CadConvertService;
import cn.ck.plm.iam.dto.ApiResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * CAD 轻量化转换的对外接口。
 *
 * <h3>前端怎么用（异步语义）</h3>
 * <pre>
 *   1) POST /api/cad-visualizations/convert   → 立刻拿到记录，status = QUEUED
 *   2) 轮询 GET  /api/cad-visualizations/latest?engDocIterationOid=xxx
 *   3) status 变为 SUCCESS 后，用返回的 glbCkfileOid 走 /api/ckfiles/{oid}/stream 取模型
 * </pre>
 * 不要在步骤 1 里同步等结果 —— 转换可能跑几十秒到几分钟，那是转换器的活儿。
 */
@RestController
@RequestMapping("/api/cad-visualizations")
public class CadVisualizationController {

    private final CadConvertService convertService;

    public CadVisualizationController(CadConvertService convertService) {
        this.convertService = convertService;
    }

    /** 转换器是否可用 —— 前端据此决定要不要显示 3D 入口 */
    @GetMapping("/available")
    public ApiResponse<Map<String, Object>> available() {
        return ApiResponse.ok(Map.of("available", convertService.isAvailable()));
    }

    /**
     * 触发转换。
     *
     * <p>幂等：源文件内容没变且已有成功产物时，直接返回上次结果，不再提交。
     *
     * @param body { engDocIterationOid, sourceCkfileOid }
     */
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
            return ApiResponse.fail(500, "提交转换失败: " + e.getMessage());
        }
    }

    /** 查某迭代最近一次转换（前端轮询入口） */
    @GetMapping("/latest")
    public ApiResponse<CadVisualization> latest(@RequestParam String engDocIterationOid) {
        CadVisualization record = convertService.getLatest(engDocIterationOid);
        return ApiResponse.ok(record);
    }

    /**
     * 容器（产品系列 / 型号）下配套的三维数模清单 —— 「产品数模」页签。
     *
     * <p>一行 = 一个工程数据（ENG_DOCUMENT）最新迭代上的一个 CAD 主文件
     * （.step/.stp/.iges/.igs/.stl）+ 它最近一次的转换状态。
     * {@code status} 为空表示还没转换过，前端显示"未转换"并给生成入口。
     */
    @GetMapping("/models")
    public ApiResponse<List<ContainerCadModelVO>> models(@RequestParam String containerOid) {
        return ApiResponse.ok(convertService.listModelsByContainer(containerOid));
    }

    /**
     * 单个零件版本的数模与转换状态 —— BOM 右侧「轻量化」页签。
     *
     * <p>{@code data} 为 {@code null} 表示该零件版本没关联工程数据、或关联的主文件不是可轻量化的数模；
     * 有值时含触发转换所需的两项（{@code iterationOid} / {@code sourceCkfileOid}），
     * 前端据此先展示现成产物（{@code glbCkfileOid}），没有才给"生成轻量化"入口。
     */
    @GetMapping("/by-part-iteration/{partIterationOid}")
    public ApiResponse<ContainerCadModelVO> byPartIteration(@PathVariable String partIterationOid) {
        return ApiResponse.ok(convertService.getModelByPartIteration(partIterationOid));
    }

    /**
     * 「下载装配数模」：把本容器下全部数模源文件打成一个 zip。
     *
     * <p>返回二进制而不是 {@code ApiResponse}，且必须带 Authorization ——
     * 前端只能用 axios 取 blob 再触发保存，{@code window.open} 带不上请求头会被 401 挡下。
     * 没有任何可下载文件时返回 404，让前端给一句人话而不是下载一个空包。
     */
    @GetMapping("/models/package")
    public ResponseEntity<byte[]> packageModels(@RequestParam String containerOid) {
        byte[] zip;
        try {
            zip = convertService.packageModels(containerOid);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
        if (zip == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        }
        String fileName = "cad-models-" + LocalDate.now() + ".zip";
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + fileName + "\"")
                .body(zip);
    }

    /**
     * 批量触发转换 —— 「产品数模」列表勾选多行后一次提交。
     *
     * <p>请求体是数组，元素为 {@code { iterationOid, ckfileOid }}；
     * 返回逐条结果（ACCEPTED / CACHED / SKIPPED / FAILED），前端据此告诉用户
     * "哪几条进去了、哪几条命中了缓存、哪几条被跳过"。
     *
     * @return 分类计数 + 逐条明细
     */
    @PostMapping("/convert-batch")
    public ApiResponse<CadConvertBatchResultVO> convertBatch(@RequestBody List<CadConvertBatchItem> items) {
        try {
            return ApiResponse.ok(convertService.submitBatch(items));
        } catch (IllegalArgumentException e) {
            return ApiResponse.fail(400, e.getMessage());
        }
    }

    /**
     * 取消排队中 / 转换中的任务 —— 批量误提交后的止损手段。
     */
    @PostMapping("/{oid}/cancel")
    public ApiResponse<Object> cancel(@PathVariable String oid) {
        try {
            return ApiResponse.ok(convertService.cancel(oid));
        } catch (IllegalArgumentException e) {
            return ApiResponse.fail(400, e.getMessage());
        } catch (IllegalStateException e) {
            return ApiResponse.fail(409, e.getMessage());
        }
    }

    /**
     * 「数模转换中心」：跨容器、跨人的全局转换队列（批量提交后的主视图）。
     *
     * <p>与 {@code /models} 的分工：那个是"某个产品下有哪些数模"，
     * 这个是"全公司的图现在排到哪了、哪些失败了"。
     *
     * @param filter active（排队+转换中）/ success / failed（失败+超时）/ skipped；空 = 全部
     * @param keyword 文件名 / 工程数据编码 / 名称 模糊匹配
     */
    @GetMapping("/jobs")
    public ApiResponse<CadConvertJobPageVO> jobs(@RequestParam(required = false) String filter,
                                                 @RequestParam(required = false) String keyword,
                                                 @RequestParam(defaultValue = "1") int page,
                                                 @RequestParam(defaultValue = "20") int size) {
        return ApiResponse.ok(convertService.listConvertJobs(filter, keyword, page, size));
    }

    /** 查单条记录 */
    @GetMapping("/{oid}")
    public ApiResponse<CadVisualization> detail(@PathVariable String oid) {
        CadVisualization record = convertService.getByOid(oid);
        if (record == null) {
            return ApiResponse.fail(404, "转换记录不存在");
        }
        return ApiResponse.ok(record);
    }

    /** 重新转换（失败后手动再试） */
    @PostMapping("/{oid}/retry")
    public ApiResponse<Object> retry(@PathVariable String oid) {
        try {
            return ApiResponse.ok(convertService.retry(oid));
        } catch (IllegalArgumentException e) {
            return ApiResponse.fail(400, e.getMessage());
        } catch (IllegalStateException e) {
            return ApiResponse.fail(409, e.getMessage());
        } catch (Exception e) {
            return ApiResponse.fail(500, "重试失败: " + e.getMessage());
        }
    }

    private String str(Object v) {
        return v == null ? null : String.valueOf(v);
    }
}
