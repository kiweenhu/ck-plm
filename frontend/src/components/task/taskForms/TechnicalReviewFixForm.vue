<template>
  <div class="trf">
    <a-spin :spinning="loading">
      <!-- 只读：上游「技术评审申请单」内容（各 TR 节点共用同一个摘要组件） -->
      <TechnicalReviewRequestSummary :task-id="taskId" />

      <!-- 整改口径：这张表只做一件事 —— 把"问题"从处理中推进到待验证，并留下根因与措施。
           "自己说改好了不算数"是后端 IssueFlow 的硬约束（SUBMIT_VERIFY 必须给 rootCause + resolution），
           所以这里两栏都必填，不给"写个已处理"糊过去的机会。 -->
      <div class="trf-intro">
        本节点由<b>问题责任人</b>逐条填写根因与整改措施并提交复评；提交后问题状态变为「待验证」，
        由复评组在下一个节点确认。没有登记过问题（或问题已全部闭环）时，可直接在下方写结论并提交。
      </div>

      <div class="trf-bar">
        <span class="trf-bar__label">整改问题</span>
        <a-tag color="blue">共 {{ rows.length }} 条</a-tag>
        <a-tag v-if="openCount" color="orange">待整改 {{ openCount }}</a-tag>
        <a-tag v-if="verifyingCount" color="purple">待复评 {{ verifyingCount }}</a-tag>
        <a-tag v-if="closedCount" color="green">已关闭 {{ closedCount }}</a-tag>
        <span class="trf-bar__source">{{ sourceLabel }}</span>
      </div>

      <a-table
        :columns="COLUMNS"
        :data-source="rows"
        row-key="oid"
        size="small"
        :pagination="false"
        :locale="{ emptyText: '本次评审没有登记问题 —— 可直接提交结论' }"
      >
        <template #bodyCell="{ column, record }">
          <template v-if="column.key === 'code'">
            <code class="trf-code">{{ record.code || '—' }}</code>
          </template>
          <template v-else-if="column.key === 'name'">
            <span class="trf-name">{{ record.name }}</span>
          </template>
          <template v-else-if="column.key === 'severity'">
            <a-tag :color="ISSUE_SEVERITY_COLOR[record.severity] || 'default'">
              {{ ISSUE_SEVERITY_LABEL[record.severity] || record.severity }}
            </a-tag>
          </template>
          <template v-else-if="column.key === 'owner'">
            {{ ownerLabel(record.owner) }}
          </template>
          <template v-else-if="column.key === 'dueDate'">
            {{ record.dueDate || '—' }}
          </template>
          <template v-else-if="column.key === 'status'">
            <a-tag :color="ISSUE_STATUS_COLOR[record.status] || 'default'">
              {{ ISSUE_STATUS_LABEL[record.status] || record.status }}
            </a-tag>
          </template>
          <template v-else-if="column.key === 'result'">
            <a-tooltip v-if="record.resolution" :title="resultTip(record)">
              <span class="trf-result">{{ record.resolution }}</span>
            </a-tooltip>
            <span v-else class="trf-muted">—</span>
          </template>
          <template v-else-if="column.key === 'action'">
            <a v-if="canSubmit(record)" class="trf-link" @click="openEdit(record)">填写整改</a>
            <a v-else-if="hasResult(record)" class="trf-link" @click="openReadonly(record)">查看</a>
            <a-tooltip v-else title="仅问题责任人可提交整改（服务端同口径校验）">
              <span class="trf-muted">由责任人提交</span>
            </a-tooltip>
          </template>
        </template>
      </a-table>

      <div class="trf-section">整改总结<span class="trf-section__tip">选填，提交后作为本节点的办理意见</span></div>
      <a-textarea
        v-model:value="model.comment"
        :rows="3"
        :maxlength="500"
        show-count
        placeholder="例如：3 条问题已全部整改（更换供应商 + 加严来料检验），请复评组确认"
      />
    </a-spin>

    <!-- 填写整改结果 -->
    <a-modal
      v-model:open="modal.open"
      :title="modal.readonly ? `整改结果：${modal.row?.code || ''}` : `填写整改结果：${modal.row?.code || ''}`"
      :ok-text="modal.readonly ? undefined : '提交并转复评'"
      :cancel-text="modal.readonly ? '关闭' : '取消'"
      :confirm-loading="modal.saving"
      :footer="modal.readonly ? null : undefined"
      width="620px"
      @ok="submitFix"
    >
      <div class="trf-modal-issue">
        <a-tag :color="ISSUE_SEVERITY_COLOR[modal.row?.severity] || 'default'">
          {{ ISSUE_SEVERITY_LABEL[modal.row?.severity] || modal.row?.severity }}
        </a-tag>
        <span>{{ modal.row?.name }}</span>
      </div>

      <a-form layout="vertical" class="trf-form">
        <a-form-item label="根因分析" required>
          <a-textarea
            v-model:value="modal.rootCause"
            :rows="3"
            :disabled="modal.readonly"
            :maxlength="1000"
            placeholder="为什么会发生？例如：来料检验只抽检外观，未覆盖尺寸公差"
          />
        </a-form-item>
        <a-form-item label="整改措施" required>
          <a-textarea
            v-model:value="modal.resolution"
            :rows="3"
            :disabled="modal.readonly"
            :maxlength="1000"
            placeholder="怎么处理的？例如：补充检具并更新 SIP，已对该批次全检"
          />
        </a-form-item>
        <a-form-item label="补充说明">
          <a-textarea
            v-model:value="modal.comment"
            :rows="2"
            :disabled="modal.readonly"
            :maxlength="500"
            placeholder="选填：影响范围、临时对策、后续预防等"
          />
        </a-form-item>
      </a-form>
    </a-modal>
  </div>
</template>

<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { message } from 'ant-design-vue'
import {
  getAllUsers,
  getIssueDetail,
  getProcessInstanceDetail,
  getTaskContext,
  listIssues,
  transitionIssue,
} from '@/api'
import {
  ISSUE_SEVERITY_COLOR,
  ISSUE_SEVERITY_LABEL,
  ISSUE_STATUS_COLOR,
  ISSUE_STATUS_LABEL,
} from '@/utils/issueFlow'
import { useUserStore } from '@/stores/user'
import TechnicalReviewRequestSummary from './TechnicalReviewRequestSummary.vue'

/**
 * 「等待整改结果」节点表单 —— 技术评审流程 n_fix。
 *
 * <p>为什么需要它：整改是"问题责任人交作业"的动作，节点上原来只有通用表单（同意/驳回 + 意见），
 * 经办人要么去「持续改进」逐条点问题，要么在意见里手抄，两处必然对不上。
 * 本表把问题搬进办理页：逐条填根因与措施 → 提交后问题进 VERIFYING → 下一个节点（复评）才有东西可验。
 *
 * <p>取数优先级：
 * <ol>
 *   <li>流程变量 {@code ckplmTechReview_issueOids}（问题登记节点写入）—— 精确到本次评审登记的那几条；</li>
 *   <li>没有变量（旧实例）时退化为"我名下未闭环的问题" —— 会多带出别处的问题，界面上明确标注来源，
 *       不假装精确。</li>
 * </ol>
 *
 * <p>权限口径与后端一致：START / SUBMIT_VERIFY 只能由 {@code issue.owner} 执行，
 * 非责任人的行只给"查看"，不给按钮（点了也只会 403）。
 */

const props = defineProps({
  /** 任务 id —— 取上下文与流程变量用 */
  taskId: { type: String, default: '' },
  /** 表单上下文（ProcessTaskFormVO）：formKey / taskDefinitionKey / dslJson */
  form: { type: Object, default: null },
})

const userStore = useUserStore()
const me = computed(() => userStore.username || '')

const COLUMNS = [
  { title: '编号', key: 'code', width: 96 },
  { title: '问题描述', key: 'name' },
  { title: '严重度', key: 'severity', width: 92 },
  { title: '责任人', key: 'owner', width: 132 },
  { title: '整改期限', key: 'dueDate', width: 112 },
  { title: '状态', key: 'status', width: 100 },
  { title: '整改措施', key: 'result', width: 200 },
  { title: '操作', key: 'action', width: 116 },
]

const loading = ref(true)
const rows = ref([])
/** 数据来源说明（精确 / 退化）—— 不写清楚就会被当成"漏了问题" */
const sourceLabel = ref('')
const userMap = ref({})
const model = reactive({ comment: '' })

const modal = reactive({
  open: false,
  readonly: false,
  saving: false,
  row: null,
  rootCause: '',
  resolution: '',
  comment: '',
})

const openCount = computed(() => rows.value.filter((r) => r.status === 'OPEN' || r.status === 'IN_PROGRESS').length)
const verifyingCount = computed(() => rows.value.filter((r) => r.status === 'VERIFYING').length)
const closedCount = computed(() => rows.value.filter((r) => r.status === 'CLOSED').length)

function hasResult(row) {
  return Boolean(row?.rootCause || row?.resolution)
}

function ownerLabel(username) {
  if (!username) return '—'
  const display = userMap.value[username]
  return display && display !== username ? `${display}（${username}）` : username
}

function resultTip(row) {
  return `根因：${row.rootCause || '—'}\n措施：${row.resolution || '—'}`
}

/** 只有责任人在"待处理 / 处理中"才能提交整改 —— 与 IssueFlow.requiresOwner 同口径 */
function canSubmit(row) {
  if (!row || row.owner !== me.value) return false
  return row.status === 'OPEN' || row.status === 'IN_PROGRESS'
}

/** 从流程实例详情里取变量（结构容错：{name:value} 或 [{name,value}]） */
function variableOf(variables, name) {
  if (!variables) return null
  if (Array.isArray(variables)) {
    const hit = variables.find((v) => v && v.name === name)
    return hit ? hit.value : null
  }
  return variables[name] ?? null
}

function splitOids(raw) {
  return String(raw || '').split(',').map((s) => s.trim()).filter(Boolean)
}

async function load() {
  loading.value = true
  try {
    // 用户表只用来把 username 显示成人名（问题里存的是账号）
    const usersRes = await getAllUsers().catch(() => null)
    if (usersRes?.code === 200) {
      const map = {}
      ;(usersRes.data || []).forEach((u) => { if (u?.username) map[u.username] = u.displayName || u.username })
      userMap.value = map
    }

    const ctxRes = props.taskId ? await getTaskContext(props.taskId).catch(() => null) : null
    const ctx = ctxRes?.code === 200 ? ctxRes.data : null
    const instId = ctx?.process?.id || null
    const detailRes = instId ? await getProcessInstanceDetail(instId).catch(() => null) : null
    const variables = detailRes?.code === 200 ? detailRes.data?.variables : null

    const oids = splitOids(variableOf(variables, 'ckplmTechReview_issueOids'))
    if (oids.length) {
      const loaded = []
      for (const oid of oids) {
        const res = await getIssueDetail(oid).catch(() => null)
        if (res?.code === 200 && res.data?.issue) loaded.push(res.data.issue)
      }
      rows.value = loaded
      sourceLabel.value = '来源：本次评审登记的问题'
      return
    }

    // 旧实例：变量里没有问题编号，退化为"我名下未闭环的问题"
    const res = await listIssues({ owner: me.value, pageSize: 200 }).catch(() => null)
    const list = res?.code === 200 ? (res.data?.rows || []) : []
    rows.value = list.filter((it) => ['OPEN', 'IN_PROGRESS', 'VERIFYING'].includes(it.status))
    sourceLabel.value = me.value
      ? '来源：你名下未闭环的问题（本次评审未记录问题编号）'
      : '来源：未识别到当前用户'
  } catch {
    rows.value = []
    sourceLabel.value = ''
  } finally {
    loading.value = false
  }
}

onMounted(load)

function openEdit(row) {
  modal.open = true
  modal.readonly = false
  modal.row = row
  modal.rootCause = row.rootCause || ''
  modal.resolution = row.resolution || ''
  modal.comment = ''
}

function openReadonly(row) {
  modal.open = true
  modal.readonly = true
  modal.row = row
  modal.rootCause = row.rootCause || ''
  modal.resolution = row.resolution || ''
  modal.comment = ''
}

async function submitFix() {
  const row = modal.row
  if (!row) return
  if (!modal.rootCause.trim()) {
    message.warning('请填写根因分析')
    return
  }
  if (!modal.resolution.trim()) {
    message.warning('请填写整改措施')
    return
  }

  modal.saving = true
  try {
    // 待处理 → 处理中：后端不允许一步跳到待验证，多一步 START 换来时间线上"开始处理"这条真实记录
    if (row.status === 'OPEN') {
      const started = await transitionIssue(row.oid, { action: 'START' })
      if (started?.code !== 200) return
      row.status = 'IN_PROGRESS'
    }

    const res = await transitionIssue(row.oid, {
      action: 'SUBMIT_VERIFY',
      rootCause: modal.rootCause.trim(),
      resolution: modal.resolution.trim(),
      comment: modal.comment.trim() || undefined,
    })
    if (res?.code !== 200) return

    row.status = 'VERIFYING'
    row.rootCause = modal.rootCause.trim()
    row.resolution = modal.resolution.trim()
    modal.open = false
    message.success(`「${row.code || row.name}」已提交，等待复评`)
  } finally {
    modal.saving = false
  }
}

/* ==================== 提交契约 ==================== */

const submitText = computed(() => '提交整改结果并转复评')
const successText = computed(() => '整改结果已提交，流程转「复评（评审组）」')
const renderable = computed(() => true)

function collect() {
  // 只卡"我负责且没交作业"的那几条：别人的问题交不了，也不该被本节点拦下
  const pendingMine = rows.value.filter((r) => canSubmit(r))
  if (pendingMine.length) {
    const codes = pendingMine.map((r) => r.code || r.name).join('、')
    return { ok: false, message: `还有 ${pendingMine.length} 条你负责的问题未提交整改：${codes}` }
  }

  const submitted = rows.value.filter((r) => r.status === 'VERIFYING').length
  const stillOpen = rows.value.filter((r) => r.status === 'OPEN' || r.status === 'IN_PROGRESS').length
  return {
    ok: true,
    payload: {
      action: 'approve',
      comment: model.comment.trim()
        || (rows.value.length
          ? `整改情况：已提交 ${submitted} 条待复评${stillOpen ? `，${stillOpen} 条仍由责任人在处理` : ''}`
          : '本次评审未登记问题，无需整改'),
      variables: {
        ckplmTechReview_fixSubmitted: submitted,
        ckplmTechReview_fixPending: stillOpen,
        ckplmTechReview_fixTotal: rows.value.length,
      },
    },
  }
}

defineExpose({ collect, submitText, successText, renderable })
</script>

<style scoped>
.trf-intro {
  margin-bottom: 12px;
  padding: 8px 12px;
  background: #f6f9ff;
  border-left: 3px solid #1677ff;
  border-radius: 4px;
  font-size: 12px;
  color: #595959;
  line-height: 1.7;
}

.trf-bar {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 8px;
}

.trf-bar__label {
  font-weight: 600;
  color: #333;
}

.trf-bar__source {
  margin-left: auto;
  font-size: 11px;
  color: #bfbfbf;
}

.trf-section {
  margin: 16px 0 8px;
  padding-left: 8px;
  border-left: 3px solid #1677ff;
  font-weight: 600;
  color: #333;
}

.trf-section__tip {
  margin-left: 10px;
  font-weight: 400;
  font-size: 11px;
  color: #bfbfbf;
}

.trf-code {
  font-size: 12px;
  color: #666;
}

.trf-name {
  color: #333;
}

.trf-result {
  display: inline-block;
  max-width: 190px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  color: #595959;
  font-size: 12px;
}

.trf-muted {
  color: #bfbfbf;
  font-size: 12px;
}

.trf-link {
  font-size: 12px;
}

.trf-modal-issue {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 12px;
  color: #333;
}

.trf-form {
  margin-bottom: -8px;
}
</style>
