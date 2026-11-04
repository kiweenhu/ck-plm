/*
 * Copyright (c) 2026~2028 深圳乘恺科技有限公司
 * All rights reserved.
 *
 * @author Kiween.Hu; Roney.Liu
 */

package cn.ck.plm.improve.controller;

import cn.ck.plm.iam.dto.ApiResponse;
import cn.ck.plm.improve.entity.Issue;
import cn.ck.plm.improve.entity.IssueActivity;
import cn.ck.plm.improve.service.api.IssueService;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 问题（持续改进 —— 跟踪与闭环）REST 控制器。
 *
 * <pre>
 * GET    /api/issues                     列表（status / severity / owner / keyword / page / pageSize）
 * GET    /api/issues/stats               各状态计数 + 闭环率
 * GET    /api/issues/{oid}               详情（问题本体 + 处理历程时间线）
 * POST   /api/issues                     新建（编号自动生成，状态恒为 OPEN）
 * PUT    /api/issues/{oid}               编辑（patch 语义；已关闭/已取消不可编辑）
 * POST   /api/issues/{oid}/transition    状态流转 { action, comment, verifyResult }
 * POST   /api/issues/{oid}/comments      追加跟踪备注 { comment }
 * DELETE /api/issues/{oid}               删除（连带时间线；一般问题应关闭而不是删除）
 * </pre>
 *
 * <p>动作与状态口径见 {@code cn.ck.plm.improve.flow.IssueFlow}：START / SUBMIT_VERIFY /
 * VERIFY_PASS / VERIFY_FAIL / CANCEL / REOPEN。
 */
@RestController
@RequestMapping("/api/issues")
public class IssueController {

    private final IssueService service;

    public IssueController(IssueService service) {
        this.service = service;
    }

    /**
     * 问题列表。
     *
     * <p>{@code relatedType / relatedCode}（2026-10-30）：按「关联对象」过滤，供对象详情页的
     * 「关联问题」页签调用 —— 问题只存关联对象的类型 + 编码，对象页拿自己的编码来查即可。
     * 例：{@code GET /api/issues?relatedType=PART&relatedCode=PART-202610-0002&pageSize=100}
     */
    @GetMapping
    public ApiResponse<Map<String, Object>> list(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String severity,
            @RequestParam(required = false) String owner,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String relatedType,
            @RequestParam(required = false) String relatedCode,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int pageSize) {
        return ApiResponse.ok(service.list(status, severity, owner, keyword,
                relatedType, relatedCode, page, pageSize));
    }

    @GetMapping("/stats")
    public ApiResponse<Map<String, Object>> stats() {
        return ApiResponse.ok(service.stats());
    }

    /**
     * 门的 ISSUE_OPEN 自动检查（"开门前还有几个未闭环问题"）：按产品统计 + 逐门判定。
     *
     * <p>现算不落库；未声明 ISSUE_OPEN 的门不出现在 gates 里。
     */
    @GetMapping("/gate-check")
    public ApiResponse<Map<String, Object>> gateCheck(@RequestParam String ownerOid) {
        try {
            return ApiResponse.ok(service.gateCheck(ownerOid));
        } catch (IllegalArgumentException e) {
            return ApiResponse.fail(400, e.getMessage());
        }
    }

    /**
     * 关联对象候选（表单里「关联对象编码」的模糊搜索，2026-10-25）。
     *
     * <pre>
     * GET /api/issues/object-candidates?keyword=003&amp;type=DOCUMENT&amp;limit=20
     * </pre>
     *
     * <p>返回 {@code oid / entityType / code / name}；{@code type} 空 = 五类实体（零组件 / 文档 /
     * 工程数据 / 产品系列 / 产品型号）都查。选中后前端把 {@code entityType + code} 一起写回表单，
     * 不用再手打编码。
     */
    @GetMapping("/object-candidates")
    public ApiResponse<List<Map<String, Object>>> objectCandidates(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String type,
            @RequestParam(defaultValue = "20") int limit) {
        return ApiResponse.ok(service.objectCandidates(keyword, type, limit));
    }

    @GetMapping("/{oid}")
    public ApiResponse<Map<String, Object>> detail(@PathVariable String oid) {
        Issue issue = service.detail(oid);
        if (issue == null) return ApiResponse.fail(404, "问题不存在");
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("issue", issue);
        result.put("activities", service.activities(oid));
        return ApiResponse.ok(result);
    }

    @PostMapping
    public ApiResponse<Issue> create(@RequestBody Issue issue) {
        try {
            return ApiResponse.ok(service.create(issue));
        } catch (IllegalArgumentException e) {
            return ApiResponse.fail(400, e.getMessage());
        } catch (IllegalStateException e) {
            return ApiResponse.fail(403, e.getMessage());
        }
    }

    @PutMapping("/{oid}")
    public ApiResponse<Issue> update(@PathVariable String oid, @RequestBody Issue patch) {
        patch.setOid(oid);
        try {
            return ApiResponse.ok(service.update(patch));
        } catch (IllegalArgumentException e) {
            return ApiResponse.fail(400, e.getMessage());
        } catch (IllegalStateException e) {
            return ApiResponse.fail(403, e.getMessage());
        }
    }

    /**
     * 状态流转（闭环核心）：START / SUBMIT_VERIFY / VERIFY_PASS / VERIFY_FAIL / CANCEL / REOPEN。
     *
     * <p>{@code SUBMIT_VERIFY} 可一并带 {@code rootCause} / {@code resolution} ——
     * "处理完了、请人来验"这一步正是填根因与措施的环节。
     */
    @PostMapping("/{oid}/transition")
    public ApiResponse<Issue> transition(@PathVariable String oid,
                                         @RequestBody(required = false) Map<String, String> body) {
        Map<String, String> params = body == null ? Map.of() : body;
        try {
            return ApiResponse.ok(service.transition(oid,
                    params.get("action"), params.get("comment"), params.get("verifyResult"),
                    params.get("rootCause"), params.get("resolution")));
        } catch (IllegalArgumentException e) {
            return ApiResponse.fail(400, e.getMessage());
        } catch (IllegalStateException e) {
            return ApiResponse.fail(403, e.getMessage());
        }
    }

    /** 追加跟踪备注（不改状态） */
    @PostMapping("/{oid}/comments")
    public ApiResponse<IssueActivity> addComment(@PathVariable String oid,
                                                 @RequestBody(required = false) Map<String, String> body) {
        Map<String, String> params = body == null ? Map.of() : body;
        try {
            return ApiResponse.ok(service.addComment(oid, params.get("comment")));
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
        } catch (IllegalStateException e) {
            return ApiResponse.fail(403, e.getMessage());
        }
    }
}
