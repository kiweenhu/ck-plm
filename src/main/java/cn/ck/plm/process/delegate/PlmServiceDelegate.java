/*
 * Copyright (c) 2025 深圳乘恺科技有限公司
 * All rights reserved.
 *
 * @author Kiween.Hu; Roney.Liu
 */

package cn.ck.plm.process.delegate;

import cn.ck.plm.process.entity.ProcessNodeLog;
import cn.ck.plm.process.service.api.ProcessNodeLogService;
import org.flowable.bpmn.model.FieldExtension;
import org.flowable.bpmn.model.FlowElement;
import org.flowable.bpmn.model.ServiceTask;
import org.flowable.engine.delegate.DelegateExecution;
import org.flowable.engine.delegate.JavaDelegate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * 「自动服务」节点的运行期委托 —— BPMN 里的
 * {@code flowable:delegateExpression="${plmServiceDelegate}"}。
 *
 * <h3>为什么必须存在这个 Bean</h3>
 * <p>编译层刻意不给每个服务一个表达式（{@code ${object.promote}} 是非法 UEL，
 * 引擎会直接拒绝部署），而是统一委托到<b>一个</b> Bean，服务 id 与参数通过
 * {@code flowable:field} 传进来。于是这个 Bean 一旦缺失，引擎在运行到服务节点时
 * 抛的是 {@code Unknown property used in expression: ${plmServiceDelegate}} ——
 * 一句对使用者毫无意义的话（它读起来像"流程画错了"，实际是"后端没实现"）。
 *
 * <h3>执行语义</h3>
 * <ul>
 *   <li>服务 id → 处理器（Spring 收集，一个服务一个类，见 {@link PlmServiceHandler}）；</li>
 *   <li>作用对象 = 实例关联的业务实体集合（{@link PlmTargetResolver}）；</li>
 *   <li>参数值里的 {@code ${流程变量}} 在这里统一解析（见 {@link #resolveVariables}）；</li>
 *   <li><b>失败即抛出</b>：让引擎回滚这一步、办理人看到可读原因，而不是"办完了但什么都没发生"
 *       —— 自动服务静默失败是最难排查的一类问题（对象状态没变，谁也不知道为什么）。</li>
 *   <li><b>执行痕迹落库</b>：开始 / 完成 / 失败各记一条到节点日志（{@code ck_process_node_log}），
 *       流程详情页点开该节点即可看到 —— 否则"自动服务到底跑没跑、报了什么错"只能去翻服务器日志。</li>
 * </ul>
 */
@Component("plmServiceDelegate")
public class PlmServiceDelegate implements JavaDelegate {

    private static final Logger log = LoggerFactory.getLogger(PlmServiceDelegate.class);

    /** 服务 id 的字段名（编译层固定写这个，见 bpmn-compiler/namespaces.ts SERVICE_ID_FIELD） */
    private static final String FIELD_SERVICE_ID = "serviceId";

    private final Map<String, PlmServiceHandler> handlers;
    private final PlmTargetResolver targetResolver;
    private final ProcessNodeLogService nodeLogService;

    public PlmServiceDelegate(List<PlmServiceHandler> handlers, PlmTargetResolver targetResolver,
                             ProcessNodeLogService nodeLogService) {
        this.handlers = handlers.stream()
                .collect(Collectors.toMap(PlmServiceHandler::serviceId, Function.identity(),
                        (existing, duplicate) -> existing));
        this.targetResolver = targetResolver;
        this.nodeLogService = nodeLogService;
    }

    @Override
    public void execute(DelegateExecution execution) {
        Map<String, String> fields = readFields(execution);
        String serviceId = trimToNull(fields.remove(FIELD_SERVICE_ID));
        String nodeName = nodeName(execution);
        if (serviceId == null) {
            String reason = "自动服务节点「" + nodeName
                    + "」没有配置服务（缺少 serviceId 字段），请在设计器里重新选择服务后再部署";
            nodeLogService.error(execution.getProcessInstanceId(), execution.getCurrentActivityId(), nodeName,
                    ProcessNodeLog.SOURCE_SERVICE, "自动服务节点未配置服务", new PlmServiceException(reason));
            throw new PlmServiceException(reason);
        }
        PlmServiceHandler handler = handlers.get(serviceId);
        if (handler == null) {
            String reason = "自动服务「" + serviceId + "」还没有运行期实现（节点「" + nodeName
                    + "」）。当前可执行的自动服务：" + String.join(" / ", handlers.keySet())
                    + "。请改用已实现的服务，或联系管理员补齐该服务的运行期实现";
            nodeLogService.error(execution.getProcessInstanceId(), execution.getCurrentActivityId(), nodeName,
                    ProcessNodeLog.SOURCE_SERVICE, "自动服务未实现：" + serviceId,
                    new PlmServiceException(reason));
            throw new PlmServiceException(reason);
        }

        // 参数值里的 ${流程变量} 统一在这里解析（设计器面板的「参数集」就是这么承诺的）。
        // 解析失败与处理器失败同样是"这一步失败"：都要落库，否则办理人只看到一句报错，
        // 流程详情里查不到是哪个参数写坏了。
        try {
            fields.replaceAll(
                    (name, value) -> resolveVariables(value, name, nodeName, execution::getVariable));
        } catch (RuntimeException e) {
            nodeLogService.error(execution.getProcessInstanceId(), execution.getCurrentActivityId(), nodeName,
                    ProcessNodeLog.SOURCE_SERVICE, "自动服务参数解析失败：" + serviceId, e);
            throw e;
        }

        List<PlmServiceTarget> targets = targetResolver.resolve(execution);
        PlmServiceContext context = new PlmServiceContext(execution, fields, targets, nodeName);
        log.info("自动服务节点执行: service={}, node={}, instance={}, targets={}, params={}",
                serviceId, nodeName, execution.getProcessInstanceId(), targets.size(), fields);
        nodeLogService.info(execution.getProcessInstanceId(), execution.getCurrentActivityId(), nodeName,
                ProcessNodeLog.SOURCE_SERVICE,
                "自动服务开始执行：服务=" + serviceId + "，对象 " + targets.size() + " 个");
        try {
            handler.execute(context);
        } catch (RuntimeException e) {
            // 失败线索必须落库：这一步的事务会回滚（对象状态不能改一半），
            // 但"为什么失败"要留在流程详情里（日志写在独立事务，不受回滚影响）
            nodeLogService.error(execution.getProcessInstanceId(), execution.getCurrentActivityId(), nodeName,
                    ProcessNodeLog.SOURCE_SERVICE, "自动服务执行失败：" + serviceId, e);
            throw e;
        }
        nodeLogService.info(execution.getProcessInstanceId(), execution.getCurrentActivityId(), nodeName,
                ProcessNodeLog.SOURCE_SERVICE,
                "自动服务执行完成：服务=" + serviceId + "，对象 " + targets.size() + " 个"
                        + (fields.isEmpty() ? "" : "，参数 " + fields));
    }

    /**
     * 读节点的 {@code flowable:field}（编译层把服务 id 与各参数都放在这里）。
     *
     * <p>值可能是 {@code <flowable:string>} 也可能是表达式，这里统一按字符串取 ——
     * 编译层写的是<b>字面量</b>（如 {@code state=IN_WORK}），所以参数里写的 {@code ${变量名}}
     * 不会被引擎求值，必须由 {@link #resolveVariables} 在运行期解析。
     */
    private Map<String, String> readFields(DelegateExecution execution) {
        Map<String, String> fields = new LinkedHashMap<>();
        FlowElement element = execution.getCurrentFlowElement();
        if (!(element instanceof ServiceTask)) {
            return fields;
        }
        List<FieldExtension> extensions = ((ServiceTask) element).getFieldExtensions();
        for (FieldExtension extension : extensions != null ? extensions : List.<FieldExtension>of()) {
            fields.put(extension.getFieldName(), extension.getStringValue());
        }
        return fields;
    }

    /**
     * 参数值里的流程变量引用：{@code ${变量名}}。
     *
     * <p>变量名限定为标识符，并且允许出现在文本中间（{@code P-${code}-X}）——
     * 这样 API 参数里别的花括号内容（如整段 JSON 报文模板）不会被误当成变量。
     */
    private static final Pattern VARIABLE_REF =
            Pattern.compile("\\$\\{\\s*([A-Za-z_][A-Za-z0-9_]*)\\s*}");

    /**
     * 把参数值里的 {@code ${流程变量}} 换成运行期的变量值。
     *
     * <h3>为什么需要它</h3>
     * <p>设计器面板的「参数集」写明"取值可写 {@code ${变量名}}"（让流程变量参与定制服务调用
     * 是最常见的诉求），但编译层把每个参数都写成 {@code <flowable:string>} —— 那是<b>字面量</b>，
     * 引擎不会求值，{@code ${orderNo}} 会被原样发给被调用的 API/服务。
     *
     * <p>解析刻意放在这里（读参数的唯一入口）而不是编译层：所有服务都受益，
     * 而且<b>已部署的存量流程无需重新部署</b>即可生效。
     *
     * <h3>解析不到就抛</h3>
     * <p>取不到变量时宁可停在这一步（失败即中断，与自动服务的一贯语义一致），
     * 也不要把 {@code ${orderNo}} 当字符串发出去 —— 对方系统收到这种"看起来有值"的
     * 垃圾数据，比直接报错难查得多。
     *
     * @param value     参数原值（可含 0..n 个 {@code ${变量名}}，其余部分按字面量保留）
     * @param fieldName 参数名（出错信息要说清是哪个参数，否则节点上一堆参数无从下手）
     * @param nodeName  节点名（出错信息里定位）
     * @param lookup    变量取值（生产传 {@code execution::getVariable}；单测传 Map 即可）
     * @return 替换后的值（没有变量引用时原样返回）
     */
    static String resolveVariables(String value, String fieldName, String nodeName,
                                   Function<String, Object> lookup) {
        if (value == null || value.indexOf("${") < 0) {
            return value;
        }
        Matcher matcher = VARIABLE_REF.matcher(value);
        if (!matcher.find()) {
            return value;
        }
        StringBuilder resolved = new StringBuilder();
        int cursor = 0;
        do {
            resolved.append(value, cursor, matcher.start());
            String name = matcher.group(1);
            Object variable = lookup.apply(name);
            if (variable == null) {
                throw new PlmServiceException("自动服务节点「" + nodeName + "」的参数 " + fieldName
                        + " 引用了流程变量 ${" + name + "}，但运行期取不到它（变量名写错，"
                        + "或该变量到这一步还没产生）。请在设计器里核对该参数，或先在前置节点里赋值");
            }
            resolved.append(variable);
            cursor = matcher.end();
        } while (matcher.find());
        resolved.append(value, cursor, value.length());
        return resolved.toString();
    }

    /** 节点名：出错信息里用它定位（流程图上的名字，比 activityId 好认） */
    private String nodeName(DelegateExecution execution) {
        FlowElement element = execution.getCurrentFlowElement();
        if (element == null) {
            return "(未知节点)";
        }
        String name = element.getName();
        return trimToNull(name) != null ? name : element.getId();
    }

    private static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
