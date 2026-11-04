<template>
  <div class="eccb">
    <!-- ① 只读：变更请求全貌 —— 与「影响分析会签」共用同一块，避免两处各写一份 -->
    <EcrSummaryPanel :form="form" />

    <!-- ② 只读：影响分析结论 —— CCB 决策的第一手依据（各专业的影响程度与说明） -->
    <div class="eccb-card">
      <div class="eccb-card__head">
        <span class="eccb-card__title">影响分析结论</span>
        <span class="eccb-muted">共 {{ impactRows.length }} 位评估人</span>
        <span v-if="!impactRows.length" class="eccb-warn">
          未取到影响分析结论（流程可能未经影响分析，或评估人未填写专业结论）
        </span>
      </div>
      <a-table
        v-if="impactRows.length"
        :columns="impactColumns"
        :data-source="impactRows"
        size="small"
        row-key="rowKey"
        :pagination="false"
      >
        <template #bodyCell="{ column, record }">
          <template v-if="column.key === 'level'">
            <a-tag v-if="record.level" :color="ASSESSMENT_LEVEL_COLOR[record.level] || 'default'">
              {{ ASSESSMENT_LEVEL_LABEL[record.level] || record.level }}
            </a-tag>
            <span v-else class="eccb-empty">—</span>
          </template>
          <template v-else-if="column.key === 'concerns'">
            <span v-if="record.concerns.length">{{ record.concerns.join('、') }}</span>
            <span v-else class="eccb-empty">—</span>
          </template>
          <template v-else-if="column.key === 'note'">
            <span class="eccb-pre">{{ record.note || '—' }}</span>
          </template>
        </template>
      </a-table>
      <div v-else class="eccb-empty-line">
        没有可汇总的评估结论：请以变更请求的「发起时影响初判」为参考，并在决策意见中写明判断依据。
      </div>
    </div>

    <!-- ③ 会签进度：委员之间要看得见彼此的态度（这是一个集体决策，不是串行审批） -->
    <div class="eccb-card">
      <div class="eccb-card__head">
        <span class="eccb-card__title">CCB 会签情况</span>
        <span class="eccb-progress">
          {{ model.progressText }}
          <template v-if="tally.approve || tally.conditional || tally.reject">
            （批准 {{ tally.approve }} · 有条件批准 {{ tally.conditional }} · 驳回 {{ tally.reject }}）
          </template>
        </span>
        <span class="eccb-rule">{{ model.ruleText }}</span>
      </div>
      <a-table
        :columns="voteColumns"
        :data-source="voteRows"
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
          <template v-else-if="column.key === 'verdict'">
            <a-tag v-if="record.ccb" :color="VERDICT_COLOR[record.ccb.verdict] || 'default'">
              {{ VERDICT_LABEL[record.ccb.verdict] || record.ccb.verdict }}
            </a-tag>
            <a-tag v-else :color="stateOf(record).color">{{ stateOf(record).text }}</a-tag>
          </template>
          <template v-else-if="column.key === 'comment'">
            <span v-if="record.comment" class="eccb-pre">{{ stripMarkup(record.comment) }}</span>
            <span v-else class="eccb-empty">—</span>
          </template>
          <template v-else>
            <span v-if="record.timeText">{{ record.timeText }}</span>
            <span v-else class="eccb-empty">—</span>
          </template>
        </template>
      </a-table>
      <div v-if="!model.total" class="eccb-empty-line">暂未取到参与者（流程进度里还没有这条活动的记录）</div>
    </div>

    <!-- ④ 我这一票：结论 + 依据 + 逐项考量 + 条件 -->
    <div class="eccb-card eccb-mine">
      <div class="eccb-card__head">
        <span class="eccb-card__title">我的 CCB 决策</span>
        <span class="eccb-muted">按变更控制委员会的判断口径逐项给出结论，提交后记入会签意见</span>
      </div>

      <a-alert
        v-if="blockingMismatch"
        type="warning"
        show-icon
        class="eccb-alert"
        message="有考量项判为「不可接受」，不能给出「批准」"
        description="请改为「有条件批准」（写明条件与闭环要求）或「驳回」。"
      />

      <a-form layout="vertical">
        <a-form-item label="决策结论" required>
          <a-radio-group v-model:value="verdict" class="eccb-routes">
            <a-radio value="APPROVE">批准</a-radio>
            <a-radio value="CONDITIONAL">有条件批准</a-radio>
            <a-radio v-if="model.opinion.allowReject" value="REJECT">驳回</a-radio>
          </a-radio-group>
          <div class="eccb-hint">{{ verdictHint }}</div>
        </a-form-item>

        <a-form-item label="决策意见" required>
          <a-textarea
            v-model:value="basis"
            :rows="3"
            :maxlength="1000"
            show-count
            :placeholder="basisPlaceholder"
          />
        </a-form-item>

        <div class="eccb-sub">
          CCB 决策考量
          <span class="eccb-muted">（逐项给出结论；判为「有条件 / 不可接受」时说明必填）</span>
        </div>
        <a-table
          :columns="considerColumns"
          :data-source="considerations"
          size="small"
          row-key="key"
          :pagination="false"
          class="eccb-table"
        >
          <template #bodyCell="{ column, record }">
            <template v-if="column.key === 'label'">
              <a-tooltip :title="record.hint">
                <span class="eccb-consider">{{ record.label }}</span>
              </a-tooltip>
            </template>
            <template v-else-if="column.key === 'verdict'">
              <a-select
                v-model:value="record.verdict"
                size="small"
                allow-clear
                placeholder="选择结论"
                :options="CONSIDER_VERDICT_OPTIONS"
              />
            </template>
            <template v-else-if="column.key === 'note'">
              <a-input
                v-model:value="record.note"
                size="small"
                :placeholder="record.verdict === 'CONDITIONAL' || record.verdict === 'FAIL'
                  ? '说明（该结论下必填）' : '说明（可空）'"
              />
            </template>
          </template>
        </a-table>
        <div class="eccb-tally">
          <span>可接受 <b>{{ considerTally.PASS }}</b></span>
          <span>有条件 <b>{{ considerTally.CONDITIONAL }}</b></span>
          <span :class="{ 'eccb-danger': considerTally.FAIL > 0 }">不可接受 <b>{{ considerTally.FAIL }}</b></span>
          <span>不涉及 <b>{{ considerTally.NA }}</b></span>
          <span v-if="pendingConsiderCount" class="eccb-warn">还有 {{ pendingConsiderCount }} 项未判断</span>
        </div>

        <template v-if="verdict === 'CONDITIONAL'">
          <a-form-item label="附加条件 / 生效要求" required>
            <a-textarea
              v-model:value="conditions"
              :rows="3"
              :maxlength="1000"
              show-count
              placeholder="逐条写明条件、闭环要求与放行口径（例如：量产前完成认证重测并出具报告；在制 120 台返工后复检）"
            />
          </a-form-item>
          <a-row :gutter="12">
            <a-col :xs="24" :sm="8">
              <a-form-item label="条件闭环责任人">
                <a-input v-model:value="conditionOwner" placeholder="谁负责关闭条件" />
              </a-form-item>
            </a-col>
            <a-col :xs="24" :sm="8">
              <a-form-item label="目标完成日期" required>
                <a-date-picker
                  v-model:value="targetDate"
                  value-format="YYYY-MM-DD"
                  placeholder="条件必须在此之前闭环"
                  style="width: 100%"
                />
              </a-form-item>
            </a-col>
            <a-col :xs="24" :sm="8">
              <a-form-item label="是否开变更通告（ECN）" required>
                <a-radio-group v-model:value="ecnRequired">
                  <a-radio value="YES">需要</a-radio>
                  <a-radio value="NO">不需要</a-radio>
                </a-radio-group>
              </a-form-item>
            </a-col>
          </a-row>
        </template>

        <a-form-item v-else-if="verdict !== 'REJECT'" label="是否开变更通告（ECN）" required>
          <a-radio-group v-model:value="ecnRequired">
            <a-radio value="YES">需要（按本决策开 ECN 派发执行）</a-radio>
            <a-radio value="NO">不需要（无需派发执行）</a-radio>
          </a-radio-group>
        </a-form-item>
      </a-form>
    </div>
  </div>
</template>

<script setup>
import { computed, reactive, ref } from 'vue'
import { buildCountersignModel, validateCountersign } from '@/flow-designer/app/task/countersign'
import { useUserStore } from '@/stores/user'
import { ASSESSMENT_LEVEL_COLOR, ASSESSMENT_LEVEL_LABEL } from '@/utils/changeFlow'
import EcrSummaryPanel from './EcrSummaryPanel.vue'

/**
 * 「变更请求 · CCB 决策会签」表单（重大变更的集体决策环节）。
 *
 * <h3>为什么需要它</h3>
 * <p>本节点此前挂在平台内置的「审批意见」上：只有同意 / 驳回两条路由，CCB 真正要回答的问题
 * ——「按什么条件批准」「哪些方面不可接受」「要不要开 ECN、何时闭环」——全部无处落。
 * 影响分析会签已经把跨专业的结论做出来了，决策环节却看不到，只能凭记忆投票。
 *
 * <h3>表单结构（对应 CCB 决策的考量点）</h3>
 * <ol>
 *   <li><b>变更请求全貌</b>：改什么、为什么改、影响初判、受影响对象（{@link EcrSummaryPanel}，
 *       与影响分析会签同一块，不各写一份）；</li>
 *   <li><b>影响分析结论</b>：各专业的影响程度 / 关注点 / 评估说明 —— 决策的事实基础；</li>
 *   <li><b>会签情况</b>：谁投了什么、还差几票（集体决策必须看得见彼此态度）；</li>
 *   <li><b>我的决策</b>：结论（批准 / 有条件批准 / 驳回）+ 决策意见 + 九项考量逐项结论
 *       + 有条件批准时的附加条件、责任人、目标日期与 ECN 要求。</li>
 * </ol>
 *
 * <h3>为什么"逐项考量"要求全部给出结论</h3>
 * <p>CCB 的价值在于「按维度看过了」，不是一个"同意"按钮。允许留空，实际上就允许
 * "什么都没看直接批"。因此每项都必须选（含「不涉及」这个明确选项），且判为
 * 「有条件 / 不可接受」时必须说明 —— 结论要能被追溯。
 *
 * <h3>结论与流程动作的对应</h3>
 * <ul>
 *   <li>批准 / 有条件批准 → {@code approve}（本节点的通过规则是全部通过）；</li>
 *   <li>驳回 → {@code reject}（流程走「驳回」出边，单据置为已否决）。</li>
 * </ul>
 * <p>结构化结论另写流程变量 {@code ccbDecision__<taskId>}（按任务分键，避免多人互相覆盖），
 * 供本表汇总他人结论；同时拼进会签意见，让「流程进度」与其它视图也能看到。
 *
 * <p><b>数据来源</b>：变更请求从流程变量 {@code requestOid} 回查（ECR 不写
 * {@code ck_process_entity_set}，{@code form.businessOid} 恒为空）；影响分析结论读实例变量
 * {@code impactAssessment__*}（见 EcrImpactCountersignForm）。
 */
const props = defineProps({
  /** 表单上下文（ProcessTaskFormVO）：variables / taskDefinitionKey / dslJson */
  form: { type: Object, default: null },
  /** 当前任务 id：本人结论在流程变量里的键后缀 */
  taskId: { type: String, default: '' },
  /** 流程进度（TaskContextVO.activities）：同一会签活动下一人一条 */
  activities: { type: Array, default: () => [] }
})

const userStore = useUserStore()

const VERDICT_LABEL = { APPROVE: '批准', CONDITIONAL: '有条件批准', REJECT: '驳回' }
const VERDICT_COLOR = { APPROVE: 'green', CONDITIONAL: 'orange', REJECT: 'red' }
const VERDICT_HINT = {
  APPROVE: '无条件批准：变更按提交的方案执行，后续按 ECN 派发实施。',
  CONDITIONAL: '有条件批准：必须写明条件与闭环要求；条件未闭环前不应放行实施。',
  REJECT: '驳回：流程按「驳回」出边退回，单据将置为「已否决」。'
}

/**
 * CCB 的考量维度 —— 一张变更决策要回答的问题清单（与影响分析的专业视角互补：
 * 专业视角回答"影响到什么"，这里回答"整体是否可以放行"）。
 */
const CONSIDERATIONS = [
  { key: 'necessity', label: '变更必要性与范围', hint: '为什么要改、范围是否清楚、是否与在途变更重复' },
  { key: 'technical', label: '技术方案与可行性', hint: '方案能否落地、是否有更优替代、对现有设计的侵入程度' },
  { key: 'quality', label: '质量与可靠性风险', hint: '验证 / 测试是否充分、是否引入新的失效模式' },
  { key: 'certification', label: '认证与合规', hint: '是否需重新认证或报备（对应单据的认证影响）' },
  { key: 'customer', label: '客户与合同', hint: '是否需知会客户、是否触发合同变更或客户批准' },
  { key: 'cost', label: '成本影响', hint: '物料、工装、返工与认证成本是否可接受' },
  { key: 'schedule', label: '交期与产能', hint: '切换节奏、交付承诺与产能是否支撑' },
  { key: 'inventory', label: '在制与库存处置', hint: '在制盘点、报废返工、旧料消化是否有方案' },
  { key: 'execution', label: '实施与验证资源', hint: '责任人、验证方案、切换到量产的准备度' }
]

const CONSIDER_VERDICT_OPTIONS = [
  { value: 'PASS', label: '可接受' },
  { value: 'CONDITIONAL', label: '有条件' },
  { value: 'FAIL', label: '不可接受' },
  { value: 'NA', label: '不涉及' }
]
const CONSIDER_VERDICT_LABEL = {
  PASS: '可接受',
  CONDITIONAL: '有条件',
  FAIL: '不可接受',
  NA: '不涉及'
}

const IMPACT_PREFIX = 'impactAssessment__'
const VAR_PREFIX = 'ccbDecision__'

const verdict = ref(undefined)
const basis = ref('')
const conditions = ref('')
const conditionOwner = ref('')
const targetDate = ref(null)
const ecnRequired = ref(undefined)

/** 逐项考量：默认全空 —— 预填"可接受"等于替委员做了判断 */
const considerations = reactive(CONSIDERATIONS.map((item) => ({ ...item, verdict: undefined, note: '' })))

const impactColumns = [
  { key: 'name', title: '评估人', dataIndex: 'name', width: 110 },
  { key: 'discipline', title: '专业', dataIndex: 'discipline', width: 90 },
  { key: 'level', title: '影响程度', dataIndex: 'level', width: 110 },
  { key: 'concerns', title: '关注点', dataIndex: 'concerns', width: 190 },
  { key: 'note', title: '评估说明', dataIndex: 'note' },
  { key: 'time', title: '时间', dataIndex: 'timeText', width: 130 }
]

const voteColumns = [
  { key: 'name', title: '委员', dataIndex: 'name', width: 130 },
  { key: 'verdict', title: '决策结论', dataIndex: 'verdict', width: 120 },
  { key: 'time', title: '时间', dataIndex: 'timeText', width: 130 },
  { key: 'comment', title: '意见', dataIndex: 'comment' }
]

const considerColumns = [
  { key: 'label', title: '考量项', dataIndex: 'label', width: 170 },
  { key: 'verdict', title: '结论', dataIndex: 'verdict', width: 140 },
  { key: 'note', title: '说明', dataIndex: 'note' }
]

/** 会签模型：规则 + 参与人（复用内置会签的纯函数，规则解释不在这里重写） */
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
    currentTaskId: props.taskId
  })
})

/** 实例变量里的 JSON 值：历史脏数据不该让整张表渲染不出来 */
function parseVariable(value) {
  try {
    const parsed = typeof value === 'string' ? JSON.parse(value) : value
    return parsed && typeof parsed === 'object' ? parsed : null
  } catch {
    return null
  }
}

/** 影响分析结论：{@code impactAssessment__*} 逐条摊平（按时间排序，先评估的在上） */
const impactRows = computed(() => {
  const variables = props.form?.variables || {}
  const rows = []
  for (const [key, value] of Object.entries(variables)) {
    if (!key.startsWith(IMPACT_PREFIX)) continue
    const parsed = parseVariable(value)
    if (!parsed) continue
    rows.push({
      rowKey: parsed.taskId || key.slice(IMPACT_PREFIX.length),
      name: parsed.name || '—',
      discipline: parsed.discipline || '—',
      level: parsed.level || '',
      concerns: Array.isArray(parsed.concerns) ? parsed.concerns : [],
      note: parsed.note || '',
      timeText: formatTime(parsed.at),
      at: parsed.at || ''
    })
  }
  return rows.sort((a, b) => String(a.at).localeCompare(String(b.at)))
})

/** 各委员已提交的决策（按 taskId 建索引，与会签参与人一一对应） */
const ccbDecisions = computed(() => {
  const map = {}
  const variables = props.form?.variables || {}
  for (const [key, value] of Object.entries(variables)) {
    if (!key.startsWith(VAR_PREFIX)) continue
    const parsed = parseVariable(value)
    if (parsed) map[parsed.taskId || key.slice(VAR_PREFIX.length)] = parsed
  }
  return map
})

const voteRows = computed(() =>
  model.value.participants.map((item, index) => ({
    ...item,
    rowKey: item.taskId || `row-${index}`,
    ccb: ccbDecisions.value[item.taskId] || null
  }))
)

/** 会签口径的票数（按结构化结论统计；没填结论的只看流程 decision） */
const tally = computed(() => {
  const result = { approve: 0, conditional: 0, reject: 0 }
  for (const row of voteRows.value) {
    const code = row.ccb?.verdict
    if (code === 'APPROVE') result.approve++
    else if (code === 'CONDITIONAL') result.conditional++
    else if (code === 'REJECT') result.reject++
  }
  return result
})

const considerTally = computed(() => {
  const result = { PASS: 0, CONDITIONAL: 0, FAIL: 0, NA: 0 }
  for (const item of considerations) {
    if (result[item.verdict] !== undefined) result[item.verdict]++
  }
  return result
})

const pendingConsiderCount = computed(
  () => considerations.filter((item) => !item.verdict).length
)

/** 考量项与总结论冲突：「不可接受」不能配上「批准」 */
const blockingMismatch = computed(
  () => verdict.value === 'APPROVE' && considerations.some((item) => item.verdict === 'FAIL')
)

const verdictHint = computed(() => {
  if (!verdict.value) {
    return model.value.opinion.allowReject
      ? '选择本次决策的结论：批准 / 有条件批准 / 驳回。'
      : '本节点未配置驳回：请选择批准或有条件批准。'
  }
  if (verdict.value === 'REJECT') {
    return `驳回：${model.value.opinion.rejectTargetText}`
  }
  return VERDICT_HINT[verdict.value] || ''
})

const basisPlaceholder = computed(() => {
  if (verdict.value === 'REJECT') return '请写明驳回理由：哪些影响不可接受、需要补充什么后再议'
  if (verdict.value === 'CONDITIONAL') return '写明拍板依据与条件之间的关系：影响分析中哪几条促成了「有条件批准」'
  return '写明拍板依据：影响分析结论、风险评估与资源准备情况'
})

function stateOf(record) {
  if (!record.done) return { text: '未办理', color: 'default' }
  if (record.decision === 'APPROVE') return { text: '已同意', color: 'green' }
  if (record.decision === 'REJECT') return { text: '已驳回', color: 'red' }
  return { text: '已办理', color: 'default' }
}

function rowClassOf(record) {
  return record?.isMe ? 'eccb-row-me' : ''
}

/** 会签意见里我们自己加的抬头（结论已在「决策结论」列体现，不重复占版面） */
function stripMarkup(text) {
  return String(text || '').replace(/^【[^】]*】\s*/, '')
}

function formatTime(value) {
  if (!value) return ''
  const date = new Date(value)
  if (Number.isNaN(date.getTime())) return String(value)
  const pad = (n) => String(n).padStart(2, '0')
  return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())} `
    + `${pad(date.getHours())}:${pad(date.getMinutes())}`
}

/* ==================== 提交契约 ==================== */

const submitText = computed(() => {
  if (verdict.value === 'REJECT') return '驳回并提交'
  if (verdict.value === 'CONDITIONAL') return '有条件批准并提交'
  return '批准并提交'
})

const successText = computed(() => {
  if (verdict.value === 'REJECT') return '已驳回，流程按驳回路径退回，单据将置为已否决'
  if (verdict.value === 'CONDITIONAL') return '已提交：有条件批准，条件闭环后再放行实施'
  return '已批准，会签按配置规则继续'
})

/** 取不到单据 / 评估结论也要能办完这一票：本表单永远可渲染，只是少几块只读信息 */
const renderable = computed(() => true)

/** 意见文本：结论抬头 + 决策意见 + 考量摘要 + 条件 —— 保证别的视图不依赖本表也能读懂 */
function buildComment() {
  const level = VERDICT_LABEL[verdict.value] || verdict.value
  const parts = [`【CCB｜${level}】${basis.value.trim()}`]

  const judged = considerations.filter((item) => item.verdict)
  if (judged.length) {
    parts.push(
      '考量：' + judged
        .map((item) => `${item.label}=${CONSIDER_VERDICT_LABEL[item.verdict]}${item.note.trim() ? `（${item.note.trim()}）` : ''}`)
        .join('；')
    )
  }
  if (verdict.value === 'CONDITIONAL') {
    const owner = conditionOwner.value.trim() ? `，责任人 ${conditionOwner.value.trim()}` : ''
    parts.push(`附加条件：${conditions.value.trim()}（目标完成 ${targetDate.value}${owner}）`)
  }
  if (verdict.value !== 'REJECT' && ecnRequired.value) {
    parts.push(ecnRequired.value === 'YES' ? '开变更通告（ECN）派发执行' : '不开变更通告')
  }
  return parts.join(' ').trim()
}

function collect() {
  if (!verdict.value) {
    return { ok: false, message: '请选择决策结论' }
  }
  if (!basis.value.trim()) {
    return { ok: false, message: '请填写决策意见（决策依据靠它追溯）' }
  }
  if (pendingConsiderCount.value > 0) {
    const pending = considerations.filter((item) => !item.verdict).map((item) => item.label)
    const names = pending.slice(0, 3).join('、') + (pending.length > 3 ? ` 等 ${pending.length} 项` : '')
    return { ok: false, message: `请逐项给出 CCB 决策考量结论：${names}` }
  }
  if (blockingMismatch.value) {
    return {
      ok: false,
      message: '有考量项判为「不可接受」：请改为「有条件批准」（写明条件）或「驳回」'
    }
  }
  const missingNote = considerations.find(
    (item) => (item.verdict === 'CONDITIONAL' || item.verdict === 'FAIL') && !item.note.trim()
  )
  if (missingNote) {
    return { ok: false, message: `「${missingNote.label}」判为${CONSIDER_VERDICT_LABEL[missingNote.verdict]}，请填写说明` }
  }
  if (verdict.value === 'CONDITIONAL') {
    if (!conditions.value.trim()) {
      return { ok: false, message: '「有条件批准」必须写明附加条件 / 生效要求' }
    }
    if (!targetDate.value) {
      return { ok: false, message: '「有条件批准」必须给出条件的目标完成日期' }
    }
  }
  if (verdict.value !== 'REJECT' && !ecnRequired.value) {
    return { ok: false, message: '请选择是否需要开变更通告（ECN）' }
  }

  const action = verdict.value === 'REJECT' ? 'reject' : 'approve'
  const comment = buildComment()
  const issue = validateCountersign(model.value, action, comment)
  if (issue) {
    return { ok: false, message: issue }
  }

  return {
    ok: true,
    payload: {
      action,
      comment,
      variables: {
        [`${VAR_PREFIX}${props.taskId}`]: JSON.stringify({
          taskId: props.taskId,
          name: userStore.displayName || userStore.username || '',
          verdict: verdict.value,
          basis: basis.value.trim(),
          considerations: considerations.map((item) => ({
            key: item.key,
            label: item.label,
            verdict: item.verdict,
            note: item.note.trim()
          })),
          conditions: verdict.value === 'CONDITIONAL' ? conditions.value.trim() : null,
          conditionOwner: verdict.value === 'CONDITIONAL' ? (conditionOwner.value.trim() || null) : null,
          targetDate: verdict.value === 'CONDITIONAL' ? targetDate.value : null,
          ecnRequired: verdict.value === 'REJECT' ? null : ecnRequired.value,
          at: new Date().toISOString()
        })
      }
    }
  }
}

defineExpose({ collect, submitText, successText, renderable })
</script>

<style scoped>
.eccb-card {
  margin-bottom: 16px;
  padding: 12px 14px;
  border: 1px solid #f0f0f0;
  border-radius: 8px;
  background: #fcfcfc;
}

.eccb-card__head {
  display: flex;
  align-items: baseline;
  flex-wrap: wrap;
  gap: 4px 10px;
  margin-bottom: 10px;
}

.eccb-card__title {
  font-size: 13px;
  font-weight: 600;
  color: #262626;
}

.eccb-progress,
.eccb-rule {
  font-size: 12px;
  color: #8c8c8c;
}

.eccb-warn {
  font-size: 12px;
  color: #d46b08;
}

.eccb-muted {
  font-size: 12px;
  color: #8c8c8c;
}

.eccb-hint {
  margin-top: 4px;
  font-size: 12px;
  color: #8c8c8c;
  line-height: 1.6;
}

.eccb-pre {
  white-space: pre-wrap;
  line-height: 1.6;
}

.eccb-empty {
  color: #bfbfbf;
}

.eccb-empty-line {
  margin-top: 6px;
  font-size: 12px;
  color: #8c8c8c;
}

.eccb-mine {
  background: #fff;
  border-color: #d6e4ff;
}

.eccb-alert {
  margin-bottom: 12px;
}

.eccb-sub {
  margin: 4px 0 8px;
  padding-left: 8px;
  border-left: 3px solid #1677ff;
  font-size: 13px;
  font-weight: 600;
  color: #333;
}

.eccb-table {
  margin-bottom: 8px;
}

.eccb-consider {
  cursor: help;
  border-bottom: 1px dashed #d9d9d9;
}

.eccb-tally {
  display: flex;
  flex-wrap: wrap;
  gap: 14px;
  margin: 0 0 14px;
  font-size: 12px;
  color: #666;
}

.eccb-danger {
  color: #cf1322;
}

.eccb-routes {
  margin-bottom: 2px;
}

:deep(.eccb-row-me) > td {
  background: #f0f5ff;
}

:deep(.eccb-row-me) > td:first-child {
  border-left: 3px solid #1677ff;
}
</style>
