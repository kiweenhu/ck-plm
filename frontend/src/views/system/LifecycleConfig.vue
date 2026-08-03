<template>
  <div class="lc-page">
    <!-- 页头 -->
    <div class="lc-header">
      <div class="lc-header-left">
        <h3 class="lc-title">生命周期管理</h3>
        <span class="lc-subtitle">定义业务对象的生命周期状态与流转模板，控制对象从创建到归档的完整生命周期</span>
      </div>
    </div>

    <a-tabs v-model:activeKey="activeTab" class="lc-tabs">
      <!-- ==================== 生命周期状态 Tab ==================== -->
      <a-tab-pane key="status" tab="生命周期状态">
        <!-- 统计栏 -->
        <div class="lc-stats-bar">
          <div class="lc-stat-item">
            <RetweetOutlined class="lc-stat-icon" />
            <span class="lc-stat-value">{{ statuses.length }}</span>
            <span class="lc-stat-label">状态总数</span>
          </div>
        </div>

        <!-- 表格 -->
        <div class="lc-table-wrap">
          <DataTable
            :columns="statusColumns"
            :data-source="statuses"
            :loading="statusLoading"
            search-placeholder="搜索状态编码 / 名称..."
            :search-fields="['code', 'displayName']"
            :enable-resize="true"
            :show-column-toggle="true"
            :max-height="400"
            row-key="code"
            size="middle"
          >
            <template #toolbar>
              <a-button type="primary" size="small" @click="openStatusCreate">
                <template #icon><PlusOutlined /></template>
                新增状态
              </a-button>
            </template>

            <template #bodyCell="{ column, record }">
              <template v-if="column.key === 'code'">
                <a-tag color="blue">{{ record.code }}</a-tag>
              </template>
              <template v-else-if="column.key === 'displayName'">
                {{ record.displayName }}
              </template>
              <template v-else-if="column.key === 'createdAt'">
                {{ formatTime(record.createdAt) }}
              </template>
              <template v-else-if="column.key === 'source'">
                <a-tag :color="isPlatformData(record) ? 'purple' : 'green'" size="small">
                  {{ isPlatformData(record) ? '平台' : '本租户' }}
                </a-tag>
              </template>
              <template v-else-if="column.key === 'action'">
                <a-space size="small">
                  <a-button type="link" size="small" @click="openStatusEdit(record)" :disabled="!canEdit(record)">编辑</a-button>
                  <a-popconfirm
                    v-if="canEdit(record)"
                    title="确定删除该生命周期状态？"
                    :disabled="isCoreStatus(record.code)"
                    @confirm="handleStatusDelete(record)"
                  >
                    <a-button
                      type="link" size="small" danger
                      :disabled="isCoreStatus(record.code)"
                    >删除</a-button>
                  </a-popconfirm>
                </a-space>
              </template>
              <template v-else>
                {{ record[column.dataIndex] }}
              </template>
            </template>
          </DataTable>
        </div>
      </a-tab-pane>

      <!-- ==================== 生命周期模板 Tab ==================== -->
      <a-tab-pane key="template" tab="生命周期模板">
        <!-- 统计栏 -->
        <div class="lc-stats-bar">
          <div class="lc-stat-item">
            <ApartmentOutlined class="lc-stat-icon" />
            <span class="lc-stat-value">{{ templates.length }}</span>
            <span class="lc-stat-label">模板总数</span>
          </div>
          <a-divider type="vertical" style="height:24px" />
          <div class="lc-stat-item">
            <a-tag color="green" size="small">启用</a-tag>
            <span class="lc-stat-value">{{ activeTemplateCount }}</span>
          </div>
          <div class="lc-stat-item">
            <a-tag color="default" size="small">禁用</a-tag>
            <span class="lc-stat-value">{{ inactiveTemplateCount }}</span>
          </div>
        </div>

        <!-- 表格 -->
        <div class="lc-table-wrap">
          <DataTable
            :columns="templateColumns"
            :data-source="templates"
            :loading="templateLoading"
            search-placeholder="搜索模板编码 / 名称 / 描述..."
            :search-fields="['code', 'name', 'description']"
            :enable-resize="true"
            :show-column-toggle="true"
            :max-height="400"
            row-key="code"
            size="middle"
          >
            <template #toolbar>
              <a-button type="primary" size="small" @click="openTemplateCreate">
                <template #icon><PlusOutlined /></template>
                新增模板
              </a-button>
            </template>

            <template #bodyCell="{ column, record }">
              <template v-if="column.key === 'code'">
                <a-tag color="purple">{{ record.code }}</a-tag>
              </template>
              <template v-else-if="column.key === 'states'">
                <a-space size="2" wrap>
                  <a-tag v-for="(s, i) in record.states" :key="i" size="small" color="blue">
                    {{ s.statusDisplayName || s.statusCode }}
                    <template v-if="s.statusCode === record.initialStateCode">
                      <CheckCircleOutlined style="font-size:10px;margin-left:2px" />
                    </template>
                  </a-tag>
                  <span v-if="!record.states?.length" class="lc-empty-hint">—</span>
                </a-space>
              </template>
              <template v-else-if="column.key === 'transitions'">
                <span class="lc-trans-count">
                  {{ (record.transitions?.length || 0) + (record.rejections?.length || 0) }} 条规则
                </span>
              </template>
              <template v-else-if="column.key === 'active'">
                <a-tag :color="record.active ? 'success' : 'default'">
                  {{ record.active ? '启用' : '禁用' }}
                </a-tag>
              </template>
              <template v-else-if="column.key === 'createdAt'">
                {{ formatTime(record.createdAt) }}
              </template>
              <template v-else-if="column.key === 'source'">
                <a-tag :color="isPlatformData(record) ? 'purple' : 'green'" size="small">
                  {{ isPlatformData(record) ? '平台' : '本租户' }}
                </a-tag>
              </template>
              <template v-else-if="column.key === 'action'">
                <a-space size="small">
                  <a-button type="link" size="small" @click="openTemplateDetail(record)">详情</a-button>
                  <a-button type="link" size="small" @click="openTemplateEdit(record)" :disabled="!canEdit(record)">编辑</a-button>
                  <a-popconfirm v-if="canEdit(record)" title="确定删除该生命周期模板？" @confirm="handleTemplateDelete(record)">
                    <a-button type="link" size="small" danger>删除</a-button>
                  </a-popconfirm>
                </a-space>
              </template>
            </template>
          </DataTable>
        </div>
      </a-tab-pane>
    </a-tabs>

    <!-- ==================== 状态新增/编辑弹窗 ==================== -->
    <a-modal
      v-model:open="statusModal.visible"
      :title="statusModal.isEdit ? '编辑生命周期状态' : '新增生命周期状态'"
      :confirm-loading="statusModal.saving"
      @ok="handleStatusSave"
      width="480px"
      :ok-text="statusModal.isEdit ? '保存' : '创建'"
    >
      <a-form :model="statusModal.form" layout="vertical">
        <a-form-item label="状态编码" required>
          <a-input
            v-model:value="statusModal.form.code"
            placeholder="如 WORKING、PUBLISHED"
            :disabled="statusModal.isEdit"
          />
        </a-form-item>
        <a-form-item label="状态名称" required>
          <a-input v-model:value="statusModal.form.name" placeholder="如 工作中" />
        </a-form-item>
        <a-form-item label="显示名称" required>
          <a-input v-model:value="statusModal.form.displayName" placeholder="如 工作中、已发布" />
        </a-form-item>
      </a-form>
    </a-modal>

    <!-- ==================== 模板新增/编辑弹窗 ==================== -->
    <a-modal
      v-model:open="templateModal.visible"
      :title="templateModal.isEdit ? '编辑生命周期模板' : '新增生命周期模板'"
      :confirm-loading="templateModal.saving"
      @ok="handleTemplateSave"
      width="820px"
      :ok-text="templateModal.isEdit ? '保存' : '创建'"
    >
      <a-form :model="templateModal.form" layout="vertical" class="lc-tmpl-form">
        <a-row :gutter="12">
          <a-col :span="8">
            <a-form-item label="模板编码" required>
              <a-input
                v-model:value="templateModal.form.code"
                placeholder="如 STANDARD_PROCESS"
                :disabled="templateModal.isEdit"
              />
            </a-form-item>
          </a-col>
          <a-col :span="10">
            <a-form-item label="模板名称" required>
              <a-input v-model:value="templateModal.form.name" placeholder="如 标准流程" />
            </a-form-item>
          </a-col>
          <a-col :span="4">
            <a-form-item label="状态">
              <a-switch v-model:checked="templateModal.form.active" checked-children="启用" un-checked-children="禁用" />
            </a-form-item>
          </a-col>
        </a-row>
        <a-form-item label="描述">
          <a-input v-model:value="templateModal.form.description" placeholder="模板用途说明" />
        </a-form-item>

        <!-- 状态选择 -->
        <div class="lc-tmpl-section">
          <div class="lc-tmpl-section-hd">
            <span class="lc-tmpl-section-title">状态列表</span>
            <span class="lc-tmpl-section-hint">选择该模板包含的状态，按序号排序</span>
          </div>
          <div class="lc-tmpl-state-list">
            <div
              v-for="(s, idx) in templateModal.form.states"
              :key="idx"
              class="lc-tmpl-state-row"
            >
              <span class="lc-tmpl-state-num">{{ idx + 1 }}</span>
              <a-select
                v-model:value="s.statusCode"
                size="small"
                placeholder="选择状态"
                style="flex:1"
                @change="(v) => onTmplStateChange(idx, v)"
              >
                <a-select-option
                  v-for="st in availableStatuses"
                  :key="st.code"
                  :value="st.code"
                  :disabled="templateModal.form.states.some((ts, i) => i !== idx && ts.statusCode === st.code)"
                >{{ st.displayName }} ({{ st.code }})</a-select-option>
              </a-select>
              <a-button
                v-if="templateModal.form.states.length > 1"
                type="link" size="small" danger @click="removeTmplState(idx)"
              ><DeleteOutlined /></a-button>
            </div>
          </div>
          <a-button type="dashed" size="small" block @click="addTmplState">
            <template #icon><PlusOutlined /></template>
            添加状态
          </a-button>
        </div>

        <!-- 流转规则 -->
        <div class="lc-tmpl-section">
          <div class="lc-tmpl-section-hd">
            <span class="lc-tmpl-section-title">升版规则（PROMOTE）</span>
            <span class="lc-tmpl-section-hint">定义状态向前推进的路径，如 WORKING → APPROVING</span>
          </div>
          <div class="lc-tmpl-rule-list">
            <div
              v-for="(tr, idx) in templateModal.form.transitions"
              :key="'t-'+idx"
              class="lc-tmpl-rule-row"
            >
              <a-select v-model:value="tr.fromStatusCode" size="small" placeholder="来源" style="width:140px">
                <a-select-option v-for="s in templateModal.form.states" :key="s.statusCode" :value="s.statusCode">{{ s.statusDisplayName || s.statusCode }}</a-select-option>
              </a-select>
              <ArrowRightOutlined class="lc-tmpl-rule-arrow" />
              <a-select v-model:value="tr.toStatusCode" size="small" placeholder="目标" style="width:140px">
                <a-select-option v-for="s in templateModal.form.states" :key="s.statusCode" :value="s.statusCode">{{ s.statusDisplayName || s.statusCode }}</a-select-option>
              </a-select>
              <a-button type="link" size="small" danger @click="removeTmplTransition(idx)"><DeleteOutlined /></a-button>
            </div>
          </div>
          <a-button type="dashed" size="small" @click="addTmplTransition">
            <template #icon><PlusOutlined /></template>
            添加升版规则
          </a-button>
        </div>

        <!-- 驳回规则 -->
        <div class="lc-tmpl-section">
          <div class="lc-tmpl-section-hd">
            <span class="lc-tmpl-section-title">驳回规则（REJECT）</span>
            <span class="lc-tmpl-section-hint">定义状态回退的路径，如 APPROVING → WORKING</span>
          </div>
          <div class="lc-tmpl-rule-list">
            <div
              v-for="(rr, idx) in templateModal.form.rejections"
              :key="'r-'+idx"
              class="lc-tmpl-rule-row"
            >
              <a-select v-model:value="rr.fromStatusCode" size="small" placeholder="来源" style="width:140px">
                <a-select-option v-for="s in templateModal.form.states" :key="s.statusCode" :value="s.statusCode">{{ s.statusDisplayName || s.statusCode }}</a-select-option>
              </a-select>
              <RollbackOutlined class="lc-tmpl-rule-arrow lc-tmpl-rule-reject" />
              <a-select v-model:value="rr.toStatusCode" size="small" placeholder="目标" style="width:140px">
                <a-select-option v-for="s in templateModal.form.states" :key="s.statusCode" :value="s.statusCode">{{ s.statusDisplayName || s.statusCode }}</a-select-option>
              </a-select>
              <a-button type="link" size="small" danger @click="removeTmplRejection(idx)"><DeleteOutlined /></a-button>
            </div>
          </div>
          <a-button type="dashed" size="small" @click="addTmplRejection">
            <template #icon><PlusOutlined /></template>
            添加驳回规则
          </a-button>
        </div>
      </a-form>
    </a-modal>

    <!-- ==================== 模板详情弹窗 ==================== -->
    <a-modal
      v-model:open="detailModal.visible"
      title="生命周期模板详情"
      :footer="null"
      width="700px"
    >
      <template v-if="detailModal.template">
        <a-descriptions :column="2" size="small" bordered>
          <a-descriptions-item label="模板编码">{{ detailModal.template.code }}</a-descriptions-item>
          <a-descriptions-item label="模板名称">{{ detailModal.template.name }}</a-descriptions-item>
          <a-descriptions-item label="启用状态">
            <a-tag :color="detailModal.template.active ? 'success' : 'default'">{{ detailModal.template.active ? '启用' : '禁用' }}</a-tag>
          </a-descriptions-item>
          <a-descriptions-item label="初始状态">{{ detailInitialStateName }}</a-descriptions-item>
          <a-descriptions-item label="描述" :span="2">{{ detailModal.template.description || '—' }}</a-descriptions-item>
          <a-descriptions-item label="状态列表" :span="2">
            <a-space wrap>
              <a-tag v-for="s in detailModal.template.states" :key="s.statusCode" color="blue">{{ s.statusDisplayName || s.statusCode }}</a-tag>
            </a-space>
          </a-descriptions-item>
        </a-descriptions>

        <a-divider />

        <a-row :gutter="16">
          <a-col :span="12">
            <h4 class="lc-detail-title">升版规则</h4>
            <div v-if="detailModal.template.transitions?.length" class="lc-detail-rules">
              <div v-for="(t, i) in detailModal.template.transitions" :key="i" class="lc-detail-rule-item">
                <a-tag color="blue">{{ t.fromStatusCode }}</a-tag>
                <ArrowRightOutlined />
                <a-tag color="green">{{ t.toStatusCode }}</a-tag>
              </div>
            </div>
            <span v-else class="lc-empty-hint">暂无升版规则</span>
          </a-col>
          <a-col :span="12">
            <h4 class="lc-detail-title">驳回规则</h4>
            <div v-if="detailModal.template.rejections?.length" class="lc-detail-rules">
              <div v-for="(r, i) in detailModal.template.rejections" :key="i" class="lc-detail-rule-item">
                <a-tag color="orange">{{ r.fromStatusCode }}</a-tag>
                <RollbackOutlined class="lc-rule-icon-reject" />
                <a-tag color="red">{{ r.toStatusCode }}</a-tag>
              </div>
            </div>
            <span v-else class="lc-empty-hint">暂无驳回规则</span>
          </a-col>
        </a-row>
      </template>
    </a-modal>
  </div>
</template>

<script setup>
import { ref, reactive, computed, onMounted, watch } from 'vue'
import { message } from 'ant-design-vue'
import {
  PlusOutlined, DeleteOutlined, RetweetOutlined, ApartmentOutlined,
  ArrowRightOutlined, RollbackOutlined, CheckCircleOutlined
} from '@ant-design/icons-vue'
import {
  getLifecycleStatuses, getLifecycleStatus, createLifecycleStatus,
  updateLifecycleStatus, deleteLifecycleStatus,
  getLifecycleTemplates, getLifecycleTemplate, createLifecycleTemplate,
  updateLifecycleTemplate, deleteLifecycleTemplate
} from '@/api'
import DataTable from '@/components/DataTable.vue'
import { useUserStore } from '@/stores/user'

const userStore = useUserStore()
const PLATFORM_TENANT_OID = '00000000-0000-0000-0000-000000000000'
const isPlatformAdmin = computed(() => userStore.roles?.includes('PLATFORM_ADMIN'))
const isPlatformData = (record) => record.tenantOid === PLATFORM_TENANT_OID
const canEdit = (record) => isPlatformAdmin.value || !isPlatformData(record)

// ==================== 标签切换 ====================
const activeTab = ref('status')

// ==================== 生命周期状态数据 ====================
const statuses = ref([])
const statusLoading = ref(false)

const statusColumns = [
  { title: '状态编码', dataIndex: 'code', key: 'code', width: 130 },
  { title: '显示名称', dataIndex: 'displayName', key: 'displayName', width: 120 },
  { title: '来源', key: 'source', width: 70 },
  { title: '创建时间', dataIndex: 'createdAt', key: 'createdAt', width: 150 },
  { title: '操作', key: 'action', width: 140 }
]

const CORE_STATUSES = ['WORKING', 'APPROVING', 'PUBLISHED', 'OFFLINE', 'ARCHIVED']
function isCoreStatus(code) {
  return CORE_STATUSES.includes(code)
}

async function loadStatuses() {
  statusLoading.value = true
  try {
    const res = await getLifecycleStatuses()
    if (res.code === 200) statuses.value = res.data || []
  } catch { message.error('加载生命周期状态失败') }
  finally { statusLoading.value = false }
}

// ==================== 生命周期模板数据 ====================
const templates = ref([])
const templateLoading = ref(false)

const activeTemplateCount = computed(() => templates.value.filter(t => t.active !== false).length)
const inactiveTemplateCount = computed(() => templates.value.filter(t => t.active === false).length)

const templateColumns = [
  { title: '模板编码', dataIndex: 'code', key: 'code', width: 180 },
  { title: '名称', dataIndex: 'name', key: 'name', width: 140 },
  { title: '状态列表', key: 'states', width: 200 },
  { title: '流转规则', key: 'transitions', width: 80 },
  { title: '来源', key: 'source', width: 70 },
  { title: '状态', key: 'active', width: 60 },
  { title: '描述', dataIndex: 'description', key: 'description', ellipsis: true },
  { title: '创建时间', dataIndex: 'createdAt', key: 'createdAt', width: 150 },
  { title: '操作', key: 'action', width: 170 }
]

async function loadTemplates() {
  templateLoading.value = true
  try {
    const res = await getLifecycleTemplates()
    if (res.code === 200) templates.value = res.data || []
  } catch { message.error('加载生命周期模板失败') }
  finally { templateLoading.value = false }
}

const availableStatuses = computed(() => statuses.value)

// ==================== 状态弹窗 ====================
const statusModal = reactive({
  visible: false, isEdit: false, saving: false,
  form: { code: '', name: '', displayName: '' }
})

function openStatusCreate() {
  statusModal.isEdit = false
  statusModal.form = { code: '', name: '', displayName: '' }
  statusModal.visible = true
}

function openStatusEdit(record) {
  statusModal.isEdit = true
  statusModal.form = {
    code: record.code,
    name: record.name || '',
    displayName: record.displayName
  }
  statusModal.visible = true
}

async function handleStatusSave() {
  const { code, name, displayName } = statusModal.form
  if (!code?.trim() || !name?.trim() || !displayName?.trim()) {
    message.warning('编码、名称和显示名称不能为空')
    return
  }
  statusModal.saving = true
  try {
    const payload = {
      code: code.trim().toUpperCase(),
      name: name.trim(),
      displayName: displayName.trim()
    }
    let res
    if (statusModal.isEdit) {
      res = await updateLifecycleStatus(code.trim(), payload)
    } else {
      res = await createLifecycleStatus(payload)
    }
    if (res.code === 200) {
      message.success(statusModal.isEdit ? '状态已更新' : '状态已创建')
      statusModal.visible = false
      await loadStatuses()
    } else {
      message.error(res.message || '操作失败')
    }
  } catch { message.error('操作失败') }
  finally { statusModal.saving = false }
}

async function handleStatusDelete(record) {
  try {
    const res = await deleteLifecycleStatus(record.code)
    if (res.code === 200) {
      message.success('状态已删除')
      await loadStatuses()
    } else {
      message.error(res.message || '删除失败')
    }
  } catch { message.error('删除失败') }
}

// ==================== 模板弹窗 ====================
const templateModal = reactive({
  visible: false, isEdit: false, saving: false,
  form: { code: '', name: '', description: '', active: true, initialStateCode: '', states: [], transitions: [], rejections: [] }
})

function resetTemplateForm() {
  templateModal.form = {
    code: '', name: '', description: '', active: true, initialStateCode: '',
    states: [], transitions: [], rejections: []
  }
}

function openTemplateCreate() {
  templateModal.isEdit = false
  resetTemplateForm()
  templateModal.visible = true
}

async function openTemplateEdit(record) {
  templateModal.isEdit = true
  templateModal.saving = false
  try {
    const res = await getLifecycleTemplate(record.code)
    if (res.code === 200 && res.data) {
      const t = res.data
      templateModal.form = {
        code: t.code,
        name: t.name,
        description: t.description || '',
        active: t.active !== false,
        initialStateCode: t.initialStateCode || '',
        states: (t.states || []).map(s => ({ statusCode: s.statusCode, statusDisplayName: s.statusDisplayName, sortOrder: s.sortOrder })),
        transitions: (t.transitions || []).map(tr => ({ fromStatusCode: tr.fromStatusCode, toStatusCode: tr.toStatusCode })),
        rejections: (t.rejections || []).map(rr => ({ fromStatusCode: rr.fromStatusCode, toStatusCode: rr.toStatusCode }))
      }
    }
  } catch { message.error('加载模板详情失败') }
  templateModal.visible = true
}

function addTmplState() {
  templateModal.form.states.push({ statusCode: '', statusDisplayName: '', sortOrder: templateModal.form.states.length + 1 })
}

function removeTmplState(idx) {
  templateModal.form.states.splice(idx, 1)
}

function onTmplStateChange(idx, code) {
  const st = availableStatuses.value.find(s => s.code === code)
  if (st) {
    templateModal.form.states[idx].statusDisplayName = st.displayName
    if (!templateModal.form.initialStateCode && idx === 0) {
      templateModal.form.initialStateCode = code
    }
  }
}

// --- 升版规则 ---
function addTmplTransition() {
  templateModal.form.transitions.push({ fromStatusCode: '', toStatusCode: '' })
}

function removeTmplTransition(idx) {
  templateModal.form.transitions.splice(idx, 1)
}

// --- 驳回规则 ---
function addTmplRejection() {
  templateModal.form.rejections.push({ fromStatusCode: '', toStatusCode: '' })
}

function removeTmplRejection(idx) {
  templateModal.form.rejections.splice(idx, 1)
}

async function handleTemplateSave() {
  const { code, name } = templateModal.form
  if (!code?.trim() || !name?.trim()) {
    message.warning('编码和名称不能为空')
    return
  }
  if (templateModal.form.states.length === 0) {
    message.warning('请至少添加一个状态')
    return
  }

  templateModal.saving = true
  try {
    const payload = {
      code: code.trim(),
      name: name.trim(),
      description: templateModal.form.description || '',
      active: templateModal.form.active,
      initialStateCode: templateModal.form.initialStateCode || (templateModal.form.states[0]?.statusCode || ''),
      states: templateModal.form.states
        .filter(s => s.statusCode)
        .map((s, i) => ({
          statusCode: s.statusCode,
          statusDisplayName: s.statusDisplayName,
          sortOrder: i + 1
        })),
      transitions: templateModal.form.transitions
        .filter(t => t.fromStatusCode && t.toStatusCode)
        .map(t => ({ fromStatusCode: t.fromStatusCode, toStatusCode: t.toStatusCode })),
      rejections: templateModal.form.rejections
        .filter(r => r.fromStatusCode && r.toStatusCode)
        .map(r => ({ fromStatusCode: r.fromStatusCode, toStatusCode: r.toStatusCode }))
    }

    let res
    if (templateModal.isEdit) {
      res = await updateLifecycleTemplate(code.trim(), payload)
    } else {
      res = await createLifecycleTemplate(payload)
    }

    if (res.code === 200) {
      message.success(templateModal.isEdit ? '模板已更新' : '模板已创建')
      templateModal.visible = false
      await loadTemplates()
    } else {
      message.error(res.message || '操作失败')
    }
  } catch { message.error('操作失败') }
  finally { templateModal.saving = false }
}

async function handleTemplateDelete(record) {
  try {
    const res = await deleteLifecycleTemplate(record.code)
    if (res.code === 200) {
      message.success('模板已删除')
      await loadTemplates()
    } else {
      message.error(res.message || '删除失败')
    }
  } catch { message.error('删除失败') }
}

// ==================== 模板详情弹窗 ====================
const detailModal = reactive({
  visible: false, template: null
})
const detailInitialStateName = computed(() => {
  if (!detailModal.template) return '—'
  const code = detailModal.template.initialStateCode
  const st = detailModal.template.states?.find(s => s.statusCode === code)
  return st ? (st.statusDisplayName || st.statusCode) : (code || '—')
})

async function openTemplateDetail(record) {
  detailModal.template = null
  try {
    const res = await getLifecycleTemplate(record.code)
    if (res.code === 200 && res.data) {
      detailModal.template = res.data
    }
  } catch { message.error('加载模板详情失败') }
  detailModal.visible = true
}

// ==================== 工具函数 ====================
function formatTime(val) {
  if (!val) return '-'
  // 兼容数组格式 [2026, 7, 6, 10, 46, 55]
  if (Array.isArray(val)) {
    const [y, m, d, h = 0, mi = 0, s = 0] = val
    return `${y}-${String(m).padStart(2, '0')}-${String(d).padStart(2, '0')} ${String(h).padStart(2, '0')}:${String(mi).padStart(2, '0')}:${String(s).padStart(2, '0')}`
  }
  const str = String(val)
  return str.replace('T', ' ').substring(0, 19)
}

// ==================== 生命周期 ====================
onMounted(async () => {
  await loadStatuses()
  await loadTemplates()
})

// 切换 tab 时刷新对应数据
watch(activeTab, (val) => {
  if (val === 'status') loadStatuses()
  else if (val === 'template') loadTemplates()
})
</script>

<style scoped>
.lc-page {
  display: flex;
  flex-direction: column;
  height: 100%;
  min-height: 0;
}

/* 页头 */
.lc-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 0 0 10px;
  flex-shrink: 0;
}
.lc-header-left {
  display: flex;
  align-items: baseline;
  gap: 8px;
}
.lc-title {
  margin: 0;
  font-size: 18px;
  font-weight: 600;
  color: #1a1a2e;
}
.lc-subtitle {
  font-size: 12px;
  color: #8c8c8c;
}

.lc-tabs {
  flex: 1;
  display: flex;
  flex-direction: column;
  min-height: 0;
}
.lc-tabs :deep(.ant-tabs-content-holder) {
  flex: 1;
  display: flex;
  flex-direction: column;
  overflow: hidden;
}
.lc-tabs :deep(.ant-tabs-content) {
  flex: 1;
  min-height: 0;
}
.lc-tabs :deep(.ant-tabs-tabpane) {
  height: 100%;
  display: flex;
  flex-direction: column;
}

/* 统计栏 */
.lc-stats-bar {
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
.lc-stat-item {
  display: flex;
  align-items: center;
  gap: 5px;
}
.lc-stat-icon {
  font-size: 14px;
  color: #1677ff;
}
.lc-stat-value {
  font-size: 14px;
  font-weight: 700;
  color: #1a1a2e;
}
.lc-stat-label {
  font-size: 12px;
  color: #8c8c8c;
}

/* 表格区域 */
.lc-table-wrap {
  flex: 1;
  min-height: 0;
}
.lc-order-badge {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 24px;
  height: 24px;
  border-radius: 12px;
  background: #e6f4ff;
  color: #1677ff;
  font-size: 12px;
  font-weight: 600;
}
.lc-trans-count {
  font-size: 12px;
  color: #8c8c8c;
}
.lc-empty-hint {
  font-size: 12px;
  color: #bfbfbf;
}

/* 模板弹窗 */
.lc-tmpl-form {
  max-height: 60vh;
  overflow-y: auto;
  padding-right: 4px;
}
.lc-tmpl-section {
  margin-top: 12px;
  padding-top: 10px;
  border-top: 1px solid #f0f0f0;
}
.lc-tmpl-section-hd {
  display: flex;
  align-items: baseline;
  gap: 8px;
  margin-bottom: 8px;
}
.lc-tmpl-section-title {
  font-size: 13px;
  font-weight: 500;
  color: #434343;
}
.lc-tmpl-section-hint {
  font-size: 11px;
  color: #bfbfbf;
}
.lc-tmpl-state-list,
.lc-tmpl-rule-list {
  display: flex;
  flex-direction: column;
  gap: 4px;
  margin-bottom: 8px;
}
.lc-tmpl-state-row,
.lc-tmpl-rule-row {
  display: flex;
  align-items: center;
  gap: 6px;
}
.lc-tmpl-state-num {
  width: 22px;
  height: 22px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  border-radius: 11px;
  background: #e6f4ff;
  color: #1677ff;
  font-size: 11px;
  font-weight: 600;
  flex-shrink: 0;
}
.lc-tmpl-rule-arrow {
  color: #52c41a;
  font-size: 14px;
  flex-shrink: 0;
}
.lc-tmpl-rule-reject {
  color: #ff4d4f;
}

/* 详情弹窗 */
.lc-detail-title {
  margin: 0 0 8px 0;
  font-size: 14px;
  font-weight: 500;
  color: #434343;
}
.lc-detail-rules {
  display: flex;
  flex-direction: column;
  gap: 4px;
}
.lc-detail-rule-item {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 12px;
}
.lc-rule-icon-reject {
  color: #ff4d4f;
  font-size: 13px;
}
</style>
