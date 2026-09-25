/**
 * 流程分组 · 仓储层（分组字典 + 组内模板移动）
 *
 * <p><b>为什么分组要有独立的一层</b>：分组是流程清单的<b>导航骨架</b> ——
 * 用法是「先建分组 → 选中分组 → 在组内设计流程」。因此"有哪些分组"必须是
 * 一份可维护的字典，而不是模板上一个自由输入的文本框：后者会产生
 * 「研发 / 研发部 / 研发中心」这类近义分裂，且改一次名要逐条改模板。
 *
 * <p>本层只收口两条规则，其余交给后端（名称唯一、改名同步模板、删除前占用检查）：
 * <ol>
 *   <li><b>名称规范化与校验</b>（{@link normalizeCategoryName}）：去首尾空格、非空、
 *       长度上限 —— 与后端 DDL 的 {@code VARCHAR(64)} 对齐，避免绕一圈才报错；</li>
 *   <li><b>响应归一</b>：复用模板仓储的 {@link unwrap}（{@code code !== 200} → fail，
 *       且不重复弹提示 —— axios 拦截器已按后端 message 弹过）。</li>
 * </ol>
 *
 * <p>与模板仓储同构：业务规则不 import `@/api`，而是依赖注入 {@link CategoryHttp} 端口，
 * 因此可以在 Node 单测里用假端口覆盖全部规则；真实接线见 `./category-http`。
 */

import { unwrap, type ApiEnvelope, type Result } from './template-repo'

/** 分组名长度上限（与后端 ck_process_category.name 的 VARCHAR(64) 一致） */
export const CATEGORY_NAME_MAX = 64

/**
 * 「未分类」的哨兵值。
 *
 * <p>分组名允许为空字符串（存量模板），故不能用 `''` 表示"全部"，需要一个哨兵。
 * 「未分类」不是分组字典里的一项（没有 oid），而是一个<b>筛选入口</b>：
 * 供存量模板找到并「移动」到正式分组里。分组必填是分组字典引入后才生效的规则。
 */
export const UNCATEGORIZED = '__uncategorized__'

/** 分组（对应后端 ProcessCategory 实体） */
export interface CategorySummary {
  oid: string
  name: string
  sortOrder?: number | null
  description?: string | null
}

/** 新建/修改分组的载荷 */
export interface CategoryPayload {
  name: string
  sortOrder?: number
  description?: string
}

// ==================== 端口 ====================

/** HTTP 端口：由 `./category-http` 用 `@/api` 实现，测试用假实现 */
export interface CategoryHttp {
  list(): Promise<ApiEnvelope<CategorySummary[]>>
  create(payload: CategoryPayload): Promise<ApiEnvelope<CategorySummary>>
  update(oid: string, payload: CategoryPayload): Promise<ApiEnvelope<CategorySummary>>
  remove(oid: string): Promise<ApiEnvelope<void>>
  /** 把某个模板移到指定分组（不产生新版本）；入参是分组 oid */
  moveTemplate(oid: string, categoryOid: string): Promise<ApiEnvelope<unknown>>
}

// ==================== 纯函数（可单测） ====================

const ok = <T>(data: T): Result<T> => ({ ok: true, data })
const fail = (error: string): Result<never> => ({ ok: false, error })

/**
 * 分组名规范化：去首尾空格 + 非空 + 长度上限。
 *
 * <p>守卫放在这里而不是只靠后端：`"研发 "` 与 `"研发"` 在后端是同一个名字（会被判重名），
 * 提前 trim 能让用户少绕一圈；而"名称就是模板的引用键"这件事值得在唯一入口处把关。
 */
export function normalizeCategoryName(name: unknown): Result<string> {
  const trimmed = typeof name === 'string' ? name.trim() : ''
  if (!trimmed) {
    return fail('分组名不能为空')
  }
  if (trimmed.length > CATEGORY_NAME_MAX) {
    return fail(`分组名不能超过 ${CATEGORY_NAME_MAX} 个字符`)
  }
  return ok(trimmed)
}

/**
 * 统计每个分组下的模板数（键为**分组 oid**；无分组的记为 `''`）。
 *
 * <p>只负责数数、<b>不负责裁剪</b>：字典里 0 个模板的分组要原样保留 ——
 * 这正是"先定义分组、再在组内设计流程"的用法，刚建好的空分组必须看得见，
 * 否则用户建完就找不到它（早先的实现在左栏过滤掉了数量为 0 的分类）。
 */
export function countTemplatesByCategoryOid(
  templates: Array<{ categoryOid?: string | null }>,
): Map<string, number> {
  const counts = new Map<string, number>()
  for (const template of templates) {
    const oid = (template.categoryOid ?? '').trim()
    counts.set(oid, (counts.get(oid) ?? 0) + 1)
  }
  return counts
}

// ==================== 仓储 ====================

export interface CategoryRepository {
  list(): Promise<Result<CategorySummary[]>>
  create(name: string, sortOrder?: number, description?: string): Promise<Result<CategorySummary>>
  /** 修改（含改名）：模板按 oid 引用分组，改名不需要同步任何模板 */
  rename(
    oid: string,
    name: string,
    sortOrder?: number,
    description?: string,
  ): Promise<Result<CategorySummary>>
  remove(oid: string): Promise<Result<void>>
  /** 把模板移到指定分组（入参是分组 oid） */
  moveTemplate(oid: string, categoryOid: string): Promise<Result<unknown>>
}

export function createCategoryRepository(http: CategoryHttp): CategoryRepository {
  /** 名称校验 + 载荷组装，三个写入口共用 */
  const payloadOf = (
    name: string,
    sortOrder?: number,
    description?: string,
  ): Result<CategoryPayload> => {
    const normalized = normalizeCategoryName(name)
    if (!normalized.ok) {
      return normalized
    }
    const trimmedDescription = (description ?? '').trim()
    return ok({
      name: normalized.data,
      ...(sortOrder === undefined || sortOrder === null ? {} : { sortOrder }),
      ...(trimmedDescription ? { description: trimmedDescription } : {}),
    })
  }

  return {
    async list() {
      return unwrap(await http.list())
    },

    async create(name, sortOrder, description) {
      const payload = payloadOf(name, sortOrder, description)
      if (!payload.ok) {
        return payload
      }
      return unwrap(await http.create(payload.data))
    },

    async rename(oid, name, sortOrder, description) {
      const payload = payloadOf(name, sortOrder, description)
      if (!payload.ok) {
        return payload
      }
      return unwrap(await http.update(oid, payload.data))
    },

    async remove(oid) {
      return unwrap(await http.remove(oid))
    },

    async moveTemplate(oid, categoryOid) {
      // 目标分组必填（空值会让模板掉出分组）；oid 不做名称校验，只去首尾空格
      const target = (categoryOid ?? '').trim()
      if (!target) {
        return fail('请选择目标分组')
      }
      return unwrap(await http.moveTemplate(oid, target))
    },
  }
}
