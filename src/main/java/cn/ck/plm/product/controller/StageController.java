/*
 * Copyright (c) 2025 深圳乘恺科技有限公司
 * All rights reserved.
 *
 * @author Kiween.Hu; Roney.Liu
 */

package cn.ck.plm.product.controller;

import cn.ck.plm.iam.dto.ApiResponse;
import cn.ck.plm.product.entity.Stage;
import cn.ck.plm.product.service.api.StageService;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 研发阶段管理 REST 控制器。
 *
 * <p>路由挂载在 /api/product-lines/{ownerOid}/stages 下，
 * 阶段管理附带于产品线/产品型号，不独立暴露根路径。
 */
@RestController
@RequestMapping("/api/product-lines/{ownerOid}/stages")
public class StageController {

    private final StageService stageService;

    public StageController(StageService stageService) {
        this.stageService = stageService;
    }

    /** 查询归属单元下所有阶段 */
    @GetMapping
    public ApiResponse<List<Stage>> list(@PathVariable String ownerOid) {
        return ApiResponse.ok(stageService.findByOwnerOid(ownerOid));
    }

    /** 为归属单元初始化默认阶段（幂等），返回创建的所有阶段 */
    @PostMapping("/init")
    public ApiResponse<List<Stage>> initDefaults(@PathVariable String ownerOid,
                                                  @RequestParam(defaultValue = "LINE") String ownerType) {
        List<Stage> stages = stageService.initDefaultStages(ownerOid, ownerType);
        return ApiResponse.ok(stages);
    }

    /** 更新单个阶段信息 */
    @PutMapping("/{stageOid}")
    public ApiResponse<Stage> update(@PathVariable String ownerOid,
                                      @PathVariable String stageOid,
                                      @RequestBody Stage stage) {
        stage.setOid(stageOid);
        stage.setOwnerOid(ownerOid);
        return ApiResponse.ok(stageService.update(stage));
    }

    /** 切换阶段在仪表盘的显示状态 */
    @PutMapping("/{stageOid}/show-on-dashboard")
    public ApiResponse<Stage> toggleShowOnDashboard(@PathVariable String ownerOid,
                                                     @PathVariable String stageOid,
                                                     @RequestBody Map<String, Boolean> body) {
        Boolean showOnDashboard = body.get("showOnDashboard");
        if (showOnDashboard == null) {
            return ApiResponse.fail(400, "showOnDashboard 参数不能为空");
        }
        Stage stage = stageService.findByOid(stageOid);
        if (stage == null) {
            return ApiResponse.fail(404, "阶段不存在");
        }
        stage.setShowOnDashboard(showOnDashboard);
        return ApiResponse.ok(stageService.update(stage));
    }
}
