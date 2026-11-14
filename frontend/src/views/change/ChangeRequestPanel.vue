<template>
  <div class="chg-panel">
    <!-- 口径条（与产品页「物料清单」同一版式）：左边说清这段列表是什么，右边给条数 -->
    <div class="ck-list-bar">
      <span>本企业全部变更请求（ECR）：一行一张单，评审中的结论由流程回写</span>
      <span class="ck-list-bar__count">共 {{ total }} 条</span>
    </div>
    <DataTable
      scroll-x="max-content"
      :columns="columns"
      :data-source="rows"
      :loading="loading"
      pagination-mode="backend"
      :pagination="{ current: page, pageSize }"
      :total="total"
      search-placeholder="搜索编号 / 标题 / 变更原因..."
      :search-fields="['code', 'name', 'reason']"
      row-key="oid"
      size="small"
      empty-text="暂无变更请求"
      @change="handleTableChange"
      @update:search="handleSearch"
    >
      <!-- 状态筛选在页头（ChangeControl 的状态概览标签）；主按钮留在右侧与搜索、视图同排 -->
      <template #toolbar>
        <a-button type="primary" size="small" @click="openCreate">
          <template #icon><PlusOutlined /></template>
          提变更请求
        </a-button>
      </template>

      <template #bodyCell="{ column, record }">
        <template v-if="column.key === 'code'">
          <a-tag color="blue" size="small">{{ record.code }}</a-tag>
        </template>
        <template v-else-if="column.key === 'name'">
          <a class="chg-link" @click="openDetail(record)">{{ record.name }}</a>
        </template>
        <template v-else-if="column.key === 'changeType'">
          <span>{{ CHANGE_TYPE_LABEL[record.changeType] || '—' }}</span>
        </template>
        <template v-else-if="column.key === 'changeLevel'">
          <a-tag :color="CHANGE_LEVEL_COLOR[record.changeLevel] || 'default'" size="small">
            {{ CHANGE_LEVEL_LABEL[record.changeLevel] || '标准' }}
          </a-tag>
        </template>
        <template v-else-if="column.key === 'priority'">
          <a-tag :color="PRIORITY_COLOR[record.priority] || 'default'" size="small">
            {{ PRIORITY_LABEL[record.priority] || record.priority || '—' }}
          </a-tag>
        </template>
        <template v-else-if="column.key === 'status'">
          <a-tag :color="CHANGE_REQUEST_STATUS_COLOR[record.status] || 'default'" size="small">
            {{ CHANGE_REQUEST_STATUS_LABEL[record.status] || record.status }}
          </a-tag>
        </template>
        <template v-else-if="column.key === 'action'">
          <a-space size="small">
            <!-- 详情对所有人开放；其余动作只有责任人可点。disabled 的按钮不触发鼠标事件，
                 所以外面套一层 span 让 tooltip 还能弹出"为什么不能点" -->
            <a-button type="link" size="small" @click="openDetail(record)">详情</a-button>
            <!-- 流程驱动的单：状态由流程推进，单据上刻意不给"改状态"的入口（给了就会和流程打架）。
                 但"评审跑到哪了、该谁办"必须点得到 —— 有待办直接去办理，没有就看流程详情 -->
            <a-button v-if="todoTaskOf(record)" type="link" size="small" @click="goTask(record)">
              去办理
            </a-button>
            <a-button
              v-else-if="record.processInstanceId"
              type="link"
              size="small"
              @click="goProcess(record)"
            >
              流程
            </a-button>
            <!-- 编辑入口在列表上：详情抽屉只读，避免"看着详情改数据"时看不出改了哪一版 -->
            <a-tooltip
              v-if="availableRequestActions(record.status).length"
              :title="isOwner(record) ? undefined : ownerHint(record)"
            >
              <span>
                <a-button
                  type="link"
                  size="small"
                  :disabled="!isOwner(record)"
                  @click="openEdit(record)"
                >
                  编辑
                </a-button>
              </span>
            </a-tooltip>
            <a-tooltip
              v-if="primaryActionOf(record)"
              :title="isOwner(record) ? undefined : ownerHint(record)"
            >
              <span>
                <a-button
                  type="link"
                  size="small"
                  :disabled="!isOwner(record)"
                  @click="openTransition(record, primaryActionOf(record))"
                >
                  {{ CHANGE_REQUEST_ACTION_LABEL[primaryActionOf(record)] }}
                </a-button>
              </span>
            </a-tooltip>
            <!-- 第②段：草稿时可以先去做影响分析（不是必经，发起评审仍可直接点） -->
            <a-tooltip
              v-if="actionsOf(record).includes('ANALYZE')"
              :title="isOwner(record) ? undefined : ownerHint(record)"
            >
              <span>
                <a-button
                  type="link"
                  size="small"
                  :disabled="!isOwner(record)"
                  @click="openTransition(record, 'ANALYZE')"
                >
                  {{ CHANGE_REQUEST_ACTION_LABEL.ANALYZE }}
                </a-button>
              </span>
            </a-tooltip>
            <a-tooltip
              v-if="record.status === 'REVIEWING' && !record.processInstanceId"
              :title="isOwner(record) ? undefined : ownerHint(record)"
            >
              <span>
                <a-button
                  type="link"
                  size="small"
                  danger
                  :disabled="!isOwner(record)"
                  @click="openTransition(record, 'REJECT')"
                >
                  否决
                </a-button>
              </span>
            </a-tooltip>
            <a-popconfirm
              v-if="record.status === 'DRAFT'"
              :title="isOwner(record) ? '确定删除该变更请求？时间线会一并删除' : ownerHint(record)"
              :ok-button-props="{ disabled: !isOwner(record) }"
              :cancel-button-props="{ disabled: !isOwner(record) }"
              @confirm="handleDelete(record)"
            >
              <a-button type="link" size="small" danger :disabled="!isOwner(record)">删除</a-button>
            </a-popconfirm>
          </a-space>
        </template>
      </template>
    </DataTable>

    <!-- 新建 / 编辑 -->
    <a-modal
      v-model:open="modal.visible"
      :title="modal.isEdit ? '编辑变更请求' : '提出变更请求'"
      :confirm-loading="modal.saving"
      :width="formWidth"
      centered
      ok-text="保存"
      cancel-text="取消"
      :body-style="{ maxHeight: 'calc(100vh - 200px)', overflowY: 'auto' }"
      @ok="handleSave"
    >
      <DynamicForm
        ref="formRef"
        v-model="modal.form"
        entity-code="CHANGE_REQUEST"
        :operation-code="modal.isEdit ? 'update' : 'create'"
        :widget-options="{ issueOptions: modal.seedIssues }"
        skip-resource-loading
      />
    </a-modal>

    <!-- 流转 -->
    <a-modal
      v-model:open="flow.visible"
      :title="flow.action ? CHANGE_REQUEST_ACTION_LABEL[flow.action] : '流转'"
      :confirm-loading="flow.saving"
      :ok-button-props="{ disabled: flowConfirmDisabled }"
      width="680px"
      centered
      ok-text="确定"
      cancel-text="取消"
      @ok="handleFlow"
    >
      <!-- 关闭：子通告没闭环时直接把差额说清楚，而不是让人点完确定再被退回 -->
      <a-alert
        v-if="flow.action === 'CLOSE'"
        :type="flowCloseBlockReason ? 'warning' : 'info'"
        show-icon
        class="chg-flow-hint"
        :message="flowCloseBlockReason || '关闭前需先派生本请求的变更通告；一张通告都没有会被退回，派生的通告也必须全部关闭（已关闭 / 已取消）'"
      />
      <!-- 发起评审：单据结论条 + 流程信息网格（与 ECN「提交审批」共用 ChangeFlowInfoPanel）——
           用户要判断的是同一件事：走哪条流程、部署没部署。以前只有一句"将走某流程评审"，
           流程 key 配错或模板没部署，要等点完确定才被后端退回 -->
      <ChangeFlowInfoPanel
        v-else-if="isReviewSubmit"
        :record="flow.record"
        :status-label="CHANGE_REQUEST_STATUS_LABEL[flow.record?.status] || flow.record?.status || ''"
        :status-color="CHANGE_REQUEST_STATUS_COLOR[flow.record?.status] || 'default'"
        :tags="reviewTags"
        :flow="approvalFlow"
        :blocked="reviewFlowBlocked"
        :block-reason="reviewFlowBlockReason"
        :block-hint="reviewFlowBlockHint"
        :info-message="reviewFlowInfo"
      />
      <!-- 免审：提交即受理，原因必须写（与后端审计口径一致） -->
      <a-alert
        v-else-if="flow.action === 'SUBMIT'"
        type="warning"
        show-icon
        class="chg-flow-hint"
        message="本租户已开启免审：提交即受理，不必走评审流程；填写的说明会记入时间线"
      />
      <a-alert
        v-else-if="flow.action === 'ANALYZE'"
        type="info"
        show-icon
        class="chg-flow-hint"
        message="接下来补「认证/客户/在制库存影响」，评估完再发起评审；快速变更可直接发起，不必停在这一步"
      />
      <a-form layout="vertical">
        <a-form-item
          label="说明"
          :required="requestRequiresComment(flow.action)"
        >
          <a-textarea
            v-model:value="flow.comment"
            :rows="3"
            :maxlength="2000"
            :placeholder="requestRequiresComment(flow.action) ? '请说明原因（会记入时间线）' : '可选'"
          />
        </a-form-item>
      </a-form>
    </a-modal>

    <!-- 详情 -->
    <a-drawer v-model:open="drawer.visible" title="变更请求" width="620" :body-style="{ padding: '16px 20px' }">
      <a-spin :spinning="drawer.loading">
        <template v-if="drawer.request">
          <div class="chg-drawer-head">
            <div>
              <a-tag color="blue">{{ drawer.request.code }}</a-tag>
              <b class="chg-drawer-title">{{ drawer.request.name }}</b>
            </div>
          </div>

          <a-descriptions :column="2" size="small" bordered>
            <a-descriptions-item label="状态">
              <a-tag :color="CHANGE_REQUEST_STATUS_COLOR[drawer.request.status] || 'default'">
                {{ CHANGE_REQUEST_STATUS_LABEL[drawer.request.status] || drawer.request.status }}
              </a-tag>
            </a-descriptions-item>
            <a-descriptions-item label="类型">
              {{ CHANGE_TYPE_LABEL[drawer.request.changeType] || '—' }}
            </a-descriptions-item>
            <a-descriptions-item label="变更分级">
              <a-tooltip title="分级决定审签路径：快速=单人审批；标准=会签+产品经理；重大=会签+CCB">
                <a-tag :color="CHANGE_LEVEL_COLOR[drawer.request.changeLevel] || 'default'">
                  {{ CHANGE_LEVEL_LABEL[drawer.request.changeLevel] || '标准' }}
                </a-tag>
              </a-tooltip>
            </a-descriptions-item>
            <a-descriptions-item label="优先级">
              <a-tag :color="PRIORITY_COLOR[drawer.request.priority] || 'default'">
                {{ PRIORITY_LABEL[drawer.request.priority] || '—' }}
              </a-tag>
            </a-descriptions-item>
            <a-descriptions-item label="计划完成">{{ drawer.request.dueDate || '—' }}</a-descriptions-item>
            <a-descriptions-item label="责任人">{{ drawer.request.owner || '—' }}</a-descriptions-item>
            <a-descriptions-item label="提出人">{{ drawer.request.requester || '—' }}</a-descriptions-item>
            <a-descriptions-item label="评审流程">
              <a
                v-if="drawer.request.processInstanceId"
                class="chg-link"
                @click="openProcessInstance(drawer.request.processInstanceId)"
              >查看流程</a>
              <span v-else class="chg-muted">未发起</span>
            </a-descriptions-item>
            <a-descriptions-item label="关联问题" :span="2">
              <a-space v-if="drawer.issues.length" wrap size="small">
                <a-tag v-for="it in drawer.issues" :key="it.oid" color="orange">
                  {{ it.code }} {{ it.name }}
                </a-tag>
              </a-space>
              <span v-else class="chg-muted">—</span>
            </a-descriptions-item>
            <a-descriptions-item label="预估受影响对象" :span="2">
              <a-space v-if="drawer.affectedObjects.length" wrap size="small">
                <a-tag v-for="obj in drawer.affectedObjects" :key="obj.oid" color="blue">
                  {{ entityTypeLabel(obj.entityType) }} · {{ obj.entityCode }} {{ obj.entityName }}
                  <span v-if="obj.containerName" class="chg-muted">（{{ obj.containerName }}）</span>
                </a-tag>
              </a-space>
              <span v-else class="chg-muted">—</span>
            </a-descriptions-item>
            <a-descriptions-item label="影响分析" :span="2">
              <a-space size="large" wrap>
                <span>认证/合规：{{ CERT_IMPACT_LABEL[drawer.request.certImpact] || '未评估' }}</span>
                <span>客户/合同：{{ CUSTOMER_IMPACT_LABEL[drawer.request.customerImpact] || '未评估' }}</span>
                <span>在制/库存：{{ INVENTORY_IMPACT_LABEL[drawer.request.inventoryImpact] || '未评估' }}</span>
              </a-space>
            </a-descriptions-item>
            <a-descriptions-item label="变更原因" :span="2">
              <div class="chg-pre chg-rich" v-html="drawerRich.reason"></div>
            </a-descriptions-item>
            <a-descriptions-item label="建议方案" :span="2">
              <div class="chg-pre chg-rich" v-html="drawerRich.proposal"></div>
            </a-descriptions-item>
            <a-descriptions-item label="补充说明" :span="2">
              <div class="chg-pre chg-rich" v-html="drawerRich.description"></div>
            </a-descriptions-item>
            <a-descriptions-item label="补充材料" :span="2">
              <a
                v-if="drawer.attachment"
                :href="getAttachmentDownloadUrl(drawer.attachment.oid)"
                target="_blank"
                rel="noopener"
              >
                {{ drawer.attachment.fileName }}
              </a>
              <span v-else class="chg-muted">—</span>
            </a-descriptions-item>
          </a-descriptions>

          <a-space v-if="actionsOf(drawer.request).length" class="chg-drawer-actions">
            <a-tooltip
              v-for="action in actionsOf(drawer.request)"
              :key="action"
              :title="drawerActionHint(action)"
            >
              <span>
                <a-button
                  :type="action === 'CANCEL' || action === 'REJECT' ? 'default' : 'primary'"
                  :danger="action === 'CANCEL' || action === 'REJECT'"
                  size="small"
                  :disabled="drawerActionDisabled(action)"
                  @click="openTransition(drawer.request, action)"
                >
                  {{ CHANGE_REQUEST_ACTION_LABEL[action] }}
                </a-button>
              </span>
            </a-tooltip>
          </a-space>

          <!-- 子流程：本请求派生出的变更通告 -->
          <a-divider orientation="left" class="chg-divider">
            变更通告（子流程，{{ drawer.notices.length }}）
          </a-divider>
          <a-empty
            v-if="!drawer.notices.length"
            description="尚未派生变更通告"
            :image-style="{ height: '40px' }"
          />
          <a-table
            v-else
            :columns="noticeColumns"
            :data-source="drawer.notices"
            :pagination="false"
            row-key="oid"
            size="small"
          >
            <template #bodyCell="{ column, record }">
              <template v-if="column.key === 'code'">
                <a class="chg-link" @click="openNoticeDetail(record)">{{ record.code }}</a>
              </template>
              <template v-else-if="column.key === 'status'">
                <a-tag :color="CHANGE_NOTICE_STATUS_COLOR[record.status] || 'default'">
                  {{ CHANGE_NOTICE_STATUS_LABEL[record.status] || record.status }}
                </a-tag>
              </template>
              <template v-else-if="column.key === 'process'">
                <a v-if="record.processInstanceId" class="chg-link" @click="openProcessInstance(record.processInstanceId)">查看流程</a>
                <span v-else class="chg-muted">未发起</span>
              </template>
            </template>
          </a-table>
          <div class="chg-hint">
            关闭变更请求前，必须先派生本请求的变更通告，并确保这些通告全部关闭（已关闭 / 已取消）。
          </div>

          <!-- 再下一层：通告派出来的变更任务。没有这一段，想知道"谁在改、改到哪"得逐张点进通告 -->
          <template v-if="drawer.notices.length">
            <a-divider orientation="left" class="chg-divider">
              变更任务（{{ drawer.tasks.length }}）
            </a-divider>
            <a-empty
              v-if="!drawer.tasks.length"
              description="这些通告下还没有变更任务"
              :image-style="{ height: '40px' }"
            />
            <a-table
              v-else
              :columns="taskColumns"
              :data-source="drawer.tasks"
              :pagination="false"
              row-key="oid"
              size="small"
            >
              <template #bodyCell="{ column, record }">
                <template v-if="column.key === 'code'">
                  <a-tag color="cyan" size="small">{{ record.code }}</a-tag>
                </template>
                <template v-else-if="column.key === 'notice'">
                  <span>{{ noticeCodeOf(record.noticeOid) }}</span>
                </template>
                <template v-else-if="column.key === 'status'">
                  <a-tag :color="CHANGE_TASK_STATUS_COLOR[record.status] || 'default'">
                    {{ CHANGE_TASK_STATUS_LABEL[record.status] || record.status }}
                  </a-tag>
                </template>
                <template v-else-if="column.key === 'assignee'">
                  <span :class="{ 'chg-muted': !record.assignee }">{{ record.assignee || '未指派' }}</span>
                </template>
                <template v-else-if="column.key === 'process'">
                  <a
                    v-if="record.processInstanceId"
                    class="chg-link"
                    @click="openProcessInstance(record.processInstanceId)"
                  >查看流程</a>
                  <span v-else class="chg-muted">未发起</span>
                </template>
              </template>
            </a-table>
            <div class="chg-hint">
              变更任务由变更通告派发，执行人按任务下的受影响对象做修订；状态在「我的任务」页签里推进。
            </div>
          </template>

          <a-divider orientation="left" class="chg-divider">处理记录</a-divider>
          <a-empty v-if="!drawer.activities.length" description="暂无记录" :image-style="{ height: '48px' }" />
          <a-timeline v-else>
            <a-timeline-item v-for="act in drawer.activities" :key="act.oid">
              <div class="chg-act-head">
                <b>{{ CHANGE_REQUEST_ACTION_LABEL[act.action] || act.action }}</b>
                <span v-if="act.fromStatus && act.toStatus" class="chg-act-flow">
                  {{ CHANGE_REQUEST_STATUS_LABEL[act.fromStatus] || act.fromStatus }}
                  →
                  {{ CHANGE_REQUEST_STATUS_LABEL[act.toStatus] || act.toStatus }}
                </span>
                <span class="chg-act-time">{{ formatDateTime(act.createdAt) }}</span>
              </div>
              <div class="chg-act-op">{{ act.operator || act.creator || '—' }}</div>
              <div v-if="act.comment" class="chg-act-comment">{{ act.comment }}</div>
            </a-timeline-item>
          </a-timeline>
        </template>
      </a-spin>
    </a-drawer>
  </div>
</template>

<script setup>
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { message } from 'ant-design-vue'
import { PlusOutlined } from '@ant-design/icons-vue'
import { useRoute, useRouter } from 'vue-router'
import DataTable from '@/components/DataTable.vue'
import DynamicForm from '@/components/DynamicForm.vue'
import { useUserStore } from '@/stores/user'
import ChangeFlowInfoPanel from './ChangeFlowInfoPanel.vue'
import {
  createChangeRequest,
  deleteChangeRequest,
  getAttachment,
  getAttachmentDownloadUrl,
  getChangeConfig,
  getChangeRequest,
  getMyTodoTasks,
  getProcessTemplateVersion,
  listChangeRequests,
  listProcessTemplates,
  transitionChangeRequest,
  updateChangeRequest
} from '@/api'
import {
  CERT_IMPACT_LABEL,
  CHANGE_LEVEL_COLOR,
  CHANGE_LEVEL_LABEL,
  CHANGE_NOTICE_STATUS_COLOR,
  CHANGE_NOTICE_STATUS_LABEL,
  CHANGE_REQUEST_ACTION_LABEL,
  CHANGE_REQUEST_STATUS_COLOR,
  CHANGE_REQUEST_STATUS_LABEL,
  CHANGE_TASK_STATUS_COLOR,
  CHANGE_TASK_STATUS_LABEL,
  CHANGE_TYPE_LABEL,
  CUSTOMER_IMPACT_LABEL,
  INVENTORY_IMPACT_LABEL,
  PRIORITY_COLOR,
  PRIORITY_LABEL,
  availableRequestActions,
  noticeClosed,
  primaryRequestAction,
  requestRequiresComment
} from '@/utils/changeFlow'
import { sanitizeHtml } from '@/utils/html'

const userStore = useUserStore()
const currentUsername = computed(() => userStore.username || '')
const route = useRoute()
const router = useRouter()

/**
 * 除「详情」外，变更请求的所有动作都只归责任人。
 *
 * <p>与后端 `ChangeRequestFlow.requiresOwner` + `ChangeRequestServiceImpl#assertOwner` 同口径：
 * owner 为空时退回 requester —— 表单里责任人可以不填，只认 owner 会让这类单据永远推不动。
 *
 * <p>前端只负责"按钮不可点"，真正的拦截在后端（改个请求就绕过前端了）。
 */
function isOwner(record) {
  const owner = record?.owner || record?.requester || ''
  return !!owner && owner === currentUsername.value
}

function ownerHint(record) {
  const owner = record?.owner || record?.requester
  return owner ? `只有责任人「${owner}」可以执行` : '未指派责任人，无法执行'
}

const columns = [
  { title: '编号', dataIndex: 'code', key: 'code', width: 110 },
  { title: '标题', dataIndex: 'name', key: 'name', width: 240 },
  { title: '类型', key: 'changeType', width: 100 },
  // 分级决定审签路径，放在列表上让人一眼看出这张单要走几道会签
  { title: '分级', key: 'changeLevel', width: 90 },
  { title: '优先级', key: 'priority', width: 80 },
  { title: '状态', key: 'status', width: 90 },
  { title: '责任人', dataIndex: 'owner', key: 'owner', width: 100 },
  { title: '提出人', dataIndex: 'requester', key: 'requester', width: 100 },
  { title: '计划完成', dataIndex: 'dueDate', key: 'dueDate', width: 110 },
  { title: '操作', key: 'action', width: 250, fixed: 'right' }
]

/** 详情抽屉里的子流程（变更通告）列表 */
const noticeColumns = [
  { title: '编号', key: 'code', width: 110 },
  { title: '标题', dataIndex: 'name', key: 'name' },
  { title: '状态', key: 'status', width: 100 },
  // 流程列：通告挂的审批实例（process_instance_id）。没有它就只能看到"审批中"三个字，
  // 想知道走到哪个节点、谁在办，得自己去流程监控里翻 —— 这里直接给入口。
  { title: '流程', key: 'process', width: 90 },
  { title: '责任人', dataIndex: 'owner', key: 'owner', width: 100 }
]

/**
 * 详情抽屉里的变更任务（ECT）列表 —— 通告的子流程。
 *
 * 列宽合计控制在一屏内：抽屉本来就窄，再加就只剩编号看得见。
 */
const taskColumns = [
  { title: '编号', key: 'code', width: 110 },
  { title: '标题', dataIndex: 'name', key: 'name' },
  // 归属通告：本请求可能派生多张 ECN，任务不标归属就分不清是给哪张单干的活
  { title: '变更通告', key: 'notice', width: 100 },
  { title: '状态', key: 'status', width: 90 },
  { title: '责任人', dataIndex: 'assignee', key: 'assignee', width: 90 },
  { title: '流程', key: 'process', width: 80 }
]

const loading = ref(false)
const rows = ref([])
const total = ref(0)
const page = ref(1)
const pageSize = ref(20)

/**
 * 状态筛选由**页头**的状态概览控制（ChangeControl 的胶囊标签）：本面板只按父级给的状态取数；
 * 列表一变就回 `changed`，让父级重取各状态计数。
 */
const props = defineProps({ status: { type: String, default: undefined } })
const emit = defineEmits(['update:status', 'changed'])

const filters = reactive({ status: props.status || undefined, keyword: '' })

/** 页头换了筛选（或清空）：同步内部状态并回到第一页重取 */
watch(() => props.status, (value) => {
  const next = value || undefined
  if (next === (filters.status || undefined)) return
  filters.status = next
  page.value = 1
  loadList()
})

/** 预估对象类型文案（与 ReviseProvider 白名单同口径） */
const OBJECT_TYPE_LABEL = { PART: '零件', DOCUMENT: '文档', ENG_DOCUMENT: '工程文档' }

function entityTypeLabel(type) {
  return OBJECT_TYPE_LABEL[type] || type || '对象'
}

/**
 * 租户变更配置：审批开关与 request 流程 key。
 *
 * <p>只用于「发起评审」弹窗 —— 真正的分支判断在后端，开关读不到时按默认（需要审批）展示，
 * 避免提示与实际相反。
 */
const changeConfig = ref({
  approvalRequired: true,
  requestProcessKey: 'change-request'
})

async function loadConfig() {
  try {
    const res = await getChangeConfig()
    if (res.code === 200 && res.data) changeConfig.value = { ...changeConfig.value, ...res.data }
  } catch {
    /* 读不到按默认值提示 */
  }
}

/**
 * 评审流程（「发起评审」真正拉起的那条）。
 *
 * <p>与 ECN「提交审批」同一口径：按配置里的 {@code requestProcessKey} 去流程清单查一次，
 * 把名称 / 版本 / 部署状态 / 说明摆进弹窗。key 配错或模板没部署，以前要等点完确定
 * 才被后端退回，用户拿到的只是一句"发起失败"。
 */
const approvalFlow = reactive({
  loading: false,
  /** 查过一次（无论是否命中）：查不到时说"未在流程清单里找到"，不再重复请求 */
  loaded: false,
  found: false,
  key: '',
  name: '',
  description: '',
  latestVersion: null,
  latestCreatedAt: '',
  deployedVersion: null,
  /** 最新版本的变更说明（要按版本号再查一次，取不到就空着） */
  changeNote: ''
})

/**
 * 查一次「发起评审会拉起的那条流程」。
 *
 * <p>每次都重查：用户可能刚在「流程清单」里部署完就回来发起，缓存会让弹窗仍显示"尚未部署"。
 * 查不到不编造；只有拿到确切结论时才禁用确定。
 */
async function loadApprovalFlow() {
  if (approvalFlow.loading) return
  approvalFlow.loading = true
  const key = changeConfig.value.requestProcessKey
  // 每次重查先清掉上一次结论：流程可能刚被删/换 key，留着旧值会显示"已部署"却发起失败
  Object.assign(approvalFlow, {
    key,
    found: false,
    name: '',
    description: '',
    latestVersion: null,
    latestCreatedAt: '',
    deployedVersion: null,
    changeNote: ''
  })
  try {
    if (!key) return
    const res = await listProcessTemplates({})
    const hit = res?.code === 200 ? (res.data || []).find((item) => item.key === key) : null
    if (!hit) return
    Object.assign(approvalFlow, {
      found: true,
      name: hit.displayName || hit.name || key,
      description: hit.description || '',
      latestVersion: hit.latestVersion ?? null,
      deployedVersion: hit.deployedVersion ?? null
    })
    // 变更说明长在版本行上：单独取一次最新版（取不到只是少一行，不影响发起）
    if (hit.latestVersion) {
      const ver = await getProcessTemplateVersion(hit.oid, hit.latestVersion).catch(() => null)
      if (ver?.code === 200 && ver.data) {
        approvalFlow.changeNote = ver.data.changeNote || ''
        approvalFlow.latestCreatedAt = String(ver.data.createdAt || '').substring(0, 19).replace('T', ' ')
      }
    }
  } catch {
    /* 查不到就不拦：真正的判定在后端 */
  } finally {
    approvalFlow.loaded = true
    approvalFlow.loading = false
  }
}

/** 本次流转是否会拉起评审流程：开了审批的提交才算（免审提交直接受理） */
const isReviewSubmit = computed(() => flow.action === 'SUBMIT' && changeConfig.value.approvalRequired)

/** 弹窗里的变更级别标签：分流依据，和流程信息一起看才解释得通 */
const reviewTags = computed(() => {
  const level = flow.record?.changeLevel
  if (!level) return []
  return [{
    text: `变更级别：${CHANGE_LEVEL_LABEL[level] || level}`,
    color: CHANGE_LEVEL_COLOR[level] || 'default'
  }]
})

/**
 * 现在发起必然被退回的原因（空串 = 可以发起）。
 *
 * <p>三档都说清楚：没配 key / 流程清单里没这条 / 有但没部署 —— 以前都要等后端抛错才知道。
 */
const reviewFlowBlockReason = computed(() => {
  // 正在查的时候什么都不判断：否则每次重开弹窗都会先闪一句"找不到流程"
  if (!isReviewSubmit.value || !approvalFlow.loaded || approvalFlow.loading) return ''
  if (!approvalFlow.key) return '未配置评审流程：请到「变更控制 → 设置」填写评审流程 key，或开启免审'
  if (!approvalFlow.found) return `流程清单里找不到「${approvalFlow.key}」这条流程，发起会被退回`
  if (!approvalFlow.deployedVersion) return '这条评审流程还没有部署，现在发起会被退回'
  return ''
})

const reviewFlowBlocked = computed(() => !!reviewFlowBlockReason.value)

/** 不能发起时补一句"去哪儿修"（光说不能发起，用户还是找不到出口） */
const reviewFlowBlockHint = computed(() => {
  if (!reviewFlowBlocked.value) return ''
  if (approvalFlow.key && approvalFlow.found) {
    return '请先在「流程清单」里部署该流程；或到「变更控制 → 设置」把评审流程换成已部署的那条。'
  }
  return '流程模板可在「业务配置 → 流程模板」查看；未配置流程也可以到「变更控制 → 设置」开启免审。'
})

/** 可以发起时的一句话：按什么分级分流、去哪儿办、结束之后本单变成什么 */
const reviewFlowInfo = computed(() => {
  const level = CHANGE_LEVEL_LABEL[flow.record?.changeLevel] || '标准'
  const path =
    {
      QUICK: '单人审批',
      STANDARD: '影响分析会签 → 评审决策',
      MAJOR: '影响分析会签 → CCB 决策会签'
    }[flow.record?.changeLevel] || '影响分析会签 → 评审决策'
  return `提交后立即发起评审流程：按「${level}」变更分流到「${path}」；评审待办在「任务中心」办理，结束后本单状态自动更新为「已受理」或「已否决」`
})

/**
 * 单据上可用的动作。
 *
 * <p>挂了评审流程的请求，受理/否决由流程回写（见后端
 * ChangeRequestReviewOutcomeHandler），单据上不再给按钮 ——
 * 否则会出现"流程还没批完，单据已被人工受理"。
 */
function actionsOf(record) {
  const actions = availableRequestActions(record.status)
  // 开启审批时，影响分析是评审流程里的一步（影响分析会签），单据上不再给人工入口 ——
  // 后端 transition 同样会拒绝 ANALYZE（见 ChangeRequestServiceImpl）。免审才保留按钮。
  const visible = changeConfig.value.approvalRequired
    ? actions.filter((action) => action !== 'ANALYZE')
    : actions
  // 挂了流程实例的单：受理/否决由流程回写；「发起评审」也不能再给 ——
  // 评审早就在跑了（状态由流程的「设置状态」节点推进），再点一次后端会以
  // "已发起过评审流程，不能重复发起"拒绝，按钮留着只会让用户白点一次。
  // 「取消」同理（2026-11-10）：单据已经在审批人手上，责任人单方面作废会让对方的待办
  // 变成指向一张作废单的幽灵待办（后端 transition 同样拦，见 ChangeRequestServiceImpl）。
  // 免审没有流程实例，这些状态仍可自行作废。
  return record.processInstanceId
    ? visible.filter((action) => !['ACCEPT', 'REJECT', 'SUBMIT', 'CANCEL'].includes(action))
    : visible
}

/** 行内主按钮：评审中的流程单没有主按钮（结论等流程回写） */
function primaryActionOf(record) {
  const primary = primaryRequestAction(record.status)
  return primary && actionsOf(record).includes(primary) ? primary : null
}

/**
 * 「关闭」前置条件：先派生过变更通告，且这些通告**全部闭环**（已关闭 / 已取消）。
 *
 * <p>为什么父单要等子单：ECN 才是"怎么改、改完没有"的执行单。父单先关、子单还在审批中，
 * 账面上就再也看不出这次变更到底做完没有 —— 后端 `ChangeRequestServiceImpl#transition`
 * 用的是同一条门槛（`selectOpenByRequest`），这里提前摆出来，免得用户点完确定才被退回。
 *
 * @returns {string} 空串 = 可以关闭；否则是不能关闭的原因（含还差哪几张通告）
 */
function closeBlockReason(notices) {
  const list = notices || []
  if (!list.length) return '尚未派生变更通告，不能关闭'
  const open = list.filter((item) => !noticeClosed(item.status))
  if (!open.length) return ''
  const codes = open.slice(0, 5).map((item) => item.code).filter(Boolean).join('、')
  const more = open.length > 5 ? ' 等' : ''
  return `还有 ${open.length} 个变更通告未闭环（${codes}${more}），不能关闭`
}

/** 详情抽屉里的子通告（详情接口带回，判定用它就够，不必另发请求） */
const drawerCloseBlockReason = computed(() => closeBlockReason(drawer.notices))

/** 流转弹窗里「关闭」的拦截原因（列表行打开时现查一次子通告） */
const flowCloseBlockReason = computed(() =>
  (flow.action === 'CLOSE' && flow.gateChecked ? closeBlockReason(flow.notices) : ''))

/**
 * 流转弹窗的确定是否置灰：核对中先不让点，核对出不满足条件也不让点；
 * 评审流程没配 / 没部署同样不给点（否则点完才被后端退回）。
 */
const flowConfirmDisabled = computed(() =>
  flow.gateLoading || !!flowCloseBlockReason.value || reviewFlowBlocked.value)

/** 详情抽屉里的按钮是否置灰：非责任人全部只读，关闭还要满足"通告已全部闭环" */
function drawerActionDisabled(action) {
  if (!isOwner(drawer.request)) return true
  return action === 'CLOSE' && !!drawerCloseBlockReason.value
}

function drawerActionHint(action) {
  if (action === 'CLOSE' && drawerCloseBlockReason.value) return drawerCloseBlockReason.value
  return isOwner(drawer.request) ? undefined : ownerHint(drawer.request)
}

/**
 * 子流程（变更通告）的详情：切到「变更通告」页签并按 noticeOid 打开抽屉。
 *
 * <p>与全局搜索 / 对象详情页走同一条路径（`?tab=notice&noticeOid=…`），
 * 详情以服务端为准，不在这里复制一份只读弹窗。
 */
function openNoticeDetail(record) {
  if (!record?.oid) return
  // 先收起本抽屉：页签容器是 destroy-inactive=false，抽屉不会随页签切换自动关，
  // 两层遮罩叠在一起会挡住刚打开的变更通告详情。
  drawer.visible = false
  router.push({ path: '/change', query: { tab: 'notice', noticeOid: record.oid } })
}

/** 任务行 → 归属通告编号（详情接口已带回 notices，不另发请求） */
function noticeCodeOf(noticeOid) {
  if (!noticeOid) return '—'
  const notice = (drawer.notices || []).find((item) => item.oid === noticeOid)
  return notice?.code || '—'
}

/** 评审流程实例详情：/workflow/instance/:id 对普通成员开放（见 router 的 ADMIN_ROUTES 注释） */
function openProcessInstance(instanceId) {
  if (!instanceId) return
  drawer.visible = false
  router.push({ path: `/workflow/instance/${instanceId}` })
}

/** 布局驱动表单（字段定义在 ck_type_page_layout 的 CHANGE_REQUEST/create|update） */
const formRef = ref(null)
/**
 * 弹窗宽度跟随页面布局的列数（单列 680 / 两列 900 / 三列 1040…，由 DynamicForm 计算）。
 *
 * <p>变更请求的布局是一行两列，写死 680px 时每格只剩 ~300px ——「变更类型」「变更系列/型号」
 * 这类下拉被挤得看不出选了什么（2026-11-01 反馈）。
 */
const formWidth = computed(() => formRef.value?.recommendedWidth || '680px')

const modal = reactive({
  visible: false,
  saving: false,
  isEdit: false,
  oid: '',
  form: emptyForm(),
  /**
   * 编辑回填的关联问题（{oid, code, name}）。
   *
   * <p>详情接口才有 code/name，列表行只有 oid；交给 change-issue-selector 作 seed，
   * 否则已选项在下拉里会显示成一串 uuid。
   */
  seedIssues: []
})

const flow = reactive({
  visible: false,
  saving: false,
  oid: '',
  action: '',
  comment: '',
  record: null,
  /**
   * 本次流转对象的子通告（只在「关闭」时取一次）。
   *
   * <p>列表行本身不带子单，不先查一遍就只能等后端退回 —— 那时用户已经点过确定，
   * 弹窗还停在那里，看不出是"哪几张没关"。
   */
  notices: [],
  gateLoading: false,
  /** 子通告是否已经核对过（false = 没查到 / 还没查，此时不拦，交给后端） */
  gateChecked: false
})

const drawer = reactive({
  visible: false,
  loading: false,
  request: null,
  activities: [],
  issues: [],
  notices: [],
  /** 通告下的变更任务（详情接口带回，含 noticeOid / processInstanceId） */
  tasks: [],
  affectedObjects: [],
  /** 补充材料的元信息（单据上只存 attachmentOid，文件名要单独查一次） */
  attachment: null
})

/**
 * 抽屉里三段富文本（变更原因 / 建议方案 / 补充说明）落库存的是 HTML。
 * 详情是只读展示，用 v-html 渲染排版，渲染前统一消毒 —— 内容来自租户提交。
 */
const drawerRich = computed(() => {
  const pick = (v) => sanitizeHtml(v) || '—'
  return {
    reason: pick(drawer.request?.reason),
    proposal: pick(drawer.request?.proposal),
    description: pick(drawer.request?.description)
  }
})

function emptyForm() {
  return {
    name: '',
    changeType: undefined,
    priority: 'MEDIUM',
    owner: undefined,
    dueDate: null,
    reason: '',
    proposal: '',
    description: '',
    issueOids: []
  }
}

async function loadList() {
  loading.value = true
  try {
    const res = await listChangeRequests({
      status: filters.status || undefined,
      keyword: filters.keyword || undefined,
      page: page.value,
      pageSize: pageSize.value
    })
    if (res.code === 200) {
      rows.value = res.data.rows || []
      total.value = res.data.total || 0
      // 列表变了（增删改 / 换页 / 换筛选）→ 让父级重取页头的状态计数
      emit('changed')
      // 待办跟着列表一起刷新：否则"去办理"会停留在上一次的分配结果上
      loadMyTodoTasks()
    } else {
      message.error(res.message || '加载失败')
    }
  } catch {
    message.error('加载变更请求失败')
  } finally {
    loading.value = false
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

function openCreate() {
  modal.isEdit = false
  modal.oid = ''
  modal.form = emptyForm()
  modal.seedIssues = []
  modal.visible = true
}

async function openEdit(record) {
  modal.isEdit = true
  modal.oid = record.oid
  modal.seedIssues = []
  modal.form = {
    name: record.name,
    changeType: record.changeType || undefined,
    // 分级 / 三项影响必须回填：漏掉时下拉会显示布局默认值（STANDARD），
    // 看起来像"已经是标准"，实际模型里是空 —— 校验报空、保存还会把 MAJOR 单降级
    // （2026-11-03 反馈：分级有内容却提示不能为空）
    changeLevel: record.changeLevel || undefined,
    certImpact: record.certImpact || undefined,
    customerImpact: record.customerImpact || undefined,
    inventoryImpact: record.inventoryImpact || undefined,
    priority: record.priority || 'MEDIUM',
    owner: record.owner || undefined,
    dueDate: record.dueDate || null,
    reason: record.reason || '',
    proposal: record.proposal || '',
    description: record.description || '',
    // 补充材料：不回填这个 oid，编辑框就显示成"没传过"，用户也就无从替换旧附件
    attachmentOid: record.attachmentOid || undefined,
    productOid: record.productOid || undefined,
    productType: record.productType || undefined,
    issueOids: []
  }
  modal.visible = true
  // 列表行里没有关联问题，编辑时补一次详情（顺带把已选项的 label 备好）
  try {
    const res = await getChangeRequest(record.oid)
    if (res.code === 200) {
      const issues = res.data?.issues || []
      modal.seedIssues = issues
      modal.form.issueOids = issues.map((i) => i.oid)

      // 所属容器以详情为准（列表列可能没带 productOid）；详情结构是 { request, issues, ... }
      const request = res.data?.request || {}
      modal.form.productOid = request.productOid || modal.form.productOid
      modal.form.productType = request.productType || modal.form.productType
      // 判定类字段同样以详情为准：列表行是投影，详情才是这张单的完整口径
      modal.form.changeType = request.changeType || modal.form.changeType
      modal.form.changeLevel = request.changeLevel || modal.form.changeLevel
      modal.form.certImpact = request.certImpact || modal.form.certImpact
      modal.form.customerImpact = request.customerImpact || modal.form.customerImpact
      modal.form.inventoryImpact = request.inventoryImpact || modal.form.inventoryImpact
    }
  } catch {
    /* 详情拉不到就按"无关联"编辑，不阻塞其它字段 */
  }
}

async function handleSave() {
  if (!modal.form.name || !modal.form.name.trim()) {
    message.warning('请填写标题')
    return
  }
  // 必填校验交给布局：字段定义在 ck_type_page_layout，前端不再重复一份
  const errors = formRef.value?.validate?.() || []
  if (errors.length) {
    message.warning(errors[0])
    return
  }
  modal.saving = true
  try {
    const res = modal.isEdit
      ? await updateChangeRequest(modal.oid, modal.form)
      : await createChangeRequest(modal.form)
    if (res.code === 200) {
      message.success(modal.isEdit ? '已保存' : '已提出')
      modal.visible = false
      loadList()
      if (drawer.visible && drawer.request) openDetail({ oid: drawer.request.oid })
    } else {
      message.error(res.message || '保存失败')
    }
  } catch (e) {
    message.error(e?.message || '保存失败')
  } finally {
    modal.saving = false
  }
}

function openTransition(record, action) {
  flow.oid = record.oid
  flow.action = action
  flow.comment = ''
  flow.record = record
  flow.notices = []
  flow.gateChecked = false
  flow.visible = true
  // 「关闭」要先知道子通告关没关完：抽屉里已有一份就复用，否则现查一次
  if (action === 'CLOSE') loadCloseGate(record)
  // 「发起评审」要先查清会拉起哪条流程（名称 / 版本 / 部署状态）：
  // key 配错或模板没部署，不能让用户点完确定才被退回。每次都重查（可能刚部署完回来）
  if (action === 'SUBMIT' && changeConfig.value.approvalRequired) loadApprovalFlow()
}

/**
 * 取本次要关闭的那张请求的子通告。
 *
 * <p>查不到就不拦（`gateChecked` 保持 false）：真正的门槛在后端，前端这层只是把
 * "还差哪几张通告"提前摆到弹窗里，不能因为一次查询失败把正常关闭也堵死。
 */
async function loadCloseGate(record) {
  if (drawer.visible && drawer.request?.oid === record.oid) {
    flow.notices = drawer.notices
    flow.gateChecked = true
    return
  }
  flow.gateLoading = true
  try {
    const res = await getChangeRequest(record.oid)
    if (res?.code === 200) {
      flow.notices = res.data?.notices || []
      flow.gateChecked = true
    }
  } catch {
    /* 查不到就交给后端判定 */
  } finally {
    flow.gateLoading = false
  }
}

async function handleFlow() {
  // 先判"发起必然失败"的那一档：它同时在 flowConfirmDisabled 里，先说的话才不会
  // 被下面那句"正在核对变更通告状态"盖住，用户才知道到底缺什么
  if (reviewFlowBlocked.value) {
    message.warning(reviewFlowBlockReason.value)
    return
  }
  if (flowConfirmDisabled.value) {
    message.warning(flowCloseBlockReason.value || '正在核对变更通告状态，请稍候')
    return
  }
  if (requestRequiresComment(flow.action) && !flow.comment.trim()) {
    message.warning('请填写说明')
    return
  }
  flow.saving = true
  try {
    const res = await transitionChangeRequest(flow.oid, { action: flow.action, comment: flow.comment })
    if (res.code === 200) {
      message.success('已' + CHANGE_REQUEST_ACTION_LABEL[flow.action])
      flow.visible = false
      loadList()
      if (drawer.visible && drawer.request && drawer.request.oid === flow.oid) {
        openDetail({ oid: flow.oid })
      }
    } else {
      message.error(res.message || '流转失败')
    }
  } catch (e) {
    message.error(e?.message || '流转失败')
  } finally {
    flow.saving = false
  }
}

async function openDetail(record) {
  drawer.visible = true
  drawer.loading = true
  drawer.issues = []
  drawer.notices = []
  drawer.tasks = []
  drawer.affectedObjects = []
  try {
    const res = await getChangeRequest(record.oid)
    if (res.code === 200) {
      drawer.request = res.data.request
      drawer.activities = res.data.activities || []
      drawer.issues = res.data.issues || []
      drawer.affectedObjects = res.data.affectedObjects || []
      // 子流程：关闭变更请求前要看到"还有哪些通告没关"
      drawer.notices = res.data.notices || []
      // 再下一层：这些通告下派了哪些变更任务、分别归谁执行
      drawer.tasks = res.data.tasks || []
      // 补充材料：单据上只存附件 oid，文件名/大小要按 oid 回查（查不到就当没传）
      drawer.attachment = null
      if (drawer.request.attachmentOid) {
        const att = await getAttachment(drawer.request.attachmentOid).catch(() => null)
        if (att?.code === 200) drawer.attachment = att.data
      }
    } else {
      message.error(res.message || '加载详情失败')
    }
  } catch {
    message.error('加载详情失败')
  } finally {
    drawer.loading = false
  }
}

async function handleDelete(record) {
  try {
    const res = await deleteChangeRequest(record.oid)
    if (res.code === 200) {
      message.success('已删除')
      loadList()
    } else {
      message.error(res.message || '删除失败')
    }
  } catch (e) {
    message.error(e?.message || '删除失败')
  }
}

/**
 * 我的待办：processInstanceId → taskId。
 *
 * <p>「去办理」要知道两件事：这张单当前卡在哪个任务、是不是轮到我。一次性拉自己的待办
 * （通常只有几条）比逐行查便宜，也不会每行抖一次请求。
 */
const todoTaskByInstance = ref({})

async function loadMyTodoTasks() {
  try {
    const res = await getMyTodoTasks({ page: 1, size: 200 })
    if (res?.code !== 200) return
    const map = {}
    for (const task of res.data || []) {
      if (task.processInstanceId && !map[task.processInstanceId]) {
        map[task.processInstanceId] = task.id
      }
    }
    todoTaskByInstance.value = map
  } catch {
    /* 待办拉不到不影响列表本身 */
  }
}

function todoTaskOf(record) {
  return record?.processInstanceId ? todoTaskByInstance.value[record.processInstanceId] || '' : ''
}

function goTask(record) {
  const taskId = todoTaskOf(record)
  if (taskId) router.push(`/workflow/task/${taskId}`)
}

function goProcess(record) {
  if (record?.processInstanceId) router.push(`/workflow/instance/${record.processInstanceId}`)
}

function formatDateTime(value) {
  if (!value) return '—'
  const s = String(value).replace('T', ' ')
  return s.length >= 16 ? s.slice(5, 16) : s
}

/**
 * 从全局搜索直接跳进来（`?tab=request&requestOid=…`）：把那张单的详情抽屉打开，
 * 别让人再在列表里翻一遍。用 watch 而不是只在 onMounted 里读一次 —— 已经停在
 * 变更控制页时再搜一张单，组件不会重建，只读 mounted 就什么都不发生。
 */
function openFromRoute() {
  const oid = route.query.requestOid
  if (!oid) return
  openDetail({ oid: String(oid) })
  // 一次性指令用完就从地址栏擦掉：`?requestOid=` 留在 URL 上，F5 会再弹一次抽屉
  // （用户 2026-11-03：*"F5刷新一直是这个画面，不应该弹出变更请求的详情"*）。
  // 关掉抽屉不影响历史记录 —— 用户自己按浏览器后退才会回到"跳进来那一刻"。
  const query = { ...route.query }
  delete query.requestOid
  router.replace({ query })
}

watch(() => route.query.requestOid, openFromRoute)

onMounted(() => {
  loadList()
  loadConfig()
  openFromRoute()
})

defineExpose({ reload })
</script>

<style scoped>
.chg-panel { display: flex; flex-direction: column; height: 100%; }
.chg-link { color: #1677ff; cursor: pointer; }
.chg-drawer-head { display: flex; justify-content: space-between; align-items: flex-start; margin-bottom: 12px; gap: 12px; }
.chg-drawer-title { margin-left: 8px; }
.chg-drawer-actions { margin-top: 12px; }
.chg-divider { margin: 16px 0 12px; }
.chg-pre { white-space: pre-wrap; word-break: break-word; }
/* 富文本详情：段落/列表自带外边距会把描述行撑开，首尾也留空 */
.chg-rich :deep(p) { margin: 0 0 6px; }
.chg-rich :deep(p:last-child) { margin-bottom: 0; }
.chg-rich :deep(ul), .chg-rich :deep(ol) { margin: 0 0 6px; padding-left: 20px; }
.chg-rich :deep(img) { max-width: 100%; height: auto; }
.chg-muted { color: #bfbfbf; }
.chg-hint { color: #8c8c8c; font-size: 12px; margin-top: 6px; line-height: 1.6; }
.chg-flow-hint { margin-bottom: 12px; }
.chg-act-head { display: flex; align-items: center; gap: 8px; flex-wrap: wrap; }
.chg-act-flow { color: #8c8c8c; font-size: 12px; }
.chg-act-time { color: #bfbfbf; font-size: 12px; margin-left: auto; }
.chg-act-op { color: #595959; font-size: 12px; margin-top: 2px; }
.chg-act-comment { margin-top: 4px; padding: 6px 8px; background: #fafafa; border-radius: 4px; white-space: pre-wrap; }
</style>
