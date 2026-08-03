<template>
  <div class="nr-page">
    <!-- 页头 -->
    <div class="nr-header">
      <div class="nr-header-left">
        <h3 class="nr-title">编码规则定义</h3>
        <span class="nr-subtitle">管理业务对象的编码生成规则，支持固定文本、日期、流水号等段类型组合</span>
      </div>
    </div>

    <!-- 统计栏 -->
    <div class="nr-stats-bar">
      <div class="nr-stat-item">
        <NumberOutlined class="nr-stat-icon" />
        <span class="nr-stat-value">{{ rules.length }}</span>
        <span class="nr-stat-label">规则总数</span>
      </div>
      <a-divider type="vertical" style="height:24px" />
      <div class="nr-stat-item">
        <a-tag color="green" size="small">启用</a-tag>
        <span class="nr-stat-value">{{ enabledCount }}</span>
      </div>
      <div class="nr-stat-item">
        <a-tag color="default" size="small">禁用</a-tag>
        <span class="nr-stat-value">{{ disabledCount }}</span>
      </div>
    </div>

    <!-- 表格 -->
    <div class="nr-table-wrap">
      <DataTable
        :columns="columns"
        :data-source="rules"
        :loading="loading"
        search-placeholder="搜索规则编码 / 名称 / 描述..."
        :search-fields="['code', 'name', 'description']"
        :enable-resize="true"
        :show-column-toggle="true"
        :max-height="420"
        row-key="code"
        size="middle"
      >
        <template #toolbar>
          <a-button type="primary" size="small" @click="openCreate">
            <template #icon><PlusOutlined /></template>
            新增规则
          </a-button>
        </template>

        <template #bodyCell="{ column, record }">
          <template v-if="column.key === 'code'">
            <a-tag color="blue">{{ record.code }}</a-tag>
          </template>
          <template v-else-if="column.key === 'segments'">
            <span class="nr-segment-preview">{{ formatSegments(record.segments) }}</span>
          </template>
          <template v-else-if="column.key === 'source'">
            <a-tag :color="isPlatformData(record) ? 'purple' : 'green'" size="small">
              {{ isPlatformData(record) ? '平台' : '本租户' }}
            </a-tag>
          </template>
          <template v-else-if="column.key === 'enabled'">
            <a-tag :color="record.enabled ? 'success' : 'default'">
              {{ record.enabled ? '启用' : '禁用' }}
            </a-tag>
          </template>
          <template v-else-if="column.key === 'createdAt'">
            {{ formatTime(record.createdAt) }}
          </template>
          <template v-else-if="column.key === 'action'">
            <a-space size="small">
              <a-button type="link" size="small" @click="openPreview(record)">预览</a-button>
              <a-button type="link" size="small" @click="openEdit(record)" :disabled="!canEdit(record)">编辑</a-button>
              <a-popconfirm v-if="canEdit(record)" title="确定删除该编码规则？" @confirm="handleDelete(record)">
                <a-button type="link" size="small" danger>删除</a-button>
              </a-popconfirm>
            </a-space>
          </template>
        </template>
      </DataTable>
    </div>

    <!-- 新增/编辑弹窗 -->
    <a-modal
      v-model:open="modal.visible"
      :title="modal.isEdit ? '编辑编码规则' : '新增编码规则'"
      :confirm-loading="modal.saving"
      @ok="handleSave"
      width="720px"
      :ok-text="modal.isEdit ? '保存' : '创建'"
    >
      <a-form :model="modal.form" layout="vertical" class="nr-modal-form">
        <a-row :gutter="12">
          <a-col :span="8">
            <a-form-item label="规则编码" required>
              <a-input
                v-model:value="modal.form.code"
                placeholder="如 PART-NO"
                :disabled="modal.isEdit"
              />
            </a-form-item>
          </a-col>
          <a-col :span="8">
            <a-form-item label="规则名称" required>
              <a-input v-model:value="modal.form.name" placeholder="如 零件编码" />
            </a-form-item>
          </a-col>
          <a-col :span="4">
            <a-form-item label="状态">
              <a-switch v-model:checked="modal.form.enabled" checked-children="启用" un-checked-children="禁用" />
            </a-form-item>
          </a-col>
        </a-row>
        <a-form-item label="描述">
          <a-input v-model:value="modal.form.description" placeholder="规则用途说明" />
        </a-form-item>

        <!-- 段定义 -->
        <div class="nr-segments-header">
          <span class="nr-segments-title">段定义（从上到下依次拼接）</span>
          <a-button type="dashed" size="small" @click="addSegment">
            <template #icon><PlusOutlined /></template>
            添加段
          </a-button>
        </div>
        <div class="nr-segments-list">
          <div
            v-for="(seg, idx) in modal.form.segments"
            :key="idx"
            class="nr-segment-row"
          >
            <a-row :gutter="8" align="middle">
              <a-col :span="3">
                <span class="nr-seg-index">{{ idx + 1 }}</span>
              </a-col>
              <a-col :span="4">
                <a-select v-model:value="seg.segmentType" size="small" @change="onSegmentTypeChange(seg)">
                  <a-select-option value="CONST">固定文本</a-select-option>
                  <a-select-option value="SEPARATOR">分隔符</a-select-option>
                  <a-select-option value="YEAR">年份</a-select-option>
                  <a-select-option value="MONTH">月份</a-select-option>
                  <a-select-option value="DAY">日期</a-select-option>
                  <a-select-option value="SERIAL">流水号</a-select-option>
                  <a-select-option value="CLASSIFICATION">分类码</a-select-option>
                </a-select>
              </a-col>
              <!-- CONST / SEPARATOR → fixedValue -->
              <a-col v-if="seg.segmentType === 'CONST' || seg.segmentType === 'SEPARATOR'" :span="6">
                <a-input v-model:value="seg.fixedValue" size="small" :placeholder="seg.segmentType === 'SEPARATOR' ? '-' : '固定值'" />
              </a-col>
              <!-- YEAR / MONTH / DAY → dateFormat -->
              <a-col v-if="seg.segmentType === 'YEAR' || seg.segmentType === 'MONTH' || seg.segmentType === 'DAY'" :span="4">
                <a-input v-model:value="seg.dateFormat" size="small" :placeholder="seg.segmentType === 'YEAR' ? 'yyyy' : seg.segmentType === 'MONTH' ? 'MM' : 'dd'" />
              </a-col>
              <!-- SERIAL → serialLength + serialStart -->
              <a-col v-if="seg.segmentType === 'SERIAL'" :span="4">
                <a-input-number v-model:value="seg.serialLength" size="small" :min="1" :max="10" placeholder="位数" style="width:100%" />
              </a-col>
              <a-col v-if="seg.segmentType === 'SERIAL'" :span="4">
                <a-input-number v-model:value="seg.serialStart" size="small" :min="0" placeholder="起始值" style="width:100%" />
              </a-col>
              <!-- CLASSIFICATION → 选择分类节点 -->
              <a-col v-if="seg.segmentType === 'CLASSIFICATION'" :span="6">
                <a-button size="small" block @click="openClsTreeForSeg(seg, idx)">
                  <template #icon><ApartmentOutlined /></template>
                  {{ seg.clsDisplayName || '选择分类' }}
                </a-button>
              </a-col>
              <a-col :span="3">
                <a-input v-model:value="seg.description" size="small" placeholder="备注" />
              </a-col>
              <a-col :span="2">
                <a-button type="link" size="small" danger @click="removeSegment(idx)">
                  <DeleteOutlined />
                </a-button>
              </a-col>
            </a-row>
          </div>
          <a-empty v-if="modal.form.segments.length === 0" description="暂未添加段定义" :image-style="{ height: '28px' }" />
        </div>

        <!-- 预览区 -->
        <div v-if="modal.previewCode" class="nr-preview-box">
          <span class="nr-preview-label">编码格式预览：</span>
          <code class="nr-preview-value">{{ modal.previewCode }}</code>
        </div>
      </a-form>
    </a-modal>

    <!-- 编码生成预览弹窗 -->
    <a-modal
      v-model:open="previewModal.visible"
      title="编码生成预览"
      :footer="null"
      width="500px"
    >
      <template v-if="previewModal.rule">
        <a-descriptions :column="2" size="small" bordered>
          <a-descriptions-item label="规则编码">{{ previewModal.rule.code }}</a-descriptions-item>
          <a-descriptions-item label="规则名称">{{ previewModal.rule.name }}</a-descriptions-item>
          <a-descriptions-item label="编码格式" :span="2">
            <code>{{ formatSegments(previewModal.rule.segments) }}</code>
          </a-descriptions-item>
        </a-descriptions>

        <a-divider />

        <div class="nr-gen-section">
          <a-button type="primary" :loading="previewModal.generating" @click="doGenerate">
            <template #icon><ThunderboltOutlined /></template>
            生成下一个编码
          </a-button>
          <a-input
            v-if="previewModal.generated"
            :value="previewModal.generated"
            readonly
            class="nr-gen-result"
          >
            <template #suffix>
              <a-button type="link" size="small" @click="copyCode">复制</a-button>
            </template>
          </a-input>
        </div>
      </template>
    </a-modal>

    <!-- 分类树选择弹窗（编码规则段配置用） -->
    <a-modal v-model:open="clsTreeModal.visible" title="选择分类节点" width="520px" :footer="null">
      <a-spin :spinning="clsTreeModal.loading" size="small">
        <a-empty v-if="!clsTreeModal.loading && clsTreeModal.tree.length === 0" description="暂无可用的分类，请先到分类管理模块创建" :image-style="{ height: '32px' }" />
        <div v-if="clsTreeModal.tree.length > 0">
          <div style="display:flex;align-items:center;gap:8px;margin-bottom:8px">
            <a-input-search v-model:value="clsTreeModal.searchText" placeholder="搜索分类" allow-clear size="small" style="flex:1" />
            <a-button size="small" @click="expandAllClsTree">展开</a-button>
            <a-button size="small" @click="collapseAllClsTree">折叠</a-button>
          </div>
          <div v-if="clsTreeModal.breadcrumb.length > 0" style="margin-bottom:6px;font-size:12px;color:#8c8c8c">
            所属: <a-breadcrumb style="display:inline">
              <a-breadcrumb-item v-for="(b, i) in clsTreeModal.breadcrumb" :key="i">{{ b }}</a-breadcrumb-item>
            </a-breadcrumb>
          </div>
          <div style="max-height:360px;overflow-y:auto;border:1px solid #f0f0f0;border-radius:6px;padding:8px">
            <a-tree
              ref="nrClsTreeRef"
              :tree-data="filteredNrClsTree"
              :field-names="{ children: 'children', title: 'displayName', key: 'oid' }"
              :default-expand-all="true"
              :selected-keys="clsTreeModal.selectedOid ? [clsTreeModal.selectedOid] : []"
              :expanded-keys="clsTreeModal.expandedKeys"
              @select="onNrClsTreeSelect"
              @expand="onNrClsTreeExpand"
            >
              <template #title="nodeData">
                <span>{{ nodeData.displayName || nodeData.name }}</span>
                <code v-if="nodeData.code" style="margin-left:6px;font-size:11px;color:#8c8c8c">{{ nodeData.code }}</code>
              </template>
            </a-tree>
          </div>
        </div>
      </a-spin>
    </a-modal>
  </div>
</template>

<script setup>
import { ref, reactive, computed, watch, onMounted } from 'vue'
import { message } from 'ant-design-vue'
import {
  PlusOutlined, DeleteOutlined, NumberOutlined, ThunderboltOutlined, ApartmentOutlined
} from '@ant-design/icons-vue'
import {
  getNumberRules, getNumberRule, createNumberRule,
  updateNumberRule, deleteNumberRule, generateNumber, previewNumber,
  getClassificationTree,
} from '@/api'
import DataTable from '@/components/DataTable.vue'

// ==================== 数据 ====================
const rules = ref([])
const loading = ref(false)

const enabledCount = computed(() => rules.value.filter(r => r.enabled !== false).length)
const disabledCount = computed(() => rules.value.filter(r => r.enabled === false).length)

import { useUserStore } from '@/stores/user'
const userStore = useUserStore()
const PLATFORM_TENANT_OID = '00000000-0000-0000-0000-000000000000'
const isPlatformAdmin = computed(() => userStore.roles?.includes('PLATFORM_ADMIN'))
const isPlatformData = (record) => record.tenantOid === PLATFORM_TENANT_OID
const canEdit = (record) => isPlatformAdmin.value || !isPlatformData(record)

const columns = [
  { title: '规则编码', dataIndex: 'code', key: 'code', width: 120 },
  { title: '名称', dataIndex: 'name', key: 'name', width: 110 },
  { title: '编码格式', key: 'segments', width: 200, ellipsis: true },
  { title: '来源', key: 'source', width: 70 },
  { title: '状态', key: 'enabled', width: 70 },
  { title: '描述', dataIndex: 'description', key: 'description', ellipsis: true },
  { title: '创建时间', dataIndex: 'createdAt', key: 'createdAt', width: 150 },
  { title: '操作', key: 'action', width: 160 }
]

// ==================== 段格式化 ====================
const SEGMENT_LABELS = {
  CONST: '固定',
  SEPARATOR: '分隔',
  YEAR: '年',
  MONTH: '月',
  DAY: '日',
  SERIAL: '流水',
  CLASSIFICATION: '分类'
}

function formatSegments(segments) {
  if (!segments || segments.length === 0) return '—'
  return segments.map(s => formatSegment(s)).join('')
}

function formatSegment(seg) {
  switch (seg.segmentType) {
    case 'CONST':
      return seg.fixedValue || ''
    case 'SEPARATOR':
      return seg.fixedValue || '-'
    case 'YEAR':
      return `{${seg.dateFormat || 'yyyy'}}`
    case 'MONTH':
      return `{${seg.dateFormat || 'MM'}}`
    case 'DAY':
      return `{${seg.dateFormat || 'dd'}}`
    case 'SERIAL':
      return `{${'0'.repeat(seg.serialLength || 4)}}`
    case 'CLASSIFICATION':
      return seg.clsPreview || '{分类码}'
    default:
      return `{?}`
  }
}

// ==================== CRUD ====================
async function loadRules() {
  loading.value = true
  try {
    const res = await getNumberRules()
    if (res.code === 200) rules.value = res.data || []
  } catch { message.error('加载编码规则失败') }
  finally { loading.value = false }
}

// ==================== 新增/编辑弹窗 ====================
const modal = reactive({
  visible: false,
  isEdit: false,
  saving: false,
  previewCode: '',
  form: { code: '', name: '', description: '', enabled: true, segments: [] }
})

function resetModalForm() {
  modal.form = { code: '', name: '', description: '', enabled: true, segments: [] }
  modal.previewCode = ''
}

function openCreate() {
  modal.isEdit = false
  resetModalForm()
  modal.visible = true
}

async function openEdit(record) {
  modal.isEdit = true
  modal.saving = false
  modal.previewCode = ''
  try {
    const res = await getNumberRule(record.code)
    if (res.code === 200 && res.data) {
      const r = res.data
      modal.form = {
        code: r.code,
        name: r.name,
        description: r.description || '',
        enabled: r.enabled !== false,
        segments: (r.segments || []).map(s => ({
          oid: s.oid,
          segmentType: s.segmentType,
          sortOrder: s.sortOrder,
          fixedValue: s.fixedValue || '',
          dateFormat: s.dateFormat || '',
          serialLength: s.serialLength,
          serialStart: s.serialStart,
          currentValue: s.currentValue,
          description: s.description || '',
          config: s.config || null,
          clsOid: s.config ? (() => { try { return JSON.parse(s.config).classificationOid } catch { return null } })() : null,
          clsDisplayName: s.config ? (() => { try { return JSON.parse(s.config).clsDisplayName } catch { return null } })() : '',
          clsPreview: s.config ? (() => { try { return JSON.parse(s.config).clsPreview } catch { return null } })() : ''
        }))
      }
      updatePreview()
    }
  } catch { message.error('加载编码规则详情失败') }
  modal.visible = true
}

function addSegment() {
  modal.form.segments.push({
    segmentType: 'CONST',
    sortOrder: modal.form.segments.length + 1,
    fixedValue: '',
    dateFormat: '',
    serialLength: 4,
    serialStart: 1,
    description: '',
    config: null,
    clsOid: null,
    clsDisplayName: '',
    clsPreview: ''
  })
}

function removeSegment(idx) {
  modal.form.segments.splice(idx, 1)
  // 重新排序
  modal.form.segments.forEach((s, i) => s.sortOrder = i + 1)
  updatePreview()
}

function onSegmentTypeChange(seg) {
  // 切换段类型时重置相关字段
  seg.fixedValue = ''
  seg.dateFormat = ''
  seg.serialLength = 4
  seg.serialStart = 1
  seg.config = null
  seg.clsOid = null
  seg.clsDisplayName = ''
  seg.clsPreview = ''
  updatePreview()
}

function updatePreview() {
  if (modal.form.segments.length === 0) {
    modal.previewCode = ''
    return
  }
  modal.previewCode = modal.form.segments.map(s => formatSegment(s)).join('')
}

// 监听段变化实时更新预览（Modal 内使用）
watch(() => modal.form.segments, () => {
  updatePreview()
}, { deep: true })

async function handleSave() {
  const { code, name, segments } = modal.form
  if (!code?.trim() || !name?.trim()) {
    message.warning('编码和名称不能为空')
    return
  }
  if (segments.length === 0) {
    message.warning('请至少添加一个段定义')
    return
  }

  modal.saving = true
  try {
    const payload = {
      code: code.trim().toUpperCase(),
      name: name.trim(),
      description: modal.form.description || '',
      enabled: modal.form.enabled,
      segments: segments.map((s, i) => ({
        segmentType: s.segmentType,
        sortOrder: i + 1,
        fixedValue: s.fixedValue || null,
        dateFormat: s.dateFormat || null,
        serialLength: s.serialLength || null,
        serialStart: s.serialStart || null,
        description: s.description || null,
        config: s.config || null
      }))
    }

    let res
    if (modal.isEdit) {
      res = await updateNumberRule(code.trim(), payload)
    } else {
      res = await createNumberRule(payload)
    }

    if (res.code === 200) {
      message.success(modal.isEdit ? '编码规则已更新' : '编码规则已创建')
      modal.visible = false
      await loadRules()
    } else {
      message.error(res.message || '操作失败')
    }
  } catch (e) {
    message.error('操作失败')
  }
  finally { modal.saving = false }
}

async function handleDelete(record) {
  try {
    const res = await deleteNumberRule(record.code)
    if (res.code === 200) {
      message.success('编码规则已删除')
      await loadRules()
    } else {
      message.error(res.message || '删除失败')
    }
  } catch { message.error('删除失败') }
}

// ==================== 分类树弹窗（编码规则段配置用） ====================
const nrClsTreeRef = ref(null)
const clsTreeModal = reactive({
  visible: false,
  loading: false,
  searchText: '',
  tree: [],
  expandedKeys: [],
  selectedOid: null,
  breadcrumb: [],
  targetSeg: null,
  targetIdx: -1
})

const filteredNrClsTree = computed(() => {
  const keyword = clsTreeModal.searchText?.trim().toLowerCase()
  if (!keyword) return clsTreeModal.tree
  const filter = (nodes) => {
    const result = []
    for (const node of nodes) {
      const name = (node.displayName || node.name || '').toLowerCase()
      const code = (node.code || '').toLowerCase()
      const children = node.children ? filter(node.children) : []
      if (name.includes(keyword) || code.includes(keyword) || children.length > 0) {
        result.push({ ...node, children: children.length > 0 ? children : node.children })
      }
    }
    return result
  }
  return filter(clsTreeModal.tree)
})

async function openClsTreeForSeg(seg, idx) {
  clsTreeModal.targetSeg = seg
  clsTreeModal.targetIdx = idx
  clsTreeModal.selectedOid = seg.clsOid || null
  clsTreeModal.searchText = ''
  clsTreeModal.breadcrumb = []
  clsTreeModal.loading = true
  clsTreeModal.visible = true
  try {
    const res = await getClassificationTree()
    clsTreeModal.tree = res?.data || res || []
    clsTreeModal.expandedKeys = collectAllClsKeys(clsTreeModal.tree)
  } catch { clsTreeModal.tree = [] }
  finally { clsTreeModal.loading = false }
}

function collectAllClsKeys(nodes) {
  const keys = []
  for (const node of nodes) {
    if (node.children && node.children.length > 0) {
      keys.push(node.oid)
      keys.push(...collectAllClsKeys(node.children))
    }
  }
  return keys
}

function expandAllClsTree() {
  clsTreeModal.expandedKeys = collectAllClsKeys(clsTreeModal.tree)
}

function collapseAllClsTree() {
  clsTreeModal.expandedKeys = []
}

function onNrClsTreeExpand(keys) {
  clsTreeModal.expandedKeys = keys
}

function buildClsBreadcrumb(classificationOid) {
  const path = []
  const find = (nodes, targetOid, parents) => {
    for (const node of nodes) {
      if (node.oid === targetOid) {
        path.push(...parents, node.displayName || node.name)
        return true
      }
      if (node.children && find(node.children, targetOid, [...parents, node.displayName || node.name])) {
        return true
      }
    }
    return false
  }
  find(clsTreeModal.tree, classificationOid, [])
  clsTreeModal.breadcrumb = path
}

function buildClsPreview(classificationOid) {
  const codes = []
  const find = (nodes, targetOid, pathCodes) => {
    for (const node of nodes) {
      if (node.oid === targetOid) {
        codes.push(...pathCodes, node.code || '')
        return true
      }
      if (node.children && find(node.children, targetOid, [...pathCodes, node.code || ''])) {
        return true
      }
    }
    return false
  }
  find(clsTreeModal.tree, classificationOid, [])
  return codes.join('')
}

function onNrClsTreeSelect(selectedKeys, { node }) {
  if (!selectedKeys || selectedKeys.length === 0) return
  const oid = selectedKeys[0]
  const displayName = node.displayName || node.name || ''
  const preview = buildClsPreview(oid)
  buildClsBreadcrumb(oid)

  // 更新段数据
  if (clsTreeModal.targetSeg) {
    clsTreeModal.targetSeg.clsOid = oid
    clsTreeModal.targetSeg.clsDisplayName = displayName
    clsTreeModal.targetSeg.clsPreview = preview
    clsTreeModal.targetSeg.config = JSON.stringify({ classificationOid: oid, clsDisplayName: displayName, clsPreview: preview })
  }
  clsTreeModal.selectedOid = oid
  clsTreeModal.visible = false
  updatePreview()
}

// ==================== 预览/生成弹窗 ====================
const previewModal = reactive({
  visible: false,
  rule: null,
  generated: '',
  generating: false
})

async function openPreview(record) {
  previewModal.rule = null
  previewModal.generated = ''
  previewModal.generating = false
  try {
    const res = await getNumberRule(record.code)
    if (res.code === 200 && res.data) {
      previewModal.rule = res.data
    }
  } catch { message.error('加载规则详情失败') }
  previewModal.visible = true
}

async function doGenerate() {
  if (!previewModal.rule) return
  previewModal.generating = true
  try {
    const res = await generateNumber(previewModal.rule.code)
    if (res.code === 200) {
      previewModal.generated = res.data
    } else {
      message.error(res.message || '生成失败')
    }
  } catch { message.error('生成编码失败') }
  finally { previewModal.generating = false }
}

function copyCode() {
  if (previewModal.generated) {
    navigator.clipboard.writeText(previewModal.generated).then(() => {
      message.success('已复制到剪贴板')
    }).catch(() => {
      message.info(previewModal.generated)
    })
  }
}

function formatTime(str) {
  if (!str) return '-'
  return str.replace('T', ' ').substring(0, 19)
}

// ==================== 生命周期 ====================
onMounted(() => {
  loadRules()
})
</script>

<style scoped>
.nr-page {
  display: flex;
  flex-direction: column;
  height: 100%;
  min-height: 0;
}

/* 页头 */
.nr-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 0 0 10px;
  flex-shrink: 0;
}

.nr-header-left {
  display: flex;
  align-items: baseline;
  gap: 8px;
}

.nr-title {
  margin: 0;
  font-size: 18px;
  font-weight: 600;
  color: #1a1a2e;
}

.nr-subtitle {
  font-size: 12px;
  color: #8c8c8c;
}

/* 统计栏 */
.nr-stats-bar {
  display: flex;
  align-items: center;
  gap: 20px;
  padding: 8px 16px;
  background: #fafafa;
  border: 1px solid #f0f0f0;
  border-radius: 6px;
  flex-shrink: 0;
  margin-bottom: 6px;
}

/* 表格区域 */
.nr-table-wrap {
  flex: 1;
  min-height: 0;
  display: flex;
  flex-direction: column;
  overflow: hidden;
}

.nr-stat-item {
  display: flex;
  align-items: center;
  gap: 5px;
}

.nr-stat-icon {
  font-size: 14px;
  color: #1677ff;
}

.nr-stat-value {
  font-size: 14px;
  font-weight: 700;
  color: #1a1a2e;
  min-width: 24px;
  text-align: center;
}

.nr-stat-label {
  font-size: 12px;
  color: #8c8c8c;
}


.nr-segment-preview {
  font-family: 'Courier New', monospace;
  font-size: 12px;
  color: #595959;
}

/* 弹窗表单 */
.nr-modal-form {
  max-height: 60vh;
  overflow-y: auto;
  padding-right: 4px;
}

/* 段定义区域 */
.nr-segments-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 8px;
  padding-bottom: 6px;
  border-bottom: 1px solid #f0f0f0;
}

.nr-segments-title {
  font-size: 13px;
  font-weight: 500;
  color: #434343;
}

.nr-segments-list {
  display: flex;
  flex-direction: column;
  gap: 4px;
  margin-bottom: 10px;
}

.nr-segment-row {
  padding: 6px 8px;
  background: #fafafa;
  border-radius: 4px;
  border: 1px solid #f0f0f0;
}

.nr-seg-index {
  font-size: 12px;
  color: #8c8c8c;
  padding-left: 4px;
}

/* 预览区 */
.nr-preview-box {
  margin-top: 10px;
  padding: 8px 12px;
  background: #e6f4ff;
  border-radius: 4px;
  border: 1px solid #91caff;
}

.nr-preview-label {
  font-size: 12px;
  color: #595959;
  margin-right: 8px;
}

.nr-preview-value {
  font-family: 'Courier New', monospace;
  font-size: 14px;
  font-weight: 600;
  color: #1677ff;
}

/* 生成预览弹窗 */
.nr-gen-section {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.nr-gen-result {
  font-family: 'Courier New', monospace;
  font-size: 16px;
}
</style>
