/*
 * Copyright (c) 2026~2028 深圳乘恺科技有限公司
 * All rights reserved.
 *
 * @author Kiween.Hu; Roney.Liu
 */

package cn.ck.plm.change.controller;

import cn.ck.plm.change.entity.ChangeRequest;
import cn.ck.plm.change.entity.ChangeRequestAffectedObject;
import cn.ck.plm.change.service.api.ChangeRequestService;
import cn.ck.plm.iam.dto.ApiResponse;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 变更请求（ECR）REST 控制器。
 *
 * <pre>
 * GET    /api/plm/change-requests                 列表（status / keyword / page / pageSize）
 * GET    /api/plm/change-requests/stats           各状态计数（页头状态概览）
 * GET    /api/plm/change-requests/{oid}           详情（请求本体 + 时间线 + 关联问题 + 预估对象）
 * POST   /api/plm/change-requests                 新建（编号自动生成 ECR-####，状态恒为 DRAFT）
 * PUT    /api/plm/change-requests/{oid}           编辑（patch 语义；已关闭/已取消不可编辑）
 * POST   /api/plm/change-requests/{oid}/transition 状态流转 { action, comment }
 * DELETE /api/plm/change-requests/{oid}           删除（仅草稿）
 * GET    /api/plm/change-requests/object-candidates 对象候选（object picker，keyword / limit）
 * GET    /api/plm/change-requests/related-objects  收集关联对象（零件 → 关联文档 + 替代件，entityOids 逗号分隔）
 * GET    /api/plm/change-requests/{oid}/affected-objects          预估受影响对象
 * POST   /api/plm/change-requests/{oid}/affected-objects          添加预估对象 { entityOid }
 * DELETE /api/plm/change-requests/{oid}/affected-objects/{affectedOid} 移除预估对象
 * </pre>
 *
 * <p>动作与状态口径见 {@code cn.ck.plm.change.flow.ChangeRequestFlow}。
 */
@RestController
@RequestMapping("/api/plm/change-requests")
public class ChangeRequestController {

    private final ChangeRequestService service;

    public ChangeRequestController(ChangeRequestService service) {
        this.service = service;
    }

    @GetMapping
    public ApiResponse<Map<String, Object>> list(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int pageSize) {
        try {
            return ApiResponse.ok(service.list(status, keyword, page, pageSize));
        } catch (IllegalArgumentException e) {
            return ApiResponse.fail(400, e.getMessage());
        }
    }

    /** 各状态计数（页头状态概览）：{@code { total, counts }} */
    @GetMapping("/stats")
    public ApiResponse<Map<String, Object>> stats() {
        return ApiResponse.ok(service.stats());
    }

    @GetMapping("/{oid}")
    public ApiResponse<Map<String, Object>> detail(@PathVariable String oid) {
        ChangeRequest request = service.detail(oid);
        if (request == null) return ApiResponse.fail(404, "变更请求不存在");
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("request", request);
        result.put("activities", service.activities(oid));
        // 关联问题单独返回（不是 request 的字段）：前端要拿编号/标题做展示与跳转
        result.put("issues", service.issues(oid));
        // 预估受影响对象：评审输入，带编码/名称/容器快照
        result.put("affectedObjects", service.affectedObjects(oid));
        // 子流程：本请求派生出的变更通告。关闭前"还差什么"看这里
        result.put("notices", service.childNotices(oid));
        return ApiResponse.ok(result);
    }

    @PostMapping
    public ApiResponse<ChangeRequest> create(@RequestBody ChangeRequest request) {
        try {
            return ApiResponse.ok(service.create(request));
        } catch (IllegalArgumentException e) {
            return ApiResponse.fail(400, e.getMessage());
        } catch (IllegalStateException e) {
            return ApiResponse.fail(403, e.getMessage());
        }
    }

    @PutMapping("/{oid}")
    public ApiResponse<ChangeRequest> update(@PathVariable String oid, @RequestBody ChangeRequest patch) {
        patch.setOid(oid);
        try {
            return ApiResponse.ok(service.update(patch));
        } catch (IllegalArgumentException e) {
            return ApiResponse.fail(400, e.getMessage());
        } catch (IllegalStateException e) {
            return ApiResponse.fail(403, e.getMessage());
        }
    }

    @PostMapping("/{oid}/transition")
    public ApiResponse<ChangeRequest> transition(@PathVariable String oid,
                                                 @RequestBody(required = false) Map<String, String> body) {
        Map<String, String> params = body == null ? Map.of() : body;
        try {
            return ApiResponse.ok(service.transition(oid, params.get("action"), params.get("comment")));
        } catch (IllegalArgumentException e) {
            return ApiResponse.fail(400, e.getMessage());
        } catch (IllegalStateException e) {
            return ApiResponse.fail(403, e.getMessage());
        }
    }

    @DeleteMapping("/{oid}")
    public ApiResponse<Boolean> delete(@PathVariable String oid) {
        try {
            return ApiResponse.ok(service.delete(oid));
        } catch (IllegalArgumentException e) {
            return ApiResponse.fail(400, e.getMessage());
        } catch (IllegalStateException e) {
            return ApiResponse.fail(403, e.getMessage());
        }
    }

    // ==================== 预估受影响对象 / 对象候选 ====================

    /**
     * 对象候选（选择器）。
     *
     * <p>返回 {@code oid / entityType / code / name / containerOid}；
     * 关键字为空时给最近更新的若干条，保证下拉打开就有内容。
     *
     * <p>{@code changeCode} 非空 = 该对象已被某张在途变更通告收录，前端置灰不可选。
     *
     * <p>{@code containerOid}（可选）= 本次变更选中的产品系列/型号：给了就只在这个范围内检索，
     * 避免把别的产品下的零件/文档挂到这张单上。
     */
    @GetMapping("/object-candidates")
    public ApiResponse<List<Map<String, Object>>> objectCandidates(
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "20") int limit,
            @RequestParam(required = false) String containerOid) {
        return ApiResponse.ok(service.objectCandidates(keyword, limit, containerOid));
    }

    /**
     * 收集所选对象关联的其他对象（变更任务挑对象时的「收集关联对象」）。
     *
     * <p>{@code entityOids} = 逗号分隔的对象 oid；当前口径只对零件生效：
     * 关联文档（DESCRIBES + REFERENCE）+ 替代件（双向），BOM 不收集。
     */
    @GetMapping("/related-objects")
    public ApiResponse<List<Map<String, Object>>> relatedObjects(@RequestParam String entityOids) {
        List<String> oids = new ArrayList<>();
        for (String part : entityOids.split(",")) {
            String oid = part.trim();
            if (!oid.isEmpty()) {
                oids.add(oid);
            }
        }
        return ApiResponse.ok(service.relatedObjects(oids));
    }

    @GetMapping("/{oid}/affected-objects")
    public ApiResponse<List<ChangeRequestAffectedObject>> affectedObjects(@PathVariable String oid) {
        return ApiResponse.ok(service.affectedObjects(oid));
    }

    @PostMapping("/{oid}/affected-objects")
    public ApiResponse<ChangeRequestAffectedObject> addAffected(@PathVariable String oid,
                                                               @RequestBody ChangeRequestAffectedObject affected) {
        try {
            return ApiResponse.ok(service.addAffected(oid, affected));
        } catch (IllegalArgumentException e) {
            return ApiResponse.fail(400, e.getMessage());
        } catch (IllegalStateException e) {
            return ApiResponse.fail(403, e.getMessage());
        }
    }

    @DeleteMapping("/{oid}/affected-objects/{affectedOid}")
    public ApiResponse<Boolean> removeAffected(@PathVariable String oid,
                                               @PathVariable String affectedOid) {
        try {
            return ApiResponse.ok(service.removeAffected(oid, affectedOid));
        } catch (IllegalArgumentException e) {
            return ApiResponse.fail(400, e.getMessage());
        } catch (IllegalStateException e) {
            return ApiResponse.fail(403, e.getMessage());
        }
    }
}
