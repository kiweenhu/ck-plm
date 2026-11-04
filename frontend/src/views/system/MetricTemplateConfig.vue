<template>
  <div class="mt-page">
    <div class="mt-header">
      <div class="mt-header-left">
        <h3 class="mt-title">指标</h3>
        <span class="mt-subtitle">
          行业包把驾驶舱指标装入企业一份（智能硬件 IPD：基础包共用 6 项 + 本包增量 5 项 = 11 项）；
          企业在此裁剪 —— 改分层、改取数依据、改数据就绪度，或<b>停用</b>（停用不删除，重装行业包也不会被改回）。
          指标与其它几类不同：它是<b>计算口径 + 数据就绪度</b>。分层给不同的人看（业务层给研发总监，经营层给老板）；
          <b>C 级指标第一版显示「暂未评估」，禁止编造数值</b> —— 一个编出来的数字被客户当场问穿，整个健康度报告的可信度就归零了。
        </span>
      </div>
    </div>

    <div class="mt-stats-bar">
      <div class="mt-stat-item">
        <AreaChartOutlined class="mt-stat-icon" />
        <span class="mt-stat-value">{{ templates.length }}</span>
        <span class="mt-stat-label">指标总数</span>
      </div>
      <div class="mt-stat-item">
        <span class="mt-stat-value">{{ businessCount }}</span>
        <span class="mt-stat-label">业务层（总监看）</span>
      </div>
      <div class="mt-stat-item">
        <span class="mt-stat-value">{{ executiveCount }}</span>
        <span class="mt-stat-label">经营层（老板看）</span>
      </div>
      <div class="mt-stat-item">
        <span class="mt-stat-value">{{ readyCount }}</span>
        <span class="mt-stat-label">A/B 级（可算）</span>
      </div>
      <div class="mt-stat-item">
        <span class="mt-stat-value" style="color:#fa8c16">{{ pendingCount }}</span>
        <span class="mt-stat-label">C 级（暂未评估）</span>
      </div>
      <div v-if="!isPlatformAdmin && !hasAnyTemplate" class="mt-stat-hint">
        暂无指标：装行业包会自动带入
      </div>
    </div>

    <div class="mt-table-wrap">
      <DataTable
        :columns="columns"
        :data-source="filteredTemplates"
        :loading="loading"
        search-placeholder="搜索指标编码 / 名称 / 取数依据..."
        :search-fields="['code', 'name', 'source']"
        :enable-resize="true"
        scroll-x="max-content"
        :show-column-toggle="true"
        stretch
        row-key="oid"
        size="middle"
      >
        <template #toolbar>
          <a-space>
            <a-select
              v-model:value="layerFilter"
              :options="layerOptions"
              placeholder="全部分层"
              style="width:160px"
              allow-clear
              size="small"
            />
            <a-select
              v-model:value="levelFilter"
              :options="levelOptions"
              placeholder="全部就绪度"
              style="width:150px"
              allow-clear
              size="small"
            />
            <a-checkbox v-model:checked="onlyEnabled">仅看启用</a-checkbox>
            <a-button type="primary" size="small" @click="openCreate">
              <template #icon><PlusOutlined /></template>
              新增指标
            </a-button>
          </a-space>
        </template>

        <template #bodyCell="{ column, record }">
          <template v-if="column.key === 'code'">
            <a-tag color="blue">{{ record.code }}</a-tag>
          </template>
          <template v-else-if="column.key === 'layer'">
            <a-tag :color="record.layer === 'executiveLayer' ? 'purple' : 'cyan'">
              {{ LAYER_LABEL[record.layer] || record.layer }}
            </a-tag>
          </template>
          <template v-else-if="column.key === 'level'">
            <a-tooltip :title="levelTooltip(record.level)">
              <a-tag :color="LEVEL_COLOR[record.level] || 'default'">
                {{ LEVEL_LABEL[record.level] || record.level }}
              </a-tag>
            </a-tooltip>
          </template>
          <template v-else-if="column.key === 'source'">
            <span v-if="record.source" class="mt-code">{{ record.source }}</span>
            <span v-else style="color:#bfbfbf">-</span>
          </template>
          <template v-else-if="column.key === 'industryNote'">
            <a-tooltip v-if="record.industryNote" :title="record.industryNote">
              <span class="mt-ellipsis">{{ record.industryNote }}</span>
            </a-tooltip>
            <span v-else style="color:#bfbfbf">-</span>
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
                title="确定删除该指标？包重装会把它加回来 —— 日常请用「停用」"
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
      :title="modal.isEdit ? '编辑指标' : '新增指标'"
      @ok="handleSave"
      :confirm-loading="modal.saving"
      width="640px"
      centered
      wrap-class-name="part-create-modal"
      :body-style="{ maxHeight: 'calc(100vh - 200px)', overflowY: 'auto', padding: '16px 20px 20px' }"
    >
      <a-form :model="modal.form" layout="vertical">
        <a-row :gutter="12">
          <a-col :span="12">
            <a-form-item label="指标编码" required>
              <a-input v-model:value="modal.form.code" placeholder="如 BOM_TOTAL_COST" :disabled="modal.isEdit" />
            </a-form-item>
          </a-col>
          <a-col :span="12">
            <a-form-item label="名称" required>
              <a-input v-model:value="modal.form.name" placeholder="如 BOM 卷积成本" />
            </a-form-item>
          </a-col>
        </a-row>

        <a-row :gutter="12">
          <a-col :span="12">
            <a-form-item label="分层" required>
              <a-select v-model:value="modal.form.layer" :options="layerOptions" />
            </a-form-item>
          </a-col>
          <a-col :span="12">
            <a-form-item label="数据就绪度" required>
              <a-select v-model:value="modal.form.level" :options="levelOptions" />
            </a-form-item>
          </a-col>
        </a-row>

        <a-form-item label="取数依据">
          <a-input v-model:value="modal.form.source" placeholder="如 BomCostCalculator / missingCostLines / PartAlternate" />
          <div class="mt-form-hint">
            就绪度与来源应当对得上：A/B 级的来源是平台或包内已落地之物；C 级的来源尚未落地（如 ReviewIssue 需评审引擎、上市时间偏差需项目实体）。
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

        <a-form-item label="行业注解 / 口径说明">
          <a-textarea v-model:value="modal.form.industryNote" :rows="3" />
        </a-form-item>
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
import { PlusOutlined, AreaChartOutlined } from '@ant-design/icons-vue'
import DataTable from '@/components/DataTable.vue'
import { useUserStore } from '@/stores/user'
import {
  listMetricTemplates,
  createMetricTemplate,
  updateMetricTemplate,
  deleteMetricTemplate,
  setMetricTemplateDisabled
} from '@/api'

const userStore = useUserStore()
const isPlatformAdmin = computed(() => userStore.roles?.includes('PLATFORM_ADMIN'))

const templates = ref([])
const loading = ref(false)
const onlyEnabled = ref(false)
const layerFilter = ref(undefined)
const levelFilter = ref(undefined)

// ========== 常量（与包内 metrics.json / 文档第 16 节同源） ==========
const LAYER_LABEL = {
  businessLayer: '业务层（总监看）',
  executiveLayer: '经营层（老板看）'
}
const LEVEL_LABEL = { A: 'A 级 · 可就绪', B: 'B 级 · 可就绪', C: 'C 级 · 暂未评估' }
const LEVEL_COLOR = { A: 'green', B: 'blue', C: 'orange' }

const layerOptions = Object.entries(LAYER_LABEL).map(([value, label]) => ({ value, label }))
const levelOptions = Object.entries(LEVEL_LABEL).map(([value, label]) => ({ value, label }))

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

/** C 级的 tooltip 把硬规则原样带出来，避免有人把它当普通待补指标 */
function levelTooltip(level) {
  if (level === 'C') {
    return 'C 级 = 数据来源尚未落地：第一版显示「暂未评估」，禁止编造数值'
  }
  if (level === 'A') return 'A 级：平台已有实现，装上即可计算'
  if (level === 'B') return 'B 级：依赖的属性/数据已定义，落地后即可计算'
  return ''
}

// ========== 统计与筛选 ==========
const hasAnyTemplate = computed(() => templates.value.length > 0)
const businessCount = computed(() => templates.value.filter(t => t.layer === 'businessLayer').length)
const executiveCount = computed(() => templates.value.filter(t => t.layer === 'executiveLayer').length)
const readyCount = computed(() => templates.value.filter(t => t.level === 'A' || t.level === 'B').length)
const pendingCount = computed(() => templates.value.filter(t => t.level === 'C').length)

const filteredTemplates = computed(() => templates.value.filter(t => {
  if (onlyEnabled.value && t.disabled) return false
  if (layerFilter.value && t.layer !== layerFilter.value) return false
  if (levelFilter.value && t.level !== levelFilter.value) return false
  return true
}))

// ========== 列 ==========
const columns = [
  { title: '编码', dataIndex: 'code', key: 'code', width: 170 },
  { title: '名称', dataIndex: 'name', key: 'name', width: 160 },
  { title: '分层', key: 'layer', width: 150 },
  { title: '数据就绪度', key: 'level', width: 140 },
  { title: '取数依据', key: 'source', width: 180 },
  { title: '行业注解 / 口径', key: 'industryNote', width: 320, ellipsis: true },
  { title: '状态', key: 'disabled', width: 90 },
  { title: '操作', key: 'action', width: 130 }
]

// ========== 数据加载 ==========
async function loadTemplates() {
  loading.value = true
  try {
    const res = await listMetricTemplates()
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
    code: '', name: '', layer: 'businessLayer', level: 'B', source: '',
    industry: undefined, enabled: true, sortOrder: 0,
    industryNote: '', description: ''
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
    layer: record.layer || 'businessLayer',
    level: record.level || 'B',
    source: record.source || '',
    industry: record.industry || undefined,
    enabled: !record.disabled,
    sortOrder: record.sortOrder ?? 0,
    industryNote: record.industryNote || '',
    description: record.description || ''
  }
  modal.visible = true
}

async function handleSave() {
  const { code, name, layer, level } = modal.form
  if (!code?.trim() && !modal.isEdit) { message.warning('编码不能为空'); return }
  if (!name?.trim()) { message.warning('名称不能为空'); return }
  if (!layer) { message.warning('请选择分层'); return }
  if (!level) { message.warning('请选择数据就绪度'); return }
  modal.saving = true
  try {
    // 后端 UPDATE 是 patch 语义：字段缺席（null）= 本次不改；空串（""）= 显式清空
    const payload = {
      code: code?.trim(),
      name: name.trim(),
      layer,
      level,
      source: modal.form.source || '',
      industry: modal.form.industry || '',
      disabled: !modal.form.enabled,
      sortOrder: modal.form.sortOrder ?? 0,
      industryNote: modal.form.industryNote || '',
      description: modal.form.description || ''
    }
    const res = modal.isEdit
      ? await updateMetricTemplate(modal.editingOid, payload)
      : await createMetricTemplate(payload)
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
    const res = await setMetricTemplateDisabled(record.oid, checked)
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
    const res = await deleteMetricTemplate(record.oid)
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
.mt-page { display: flex; flex-direction: column; flex: 1; min-height: 0; padding: 0; }
.mt-header { padding: 16px 0; border-bottom: 1px solid #f0f0f0; margin-bottom: 12px; flex-shrink: 0; }
.mt-title { margin: 0 0 4px 0; font-size: 18px; font-weight: 600; }
.mt-subtitle { font-size: 13px; color: #8c8c8c; line-height: 1.6; }
.mt-stats-bar { display: flex; align-items: center; gap: 20px; margin-bottom: 12px; padding: 8px 16px; background: #fafafa; border-radius: 6px; flex-shrink: 0; }
.mt-stat-item { display: flex; align-items: center; gap: 6px; }
.mt-stat-icon { font-size: 16px; color: #1677ff; }
.mt-stat-value { font-size: 18px; font-weight: 600; }
.mt-stat-label { font-size: 12px; color: #8c8c8c; }
.mt-stat-hint { font-size: 12px; color: #fa8c16; }
.mt-table-wrap { margin-top: 0; flex: 1; min-height: 320px; display: flex; flex-direction: column; overflow: hidden; }
.mt-code { font-family: Consolas, Monaco, monospace; font-size: 12px; color: #595959; }
.mt-ellipsis { display: inline-block; max-width: 310px; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; vertical-align: middle; color: #595959; }
.mt-form-hint { font-size: 12px; color: #8c8c8c; margin-top: 4px; line-height: 1.6; }
.mt-industry-card { border: 1px solid #f0f0f0; transition: all 0.2s; }
.mt-industry-card:hover { border-color: #1677ff; box-shadow: 0 2px 8px rgba(22,119,255,0.1); }
.mt-industry-header { display: flex; align-items: center; justify-content: space-between; margin-bottom: 8px; }
.mt-industry-count { font-size: 12px; color: #8c8c8c; }
.mt-industry-desc { font-size: 12px; color: #595959; min-height: 40px; margin-bottom: 12px; line-height: 1.5; }

/* 复用 Part 编辑窗口的紧凑样式（与前面几个模板页同款） */
.part-create-modal :deep(.ant-modal-header) { padding: 12px 16px; border-bottom: 1px solid #f0f0f0; }
.part-create-modal :deep(.ant-modal-title) { font-size: 15px; }
.part-create-modal :deep(.ant-modal-footer) { padding: 10px 16px; border-top: 1px solid #f0f0f0; }
.part-create-modal :deep(.ant-form-item) { margin-bottom: 12px; }
.part-create-modal :deep(.ant-form-item-label) { padding-bottom: 2px; }
.part-create-modal :deep(.ant-form-item-label > label) { font-size: 13px; color: #595959; }
</style>
