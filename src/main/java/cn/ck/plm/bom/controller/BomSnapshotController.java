/*
 * Copyright (c) 2025 深圳乘恺科技有限公司
 * All rights reserved.
 *
 * @author Kiween.Hu; Roney.Liu
 */

package cn.ck.plm.bom.controller;

import cn.ck.plm.bom.entity.BomSnapshot;
import cn.ck.plm.bom.service.api.BomSnapshotService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

/**
 * BomSnapshot REST 控制器。
 */
@RestController
@RequestMapping("/api/bom-snapshots")
public class BomSnapshotController {

    @Autowired
    private BomSnapshotService bomSnapshotService;

    @PutMapping
    public BomSnapshot save(@RequestBody BomSnapshot snapshot) {
        return bomSnapshotService.save(snapshot);
    }

    @DeleteMapping("/{iterationOid}")
    public void delete(@PathVariable String iterationOid) {
        bomSnapshotService.deleteByIterationOid(iterationOid);
    }

    @GetMapping("/{iterationOid}")
    public BomSnapshot getByIterationOid(@PathVariable String iterationOid) {
        return bomSnapshotService.getByIterationOid(iterationOid);
    }
}
