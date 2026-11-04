<template>
  <div class="gt-page">
    <div class="gt-header">
      <div class="gt-header-left">
        <h3 class="gt-title">阶段门</h3>
        <span class="gt-subtitle">
          行业包把阶段门装入企业一份（如智能硬件 IPD 的 10 道门：TR1–TR6 技术评审 + Charter/PDCP/ADCP/LDCP 决策评审）；
          企业在此裁剪 —— 改决策选项、改参与者、改自动检查，或<b>停用</b>（停用不删除，重装行业包也不会被改回）。
          门按「本阶段交付件完成率」等条件判定，故门的（编码, 阶段）与交付件的（门, 阶段）一一对应。
        </span>
      </div>
    </div>

    <div class="gt-stats-bar">
      <div class="gt-stat-item">
        <SafetyCertificateOutlined class="gt-stat-icon" />
        <span class="gt-stat-value">{{ templates.length }}</span>
        <span class="gt-stat-label">阶段门总数</span>
      </div>
      <div class="gt-stat-item">
        <span class="gt-stat-value">{{ technicalCount }}</span>
        <span class="gt-stat-label">技术评审</span>
      </div>
      <div class="gt-stat-item">
        <span class="gt-stat-value">{{ decisionCount }}</span>
        <span class="gt-stat-label">决策评审</span>
      </div>
      <div class="gt-stat-item">
        <span class="gt-stat-value">{{ disabledCount }}</span>
        <span class="gt-stat-label">已停用</span>
      </div>
      <div class="gt-stat-item">
        <span class="gt-stat-value">{{ stageCoverage }}</span>
        <span class="gt-stat-label">覆盖阶段</span>
      </div>
      <div v-if="!isPlatformAdmin && !hasAnyTemplate" class="gt-stat-hint">
        暂无阶段门：装行业包会自动带入
      </div>
    </div>

    <div class="gt-table-wrap">
      <DataTable
        :columns="columns"
        :data-source="filteredTemplates"
        :loading="loading"
        search-placeholder="搜索门编码 / 名称 / 流程..."
        :search-fields="['code', 'name', 'stageKey', 'processKey']"
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
              style="width:170px"
              allow-clear
              size="small"
            />
            <a-select
              v-model:value="typeFilter"
              :options="typeOptions"
              placeholder="全部类型"
              style="width:140px"
              allow-clear
              size="small"
            />
            <a-checkbox v-model:checked="onlyEnabled">仅看启用</a-checkbox>
            <a-button type="primary" size="small" @click="openCreate">
              <template #icon><PlusOutlined /></template>
              新增阶段门
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
          <template v-else-if="column.key === 'type'">
            <a-tag :color="record.type === 'DECISION_REVIEW' ? 'purple' : 'cyan'">
              {{ GATE_TYPE_LABEL[record.type] || record.type }}
            </a-tag>
          </template>
          <template v-else-if="column.key === 'decisionOptions'">
            <a-space size="4" wrap>
              <a-tag v-for="d in parseArray(record.decisionOptions)" :key="d" size="small" style="white-space:nowrap">
                {{ DECISION_LABEL[d] || d }}
              </a-tag>
              <span v-if="parseArray(record.decisionOptions).length === 0" style="color:#bfbfbf">-</span>
            </a-space>
          </template>
          <template v-else-if="column.key === 'autoChecks'">
            <a-tooltip :title="formatChecks(record.autoChecks)">
              <a-space size="4" wrap>
                <a-tag
                  v-for="(c, i) in parseChecks(record.autoChecks)"
                  :key="i"
                  size="small"
                  :color="c.severity === 'BLOCK' ? 'red' : 'orange'"
                  style="white-space:nowrap"
                >{{ CHECK_TYPE_LABEL[c.type] || c.type }}</a-tag>
                <span v-if="parseChecks(record.autoChecks).length === 0" style="color:#bfbfbf">-</span>
              </a-space>
            </a-tooltip>
          </template>
          <template v-else-if="column.key === 'participants'">
            <a-tooltip :title="parseArray(record.requiredParticipants).join('、')">
              <span>
                {{ parseArray(record.requiredParticipants).length }} 位
                <span style="color:#8c8c8c;font-size:12px">
                  {{ parseArray(record.requiredParticipants).slice(0, 2).join('、') }}{{ parseArray(record.requiredParticipants).length > 2 ? '…' : '' }}
                </span>
              </span>
            </a-tooltip>
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
                title="确定删除该阶段门？包重装会把它加回来 —— 日常请用「停用」"
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
      :title="modal.isEdit ? '编辑阶段门' : '新增阶段门'"
      @ok="handleSave"
      :confirm-loading="modal.saving"
      width="680px"
      centered
      wrap-class-name="part-create-modal"
      :body-style="{ maxHeight: 'calc(100vh - 200px)', overflowY: 'auto', padding: '16px 20px 20px' }"
    >
      <a-form :model="modal.form" layout="vertical">
        <a-row :gutter="12">
          <a-col :span="12">
            <a-form-item label="门编码" required>
              <a-input v-model:value="modal.form.code" placeholder="如 TR3" :disabled="modal.isEdit" />
            </a-form-item>
          </a-col>
          <a-col :span="12">
            <a-form-item label="名称" required>
              <a-input v-model:value="modal.form.name" placeholder="如 详细设计评审（投板门）" />
            </a-form-item>
          </a-col>
        </a-row>

        <a-row :gutter="12">
          <a-col :span="8">
            <a-form-item label="所属研发阶段" required>
              <a-select
                v-model:value="modal.form.stageKey"
                :options="stageOptions"
                placeholder="来自研发阶段"
                show-search
                option-filter-prop="label"
              />
            </a-form-item>
          </a-col>
          <a-col :span="8">
            <a-form-item label="门类型" required>
              <a-select v-model:value="modal.form.type" :options="typeOptions" />
            </a-form-item>
          </a-col>
          <a-col :span="8">
            <a-form-item label="承载流程 key">
              <a-input v-model:value="modal.form.processKey" placeholder="如 technical-review（留空=无流程）" />
            </a-form-item>
          </a-col>
        </a-row>

        <a-form-item label="决策选项（JSON 数组）">
          <a-textarea
            v-model:value="modal.form.decisionOptions"
            :rows="2"
            placeholder='["GO","GO_WITH_CONDITION","RECYCLE"] 或 ["GO","HOLD","TERMINATE"]'
          />
        </a-form-item>

        <a-form-item label="自动检查（JSON 数组，系统据此判定门上是否放行）">
          <a-textarea
            v-model:value="modal.form.autoChecks"
            :rows="4"
            placeholder='[{"type":"DELIVERABLE_RATE","stage":"IPD_CONCEPT","minPercent":100,"severity":"BLOCK"}]'
          />
          <div class="gt-form-hint">
            可用类型：DELIVERABLE_RATE（本阶段交付件完成率）/ RULE_VIOLATION（规则违规）/ ISSUE_OPEN（未关闭问题）/
            COST_ACHIEVE（成本达成）/ CERT_COMPLETE（认证齐套）/ SUPPLY_CONFIRMED（供应确认）；
            severity 取 BLOCK 或 WARN
          </div>
        </a-form-item>

        <a-row :gutter="12">
          <a-col :span="12">
            <a-form-item label="必备参与者（JSON 数组，角色码）">
              <a-textarea
                v-model:value="modal.form.requiredParticipants"
                :rows="2"
                placeholder='["SYSTEM_ENGINEER","HW_ENGINEER","QA_ENGINEER"]'
              />
            </a-form-item>
          </a-col>
          <a-col :span="12">
            <a-form-item label="留痕字段（JSON 数组）">
              <a-textarea
                v-model:value="modal.form.recordFields"
                :rows="2"
                placeholder='["decision","decisionBasis","riskList"]'
              />
            </a-form-item>
          </a-col>
        </a-row>

        <a-row :gutter="12">
          <a-col :span="8">
            <a-form-item label="必须留证据">
              <a-switch v-model:checked="modal.form.evidenceRequired" checked-children="是" un-checked-children="否" />
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

        <a-form-item label="行业 / 方法论">
          <a-select
            v-model:value="modal.form.industry"
            :options="industryOptions"
            placeholder="留空 = 通用（不限定方法论）"
            allow-clear
          />
        </a-form-item>
        <a-form-item label="行业注解（这道门为什么这么设）">
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
import { PlusOutlined, SafetyCertificateOutlined } from '@ant-design/icons-vue'
import DataTable from '@/components/DataTable.vue'
import { useUserStore } from '@/stores/user'
import {
  listGateTemplates,
  createGateTemplate,
  updateGateTemplate,
  deleteGateTemplate,
  setGateTemplateDisabled,
  listStageTemplates
} from '@/api'
// 类型 / 决策选项的中文字典：与项目内的阶段门面板共用（用户两次反馈"显示的是 code"）
import { GATE_TYPE_LABEL, GATE_DECISION_LABEL as DECISION_LABEL } from '@/utils/gateDefs'

const userStore = useUserStore()
const isPlatformAdmin = computed(() => userStore.roles?.includes('PLATFORM_ADMIN'))

const templates = ref([])
const loading = ref(false)
const onlyEnabled = ref(false)
const stageFilter = ref(undefined)
const typeFilter = ref(undefined)

// ========== 常量（与行业包里的码同源） ==========
// 门的类型 / 决策选项字典在 utils/gateDefs.js（与项目内的阶段门面板共用一份，避免两处漂移）
const CHECK_TYPE_LABEL = {
  DELIVERABLE_RATE: '交付件完成率',
  RULE_VIOLATION: '规则违规',
  ISSUE_OPEN: '未关闭问题',
  COST_ACHIEVE: '成本达成',
  CERT_COMPLETE: '认证齐套',
  SUPPLY_CONFIRMED: '供应确认'
}

const INDUSTRY_OPTIONS = [
  { value: 'TRADITIONAL', label: '传统产品研发', desc: '通用产品研发流程：市场验证→需求论证→方案设计→详细设计→工艺规划→试产' },
  { value: 'IPD', label: '电子高科 (IPD)', desc: '集成产品开发：概念→计划→开发→验证→发布→生命周期管理' },
  { value: 'MILITARY', label: '军工', desc: 'GJB 流程：论证→立项→方案→工程研制→设计定型→生产定型' },
  { value: 'AUTOMOTIVE', label: '汽车', desc: 'APQP 流程：概念设计→开发验证→生产准备→量产→上市' }
]
const INDUSTRY_COLOR = { TRADITIONAL: 'blue', IPD: 'purple', MILITARY: 'red', AUTOMOTIVE: 'green' }

const typeOptions = [
  { value: 'TECHNICAL_REVIEW', label: '技术评审（TR）' },
  { value: 'DECISION_REVIEW', label: '决策评审（DCP）' }
]

function industryLabel(code) { return (INDUSTRY_OPTIONS.find(o => o.value === code) || {}).label || code || '通用' }
function industryColor(code) { return INDUSTRY_COLOR[code] || 'default' }
function industryDesc(code) { return (INDUSTRY_OPTIONS.find(o => o.value === code) || {}).desc || '' }
const industryOptions = INDUSTRY_OPTIONS.map(({ value, label }) => ({ value, label }))

// ========== 阶段：单一来源（研发阶段模板），不在前端硬编码 ==========
const stageOptions = ref([])
const stageNameMap = ref({})

function stageLabel(code) {
  if (!code) return '-'
  return stageNameMap.value[code] || code
}

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

// ========== 统计与筛选 ==========
const hasAnyTemplate = computed(() => templates.value.length > 0)
const technicalCount = computed(() => templates.value.filter(t => t.type === 'TECHNICAL_REVIEW').length)
const decisionCount = computed(() => templates.value.filter(t => t.type === 'DECISION_REVIEW').length)
const disabledCount = computed(() => templates.value.filter(t => t.disabled).length)
const stageCoverage = computed(() => {
  const set = new Set(templates.value.filter(t => !t.disabled).map(t => t.stageKey).filter(Boolean))
  return set.size
})

const stageFilterOptions = computed(() => {
  const codes = [...new Set(templates.value.map(t => t.stageKey).filter(Boolean))]
  return codes.map(code => ({ value: code, label: `${stageLabel(code)}（${code}）` }))
})

const filteredTemplates = computed(() => templates.value.filter(t => {
  if (onlyEnabled.value && t.disabled) return false
  if (stageFilter.value && t.stageKey !== stageFilter.value) return false
  if (typeFilter.value && t.type !== typeFilter.value) return false
  return true
}))

// ========== 列 ==========
const columns = [
  { title: '编码', dataIndex: 'code', key: 'code', width: 100 },
  { title: '名称', dataIndex: 'name', key: 'name', width: 220 },
  { title: '所属阶段', key: 'stage', width: 130 },
  { title: '类型', key: 'type', width: 110 },
  { title: '决策选项', key: 'decisionOptions', width: 190 },
  { title: '自动检查', key: 'autoChecks', width: 230 },
  { title: '参与者', key: 'participants', width: 130 },
  { title: '流程', key: 'process', width: 140 },
  { title: '状态', key: 'disabled', width: 90 },
  { title: '操作', key: 'action', width: 130 }
]

// ========== JSON 解析 ==========
function parseArray(json) {
  if (!json) return []
  try {
    const v = typeof json === 'string' ? JSON.parse(json) : json
    return Array.isArray(v) ? v : []
  } catch { return [] }
}

function parseChecks(json) {
  return parseArray(json).filter(c => c && typeof c === 'object')
}

function formatChecks(json) {
  const checks = parseChecks(json)
  if (checks.length === 0) return '（未设置）'
  return checks.map(c => {
    const label = CHECK_TYPE_LABEL[c.type] || c.type
    const detail = [
      c.stage ? '阶段 ' + c.stage : null,
      c.minPercent != null ? '≥' + c.minPercent + '%' : null,
      c.maxCount != null ? '上限 ' + c.maxCount : null,
      c.scope ? '范围 ' + c.scope : null,
      c.maxDeviationPercent != null ? '偏差 ≤' + c.maxDeviationPercent + '%' : null,
      c.severity || null
    ].filter(Boolean).join('，')
    return label + (detail ? '（' + detail + '）' : '')
  }).join('\n')
}

// ========== 数据加载 ==========
async function loadTemplates() {
  loading.value = true
  try {
    const res = await listGateTemplates()
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
    code: '', name: '', stageKey: undefined, type: 'TECHNICAL_REVIEW', processKey: '',
    decisionOptions: '["GO","GO_WITH_CONDITION","RECYCLE"]', autoChecks: '',
    requiredParticipants: '', recordFields: '', evidenceRequired: true,
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
    stageKey: record.stageKey, type: record.type || 'TECHNICAL_REVIEW',
    processKey: record.processKey || '',
    decisionOptions: typeof record.decisionOptions === 'string' ? record.decisionOptions : JSON.stringify(record.decisionOptions || []),
    autoChecks: typeof record.autoChecks === 'string' ? record.autoChecks : JSON.stringify(record.autoChecks || []),
    requiredParticipants: typeof record.requiredParticipants === 'string' ? record.requiredParticipants : JSON.stringify(record.requiredParticipants || []),
    recordFields: typeof record.recordFields === 'string' ? record.recordFields : JSON.stringify(record.recordFields || []),
    evidenceRequired: record.evidenceRequired !== false,
    industry: record.industry || undefined,
    enabled: !record.disabled,
    sortOrder: record.sortOrder ?? 0,
    industryNote: record.industryNote || '',
    description: record.description || ''
  }
  modal.visible = true
}

async function handleSave() {
  const { code, name, stageKey, type } = modal.form
  if (!code?.trim() && !modal.isEdit) { message.warning('编码不能为空'); return }
  if (!name?.trim()) { message.warning('名称不能为空'); return }
  if (!stageKey) { message.warning('请选择所属研发阶段'); return }
  if (!type) { message.warning('请选择门类型'); return }
  // 四个 JSON 字段逐个校验（宁可拦下来，也不把坏 JSON 写进库）
  const jsonFields = [
    ['decisionOptions', '决策选项'],
    ['autoChecks', '自动检查'],
    ['requiredParticipants', '必备参与者'],
    ['recordFields', '留痕字段']
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
      stageKey,
      type,
      processKey: modal.form.processKey || '',
      decisionOptions: (modal.form.decisionOptions || '').trim(),
      autoChecks: (modal.form.autoChecks || '').trim(),
      requiredParticipants: (modal.form.requiredParticipants || '').trim(),
      recordFields: (modal.form.recordFields || '').trim(),
      evidenceRequired: !!modal.form.evidenceRequired,
      industry: modal.form.industry || '',
      disabled: !modal.form.enabled,
      sortOrder: modal.form.sortOrder ?? 0,
      industryNote: modal.form.industryNote || '',
      description: modal.form.description || ''
    }
    const res = modal.isEdit
      ? await updateGateTemplate(modal.editingOid, payload)
      : await createGateTemplate(payload)
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
    const res = await setGateTemplateDisabled(record.oid, checked)
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
    const res = await deleteGateTemplate(record.oid)
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
/* 撑满型页面（同 LifecycleConfig / StageTemplateConfig 口径）：
   页面填满页签给的确定高度，表格区 flex:1 —— stretch 模式下表体内部滚动、
   分页器与横向滚动条固定在视口底部，不必拉到页面最底才能横向滚动。 */
.gt-page {
  display: flex;
  flex-direction: column;
  flex: 1;
  min-height: 0;
  padding: 0;
}
.gt-header { padding: 16px 0; border-bottom: 1px solid #f0f0f0; margin-bottom: 12px; flex-shrink: 0; }
.gt-title { margin: 0 0 4px 0; font-size: 18px; font-weight: 600; }
.gt-subtitle { font-size: 13px; color: #8c8c8c; line-height: 1.6; }
.gt-stats-bar { display: flex; align-items: center; gap: 20px; margin-bottom: 12px; padding: 8px 16px; background: #fafafa; border-radius: 6px; flex-shrink: 0; }
.gt-stat-item { display: flex; align-items: center; gap: 6px; }
.gt-stat-icon { font-size: 16px; color: #1677ff; }
.gt-stat-value { font-size: 18px; font-weight: 600; }
.gt-stat-label { font-size: 12px; color: #8c8c8c; }
.gt-stat-hint { font-size: 12px; color: #fa8c16; }
/* 表格区域：flex 容器 + 保底高度（同 LifecycleConfig）——
   矮窗口下整页由 .bc-content 滚，高窗口下 flex:1 撑满、横向滚动条常驻可见。 */
.gt-table-wrap {
  margin-top: 0;
  flex: 1;
  min-height: 320px;
  display: flex;
  flex-direction: column;
  overflow: hidden;
}
.gt-form-hint { font-size: 12px; color: #8c8c8c; margin-top: 4px; line-height: 1.6; }
.gt-industry-card { border: 1px solid #f0f0f0; transition: all 0.2s; }
.gt-industry-card:hover { border-color: #1677ff; box-shadow: 0 2px 8px rgba(22,119,255,0.1); }
.gt-industry-header { display: flex; align-items: center; justify-content: space-between; margin-bottom: 8px; }
.gt-industry-count { font-size: 12px; color: #8c8c8c; }
.gt-industry-desc { font-size: 12px; color: #595959; min-height: 40px; margin-bottom: 12px; line-height: 1.5; }

/* 复用 Part 编辑窗口的紧凑样式（与阶段模板/交付件模板页同款） */
.part-create-modal :deep(.ant-modal-header) { padding: 12px 16px; border-bottom: 1px solid #f0f0f0; }
.part-create-modal :deep(.ant-modal-title) { font-size: 15px; }
.part-create-modal :deep(.ant-modal-footer) { padding: 10px 16px; border-top: 1px solid #f0f0f0; }
.part-create-modal :deep(.ant-form-item) { margin-bottom: 12px; }
.part-create-modal :deep(.ant-form-item-label) { padding-bottom: 2px; }
.part-create-modal :deep(.ant-form-item-label > label) { font-size: 13px; color: #595959; }
</style>
