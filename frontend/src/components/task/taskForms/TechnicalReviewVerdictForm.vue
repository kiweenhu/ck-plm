<template>
  <div class="trv">
    <a-spin :spinning="loading">
      <!-- 只读：上游「技术评审申请单」内容（各 TR 节点共用同一个摘要组件） -->
      <TechnicalReviewRequestSummary :task-id="taskId" />

      <!-- 这是技术评审的"出口留痕"：结论 + 依据 + 遗留条件。
           列表页（ReviewProcessEvaluator）读 ckplmTechReview_verdict 显示这条评审的结论，
           所以本表提交时必须把最终结论写回那个变量，否则列表永远是「未评定」。 -->
      <div class="trv-intro">
        汇总本次评审的检查结论与问题闭环情况，登记<b>最终结论</b>与依据。
        提交后该结论会写回流程变量，产品详情的评审记录据此显示"通过 / 有条件通过 / 不通过"。
      </div>

      <div class="trv-summary">
        <div class="trv-summary__item">
          <span class="trv-summary__label">检查结论</span>
          <a-tag v-if="upstream.verdict" :color="verdictColor(upstream.verdict)">
            {{ VERDICT_LABEL[upstream.verdict] || upstream.verdict }}
          </a-tag>
          <span v-else class="trv-muted">未取到</span>
        </div>
        <div class="trv-summary__item">
          <span class="trv-summary__label">不符合项</span>
          <span>{{ upstream.failCount }} 项<template v-if="upstream.blockCount"> （BLOCK {{ upstream.blockCount }}）</template></span>
        </div>
        <div class="trv-summary__item">
          <span class="trv-summary__label">问题闭环</span>
          <span v-if="issueStats.known">
            共 {{ issueStats.total }} 条 · 已关闭 {{ issueStats.closed }} · 未闭环 {{ issueStats.open }}
            <template v-if="issueStats.openBlock"> · 其中 BLOCK {{ issueStats.openBlock }}</template>
          </span>
          <span v-else class="trv-muted">未取到问题清单（仅按结论判断）</span>
        </div>
        <a-tag v-if="issueStats.openBlock" color="error">仍有 BLOCK 级问题未闭环</a-tag>
      </div>

      <a-form layout="vertical">
        <a-form-item label="最终结论" required>
          <a-radio-group v-model:value="model.verdict">
            <a-radio v-for="opt in VERDICT_OPTIONS" :key="opt.value" :value="opt.value">{{ opt.label }}</a-radio>
          </a-radio-group>
        </a-form-item>

        <a-form-item label="结论依据" required>
          <a-textarea
            v-model:value="model.basis"
            :rows="3"
            :maxlength="1000"
            placeholder="写清拍板依据：检查项结论、问题闭环情况、复评意见、遗留风险等"
          />
        </a-form-item>

        <a-form-item label="遗留条件" :required="model.verdict === 'PASS_WITH_CONDITION'">
          <a-textarea
            v-model:value="model.conditions"
            :rows="3"
            :maxlength="1000"
            placeholder="结论为「有条件通过」时必填：逐条写明条件、责任人与闭环期限（例如：量产前关闭 2 个 BLOCK 问题，责任人：张三）"
          />
        </a-form-item>

        <a-form-item label="意见说明">
          <a-textarea
            v-model:value="model.comment"
            :rows="2"
            :maxlength="500"
            placeholder="选填：补充说明（提交后记入本节点的办理意见）"
          />
        </a-form-item>
      </a-form>
    </a-spin>
  </div>
</template>

<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { getIssueDetail, getProcessInstanceDetail, getTaskContext } from '@/api'
import TechnicalReviewRequestSummary from './TechnicalReviewRequestSummary.vue'

/**
 * 「结论登记（留痕）」节点表单 —— 技术评审流程 n_verdict。
 *
 * <h3>为什么需要</h3>
 * <p>上游三个节点各自产出事实（检查结论、问题、复评结果），但<b>没有一处落最终结论</b>：
 * {@code ReviewProcessEvaluator} 在产品评审记录里读 {@code ckplmTechReview_verdict}，
 * 注释写着"预留给结论登记节点；当前流程未采集，取不到即留空"—— 于是列表永远显示「未评定」。
 * 本表把这件事补上：结论 + 依据 + 遗留条件结构化留痕，并写回该变量。
 *
 * <h3>动作口径：恒为 approve</h3>
 * <p>该节点是流程最后一个办理节点，出口只有一条（→ 结束）。用 {@code reject} 会去找「驳回」出边，
 * 而这条流程没有画 —— 那会导致提交直接失败。结论本身是<b>数据</b>（PASS / PASS_WITH_CONDITION / FAIL），
 * 不是路由：不通过也要正常走到结束并留下记录，而不是把流程堵在这里。
 *
 * <h3>与阶段门同口径的硬约束</h3>
 * <p>仍有 BLOCK 级问题未闭环时不允许选「通过」—— 阶段门的 ISSUE_OPEN 检查用的是同一判据，
 * 在这里放过去，等于让门检查事后打脸。
 */
const props = defineProps({
  /** 任务 id —— 取流程变量与问题闭环情况 */
  taskId: { type: String, default: '' },
  /** 表单上下文（ProcessTaskFormVO） */
  form: { type: Object, default: null },
})

const VERDICT_OPTIONS = [
  { value: 'PASS', label: '通过' },
  { value: 'PASS_WITH_CONDITION', label: '有条件通过' },
  { value: 'FAIL', label: '不通过' },
]
const VERDICT_LABEL = { PASS: '通过', PASS_WITH_CONDITION: '有条件通过', FAIL: '不通过' }

const loading = ref(true)
const upstream = reactive({ verdict: '', failCount: 0, blockCount: 0 })
/** 问题闭环统计；known=false 表示本次流程没记录问题编号，拿不到可靠清单 */
const issueStats = reactive({ known: false, total: 0, closed: 0, open: 0, openBlock: 0 })

const model = reactive({
  verdict: 'PASS',
  basis: '',
  conditions: '',
  comment: '',
})

function verdictColor(code) {
  if (code === 'FAIL') return 'red'
  if (code === 'PASS_WITH_CONDITION') return 'orange'
  return 'green'
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
    const ctxRes = props.taskId ? await getTaskContext(props.taskId).catch(() => null) : null
    const ctx = ctxRes?.code === 200 ? ctxRes.data : null
    const instId = ctx?.process?.id || null
    const detailRes = instId ? await getProcessInstanceDetail(instId).catch(() => null) : null
    const variables = detailRes?.code === 200 ? detailRes.data?.variables : null

    const verdict = String(variableOf(variables, 'ckplmTechReview_verdict') || '')
    if (verdict) {
      upstream.verdict = verdict
      model.verdict = verdict
    }
    upstream.failCount = Number(variableOf(variables, 'ckplmTechReview_failCount') || 0)
    upstream.blockCount = Number(variableOf(variables, 'ckplmTechReview_blockCount') || 0)
    model.conditions = String(variableOf(variables, 'ckplmTechReview_conditionList') || '')

    // 问题闭环情况：按登记节点写下的 oid 列表精确取
    const oids = String(variableOf(variables, 'ckplmTechReview_issueOids') || '')
      .split(',').map((s) => s.trim()).filter(Boolean)
    if (!oids.length) return

    const issues = []
    for (const oid of oids) {
      const res = await getIssueDetail(oid).catch(() => null)
      if (res?.code === 200 && res.data?.issue) issues.push(res.data.issue)
    }
    issueStats.known = true
    issueStats.total = issues.length
    issueStats.closed = issues.filter((it) => it.status === 'CLOSED').length
    issueStats.open = issues.filter((it) => it.status !== 'CLOSED' && it.status !== 'CANCELLED').length
    issueStats.openBlock = issues.filter(
      (it) => it.severity === 'BLOCK' && it.status !== 'CLOSED' && it.status !== 'CANCELLED',
    ).length
  } finally {
    loading.value = false
  }
}

onMounted(load)

/* ==================== 提交契约 ==================== */

const submitText = computed(() => `登记结论（${VERDICT_LABEL[model.verdict] || model.verdict}）`)
const successText = computed(() => '结论已登记，流程走向结束')
const renderable = computed(() => true)

function collect() {
  if (!model.verdict) {
    return { ok: false, message: '请选择最终结论' }
  }
  if (!model.basis.trim()) {
    return { ok: false, message: '请填写结论依据（追溯靠它）' }
  }
  if (model.verdict === 'PASS_WITH_CONDITION' && !model.conditions.trim()) {
    return { ok: false, message: '结论为「有条件通过」时必须填写遗留条件' }
  }
  // 与阶段门 ISSUE_OPEN 同一判据：BLOCK 未闭环不能判"通过"
  if (model.verdict === 'PASS' && issueStats.openBlock > 0) {
    return {
      ok: false,
      message: `还有 ${issueStats.openBlock} 个 BLOCK 级问题未闭环：请改为「有条件通过」（写明条件）或「不通过」`,
    }
  }

  const label = VERDICT_LABEL[model.verdict] || model.verdict
  return {
    ok: true,
    payload: {
      // 末节点且只有一条出口：结论是数据不是路由（用它去 reject 会因没有驳回边而提交失败）
      action: 'approve',
      comment: model.comment.trim() || `评审结论：${label}`,
      variables: {
        // 关键：列表页（ReviewProcessEvaluator）读的就是它
        ckplmTechReview_verdict: model.verdict,
        ckplmTechReview_finalVerdict: model.verdict,
        ckplmTechReview_verdictBasis: model.basis.trim(),
        ckplmTechReview_finalConditions: model.conditions.trim() || null,
        ckplmTechReview_openIssueCount: issueStats.known ? issueStats.open : null,
        ckplmTechReview_openBlockCount: issueStats.known ? issueStats.openBlock : null,
      },
    },
  }
}

defineExpose({ collect, submitText, successText, renderable })
</script>

<style scoped>
.trv-intro {
  margin-bottom: 12px;
  padding: 8px 12px;
  background: #f6f9ff;
  border-left: 3px solid #1677ff;
  border-radius: 4px;
  font-size: 12px;
  color: #595959;
  line-height: 1.7;
}

.trv-summary {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 8px 18px;
  margin-bottom: 16px;
  padding: 10px 12px;
  background: #fafafa;
  border: 1px solid #f0f0f0;
  border-radius: 6px;
  font-size: 13px;
  color: #333;
}

.trv-summary__item {
  display: inline-flex;
  align-items: center;
  gap: 6px;
}

.trv-summary__label {
  font-size: 12px;
  color: #8c8c8c;
}

.trv-muted {
  color: #bfbfbf;
  font-size: 12px;
}
</style>
