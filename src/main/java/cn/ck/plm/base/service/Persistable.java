/*
 * Copyright (c) 2025 深圳乘恺科技有限公司
 * All rights reserved.
 *
 * @author Kiween.Hu; Roney.Liu
 */

package cn.ck.plm.base.service;

import java.time.LocalDateTime;

/**
 * 持久化对象根契约（Persistable），参考 Windchill Persistable 接口。
 *
 * <p>定义所有可持久化业务对象的公共能力：全局唯一标识（oid）、
 * 审计字段（创建/修改）、以及持久化状态判断。是
 * {@link cn.ck.plm.base.entity.BaseEntity} 的抽象接口层。
 *
 * <h3>Windchill 对应关系</h3>
 * <pre>
 * Persistable (this)  ← 等价于  wt.fc.Persistable
 *   ↑ implements
 * BaseEntity          ← 等价于  wt.fc.BusinessObject（简化版）
 *   ↑ extends
 * Mastered / RevisionControlled (future)
 * </pre>
 *
 * <p>Service 层面向本接口即可编写通用持久化逻辑，无需关心具体实体类型。
 */
public interface Persistable {

    // ==================== 全局标识 ====================

    /** 获取全局唯一标识（UUID v4，构造时自动生成） */
    String getOid();

    /** 设置全局唯一标识（主要用于 MyBatis 映射 / 反序列化） */
    void setOid(String oid);

    // ==================== 审计字段 ====================

    LocalDateTime getCreatedAt();
    void setCreatedAt(LocalDateTime createdAt);

    String getCreator();
    void setCreator(String creator);

    LocalDateTime getUpdatedAt();
    void setUpdatedAt(LocalDateTime updatedAt);

    String getUpdater();
    void setUpdater(String updater);

    // ==================== 持久化状态 ====================

    /**
     * 判断对象是否尚未持久化（createdAt 为空）。
     *
     * @return true 表示尚未入库
     */
    boolean isNew();

    /**
     * 判断对象是否已完成持久化（createdAt 不为空）。
     *
     * @return true 表示已入库
     */
    boolean isPersisted();
}
