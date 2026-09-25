/**
 * 流程模板 · 仓储层（保存 / 版本 / 部署开关 / 复制 / 部署）
 *
 * <p><b>为什么要有这一层</b>：模板治理的规则不该散落在各按钮的点击回调里 ——
 * 一旦散开，就会出现「列表页拦了、设计器没拦」「保存拦了、部署没拦」的分叉。
 * 本文件把全部前置规则收口为单一实现：
 *
 * <ol>
 *   <li><b>保存拦截</b>（spec §4-I P0）：`validateDsl` 有 ERROR 时拒绝保存/部署，
 *       并把第一条错误原样回给 UI —— 这是「不合法不保存」的唯一实现点。</li>
 *   <li><b>流程 key 不可变</b>：后端保存只接收 `dslJson`，不改 `key` 列；
 *       若 DSL 里的 `meta.key` 被改过，部署时后端会拒绝
 *       （BPMN 的 `&lt;process id&gt;` 必须等于模板 key）。故在此提前拦下并给出正确做法。</li>
 *   <li><b>部署前置编译</b>：先编译再提交；产物为空或缺少 `&lt;process id&gt;` 即失败，
 *       不必绕到后端才报错。</li>
 *   <li><b>响应归一</b>：后端 `code !== 200` 时返回 `{ ok:false, error }`，
 *       <b>不再重复弹提示</b>（axios 拦截器已弹过一次）。</li>
 * </ol>
 *
 * <p>业务规则不 import `@/api`，而是依赖注入 {@link TemplateHttp} 端口 ——
 * 因此可以在 Node 单测里用假端口覆盖全部规则；真实接线见 `./template-http`。
 */

import { IssueLevel, parseFlowDsl, validateDsl, type FlowDsl, type ValidationReport } from '@flow-dsl-core'
import { compileToBpmnXml, type CompileOptions } from '@flow-compiler'

// ==================== 后端数据形态 ====================

/** 后端统一包装 `{ code, message, data }` */
export interface ApiEnvelope<T> {
  code: number
  message?: string
  data?: T
}

/** 流程模板主档（对应 ProcessTemplate 实体） */
export interface TemplateSummary {
  oid: string
  key: string
  name: string
  displayName?: string | null
  /** 所属分组 oid（引用 ck_process_category.oid）；显示名由分组字典解析 */
  categoryOid?: string | null
  description?: string | null
  // 原 primaryObjectType（主业务对象）已移除：业务实体关联改由 ProcessEntitySet 承担
  latestVersion?: number | null
  enabled?: boolean | null
  deployedVersion?: number | null
  deploymentId?: string | null
  processDefinitionId?: string | null
  deployedAt?: string | null
  dslJson?: string | null
}

/** 模板版本（对应 ProcessTemplateVersion 实体） */
export interface TemplateVersion {
  oid: string
  version: number
  changeNote?: string | null
  /** 该版本是否已部署 */
  deployed?: boolean | null
  deploymentId?: string | null
  /** 部署时的 BPMN 快照（未部署为 null） */
  bpmnXml?: string | null
  dslJson?: string | null
  createdAt?: string | null
}

/** 部署返回（ProcessTemplateDeployResult，字段以后端为准，故保留原样透出） */
export interface DeployOutcome extends Record<string, unknown> {
  deploymentId?: string | null
  processDefinitionId?: string | null
  version?: number | null
}

/** 部署结果：除后端返回外，带上本次编译的警告与产物（供 UI 提示降级项） */
export interface DeployReport {
  outcome: DeployOutcome
  warnings: string[]
  xml: string
}

/** 按版本删除的结果（对应后端 ProcessVersionDeleteResult） */
export interface VersionDeleteOutcome {
  templateOid?: string | null
  /** 本次真正删掉的版本号 */
  deleted?: number[] | null
  /** 删完最后一个版本后，该流程是否已整体消失 */
  templateRemoved?: boolean | null
  /** 删除后的最新版本号（流程整体消失时为 null） */
  latestVersion?: number | null
}

// ==================== 端口与结果 ====================

/** HTTP 端口：由 `./template-http` 用 `@/api` 实现，测试用假实现 */
export interface TemplateHttp {
  list(params?: { keyword?: string; categoryOid?: string; enabled?: boolean }): Promise<ApiEnvelope<TemplateSummary[]>>
  get(oid: string): Promise<ApiEnvelope<TemplateSummary>>
  create(payload: Record<string, unknown>): Promise<ApiEnvelope<TemplateSummary>>
  save(oid: string, payload: { dslJson: string; changeNote?: string }): Promise<ApiEnvelope<TemplateSummary>>
  /** 按版本删除（流程删除的唯一方式） */
  removeVersions(oid: string, versions: number[]): Promise<ApiEnvelope<VersionDeleteOutcome>>
  copy(oid: string, payload: { key: string; name?: string }): Promise<ApiEnvelope<TemplateSummary>>
  setEnabled(oid: string, enabled: boolean): Promise<ApiEnvelope<TemplateSummary>>
  versions(oid: string): Promise<ApiEnvelope<TemplateVersion[]>>
  version(oid: string, version: number): Promise<ApiEnvelope<TemplateVersion>>
  deploy(oid: string, payload: { version?: number; bpmnXml: string }): Promise<ApiEnvelope<DeployOutcome>>
}

export type Result<T> = { ok: true; data: T } | { ok: false; error: string }

const ok = <T>(data: T): Result<T> => ({ ok: true, data })
const fail = (error: string): Result<never> => ({ ok: false, error })

/** 加载后的模板：主档 + 已解析的 DSL + 校验报告 */
export interface LoadedTemplate {
  template: TemplateSummary
  dsl: FlowDsl
  report: ValidationReport
}

/** 指定版本：快照 + 已解析的 DSL */
export interface LoadedVersion {
  version: TemplateVersion
  dsl: FlowDsl
}

// ==================== 纯函数（可单测） ====================

/** DSL → 后端存储用的 JSON 文本 */
export function dslToJson(dsl: FlowDsl): string {
  return JSON.stringify(dsl)
}

/** 后端存储的 DSL 文本 → DSL 对象（结构不合法时给出可读原因） */
export function dslFromJson(json: string | null | undefined): Result<FlowDsl> {
  if (!json || !json.trim()) {
    return fail('模板没有 DSL 内容')
  }
  let parsed: unknown
  try {
    parsed = JSON.parse(json)
  } catch (e) {
    return fail(`模板 DSL 不是合法 JSON: ${e instanceof Error ? e.message : String(e)}`)
  }
  const result = parseFlowDsl(parsed)
  return result.ok ? ok(result.dsl) : fail(result.error)
}

/**
 * 保存/部署共用的合法性拦截。
 *
 * <p>这是 spec §4-I「不合法不保存」的唯一实现点。
 */
export function ensureSavable(dsl: FlowDsl): Result<ValidationReport> {
  const report = validateDsl(dsl)
  if (report.ok) {
    return ok(report)
  }
  const first = report.issues.find((issue) => issue.level === IssueLevel.ERROR)
  const where = first?.nodeId ? `（节点 ${first.nodeId}）` : ''
  return fail(`校验未通过，共 ${report.errorCount} 个错误${where}：${first?.message ?? '未知错误'}`)
}

/**
 * 该版本是否可删除：**已部署的版本不可删**。
 *
 * <p>为什么：版本行上存着 `bpmnXml` 部署快照与 `deploymentId`，而流程引擎中的流程定义
 * 与历史实例仍按它引用这一版 —— 删掉就失去了「当时部署的到底是哪份 DSL / BPMN」的追溯线索。
 *
 * <p>这是「哪些版本能删」的**唯一判定**（后端 `ProcessTemplateService#deleteVersions`
 * 同样以 `deployed` 为准并整批拒绝，见 {@link VersionDeleteOutcome}）；勾选框是否可点、
 * 「没有可删版本」的空态、以及"删完就整体消失"的提示都取自它，避免三处各判一遍。
 */
export function isVersionDeletable(version: { deployed?: boolean | null } | null | undefined): boolean {
  return !version?.deployed
}

/**
 * 把版本列表拆成「可删 / 已锁定」，供删除弹窗一次性拿到三个数字：
 * 可选数、锁定数、以及<b>全选是否会连流程一起删掉</b>（`deletable.length === versions.length`）。
 */
export function splitDeletableVersions<T extends { deployed?: boolean | null }>(
  versions: T[],
): { deletable: T[]; locked: T[] } {
  const deletable: T[] = []
  const locked: T[] = []
  for (const version of versions ?? []) {
    if (isVersionDeletable(version)) {
      deletable.push(version)
    } else {
      locked.push(version)
    }
  }
  return { deletable, locked }
}

/** 从 BPMN XML 中取出 `<process id="…">`（后端部署时会强校验它等于模板 key） */
export function extractProcessId(xml: string): string | null {
  const matched = /<(?:\w+:)?process\b[^>]*\bid="([^"]+)"/.exec(xml)
  return matched ? matched[1] : null
}

/** DSL 指纹：用于「是否有未保存改动」的判定 */
export function dslSignature(dsl: FlowDsl): string {
  return JSON.stringify(dsl)
}

/**
 * 后端统一包装 → {@link Result} 归一。
 *
 * <p>导出供同层仓储复用（分组仓储 `./category-repo` 用的是同一套后端约定），
 * 避免"两处各写一遍 code !== 200 的判断"。
 */
export function unwrap<T>(response: ApiEnvelope<T> | undefined): Result<T> {
  if (!response) {
    return fail('后端无响应')
  }
  if (response.code !== 200) {
    // 不在此处弹提示：axios 拦截器已按后端 message 弹过
    return fail(response.message || `请求失败（code=${response.code}）`)
  }
  return ok(response.data as T)
}

// ==================== 仓储 ====================

export interface TemplateRepository {
  list(params?: { keyword?: string; categoryOid?: string; enabled?: boolean }): Promise<Result<TemplateSummary[]>>
  load(oid: string): Promise<Result<LoadedTemplate>>
  /**
   * 新建：key/name 等取自 DSL 的 meta，保证「模板列」与「DSL.meta」一致。
   *
   * <p>分组由<b>入参</b>指定 oid：分组 oid 是数据库侧的引用，不该写进 DSL
   * （DSL 里 `meta.category` 只留一个可读的名称快照，供导出展示）。
   */
  create(dsl: FlowDsl, categoryOid: string): Promise<Result<TemplateSummary>>
  save(oid: string, dsl: FlowDsl, changeNote?: string, currentKey?: string): Promise<Result<TemplateSummary>>
  deploy(oid: string, dsl: FlowDsl, version?: number | null): Promise<Result<DeployReport>>
  versions(oid: string): Promise<Result<TemplateVersion[]>>
  loadVersion(oid: string, version: number): Promise<Result<LoadedVersion>>
  copy(oid: string, key: string, name?: string): Promise<Result<TemplateSummary>>
  setEnabled(oid: string, enabled: boolean): Promise<Result<TemplateSummary>>
  /**
   * 按版本删除（**流程删除的唯一方式**）：只允许删未部署的版本；
   * 删完最后一个版本 → 该流程整体消失。
   */
  removeVersions(oid: string, versions: number[]): Promise<Result<VersionDeleteOutcome>>
  /** 编译预览（与部署走同一编译入口，避免两处产物不一致） */
  compile(dsl: FlowDsl, options?: CompileOptions): Result<{ xml: string; warnings: string[]; processId: string | null }>
}

export function createTemplateRepository(http: TemplateHttp): TemplateRepository {
  return {
    async list(params) {
      return unwrap(await http.list(params))
    },

    async load(oid) {
      const fetched = unwrap(await http.get(oid))
      if (!fetched.ok) {
        return fetched
      }
      const template = fetched.data
      const parsed = dslFromJson(template?.dslJson)
      if (!parsed.ok) {
        return parsed
      }
      return ok({ template, dsl: parsed.data, report: validateDsl(parsed.data) })
    },

    async create(dsl, categoryOid) {
      const guard = ensureSavable(dsl)
      if (!guard.ok) {
        return guard
      }
      // 分组必填：清单页的用法是「先建分组 → 选中分组 → 组内设计流程」，
      // 没有分组的模板在左栏无处安放（后端同样会拒），故在唯一的新建入口提前拦下
      const target = (categoryOid ?? '').trim()
      if (!target) {
        return fail('请先选择分组：流程必须挂在某个分组下（可在清单页左侧「新建分组」）')
      }
      return unwrap(
        await http.create({
          key: dsl.meta.key,
          name: dsl.meta.name,
          displayName: dsl.meta.displayName,
          categoryOid: target,
          description: dsl.meta.description,
          dslJson: dslToJson(dsl),
        }),
      )
    },

    async save(oid, dsl, changeNote, currentKey) {
      const guard = ensureSavable(dsl)
      if (!guard.ok) {
        return guard
      }
      // 后端保存只写 dslJson，不改 key 列；改了 meta.key 会让「部署」在后端被拒，故提前拦下
      if (currentKey && dsl.meta.key !== currentKey) {
        return fail(
          `流程 key 不可在编辑中修改（模板为 ${currentKey}，DSL 中为 ${dsl.meta.key}）；如需新 key 请用「复制/另存」派生`,
        )
      }
      return unwrap(await http.save(oid, { dslJson: dslToJson(dsl), changeNote }))
    },

    async deploy(oid, dsl, version) {
      const guard = ensureSavable(dsl)
      if (!guard.ok) {
        return guard
      }
      const compiled = compileToBpmnXml(dsl)
      if (!compiled.xml || !compiled.xml.trim()) {
        return fail('编译产物为空，无法部署')
      }
      const processId = extractProcessId(compiled.xml)
      if (!processId) {
        return fail('编译产物缺少 <process id="…">，无法部署')
      }
      const deployed = unwrap(
        await http.deploy(oid, { ...(version ? { version } : {}), bpmnXml: compiled.xml }),
      )
      if (!deployed.ok) {
        return deployed
      }
      return ok({ outcome: deployed.data, warnings: compiled.warnings, xml: compiled.xml })
    },

    async versions(oid) {
      return unwrap(await http.versions(oid))
    },

    async loadVersion(oid, version) {
      const fetched = unwrap(await http.version(oid, version))
      if (!fetched.ok) {
        return fetched
      }
      const parsed = dslFromJson(fetched.data?.dslJson)
      if (!parsed.ok) {
        return parsed
      }
      return ok({ version: fetched.data, dsl: parsed.data })
    },

    async copy(oid, key, name) {
      return unwrap(await http.copy(oid, { key, ...(name ? { name } : {}) }))
    },

    async setEnabled(oid, enabled) {
      return unwrap(await http.setEnabled(oid, enabled))
    },

    async removeVersions(oid, versions) {
      // 空选直接拦下：后端同样会拒（"请选择要删除的版本"），没必要白跑一次请求
      if (!versions?.length) {
        return fail('请选择要删除的版本')
      }
      return unwrap(await http.removeVersions(oid, versions))
    },

    compile(dsl, options) {
      const compiled = compileToBpmnXml(dsl, options)
      return ok({
        xml: compiled.xml,
        warnings: compiled.warnings,
        processId: extractProcessId(compiled.xml),
      })
    },
  }
}
