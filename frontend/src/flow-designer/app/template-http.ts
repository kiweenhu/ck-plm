/**
 * 流程模板 · HTTP 适配器（真实接线）
 *
 * <p>唯一职责：把 `@/api` 的具体函数适配成 {@link ./template-repo} 声明的
 * {@link TemplateHttp} 端口。
 *
 * <p>为什么要这一层：仓库层（{@link ./template-repo}）承载全部业务规则
 * （保存拦截、key 不可变、编译前置校验），它<b>不能直接 import `@/api`</b>——
 * 那会把 axios 与 ant-design-vue 拖进单测环境。故业务规则靠注入的端口测试，
 * 本文件只做「函数名 → 端点」的机械映射，由构建与联调验证。
 */

import {
  copyProcessTemplate,
  createProcessTemplate,
  deleteProcessTemplateVersions,
  deployProcessTemplate,
  disableProcessTemplate,
  enableProcessTemplate,
  getProcessTemplate,
  getProcessTemplateVersion,
  listProcessTemplates,
  listProcessTemplateVersions,
  saveProcessTemplate,
} from '@/api'
import type {
  ApiEnvelope,
  DeployOutcome,
  TemplateHttp,
  TemplateSummary,
  TemplateVersion,
  VersionDeleteOutcome,
} from './template-repo'

/**
 * 断言为后端统一包装。
 *
 * <p>需要断言的原因：`api/index.js` 的响应拦截器已经 `return res`（解包为
 * `{ code, message, data }`），但静态类型上仍是 axios 的 `AxiosResponse`——
 * JS 模块无法把「拦截器改了返回形态」表达给 TS。此处集中一次收口，
 * 避免在每个方法上散落 `as unknown as`。
 */
const asEnvelope = <T>(request: Promise<unknown>): Promise<ApiEnvelope<T>> =>
  request as unknown as Promise<ApiEnvelope<T>>

export function createTemplateHttp(): TemplateHttp {
  return {
    list: (params) => asEnvelope<TemplateSummary[]>(listProcessTemplates(params ?? {})),
    get: (oid) => asEnvelope<TemplateSummary>(getProcessTemplate(oid)),
    create: (payload) => asEnvelope<TemplateSummary>(createProcessTemplate(payload)),
    save: (oid, payload) => asEnvelope<TemplateSummary>(saveProcessTemplate(oid, payload)),
    removeVersions: (oid, versions) =>
      asEnvelope<VersionDeleteOutcome>(deleteProcessTemplateVersions(oid, versions)),
    copy: (oid, payload) => asEnvelope<TemplateSummary>(copyProcessTemplate(oid, payload)),
    setEnabled: (oid, enabled) =>
      asEnvelope<TemplateSummary>(enabled ? enableProcessTemplate(oid) : disableProcessTemplate(oid)),
    versions: (oid) => asEnvelope<TemplateVersion[]>(listProcessTemplateVersions(oid)),
    version: (oid, version) => asEnvelope<TemplateVersion>(getProcessTemplateVersion(oid, version)),
    deploy: (oid, payload) => asEnvelope<DeployOutcome>(deployProcessTemplate(oid, payload)),
  }
}
