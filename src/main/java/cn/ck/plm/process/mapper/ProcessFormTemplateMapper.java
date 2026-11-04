/*
 * Copyright (c) 2026~2028 深圳乘恺科技有限公司
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
 * <p><b>本表按租户隔离</b>（2026-10-10 由 PLATFORM_SHARED 改来）：
 * 查询侧 SQL **不写**租户条件，由 {@code TenantStatementInterceptor} 注入 {@code tenant_oid = 当前租户}；
 * INSERT 显式写 {@code #{tenantOid}}（内置副本由 {@code FormInstaller} 从平台那份补齐到本租户）。
 *
 * <p>隔离的缘由：内置 3 张是引擎与前端的**渲染契约**（运行期按 code 派发），
 * 原先靠"平台一行全员可见"满足；改为隔离后必须**每个租户各有一份**，
 * 否则租户既看不到、也无法按需停用自己那张。详见 {@code FormInstaller} 的类注释。
 *
 * <p>行的"不可改不可删"由服务层把关（内置模板不可删、契约字段不可改），不能指望 SQL 拦住。
 */
public interface ProcessFormTemplateMapper {

    int insert(ProcessFormTemplate template);

    int update(ProcessFormTemplate template);

    int deleteByOid(String oid);

    ProcessFormTemplate selectByOid(String oid);

    ProcessFormTemplate selectByCode(String code);

    /** 本租户全部模板（内置 + 自定义），按 sort_order、code 排序 */
    List<ProcessFormTemplate> selectList();

    /** code 是否已被占用（excludeOid 用于编辑时排除自身）—— 只看本租户 */
    int countByCode(String code, String excludeOid);
}
