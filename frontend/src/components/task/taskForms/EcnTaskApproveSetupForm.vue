<template>
  <div class="etas">
    <EcnTaskObjectsPanel :form="form" />

    <div class="etas-card">
      <div class="etas-head">
        指定下游审批人
        <span class="etas-muted">先看上面这批对象改到了哪一步，再决定下游审批 / 会签交给谁</span>
      </div>
      <!--
        槽位、候选范围、校验、ckplmSetupAssignees_* 变量全部复用内置「设置流程参与者」表单，
        与设计器同一份实现；这里只把它挂到行业表单的上下文里。
      -->
      <SetupAssigneeForm ref="setupRef" :form="form" />
    </div>
  </div>
</template>

<script setup>
import { computed, ref } from 'vue'
import EcnTaskObjectsPanel from './EcnTaskObjectsPanel.vue'
import SetupAssigneeForm from './SetupAssigneeForm.vue'

/**
 * 「变更任务审批设置参与者」表单（`change-task-approve-setup`，设置审批人节点）。
 *
 * <p>上半屏是本任务已修订的对象与新版本 —— 派谁审要看改的是结构件还是工程数据；
 * 下半屏照旧列出下游人工活动逐个挑人。选人逻辑直接复用内置表单，
 * 这里只做组合与提交载荷转发，避免"设计器一套、行业包另一套"的槽位判定分叉。
 */
const props = defineProps({
  form: { type: Object, default: null },
  taskId: { type: String, default: '' },
  activities: { type: Array, default: () => [] }
})

const setupRef = ref(null)

const submitText = computed(() => setupRef.value?.submitText || '确定')
const successText = computed(() => setupRef.value?.successText || '已指定下游参与人，流程继续流转')
/** 内置表单拿不到 DSL 时会自报不可渲染，由渲染器兜底到通用表单 */
const renderable = computed(() => setupRef.value?.renderable !== false)

async function collect() {
  // 候选还在拉就提交会写出"零槽位"的空变量：下游节点没人认领，且很难回溯。
  // 内置表单为此暴露了 ready，这里在转发前先挡一下。
  if (!setupRef.value?.ready) {
    return { ok: false, message: '下游参与人候选还在加载，请稍候再提交' }
  }
  return (await setupRef.value?.collect?.()) || { ok: false, message: '参与人设置尚未就绪' }
}

defineExpose({ collect, submitText, successText, renderable })
</script>

<style scoped>
.etas-card {
  padding: 12px 14px;
  border: 1px solid #f0f0f0;
  border-radius: 8px;
  background: #fff;
}

.etas-head {
  margin-bottom: 10px;
  font-size: 13px;
  font-weight: 600;
  color: #262626;
}

.etas-muted {
  margin-left: 10px;
  font-size: 12px;
  font-weight: 400;
  color: #8c8c8c;
}
</style>
