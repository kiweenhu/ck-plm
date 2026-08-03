/**
 * Widget Component Library — 统一入口
 *
 * 用法:
 *   import {
 *     WidgetToolbox, WidgetSelector, WidgetPreview,
 *     getWidgetByType, getWidgetIcon, createFieldFromWidget,
 *     renderWidgetPreview, isContainerWidget
 *   } from '@/widgets'
 */

// 组件
export { default as WidgetToolbox } from './components/WidgetToolbox.vue'
export { default as WidgetSelector } from './components/WidgetSelector.vue'
export { default as WidgetPreview } from './components/WidgetPreview.vue'

// 数据
export { WIDGETS, CATEGORIES, getWidgetByType, getWidgetIcon, isContainerWidget, getFieldWidgetTypes } from './catalog'

// 工具
export { createFieldFromWidget, getWidgetLabel, createCompanionFields } from './registry'

// 渲染
export { renderWidgetPreview } from './renderer'
