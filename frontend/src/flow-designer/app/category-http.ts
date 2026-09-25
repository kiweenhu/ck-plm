/**
 * 流程分组 · HTTP 适配器（真实接线）
 *
 * <p>唯一职责：把 `@/api` 的具体函数适配成 {@link ./category-repo} 声明的
 * {@link CategoryHttp} 端口 —— 与 `./template-http` 同一套路：
 * 业务规则在仓储层、端点映射在这里，仓储层因此可以在 Node 单测里跑。
 */

import {
  createProcessCategory,
  deleteProcessCategory,
  listProcessCategories,
  moveProcessTemplateCategory,
  updateProcessCategory,
} from '@/api'
import type { ApiEnvelope } from './template-repo'
import type { CategoryHttp, CategorySummary } from './category-repo'

/**
 * 断言为后端统一包装（原因与 `./template-http` 相同：`api/index.js` 的响应拦截器
 * 已把 axios 响应解包为 `{ code, message, data }`，但静态类型上仍是 `AxiosResponse`）。
 */
const asEnvelope = <T>(request: Promise<unknown>): Promise<ApiEnvelope<T>> =>
  request as unknown as Promise<ApiEnvelope<T>>

export function createCategoryHttp(): CategoryHttp {
  return {
    list: () => asEnvelope<CategorySummary[]>(listProcessCategories()),
    create: (payload) => asEnvelope<CategorySummary>(createProcessCategory(payload)),
    update: (oid, payload) => asEnvelope<CategorySummary>(updateProcessCategory(oid, payload)),
    remove: (oid) => asEnvelope<void>(deleteProcessCategory(oid)),
    moveTemplate: (oid, categoryOid) =>
      asEnvelope<unknown>(moveProcessTemplateCategory(oid, categoryOid)),
  }
}
