/*
 * Copyright (c) 2025 深圳乘恺科技有限公司
 * All rights reserved.
 *
 * @author Kiween.Hu; Roney.Liu
 */

package cn.ck.plm.process.mapper;

import cn.ck.plm.process.entity.ProcessEntitySet;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 流程实例关联业务实体（{@code ck_process_entity_set}）数据访问接口。
 *
 * <p>与流程模板同源：SQL 里<b>不写 tenant_oid</b> —— 查询条件与 INSERT 的租户列
 * 都由 {@code TenantStatementInterceptor} 注入（本表为业务表）。
 */
public interface ProcessEntitySetMapper {

    /**
     * 写入一条关联（幂等）。
     *
     * <p>实现用 {@code ON CONFLICT DO NOTHING}：同一流程实例重复记录同一实体时不报错、不重复入行
     * （唯一键见 {@code ProcessTemplateSchemaInitializer#createEntitySetTable}）。
     *
     * @return 实际新增行数（已存在则为 0）
     */
    int insertIfAbsent(ProcessEntitySet row);

    /** 某流程实例关联的全部业务实体 */
    List<ProcessEntitySet> selectByProcessInstanceId(@Param("processInstanceId") String processInstanceId);

    /** 某业务实体（主对象）参与过的全部流程关联 */
    List<ProcessEntitySet> selectByEntityOid(@Param("entityOid") String entityOid);

    /** 某业务对象在指定<b>大版本</b>下参与过的全部流程关联（带版本对象按大版本反查） */
    List<ProcessEntitySet> selectByEntityOidAndEntityVersion(@Param("entityOid") String entityOid,
                                                             @Param("entityVersion") String entityVersion);

    /** 按发起时传入的业务标识查询 */
    List<ProcessEntitySet> selectByBusinessKey(@Param("businessKey") String businessKey);

    /**
     * 批量：这些业务对象参与过的全部流程关联。
     *
     * <p>给"候选里排除正在流程中的对象"做<b>粗筛</b>：一条 IN 就能把从未进过流程的对象
     * 一次性排除，只有剩下的少数才需要逐个去问流程引擎。
     */
    List<ProcessEntitySet> selectByEntityOids(@Param("entityOids") List<String> entityOids);

    /** 清理某流程实例的全部关联（流程实例被删除时一并清理，避免悬挂） */
    int deleteByProcessInstanceId(@Param("processInstanceId") String processInstanceId);
}
