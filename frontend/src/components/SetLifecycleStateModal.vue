<template>
  <a-modal
    :visible="visible"
    title="设置生命周期状态"
    :confirm-loading="saving"
    :ok-button-props="{ disabled: !canSubmit }"
    ok-text="确定"
    cancel-text="取消"
    width="540px"
    @ok="confirm"
    @cancel="close"
  >
    <a-spin :spinning="loading">
      <!-- 类型没绑生命周期模板：说清原因，不给一个空下拉让人猜 -->
      <a-alert
        v-if="options?.reason"
        type="warning"
        show-icon
        :message="options.reason"
      />
      <template v-else>
        <!-- 对象正在流程中：候选照给，但这次不许设（流程里的「设置状态」节点会在该到的时候改它） -->
        <a-alert
          v-if="blocked"
          type="warning"
          show-icon
          class="slm-blocked"
          :message="options.blockedReason"
        />
        <div class="slm-head">
          <span class="slm-object">{{ record?.name || record?.code || record?.number || '该对象' }}</span>
          <span class="slm-meta">
            当前状态：<b>{{ currentName }}</b>
            <span class="slm-sep">·</span>
            生命周期模板：{{ options?.templateName || options?.templateCode || '—' }}
          </span>
        </div>

        <a-radio-group v-model:value="mode" class="slm-radio" :disabled="blocked">
          <a-radio value="INITIAL" :disabled="initialDisabled">
            设置对象到初始状态（{{ options?.initial?.name || options?.initial?.code || '—' }}）
          </a-radio>
          <a-radio value="SPECIFIED">设置对象到指定状态</a-radio>
        </a-radio-group>

        <div v-if="mode === 'SPECIFIED'" class="slm-states">
          <a-select
            v-model:value="targetStateCode"
            style="width:100%"
            placeholder="选择目标状态"
            :options="stateOptions"
            :disabled="blocked"
          />
        </div>

        <div class="slm-hint">{{ hint }}</div>
      </template>
    </a-spin>
  </a-modal>
</template>

<script setup>
/**
 * 「设置生命周期状态」弹窗 —— 行操作下拉里的那一项。
 *
 * <p><b>两个选项，依据都是该类型绑定的生命周期模板</b>：
 * <ol>
 *   <li><b>设置对象到初始状态</b>：模板定义的初始状态（如「草稿」）。执行时<b>沿模板的回退规则
 *       逐跳退</b>（已发布 → 工作中 → 草稿），不是一步跳过去 —— 多跳在模板里本来就没有直接规则，
 *       允许跳跃等于把回退链当摆设；某一跳没有回退规则会明确失败并说清卡在哪。</li>
 *   <li><b>设置对象到指定状态</b>：从模板状态清单里挑一个，受迁移规则约束（当前状态能一步到它）。
 *       候选由后端按同一份模板给出，并标出"不到"的原因 —— 迁不过去的选项直接禁用，
 *       不让用户点了才知道。</li>
 * </ol>
 *
 * <p>候选、可达性都来自后端（{@code GET /{oid}/lifecycle-states}），与执行侧同一份模板，
 * 因此不会出现"界面让选、后端拒收"。
 */
import { computed, ref, watch } from 'vue'
import { message } from 'ant-design-vue'
import { getLifecycleStateOptions, setLifecycleState } from '@/api'

const props = defineProps({
  visible: { type: Boolean, default: false },
  /**
   * 业务对象（列表行）：{ oid, name, code/number, typeDefinitionCode, statusCode? }
   * 大版本不用传：服务端按"该大版本当前的最新小版本"解析。
   */
  record: { type: Object, default: null },
})
const emit = defineEmits(['update:visible', 'success'])

const loading = ref(false)
const saving = ref(false)
/** 候选项（后端按类型绑定的生命周期模板给出） */
const options = ref(null)
const mode = ref('INITIAL')
const targetStateCode = ref(undefined)

const typeCode = computed(() =>
  props.record?.typeDefinitionCode || props.record?.typeCode || '')

const currentName = computed(() =>
  options.value?.current?.name || options.value?.current?.code || '（无状态）')

/**
 * 该对象正在流程中（同一对象 + 大版本已有流程实例在跑）—— 这次不允许手工改状态。
 *
 * <p>后端判的：流程里的「设置状态」服务节点会在该到的时候改它，人手工插一脚会让
 * "流程走到哪一步"与"对象是什么状态"对不上。这里只负责呈现与禁用；
 * 提交侧后端还会再挡一次（409），界面禁用不是唯一防线。
 */
const blocked = computed(() => !!options.value?.blockedReason)

/** 已在初始状态就没有"退回"可言 */
const initialDisabled = computed(() =>
  !!options.value?.initial?.code
  && options.value?.initial?.code === options.value?.current?.code)

/** 状态下拉：不可达的禁用，并把原因写进选项末尾（鼠标悬停也能看全） */
const stateOptions = computed(() =>
  (options.value?.states || []).map((s) => ({
    value: s.code,
    label: s.reachable ? s.name : `${s.name}（不可设置）`,
    title: s.reachable ? undefined : s.reason,
    disabled: !s.reachable,
  })))

const canSubmit = computed(() => {
  if (!props.visible || options.value?.reason || blocked.value) return false
  if (mode.value === 'INITIAL') return !initialDisabled.value
  return !!targetStateCode.value
})

const hint = computed(() => {
  // 被挡住时上面那条警示已经把原因说完了，再说一遍是噪声
  if (blocked.value) return ''
  if (mode.value === 'INITIAL') {
    return initialDisabled.value
      ? '对象已经在初始状态，无需设置。'
      : `将沿模板的回退规则逐跳退回「${options.value?.initial?.name || ''}」；`
        + '若中途某个状态没有定义回退规则，会明确提示卡在哪一步。'
  }
  const picked = (options.value?.states || []).find((s) => s.code === targetStateCode.value)
  if (picked?.reason) return picked.reason
  return '只能选当前状态能一步迁到的状态（灰掉的项鼠标悬停可见原因）。'
})

watch(() => props.visible, (open) => {
  if (!open) return
  mode.value = 'INITIAL'
  targetStateCode.value = undefined
  options.value = null
  load()
})

async function load() {
  if (!props.record?.oid || !typeCode.value) {
    options.value = { reason: '缺少对象或类型信息，无法确定生命周期模板' }
    return
  }
  loading.value = true
  try {
    const res = await getLifecycleStateOptions(props.record.oid, typeCode.value)
    options.value = res?.code === 200
      ? (res.data || {})
      : { reason: res?.message || '读取生命周期状态失败' }
    // 已有目标状态就直接落在"指定状态"上，少一次点击
    if (options.value?.states?.length) {
      const first = options.value.states.find((s) => s.reachable)
      targetStateCode.value = first?.code
    }
  } catch {
    options.value = { reason: '读取生命周期状态失败，请稍后重试' }
  } finally {
    loading.value = false
  }
}

async function confirm() {
  if (!canSubmit.value) return
  saving.value = true
  try {
    const res = await setLifecycleState(props.record.oid, {
      typeDefinitionCode: typeCode.value,
      mode: mode.value,
      targetStateCode: mode.value === 'SPECIFIED' ? targetStateCode.value : undefined,
    })
    if (res?.code === 200) {
      const target = mode.value === 'INITIAL'
        ? (options.value?.initial?.name || options.value?.initial?.code)
        : (options.value?.states || []).find((s) => s.code === targetStateCode.value)?.name
      message.success(`「${props.record?.name || props.record?.code || '对象'}」已设置为「${target}」`)
      close()
      emit('success')
    }
    // 失败原因（未绑模板 / 不允许的迁移 / 宿主不支持）由响应拦截器统一提示
  } finally {
    saving.value = false
  }
}

function close() {
  emit('update:visible', false)
}
</script>

<style scoped>
.slm-blocked {
  margin-bottom: 12px;
}

.slm-head {
  display: flex;
  flex-direction: column;
  gap: 2px;
  margin-bottom: 12px;
}

.slm-object {
  font-weight: 500;
}

.slm-meta {
  font-size: 12px;
  color: #8c8c8c;
}

.slm-sep {
  margin: 0 6px;
}

.slm-radio {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.slm-states {
  margin-top: 8px;
}

.slm-hint {
  margin-top: 10px;
  padding: 6px 8px;
  border-radius: 4px;
  background: #fafafa;
  font-size: 12px;
  color: #8c8c8c;
  line-height: 1.7;
}
</style>
