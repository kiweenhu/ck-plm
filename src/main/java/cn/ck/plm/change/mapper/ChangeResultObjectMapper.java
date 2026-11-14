/*
 * Copyright (c) 2026~2028 深圳乘恺科技有限公司
 * All rights reserved.
 *
 * @author Kiween.Hu; Roney.Liu
 */

package cn.ck.plm.change.mapper;

import cn.ck.plm.change.entity.ChangeResultObject;

import java.util.List;

/**
 * 变更结果对象 Mapper（「变更后」）。
 *
 * <p>与 {@link ChangeAffectedObjectMapper}（「变更前」）是两张表：
 * 一条变更前对象可以对应多条结果（一拆多），也可能没有（还没执行）。
 */
public interface ChangeResultObjectMapper {

    int insert(ChangeResultObject result);

    /** 某个变更前对象产出的全部结果（按创建时间正序；一对一就是一条） */
    List<ChangeResultObject> selectByAffected(String affectedOid);

    /** 某张变更通告下的全部结果 */
    List<ChangeResultObject> selectByNotice(String noticeOid);

    /** 某个变更任务执行产出的全部结果 */
    List<ChangeResultObject> selectByTask(String taskOid);

    /** 某条变更前对象已产出的结果条数（0 = 还没执行） */
    int countByAffected(String affectedOid);

    /** 某张变更通告下已产出的结果条数（汇总用） */
    int countByNotice(String noticeOid);

    /** 删除某张通告的全部结果（通告重开清单时用） */
    int deleteByNotice(String noticeOid);
}
