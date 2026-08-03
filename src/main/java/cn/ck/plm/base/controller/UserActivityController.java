/*
 * Copyright (c) 2025 深圳乘恺科技有限公司
 * All rights reserved.
 */
package cn.ck.plm.base.controller;

import cn.ck.plm.base.entity.UserActivity;
import cn.ck.plm.base.mapper.UserActivityMapper;
import cn.ck.plm.base.util.TenantContext;
import cn.ck.plm.base.util.UserContext;
import cn.ck.plm.iam.dto.ApiResponse;
import org.springframework.web.bind.annotation.*;

import jakarta.servlet.http.HttpServletRequest;
import java.util.*;

/**
 * 用户活动 API —— 记录和查询最近访问、最近操作。
 */
@RestController
@RequestMapping("/api/activity")
public class UserActivityController {

    private final UserActivityMapper activityMapper;

    public UserActivityController(UserActivityMapper activityMapper) {
        this.activityMapper = activityMapper;
    }

    /** 记录活动（通用入口，自动提取 IP/UA，自动填充 tenantOid） */
    @PostMapping
    public ApiResponse<Void> record(@RequestBody UserActivity activity, HttpServletRequest request) {
        try {
            String user = UserContext.get();
            activity.setOid(UUID.randomUUID().toString());
            activity.setUserOid(user);
            activity.setCreator(user);
            activity.setUpdater(user);
            activity.setTenantOid(TenantContext.get());
            // 自动捕获 IP 和 UA
            if (activity.getOperatorIp() == null) {
                activity.setOperatorIp(getClientIp(request));
            }
            if (activity.getUserAgent() == null) {
                activity.setUserAgent(request.getHeader("User-Agent"));
            }
            activityMapper.insert(activity);
            return ApiResponse.ok();
        } catch (Exception e) {
            return ApiResponse.fail(500, "记录失败: " + e.getMessage());
        }
    }

    /** 最近访问 */
    @GetMapping("/recent-access")
    public ApiResponse<List<Map<String, Object>>> recentAccess() {
        String userOid = UserContext.get();
        List<UserActivity> list = activityMapper.selectRecentAccess(userOid, 20);
        List<Map<String, Object>> result = new ArrayList<>();
        for (UserActivity a : list) {
            Map<String, Object> m = new HashMap<>();
            m.put("name", a.getTargetName());
            m.put("type", a.getTargetType());
            m.put("path", a.getTargetPath());
            m.put("time", a.getCreatedAt() != null ? a.getCreatedAt().toString() : null);
            m.put("ip", a.getOperatorIp());
            result.add(m);
        }
        return ApiResponse.ok(result);
    }

    /** 最近操作（含登录/注销） */
    @GetMapping("/recent-operations")
    public ApiResponse<List<Map<String, Object>>> recentOperations() {
        String userOid = UserContext.get();
        List<UserActivity> list = activityMapper.selectRecentOperations(userOid, 20);
        List<Map<String, Object>> result = new ArrayList<>();
        for (UserActivity a : list) {
            Map<String, Object> m = new HashMap<>();
            m.put("action", a.getActionDesc());
            m.put("target", a.getTargetName());
            m.put("time", a.getCreatedAt() != null ? a.getCreatedAt().toString() : null);
            m.put("result", a.getResult());
            m.put("ip", a.getOperatorIp());
            result.add(m);
        }
        return ApiResponse.ok(result);
    }

    /** 分页查询操作日志（按租户隔离，支持类型、时间筛选） */
    @GetMapping("/logs")
    public ApiResponse<Map<String, Object>> logs(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int pageSize,
            @RequestParam(required = false) String activityType,
            @RequestParam(required = false) String startDate,
            @RequestParam(required = false) String endDate) {
        String tenantOid = TenantContext.get();
        int offset = (page - 1) * pageSize;

        // 处理 endDate：如果指定了日期，扩展到当天 23:59:59
        String endDateQuery = endDate;
        if (endDate != null && !endDate.isEmpty() && endDate.length() <= 10) {
            endDateQuery = endDate + " 23:59:59";
        }

        List<UserActivity> list = activityMapper.selectActivityLogs(
                tenantOid, activityType, startDate, endDateQuery, pageSize, offset);
        int total = activityMapper.countActivityLogs(tenantOid, activityType, startDate, endDateQuery);

        List<Map<String, Object>> rows = new ArrayList<>();
        for (UserActivity a : list) {
            Map<String, Object> m = new HashMap<>();
            m.put("oid", a.getOid());
            m.put("userOid", a.getUserOid());
            m.put("activityType", a.getActivityType());
            m.put("actionDesc", a.getActionDesc());
            m.put("targetName", a.getTargetName());
            m.put("targetType", a.getTargetType());
            m.put("targetPath", a.getTargetPath());
            m.put("operatorIp", a.getOperatorIp());
            m.put("userAgent", a.getUserAgent());
            m.put("result", a.getResult());
            m.put("durationMs", a.getDurationMs());
            m.put("errorMessage", a.getErrorMessage());
            m.put("creator", a.getCreator());
            m.put("createdAt", a.getCreatedAt() != null ? a.getCreatedAt().toString() : null);
            rows.add(m);
        }

        Map<String, Object> result = new HashMap<>();
        result.put("rows", rows);
        result.put("total", total);
        result.put("page", page);
        result.put("pageSize", pageSize);
        return ApiResponse.ok(result);
    }

    /** 获取客户端真实 IP */
    private String getClientIp(HttpServletRequest request) {
        String ip = request.getHeader("X-Forwarded-For");
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getHeader("X-Real-IP");
        }
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getRemoteAddr();
        }
        if (ip != null && ip.contains(",")) {
            ip = ip.split(",")[0].trim();
        }
        return ip;
    }
}
