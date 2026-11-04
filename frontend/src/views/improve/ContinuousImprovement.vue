<template>
  <div class="ci-page">
    <!-- 统计卡：点击即筛选（再点一次取消） -->
    <a-row :gutter="12" class="ci-stats">
      <a-col :xs="12" :sm="8" :md="4" v-for="card in statCards" :key="card.key">
        <a-card
          size="small"
          hoverable
          class="ci-stat-card"
          :class="{ 'ci-stat-card--active': filters.status === card.filterStatus }"
          @click="clickStatCard(card)"
        >
          <a-statistic
            :title="card.label"
            :value="card.key === 'closeRate' ? closeRatePercent : (stats[card.key] || 0)"
            :suffix="card.key === 'closeRate' ? '%' : ''"
            :value-style="{ fontSize: '20px', color: card.color }"
          />
        </a-card>
      </a-col>
    </a-row>

    <DataTable
      scroll-x="max-content"
      :columns="columns"
      :data-source="rows"
      :loading="loading"
      pagination-mode="backend"
      :pagination="{ current: page, pageSize }"
      :total="total"
      search-placeholder="搜索编号 / 标题 / 描述..."
      :search-fields="['code', 'name', 'description']"
      row-key="oid"
      size="middle"
      @change="handleTableChange"
      @update:search="handleSearch"
    >
      <template #toolbar>
        <a-space>
          <a-select
            v-model:value="filters.status"
            :options="statusFilterOptions"
            placeholder="全部状态"
            allow-clear
            size="small"
            style="width: 110px"
            @change="reload"
          />
          <a-select
            v-model:value="filters.severity"
            :options="severityFilterOptions"
            placeholder="全部严重度"
            allow-clear
            size="small"
            style="width: 110px"
            @change="reload"
          />
          <a-select
            v-model:value="filters.owner"
            :options="ownerOptions"
            placeholder="全部责任人"
            allow-clear
            show-search
            option-filter-prop="label"
            size="small"
            style="width: 140px"
            @change="reload"
          />
          <a-button type="primary" size="small" @click="openCreate">
            <template #icon><PlusOutlined /></template>
            提问题
          </a-button>
        </a-space>
      </template>

      <template #bodyCell="{ column, record }">
        <template v-if="column.key === 'code'">
          <a-tag color="blue">{{ record.code }}</a-tag>
        </template>
        <template v-else-if="column.key === 'name'">
          <a class="ci-title-link" @click="openDetail(record)">{{ record.name }}</a>
        </template>
        <template v-else-if="column.key === 'product'">
          <div class="ci-product">
            <span>{{ productDisplay(record) }}</span>
            <a-tooltip v-if="gateCapability && record.gateLabel" :title="record.gateLabel">
              <span class="ci-gate">{{ gateCode(record.gateLabel) }}</span>
            </a-tooltip>
          </div>
        </template>
        <template v-else-if="column.key === 'issueType'">
          <span>{{ typeLabel(record.issueType) }}</span>
        </template>
        <template v-else-if="column.key === 'severity'">
          <a-tag :color="ISSUE_SEVERITY_COLOR[record.severity] || 'default'">
            {{ ISSUE_SEVERITY_LABEL[record.severity] || record.severity || '—' }}
          </a-tag>
        </template>
        <template v-else-if="column.key === 'status'">
          <a-tag :color="ISSUE_STATUS_COLOR[record.status] || 'default'">
            {{ ISSUE_STATUS_LABEL[record.status] || record.status }}
          </a-tag>
        </template>
        <template v-else-if="column.key === 'owner'">
          <span>{{ userDisplay(record.owner) }}</span>
        </template>
        <template v-else-if="column.key === 'dueDate'">
          <span :class="{ 'ci-overdue': isOverdue(record) }">
            {{ record.dueDate || '—' }}
            <a-tooltip v-if="isOverdue(record)" title="已超期">
              <ExclamationCircleOutlined class="ci-overdue-icon" />
            </a-tooltip>
          </span>
        </template>
        <template v-else-if="column.key === 'reporter'">
          <span>{{ userDisplay(record.reporter) }}</span>
        </template>
        <template v-else-if="column.key === 'action'">
          <a-space size="small">
            <a-button type="link" size="small" @click="openDetail(record)">详情</a-button>
            <a-button
              v-if="primaryAction(record.status) && canOperate(record, primaryAction(record.status))"
              type="link"
              size="small"
              @click="openTransition(record, primaryAction(record.status))"
            >
              {{ ISSUE_ACTION_LABEL[primaryAction(record.status)] }}
            </a-button>
            <a-popconfirm
              v-if="record.status === 'OPEN' && record.reporter === currentUsername"
              title="确定删除该问题？处理记录会一并删除"
              @confirm="handleDelete(record)"
            >
              <a-button type="link" size="small" danger>删除</a-button>
            </a-popconfirm>
          </a-space>
        </template>
      </template>
    </DataTable>

    <!-- 新建 / 编辑弹窗 -->
    <a-modal
      v-model:open="modal.visible"
      :title="modal.isEdit ? '编辑问题' : '提出问题'"
      :confirm-loading="modal.saving"
      width="680px"
      centered
      ok-text="保存"
      cancel-text="取消"
      :body-style="{ maxHeight: 'calc(100vh - 200px)', overflowY: 'auto', padding: '16px 20px 20px' }"
      @ok="handleSave"
    >
      <a-form :model="modal.form" layout="vertical">
        <a-form-item label="标题" required>
          <a-input
            v-model:value="modal.form.name"
            placeholder="一句话说清问题（如：X10 主板电池仓卡扣断裂）"
            :maxlength="300"
          />
        </a-form-item>
        <a-row :gutter="12">
          <a-col :span="8">
            <a-form-item label="类型">
              <a-select
                v-model:value="modal.form.issueType"
                :options="typeOptions"
                placeholder="未分类"
                allow-clear
                show-search
                option-filter-prop="label"
              />
            </a-form-item>
          </a-col>
          <a-col :span="8">
            <a-form-item label="严重度">
              <a-select v-model:value="modal.form.severity" :options="severityFilterOptions" />
            </a-form-item>
          </a-col>
          <a-col :span="8">
            <a-form-item label="来源">
              <a-select
                v-model:value="modal.form.source"
                :options="sourceOptions"
                placeholder="未填"
                allow-clear
              />
            </a-form-item>
          </a-col>
        </a-row>
        <a-row :gutter="12">
          <a-col :span="12">
            <a-form-item label="关联产品（系列 / 型号）" required>
              <a-select
                v-model:value="modal.form.product"
                :options="productOptions"
                placeholder="选择产品系列或型号"
                show-search
                option-filter-prop="label"
                @change="handleProductChange"
              />
            </a-form-item>
          </a-col>
          <a-col v-if="gateCapability" :span="12">
            <a-form-item label="关联阶段门">
              <a-select
                v-model:value="modal.form.gateOid"
                :options="gateOptions"
                :disabled="!productOidOf(modal.form.product)"
                placeholder="可选：哪道门发现的"
                allow-clear
                show-search
                option-filter-prop="label"
              />
            </a-form-item>
          </a-col>
        </a-row>
        <a-row :gutter="12">
          <a-col :span="12">
            <a-form-item label="责任人">
              <a-select
                v-model:value="modal.form.owner"
                :options="ownerOptions"
                placeholder="未指派"
                allow-clear
                show-search
                option-filter-prop="label"
              />
            </a-form-item>
          </a-col>
          <a-col :span="12">
            <a-form-item label="计划完成">
              <a-input v-model:value="modal.form.dueDate" type="date" />
            </a-form-item>
          </a-col>
        </a-row>
        <a-row :gutter="12">
          <a-col :span="9">
            <a-form-item label="关联对象类型">
              <a-select
                v-model:value="modal.form.relatedType"
                :options="relatedTypeOptions"
                placeholder="不关联"
                allow-clear
              />
            </a-form-item>
          </a-col>
          <a-col :span="15">
            <!-- 关联对象编码：模糊搜索（2026-10-25 用户反馈"输入 003 搜不到"）。
                 选中即把类型 + 编码一起写回；BOM / 其他 这类没有候选对象的类型仍可直接手写 -->
            <a-form-item label="关联对象编码">
              <a-select
                ref="relatedCodeSelectRef"
                v-model:value="relatedCodeValue"
                show-search
                allow-clear
                :filter-option="false"
                :options="relatedObjectOptions"
                :loading="relatedObjectLoading"
                placeholder="输入编码 / 名称搜索（如 003）；BOM / 其他 可直接手写"
                @search="onSearchRelatedObject"
                @focus="onFocusRelatedObject"
                @change="onRelatedObjectChange"
              >
                <template #notFoundContent>
                  <div class="ci-pick-empty">
                    <span v-if="relatedObjectLoading">搜索中…</span>
                    <template v-else>
                      <span>没有匹配的对象</span>
                      <a v-if="relatedKeyword" @click="useTypedCode">直接用「{{ relatedKeyword }}」</a>
                    </template>
                  </div>
                </template>
              </a-select>
            </a-form-item>
          </a-col>
        </a-row>
        <a-form-item label="问题描述">
          <a-textarea
            v-model:value="modal.form.description"
            :rows="3"
            :maxlength="4000"
            placeholder="现象、发生环节、影响范围..."
          />
        </a-form-item>
        <template v-if="modal.isEdit">
          <a-form-item label="根因分析">
            <a-textarea v-model:value="modal.form.rootCause" :rows="2" :maxlength="2000" />
          </a-form-item>
          <a-form-item label="处理措施">
            <a-textarea v-model:value="modal.form.resolution" :rows="2" :maxlength="2000" />
          </a-form-item>
        </template>
      </a-form>
    </a-modal>

    <!-- 详情抽屉：字段 + 闭环动作 + 时间线 -->
    <a-drawer
      v-model:open="drawer.visible"
      :width="640"
      :title="drawer.issue ? `${drawer.issue.code} ${drawer.issue.name}` : '问题详情'"
      :body-style="{ paddingBottom: '16px' }"
    >
      <a-spin :spinning="drawer.loading">
        <template v-if="drawer.issue">
          <div class="ci-flow-actions">
            <a-button
              v-for="action in drawerActions"
              :key="action"
              size="small"
              :type="actionKind(action)"
              :danger="actionDanger(action)"
              @click="openTransition(drawer.issue, action)"
            >
              {{ ISSUE_ACTION_LABEL[action] }}
            </a-button>
            <a-button
              v-if="canEditIssue"
              size="small"
              @click="openEdit(drawer.issue)"
            >
              编辑
            </a-button>
          </div>

          <a-descriptions :column="2" size="small" bordered class="ci-desc">
            <a-descriptions-item label="状态">
              <a-tag :color="ISSUE_STATUS_COLOR[drawer.issue.status] || 'default'">
                {{ ISSUE_STATUS_LABEL[drawer.issue.status] || drawer.issue.status }}
              </a-tag>
            </a-descriptions-item>
            <a-descriptions-item label="严重度">
              <a-tag :color="ISSUE_SEVERITY_COLOR[drawer.issue.severity] || 'default'">
                {{ ISSUE_SEVERITY_LABEL[drawer.issue.severity] || drawer.issue.severity || '—' }}
              </a-tag>
            </a-descriptions-item>
            <a-descriptions-item label="类型">{{ typeLabel(drawer.issue.issueType) }}</a-descriptions-item>
            <a-descriptions-item label="来源">{{ ISSUE_SOURCE_LABEL[drawer.issue.source] || '—' }}</a-descriptions-item>
            <a-descriptions-item label="责任人">{{ userDisplay(drawer.issue.owner) }}</a-descriptions-item>
            <a-descriptions-item label="提出人">{{ userDisplay(drawer.issue.reporter) }}</a-descriptions-item>
            <a-descriptions-item label="计划完成">
              <span :class="{ 'ci-overdue': isOverdue(drawer.issue) }">{{ drawer.issue.dueDate || '—' }}</span>
            </a-descriptions-item>
            <a-descriptions-item label="关闭时间">{{ formatDateTime(drawer.issue.closedAt) }}</a-descriptions-item>
            <a-descriptions-item label="关联产品">{{ productDisplay(drawer.issue) }}</a-descriptions-item>
            <a-descriptions-item v-if="gateCapability" label="关联阶段门">{{ drawer.issue.gateLabel || '—' }}</a-descriptions-item>
            <a-descriptions-item label="关联对象" :span="2">
              {{ drawer.issue.relatedCode ? `${drawer.issue.relatedType || ''} ${drawer.issue.relatedCode}`.trim() : '—' }}
            </a-descriptions-item>
            <a-descriptions-item label="问题描述" :span="2">
              <div class="ci-pre">{{ drawer.issue.description || '—' }}</div>
            </a-descriptions-item>
            <a-descriptions-item label="根因分析" :span="2">
              <div class="ci-pre">{{ drawer.issue.rootCause || '—' }}</div>
            </a-descriptions-item>
            <a-descriptions-item label="处理措施" :span="2">
              <div class="ci-pre">{{ drawer.issue.resolution || '—' }}</div>
            </a-descriptions-item>
            <a-descriptions-item label="验证结论" :span="2">
              <div class="ci-pre">{{ drawer.issue.verifyResult || '—' }}</div>
            </a-descriptions-item>
          </a-descriptions>

          <div class="ci-comment-box">
            <a-textarea
              v-model:value="commentText"
              :rows="2"
              :maxlength="2000"
              placeholder="追加一条跟踪备注（不改变状态）..."
            />
            <a-button type="primary" size="small" :loading="commentSaving" @click="handleAddComment">
              追加备注
            </a-button>
          </div>

          <a-divider orientation="left" class="ci-divider">处理记录</a-divider>
          <a-empty v-if="!drawer.activities.length" description="暂无记录" :image-style="{ height: '48px' }" />
          <a-timeline v-else class="ci-timeline">
            <a-timeline-item
              v-for="act in drawerActivities"
              :key="act.oid"
              :color="actionColor(act.action)"
            >
              <div class="ci-act-head">
                <b>{{ ISSUE_ACTION_LABEL[act.action] || act.action }}</b>
                <span v-if="act.fromStatus && act.toStatus" class="ci-act-flow">
                  {{ ISSUE_STATUS_LABEL[act.fromStatus] || act.fromStatus }}
                  →
                  {{ ISSUE_STATUS_LABEL[act.toStatus] || act.toStatus }}
                </span>
                <span class="ci-act-time">{{ formatDateTime(act.createdAt) }}</span>
              </div>
              <div class="ci-act-op">{{ userDisplay(act.operator || act.creator) }}</div>
              <div v-if="act.comment" class="ci-act-comment">{{ act.comment }}</div>
            </a-timeline-item>
          </a-timeline>
        </template>
      </a-spin>
    </a-drawer>

    <!-- 流转弹窗（需要填说明 / 验证结论的动作） -->
    <a-modal
      v-model:open="flow.visible"
      :title="flow.action ? ISSUE_ACTION_LABEL[flow.action] : '流转'"
      :confirm-loading="flow.saving"
      width="520px"
      centered
      ok-text="确定"
      cancel-text="取消"
      @ok="handleFlow"
    >
      <a-form layout="vertical">
        <!-- 「提交验证」= 处理完成、请人来验 —— 根因与措施两项必填（2026-10-16 用户反馈） -->
        <template v-if="flow.action === 'SUBMIT_VERIFY'">
          <a-form-item label="根因分析" required>
            <a-textarea
              v-model:value="flow.rootCause"
              :rows="2"
              :maxlength="2000"
              placeholder="为什么会发生（不找根因，改完还会再犯）"
            />
          </a-form-item>
          <a-form-item label="处理措施" required>
            <a-textarea
              v-model:value="flow.resolution"
              :rows="2"
              :maxlength="2000"
              placeholder="怎么处理的（改了什么、补了什么）"
            />
          </a-form-item>
        </template>
        <a-form-item v-if="requiresVerifyResult(flow.action)" label="验证结论" required>
          <a-textarea
            v-model:value="flow.verifyResult"
            :rows="3"
            :maxlength="2000"
            placeholder="怎么验证的、结果如何（如：复测 3 台，连续 72h 无复现）"
          />
        </a-form-item>
        <a-form-item
          v-if="requiresComment(flow.action) || requiresVerifyResult(flow.action)"
          :label="flowCommentLabel(flow.action)"
          :required="requiresComment(flow.action)"
        >
          <a-textarea
            v-model:value="flow.comment"
            :rows="3"
            :maxlength="2000"
            :placeholder="flowCommentPlaceholder(flow.action)"
          />
        </a-form-item>
      </a-form>
    </a-modal>
  </div>
</template>

<script setup>
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { useRoute } from 'vue-router'
import { message } from 'ant-design-vue'
import { ExclamationCircleOutlined, PlusOutlined } from '@ant-design/icons-vue'
import DataTable from '@/components/DataTable.vue'
import {
  addIssueComment,
  createIssue,
  deleteIssue,
  getAllUsers,
  getIssueDetail,
  getIssueStats,
  getProductLines,
  getProductModels,
  listIssues,
  listIssueObjectCandidates,
  listIssueTypeTemplates,
  listMethodologyInstances,
  transitionIssue,
  updateIssue
} from '@/api'
import {
  ISSUE_ACTION_LABEL,
  ISSUE_SEVERITY_COLOR,
  ISSUE_SEVERITY_LABEL,
  ISSUE_SOURCE_LABEL,
  ISSUE_STATUS_COLOR,
  ISSUE_STATUS_LABEL,
  availableActions,
  primaryAction,
  requiresComment,
  requiresOwner,
  requiresResolution,
  requiresRootCause,
  requiresVerifyResult
} from '@/utils/issueFlow'
import { useCapabilities } from '@/composables/useCapabilities'
import { useUserStore } from '@/stores/user'

const userStore = useUserStore()
const currentUsername = computed(() => userStore.username || '')

/**
 * 当前登录用户能否执行该流转动作 —— 责任人类动作（START 开始处理 / SUBMIT_VERIFY 提交验证）
 * 只给责任人本人（`owner`），别人连按钮都不出现；后端 transition 同样比对（非责任人返 403）。
 * 验证类动作归提出人，取消 / 重开不限，故不受此限。
 */
function canOperate(record, action) {
  if (!record || !action) return false
  if (!requiresOwner(action)) return true
  return !!record.owner && record.owner === currentUsername.value
}

// 阶段门能力开关（读 ck_tenant_capability 账本）：未启用时不显示门相关入口 ——
// 门是 IPD / 门径体系的产物（包的门清单 technical-gates.json / decision-gates.json 只在 smart-hardware-ipd），后端同样校验。
const { has: hasCapability, load: loadCapabilities } = useCapabilities()
const gateCapability = computed(() => hasCapability('GATE_REVIEW'))

// ==================== 列表 ====================

const columns = [
  { title: '编号', dataIndex: 'code', key: 'code', width: 100 },
  { title: '标题', dataIndex: 'name', key: 'name', width: 220 },
  { title: '关联产品', key: 'product', width: 180 },
  { title: '类型', key: 'issueType', width: 100 },
  { title: '严重度', key: 'severity', width: 80 },
  { title: '状态', key: 'status', width: 90 },
  { title: '责任人', key: 'owner', width: 100 },
  { title: '计划完成', dataIndex: 'dueDate', key: 'dueDate', width: 100 },
  { title: '提出人', key: 'reporter', width: 100 },
  { title: '操作', key: 'action', width: 140 }
]

const loading = ref(false)
const rows = ref([])
const total = ref(0)
const page = ref(1)
const pageSize = ref(20)
const filters = reactive({ status: undefined, severity: undefined, owner: undefined, keyword: '' })
const stats = reactive({ total: 0, OPEN: 0, IN_PROGRESS: 0, VERIFYING: 0, CLOSED: 0, CANCELLED: 0, closeRate: null })

const closeRatePercent = computed(() =>
  stats.closeRate == null ? 0 : Math.round(stats.closeRate * 1000) / 10
)

const statCards = [
  { key: 'OPEN', label: '待处理', filterStatus: 'OPEN', color: '#1677ff' },
  { key: 'IN_PROGRESS', label: '处理中', filterStatus: 'IN_PROGRESS', color: '#fa8c16' },
  { key: 'VERIFYING', label: '待验证', filterStatus: 'VERIFYING', color: '#722ed1' },
  { key: 'CLOSED', label: '已关闭', filterStatus: 'CLOSED', color: '#52c41a' },
  { key: 'CANCELLED', label: '已取消', filterStatus: 'CANCELLED', color: '#8c8c8c' },
  { key: 'closeRate', label: '闭环率', filterStatus: null, color: '#13c2c2' }
]

const statusFilterOptions = [
  { value: 'OPEN', label: '待处理' },
  { value: 'IN_PROGRESS', label: '处理中' },
  { value: 'VERIFYING', label: '待验证' },
  { value: 'CLOSED', label: '已关闭' },
  { value: 'CANCELLED', label: '已取消' }
]
const severityFilterOptions = [
  { value: 'BLOCK', label: '阻塞' },
  { value: 'WARN', label: '警告' }
]
const sourceOptions = Object.entries(ISSUE_SOURCE_LABEL).map(([value, label]) => ({ value, label }))

/**
 * 关联对象的类型（对应 ck_issue.related_type；给常用项，不强制）。
 * 前五项在「关联对象编码」里能搜出来选（见 object-candidates）；BOM / 其他 只能手写编码。
 */
const relatedTypeOptions = [
  { value: 'PART', label: '零组件' },
  { value: 'DOCUMENT', label: '文档' },
  { value: 'ENG_DOCUMENT', label: '工程数据' },
  { value: 'PRODUCT_LINE', label: '产品系列' },
  { value: 'PRODUCT_MODEL', label: '产品型号' },
  { value: 'BOM', label: 'BOM' },
  { value: 'OTHER', label: '其他' }
]

function clickStatCard(card) {
  if (!card.filterStatus) return
  filters.status = filters.status === card.filterStatus ? undefined : card.filterStatus
  reload()
}

async function loadList() {
  loading.value = true
  try {
    const res = await listIssues({
      status: filters.status || undefined,
      severity: filters.severity || undefined,
      owner: filters.owner || undefined,
      keyword: filters.keyword || undefined,
      page: page.value,
      pageSize: pageSize.value
    })
    if (res.code === 200) {
      rows.value = res.data.rows || []
      total.value = res.data.total || 0
    } else {
      message.error(res.message || '加载失败')
    }
  } catch {
    message.error('加载问题列表失败')
  } finally {
    loading.value = false
  }
}

async function loadStats() {
  try {
    const res = await getIssueStats()
    if (res.code === 200) {
      Object.assign(stats, res.data || {})
    }
  } catch {
    /* 统计失败不打扰主流程 */
  }
}

function reload() {
  page.value = 1
  loadList()
}

function handleSearch(value) {
  filters.keyword = value || ''
  reload()
}

function handleTableChange({ pagination }) {
  if (pagination) {
    page.value = pagination.current || 1
    pageSize.value = pagination.pageSize || 20
  }
  loadList()
}

async function refreshAll() {
  await Promise.all([loadList(), loadStats()])
}

// ==================== 用户与类型字典 ====================

const users = ref([])
const typeTemplates = ref([])

const ownerOptions = computed(() =>
  users.value.map((u) => ({
    value: u.username,
    label: u.displayName ? `${u.displayName}（${u.username}）` : u.username
  }))
)
const typeOptions = computed(() =>
  typeTemplates.value.filter((t) => !t.disabled).map((t) => ({ value: t.code, label: t.name }))
)

function userDisplay(username) {
  if (!username) return '—'
  const u = users.value.find((item) => item.username === username)
  return u && u.displayName ? u.displayName : username
}

function typeLabel(code) {
  if (!code) return '—'
  const t = typeTemplates.value.find((item) => item.code === code)
  return t ? t.name : code
}

async function loadUsers() {
  try {
    const res = await getAllUsers()
    if (res.code === 200) users.value = res.data || []
  } catch {
    /* 用户字典失败只影响显示名 */
  }
}

async function loadTypes() {
  try {
    const res = await listIssueTypeTemplates()
    if (res.code === 200) typeTemplates.value = res.data || []
  } catch {
    /* 类型字典失败只影响下拉 */
  }
}

// ==================== 产品与阶段门（IPD 上下文） ====================

const productLines = ref([])
const productModels = ref([])
const gates = ref([])

/** 下拉分组：产品系列 + 产品型号（值用「类型:oid」复合串，一次选择定两字段） */
const productOptions = computed(() => {
  const groups = []
  if (productLines.value.length) {
    groups.push({
      label: '产品系列',
      options: productLines.value.map((p) => ({
        value: `PRODUCT_LINE:${p.oid}`,
        label: p.name ? `${p.name}（${p.code}）` : p.code
      }))
    })
  }
  if (productModels.value.length) {
    groups.push({
      label: '产品型号',
      options: productModels.value.map((p) => ({
        value: `PRODUCT_MODEL:${p.oid}`,
        label: p.name ? `${p.name}（${p.code}）` : p.code
      }))
    })
  }
  return groups
})

const gateOptions = computed(() =>
  gates.value.map((g) => ({ value: g.oid, label: `${g.code} ${g.name}` }))
)

/** 从复合值取 oid */
function productOidOf(composite) {
  if (!composite) return ''
  const idx = String(composite).indexOf(':')
  return idx >= 0 ? String(composite).slice(idx + 1) : ''
}

function productDisplay(record) {
  if (!record || !record.productOid) return '—'
  const list = record.productType === 'PRODUCT_MODEL' ? productModels.value : productLines.value
  const hit = list.find((p) => p.oid === record.productOid)
  return hit ? (hit.name || hit.code) : record.productOid
}

/** 门标签（后端装配 gateLabel：「TR3 详细设计评审（投板门）」）取首段 code 显示，全文在 tooltip */
function gateCode(label) {
  if (!label) return ''
  return String(label).split(' ')[0]
}

async function loadProducts() {
  try {
    const [linesRes, modelsRes] = await Promise.all([getProductLines(), getProductModels({})])
    if (linesRes.code === 200) {
      productLines.value = Array.isArray(linesRes.data) ? linesRes.data : (linesRes.data?.rows || [])
    }
    if (modelsRes.code === 200) {
      productModels.value = Array.isArray(modelsRes.data) ? modelsRes.data : (modelsRes.data?.rows || [])
    }
  } catch {
    /* 产品字典失败只影响名称显示与下拉 */
  }
}

/** 选定产品后取该产品的门实例（六类实例接口一次往返） */
async function loadGates(productOid) {
  gates.value = []
  if (!productOid || !gateCapability.value) return
  try {
    const res = await listMethodologyInstances(productOid)
    if (res.code === 200) gates.value = res.data.gates || []
  } catch {
    /* 门清单失败 = 门下拉为空；不影响其它字段 */
  }
}

async function handleProductChange(value) {
  modal.form.gateOid = undefined
  await loadGates(productOidOf(value))
}

// ==================== 新建 / 编辑 ====================

function emptyForm() {
  return {
    name: '',
    product: undefined,
    gateOid: undefined,
    issueType: undefined,
    severity: 'WARN',
    source: undefined,
    owner: undefined,
    dueDate: undefined,
    relatedType: undefined,
    relatedCode: '',
    description: '',
    rootCause: '',
    resolution: ''
  }
}

const modal = reactive({ visible: false, isEdit: false, saving: false, editingOid: null, form: emptyForm() })

// ==================== 关联对象选择器（模糊搜索，2026-10-25） ====================

/** 关联对象编码选择器（组件实例：选中后用它收下拉） */
const relatedCodeSelectRef = ref()

/** 后端候选（按关键字 + 关联对象类型过滤） */
const relatedObjectRaw = ref([])
const relatedObjectLoading = ref(false)
/**
 * 当前选中 / 回填的那条。
 * 为什么单独留着：列表每次搜索都会换一批，选中项若不在新列表里，
 * 下拉就只剩一个裸编码 —— 名称是用户确认"选的是哪个对象"的唯一依据。
 */
const relatedObjectPicked = ref(null)
const relatedKeyword = ref('')
let relatedObjectSeq = 0
/** 选中候选时会把类型一起写回 —— 那次类型变化不该再触发一次搜索 */
let skipTypeReload = false

/**
 * 候选项 = 选中项 + 本次搜索结果（按编码去重，选中项排第一）。
 *
 * <p>但**正在输入关键字时**不掺选中项：否则"搜不到"的空态永远不出现
 * （永远有一个选中项顶着），用户就看不到「直接用输入的编码」这条出路。
 */
const relatedObjectOptions = computed(() => {
  const picked = relatedObjectPicked.value
  const raw = relatedObjectRaw.value
  if (!picked) return raw
  if (relatedKeyword.value && !raw.some((o) => o.value === picked.value)) return raw
  return [picked, ...raw.filter((o) => o.value !== picked.value)]
})

/** 表单里存的仍是编码字符串（ck_issue.related_code）；选项只负责把名称显示出来 */
const relatedCodeValue = computed({
  get: () => modal.form.relatedCode || undefined,
  set: (v) => { modal.form.relatedCode = v || '' }
})

/** 拉候选：keyword 空 = 各类给最近更新的 20 条（打开下拉就有内容可选） */
async function loadRelatedObjects(keyword = '') {
  const seq = ++relatedObjectSeq
  relatedKeyword.value = keyword
  relatedObjectLoading.value = true
  try {
    const res = await listIssueObjectCandidates({
      keyword: keyword || undefined,
      type: modal.form.relatedType || undefined,
      limit: 20
    })
    if (seq !== relatedObjectSeq) return // 只让最后一次搜索落地
    const rows = (res?.code === 200 && Array.isArray(res.data)) ? res.data : []
    relatedObjectRaw.value = rows
      .filter((r) => r && r.code)
      .map((r) => ({
        value: r.code,
        label: `${r.code}${r.name ? ' ' + r.name : ''}`,
        entityType: r.entityType
      }))
  } catch {
    if (seq === relatedObjectSeq) relatedObjectRaw.value = []
  } finally {
    if (seq === relatedObjectSeq) relatedObjectLoading.value = false
  }
}

function onSearchRelatedObject(keyword) {
  loadRelatedObjects(keyword || '')
}

function onFocusRelatedObject() {
  if (!relatedObjectRaw.value.length) loadRelatedObjects('')
}

/** 选中候选：关联对象类型跟着走（不用再手选一次） */
function onRelatedObjectChange(code) {
  relatedKeyword.value = ''
  const hit = relatedObjectOptions.value.find((o) => o.value === code)
  if (!hit) {
    relatedObjectPicked.value = null
    return
  }
  relatedObjectPicked.value = hit
  if (hit.entityType && hit.entityType !== modal.form.relatedType) {
    skipTypeReload = true
    modal.form.relatedType = hit.entityType
  }
}

/** 搜不到时允许把输入的编码直接当值（BOM / 其他 这类本来就没有候选对象） */
function useTypedCode() {
  if (!relatedKeyword.value) return
  modal.form.relatedCode = relatedKeyword.value
  relatedObjectPicked.value = { value: relatedKeyword.value, label: relatedKeyword.value }
  relatedKeyword.value = ''
  relatedCodeSelectRef.value?.blur?.() // 选完就把下拉收起来，免得挡着下一项
}

/** 编辑回填：先按已存编码搜一次，把名称补上（搜不到就只显示编码） */
async function restoreRelatedObject(record) {
  const code = record.relatedCode || ''
  relatedObjectRaw.value = []
  relatedObjectPicked.value = code ? { value: code, label: code } : null
  if (!code) return
  await loadRelatedObjects(code)
  const hit = relatedObjectRaw.value.find((o) => o.value === code)
  if (hit) relatedObjectPicked.value = hit
}

// 用户手动改类型 → 候选范围跟着变（选中候选引起的那次跳过）
watch(() => modal.form.relatedType, () => {
  if (skipTypeReload) {
    skipTypeReload = false
    return
  }
  if (modal.visible) loadRelatedObjects('')
})

function openCreate() {
  modal.isEdit = false
  modal.editingOid = null
  modal.form = emptyForm()
  gates.value = []
  relatedObjectRaw.value = []
  relatedObjectPicked.value = null
  relatedKeyword.value = ''
  modal.visible = true
}

function openEdit(record) {
  modal.isEdit = true
  modal.editingOid = record.oid
  modal.form = {
    name: record.name || '',
    product: record.productOid ? `${record.productType}:${record.productOid}` : undefined,
    gateOid: record.gateOid || undefined,
    issueType: record.issueType || undefined,
    severity: record.severity || 'WARN',
    source: record.source || undefined,
    owner: record.owner || undefined,
    dueDate: record.dueDate || undefined,
    relatedType: record.relatedType || undefined,
    relatedCode: record.relatedCode || '',
    description: record.description || '',
    rootCause: record.rootCause || '',
    resolution: record.resolution || ''
  }
  modal.visible = true
  // 回填后按当前产品加载门清单（选中值已在表单里，选项到了就显示）
  loadGates(record.productOid || '')
  // 关联对象：把已存编码还原成"编码 + 名称"
  restoreRelatedObject(record)
}

async function handleSave() {
  if (!modal.form.name || !modal.form.name.trim()) {
    message.warning('请填写问题标题')
    return
  }
  if (!modal.form.product) {
    message.warning('请选择关联的产品系列或型号')
    return
  }
  modal.saving = true
  try {
    const [productType, productOid] = String(modal.form.product).split(':')
    const payload = {
      ...modal.form,
      productType,
      productOid,
      gateOid: modal.form.gateOid || '',
      source: modal.form.source || '',
      // 日期不传空串：空串会落到后端的"null = 不改"分支语义模糊，干脆缺席
      dueDate: modal.form.dueDate || undefined
    }
    delete payload.product
    const res = modal.isEdit
      ? await updateIssue(modal.editingOid, payload)
      : await createIssue(payload)
    if (res.code === 200) {
      message.success(modal.isEdit ? '已保存' : `问题已提出（${res.data?.code || ''}）`)
      modal.visible = false
      await refreshAll()
      if (drawer.visible && drawer.issue && drawer.issue.oid === (modal.editingOid || res.data?.oid)) {
        await loadDetail(drawer.issue.oid)
      }
    } else {
      message.error(res.message || '保存失败')
    }
  } catch {
    message.error('保存失败')
  } finally {
    modal.saving = false
  }
}

async function handleDelete(record) {
  try {
    const res = await deleteIssue(record.oid)
    if (res.code === 200) {
      message.success('已删除')
      if (drawer.issue && drawer.issue.oid === record.oid) drawer.visible = false
      await refreshAll()
    } else {
      message.error(res.message || '删除失败')
    }
  } catch {
    message.error('删除失败')
  }
}

// ==================== 详情抽屉 ====================

const drawer = reactive({ visible: false, loading: false, issue: null, activities: [] })
const commentText = ref('')
const commentSaving = ref(false)

const drawerActivities = computed(() => [...drawer.activities].reverse())
const drawerActions = computed(() => {
  if (!drawer.issue) return []
  // 责任人才能推进的动作（开始处理 / 提交验证）对非责任人不出现，避免点了才吃 403
  return availableActions(drawer.issue.status).filter((action) => canOperate(drawer.issue, action))
})
// 终态也允许编辑（2026-10-15 用户反馈）：闭环后常要补填根因 / 处理措施 / 关联对象 ——
// 这些是"记录"不是"状态"；状态 / 关闭时间 / 验证结论后端仍不允许经此改动。
const canEditIssue = computed(() => !!drawer.issue)

async function loadDetail(oid) {
  drawer.loading = true
  try {
    const res = await getIssueDetail(oid)
    if (res.code === 200) {
      drawer.issue = res.data.issue
      drawer.activities = res.data.activities || []
    } else {
      message.error(res.message || '加载详情失败')
    }
  } catch {
    message.error('加载详情失败')
  } finally {
    drawer.loading = false
  }
}

async function openDetail(record) {
  drawer.visible = true
  drawer.issue = record
  drawer.activities = []
  commentText.value = ''
  await loadDetail(record.oid)
}

async function handleAddComment() {
  if (!commentText.value || !commentText.value.trim()) {
    message.warning('请先填写备注内容')
    return
  }
  commentSaving.value = true
  try {
    const res = await addIssueComment(drawer.issue.oid, commentText.value.trim())
    if (res.code === 200) {
      commentText.value = ''
      message.success('已追加')
      await loadDetail(drawer.issue.oid)
    } else {
      message.error(res.message || '追加失败')
    }
  } catch {
    message.error('追加失败')
  } finally {
    commentSaving.value = false
  }
}

// ==================== 状态流转 ====================

const flow = reactive({
  visible: false, saving: false, issue: null, action: null,
  comment: '', verifyResult: '', rootCause: '', resolution: ''
})

function openTransition(record, action) {
  if (requiresComment(action) || requiresVerifyResult(action) || action === 'SUBMIT_VERIFY') {
    flow.issue = record
    flow.action = action
    flow.comment = ''
    flow.verifyResult = ''
    // 根因 / 措施预填现值：本来就是"接着往下写"，不该让人重抄
    flow.rootCause = record.rootCause || ''
    flow.resolution = record.resolution || ''
    flow.visible = true
  } else {
    submitFlow(record, action, '', '')
  }
}

async function handleFlow() {
  if (requiresRootCause(flow.action) && !flow.rootCause.trim()) {
    message.warning('提交验证必须填写根因分析')
    return
  }
  if (requiresResolution(flow.action) && !flow.resolution.trim()) {
    message.warning('提交验证必须填写处理措施')
    return
  }
  if (requiresComment(flow.action) && !flow.comment.trim()) {
    message.warning(flowCommentHint(flow.action))
    return
  }
  if (requiresVerifyResult(flow.action) && !flow.verifyResult.trim()) {
    message.warning('请填写验证结论')
    return
  }
  await submitFlow(flow.issue, flow.action, flow.comment.trim(), flow.verifyResult.trim(),
    flow.rootCause.trim(), flow.resolution.trim())
}

async function submitFlow(record, action, comment, verifyResult, rootCause = '', resolution = '') {
  flow.saving = true
  try {
    const res = await transitionIssue(record.oid, { action, comment, verifyResult, rootCause, resolution })
    if (res.code === 200) {
      message.success(`${ISSUE_ACTION_LABEL[action] || action}完成`)
      flow.visible = false
      await refreshAll()
      if (drawer.visible && drawer.issue && drawer.issue.oid === record.oid) {
        await loadDetail(record.oid)
      }
    } else {
      message.error(res.message || '操作失败')
    }
  } catch {
    message.error('操作失败')
  } finally {
    flow.saving = false
  }
}

function flowCommentLabel(action) {
  if (action === 'VERIFY_FAIL') return '退回原因'
  if (action === 'CANCEL') return '取消原因'
  if (action === 'REOPEN') return '重开原因'
  return '说明'
}

function flowCommentHint(action) {
  if (action === 'VERIFY_FAIL') return '请填写退回原因'
  if (action === 'CANCEL') return '请填写取消原因'
  if (action === 'REOPEN') return '请填写重开原因'
  return '请填写说明'
}

function flowCommentPlaceholder(action) {
  if (action === 'VERIFY_FAIL') return '哪里没过、需要怎么补（退回后回到处理中）'
  if (action === 'CANCEL') return '为什么不做（确认不推进的原因）'
  if (action === 'REOPEN') return '为什么重开（复现情况 / 新证据）'
  return '补充说明（可选）'
}

function actionKind(action) {
  return ['START', 'SUBMIT_VERIFY', 'VERIFY_PASS'].includes(action) ? 'primary' : 'default'
}

function actionDanger(action) {
  return action === 'VERIFY_FAIL' || action === 'CANCEL'
}

function actionColor(action) {
  if (action === 'VERIFY_PASS') return 'green'
  if (action === 'VERIFY_FAIL' || action === 'CANCEL') return 'red'
  if (action === 'REOPEN') return 'orange'
  if (action === 'CREATE') return 'blue'
  return 'gray'
}

// ==================== 小工具 ====================

function isOverdue(record) {
  if (!record.dueDate) return false
  if (record.status === 'CLOSED' || record.status === 'CANCELLED') return false
  const today = new Date()
  const todayStr = `${today.getFullYear()}-${String(today.getMonth() + 1).padStart(2, '0')}-${String(today.getDate()).padStart(2, '0')}`
  return String(record.dueDate) < todayStr
}

function formatDateTime(value) {
  if (!value) return '—'
  const s = String(value).replace('T', ' ')
  return s.length >= 16 ? s.slice(5, 16) : s
}

/** 深链入口：对象详情页「关联问题」点编码跳过来时带 ?issue=<oid> */
const route = useRoute()

onMounted(() => {
  refreshAll()
  loadUsers()
  loadTypes()
  loadProducts()
  loadCapabilities()
  // 带 ?issue= 就直奔那条问题的详情抽屉，不让用户在台账里再找一遍
  const issueOid = route.query.issue
  if (issueOid) openDetail({ oid: String(issueOid) })
})
</script>

<style scoped>
.ci-page { padding: 4px 0; }

.ci-stats { margin-bottom: 12px; }

.ci-stat-card { cursor: pointer; }
.ci-stat-card--active { border-color: #1677ff; box-shadow: 0 0 0 1px #1677ff inset; }

.ci-title-link { color: inherit; }
.ci-title-link:hover { color: #1677ff; }

.ci-product { display: flex; align-items: center; gap: 6px; }
.ci-gate {
  background: #f0f5ff;
  border: 1px solid #adc6ff;
  color: #2f54eb;
  border-radius: 3px;
  padding: 0 4px;
  font-size: 11px;
  white-space: nowrap;
  cursor: default;
}

.ci-overdue { color: #cf1322; font-weight: 600; }
.ci-overdue-icon { margin-left: 4px; color: #cf1322; }

.ci-flow-actions {
  display: flex;
  gap: 8px;
  flex-wrap: wrap;
  margin-bottom: 16px;
}

.ci-desc { margin-bottom: 16px; }

.ci-pre {
  white-space: pre-wrap;
  word-break: break-word;
  max-height: 140px;
  overflow-y: auto;
}

/* 关联对象下拉的"没有匹配"：给一条"直接用输入的编码" */
.ci-pick-empty {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 4px 0;
  font-size: 13px;
  color: #8c8c8c;
}
.ci-pick-empty a {
  cursor: pointer;
}

.ci-comment-box {
  display: flex;
  gap: 8px;
  align-items: flex-end;
  margin-bottom: 8px;
}

.ci-comment-box :deep(.ant-input) { flex: 1; }

.ci-divider { margin: 12px 0; }

.ci-timeline { margin-top: 8px; }

.ci-act-head { display: flex; align-items: center; gap: 8px; flex-wrap: wrap; }
.ci-act-flow { color: #595959; font-size: 12px; }
.ci-act-time { color: #8c8c8c; font-size: 12px; margin-left: auto; }
.ci-act-op { color: #8c8c8c; font-size: 12px; }
.ci-act-comment {
  margin-top: 4px;
  white-space: pre-wrap;
  word-break: break-word;
  color: #262626;
}
</style>
