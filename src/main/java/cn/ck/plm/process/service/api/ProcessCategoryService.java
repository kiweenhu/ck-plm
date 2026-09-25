/*
 * Copyright (c) 2025 深圳乘恺科技有限公司
 * All rights reserved.
 *
 * @author Kiween.Hu; Roney.Liu
 */

package cn.ck.plm.process.service.api;

import cn.ck.plm.process.entity.ProcessCategory;

import java.util.List;

/**
 * 流程分组服务 —— 流程清单页「分组」维度的后端契约。
 *
 * <p>分组是流程清单的第一层组织维度（先建分组 → 选分组 → 组内设计流程），
 * 本服务是分组字典的<b>唯一写入方</b>；模板侧的 {@code category} 列由这里统一维护：
 * <ul>
 *   <li>新建模板时校验分组存在（见 {@link ProcessTemplateService#create}）；</li>
 *   <li>分组改名时<b>同步</b>该分组下全部模板（见 {@link #update}）。</li>
 * </ul>
 */
public interface ProcessCategoryService {

    /** 全部分组（按 sort_order、name 排序） */
    List<ProcessCategory> list();

    /**
     * 新建分组。
     *
     * @param name        分组名（必填，租户内唯一）
     * @param sortOrder   排序序号（可空，默认 0）
     * @param description 说明（可空）
     */
    ProcessCategory create(String name, Integer sortOrder, String description);

    /**
     * 修改分组（改名 / 排序 / 说明）。
     *
     * <p><b>改名不需要同步模板</b>：模板引用的是本表的 oid，改名只影响显示
     * —— 这正是从"名称引用"改为"oid 引用"换来的收益。
     */
    ProcessCategory update(String oid, String name, Integer sortOrder, String description);

    /**
     * 删除分组。
     *
     * <p>组内仍有流程时<b>拒绝</b>并提示先移走 —— 静默把模板变成「未分类」
     * 会让用户在列表里"丢"掉流程。
     */
    void delete(String oid);

    /**
     * 按 oid 取分组，不存在即抛 {@link IllegalArgumentException}。
     *
     * <p>供模板服务在「新建模板 / 移动分组」前把关：模板的 {@code category_oid}
     * 必须指向一个真实存在的分组（分组是导航骨架，不能指向空气）。
     */
    ProcessCategory requireByOid(String oid);
}
