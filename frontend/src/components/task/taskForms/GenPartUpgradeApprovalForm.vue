<template>
  <div class="gpuaf">
    <PartUsedByPanel :form="form" />

    <div class="gpuaf-card">
      <div class="gpuaf-head">审批结论</div>
      <!-- 结论规则（能否驳回 / 驳回目标 / 意见必填）只认 DSL 那一份解释，不在这里重写 -->
      <ApprovalOpinionForm ref="opinionRef" :form="form" />
    </div>
  </div>
</template>

<script setup>
import { computed, ref } from 'vue'
import PartUsedByPanel from './PartUsedByPanel.vue'
import ApprovalOpinionForm from './ApprovalOpinionForm.vue'

/**
 * 「升级为通用件认定」领导审批表单（`gen-part-upgrade-approval`）。
 *
 * <p>比内置「审批意见」多一块只读的<b>零件被引用情况</b>：审批人判的是这个零件
 * 是否真的跨型号复用、够不够通用件门槛，不能凭记忆签。结论区复用内置审批意见表单 ——
 * 驳回开关、驳回目标、意见必填一律由 DSL 节点声明解释。
 *
 * <p>本组件只做「组合 + 转发」：提交载荷由被包裹的内置表单收集，
 * submitText / successText / renderable 照原样透出，按钮文案不写第二份。
 */
const props = defineProps({
  form: { type: Object, default: null },
  taskId: { type: String, default: '' },
  activities: { type: Array, default: () => [] }
})

const opinionRef = ref(null)

const submitText = computed(() => opinionRef.value?.submitText || '同意并提交')
const successText = computed(() => opinionRef.value?.successText || '审批通过，流程继续流转')
/** 引用面板取不到数据也照样能审批，结论表单本身不依赖它 */
const renderable = computed(() => opinionRef.value?.renderable !== false)

async function collect() {
  return (await opinionRef.value?.collect?.()) || { ok: true, payload: { action: 'approve', comment: '' } }
}

defineExpose({ collect, submitText, successText, renderable })
</script>

<style scoped>
.gpuaf-card {
  padding: 12px 14px;
  border: 1px solid #f0f0f0;
  border-radius: 8px;
  background: #fff;
}

.gpuaf-head {
  margin-bottom: 10px;
  font-size: 13px;
  font-weight: 600;
  color: #262626;
}
</style>
