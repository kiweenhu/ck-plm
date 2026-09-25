/*
 * Copyright (c) 2025 深圳乘恺科技有限公司
 * All rights reserved.
 *
 * @author Kiween.Hu; Roney.Liu
 */

package cn.ck.plm.softtype.service.api;

import cn.ck.plm.softtype.dto.SoftTypeInstanceResult;
import cn.ck.plm.softtype.entity.TypeDefinition;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * 能力宿主策略 —— 一个能力宿主（{@code type_definition.root_type_code}）一个实现，
 * 覆盖该宿主的<b>全部读写生命周期操作</b>，供统一入口 {@code /api/softtype-instances} 路由。
 *
 * <h3>实现位置：就落在宿主自己的 Service 实现类上</h3>
 * <p>本接口由宿主 Service 的实现类实现（如 {@code PartServiceImpl implements PartService,
 * SoftTypeInstanceCapability}），而<b>不是</b>另建一个平行的 {@code *InstanceCreator} 适配类。
 * 理由：那些适配类与宿主 Service 的职责高度重叠（同样要懂该宿主的编码/版本/生命周期/IBA 规则），
 * 平行存在就等于两套真相来源，随迭代必然分叉。
 *
 * <p>域接口（{@link cn.ck.plm.part.service.api.PartService} 等）<b>保持干净</b>，
 * 不感知本接口 —— 能力方法名统一带 {@code Instance} 后缀，既是自文档，也避免与
 * 域方法签名冲突（例如 {@code ProductLineService.delete(String)} 返回 {@code boolean}，
 * 与 {@code void delete(String)} 无法共存）。
 *
 * <h3>路由与失败</h3>
 * <ul>
 *   <li>创建 / 读取 / 更新为<b>必备能力</b>（无默认实现，编译期强制）。</li>
 *   <li>其余操作为<b>可选能力</b>：默认实现抛 {@link UnsupportedOperationException}，
 *       宿主按需覆写；不会出现「静默走错表」，也不会有「假分派」。</li>
 *   <li>{@link #supportedOperations()} 是前端行菜单的唯一依据 ——
 *       菜单项由能力驱动，不再靠前端映射表猜宿主。</li>
 * </ul>
 */
public interface SoftTypeInstanceCapability {

    /** 能力宿主 code（= {@code type_definition.root_type_code}，如 PART / ENG_DOCUMENT） */
    String hostCode();

    // ==================== 必备能力 ====================

    /**
     * 创建实例。
     *
     * @param type    类型定义（软类型时 {@code type.getCode()} 即软类型编码，须落到实例上）
     * @param payload 请求载荷（字段与实体原生端点一致，含分类 IBA / 实体 IBA 的动态字段）
     */
    SoftTypeInstanceResult createInstance(TypeDefinition type, Map<String, Object> payload);

    /**
     * 读取实例详情。
     *
     * <p>返回形态须与该实体原生 GET 端点一致（主对象 + 迭代合并字段 + 分类 IBA 等），
     * 前端通用组件据此回填表单。
     *
     * @return 实例详情；不存在返回 {@code null}
     */
    Object getInstance(String oid, Map<String, Object> params);

    /** 更新实例（主数据 + 迭代级真实列 + 分类 IBA + 实体 IBA），返回更新后的实体 */
    Object updateInstance(String oid, Map<String, Object> body);

    // ==================== 可选能力（默认不支持）====================

    /** 重命名（仅更新 name） */
    default Object renameInstance(String oid, String name) {
        throw unsupported("重命名");
    }

    /** 移动到新的容器 / 阶段 / 文件夹 */
    default Object moveInstance(String oid, Map<String, Object> body) {
        throw unsupported("移动");
    }

    /** 另存为：复制为新对象（新编号、新初始版本，含 IBA 复制） */
    default Object saveAsInstance(String oid, Map<String, Object> body) {
        throw unsupported("另存为");
    }

    /** 删除实例及其全部子版本 */
    default void deleteInstance(String oid) {
        throw unsupported("删除");
    }

    /** 删除最新小版本（仅删最新 iteration；若删后无版本则连同主对象删除） */
    default void deleteLatestIterationInstance(String oid) {
        throw unsupported("删除最新小版本");
    }

    /** 新建视图版本（revision+1，iteration=1） */
    default void newViewVersionInstance(String oid) {
        throw unsupported("新建视图版本");
    }

    /** 查询历史版本（全部子版本，含迭代级分类 IBA 等展示字段） */
    default List<Map<String, Object>> listIterationsInstance(String oid) {
        throw unsupported("查看历史版本");
    }

    /**
     * 设置迭代的生命周期状态（按<b>目标状态 code</b>，须符合该迭代绑定模板的迁移规则）。
     *
     * <p><b>为什么不在 {@link Operation} 里</b>：那个枚举驱动的是前端行菜单
     * （支持则显示菜单项）。本能力是流程「设置状态」自动服务节点的运行期动作，
     * 不是用户在列表上点出来的操作，进菜单等于凭空多一个用户可见的入口。
     *
     * @param oid             主对象 oid
     * @param entityVersion   大版本（如 A）；为空表示当前最新大版本
     * @param targetStateCode 目标状态 code（如 IN_WORK / PUBLISHED）
     * @throws IllegalArgumentException 目标状态不存在 / 当前状态不允许迁到它
     */
    default void setLifecycleStateInstance(String oid, String entityVersion, String targetStateCode) {
        throw unsupported("设置生命周期状态");
    }

    /**
     * 退回<b>模板的初始状态</b>（如 已发布 → 工作中 → 草稿）—— 行操作「设置对象到初始状态」用。
     *
     * <p>与 {@link #setLifecycleStateInstance} 的区别是"多跳"：指定状态要求当前状态能<b>一步</b>
     * 迁到目标；退回初始状态则允许沿模板的回退规则逐跳走（多跳在模板里没有直接规则），
     * 但每一跳都必须是模板允许的回退，不会"跳跃式改状态"。
     *
     * @param entityVersion 大版本（如 A）；为空表示当前最新大版本
     */
    default void resetLifecycleStateInstance(String oid, String entityVersion) {
        throw unsupported("退回初始状态");
    }

    // ==================== 能力发现 ====================

    /**
     * 该宿主实际支持的操作集合。
     *
     * <p><b>唯一用途</b>：驱动前端行菜单渲染 —— 支持则显示、不支持则不显示。
     * 实现须与实际覆写的方法保持一致；若声明了却未覆写，调用时会抛
     * {@link UnsupportedOperationException} 并在消息中指明操作名。
     */
    Set<Operation> supportedOperations();

    /** 生命周期操作枚举（{@code code()} 即前端菜单使用的标识） */
    enum Operation {
        CREATE,
        READ,
        UPDATE,
        RENAME,
        MOVE,
        SAVE_AS,
        DELETE,
        DELETE_LATEST_ITERATION,
        NEW_VIEW_VERSION,
        ITERATIONS;

        /** 小写下划线标识（前端菜单键，如 {@code new_view_version}） */
        public String code() {
            return name().toLowerCase(Locale.ROOT);
        }
    }

    /** 构造「宿主不支持该操作」异常，消息中带上宿主与操作名，便于诊断 */
    private UnsupportedOperationException unsupported(String operationName) {
        return new UnsupportedOperationException(
                "能力宿主 " + hostCode() + " 不支持「" + operationName + "」操作");
    }
}
