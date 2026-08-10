/*
 * Copyright (c) 2025 深圳乘恺科技有限公司
 * All rights reserved.
 *
 * @author Kiween.Hu; Roney.Liu
 */

package cn.ck.plm.bom.controller;

import cn.ck.plm.bom.entity.BomDiff;
import cn.ck.plm.bom.service.api.BomDiffService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * BomDiff REST 控制器。
 */
@RestController
@RequestMapping("/api/bom-diffs")
public class BomDiffController {

    @Autowired
    private BomDiffService bomDiffService;

    @PostMapping
    public BomDiff create(@RequestBody BomDiff diff) {
        return bomDiffService.create(diff);
    }

    @DeleteMapping("/{oid}")
    public void delete(@PathVariable String oid) {
        bomDiffService.deleteByOid(oid);
    }

    @GetMapping("/{oid}")
    public BomDiff getByOid(@PathVariable String oid) {
        return bomDiffService.getByOid(oid);
    }

    @GetMapping("/from/{fromIterationOid}")
    public List<BomDiff> listByFrom(@PathVariable String fromIterationOid) {
        return bomDiffService.listByFromIteration(fromIterationOid);
    }

    @GetMapping("/to/{toIterationOid}")
    public List<BomDiff> listByTo(@PathVariable String toIterationOid) {
        return bomDiffService.listByToIteration(toIterationOid);
    }

    @GetMapping("/between")
    public BomDiff getBetween(@RequestParam String fromIterationOid, @RequestParam String toIterationOid) {
        return bomDiffService.getByFromAndTo(fromIterationOid, toIterationOid);
    }
}
