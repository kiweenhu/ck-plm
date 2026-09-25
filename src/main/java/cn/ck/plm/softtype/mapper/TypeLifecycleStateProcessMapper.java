/*
 * Copyright (c) 2025 深圳乘恺科技有限公司
 * All rights reserved.
 *
 * @author Kiween.Hu; Roney.Liu
 */

package cn.ck.plm.softtype.mapper;

import cn.ck.plm.softtype.entity.TypeLifecycleStateProcessLink;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 类型-生命周期状态-流程模板 关联（{@code ck_type_lifecycle_state_process_link}）数据访问接口。
 *
 * <p>本表在 {@code TenantStatementInterceptor} 的 <b>PLATFORM_SHARED</b> 白名单里
 * （与 {@code ck_type_lifecycle_template_link}、{@code ck_lifecycle_template*} 同族）：
 * 查询时注入 {@code tenant_oid IN (平台租户, 当前租户)}，因此平台类型的配置在业务租户下也看得到；
 * INSERT 的租户列由实体显式带上（取"类型关联行"的租户，见
 * {@code DefaultTypeLifecycleStateProcessService}）。
 *
 * <p>键是「类型 + 生命周期模板<b>子版本</b> + 状态」—— 子版本维度与业务对象迭代固化的
 * {@code lifecycle_template_iteration_oid} 同层，便于运行期精确解析。
 */
public interface TypeLifecycleStateProcessMapper {

    int insert(TypeLifecycleStateProcessLink link);

    /** 改绑：只更新目标流程模板（1:1，行本身不变） */
    int updateProcessTemplateOid(@Param("oid") String oid,
                                 @Param("processTemplateOid") String processTemplateOid,
                                 @Param("updater") String updater,
                                 @Param("updatedAt") LocalDateTime updatedAt);

    int deleteByOid(@Param("oid") String oid);

    /** 某类型在某生命周期模板子版本下的全部关联 */
    List<TypeLifecycleStateProcessLink> selectByTypeAndIteration(
            @Param("typeOid") String typeOid,
            @Param("lifecycleTemplateIterationOid") String lifecycleTemplateIterationOid);

    /**
     * 某类型 · 某子版本 · 某状态的全部关联行。
     *
     * <p><b>可能多条</b>：配置归属租户（{@code tenant_oid}），各租户可各自配置同一类型同一状态；
     * 平台租户的行作<b>共享默认</b>。本表按 {@code tenant_oid IN (平台, 当前)} 过滤，
     * 所以业务租户读到的常见是"平台默认 + 自己的一条"，由调用方按"本租户优先"挑。
     */
    List<TypeLifecycleStateProcessLink> selectByTypeIterationStatus(
            @Param("typeOid") String typeOid,
            @Param("lifecycleTemplateIterationOid") String lifecycleTemplateIterationOid,
            @Param("statusCode") String statusCode);

    /**
     * 某子版本下的全部关联（跨类型）。
     *
     * <p>生命周期模板产生新子版本时，用它取出旧版本的配置逐条继承到新版本。
     */
    List<TypeLifecycleStateProcessLink> selectByIterationOid(
            @Param("lifecycleTemplateIterationOid") String lifecycleTemplateIterationOid);

    /**
     * 清掉某类型的全部关联（类型改绑到别的生命周期模板 / 解绑模板时调用）。
     *
     * <p>不清的话，这些行会变成"界面上看不到、却仍挡着流程模板删除"的幽灵引用
     * —— 它们挂的子版本已不属于该类型当前用的模板。
     */
    int deleteByTypeOid(@Param("typeOid") String typeOid);

    /** 清掉挂在指定子版本上的全部关联（生命周期模板被删除时调用） */
    int deleteByIterations(@Param("iterationOids") List<String> iterationOids);

    /** 引用了该流程模板的全部关联行（删除流程模板前检查引用） */
    List<TypeLifecycleStateProcessLink> selectByProcessTemplateOid(@Param("processTemplateOid") String processTemplateOid);
}
