/**
 * 控件目录 (Widget Catalog)
 *
 * 中央化管理所有可用控件的定义。
 * 新增控件只需在此文件中添加条目，无需修改 PageDesigner 或其他组件。
 *
 * 每个控件条目：
 *   type         - 唯一标识，对应 uiComponent
 *   label        - 中文显示名称
 *   icon         - 在工具箱中展示的图标（shallowRef 包裹的组件）
 *   category     - 所属分类 key
 *   dataType     - 默认数据类型
 *   defaults     - 拖入画布时的默认属性（可与系统默认合并）
 */

import { shallowRef } from 'vue'
import {
  FontSizeOutlined, FileTextOutlined, NumberOutlined,
  OrderedListOutlined, CheckSquareOutlined, CalendarOutlined,
  ApartmentOutlined, UserOutlined, TeamOutlined, BlockOutlined,
  BuildOutlined, InsertRowBelowOutlined, ClusterOutlined,
  PictureOutlined, HighlightOutlined, TableOutlined,
  FolderOutlined, PaperClipOutlined, ExperimentOutlined, CloudUploadOutlined,
  TagOutlined
} from '@ant-design/icons-vue'

// ==================== 控件定义 ====================

export const WIDGETS = [
  // ---------- 输入控件 ----------
  {
    type: 'input',
    label: '输入框',
    category: 'input',
    icon: shallowRef(FontSizeOutlined),
    dataType: 'STRING',
    description: '单行文本输入框，适用于短文本内容',
    defaults: { uiComponent: 'input', required: false }
  },
  {
    type: 'textarea',
    label: '文本域',
    category: 'input',
    icon: shallowRef(FileTextOutlined),
    dataType: 'LONGTEXT',
    description: '多行文本输入框，适用于描述、备注等长文本',
    defaults: { uiComponent: 'textarea', required: false }
  },
  {
    type: 'rich-text',
    label: '富文本',
    category: 'input',
    icon: shallowRef(HighlightOutlined),
    dataType: 'LONGTEXT',
    description: '富文本编辑器，支持文字排版、图片、链接等丰富内容',
    defaults: { uiComponent: 'rich-text', required: false }
  },
  {
    type: 'input-number',
    label: '数字框',
    category: 'input',
    icon: shallowRef(NumberOutlined),
    dataType: 'NUMBER',
    description: '数值输入框，支持 min/max/step 约束',
    defaults: { uiComponent: 'input-number', required: false }
  },

  // ---------- 选择控件 ----------
  {
    type: 'select',
    label: '下拉框',
    category: 'select',
    icon: shallowRef(OrderedListOutlined),
    dataType: 'SELECT',
    description: '单选下拉框，选项需自行配置 JSON 列表',
    defaults: { uiComponent: 'select', required: false }
  },
  {
    type: 'switch',
    label: '开关',
    category: 'select',
    icon: shallowRef(CheckSquareOutlined),
    dataType: 'BOOLEAN',
    description: '布尔值开关，用于是/否、启用/禁用类型的字段',
    defaults: { uiComponent: 'switch', required: false }
  },
  {
    type: 'tree-select',
    label: '树选择',
    category: 'select',
    icon: shallowRef(ApartmentOutlined),
    dataType: 'STRING',
    description: '树形结构选择器，适合组织和层级数据选择',
    defaults: { uiComponent: 'tree-select', required: false }
  },

  // ---------- 日期时间 ----------
  {
    type: 'datepicker',
    label: '日期选择',
    category: 'date',
    icon: shallowRef(CalendarOutlined),
    dataType: 'DATE',
    description: '日期/时间选择器，支持 YYYY-MM-DD 或精确到秒',
    defaults: { uiComponent: 'datepicker', required: false }
  },

  // ---------- 业务组件 ----------
  {
    type: 'user-selector',
    label: '用户选择',
    category: 'system',
    icon: shallowRef(UserOutlined),
    dataType: 'STRING',
    description: '系统用户下拉搜索选择，用于负责人、经办人等字段',
    defaults: { uiComponent: 'user-selector', required: false }
  },
  {
    type: 'user-multi-selector',
    label: '用户多选',
    category: 'system',
    icon: shallowRef(TeamOutlined),
    dataType: 'ARRAY',
    description: '系统用户多选搜索选择，用于协作人、审批人等多用户字段',
    defaults: { uiComponent: 'user-multi-selector', required: false }
  },
  {
    type: 'org-selector',
    label: '组织选择',
    category: 'system',
    icon: shallowRef(TeamOutlined),
    dataType: 'STRING',
    description: '组织架构树形选择器，用于选择部门或团队',
    defaults: { uiComponent: 'org-selector', required: false }
  },
  {
    type: 'product-line-select',
    label: '所属产品系列',
    category: 'system',
    icon: shallowRef(ClusterOutlined),
    dataType: 'STRING',
    description: '产品系列树形选择（仅系列层级），用于指定父级系列归属',
    bindFields: ['parentOid'],
    defaults: { uiComponent: 'product-line-select', required: false, placeholder: '请选择所属产品系列' }
  },
  {
    type: 'product-select',
    label: '所属产品',
    category: 'system',
    icon: shallowRef(ClusterOutlined),
    dataType: 'STRING',
    description: '产品系列/型号树形选择，选中自动设 containerType=PRODUCT_LINE 或 PRODUCT_MODEL',
    bindFields: ['containerOid', 'containerType'],
    defaults: { uiComponent: 'product-select', required: false, placeholder: '请选择所属产品' }
  },
  {
    type: 'image-upload',
    label: '图片上传',
    category: 'media',
    icon: shallowRef(PictureOutlined),
    dataType: 'STRING',
    description: '上传图片至 Media 实体（图片空间），支持直接上传或从产品图册选择',
    bindFields: ['thumbnail'],
    defaults: { uiComponent: 'image-upload', required: false }
  },
  {
    type: 'file-upload',
    label: '附件上传',
    category: 'media',
    icon: shallowRef(PaperClipOutlined),
    dataType: 'STRING',
    description: '上传附件至 CKAttachment 实体，存储文件元信息到业务字段',
    bindFields: ['attachmentOid'],
    defaults: { uiComponent: 'file-upload', required: false }
  },
  {
    type: 'ckfile-upload',
    label: '主文档',
    category: 'media',
    icon: shallowRef(CloudUploadOutlined),
    dataType: 'STRING',
    description: 'CKFile 主文档上传，支持本地上传和网络 URL 两种来源',
    bindFields: ['ckfileOid'],
    defaults: { uiComponent: 'ckfile-upload', required: false, placeholder: '上传主文档或录入URL' }
  },
  {
    type: 'document-type-select',
    label: '文档类型',
    category: 'system',
    icon: shallowRef(FolderOutlined),
    dataType: 'STRING',
    description: 'TypeDefinition 类型树形选择，用于关联文档的类型定义',
    bindFields: ['typeDefinitionCode'],
    defaults: { uiComponent: 'document-type-select', required: false, placeholder: '请选择文档类型' }
  },
  {
    type: 'folder-select',
    label: '所属文件夹',
    category: 'system',
    icon: shallowRef(FolderOutlined),
    dataType: 'STRING',
    description: '文件夹树形选择器，选择文档所属的过程资料目录',
    bindFields: ['folderOid'],
    defaults: { uiComponent: 'folder-select', required: false, placeholder: '请选择所属文件夹' }
  },
  {
    type: 'stage-select',
    label: '所属研发阶段',
    category: 'system',
    icon: shallowRef(ExperimentOutlined),
    dataType: 'STRING',
    description: '研发阶段下拉选择，根据当前产品线/型号的可见阶段动态加载选项',
    bindFields: ['stageOid'],
    defaults: { uiComponent: 'stage-select', required: true, placeholder: '请选择所属研发阶段' }
  },
  {
    type: 'classification-select',
    label: '分类选择',
    category: 'system',
    icon: shallowRef(TagOutlined),
    dataType: 'STRING',
    description: '分类树形选择器，选择分类节点后回传 oid 和 code',
    bindFields: ['classificationOid'],
    defaults: { uiComponent: 'classification-select', required: false, placeholder: '请选择分类' }
  },
  {
    type: 'form-group',
    label: '分组',
    category: 'layout',
    icon: shallowRef(BlockOutlined),
    dataType: null,
    special: 'group',
    description: '表单分组容器，用于将相关字段归类收纳',
    defaults: {}
  },
  {
    type: 'layout-row',
    label: '布局行',
    category: 'layout',
    icon: shallowRef(InsertRowBelowOutlined),
    dataType: null,
    special: 'layout-row',
    description: '布局行组件控制页面布局的显示列数',
    defaults: { columns: 2, children: [] }
  },
  {
    type: 'data-table',
    label: '数据表格',
    category: 'data',
    icon: shallowRef(TableOutlined),
    dataType: null,
    special: 'data-table',
    description: '内嵌数据表格容器，支持 CRUD 行操作、分页、排序',
    defaults: {
      hasEdit: true, hasDelete: true, hasAdd: false,
      pagination: true, pageSize: 10,
      children: []
    }
  }
]

// 默认的 fallback 图标
export const FALLBACK_ICON = shallowRef(BuildOutlined)

// ==================== 控件分类 ====================

export const CATEGORIES = [
  { key: 'input', label: '输入控件', collapsed: false },
  { key: 'select', label: '选择控件', collapsed: false },
  { key: 'date', label: '日期时间', collapsed: true },
  { key: 'media', label: '媒体控件', collapsed: false },
  { key: 'data', label: '数据展示', collapsed: false },
  { key: 'layout', label: '布局容器', collapsed: false },
  { key: 'system', label: '业务组件', collapsed: false }
]

// ==================== 快速查找映射表（性能优化） ====================

/** type → widget 定义 */
export const widgetByType = Object.fromEntries(
  WIDGETS.map(w => [w.type, w])
)

/** type → 图标组件引用 */
export const widgetIconByType = Object.fromEntries(
  WIDGETS.map(w => [w.type, w.icon])
)

// ==================== 工具函数 ====================

/**
 * 按分类组织控件列表（含折叠状态）
 * 用于工具箱 UI 渲染
 */
export function getCategorizedWidgets() {
  return CATEGORIES.map(cat => ({
    ...cat,
    widgets: WIDGETS.filter(w => w.category === cat.key)
  }))
}

/**
 * 根据 type 获取控件定义，找不到返回 null
 */
export function getWidgetByType(type) {
  return widgetByType[type] || null
}

/**
 * 根据 type 获取图标 shallowRef
 */
export function getWidgetIcon(type) {
  return widgetIconByType[type] || FALLBACK_ICON
}

/**
 * 判断是否为容器类控件（如分组、布局行）
 */
export function isContainerWidget(type) {
  const w = widgetByType[type]
  return w?.special === 'group' || w?.special === 'layout-row' || w?.special === 'data-table'
}

/**
 * 获取所有可控件的 type 列表（用于下拉选择等场景）
 * 排除容器控件
 */
export function getFieldWidgetTypes() {
  return WIDGETS.filter(w => !w.special).map(w => w.type)
}
