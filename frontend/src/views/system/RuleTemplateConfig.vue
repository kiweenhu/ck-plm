<template>
  <div class="rl-page">
    <div class="rl-header">
      <div class="rl-header-left">
        <h3 class="rl-title">检查规则</h3>
        <span class="rl-subtitle">
          行业包把检查规则装入企业一份（智能硬件 IPD：基础包共用 7 条 + 本包增量 4 条 = 11 条）；
          企业在此裁剪 —— 改条件、改严重度、改提示语、改修复动作，或<b>停用</b>（停用不删除，重装行业包也不会被改回）。
          规则是声明式 JSON，不是脚本：时机（进入阶段 / 保存零件 / 保存 BOM / 过门 / 发布）+ 对象 + 条件 + 严重度。
        </span>
      </div>
    </div>

    <div class="rl-stats-bar">
      <div class="rl-stat-item">
        <FileProtectOutlined class="rl-stat-icon" />
        <span class="rl-stat-value">{{ templates.length }}</span>
        <span class="rl-stat-label">规则总数</span>
      </div>
      <div class="rl-stat-item">
        <span class="rl-stat-value">{{ blockCount }}</span>
        <span class="rl-stat-label">BLOCK 阻断</span>
      </div>
      <div class="rl-stat-item">
        <span class="rl-stat-value">{{ warnCount }}</span>
        <span class="rl-stat-label">WARN 提示</span>
      </div>
      <div class="rl-stat-item">
        <span class="rl-stat-value">{{ commonCount }}</span>
        <span class="rl-stat-label">通用规则</span>
      </div>
      <div class="rl-stat-item">
        <span class="rl-stat-value">{{ disabledCount }}</span>
        <span class="rl-stat-label">已停用</span>
      </div>
      <div v-if="!isPlatformAdmin && !hasAnyTemplate" class="rl-stat-hint">
        暂无检查规则：装行业包会自动带入
      </div>
    </div>

    <div class="rl-table-wrap">
      <DataTable
        :columns="columns"
        :data-source="filteredTemplates"
        :loading="loading"
        search-placeholder="搜索规则编码 / 名称 / 字段..."
        :search-fields="['code', 'name', 'message', 'condition']"
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
              v-model:value="triggerFilter"
              :options="triggerFilterOptions"
              placeholder="全部时机"
              style="width:170px"
              allow-clear
              size="small"
            />
            <a-select
              v-model:value="severityFilter"
              :options="severityOptions"
              placeholder="全部严重度"
              style="width:140px"
              allow-clear
              size="small"
            />
            <a-checkbox v-model:checked="onlyEnabled">仅看启用</a-checkbox>
            <a-button type="primary" size="small" @click="openCreate">
              <template #icon><PlusOutlined /></template>
              新增检查规则
            </a-button>
          </a-space>
        </template>

        <template #bodyCell="{ column, record }">
          <template v-if="column.key === 'code'">
            <a-tag color="blue">{{ record.code }}</a-tag>
          </template>
          <template v-else-if="column.key === 'trigger'">
            <a-tag color="geekblue">{{ TRIGGER_LABEL[record.triggerType] || record.triggerType }}</a-tag>
          </template>
          <template v-else-if="column.key === 'scope'">
            <a-tag color="cyan">{{ SCOPE_LABEL[record.scope] || record.scope }}</a-tag>
          </template>
          <template v-else-if="column.key === 'condition'">
            <a-tooltip :title="formatCondition(record.condition)">
              <span class="rl-ellipsis">{{ formatCondition(record.condition) }}</span>
            </a-tooltip>
          </template>
          <template v-else-if="column.key === 'severity'">
            <a-tag :color="record.severity === 'BLOCK' ? 'red' : 'orange'">
              {{ SEVERITY_LABEL[record.severity] || record.severity }}
            </a-tag>
            <a-tooltip v-if="record.defaultOverride" :title="formatOverride(record.defaultOverride)">
              <a-tag color="purple" size="small" style="margin-left:4px">画像联动</a-tag>
            </a-tooltip>
          </template>
          <template v-else-if="column.key === 'anchor'">
            <span v-if="record.gate" style="margin-right:4px">
              <a-tag color="orange">门 {{ gateLabel(record.gate) }}</a-tag>
            </span>
            <span v-if="record.stageKey">
              <a-tag color="geekblue">阶段 {{ stageLabel(record.stageKey) }}</a-tag>
            </span>
            <span v-if="!record.gate && !record.stageKey" style="color:#bfbfbf">-</span>
          </template>
          <template v-else-if="column.key === 'fixAction'">
            <a-tooltip v-if="record.fixAction" :title="formatFix(record.fixAction)">
              <a-tag color="green">{{ fixCode(record.fixAction) || '一键修复' }}</a-tag>
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
                title="确定删除该检查规则？包重装会把它加回来 —— 日常请用「停用」"
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
      :title="modal.isEdit ? '编辑检查规则' : '新增检查规则'"
      @ok="handleSave"
      :confirm-loading="modal.saving"
      width="720px"
      centered
      wrap-class-name="part-create-modal"
      :body-style="{ maxHeight: 'calc(100vh - 200px)', overflowY: 'auto', padding: '16px 20px 20px' }"
    >
      <a-form :model="modal.form" layout="vertical">
        <a-row :gutter="12">
          <a-col :span="12">
            <a-form-item label="规则编码" required>
              <a-input v-model:value="modal.form.code" placeholder="如 R-SH-01" :disabled="modal.isEdit" />
            </a-form-item>
          </a-col>
          <a-col :span="12">
            <a-form-item label="名称" required>
              <a-input v-model:value="modal.form.name" placeholder="如 认证证书缺失不得进入发布阶段" />
            </a-form-item>
          </a-col>
        </a-row>

        <a-row :gutter="12">
          <a-col :span="8">
            <a-form-item label="检查时机" required>
              <a-select v-model:value="modal.form.triggerType" :options="triggerOptions" />
            </a-form-item>
          </a-col>
          <a-col :span="8">
            <a-form-item label="检查对象" required>
              <a-select v-model:value="modal.form.scope" :options="scopeOptions" />
            </a-form-item>
          </a-col>
          <a-col :span="8">
            <a-form-item label="严重度" required>
              <a-select v-model:value="modal.form.severity" :options="severityOptions" />
            </a-form-item>
          </a-col>
        </a-row>

        <a-form-item label="条件（JSON，规则的判定主体）">
          <a-textarea
            v-model:value="modal.form.condition"
            :rows="4"
            placeholder='{"type":"FIELD_CHECK","field":"certStatus","operator":"NOT_IN","value":["OBTAINED"]}'
          />
          <div class="rl-form-hint">
            四种形态：FIELD_CHECK（字段判定：field + operator + value）/ FIELD_EMPTY（字段为空）/ ISSUE_OPEN（未关闭问题：issueType + minCount）/
            OBJECT_MISSING（对象缺失：objectType + stage）。operator 常用 EQ / NOT_EQ / NOT_IN / IN。
            当前：{{ formatCondition(modal.form.condition) }}
          </div>
        </a-form-item>

        <a-form-item label="提示语（可含 {model.code} / {part.code} / {count} 占位符）">
          <a-input v-model:value="modal.form.message" placeholder="产品 {model.code} 未完成认证，不得进入发布阶段" />
        </a-form-item>

        <a-row :gutter="12">
          <a-col :span="12">
            <a-form-item label="所属阶段（可选，来自研发阶段）">
              <a-select
                v-model:value="modal.form.stageKey"
                :options="stageOptions"
                placeholder="留空 = 不限阶段"
                allow-clear
                show-search
                option-filter-prop="label"
              />
            </a-form-item>
          </a-col>
          <a-col :span="12">
            <a-form-item label="所属阶段门（可选，来自阶段门）">
              <a-select
                v-model:value="modal.form.gate"
                :options="gateOptions"
                placeholder="留空 = 不限门"
                allow-clear
                show-search
                option-filter-prop="label"
              />
            </a-form-item>
          </a-col>
        </a-row>

        <a-row :gutter="12">
          <a-col :span="12">
            <a-form-item label="分类（可选）">
              <a-input v-model:value="modal.form.category" placeholder="如 battery" />
            </a-form-item>
          </a-col>
          <a-col :span="12">
            <a-form-item label="企业画像联动（JSON，可选）">
              <a-input
                v-model:value="modal.form.defaultOverride"
                placeholder='{"severity":"BLOCK","when":"exportMarket = true"}'
              />
            </a-form-item>
          </a-col>
        </a-row>

        <a-form-item label="修复动作（JSON，可选：一键跳到该去补的地方）">
          <a-input
            v-model:value="modal.form.fixAction"
            placeholder='{"type":"NAVIGATE","target":"deliverable","code":"DLV-CERT-REPORT"}'
          />
        </a-form-item>

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

        <a-form-item label="行业注解（这条规则为什么这么定）">
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
import { PlusOutlined, FileProtectOutlined } from '@ant-design/icons-vue'
import DataTable from '@/components/DataTable.vue'
import { useUserStore } from '@/stores/user'
import {
  listRuleTemplates,
  createRuleTemplate,
  updateRuleTemplate,
  deleteRuleTemplate,
  setRuleTemplateDisabled,
  listStageTemplates,
  listGateTemplates
} from '@/api'

const userStore = useUserStore()
const isPlatformAdmin = computed(() => userStore.roles?.includes('PLATFORM_ADMIN'))

const templates = ref([])
const loading = ref(false)
const onlyEnabled = ref(false)
const triggerFilter = ref(undefined)
const severityFilter = ref(undefined)

// ========== 常量（与行业包 note 里的枚举同源） ==========
const TRIGGER_LABEL = {
  STAGE_ENTRY: '进入阶段',
  PART_SAVE: '保存零件',
  BOM_SAVE: '保存 BOM',
  GATE_CHECK: '过门检查',
  RELEASE: '发布'
}
const SCOPE_LABEL = {
  PRODUCT_MODEL: '产品型号',
  PART: '零件',
  BOM: 'BOM'
}
const SEVERITY_LABEL = { BLOCK: 'BLOCK 阻断', WARN: 'WARN 提示' }
const CONDITION_LABEL = {
  FIELD_CHECK: '字段判定',
  FIELD_EMPTY: '字段为空',
  ISSUE_OPEN: '未关闭问题',
  OBJECT_MISSING: '对象缺失'
}

const INDUSTRY_OPTIONS = [
  { value: 'TRADITIONAL', label: '传统产品研发', desc: '通用产品研发流程：市场验证→需求论证→方案设计→详细设计→工艺规划→试产' },
  { value: 'IPD', label: '电子高科 (IPD)', desc: '集成产品开发：概念→计划→开发→验证→发布→生命周期管理' },
  { value: 'MILITARY', label: '军工', desc: 'GJB 流程：论证→立项→方案→工程研制→设计定型→生产定型' },
  { value: 'AUTOMOTIVE', label: '汽车', desc: 'APQP 流程：概念设计→开发验证→生产准备→量产→上市' }
]
const INDUSTRY_COLOR = { TRADITIONAL: 'blue', IPD: 'purple', MILITARY: 'red', AUTOMOTIVE: 'green' }

function industryLabel(code) { return (INDUSTRY_OPTIONS.find(o => o.value === code) || {}).label || code || '通用' }
function industryColor(code) { return INDUSTRY_COLOR[code] || 'default' }
function industryDesc(code) { return (INDUSTRY_OPTIONS.find(o => o.value === code) || {}).desc || '' }
const industryOptions = INDUSTRY_OPTIONS.map(({ value, label }) => ({ value, label }))

const triggerOptions = Object.entries(TRIGGER_LABEL).map(([value, label]) => ({ value, label }))
const scopeOptions = Object.entries(SCOPE_LABEL).map(([value, label]) => ({ value, label }))
const severityOptions = Object.entries(SEVERITY_LABEL).map(([value, label]) => ({ value, label }))

// ========== 阶段与门：单一来源（各自模板），不在前端硬编码 ==========
const stageOptions = ref([])
const stageNameMap = ref({})
const gateOptions = ref([])
const gateNameMap = ref({})

function stageLabel(code) { return code ? (stageNameMap.value[code] || code) : '-' }
function gateLabel(code) { return code ? (gateNameMap.value[code] || code) : '-' }

async function loadAnchors() {
  try {
    const res = await listStageTemplates()
    const list = (res.code === 200 && Array.isArray(res.data)) ? res.data : []
    stageOptions.value = list.map(s => ({ value: s.code, label: `${s.name}（${s.code}）` }))
    const map = {}
    for (const s of list) map[s.code] = s.name
    stageNameMap.value = map
  } catch { /* 读不到不阻断本页：回落显示原码 */ }
  try {
    const res = await listGateTemplates()
    const list = (res.code === 200 && Array.isArray(res.data)) ? res.data : []
    gateOptions.value = list.map(g => ({ value: g.code, label: `${g.name}（${g.code}）` }))
    const map = {}
    for (const g of list) map[g.code] = g.name
    gateNameMap.value = map
  } catch { /* 同上 */ }
}

// ========== JSON 解析与摘要 ==========
function parseJson(json) {
  if (!json) return null
  try {
    return typeof json === 'string' ? JSON.parse(json) : json
  } catch { return null }
}

/** 条件 → 人话摘要 */
function formatCondition(json) {
  const c = parseJson(json)
  if (!c || !c.type) return '（未设置）'
  const label = CONDITION_LABEL[c.type] || c.type
  if (c.type === 'FIELD_CHECK') {
    const value = Array.isArray(c.value) ? '[' + c.value.join(', ') + ']' : (c.value === undefined ? '' : String(c.value))
    return `${label}：${c.field || '-'} ${c.operator || ''} ${value}`.trim()
  }
  if (c.type === 'FIELD_EMPTY') {
    return `${label}：${c.field || '-'}`
  }
  if (c.type === 'ISSUE_OPEN') {
    return `${label}：${c.issueType || '-'} 至少 ${c.minCount ?? 0} 项`
  }
  if (c.type === 'OBJECT_MISSING') {
    return `${label}：${c.objectType || '-'}${c.stage ? '（阶段 ' + stageLabel(c.stage) + '）' : ''}`
  }
  return label
}

function formatOverride(json) {
  const o = parseJson(json)
  if (!o) return ''
  return `当 ${o.when || '?'} 时，严重度改为 ${SEVERITY_LABEL[o.severity] || o.severity || '?'}`
}

function fixCode(json) {
  const f = parseJson(json)
  return f && f.code ? f.code : ''
}

function formatFix(json) {
  const f = parseJson(json)
  if (!f) return ''
  return `点开跳转到${f.target === 'deliverable' ? '交付件' : (f.target || '目标')} ${f.code || ''}`
}

// ========== 统计与筛选 ==========
const hasAnyTemplate = computed(() => templates.value.length > 0)
const blockCount = computed(() => templates.value.filter(t => t.severity === 'BLOCK').length)
const warnCount = computed(() => templates.value.filter(t => t.severity === 'WARN').length)
const commonCount = computed(() => templates.value.filter(t => !t.industry).length)
const disabledCount = computed(() => templates.value.filter(t => t.disabled).length)

const triggerFilterOptions = computed(() => {
  const codes = [...new Set(templates.value.map(t => t.triggerType).filter(Boolean))]
  return codes.map(code => ({ value: code, label: TRIGGER_LABEL[code] || code }))
})

const filteredTemplates = computed(() => templates.value.filter(t => {
  if (onlyEnabled.value && t.disabled) return false
  if (triggerFilter.value && t.triggerType !== triggerFilter.value) return false
  if (severityFilter.value && t.severity !== severityFilter.value) return false
  return true
}))

// ========== 列 ==========
const columns = [
  { title: '编码', dataIndex: 'code', key: 'code', width: 110 },
  { title: '名称', dataIndex: 'name', key: 'name', width: 230 },
  { title: '时机', key: 'trigger', width: 110 },
  { title: '对象', key: 'scope', width: 100 },
  { title: '条件', key: 'condition', width: 240, ellipsis: true },
  { title: '严重度', key: 'severity', width: 130 },
  { title: '关联阶段/门', key: 'anchor', width: 180 },
  { title: '修复动作', key: 'fixAction', width: 150 },
  { title: '状态', key: 'disabled', width: 90 },
  { title: '操作', key: 'action', width: 130 }
]

// ========== 数据加载 ==========
async function loadTemplates() {
  loading.value = true
  try {
    const res = await listRuleTemplates()
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
    code: '', name: '', triggerType: 'BOM_SAVE', scope: 'BOM', severity: 'WARN',
    condition: '', message: '', stageKey: undefined, gate: undefined,
    category: '', defaultOverride: '', fixAction: '',
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
    triggerType: record.triggerType, scope: record.scope, severity: record.severity,
    condition: typeof record.condition === 'string' ? record.condition : JSON.stringify(record.condition || null),
    message: record.message || '',
    stageKey: record.stageKey || undefined,
    gate: record.gate || undefined,
    category: record.category || '',
    defaultOverride: typeof record.defaultOverride === 'string' ? record.defaultOverride : (record.defaultOverride ? JSON.stringify(record.defaultOverride) : ''),
    fixAction: typeof record.fixAction === 'string' ? record.fixAction : (record.fixAction ? JSON.stringify(record.fixAction) : ''),
    industry: record.industry || undefined,
    enabled: !record.disabled,
    sortOrder: record.sortOrder ?? 0,
    industryNote: record.industryNote || '',
    description: record.description || ''
  }
  modal.visible = true
}

async function handleSave() {
  const { code, name, triggerType, scope, severity } = modal.form
  if (!code?.trim() && !modal.isEdit) { message.warning('编码不能为空'); return }
  if (!name?.trim()) { message.warning('名称不能为空'); return }
  if (!triggerType) { message.warning('请选择检查时机'); return }
  if (!scope) { message.warning('请选择检查对象'); return }
  if (!severity) { message.warning('请选择严重度'); return }
  const cond = (modal.form.condition || '').trim()
  if (!cond) { message.warning('条件不能为空（规则没有判定条件就没有意义）'); return }
  try { JSON.parse(cond) } catch { message.warning('条件不是合法 JSON，请检查'); return }
  const optionalJson = [['defaultOverride', '企业画像联动'], ['fixAction', '修复动作']]
  for (const [key, label] of optionalJson) {
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
      triggerType,
      scope,
      severity,
      condition: cond,
      message: modal.form.message || '',
      stageKey: modal.form.stageKey || '',
      gate: modal.form.gate || '',
      category: modal.form.category || '',
      defaultOverride: (modal.form.defaultOverride || '').trim(),
      fixAction: (modal.form.fixAction || '').trim(),
      industry: modal.form.industry || '',
      disabled: !modal.form.enabled,
      sortOrder: modal.form.sortOrder ?? 0,
      industryNote: modal.form.industryNote || '',
      description: modal.form.description || ''
    }
    const res = modal.isEdit
      ? await updateRuleTemplate(modal.editingOid, payload)
      : await createRuleTemplate(payload)
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
    const res = await setRuleTemplateDisabled(record.oid, checked)
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
    const res = await deleteRuleTemplate(record.oid)
    if (res.code === 200) {
      message.success('已删除')
      await loadTemplates()
    } else {
      message.error(res.message || '删除失败')
    }
  } catch { message.error('删除失败') }
}


onMounted(() => {
  loadAnchors()
  loadTemplates()
})
</script>

<style scoped>
.rl-page { display: flex; flex-direction: column; flex: 1; min-height: 0; padding: 0; }
.rl-header { padding: 16px 0; border-bottom: 1px solid #f0f0f0; margin-bottom: 12px; flex-shrink: 0; }
.rl-title { margin: 0 0 4px 0; font-size: 18px; font-weight: 600; }
.rl-subtitle { font-size: 13px; color: #8c8c8c; line-height: 1.6; }
.rl-stats-bar { display: flex; align-items: center; gap: 20px; margin-bottom: 12px; padding: 8px 16px; background: #fafafa; border-radius: 6px; flex-shrink: 0; }
.rl-stat-item { display: flex; align-items: center; gap: 6px; }
.rl-stat-icon { font-size: 16px; color: #1677ff; }
.rl-stat-value { font-size: 18px; font-weight: 600; }
.rl-stat-label { font-size: 12px; color: #8c8c8c; }
.rl-stat-hint { font-size: 12px; color: #fa8c16; }
.rl-table-wrap { margin-top: 0; flex: 1; min-height: 320px; display: flex; flex-direction: column; overflow: hidden; }
.rl-ellipsis { display: inline-block; max-width: 230px; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; vertical-align: middle; }
.rl-form-hint { font-size: 12px; color: #8c8c8c; margin-top: 4px; line-height: 1.6; }
.rl-industry-card { border: 1px solid #f0f0f0; transition: all 0.2s; }
.rl-industry-card:hover { border-color: #1677ff; box-shadow: 0 2px 8px rgba(22,119,255,0.1); }
.rl-industry-header { display: flex; align-items: center; justify-content: space-between; margin-bottom: 8px; }
.rl-industry-count { font-size: 12px; color: #8c8c8c; }
.rl-industry-desc { font-size: 12px; color: #595959; min-height: 40px; margin-bottom: 12px; line-height: 1.5; }

/* 复用 Part 编辑窗口的紧凑样式（与前面几个模板页同款） */
.part-create-modal :deep(.ant-modal-header) { padding: 12px 16px; border-bottom: 1px solid #f0f0f0; }
.part-create-modal :deep(.ant-modal-title) { font-size: 15px; }
.part-create-modal :deep(.ant-modal-footer) { padding: 10px 16px; border-top: 1px solid #f0f0f0; }
.part-create-modal :deep(.ant-form-item) { margin-bottom: 12px; }
.part-create-modal :deep(.ant-form-item-label) { padding-bottom: 2px; }
.part-create-modal :deep(.ant-form-item-label > label) { font-size: 13px; color: #595959; }
</style>
