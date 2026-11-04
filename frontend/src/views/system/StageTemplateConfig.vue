<template>
  <div class="st-page">
    <a-tabs v-model:activeKey="activeTab" class="st-tabs">
      <a-tab-pane key="stage" tab="研发阶段">
        <div class="st-header">
          <div class="st-header-left">
            <h3 class="st-title">研发阶段</h3>
            <span class="st-subtitle">定义研发阶段，创建产品线/型号时按此生成阶段实例；阶段随企业所选方法论（传统产品研发 / IPD / 军工 / 汽车）落地，可在此调整。</span>
          </div>
        </div>

        <div class="st-stats-bar">
          <div class="st-stat-item">
            <RocketOutlined class="st-stat-icon" />
            <span class="st-stat-value">{{ templates.length }}</span>
            <span class="st-stat-label">阶段总数</span>
          </div>
          <div v-if="!isPlatformAdmin && !hasAnyTemplate" class="st-stat-hint">
            暂无研发阶段：装行业包会自动带入
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
            scroll-x="max-content"
            :show-column-toggle="true"
            stretch
            row-key="oid"
            size="middle"
          >
            <template #toolbar>
              <a-space>
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
                <!-- 短标签 + nowrap：长文案（"功能 (Functional)"）在窄列里会逐字换行，把整列行高撑乱 -->
                <a-space size="4" wrap>
                  <a-tag
                    v-for="t in parseManagedObjects(record.managedObjectTypes)"
                    :key="t"
                    color="purple"
                    size="small"
                    style="white-space:nowrap"
                    :title="managedObjectLabel(t)"
                  >{{ managedObjectShortLabel(t) }}</a-tag>
                  <span v-if="parseManagedObjects(record.managedObjectTypes).length === 0" style="color:#bfbfbf">-</span>
                </a-space>
              </template>
              <template v-else-if="column.key === 'defaultFolders'">
                <!-- tooltip 给"格式化后"的完整内容，而不是原始 JSON 串 -->
                <a-tooltip :title="formatFolders(record.defaultFolders)">
                  <span class="st-folders-preview">{{ formatFolders(record.defaultFolders) }}</span>
                </a-tooltip>
              </template>
              <template v-else-if="column.key === 'action'">
                <a-space size="small">
                  <a-button type="link" size="small" @click="openEdit(record)">编辑</a-button>
                  <a-popconfirm title="确定删除该阶段？" @confirm="handleDelete(record)">
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
          :title="modal.isEdit ? '编辑阶段' : '新增阶段'"
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

      </a-tab-pane>

      <!-- 交付件模板：与阶段模板同属"方法论内容"，装包带入企业一份、企业可裁剪 -->
      <a-tab-pane key="deliverable" tab="交付件">
        <DeliverableTemplateConfig />
      </a-tab-pane>

      <!-- 阶段门模板：门的（编码, 阶段）与交付件的（门, 阶段）一一对应，可交叉校验 -->
      <a-tab-pane key="gate" tab="阶段门">
        <GateTemplateConfig />
      </a-tab-pane>

      <!-- 评审模板：每份评审挂在某道门上；交付件的"评审通过"完成条件引用这里的编码 -->
      <a-tab-pane key="review" tab="评审">
        <ReviewTemplateConfig />
      </a-tab-pane>

      <!-- 检查规则：声明式 JSON（时机 + 对象 + 条件 + 严重度），运行期按这些声明做检查 -->
      <a-tab-pane key="rule" tab="检查规则">
        <RuleTemplateConfig />
      </a-tab-pane>

      <!-- 问题类型：问题被结构化的地基（闭环率 / 超期问题数等指标都按这里的编码统计） -->
      <a-tab-pane key="issuetype" tab="问题类型">
        <IssueTypeTemplateConfig />
      </a-tab-pane>

      <!-- 指标：计算口径 + 数据就绪度（A/B/C；C 级第一版显示「暂未评估」，禁止编造数值） -->
      <a-tab-pane key="metric" tab="指标">
        <MetricTemplateConfig />
      </a-tab-pane>

      <!-- 驾驶舱：布局定义（3 个视角 + 分组件；主视角 = 产品经理/研发的日常工作面） -->
      <a-tab-pane key="dashboard" tab="驾驶舱">
        <DashboardTemplateConfig />
      </a-tab-pane>
    </a-tabs>
  </div>
</template>

<script setup>
import { ref, reactive, computed, onMounted } from 'vue'
import { message } from 'ant-design-vue'
import { PlusOutlined, RocketOutlined } from '@ant-design/icons-vue'
import DataTable from '@/components/DataTable.vue'
import DeliverableTemplateConfig from '@/views/system/DeliverableTemplateConfig.vue'
import GateTemplateConfig from '@/views/system/GateTemplateConfig.vue'
import ReviewTemplateConfig from '@/views/system/ReviewTemplateConfig.vue'
import RuleTemplateConfig from '@/views/system/RuleTemplateConfig.vue'
import IssueTypeTemplateConfig from '@/views/system/IssueTypeTemplateConfig.vue'
import MetricTemplateConfig from '@/views/system/MetricTemplateConfig.vue'
import DashboardTemplateConfig from '@/views/system/DashboardTemplateConfig.vue'
import { useUserStore } from '@/stores/user'
import { useCapabilities } from '@/composables/useCapabilities'

// 功能架构（FUNCTIONAL）随 military-gjb / automotive-apqp 两个行业包而来（platform-base 已不含它）。
// "管理对象"下拉据此过滤；标签映射仍保留全量（见下方 MANAGED_OBJECT_LABEL），
// 否则历史阶段模板里已配的 FUNCTIONAL 会显示成裸 code。
// 账本由 MainLayout 统一加载，这里只读。
const { has } = useCapabilities()
import {
  listStageTemplates,
  createStageTemplate,
  updateStageTemplate,
  deleteStageTemplate
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
// 标签映射：**全量**（含 FUNCTIONAL）—— 历史阶段模板里已配 FUNCTIONAL 的行仍要显示中文名。
const MANAGED_OBJECT_LABEL = {
  FUNCTIONAL: '功能 (Functional)',
  PART:       '零组件 (Part)',
  DOCUMENT:   '文档 (Document)',
}
// 下拉可选项：按能力账本过滤 —— FUNCTIONAL 随 military-gjb / automotive-apqp 两个行业包而来
// （platform-base 已不含它），没装包的企业不给这个选项。标签映射仍取全量，见上。
// 2026-11-03：零件不再作为阶段的管理对象 —— 零组件统一在「物料清单」页签创建与维护，
// 阶段下的资料区只放文档 / 功能架构。已配了 PART 的历史模板仍按标签显示（见上），只是不能再选。
const MANAGED_OBJECT_OPTIONS = computed(() => [
  ...(has('FUNCTIONAL_ARCHITECTURE')
    ? [{ value: 'FUNCTIONAL', label: MANAGED_OBJECT_LABEL.FUNCTIONAL }]
    : []),
  { value: 'DOCUMENT', label: MANAGED_OBJECT_LABEL.DOCUMENT },
])

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

/** 列表短标签：去掉"（英文）"后缀，窄列里才排得下（完整名称放在标签 title 上） */
function managedObjectShortLabel(t) {
  return managedObjectLabel(t).replace(/\s*[（(][^）)]*[）)]\s*$/, '')
}

const industryOptions = INDUSTRY_OPTIONS.map(({ value, label }) => ({ value, label }))
const managedObjectOptions = MANAGED_OBJECT_OPTIONS

const hasAnyTemplate = computed(() => templates.value.length > 0)

const columns = [
  { title: '阶段编码', dataIndex: 'code', key: 'code', width: 160 },
  { title: '名称', dataIndex: 'name', key: 'name', width: 110 },
  { title: '行业', key: 'industry', width: 130 },
  // 管理对象列给足宽度（短标签 + wrap 后一行放得下 2-3 个）
  { title: '管理对象', key: 'managedObjects', width: 240 },
  { title: '图标', dataIndex: 'icon', key: 'icon', width: 120 },
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


onMounted(loadTemplates)
</script>

<style scoped>
/* 撑满型页面（同 LifecycleConfig 口径）：页签内容区给出确定高度，
   让各 Tab 里的 DataTable 能用 stretch 模式 —— 表体内部滚动、
   横向滚动条固定在视口底部，不必拉到页面最底才能横向滚动。 */
.st-page {
  display: flex;
  flex-direction: column;
  height: 100%;
  min-height: 0;
  padding: 0;
}
.st-header { padding: 16px 0; border-bottom: 1px solid #f0f0f0; margin-bottom: 12px; flex-shrink: 0; }
.st-title { margin: 0 0 4px 0; font-size: 18px; font-weight: 600; }
.st-subtitle { font-size: 13px; color: #8c8c8c; }
.st-stats-bar { display: flex; align-items: center; gap: 16px; margin-bottom: 12px; padding: 8px 16px; background: #fafafa; border-radius: 6px; flex-shrink: 0; }
.st-stat-item { display: flex; align-items: center; gap: 6px; }
.st-stat-icon { font-size: 16px; color: #1677ff; }
.st-stat-value { font-size: 18px; font-weight: 600; }
.st-stat-label { font-size: 12px; color: #8c8c8c; }
.st-stat-hint { font-size: 12px; color: #fa8c16; }
/* 表格区域：flex 容器 + 保底高度（同 LifecycleConfig）——
   stretch 表体自滚、分页器固定；矮窗口下由 .bc-content 的外层滚动条兜底。 */
.st-table-wrap {
  margin-top: 0;
  flex: 1;
  min-height: 320px;
  display: flex;
  flex-direction: column;
  overflow: hidden;
}
.st-color-dot { display: inline-block; width: 12px; height: 12px; border-radius: 50%; margin-right: 6px; vertical-align: middle; }
.st-folders-preview { color: #666; font-size: 12px; cursor: pointer; }
.st-industry-card { border: 1px solid #f0f0f0; transition: all 0.2s; }
.st-industry-card:hover { border-color: #1677ff; box-shadow: 0 2px 8px rgba(22,119,255,0.1); }
.st-industry-header { display: flex; align-items: center; justify-content: space-between; margin-bottom: 8px; }
.st-industry-count { font-size: 12px; color: #8c8c8c; }
.st-industry-desc { font-size: 12px; color: #595959; min-height: 40px; margin-bottom: 12px; line-height: 1.5; }

/* 页签撑满链（同 LifecycleConfig 的 .lc-tabs）：content-holder / content / tabpane
   逐层放行高度，各 Tab 的根组件才能拿到确定高度启用内部滚动。
   content-holder 用 overflow:visible —— 矮视口下有 min-height 保底的表格区
   溢出时交给 .bc-content 的外层滚动条，避免下半截被裁掉。 */
.st-tabs {
  flex: 1;
  display: flex;
  flex-direction: column;
  min-height: 0;
}
.st-tabs :deep(.ant-tabs-content-holder) {
  flex: 1;
  display: flex;
  flex-direction: column;
  overflow: visible;
}
.st-tabs :deep(.ant-tabs-content) {
  flex: 1;
  min-height: 0;
}
.st-tabs :deep(.ant-tabs-tabpane) {
  height: 100%;
  display: flex;
  flex-direction: column;
}

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