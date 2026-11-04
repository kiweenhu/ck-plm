<template>
  <a-form layout="vertical">
    <!-- ⓪ 只读：上游「技术评审申请单」填过的内容。
         逐条核对之前先得看清"评的是谁、按哪个模板、送了什么材料"，
         否则检查项是悬空的（2026-10-29 用户反馈）。各 TR 节点共用同一个摘要组件 -->
    <TechnicalReviewRequestSummary :task-id="taskId" class="trc-request" />

    <div class="trc-section">检查项逐条结论</div>

    <a-spin :spinning="loading">
      <a-alert
        v-if="!loading && !items.length"
        type="warning"
        show-icon
        class="trc-alert"
        message="未取到检查项清单"
        description="检查项来自上游「技术评审申请单」选定的评审模板；若流程未经该节点（或模板未配检查项），此处为空 —— 仍可填写结论与意见完成办理。"
      />
      <a-table
        v-else
        :columns="COLUMNS"
        :data-source="items"
        :pagination="false"
        row-key="seq"
        size="small"
        class="trc-table"
      >
        <template #bodyCell="{ column, record }">
          <template v-if="column.key === 'severity'">
            <a-tag :color="record.severity === 'BLOCK' ? 'error' : 'warning'">{{ record.severity || '—' }}</a-tag>
          </template>
          <template v-else-if="column.key === 'result'">
            <a-radio-group v-model:value="record.result" size="small">
              <a-radio-button value="PASS">符合</a-radio-button>
              <a-radio-button value="FAIL">不符合</a-radio-button>
              <a-radio-button value="NA">不适用</a-radio-button>
            </a-radio-group>
          </template>
          <template v-else-if="column.key === 'note'">
            <a-input v-model:value="record.note" size="small" placeholder="不符合说明（可空）" />
          </template>
        </template>
      </a-table>
    </a-spin>

    <div v-if="items.length" class="trc-summary">
      <span>不符合项：<b :class="{ 'trc-danger': failCount > 0 }">{{ failCount }}</b> 项</span>
      <span>其中 BLOCK 级：<b :class="{ 'trc-danger': blockFailCount > 0 }">{{ blockFailCount }}</b> 项</span>
      <a-tag v-if="blockFailCount > 0" color="error">存在未闭环的阻断项</a-tag>
      <a-tag v-else color="success">无阻断项</a-tag>
    </div>

    <div class="trc-section">评审结论</div>

    <a-form-item label="结论" required>
      <a-radio-group v-model:value="model.verdict">
        <a-radio v-for="opt in verdictOptions" :key="opt.value" :value="opt.value">{{ opt.label }}</a-radio>
      </a-radio-group>
    </a-form-item>

    <a-form-item label="附加条件" :required="model.verdict === 'PASS_WITH_CONDITION'">
      <a-textarea v-model:value="model.conditionList" :rows="3"
        placeholder="结论为「有条件通过」时必填：逐条写明条件与闭环要求" />
    </a-form-item>

    <a-form-item label="意见说明" :required="commentRequired">
      <a-textarea v-model:value="model.comment" :rows="3"
        :placeholder="commentRequired ? '该活动要求填写意见' : '补充说明（提交后记入本节点的审批意见）'" />
    </a-form-item>
  </a-form>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { getTaskContext, getProcessInstanceDetail, listReviewTemplates } from '@/api'
import { variableOf } from './variables'
import TechnicalReviewRequestSummary from './TechnicalReviewRequestSummary.vue'

/**
 * 「技术评审检查表」表单 —— 评审活动逐条核对（行业包 `electronics-core` 的
 * `technical-review-checklist`，组件名字由包 `forms.json` 标注、运行期按名派发）。
 *
 * <h3>检查项从哪来</h3>
 * <p>不手填：上游「技术评审申请单」选定的评审模板码经流程变量
 * {@code ckplmTechReview_reviewTemplate} 传下来 → 本组件按该 code 找到评审模板
 * （{@code /review-templates}）→ 解析其 {@code checkItems}（JSON：
 * {@code [{seq, text, severity}]}）逐条列出。取不到给明确提示但仍可办理
 * （评审不该因为"清单拉不出来"就卡住）。
 *
 * <h3>结论与动作的对应</h3>
 * <ul>
 *   <li>通过 / 有条件通过 → {@code approve}（有条件通过时"附加条件"必填）；</li>
 *   <li>不通过 → {@code reject}（流程按节点的驳回配置退回）。</li>
 * </ul>
 * <p>{@code blockCount} 由表格实时算（不符合且 BLOCK 级），与门上的 ISSUE_OPEN 检查同源。
 * 变量统一 {@code ckplmTechReview_} 前缀。
 */
const props = defineProps({
  /** 任务 id —— 取上下文与流程变量用 */
  taskId: { type: String, default: '' },
  /** 表单上下文（ProcessTaskFormVO）：formKey / taskDefinitionKey / dslJson */
  form: { type: Object, default: null },
})

const COLUMNS = [
  { title: '#', dataIndex: 'seq', key: 'seq', width: 56 },
  { title: '检查项', dataIndex: 'text', key: 'text' },
  { title: '级别', key: 'severity', width: 90 },
  { title: '结论', key: 'result', width: 250 },
  { title: '说明', key: 'note', width: 200 },
]

const loading = ref(true)
const items = ref([])
const reviewOptions = ref([])
const reviewLabel = ref('')
/** 本次实际命中的评审模板（拿它的 checkItems / verdictOptions） */
const currentTemplate = ref(null)
const commentRequired = computed(() => {
  const raw = props.form?.dslJson
  if (!raw) return false
  try {
    const node = (JSON.parse(raw)?.nodes || []).find((n) => n.id === props.form?.taskDefinitionKey)
    return node?.commentRequired === true
  } catch {
    return false
  }
})

const model = ref({
  verdict: 'PASS',
  conditionList: '',
  comment: '',
})

/** 结论选项：优先用评审模板自带的 verdictOptions，缺省用三值口径 */
const DEFAULT_VERDICTS = [
  { value: 'PASS', label: '通过' },
  { value: 'PASS_WITH_CONDITION', label: '有条件通过' },
  { value: 'FAIL', label: '不通过' },
]
const VERDICT_LABEL = {
  PASS: '通过', PASS_WITH_CONDITION: '有条件通过', FAIL: '不通过',
}
const verdictOptions = computed(() => {
  const tpl = currentTemplate.value
  if (!tpl?.verdictOptions) return DEFAULT_VERDICTS
  try {
    const codes = JSON.parse(tpl.verdictOptions)
    if (!Array.isArray(codes) || !codes.length) return DEFAULT_VERDICTS
    return codes.map((code) => ({ value: code, label: VERDICT_LABEL[code] || code }))
  } catch {
    return DEFAULT_VERDICTS
  }
})

const failCount = computed(() => items.value.filter((it) => it.result === 'FAIL').length)
const blockFailCount = computed(
  () => items.value.filter((it) => it.result === 'FAIL' && it.severity === 'BLOCK').length,
)

async function load() {
  loading.value = true
  try {
    // 1) 评审模板清单（用于把 code 翻成检查项；顺带拿到 verdictOptions）
    const reviewRes = await listReviewTemplates().catch(() => null)
    const templates = reviewRes?.code === 200 ? (reviewRes.data || []) : []

    // 2) 从流程变量取上游选定的评审模板码（申请内容的展示交给摘要组件，此处只要码）
    let templateCode = null
    if (props.taskId) {
      const ctxRes = await getTaskContext(props.taskId).catch(() => null)
      const instId = ctxRes?.code === 200 ? ctxRes.data?.process?.id : null
      if (instId) {
        const detailRes = await getProcessInstanceDetail(instId).catch(() => null)
        templateCode = detailRes?.code === 200
          ? variableOf(detailRes.data?.variables, 'ckplmTechReview_reviewTemplate') : null
      }
    }

    const tpl = templates.find((t) => t.code === templateCode)
      || (templateCode ? null : templates[0] || null)
    currentTemplate.value = tpl
    reviewOptions.value = templates
    reviewLabel.value = tpl ? (tpl.name || tpl.code) : (templateCode || '')

    // 3) 解析检查项
    let checkItems = []
    if (tpl?.checkItems) {
      try {
        const parsed = JSON.parse(tpl.checkItems)
        if (Array.isArray(parsed)) checkItems = parsed
      } catch { /* 配错就当没有检查项，不挡办理 */ }
    }
    items.value = checkItems.map((it, idx) => ({
      seq: it.seq ?? (idx + 1),
      text: it.text || it.name || '',
      severity: it.severity || 'WARN',
      result: 'PASS',
      note: '',
    }))
  } catch {
    items.value = []
  } finally {
    loading.value = false
  }
}

onMounted(load)

const submitText = computed(() => (model.value.verdict === 'FAIL' ? '提交不通过' : '提交评审结论'))
const successText = computed(() => (model.value.verdict === 'FAIL'
  ? '评审不通过，流程按驳回配置退回' : '评审结论已提交，流程继续流转'))
/** 只依赖自身字段（清单拉不到也能填结论），任何情况下都可渲染 */
const renderable = computed(() => true)

function collect() {
  if (!model.value.verdict) {
    return { ok: false, message: '请选择评审结论' }
  }
  if (model.value.verdict === 'PASS_WITH_CONDITION' && !model.value.conditionList.trim()) {
    return { ok: false, message: '结论为「有条件通过」时必须填写附加条件' }
  }
  if (commentRequired.value && !model.value.comment.trim()) {
    return { ok: false, message: '该活动要求填写意见' }
  }
  return {
    ok: true,
    payload: {
      action: model.value.verdict === 'FAIL' ? 'reject' : 'approve',
      comment: model.value.comment.trim() || `评审结论：${VERDICT_LABEL[model.value.verdict] || model.value.verdict}`,
      variables: {
        // 写**评审码**而不是展示名：下游（问题登记取检查项原文、评审清单认领流程实例）都按 code 匹配。
        // 2026-10-29：这里原来写 reviewLabel（模板名），把申请节点写的 TR1_CONCEPT 覆盖成了
        // 「概念与需求评审」，评审清单于是永远认不到这条实例，流程走完还显示「未开始 / 未评审」。
        ckplmTechReview_reviewTemplate: currentTemplate.value?.code || reviewLabel.value || null,
        ckplmTechReview_verdict: model.value.verdict,
        ckplmTechReview_blockCount: blockFailCount.value,
        ckplmTechReview_failCount: failCount.value,
        ckplmTechReview_conditionList: model.value.conditionList.trim() || null,
        // 逐条结论留给追溯（仅记"不符合"的项，避免流程变量过大）
        ckplmTechReview_failItems: JSON.stringify(
          items.value.filter((it) => it.result === 'FAIL')
            .map((it) => ({ seq: it.seq, severity: it.severity, note: it.note || '' })),
        ),
      },
    },
  }
}

defineExpose({ collect, submitText, successText, renderable })
</script>

<style scoped>
.trc-section {
  margin: 6px 0 12px;
  padding-left: 8px;
  border-left: 3px solid #1677ff;
  font-weight: 600;
  color: #333;
}

/* 申请信息（只读）：读完就往下核对，和检查项表格之间留出一段的呼吸 */
.trc-request {
  margin-bottom: 6px;
}

.trc-alert {
  margin-bottom: 12px;
}

.trc-table {
  margin-bottom: 10px;
}

.trc-summary {
  display: flex;
  align-items: center;
  gap: 14px;
  margin-bottom: 6px;
  font-size: 12px;
  color: #666;
}

.trc-danger {
  color: #cf1322;
}
</style>
