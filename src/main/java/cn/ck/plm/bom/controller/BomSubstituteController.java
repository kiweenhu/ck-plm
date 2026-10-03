/*
 * Copyright (c) 2026~2028 深圳乘恺科技有限公司
 * All rights reserved.
 *
 * @author Kiween.Hu; Roney.Liu
 */

package cn.ck.plm.bom.controller;

import cn.ck.plm.bom.dto.BomSubstituteVO;
import cn.ck.plm.bom.entity.BomSubstituteLink;
import cn.ck.plm.bom.service.api.BomSubstituteService;
import cn.ck.plm.iam.dto.ApiResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 局部替代（BOM 行替代）REST 控制器 —— BOM 行工具栏「局部替代」对应的后端。
 *
 * <p>与 {@code /api/part-alternates}（全局替代：物料主数据级）区分开：这里的关系只在该
 * BOM 行上下文生效。
 */
@RestController
@RequestMapping("/api/bom-substitutes")
public class BomSubstituteController {

    @Autowired
    private BomSubstituteService bomSubstituteService;

    /** 某 BOM 行的替代件清单（局部替代） */
    @GetMapping("/by-bom-link/{bomLinkOid}")
    public ApiResponse<List<BomSubstituteVO>> listByBomLink(@PathVariable String bomLinkOid) {
        try {
            return ApiResponse.ok(bomSubstituteService.listByBomLink(bomLinkOid));
        } catch (Exception e) {
            return ApiResponse.fail(500, "查询替代件失败: " + e.getMessage());
        }
    }

    /**
     * 设置替代件（局部替代）。
     *
     * <p>同一 BOM 行 + 同一替代件重复提交 = 更新其参数，不是报重复。
     */
    @PostMapping
    public ApiResponse<BomSubstituteVO> add(@RequestBody BomSubstituteLink link) {
        try {
            return ApiResponse.ok(bomSubstituteService.add(link));
        } catch (IllegalArgumentException e) {
            return ApiResponse.fail(400, e.getMessage());
        } catch (IllegalStateException e) {
            return ApiResponse.fail(400, e.getMessage());
        }
    }

    /** 删除一条局部替代关系 */
    @DeleteMapping("/{oid}")
    public ApiResponse<Void> delete(@PathVariable String oid) {
        try {
            bomSubstituteService.delete(oid);
            return ApiResponse.ok();
        } catch (IllegalArgumentException e) {
            return ApiResponse.fail(400, e.getMessage());
        }
    }

    /** 取消替代：清空某 BOM 行的全部局部替代（返回删除条数） */
    @DeleteMapping("/by-bom-link/{bomLinkOid}")
    public ApiResponse<Integer> clearByBomLink(@PathVariable String bomLinkOid) {
        try {
            return ApiResponse.ok(bomSubstituteService.clearByBomLink(bomLinkOid));
        } catch (IllegalArgumentException e) {
            return ApiResponse.fail(400, e.getMessage());
        }
    }
}
