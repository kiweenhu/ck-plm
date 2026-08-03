/**
 * Activiti 7 建模器扩展模块统一入口
 */
export { default as activitiModdleDescriptor } from './activiti.json'
export { default as ActivitiPropertiesProvider } from './ActivitiPropertiesProvider'

// bpmn-js additionalModules 使用的 DI 模块描述符
import ActivitiPropertiesProvider from './ActivitiPropertiesProvider'

export const ActivitiPropertiesProviderModule = {
  __init__: ['activitiPropertiesProvider'],
  activitiPropertiesProvider: ['type', ActivitiPropertiesProvider]
}
