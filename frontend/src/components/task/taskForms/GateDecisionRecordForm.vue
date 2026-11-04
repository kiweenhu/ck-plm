<template>
  <a-form layout="vertical">
    <div class="gdr-section">决策结论</div>

    <a-form-item label="决策" required>
      <a-radio-group v-model:value="model.decision">
        <a-radio v-for="opt in DECISION_OPTIONS" :key="opt.value" :value="opt.value">
          {{ opt.label }}
        </a-radio>
      </a-radio-group>
      <template #extra>
        <span class="gdr-extra">
          决策门（CHARTER / PDCP / ADCP / LDCP）用 GO / HOLD / TERMINATE；
          技术门（TR·）用 GO / GO_WITH_CONDITION / RECYCLE。
          提交时：GO / GO_WITH_CONDITION → 通过；HOLD / TERMINATE / RECYCLE → 驳回（流程按配置退回）。
        </span>
      </template>
    </a-form-item>

    <a-form-item label="决策依据" required>
      <a-textarea v-model:value="model.decisionBasis" :rows="4"
        placeholder="写清拍板所依据的事实：交付物齐套情况、评审结论、问题闭环、供应与认证状态等" />
    </a-form-item>

    <div class="gdr-section">三类风险</div>

    <a-row :gutter="12">
      <a-col :span="8">
        <a-form-item label="商业风险">
          <a-textarea v-model:value="model.businessRisk" :rows="3" placeholder="市场 / 成本 / 竞争" />
        </a-form-item>
      </a-col>
      <a-col :span="8">
        <a-form-item label="技术风险">
          <a-textarea v-model:value="model.technicalRisk" :rows="3" placeholder="设计 / 验证 / 工艺" />
        </a-form-item>
      </a-col>
      <a-col :span="8">
        <a-form-item label="供应风险">
          <a-textarea v-model:value="model.supplyRisk" :rows="3" placeholder="渠道 / 交期 / 单一来源" />
        </a-form-item>
      </a-col>
    </a-row>

    <div class="gdr-section">留痕补充</div>

    <a-form-item label="附加条件">
      <a-textarea v-model:value="model.conditionList" :rows="3"
        placeholder="决策带条件时逐条写明（如：GO 但须在 LDCP 前关闭 3 个 BLOCK 项）" />
    </a-form-item>

    <a-form-item label="表决结果">
      <a-textarea v-model:value="model.voteResult" :rows="2"
        placeholder="技术门会签时用：如「5 票同意 / 1 票反对」" />
    </a-form-item>

    <a-form-item label="意见说明">
      <a-textarea v-model:value="model.comment" :rows="3"
        placeholder="补充说明（提交后记入本节点的办理意见）" />
    </a-form-item>
  </a-form>
</template>

<script setup>
import { ref, computed } from 'vue'

/**
 * 「决策结论登记表」表单 —— 门评审结论留痕（行业包 `electronics-core` 的
 * `gate-decision-record`，组件名字由包 `forms.json` 标注、运行期按名派发）。
 *
 * <h3>为什么单独一张"登记表"（而不是靠审批意见）</h3>
 * <p>审厂与追溯看的正是这张表：决策 + <b>三类风险</b>（商业 / 技术 / 供应）+ 决策依据 + 附加条件
 * + 表决结果 —— 意见栏一两句话承载不了，事后复盘时"当时怎么想的"必须结构化留存
 * （行业包 decision-gates.json 的 {@code recordFields} 与本表逐项对应）。
 *
 * <h3>决策 → 动作的映射</h3>
 * <ul>
 *   <li>GO / GO_WITH_CONDITION → {@code approve}（流程继续）；</li>
 *   <li>HOLD / TERMINATE / RECYCLE → {@code reject}（流程按节点的驳回配置退回）。</li>
 * </ul>
 * <p>变量统一 {@code ckplmGateRecord_} 前缀。
 */
const props = defineProps({
  /** 任务 id（本表不依赖上下文，保留以满足统一契约） */
  taskId: { type: String, default: '' },
  /** 表单上下文（ProcessTaskFormVO） */
  form: { type: Object, default: null },
})

const DECISION_OPTIONS = [
  { value: 'GO', label: 'GO —— 放行' },
  { value: 'GO_WITH_CONDITION', label: 'GO_WITH_CONDITION —— 有条件放行' },
  { value: 'HOLD', label: 'HOLD —— 暂缓' },
  { value: 'TERMINATE', label: 'TERMINATE —— 终止' },
  { value: 'RECYCLE', label: 'RECYCLE —— 退回重做' },
]

/** 驳回类决策（提交时走 reject） */
const REJECT_DECISIONS = ['HOLD', 'TERMINATE', 'RECYCLE']

const model = ref({
  decision: 'GO',
  decisionBasis: '',
  businessRisk: '',
  technicalRisk: '',
  supplyRisk: '',
  conditionList: '',
  voteResult: '',
  comment: '',
})

const submitText = computed(() => (REJECT_DECISIONS.includes(model.value.decision)
  ? `提交（${model.value.decision}，退回）` : `提交（${model.value.decision}）`))
const successText = computed(() => (REJECT_DECISIONS.includes(model.value.decision)
  ? '决策已登记，流程按配置退回' : '决策已登记，流程继续流转'))
const renderable = computed(() => true)

function collect() {
  if (!model.value.decision) {
    return { ok: false, message: '请选择决策' }
  }
  if (!model.value.decisionBasis.trim()) {
    return { ok: false, message: '请填写决策依据（追溯靠它）' }
  }
  return {
    ok: true,
    payload: {
      action: REJECT_DECISIONS.includes(model.value.decision) ? 'reject' : 'approve',
      comment: model.value.comment.trim() || `决策：${model.value.decision}`,
      variables: {
        ckplmGateRecord_decision: model.value.decision,
        ckplmGateRecord_decisionBasis: model.value.decisionBasis.trim(),
        ckplmGateRecord_businessRisk: model.value.businessRisk.trim() || null,
        ckplmGateRecord_technicalRisk: model.value.technicalRisk.trim() || null,
        ckplmGateRecord_supplyRisk: model.value.supplyRisk.trim() || null,
        ckplmGateRecord_conditionList: model.value.conditionList.trim() || null,
        ckplmGateRecord_voteResult: model.value.voteResult.trim() || null,
      },
    },
  }
}

defineExpose({ collect, submitText, successText, renderable })
</script>

<style scoped>
.gdr-section {
  margin: 6px 0 12px;
  padding-left: 8px;
  border-left: 3px solid #1677ff;
  font-weight: 600;
  color: #333;
}

.gdr-extra {
  display: block;
  margin-top: 4px;
  font-size: 12px;
  color: #999;
  line-height: 1.7;
}
</style>
