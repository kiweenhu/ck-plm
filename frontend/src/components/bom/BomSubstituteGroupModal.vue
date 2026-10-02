<template>
  <a-modal
    v-model:open="visible"
    :title="`成组替代 · ${partName || '本版 BOM'}`"
    width="1040px"
    :mask-closable="false"
  >
    <!-- ==================== 列表视图 ==================== -->
    <template v-if="!editing">
      <div class="bsg-toolbar">
        <span class="bsg-intro">
          成组替代换的是<b>一组行</b>：原料侧的多条 BOM 行被替代侧的一组物料整组替换。
          勾了「整组替换」就必须整组一起换 —— 拆开只换其中任何一个都不成立。
        </span>
        <a-button size="small" type="primary" @click="onCreate">
          <PlusOutlined /> 新建替代组
        </a-button>
      </div>

      <a-table
        :columns="listColumns"
        :data-source="groups"
        :loading="loading"
        :pagination="false"
        row-key="oid"
        size="small"
        :scroll="{ x: 1040 }"
        :locale="{ emptyText: '本版 BOM 还没有成组替代' }"
      >
        <template #bodyCell="{ column, record }">
          <template v-if="column.key === 'name'">
            <div class="bsg-name">{{ record.name || '未命名替代组' }}</div>
            <div v-if="record.description" class="bsg-desc">{{ record.description }}</div>
          </template>
          <template v-else-if="column.key === 'sources'">
            <div v-for="m in record.sources" :key="m.oid" class="bsg-member">
              <code>{{ m.bomLineNumber ?? '-' }}</code>
              <span class="bsg-member-name">{{ m.sourcePartName || '-' }}</span>
              <span class="bsg-member-code">{{ m.sourcePartNumber || '-' }}</span>
              <span class="bsg-qty">×{{ m.quantity ?? 1 }}{{ m.unit || '' }}</span>
            </div>
          </template>
          <template v-else-if="column.key === 'substitutes'">
            <div v-for="m in record.substitutes" :key="m.oid" class="bsg-member">
              <span class="bsg-member-name">{{ m.partName || '-' }}</span>
              <span class="bsg-member-code">{{ m.partNumber || '-' }}</span>
              <span v-if="m.partVersion" class="bsg-member-code">{{ m.partVersion }}</span>
              <span class="bsg-qty">×{{ m.quantity ?? 1 }}{{ m.unit || '' }}</span>
            </div>
          </template>
          <template v-else-if="column.key === 'flags'">
            <a-tag :color="record.atomicReplace === false ? 'default' : 'purple'" size="small">
              {{ record.atomicReplace === false ? '可拆开换' : '整组替换' }}
            </a-tag>
            <a-tag v-if="record.enabled === false" size="small">已停用</a-tag>
          </template>
          <template v-else-if="column.key === 'status'">
            <a-tag :color="statusOf(record.status).color" size="small">{{ statusOf(record.status).label }}</a-tag>
          </template>
          <template v-else-if="column.key === 'action'">
            <a-button type="link" size="small" @click="onEdit(record)">编辑</a-button>
            <a-button
              v-if="record.status !== 'APPROVED'"
              type="link"
              size="small"
              @click="onChangeStatus(record, 'APPROVED')"
            >
              批准
            </a-button>
            <a-button v-else type="link" size="small" @click="onChangeStatus(record, 'DRAFT')">退回草稿</a-button>
            <a-button type="link" size="small" danger @click="onDelete(record)">删除</a-button>
          </template>
        </template>
      </a-table>
    </template>

    <!-- ==================== 编辑视图 ==================== -->
    <template v-else>
      <div class="bsg-form">
        <div class="bsg-form-row">
          <span class="bsg-label">名称</span>
          <a-input v-model:value="form.name" placeholder="留空则按行号自动命名" style="width:240px" />
          <a-checkbox v-model:checked="form.atomicReplace">整组替换（不允许拆开换）</a-checkbox>
          <a-checkbox v-model:checked="form.enabled">启用</a-checkbox>
        </div>
        <div class="bsg-form-row">
          <span class="bsg-label">说明</span>
          <a-input
            v-model:value="form.description"
            placeholder="为什么成组换（例：三个分立元件被一颗集成模块整组替换）"
            style="flex:1"
          />
        </div>
      </div>

      <!-- 原料侧 -->
      <div class="bsg-section">
        <div class="bsg-section-head">
          <span class="bsg-section-title">
            原料侧 · {{ scopeName ? `「${scopeName}」的下挂` : '被替换的 BOM 行' }}
          </span>
          <span class="bsg-section-hint">已选 {{ selectedSourceOids.length }} 条（数量默认取该行用量，可改）</span>
        </div>
        <a-table
          :columns="sourceColumns"
          :data-source="sourceOptions"
          :pagination="false"
          row-key="oid"
          size="small"
          :scroll="{ y: 190 }"
          :row-selection="{ selectedRowKeys: selectedSourceOids, onChange: onSourceSelect }"
          :locale="{ emptyText: '本版 BOM 没有可作为原料侧的行' }"
        >
          <template #bodyCell="{ column, record }">
            <template v-if="column.key === 'qty'">
              <a-input-number
                v-if="selectedSourceOids.includes(record.oid)"
                v-model:value="sourceQty[record.oid]"
                :min="0"
                :step="1"
                size="small"
                style="width:90px"
              />
              <span v-else class="bsg-muted">{{ record.quantity ?? '-' }}</span>
            </template>
          </template>
        </a-table>
      </div>

      <!-- 替代侧 -->
      <div class="bsg-section">
        <div class="bsg-section-head">
          <span class="bsg-section-title">替代侧 · 整组替换上去的物料</span>
          <span class="bsg-section-hint">已选 {{ selectedSubOids.length }} 颗</span>
        </div>
        <div class="bsg-filter">
          <span class="bsg-label">所属容器</span>
          <a-select
            v-model:value="containerOid"
            placeholder="选择产品系列/型号"
            allow-clear
            show-search
            style="width:220px"
            @change="onContainerChange"
          >
            <a-select-option v-for="o in displayContainerOptions" :key="o.value" :value="o.value">
              {{ o.label }}
            </a-select-option>
          </a-select>
          <a-input-search
            v-model:value="keyword"
            placeholder="按名称或编码搜索"
            style="flex:1;min-width:160px"
            @search="loadParts"
          />
        </div>
        <a-table
          :columns="pickColumns"
          :data-source="substitutableParts"
          :loading="partsLoading"
          :pagination="false"
          row-key="oid"
          size="small"
          :scroll="{ y: 190 }"
          :row-selection="{ selectedRowKeys: selectedSubOids, onChange: onSubSelect }"
          :locale="{ emptyText: containerOid ? '该容器下没有可选物料' : '请先选择所属容器' }"
        >
          <template #bodyCell="{ column, record }">
            <template v-if="column.key === 'type'">
              {{ record.typeDefinitionName || record.typeDefinitionCode || '-' }}
            </template>
            <template v-else-if="column.key === 'qty'">
              <a-input-number
                v-if="selectedSubOids.includes(record.oid)"
                v-model:value="subQty[record.oid]"
                :min="0"
                :step="1"
                size="small"
                style="width:90px"
              />
              <span v-else class="bsg-muted">1</span>
            </template>
          </template>
        </a-table>
      </div>
    </template>

    <template #footer>
      <div class="bsg-footer">
        <template v-if="editing">
          <a-button @click="editing = false">返回列表</a-button>
          <a-button type="primary" :loading="saving" @click="save">
            {{ editingOid ? '保存修改' : '创建替代组' }}
          </a-button>
        </template>
        <template v-else>
          <a-button @click="close">关闭</a-button>
        </template>
      </div>
    </template>
  </a-modal>
</template>

<script setup>
import { computed, ref, watch } from 'vue'
import { message, Modal } from 'ant-design-vue'
import { PlusOutlined } from '@ant-design/icons-vue'
import {
  getBomSubstituteGroups,
  createBomSubstituteGroup,
  updateBomSubstituteGroup,
  deleteBomSubstituteGroup,
  changeBomSubstituteGroupStatus,
  getPartsByContainer,
} from '@/api'

const props = defineProps({
  open: { type: Boolean, default: false },
  /** 组的挂载点：当前查看的父件迭代 oid */
  parentIterationOid: { type: String, default: '' },
  /** 标题里显示的父件名 */
  partName: { type: String, default: '' },
  /** 原料侧的父项名（左侧选中行）—— 原料侧候选取的就是它的下挂 */
  scopeName: { type: String, default: '' },
  /** 可作原料侧的 BOM 行（宿主把它拍平成 [{ oid, lineNumber, partNumber, partName, quantity, unit }]） */
  sourceOptions: { type: Array, default: () => [] },
  /** 选替代件的容器下拉选项（复用宿主的系列/型号清单） */
  containerOptions: { type: Array, default: () => [] },
  defaultContainerOid: { type: String, default: '' },
  defaultContainerType: { type: String, default: '' },
})
const emit = defineEmits(['update:open', 'changed'])

const visible = computed({
  get: () => props.open,
  set: (v) => emit('update:open', v),
})

const loading = ref(false)
const saving = ref(false)
const groups = ref([])

/** 列表视图 / 编辑视图 */
const editing = ref(false)
/** 正在编辑的组 oid（空 = 新建） */
const editingOid = ref('')

const form = ref({ name: '', description: '', atomicReplace: true, enabled: true })
const selectedSourceOids = ref([])
const sourceQty = ref({})
const selectedSubOids = ref([])
const subQty = ref({})

// 替代侧选件
const containerOid = ref(null)
const keyword = ref('')
const parts = ref([])
const partsLoading = ref(false)

// 列宽合计 = 1040，与下面 a-table 的 :scroll="{ x: 1040 }" 对齐：
// 弹窗再窄也不会把某一列压到 0（那样表头文字会互相挤在一起）
const listColumns = [
  { title: '替代组', key: 'name', ellipsis: true, width: 200 },
  { title: '原料侧（被替换的行）', key: 'sources', width: 260 },
  { title: '替代侧（整组换上）', key: 'substitutes', width: 220 },
  { title: '约束', key: 'flags', width: 110 },
  { title: '状态', key: 'status', width: 80 },
  { title: '操作', key: 'action', width: 170 },
]

const sourceColumns = [
  { title: '行号', dataIndex: 'lineNumber', key: 'lineNumber', width: 70, align: 'center' },
  { title: '编码', dataIndex: 'partNumber', key: 'partNumber', width: 150 },
  { title: '名称', dataIndex: 'partName', key: 'partName', ellipsis: true },
  { title: '用量', key: 'qty', width: 110, align: 'center' },
]

const pickColumns = [
  { title: '编码', dataIndex: 'number', key: 'number', width: 150 },
  { title: '名称', dataIndex: 'name', key: 'name', ellipsis: true },
  // 类型走 bodyCell 槽：零件接口给的是 typeDefinitionName / typeDefinitionCode 两个字段，
  // 哪个有值显示哪个（只写 key 不写 dataIndex 的列 a-table 不会自动取值，会显示空白）
  { title: '类型', key: 'type', width: 110 },
  { title: '数量', key: 'qty', width: 110, align: 'center' },
]

/** 组状态 → 中文 + 颜色（与 BomSubstituteGroup 注释同一套） */
const STATUS_MAP = {
  DRAFT: { label: '草稿', color: 'default' },
  APPROVED: { label: '已批准', color: 'success' },
  OBSOLETE: { label: '已废弃', color: 'default' },
}
function statusOf(status) {
  return STATUS_MAP[status] || { label: status || '-', color: 'default' }
}

/** 容器类型 → 可读文案（容器不是系列/型号时给个能看的兜底项，理由见局部替代弹窗） */
const CONTAINER_TYPE_LABEL = {
  PRODUCT_LINE: '产品系列',
  LINE: '产品系列',
  PRODUCT_MODEL: '产品型号',
  MODEL: '产品型号',
  CORP_RESOURCE: '企业资源',
  FOLDER: '文件夹',
}

const displayContainerOptions = computed(() => {
  const opts = props.containerOptions || []
  const cur = containerOid.value
  if (!cur || opts.some((o) => o.value === cur)) return opts
  const typeLabel = CONTAINER_TYPE_LABEL[props.defaultContainerType] || '当前容器'
  return [{ value: cur, label: `${typeLabel}（当前容器）` }, ...opts]
})

/**
 * 替代侧候选：排除已被选作<b>原料侧</b>的那些物料。
 *
 * <p>把 C1 自己当成"C1 所在组的替代件"没有任何意义（还会让"整组替换"自相矛盾），
 * 所以在挑料这一步就不给选。已经选过的替代件即使被排除也仍算已选 —— 由服务端决定要不要拦。
 */
const substitutableParts = computed(() => {
  const taken = new Set(
    selectedSourceOids.value
      .map((oid) => (props.sourceOptions || []).find((r) => r.oid === oid)?.partOid)
      .filter(Boolean),
  )
  return (parts.value || []).filter((p) => p?.oid && !taken.has(p.oid))
})

watch(
  () => [props.open, props.parentIterationOid],
  ([open]) => {
    if (!open) return
    editing.value = false
    editingOid.value = ''
    load()
  },
)

/** 拉本版全部成组替代；列表变了就通知宿主（宿主据此刷新行内标记） */
async function load() {
  if (!props.parentIterationOid) {
    groups.value = []
    return
  }
  loading.value = true
  try {
    const res = await getBomSubstituteGroups(props.parentIterationOid)
    groups.value = res?.code === 200 ? res.data || [] : []
  } catch {
    groups.value = []
  } finally {
    loading.value = false
  }
  emit('changed', groups.value)
}

// ==================== 列表操作 ====================

function onCreate() {
  editingOid.value = ''
  form.value = { name: '', description: '', atomicReplace: true, enabled: true }
  // 默认把选中行的下挂整层带进来（成组替代最典型的用法就是"这一层一起换"），
  // 不需要的行由用户取消勾选；数量默认取各行原本的用量
  const rows = props.sourceOptions || []
  selectedSourceOids.value = rows.map((r) => r.oid).filter(Boolean)
  sourceQty.value = Object.fromEntries(rows.map((r) => [r.oid, r.quantity ?? 1]))
  selectedSubOids.value = []
  subQty.value = {}
  keyword.value = ''
  containerOid.value = props.defaultContainerOid || null
  editing.value = true
  loadParts()
}

function onEdit(record) {
  editingOid.value = record.oid
  form.value = {
    name: record.name || '',
    description: record.description || '',
    atomicReplace: record.atomicReplace !== false,
    enabled: record.enabled !== false,
  }
  selectedSourceOids.value = (record.sources || []).map((m) => m.bomLinkOid).filter(Boolean)
  sourceQty.value = Object.fromEntries(
    (record.sources || []).map((m) => [m.bomLinkOid, m.quantity ?? null]).filter(([k]) => k),
  )
  selectedSubOids.value = (record.substitutes || []).map((m) => m.partOid).filter(Boolean)
  subQty.value = Object.fromEntries(
    (record.substitutes || []).map((m) => [m.partOid, m.quantity ?? 1]).filter(([k]) => k),
  )
  keyword.value = ''
  containerOid.value = props.defaultContainerOid || null
  editing.value = true
  loadParts()
}

async function onChangeStatus(record, status) {
  try {
    const res = await changeBomSubstituteGroupStatus(record.oid, status)
    if (res?.code === 200) {
      message.success(status === 'APPROVED' ? '已批准（下游按整组替换解析）' : '已退回草稿')
      await load()
    } else {
      message.error(res?.message || '状态变更失败')
    }
  } catch (e) {
    message.error(e?.response?.data?.message || e.message)
  }
}

function onDelete(record) {
  Modal.confirm({
    title: `删除成组替代「${record.name || '未命名替代组'}」？`,
    content: `原料侧 ${(record.sources || []).length} 行、替代侧 ${(record.substitutes || []).length} 颗物料会一并删除；不会动 BOM 本身，也不会动局部替代。`,
    okText: '删除',
    okType: 'danger',
    cancelText: '取消',
    onOk: async () => {
      try {
        const res = await deleteBomSubstituteGroup(record.oid)
        if (res?.code === 200) {
          message.success('已删除')
          await load()
        } else {
          message.error(res?.message || '删除失败')
        }
      } catch (e) {
        message.error(e?.response?.data?.message || e.message)
      }
    },
  })
}

// ==================== 编辑 ====================

/** 勾选原料侧行：默认带上该行的用量，切走时把数量也清掉（避免留下看不见的脏值） */
function onSourceSelect(keys, rows) {
  selectedSourceOids.value = keys
  const next = { ...sourceQty.value }
  for (const row of rows || []) {
    if (next[row.oid] === undefined || next[row.oid] === null) {
      next[row.oid] = row.quantity ?? 1
    }
  }
  for (const oid of Object.keys(next)) {
    if (!keys.includes(oid)) delete next[oid]
  }
  sourceQty.value = next
}

function onSubSelect(keys, rows) {
  selectedSubOids.value = keys
  const next = { ...subQty.value }
  for (const row of rows || []) {
    if (next[row.oid] === undefined || next[row.oid] === null) next[row.oid] = 1
  }
  for (const oid of Object.keys(next)) {
    if (!keys.includes(oid)) delete next[oid]
  }
  subQty.value = next
}

function onContainerChange() {
  selectedSubOids.value = []
  subQty.value = {}
  loadParts()
}

/** 可选物料：排除已选之外的都展示（已选的仍留在列表里，否则勾选会"消失"） */
async function loadParts() {
  if (!containerOid.value) {
    parts.value = []
    return
  }
  partsLoading.value = true
  try {
    const res = await getPartsByContainer(containerOid.value, keyword.value.trim() || undefined)
    const arr = Array.isArray(res?.data) ? res.data : Array.isArray(res) ? res : []
    parts.value = arr.filter((p) => p?.oid)
  } catch {
    parts.value = []
  } finally {
    partsLoading.value = false
  }
}

async function save() {
  if (!props.parentIterationOid) {
    message.warning('尚未确定当前版本，无法保存')
    return
  }
  if (!selectedSourceOids.value.length) {
    message.warning('请至少选择一条被替换的 BOM 行（原料侧）')
    return
  }
  if (!selectedSubOids.value.length) {
    message.warning('请至少选择一个替代物料（替代侧）')
    return
  }
  const unitOf = (oid) => (props.sourceOptions || []).find((r) => r.oid === oid)?.unit || null
  const payload = {
    parentIterationOid: props.parentIterationOid,
    name: form.value.name?.trim() || null,
    description: form.value.description?.trim() || null,
    atomicReplace: form.value.atomicReplace,
    enabled: form.value.enabled,
    sources: selectedSourceOids.value.map((oid) => ({
      bomLinkOid: oid,
      quantity: sourceQty.value[oid] ?? null,
      unit: unitOf(oid),
    })),
    substitutes: selectedSubOids.value.map((oid) => ({
      partOid: oid,
      quantity: subQty.value[oid] ?? 1,
      unit: null,
    })),
  }
  saving.value = true
  try {
    const res = editingOid.value
      ? await updateBomSubstituteGroup(editingOid.value, payload)
      : await createBomSubstituteGroup(payload)
    if (res?.code === 200) {
      message.success(editingOid.value ? '已保存（已批准的组会退回草稿）' : '已创建成组替代（状态：草稿）')
      editing.value = false
      await load()
    } else {
      message.error(res?.message || '保存失败')
    }
  } catch (e) {
    message.error(e?.response?.data?.message || e.message)
  } finally {
    saving.value = false
  }
}

function close() {
  emit('update:open', false)
}
</script>

<style scoped>
.bsg-toolbar {
  display: flex;
  align-items: flex-start;
  gap: 12px;
  margin-bottom: 10px;
}
.bsg-intro {
  flex: 1;
  font-size: 12px;
  color: #8c8c8c;
  line-height: 1.7;
}
.bsg-footer {
  display: flex;
  justify-content: flex-end;
  gap: 8px;
}
.bsg-name {
  font-weight: 500;
  color: #1a1a2e;
}
.bsg-desc {
  font-size: 12px;
  color: #8c8c8c;
}
.bsg-member {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 12px;
  line-height: 1.9;
}
.bsg-member code {
  background: #f5f5f5;
  padding: 0 4px;
  border-radius: 3px;
  color: #595959;
}
.bsg-member-name {
  color: #262626;
}
.bsg-member-code {
  color: #8c8c8c;
}
.bsg-qty {
  color: #8c8c8c;
}
.bsg-muted {
  color: #bfbfbf;
}
.bsg-form {
  margin-bottom: 12px;
}
.bsg-form-row {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 8px;
}
.bsg-label {
  font-size: 12px;
  color: #8c8c8c;
  white-space: nowrap;
}
.bsg-section {
  margin-bottom: 14px;
}
.bsg-section-head {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-bottom: 6px;
}
.bsg-section-title {
  font-weight: 600;
  color: #1a1a2e;
}
.bsg-section-hint {
  font-size: 12px;
  color: #8c8c8c;
}
.bsg-filter {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 8px;
}
</style>
