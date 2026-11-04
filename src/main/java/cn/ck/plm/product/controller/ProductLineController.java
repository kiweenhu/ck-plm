/*
 * Copyright (c) 2026~2028 深圳乘恺科技有限公司
 * All rights reserved.
 *
 * @author Kiween.Hu; Roney.Liu
 */

package cn.ck.plm.product.controller;

import cn.ck.plm.iam.dto.ApiResponse;
import cn.ck.plm.product.dto.TeamMemberVO;
import cn.ck.plm.product.entity.ProductLine;
import cn.ck.plm.product.entity.ProductModel;
import cn.ck.plm.product.entity.Team;
import cn.ck.plm.product.service.api.ProductLineService;
import cn.ck.plm.product.service.api.ProductModelService;
import cn.ck.plm.product.service.impl.ProductTeamRoleResolver;
import cn.ck.plm.product.service.impl.ProductTeamVisibility;
import cn.ck.plm.softtype.dto.SoftTypeInstanceResult;
import cn.ck.plm.softtype.service.api.IBADataService;
import cn.ck.plm.softtype.service.api.SoftTypeInstanceService;
import cn.ck.plm.softtype.service.impl.IbaDataSupport;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 产品系列管理 REST 控制器。
 * <p>提供产品线的 CRUD 以及团队成员的增删查功能。
 */
@RestController
@RequestMapping("/api/product-lines")
public class ProductLineController {

    private final ProductLineService productLineService;
    private final ProductModelService productModelService;
    private final IbaDataSupport ibaDataSupport;
    private final IBADataService ibaDataService;
    private final ObjectMapper objectMapper;
    private final SoftTypeInstanceService softTypeInstanceService;
    private final ProductTeamVisibility productTeamVisibility;
    private final ProductTeamRoleResolver productTeamRoleResolver;

    private static final String IBA_ENTITY_TYPE = "PRODUCT_LINE";
    /** 型号的 IBA 宿主码（与 ProductModelServiceImpl.HOST 同值） */
    private static final String IBA_ENTITY_TYPE_MODEL = "PRODUCT_MODEL";
    /** 目标成本的 IBA 编码 —— 行业包 electronics-core 的 attributes.json 第 7 组 */
    private static final String IBA_TARGET_COST = "TARGET_COST";
    /** 目标上市日期的 IBA 编码 —— 同文件第 8 组 LAUNCH_TARGET */
    private static final String IBA_TARGET_LAUNCH_DATE = "TARGET_LAUNCH_DATE";
    /** 目标成本上限（元）：只防"手滑多打几个 0"，不做业务判断 */
    private static final BigDecimal TARGET_COST_MAX = new BigDecimal("1000000000000");

    public ProductLineController(ProductLineService productLineService, ProductModelService productModelService,
                                  IbaDataSupport ibaDataSupport, IBADataService ibaDataService,
                                  ObjectMapper objectMapper,
                                  SoftTypeInstanceService softTypeInstanceService,
                                  ProductTeamVisibility productTeamVisibility,
                                  ProductTeamRoleResolver productTeamRoleResolver) {
        this.productLineService = productLineService;
        this.productModelService = productModelService;
        this.ibaDataSupport = ibaDataSupport;
        this.ibaDataService = ibaDataService;
        this.objectMapper = objectMapper;
        this.softTypeInstanceService = softTypeInstanceService;
        this.productTeamVisibility = productTeamVisibility;
        this.productTeamRoleResolver = productTeamRoleResolver;
    }

    /**
     * 创建产品线。
     *
     * <p>创建编排已收敛到 {@code ProductLineInstanceCreator}，本方法复用同一实现。
     */
    @PostMapping
    public ApiResponse<ProductLine> create(@RequestBody Map<String, Object> body) {
        try {
            String typeCode = ibaDataSupport.getString(body, "typeDefinitionCode");
            SoftTypeInstanceResult result = softTypeInstanceService.createForHost(
                    IBA_ENTITY_TYPE, typeCode != null ? typeCode : IBA_ENTITY_TYPE, body);
            return ApiResponse.ok((ProductLine) result.getEntity());
        } catch (IllegalArgumentException e) {
            return ApiResponse.fail(400, e.getMessage());
        }
    }

    /** 更新产品线 —— 仅产品经理（团队角色）或管理员可维护（2026-10-17 用户口径） */
    @PutMapping("/{oid}")
    public ApiResponse<ProductLine> update(@PathVariable String oid, @RequestBody Map<String, Object> body) {
        if (!productTeamRoleResolver.canManageUnder(oid)) {
            return ApiResponse.fail(403, "仅产品经理可编辑产品系列");
        }
        try {
            // 更新编排已收敛到 ProductLineServiceImpl#updateInstance，与统一入口共用同一实现
            Object entity = softTypeInstanceService.updateForHost(IBA_ENTITY_TYPE, oid, body);
            return ApiResponse.ok((ProductLine) entity);
        } catch (IllegalArgumentException e) {
            return ApiResponse.fail(400, e.getMessage());
        } catch (UnsupportedOperationException e) {
            return ApiResponse.fail(501, e.getMessage());
        }
    }

    // ==================== 目标成本（IBA TARGET_COST） ====================

    /**
     * 读取容器（产品系列 / 型号）的目标成本。
     *
     * <p>目标成本是 IBA 属性（{@code ck_type_iba_data}），不在 {@code ck_product_line} 的列上，
     * 所以单独开这一个读写口：经营战报要就地显示与修改它，为读一个数把整个实体和六类实例都拉一遍不划算。
     * 系列 / 型号共用这套路由 —— oid 属于哪张表由 {@link #resolveIbaHost(String)} 判。
     */
    @GetMapping("/{oid}/target-cost")
    public ApiResponse<Map<String, Object>> getTargetCost(@PathVariable String oid) {
        String host = resolveIbaHost(oid);
        if (host == null) {
            return ApiResponse.fail(404, "产品系列或产品型号不存在");
        }
        return ApiResponse.ok(targetCostPayload(host, oid));
    }

    /**
     * 设置 / 清除容器（产品系列 / 型号）的目标成本。
     *
     * <p>权限与「编辑系列 / 型号」同一口径（团队角色 = 产品经理，管理员豁免）——
     * 目标成本是对成本的一句承诺，不是随手填的备注。
     *
     * <p>{@code targetCost} 传 null 或空串 = 清除：{@code mergeValues} 里值为 null 的那一项会被
     * 逐条插入时跳过，等价于只删这一条属性，其余 IBA 原样保留。负数与明显越界的数直接 400 ——
     * 静默截断会让人以为"填进去了"，比报错更糟。
     */
    @PutMapping("/{oid}/target-cost")
    public ApiResponse<Map<String, Object>> setTargetCost(@PathVariable String oid,
                                                          @RequestBody(required = false) Map<String, Object> body) {
        if (!productTeamRoleResolver.canManageUnder(oid)) {
            return ApiResponse.fail(403, "仅产品经理或管理员可设置目标成本");
        }
        String host = resolveIbaHost(oid);
        if (host == null) {
            return ApiResponse.fail(404, "产品系列或产品型号不存在");
        }
        Object raw = body == null ? null : body.get("targetCost");
        BigDecimal value = null;
        if (raw != null && !String.valueOf(raw).trim().isEmpty()) {
            try {
                value = new BigDecimal(String.valueOf(raw).trim());
            } catch (NumberFormatException e) {
                return ApiResponse.fail(400, "目标成本必须是数字（单位：元）");
            }
            if (value.signum() < 0) {
                return ApiResponse.fail(400, "目标成本不能为负数");
            }
            if (value.compareTo(TARGET_COST_MAX) > 0) {
                return ApiResponse.fail(400, "目标成本超出合理范围（上限 " + TARGET_COST_MAX.toPlainString() + " 元）");
            }
            value = value.setScale(2, RoundingMode.HALF_UP);
        }
        Map<String, Object> values = new HashMap<>();
        values.put(IBA_TARGET_COST, value);
        ibaDataService.mergeValues(host, oid, values);
        return ApiResponse.ok(targetCostPayload(host, oid));
    }

    /** oid 落在哪张表：系列优先（两者的 oid 不会重合），都不是则 null */
    private String resolveIbaHost(String oid) {
        if (oid == null || oid.trim().isEmpty()) {
            return null;
        }
        String key = oid.trim();
        try {
            if (productLineService.findByOid(key) != null) {
                return IBA_ENTITY_TYPE;
            }
        } catch (Exception ignore) {
            // 查系列失败不代表它是型号，继续往下判
        }
        try {
            if (productModelService.findByOid(key) != null) {
                return IBA_ENTITY_TYPE_MODEL;
            }
        } catch (Exception ignore) {
            // 统一落到下面的 404
        }
        return null;
    }

    /** 读回当前值：写完之后回显的就是库里的数，不让前端自己猜 */
    private Map<String, Object> targetCostPayload(String host, String oid) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("ownerType", IBA_ENTITY_TYPE_MODEL.equals(host) ? "MODEL" : "LINE");
        data.put("targetCost", ibaDataService.getValues(host, oid).get(IBA_TARGET_COST));
        return data;
    }

    /**
     * 读取容器（产品系列 / 型号）的目标上市日期（{@code TARGET_LAUNCH_DATE}，yyyy-MM-dd）。
     *
     * <p>与目标成本同一套路由与权限：这是经营战报「上市时间」卡片就地编辑用的，
     * 单独开一个口比让人去编辑弹窗里找字段现实。
     */
    @GetMapping("/{oid}/target-launch-date")
    public ApiResponse<Map<String, Object>> getTargetLaunchDate(@PathVariable String oid) {
        String host = resolveIbaHost(oid);
        if (host == null) {
            return ApiResponse.fail(404, "产品系列或产品型号不存在");
        }
        return ApiResponse.ok(launchDatePayload(host, oid));
    }

    /**
     * 设置 / 清除目标上市日期。
     *
     * <p>只收 {@code yyyy-MM-dd}：这是个"用来算天数差"的日期，不是自由文本，
     * 存成 2027/3/31 或 3 月 31 日之后就没法相减。传 null 或空串 = 清除。
     */
    @PutMapping("/{oid}/target-launch-date")
    public ApiResponse<Map<String, Object>> setTargetLaunchDate(@PathVariable String oid,
                                                                @RequestBody(required = false) Map<String, Object> body) {
        if (!productTeamRoleResolver.canManageUnder(oid)) {
            return ApiResponse.fail(403, "仅产品经理或管理员可设置目标上市日期");
        }
        String host = resolveIbaHost(oid);
        if (host == null) {
            return ApiResponse.fail(404, "产品系列或产品型号不存在");
        }
        Object raw = body == null ? null : body.get("targetLaunchDate");
        String value = null;
        if (raw != null && !String.valueOf(raw).trim().isEmpty()) {
            try {
                value = LocalDate.parse(String.valueOf(raw).trim(), DateTimeFormatter.ISO_LOCAL_DATE).toString();
            } catch (DateTimeParseException e) {
                return ApiResponse.fail(400, "目标上市日期格式应为 yyyy-MM-dd");
            }
        }
        Map<String, Object> values = new HashMap<>();
        values.put(IBA_TARGET_LAUNCH_DATE, value);
        ibaDataService.mergeValues(host, oid, values);
        return ApiResponse.ok(launchDatePayload(host, oid));
    }

    private Map<String, Object> launchDatePayload(String host, String oid) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("ownerType", IBA_ENTITY_TYPE_MODEL.equals(host) ? "MODEL" : "LINE");
        data.put("targetLaunchDate", ibaDataService.getValues(host, oid).get(IBA_TARGET_LAUNCH_DATE));
        return data;
    }

    /** 删除产品线（逻辑删除，进入回收站）—— 仅产品经理（团队角色）或管理员 */
    @DeleteMapping("/{oid}")
    public ApiResponse<Boolean> delete(@PathVariable String oid) {
        if (!productTeamRoleResolver.canManageUnder(oid)) {
            return ApiResponse.fail(403, "仅产品经理可删除产品系列");
        }
        return ApiResponse.ok(productLineService.delete(oid));
    }

    /** 查询回收站（逻辑删除的产品系列）—— 仅管理员可见（口径同前端：回收站只对租户管理员开放） */
    @GetMapping("/deleted")
    public ApiResponse<List<ProductLine>> listDeleted() {
        if (!productTeamVisibility.isAdmin()) {
            return ApiResponse.fail(403, "回收站仅租户管理员可见");
        }
        return ApiResponse.ok(productLineService.findDeleted());
    }

    /** 恢复产品系列（从回收站）—— 仅管理员可操作 */
    @PostMapping("/{oid}/restore")
    public ApiResponse<Boolean> restore(@PathVariable String oid) {
        if (!productTeamVisibility.isAdmin()) {
            return ApiResponse.fail(403, "回收站仅租户管理员可操作");
        }
        return ApiResponse.ok(productLineService.restore(oid));
    }

    /**
     * 查询产品线详情（同时支持产品系列和产品型号）。
     *
     * <p>「系列优先、型号回退并转换形态」的读取编排已收敛到
     * {@code ProductLineInstanceCreator#get}，与统一入口共用同一实现。
     */
    @GetMapping("/{oid}")
    public ApiResponse<Map<String, Object>> getByOid(@PathVariable String oid) {
        Object entity = softTypeInstanceService.getForHost(IBA_ENTITY_TYPE, oid, null);
        if (entity == null) {
            return ApiResponse.fail(404, "产品线或产品型号不存在");
        }
        return ApiResponse.ok(asFlatMap(entity));
    }

    /**
     * 详情统一按 Map 出参。
     *
     * <p>系列 / 型号都不是 {@code IBAExtensible}，读取路径靠 {@code withIba} 把 IBA 值摊平进 Map
     * （不摊平的话编辑弹窗里 {@code TARGET_COST / TARGET_LAUNCH_DATE} 永远是空，一保存就冲掉）。
     * 本端点原先硬转 {@code ProductLine}，于是每次读详情都 ClassCastException → 500
     * （2026-11-03 用户报「加载系列和信息错误」）。实体形态也一并兜住，免得哪天改回实体又炸。
     */
    @SuppressWarnings("unchecked")
    private Map<String, Object> asFlatMap(Object entity) {
        if (entity instanceof Map) {
            return (Map<String, Object>) entity;
        }
        return objectMapper.convertValue(entity, Map.class);
    }

    /** 产品线列表 / 搜索 */
    @GetMapping
    public ApiResponse<List<ProductLine>> list(@RequestParam(required = false) String keyword) {
        if (keyword != null && !keyword.trim().isEmpty()) {
            return ApiResponse.ok(productLineService.search(keyword));
        }
        return ApiResponse.ok(productLineService.findAll());
    }

    /** 产品线树（嵌套 children 结构，含子系列 + 产品型号） */
    @GetMapping("/tree")
    public ApiResponse<List<ProductLine>> tree() {
        return ApiResponse.ok(productLineService.findTree());
    }

    /** 纯产品系列树（嵌套 children 结构，仅子系列，不含产品型号，用于 product-line-select 控件） */
    @GetMapping("/tree-lines-only")
    public ApiResponse<List<ProductLine>> treeLinesOnly() {
        return ApiResponse.ok(productLineService.findLinesOnlyTree());
    }

    /** 产品线根节点列表 */
    @GetMapping("/roots")
    public ApiResponse<List<ProductLine>> roots() {
        return ApiResponse.ok(productLineService.findRoots());
    }

    /** 查询指定父节点的子节点 */
    @GetMapping("/children/{parentOid}")
    public ApiResponse<List<ProductLine>> children(@PathVariable String parentOid) {
        return ApiResponse.ok(productLineService.findChildren(parentOid));
    }

    /** 批量获取产品线统计（子系列数量 + 产品型号数量） */
    @GetMapping("/stats")
    public ApiResponse<Map<String, Map<String, Integer>>> stats() {
        return ApiResponse.ok(productLineService.getStats());
    }


    // ===== 团队管理 =====

    /** 获取产品线关联的团队信息 */
    @GetMapping("/{oid}/team")
    public ApiResponse<Team> getTeam(@PathVariable String oid) {
        Team team = productLineService.getTeamByProductLineOid(oid);
        if (team == null) {
            return ApiResponse.fail(404, "产品线或团队不存在");
        }
        return ApiResponse.ok(team);
    }

    /** 获取团队成员列表 */
    @GetMapping("/{oid}/team/members")
    public ApiResponse<List<TeamMemberVO>> getTeamMembers(@PathVariable String oid) {
        try {
            return ApiResponse.ok(productLineService.getTeamMembers(oid));
        } catch (Exception e) {
            return ApiResponse.fail(500, "获取团队成员失败: " + e.getMessage());
        }
    }

    /** 添加团队成员 —— 仅产品经理（团队角色）或管理员 */
    @PostMapping("/{oid}/team/members")
    public ApiResponse<Void> addTeamMember(@PathVariable String oid,
                                           @RequestBody Map<String, String> body) {
        if (!productTeamRoleResolver.canManageUnder(oid)) {
            return ApiResponse.fail(403, "仅产品经理可维护团队");
        }
        try {
            productLineService.addTeamMember(oid, body.get("userId"), body.get("roleName"));
            return ApiResponse.ok();
        } catch (IllegalArgumentException e) {
            return ApiResponse.fail(400, e.getMessage());
        }
    }

    /** 移除团队成员 —— 仅产品经理（团队角色）或管理员 */
    @DeleteMapping("/{oid}/team/members/{userId}")
    public ApiResponse<Void> removeTeamMember(@PathVariable String oid,
                                               @PathVariable String userId) {
        if (!productTeamRoleResolver.canManageUnder(oid)) {
            return ApiResponse.fail(403, "仅产品经理可维护团队");
        }
        try {
            productLineService.removeTeamMember(oid, userId);
            return ApiResponse.ok();
        } catch (IllegalArgumentException e) {
            return ApiResponse.fail(400, e.getMessage());
        }
    }

    /**
     * 批量查询「我能否维护这些系列 / 型号」（阶段 / 团队 / 编辑 / 删除）。
     *
     * <p>2026-10-17 用户要求：这四类操作只有产品经理（团队角色）能做，管理员豁免。
     * 列表页用它控制行按钮显隐（型号 oid 一并支持，同一判定）；真正的闸门在各写接口
     * （同样判 {@code canManageUnder}），这里只管显示、不管越权。
     */
    @GetMapping("/my-manage-permissions")
    public ApiResponse<Map<String, Boolean>> myManagePermissions(@RequestParam(required = false) List<String> oids) {
        Map<String, Boolean> result = new java.util.HashMap<>();
        if (oids != null) {
            for (String oid : oids) {
                if (oid != null && !oid.trim().isEmpty()) {
                    result.put(oid.trim(), productTeamRoleResolver.canManageUnder(oid.trim()));
                }
            }
        }
        return ApiResponse.ok(result);
    }
}
