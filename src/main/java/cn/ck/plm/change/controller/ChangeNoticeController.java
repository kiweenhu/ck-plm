/*
 * Copyright (c) 2026~2028 深圳乘恺科技有限公司
 * All rights reserved.
 *
 * @author Kiween.Hu; Roney.Liu
 */

package cn.ck.plm.change.controller;

import cn.ck.plm.base.util.UserContext;
import cn.ck.plm.change.entity.ChangeAffectedObject;
import cn.ck.plm.change.entity.ChangeNotice;
import cn.ck.plm.change.service.api.ChangeNoticeService;
import cn.ck.plm.iam.dto.ApiResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 变更通告（ECN）REST 控制器 —— 变更模块的主界面入口。
 *
 * <pre>
 * GET    /api/plm/change-notices                            列表（status / requestOid / keyword / page / pageSize）
 * GET    /api/plm/change-notices/stats                      各状态计数
 * GET    /api/plm/change-notices/{oid}                      详情聚合 { notice, request, affectedObjects, tasks, activities }
 * POST   /api/plm/change-notices                            新建（编号 ECN-####，状态恒为 DRAFT）
 * PUT    /api/plm/change-notices/{oid}                      编辑（patch 语义；仅草稿/已驳回）
 * POST   /api/plm/change-notices/{oid}/transition           状态流转 { action, comment }
 * POST   /api/plm/change-notices/{oid}/status               管理员直接设置状态 { status, reason }
 * DELETE /api/plm/change-notices/{oid}                      删除（仅草稿）
 * GET    /api/plm/change-notices/by-entity                  按对象反查（entityType + entityOid → 通告列表）
 * GET    /api/plm/change-notices/{oid}/affected-objects     受影响对象
 * POST   /api/plm/change-notices/{oid}/affected-objects     添加受影响对象（必须指定具体版本）
 * DELETE /api/plm/change-notices/{oid}/affected-objects/{id} 移除（未执行前）
 * </pre>
 *
 * <p>动作与状态口径见 {@code cn.ck.plm.change.flow.ChangeNoticeFlow}。
 */
@RestController
@RequestMapping("/api/plm/change-notices")
public class ChangeNoticeController {

    private static final Logger log = LoggerFactory.getLogger(ChangeNoticeController.class);

    /** 能改单据状态的角色 —— 口径与 {@code ChangeConfigController} / 前端 MainLayout 一致 */
    private static final String ADMIN_ROLE_FILTER = "('TENANT_ADMIN', 'ADMIN', 'PLATFORM_ADMIN')";

    private final ChangeNoticeService service;
    private final JdbcTemplate jdbcTemplate;

    public ChangeNoticeController(ChangeNoticeService service, JdbcTemplate jdbcTemplate) {
        this.service = service;
        this.jdbcTemplate = jdbcTemplate;
    }

    @GetMapping
    public ApiResponse<Map<String, Object>> list(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String requestOid,
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int pageSize) {
        try {
            return ApiResponse.ok(service.list(status, requestOid, keyword, page, pageSize));
        } catch (IllegalArgumentException e) {
            return ApiResponse.fail(400, e.getMessage());
        }
    }

    @GetMapping("/stats")
    public ApiResponse<Map<String, Object>> stats() {
        return ApiResponse.ok(service.stats());
    }

    /**
     * 对象反查变更通告（对象详情页「变更情况」）。
     *
     * <p>路径写成 {@code /by-entity} 而不用 {@code /{oid}} 的变体：字面量段优先于路径变量，
     * 不会把 "by-entity" 当成某个通告 oid。
     */
    @GetMapping("/by-entity")
    public ApiResponse<List<Map<String, Object>>> byEntity(@RequestParam String entityType,
                                                           @RequestParam String entityOid) {
        try {
            return ApiResponse.ok(service.historyByEntity(entityType, entityOid));
        } catch (IllegalArgumentException e) {
            return ApiResponse.fail(400, e.getMessage());
        }
    }

    @GetMapping("/{oid}")
    public ApiResponse<Map<String, Object>> detail(@PathVariable String oid) {
        try {
            return ApiResponse.ok(service.detailBundle(oid));
        } catch (IllegalArgumentException e) {
            return ApiResponse.fail(404, e.getMessage());
        }
    }

    @PostMapping
    public ApiResponse<ChangeNotice> create(@RequestBody ChangeNotice notice) {
        try {
            return ApiResponse.ok(service.create(notice));
        } catch (IllegalArgumentException e) {
            return ApiResponse.fail(400, e.getMessage());
        } catch (IllegalStateException e) {
            return ApiResponse.fail(403, e.getMessage());
        }
    }

    @PutMapping("/{oid}")
    public ApiResponse<ChangeNotice> update(@PathVariable String oid, @RequestBody ChangeNotice patch) {
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
    public ApiResponse<ChangeNotice> transition(@PathVariable String oid,
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

    /**
     * 管理员直接设置单据状态（运维纠错）。
     *
     * <p>body：{@code { "status": "DRAFT", "reason": "..." }}。与 {@code /transition} 分开是
     * 故意为之：那条走状态机（谁都能按规则推），这条跳过规则与门槛（只有管理员能用），
     * 混在一个端点上，"有权限"和"能流转"就分不清了。
     */
    @PostMapping("/{oid}/status")
    public ApiResponse<ChangeNotice> setStatus(@PathVariable String oid,
                                               @RequestBody(required = false) Map<String, String> body) {
        if (!isTenantAdmin(UserContext.get())) {
            return ApiResponse.fail(403, "只有租户管理员可以设置单据状态");
        }
        Map<String, String> params = body == null ? Map.of() : body;
        try {
            return ApiResponse.ok(service.setStatusByAdmin(oid, params.get("status"), params.get("reason")));
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

    @GetMapping("/{oid}/affected-objects")
    public ApiResponse<List<ChangeAffectedObject>> affectedObjects(@PathVariable String oid) {
        return ApiResponse.ok(service.affectedObjects(oid));
    }

    @PostMapping("/{oid}/affected-objects")
    public ApiResponse<ChangeAffectedObject> addAffected(@PathVariable String oid,
                                                         @RequestBody ChangeAffectedObject affected) {
        try {
            return ApiResponse.ok(service.addAffected(oid, affected));
        } catch (IllegalArgumentException e) {
            return ApiResponse.fail(400, e.getMessage());
        } catch (IllegalStateException e) {
            return ApiResponse.fail(403, e.getMessage());
        }
    }

    /**
     * 按对象 oid 添加受影响对象（版本由服务端按对象 latest 迭代回查）。
     *
     * <p>body：{@code { "entityOid": "..." }}。更改事项上直接挑对象时用它 ——
     * 前端只有对象 oid，没有"被修订版本"；幂等，已在本通告里的对象直接返回既有那行。
     */
    @PostMapping("/{oid}/affected-objects/by-entity")
    public ApiResponse<ChangeAffectedObject> addAffectedByEntity(@PathVariable String oid,
                                                                 @RequestBody(required = false) Map<String, String> body) {
        try {
            String entityOid = body == null ? null : body.get("entityOid");
            return ApiResponse.ok(service.addAffectedByEntity(oid, entityOid));
        } catch (IllegalArgumentException e) {
            return ApiResponse.fail(400, e.getMessage());
        } catch (IllegalStateException e) {
            return ApiResponse.fail(403, e.getMessage());
        }
    }

    /**
     * 把受影响对象改派到某个更改事项（= 变更任务）。
     *
     * <p>body：{@code { "taskOid": "..." }}；传空 / 不传 = 取消归属（回到待分配）。
     */
    @PutMapping("/{oid}/affected-objects/{affectedOid}/task")
    public ApiResponse<ChangeAffectedObject> assignAffectedTask(@PathVariable String oid,
                                                                @PathVariable String affectedOid,
                                                                @RequestBody(required = false) Map<String, String> body) {
        try {
            String taskOid = body == null ? null : body.get("taskOid");
            return ApiResponse.ok(service.assignAffectedTask(oid, affectedOid, taskOid));
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

    /**
     * 当前用户是否本租户的管理员 —— 与 {@code ChangeConfigController#isTenantAdmin} 同一口径。
     *
     * <p>用 JDBC 而不是 Mapper：这里要 JOIN 三张表，MyBatis 的租户拦截器会改写 JOIN 上的
     * 租户条件，口径容易与本意漂移。角色按租户隔离（{@code ck_role} 各租户各一份），
     * 必须限定 {@code r.tenant_oid = rm.tenant_oid}，否则会命中别家租户的同名角色副本。
     */
    private boolean isTenantAdmin(String username) {
        if (username == null || username.trim().isEmpty()) {
            return false;
        }
        try {
            Integer count = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM ck_role_member rm "
                            + "INNER JOIN ck_role r ON r.oid = rm.role_oid "
                            + "INNER JOIN ck_user u ON u.oid = rm.user_oid "
                            + "WHERE u.username = ? AND r.code IN " + ADMIN_ROLE_FILTER
                            + " AND r.tenant_oid = rm.tenant_oid",
                    Integer.class, username);
            return count != null && count > 0;
        } catch (Exception e) {
            log.warn("租户管理员校验失败（按无权限处理）: username={}, {}", username, e.getMessage());
            return false;
        }
    }
}
