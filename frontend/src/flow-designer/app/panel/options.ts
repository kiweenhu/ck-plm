/**
 * CK-PLM 流程设计器 · 字段选项来源
 *
 * <p>把「选项从哪来」与「字段怎么声明」分开：字段声明只写 `optionsSource: 'ROLES'`，
 * 具体接口与响应形态的适配收拢在本文件，接口变更不会渗透到字段声明里。
 *
 * <p>内置模块级缓存 + 并发去重：同一来源在一次编辑会话里只请求一次
 * （角色/用户/对象类型都是低频变更的主数据）。
 */

import {
  getAllUsers,
  getAutomationServices,
  getLifecycleTemplates,
  getRoleMembers,
  getRoles,
  getTargetSystems,
  getTypeDefinitions,
} from '@/api'
import { AUTH_TYPE_LABEL, HTTP_METHOD_LABEL } from '@flow-dsl-core'
import type { OptionItem, OptionSourceId } from './field-schema'

const cache = new Map<OptionSourceId, OptionItem[]>()
const inflight = new Map<OptionSourceId, Promise<OptionItem[]>>()

/** 角色成员缓存：角色 code 组合 → 成员选项 */
const roleMemberCache = new Map<string, OptionItem[]>()
const roleMemberInflight = new Map<string, Promise<OptionItem[]>>()

/** 同步读取已缓存的选项（未加载时返回 undefined，供渲染器先渲染空列表） */
export function cachedOptions(id: OptionSourceId): OptionItem[] | undefined {
  return cache.get(id)
}

/** 预置（供测试或 SSR 注入） */
export function primeOptions(id: OptionSourceId, items: OptionItem[]): void {
  cache.set(id, items)
}

export function clearOptionCache(): void {
  cache.clear()
  inflight.clear()
  roleMemberCache.clear()
  roleMemberInflight.clear()
}

/**
 * 加载「一组角色的成员」。
 *
 * <p>用于「设置审批人」任务表单：槽位的候选范围应限定为**该活动指定的角色成员**
 * （用户口径："设置后面审批节点、会签节点、办理任务节点指定角色的成员"）。
 *
 * <p>接口按 **roleOid** 取成员（`GET /roles/{oid}/members`），而 DSL 里存的是角色
 * **code**（稳定标识），所以先用角色列表建立 code → oid 映射。
 *
 * <p>按 code 组合缓存 + 并发去重：同一个角色的成员只请求一次。
 */
export async function loadRoleMembers(roleCodes: string[]): Promise<OptionItem[]> {
  const codes = Array.from(new Set(roleCodes.filter(Boolean))).sort()
  if (codes.length === 0) {
    return []
  }
  const key = codes.join(',')
  const cached = roleMemberCache.get(key)
  if (cached) {
    return cached
  }
  const pending = roleMemberInflight.get(key)
  if (pending) {
    return pending
  }
  const task = fetchRoleMembers(codes)
    .then((items) => {
      roleMemberCache.set(key, items)
      return items
    })
    .catch(() => {
      // 主数据接口不可用时降级为空列表：表单结构照常展示，只是候选暂时拿不到
      roleMemberCache.set(key, [])
      return [] as OptionItem[]
    })
    .finally(() => {
      roleMemberInflight.delete(key)
    })
  roleMemberInflight.set(key, task)
  return task
}

async function fetchRoleMembers(codes: string[]): Promise<OptionItem[]> {
  const roles = unwrap(await getRoles())
  const oidByCode = new Map<string, string>()
  for (const role of roles) {
    const record = role as Record<string, unknown>
    const code = record.code ?? record.oid ?? record.id
    const oid = record.oid ?? record.id
    if (code !== undefined && code !== null && oid !== undefined && oid !== null) {
      oidByCode.set(String(code), String(oid))
    }
  }
  const members = new Map<string, OptionItem>()
  for (const code of codes) {
    const oid = oidByCode.get(code)
    if (!oid) {
      continue
    }
    for (const member of unwrap(await getRoleMembers(oid))) {
      // 角色成员也是"人"，同样要用 username 作值（见 userOption）
      const option = userOption(member)
      if (option) {
        members.set(option.value, option)
      }
    }
  }
  return Array.from(members.values())
}

/** 从常见响应形态里取出数组（后端统一 `{ code, data }`） */
function unwrap(response: unknown): unknown[] {
  if (Array.isArray(response)) {
    return response
  }
  const data = (response as { data?: unknown } | null)?.data
  if (Array.isArray(data)) {
    return data
  }
  // 分页形态：{ data: { records: [] } } / { data: { list: [] } }
  const nested = (data as { records?: unknown; list?: unknown } | undefined) ?? undefined
  if (nested && Array.isArray(nested.records)) {
    return nested.records as unknown[]
  }
  if (nested && Array.isArray(nested.list)) {
    return nested.list as unknown[]
  }
  return []
}

/**
 * 适配为选项项。
 *
 * <p>取值优先 `code`（业务标识，稳定），回落 `oid`；显示名优先 `name`，
 * 依次回落 `displayName` / `label` / `code` —— 兼容各主数据模块的既有字段命名，
 * 避免因某个模块字段名不同而整片下拉为空。
 *
 * <p><b>人员不要用这个</b>：见 {@link userOption}。
 */
function toOption(item: unknown): OptionItem | null {
  if (!item || typeof item !== 'object') {
    return null
  }
  const record = item as Record<string, unknown>
  const value = record.code ?? record.oid ?? record.id ?? record.value
  if (value === undefined || value === null || value === '') {
    return null
  }
  const label =
    record.name ?? record.displayName ?? record.label ?? record.title ?? record.username ?? value
  return { value: String(value), label: String(label) }
}

/**
 * 生命周期状态选项：value = **状态 code**、label = **状态名**。
 *
 * <p>不能走通用 {@link toOption}：状态引用对象的字段名是 `statusCode` / `statusDisplayName`
 * （见 {@code LifecycleTemplateStatusRef}），通用适配两个都认不出 —— 它会退回 `oid` 当取值、
 * 又因为没有 `name` 而把 oid 当显示名。结果是下拉里整片 UUID：设计者根本看不出"这行是已发布"，
 * 而编译产物里要的是 state code。
 *
 * <p>label 兜底用 code（拼不出名字时至少还能认出是哪个状态），但**绝不用 oid**
 * —— oid 是库里的内部标识，对设计者没有任何意义。
 */
export function lifecycleStateOption(item: unknown): OptionItem | null {
  if (!item || typeof item !== 'object') {
    return null
  }
  const record = item as Record<string, unknown>
  const code = record.statusCode ?? record.code
  if (code === undefined || code === null || code === '') {
    return null
  }
  const label = record.statusDisplayName ?? record.name ?? String(code)
  return { value: String(code), label: String(label) }
}

/**
 * 生命周期模板 → 状态选项（按 code 去重）。
 *
 * <p>去重是必要的：多个模板往往共用同一批状态（草稿 / 工作中 / 已发布），
 * 不去重下拉里会出现好几行一模一样的；保留首次出现的顺序（模板内的状态顺序即业务顺序）。
 */
export function lifecycleStateOptions(templates: unknown[]): OptionItem[] {
  const byCode = new Map<string, OptionItem>()
  for (const template of templates) {
    const states = (template as { states?: unknown })?.states
    if (!Array.isArray(states)) {
      continue
    }
    for (const state of states) {
      const option = lifecycleStateOption(state)
      if (option && !byCode.has(option.value)) {
        byCode.set(option.value, option)
      }
    }
  }
  return Array.from(byCode.values())
}

/**
 * 人员选项：value = **用户名（username）**。
 *
 * <p>Flowable 侧的 userId 口径就是 {@code ck_user.username}（见 ProcessIdentitySupport 的类注释）：
 * {@code assignee} / {@code candidateUsers} 都拿它去匹配。这里若给 oid，任务建出来就"谁都不认"——
 * 待办查不到、也认领不了。这不是假设：平台确实这么坏过一轮（「设置审批人」按 oid 指派下游，
 * 责任人在进度里看得到自己，待办里却什么都没有）。
 */
function userOption(item: unknown): OptionItem | null {
  if (!item || typeof item !== 'object') {
    return null
  }
  const record = item as Record<string, unknown>
  const username = record.username ?? record.code ?? record.value
  if (username === undefined || username === null || username === '') {
    return null
  }
  const label = record.displayName ?? record.name ?? record.label ?? username
  return { value: String(username), label: String(label) }
}

/** 静态清单 → 下拉选项（与 dsl-core 的 *_LABEL 同源，避免两处各写一份文案） */
function staticOptions(labels: Record<string, string>): OptionItem[] {
  return Object.entries(labels).map(([value, label]) => ({ value, label }))
}

async function fetchOptions(id: OptionSourceId): Promise<OptionItem[]> {
  switch (id) {
    case 'ROLES':
      return unwrap(await getRoles()).map(toOption).filter(Boolean) as OptionItem[]
    case 'USERS':
      return unwrap(await getAllUsers({})).map(userOption).filter(Boolean) as OptionItem[]
    case 'OBJECT_TYPES':
      return unwrap(await getTypeDefinitions({})).map(toOption).filter(Boolean) as OptionItem[]
    case 'LIFECYCLE_STATES':
      // 生命周期模板 → 其状态清单（取值=状态 code、显示=状态名，见 lifecycleStateOptions）
      return lifecycleStateOptions(unwrap(await getLifecycleTemplates()))
    // 静态清单：不取数，直接来自 dsl-core 的取值表（认证方式 / HTTP 方法）
    case 'AUTH_TYPE':
      return staticOptions(AUTH_TYPE_LABEL)
    case 'HTTP_METHOD':
      return staticOptions(HTTP_METHOD_LABEL)
    case 'TARGET_SYSTEMS': {
      // 目标系统注册表（管理员维护）。值取 code：后端按 code + 租户取系统；
      // 显示名带上编码，便于设计者与管理员对账。停用的系统不出现在候选里
      // （引用它的存量节点会照旧显示原值，运行期由后端给出"已停用"的明确报错）
      const systems = unwrap(await getTargetSystems()) as Array<{
        code?: string
        name?: string
        enabled?: boolean
      }>
      return systems
        .filter((system) => !!system.code && system.enabled !== false)
        .map((system) => ({
          value: String(system.code),
          label: `${system.name ?? system.code}（${system.code}）`,
        }))
    }
    default:
      return []
  }
}

/**
 * 后端已注册的自动服务（函数）清单。
 *
 * <p>候选清单的"真相"在后端（{@code PlmServiceHandler} 由 Spring 收集），前端白名单只负责
 * 显示名与参数声明。两份清单必然漂移，而漂移的表现是最难查的那种：函数部署好了、
 * 设计器下拉里却选不到。
 */
let automationServices: OptionItem[] = []

/** 拉一次后端清单。失败降级为空数组：面板照旧显示前端声明的服务，不阻断设计 */
export async function loadAutomationServices(): Promise<OptionItem[]> {
  try {
    const list = unwrap(await getAutomationServices()) as Array<{ id?: string; label?: string }>
    automationServices = list
      .filter((item) => !!item?.id)
      .map((item) => ({ value: String(item.id), label: item.label || String(item.id) }))
  } catch {
    automationServices = []
  }
  return automationServices
}

/**
 * 合并成候选清单：<b>以前的声明为准</b>（顺序与显示名不变，面板要按声明渲染参数控件），
 * 后端多出来的追加在后面。纯函数，便于单测。
 */
export function mergeServiceOptions(
  declared: OptionItem[],
  fromBackend: OptionItem[],
): OptionItem[] {
  const known = new Set(declared.map((item) => item.value))
  return [...declared, ...fromBackend.filter((item) => !known.has(item.value))]
}

/** 加载选项（带缓存与并发去重；失败时返回空数组，不阻断面板渲染） */
export async function loadOptions(id: OptionSourceId): Promise<OptionItem[]> {
  const cached = cache.get(id)
  if (cached) {
    return cached
  }
  const pending = inflight.get(id)
  if (pending) {
    return pending
  }
  const task = fetchOptions(id)
    .then((items) => {
      cache.set(id, items)
      return items
    })
    .catch(() => {
      // 主数据接口不可用时降级为空列表：面板仍可编辑其它字段
      cache.set(id, [])
      return [] as OptionItem[]
    })
    .finally(() => {
      inflight.delete(id)
    })
  inflight.set(id, task)
  return task
}
