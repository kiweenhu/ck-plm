/*
 * Copyright (c) 2026~2028 深圳乘恺科技有限公司
 * All rights reserved.
 *
 * @author Kiween.Hu; Roney.Liu
 */

package cn.ck.plm.bom.controller;

import cn.ck.plm.bom.dto.BomSnapshotCompareVO;
import cn.ck.plm.bom.entity.BomSnapshot;
import cn.ck.plm.bom.service.api.BomSnapshotService;
import cn.ck.plm.iam.dto.ApiResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * BOM 快照 REST 控制器。
 *
 * <p>操作三类：新增快照（把当前 BOM 行定格）、快照记录（列表 / 删除）、对比（快照 ↔ 快照 / 快照 ↔ 当前 BOM）。
 *
 * <p><b>一律返回 {@link ApiResponse}</b>：前端 axios 拦截器按 {@code {code, message, data}} 判定成败 ——
 * 裸返回实体会被判成「请求失败」。
 */
@RestController
@RequestMapping("/api/bom-snapshots")
public class BomSnapshotController {

    private static final Logger log = LoggerFactory.getLogger(BomSnapshotController.class);

    @Autowired
    private BomSnapshotService bomSnapshotService;

    /** 新增快照：body = {iterationOid, name, remark} */
    @PostMapping
    public ApiResponse<BomSnapshot> create(@RequestBody Map<String, Object> body) {
        String iterationOid = str(body.get("iterationOid"));
        String name = str(body.get("name"));
        try {
            BomSnapshot snapshot = bomSnapshotService.createSnapshot(iterationOid, name, str(body.get("remark")));
            // 快照明细不回传：前端只需要 oid / 名称 / 行数
            snapshot.setSnapshotJson(null);
            return ApiResponse.ok(snapshot);
        } catch (IllegalArgumentException e) {
            return ApiResponse.fail(400, e.getMessage());
        } catch (Exception e) {
            // 这里 swallow 了异常，不打日志的话线上 / 本地都只剩前端一句"保存快照失败"，无从查起
            log.error("创建 BOM 快照失败: iterationOid={} name={}", iterationOid, name, e);
            return ApiResponse.fail(500, "创建快照失败: " + e.getMessage());
        }
    }

    /** 快照记录：按零件（跨版本）或按迭代过滤 */
    @GetMapping
    public ApiResponse<List<BomSnapshot>> list(@RequestParam(required = false) String partOid,
                                               @RequestParam(required = false) String iterationOid) {
        try {
            return ApiResponse.ok(bomSnapshotService.listSnapshots(partOid, iterationOid));
        } catch (Exception e) {
            return ApiResponse.fail(500, "读取快照记录失败: " + e.getMessage());
        }
    }

    /**
     * 快照对比。
     *
     * @param from         源快照 oid
     * @param toSnapshot   目标快照 oid（与 toIteration 二选一）
     * @param toIteration  目标迭代 oid（取该迭代当下的 BOM 行）
     */
    @GetMapping("/compare")
    public ApiResponse<BomSnapshotCompareVO> compare(@RequestParam("from") String from,
                                                     @RequestParam(required = false) String toSnapshot,
                                                     @RequestParam(required = false) String toIteration) {
        try {
            return ApiResponse.ok(bomSnapshotService.compare(from, toSnapshot, toIteration));
        } catch (IllegalArgumentException e) {
            return ApiResponse.fail(400, e.getMessage());
        } catch (Exception e) {
            return ApiResponse.fail(500, "快照对比失败: " + e.getMessage());
        }
    }

    /** 单份快照（含 BOM 明细） */
    @GetMapping("/{oid}")
    public ApiResponse<BomSnapshot> get(@PathVariable String oid) {
        BomSnapshot snapshot = bomSnapshotService.getByOid(oid);
        if (snapshot == null) {
            return ApiResponse.fail(404, "快照不存在，可能已被删除");
        }
        return ApiResponse.ok(snapshot);
    }

    @DeleteMapping("/{oid}")
    public ApiResponse<Void> delete(@PathVariable String oid) {
        try {
            bomSnapshotService.delete(oid);
            return ApiResponse.ok();
        } catch (IllegalArgumentException e) {
            return ApiResponse.fail(400, e.getMessage());
        } catch (Exception e) {
            return ApiResponse.fail(500, "删除快照失败: " + e.getMessage());
        }
    }

    private String str(Object value) {
        return value == null ? null : String.valueOf(value);
    }
}
