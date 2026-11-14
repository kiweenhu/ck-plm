<template>
  <EcnSummaryPanel ref="panel" :form="form" detailed />

  <div class="ets-card">
    <div class="ets-head">汇总确认</div>
    <a-alert
      type="info"
      show-icon
      :message="'请逐个核对上面的「更改事项」与「受影响对象」：任务是否都已完成、对象是否都已修出新版本。确认后变更通告标记为「已完成」。'"
    />
    <a-form layout="vertical" style="margin-top: 12px">
      <a-form-item label="确认意见">
        <a-textarea
          v-model:value="comment"
          :rows="3"
          placeholder="可补充实施结果说明、遗留事项、关闭建议等（选填）"
        />
      </a-form-item>
    </a-form>
  </div>
</template>

<script setup>
import { ref } from 'vue'
import EcnSummaryPanel from './EcnSummaryPanel.vue'

/**
 * 「变更通告 · 汇总确认」表单。
 *
 * <p>多实例子流程全部跑完后停在这里，由通告责任人逐个核对执行结果 —— 「跑完」不等于
 * 「状态变了」，所以核对的是任务状态与对象执行状态，而不只是"流程都走完了"。
 *
 * <p>前置校验<b>只做一次提示，不做最终裁决</b>：真正的门槛在后端
 * （{@code ChangeNoticeService#completeByProcess} 与人工「完成」同一口径）。
 * 前端这关的价值是把"还差哪几个"提前说出来，而不是让人提交后才收到一条失败。
 */
const props = defineProps({
  /** 表单上下文（ProcessTaskFormVO）：variables / taskDefinitionKey / dslJson */
  form: { type: Object, default: null }
})

const panel = ref(null)
const comment = ref('')

const submitText = '确认并提交'
const successText = '汇总确认已提交，变更通告标记为实施完成'
const renderable = true

function collect() {
  const snapshot = panel.value?.snapshot?.() || { tasks: [], affectedObjects: [] }

  const openTasks = snapshot.tasks.filter((task) => task.status !== 'COMPLETED')
  if (openTasks.length > 0) {
    const codes = openTasks.slice(0, 3).map((task) => task.code || task.name).join('、')
    return {
      ok: false,
      message: `还有 ${openTasks.length} 个变更任务未完成（${codes}${openTasks.length > 3 ? ' 等' : ''}），`
        + '不能确认。请让这些任务的流程先走完'
    }
  }

  const pending = snapshot.affectedObjects.filter((item) => !item.executedAt)
  if (pending.length > 0) {
    const codes = pending.slice(0, 3).map((item) => item.entityCode || item.entityName).join('、')
    return {
      ok: false,
      message: `还有 ${pending.length} 个受影响对象未执行完毕（${codes}${pending.length > 3 ? ' 等' : ''}），`
        + '不能在此时确认完成'
    }
  }

  return {
    ok: true,
    payload: {
      action: 'approve',
      comment: comment.value.trim() || '全部变更任务已执行完毕，汇总确认通过'
    }
  }
}

defineExpose({ collect, submitText, successText, renderable })
</script>

<style scoped>
.ets-card {
  margin-bottom: 16px;
  padding: 12px 14px;
  border: 1px solid #f0f0f0;
  border-radius: 8px;
  background: #fcfcfc;
}

.ets-head {
  margin-bottom: 10px;
  font-size: 13px;
  font-weight: 600;
}
</style>
