/*
 * Copyright (c) 2026~2028 深圳乘恺科技有限公司
 * All rights reserved.
 *
 * @author Kiween.Hu; Roney.Liu
 */

package cn.ck.plm.product.controller;

import cn.ck.plm.iam.dto.ApiResponse;
import cn.ck.plm.product.dto.MethodologyInstances;
import cn.ck.plm.product.service.api.MethodologyInstanceService;
import cn.ck.plm.product.service.impl.DeliverableStatusEvaluator;
import cn.ck.plm.product.service.impl.MetricEvaluator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 方法论实例读接口 —— 系列 / 型号页的「交付件 / 阶段门 / 评审 / 检查规则 / 指标 / 驾驶舱」面板的数据源。
 *
 * <p>归属（ownerOid = 系列或型号的 oid）由调用方给出；<b>阶段过滤在前端做</b>：
 * 数据量本来就小（一套 IPD = 46 + 10 + 8 + 11 + 11 + 3），一次往返比六个接口 + 六次过滤更省，
 * 也让"切阶段"变成纯前端动作。
 *
 * <p><b>2026-10-11 起交付件状态在返回前重算</b>（P3 判定首版，规则见
 * {@link DeliverableStatusEvaluator}）—— 绑定产出物 / 产出物进流程 / 产出物生命周期推进，
 * 都会在打开面板时反映到状态列。判定失败只告警、按库中现值返回，绝不因此打不开面板。
 * 门 / 评审 / 规则等其余五类的状态写回路仍属后续期。
 */
@RestController
@RequestMapping("/api/instances")
public class MethodologyInstanceController {

    private static final Logger log = LoggerFactory.getLogger(MethodologyInstanceController.class);

    private final MethodologyInstanceService instanceService;
    private final DeliverableStatusEvaluator deliverableStatusEvaluator;
    private final MetricEvaluator metricEvaluator;

    public MethodologyInstanceController(MethodologyInstanceService instanceService,
                                         DeliverableStatusEvaluator deliverableStatusEvaluator,
                                         MetricEvaluator metricEvaluator) {
        this.instanceService = instanceService;
        this.deliverableStatusEvaluator = deliverableStatusEvaluator;
        this.metricEvaluator = metricEvaluator;
    }

    /** 取某归属单元（系列 / 型号）的六类实例（交付件状态与指标数值先重算再返回） */
    @GetMapping
    public ApiResponse<MethodologyInstances> byOwner(@RequestParam String ownerOid) {
        try {
            deliverableStatusEvaluator.reevaluate(ownerOid);
        } catch (Exception e) {
            // 判定是"锦上添花"：失败就按库中现值返回（宁可用旧值，也不要打不开交付件面板）
            log.warn("交付件状态判定失败（按库中现值返回）: owner={} err={}", ownerOid, e.getMessage());
        }
        try {
            metricEvaluator.reevaluate(ownerOid);
        } catch (Exception e) {
            // 同理：指标算不出来不影响交付件 / 门 / 评审面板，指标区显示库中现值
            log.warn("指标评估失败（按库中现值返回）: owner={} err={}", ownerOid, e.getMessage());
        }
        return ApiResponse.ok(instanceService.findByOwner(ownerOid));
    }

    /**
     * 裁剪一条实例 —— <b>实例层</b>：「本项目不适用」（停用 / 恢复）；交付件还可改强制项与责任人。
     *
     * <pre>
     * PATCH /api/instances/deliverable/{oid}   { "disabled": true }              标记本项目不适用
     * PATCH /api/instances/deliverable/{oid}   { "mandatory": false }            改「强制 ↔ 可选」
     * PATCH /api/instances/deliverable/{oid}   { "ownerRole": "SYSTEM_ENGINEER" } 改责任人
     * PATCH /api/instances/gate/{oid}          { "disabled": false }             恢复
     * </pre>
     *
     * <p><b>权限是项目级的（2026-10-14 起）</b>：由"我在<b>该系列 / 型号团队</b>里的角色"决定 ——
     * 团队角色 = 产品经理（或管理员）可裁剪；判定见 {@link MethodologyInstanceService#tailor}
     * 与 {@code ProductTeamRoleResolver} —— 无权限返回 403。
     * 与模板层「企业裁剪」（业务配置 → 模型定义）是两层：这里只动本系列/型号的实例。
     */
    @PatchMapping("/{kind}/{oid}")
    public ApiResponse<Void> tailor(@PathVariable String kind, @PathVariable String oid,
                                    @RequestBody(required = false) Map<String, Object> body) {
        try {
            Boolean disabled = boolOrNull(body == null ? null : body.get("disabled"));
            Boolean mandatory = boolOrNull(body == null ? null : body.get("mandatory"));
            String ownerRole = strOrNull(body == null ? null : body.get("ownerRole"));
            instanceService.tailor(kind, oid, disabled, mandatory, ownerRole);
            return ApiResponse.ok();
        } catch (SecurityException e) {
            return ApiResponse.fail(403, e.getMessage());
        } catch (IllegalArgumentException e) {
            return ApiResponse.fail(400, e.getMessage());
        } catch (Exception e) {
            log.warn("实例裁剪失败: kind={} oid={} err={}", kind, oid, e.getMessage());
            return ApiResponse.fail(500, "裁剪失败: " + e.getMessage());
        }
    }

    /** body 里的布尔值：只认真正的 Boolean（字符串 / 数字一律当"没给"） */
    private static Boolean boolOrNull(Object value) {
        return value instanceof Boolean b ? b : null;
    }

    /** body 里的字符串：去空白；空串 / 非字符串一律当"没给" */
    private static String strOrNull(Object value) {
        if (!(value instanceof String s)) {
            return null;
        }
        String trimmed = s.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
