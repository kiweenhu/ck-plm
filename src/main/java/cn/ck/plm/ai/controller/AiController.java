/*
 * Copyright (c) 2025 深圳乘恺科技有限公司
 * All rights reserved.
 *
 * @author Kiween.Hu; Roney.Liu
 */

package cn.ck.plm.ai.controller;

import cn.ck.plm.ai.service.api.AiChatService;
import cn.ck.plm.iam.dto.ApiResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * CK-PLM 助手 REST API —— 顶栏的聊天入口用。
 *
 * <pre>
 * GET  /api/ai/status   助手状态（是否启用 / 是否配置完整 / 可选模型清单 / 认证方式）
 * POST /api/ai/chat     一轮对话
 *                       { "message": "...", "history": [{"role","content"}], "modelId": "workbuddy" }
 * </pre>
 *
 * <p>{@code modelId} 可空 —— 不传就用 {@code plm.ai.default-model}；传了但没配置过，
 * 会明确返回"没有这个模型"，而不是静默换一个（否则用户会以为"换模型生效了"，实际没有）。
 *
 * <p><b>权限口径</b>：助手执行工具时用的是"发起对话的这个人"的身份与租户（工具在同一请求线程里调
 * 既有服务），所以<b>它看到的数据不会比该用户手动查到的更多</b>。这不是顺带的结果，而是必须的性质 ——
 * 否则 AI 就成了绕过权限的查询入口。
 */
@RestController
@RequestMapping("/api/ai")
public class AiController {

    private static final Logger log = LoggerFactory.getLogger(AiController.class);

    private final AiChatService aiChatService;

    public AiController(AiChatService aiChatService) {
        this.aiChatService = aiChatService;
    }

    /** 助手状态 */
    @GetMapping("/status")
    public ApiResponse<Map<String, Object>> status() {
        return ApiResponse.ok(aiChatService.status());
    }

    /**
     * 一轮对话。
     *
     * <p>未配置大模型时同样返回 200，只是回答里说明"还没接上"—— 前端据此提示，
     * 不必为此专门做一套错误页。
     */
    @PostMapping("/chat")
    public ApiResponse<Map<String, Object>> chat(@RequestBody(required = false) Map<String, Object> body) {
        try {
            return ApiResponse.ok(aiChatService.chat(str(body, "message"), history(body), str(body, "modelId")));
        } catch (Exception e) {
            log.error("AI 对话失败: {}", e.getMessage(), e);
            return ApiResponse.fail(500, "AI 对话失败: " + e.getMessage());
        }
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, String>> history(Map<String, Object> body) {
        if (body == null) return List.of();
        Object raw = body.get("history");
        if (!(raw instanceof List<?> list)) return List.of();
        List<Map<String, String>> result = new ArrayList<>();
        for (Object item : list) {
            if (!(item instanceof Map<?, ?> map)) continue;
            Map<String, String> row = new LinkedHashMap<>();
            row.put("role", map.get("role") == null ? "" : map.get("role").toString());
            row.put("content", map.get("content") == null ? "" : map.get("content").toString());
            result.add(row);
        }
        return result;
    }

    private String str(Map<String, Object> body, String key) {
        if (body == null) return null;
        Object value = body.get(key);
        return value == null ? null : value.toString();
    }
}
