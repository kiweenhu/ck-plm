/*
 * Copyright (c) 2026~2028 深圳乘恺科技有限公司
 * All rights reserved.
 */
package cn.ck.plm.revise.service.api;

/**
 * 修订提供者 —— 每种带版本的实体类型实现此接口，统一处理「修订」（大版本递增）。
 *
 * <p>与检出/检入（{@link cn.ck.plm.checkout.service.api.CheckoutProvider}）同一套扩展方式：
 * 新增带版本的实体类型时，实现本接口并声明为 Spring Bean，
 * {@code ReviseOperationServiceImpl} 会自动发现并按 {@code entityType} 路由，
 * 前端只需调用统一入口 {@code POST /api/revise}，不必为每种对象各写一条链路。
 *
 * <p><b>实现约定</b>：宿主已有修订实现的，Provider 只做转调（不复制一份版本复制逻辑），
 * 保证「行操作修订」与「流程 object.promote」走的是同一段代码。
 */
public interface ReviseProvider {

    /** 实体类型标识（如 PART、DOCUMENT、ENG_DOCUMENT） */
    String getEntityType();

    /** 实体类型中文名（如 部件、文档、工程数据） */
    String getEntityTypeName();

    /**
     * 修订：基于当前最新版本创建新的大版本（revision+1，iteration=1）。
     *
     * @param entityOid 主对象 oid（不是迭代 oid）
     * @param user      操作人（供需要记活动日志的宿主使用，可忽略）
     * @throws IllegalArgumentException 对象不存在 / 没有可用版本
     * @throws IllegalStateException    当前版本已检出等不允许修订的状态
     */
    void revise(String entityOid, String user);
}
