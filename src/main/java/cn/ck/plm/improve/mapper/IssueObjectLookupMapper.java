/*
 * Copyright (c) 2026~2028 深圳乘恺科技有限公司
 * All rights reserved.
 *
 * @author Kiween.Hu; Roney.Liu
 */

package cn.ck.plm.improve.mapper;

import java.util.List;
import java.util.Map;

/**
 * 问题「关联对象」的候选查询（只读）。
 *
 * <h3>为什么单独一个 Mapper</h3>
 * 问题的关联对象只存<b>类型 + 编码</b>（{@code ck_issue.related_type / related_code}），
 * 用户在表单里要能<b>按关键字搜出来选</b>，而不是手打编码（2026-10-25 用户反馈：
 * 输入"003"什么也搜不到）。这里只做五张主表的只读模糊查询，不依赖零件 / 文档服务的领域接口
 * —— 与变更模块 {@code ChangeObjectLookupMapper} 同一口径（那边还带单对象快照，问题这边不需要）。
 *
 * <p>返回字段统一为 {@code oid / entityType / code / name}，前端直接当选择器选项用。
 * 租户隔离交给 {@code TenantStatementInterceptor} 注入（SQL 里不写 tenant_oid）。
 */
public interface IssueObjectLookupMapper {

    /** 零组件：编码或名称模糊匹配（keyword 可空 = 最近更新的若干条） */
    List<Map<String, Object>> searchParts(String keyword, int limit);

    /** 文档 */
    List<Map<String, Object>> searchDocuments(String keyword, int limit);

    /** 工程数据（原理图 / PCB 等） */
    List<Map<String, Object>> searchEngDocuments(String keyword, int limit);

    /** 产品系列 */
    List<Map<String, Object>> searchProductLines(String keyword, int limit);

    /** 产品型号 */
    List<Map<String, Object>> searchProductModels(String keyword, int limit);
}
