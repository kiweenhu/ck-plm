/*
 * Copyright (c) 2026~2028 深圳乘恺科技有限公司
 * All rights reserved.
 *
 * @author Kiween.Hu; Roney.Liu
 */

package cn.ck.plm.product.dto;

import cn.ck.plm.product.entity.Dashboard;
import cn.ck.plm.product.entity.Deliverable;
import cn.ck.plm.product.entity.Gate;
import cn.ck.plm.product.entity.Metric;
import cn.ck.plm.product.entity.Review;
import cn.ck.plm.product.entity.Rule;

import java.util.List;

/**
 * 某归属单元（产品系列 / 型号）的六类方法论实例"一包带走"。
 *
 * <p>为什么一次全取而不是六个接口：数据量本就很小（智能硬件 IPD 一套 =
 * 交付件 46 + 阶段门 10 + 评审 8 + 规则 11 + 指标 11 + 驾驶舱 3），
 * 一次往返便于页面统一缓存与**按阶段在前端过滤**（阶段 → 交付件 / 门 / 规则按 stageKey 过滤，
 * 评审按该阶段的门过滤）。判定与求值属后续版本，这里只做展示所需的读取。
 */
public class MethodologyInstances {

    private List<Deliverable> deliverables;
    private List<Gate> gates;
    private List<Review> reviews;
    private List<Rule> rules;
    private List<Metric> metrics;
    private List<Dashboard> dashboards;

    /**
     * 「我在这个归属单元下能做什么」（2026-10-14 新增）—— 按**项目级团队角色**判，
     * 前端用它决定裁剪 / 发起 / 关联按钮的显隐；后端三处写操作各自独立校验。
     */
    private InstancePermissions permissions;

    public List<Deliverable> getDeliverables() { return deliverables; }
    public void setDeliverables(List<Deliverable> deliverables) { this.deliverables = deliverables; }

    public List<Gate> getGates() { return gates; }
    public void setGates(List<Gate> gates) { this.gates = gates; }

    public List<Review> getReviews() { return reviews; }
    public void setReviews(List<Review> reviews) { this.reviews = reviews; }

    public List<Rule> getRules() { return rules; }
    public void setRules(List<Rule> rules) { this.rules = rules; }

    public List<Metric> getMetrics() { return metrics; }
    public void setMetrics(List<Metric> metrics) { this.metrics = metrics; }

    public List<Dashboard> getDashboards() { return dashboards; }
    public void setDashboards(List<Dashboard> dashboards) { this.dashboards = dashboards; }

    public InstancePermissions getPermissions() { return permissions; }
    public void setPermissions(InstancePermissions permissions) { this.permissions = permissions; }
}
