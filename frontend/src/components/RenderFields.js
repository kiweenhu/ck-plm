import { h } from 'vue'
import { Textarea, InputNumber, Select, Switch, DatePicker, TreeSelect, Input, Card, Row, Col, Form, Tag, Button, Space } from 'ant-design-vue'
import ImageUploader from './ImageUploader.vue'
import FileUploader from './FileUploader.vue'
import CKFileUploader from './CKFileUploader.vue'
import StageDisplay from './StageDisplay.vue'
import RichTextEditor from './RichTextEditor.vue'
import DataTable from './DataTable.vue'
import DocumentTypeSelect from './DocumentTypeSelect.vue'
import ClassificationSelect from './ClassificationSelect.vue'

/** 去除 HTML 标签，保留纯文本 */
const stripHtml = (html) => {
  if (!html || typeof html !== 'string') return html ?? ''
  return html.replace(/<[^>]*>/g, '').replace(/&nbsp;/g, ' ').replace(/&amp;/g, '&').replace(/&lt;/g, '<').replace(/&gt;/g, '>').replace(/&quot;/g, '"')
}

/** 确保树节点的 title 不为空，fallback 到 value */
function ensureTreeTitle(nodes) {
  if (!nodes) return []
  return nodes.map(node => ({
    ...node,
    title: node.title || node.label || node.value || String(node.value || ''),
    children: node.children?.length ? ensureTreeTitle(node.children) : undefined,
  }))
}

/**
 * 递归渲染表单字段的 render-function 组件。
 * 支持普通字段、group 分组容器、layout-row 布局行容器。
 */
const RenderFields = {
  name: 'RenderFields',
  props: {
    fields: { type: Array, default: () => [] },
    formData: { type: Object, default: () => ({}) },
    productLineTree: { type: Array, default: () => [] },
    productOwnerTree: { type: Array, default: () => [] },
    nodeTypeMap: { type: Object, default: () => ({}) },
    orgTreeData: { type: Array, default: () => [] },
    userOptions: { type: Array, default: () => [] },
    folderTree: { type: Array, default: () => [] },
    stageOptions: { type: Array, default: () => [] },
  },
  emits: ['update', 'table-action'],
  render() {
    if (!this.fields || this.fields.length === 0) return null

    const items = []
    for (const field of this.fields) {
      // ===== 分组容器 =====
      if (field.type === 'group' || field.uiComponent === 'form-group') {
        items.push(
          h(Card, {
            key: field.id || field.fieldName,
            title: field.label || '分组',
            size: 'small',
            style: { marginBottom: '16px' },
          }, {
            default: () => field.children?.length
              ? h(RenderFieldsWrapper, {
                  fields: field.children,
                  formData: this.formData,
                  productLineTree: this.productLineTree,
                  productOwnerTree: this.productOwnerTree,
                  nodeTypeMap: this.nodeTypeMap,
                  orgTreeData: this.orgTreeData,
                  userOptions: this.userOptions,
                  folderTree: this.folderTree,
                  stageOptions: this.stageOptions,
                  onUpdate: (key, val) => this.$emit('update', key, val),
                  'onTable-action': (payload) => this.$emit('table-action', payload),
                })
              : null,
          })
        )
        continue
      }

      // ===== 布局行容器 =====
      if (field.type === 'layout-row' || field.uiComponent === 'layout-row') {
        const cols = field.columns || 2
        const span = Math.floor(24 / cols)
        const rowChildren = (field.children || []).map(child =>
          h(Col, { key: child.id || child.fieldName, span }, {
            default: () => h(RenderFieldsWrapper, {
              fields: [child],
              formData: this.formData,
              productLineTree: this.productLineTree,
              productOwnerTree: this.productOwnerTree,
              nodeTypeMap: this.nodeTypeMap,
              orgTreeData: this.orgTreeData,
              userOptions: this.userOptions,
              folderTree: this.folderTree,
              stageOptions: this.stageOptions,
              onUpdate: (key, val) => this.$emit('update', key, val),
              'onTable-action': (payload) => this.$emit('table-action', payload),
            }),
          })
        )
        items.push(
          h(Row, {
            key: field.id || field.fieldName,
            gutter: 16,
            style: { marginBottom: '8px' },
          }, { default: () => rowChildren })
        )
        continue
      }

      // ===== 数据表格容器 =====
      if (field.type === 'data-table' || field.uiComponent === 'data-table') {
        items.push(this.renderDataTable(field))
        continue
      }

      // ===== 普通字段 =====
      items.push(this.renderFieldItem(field))
    }
    return items
  },
  methods: {
    /**
     * 渲染数据表格组件
     */
    renderDataTable(field) {
      const children = field.children || []
      const bindField = field.dataBindField || field.fieldName
      const dataSource = this.formData[bindField]

      // 构建 DataTable 的 columns 配置
      const columns = children.map(col => ({
        title: col.label || col.fieldName,
        dataIndex: col.fieldName,
        key: col.fieldName,
        width: col.width || undefined,
        sorter: col.sortable || false,
      }))

      // 操作列
      const hasActions = field.hasEdit || field.hasDelete
      if (hasActions) {
        columns.push({
          title: '操作',
          key: 'action',
          width: 120,
          fixed: 'right',
        })
      }

      // 分页配置
      const pagination = field.pagination !== false
        ? { pageSize: field.pageSize || 10, showTotal: (t) => `共 ${t} 条` }
        : false

      // bodyCell 自定义渲染
      const bodyCellRender = (col, record, index) => {
        if (col.key === 'action') {
          return h(Space, { size: 4 }, () => [
            field.hasEdit ? h(Button, {
              type: 'link', size: 'small',
              onClick: () => this.$emit('table-action', { action: 'edit', tableField: field, record, index }),
            }, () => '编辑') : null,
            field.hasDelete ? h(Button, {
              type: 'link', size: 'small', danger: true,
              onClick: () => this.$emit('table-action', { action: 'delete', tableField: field, record, index }),
            }, () => '删除') : null,
          ])
        }
        // 统计列用虚线框标识
        const colDef = children.find(c => c.fieldName === col.key)
        if (colDef?.isCustomColumn) {
          const expr = colDef.customExpression || ''
          const val = record[col.key] ?? ''
          return h('span', {
            style: 'border-bottom:1px dashed #1677ff;color:#1677ff',
            title: expr ? `统计表达式: ${expr}` : '',
          }, val !== null && val !== undefined ? String(val) : '--')
        }
        // 富文本列：在表格中显示纯文本
        if (colDef?.uiComponent === 'rich-text') {
          const val = record[col.key]
          const text = stripHtml(val)
          return h('span', {
            title: text || '',
            style: 'overflow:hidden;text-overflow:ellipsis;white-space:nowrap;max-width:200px;display:inline-block'
          }, text || '--')
        }
        return null // 默认渲染
      }

      return h(Card, {
        key: field.fieldName,
        title: field.label || '数据表格',
        size: 'small',
        style: { marginBottom: '16px' },
        extra: field.hasAdd ? h(Button, {
          size: 'small', type: 'primary',
          onClick: () => this.$emit('table-action', { action: 'add', tableField: field }),
        }, () => '新增') : null,
      }, {
        default: () => h(DataTable, {
          columns,
          dataSource: Array.isArray(dataSource) ? dataSource : [],
          pagination,
          searchable: false,
          showColumnToggle: false,
          enableResize: false,
          rowKey: (r, i) => r.oid || r.id || `row_${i}`,
          scrollX: columns.reduce((s, c) => s + (c.width || 150), 0),
        }, {
          bodyCell: (col, record, index) => bodyCellRender(col, record, index),
        }),
      })
    },

    renderFieldItem(field) {
      const isVertical = field.labelLayout === 'vertical'
      const isRequired = field.required === true
      const isReadonly = field.readonly === true
      const component = this.buildFieldComponent(field, isReadonly)

      return h(Form.Item, {
        key: field.id || field.fieldName,
        label: field.label,
        required: isRequired,
        class: isVertical ? 'df-form-item-vertical' : '',
        style: { marginBottom: '16px' },
      }, { default: () => component })
    },

    buildFieldComponent(field, isReadonly) {
      const fieldName = field.fieldName
      const value = this.formData[fieldName] ?? field.defaultValue ?? ''
      const placeholder = field.placeholder || `请输入${field.label || ''}`
      const commonProps = { value, disabled: isReadonly, placeholder, style: { width: '100%' } }
      const type = field.uiComponent || 'input'

      switch (type) {
        case 'textarea':
          return h(Textarea, {
            ...commonProps, rows: field.rows || 3,
            'onUpdate:value': (val) => this.$emit('update', fieldName, val),
          })

        case 'rich-text':
          return h(RichTextEditor, {
            modelValue: value || '',
            disabled: isReadonly,
            placeholder: placeholder || '请输入内容...',
            'onUpdate:modelValue': (val) => this.$emit('update', fieldName, val),
          })

        case 'input-number':
          return h(InputNumber, {
            ...commonProps, min: field.min, max: field.max, step: field.step,
            'onUpdate:value': (val) => this.$emit('update', fieldName, val),
          })

        case 'select': {
          let options = []
          if (field.options) {
            try {
              options = typeof field.options === 'string' ? JSON.parse(field.options) : field.options
            } catch { options = [] }
          }
          return h(Select, {
            ...commonProps, allowClear: true, showSearch: true,
            options: options.map(o => ({ label: o.label || o.name || o.title || o, value: o.value || o.id || o })),
            filterOption: (input, option) => (option.label || '').toLowerCase().includes(input.toLowerCase()),
            'onUpdate:value': (val) => this.$emit('update', fieldName, val),
          })
        }

        case 'switch':
          return h(Switch, {
            value, disabled: isReadonly, checked: value === true || value === 'true',
            'onUpdate:checked': (val) => this.$emit('update', fieldName, val),
          })

        case 'datepicker':
          return h(DatePicker, {
            ...commonProps, showTime: field.showTime || false,
            format: field.format || 'YYYY-MM-DD',
            'onUpdate:value': (val) => this.$emit('update', fieldName, val),
          })

        case 'tree-select':
          return h(TreeSelect, {
            ...commonProps, treeData: field.treeData || [],
            treeDefaultExpandAll: true, showSearch: true, treeNodeFilterProp: 'title', allowClear: true,
            fieldNames: field.fieldNames || { label: 'name', key: 'oid', value: 'oid', children: 'children' },
            'onUpdate:value': (val) => this.$emit('update', fieldName, val),
          })

        case 'product-line-select':
          return h(TreeSelect, {
            ...commonProps, treeData: this.productLineTree || [],
            treeDefaultExpandAll: true, showSearch: true, treeNodeFilterProp: 'title', allowClear: true,
            fieldNames: { label: 'title', key: 'key', value: 'value', children: 'children' },
            placeholder: placeholder || '请选择所属产品系列',
            'onUpdate:value': (val) => this.$emit('update', fieldName, val),
          })

        case 'product-select': {
          // 优先使用组合树（含系列+型号），回退到纯系列树
          const tree = (this.productOwnerTree?.length ? this.productOwnerTree : this.productLineTree) || []
          return h(TreeSelect, {
            ...commonProps, treeData: tree,
            treeDefaultExpandAll: true, showSearch: true, treeNodeFilterProp: 'title', allowClear: true,
            fieldNames: { label: 'title', key: 'key', value: 'value', children: 'children' },
            placeholder: placeholder || '请选择所属产品',
            'onUpdate:value': (val) => {
              this.$emit('update', fieldName, val)
              if (val) {
                const nt = this.nodeTypeMap?.[val] || 'PRODUCT_LINE'
                this.$emit('update', 'containerType', nt)
              }
            },
          })
        }

        case 'org-selector':
          return h(TreeSelect, {
            ...commonProps, treeData: this.orgTreeData || [],
            treeDefaultExpandAll: true, showSearch: true, treeNodeFilterProp: 'title', allowClear: true,
            placeholder: placeholder || '选择组织',
            fieldNames: { label: 'name', key: 'oid', value: 'oid', children: 'children' },
            'onUpdate:value': (val) => this.$emit('update', fieldName, val),
          })

        case 'user-selector':
          return h(Select, {
            ...commonProps, showSearch: true, allowClear: true,
            options: this.userOptions || [],
            placeholder: placeholder || '选择用户',
            filterOption: (input, option) => (option.label || '').toLowerCase().includes(input.toLowerCase()),
            'onUpdate:value': (val) => this.$emit('update', fieldName, val),
          })

        case 'user-multi-selector': {
          const multiValue = Array.isArray(value) ? value : (value ? [value] : [])
          return h(Select, {
            ...commonProps, value: multiValue, mode: 'multiple', showSearch: true, allowClear: true,
            options: this.userOptions || [],
            placeholder: placeholder || '选择用户（可多选）',
            filterOption: (input, option) => (option.label || '').toLowerCase().includes(input.toLowerCase()),
            'onUpdate:value': (val) => this.$emit('update', fieldName, val || []),
          })
        }

        case 'image-upload':
          return h(ImageUploader, {
            value,
            disabled: isReadonly,
            'onUpdate:value': (val) => this.$emit('update', fieldName, val),
          })

        case 'file-upload':
          return h(FileUploader, {
            value,
            disabled: isReadonly,
            'onUpdate:value': (val) => this.$emit('update', fieldName, val),
          })

        case 'ckfile-upload':
          return h(CKFileUploader, {
            value,
            disabled: isReadonly,
            'onUpdate:value': (val) => this.$emit('update', fieldName, val),
          })

        case 'document-type-select':
          return h(DocumentTypeSelect, {
            modelValue: value,
            disabled: isReadonly,
            placeholder: placeholder || '请选择文档类型',
            'onUpdate:modelValue': (val) => this.$emit('update', fieldName, val),
          })

        case 'stage-select':
          return h(Select, {
            ...commonProps, allowClear: false, showSearch: false,
            options: this.stageOptions?.length ? this.stageOptions : [],
            placeholder: placeholder || '请选择所属研发阶段',
            fieldNames: { label: 'label', value: 'value' },
            'onUpdate:value': (val) => this.$emit('update', fieldName, val),
          })

        case 'classification-select':
          return h(ClassificationSelect, {
            modelValue: value,
            disabled: isReadonly,
            placeholder: placeholder || '请选择分类',
            allowClear: true,
            'onUpdate:modelValue': (val) => this.$emit('update', fieldName, val),
          })

        case 'folder-select': {
          // 确保每个节点有可显示的 title
          const safeTree = ensureTreeTitle(this.folderTree || [])
          return h(TreeSelect, {
            ...commonProps, treeData: safeTree,
            treeDefaultExpandAll: true, showSearch: true, treeNodeFilterProp: 'title', allowClear: true,
            fieldNames: { label: 'title', key: 'key', value: 'value', children: 'children' },
            placeholder: placeholder || '请选择所属文件夹',
            'onUpdate:value': (val) => this.$emit('update', fieldName, val),
          })
        }

        case 'input':
        default:
          return h(Input, {
            ...commonProps, type: field.inputType || 'text',
            'onUpdate:value': (val) => this.$emit('update', fieldName, val),
          })
      }
    },
  },
}

/**
 * 自引用包装器，使 RenderFields 可以递归调用自身。
 * 这在 Options API render 函数中通过 this 引用自身名称来工作。
 */
const RenderFieldsWrapper = {
  name: 'RenderFieldsWrapper',
  ...RenderFields,
}

export default RenderFields
