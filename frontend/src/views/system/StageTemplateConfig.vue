<template>
  <div class="st-page">
    <a-tabs v-model:activeKey="activeTab" class="st-tabs">
      <a-tab-pane key="stage" tab="研发阶段模板">
        <div class="st-header">
          <div class="st-header-left">
            <h3 class="st-title">研发阶段模板</h3>
            <span class="st-subtitle">定义研发阶段模板，创建产品线/型号时从此模板生成阶段实例。平台提供多套行业模板（传统产品研发 / IPD / 军工 / 汽车）可供租户克隆。</span>
          </div>
        </div>

        <div class="st-stats-bar">
          <div class="st-stat-item">
            <RocketOutlined class="st-stat-icon" />
            <span class="st-stat-value">{{ templates.length }}</span>
            <span class="st-stat-label">阶段总数</span>
          </div>
          <div v-if="!isPlatformAdmin && !hasAnyTemplate" class="st-stat-hint">
            暂无研发阶段模板，请点击「克隆平台模板」快速初始化
          </div>
        </div>

        <div class="st-table-wrap">
          <DataTable
            :columns="columns"
            :data-source="templates"
            :loading="loading"
            search-placeholder="搜索阶段编码 / 名称..."
            :search-fields="['code', 'name', 'industry']"
            :enable-resize="true"
            :show-column-toggle="true"
            :max-height="420"
            row-key="oid"
            size="middle"
          >
            <template #toolbar>
              <a-space>
                <a-button type="primary" size="small" @click="openCloneModal" :loading="loadingPlatform">
                  <template #icon><CloudDownloadOutlined /></template>
                  克隆平台模板
                </a-button>
                <a-button type="primary" size="small" @click="openCreate">
                  <template #icon><PlusOutlined /></template>
                  新增阶段
                </a-button>
              </a-space>
            </template>

            <template #bodyCell="{ column, record }">
              <template v-if="column.key === 'code'">
                <a-tag color="blue">{{ record.code }}</a-tag>
              </template>
              <template v-else-if="column.key === 'color'">
                <span class="st-color-dot" :style="{ background: record.color }" />
                <span>{{ record.color }}</span>
              </template>
              <template v-else-if="column.key === 'industry'">
                <a-tag :color="industryColor(record.industry)">{{ industryLabel(record.industry) }}</a-tag>
              </template>
              <template v-else-if="column.key === 'managedObjects'">
                <a-space size="small" wrap>
                  <a-tag v-for="t in parseManagedObjects(record.managedObjectTypes)" :key="t" color="purple" size="small">{{ managedObjectLabel(t) }}</a-tag>
                  <span v-if="parseManagedObjects(record.managedObjectTypes).length === 0" style="color:#bfbfbf">-</span>
                </a-space>
              </template>
              <template v-else-if="column.key === 'defaultFolders'">
                <a-tooltip :title="record.defaultFolders">
                  <span class="st-folders-preview">{{ formatFolders(record.defaultFolders) }}</span>
                </a-tooltip>
              </template>
              <template v-else-if="column.key === 'action'">
                <a-space size="small">
                  <a-button type="link" size="small" @click="openEdit(record)">编辑</a-button>
                  <a-popconfirm title="确定删除该阶段模板？" @confirm="handleDelete(record)">
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
          :title="modal.isEdit ? '编辑阶段模板' : '新增阶段模板'"
          @ok="handleSave"
          :confirm-loading="modal.saving"
          width="560px"
          centered
          wrap-class-name="part-create-modal"
          :body-style="{ maxHeight: 'calc(100vh - 200px)', overflowY: 'auto', padding: '16px 20px 20px' }"
        >
          <a-form :model="modal.form" layout="vertical">
            <a-form-item label="阶段编码" required>
              <a-input v-model:value="modal.form.code" placeholder="如 TRIAL" :disabled="modal.isEdit" />
            </a-form-item>
            <a-form-item label="阶段名称" required>
              <a-input v-model:value="modal.form.name" placeholder="如 试产" />
            </a-form-item>
            <a-row :gutter="12">
              <a-col :span="12">
                <a-form-item label="行业/场景">
                  <a-select v-model:value="modal.form.industry" :options="industryOptions" />
                </a-form-item>
              </a-col>
              <a-col :span="12">
                <a-form-item label="排序">
                  <a-input-number v-model:value="modal.form.sortOrder" :min="1" style="width:100%" />
                </a-form-item>
              </a-col>
            </a-row>
            <a-form-item label="图标">
              <a-input v-model:value="modal.form.icon" placeholder="Ant Design 图标名，如 RocketOutlined" />
            </a-form-item>
            <a-form-item label="标识色">
              <a-input v-model:value="modal.form.color" placeholder="#52c41a">
                <template #addonBefore>
                  <span class="st-color-dot" :style="{ background: modal.form.color || '#ccc' }" />
                </template>
              </a-input>
            </a-form-item>
            <a-form-item label="管理的业务对象（多选）">
              <a-select
                v-model:value="modal.form.managedObjectTypes"
                mode="multiple"
                :options="managedObjectOptions"
                placeholder="选择该阶段管理的业务对象类型"
              />
            </a-form-item>
            <a-form-item label="默认文件夹（JSON数组）">
              <a-textarea v-model:value="modal.form.defaultFolders" :rows="3" placeholder='["文件夹1","文件夹2"]' />
            </a-form-item>
            <a-form-item label="描述">
              <a-textarea v-model:value="modal.form.description" :rows="2" placeholder="阶段描述" />
            </a-form-item>
          </a-form>
        </a-modal>

        <!-- 克隆平台模板弹窗 -->
        <a-modal
          v-model:open="cloneModal.visible"
          title="克隆平台阶段模板"
          ok-text="关闭"
          cancel-text="取消"
          :footer="null"
          width="560px"
        >
          <a-alert
            type="info"
            show-icon
            message="平台提供以下行业模板，选择一套克隆到本租户即可在此基础上调整。"
            style="margin-bottom:12px"
          />
          <div v-loading="loadingPlatform">
            <div v-if="platformIndustries.length === 0" style="color:#999;text-align:center;padding:24px">
              暂无可用的平台行业模板
            </div>
            <a-row v-else :gutter="[12,12]">
              <a-col v-for="ind in platformIndustries" :key="ind.code" :span="12">
                <a-card size="small" hoverable class="st-industry-card">
                  <div class="st-industry-header">
                    <a-tag :color="industryColor(ind.code)" size="large">{{ industryLabel(ind.code) }}</a-tag>
                    <span class="st-industry-count">{{ ind.count }} 个阶段</span>
                  </div>
                  <div class="st-industry-desc">{{ industryDesc(ind.code) }}</div>
                  <a-button type="primary" block size="small" :loading="cloningIndustry === ind.code" @click="handleCloneIndustry(ind.code)">
                    <template #icon><CopyOutlined /></template>
                    克隆{{ industryLabel(ind.code) }}模板
                  </a-button>
                </a-card>
              </a-col>
            </a-row>
          </div>
        </a-modal>
      </a-tab-pane>
    </a-tabs>
  </div>
</template>

<script setup>
import { ref, reactive, computed, onMounted } from 'vue'
import { message, Modal } from 'ant-design-vue'
import { PlusOutlined, RocketOutlined, CopyOutlined, CloudDownloadOutlined } from '@ant-design/icons-vue'
import DataTable from '@/components/DataTable.vue'
import { useUserStore } from '@/stores/user'
import {
  listStageTemplates, createStageTemplate, updateStageTemplate, deleteStageTemplate,
  listPlatformIndustries, listPlatformTemplates, cloneStageTemplatesFromPlatform
} from '@/api'

const activeTab = ref('stage')

const userStore = useUserStore()
const isPlatformAdmin = computed(() => userStore.roles?.includes('PLATFORM_ADMIN'))

const templates = ref([])
const loading = ref(false)

// 行业与管理对象常量
const INDUSTRY_OPTIONS = [
  { value: 'TRADITIONAL', label: '传统产品研发', desc: '通用产品研发流程：市场验证→需求论证→方案设计→详细设计→工艺规划→试产' },
  { value: 'IPD', label: '电子高科 (IPD)', desc: '集成产品开发：概念→计划→开发→验证→发布→生命周期管理' },
  { value: 'MILITARY', label: '军工', desc: 'GJB 流程：论证→立项→方案→工程研制→设计定型→生产定型' },
  { value: 'AUTOMOTIVE', label: '汽车', desc: 'APQP 流程：概念设计→开发验证→生产准备→量产→上市' },
]
const INDUSTRY_COLOR = { TRADITIONAL: 'blue', IPD: 'purple', MILITARY: 'red', AUTOMOTIVE: 'green' }
const MANAGED_OBJECT_OPTIONS = [
  { value: 'FUNCTIONAL', label: '功能 (Functional)' },
  { value: 'PART',       label: '零组件 (Part)' },
  { value: 'DOCUMENT',   label: '文档 (Document)' },
]
const MANAGED_OBJECT_LABEL = Object.fromEntries(MANAGED_OBJECT_OPTIONS.map(o => [o.value, o.label]))

function industryLabel(code) { return (INDUSTRY_OPTIONS.find(o => o.value === code) || {}).label || code || '通用' }
function industryColor(code) { return INDUSTRY_COLOR[code] || 'default' }
function industryDesc(code) { return (INDUSTRY_OPTIONS.find(o => o.value === code) || {}).desc || '' }
function parseManagedObjects(json) {
  if (!json) return []
  try {
    const v = typeof json === 'string' ? JSON.parse(json) : json
    return Array.isArray(v) ? v : []
  } catch { return [] }
}
function managedObjectLabel(t) { return MANAGED_OBJECT_LABEL[t] || t }

const industryOptions = INDUSTRY_OPTIONS.map(({ value, label }) => ({ value, label }))
const managedObjectOptions = MANAGED_OBJECT_OPTIONS

const hasAnyTemplate = computed(() => templates.value.length > 0)

const columns = [
  { title: '阶段编码', dataIndex: 'code', key: 'code', width: 160 },
  { title: '名称', dataIndex: 'name', key: 'name', width: 100 },
  { title: '行业', key: 'industry', width: 140 },
  { title: '管理对象', key: 'managedObjects', width: 200 },
  { title: '图标', dataIndex: 'icon', key: 'icon', width: 140 },
  { title: '标识色', key: 'color', width: 100 },
  { title: '排序', dataIndex: 'sortOrder', key: 'sortOrder', width: 60 },
  { title: '默认文件夹', key: 'defaultFolders', width: 160, ellipsis: true },
  { title: '操作', key: 'action', width: 140 }
]

function formatFolders(json) {
  if (!json) return '-'
  try {
    const arr = typeof json === 'string' ? JSON.parse(json) : json
    return arr.join(', ')
  } catch { return json }
}

async function loadTemplates() {
  loading.value = true
  try {
    const res = await listStageTemplates()
    if (res.code === 200) templates.value = res.data || []
  } catch { message.error('加载失败') }
  finally { loading.value = false }
}

const modal = reactive({
  visible: false, isEdit: false, saving: false, editingOid: null,
  form: { code: '', name: '', icon: '', color: '', industry: 'TRADITIONAL', sortOrder: 1, defaultFolders: '', description: '', managedObjectTypes: [] }
})

function openCreate() {
  modal.editingOid = null; modal.isEdit = false
  modal.form = { code: '', name: '', icon: '', color: '', industry: 'TRADITIONAL', sortOrder: templates.value.length + 1, defaultFolders: '', description: '', managedObjectTypes: [] }
  modal.visible = true
}

function openEdit(record) {
  modal.editingOid = record.oid; modal.isEdit = true
  modal.form = {
    code: record.code, name: record.name || '',
    icon: record.icon || '', color: record.color || '',
    industry: record.industry || 'TRADITIONAL',
    sortOrder: record.sortOrder || 1,
    defaultFolders: typeof record.defaultFolders === 'string' ? record.defaultFolders : JSON.stringify(record.defaultFolders || []),
    description: record.description || '',
    managedObjectTypes: parseManagedObjects(record.managedObjectTypes)
  }
  modal.visible = true
}

async function handleSave() {
  const { code, name } = modal.form
  if (!code?.trim()) { message.warning('编码不能为空'); return }
  if (!name?.trim()) { message.warning('名称不能为空'); return }
  modal.saving = true
  try {
    const payload = {
      code: code.trim(), name: name.trim(),
      icon: modal.form.icon, color: modal.form.color,
      industry: modal.form.industry,
      sortOrder: modal.form.sortOrder,
      defaultFolders: modal.form.defaultFolders,
      description: modal.form.description,
      // 后端字段为 String（JSON 字符串），需把数组序列化为字符串
      managedObjectTypes: Array.isArray(modal.form.managedObjectTypes)
        ? JSON.stringify(modal.form.managedObjectTypes)
        : (modal.form.managedObjectTypes || null)
    }
    const res = modal.isEdit
      ? await updateStageTemplate(modal.editingOid, payload)
      : await createStageTemplate(payload)
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

async function handleDelete(record) {
  try {
    const res = await deleteStageTemplate(record.oid)
    if (res.code === 200) {
      message.success('已删除')
      await loadTemplates()
    } else {
      message.error(res.message || '删除失败')
    }
  } catch { message.error('删除失败') }
}

// ========== 克隆平台模板 ==========
const cloneModal = reactive({ visible: false })
const platformIndustries = ref([])
const loadingPlatform = ref(false)
const cloningIndustry = ref('')

async function loadPlatformIndustries() {
  loadingPlatform.value = true
  try {
    const indRes = await listPlatformIndustries()
    const industries = (indRes.code === 200 && indRes.data) ? indRes.data : []
    // 统计每个行业的阶段数
    const counts = {}
    for (const code of industries) {
      const listRes = await listPlatformTemplates(code)
      counts[code] = (listRes.code === 200 && Array.isArray(listRes.data)) ? listRes.data.length : 0
    }
    platformIndustries.value = industries.map(code => ({ code, count: counts[code] || 0 }))
  } catch { message.error('加载平台模板失败') }
  finally { loadingPlatform.value = false }
}

async function openCloneModal() {
  cloneModal.visible = true
  await loadPlatformIndustries()
}

async function handleCloneIndustry(industry) {
  Modal.confirm({
    title: `确认克隆${industryLabel(industry)}模板？`,
    content: `将把平台 ${industryLabel(industry)} 模板（约 ${platformIndustries.value.find(i => i.code === industry)?.count || 0} 个阶段）克隆到本租户。已存在的同编码阶段会自动跳过。`,
    okText: '确认克隆',
    cancelText: '取消',
    onOk: async () => {
      cloningIndustry.value = industry
      try {
        const res = await cloneStageTemplatesFromPlatform(industry)
        if (res.code === 200) {
          const n = res.data || 0
          if (n > 0) message.success(`已克隆 ${n} 个${industryLabel(industry)}阶段模板`)
          else message.info(`${industryLabel(industry)}模板已全部存在，无需重复克隆`)
          await loadTemplates()
        } else {
          message.error(res.message || '克隆失败')
        }
      } catch { message.error('克隆失败') }
      finally { cloningIndustry.value = '' }
    }
  })
}

onMounted(loadTemplates)
</script>

<style scoped>
.st-page { padding: 0; }
.st-header { padding: 16px 0; border-bottom: 1px solid #f0f0f0; margin-bottom: 12px; }
.st-title { margin: 0 0 4px 0; font-size: 18px; font-weight: 600; }
.st-subtitle { font-size: 13px; color: #8c8c8c; }
.st-stats-bar { display: flex; align-items: center; gap: 16px; margin-bottom: 12px; padding: 8px 16px; background: #fafafa; border-radius: 6px; }
.st-stat-item { display: flex; align-items: center; gap: 6px; }
.st-stat-icon { font-size: 16px; color: #1677ff; }
.st-stat-value { font-size: 18px; font-weight: 600; }
.st-stat-label { font-size: 12px; color: #8c8c8c; }
.st-stat-hint { font-size: 12px; color: #fa8c16; }
.st-table-wrap { margin-top: 0; }
.st-color-dot { display: inline-block; width: 12px; height: 12px; border-radius: 50%; margin-right: 6px; vertical-align: middle; }
.st-folders-preview { color: #666; font-size: 12px; cursor: pointer; }
.st-industry-card { border: 1px solid #f0f0f0; transition: all 0.2s; }
.st-industry-card:hover { border-color: #1677ff; box-shadow: 0 2px 8px rgba(22,119,255,0.1); }
.st-industry-header { display: flex; align-items: center; justify-content: space-between; margin-bottom: 8px; }
.st-industry-count { font-size: 12px; color: #8c8c8c; }
.st-industry-desc { font-size: 12px; color: #595959; min-height: 40px; margin-bottom: 12px; line-height: 1.5; }

/* 复用 Part 编辑窗口的紧凑样式 */
.part-create-modal :deep(.ant-modal-header) {
  padding: 12px 16px;
  border-bottom: 1px solid #f0f0f0;
}
.part-create-modal :deep(.ant-modal-title) {
  font-size: 15px;
}
.part-create-modal :deep(.ant-modal-footer) {
  padding: 10px 16px;
  border-top: 1px solid #f0f0f0;
}
.part-create-modal :deep(.ant-form-item) {
  margin-bottom: 12px;
}
.part-create-modal :deep(.ant-form-item-label) {
  padding-bottom: 2px;
}
.part-create-modal :deep(.ant-form-item-label > label) {
  font-size: 13px;
  color: #595959;
}
</style>