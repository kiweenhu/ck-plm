<template>
  <div class="tfr">
    <!-- 按 formKey 派发到模板组件；解析不到走兜底模板（见 taskForms/index.js）
         activities 一并下传：会签表单要靠它显示"他人办到哪一步了"（同一活动下每人一条） -->
    <component :is="view" ref="formRef" :form="form" :task-id="taskId" :activities="activities" />

    <!-- 动作条由渲染器统一提供：所有模板的"提交/取消/并发/失败"处理只有这一份 -->
    <div class="tfr-actions">
      <span class="tfr-tip">提交后会立即推进流程</span>
      <a-space>
        <a-button @click="emit('cancel')">取消</a-button>
        <a-button type="primary" :loading="submitting" @click="submit">{{ submitText }}</a-button>
      </a-space>
    </div>
  </div>
</template>

<script setup>
import { ref, computed, watch } from 'vue'
import { message } from 'ant-design-vue'
import { completeTask } from '@/api'
import { resolveTaskFormView, FALLBACK_FORM_VIEW } from './taskForms'

/**
 * 任务表单**渲染器** —— 「通用信息 + 任务表单模板」里的那一半：模板派发 + 提交。
 *
 * <p>职责边界刻意这样切：
 * <ul>
 *   <li><b>模板</b>（taskForms/*）：只管渲染字段与"收集值"（暴露 {@code collect()}），
 *       它不知道提交走哪个接口、失败怎么办；</li>
 *   <li><b>渲染器</b>（本组件）：按 formKey 选模板、调用 {@code collect()}、提交、
 *       处理并发与失败。</li>
 * </ul>
 * 好处是新增模板不用重写提交逻辑，提交口径也不会随模板数量漂移。
 *
 * <p>模板暴露的契约：{@code collect() → { ok, payload?, message? }}、{@code submitText}、
 * {@code successText}。
 */
const props = defineProps({
  /** 任务 id */
  taskId: { type: String, required: true },
  /** 表单上下文（ProcessTaskFormVO）：formKey / taskDefinitionKey / dslJson */
  form: { type: Object, default: null },
  /**
   * 流程进度（TaskContextVO.activities）—— 会签表单用它列出"谁办了、什么结论、什么意见"。
   *
   * <p>不另外请求：办理页已经拿到了整份进度（左栏时间轴用的就是它），
   * 表单再去请求一次就会出现"左栏与表单显示不一致"的可能。
   */
  activities: { type: Array, default: () => [] },
})
const emit = defineEmits(['completed', 'cancel'])

const formRef = ref(null)
const submitting = ref(false)
/** 专属模板渲染不了（如它依赖的 DSL 缺失）时，强制走兜底模板 */
const forceFallback = ref(false)

// 传整个表单上下文（不只是 formKey）：没写 formKey 的老实例要按节点类型派生专属模板
const view = computed(() =>
  forceFallback.value ? FALLBACK_FORM_VIEW : resolveTaskFormView(props.form))

const submitText = computed(() => formRef.value?.submitText || '提交办理')

/**
 * 模板自报"能不能渲染"（暴露的 `renderable`）。
 *
 * <p>专属模板依赖流程 DSL（例如「设置流程参与者」要列出下游活动），而老数据的部署
 * 可能取不到 DSL。此时**兜底到通用表单**而不是把办理卡死：办理本身只依赖"结论 + 意见"，
 * DSL 缺失是数据/配置问题，不该变成用户办不了事。
 */
const renderable = computed(() => formRef.value?.renderable !== false)
watch(renderable, (ok) => {
  if (!ok) {
    message.info('该节点配置的表单取不到流程内容，已切换为通用审批表单')
    forceFallback.value = true
  }
}, { flush: 'post' })

// 换了任务/节点就重新判断（不要把上一条任务的兜底状态带过来）
watch(() => props.form, () => { forceFallback.value = false })

async function submit() {
  if (submitting.value) return
  const collected = formRef.value?.collect?.() || { ok: true, payload: {} }
  if (!collected.ok) {
    message.warning(collected.message || '表单填写有误')
    return
  }
  // action 缺省给 approve：设置类节点没有"驳回"这种结论，但流程变量 approved 在
  // 发起那一刻就写下更安全（下游网关读它时不会因为没有值而走错分支）—— 与原来弹窗的行为一致
  const payload = { action: 'approve', ...collected.payload }
  submitting.value = true
  try {
    const res = await completeTask(props.taskId, payload)
    if (res?.code !== 200) {
      // 并发办理：别人先办掉了 —— 说清楚，别让人以为是自己操作失败
      message.warning(res?.message || '任务已被处理')
      emit('completed', { ok: false, message: res?.message })
      return
    }
    const text = formRef.value?.successText || '已提交'
    message.success(text)
    emit('completed', { ok: true, text })
  } catch {
    // 失败原因由响应拦截器统一提示
  } finally {
    submitting.value = false
  }
}
</script>

<style scoped>
.tfr-actions {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-top: 20px;
  padding-top: 14px;
  border-top: 1px solid #f0f0f0;
}

.tfr-tip {
  font-size: 12px;
  color: #bfbfbf;
}
</style>
