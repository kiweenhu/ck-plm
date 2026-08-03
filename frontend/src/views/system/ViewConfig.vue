<template>
  <div class="vc-page">
    <!-- 页头 -->
    <div class="vc-header">
      <div class="vc-header-left">
        <h3 class="vc-title">视图定义</h3>
        <span class="vc-subtitle">定义业务对象的视图及视图切换规则，实现多视角下的版本筛选与切换控制</span>
      </div>
    </div>

    <a-tabs v-model:activeKey="activeTab" class="vc-tabs">
      <!-- ==================== 视图管理 Tab ==================== -->
      <a-tab-pane key="view" tab="视图管理">
        <!-- 统计栏 -->
        <div class="vc-stats-bar">
          <div class="vc-stat-item">
            <EyeOutlined class="vc-stat-icon" />
            <span class="vc-stat-value">{{ views.length }}</span>
            <span class="vc-stat-label">视图总数</span>
          </div>
          <a-divider type="vertical" style="height:24px" />
          <div class="vc-stat-item">
            <a-tag color="green" size="small">启用</a-tag>
            <span class="vc-stat-value">{{ enabledViewCount }}</span>
          </div>
          <div class="vc-stat-item">
            <a-tag color="default" size="small">禁用</a-tag>
            <span class="vc-stat-value">{{ disabledViewCount }}</span>
          </div>
        </div>

        <!-- 表格 -->
        <div class="vc-table-wrap">
          <DataTable
            :columns="viewColumns"
            :data-source="views"
            :loading="viewLoading"
            search-placeholder="搜索视图编码 / 名称..."
            :search-fields="['code', 'name']"
            :enable-resize="true"
            :show-column-toggle="true"
            :max-height="400"
            row-key="code"
            size="middle"
          >
            <template #toolbar>
              <a-button type="primary" size="small" @click="openViewCreate">
                <template #icon><PlusOutlined /></template>
                新增视图
              </a-button>
            </template>

            <template #bodyCell="{ column, record }">
              <template v-if="column.key === 'code'">
                <a-tag color="blue">{{ record.code }}</a-tag>
              </template>
              <template v-else-if="column.key === 'sortOrder'">
                <span class="vc-order-badge">{{ record.sortOrder }}</span>
              </template>
              <template v-else-if="column.key === 'enabled'">
                <a-switch
                  :checked="record.enabled !== false"
                  size="small"
                  @change="(val) => handleViewToggle(record, val)"
                />
              </template>
              <template v-else-if="column.key === 'transitionCount'">
                <span class="vc-trans-count">{{ record._transitionCount ?? 0 }} 条规则</span>
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
                  <a-button type="link" size="small" @click="openViewEdit(record)" :disabled="!canEdit(record)">编辑</a-button>
                  <a-popconfirm v-if="canEdit(record)" title="确定删除该视图？关联的切换规则也将被删除" @confirm="handleViewDelete(record)">
                    <a-button type="link" size="small" danger>删除</a-button>
                  </a-popconfirm>
                </a-space>
              </template>
            </template>
          </DataTable>
        </div>
      </a-tab-pane>

      <!-- ==================== 切换规则 Tab ==================== -->
      <a-tab-pane key="transition" tab="切换规则">
        <!-- 统计栏 -->
        <div class="vc-stats-bar">
          <div class="vc-stat-item">
            <SwapOutlined class="vc-stat-icon" />
            <span class="vc-stat-value">{{ transitions.length }}</span>
            <span class="vc-stat-label">规则总数</span>
          </div>
          <a-divider type="vertical" style="height:24px" />
          <a-select
            v-model:value="transitionFilter.viewCode"
            placeholder="按源视图筛选"
            allow-clear
            style="width:180px"
            size="small"
            @change="loadTransitions"
          >
            <a-select-option v-for="v in views" :key="v.code" :value="v.code">{{ v.name }} ({{ v.code }})</a-select-option>
          </a-select>
        </div>

        <!-- 表格 -->
        <div class="vc-table-wrap">
          <DataTable
            :columns="transitionColumns"
            :data-source="filteredTransitions"
            :loading="transitionLoading"
            search-placeholder="搜索源视图 / 目标视图 / 状态条件..."
            :search-fields="['fromViewCode', 'toViewCode', 'conditionStatus']"
            :enable-resize="true"
            :show-column-toggle="true"
            :max-height="400"
            row-key="oid"
            size="middle"
          >
            <template #toolbar>
              <a-button type="primary" size="small" @click="openTransitionCreate">
                <template #icon><PlusOutlined /></template>
                新增规则
              </a-button>
            </template>

            <template #bodyCell="{ column, record }">
              <template v-if="column.key === 'direction'">
                <a-space size="4">
                  <a-tag color="blue">{{ record.fromViewCode }}</a-tag>
                  <ArrowRightOutlined class="vc-rule-arrow" />
                  <a-tag color="green">{{ record.toViewCode }}</a-tag>
                </a-space>
              </template>
              <template v-else-if="column.key === 'conditionStatus'">
                <a-tag v-if="record.conditionStatus" color="orange">{{ record.conditionStatus }}</a-tag>
                <span v-else class="vc-empty-hint">无前置条件</span>
              </template>
              <template v-else-if="column.key === 'conditionViewLatest'">
                <a-tag :color="record.conditionViewLatest !== false ? 'success' : 'default'">
                  {{ record.conditionViewLatest !== false ? '是' : '否' }}
                </a-tag>
              </template>
              <template v-else-if="column.key === 'enabled'">
                <a-switch
                  :checked="record.enabled !== false"
                  size="small"
                  @change="(val) => handleTransitionToggle(record, val)"
                />
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
                  <a-button type="link" size="small" @click="openTransitionEdit(record)" :disabled="!canEdit(record)">编辑</a-button>
                  <a-popconfirm v-if="canEdit(record)" title="确定删除该切换规则？" @confirm="handleTransitionDelete(record)">
                    <a-button type="link" size="small" danger>删除</a-button>
                  </a-popconfirm>
                </a-space>
              </template>
            </template>
          </DataTable>
        </div>
      </a-tab-pane>
    </a-tabs>

    <!-- ==================== 视图新增/编辑弹窗 ==================== -->
    <a-modal
      v-model:open="viewModal.visible"
      :title="viewModal.isEdit ? '编辑视图' : '新增视图'"
      :confirm-loading="viewModal.saving"
      @ok="handleViewSave"
      width="480px"
      :ok-text="viewModal.isEdit ? '保存' : '创建'"
    >
      <a-form :model="viewModal.form" layout="vertical">
        <a-form-item label="视图编码" required>
          <a-input
            v-model:value="viewModal.form.code"
            placeholder="如 Design、Manufacturing、Service"
            :disabled="viewModal.isEdit"
          />
        </a-form-item>
        <a-form-item label="视图名称" required>
          <a-input v-model:value="viewModal.form.name" placeholder="如 设计视图、制造视图" />
        </a-form-item>
        <a-form-item label="描述">
          <a-textarea v-model:value="viewModal.form.description" placeholder="视图用途说明" :rows="2" />
        </a-form-item>
        <a-row :gutter="12">
          <a-col :span="10">
            <a-form-item label="排序序号">
              <a-input-number v-model:value="viewModal.form.sortOrder" :min="0" style="width:100%" />
            </a-form-item>
          </a-col>
          <a-col :span="10">
            <a-form-item label="启用状态">
              <a-switch v-model:checked="viewModal.form.enabled" checked-children="启用" un-checked-children="禁用" />
            </a-form-item>
          </a-col>
        </a-row>
      </a-form>
    </a-modal>

    <!-- ==================== 切换规则新增/编辑弹窗 ==================== -->
    <a-modal
      v-model:open="transitionModal.visible"
      :title="transitionModal.isEdit ? '编辑切换规则' : '新增切换规则'"
      :confirm-loading="transitionModal.saving"
      @ok="handleTransitionSave"
      width="560px"
      :ok-text="transitionModal.isEdit ? '保存' : '创建'"
    >
      <a-form :model="transitionModal.form" layout="vertical">
        <a-row :gutter="12">
          <a-col :span="10">
            <a-form-item label="源视图" required>
              <a-select
                v-model:value="transitionModal.form.fromViewCode"
                placeholder="选择源视图"
                :disabled="transitionModal.isEdit"
              >
                <a-select-option v-for="v in views" :key="v.code" :value="v.code">
                  {{ v.name }} ({{ v.code }})
                </a-select-option>
              </a-select>
            </a-form-item>
          </a-col>
          <a-col :span="2" style="display:flex;align-items:center;justify-content:center;padding-top:30px">
            <ArrowRightOutlined style="color:#1677ff;font-size:18px" />
          </a-col>
          <a-col :span="12">
            <a-form-item label="目标视图" required>
              <a-select
                v-model:value="transitionModal.form.toViewCode"
                placeholder="选择目标视图"
              >
                <a-select-option v-for="v in views" :key="v.code" :value="v.code">
                  {{ v.name }} ({{ v.code }})
                </a-select-option>
              </a-select>
            </a-form-item>
          </a-col>
        </a-row>
        <a-form-item label="前置条件 - 生命周期状态（可选）">
          <a-input
            v-model:value="transitionModal.form.conditionStatus"
            placeholder="如 Released，留空表示无前置条件"
          />
        </a-form-item>
        <a-row :gutter="12">
          <a-col :span="10">
            <a-form-item label="要求最新小版本">
              <a-switch
                v-model:checked="transitionModal.form.conditionViewLatest"
                checked-children="是"
                un-checked-children="否"
              />
            </a-form-item>
          </a-col>
          <a-col :span="10">
            <a-form-item label="启用状态">
              <a-switch
                v-model:checked="transitionModal.form.enabled"
                checked-children="启用"
                un-checked-children="禁用"
              />
            </a-form-item>
          </a-col>
        </a-row>
        <a-form-item label="排序序号">
          <a-input-number v-model:value="transitionModal.form.sortOrder" :min="0" style="width:100%" />
        </a-form-item>
        <a-form-item label="描述">
          <a-textarea v-model:value="transitionModal.form.description" placeholder="规则说明" :rows="2" />
        </a-form-item>
      </a-form>
    </a-modal>
  </div>
</template>

<script setup>
import { ref, reactive, computed, onMounted, watch } from 'vue'
import { message } from 'ant-design-vue'
import {
  PlusOutlined, EyeOutlined, SwapOutlined, ArrowRightOutlined
} from '@ant-design/icons-vue'
import {
  getViews, getView, createView, updateView, deleteView,
  getViewTransitions, createViewTransition, updateViewTransition, deleteViewTransition
} from '@/api'
import DataTable from '@/components/DataTable.vue'
import { useUserStore } from '@/stores/user'

const userStore = useUserStore()
const PLATFORM_TENANT_OID = '00000000-0000-0000-0000-000000000000'
const isPlatformAdmin = computed(() => userStore.roles?.includes('PLATFORM_ADMIN'))
const isPlatformData = (record) => record.tenantOid === PLATFORM_TENANT_OID
const canEdit = (record) => isPlatformAdmin.value || !isPlatformData(record)

// ==================== 标签切换 ====================
const activeTab = ref('view')

// ==================== 视图数据 ====================
const views = ref([])
const viewLoading = ref(false)

const enabledViewCount = computed(() => views.value.filter(v => v.enabled !== false).length)
const disabledViewCount = computed(() => views.value.filter(v => v.enabled === false).length)

const viewColumns = [
  { title: '视图编码', dataIndex: 'code', key: 'code', width: 130 },
  { title: '视图名称', dataIndex: 'name', key: 'name', width: 120 },
  { title: '描述', dataIndex: 'description', key: 'description', ellipsis: true },
  { title: '排序', key: 'sortOrder', width: 60 },
  { title: '来源', key: 'source', width: 70 },
  { title: '启用', key: 'enabled', width: 60 },
  { title: '切换规则', key: 'transitionCount', width: 80 },
  { title: '创建时间', dataIndex: 'createdAt', key: 'createdAt', width: 150 },
  { title: '操作', key: 'action', width: 140 }
]

async function loadViews() {
  viewLoading.value = true
  try {
    const res = await getViews()
    if (res.code === 200) {
      views.value = res.data || []
      // 加载每个视图的规则数量
      await loadTransitionCounts()
    }
  } catch { message.error('加载视图列表失败') }
  finally { viewLoading.value = false }
}

async function loadTransitionCounts() {
  for (const v of views.value) {
    try {
      const res = await getViewTransitions(v.code)
      if (res.code === 200) {
        v._transitionCount = (res.data || []).length
      }
    } catch { v._transitionCount = 0 }
  }
}

async function handleViewToggle(record, checked) {
  try {
    const res = await updateView(record.code, {
      ...record, enabled: checked
    })
    if (res.code === 200) {
      record.enabled = checked
      message.success(checked ? '视图已启用' : '视图已禁用')
    } else {
      message.error(res.message || '更新失败')
    }
  } catch { message.error('更新失败') }
}

// ==================== 视图弹窗 ====================
const viewModal = reactive({
  visible: false, isEdit: false, saving: false,
  form: { code: '', name: '', description: '', sortOrder: 0, enabled: true }
})

function openViewCreate() {
  viewModal.isEdit = false
  viewModal.form = {
    code: '', name: '', description: '',
    sortOrder: views.value.length > 0 ? Math.max(...views.value.map(v => v.sortOrder || 0)) + 1 : 1,
    enabled: true
  }
  viewModal.visible = true
}

function openViewEdit(record) {
  viewModal.isEdit = true
  viewModal.form = {
    code: record.code,
    name: record.name,
    description: record.description || '',
    sortOrder: record.sortOrder ?? 0,
    enabled: record.enabled !== false
  }
  viewModal.visible = true
}

async function handleViewSave() {
  const { code, name } = viewModal.form
  if (!code?.trim() || !name?.trim()) {
    message.warning('编码和名称不能为空')
    return
  }
  viewModal.saving = true
  try {
    const payload = {
      code: code.trim(),
      name: name.trim(),
      description: viewModal.form.description || '',
      sortOrder: viewModal.form.sortOrder ?? 0,
      enabled: viewModal.form.enabled
    }
    let res
    if (viewModal.isEdit) {
      res = await updateView(code.trim(), payload)
    } else {
      res = await createView(payload)
    }
    if (res.code === 200) {
      message.success(viewModal.isEdit ? '视图已更新' : '视图已创建')
      viewModal.visible = false
      await loadViews()
    } else {
      message.error(res.message || '操作失败')
    }
  } catch { message.error('操作失败') }
  finally { viewModal.saving = false }
}

async function handleViewDelete(record) {
  try {
    const res = await deleteView(record.code)
    if (res.code === 200) {
      message.success('视图已删除')
      await loadViews()
    } else {
      message.error(res.message || '删除失败')
    }
  } catch { message.error('删除失败') }
}

// ==================== 切换规则数据 ====================
const transitions = ref([])
const transitionLoading = ref(false)
const transitionFilter = reactive({ viewCode: null })

const filteredTransitions = computed(() => {
  if (transitionFilter.viewCode) {
    return transitions.value.filter(t => t.fromViewCode === transitionFilter.viewCode)
  }
  return transitions.value
})

const transitionColumns = [
  { title: '切换方向', key: 'direction', width: 220 },
  { title: '前置生命周期状态', key: 'conditionStatus', width: 150 },
  { title: '要求最新小版本', key: 'conditionViewLatest', width: 120 },
  { title: '排序', dataIndex: 'sortOrder', key: 'sortOrder', width: 60 },
  { title: '来源', key: 'source', width: 70 },
  { title: '启用', key: 'enabled', width: 60 },
  { title: '描述', dataIndex: 'description', key: 'description', ellipsis: true },
  { title: '创建时间', dataIndex: 'createdAt', key: 'createdAt', width: 150 },
  { title: '操作', key: 'action', width: 140 }
]

async function loadAllTransitions() {
  const all = []
  for (const v of views.value) {
    try {
      const res = await getViewTransitions(v.code)
      if (res.code === 200 && res.data) {
        all.push(...res.data)
      }
    } catch { /* skip */ }
  }
  transitions.value = all.sort((a, b) => (a.sortOrder ?? 0) - (b.sortOrder ?? 0))
}

async function loadTransitions() {
  transitionLoading.value = true
  try {
    if (transitionFilter.viewCode) {
      const res = await getViewTransitions(transitionFilter.viewCode)
      if (res.code === 200) {
        transitions.value = (res.data || []).sort((a, b) => (a.sortOrder ?? 0) - (b.sortOrder ?? 0))
      }
    } else {
      await loadAllTransitions()
    }
  } catch { message.error('加载切换规则失败') }
  finally { transitionLoading.value = false }
}

async function handleTransitionToggle(record, checked) {
  try {
    const res = await updateViewTransition(record.oid, {
      ...record, enabled: checked
    })
    if (res.code === 200) {
      record.enabled = checked
      message.success(checked ? '规则已启用' : '规则已禁用')
    } else {
      message.error(res.message || '更新失败')
    }
  } catch { message.error('更新失败') }
}

// ==================== 切换规则弹窗 ====================
const transitionModal = reactive({
  visible: false, isEdit: false, saving: false,
  form: {
    fromViewCode: '', toViewCode: '',
    conditionStatus: '', conditionViewLatest: true,
    description: '', sortOrder: 0, enabled: true
  }
})

function openTransitionCreate() {
  transitionModal.isEdit = false
  transitionModal.form = {
    fromViewCode: '', toViewCode: '',
    conditionStatus: '', conditionViewLatest: true,
    description: '', sortOrder: transitions.value.length + 1,
    enabled: true
  }
  transitionModal.visible = true
}

function openTransitionEdit(record) {
  transitionModal.isEdit = true
  transitionModal.form = {
    oid: record.oid,
    fromViewCode: record.fromViewCode,
    toViewCode: record.toViewCode,
    conditionStatus: record.conditionStatus || '',
    conditionViewLatest: record.conditionViewLatest !== false,
    description: record.description || '',
    sortOrder: record.sortOrder ?? 0,
    enabled: record.enabled !== false
  }
  transitionModal.visible = true
}

async function handleTransitionSave() {
  const { fromViewCode, toViewCode } = transitionModal.form
  if (!fromViewCode || !toViewCode) {
    message.warning('源视图和目标视图不能为空')
    return
  }
  if (fromViewCode === toViewCode) {
    message.warning('源视图和目标视图不能相同')
    return
  }
  transitionModal.saving = true
  try {
    const payload = {
      fromViewCode,
      toViewCode,
      conditionStatus: transitionModal.form.conditionStatus || null,
      conditionViewLatest: transitionModal.form.conditionViewLatest,
      description: transitionModal.form.description || '',
      sortOrder: transitionModal.form.sortOrder ?? 0,
      enabled: transitionModal.form.enabled
    }
    let res
    if (transitionModal.isEdit) {
      res = await updateViewTransition(transitionModal.form.oid, payload)
    } else {
      res = await createViewTransition(payload)
    }
    if (res.code === 200) {
      message.success(transitionModal.isEdit ? '规则已更新' : '规则已创建')
      transitionModal.visible = false
      await loadTransitions()
      await loadTransitionCounts()
    } else {
      message.error(res.message || '操作失败')
    }
  } catch { message.error('操作失败') }
  finally { transitionModal.saving = false }
}

async function handleTransitionDelete(record) {
  try {
    const res = await deleteViewTransition(record.oid)
    if (res.code === 200) {
      message.success('规则已删除')
      await loadTransitions()
      await loadTransitionCounts()
    } else {
      message.error(res.message || '删除失败')
    }
  } catch { message.error('删除失败') }
}

// ==================== 工具函数 ====================
function formatTime(str) {
  if (!str) return '-'
  return str.replace('T', ' ').substring(0, 19)
}

// ==================== 生命周期 ====================
onMounted(async () => {
  await loadViews()
  await loadTransitions()
})

watch(activeTab, (val) => {
  if (val === 'view') loadViews()
  else if (val === 'transition') loadTransitions()
})
</script>

<style scoped>
.vc-page {
  display: flex;
  flex-direction: column;
  height: 100%;
  min-height: 0;
}

/* 页头 */
.vc-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 0 0 10px;
  flex-shrink: 0;
}
.vc-header-left {
  display: flex;
  align-items: baseline;
  gap: 8px;
}
.vc-title {
  margin: 0;
  font-size: 18px;
  font-weight: 600;
  color: #1a1a2e;
}
.vc-subtitle {
  font-size: 12px;
  color: #8c8c8c;
}

.vc-tabs {
  flex: 1;
  display: flex;
  flex-direction: column;
  min-height: 0;
}
.vc-tabs :deep(.ant-tabs-content-holder) {
  flex: 1;
  display: flex;
  flex-direction: column;
  overflow: hidden;
}
.vc-tabs :deep(.ant-tabs-content) {
  flex: 1;
  min-height: 0;
}
.vc-tabs :deep(.ant-tabs-tabpane) {
  height: 100%;
  display: flex;
  flex-direction: column;
}

/* 统计栏 */
.vc-stats-bar {
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
.vc-stat-item {
  display: flex;
  align-items: center;
  gap: 5px;
}
.vc-stat-icon {
  font-size: 14px;
  color: #1677ff;
}
.vc-stat-value {
  font-size: 14px;
  font-weight: 700;
  color: #1a1a2e;
}
.vc-stat-label {
  font-size: 12px;
  color: #8c8c8c;
}

/* 表格区域 */
.vc-table-wrap {
  flex: 1;
  min-height: 0;
}
.vc-order-badge {
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
.vc-trans-count {
  font-size: 12px;
  color: #8c8c8c;
}
.vc-empty-hint {
  font-size: 12px;
  color: #bfbfbf;
}
.vc-rule-arrow {
  color: #52c41a;
  font-size: 14px;
  flex-shrink: 0;
}
</style>
