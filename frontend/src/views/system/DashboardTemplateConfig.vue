<template>
  <div class="db-page">
    <div class="db-header">
      <div class="db-header-left">
        <h3 class="db-title">驾驶舱</h3>
        <span class="db-subtitle">
          行业包把驾驶舱布局装入企业一份（智能硬件 IPD：3 个视角 —— 经营战报给 GM、系列战况简报给 GM+研发总监、型号作战地图给研发/PM）；
          企业在此裁剪 —— 改受众、改分组件、改生成节奏，或<b>停用</b>（停用不删除，重装行业包也不会被改回）。
          文档的硬话：「系列层分数必然是平均数，而平均数是管理的大敌。把平均数拆开的唯一手段是排名」——
          故 <b>MODEL_RANKING</b> / <b>MODEL_COMPARE_TABLE</b> 是必选件。<b>★主视角</b> 是产品经理/研发的日常工作面（来自包的 primaryScopeType）。
        </span>
      </div>
    </div>

    <div class="db-stats-bar">
      <div class="db-stat-item">
        <DashboardOutlined class="db-stat-icon" />
        <span class="db-stat-value">{{ templates.length }}</span>
        <span class="db-stat-label">视角总数</span>
      </div>
      <div class="db-stat-item">
        <span class="db-stat-value">{{ widgetTotal }}</span>
        <span class="db-stat-label">分组件总数</span>
      </div>
      <div class="db-stat-item">
        <span class="db-stat-value">{{ primaryName || '—' }}</span>
        <span class="db-stat-label">主视角</span>
      </div>
      <div class="db-stat-item">
        <span class="db-stat-value">{{ disabledCount }}</span>
        <span class="db-stat-label">已停用</span>
      </div>
      <div v-if="!isPlatformAdmin && !hasAnyTemplate" class="db-stat-hint">
        暂无驾驶舱布局：装行业包会自动带入
      </div>
    </div>

    <div class="db-table-wrap">
      <DataTable
        :columns="columns"
        :data-source="filteredTemplates"
        :loading="loading"
        search-placeholder="搜索视角编码 / 名称 / 受众 / 分组件..."
        :search-fields="['code', 'name', 'audience', 'widgets']"
        :enable-resize="true"
        scroll-x="max-content"
        :show-column-toggle="true"
        stretch
        row-key="oid"
        size="middle"
      >
        <template #toolbar>
          <a-space>
            <a-checkbox v-model:checked="onlyEnabled">仅看启用</a-checkbox>
            <a-button type="primary" size="small" @click="openCreate">
              <template #icon><PlusOutlined /></template>
              新增驾驶舱
            </a-button>
          </a-space>
        </template>

        <template #bodyCell="{ column, record }">
          <template v-if="column.key === 'code'">
            <a-tag color="blue">{{ record.code }}</a-tag>
            <a-tag v-if="record.primaryScope" color="gold" style="margin-left:4px">★主视角</a-tag>
          </template>
          <template v-else-if="column.key === 'audience'">
            <a-tag v-if="record.audience" color="purple">{{ record.audience }}</a-tag>
            <span v-else style="color:#bfbfbf">-</span>
          </template>
          <template v-else-if="column.key === 'pageCount'">
            <span v-if="record.pageCount">{{ record.pageCount }} 页</span>
            <span v-else style="color:#bfbfbf">-</span>
          </template>
          <template v-else-if="column.key === 'widgets'">
            <a-tooltip :title="parseWidgets(record.widgets).join('、')">
              <a-space size="4" wrap>
                <a-tag
                  v-for="w in parseWidgets(record.widgets).slice(0, 3)"
                  :key="w"
                  size="small"
                  :color="WIDGET_LABEL[w] ? 'cyan' : 'default'"
                  style="white-space:nowrap"
                >{{ WIDGET_LABEL[w] || w }}</a-tag>
                <span v-if="parseWidgets(record.widgets).length > 3" style="color:#8c8c8c;font-size:12px">
                  +{{ parseWidgets(record.widgets).length - 3 }}
                </span>
                <span v-if="parseWidgets(record.widgets).length === 0" style="color:#bfbfbf">-</span>
              </a-space>
            </a-tooltip>
          </template>
          <template v-else-if="column.key === 'generatePolicy'">
            <a-tag v-if="record.generatePolicy" color="green">{{ POLICY_LABEL[record.generatePolicy] || record.generatePolicy }}</a-tag>
            <span v-else style="color:#bfbfbf">未标注</span>
          </template>
          <template v-else-if="column.key === 'disabled'">
            <a-switch
              :checked="!record.disabled"
              checked-children="启用"
              un-checked-children="停用"
              size="small"
              @change="(checked) => handleToggle(record, checked)"
            />
          </template>
          <template v-else-if="column.key === 'action'">
            <a-space size="small">
              <a-button type="link" size="small" @click="openEdit(record)">编辑</a-button>
              <a-popconfirm
                title="确定删除该驾驶舱布局？包重装会把它加回来 —— 日常请用「停用」"
                @confirm="handleDelete(record)"
              >
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
      :title="modal.isEdit ? '编辑驾驶舱布局' : '新增驾驶舱布局'"
      @ok="handleSave"
      :confirm-loading="modal.saving"
      width="660px"
      centered
      wrap-class-name="part-create-modal"
      :body-style="{ maxHeight: 'calc(100vh - 200px)', overflowY: 'auto', padding: '16px 20px 20px' }"
    >
      <a-form :model="modal.form" layout="vertical">
        <a-row :gutter="12">
          <a-col :span="12">
            <a-form-item label="视角编码" required>
              <a-input v-model:value="modal.form.code" placeholder="如 PRODUCT_MODEL" :disabled="modal.isEdit" />
              <div class="db-form-hint">视角类型即它的稳定身份：ENTERPRISE / PRODUCT_LINE / PRODUCT_MODEL</div>
            </a-form-item>
          </a-col>
          <a-col :span="12">
            <a-form-item label="名称" required>
              <a-input v-model:value="modal.form.name" placeholder="如 型号作战地图" />
            </a-form-item>
          </a-col>
        </a-row>

        <a-row :gutter="12">
          <a-col :span="8">
            <a-form-item label="受众">
              <a-input v-model:value="modal.form.audience" placeholder="如 RD/PM" />
            </a-form-item>
          </a-col>
          <a-col :span="8">
            <a-form-item label="页数">
              <a-input-number v-model:value="modal.form.pageCount" :min="1" style="width:100%" placeholder="可选" />
            </a-form-item>
          </a-col>
          <a-col :span="8">
            <a-form-item label="生成节奏">
              <a-input v-model:value="modal.form.generatePolicy" placeholder="如 MONTHLY_ALWAYS（留空=未标注）" />
            </a-form-item>
          </a-col>
        </a-row>

        <a-form-item label="分组件（JSON 数组，码）">
          <a-textarea
            v-model:value="modal.form.widgets"
            :rows="4"
            placeholder='["STAGE_READINESS","DELIVERABLE_MATRIX","REVIEW_STATUS","RISK_LIST","ACTION_ENTRY"]'
          />
          <div class="db-form-hint">
            常见码：HEALTH_SCORE_TREND 健康度趋势 / COST_ACHIEVE 成本达成 / RISK_TOP5 风险 Top5 / MODEL_RANKING 型号排名 /
            MODEL_COMPARE_TABLE 型号对比 / STAGE_DISTRIBUTION 阶段分布 / COST_TREND 成本趋势 / STAGE_READINESS 阶段就绪度 /
            DELIVERABLE_MATRIX 交付件矩阵 / REVIEW_STATUS 评审状态 / RISK_LIST 风险清单 / ACTION_ENTRY 行动入口。
            其中 <b>COST_ACHIEVE 同时是指标码</b>（可与「指标」对账）；其余为渲染件，暂无定义表。
            当前：{{ parseWidgets(modal.form.widgets).length }} 个
          </div>
        </a-form-item>

        <a-row :gutter="12">
          <a-col :span="12">
            <a-form-item label="行业 / 方法论">
              <a-select
                v-model:value="modal.form.industry"
                :options="industryOptions"
                placeholder="留空 = 通用"
                allow-clear
              />
            </a-form-item>
          </a-col>
          <a-col :span="6">
            <a-form-item label="排序">
              <a-input-number v-model:value="modal.form.sortOrder" :min="0" style="width:100%" />
            </a-form-item>
          </a-col>
          <a-col :span="6">
            <a-form-item label="状态">
              <a-switch v-model:checked="modal.form.enabled" checked-children="启用" un-checked-children="停用" />
            </a-form-item>
          </a-col>
        </a-row>

        <a-form-item label="描述">
          <a-textarea v-model:value="modal.form.description" :rows="2" />
        </a-form-item>
      </a-form>
    </a-modal>

  </div>
</template>

<script setup>
import { ref, reactive, computed, onMounted } from 'vue'
import { message } from 'ant-design-vue'
import { PlusOutlined, DashboardOutlined } from '@ant-design/icons-vue'
import DataTable from '@/components/DataTable.vue'
// 分组件码 → 中文名：与实例面板、整机视图页签共用同一份字典（见 utils/dashboardWidgets）
import { WIDGET_LABEL } from '@/utils/dashboardWidgets'
import { useUserStore } from '@/stores/user'
import {
  listDashboardTemplates,
  createDashboardTemplate,
  updateDashboardTemplate,
  deleteDashboardTemplate,
  setDashboardTemplateDisabled
} from '@/api'

const userStore = useUserStore()
const isPlatformAdmin = computed(() => userStore.roles?.includes('PLATFORM_ADMIN'))

const templates = ref([])
const loading = ref(false)
const onlyEnabled = ref(false)

// ========== 常量（与包内 dashboards.json / 文档第 17 节同源） ==========
// WIDGET_LABEL 已抽到 utils/dashboardWidgets（2026-10-26）—— 三处共用一份，避免改名只改一半
const POLICY_LABEL = {
  MONTHLY_ALWAYS: '每月必出',
  MONTHLY_IF_ACTIVE: '有活跃型号才出',
  ON_DEMAND_OR_THRESHOLD: '按需或按阈值'
}

const INDUSTRY_OPTIONS = [
  { value: 'TRADITIONAL', label: '传统产品研发', desc: '通用产品研发流程：市场验证→需求论证→方案设计→详细设计→工艺规划→试产' },
  { value: 'IPD', label: '电子高科 (IPD)', desc: '集成产品开发：概念→计划→开发→验证→发布→生命周期管理' },
  { value: 'MILITARY', label: '军工', desc: 'GJB 流程：论证→立项→方案研制→设计定型→生产定型' },
  { value: 'AUTOMOTIVE', label: '汽车', desc: 'APQP 流程：概念设计→开发验证→生产准备→量产→上市' }
]
const INDUSTRY_COLOR = { TRADITIONAL: 'blue', IPD: 'purple', MILITARY: 'red', AUTOMOTIVE: 'green' }

function industryLabel(code) { return (INDUSTRY_OPTIONS.find(o => o.value === code) || {}).label || code || '通用' }
function industryColor(code) { return INDUSTRY_COLOR[code] || 'default' }
function industryDesc(code) { return (INDUSTRY_OPTIONS.find(o => o.value === code) || {}).desc || '' }
const industryOptions = INDUSTRY_OPTIONS.map(({ value, label }) => ({ value, label }))

// ========== JSON 解析 ==========
function parseWidgets(json) {
  if (!json) return []
  try {
    const v = typeof json === 'string' ? JSON.parse(json) : json
    return Array.isArray(v) ? v : []
  } catch { return [] }
}

// ========== 统计 ==========
const hasAnyTemplate = computed(() => templates.value.length > 0)
const widgetTotal = computed(() => templates.value.reduce((sum, t) => sum + parseWidgets(t.widgets).length, 0))
const primaryName = computed(() => {
  const p = templates.value.find(t => t.primaryScope)
  return p ? (p.name || p.code) : ''
})
const disabledCount = computed(() => templates.value.filter(t => t.disabled).length)

const filteredTemplates = computed(() => templates.value.filter(t => {
  if (onlyEnabled.value && t.disabled) return false
  return true
}))

// ========== 列 ==========
const columns = [
  { title: '视角', key: 'code', width: 210 },
  { title: '名称', dataIndex: 'name', key: 'name', width: 150 },
  { title: '受众', key: 'audience', width: 110 },
  { title: '页数', key: 'pageCount', width: 80 },
  { title: '分组件', key: 'widgets', width: 320 },
  { title: '生成节奏', key: 'generatePolicy', width: 140 },
  { title: '状态', key: 'disabled', width: 90 },
  { title: '操作', key: 'action', width: 130 }
]

// ========== 数据加载 ==========
async function loadTemplates() {
  loading.value = true
  try {
    const res = await listDashboardTemplates()
    if (res.code === 200) templates.value = res.data || []
  } catch { message.error('加载失败') }
  finally { loading.value = false }
}

// ========== 新增 / 编辑 ==========
const modal = reactive({
  visible: false, isEdit: false, saving: false, editingOid: null,
  form: emptyForm()
})

function emptyForm() {
  return {
    code: '', name: '', audience: '', pageCount: undefined,
    widgets: '', generatePolicy: '', industry: undefined, enabled: true,
    sortOrder: 0, description: ''
  }
}

function openCreate() {
  modal.editingOid = null; modal.isEdit = false
  modal.form = emptyForm()
  modal.form.sortOrder = templates.value.length + 1
  modal.visible = true
}

function openEdit(record) {
  modal.editingOid = record.oid; modal.isEdit = true
  modal.form = {
    code: record.code, name: record.name || '',
    audience: record.audience || '',
    pageCount: record.pageCount ?? undefined,
    widgets: typeof record.widgets === 'string' ? record.widgets : JSON.stringify(record.widgets || []),
    generatePolicy: record.generatePolicy || '',
    industry: record.industry || undefined,
    enabled: !record.disabled,
    sortOrder: record.sortOrder ?? 0,
    description: record.description || ''
  }
  modal.visible = true
}

async function handleSave() {
  const { code, name } = modal.form
  if (!code?.trim() && !modal.isEdit) { message.warning('视角编码不能为空'); return }
  if (!name?.trim()) { message.warning('名称不能为空'); return }
  const rawWidgets = (modal.form.widgets || '').trim()
  if (!rawWidgets) { message.warning('分组件不能为空 —— 空驾驶舱没有意义'); return }
  let widgets = []
  try {
    widgets = JSON.parse(rawWidgets)
    if (!Array.isArray(widgets)) throw new Error('not array')
  } catch { message.warning('分组件不是合法的 JSON 数组，请检查'); return }
  modal.saving = true
  try {
    // 后端 UPDATE 是 patch 语义：字段缺席（null）= 本次不改；空串（""）= 显式清空
    const payload = {
      code: code?.trim(),
      name: name.trim(),
      audience: modal.form.audience || '',
      pageCount: modal.form.pageCount ?? null,
      widgets: JSON.stringify(widgets),
      generatePolicy: modal.form.generatePolicy || '',
      industry: modal.form.industry || '',
      disabled: !modal.form.enabled,
      sortOrder: modal.form.sortOrder ?? 0,
      description: modal.form.description || ''
    }
    const res = modal.isEdit
      ? await updateDashboardTemplate(modal.editingOid, payload)
      : await createDashboardTemplate(payload)
    if (res.code === 200) {
      message.success(modal.isEdit ? '更新成功' : '创建成功')
      modal.visible = false
      await loadTemplates()
    } else {
      message.error(res.message || '操作失败')
    }
  } catch { message.error('操作失败') }
  finally { modal.saving = false }
}

// ========== 停用 / 删除 ==========
async function handleToggle(record, checked) {
  try {
    const res = await setDashboardTemplateDisabled(record.oid, checked)
    if (res.code === 200) {
      message.success(checked ? '已启用' : '已停用（重装行业包不会改回）')
      await loadTemplates()
    } else {
      message.error(res.message || '操作失败')
    }
  } catch { message.error('操作失败') }
}

async function handleDelete(record) {
  try {
    const res = await deleteDashboardTemplate(record.oid)
    if (res.code === 200) {
      message.success('已删除')
      await loadTemplates()
    } else {
      message.error(res.message || '删除失败')
    }
  } catch { message.error('删除失败') }
}


onMounted(loadTemplates)
</script>

<style scoped>
.db-page { display: flex; flex-direction: column; flex: 1; min-height: 0; padding: 0; }
.db-header { padding: 16px 0; border-bottom: 1px solid #f0f0f0; margin-bottom: 12px; flex-shrink: 0; }
.db-title { margin: 0 0 4px 0; font-size: 18px; font-weight: 600; }
.db-subtitle { font-size: 13px; color: #8c8c8c; line-height: 1.6; }
.db-stats-bar { display: flex; align-items: center; gap: 20px; margin-bottom: 12px; padding: 8px 16px; background: #fafafa; border-radius: 6px; flex-shrink: 0; }
.db-stat-item { display: flex; align-items: center; gap: 6px; }
.db-stat-icon { font-size: 16px; color: #1677ff; }
.db-stat-value { font-size: 18px; font-weight: 600; }
.db-stat-label { font-size: 12px; color: #8c8c8c; }
.db-stat-hint { font-size: 12px; color: #fa8c16; }
.db-table-wrap { margin-top: 0; flex: 1; min-height: 320px; display: flex; flex-direction: column; overflow: hidden; }
.db-form-hint { font-size: 12px; color: #8c8c8c; margin-top: 4px; line-height: 1.6; }
.db-industry-card { border: 1px solid #f0f0f0; transition: all 0.2s; }
.db-industry-card:hover { border-color: #1677ff; box-shadow: 0 2px 8px rgba(22,119,255,0.1); }
.db-industry-header { display: flex; align-items: center; justify-content: space-between; margin-bottom: 8px; }
.db-industry-count { font-size: 12px; color: #8c8c8c; }
.db-industry-desc { font-size: 12px; color: #595959; min-height: 40px; margin-bottom: 12px; line-height: 1.5; }

/* 复用 Part 编辑窗口的紧凑样式（与前面几个模板页同款） */
.part-create-modal :deep(.ant-modal-header) { padding: 12px 16px; border-bottom: 1px solid #f0f0f0; }
.part-create-modal :deep(.ant-modal-title) { font-size: 15px; }
.part-create-modal :deep(.ant-modal-footer) { padding: 10px 16px; border-top: 1px solid #f0f0f0; }
.part-create-modal :deep(.ant-form-item) { margin-bottom: 12px; }
.part-create-modal :deep(.ant-form-item-label) { padding-bottom: 2px; }
.part-create-modal :deep(.ant-form-item-label > label) { font-size: 13px; color: #595959; }
</style>
