/*
 * Copyright (c) 2025 深圳乘恺科技有限公司
 * All rights reserved.
 *
 * @author Kiween.Hu; Roney.Liu
 */

package cn.ck.plm.ai.support;

import cn.ck.plm.base.util.UserContext;
import cn.ck.plm.checkout.service.api.CheckoutService;
import cn.ck.plm.home.service.api.RecentObjectService;
import cn.ck.plm.iam.entity.User;
import cn.ck.plm.iam.service.api.NotificationService;
import cn.ck.plm.iam.service.api.UserService;
import cn.ck.plm.process.service.api.ProcessService;
import cn.ck.plm.search.GlobalSearchService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * AI 助手能"看"到的 CK-PLM 数据 —— 一组可被大模型调用的工具（function calling）。
 *
 * <h3>为什么工具是这几个</h3>
 * <p>它们对应"我在这个系统里最常问的几件事"：这个编码是什么对象、我有哪些待办、
 * 我最近动过什么、我锁了什么、有哪些流程在跑、企业最近发了什么公告。
 * 每个工具都<b>只是一个已有服务方法的薄封装</b> —— 不另写查询、不绕过权限。
 *
 * <h3>权限与租户</h3>
 * <p>工具在<b>发起聊天的那个请求线程</b>里执行，因此 {@code UserContext}/{@code TenantContext}
 * 天然是当前用户与当前租户：助手看到的数据<b>与用户本人手动查到的完全一致</b>，
 * 不会因为"问 AI"而多看到一条。这一点是刻意的 —— 否则 AI 就成了越权入口。
 *
 * <h3>返回格式</h3>
 * <p>统一返回 JSON 字符串（直接喂给模型）。字段名沿用各 VO 的原始字段，
 * 让模型自己判断该引用哪些 —— 这里不做"为模型裁剪字段"的加工，免得回答里缺细节。
 */
@Component
public class PlmTools {

    private static final Logger log = LoggerFactory.getLogger(PlmTools.class);

    /** 单次工具返回的条数上限：喂给模型的上下文不是越大越好 */
    private static final int LIMIT = 20;

    private final GlobalSearchService globalSearchService;
    private final ProcessService processService;
    private final RecentObjectService recentObjectService;
    private final CheckoutService checkoutService;
    private final NotificationService notificationService;
    private final UserService userService;
    private final ObjectMapper objectMapper;

    public PlmTools(GlobalSearchService globalSearchService,
                    ProcessService processService,
                    RecentObjectService recentObjectService,
                    CheckoutService checkoutService,
                    NotificationService notificationService,
                    UserService userService,
                    ObjectMapper objectMapper) {
        this.globalSearchService = globalSearchService;
        this.processService = processService;
        this.recentObjectService = recentObjectService;
        this.checkoutService = checkoutService;
        this.notificationService = notificationService;
        this.userService = userService;
        this.objectMapper = objectMapper;
    }

    /** OpenAI tools 格式的工具声明（description 写清楚"什么时候用它"，模型才选得准） */
    public List<Map<String, Object>> definitions() {
        List<Map<String, Object>> tools = new ArrayList<>();
        tools.add(tool("search_objects",
                "在 CK-PLM 里按关键字搜索业务对象（零组件、文档、工程数据等）。"
                        + "用户提到某个编码、名称，或问\"有没有/是哪个\"时用它。",
                prop("keyword", "string", "搜索关键字：编码或名称的片段"), List.of("keyword")));
        tools.add(tool("my_todo_tasks",
                "查询当前用户的待办任务（要我去审批/办理的流程任务）。"
                        + "用户问\"我有什么要办的\"\"我的待办\"时用它。",
                null, List.of()));
        tools.add(tool("my_recent_objects",
                "查询当前用户最近创建或修改过的业务对象。用户问\"我最近改了什么/最近动过哪些对象\"时用它。",
                prop("days", "integer", "回溯天数，默认 5"), List.of()));
        tools.add(tool("my_checkouts",
                "查询当前用户检出的对象（被他锁住、别人不能改的）。用户问\"我锁了什么/我的检出\"时用它。",
                null, List.of()));
        tools.add(tool("running_processes",
                "查询本租户当前正在运行的流程实例。用户问\"有哪些流程在跑/谁还没批\"时用它。",
                null, List.of()));
        tools.add(tool("latest_announcements",
                "查询企业最近发布的公告。用户问\"有什么通知/公告\"时用它。",
                null, List.of()));
        return tools;
    }

    /**
     * 执行一个工具调用。
     *
     * <p>任何失败都返回一句可读的说明而不是抛异常 —— 工具报错不该让整轮对话崩掉，
     * 模型看到"查询失败的原因"往往还能给出有用的回答。
     */
    public String execute(String name, Map<String, Object> args) {
        try {
            switch (name == null ? "" : name) {
                case "search_objects":
                    return json(globalSearchService.search(str(args, "keyword"), LIMIT));
                case "my_todo_tasks":
                    return json(processService.findTodoTasks(1, LIMIT));
                case "my_recent_objects":
                    return json(recentObjectService.findMyRecentObjects(intArg(args, "days", 5), LIMIT));
                case "my_checkouts": {
                    String userOid = currentUserOid();
                    return userOid == null ? "当前用户信息不可用（未登录或用户不存在）"
                            : json(checkoutService.findMyCheckouts(userOid));
                }
                case "running_processes":
                    return json(processService.findInstances("all-running", 1, LIMIT));
                case "latest_announcements": {
                    String userOid = currentUserOid();
                    return userOid == null ? "当前用户信息不可用（未登录或用户不存在）"
                            : json(notificationService.getByType(userOid, "ANNOUNCEMENT", 10));
                }
                default:
                    return "未知工具: " + name;
            }
        } catch (Exception e) {
            log.warn("AI 工具执行失败: name={} error={}", name, e.getMessage());
            return "查询失败：" + e.getMessage();
        }
    }

    /** 当前用户的 oid（工具要按人查的场景用） */
    private String currentUserOid() {
        String username = UserContext.get();
        if (username == null) return null;
        User user = userService.findByUsername(username);
        return user == null ? null : user.getOid();
    }

    private String json(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception e) {
            return "序列化结果失败：" + e.getMessage();
        }
    }

    private static Map<String, Object> tool(String name, String description,
                                            Map<String, Object> properties, List<String> required) {
        Map<String, Object> fn = new LinkedHashMap<>();
        fn.put("name", name);
        fn.put("description", description);
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("type", "object");
        params.put("properties", properties == null ? Map.of() : properties);
        params.put("required", required);
        fn.put("parameters", params);

        Map<String, Object> tool = new LinkedHashMap<>();
        tool.put("type", "function");
        tool.put("function", fn);
        return tool;
    }

    private static Map<String, Object> prop(String name, String type, String description) {
        Map<String, Object> p = new LinkedHashMap<>();
        p.put("type", type);
        p.put("description", description);
        Map<String, Object> props = new LinkedHashMap<>();
        props.put(name, p);
        return props;
    }

    private static String str(Map<String, Object> args, String key) {
        if (args == null) return "";
        Object v = args.get(key);
        return v == null ? "" : v.toString();
    }

    private static int intArg(Map<String, Object> args, String key, int fallback) {
        try {
            String raw = str(args, key);
            return raw.isEmpty() ? fallback : Integer.parseInt(raw.trim());
        } catch (Exception e) {
            return fallback;
        }
    }
}
