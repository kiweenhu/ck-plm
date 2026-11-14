<template>
  <div class="etcs">
    <EcnTaskObjectsPanel :form="form" />

    <div class="etcs-card">
      <div class="etcs-head">会签结论</div>
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
import EcnTaskObjectsPanel from './EcnTaskObjectsPanel.vue'
import CountersignForm from './CountersignForm.vue'

/**
 * 「变更任务执行结果会签」表单（`change-task-countersign`）。
 *
 * <p>与变更通告会签的区别在问题：ECN 问"这一段怎么改"，这里问"这条任务改出来的结果认不认" ——
 * 所以顶部是<b>本任务修订的对象与版本</b>（只读），结论区复用内置会签表单：
 * 聚合规则 / 参与人进度 / 我这一票由 {@code buildCountersignModel} 统一解释，
 * 不在行业表单里另写一份规则。
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
.etcs-card {
  padding: 12px 14px;
  border: 1px solid #f0f0f0;
  border-radius: 8px;
  background: #fff;
}

.etcs-head {
  margin-bottom: 10px;
  font-size: 13px;
  font-weight: 600;
  color: #262626;
}
</style>
