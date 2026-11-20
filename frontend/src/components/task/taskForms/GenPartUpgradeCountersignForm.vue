<template>
  <div class="gputf">
    <PartUsedByPanel :form="form" />

    <div class="gputf-card">
      <div class="gputf-head">会签结论</div>
      <!-- 规则、他人办理进度、本人一票都复用内置会签表单（规则来自 DSL，进度来自办理页时间轴） -->
      <CountersignForm
        ref="countersignRef"
        :form="form"
        :task-id="taskId"
        :activities="activities"
      />
    </div>
  </div>
</template>

<script setup>
import { computed, ref } from 'vue'
import PartUsedByPanel from './PartUsedByPanel.vue'
import CountersignForm from './CountersignForm.vue'

/**
 * 「升级为通用件认定」工程会签表单（`gen-part-upgrade-countersign`）。
 *
 * <p>与领导审批看的是同一块只读引用情况 —— 会签人判的同样是"这个零件够不够通用件"，
 * 只是聚合规则与每人一票由内置会签表单按 DSL 解释，行业表单不另写一份规则。
 */
const props = defineProps({
  form: { type: Object, default: null },
  /** 当前任务 id：会签表单用来标出"我"那一行 */
  taskId: { type: String, default: '' },
  /** 流程进度：同一会签活动下每人一条 */
  activities: { type: Array, default: () => [] }
})

const countersignRef = ref(null)

const submitText = computed(() => countersignRef.value?.submitText || '同意并提交')
const successText = computed(() => countersignRef.value?.successText || '已同意，会签按配置规则继续')
const renderable = computed(() => countersignRef.value?.renderable !== false)

async function collect() {
  return (await countersignRef.value?.collect?.()) || { ok: true, payload: { action: 'approve', comment: '' } }
}

defineExpose({ collect, submitText, successText, renderable })
</script>

<style scoped>
.gputf-card {
  padding: 12px 14px;
  border: 1px solid #f0f0f0;
  border-radius: 8px;
  background: #fff;
}

.gputf-head {
  margin-bottom: 10px;
  font-size: 13px;
  font-weight: 600;
  color: #262626;
}
</style>
