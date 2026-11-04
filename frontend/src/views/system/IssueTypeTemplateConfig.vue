<template>
  <div class="it-page">
    <div class="it-header">
      <div class="it-header-left">
        <h3 class="it-title">问题类型</h3>
        <span class="it-subtitle">
          行业包把问题类型装入企业一份（智能硬件 IPD：7 类 —— 设计缺陷 / 器件风险 / 成本超支 / 供应风险 / 测试未通过 / 认证缺失 / 工艺问题）；
          企业在此裁剪 —— 改默认严重度、改要不要做根因分析、要不要复测，或<b>停用</b>（停用不删除，重装行业包也不会被改回）。
          问题类型必须是独立数据：「问题闭环率」「超期问题数」是健康度报告里最有说服力的指标，而它们只有在问题被结构化之后才算得出来。
        </span>
      </div>
    </div>

    <div class="it-stats-bar">
      <div class="it-stat-item">
        <WarningOutlined class="it-stat-icon" />
        <span class="it-stat-value">{{ templates.length }}</span>
        <span class="it-stat-label">问题类型总数</span>
      </div>
      <div class="it-stat-item">
        <span class="it-stat-value">{{ blockCount }}</span>
        <span class="it-stat-label">默认 BLOCK</span>
      </div>
      <div class="it-stat-item">
        <span class="it-stat-value">{{ rootCauseCount }}</span>
        <span class="it-stat-label">需根因分析</span>
      </div>
      <div class="it-stat-item">
        <span class="it-stat-value">{{ retestCount }}</span>
        <span class="it-stat-label">需复测</span>
      </div>
      <div class="it-stat-item">
        <span class="it-stat-value">{{ disabledCount }}</span>
        <span class="it-stat-label">已停用</span>
      </div>
      <div v-if="!isPlatformAdmin && !hasAnyTemplate" class="it-stat-hint">
        暂无问题类型：装行业包会自动带入
      </div>
    </div>

    <div class="it-table-wrap">
      <DataTable
        :columns="columns"
        :data-source="filteredTemplates"
        :loading="loading"
        search-placeholder="搜索编码 / 名称 / 注解..."
        :search-fields="['code', 'name', 'industryNote']"
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
              v-model:value="severityFilter"
              :options="severityOptions"
              placeholder="全部严重度"
              style="width:150px"
              allow-clear
              size="small"
            />
            <a-checkbox v-model:checked="onlyEnabled">仅看启用</a-checkbox>
            <a-button type="primary" size="small" @click="openCreate">
              <template #icon><PlusOutlined /></template>
              新增问题类型
            </a-button>
          </a-space>
        </template>

        <template #bodyCell="{ column, record }">
          <template v-if="column.key === 'code'">
            <a-tag color="blue">{{ record.code }}</a-tag>
          </template>
          <template v-else-if="column.key === 'severity'">
            <a-tag :color="record.defaultSeverity === 'BLOCK' ? 'red' : 'orange'">
              {{ SEVERITY_LABEL[record.defaultSeverity] || record.defaultSeverity }}
            </a-tag>
          </template>
          <template v-else-if="column.key === 'rootCause'">
            <a-tag :color="record.requireRootCause ? 'purple' : 'default'">
              {{ record.requireRootCause ? '需根因分析' : '不要求' }}
            </a-tag>
          </template>
          <template v-else-if="column.key === 'retest'">
            <a-tag :color="record.requireRetest ? 'geekblue' : 'default'">
              {{ record.requireRetest ? '需复测' : '不要求' }}
            </a-tag>
          </template>
          <template v-else-if="column.key === 'industryNote'">
            <a-tooltip v-if="record.industryNote" :title="record.industryNote">
              <span class="it-ellipsis">{{ record.industryNote }}</span>
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
                title="确定删除该问题类型？包重装会把它加回来 —— 日常请用「停用」"
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
      :title="modal.isEdit ? '编辑问题类型' : '新增问题类型'"
      @ok="handleSave"
      :confirm-loading="modal.saving"
      width="620px"
      centered
      wrap-class-name="part-create-modal"
      :body-style="{ maxHeight: 'calc(100vh - 200px)', overflowY: 'auto', padding: '16px 20px 20px' }"
    >
      <a-form :model="modal.form" layout="vertical">
        <a-row :gutter="12">
          <a-col :span="12">
            <a-form-item label="类型编码" required>
              <a-input v-model:value="modal.form.code" placeholder="如 TEST_FAIL" :disabled="modal.isEdit" />
            </a-form-item>
          </a-col>
          <a-col :span="12">
            <a-form-item label="名称" required>
              <a-input v-model:value="modal.form.name" placeholder="如 测试未通过" />
            </a-form-item>
          </a-col>
        </a-row>

        <a-row :gutter="12">
          <a-col :span="8">
            <a-form-item label="默认严重度" required>
              <a-select v-model:value="modal.form.defaultSeverity" :options="severityOptions" />
            </a-form-item>
          </a-col>
          <a-col :span="8">
            <a-form-item label="需根因分析">
              <a-switch v-model:checked="modal.form.requireRootCause" checked-children="需要" un-checked-children="不要求" />
            </a-form-item>
          </a-col>
          <a-col :span="8">
            <a-form-item label="需复测">
              <a-switch v-model:checked="modal.form.requireRetest" checked-children="需要" un-checked-children="不要求" />
            </a-form-item>
          </a-col>
        </a-row>

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

        <a-form-item label="行业注解（这类问题为什么这么定）">
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
import { PlusOutlined, WarningOutlined } from '@ant-design/icons-vue'
import DataTable from '@/components/DataTable.vue'
import { useUserStore } from '@/stores/user'
import {
  listIssueTypeTemplates,
  createIssueTypeTemplate,
  updateIssueTypeTemplate,
  deleteIssueTypeTemplate,
  setIssueTypeTemplateDisabled
} from '@/api'

const userStore = useUserStore()
const isPlatformAdmin = computed(() => userStore.roles?.includes('PLATFORM_ADMIN'))

const templates = ref([])
const loading = ref(false)
const onlyEnabled = ref(false)
const severityFilter = ref(undefined)

// ========== 常量（与检查规则、阶段门同一口径） ==========
const SEVERITY_LABEL = { BLOCK: 'BLOCK 阻断', WARN: 'WARN 提示' }
const severityOptions = Object.entries(SEVERITY_LABEL).map(([value, label]) => ({ value, label }))

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

// ========== 统计与筛选 ==========
const hasAnyTemplate = computed(() => templates.value.length > 0)
const blockCount = computed(() => templates.value.filter(t => t.defaultSeverity === 'BLOCK').length)
const rootCauseCount = computed(() => templates.value.filter(t => t.requireRootCause).length)
const retestCount = computed(() => templates.value.filter(t => t.requireRetest).length)
const disabledCount = computed(() => templates.value.filter(t => t.disabled).length)

const filteredTemplates = computed(() => templates.value.filter(t => {
  if (onlyEnabled.value && t.disabled) return false
  if (severityFilter.value && t.defaultSeverity !== severityFilter.value) return false
  return true
}))

// ========== 列 ==========
const columns = [
  { title: '编码', dataIndex: 'code', key: 'code', width: 160 },
  { title: '名称', dataIndex: 'name', key: 'name', width: 140 },
  { title: '默认严重度', key: 'severity', width: 130 },
  { title: '根因分析', key: 'rootCause', width: 130 },
  { title: '复测', key: 'retest', width: 110 },
  { title: '行业注解', key: 'industryNote', width: 300, ellipsis: true },
  { title: '状态', key: 'disabled', width: 90 },
  { title: '操作', key: 'action', width: 130 }
]

// ========== 数据加载 ==========
async function loadTemplates() {
  loading.value = true
  try {
    const res = await listIssueTypeTemplates()
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
    code: '', name: '', defaultSeverity: 'WARN',
    requireRootCause: false, requireRetest: false,
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
    defaultSeverity: record.defaultSeverity || 'WARN',
    requireRootCause: !!record.requireRootCause,
    requireRetest: !!record.requireRetest,
    industry: record.industry || undefined,
    enabled: !record.disabled,
    sortOrder: record.sortOrder ?? 0,
    industryNote: record.industryNote || '',
    description: record.description || ''
  }
  modal.visible = true
}

async function handleSave() {
  const { code, name, defaultSeverity } = modal.form
  if (!code?.trim() && !modal.isEdit) { message.warning('编码不能为空'); return }
  if (!name?.trim()) { message.warning('名称不能为空'); return }
  if (!defaultSeverity) { message.warning('请选择默认严重度'); return }
  modal.saving = true
  try {
    // 后端 UPDATE 是 patch 语义：字段缺席（null）= 本次不改；空串（""）= 显式清空
    const payload = {
      code: code?.trim(),
      name: name.trim(),
      defaultSeverity,
      requireRootCause: !!modal.form.requireRootCause,
      requireRetest: !!modal.form.requireRetest,
      industry: modal.form.industry || '',
      disabled: !modal.form.enabled,
      sortOrder: modal.form.sortOrder ?? 0,
      industryNote: modal.form.industryNote || '',
      description: modal.form.description || ''
    }
    const res = modal.isEdit
      ? await updateIssueTypeTemplate(modal.editingOid, payload)
      : await createIssueTypeTemplate(payload)
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
    const res = await setIssueTypeTemplateDisabled(record.oid, checked)
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
    const res = await deleteIssueTypeTemplate(record.oid)
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
.it-page { display: flex; flex-direction: column; flex: 1; min-height: 0; padding: 0; }
.it-header { padding: 16px 0; border-bottom: 1px solid #f0f0f0; margin-bottom: 12px; flex-shrink: 0; }
.it-title { margin: 0 0 4px 0; font-size: 18px; font-weight: 600; }
.it-subtitle { font-size: 13px; color: #8c8c8c; line-height: 1.6; }
.it-stats-bar { display: flex; align-items: center; gap: 20px; margin-bottom: 12px; padding: 8px 16px; background: #fafafa; border-radius: 6px; flex-shrink: 0; }
.it-stat-item { display: flex; align-items: center; gap: 6px; }
.it-stat-icon { font-size: 16px; color: #fa8c16; }
.it-stat-value { font-size: 18px; font-weight: 600; }
.it-stat-label { font-size: 12px; color: #8c8c8c; }
.it-stat-hint { font-size: 12px; color: #fa8c16; }
.it-table-wrap { margin-top: 0; flex: 1; min-height: 320px; display: flex; flex-direction: column; overflow: hidden; }
.it-ellipsis { display: inline-block; max-width: 290px; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; vertical-align: middle; color: #595959; }
.it-industry-card { border: 1px solid #f0f0f0; transition: all 0.2s; }
.it-industry-card:hover { border-color: #1677ff; box-shadow: 0 2px 8px rgba(22,119,255,0.1); }
.it-industry-header { display: flex; align-items: center; justify-content: space-between; margin-bottom: 8px; }
.it-industry-count { font-size: 12px; color: #8c8c8c; }
.it-industry-desc { font-size: 12px; color: #595959; min-height: 40px; margin-bottom: 12px; line-height: 1.5; }

/* 复用 Part 编辑窗口的紧凑样式（与前面几个模板页同款） */
.part-create-modal :deep(.ant-modal-header) { padding: 12px 16px; border-bottom: 1px solid #f0f0f0; }
.part-create-modal :deep(.ant-modal-title) { font-size: 15px; }
.part-create-modal :deep(.ant-modal-footer) { padding: 10px 16px; border-top: 1px solid #f0f0f0; }
.part-create-modal :deep(.ant-form-item) { margin-bottom: 12px; }
.part-create-modal :deep(.ant-form-item-label) { padding-bottom: 2px; }
.part-create-modal :deep(.ant-form-item-label > label) { font-size: 13px; color: #595959; }
</style>
