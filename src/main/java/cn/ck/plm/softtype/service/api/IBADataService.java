/*
 * Copyright (c) 2025 深圳乘恺科技有限公司
 * All rights reserved.
 *
 * @author Kiween.Hu; Roney.Liu
 */

package cn.ck.plm.softtype.service.api;

import cn.ck.plm.softtype.IBAExtensible;

import java.util.Map;

/**
 * 通用 IBA 数据存取服务 —— 对 {@code ck_type_iba_data} 表进行 CRUD。
 *
 * <h3>使用模式</h3>
 * <pre>{@code
 * // 创建/更新：保存实体自身字段后调用
 * entityMapper.insert(entity);
 * ibaDataService.save(entity);
 *
 * // 查询：加载实体后还原 IBA 属性
 * MyEntity entity = entityMapper.selectByOid(oid);
 * ibaDataService.restore(entity);
 *
 * // 删除：移除实体后级联清理 IBA 数据
 * entityMapper.deleteByOid(oid);
 * ibaDataService.deleteByEntity(entity.getEntityType(), entity.getEntityOid());
 * }</pre>
 *
 * @see IBAExtensible
 */
public interface IBADataService {

    /**
     * 保存 IBA 动态属性值。
     * <p>先删除该实体的全部旧记录，再逐条插入新值，
     * 确保移除的 IBA 属性不会被残留。
     *
     * @param entity 实现了 {@link IBAExtensible} 的实体实例
     */
    void save(IBAExtensible entity);

    /**
     * 保存 IBA 动态属性值（Map 形式，不依赖 IBAExtensible 接口）。
     * <p>采用 delete-all + insert-all 策略，适用于新建场景（无旧数据）。
     * 更新场景请使用 {@link #mergeValues(String, String, Map)} 避免误删未提交的字段。
     *
     * @param entityType 实体类型（如 "ck_product_line"）
     * @param entityOid  实体 oid
     * @param values     attr_code → attr_value
     */
    void saveValues(String entityType, String entityOid, Map<String, Object> values);

    /**
     * 合并保存 IBA 动态属性值（Map 形式，适用于更新场景）。
     * <p>先加载已持久化的 IBA 数据，再用本次提交的值覆盖/新增，
     * 最后全量保存。已持久化但本次请求未包含的 IBA 字段不会被误删。
     *
     * @param entityType 实体类型
     * @param entityOid  实体 oid
     * @param values     本次提交的 attr_code → attr_value（仅含变更字段）
     */
    void mergeValues(String entityType, String entityOid, Map<String, Object> values);

    /**
     * 从数据库还原 IBA 属性值到实体。
     * <p>查询 {@code ck_type_iba_data} 表，将 attr_code → attr_value
     * 注入实体的 extAttrs Map，供 Jackson 序列化时展平输出。
     *
     * @param entity 实现了 {@link IBAExtensible} 的实体实例
     */
    void restore(IBAExtensible entity);

    /**
     * 获取实体的 IBA 属性值（Map 形式）。
     *
     * @param entityType 实体类型（如 "ck_product_line"）
     * @param entityOid  实体 oid
     * @return attr_code → attr_value 的 Map，无数据返回空 Map
     */
    Map<String, Object> getValues(String entityType, String entityOid);

    /**
     * 删除指定实体的全部 IBA 属性数据。
     *
     * @param entityType 实体类型
     * @param entityOid  实体 oid
     */
    void deleteByEntity(String entityType, String entityOid);
}
