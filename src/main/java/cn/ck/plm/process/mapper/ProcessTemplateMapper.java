/*
 * Copyright (c) 2025 深圳乘恺科技有限公司
 * All rights reserved.
 *
 * @author Kiween.Hu; Roney.Liu
 */

package cn.ck.plm.process.mapper;

import cn.ck.plm.process.entity.ProcessTemplate;

import java.util.List;

/**
 * 流程模板（{@code ck_process_template}）数据访问接口。
 *
 * <p>租户隔离由 {@code TenantStatementInterceptor} 自动注入（本表为业务表），
 * 因此此处 SQL <b>不写 tenant_oid</b>，保持最简。
 */
public interface ProcessTemplateMapper {

    int insert(ProcessTemplate template);

    int update(ProcessTemplate template);

    /** 删除模板主档（版本行由调用方一并删除） */
    int deleteByOid(String oid);

    ProcessTemplate selectByOid(String oid);

    ProcessTemplate selectByKey(String key);

    /**
     * 列表查询。
     *
     * @param keyword     名称/编码/描述模糊匹配，可为 null
     * @param categoryOid 分组 oid 精确匹配，可为 null
     * @param enabled     部署开关过滤（true = 允许部署），可为 null
     */
    List<ProcessTemplate> selectList(String keyword, String categoryOid, Boolean enabled);

    /** key 是否已被占用（excludeOid 用于更新场景排除自身） */
    int countByKey(String key, String excludeOid);

    /**
     * 移动到指定分组（只改 {@code category_oid} 列，不动 DSL 与版本）。
     *
     * <p>分类不是流程逻辑，而是模板的治理属性 —— 因此"换组"是一次元数据更新，
     * 不产生新版本（DSL 完全没变，不该有版本噪音）。
     */
    int updateCategory(String oid, String categoryOid);

    /**
     * 该分组下的模板数量（删除分组前的占用检查）。
     *
     * <p>刻意<b>没有</b>「按分组名批量改模板」这类方法：模板引用的是分组 oid，
     * 改字典里的名字不会影响任何模板行 —— 少一个能写错的地方。
     */
    int countByCategory(String categoryOid);
}
