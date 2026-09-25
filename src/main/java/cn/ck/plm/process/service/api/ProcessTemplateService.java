/*
 * Copyright (c) 2025 深圳乘恺科技有限公司
 * All rights reserved.
 *
 * @author Kiween.Hu; Roney.Liu
 */

package cn.ck.plm.process.service.api;

import cn.ck.plm.process.dto.ProcessTemplateDeployResult;
import cn.ck.plm.process.dto.ProcessVersionDeleteResult;
import cn.ck.plm.process.entity.ProcessTemplate;
import cn.ck.plm.process.entity.ProcessTemplateVersion;

import java.util.List;

/**
 * 流程模板服务 —— 前端流程设计器的后端契约（spec §2.3）。
 *
 * <p>职责边界（与「只换入口，不动引擎」一致）：
 * <ul>
 *   <li>模板主存是 <b>DSL JSON</b>（设计态的唯一事实源），本服务不做 DSL→BPMN 翻译；</li>
 *   <li>部署时接收<b>前端编译产物</b>（BPMN XML），只做防御性校验后交给 Flowable；</li>
 *   <li>引擎内核（RepositoryService / RuntimeService）零改动。</li>
 * </ul>
 *
 * <p>版本语义：每次保存生成一条版本记录；部署可指定版本，默认部署最新版本。
 */
public interface ProcessTemplateService {

    /** 列表（keyword 模糊匹配名称/编码/描述；categoryOid、enabled 可选过滤） */
    List<ProcessTemplate> list(String keyword, String categoryOid, Boolean enabled);

    /** 模板详情（含最新 DSL） */
    ProcessTemplate get(String oid);

    /**
     * 新建模板（同时生成第 1 版）。
     *
     * <p><b>分组必填且必须已存在</b>（见 {@code ProcessCategoryService#requireByOid}）：
     * 分类是流程清单的导航骨架，若允许空值或自由文本，分组就会退化成命名混乱的标签。
     */
    ProcessTemplate create(ProcessTemplate template, String dslJson);

    /**
     * 移动到指定分组（元数据更新，不产生新版本）。
     *
     * <p>分类的<b>唯一修改入口</b>：设计器里不提供分类字段，换组在清单页完成，
     * 避免"设计器改了保存不生效"这类两处真相。
     *
     * @param categoryOid 目标分组 oid（引用 {@code ck_process_category.oid}）
     */
    ProcessTemplate moveToCategory(String oid, String categoryOid);

    /**
     * 保存：以新版本落库，并更新主档的最新 DSL 与版本号。
     *
     * @param changeNote 变更说明（可空）
     */
    ProcessTemplate save(String oid, String dslJson, String changeNote);

    /** 复制/另存：派生一个新模板（key 与名称由入参指定），以源模板最新 DSL 作为第 1 版 */
    ProcessTemplate copy(String oid, String newKey, String newName);

    /**
     * 部署开关：允许部署 / 停止部署（UI 文案即「允许部署」「停止部署」）。
     *
     * <p>停止部署后：<b>不能再发布新版本</b>，但模板仍可编辑、保存、复制、按版本删除，
     * <b>已部署的流程定义与在途实例不受影响</b>。
     *
     * <p>注意它<b>不是</b>"下架"：按 key 直接发起新实例并不会被这条挡住
     * （{@code startProcess} 不查模板），见 {@code ProcessTemplate#enabled} 的说明。
     */
    ProcessTemplate setEnabled(String oid, boolean enabled);

    /**
     * 按版本删除 —— <b>流程删除的唯一方式</b>。
     *
     * <p>清单页的交互与之对应：列出全部版本，<b>已部署的置灰不可选</b>，勾选未部署的版本后删除。
     *
     * <ol>
     *   <li>只允许删<b>未部署</b>的版本；选中的版本里只要有一版已部署，整批拒绝
     *       （抛 {@link IllegalStateException}）—— 引擎中的流程定义与历史实例仍按
     *       {@code deployment_id} 引用它，版本行一删就失去「当时部署了哪份 DSL / BPMN」的线索；</li>
     *   <li>删掉"最新版"时，主档的 {@code latest_version} 与 {@code dsl_json}
     *       <b>回落到剩下的最新版</b>（主档镜像的就是最新版）；</li>
     *   <li>删完最后一个版本 → 该流程<b>整体消失</b>（主档一并删除）。</li>
     * </ol>
     *
     * @param versions 要删除的版本号（自动去重、升序；为空即报错）
     */
    ProcessVersionDeleteResult deleteVersions(String oid, List<Integer> versions);

    /** 版本历史（新版本在前，不含 dslJson 正文以外的大字段裁剪） */
    List<ProcessTemplateVersion> versions(String oid);

    /** 指定版本详情（含该版本 DSL 与部署产物快照） */
    ProcessTemplateVersion version(String oid, Integer version);

    /**
     * 部署：把前端编译出的 BPMN XML 交给 Flowable，并记录部署标识与 XML 快照。
     *
     * @param oid      模板 oid
     * @param version  要部署的模板版本（null 表示最新版本）
     * @param bpmnXml  前端编译产物（BPMN 2.0 XML）
     */
    ProcessTemplateDeployResult deploy(String oid, Integer version, String bpmnXml);
}
