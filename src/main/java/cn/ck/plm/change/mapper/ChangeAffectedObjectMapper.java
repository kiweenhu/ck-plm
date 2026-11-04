/*
 * Copyright (c) 2026~2028 深圳乘恺科技有限公司
 * All rights reserved.
 */

package cn.ck.plm.change.mapper;

import cn.ck.plm.change.entity.ChangeAffectedObject;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.Map;

/**
 * 受影响对象 Mapper。
 *
 * <h3>两个关键查询</h3>
 * <ul>
 *   <li>{@link #selectActiveLock}：查出"这个主对象正被哪张变更单占用"，
 *       用于把数据库的唯一索引冲突翻译成可读提示；</li>
 *   <li>{@link #countPending}：统计未执行的受影响对象数 —— ECN 的 COMPLETE 判据。</li>
 * </ul>
 */
public interface ChangeAffectedObjectMapper {

    int insert(ChangeAffectedObject affected);

    int update(ChangeAffectedObject affected);

    int deleteByOid(String oid);

    ChangeAffectedObject selectByOid(String oid);

    /** 某张变更通告下的全部受影响对象（按创建时间正序） */
    List<ChangeAffectedObject> selectByNotice(String noticeOid);

    /**
     * 某个对象（主对象级）出现在哪些变更通告的受影响对象里 —— 「变更情况」反查用。
     *
     * <p>返回的是**通告 + 该条受影响对象**拼好的一行（前端一次渲染，不必再逐张通告取详情）：
     * 通告的编号 / 标题 / 状态 / 责任人 / 时间，加上被修订版本、修订产物版本、执行时间。
     * 按通告创建时间倒序（最近的变更在最前）。
     */
    List<Map<String, Object>> selectNoticesByEntity(@Param("entityType") String entityType,
                                                    @Param("entityOid") String entityOid);

    /** 某个变更任务下的受影响对象 */
    List<ChangeAffectedObject> selectByTask(String taskOid);

    /** 同一通告里该对象是否已存在（服务层前置校验，避免撞唯一索引） */
    int countByNoticeAndEntity(String noticeOid, String entityType, String entityOid);

    /** 该主对象当前被哪条未完成的变更占用（locked = TRUE），无则 null */
    ChangeAffectedObject selectActiveLock(String entityType, String entityOid);

    /** 未执行的受影响对象数（executed_at IS NULL） */
    int countPending(String noticeOid);

    /** 解锁整张通告（完成 / 取消时调用） */
    int releaseByNotice(String noticeOid);

    /** 锁定整张通告（批准时调用；返回 0 说明对象已被别的变更抢占，由唯一索引兜底） */
    int lockByNotice(String noticeOid);
}
