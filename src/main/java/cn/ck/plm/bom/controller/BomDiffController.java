/*
 * Copyright (c) 2025 深圳乘恺科技有限公司
 * All rights reserved.
 *
 * @author Kiween.Hu; Roney.Liu
 */

package cn.ck.plm.bom.controller;

import cn.ck.plm.bom.entity.BomDiff;
import cn.ck.plm.bom.service.api.BomDiffService;
import cn.ck.plm.iam.dto.ApiResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * BomDiff REST 控制器。
 *
 * <p><b>一律返回 {@link ApiResponse}</b>：前端 axios 拦截器按 {@code {code, message, data}}
 * 判定成败 —— 裸返回实体（HTTP 200 但报文里没有 code 字段）会被判成「请求失败」，
 * BOM 版本对比就是这么弹错的。新增接口请沿用这里的写法。
 */
@RestController
@RequestMapping("/api/bom-diffs")
public class BomDiffController {

    @Autowired
    private BomDiffService bomDiffService;

    @PostMapping
    public ApiResponse<BomDiff> create(@RequestBody BomDiff diff) {
        return ApiResponse.ok(bomDiffService.create(diff));
    }

    @DeleteMapping("/{oid}")
    public ApiResponse<Void> delete(@PathVariable String oid) {
        bomDiffService.deleteByOid(oid);
        return ApiResponse.ok();
    }

    @GetMapping("/{oid}")
    public ApiResponse<BomDiff> getByOid(@PathVariable String oid) {
        return ApiResponse.ok(bomDiffService.getByOid(oid));
    }

    @GetMapping("/from/{fromIterationOid}")
    public ApiResponse<List<BomDiff>> listByFrom(@PathVariable String fromIterationOid) {
        return ApiResponse.ok(bomDiffService.listByFromIteration(fromIterationOid));
    }

    @GetMapping("/to/{toIterationOid}")
    public ApiResponse<List<BomDiff>> listByTo(@PathVariable String toIterationOid) {
        return ApiResponse.ok(bomDiffService.listByToIteration(toIterationOid));
    }

    @GetMapping("/between")
    public ApiResponse<BomDiff> getBetween(@RequestParam String fromIterationOid,
                                           @RequestParam String toIterationOid) {
        return ApiResponse.ok(bomDiffService.getByFromAndTo(fromIterationOid, toIterationOid));
    }

    /** 实时对比两个迭代的顶层 BOM 行差异（不依赖预计算，直接两次拉取树比对） */
    @GetMapping("/compare")
    public ApiResponse<BomDiff> compare(@RequestParam String fromIterationOid,
                                        @RequestParam String toIterationOid) {
        return ApiResponse.ok(bomDiffService.compareNow(fromIterationOid, toIterationOid));
    }
}
