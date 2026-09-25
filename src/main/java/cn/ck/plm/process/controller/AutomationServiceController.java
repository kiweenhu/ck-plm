/*
 * Copyright (c) 2025 深圳乘恺科技有限公司
 * All rights reserved.
 *
 * @author Kiween.Hu; Roney.Liu
 */

package cn.ck.plm.process.controller;

import cn.ck.plm.iam.dto.ApiResponse;
import cn.ck.plm.process.delegate.PlmServiceHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Comparator;
import java.util.List;

/**
 * 自动化服务清单 API —— 供流程设计器的「函数调用」节点列出<b>后端已实现</b>的函数。
 *
 * <h3>为什么需要它</h3>
 * <p>候选清单原本写死在前端白名单里（{@code BUILTIN_SERVICES}），而后端实现是 Spring 收集的
 * {@link PlmServiceHandler} —— 两份清单必然漂移：新增一个后端函数忘了改前端，表现就是
 * "函数明明部署了，设计器的下拉里却选不到"，而没人会想到去翻前端常量（真实反馈）。
 *
 * <p>现在以后端为准：前端把这里返回的清单**追加**到自己的声明之后（显示名与参数声明仍在前端，
 * 因为面板要按它们渲染控件）。新增一个后端函数<b>只改后端一处</b>。
 */
@RestController
@RequestMapping("/api/plm/automation-services")
public class AutomationServiceController {

    private final List<PlmServiceHandler> handlers;

    public AutomationServiceController(List<PlmServiceHandler> handlers) {
        this.handlers = handlers;
    }

    @GetMapping
    public ApiResponse<List<AutomationServiceItem>> list() {
        return ApiResponse.ok(handlers.stream()
                .map(handler -> new AutomationServiceItem(handler.serviceId(), handler.label()))
                .sorted(Comparator.comparing(AutomationServiceItem::getId))
                .toList());
    }

    /** 下拉项：id 是节点上真正存下来的引用，label 只给人看 */
    public static class AutomationServiceItem {

        private final String id;
        private final String label;

        public AutomationServiceItem(String id, String label) {
            this.id = id;
            this.label = label;
        }

        public String getId() { return id; }
        public String getLabel() { return label; }
    }
}
