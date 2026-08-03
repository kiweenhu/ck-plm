/*
 * Copyright (c) 2025 深圳乘恺科技有限公司
 * All rights reserved.
 *
 * @author Kiween.Hu; Roney.Liu
 */

package cn.ck.plm.base.util;

/**
 * 当前操作用户上下文 —— 基于 ThreadLocal 传递用户标识。
 *
 * <p>典型使用场景：
 * <ul>
 *   <li>Web 层通过 Filter/Interceptor 在请求入口设置当前用户</li>
 *   <li>{@link cn.ck.plm.base.config.AuditInterceptor} 自动读取填充审计字段</li>
 *   <li>请求结束后调用 {@link #clear()} 防止内存泄漏</li>
 * </ul>
 *
 * <p>未设置时默认返回 "system"。
 */
public final class UserContext {

    private static final String DEFAULT_USER = "system";
    private static final ThreadLocal<String> CURRENT_USER = new ThreadLocal<>();

    private UserContext() {
    }

    /** 设置当前用户（请求入口调用） */
    public static void set(String user) {
        CURRENT_USER.set(user);
    }

    /** 获取当前用户，未设置返回 "system" */
    public static String get() {
        String user = CURRENT_USER.get();
        return user != null ? user : DEFAULT_USER;
    }

    /** 请求结束后清理 */
    public static void clear() {
        CURRENT_USER.remove();
    }
}
