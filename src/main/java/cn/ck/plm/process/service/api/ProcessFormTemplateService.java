/*
 * Copyright (c) 2025 深圳乘恺科技有限公司
 * All rights reserved.
 *
 * @author Kiween.Hu; Roney.Liu
 */

package cn.ck.plm.process.service.api;

import cn.ck.plm.process.entity.ProcessFormTemplate;

import java.util.List;

/**
 * 流程表单模板服务。
 *
 * <p>三件事：列表（平台内置 + 本租户自定义）、按节点类型挑可选项（设计器用）、
 * 以及自定义模板的增删改（内置模板受保护）。
 */
public interface ProcessFormTemplateService {

    /** 全部模板（平台内置 + 当前租户自定义），按 sort_order、code 排序 */
    List<ProcessFormTemplate> list();

    /**
     * 某节点类型在设计期可选的模板：<b>启用</b>且适用该类型。
     *
     * <p>与 {@link #list()} 分开是有意的：配置页要看全部（含停用的），
     * 设计器下拉只能看到能用的 —— 停用的模板出现在下拉里，用户选了就会得到一张
     * "运行期不渲染"的空表单。
     */
    List<ProcessFormTemplate> listForNodeType(String nodeType);

    /** 新建自定义模板（code 租户内唯一；builtin 强制为 false，租户取当前上下文） */
    ProcessFormTemplate create(ProcessFormTemplate template);

    /** 修改模板；内置模板只允许改 name / description / enabled / sortOrder */
    ProcessFormTemplate update(ProcessFormTemplate template);

    /** 删除模板；内置模板不允许删除 */
    void delete(String oid);
}
