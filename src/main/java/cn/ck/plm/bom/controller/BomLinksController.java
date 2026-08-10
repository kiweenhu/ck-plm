/*
 * Copyright (c) 2025 深圳乘恺科技有限公司
 * All rights reserved.
 *
 * @author Kiween.Hu; Roney.Liu
 */

package cn.ck.plm.bom.controller;

import cn.ck.plm.bom.entity.BomLinks;
import cn.ck.plm.bom.service.api.BomLinksService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * BomLinks REST 控制器。
 */
@RestController
@RequestMapping("/api/bom-links")
public class BomLinksController {

    @Autowired
    private BomLinksService bomLinksService;

    @PostMapping
    public BomLinks create(@RequestBody BomLinks bomLinks) {
        return bomLinksService.create(bomLinks);
    }

    @PutMapping("/{oid}")
    public BomLinks update(@PathVariable String oid, @RequestBody BomLinks bomLinks) {
        bomLinks.setOid(oid);
        return bomLinksService.update(bomLinks);
    }

    @DeleteMapping("/{oid}")
    public void delete(@PathVariable String oid) {
        bomLinksService.deleteByOid(oid);
    }

    @GetMapping("/{oid}")
    public BomLinks getByOid(@PathVariable String oid) {
        return bomLinksService.getByOid(oid);
    }

    @GetMapping("/by-parent-iteration/{parentIterationOid}")
    public List<BomLinks> listByParentIteration(@PathVariable String parentIterationOid) {
        return bomLinksService.listByParentIterationOid(parentIterationOid);
    }

    @GetMapping("/by-child-part/{childPartOid}")
    public List<BomLinks> listByChildPart(@PathVariable String childPartOid) {
        return bomLinksService.listByChildPartOid(childPartOid);
    }

    @GetMapping("/by-child-iteration/{childIterationOid}")
    public List<BomLinks> listByChildIteration(@PathVariable String childIterationOid) {
        return bomLinksService.listByChildIterationOid(childIterationOid);
    }

    @PutMapping("/{oid}/resolve-iteration")
    public void refreshResolvedIteration(@PathVariable String oid, @RequestParam String resolvedIterationOid) {
        bomLinksService.refreshResolvedIteration(oid, resolvedIterationOid);
    }

    @GetMapping
    public List<BomLinks> listAll() {
        return bomLinksService.listAll();
    }
}
