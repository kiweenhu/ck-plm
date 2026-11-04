/*
 * Copyright (c) 2026~2028 深圳乘恺科技有限公司
 * All rights reserved.
 *
 * @author Kiween.Hu; Roney.Liu
 */

package cn.ck.plm.part.controller;

import cn.ck.plm.iam.dto.ApiResponse;
import cn.ck.plm.part.entity.Manufacturer;
import cn.ck.plm.part.entity.PartAml;
import cn.ck.plm.part.entity.PartAvl;
import cn.ck.plm.part.entity.Supplier;
import cn.ck.plm.part.service.api.MpnService;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * MPN 管理 REST API 控制器（参考 Windchill 的 AML/AVL）。
 *
 * <pre>
 * 主数据   /api/mpn/manufacturers…   制造商
 *          /api/mpn/suppliers…       供应商
 * 三层结构 /api/mpn/parts/{oid}/aml  某内部件的 AML 树（AML 行内嵌其 AVL 行）
 *          /api/mpn/aml…             AML 行
 *          /api/mpn/avl…             AVL 行
 * 汇总     /api/mpn/parts/{oid}/summary  AML 数 / 去重供应商数 / 是否单一来源（R-SH-11）
 * 选件器   /api/mpn/parts            按关键字搜内部件（oid/code/name）
 * </pre>
 *
 * <p>异常分流与 PartController 同口径：IllegalArgumentException → 404（对象不存在）、
 * IllegalStateException → 400（业务规则拒绝，如"被引用的主数据不能删""同一料号重复录入"）。
 */
@RestController
@RequestMapping("/api/mpn")
public class MpnController {

    private final MpnService mpnService;

    public MpnController(MpnService mpnService) {
        this.mpnService = mpnService;
    }

    // ==================== 制造商 ====================

    @GetMapping("/manufacturers")
    public ApiResponse<List<Manufacturer>> listManufacturers(@RequestParam(required = false) String keyword) {
        return ApiResponse.ok(mpnService.listManufacturers(keyword));
    }

    @GetMapping("/manufacturers/{oid}")
    public ApiResponse<Manufacturer> getManufacturer(@PathVariable String oid) {
        Manufacturer found = mpnService.getManufacturer(oid);
        return found == null ? ApiResponse.fail(404, "制造商不存在: " + oid) : ApiResponse.ok(found);
    }

    @PostMapping("/manufacturers")
    public ApiResponse<Manufacturer> createManufacturer(@RequestBody Manufacturer manufacturer) {
        try {
            return ApiResponse.ok(mpnService.createManufacturer(manufacturer));
        } catch (IllegalStateException e) {
            return ApiResponse.fail(400, e.getMessage());
        } catch (Exception e) {
            return ApiResponse.fail(500, "新增制造商失败: " + e.getMessage());
        }
    }

    @PutMapping("/manufacturers/{oid}")
    public ApiResponse<Manufacturer> updateManufacturer(@PathVariable String oid,
                                                        @RequestBody Manufacturer patch) {
        try {
            return ApiResponse.ok(mpnService.updateManufacturer(oid, patch));
        } catch (IllegalArgumentException e) {
            return ApiResponse.fail(404, e.getMessage());
        } catch (Exception e) {
            return ApiResponse.fail(500, "更新制造商失败: " + e.getMessage());
        }
    }

    @DeleteMapping("/manufacturers/{oid}")
    public ApiResponse<Void> deleteManufacturer(@PathVariable String oid) {
        try {
            mpnService.deleteManufacturer(oid);
            return ApiResponse.ok(null);
        } catch (IllegalStateException e) {
            return ApiResponse.fail(400, e.getMessage());
        } catch (Exception e) {
            return ApiResponse.fail(500, "删除制造商失败: " + e.getMessage());
        }
    }

    // ==================== 供应商 ====================

    @GetMapping("/suppliers")
    public ApiResponse<List<Supplier>> listSuppliers(@RequestParam(required = false) String keyword) {
        return ApiResponse.ok(mpnService.listSuppliers(keyword));
    }

    @GetMapping("/suppliers/{oid}")
    public ApiResponse<Supplier> getSupplier(@PathVariable String oid) {
        Supplier found = mpnService.getSupplier(oid);
        return found == null ? ApiResponse.fail(404, "供应商不存在: " + oid) : ApiResponse.ok(found);
    }

    @PostMapping("/suppliers")
    public ApiResponse<Supplier> createSupplier(@RequestBody Supplier supplier) {
        try {
            return ApiResponse.ok(mpnService.createSupplier(supplier));
        } catch (IllegalStateException e) {
            return ApiResponse.fail(400, e.getMessage());
        } catch (Exception e) {
            return ApiResponse.fail(500, "新增供应商失败: " + e.getMessage());
        }
    }

    @PutMapping("/suppliers/{oid}")
    public ApiResponse<Supplier> updateSupplier(@PathVariable String oid, @RequestBody Supplier patch) {
        try {
            return ApiResponse.ok(mpnService.updateSupplier(oid, patch));
        } catch (IllegalArgumentException e) {
            return ApiResponse.fail(404, e.getMessage());
        } catch (Exception e) {
            return ApiResponse.fail(500, "更新供应商失败: " + e.getMessage());
        }
    }

    @DeleteMapping("/suppliers/{oid}")
    public ApiResponse<Void> deleteSupplier(@PathVariable String oid) {
        try {
            mpnService.deleteSupplier(oid);
            return ApiResponse.ok(null);
        } catch (IllegalStateException e) {
            return ApiResponse.fail(400, e.getMessage());
        } catch (Exception e) {
            return ApiResponse.fail(500, "删除供应商失败: " + e.getMessage());
        }
    }

    // ==================== 内部件选择器 + AML 树 + 汇总 ====================

    /** 按关键字搜内部件（选件器用）；keyword 为空则给前 N 个 */
    @GetMapping("/parts")
    public ApiResponse<List<Map<String, Object>>> searchParts(@RequestParam(required = false) String keyword,
                                                              @RequestParam(required = false) Integer limit) {
        return ApiResponse.ok(mpnService.searchParts(keyword, limit));
    }

    /** 某内部件的 AML 树（AML 行内嵌 avls） */
    @GetMapping("/parts/{partOid}/aml")
    public ApiResponse<List<Map<String, Object>>> listAmlTree(@PathVariable String partOid) {
        return ApiResponse.ok(mpnService.listAmlTree(partOid));
    }

    /** ★ 汇总：AML 数 / 去重供应商数 / 是否单一来源（R-SH-11 口径） */
    @GetMapping("/parts/{partOid}/summary")
    public ApiResponse<Map<String, Object>> summarize(@PathVariable String partOid) {
        return ApiResponse.ok(mpnService.summarize(partOid));
    }

    // ==================== AML ====================

    @PostMapping("/aml")
    public ApiResponse<PartAml> createAml(@RequestBody PartAml aml) {
        try {
            return ApiResponse.ok(mpnService.createAml(aml));
        } catch (IllegalStateException e) {
            return ApiResponse.fail(400, e.getMessage());
        } catch (Exception e) {
            return ApiResponse.fail(500, "新增 AML 行失败: " + e.getMessage());
        }
    }

    @PutMapping("/aml/{oid}")
    public ApiResponse<PartAml> updateAml(@PathVariable String oid, @RequestBody PartAml patch) {
        try {
            return ApiResponse.ok(mpnService.updateAml(oid, patch));
        } catch (IllegalArgumentException e) {
            return ApiResponse.fail(404, e.getMessage());
        } catch (IllegalStateException e) {
            return ApiResponse.fail(400, e.getMessage());
        } catch (Exception e) {
            return ApiResponse.fail(500, "更新 AML 行失败: " + e.getMessage());
        }
    }

    @DeleteMapping("/aml/{oid}")
    public ApiResponse<Void> deleteAml(@PathVariable String oid) {
        try {
            mpnService.deleteAml(oid);
            return ApiResponse.ok(null);
        } catch (Exception e) {
            return ApiResponse.fail(500, "删除 AML 行失败: " + e.getMessage());
        }
    }

    // ==================== AVL ====================

    @PostMapping("/avl")
    public ApiResponse<PartAvl> createAvl(@RequestBody PartAvl avl) {
        try {
            return ApiResponse.ok(mpnService.createAvl(avl));
        } catch (IllegalStateException e) {
            return ApiResponse.fail(400, e.getMessage());
        } catch (Exception e) {
            return ApiResponse.fail(500, "新增 AVL 行失败: " + e.getMessage());
        }
    }

    @PutMapping("/avl/{oid}")
    public ApiResponse<PartAvl> updateAvl(@PathVariable String oid, @RequestBody PartAvl patch) {
        try {
            return ApiResponse.ok(mpnService.updateAvl(oid, patch));
        } catch (IllegalArgumentException e) {
            return ApiResponse.fail(404, e.getMessage());
        } catch (IllegalStateException e) {
            return ApiResponse.fail(400, e.getMessage());
        } catch (Exception e) {
            return ApiResponse.fail(500, "更新 AVL 行失败: " + e.getMessage());
        }
    }

    @DeleteMapping("/avl/{oid}")
    public ApiResponse<Void> deleteAvl(@PathVariable String oid) {
        try {
            mpnService.deleteAvl(oid);
            return ApiResponse.ok(null);
        } catch (Exception e) {
            return ApiResponse.fail(500, "删除 AVL 行失败: " + e.getMessage());
        }
    }
}
