/**
 * 控件预览渲染器 (Widget Preview Renderer)
 *
 * 根据控件 type 和模式，使用 Vue h() 渲染预览态的控件外观。
 * 设计模式下渲染禁用态，预览模式下渲染可用态。
 *
 * 扩展方式：新增控件时在此 switch 中添加对应 case 即可。
 */

import { h } from 'vue'
import { Input, Select, DatePicker, InputNumber, Switch, TreeSelect, Button, Table } from 'ant-design-vue'
import { UploadOutlined, TableOutlined } from '@ant-design/icons-vue'
import { CAD_TOOL_OPTIONS } from './cadTools'

/**
 * 渲染单个控件的预览外观
 *
 * @param {string}  type            - 控件 type
 * @param {string}  mode            - 'design' | 'preview'
 * @param {object}  [options]       - 可选上下文
 * @param {Array}   [options.userOptions]    - 用户下拉选项
 * @param {Array}   [options.orgTreeData]    - 组织树数据
 * @returns {VNode}
 */
export function renderWidgetPreview(type, mode = 'design', options = {}) {
  const isPreview = mode === 'preview'

  switch (type) {
    case 'textarea':
      if (isPreview) {
        return h('div', {
          class: 'md-preview-textarea-preview',
          style: 'min-height:60px;display:flex;align-items:center;padding:4px 8px;color:#bfbfbf;font-size:12px'
        }, '多行文本输入...')
      }
      return h('div', {
        class: 'md-preview-textarea'
      }, '')

    case 'rich-text':
      if (isPreview) {
        return h('div', {
          class: 'md-preview-textarea-preview',
          style: 'min-height:120px;display:flex;align-items:center;justify-content:center;color:#bfbfbf;font-size:13px'
        }, '富文本编辑区 — 点击可输入')
      }
      return h('div', {
        class: 'md-preview-textarea',
        style: 'min-height:80px;display:flex;align-items:center;padding:4px 8px;color:#bfbfbf;font-style:italic'
      }, '富文本编辑区...')

    case 'input-number':
      return h(InputNumber, {
        size: 'small',
        style: 'width:100%',
        disabled: !isPreview,
        placeholder: ''
      })

    case 'select':
      return h(Select, {
        size: 'small',
        style: 'width:100%',
        disabled: !isPreview,
        placeholder: '请选择'
      })

    case 'switch':
      return h(Switch, {
        size: 'small',
        disabled: !isPreview
      })

    case 'datepicker':
      return h(DatePicker, {
        size: 'small',
        style: 'width:100%',
        disabled: !isPreview,
        placeholder: '选择日期'
      })

    case 'tree-select':
      return h(Select, {
        size: 'small',
        style: 'width:100%',
        disabled: !isPreview,
        placeholder: '树选择'
      })

    case 'user-selector':
      return h(Select, {
        size: 'small',
        style: 'width:100%',
        disabled: !isPreview,
        placeholder: '选择用户',
        showSearch: true,
        filterOption: (input, option) =>
          (option.label || '').toLowerCase().includes(input.toLowerCase()),
        options: options.userOptions || []
      })

    case 'user-multi-selector':
      return h(Select, {
        size: 'small',
        style: 'width:100%',
        disabled: !isPreview,
        mode: 'multiple',
        placeholder: '选择用户（可多选）',
        showSearch: true,
        filterOption: (input, option) =>
          (option.label || '').toLowerCase().includes(input.toLowerCase()),
        options: options.userOptions || []
      })

    case 'org-selector':
      return h(TreeSelect, {
        size: 'small',
        style: 'width:100%',
        disabled: !isPreview,
        placeholder: '选择组织',
        treeDefaultExpandAll: true,
        treeData: options.orgTreeData || []
      })

    case 'product-line-select':
      return h(TreeSelect, {
        size: 'small',
        style: 'width:100%',
        disabled: !isPreview,
        placeholder: '请选择所属产品系列',
        treeDefaultExpandAll: true,
        showSearch: true,
        treeNodeFilterProp: 'title',
        fieldNames: { label: 'title', key: 'key', value: 'value', children: 'children' },
        treeData: (options.productLineTree && options.productLineTree.length) ? options.productLineTree : [
          { title: '示例产品系列A', value: 'demo_a', key: 'demo_a' },
          { title: '示例产品系列B', value: 'demo_b', key: 'demo_b', children: [{ title: '子系列B1', value: 'demo_b1', key: 'demo_b1' }] }
        ]
      })

    case 'product-select':
      return h(TreeSelect, {
        size: 'small',
        style: 'width:100%',
        disabled: !isPreview,
        placeholder: '请选择所属产品',
        treeDefaultExpandAll: true,
        showSearch: true,
        treeNodeFilterProp: 'title',
        treeData: options.productLineTree || [],
        allowClear: true
      })

    case 'image-upload':
      return h('div', {
        style: 'display:flex;align-items:center;gap:8px;padding:4px 0'
      }, [
        h('div', {
          style: 'width:60px;height:45px;border:1px dashed #d9d9d9;border-radius:4px;display:flex;align-items:center;justify-content:center;background:#fafafa'
        }, [
          h(UploadOutlined, { style: 'font-size:16px;color:#bfbfbf' })
        ]),
        h(Button, {
          size: 'small',
          disabled: !isPreview,
          icon: h(UploadOutlined)
        }, { default: () => '上传图片' })
      ])

    case 'file-upload':
      return h('div', {
        style: 'display:flex;align-items:center;gap:8px;padding:4px 0'
      }, [
        h('div', {
          style: 'padding:4px 12px;border:1px dashed #d9d9d9;border-radius:4px;background:#fafafa;color:#bfbfbf;font-size:12px;flex:1;min-width:0;overflow:hidden;text-overflow:ellipsis;white-space:nowrap'
        }, '未选择文件'),
        h(Button, {
          size: 'small',
          disabled: !isPreview,
          icon: h(UploadOutlined)
        }, { default: () => '上传文件' })
      ])

    case 'ckfile-upload':
      return h('div', {
        style: 'display:flex;align-items:center;gap:8px;padding:4px 0'
      }, [
        h('div', {
          style: 'padding:4px 12px;border:1px dashed #d9d9d9;border-radius:4px;background:#fafafa;color:#bfbfbf;font-size:12px;flex:1;min-width:0;overflow:hidden;text-overflow:ellipsis;white-space:nowrap'
        }, '未选择主文档（支持本地/URL）'),
        h(Button, {
          size: 'small',
          disabled: !isPreview,
        }, { default: () => '选择/录入' })
      ])

    case 'data-table':
      return h(TableOutlined, { style: 'font-size:28px;color:#1677ff' })

    case 'folder-select':
      return h(TreeSelect, {
        size: 'small',
        style: 'width:100%',
        disabled: !isPreview,
        placeholder: '请选择所属文件夹',
        treeDefaultExpandAll: true,
        showSearch: true,
        treeNodeFilterProp: 'title',
        treeData: options.folderTree || [],
        allowClear: true
      })

    case 'document-type-select':
      return h(TreeSelect, {
        size: 'small',
        style: 'width:100%',
        disabled: !isPreview,
        placeholder: '请选择文档类型',
        treeDefaultExpandAll: true
      })

    case 'stage-select':
      return h(Select, {
        size: 'small', style: 'width:100%', disabled: !isPreview,
        placeholder: '请选择所属研发阶段',
        options: options.stageOptions || [],
      })

    case 'classification-select':
      return h(TreeSelect, {
        size: 'small',
        style: 'width:100%',
        disabled: !isPreview,
        placeholder: '请选择分类',
        treeDefaultExpandAll: true,
        showSearch: true,
        treeNodeFilterProp: 'title',
        treeData: options.classificationTree || [],
        allowClear: true
      })

    case 'classification-bound-select':
      return h(TreeSelect, {
        size: 'small',
        style: 'width:100%',
        disabled: !isPreview,
        placeholder: '请选择分类（受类型绑定限制）',
        treeDefaultExpandAll: true,
        showSearch: true,
        treeNodeFilterProp: 'title',
        treeData: options.classificationTree || [],
        allowClear: true
      })

    case 'unit-select':
      return h(Select, {
        size: 'small',
        style: 'width:100%',
        disabled: !isPreview,
        placeholder: '请选择单位',
        showSearch: true,
        options: options.unitOptions || [
          { label: '个', value: 'ea' },
          { label: '米', value: 'm' },
          { label: '千克', value: 'kg' }
        ]
      })

    case 'source-select':
      return h(Select, {
        size: 'small',
        style: 'width:100%',
        disabled: !isPreview,
        placeholder: '请选择来源',
        options: [
          { label: '自制', value: '自制' },
          { label: '委外', value: '委外' },
          { label: '采购', value: '采购' }
        ]
      })

    case 'lifecycle-display':
      return h('div', {
        style: 'display:flex;align-items:center;gap:8px;width:100%;min-height:32px;padding:4px 11px;border:1px solid #d9d9d9;border-radius:6px;background:#f5f5f5;color:#8c8c8c;font-size:12px'
      }, [
        h('span', { style: 'color:#333;font-weight:500' }, '标准流程'),
        h('span', { style: 'color:#bfbfbf' }, '|'),
        h('span', {}, '初始状态'),
        h('span', { style: 'color:#1677ff' }, '草稿')
      ])

    case 'version-display':
      // 设计态预览：编辑态外观（创建态运行时显示「由系统生成」）
      return h('div', {
        style: 'display:flex;align-items:center;gap:8px;width:100%;min-height:32px;padding:4px 11px;border:1px solid #d9d9d9;border-radius:6px;background:#f5f5f5;color:#8c8c8c;font-size:12px'
      }, [
        h('span', { style: 'color:#1677ff;font-weight:600;letter-spacing:0.5px' }, 'A.1'),
        h('span', {}, '当前对象版本（只读）')
      ])

    case 'number-preview':
      return h('div', {
        style: 'display:flex;align-items:center;gap:8px;width:100%;min-height:32px;padding:4px 11px;border:1px solid #d9d9d9;border-radius:6px;background:#f5f5f5;color:#8c8c8c;font-size:12px'
      }, [
        h('span', { style: 'color:#1677ff;font-weight:600;letter-spacing:0.5px' }, 'PART-2026-****')
      ])

    case 'resource-container-select':
      // 设计态预览：企业资源库下的子库（运行时由 getResourceChildren() 动态加载）
      return h(Select, {
        size: 'small',
        style: 'width:100%',
        disabled: !isPreview,
        placeholder: '请选择构建容器',
        options: [
          { label: '元器件库', value: 'COMPONENT' },
          { label: '标准件库', value: 'STD_PART' },
          { label: '通用件库', value: 'GEN_PART' }
        ]
      })

    case 'mcad-tool-select':
      return h(Select, {
        size: 'small',
        style: 'width:100%',
        disabled: !isPreview,
        placeholder: '请选择 MCAD 工具',
        showSearch: true,
        options: CAD_TOOL_OPTIONS.MCAD
      })

    case 'ecad-tool-select':
      return h(Select, {
        size: 'small',
        style: 'width:100%',
        disabled: !isPreview,
        placeholder: '请选择 ECAD 工具',
        showSearch: true,
        options: CAD_TOOL_OPTIONS.ECAD
      })

    default:
      return h(Input, {
        size: 'small',
        disabled: !isPreview,
        placeholder: ''
      })
  }
}
