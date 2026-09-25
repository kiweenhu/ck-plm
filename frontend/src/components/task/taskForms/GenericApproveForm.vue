<template>
  <a-form layout="vertical">
    <a-form-item label="审批动作">
      <a-radio-group v-model:value="action">
        <a-radio value="approve">同意</a-radio>
        <a-radio value="reject">驳回</a-radio>
      </a-radio-group>
    </a-form-item>
    <a-form-item label="审批意见" :required="commentRequired">
      <a-textarea v-model:value="comment" :rows="4"
        :placeholder="commentRequired ? '该活动要求填写意见' : '请输入审批意见...'" />
    </a-form-item>
  </a-form>
</template>

<script setup>
import { ref, computed } from 'vue'

/**
 * 通用审批表单 —— **没有专属模板时的兜底**（审批 / 会签 / 办理活动的默认形态）。
 *
 * <p>兜底很重要：节点没配表单、或配了一个运行期取不到的 formKey，办理都不能"打不开"。
 * 它只要两样东西就能办完一件事：结论（同意/驳回）+ 意见。
 *
 * <p>要不要填意见由 DSL 节点声明（{@code commentRequired}）决定 —— 与「设置流程参与者」同一口径，
 * 不在这里写死必填。
 */
const props = defineProps({
  /** 表单上下文（ProcessTaskFormVO）：formKey / taskDefinitionKey / dslJson */
  form: { type: Object, default: null },
})

const action = ref('approve')
const comment = ref('')

/** 当前节点在 DSL 里的声明（意见是否必填等规则写在节点上，不在这里写死） */
const dslNode = computed(() => {
  const raw = props.form?.dslJson
  if (!raw) return null
  try {
    const dsl = JSON.parse(raw)
    return (dsl?.nodes || []).find(n => n.id === props.form?.taskDefinitionKey) || null
  } catch {
    return null
  }
})

/** 该活动是否要求必须填意见（来自 DSL 节点声明） */
const commentRequired = computed(() => dslNode.value?.commentRequired === true)

/** 按钮文案表明这次提交的结论，避免选了"驳回"却按了个"确定" */
const submitText = computed(() => (action.value === 'reject' ? '驳回并提交' : '同意并提交'))

/** 提交成功后的回执文案（结果页用）：带上结论，别让用户回头猜自己点了什么 */
const successText = computed(() => (action.value === 'reject' ? '已驳回，流程继续流转' : '审批通过，流程继续流转'))

/** 兜底模板永远可渲染（它只依赖"结论 + 意见"，不依赖流程 DSL） */
const renderable = computed(() => true)

/** 收集提交载荷；不合规时返回原因（由渲染器统一提示） */
function collect() {
  if (commentRequired.value && !comment.value.trim()) {
    return { ok: false, message: '该活动要求填写审批意见' }
  }
  return { ok: true, payload: { action: action.value, comment: comment.value } }
}

defineExpose({ collect, submitText, successText, renderable })
</script>
