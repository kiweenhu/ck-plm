/*
 * Copyright (c) 2025 深圳乘恺科技有限公司
 * All rights reserved.
 *
 * @author Kiween.Hu; Roney.Liu
 */

package cn.ck.plm.process.mapper;

import cn.ck.plm.process.entity.ProcessCategory;

import java.util.List;

/**
 * 流程分组（{@code ck_process_category}）数据访问接口。
 *
 * <p>租户隔离由 {@code TenantStatementInterceptor} 自动注入（本表为业务表），
 * 因此此处 SQL <b>不写 tenant_oid</b>，唯一索引的租户维度由 DDL 保证。
 */
public interface ProcessCategoryMapper {

    int insert(ProcessCategory category);

    int update(ProcessCategory category);

    int deleteByOid(String oid);

    ProcessCategory selectByOid(String oid);

    /** 全部分组（按 sort_order、name 排序） */
    List<ProcessCategory> selectList();

    /** 名称是否已被占用（excludeOid 用于改名时排除自身） */
    int countByName(String name, String excludeOid);
}
