/**
 * 流程模板仓储的单例装配。
 *
 * <p>`template-repo`（业务规则）与 `template-http`（端点映射）是两个可独立替换的模块，
 * 组件只需要拿到装配好的实例，不关心端口实现。
 */

import { createTemplateHttp } from './template-http'
import { createTemplateRepository, type TemplateRepository } from './template-repo'

export const templateRepository: TemplateRepository = createTemplateRepository(createTemplateHttp())
