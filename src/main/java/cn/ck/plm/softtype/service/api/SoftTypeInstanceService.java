/*
 * Copyright (c) 2025 深圳乘恺科技有限公司
 * All rights reserved.
 *
 * @author Kiween.Hu; Roney.Liu
 */

package cn.ck.plm.softtype.service.api;

import cn.ck.plm.softtype.dto.SoftTypeInstanceResult;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 软类型实例统一服务 —— 以 {@code type_definition.root_type_code} 为<b>唯一真相</b>做服务端路由。
 *
 * <p>调用方只需给出类型编码，无需知道该类型落在哪张表、由哪个服务处理。
 * 路由目标为宿主自己的能力策略（{@link SoftTypeInstanceCapability}，实现在宿主 Service 实现类上）：
 *
 * <pre>
 * typeDefinitionCode
 *   → TypeDefinitionService.findByCode()   取类型定义
 *   → 校验非 DOMAIN（域锚点是命名空间，无能力宿主）
 *   → rootTypeCode                         唯一真相
 *   → SoftTypeInstanceRegistry.of(host)    取能力策略
 *   → capability.xxxInstance(...)          委托宿主执行
 * </pre>
 *
 * <p>任一环节不成立都<b>立即失败并说明原因</b>，不会静默落到错误的表。
 *
 * <h3>为什么需要它</h3>
 * <p>此前「类型 → 端点」映射被放在前端（{@code ENTITY_API_PATH}），形成两个真相来源，
 * 必然漂移（例如封装 FOOTPRINT 声明宿主为 ENG_DOCUMENT，却被提交到 {@code /parts}）。
 * 本服务把映射收敛到服务端权威字段。
 */
public interface SoftTypeInstanceService {

    // ==================== 统一入口（按类型编码路由）====================

    /**
     * 创建指定类型的实例。
     *
     * @param typeDefinitionCode 类型编码（OOTB 或 SOFT_TYPE，如 FOOTPRINT / ELECTRONIC）
     * @param payload            请求载荷（字段与实体原生端点一致）
     * @throws IllegalArgumentException 类型不存在 / 为域锚点 / 已禁用 / 宿主未实现能力
     */
    SoftTypeInstanceResult create(String typeDefinitionCode, Map<String, Object> payload);

    /**
     * 读取实例详情。
     *
     * <p>返回形态与该实体原生 GET 端点一致（主对象 + 最新迭代合并字段 + 分类 IBA 等）。
     *
     * @return 实例详情；不存在返回 {@code null}
     */
    Object get(String typeDefinitionCode, String oid, Map<String, Object> params);

    /** 更新实例（主数据 + 迭代级真实列 + 分类 IBA + 实体 IBA） */
    Object update(String typeDefinitionCode, String oid, Map<String, Object> body);

    /** 重命名（仅更新 name） */
    Object rename(String typeDefinitionCode, String oid, String name);

    /** 移动到新的容器 / 阶段 / 文件夹 */
    Object move(String typeDefinitionCode, String oid, Map<String, Object> body);

    /** 另存为：复制为新对象 */
    Object saveAs(String typeDefinitionCode, String oid, Map<String, Object> body);

    /** 删除实例及其全部子版本 */
    void delete(String typeDefinitionCode, String oid);

    /** 删除最新小版本 */
    void deleteLatestIteration(String typeDefinitionCode, String oid);

    /** 新建视图版本（revision+1，iteration=1） */
    void newViewVersion(String typeDefinitionCode, String oid);

    /** 查询历史版本（全部子版本，含展示字段） */
    List<Map<String, Object>> iterations(String typeDefinitionCode, String oid);

    /**
     * 设置实例的生命周期状态（按目标状态 code）。
     *
     * <p>供流程「设置状态」自动服务节点调用：它面对的是"这批对象该到哪个状态"，
     * 而不是某个宿主的表结构 —— 因此按类型编码路由到宿主能力，与其它统一入口一致。
     *
     * @param entityVersion   大版本（如 A）；为空表示当前最新大版本
     * @param targetStateCode 目标状态 code
     */
    void setLifecycleState(String typeDefinitionCode, String oid, String entityVersion, String targetStateCode);

    /**
     * 把实例退回生命周期模板的<b>初始状态</b>（沿模板的回退规则逐跳走）。
     *
     * @param entityVersion 大版本（如 A）；为空表示当前最新大版本
     */
    void resetLifecycleState(String typeDefinitionCode, String oid, String entityVersion);

    // ==================== 实体原生端点（按宿主，无类型信息）====================

    /**
     * 创建**指定宿主**下的实例（供实体原生端点复用，避免编排逻辑重复实现）。
     *
     * <p>会校验该类型的能力宿主是否等于 {@code expectedHost}，不符则拒绝 ——
     * 这样 {@code POST /api/parts} 仍严格只创建 PART 宿主对象，
     * 而不是变成统一入口的别名，同时二者共用同一份创建编排。
     *
     * @param expectedHost 期望的能力宿主，null 或空表示不校验
     */
    SoftTypeInstanceResult createForHost(String expectedHost, String typeDefinitionCode,
                                         Map<String, Object> payload);

    /**
     * 读取**指定宿主**下的实例详情（供实体原生端点复用）。
     *
     * <p>原生端点的请求里没有类型信息，但宿主已足以定位能力策略 —— 同一宿主的实例总在同一张表。
     *
     * @return 实例详情；不存在返回 {@code null}
     */
    Object getForHost(String hostCode, String oid, Map<String, Object> params);

    /** 更新**指定宿主**下的实例（供实体原生端点复用） */
    Object updateForHost(String hostCode, String oid, Map<String, Object> body);

    // ==================== 能力发现 / 诊断 ====================

    /**
     * 某类型在统一入口下支持的操作集合（小写下划线标识，如 {@code create} / {@code update}）。
     *
     * <p><b>前端行菜单的唯一依据</b>：支持则显示菜单项、不支持则不显示，
     * 因此新增宿主/软类型前端零改动，也不会出现「点了才报错」或「走错表」。
     */
    Set<String> supportedOperations(String typeDefinitionCode);

    /**
     * 解析类型所属的能力宿主（root_type_code），并校验其可用性。
     *
     * @return 宿主 code（大写）
     */
    String resolveHost(String typeDefinitionCode);

    /** 当前已注册的能力宿主集合（用于诊断与能力发现） */
    Set<String> supportedHosts();
}
