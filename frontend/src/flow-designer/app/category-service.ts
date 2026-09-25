/**
 * 流程分组仓储的单例装配（与 `./template-service` 同构）。
 *
 * <p>`category-repo`（业务规则）与 `category-http`（端点映射）是两个可独立替换的模块，
 * 组件只需要拿到装配好的实例，不关心端口实现。
 */

import { createCategoryHttp } from './category-http'
import { createCategoryRepository, type CategoryRepository } from './category-repo'

export const categoryRepository: CategoryRepository = createCategoryRepository(createCategoryHttp())
