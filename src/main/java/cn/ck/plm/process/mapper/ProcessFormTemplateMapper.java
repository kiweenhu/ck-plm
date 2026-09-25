/*
 * Copyright (c) 2025 深圳乘恺科技有限公司
 * All rights reserved.
 *
 * @author Kiween.Hu; Roney.Liu
 */

package cn.ck.plm.process.mapper;

import cn.ck.plm.process.entity.ProcessFormTemplate;

import java.util.List;

/**
 * 流程表单模板（{@code ck_process_form_template}）数据访问接口。
 *
 * <p>本表在 {@code TenantStatementInterceptor} 里登记为 <b>PLATFORM_SHARED</b>：
 * <ul>
 *   <li>SELECT → 自动注入 {@code tenant_oid IN (平台, 当前租户)}，因此 SQL 里<b>不写</b>租户条件
 *       —— 平台内置模板对所有租户可见，租户自定义只对自己可见；</li>
 *   <li>INSERT → 自动注入当前租户（内置模板由初始化器显式设为平台租户）。</li>
 * </ul>
 *
 * <p>平台行的"不可改不可删"由服务层用 {@code TenantContext.requireEditPermission} 把关，
 * 不能指望 SQL 拦住 —— UPDATE/DELETE 的租户条件与 SELECT 同宽。
 */
public interface ProcessFormTemplateMapper {

    int insert(ProcessFormTemplate template);

    int update(ProcessFormTemplate template);

    int deleteByOid(String oid);

    ProcessFormTemplate selectByOid(String oid);

    ProcessFormTemplate selectByCode(String code);

    /** 全部模板（平台内置 + 当前租户自定义），按 sort_order、code 排序 */
    List<ProcessFormTemplate> selectList();

    /** code 是否已被占用（excludeOid 用于编辑时排除自身） */
    int countByCode(String code, String excludeOid);
}
