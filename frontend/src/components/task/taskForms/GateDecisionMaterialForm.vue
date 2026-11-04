<template>
  <div class="gdm">
    <!-- ① 只读：发起节点填过的决策申请（变量 ckplmGateDecision_*，见 GateDecisionApplyForm#collect） -->
    <div class="gdm-section">决策申请（只读）</div>
    <GateDecisionRequestSummary :form="form" class="gdm-summary" />

    <!-- ② 操作：核对结论 + 意见（口径与内置「审批意见」一致，规则全部来自节点声明） -->
    <div class="gdm-section">齐备性核对</div>
    <a-form layout="vertical">
      <a-form-item label="核对意见" :required="requiredNow">
        <div class="gdm-routes">
          <a-radio-group v-model:value="action">
            <a-radio value="approve">材料齐备，同意进入决策会</a-radio>
            <!-- 活动没开驳回时不摆这个选项：让人选了却被后端/流程拒掉，不如不给 -->
            <a-radio v-if="model.allowReject" value="reject">材料不齐备，退回补齐</a-radio>
          </a-radio-group>
          <span v-if="isReject && model.allowReject" class="gdm-target">
            驳回后回到：{{ model.rejectTargetText }}
          </span>
        </div>
        <a-textarea v-model:value="comment" :rows="4" :placeholder="placeholder" />
      </a-form-item>
    </a-form>
  </div>
</template>

<script setup>
import { computed, ref } from 'vue'
import { buildApprovalOpinionModel, validateApprovalOpinion } from '@/flow-designer/app/task/approval-opinion'
import GateDecisionRequestSummary from './GateDecisionRequestSummary.vue'

/**
 * 「决策材料齐备性核对单」—— 阶段决策流程（gate-decision）的**核对节点专用**（2026-10-29 用户要求）。
 *
 * <h3>为什么不用内置「审批意见」</h3>
 * <p>用户原文：*"决策评审材料齐备性核对没有独立的节点表单，表单中需要体现决策申请内容，参考技术评审"*。
 * 内置那张只有"同意 / 驳回 + 意见" —— 核对人看不到<b>申请了什么</b>（哪道门、完成率多少、
 * 还有几个阻塞问题、决策材料是哪几项交付件），等于凭记忆签字，与技术评审核对人此前遇到的是同一个问题
 * （见 `ComponentQualificationReviewForm` 的同款说明）。
 *
 * <h3>两块内容</h3>
 * <ol>
 *   <li><b>只读的决策申请</b>：{@link GateDecisionRequestSummary}（与决策会那张表共用同一份）；</li>
 *   <li><b>核对结论 + 意见</b>：与内置审批完全同一套规则（复用 {@code buildApprovalOpinionModel} /
 *       {@code validateApprovalOpinion}），允许驳回、驳回目标、意见是否必填全部随节点声明。
 *       本流程的核对节点没有画「驳回」出边时不显示驳回选项 —— 否则点了会顺着默认出边继续往前。</li>
 * </ol>
 *
 * <h3>提交口径</h3>
 * <p>与内置审批一致：{@code collect() → { ok, payload: { action, comment } }}，不额外写流程变量。
 */
const props = defineProps({
  /** 任务 id（本表不依赖任务上下文，保留以满足统一契约） */
  taskId: { type: String, default: '' },
  /** 表单上下文（ProcessTaskFormVO）：formKey / taskDefinitionKey / dslJson / variables / businessOid */
  form: { type: Object, default: null },
})

const action = ref('approve')
const comment = ref('')

/** 该审批活动的表单模型（规则来自 DSL：有没有驳回出边、意见是否必填） */
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
  return buildApprovalOpinionModel(dsl, props.form?.taskDefinitionKey)
})

const isReject = computed(() => action.value === 'reject')

const requiredNow = computed(() =>
  (isReject.value ? model.value.commentRequiredOnReject : model.value.commentRequiredOnApprove))

const placeholder = computed(() => {
  if (isReject.value) {
    return model.value.commentRequiredOnReject ? '请说明缺哪些材料、需要谁补齐（必填）' : '请说明缺哪些材料...'
  }
  return model.value.commentRequiredOnApprove ? '请填写核对意见（必填）' : '请输入核对意见...'
})

const submitText = computed(() => (isReject.value ? '退回并提交' : '同意并提交'))
const successText = computed(() =>
  (isReject.value ? '已退回补齐，流程按配置退回到指定位置' : '核对通过，流程继续流转'))
/** 申请内容取不到也挡不住核对（结论与意见不依赖它） */
const renderable = computed(() => true)

function collect() {
  const issue = validateApprovalOpinion(model.value, action.value, comment.value)
  if (issue) {
    return { ok: false, message: issue }
  }
  return { ok: true, payload: { action: action.value, comment: comment.value } }
}

defineExpose({ collect, submitText, successText, renderable })
</script>

<style scoped>
.gdm-section {
  margin: 6px 0 12px;
  padding-left: 8px;
  border-left: 3px solid #1677ff;
  font-weight: 600;
  color: #333;
}

.gdm-summary {
  margin-bottom: 18px;
}

.gdm-routes {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 4px 12px;
  margin-bottom: 8px;
}

.gdm-target {
  font-size: 12px;
  color: #d46b08;
  background: #fff7e6;
  border: 1px solid #ffe7ba;
  border-radius: 4px;
  padding: 1px 8px;
}
</style>
