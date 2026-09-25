/*
 * Copyright (c) 2025 深圳乘恺科技有限公司
 * All rights reserved.
 *
 * @author Kiween.Hu; Roney.Liu
 */

package cn.ck.plm.integration.controller;

import cn.ck.plm.iam.dto.ApiResponse;
import cn.ck.plm.integration.entity.TargetSystem;
import cn.ck.plm.integration.service.TargetSystemService;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 目标系统注册表 API —— 管理员维护（前端「系统配置 → 目标系统」），
 * 同时供流程设计器的「REST 接口调用」节点取下拉清单。
 *
 * <p><b>凭据永不回传</b>：这里统一把 {@code secret} 置空再返回，只带一个
 * {@code secretSet} 标记告诉前端"已配置过"。前端编辑时该栏留空即表示不修改
 * （见 {@code TargetSystemService.update} 的注释）。
 */
@RestController
@RequestMapping("/api/plm/target-systems")
public class TargetSystemController {

    private final TargetSystemService service;

    public TargetSystemController(TargetSystemService service) {
        this.service = service;
    }

    @GetMapping
    public ApiResponse<List<TargetSystem>> list() {
        List<TargetSystem> systems = service.listAll();
        systems.forEach(this::maskSecret);
        return ApiResponse.ok(systems);
    }

    @GetMapping("/{oid}")
    public ApiResponse<TargetSystem> get(@PathVariable String oid) {
        TargetSystem system = service.getByOid(oid);
        if (system == null) {
            return ApiResponse.fail(404, "目标系统不存在");
        }
        maskSecret(system);
        return ApiResponse.ok(system);
    }

    @PostMapping
    public ApiResponse<TargetSystem> create(@RequestBody TargetSystem system) {
        String error = validate(system);
        if (error != null) {
            return ApiResponse.fail(400, error);
        }
        TargetSystem created = service.create(system);
        maskSecret(created);
        return ApiResponse.ok(created);
    }

    @PutMapping("/{oid}")
    public ApiResponse<TargetSystem> update(@PathVariable String oid, @RequestBody TargetSystem system) {
        system.setOid(oid);
        String error = validate(system);
        if (error != null) {
            return ApiResponse.fail(400, error);
        }
        TargetSystem updated = service.update(system);
        if (updated == null) {
            return ApiResponse.fail(404, "目标系统不存在");
        }
        maskSecret(updated);
        return ApiResponse.ok(updated);
    }

    @DeleteMapping("/{oid}")
    public ApiResponse<Void> delete(@PathVariable String oid) {
        return service.delete(oid) ? ApiResponse.ok() : ApiResponse.fail(404, "目标系统不存在");
    }

    /**
     * 校验：地址是运行期唯一会被真正请求的东西，格式错必须拦在这里 ——
     * 否则流程跑到一半才发现"目标系统地址不是 URL"，而那时业务已经在等结果。
     */
    private String validate(TargetSystem system) {
        if (system.getCode() == null || system.getCode().trim().isEmpty()) {
            return "系统编码不能为空（流程节点按它引用该系统）";
        }
        if (system.getName() == null || system.getName().trim().isEmpty()) {
            return "系统名称不能为空";
        }
        String baseUrl = system.getBaseUrl();
        if (baseUrl == null || baseUrl.trim().isEmpty()) {
            return "系统地址不能为空";
        }
        if (!baseUrl.startsWith("http://") && !baseUrl.startsWith("https://")) {
            return "系统地址必须以 http:// 或 https:// 开头";
        }
        return null;
    }

    /** 抹掉明文凭据，但先把"配没配"记下来 —— 抹完就推不出来了 */
    private void maskSecret(TargetSystem system) {
        system.setSecretSet(system.getSecret() != null && !system.getSecret().isEmpty());
        system.setSecret(null);
    }
}
