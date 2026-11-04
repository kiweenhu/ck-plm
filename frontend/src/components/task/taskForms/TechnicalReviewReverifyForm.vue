<template>
  <div class="trr">
    <a-spin :spinning="loading">
      <!-- 只读：上游「技术评审申请单」内容（各 TR 节点共用同一个摘要组件） -->
      <TechnicalReviewRequestSummary :task-id="taskId" />

      <!-- 复评要同时回答两个问题：① 每条问题到底改没改好（要推进状态）；② 我这一票投什么。
           内置会签只回答②，于是问题会永远停在「待验证」—— 阶段门的 ISSUE_OPEN 永远算未闭环。 -->
      <div class="trr-intro">
        逐条确认整改结果：<b>通过</b>即关闭该问题，<b>退回</b>即回到处理中（责任人需重新整改）。
        全部问题处理完才能表决；只要有一条被退回，本节点就必须「驳回」，流程退回整改。
      </div>

      <div class="trr-bar">
        <span class="trr-bar__label">待复评问题</span>
        <a-tag color="blue">共 {{ issues.length }} 条</a-tag>
        <a-tag v-if="verifyingCount" color="purple">待验证 {{ verifyingCount }}</a-tag>
        <a-tag v-if="closedCount" color="green">已通过 {{ closedCount }}</a-tag>
        <a-tag v-if="returnedCount" color="red">已退回 {{ returnedCount }}</a-tag>
        <span class="trr-bar__source">{{ sourceLabel }}</span>
      </div>

      <a-table
        :columns="ISSUE_COLUMNS"
        :data-source="issues"
        row-key="oid"
        size="small"
        :pagination="false"
        :locale="{ emptyText: '本次评审没有登记问题 —— 可直接表决' }"
      >
        <template #bodyCell="{ column, record }">
          <template v-if="column.key === 'code'">
            <code class="trr-code">{{ record.code || '—' }}</code>
          </template>
          <template v-else-if="column.key === 'severity'">
            <a-tag :color="ISSUE_SEVERITY_COLOR[record.severity] || 'default'">
              {{ ISSUE_SEVERITY_LABEL[record.severity] || record.severity }}
            </a-tag>
          </template>
          <template v-else-if="column.key === 'owner'">{{ ownerLabel(record.owner) }}</template>
          <template v-else-if="column.key === 'rootCause'">
            <a-tooltip v-if="record.rootCause" :title="record.rootCause">
              <span class="trr-ellipsis">{{ record.rootCause }}</span>
            </a-tooltip>
            <span v-else class="trr-muted">—</span>
          </template>
          <template v-else-if="column.key === 'resolution'">
            <a-tooltip v-if="record.resolution" :title="record.resolution">
              <span class="trr-ellipsis">{{ record.resolution }}</span>
            </a-tooltip>
            <span v-else class="trr-muted">—</span>
          </template>
          <template v-else-if="column.key === 'status'">
            <a-tag :color="ISSUE_STATUS_COLOR[record.status] || 'default'">
              {{ ISSUE_STATUS_LABEL[record.status] || record.status }}
            </a-tag>
          </template>
          <template v-else-if="column.key === 'action'">
            <template v-if="record.status === 'VERIFYING'">
              <a class="trr-link" @click="openVerify(record, true)">通过</a>
              <a-divider type="vertical" />
              <a class="trr-link trr-link--danger" @click="openVerify(record, false)">退回</a>
            </template>
            <span v-else-if="record.status === 'CLOSED'" class="trr-passed">已验证通过</span>
            <span v-else-if="record.status === 'IN_PROGRESS'" class="trr-returned">已退回整改</span>
            <span v-else class="trr-muted">—</span>
          </template>
        </template>
      </a-table>

      <!-- 会签：规则 + 各参与人办理情况（与内置会签同源，不另写一套口径） -->
      <div class="trr-section">会签</div>
      <div class="trr-rule">
        <span class="trr-rule__label">会签规则</span>
        <span class="trr-rule__value">{{ model.ruleText }}</span>
        <span class="trr-rule__detail">{{ model.ruleDetail }}</span>
      </div>

      <a-table
        class="trr-votes"
        :columns="VOTE_COLUMNS"
        :data-source="rows"
        size="small"
        row-key="rowKey"
        :pagination="false"
        :row-class-name="rowClassOf"
      >
        <template #bodyCell="{ column, record }">
          <template v-if="column.key === 'name'">
            {{ record.name }}
            <a-tag v-if="record.isMe" color="blue">我</a-tag>
          </template>
          <template v-else-if="column.key === 'state'">
            <a-tag :color="stateOf(record).color">{{ stateOf(record).text }}</a-tag>
          </template>
          <template v-else-if="column.key === 'comment'">
            <span v-if="record.comment" class="trr-comment">{{ record.comment }}</span>
            <span v-else class="trr-muted">—</span>
          </template>
          <template v-else>
            <span v-if="record.timeText">{{ record.timeText }}</span>
            <span v-else class="trr-muted">—</span>
          </template>
        </template>
      </a-table>
      <div v-if="!model.total" class="trr-empty-line">暂未取到参与者（流程进度里还没有这条活动的记录）</div>

      <!-- 我这一票 -->
      <a-form layout="vertical" class="trr-mine">
        <a-form-item label="我的复评意见" :required="requiredNow">
          <div class="trr-routes">
            <a-radio-group v-model:value="action">
              <a-radio value="approve">同意</a-radio>
              <a-radio v-if="model.opinion.allowReject" value="reject">驳回</a-radio>
            </a-radio-group>
            <span v-if="isReject && model.opinion.allowReject" class="trr-target">
              驳回后回到：{{ model.opinion.rejectTargetText }}
            </span>
          </div>
          <a-textarea v-model:value="comment" :rows="4" :placeholder="placeholder" />
        </a-form-item>
      </a-form>
    </a-spin>

    <!-- 逐条验证 -->
    <a-modal
      v-model:open="modal.open"
      :title="modal.pass ? `验证通过：${modal.row?.code || ''}` : `退回整改：${modal.row?.code || ''}`"
      :ok-text="modal.pass ? '确认通过' : '确认退回'"
      :ok-button-props="{ danger: !modal.pass }"
      :confirm-loading="modal.saving"
      width="600px"
      @ok="submitVerify"
    >
      <div class="trr-modal-issue">
        <a-tag :color="ISSUE_SEVERITY_COLOR[modal.row?.severity] || 'default'">
          {{ ISSUE_SEVERITY_LABEL[modal.row?.severity] || modal.row?.severity }}
        </a-tag>
        <span>{{ modal.row?.name }}</span>
      </div>

      <div class="trr-modal-fix">
        <div><b>根因：</b>{{ modal.row?.rootCause || '—' }}</div>
        <div><b>措施：</b>{{ modal.row?.resolution || '—' }}</div>
      </div>

      <a-form layout="vertical">
        <a-form-item :label="modal.pass ? '验证结论' : '退回原因'" required>
          <a-textarea
            v-model:value="modal.text"
            :rows="3"
            :placeholder="modal.pass ? '例如：已核对新检具报告与全检记录，问题不再复现' : '例如：仅补了检验记录，未更新 SIP，请重新整改'"
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
import { buildCountersignModel, validateCountersign } from '@/flow-designer/app/task/countersign'
import TechnicalReviewRequestSummary from './TechnicalReviewRequestSummary.vue'

/**
 * 「复评（评审组）」节点表单 —— 技术评审流程 n_reverify（会签审批）。
 *
 * <h3>为什么必须是独立表单</h3>
 * <p>内置「会签」只解决"规则是什么 / 别人投了什么 / 我怎么投"，复评人看不到要复的东西，
 * 更要命的是<b>通过/退回不会改变问题状态</b>：问题永远停在 {@code VERIFYING}，
 * 阶段门的 ISSUE_OPEN 检查就永远算"未闭环"。
 * 本表 = 内置会签的全部内容（复用 {@link buildCountersignModel} / {@link validateCountersign}，
 * 规则、进度、逐人结论、否决口径一处不改）<b>+ 逐条验证</b>（VERIFY_PASS / VERIFY_FAIL）。
 *
 * <h3>一致性约束</h3>
 * <p>提交前必须把所有问题处理完（不能留着"待验证"就去表决，否则下一轮整改节点没有入口再改它）；
 * 只要有一条被退回，就不允许「同意」—— 会签同意 + 问题还在整改，是自相矛盾的状态。
 *
 * <h3>提交口径</h3>
 * <p>与内置会签完全一致：{@code collect() → { ok, payload: { action, comment } }}。
 * 问题的状态推进在"逐条验证"时即时完成（不是提交时批量刷），这样会签者之间的进度可见。
 */
const props = defineProps({
  /** 任务 id（标出"我"那一行，也供取上下文用） */
  taskId: { type: String, default: '' },
  /** 表单上下文（ProcessTaskFormVO）：formKey / taskDefinitionKey / dslJson */
  form: { type: Object, default: null },
  /** 流程进度（TaskContextVO.activities）：同一会签活动下一人一条 */
  activities: { type: Array, default: () => [] },
})

const ISSUE_COLUMNS = [
  { title: '编号', key: 'code', width: 96 },
  { title: '问题描述', key: 'name' },
  { title: '严重度', key: 'severity', width: 90 },
  { title: '责任人', key: 'owner', width: 126 },
  { title: '根因分析', key: 'rootCause', width: 190 },
  { title: '整改措施', key: 'resolution', width: 190 },
  { title: '状态', key: 'status', width: 96 },
  { title: '操作', key: 'action', width: 128 },
]

const VOTE_COLUMNS = [
  { key: 'name', title: '参与人', width: 150 },
  { key: 'state', title: '结论', width: 100 },
  { key: 'time', title: '时间', width: 150 },
  { key: 'comment', title: '意见' },
]

const action = ref('approve')
const comment = ref('')

const loading = ref(true)
const issues = ref([])
const sourceLabel = ref('')
const userMap = ref({})

const modal = reactive({ open: false, pass: true, saving: false, row: null, text: '' })

const model = computed(() => {
  let dsl = null
  const raw = props.form?.dslJson
  if (raw) {
    try {
      dsl = JSON.parse(raw)
    } catch {
      dsl = null
    }
  }
  return buildCountersignModel({
    dsl,
    nodeId: props.form?.taskDefinitionKey,
    activities: props.activities,
    currentTaskId: props.taskId,
  })
})

const rows = computed(() =>
  model.value.participants.map((item, index) => ({ ...item, rowKey: item.taskId || `row-${index}` })),
)

const verifyingCount = computed(() => issues.value.filter((r) => r.status === 'VERIFYING').length)
const closedCount = computed(() => issues.value.filter((r) => r.status === 'CLOSED').length)
const returnedCount = computed(() => issues.value.filter((r) => r.status === 'IN_PROGRESS').length)

function ownerLabel(username) {
  if (!username) return '—'
  const display = userMap.value[username]
  return display && display !== username ? `${display}（${username}）` : username
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

async function load() {
  loading.value = true
  try {
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

    const oids = String(variableOf(variables, 'ckplmTechReview_issueOids') || '')
      .split(',').map((s) => s.trim()).filter(Boolean)

    if (oids.length) {
      const loaded = []
      for (const oid of oids) {
        const res = await getIssueDetail(oid).catch(() => null)
        if (res?.code === 200 && res.data?.issue) loaded.push(res.data.issue)
      }
      issues.value = loaded
      sourceLabel.value = '来源：本次评审登记的问题'
      return
    }

    // 旧实例：没有 oid 变量，退化为"当前处于待验证/已退回的问题"
    const res = await listIssues({ pageSize: 200 }).catch(() => null)
    const list = res?.code === 200 ? (res.data?.rows || []) : []
    issues.value = list.filter((it) => ['VERIFYING', 'IN_PROGRESS'].includes(it.status))
    sourceLabel.value = '来源：当前待验证 / 已退回的问题（本次评审未记录问题编号）'
  } catch {
    issues.value = []
    sourceLabel.value = ''
  } finally {
    loading.value = false
  }
}

onMounted(load)

function openVerify(row, pass) {
  modal.open = true
  modal.pass = pass
  modal.saving = false
  modal.row = row
  modal.text = ''
}

async function submitVerify() {
  const row = modal.row
  if (!row) return
  if (!modal.text.trim()) {
    message.warning(modal.pass ? '请填写验证结论' : '请填写退回原因')
    return
  }

  modal.saving = true
  try {
    const res = modal.pass
      ? await transitionIssue(row.oid, { action: 'VERIFY_PASS', verifyResult: modal.text.trim() })
      : await transitionIssue(row.oid, { action: 'VERIFY_FAIL', comment: modal.text.trim() })
    if (res?.code !== 200) return

    row.status = modal.pass ? 'CLOSED' : 'IN_PROGRESS'
    row.verifyResult = modal.pass ? modal.text.trim() : row.verifyResult
    modal.open = false
    message.success(modal.pass ? `「${row.code || row.name}」验证通过，已关闭` : `「${row.code || row.name}」已退回整改`)
  } finally {
    modal.saving = false
  }
}

/* ==================== 会签意见 / 提交契约 ==================== */

const isReject = computed(() => action.value === 'reject')

function stateOf(record) {
  if (!record.done) return { text: '未办理', color: 'default' }
  if (record.decision === 'APPROVE') return { text: '已同意', color: 'green' }
  if (record.decision === 'REJECT') return { text: '已驳回', color: 'red' }
  return { text: '已办理', color: 'default' }
}

function rowClassOf(record) {
  return record?.isMe ? 'trr-row-me' : ''
}

const requiredNow = computed(() =>
  (isReject.value
    ? model.value.opinion.commentRequiredOnReject
    : model.value.opinion.commentRequiredOnApprove))

const placeholder = computed(() => {
  if (isReject.value) {
    return model.value.opinion.commentRequiredOnReject ? '请说明驳回理由（必填）' : '请说明驳回理由...'
  }
  return model.value.opinion.commentRequiredOnApprove ? '请填写复评意见（必填）' : '请输入复评意见...'
})

const submitText = computed(() => (isReject.value ? '驳回并回退整改' : '同意并提交'))
const successText = computed(() =>
  (isReject.value ? '已驳回，流程退回整改节点' : '已同意，会签按配置规则继续'))
const renderable = computed(() => true)

function collect() {
  // ① 不能留着"待验证"就表决：退回后该问题在整改节点没有入口（那张表只列我负责的待处理/处理中）
  if (verifyingCount.value > 0) {
    return { ok: false, message: `还有 ${verifyingCount.value} 条问题未验证：请逐条「通过」或「退回」后再提交` }
  }
  // ② 有退回就不能同意：会签同意 + 问题仍在整改，是自相矛盾的状态
  if (!isReject.value && returnedCount.value > 0) {
    return {
      ok: false,
      message: model.value.opinion.allowReject
        ? `有 ${returnedCount.value} 条问题被退回，仍在整改中：请把结论改为「驳回」，让流程回到整改`
        // 没画驳回边时别说"请驳回"——那是让人点一个不存在的按钮，得指到真正要改的地方
        : `有 ${returnedCount.value} 条问题被退回，但本节点没有「驳回」路径：`
          + '请在流程图里从「复评（评审组）」画一条驳回边指向「等待整改结果」，否则退回回不到整改',
    }
  }
  // ③ 会签口径（意见是否必填、是否允许驳回）仍由 DSL 声明决定，与内置会签同一个校验函数
  const issue = validateCountersign(model.value, action.value, comment.value)
  if (issue) return { ok: false, message: issue }
  return { ok: true, payload: { action: action.value, comment: comment.value } }
}

defineExpose({ collect, submitText, successText, renderable })
</script>

<style scoped>
.trr-intro {
  margin-bottom: 12px;
  padding: 8px 12px;
  background: #f6f9ff;
  border-left: 3px solid #1677ff;
  border-radius: 4px;
  font-size: 12px;
  color: #595959;
  line-height: 1.7;
}

.trr-bar {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 8px;
}

.trr-bar__label {
  font-weight: 600;
  color: #333;
}

.trr-bar__source {
  margin-left: auto;
  font-size: 11px;
  color: #bfbfbf;
}

.trr-section {
  margin: 16px 0 8px;
  padding-left: 8px;
  border-left: 3px solid #1677ff;
  font-weight: 600;
  color: #333;
}

.trr-rule {
  display: flex;
  align-items: baseline;
  flex-wrap: wrap;
  gap: 4px 10px;
  padding: 8px 12px;
  background: #f0f5ff;
  border: 1px solid #adc6ff;
  border-radius: 6px;
  margin-bottom: 10px;
}

.trr-rule__label {
  font-size: 12px;
  color: #2f54eb;
}

.trr-rule__value {
  font-size: 13px;
  font-weight: 600;
  color: #1d39c4;
}

.trr-rule__detail {
  font-size: 12px;
  color: #8c8c8c;
}

.trr-votes {
  margin-bottom: 4px;
}

.trr-comment {
  white-space: pre-wrap;
}

.trr-mine {
  margin-top: 12px;
}

.trr-routes {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 4px 12px;
  margin-bottom: 8px;
}

.trr-target {
  font-size: 12px;
  color: #d46b08;
  background: #fff7e6;
  border: 1px solid #ffe7ba;
  border-radius: 4px;
  padding: 1px 8px;
}

.trr-code {
  font-size: 12px;
  color: #666;
}

.trr-ellipsis {
  display: inline-block;
  max-width: 180px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  color: #595959;
  font-size: 12px;
}

.trr-muted {
  color: #bfbfbf;
  font-size: 12px;
}

.trr-link {
  font-size: 12px;
}

.trr-link--danger {
  color: #ff4d4f;
}

.trr-passed {
  font-size: 12px;
  color: #389e0d;
}

.trr-returned {
  font-size: 12px;
  color: #d4380d;
}

.trr-empty-line {
  margin-top: 6px;
  font-size: 12px;
  color: #8c8c8c;
}

.trr :deep(.trr-row-me) > td {
  background: #f0f5ff;
}

.trr :deep(.trr-row-me) > td:first-child {
  border-left: 3px solid #1677ff;
}

.trr-modal-issue {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 10px;
  color: #333;
}

.trr-modal-fix {
  margin-bottom: 12px;
  padding: 8px 12px;
  background: #fafafa;
  border-radius: 4px;
  font-size: 12px;
  color: #595959;
  line-height: 1.8;
  word-break: break-all;
}
</style>
