<template>
  <div class="dynamic-form">
    <a-spin :spinning="loading || entityLoading" tip="加载表单配置...">
      <a-empty
        v-if="!loading && !layoutData"
        description="未配置表单布局，请在「页面设计器」中设计"
      />

      <a-form
        v-else-if="layoutData"
        ref="formRefRef"
        :model="localFormData"
        :layout="formLayout"
      >
        <RenderFields
          :fields="layoutData.form?.fields || []"
          :form-data="localFormData"
          :product-line-tree="productLineTree"
          :product-owner-tree="productOwnerTree"
          :node-type-map="nodeTypeMap"
          :org-tree-data="orgTreeData"
          :user-options="userOptions"
          :folder-tree="folderTree"
          :stage-options="stageOptions"
          :type-definition-oid="resolvedTypeOid || typeDefinitionOid"
          :current-container-oid="currentContainerOid"
          :entity-code="entityCode"
          :widget-options="widgetOptions"
          :readonly-fields="readonlyFields"
          @update="onFieldUpdate"
          @table-action="onTableAction"
          @object-options="onObjectOptions"
        />

        <!-- 分类属性分区：选择分类后加载该分类的 IBA 布局/属性 -->
        <div v-if="currentClsOid" class="df-cls-section">
          <div class="df-cls-section-header">
            <span class="df-cls-section-title">分类属性</span>
            <code v-if="currentClsLabel" class="df-cls-section-code">{{ currentClsLabel }}</code>
            <a-spin v-if="clsIbaLoading" size="small" />
          </div>
          <a-empty
            v-if="!clsIbaLoading && clsIbaFields.length === 0"
            description="该分类未配置 IBA 属性布局"
            :image-style="{ height: '32px' }"
          />
          <RenderFields
            v-else-if="clsIbaFields.length > 0"
            :fields="clsIbaFields"
            :form-data="localFormData"
            :product-line-tree="productLineTree"
            :product-owner-tree="productOwnerTree"
            :node-type-map="nodeTypeMap"
            :org-tree-data="orgTreeData"
            :user-options="userOptions"
            :folder-tree="folderTree"
            :stage-options="stageOptions"
            :type-definition-oid="resolvedTypeOid || typeDefinitionOid"
            :current-container-oid="currentContainerOid"
            :entity-code="entityCode"
            :readonly-fields="readonlyFields"
            @update="onFieldUpdate"
            @table-action="onTableAction"
          />
        </div>
      </a-form>
    </a-spin>
  </div>
</template>

<script setup>
import { ref, reactive, watch, computed, onMounted } from 'vue'
import { message } from 'ant-design-vue'
import { getPageLayoutByCode, getProductLineTree, getProductLineTreeLinesOnly, getOrgTree, getAllUsers, getEntityByCode, getEntityIbaData, getAllFolderTree, getClsIbaLayout, getClassificationIBAs, getClassification, getTypeClassificationSubtree } from '@/api'
import RenderFields from './RenderFields.js'

// ==================== Props & Emits ====================
const props = defineProps({
  /** 实体编码，如 PRODUCT_LINE */
  entityCode: { type: String, required: true },
  /** 操作编码，如 create / update */
  operationCode: { type: String, required: true },
  /** v-model 表单数据 */
  modelValue: { type: Object, default: () => ({}) },
  /** 表单布局模式 */
  formLayout: { type: String, default: 'vertical' },
  /** 外部传入的产品线树（如编辑时需要过滤） */
  externalProductLineTree: { type: Array, default: null },
  /** 外部传入的组织树 */
  externalOrgTreeData: { type: Array, default: null },
  /** 外部传入的用户选项 */
  externalUserOptions: { type: Array, default: null },
  /**
   * 实体 OID（编辑场景）。
   * 当 operationCode === 'update' 且 entityOid 有值时，
   * DynamicForm 会自动调用 getEntityByCode 加载实体数据并填充表单。
   */
  entityOid: { type: String, default: null },
  /**
   * 回退实体编码。当 entityCode 对应的布局不存在时，
   * 使用此编码再次尝试查询布局。
   * 例如：文档子类型 BOM 没有专属布局时，回退到 DOCUMENT 布局。
   */
  fallbackEntityCode: { type: String, default: null },
  /** 当前容器 oid，用于过滤文件夹树（只显示当前产品/型号下的文件夹） */
  currentContainerOid: { type: String, default: null },
  /** 当前研发阶段 oid，用于过滤文件夹树（只显示当前阶段下的文件夹） */
  currentStageOid: { type: String, default: null },
  /** 外部传入的阶段选项 [{label, value}]，用于 stage-select 控件 */
  stageOptions: { type: Array, default: () => [] },
  /**
   * 跳过产品线 / 组织 / 文件夹树等资源预加载。
   *
   * <p>变更单据的表单只用到用户与问题选择器（控件各自按需拉取），预加载产品树会
   * 按系列逐个请求型号，弹窗打开明显变慢。布局加载与实体数据回填不受影响。
   */
  skipResourceLoading: { type: Boolean, default: false },
  /** 类型定义 oid，用于 classification-bound-select 组件加载绑定分类子树 */
  typeDefinitionOid: { type: String, default: null },
  /**
   * 控件级外部数据，原样透传给 RenderFields。
   *
   * <p>目前唯一用途：变更单据编辑回填时，把详情接口带回的关联问题
   * （{oid, code, name}）作为 `issueOptions` 交给 change-issue-selector ——
   * 否则下拉里只有 oid，显示成一串 uuid。
   */
  widgetOptions: { type: Object, default: () => ({}) },
  /**
   * 需要追加锁定的字段名（在布局自带的 `readonly` 之外）。
   *
   * <p>用于"值来自上游单据、不允许在本单改"的场景：变更通告从 ECR 派生时，
   * 变更类型 / 分级 / 优先级 / 三项影响 / 变更原因 继承的是 ECR 的判定结论，
   * 在这里只读 —— 判定只在 ECR 上做一次。
   */
  readonlyFields: { type: Array, default: () => [] },
})

const emit = defineEmits(['update:modelValue', 'table-action', 'object-options'])

// ==================== 状态 ====================
const formRefRef = ref(null)
const loading = ref(false)
const layoutData = ref(null)
const productLineTreeInternal = ref([])
const orgTreeDataInternal = ref([])
const userOptionsInternal = ref([])
const folderTreeInternal = ref([])

// typeDefinitionOid 兜底：外部未传入时，通过 entityCode 反查 oid
const resolvedTypeOid = ref(null)

async function resolveTypeDefinitionOid() {
  if (props.typeDefinitionOid) {
    resolvedTypeOid.value = props.typeDefinitionOid
    return
  }
  if (!props.entityCode) {
    resolvedTypeOid.value = null
    return
  }
  try {
    const { getTypeDefinitions } = await import('@/api')
    const res = await getTypeDefinitions()
    const list = res?.data || res || []
    const found = Array.isArray(list) ? list.find(t => t.code === props.entityCode) : null
    resolvedTypeOid.value = found?.oid || null
  } catch {
    resolvedTypeOid.value = null
  }
}

watch(() => [props.typeDefinitionOid, props.entityCode], resolveTypeDefinitionOid, { immediate: true })

// 优先使用外部传入的树数据
const productLineTree = computed(() =>
  props.externalProductLineTree || productLineTreeInternal.value
)
const orgTreeData = computed(() =>
  props.externalOrgTreeData || orgTreeDataInternal.value
)
const userOptions = computed(() =>
  props.externalUserOptions || userOptionsInternal.value
)
const folderTree = computed(() => {
  if (props.currentContainerOid && props.currentStageOid) {
    return filterFolderTree(folderTreeInternal.value, props.currentContainerOid, props.currentStageOid)
  }
  return folderTreeInternal.value
})

/** 组合产品树：系列 + 型号，每个节点携带 nodeType 元数据 */
const productOwnerTree = ref([])
/** oid → nodeType 映射：LINE 或 MODEL */
const nodeTypeMap = ref({})

/** 内部 stageOptions：优先使用外部传入的 prop */
const stageOptions = computed(() => props.stageOptions || [])

// ==================== 分类属性（cls IBA）联动加载 ====================

/** 当前选中的分类 oid（clsOid 或 classificationOid） */
const currentClsOid = ref(null)
/** 当前分类的展示标签（用于分区标题） */
const currentClsLabel = ref('')
/** 分类 IBA 字段列表（布局字段或动态生成的属性字段） */
const clsIbaFields = ref([])
/** 分类属性加载中 */
const clsIbaLoading = ref(false)

/** 从表单数据中提取分类 oid，兼容 clsOid 与 classificationOid */
function extractClsOid() {
  return localFormData.clsOid || localFormData.classificationOid || null
}

/** 数据类型 → uiComponent 映射（动态生成字段时使用） */
function dataTypeToUiComponent(dataType) {
  const dt = (dataType || 'STRING').toUpperCase()
  switch (dt) {
    case 'INTEGER':
    case 'DOUBLE':
    case 'NUMBER':
      return 'input-number'
    case 'BOOLEAN':
      return 'switch'
    case 'DATE':
      return 'datepicker'
    case 'DATETIME':
      return 'datepicker'
    case 'LONGTEXT':
      return 'textarea'
    default:
      return 'input'
  }
}

/** 根据 IBA 列表动态生成字段 */
function buildFieldsFromIBAs(ibaList) {
  return (Array.isArray(ibaList) ? ibaList : []).map((iba) => {
    const code = iba.ibaCode || iba.code
    const name = iba.ibaName || iba.name
    const displayName = iba.ibaDisplayName || iba.displayName || name || code
    const dataType = iba.ibaDataType || iba.dataType || 'STRING'
    return {
      fieldName: code,
      label: displayName,
      uiComponent: dataTypeToUiComponent(dataType),
      dataType,
      required: false,
      readonly: false,
      placeholder: `请输入${displayName}`,
    }
  })
}

/** 递归归一化分类 IBA 布局字段的 fieldName 大小写（例如 iba_cap → IBA_CAP） */
function normalizeClsFieldNames(fields, codeMap) {
  return (fields || []).map(f => {
    const copy = { ...f }
    if (copy.children?.length) {
      copy.children = normalizeClsFieldNames(copy.children, codeMap)
    } else if (copy.fieldName && codeMap[String(copy.fieldName).toLowerCase()]) {
      copy.fieldName = codeMap[String(copy.fieldName).toLowerCase()]
    }
    return copy
  })
}

/** 加载分类 IBA 布局（无布局时回退到动态生成字段） */
async function loadClsIba() {
  const clsOid = extractClsOid()
  currentClsOid.value = clsOid
  if (!clsOid) {
    clsIbaFields.value = []
    currentClsLabel.value = ''
    return
  }

  clsIbaLoading.value = true
  try {
    // 0. 加载分类名称（用于分区标题展示）
    try {
      const clsRes = await getClassification(clsOid)
      const cls = clsRes?.data || clsRes
      currentClsLabel.value = cls?.name || cls?.identifier || cls?.code || ''
    } catch { currentClsLabel.value = '' }

    // 1. 加载分类绑定的 IBA 列表，构建「小写 fieldName → 原始 IBA code」映射（用于归一化大小写）
    let ibaList = []
    const codeMap = {}
    try {
      const ibaRes = await getClassificationIBAs(clsOid)
      ibaList = ibaRes?.data || ibaRes || []
      ;(Array.isArray(ibaList) ? ibaList : []).forEach(iba => {
        const code = iba.ibaCode || iba.code
        if (code) codeMap[String(code).toLowerCase()] = code
      })
    } catch { /* 忽略，映射为空则不做归一化 */ }

    // 2. 优先加载分类 IBA 布局
    let fields = null
    try {
      const layoutRes = await getClsIbaLayout(clsOid, props.operationCode)
      const layout = layoutRes?.data || layoutRes
      if (layout?.layoutJson) {
        let json = layout.layoutJson
        if (typeof json === 'string') {
          try { json = JSON.parse(json) } catch { json = null }
        }
        if (json?.form?.fields?.length) {
          fields = json.form.fields
        }
      }
    } catch { /* 忽略布局加载失败，回退到属性集 */ }

    // 3. 无布局时，回退到分类关联的 IBA 属性集动态生成
    if (!fields || fields.length === 0) {
      fields = buildFieldsFromIBAs(ibaList)
    } else {
      // 有布局时，将布局 fieldName 归一化到 IBA 原始 code（例如 iba_cap → IBA_CAP）
      fields = normalizeClsFieldNames(fields, codeMap)
    }

    clsIbaFields.value = fields || []
  } catch {
    clsIbaFields.value = []
  } finally {
    clsIbaLoading.value = false
  }
}

// ==================== 表单数据双向绑定 ====================
const localFormData = reactive({ ...props.modelValue })

/**
 * 自建浅层比对：若两个对象的自有属性键集合和值完全相同，视为相等。
 * 用于打断 modelValue ↔ localFormData 双向 watch 的死循环。
 */
function shallowEqual(a, b) {
  const keysA = Object.keys(a)
  const keysB = Object.keys(b)
  if (keysA.length !== keysB.length) return false
  return keysA.every(k => b.hasOwnProperty(k) && a[k] === b[k])
}

/** 外部 modelValue 变更 → 同步到内部表单数据 */
watch(
  () => props.modelValue,
  (val) => {
    if (!val || Object.keys(val).length === 0) return
    if (shallowEqual(localFormData, val)) return  // 内容相同，跳过，打断死循环
    Object.keys(localFormData).forEach(k => delete localFormData[k])
    Object.assign(localFormData, val)
  },
  { deep: true }
)

/** 内部表单变更 → 同步到外部 modelValue */
watch(
  () => ({ ...localFormData }),
  (val) => emit('update:modelValue', { ...val }),
  { deep: true }
)

// ==================== 字段更新 ====================
function onFieldUpdate(key, val) {
  localFormData[key] = val
}

/** 数据表格操作事件转发 */
function onTableAction(payload) {
  emit('table-action', payload)
}

/** 对象选择控件的候选（oid → 文案）转发：上层要用它渲染二级下拉 */
function onObjectOptions(fieldName, options) {
  emit('object-options', fieldName, options)
}

// 监听分类字段变化（clsOid / classificationOid），触发分类属性加载
watch(
  () => [localFormData.clsOid, localFormData.classificationOid],
  () => loadClsIba()
)

// ==================== 加载布局 ====================
/**
 * 2026-10-11：未绑定分类时，把 classification-bound-select 字段**整体**剔除（含 Form.Item 的
 * label「分类」）。组件自身的 hideWhenUnbound 只藏控件自己，外层标签与空白框仍留在表单里
 * （用户反馈："未绑定分类的话，红色框的区域不应该展示"）。
 * 只走 DynamicForm（创建/编辑表单）—— 设计器预览不经这里、且 hideWhenUnbound=false，展示不受影响。
 */
async function stripUnboundClassificationFields(fields) {
  if (!fields?.length) return fields
  // 判定依据：外部传入的 typeOid，**或组件自己按 entityCode 反查出来的**（resolvedTypeOid）。
  // 2026-10-13 修：原先只看 props.typeDefinitionOid —— 调用方没传时（如元器件库「新建IPN」）
  // bound 恒为 false，「分类」字段连 label 被无条件删掉（用户反馈："分类为什么没有了"）。
  const typeOid = props.typeDefinitionOid || resolvedTypeOid.value
  // 拿不到 typeOid（反查也失败）时**不动字段** —— "判不了"不等于"未绑定"：
  // 控件自身的 hideWhenUnbound 兜得住"未绑定"的展示，但整块删除是不可逆的
  if (!typeOid) return fields
  let bound = false
  try {
    const res = await getTypeClassificationSubtree(typeOid)
    const data = res?.data || res
    bound = !!(data && data.oid)
  } catch {
    // 查询失败按"未绑定"处理（失败原因由响应拦截器统一提示，与 ClassificationBoundSelect 同一口径）
    bound = false
  }
  if (bound) return fields
  const prune = (list) => (list || []).filter(f => {
    if (f.uiComponent === 'classification-bound-select' || f.type === 'classification-bound-select') return false
    if (f.children?.length) f.children = prune(f.children)
    return true
  })
  return prune(fields)
}

async function loadLayout() {
  if (!props.entityCode || !props.operationCode) return

  // 分类字段的取舍要靠 typeOid 判定（见 stripUnboundClassificationFields）——
  // 外部没传时先按 entityCode 反查，别把"还没解析出来"当成"类型未绑定分类"
  await resolveTypeDefinitionOid()

  loading.value = true
  try {
    let res = await getPageLayoutByCode(props.entityCode, props.operationCode)

    // 主编码未找到布局时，尝试回退编码
    if ((!res || res.code !== 200 || !res.data) && props.fallbackEntityCode) {
      res = await getPageLayoutByCode(props.fallbackEntityCode, props.operationCode)
    }

    if (res?.code === 200 && res.data) {
      let layout = res.data.layoutJson
      if (typeof layout === 'string') {
        try { layout = JSON.parse(layout) } catch { layout = null }
      }
      // 未绑定分类 → 剔除"分类"字段（连 label 一起），不让空区块出现在表单里
      if (layout?.form?.fields) {
        layout.form.fields = await stripUnboundClassificationFields(layout.form.fields)
      }
      layoutData.value = layout
      if (layout?.form?.fields) initDefaultValues(layout.form.fields)
    } else {
      layoutData.value = null
    }
  } catch {
    layoutData.value = null
  } finally {
    loading.value = false
  }
}

/** 递归初始化字段默认值 */
function initDefaultValues(fields) {
  for (const field of fields) {
    if (field.children?.length) {
      initDefaultValues(field.children)
      continue
    }
    if (field.fieldName && field.defaultValue !== undefined && field.defaultValue !== '') {
      if (!(field.fieldName in localFormData)) {
        localFormData[field.fieldName] = field.defaultValue
      }
    }
  }
}

// ==================== 加载树数据 ====================

/** 加载纯产品系列树（含子系列，不含产品型号），用于 product-line-select 控件 */
async function loadProductLineTree() {
  try {
    const res = await getProductLineTreeLinesOnly()
    if (res.code === 200) {
      productLineTreeInternal.value = transformTreeData(res.data || [])
    }
  } catch { /* ignore */ }
}

/**
 * 加载组合产品树（系列 + 型号），用于 product-select 控件（含型号可选）。
 *
 * <p>`/product-lines/tree` 本身就把型号挂在系列的 children 里，并带 `nodeType`
 * （PRODUCT_LINE / PRODUCT_MODEL）。早先这里又按系列逐个查 `/product-models` 再拼一遍，
 * 结果型号被挂了两份；更糟的是随后递归 children 时把型号当成子系列、覆盖成
 * PRODUCT_LINE —— 选中型号会存下错的 productType。现在直接按 nodeType 映射一遍。
 */
async function loadProductOwnerTree() {
  try {
    const lineRes = await getProductLineTree()
    if (lineRes.code !== 200) return
    const typeMap = {}

    function buildNode(node) {
      const type = node.nodeType === 'PRODUCT_MODEL' ? 'PRODUCT_MODEL' : 'PRODUCT_LINE'
      typeMap[node.oid] = type
      const item = {
        title: node.name || node.code,
        value: node.oid,
        key: node.oid,
        nodeType: type,
      }
      if (node.children?.length) {
        item.children = node.children.map(buildNode)
      }
      return item
    }

    productOwnerTree.value = (lineRes.data || []).map(buildNode)
    nodeTypeMap.value = typeMap
  } catch (e) {
    // 不要静默吞：这棵树空了会退回纯系列树，「所属产品」看起来能选、却永远选不到型号
    console.warn('[DynamicForm] 加载产品系列+型号树失败，已退回仅系列树', e)
  }
}

async function loadOrgTree() {
  try {
    const res = await getOrgTree()
    if (res.code === 200) {
      orgTreeDataInternal.value = transformOrgTree(res.data || [])
    }
  } catch { /* ignore */ }
}

async function loadFolderTree() {
  try {
    const res = await getAllFolderTree()
    if (res.code === 200) {
      folderTreeInternal.value = transformFolderTreeData(res.data || [])
    }
  } catch { /* ignore */ }
}

/** 专属文件夹树转换：保留 containerOid/stageOid 用于过滤 */
function transformFolderTreeData(nodes, parentPath = '') {
  return nodes.map(node => {
    const label = parentPath ? `${parentPath} / ${node.name}` : node.name
    return {
      title: label,
      label,
      value: node.oid,
      key: node.oid,
      _containerOid: node.ownerOid,
      _stageOid: node.stageOid,
      children: node.children?.length ? transformFolderTreeData(node.children, label) : undefined,
    }
  })
}

/** 根据当前 stage/container 过滤文件夹树的递归辅助 */
function filterFolderTree(nodes, containerOid, stageOid) {
  if (!nodes) return []
  const result = nodes.reduce((acc, node) => {
    if (node._containerOid !== containerOid || node._stageOid !== stageOid) return acc
    const filteredChildren = filterFolderTree(node.children, containerOid, stageOid)
    acc.push({ ...node, children: filteredChildren.length ? filteredChildren : undefined })
    return acc
  }, [])
  // 如果过滤后为空，返回全部树（避免因过滤条件导致下拉为空）
  return result.length ? result : nodes
}

async function loadUserOptions() {
  try {
    const res = await getAllUsers()
    if (res.code === 200) {
      userOptionsInternal.value = (res.data || []).map(u => ({
        label: u.displayName || u.username,
        value: u.oid || u.id,
      }))
    }
  } catch { /* ignore */ }
}

function transformTreeData(nodes, parentPath = '') {
  return nodes.map(node => {
    const label = parentPath ? `${parentPath} / ${node.name}` : node.name
    return {
      title: label,
      label,
      value: node.oid,
      key: node.oid,
      children: node.children?.length ? transformTreeData(node.children, label) : undefined,
    }
  })
}

function transformOrgTree(nodes) {
  return (nodes || []).map(node => ({
    name: node.name,
    oid: node.oid,
    children: node.children?.length ? transformOrgTree(node.children) : undefined,
  }))
}

// ==================== 统一实体数据加载（编辑场景） ====================

/** 是否正在加载实体数据 */
const entityLoading = ref(false)

/** 布局（含分组 / 布局行嵌套）里是否存在某字段 */
function hasLayoutField(fieldName) {
  const walk = (list) => (list || []).some(f =>
    f.fieldName === fieldName || (f.children?.length ? walk(f.children) : false))
  return walk(layoutData.value?.form?.fields)
}

/**
 * 通过 entityCode + entityOid 从后端获取实体完整数据，
 * 并填入 localFormData。
 * 这是编辑表单的统一数据加载入口。
 */
async function loadEntityData() {
  if (!props.entityOid || !props.entityCode) return

  entityLoading.value = true
  try {
    const res = await getEntityByCode(props.entityCode, props.entityOid)
    if (res.code === 200 && res.data) {
      const entity = res.data
      // 收集实体中的普通字段
      const data = {}
      for (const [key, val] of Object.entries(entity)) {
        if (val === null || val === undefined) continue
        if (typeof val === 'object' && !Array.isArray(val)) continue
        if (Array.isArray(val) && val.length > 0 && typeof val[0] === 'object') continue
        data[key] = val
      }
      // 合并分类 IBA 值（后端放在 entity.clsIba 嵌套字段，避免与实体字段 putAll 冲突）
      if (entity.clsIba && typeof entity.clsIba === 'object' && !Array.isArray(entity.clsIba)) {
        for (const [k, v] of Object.entries(entity.clsIba)) {
          if (v === null || v === undefined || v === '') continue
          if (typeof v === 'object') continue
          data[k] = v
        }
      }
      // 「所属产品」在 DOCUMENT / ENG_DOCUMENT 的基准布局里绑的是 ownerOid，
      // 实体上的归属列却是 containerOid —— 不补这一笔，编辑时该下拉永远是空的
      if (data.containerOid) {
        if (!data.ownerOid && hasLayoutField('ownerOid')) data.ownerOid = data.containerOid
        // 设计器改过布局时可能写成 productOid（变更单据同款写法）
        if (!data.productOid && hasLayoutField('productOid')) data.productOid = data.containerOid
      }
      // 先清空再批量赋值，确保 TreeSelect 等控件正确响应值变化
      Object.keys(localFormData).forEach(k => delete localFormData[k])
      Object.assign(localFormData, data)

      // 加载 IBA 属性值并合并到表单数据
      try {
        const entityType = entityCodeToLower(props.entityCode)
        const ibaRes = await getEntityIbaData(entityType, props.entityOid)
        if (ibaRes.code === 200 && ibaRes.data) {
          Object.assign(localFormData, ibaRes.data)
        }
      } catch { /* IBA 加载失败不影响基本功能 */ }

      // 数据加载完成后手动同步到父组件
      emit('update:modelValue', { ...localFormData })
    } else {
      message.error(res.message || '加载实体数据失败')
    }
  } catch {
    message.error('加载实体数据失败')
  } finally {
    entityLoading.value = false
  }
}

// ==================== 公共方法 ====================
function getFormData() {
  return { ...localFormData }
}

/** 递归收集分类 IBA 布局中的叶子字段（含 group/layout-row 容器的 children） */
function collectClsLeafFields(fields) {
  const result = []
  for (const f of fields || []) {
    if (f.children?.length) {
      result.push(...collectClsLeafFields(f.children))
    } else if (f.fieldName) {
      result.push(f)
    }
  }
  return result
}

/** 获取分类 IBA 字段名列表（用于父组件提交时区分实体 IBA 与分类 IBA） */
function getClsIbaFieldNames() {
  return collectClsLeafFields(clsIbaFields.value)
    .map(f => f.fieldName)
    .filter(Boolean)
}

/**
 * 获取分类 IBA 字段值（用于提交到 ck_cls_iba_data）。
 * 返回值格式：{ attrCode: value, ... }
 */
function getClsIbaValues() {
  const result = {}
  for (const f of collectClsLeafFields(clsIbaFields.value)) {
    const k = f.fieldName
    if (!k) continue
    const v = localFormData[k]
    if (v === undefined || v === null || v === '') continue
    result[k] = v
  }
  return result
}

function validate() {
  const errors = []
  const fields = layoutData.value?.form?.fields || []
  collectRequiredErrors(fields, errors)
  return errors
}

function collectRequiredErrors(fields, errors) {
  for (const field of fields) {
    if (field.children?.length) {
      collectRequiredErrors(field.children, errors)
      continue
    }
    if (field.required && field.fieldName) {
      // 取值口径必须和渲染一致（RenderFields 是 formData ?? defaultValue ?? ''）：
      // 否则「带默认值的必填项」会一面显示着值、一面提示不能为空
      const val = localFormData[field.fieldName] ?? field.defaultValue
      if (val === undefined || val === null || val === '') {
        errors.push(`${field.label || field.fieldName} 不能为空`)
      }
    }
  }
}

/** Part 子类型（SOFT_TYPE）统一归一为 'PART'，与 PartController.IBA_ENTITY_TYPE 一致 */
const PART_SOFT_TYPE_IBA = new Set(['PART', 'FUNCTIONAL', 'ELECTRONIC', 'STRUCTURAL', 'ELECTRICAL', 'SOFTWARE', 'PCBA'])

/** 将实体 code 映射为 IBA entityType（Part 子类型归一为 PART） */
function entityCodeToLower(entityCode) {
  if (!entityCode) return ''
  return PART_SOFT_TYPE_IBA.has(entityCode) ? 'PART' : entityCode
}

// ==================== 弹窗宽度建议 ====================

/**
 * 布局里 {@code layout-row} 的最大列数。
 *
 * <p>一行 N 列的布局，每列只分到「弹窗宽度 / N」——680px 的弹窗塞两列时，
 * 单行输入框只剩 ~300px，下拉和日期选择器都被挤成一条。把真实列数暴露给宿主，
 * 由宿主决定弹窗宽多少，而不是所有表单写死同一个宽度。
 */
const layoutColumns = computed(() => {
  let max = 1
  const walk = (fields) => {
    for (const f of fields || []) {
      if (f.type === 'layout-row' || f.uiComponent === 'layout-row') {
        const cols = Number(f.columns) || 0
        if (cols > max) max = cols
      }
      if (f.children?.length) walk(f.children)
    }
  }
  walk(layoutData.value?.form?.fields)
  return max
})

/**
 * 建议弹窗宽度：单列 680 / 两列 900 / 三列 1040 / 四列 1180，且不超过视口 94%。
 *
 * <p>用法（宿主弹窗）：{@code :width="formRef?.recommendedWidth || '680px'"}。
 * 布局在弹窗打开前已加载完时不会出现"宽度跳一下"。
 */
const recommendedWidth = computed(() => {
  const width = { 1: 680, 2: 900, 3: 1040, 4: 1180 }[layoutColumns.value] || 1180
  return `min(${width}px, 94vw)`
})

defineExpose({
  getFormData, getClsIbaFieldNames, getClsIbaValues, validate, loading, entityLoading, layoutData,
  layoutColumns, recommendedWidth,
})

/**
 * 布局里是否用到了产品树控件。
 *
 * <p>skipResourceLoading 的表单（变更单据等）原本一张树都不拉；一旦布局里配了
 * 「所属容器」（product-select），不补树就是空下拉 —— 控件在、却什么都选不到。
 * 这里按布局实际用到的控件决定，避免为不相干的表单白拉接口。
 */
function layoutNeedsProductTree() {
  const walk = (fields) => (fields || []).some(f =>
    f.uiComponent === 'product-select'
    || f.uiComponent === 'product-line-select'
    || (f.children?.length && walk(f.children)))
  return walk(layoutData.value?.form?.fields)
}

// ==================== 生命周期 ====================
onMounted(async () => {
  // 先并行加载布局和树数据，确保 TreeSelect 等组件渲染前树已就绪；
  // 轻量表单（如变更单据）只需布局，跳过产品 / 组织 / 文件夹树（见 skipResourceLoading）
  if (props.skipResourceLoading) {
    await loadLayout()
    if (layoutNeedsProductTree()) {
      await Promise.all([loadProductLineTree(), loadProductOwnerTree()])
    }
  } else {
    await Promise.all([loadLayout(), loadProductLineTree(), loadProductOwnerTree(), loadOrgTree(), loadUserOptions(), loadFolderTree()])
  }
  // 编辑场景：布局和树数据就绪后，再加载实体数据填入表单（await 确保数据就绪后再渲染）
  if (props.entityOid && props.operationCode === 'update') {
    await loadEntityData()
  }
})

watch(
  () => [props.entityCode, props.operationCode],
  async () => {
    await loadLayout()
    // 新建 / 编辑切换时布局可能刚变成含「所属容器」的那份，树没拉过就补一次
    if (props.skipResourceLoading && layoutNeedsProductTree() && !productOwnerTree.value.length) {
      await Promise.all([loadProductLineTree(), loadProductOwnerTree()])
    }
  }
)

/** 当传入 entityOid 时自动加载实体数据填入表单（编辑场景统一入口） */
watch(
  () => props.entityOid,
  (oid) => {
    if (oid && props.operationCode === 'update') {
      loadEntityData()
    }
  },
  { immediate: false }
)
</script>

<style scoped>
.dynamic-form {
  min-height: 120px;
}

/*
  字段上下间距 12px（用户 2026-11-01 指定，见弹窗里两处红框；先定 16px，同日改 12px）。
  间距只能在这里定：antd-vue 会把 Form.Item 的 style 落到**内层** .ant-form-item-row 上，
  与外层 .ant-form-item 自带的 margin 折叠，写在 RenderFields 里从来不准（16px 曾实际是 24px）。
*/
.dynamic-form :deep(.ant-form-item) {
  margin-bottom: 12px;
}

.df-cls-section {
  margin-top: 16px;
  padding-top: 16px;
  border-top: 1px solid #f0f0f0;
}

.df-cls-section-header {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 12px;
}

.df-cls-section-title {
  font-size: 15px;
  font-weight: 600;
  color: #1a1a2e;
}

.df-cls-section-code {
  font-size: 12px;
  color: #8c8c8c;
  background: #f5f5f5;
  padding: 1px 8px;
  border-radius: 3px;
}

.df-form-title {
  margin: 0 0 16px 0;
  font-size: 15px;
  font-weight: 600;
  color: #1a1a2e;
  padding-bottom: 12px;
  border-bottom: 1px solid #f0f0f0;
}

:deep(.df-form-item-vertical .ant-form-item-label) {
  text-align: left;
  padding-bottom: 0;
}
</style>
