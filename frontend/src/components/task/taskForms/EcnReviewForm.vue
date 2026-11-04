<template>
  <EcnSummaryPanel :form="form" />

  <div class="erev-card">
    <div class="erev-head">审批结论</div>
    <a-form layout="vertical">
      <a-form-item label="审批意见" :required="requiredNow">
        <div class="erev-routes">
          <a-radio-group v-model:value="action">
            <a-radio value="approve">同意</a-radio>
            <a-radio v-if="model.allowReject" value="reject">驳回</a-radio>
          </a-radio-group>
          <span v-if="isReject && model.allowReject" class="erev-target">
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
import EcnSummaryPanel from './EcnSummaryPanel.vue'

/**
 * 「变更通告 · 审批」表单。
 *
 * <p>比内置「审批意见」多一块<b>只读的通告全貌</b>：审批人判的是"这张通告要改什么、影响哪些对象、
 * 拆成了哪些任务"，只给一个意见框等于让人凭记忆签。摘要抽在 {@link EcnSummaryPanel}，
 * 与「变更通告会签」共用。
 *
 * <p>结论规则（能否驳回、退回到哪、意见是否必填）仍然只来自 DSL 节点声明，
 * 由 {@link buildApprovalOpinionModel} 解释 —— 规则写死在表单里就会与设计器的配置各说各话。
 */
const props = defineProps({
  /** 表单上下文（ProcessTaskFormVO）：variables / taskDefinitionKey / dslJson */
  form: { type: Object, default: null }
})

const action = ref('approve')
const comment = ref('')

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
  isReject.value ? model.value.commentRequiredOnReject : model.value.commentRequiredOnApprove)

const placeholder = computed(() => {
  if (isReject.value) {
    return model.value.commentRequiredOnReject ? '请说明驳回理由（必填）' : '请说明驳回理由...'
  }
  return model.value.commentRequiredOnApprove ? '请填写审批意见（必填）' : '可补充说明...'
})

const submitText = computed(() => (isReject.value ? '驳回并提交' : '同意并提交'))
const successText = computed(() =>
  isReject.value ? '已驳回，流程按配置退回到指定位置' : '审批通过，流程继续流转')

/** 取不到通告详情也要能办完这一票：本表单永远可渲染，只是少一块只读信息 */
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
.erev-card {
  padding: 12px 14px;
  border: 1px solid #f0f0f0;
  border-radius: 8px;
  background: #fff;
}

.erev-head {
  margin-bottom: 10px;
  font-size: 13px;
  font-weight: 600;
  color: #262626;
}

.erev-routes {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 4px 12px;
  margin-bottom: 8px;
}

.erev-target {
  padding: 1px 8px;
  font-size: 12px;
  color: #d46b08;
  background: #fff7e6;
  border: 1px solid #ffe7ba;
  border-radius: 4px;
}
</style>
