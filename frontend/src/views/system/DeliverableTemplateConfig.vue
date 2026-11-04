<template>
  <div class="dl-page">
    <div class="dl-header">
      <div class="dl-header-left">
        <h3 class="dl-title">交付件</h3>
        <span class="dl-subtitle">
          行业包把交付件清单装入企业一份（如智能硬件 IPD 的 46 项）；企业在此裁剪 ——
          改强制项、改完成条件、改责任人，或<b>停用</b>（停用不删除，重装行业包也不会被改回）。
          创建产品系列 / 型号时，按此快照成实例。
        </span>
      </div>
    </div>

    <div class="dl-stats-bar">
      <div class="dl-stat-item">
        <SendOutlined class="dl-stat-icon" />
        <span class="dl-stat-value">{{ templates.length }}</span>
        <span class="dl-stat-label">交付件总数</span>
      </div>
      <div class="dl-stat-item">
        <span class="dl-stat-value">{{ mandatoryCount }}</span>
        <span class="dl-stat-label">强制项</span>
      </div>
      <div class="dl-stat-item">
        <span class="dl-stat-value">{{ disabledCount }}</span>
        <span class="dl-stat-label">已停用</span>
      </div>
      <div class="dl-stat-item">
        <span class="dl-stat-value">{{ stageCoverage }}</span>
        <span class="dl-stat-label">覆盖阶段</span>
      </div>
      <div v-if="!isPlatformAdmin && !hasAnyTemplate" class="dl-stat-hint">
        暂无交付件：装行业包会自动带入
      </div>
    </div>

    <div class="dl-table-wrap">
      <DataTable
        :columns="columns"
        :data-source="filteredTemplates"
        :loading="loading"
        search-placeholder="搜索交付件编码 / 名称 / 阶段..."
        :search-fields="['code', 'name', 'stageKey']"
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
              v-model:value="stageFilter"
              :options="stageFilterOptions"
              placeholder="全部阶段"
              style="width:180px"
              allow-clear
              size="small"
            />
            <a-checkbox v-model:checked="onlyEnabled">仅看启用</a-checkbox>
            <a-button type="primary" size="small" @click="openCreate">
              <template #icon><PlusOutlined /></template>
              新增交付件
            </a-button>
          </a-space>
        </template>

        <template #bodyCell="{ column, record }">
          <template v-if="column.key === 'code'">
            <a-tag color="blue">{{ record.code }}</a-tag>
          </template>
          <template v-else-if="column.key === 'stage'">
            <!-- 阶段名取自「研发阶段模板」同一份数据（单一来源，不硬编码；查不到回落原码） -->
            <a-tag color="geekblue" :title="record.stageKey">{{ stageLabel(record.stageKey) }}</a-tag>
          </template>
          <template v-else-if="column.key === 'mandatory'">
            <a-tag :color="record.mandatory ? 'red' : 'default'">{{ record.mandatory ? '强制' : '可选' }}</a-tag>
          </template>
          <template v-else-if="column.key === 'ownerRole'">
            <span v-if="record.ownerRole">{{ record.ownerRole }}</span>
            <span v-else style="color:#bfbfbf">-</span>
          </template>
          <template v-else-if="column.key === 'gate'">
            <a-tag v-if="record.gate" color="orange">{{ record.gate }}</a-tag>
            <span v-else style="color:#bfbfbf">-</span>
          </template>
          <template v-else-if="column.key === 'completionRule'">
            <a-tooltip :title="formatRule(record.completionRule)">
              <a-space size="4" wrap>
                <a-tag
                  v-for="t in parseRuleTypes(record.completionRule)"
                  :key="t"
                  size="small"
                  style="white-space:nowrap"
                >{{ RULE_TYPE_LABEL[t] || t }}</a-tag>
                <span v-if="parseRuleTypes(record.completionRule).length === 0" style="color:#bfbfbf">-</span>
              </a-space>
            </a-tooltip>
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
                title="确定删除该交付件？包重装会把它加回来 —— 日常请用「停用」"
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
      :title="modal.isEdit ? '编辑交付件' : '新增交付件'"
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
            <a-form-item label="交付件编码" required>
              <a-input v-model:value="modal.form.code" placeholder="如 DLV-SCHEMATIC" :disabled="modal.isEdit" />
            </a-form-item>
          </a-col>
          <a-col :span="12">
            <a-form-item label="名称" required>
              <a-input v-model:value="modal.form.name" placeholder="如 原理图设计评审通过" />
            </a-form-item>
          </a-col>
        </a-row>

        <a-row :gutter="12">
          <a-col :span="12">
            <a-form-item label="所属研发阶段" required>
              <!-- 选项来自本租户的研发阶段模板：交付件与阶段的关系就靠这一列 -->
              <a-select
                v-model:value="modal.form.stageKey"
                :options="stageOptions"
                placeholder="选择阶段（来自研发阶段）"
                show-search
                option-filter-prop="label"
              />
            </a-form-item>
          </a-col>
          <a-col :span="12">
            <a-form-item label="阶段门">
              <a-select
                v-model:value="modal.form.gate"
                :options="gateOptions"
                placeholder="须等于该阶段的门（如 TR3）"
                allow-clear
                show-search
              />
            </a-form-item>
          </a-col>
        </a-row>

        <a-row :gutter="12">
          <a-col :span="12">
            <a-form-item label="专业">
              <a-select
                v-model:value="modal.form.discipline"
                :options="disciplineOptions"
                placeholder="如 HARDWARE"
                allow-clear
                show-search
              />
            </a-form-item>
          </a-col>
          <a-col :span="12">
            <a-form-item label="责任人">
              <a-input v-model:value="modal.form.ownerRole" placeholder="如 HW_ENGINEER（角色编码）" />
            </a-form-item>
          </a-col>
        </a-row>

        <a-row :gutter="12">
          <a-col :span="8">
            <a-form-item label="强制项">
              <a-switch v-model:checked="modal.form.mandatory" checked-children="强制" un-checked-children="可选" />
            </a-form-item>
          </a-col>
          <a-col :span="8">
            <a-form-item label="工期偏移（天）">
              <a-input-number v-model:value="modal.form.dueOffsetDays" style="width:100%" placeholder="负数=提前" />
            </a-form-item>
          </a-col>
          <a-col :span="8">
            <a-form-item label="排序">
              <a-input-number v-model:value="modal.form.sortOrder" :min="0" style="width:100%" />
            </a-form-item>
          </a-col>
        </a-row>

        <a-form-item label="完成条件（JSON，系统据此自动判定「什么叫完成」）">
          <a-textarea
            v-model:value="modal.form.completionRule"
            :rows="4"
            placeholder='{"all":[{"type":"BOUND"},{"type":"LIFECYCLE","minState":"已评审"}]}'
          />
          <div class="dl-form-hint">
            可用类型：BOUND（绑定产出物）/ LIFECYCLE（达到某生命周期状态）/ REVIEW_PASSED（评审通过）/
            ISSUE_CLOSED（阻断问题清零）/ FIELD_CHECK（字段校验）/ COVERAGE（覆盖率）
          </div>
        </a-form-item>

        <a-row :gutter="12">
          <a-col :span="12">
            <a-form-item label="行业 / 方法论">
              <a-select
                v-model:value="modal.form.industry"
                :options="industryOptions"
                placeholder="留空 = 通用（不限定方法论）"
                allow-clear
              />
            </a-form-item>
          </a-col>
          <a-col :span="12">
            <a-form-item label="状态">
              <a-switch
                v-model:checked="modal.form.enabled"
                checked-children="启用"
                un-checked-children="停用"
              />
            </a-form-item>
          </a-col>
        </a-row>

        <a-form-item label="行业注解（这一条为什么这么定）">
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
import { PlusOutlined, SendOutlined } from '@ant-design/icons-vue'
import DataTable from '@/components/DataTable.vue'
import { useUserStore } from '@/stores/user'
import {
  listDeliverableTemplates,
  createDeliverableTemplate,
  updateDeliverableTemplate,
  deleteDeliverableTemplate,
  setDeliverableTemplateDisabled,
  listStageTemplates
} from '@/api'

const userStore = useUserStore()
const isPlatformAdmin = computed(() => userStore.roles?.includes('PLATFORM_ADMIN'))

const templates = ref([])
const loading = ref(false)
const onlyEnabled = ref(false)
const stageFilter = ref(undefined)

// ========== 常量（与行业包里的码同源：BOUND/LIFECYCLE/REVIEW_PASSED/ISSUE_CLOSED/FIELD_CHECK/COVERAGE） ==========
const RULE_TYPE_LABEL = {
  BOUND: '已绑定产出物',
  LIFECYCLE: '生命周期达标',
  REVIEW_PASSED: '评审通过',
  ISSUE_CLOSED: '阻断问题清零',
  FIELD_CHECK: '字段校验',
  COVERAGE: '覆盖率'
}

const INDUSTRY_OPTIONS = [
  { value: 'TRADITIONAL', label: '传统产品研发', desc: '通用产品研发流程：市场验证→需求论证→方案设计→详细设计→工艺规划→试产' },
  { value: 'IPD', label: '电子高科 (IPD)', desc: '集成产品开发：概念→计划→开发→验证→发布→生命周期管理' },
  { value: 'MILITARY', label: '军工', desc: 'GJB 流程：论证→立项→方案→工程研制→设计定型→生产定型' },
  { value: 'AUTOMOTIVE', label: '汽车', desc: 'APQP 流程：概念设计→开发验证→生产准备→量产→上市' }
]
const INDUSTRY_COLOR = { TRADITIONAL: 'blue', IPD: 'purple', MILITARY: 'red', AUTOMOTIVE: 'green' }

const DISCIPLINE_OPTIONS = [
  { value: 'PRODUCT',   label: '产品 (PRODUCT)' },
  { value: 'SYSTEM',    label: '系统 (SYSTEM)' },
  { value: 'HARDWARE',  label: '硬件 (HARDWARE)' },
  { value: 'SOFTWARE',  label: '软件 (SOFTWARE)' },
  { value: 'MECHANICAL',label: '结构 (MECHANICAL)' },
  { value: 'PROCESS',   label: '工艺 (PROCESS)' },
  { value: 'TEST',      label: '测试 (TEST)' },
  { value: 'QUALITY',   label: '质量 (QUALITY)' },
  { value: 'SUPPLY',    label: '供应链 (SUPPLY)' }
]

function industryLabel(code) { return (INDUSTRY_OPTIONS.find(o => o.value === code) || {}).label || code || '通用' }
function industryColor(code) { return INDUSTRY_COLOR[code] || 'default' }
function industryDesc(code) { return (INDUSTRY_OPTIONS.find(o => o.value === code) || {}).desc || '' }
const industryOptions = INDUSTRY_OPTIONS.map(({ value, label }) => ({ value, label }))
const disciplineOptions = DISCIPLINE_OPTIONS

// ========== 阶段：单一来源（研发阶段模板），不在前端硬编码 ==========
const stageOptions = ref([])
const stageNameMap = ref({})

function stageLabel(code) {
  if (!code) return '-'
  return stageNameMap.value[code] || code
}

const gateOptions = computed(() => {
  // 阶段门候选来自已加载的阶段模板同名数据；没读到就退回 IPD 的常见门（TR1–TR6 + 决策门）
  return ['TR1', 'TR2', 'TR3', 'TR4', 'TR5', 'TR6', 'Charter', 'PDCP', 'ADCP', 'LDCP']
    .map(v => ({ value: v, label: v }))
})

async function loadStages() {
  try {
    const res = await listStageTemplates()
    const list = (res.code === 200 && Array.isArray(res.data)) ? res.data : []
    stageOptions.value = list.map(s => ({ value: s.code, label: `${s.name}（${s.code}）` }))
    const map = {}
    for (const s of list) map[s.code] = s.name
    stageNameMap.value = map
  } catch { /* 阶段读不到不阻断本页：阶段列回落显示原码 */ }
}

// ========== 统计 ==========
const hasAnyTemplate = computed(() => templates.value.length > 0)
const mandatoryCount = computed(() => templates.value.filter(t => t.mandatory).length)
const disabledCount = computed(() => templates.value.filter(t => t.disabled).length)
const stageCoverage = computed(() => {
  const set = new Set(templates.value.filter(t => !t.disabled).map(t => t.stageKey).filter(Boolean))
  return set.size
})

// 阶段筛选选项：只列本租户实际有交付件的阶段
const stageFilterOptions = computed(() => {
  const codes = [...new Set(templates.value.map(t => t.stageKey).filter(Boolean))]
  return codes.map(code => ({ value: code, label: `${stageLabel(code)}（${code}）` }))
})

const filteredTemplates = computed(() => templates.value.filter(t => {
  if (onlyEnabled.value && t.disabled) return false
  if (stageFilter.value && t.stageKey !== stageFilter.value) return false
  return true
}))

// ========== 列 ==========
const columns = [
  { title: '编码', dataIndex: 'code', key: 'code', width: 170 },
  { title: '名称', dataIndex: 'name', key: 'name', width: 190 },
  { title: '所属阶段', key: 'stage', width: 130 },
  { title: '专业', dataIndex: 'discipline', key: 'discipline', width: 110 },
  { title: '强制', key: 'mandatory', width: 70 },
  { title: '责任人', key: 'ownerRole', width: 140 },
  { title: '阶段门', key: 'gate', width: 90 },
  { title: '完成条件', key: 'completionRule', width: 240 },
  { title: '工期偏移', dataIndex: 'dueOffsetDays', key: 'dueOffsetDays', width: 90 },
  { title: '状态', key: 'disabled', width: 90 },
  { title: '操作', key: 'action', width: 130 }
]

// ========== 完成条件 ==========
function parseRuleTypes(json) {
  if (!json) return []
  try {
    const rule = typeof json === 'string' ? JSON.parse(json) : json
    const all = rule?.all
    if (!Array.isArray(all)) return []
    return all.map(r => r?.type).filter(Boolean)
  } catch { return [] }
}

function formatRule(json) {
  const types = parseRuleTypes(json)
  if (types.length === 0) return '（未设置）'
  return types.map(t => RULE_TYPE_LABEL[t] || t).join(' · ')
}

// ========== 数据加载 ==========
async function loadTemplates() {
  loading.value = true
  try {
    const res = await listDeliverableTemplates()
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
    code: '', name: '', stageKey: undefined, gate: undefined, discipline: undefined,
    ownerRole: '', mandatory: false, dueOffsetDays: null, sortOrder: 0,
    completionRule: '', industry: undefined, enabled: true,
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
    stageKey: record.stageKey, gate: record.gate || undefined,
    discipline: record.discipline || undefined,
    ownerRole: record.ownerRole || '',
    mandatory: !!record.mandatory,
    dueOffsetDays: record.dueOffsetDays ?? null,
    sortOrder: record.sortOrder ?? 0,
    completionRule: typeof record.completionRule === 'string'
      ? record.completionRule
      : (record.completionRule ? JSON.stringify(record.completionRule) : ''),
    industry: record.industry || undefined,
    enabled: !record.disabled,
    industryNote: record.industryNote || '',
    description: record.description || ''
  }
  modal.visible = true
}

async function handleSave() {
  const { code, name, stageKey } = modal.form
  if (!code?.trim() && !modal.isEdit) { message.warning('编码不能为空'); return }
  if (!name?.trim()) { message.warning('名称不能为空'); return }
  if (!stageKey) { message.warning('请选择所属研发阶段'); return }
  const rule = (modal.form.completionRule || '').trim()
  if (rule) {
    try { JSON.parse(rule) } catch { message.warning('完成条件不是合法 JSON，请检查'); return }
  }
  modal.saving = true
  try {
    // 后端 UPDATE 是 patch 语义：字段缺席（null）= 本次不改；空串（""）= 显式清空。
    // 故"清空一个字符串字段"必须提交空串，提交 null 会被当成"不改"。
    const payload = {
      code: code?.trim(),
      name: name.trim(),
      stageKey,
      gate: modal.form.gate || '',
      discipline: modal.form.discipline || '',
      ownerRole: modal.form.ownerRole || '',
      mandatory: !!modal.form.mandatory,
      dueOffsetDays: modal.form.dueOffsetDays ?? null,
      sortOrder: modal.form.sortOrder ?? 0,
      // 后端字段为 String（JSON 字符串），与阶段模板的 defaultFolders 同款
      completionRule: rule || '',
      industry: modal.form.industry || '',
      disabled: !modal.form.enabled,
      industryNote: modal.form.industryNote || '',
      description: modal.form.description || ''
    }
    const res = modal.isEdit
      ? await updateDeliverableTemplate(modal.editingOid, payload)
      : await createDeliverableTemplate(payload)
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
    const res = await setDeliverableTemplateDisabled(record.oid, checked)
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
    const res = await deleteDeliverableTemplate(record.oid)
    if (res.code === 200) {
      message.success('已删除')
      await loadTemplates()
    } else {
      message.error(res.message || '删除失败')
    }
  } catch { message.error('删除失败') }
}


onMounted(() => {
  loadStages()
  loadTemplates()
})
</script>

<style scoped>
.dl-page { display: flex; flex-direction: column; flex: 1; min-height: 0; padding: 0; }
.dl-header { padding: 16px 0; border-bottom: 1px solid #f0f0f0; margin-bottom: 12px; flex-shrink: 0; }
.dl-title { margin: 0 0 4px 0; font-size: 18px; font-weight: 600; }
.dl-subtitle { font-size: 13px; color: #8c8c8c; line-height: 1.6; }
.dl-stats-bar { display: flex; align-items: center; gap: 20px; margin-bottom: 12px; padding: 8px 16px; background: #fafafa; border-radius: 6px; flex-shrink: 0; }
.dl-stat-item { display: flex; align-items: center; gap: 6px; }
.dl-stat-icon { font-size: 16px; color: #1677ff; }
.dl-stat-value { font-size: 18px; font-weight: 600; }
.dl-stat-label { font-size: 12px; color: #8c8c8c; }
.dl-stat-hint { font-size: 12px; color: #fa8c16; }
.dl-table-wrap { margin-top: 0; flex: 1; min-height: 320px; display: flex; flex-direction: column; overflow: hidden; }
.dl-form-hint { font-size: 12px; color: #8c8c8c; margin-top: 4px; line-height: 1.6; }
.dl-industry-card { border: 1px solid #f0f0f0; transition: all 0.2s; }
.dl-industry-card:hover { border-color: #1677ff; box-shadow: 0 2px 8px rgba(22,119,255,0.1); }
.dl-industry-header { display: flex; align-items: center; justify-content: space-between; margin-bottom: 8px; }
.dl-industry-count { font-size: 12px; color: #8c8c8c; }
.dl-industry-desc { font-size: 12px; color: #595959; min-height: 40px; margin-bottom: 12px; line-height: 1.5; }

/* 复用 Part 编辑窗口的紧凑样式（与阶段模板页同款） */
.part-create-modal :deep(.ant-modal-header) { padding: 12px 16px; border-bottom: 1px solid #f0f0f0; }
.part-create-modal :deep(.ant-modal-title) { font-size: 15px; }
.part-create-modal :deep(.ant-modal-footer) { padding: 10px 16px; border-top: 1px solid #f0f0f0; }
.part-create-modal :deep(.ant-form-item) { margin-bottom: 12px; }
.part-create-modal :deep(.ant-form-item-label) { padding-bottom: 2px; }
.part-create-modal :deep(.ant-form-item-label > label) { font-size: 13px; color: #595959; }
</style>
