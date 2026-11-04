<template>
  <div class="cqr">
    <!-- ① 只读：本流程的元器件引入申请信息（器件 + MPN + 申请要点） -->
    <div class="cqr-section">申请信息（只读）</div>
    <QualificationRequestSummary :task-id="taskId" class="cqr-summary" />

    <!-- ② 操作：审批结论 + 意见（口径与内置「审批意见」一致：允许驳回、驳回目标、意见必填都看节点声明） -->
    <div class="cqr-section">审批结论</div>
    <a-form layout="vertical">
      <a-form-item label="审批意见" :required="requiredNow">
        <div class="cqr-routes">
          <a-radio-group v-model:value="action">
            <a-radio value="approve">同意</a-radio>
            <!-- 活动没开驳回时不摆这个选项：让人选了却被后端/流程拒掉，不如不给 -->
            <a-radio v-if="model.allowReject" value="reject">驳回</a-radio>
          </a-radio-group>
          <span v-if="isReject && model.allowReject" class="cqr-target">
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
import QualificationRequestSummary from './QualificationRequestSummary.vue'

/**
 * 「元器件认证审批」—— 本流程**审批节点专用**（用户要求，2026-10-13）。
 *
 * <h3>为什么不用内置「审批意见」</h3>
 * <p>内置那张只有"同意 / 驳回 + 意见"，审批人<b>看不到这颗器件的申请信息</b>（需求、参数、
 * 使用位置、认证要求、器件与 MPN、单一来源风险……），等于让人凭记忆签字。本表 =
 * 内置审批的表决口径（直接复用 {@code buildApprovalOpinionModel} /
 * {@code validateApprovalOpinion}，允许驳回、驳回目标、意见必填全部随节点声明，一处不改）
 * <b>+ 一块只读申请信息</b>（{@link QualificationRequestSummary}，与会签表、认证表共用）。
 *
 * <h3>提交口径</h3>
 * <p>与内置审批完全一致：{@code collect() → { ok, payload: { action, comment } }} ——
 * 不额外写流程变量（结论由引擎的 approved / 驳回路由处理），保证节点行为与内置表无差别。
 */
const props = defineProps({
  /** 任务 id —— 只读摘要据此取申请信息 */
  taskId: { type: String, default: '' },
  /** 表单上下文（ProcessTaskFormVO）：formKey / taskDefinitionKey / dslJson */
  form: { type: Object, default: null },
})

const action = ref('approve')
const comment = ref('')

/** 该审批活动的表单模型（DSL 取不到时走默认：可驳回、退上一步、驳回必填意见） */
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

/** 该不该标"必填"：随当前选择变 —— 驳回必填、同意按节点声明 */
const requiredNow = computed(() =>
  (isReject.value ? model.value.commentRequiredOnReject : model.value.commentRequiredOnApprove))

const placeholder = computed(() => {
  if (isReject.value) {
    return model.value.commentRequiredOnReject ? '请说明驳回理由（必填）' : '请说明驳回理由...'
  }
  return model.value.commentRequiredOnApprove ? '请填写审批意见（必填）' : '请输入审批意见...'
})

const submitText = computed(() => (isReject.value ? '驳回并提交' : '同意并提交'))
const successText = computed(() =>
  (isReject.value ? '已驳回，流程按配置退回到指定位置' : '审批通过，流程继续流转'))
/** 只读摘要取不到也不挡办理（结论与意见不依赖它） */
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
.cqr-section {
  margin: 6px 0 12px;
  padding-left: 8px;
  border-left: 3px solid #1677ff;
  font-weight: 600;
  color: #333;
}

.cqr-summary {
  margin-bottom: 18px;
}

.cqr-routes {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 4px 12px;
  margin-bottom: 8px;
}

.cqr-target {
  font-size: 12px;
  color: #d46b08;
  background: #fff7e6;
  border: 1px solid #ffe7ba;
  border-radius: 4px;
  padding: 1px 8px;
}
</style>
