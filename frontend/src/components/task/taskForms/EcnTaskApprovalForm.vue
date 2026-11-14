<template>
  <div class="etaf">
    <EcnTaskObjectsPanel :form="form" />

    <div class="etaf-card">
      <div class="etaf-head">审批结论</div>
      <!-- 结论规则（能否驳回 / 驳回目标 / 意见必填）只认 DSL 那一份解释，不在这里重写 -->
      <ApprovalOpinionForm ref="opinionRef" :form="form" />
    </div>
  </div>
</template>

<script setup>
import { computed, ref } from 'vue'
import EcnTaskObjectsPanel from './EcnTaskObjectsPanel.vue'
import ApprovalOpinionForm from './ApprovalOpinionForm.vue'

/**
 * 「变更任务执行结果审批」表单（`change-task-approval`，领导审批节点）。
 *
 * <p>比内置「审批意见」多一块只读的<b>本任务修订对象与新版本</b>：审批人判的是
 * "这批对象是否已经改到新版本"，不是凭记忆签。下方结论区直接复用内置审批意见表单 ——
 * 驳回开关、驳回目标、意见必填一律由 DSL 节点声明解释，模板多一套实现就会与设计器各说各话。
 *
 * <p>本组件只做「组合 + 转发」：渲染器看到的是这一层，提交载荷由被包裹的内置表单收集，
 * submitText / successText / renderable 也照原样透出，避免出现两处按钮文案。
 */
const props = defineProps({
  form: { type: Object, default: null },
  taskId: { type: String, default: '' },
  activities: { type: Array, default: () => [] }
})

const opinionRef = ref(null)

const submitText = computed(() => opinionRef.value?.submitText || '同意并提交')
const successText = computed(() => opinionRef.value?.successText || '审批通过，流程继续流转')
/** 只读面板取不到详情也照样能审批，结论表单本身不依赖 DSL 才能渲染 */
const renderable = computed(() => opinionRef.value?.renderable !== false)

async function collect() {
  return (await opinionRef.value?.collect?.()) || { ok: true, payload: { action: 'approve', comment: '' } }
}

defineExpose({ collect, submitText, successText, renderable })
</script>

<style scoped>
.etaf-card {
  padding: 12px 14px;
  border: 1px solid #f0f0f0;
  border-radius: 8px;
  background: #fff;
}

.etaf-head {
  margin-bottom: 10px;
  font-size: 13px;
  font-weight: 600;
  color: #262626;
}
</style>
