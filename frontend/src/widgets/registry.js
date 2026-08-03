/**
 * 控件注册表 (Widget Registry)
 *
 * 提供从控件 type 创建 field 对象、查找控件元数据等工厂方法。
 * PageDesigner 拖放时调用 createFieldFromWidget() 即可获取标准化 field。
 */

import { getWidgetByType, getWidgetIcon, isContainerWidget } from './catalog'

/**
 * 从控件 type 创建默认 field 对象
 *
 * @param {string} widgetType - 控件 type，如 'input' | 'select' | 'form-group'
 * @param {object} overrides   - 覆盖默认值的属性
 * @returns {object} 标准 field 对象（或 group 对象）
 */
export function createFieldFromWidget(widgetType, overrides = {}) {
  const widget = getWidgetByType(widgetType)

  // 布局行容器
  if (widget && widget.special === 'layout-row') {
    return {
      type: 'layout-row',
      fieldName: 'row_' + Date.now(),
      label: widget.label,
      columns: 2,
      children: [],
      ...overrides
    }
  }

  // 分组容器
  if (widget && widget.special === 'group') {
    return {
      type: 'group',
      fieldName: 'group_' + Date.now(),
      label: widget.label,
      collapsed: false,
      children: [],
      ...overrides
    }
  }

  // 数据表格容器
  if (widget && widget.special === 'data-table') {
    return {
      type: 'data-table',
      fieldName: 'data_table_' + Date.now(),
      label: widget.label,
      source: 'CUSTOM',
      uiComponent: 'data-table',
      dataBindField: [],
      hasEdit: true,
      hasDelete: true,
      hasAdd: false,
      pagination: true,
      pageSize: 10,
      children: [],
      ...overrides
    }
  }

  // 普通控件
  const label = widget?.label || widgetType
  const dataType = widget?.dataType || 'STRING'
  const bindFields = widget?.bindFields
  // 有 bindFields 的控件使用固定字段名，无则用时间戳生成
  const fieldName = bindFields?.length
    ? bindFields[0]
    : (widgetType || 'field') + '_' + Date.now()

  return {
    fieldName,
    label,
    source: 'CUSTOM',
    dataType,
    uiComponent: widgetType,
    bindFields: bindFields || null,
    width: 150,
    required: false,
    sortable: false,
    fixed: undefined,
    readonly: false,
    defaultValue: '',
    placeholder: '',
    labelLayout: 'horizontal',
    description: widget?.description || '',
    ...widget?.defaults,
    ...overrides
  }
}

/**
 * 创建控件所需的所有关联字段（含复合字段的辅助字段）。
 * 例如 product-line-select 需要 [containerOid, containerType] 两个字段。
 */
export function createCompanionFields(widgetType) {
  const widget = getWidgetByType(widgetType)
  const bindFields = widget?.bindFields
  if (!bindFields || bindFields.length <= 1) return []
  // 跳过第一个（主字段），返回其余关联字段
  return bindFields.slice(1).map(fn => ({
    fieldName: fn,
    label: fn,
    source: 'CUSTOM',
    dataType: 'STRING',
    uiComponent: 'input',
    readonly: true,
    hidden: true,
    defaultValue: '',
    placeholder: ''
  }))
}

/**
 * 获取控件标签名
 */
export function getWidgetLabel(type) {
  const w = getWidgetByType(type)
  return w?.label || type
}

export { getWidgetByType, getWidgetIcon, isContainerWidget }
