<template>
  <a-form layout="vertical">
    <a-form-item label="审批意见" :required="requiredNow">
      <div class="aof-routes">
        <a-radio-group v-model:value="action">
          <a-radio value="approve">同意</a-radio>
          <!-- 活动没开驳回时不摆这个选项：让人选了却被后端/流程拒掉，不如不给 -->
          <a-radio v-if="model.allowReject" value="reject">驳回</a-radio>
        </a-radio-group>
        <!-- 驳回后回到哪儿是设计期定的，但必须让经办人看见：否则不知道这一下把流程退到了哪里 -->
        <span v-if="action === 'reject' && model.allowReject" class="aof-target">
          驳回后回到：{{ model.rejectTargetText }}
        </span>
      </div>
      <a-textarea v-model:value="comment" :rows="4" :placeholder="placeholder" />
    </a-form-item>
  </a-form>
</template>

<script setup>
import { computed, ref } from 'vue'
import { buildApprovalOpinionModel, validateApprovalOpinion } from '@/flow-designer/app/task/approval-opinion'

/**
 * 「审批意见」—— 审批活动的固定表单模板。
 *
 * <p>它回答两件事：这次是<b>同意还是驳回</b>，以及<b>为什么</b>。
 * 驳回时额外显示退回到哪里（驳回目标是设计期配置，运行期只显示）。
 *
 * <p>规则（是否允许驳回、驳回目标、意见是否必填）全部来自 DSL 节点声明，
 * 由 {@link buildApprovalOpinionModel} 解释成模型；本组件只渲染与收集 ——
 * 规则写在组件里就会与设计器的配置各说各话。
 */
const props = defineProps({
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

/** 按钮文案表明这次提交的结论，避免选了"驳回"却按了个"确定" */
const submitText = computed(() => (isReject.value ? '驳回并提交' : '同意并提交'))

/** 提交成功后的回执文案（结果页用）：带上结论，别让用户回头猜自己点了什么 */
const successText = computed(() =>
  (isReject.value ? '已驳回，流程按配置退回到指定位置' : '审批通过，流程继续流转'))

/** 本表单不依赖 DSL 才能渲染（没 DSL 也能"同意/驳回 + 意见"），故始终可渲染 */
const renderable = computed(() => true)

/** 收集提交载荷；不合规时返回原因（由渲染器统一提示） */
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
/* 选项与"驳回后回到"同一行：结论和它的后果应该一起看到 */
.aof-routes {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 4px 12px;
  margin-bottom: 8px;
}

.aof-target {
  font-size: 12px;
  color: #d46b08;
  background: #fff7e6;
  border: 1px solid #ffe7ba;
  border-radius: 4px;
  padding: 1px 8px;
}
</style>
