<template>
  <div class="rv-page">
    <div class="rv-header">
      <div class="rv-header-left">
        <h3 class="rv-title">评审</h3>
        <span class="rv-subtitle">
          行业包把评审装入企业一份（如智能硬件 IPD 的 8 份：原理图 / 概念与需求 / 规格与架构 / 结构 / 软件固件 / 验证与认证 / 量产就绪 / 生命周期复盘）；
          企业在此裁剪 —— 增删检查项、改级别、改结论选项，或<b>停用</b>（停用不删除，重装行业包也不会被改回）。
          每份评审挂在某道门上：交付件的完成条件里「评审通过」引用的就是这里的编码。
        </span>
      </div>
    </div>

    <div class="rv-stats-bar">
      <div class="rv-stat-item">
        <AuditOutlined class="rv-stat-icon" />
        <span class="rv-stat-value">{{ templates.length }}</span>
        <span class="rv-stat-label">评审总数</span>
      </div>
      <div class="rv-stat-item">
        <span class="rv-stat-value">{{ totalCheckItems }}</span>
        <span class="rv-stat-label">检查项总数</span>
      </div>
      <div class="rv-stat-item">
        <span class="rv-stat-value">{{ totalBlockItems }}</span>
        <span class="rv-stat-label">其中 BLOCK 级</span>
      </div>
      <div class="rv-stat-item">
        <span class="rv-stat-value">{{ gateCoverage }}</span>
        <span class="rv-stat-label">覆盖阶段门</span>
      </div>
      <div class="rv-stat-item">
        <span class="rv-stat-value">{{ disabledCount }}</span>
        <span class="rv-stat-label">已停用</span>
      </div>
      <div v-if="!isPlatformAdmin && !hasAnyTemplate" class="rv-stat-hint">
        暂无评审：装行业包会自动带入
      </div>
    </div>

    <div class="rv-table-wrap">
      <DataTable
        :columns="columns"
        :data-source="filteredTemplates"
        :loading="loading"
        search-placeholder="搜索评审编码 / 名称 / 检查项..."
        :search-fields="['code', 'name', 'gate', 'processKey']"
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
              v-model:value="gateFilter"
              :options="gateFilterOptions"
              placeholder="全部阶段门"
              style="width:200px"
              allow-clear
              size="small"
            />
            <a-checkbox v-model:checked="onlyEnabled">仅看启用</a-checkbox>
            <a-button type="primary" size="small" @click="openCreate">
              <template #icon><PlusOutlined /></template>
              新增评审
            </a-button>
          </a-space>
        </template>

        <template #bodyCell="{ column, record }">
          <template v-if="column.key === 'code'">
            <a-tag color="blue">{{ record.code }}</a-tag>
          </template>
          <template v-else-if="column.key === 'gate'">
            <!-- 门名取自「阶段门模板」同一份数据（单一来源，不硬编码；查不到回落原码） -->
            <a-tag color="orange" :title="record.gate">{{ gateLabel(record.gate) }}</a-tag>
          </template>
          <template v-else-if="column.key === 'type'">
            <a-tag :color="record.type === 'DECISION' ? 'purple' : 'cyan'">
              {{ REVIEW_TYPE_LABEL[record.type] || record.type }}
            </a-tag>
          </template>
          <template v-else-if="column.key === 'checkItems'">
            <a-tooltip>
              <template #title>
                <div v-for="i in parseItems(record.checkItems)" :key="i.seq" style="line-height:1.7">
                  <span :style="{ color: i.severity === 'BLOCK' ? '#ff7875' : '#ffd591' }">{{ i.severity === 'BLOCK' ? '■' : '□' }}</span>
                  {{ i.seq }}. {{ i.text }}
                </div>
              </template>
              <a-space size="4">
                <span>{{ parseItems(record.checkItems).length }} 项</span>
                <a-tag v-if="blockCount(record) > 0" color="red" size="small">{{ blockCount(record) }} BLOCK</a-tag>
                <a-tag v-if="parseItems(record.checkItems).length - blockCount(record) > 0" color="orange" size="small">
                  {{ parseItems(record.checkItems).length - blockCount(record) }} WARN
                </a-tag>
              </a-space>
            </a-tooltip>
          </template>
          <template v-else-if="column.key === 'verdictOptions'">
            <a-space size="4" wrap>
              <a-tag v-for="v in parseArray(record.verdictOptions)" :key="v" size="small" style="white-space:nowrap">
                {{ VERDICT_LABEL[v] || v }}
              </a-tag>
              <span v-if="parseArray(record.verdictOptions).length === 0" style="color:#bfbfbf">-</span>
            </a-space>
          </template>
          <template v-else-if="column.key === 'process'">
            <a-tag v-if="record.processKey" color="green">{{ record.processKey }}</a-tag>
            <span v-else style="color:#bfbfbf">无流程</span>
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
                title="确定删除该评审？包重装会把它加回来 —— 日常请用「停用」"
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
      :title="modal.isEdit ? '编辑评审' : '新增评审'"
      @ok="handleSave"
      :confirm-loading="modal.saving"
      width="700px"
      centered
      wrap-class-name="part-create-modal"
      :body-style="{ maxHeight: 'calc(100vh - 200px)', overflowY: 'auto', padding: '16px 20px 20px' }"
    >
      <a-form :model="modal.form" layout="vertical">
        <a-row :gutter="12">
          <a-col :span="12">
            <a-form-item label="评审编码" required>
              <a-input v-model:value="modal.form.code" placeholder="如 TR4_HW_SCHEMATIC" :disabled="modal.isEdit" />
            </a-form-item>
          </a-col>
          <a-col :span="12">
            <a-form-item label="名称" required>
              <a-input v-model:value="modal.form.name" placeholder="如 原理图技术评审" />
            </a-form-item>
          </a-col>
        </a-row>

        <a-row :gutter="12">
          <a-col :span="8">
            <a-form-item label="归属阶段门" required>
              <a-select
                v-model:value="modal.form.gate"
                :options="gateOptions"
                placeholder="来自阶段门"
                show-search
                option-filter-prop="label"
              />
            </a-form-item>
          </a-col>
          <a-col :span="8">
            <a-form-item label="评审类型" required>
              <a-select v-model:value="modal.form.type" :options="typeOptions" />
            </a-form-item>
          </a-col>
          <a-col :span="8">
            <a-form-item label="承载流程 key">
              <a-input v-model:value="modal.form.processKey" placeholder="如 technical-review（留空=无流程）" />
            </a-form-item>
          </a-col>
        </a-row>

        <a-form-item label="检查项（JSON 数组，逐条带 BLOCK / WARN 级别）">
          <a-textarea
            v-model:value="modal.form.checkItems"
            :rows="6"
            placeholder='[{"seq":1,"text":"电源架构是否满足各级电压/电流余量要求（≥20% 降额）","severity":"BLOCK"}]'
          />
          <div class="rv-form-hint">
            这些条目是行业 know-how 的落点：写代码的人想不出来、教科书也抄不到（如「20% 降额」「封装与实物库一致」来自真实事故）。
            当前共 {{ parseItems(modal.form.checkItems).length }} 项，其中 BLOCK {{ parseItems(modal.form.checkItems).filter(i => i.severity === 'BLOCK').length }} 项。
          </div>
        </a-form-item>

        <a-row :gutter="12">
          <a-col :span="12">
            <a-form-item label="输出类型（JSON 数组）">
              <a-textarea
                v-model:value="modal.form.outputTypes"
                :rows="2"
                placeholder='["ISSUE_LIST","VERDICT"]'
              />
            </a-form-item>
          </a-col>
          <a-col :span="12">
            <a-form-item label="结论选项（JSON 数组）">
              <a-textarea
                v-model:value="modal.form.verdictOptions"
                :rows="2"
                placeholder='["PASS","PASS_WITH_CONDITION","FAIL"]'
              />
            </a-form-item>
          </a-col>
        </a-row>

        <a-row :gutter="12">
          <a-col :span="8">
            <a-form-item label="行业 / 方法论">
              <a-select
                v-model:value="modal.form.industry"
                :options="industryOptions"
                placeholder="留空 = 通用"
                allow-clear
              />
            </a-form-item>
          </a-col>
          <a-col :span="8">
            <a-form-item label="排序">
              <a-input-number v-model:value="modal.form.sortOrder" :min="0" style="width:100%" />
            </a-form-item>
          </a-col>
          <a-col :span="8">
            <a-form-item label="状态">
              <a-switch v-model:checked="modal.form.enabled" checked-children="启用" un-checked-children="停用" />
            </a-form-item>
          </a-col>
        </a-row>

        <a-form-item label="行业注解（这份评审为什么这么设）">
          <a-textarea v-model:value="modal.form.industryNote" :rows="2" />
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
import { PlusOutlined, AuditOutlined } from '@ant-design/icons-vue'
import DataTable from '@/components/DataTable.vue'
import { useUserStore } from '@/stores/user'
import {
  listReviewTemplates,
  createReviewTemplate,
  updateReviewTemplate,
  deleteReviewTemplate,
  setReviewTemplateDisabled,
  listGateTemplates
} from '@/api'

const userStore = useUserStore()
const isPlatformAdmin = computed(() => userStore.roles?.includes('PLATFORM_ADMIN'))

const templates = ref([])
const loading = ref(false)
const onlyEnabled = ref(false)
const gateFilter = ref(undefined)

// ========== 常量（与行业包里的码同源） ==========
const REVIEW_TYPE_LABEL = { TECHNICAL: '技术评审', DECISION: '决策评审' }
const VERDICT_LABEL = {
  PASS: '通过',
  PASS_WITH_CONDITION: '有条件通过',
  FAIL: '不通过'
}

const INDUSTRY_OPTIONS = [
  { value: 'TRADITIONAL', label: '传统产品研发', desc: '通用产品研发流程：市场验证→需求论证→方案设计→详细设计→工艺规划→试产' },
  { value: 'IPD', label: '电子高科 (IPD)', desc: '集成产品开发：概念→计划→开发→验证→发布→生命周期管理' },
  { value: 'MILITARY', label: '军工', desc: 'GJB 流程：论证→立项→方案→工程研制→设计定型→生产定型' },
  { value: 'AUTOMOTIVE', label: '汽车', desc: 'APQP 流程：概念设计→开发验证→生产准备→量产→上市' }
]
const INDUSTRY_COLOR = { TRADITIONAL: 'blue', IPD: 'purple', MILITARY: 'red', AUTOMOTIVE: 'green' }

const typeOptions = [
  { value: 'TECHNICAL', label: '技术评审' },
  { value: 'DECISION', label: '决策类评审' }
]

function industryLabel(code) { return (INDUSTRY_OPTIONS.find(o => o.value === code) || {}).label || code || '通用' }
function industryColor(code) { return INDUSTRY_COLOR[code] || 'default' }
function industryDesc(code) { return (INDUSTRY_OPTIONS.find(o => o.value === code) || {}).desc || '' }
const industryOptions = INDUSTRY_OPTIONS.map(({ value, label }) => ({ value, label }))

// ========== 门：单一来源（阶段门模板），不在前端硬编码 ==========
const gateOptions = ref([])
const gateNameMap = ref({})

function gateLabel(code) {
  if (!code) return '-'
  return gateNameMap.value[code] || code
}

async function loadGates() {
  try {
    const res = await listGateTemplates()
    const list = (res.code === 200 && Array.isArray(res.data)) ? res.data : []
    gateOptions.value = list.map(g => ({ value: g.code, label: `${g.name}（${g.code}）` }))
    const map = {}
    for (const g of list) map[g.code] = `${g.code} ${g.name}`
    gateNameMap.value = map
  } catch { /* 门读不到不阻断本页：归属门列回落显示原码 */ }
}

// ========== JSON 解析 ==========
function parseArray(json) {
  if (!json) return []
  try {
    const v = typeof json === 'string' ? JSON.parse(json) : json
    return Array.isArray(v) ? v : []
  } catch { return [] }
}

function parseItems(json) {
  return parseArray(json).filter(i => i && typeof i === 'object')
}

function blockCount(record) {
  return parseItems(record.checkItems).filter(i => i.severity === 'BLOCK').length
}

// ========== 统计与筛选 ==========
const hasAnyTemplate = computed(() => templates.value.length > 0)
const totalCheckItems = computed(() =>
  templates.value.reduce((sum, t) => sum + parseItems(t.checkItems).length, 0))
const totalBlockItems = computed(() =>
  templates.value.reduce((sum, t) => sum + parseItems(t.checkItems).filter(i => i.severity === 'BLOCK').length, 0))
const disabledCount = computed(() => templates.value.filter(t => t.disabled).length)
const gateCoverage = computed(() => {
  const set = new Set(templates.value.filter(t => !t.disabled).map(t => t.gate).filter(Boolean))
  return set.size
})

const gateFilterOptions = computed(() => {
  const codes = [...new Set(templates.value.map(t => t.gate).filter(Boolean))]
  return codes.map(code => ({ value: code, label: gateLabel(code) }))
})

const filteredTemplates = computed(() => templates.value.filter(t => {
  if (onlyEnabled.value && t.disabled) return false
  if (gateFilter.value && t.gate !== gateFilter.value) return false
  return true
}))

// ========== 列 ==========
const columns = [
  { title: '编码', dataIndex: 'code', key: 'code', width: 190 },
  { title: '名称', dataIndex: 'name', key: 'name', width: 190 },
  { title: '归属阶段门', key: 'gate', width: 170 },
  { title: '类型', key: 'type', width: 110 },
  { title: '检查项', key: 'checkItems', width: 170 },
  { title: '结论选项', key: 'verdictOptions', width: 190 },
  { title: '流程', key: 'process', width: 140 },
  { title: '状态', key: 'disabled', width: 90 },
  { title: '操作', key: 'action', width: 130 }
]

// ========== 数据加载 ==========
async function loadTemplates() {
  loading.value = true
  try {
    const res = await listReviewTemplates()
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
    code: '', name: '', gate: undefined, type: 'TECHNICAL', processKey: '',
    checkItems: '', outputTypes: '["ISSUE_LIST","VERDICT"]',
    verdictOptions: '["PASS","PASS_WITH_CONDITION","FAIL"]',
    industry: undefined, enabled: true, sortOrder: 0, industryNote: '', description: ''
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
    gate: record.gate, type: record.type || 'TECHNICAL',
    processKey: record.processKey || '',
    checkItems: typeof record.checkItems === 'string' ? record.checkItems : JSON.stringify(record.checkItems || []),
    outputTypes: typeof record.outputTypes === 'string' ? record.outputTypes : JSON.stringify(record.outputTypes || []),
    verdictOptions: typeof record.verdictOptions === 'string' ? record.verdictOptions : JSON.stringify(record.verdictOptions || []),
    industry: record.industry || undefined,
    enabled: !record.disabled,
    sortOrder: record.sortOrder ?? 0,
    industryNote: record.industryNote || '',
    description: record.description || ''
  }
  modal.visible = true
}

async function handleSave() {
  const { code, name, gate, type } = modal.form
  if (!code?.trim() && !modal.isEdit) { message.warning('编码不能为空'); return }
  if (!name?.trim()) { message.warning('名称不能为空'); return }
  if (!gate) { message.warning('请选择归属阶段门'); return }
  if (!type) { message.warning('请选择评审类型'); return }
  // 三个 JSON 字段逐个校验（宁可拦下来，也不把坏 JSON 写进库）
  const jsonFields = [
    ['checkItems', '检查项'],
    ['outputTypes', '输出类型'],
    ['verdictOptions', '结论选项']
  ]
  for (const [key, label] of jsonFields) {
    const raw = (modal.form[key] || '').trim()
    if (!raw) continue
    try { JSON.parse(raw) } catch { message.warning(label + '不是合法 JSON，请检查'); return }
  }
  modal.saving = true
  try {
    // 后端 UPDATE 是 patch 语义：字段缺席（null）= 本次不改；空串（""）= 显式清空
    const payload = {
      code: code?.trim(),
      name: name.trim(),
      gate,
      type,
      processKey: modal.form.processKey || '',
      checkItems: (modal.form.checkItems || '').trim(),
      outputTypes: (modal.form.outputTypes || '').trim(),
      verdictOptions: (modal.form.verdictOptions || '').trim(),
      industry: modal.form.industry || '',
      disabled: !modal.form.enabled,
      sortOrder: modal.form.sortOrder ?? 0,
      industryNote: modal.form.industryNote || '',
      description: modal.form.description || ''
    }
    const res = modal.isEdit
      ? await updateReviewTemplate(modal.editingOid, payload)
      : await createReviewTemplate(payload)
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
    const res = await setReviewTemplateDisabled(record.oid, checked)
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
    const res = await deleteReviewTemplate(record.oid)
    if (res.code === 200) {
      message.success('已删除')
      await loadTemplates()
    } else {
      message.error(res.message || '删除失败')
    }
  } catch { message.error('删除失败') }
}


onMounted(() => {
  loadGates()
  loadTemplates()
})
</script>

<style scoped>
.rv-page { display: flex; flex-direction: column; flex: 1; min-height: 0; padding: 0; }
.rv-header { padding: 16px 0; border-bottom: 1px solid #f0f0f0; margin-bottom: 12px; flex-shrink: 0; }
.rv-title { margin: 0 0 4px 0; font-size: 18px; font-weight: 600; }
.rv-subtitle { font-size: 13px; color: #8c8c8c; line-height: 1.6; }
.rv-stats-bar { display: flex; align-items: center; gap: 20px; margin-bottom: 12px; padding: 8px 16px; background: #fafafa; border-radius: 6px; flex-shrink: 0; }
.rv-stat-item { display: flex; align-items: center; gap: 6px; }
.rv-stat-icon { font-size: 16px; color: #1677ff; }
.rv-stat-value { font-size: 18px; font-weight: 600; }
.rv-stat-label { font-size: 12px; color: #8c8c8c; }
.rv-stat-hint { font-size: 12px; color: #fa8c16; }
.rv-table-wrap { margin-top: 0; flex: 1; min-height: 320px; display: flex; flex-direction: column; overflow: hidden; }
.rv-form-hint { font-size: 12px; color: #8c8c8c; margin-top: 4px; line-height: 1.6; }
.rv-industry-card { border: 1px solid #f0f0f0; transition: all 0.2s; }
.rv-industry-card:hover { border-color: #1677ff; box-shadow: 0 2px 8px rgba(22,119,255,0.1); }
.rv-industry-header { display: flex; align-items: center; justify-content: space-between; margin-bottom: 8px; }
.rv-industry-count { font-size: 12px; color: #8c8c8c; }
.rv-industry-desc { font-size: 12px; color: #595959; min-height: 40px; margin-bottom: 12px; line-height: 1.5; }

/* 复用 Part 编辑窗口的紧凑样式（与前面几个模板页同款） */
.part-create-modal :deep(.ant-modal-header) { padding: 12px 16px; border-bottom: 1px solid #f0f0f0; }
.part-create-modal :deep(.ant-modal-title) { font-size: 15px; }
.part-create-modal :deep(.ant-modal-footer) { padding: 10px 16px; border-top: 1px solid #f0f0f0; }
.part-create-modal :deep(.ant-form-item) { margin-bottom: 12px; }
.part-create-modal :deep(.ant-form-item-label) { padding-bottom: 2px; }
.part-create-modal :deep(.ant-form-item-label > label) { font-size: 13px; color: #595959; }
</style>
