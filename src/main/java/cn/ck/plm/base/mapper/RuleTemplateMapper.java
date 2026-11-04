/*
 * Copyright (c) 2026~2028 深圳乘恺科技有限公司
 * All rights reserved.
 *
 * @author Kiween.Hu; Roney.Liu
 */

package cn.ck.plm.base.mapper;

import cn.ck.plm.base.entity.RuleTemplate;

import java.util.List;

/**
 * 检查规则模板数据访问接口。
 *
 * <p><b>实现必须显式书写 {@code tenant_oid}</b>：本表不在
 * {@code TenantStatementInterceptor} 的共享名单里，按「业务表」处理会被注入
 * {@code tenant_oid = 当前租户} —— 那样读不到平台副本（供克隆）。逐条语句显式带上 tenant_oid 后，
 * 拦截器命中即跳过注入（与阶段 / 交付件 / 门 / 评审模板的 Mapper 同款）。
 */
public interface RuleTemplateMapper {

    int insert(RuleTemplate template);

    int update(RuleTemplate template);

    int deleteByOid(String oid);

    RuleTemplate selectByOid(String oid);

    /** 按 code 取一条：本租户那份优先，缺则回落到平台那份 */
    RuleTemplate selectByCode(String code, String tenantOid, String platformOid);

    /** 查询本租户 + 平台的全部模板（本租户优先排序） */
    List<RuleTemplate> selectAll(String tenantOid, String platformOid);

    /** 查询指定租户的全部模板（克隆时用来取平台那一份） */
    List<RuleTemplate> selectByTenant(String tenantOid);

    /** 判重（幂等键）：本租户或平台已存在同 code 即算存在 */
    int existsByCode(String code, String tenantOid, String platformOid);
}
