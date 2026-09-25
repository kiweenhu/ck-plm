/*
 * Copyright (c) 2025 深圳乘恺科技有限公司
 * All rights reserved.
 *
 * @author Kiween.Hu; Roney.Liu
 */

package cn.ck.plm.process.service.impl;

import cn.ck.plm.process.entity.ProcessNodeLog;
import cn.ck.plm.process.mapper.ProcessNodeLogMapper;
import cn.ck.plm.process.service.api.ProcessNodeLogService;
import cn.ck.plm.process.support.ProcessIdentitySupport;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * {@link ProcessNodeLogService} 的实现。
 *
 * <h3>两个关键决定</h3>
 * <ol>
 *   <li><b>独立事务（{@code REQUIRES_NEW}）</b>：服务节点失败时，引擎这一步的事务要回滚
 *       （对象状态不能改一半），但<b>日志必须留下</b> —— 否则"为什么失败"又只能去服务器上翻。
 *       独立事务让它不受外层回滚影响。</li>
 *   <li><b>写日志的异常不往外抛</b>：记录失败最多少一条线索；因为写日志失败让流程走不下去，
 *       是把可观测性问题升级成业务故障。这里吞掉并留一条应用日志。</li>
 * </ol>
 *
 * <h3>{@code REQUIRES_NEW} 必须标在"门面方法"上，不能只标 {@link #record}</h3>
 * <p>Spring 的事务是代理生效的：{@code info()} 内部调 {@code this.record()} 属于<b>自调用</b>，
 * 不经过代理，{@code record} 上的 {@code REQUIRES_NEW} 不会生效 —— 日志就会跟着外层
 * （失败节点的）事务一起回滚。<b>这不是推测</b>：端到端验证时命中过 —— 服务节点失败后
 * 日志表里一条都没有，而"日志必须留下"正是这张表存在的理由。
 * 因此三个对外方法各标一遍，{@code record} 被自调用时沿用外层新事务即可。
 */
@Service
public class ProcessNodeLogServiceImpl implements ProcessNodeLogService {

    private static final Logger log = LoggerFactory.getLogger(ProcessNodeLogServiceImpl.class);

    /** 堆栈留一段就够：目的是定位，不是备份整个调用链 */
    private static final int MAX_DETAIL = 8000;

    private final ProcessNodeLogMapper mapper;
    private final ProcessIdentitySupport identity;

    public ProcessNodeLogServiceImpl(ProcessNodeLogMapper mapper, ProcessIdentitySupport identity) {
        this.mapper = mapper;
        this.identity = identity;
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void record(ProcessNodeLog row) {
        if (row == null || isBlank(row.getProcessInstanceId()) || isBlank(row.getActivityId())) {
            return;
        }
        try {
            if (isBlank(row.getOid())) {
                row.setOid(UUID.randomUUID().toString());
            }
            if (isBlank(row.getTenantOid())) {
                row.setTenantOid(identity.currentTenantId());
            }
            if (row.getCreatedAt() == null) {
                row.setCreatedAt(LocalDateTime.now());
            }
            if (row.getMessage() == null) {
                row.setMessage("");
            }
            row.setDetail(truncate(row.getDetail()));
            mapper.insertLog(row);
        } catch (Exception e) {
            // 记日志失败不能影响流程本身：这里只在应用日志里留痕
            log.warn("写流程节点日志失败（不影响流程）: instance={} activity={} error={}",
                    row.getProcessInstanceId(), row.getActivityId(), e.getMessage());
        }
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void info(String instanceId, String activityId, String activityName, String source, String message) {
        record(base(instanceId, activityId, activityName, source, ProcessNodeLog.LEVEL_INFO, message));
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void error(String instanceId, String activityId, String activityName, String source,
                      String message, Throwable error) {
        ProcessNodeLog row = base(instanceId, activityId, activityName, source,
                ProcessNodeLog.LEVEL_ERROR, message);
        if (error != null) {
            row.setDetail(stackOf(error));
        }
        record(row);
    }

    @Override
    public List<ProcessNodeLog> findByInstance(String instanceId) {
        if (isBlank(instanceId)) {
            return List.of();
        }
        return mapper.selectByInstance(instanceId);
    }

    @Override
    public List<ProcessNodeLog> findByActivity(String instanceId, String activityId) {
        if (isBlank(instanceId) || isBlank(activityId)) {
            return List.of();
        }
        return mapper.selectByActivity(instanceId, activityId);
    }

    @Override
    public Map<String, int[]> countsByActivity(String instanceId) {
        Map<String, int[]> counts = new LinkedHashMap<>();
        if (isBlank(instanceId)) {
            return counts;
        }
        List<Map<String, Object>> rows;
        try {
            rows = mapper.selectCountsByInstance(instanceId);
        } catch (Exception e) {
            // 角标是锦上添花：查不到就不显示，不能让节点经路整页失败
            log.warn("统计节点日志数失败: instance={} error={}", instanceId, e.getMessage());
            return counts;
        }
        for (Map<String, Object> row : rows != null ? rows : List.<Map<String, Object>>of()) {
            Object activityId = valueOf(row, "activity_id", "activityId");
            Object level = valueOf(row, "level");
            Object cnt = valueOf(row, "cnt", "count");
            if (activityId == null || cnt == null) {
                continue;
            }
            int[] pair = counts.computeIfAbsent(activityId.toString(), key -> new int[2]);
            int count = toInt(cnt);
            pair[0] += count;
            if (ProcessNodeLog.LEVEL_ERROR.equals(String.valueOf(level))) {
                pair[1] += count;
            }
        }
        return counts;
    }

    @Override
    public void deleteByInstance(String instanceId) {
        if (isBlank(instanceId)) {
            return;
        }
        try {
            mapper.deleteByInstance(instanceId);
        } catch (Exception e) {
            log.warn("清理流程节点日志失败: instance={} error={}", instanceId, e.getMessage());
        }
    }

    // ==================== 内部方法 ====================

    private ProcessNodeLog base(String instanceId, String activityId, String activityName,
                                String source, String level, String message) {
        ProcessNodeLog row = new ProcessNodeLog();
        row.setProcessInstanceId(instanceId);
        row.setActivityId(activityId);
        row.setActivityName(activityName);
        row.setSource(source);
        row.setLevel(level);
        row.setMessage(message);
        return row;
    }

    private static String stackOf(Throwable error) {
        StringWriter writer = new StringWriter();
        error.printStackTrace(new PrintWriter(writer));
        return error.getClass().getName() + ": " + error.getMessage() + "\n" + writer;
    }

    private static String truncate(String text) {
        if (text == null || text.length() <= MAX_DETAIL) {
            return text;
        }
        return text.substring(0, MAX_DETAIL) + "\n…（已截断）";
    }

    /** 列名在不同驱动/写法下的大小写与别名可能不同，两种键都认一下 */
    private static Object valueOf(Map<String, Object> row, String... keys) {
        for (String key : keys) {
            if (row.containsKey(key)) {
                return row.get(key);
            }
        }
        return null;
    }

    private static int toInt(Object value) {
        if (value instanceof Number) {
            return ((Number) value).intValue();
        }
        try {
            return Integer.parseInt(String.valueOf(value));
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private static boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }
}
